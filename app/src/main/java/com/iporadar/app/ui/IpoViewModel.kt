package com.iporadar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iporadar.app.data.local.AppPrefs
import com.iporadar.app.data.local.DarkMode
import com.iporadar.app.data.local.NotificationField
import com.iporadar.app.data.local.NotificationSettings
import com.iporadar.app.data.model.AllotmentResult
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoBoard
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.data.model.PanCheck
import com.iporadar.app.data.model.PanEntry
import com.iporadar.app.data.model.Relationship
import com.iporadar.app.data.repo.AllotmentRepository
import com.iporadar.app.data.repo.CheckableCompany
import com.iporadar.app.data.repo.DataSource
import com.iporadar.app.data.repo.IpoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class HomeTab { ACTIVE, UPCOMING, CLOSED, LISTED;
    val label: String get() = when (this) {
        ACTIVE -> "Active"
        UPCOMING -> "Upcoming"
        CLOSED -> "Closed"
        LISTED -> "Listed"
    }
}

enum class BoardFilter { ALL, MAINBOARD, SME;
    val label: String get() = when (this) {
        ALL -> "All"
        MAINBOARD -> "Mainboard"
        SME -> "SME"
    }
}

data class IpoUiState(
    val ipos: List<Ipo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0L,
    val source: DataSource = DataSource.NONE,
    val watchlist: Set<String> = emptySet(),
    val query: String = "",
    val boardFilter: BoardFilter = BoardFilter.ALL,
    val watchlistOnly: Boolean = false
) {
    /** Feed after search + board + watchlist filters, before the status tab split. */
    val filtered: List<Ipo>
        get() {
            val q = query.trim().lowercase(Locale.ENGLISH)
            return ipos.filter { ipo ->
                val boardOk = when (boardFilter) {
                    BoardFilter.ALL -> true
                    BoardFilter.MAINBOARD -> ipo.board == IpoBoard.MAINBOARD
                    BoardFilter.SME -> ipo.board == IpoBoard.SME
                }
                val watchOk = !watchlistOnly || ipo.id in watchlist
                val queryOk = q.isEmpty() ||
                    ipo.name.lowercase(Locale.ENGLISH).contains(q) ||
                    ipo.symbol.lowercase(Locale.ENGLISH).contains(q)
                boardOk && watchOk && queryOk
            }
        }

    fun forStatus(status: IpoStatus): List<Ipo> = filtered.filter { it.status == status }

    /**
     * Tab contents. "Active" deliberately keeps a just-closed IPO in place until it
     * lists — that whole window (allotment, then listing) is when people check most.
     */
    fun forTab(tab: HomeTab): List<Ipo> = when (tab) {
        HomeTab.ACTIVE -> filtered.filter { it.isActive() }
            .sortedBy { it.stage().ordinal }
        HomeTab.UPCOMING -> filtered.filter { it.status == IpoStatus.UPCOMING }
        HomeTab.CLOSED -> filtered.filter { it.status == IpoStatus.CLOSED }
        HomeTab.LISTED -> filtered.filter { it.status == IpoStatus.LISTED }
    }

}

