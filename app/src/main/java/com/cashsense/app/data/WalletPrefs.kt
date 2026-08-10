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

    suspend fun setOnboarded(value: Boolean)
    suspend fun setAutoApplyDetected(value: Boolean)
    suspend fun setLastSeenBalancePaise(value: Long)
}

class WalletPrefs(private val context: Context) : WalletPreferences {

    private val onboardedKey = booleanPreferencesKey("has_onboarded")
    private val autoApplyKey = booleanPreferencesKey("auto_apply_detected")
    private val lastSeenBalanceKey = longPreferencesKey("last_seen_balance_paise")

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
}
