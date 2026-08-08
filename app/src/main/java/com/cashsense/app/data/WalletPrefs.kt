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

    val hasOnboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[onboardedKey] = value }
    }
}
