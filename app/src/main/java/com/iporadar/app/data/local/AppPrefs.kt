package com.iporadar.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iporadar.app.data.model.PanEntry
import com.iporadar.app.data.model.Relationship
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("ipo_radar_prefs")

/** Watchlist, PAN vault, notification toggles and the offline snapshot of the last good feed. */
class AppPrefs(private val context: Context) {

    private val panJson = Json { ignoreUnknownKeys = true }
    private val panSerializer = ListSerializer(PanEntry.serializer())

    private object Keys {
        val WATCHLIST = stringSetPreferencesKey("watchlist")
        val CACHED_FEED = stringPreferencesKey("cached_feed")
        val CACHED_AT = longPreferencesKey("cached_at")
        val NOTIFY_OPEN = booleanPreferencesKey("notify_open")
        val NOTIFY_CLOSING = booleanPreferencesKey("notify_closing")
        val NOTIFY_ALLOTMENT = booleanPreferencesKey("notify_allotment")
        val NOTIFY_LISTING = booleanPreferencesKey("notify_listing")
        val NOTIFY_GMP = booleanPreferencesKey("notify_gmp")
        val WATCHLIST_ONLY = booleanPreferencesKey("notify_watchlist_only")
        val NOTIFIED_KEYS = stringSetPreferencesKey("notified_keys")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val SAVED_PAN = stringPreferencesKey("saved_pan")
        val PAN_VAULT = stringPreferencesKey("pan_vault")
    }

    val watchlist: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.WATCHLIST] ?: emptySet() }

    suspend fun toggleWatch(ipoId: String): Boolean {
        var nowWatched = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.WATCHLIST] ?: emptySet()
            nowWatched = ipoId !in current
            prefs[Keys.WATCHLIST] = if (nowWatched) current + ipoId else current - ipoId
        }
        return nowWatched
    }

    suspend fun watchlistNow(): Set<String> = watchlist.first()

    /* ---- offline snapshot ---- */

    suspend fun saveFeedSnapshot(json: String) {
        context.dataStore.edit {
            it[Keys.CACHED_FEED] = json
            it[Keys.CACHED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun feedSnapshot(): Pair<String, Long>? {
        val prefs = context.dataStore.data.first()
        val json = prefs[Keys.CACHED_FEED] ?: return null
        return json to (prefs[Keys.CACHED_AT] ?: 0L)
    }

    /* ---- notification settings ---- */

    val settings: Flow<NotificationSettings> = context.dataStore.data.map { p ->
        NotificationSettings(
            ipoOpen = p[Keys.NOTIFY_OPEN] ?: true,
            closingSoon = p[Keys.NOTIFY_CLOSING] ?: true,
            allotmentOut = p[Keys.NOTIFY_ALLOTMENT] ?: true,
            listingDay = p[Keys.NOTIFY_LISTING] ?: true,
            gmpMoves = p[Keys.NOTIFY_GMP] ?: false,
            watchlistOnly = p[Keys.WATCHLIST_ONLY] ?: false
        )
    }

    suspend fun settingsNow(): NotificationSettings = settings.first()

    suspend fun setSetting(field: NotificationField, value: Boolean) {
        val key = when (field) {
            NotificationField.IPO_OPEN -> Keys.NOTIFY_OPEN
            NotificationField.CLOSING_SOON -> Keys.NOTIFY_CLOSING
            NotificationField.ALLOTMENT_OUT -> Keys.NOTIFY_ALLOTMENT
            NotificationField.LISTING_DAY -> Keys.NOTIFY_LISTING
            NotificationField.GMP_MOVES -> Keys.NOTIFY_GMP
            NotificationField.WATCHLIST_ONLY -> Keys.WATCHLIST_ONLY
        }
        context.dataStore.edit { it[key] = value }
    }

    /** Dedupe store so the same alert never fires twice. */
    suspend fun alreadyNotified(key: String): Boolean =
        key in (context.dataStore.data.first()[Keys.NOTIFIED_KEYS] ?: emptySet())

    suspend fun markNotified(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NOTIFIED_KEYS] ?: emptySet()
            // Keep the ledger from growing without bound.
            val trimmed = if (current.size > 400) current.toList().takeLast(200).toSet() else current
            prefs[Keys.NOTIFIED_KEYS] = trimmed + key
        }
    }

    val darkMode: Flow<DarkMode> = context.dataStore.data.map { p ->
        runCatching { DarkMode.valueOf(p[Keys.DARK_MODE] ?: DarkMode.SYSTEM.name) }
            .getOrDefault(DarkMode.SYSTEM)
    }

    suspend fun setDarkMode(mode: DarkMode) {
        context.dataStore.edit { it[Keys.DARK_MODE] = mode.name }
    }

    /* ---- PAN vault ---- */

    /**
     * Registrar sites are captcha-gated, so allotment checking always ends in a browser.
     * Families apply from several accounts, so we keep a list rather than one PAN —
     * on this device only, never uploaded anywhere.
     */
    val pans: Flow<List<PanEntry>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PAN_VAULT]
        val stored = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { panJson.decodeFromString(panSerializer, raw) }.getOrDefault(emptyList())
        }
        // Migrate the single PAN saved by earlier versions into the vault view.
        val legacy = prefs[Keys.SAVED_PAN]
        if (stored.isEmpty() && !legacy.isNullOrBlank()) {
            listOf(PanEntry(id = "legacy", pan = legacy, holderName = "", relationship = Relationship.SELF))
        } else {
            stored
        }
    }

    suspend fun upsertPan(entry: PanEntry) {
        mutatePans { current ->
            val index = current.indexOfFirst { it.id == entry.id }
            if (index >= 0) current.toMutableList().also { it[index] = entry }
            else current + entry
        }
    }

    suspend fun addPan(pan: String, holderName: String, relationship: Relationship) {
        upsertPan(
            PanEntry(
                id = UUID.randomUUID().toString(),
                pan = PanEntry.normalise(pan),
                holderName = holderName.trim(),
                relationship = relationship
            )
        )
    }

    suspend fun removePan(id: String) {
        mutatePans { current -> current.filterNot { it.id == id } }
    }

    private suspend fun mutatePans(transform: (List<PanEntry>) -> List<PanEntry>) {
        context.dataStore.edit { prefs ->
            val raw = prefs[Keys.PAN_VAULT]
            val current = if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { panJson.decodeFromString(panSerializer, raw) }.getOrDefault(emptyList())
            }
            prefs[Keys.PAN_VAULT] = panJson.encodeToString(panSerializer, transform(current))
            // The legacy single-PAN key is superseded once the vault exists.
            prefs.remove(Keys.SAVED_PAN)
        }
    }
}

enum class DarkMode { SYSTEM, LIGHT, DARK }

enum class NotificationField {
    IPO_OPEN, CLOSING_SOON, ALLOTMENT_OUT, LISTING_DAY, GMP_MOVES, WATCHLIST_ONLY
}

data class NotificationSettings(
    val ipoOpen: Boolean = true,
    val closingSoon: Boolean = true,
    val allotmentOut: Boolean = true,
    val listingDay: Boolean = true,
    val gmpMoves: Boolean = false,
    val watchlistOnly: Boolean = false
)
