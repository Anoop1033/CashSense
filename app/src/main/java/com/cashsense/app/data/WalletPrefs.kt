package com.cashsense.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cashsense_prefs")

class WalletPrefs(private val context: Context) {

    private val onboardedKey = booleanPreferencesKey("has_onboarded")
    private val autoApplyKey = booleanPreferencesKey("auto_apply_detected")

    val hasOnboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    /**
     * Whether a detected payment goes straight into the balance instead of waiting to be
     * confirmed. On by default: a balance that only updates once you tap "Confirm" is stale
     * exactly when you'd want to glance at it, which defeats the point of the wallet view.
     */
    val autoApplyDetected: Flow<Boolean> =
        context.dataStore.data.map { it[autoApplyKey] ?: true }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[onboardedKey] = value }
    }

    suspend fun setAutoApplyDetected(value: Boolean) {
        context.dataStore.edit { it[autoApplyKey] = value }
    }
}