class IpoViewModel(
    private val repo: IpoRepository,
    private val prefs: AppPrefs,
    private val allotment: AllotmentRepository
) : ViewModel() {

    private val pansFlow = prefs.pans

    private val query = MutableStateFlow("")
    private val boardFilter = MutableStateFlow(BoardFilter.ALL)
    private val watchlistOnly = MutableStateFlow(false)

    val state: StateFlow<IpoUiState> = combine(
        repo.state,
        prefs.watchlist,
        query,
        boardFilter,
        watchlistOnly
    ) { feed, watchlist, q, board, onlyWatched ->
        IpoUiState(
            ipos = feed.ipos,
            loading = feed.loading,
            error = feed.error,
            updatedAt = feed.updatedAt,
            source = feed.source,
            watchlist = watchlist,
            query = q,
            boardFilter = board,
            watchlistOnly = onlyWatched
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IpoUiState())

    val notificationSettings: StateFlow<NotificationSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettings())

    val darkMode: StateFlow<DarkMode> = prefs.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DarkMode.SYSTEM)

    val pans: StateFlow<List<PanEntry>> = pansFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /* ---------------------------------------------------------- allotment checking */

    private val _selectedAllotmentIpo = MutableStateFlow<String?>(null)
    val selectedAllotmentIpo: StateFlow<String?> = _selectedAllotmentIpo.asStateFlow()

    private val _checks = MutableStateFlow<Map<String, AllotmentResult>>(emptyMap())

    /** Saved PANs paired with their result for the currently selected IPO. */
    val panChecks: StateFlow<List<PanCheck>> = combine(pansFlow, _checks) { pans, results ->
        pans.map { PanCheck(it, results[it.id] ?: AllotmentResult.Idle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _checkSupported = MutableStateFlow(false)
    val checkSupported: StateFlow<Boolean> = _checkSupported.asStateFlow()

    /** Companies the supported registrars hold, for IPOs our own list never saw. */
    private val _registrarCompanies = MutableStateFlow<List<CheckableCompany>>(emptyList())
    val registrarCompanies: StateFlow<List<CheckableCompany>> = _registrarCompanies.asStateFlow()

    private val _selectedCompany = MutableStateFlow<CheckableCompany?>(null)
    val selectedCompany: StateFlow<CheckableCompany?> = _selectedCompany.asStateFlow()

    fun loadRegistrarCompanies() {
        if (_registrarCompanies.value.isNotEmpty()) return
        viewModelScope.launch {
            _registrarCompanies.value = allotment.registrarCatalogue()
        }
    }

    fun selectCompany(company: CheckableCompany?) {
        _selectedCompany.value = company
        _selectedAllotmentIpo.value = null
        _checks.value = emptyMap()
        _checkSupported.value = company != null
    }

    fun selectAllotmentIpo(ipoId: String?) {
        if (_selectedAllotmentIpo.value == ipoId) return
        _selectedCompany.value = null
        _selectedAllotmentIpo.value = ipoId
        // Results belong to one IPO; switching clears them.
        _checks.value = emptyMap()
        _checkSupported.value = false
        val ipo = ipoId?.let { repo.byId(it) } ?: return
        viewModelScope.launch {
            _checkSupported.value = allotment.supports(ipo)
        }
    }

    private suspend fun runCheck(entry: PanEntry): AllotmentResult {
        _selectedCompany.value?.let { return allotment.checkDirect(it, entry) }
        val ipo = _selectedAllotmentIpo.value?.let { repo.byId(it) }
            ?: return AllotmentResult.NotSupported
        return allotment.check(ipo, entry)
    }

    fun checkAll() {
        if (_selectedCompany.value == null && _selectedAllotmentIpo.value == null) return
        viewModelScope.launch {
            val pans = prefs.pans.first()
            if (pans.isEmpty()) {
                _snackbar.value = "Pehle koi PAN add karein"
                return@launch
            }
            _checks.value = pans.associate { it.id to AllotmentResult.Checking }
            // Sequential on purpose — this is someone else's server, not ours to flood.
            for (entry in pans) {
                _checks.value = _checks.value + (entry.id to runCheck(entry))
            }
        }
    }

    fun checkOne(entry: PanEntry) {
        if (_selectedCompany.value == null && _selectedAllotmentIpo.value == null) return
        viewModelScope.launch {
            _checks.value = _checks.value + (entry.id to AllotmentResult.Checking)
            _checks.value = _checks.value + (entry.id to runCheck(entry))
        }
    }

    init {
        viewModelScope.launch {
            repo.warmStart()
            repo.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { repo.refresh() }
    }

    fun setQuery(value: String) { query.value = value }
    fun setBoardFilter(value: BoardFilter) { boardFilter.value = value }
    fun toggleWatchlistOnly() { watchlistOnly.value = !watchlistOnly.value }

    fun toggleWatch(ipo: Ipo) {
        viewModelScope.launch {
            val added = prefs.toggleWatch(ipo.id)
            _snackbar.value = if (added) {
                "${ipo.name} watchlist me add ho gaya"
            } else {
                "${ipo.name} watchlist se hata diya"
            }
        }
    }

    fun consumeSnackbar() { _snackbar.value = null }

    fun ipoById(id: String): Ipo? = repo.byId(id)

    fun setNotification(field: NotificationField, value: Boolean) {
        viewModelScope.launch { prefs.setSetting(field, value) }
    }

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch { prefs.setDarkMode(mode) }
    }

    fun addPan(pan: String, holderName: String, relationship: Relationship) {
        val normalised = PanEntry.normalise(pan)
        if (!PanEntry.PAN_PATTERN.matches(normalised)) {
            _snackbar.value = "PAN format galat hai — ABCDE1234F jaisa hona chahiye"
            return
        }
        viewModelScope.launch {
            val duplicate = prefs.pans.first().any { it.pan == normalised }
            if (duplicate) {
                _snackbar.value = "Ye PAN pehle se saved hai"
                return@launch
            }
            prefs.addPan(normalised, holderName, relationship)
            _snackbar.value = "PAN save ho gaya"
        }
    }

    fun removePan(entry: PanEntry) {
        viewModelScope.launch {
            prefs.removePan(entry.id)
            _snackbar.value = "${entry.displayName} ka PAN hata diya"
        }
    }

    class Factory(
        private val repo: IpoRepository,
        private val prefs: AppPrefs,
        private val allotment: AllotmentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            IpoViewModel(repo, prefs, allotment) as T
    }
}
