package com.iporadar.app.data.repo

import android.content.Context
import com.iporadar.app.data.local.AppPrefs
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.data.model.Registrar
import com.iporadar.app.data.model.Subscription
import com.iporadar.app.data.remote.FeedApi
import com.iporadar.app.data.remote.FeedEnvelope
import com.iporadar.app.data.remote.NseApi
import com.iporadar.app.data.remote.nseToDetail
import com.iporadar.app.data.remote.nseToIpos
import com.iporadar.app.data.remote.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

enum class DataSource { NONE, BUNDLED, CACHE, NETWORK }

data class IpoFeedState(
    val ipos: List<Ipo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0L,
    val source: DataSource = DataSource.NONE
)

/**
 * Single source of truth for IPO data.
 *
 * Order of preference: our free JSON feed (carries GMP, registrar, exact dates),
 * enriched with NSE live subscription numbers. If the network is unavailable we
 * fall back to the last good snapshot, and finally to the bundled sample so the
 * app is never blank on first launch.
 */
class IpoRepository(
    private val context: Context,
    private val feedApi: FeedApi,
    private val nseApi: NseApi,
    private val prefs: AppPrefs,
    private val json: Json
) {

    private val _state = MutableStateFlow(IpoFeedState())
    val state: StateFlow<IpoFeedState> = _state.asStateFlow()

    private val refreshLock = Mutex()

    fun byId(id: String): Ipo? = _state.value.ipos.firstOrNull { it.id == id }

    /** Populates the UI instantly from disk before any network call goes out. */
    suspend fun warmStart() {
        if (_state.value.ipos.isNotEmpty()) return
        val cached = runCatching { prefs.feedSnapshot() }.getOrNull()
        if (cached != null) {
            val parsed = parseEnvelope(cached.first)
            if (parsed.isNotEmpty()) {
                _state.value = IpoFeedState(parsed, updatedAt = cached.second, source = DataSource.CACHE)
                return
            }
        }
        val bundled = readBundled()
        if (bundled.isNotEmpty()) {
            _state.value = IpoFeedState(bundled, source = DataSource.BUNDLED)
        }
    }

    suspend fun refresh(): Result<Unit> = refreshLock.withLock {
        _state.value = _state.value.copy(loading = true, error = null)

        val result = runCatching {
            coroutineScope {
                val feedJob = async(Dispatchers.IO) { runCatching { feedApi.ipos() }.getOrNull() }
                val nseJob = async(Dispatchers.IO) { fetchNse() }
                feedJob.await() to nseJob.await()
            }
        }

        val pair = result.getOrNull()
        val feed = pair?.first
        val nse = pair?.second ?: emptyList()
        val feedIpos = feed?.ipos?.mapNotNull { it.toDomain() }.orEmpty()

        if (feedIpos.isEmpty() && nse.isEmpty()) {
            val message = result.exceptionOrNull()?.readableMessage()
                ?: "Data source reachable nahi hai. Internet check karein."
            _state.value = _state.value.copy(loading = false, error = message)
            return@withLock Result.failure(IllegalStateException(message))
        }

        val merged = merge(feedIpos, nse)
        _state.value = IpoFeedState(
            ipos = merged,
            loading = false,
            error = null,
            updatedAt = System.currentTimeMillis(),
            source = DataSource.NETWORK
        )

        if (feed != null && feedIpos.isNotEmpty()) {
            runCatching { prefs.saveFeedSnapshot(json.encodeToString(FeedEnvelope.serializer(), feed)) }
        }
        Result.success(Unit)
    }

    private suspend fun fetchNse(): List<Ipo> = withContext(Dispatchers.IO) {
        val current = runCatching { nseApi.currentIssues().nseToIpos(IpoStatus.OPEN) }
            .getOrDefault(emptyList())
        val upcoming = runCatching { nseApi.upcoming().nseToIpos(IpoStatus.UPCOMING) }
            .getOrDefault(emptyList())
        val past = runCatching { nseApi.pastIssues().nseToIpos() }
            .getOrDefault(emptyList())
            .recentlyClosed()

        val live = (current + upcoming).distinctBy { it.id }
        val all = (live + past).distinctBy { it.id }

        // Detail requests go to live issues *and* the most recent closed ones: that
        // endpoint is where the bid lot, category-wise subscription and — the reason
        // closed issues matter — the registrar name come from. Without the registrar
        // there is nothing to check allotment against.
        val symbols = (live + past.take(RECENT_CLOSED_TO_ENRICH))
            .mapNotNull { it.symbol.ifBlank { null } }
            .distinct()

        enrichWithDetails(all, symbolsToEnrich = symbols)
    }

    /**
     * public-past-issues returns every issue NSE has ever listed (1400+). Only the
     * last couple of months belong in the Closed / Listed tabs.
     */
    private fun List<Ipo>.recentlyClosed(): List<Ipo> {
        val cutoff = LocalDate.now().minusDays(PAST_ISSUE_WINDOW_DAYS)
        return asSequence()
            .filter { ipo ->
                val reference = ipo.listingDate ?: ipo.closeDate ?: return@filter false
                !reference.isBefore(cutoff)
            }
            .sortedByDescending { it.listingDate ?: it.closeDate }
            .take(MAX_PAST_ISSUES)
            .toList()
    }

    private suspend fun enrichWithDetails(
        ipos: List<Ipo>,
        symbolsToEnrich: List<String>
    ): List<Ipo> = coroutineScope {
        if (symbolsToEnrich.isEmpty()) return@coroutineScope ipos

        val wanted = symbolsToEnrich.take(MAX_DETAIL_REQUESTS).toSet()
        val details = wanted
            .map { symbol ->
                async(Dispatchers.IO) {
                    symbol to runCatching { nseApi.ipoDetail(symbol).nseToDetail() }.getOrNull()
                }
            }
            .awaitAll()
            .mapNotNull { (symbol, detail) ->
                if (detail == null || detail.isEmpty) null else symbol to detail
            }
            .toMap()

        if (details.isEmpty()) return@coroutineScope ipos

        ipos.map { ipo ->
            val detail = details[ipo.symbol] ?: return@map ipo
            ipo.copy(
                lotSize = ipo.lotSize ?: detail.lotSize,
                priceMin = ipo.priceMin ?: detail.priceMin,
                priceMax = ipo.priceMax ?: detail.priceMax,
                registrar = ipo.registrar ?: Registrar.match(detail.registrarName),
                registrarName = ipo.registrarName ?: detail.registrarName,
                subscription = mergeSubscription(ipo.subscription, detail.subscription)
            )
        }
    }

    /**
     * Feed wins on descriptive fields (it has GMP, registrar, lot size); NSE wins on
     * anything live (subscription times, and OPEN status once bidding actually starts).
     */
    private fun merge(feed: List<Ipo>, nse: List<Ipo>): List<Ipo> {
        if (nse.isEmpty()) return feed.sortedWith(ordering)
        if (feed.isEmpty()) return nse.sortedWith(ordering)

        val nseByKey = nse.associateBy { matchKey(it) }
        val used = mutableSetOf<String>()

        val enriched = feed.map { base ->
            val key = matchKey(base)
            val (matchedKey, live) = nseByKey[key]?.let { key to it }
                ?: prefixMatch(key, nseByKey)
                ?: return@map base
            used += matchedKey
            base.copy(
                status = if (live.status == IpoStatus.OPEN) IpoStatus.OPEN else base.status,
                priceMin = base.priceMin ?: live.priceMin,
                priceMax = base.priceMax ?: live.priceMax,
                lotSize = base.lotSize ?: live.lotSize,
                issueSizeCr = base.issueSizeCr ?: live.issueSizeCr,
                freshIssueCr = base.freshIssueCr ?: live.freshIssueCr,
                ofsCr = base.ofsCr ?: live.ofsCr,
                openDate = base.openDate ?: live.openDate,
                closeDate = base.closeDate ?: live.closeDate,
                listingDate = base.listingDate ?: live.listingDate,
                exchange = base.exchange ?: live.exchange,
                symbol = base.symbol.ifBlank { live.symbol },
                // NSE's detail endpoint is usually the only place the registrar appears;
                // without carrying it across, allotment checking has nothing to work with.
                registrar = base.registrar ?: live.registrar,
                registrarName = base.registrarName ?: live.registrarName,
                subscription = mergeSubscription(base.subscription, live.subscription)
            )
        }

        val extras = nse.filter { matchKey(it) !in used }
        return (enriched + extras).sortedWith(ordering)
    }

    private fun mergeSubscription(base: Subscription?, live: Subscription?): Subscription? {
        if (live == null) return base
        if (base == null) return live
        return base.copy(
            qib = live.qib ?: base.qib,
            nii = live.nii ?: base.nii,
            niiSmall = live.niiSmall ?: base.niiSmall,
            niiBig = live.niiBig ?: base.niiBig,
            retail = live.retail ?: base.retail,
            employee = live.employee ?: base.employee,
            total = live.total ?: base.total,
            updatedAt = live.updatedAt ?: base.updatedAt
        )
    }

    /**
     * Join on the normalised company name, not the symbol.
     *
     * Symbols look like the obvious key, but only NSE issues have one — BSE SME rows
     * arrive from the GMP feed with none. Keying on the symbol therefore let the same
     * company through twice, once per source. The name, with its suffixes stripped,
     * is the one field both sources always carry.
     */
    /**
     * The two sources spell long names differently — NSE carries the full legal name
     * ("Rays of Belief Limited- For Profit Social Enterprise (FPSE)") while the GMP
     * report uses the short one ("Rays of Belief"). Exact keys miss those, so fall
     * back to a prefix match once the shared head is long enough to be unambiguous.
     */
    private fun prefixMatch(key: String, candidates: Map<String, Ipo>): Pair<String, Ipo>? {
        if (key.length < MIN_PREFIX) return null
        return candidates.entries.firstOrNull { (other, _) ->
            other.length >= MIN_PREFIX &&
                (other.startsWith(key.take(MIN_PREFIX)) || key.startsWith(other.take(MIN_PREFIX)))
        }?.let { it.key to it.value }
    }

    private fun matchKey(ipo: Ipo): String {
        val byName = ipo.name
            .lowercase()
            .replace(Regex("\\b(limited|ltd|private|pvt|india|ipo|sme|the)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")
        return byName.takeIf { it.length >= 4 } ?: ipo.symbol.lowercase()
    }

    private fun parseEnvelope(raw: String): List<Ipo> = runCatching {
        json.decodeFromString(FeedEnvelope.serializer(), raw).ipos.mapNotNull { it.toDomain() }
    }.getOrDefault(emptyList())

    private fun readBundled(): List<Ipo> = runCatching {
        context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
    }.mapCatching { parseEnvelope(it) }.getOrDefault(emptyList())

    private fun Throwable.readableMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Internet connection nahi mil raha."
        is java.net.SocketTimeoutException -> "Server timeout ho gaya. Dobara try karein."
        else -> message ?: "Kuch galat ho gaya."
    }

    private companion object {
        const val BUNDLED_ASSET = "ipos_sample.json"
        const val PAST_ISSUE_WINDOW_DAYS = 75L
        const val MAX_PAST_ISSUES = 40
        const val MAX_DETAIL_REQUESTS = 18
        /** Shared head long enough that two different companies will not collide. */
        const val MIN_PREFIX = 12
        const val RECENT_CLOSED_TO_ENRICH = 8

        /** Open first, then upcoming by nearest open date, then most recent closed/listed. */
        val ordering: Comparator<Ipo> = compareBy(
            { rankOf(it.status) },
            { sortDate(it) },
            { it.name }
        )

        fun rankOf(status: IpoStatus): Int = when (status) {
            IpoStatus.OPEN -> 0
            IpoStatus.UPCOMING -> 1
            IpoStatus.CLOSED -> 2
            IpoStatus.LISTED -> 3
        }

        fun sortDate(ipo: Ipo): Long {
            val day = ipo.openDate?.toEpochDay() ?: return Long.MAX_VALUE
            // Upcoming: soonest first. Everything else: most recent first.
            return if (ipo.status == IpoStatus.UPCOMING) day else -day
        }
    }
}
