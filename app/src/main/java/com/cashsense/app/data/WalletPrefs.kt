package com.cashsense.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cashsense_prefs")

/**
 * The settings the wallet depends on, behind an interface so the repository can be exercised in
 * plain JVM tests — the DataStore-backed implementation needs a Context, which unit tests have no
 * way to supply.
 */
interface WalletPreferences {
    val hasOnboarded: Flow<Boolean>
    val autoApplyDetected: Flow<Boolean>

    /**
     * The balance as of the last time the wallet was actually on screen, or null if it has never
     * been shown. Kept so that money which arrives while the app is closed can still be played
     * out as notes moving when the app is next opened, rather than the total having quietly
     * changed behind the user's back.
     */
    val lastSeenBalancePaise: Flow<Long?>

    /**
     * When Android last took the notification listener away, or null while it is bound.
     *
     * Android unbinds the listener whenever it puts the app to sleep, which aggressive vendor
     * battery managers do freely. Nothing arrives while that lasts: no notification, no parse
     * failure, no trace of any kind. Payments made in that window are simply never seen, which is
     * why they were impossible to account for after the fact. Remembering when it started is what
     * lets the app say so.
     */
    val listenerDisconnectedAt: Flow<Long?>

    /** The span of the last period detection was off, or null if there has not been one. */
    val detectionGapStart: Flow<Long?>
    val detectionGapEnd: Flow<Long?>

    suspend fun setOnboarded(value: Boolean)
    suspend fun setAutoApplyDetected(value: Boolean)
    suspend fun setLastSeenBalancePaise(value: Long)
    suspend fun setListenerDisconnectedAt(value: Long?)
    suspend fun setDetectionGap(startMillis: Long, endMillis: Long)
    suspend fun clearDetectionGap()
}

class WalletPrefs(private val context: Context) : WalletPreferences {

    private val onboardedKey = booleanPreferencesKey("has_onboarded")
    private val autoApplyKey = booleanPreferencesKey("auto_apply_detected")
    private val lastSeenBalanceKey = longPreferencesKey("last_seen_balance_paise")
    private val listenerDisconnectedAtKey = longPreferencesKey("listener_disconnected_at")
    private val detectionGapStartKey = longPreferencesKey("detection_gap_start")
    private val detectionGapEndKey = longPreferencesKey("detection_gap_end")

    override val hasOnboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    /**
     * Whether a detected payment goes straight into the balance instead of waiting to be
     * confirmed. On by default: a balance that only updates once you tap "Confirm" is stale
     * exactly when you'd want to glance at it, which defeats the point of the wallet view.
     */
    override val autoApplyDetected: Flow<Boolean> =
        context.dataStore.data.map { it[autoApplyKey] ?: true }

    override suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[onboardedKey] = value }
    }

    override val lastSeenBalancePaise: Flow<Long?> =
        context.dataStore.data.map { it[lastSeenBalanceKey] }

    override suspend fun setAutoApplyDetected(value: Boolean) {
        context.dataStore.edit { it[autoApplyKey] = value }
    }

    override suspend fun setLastSeenBalancePaise(value: Long) {
        context.dataStore.edit { it[lastSeenBalanceKey] = value }
    }

    override val listenerDisconnectedAt: Flow<Long?> =
        context.dataStore.data.map { it[listenerDisconnectedAtKey] }

    override val detectionGapStart: Flow<Long?> =
        context.dataStore.data.map { it[detectionGapStartKey] }

    override val detectionGapEnd: Flow<Long?> =
        context.dataStore.data.map { it[detectionGapEndKey] }

    override suspend fun setListenerDisconnectedAt(value: Long?) {
        context.dataStore.edit {
            if (value == null) it.remove(listenerDisconnectedAtKey) else it[listenerDisconnectedAtKey] = value
        }
    }

    override suspend fun setDetectionGap(startMillis: Long, endMillis: Long) {
        context.dataStore.edit {
            it[detectionGapStartKey] = startMillis
            it[detectionGapEndKey] = endMillis
        }
    }

    override suspend fun clearDetectionGap() {
        context.dataStore.edit {
            it.remove(detectionGapStartKey)
            it.remove(detectionGapEndKey)
        }
    }
}
