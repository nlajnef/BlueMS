/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.st.preferences.di.PreferencesScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StPreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @PreferencesScope private val coroutineScope: CoroutineScope
) : StPreferences {

    init {
        coroutineScope.launch { dataStore.data.first() } // prefetch prefs in datastore cache
    }

    override fun mustShowFwUpdate(nodeId: String, currentFw: String): Boolean {
        val fwUpdateKey = booleanPreferencesKey(String.format(FORMATTER_KEY, currentFw, nodeId))
        return runBlocking { dataStore.data.first()[fwUpdateKey]?.not() ?: true }
    }

    override fun doNotShowAgainFwUpdate(nodeId: String, currentFw: String) {
        coroutineScope.launch {
            val fwUpdateKey =
                booleanPreferencesKey(String.format(FORMATTER_KEY, currentFw, nodeId))
            dataStore.edit { prefs -> prefs[fwUpdateKey] = true }
        }
    }

    override fun termsPrefsUpdates(): Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[TERMS_KEY] ?: false
        }

    override fun hasAcceptedTerms(): Boolean {
        return runBlocking { dataStore.data.first()[TERMS_KEY] ?: false }
    }

    override fun getLevelProficiency(): String {
        return runBlocking { dataStore.data.first()[LEVEL_KEY] ?: "" }
    }

    override fun getProfileType(): String {
        return runBlocking { dataStore.data.first()[TYPE_KEY] ?: "" }
    }

    override fun setTermsFlag(accepted: Boolean) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[TERMS_KEY] = accepted }
        }
    }

    override fun setLevelProficiency(level: String) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[LEVEL_KEY] = level }
        }
    }

    override fun setProfileType(profile: String) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[TYPE_KEY] = profile }
        }
    }

    override fun welcomePrefsUpdates(): Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[WELCOME_KEY] ?: false
        }

    override fun mustShowWelcome(): Boolean {
        return runBlocking { dataStore.data.first()[WELCOME_KEY]?.not() ?: true }
    }

    override fun setWelcomeFlag(completed: Boolean) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[WELCOME_KEY] = completed }
        }
    }

    override fun setBetaApplicationFlag(enableBeta: Boolean) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[BETA_KEY] = enableBeta }
        }
    }

    override fun isBetaApplication(): Boolean {
        return runBlocking { dataStore.data.first()[BETA_KEY] ?: false }
    }

    override fun profilePrefsUpdates() = dataStore.data
        .map { preferences ->
            preferences[PROFILE_KEY] ?: false
        }

    override fun hasSelectedProfile(): Boolean {
        return runBlocking { dataStore.data.first()[PROFILE_KEY] ?: false }
    }

    override fun setProfileFlag(selected: Boolean) {
        coroutineScope.launch {
            dataStore.edit { prefs -> prefs[PROFILE_KEY] = selected }
        }
    }

    override fun getDemoOrder(nodeId: String): List<String> {
        val idKey = stringPreferencesKey(nodeId)
        return runBlocking { dataStore.data.first()[idKey]?.split(", ") ?: emptyList() }
    }

    override fun setDemoOrder(nodeId: String, demos: List<String>) {
        coroutineScope.launch {
            val idKey = stringPreferencesKey(nodeId)
            dataStore.edit { prefs -> prefs[idKey] = demos.joinToString() }
        }
    }

    override fun getFavouriteDevices(): Flow<List<String>> = dataStore.data
        .map { preferences ->
            preferences[FAVOURITE_DEVICES_KEY]?.split(", ") ?: emptyList()
        }

    override fun setFavouriteDevice(nodeId: String) {
        coroutineScope.launch {
            dataStore.edit { prefs ->
                val favourites = (dataStore.data.first()[FAVOURITE_DEVICES_KEY]?.split(", ")
                    ?: emptyList()) + listOf(nodeId)
                prefs[FAVOURITE_DEVICES_KEY] = favourites.joinToString()
            }
        }
    }

    override fun unsetFavouriteDevice(nodeId: String) {
        coroutineScope.launch {
            dataStore.edit { prefs ->
                val favourites =
                    (dataStore.data.first()[FAVOURITE_DEVICES_KEY]?.split(", ")
                        ?: emptyList()).filter { it != nodeId }
                prefs[FAVOURITE_DEVICES_KEY] = favourites.joinToString()
            }
        }
    }

    companion object {
        private const val FORMATTER_KEY = "propose_fw_update_for%s_%s"
        private val TERMS_KEY = booleanPreferencesKey("terms_key")
        private val FAVOURITE_DEVICES_KEY = stringPreferencesKey("favourite_devices_key")
        private val WELCOME_KEY = booleanPreferencesKey("welcome_key")
        private val PROFILE_KEY = booleanPreferencesKey("profile_key")
        private val LEVEL_KEY = stringPreferencesKey("level_key")
        private val TYPE_KEY = stringPreferencesKey("type_key")
        private val BETA_KEY = booleanPreferencesKey("beta_key")
    }
}
