package com.example.anonymousmeetup.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val NICKNAME = stringPreferencesKey("nickname")
        val IS_AUTHORIZED = booleanPreferencesKey("is_authorized")
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val PRIVATE_KEY = stringPreferencesKey("private_key")
        val KEY_DATE = stringPreferencesKey("key_date")
        val KEYS_JSON = stringPreferencesKey("keys_json")
        val ENABLED_GROUPS = stringSetPreferencesKey("enabled_groups")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SECURE_BACKUP_ENABLED = booleanPreferencesKey("secure_backup_enabled")
    }

    val nickname: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NICKNAME]
    }

    val isAuthorized: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_AUTHORIZED] ?: false
    }

    val publicKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PUBLIC_KEY]
    }

    val privateKey: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PRIVATE_KEY]
    }

    val keyDate: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEY_DATE]
    }

    val enabledGroups: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
    }

    val isLocationTrackingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
    }

    val secureBackupEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SECURE_BACKUP_ENABLED] ?: false
    }

    suspend fun getNickname(): String? {
        val nickname = nickname.first()
        Log.d("UserPreferences", "РџРѕР»СѓС‡РµРЅ РЅРёРєРЅРµР№Рј: $nickname")
        return nickname
    }

    suspend fun isUserAuthorized(): Boolean {
        val isAuthorized = isAuthorized.first()
        Log.d("UserPreferences", "РџСЂРѕРІРµСЂРєР° Р°РІС‚РѕСЂРёР·Р°С†РёРё: $isAuthorized")
        return isAuthorized
    }

    suspend fun saveNickname(nickname: String) {
        Log.d("UserPreferences", "РЎРѕС…СЂР°РЅРµРЅРёРµ РЅРёРєРЅРµР№РјР°: $nickname")
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NICKNAME] = nickname
            preferences[PreferencesKeys.IS_AUTHORIZED] = true
        }
        Log.d("UserPreferences", "РќРёРєРЅРµР№Рј СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅРµРЅ")
    }

    suspend fun savePublicKey(publicKey: String) {
        Log.d("UserPreferences", "РЎРѕС…СЂР°РЅРµРЅРёРµ РїСѓР±Р»РёС‡РЅРѕРіРѕ РєР»СЋС‡Р°")
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = publicKey
        }
        Log.d("UserPreferences", "РџСѓР±Р»РёС‡РЅС‹Р№ РєР»СЋС‡ СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅРµРЅ")
    }

    suspend fun savePrivateKey(privateKey: String) {
        Log.d("UserPreferences", "РЎРѕС…СЂР°РЅРµРЅРёРµ РїСЂРёРІР°С‚РЅРѕРіРѕ РєР»СЋС‡Р°")
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVATE_KEY] = privateKey
        }
        Log.d("UserPreferences", "РџСЂРёРІР°С‚РЅС‹Р№ РєР»СЋС‡ СѓСЃРїРµС€РЅРѕ СЃРѕС…СЂР°РЅРµРЅ")
    }

    suspend fun getPublicKey(): String? {
        val publicKey = publicKey.first()
        Log.d("UserPreferences", "РџРѕР»СѓС‡РµРЅ РїСѓР±Р»РёС‡РЅС‹Р№ РєР»СЋС‡: ${publicKey?.take(10)}...")
        return publicKey
    }

    suspend fun getPrivateKey(): String? {
        val privateKey = privateKey.first()
        Log.d("UserPreferences", "РџРѕР»СѓС‡РµРЅ РїСЂРёРІР°С‚РЅС‹Р№ РєР»СЋС‡: ${privateKey?.take(10)}...")
        return privateKey
    }

    suspend fun getCurrentKeyDate(): String? {
        return keyDate.first()
    }

    suspend fun saveKeyPairForDate(date: String, publicKey: String, privateKey: String) {
        val updatedJson = updateKeysJson(date, publicKey, privateKey)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = publicKey
            preferences[PreferencesKeys.PRIVATE_KEY] = privateKey
            preferences[PreferencesKeys.KEY_DATE] = date
            preferences[PreferencesKeys.KEYS_JSON] = updatedJson.toString()
        }
    }

    suspend fun getKeyPairForDate(date: String): Pair<String, String>? {
        val keysJson = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first() ?: return null
        return try {
            val json = JSONObject(keysJson)
            val entry = json.optJSONObject(date) ?: return null
            val publicKey = entry.optString("publicKey", "")
            val privateKey = entry.optString("privateKey", "")
            if (publicKey.isBlank() || privateKey.isBlank()) null else Pair(publicKey, privateKey)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllKeyDates(): List<String> {
        val keysJson = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first() ?: return emptyList()
        return try {
            val json = JSONObject(keysJson)
            json.keys().asSequence().toList().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun updateKeysJson(date: String, publicKey: String, privateKey: String): JSONObject {
        val existing = dataStore.data.map { it[PreferencesKeys.KEYS_JSON] }.first()
        val json = if (existing.isNullOrBlank()) JSONObject() else JSONObject(existing)
        val entry = JSONObject().apply {
            put("publicKey", publicKey)
            put("privateKey", privateKey)
        }
        json.put(date, entry)

        val dates = json.keys().asSequence().toList().sorted()
        if (dates.size > 7) {
            val toRemove = dates.take(dates.size - 7)
            toRemove.forEach { json.remove(it) }
        }
        return json
    }

    suspend fun addEnabledGroup(groupId: String) {
        Log.d("UserPreferences", "Р”РѕР±Р°РІР»РµРЅРёРµ РіСЂСѓРїРїС‹ РІ СЃРїРёСЃРѕРє Р°РєС‚РёРІРЅС‹С…: $groupId")
        dataStore.edit { preferences ->
            val currentGroups = preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
            preferences[PreferencesKeys.ENABLED_GROUPS] = currentGroups + groupId
        }
        Log.d("UserPreferences", "Р“СЂСѓРїРїР° СѓСЃРїРµС€РЅРѕ РґРѕР±Р°РІР»РµРЅР° РІ СЃРїРёСЃРѕРє Р°РєС‚РёРІРЅС‹С…")
    }

    suspend fun removeEnabledGroup(groupId: String) {
        Log.d("UserPreferences", "РЈРґР°Р»РµРЅРёРµ РіСЂСѓРїРїС‹ РёР· СЃРїРёСЃРєР° Р°РєС‚РёРІРЅС‹С…: $groupId")
        dataStore.edit { preferences ->
            val currentGroups = preferences[PreferencesKeys.ENABLED_GROUPS] ?: emptySet()
            preferences[PreferencesKeys.ENABLED_GROUPS] = currentGroups - groupId
        }
        Log.d("UserPreferences", "Р“СЂСѓРїРїР° СѓСЃРїРµС€РЅРѕ СѓРґР°Р»РµРЅР° РёР· СЃРїРёСЃРєР° Р°РєС‚РёРІРЅС‹С…")
    }

    suspend fun getEnabledGroups(): Set<String> {
        val groups = enabledGroups.first()
        Log.d("UserPreferences", "РџРѕР»СѓС‡РµРЅС‹ РіСЂСѓРїРїС‹: $groups")
        return groups
    }

    suspend fun setLocationTrackingEnabled(enabled: Boolean) {
        Log.d("UserPreferences", "РЈСЃС‚Р°РЅРѕРІРєР° СЃС‚Р°С‚СѓСЃР° РѕС‚СЃР»РµР¶РёРІР°РЅРёСЏ РјРµСЃС‚РѕРїРѕР»РѕР¶РµРЅРёСЏ: $enabled")
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_TRACKING_ENABLED] = enabled
        }
        Log.d("UserPreferences", "РЎС‚Р°С‚СѓСЃ РѕС‚СЃР»РµР¶РёРІР°РЅРёСЏ РјРµСЃС‚РѕРїРѕР»РѕР¶РµРЅРёСЏ СѓСЃРїРµС€РЅРѕ РѕР±РЅРѕРІР»РµРЅ")
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun isNotificationsEnabled(): Boolean {
        return notificationsEnabled.first()
    }

    suspend fun setSecureBackupEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECURE_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun isSecureBackupEnabled(): Boolean {
        return secureBackupEnabled.first()
    }

    suspend fun clearUserData() {
        Log.d("UserPreferences", "РћС‡РёСЃС‚РєР° РґР°РЅРЅС‹С… РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ")
        dataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d("UserPreferences", "Р”Р°РЅРЅС‹Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ СѓСЃРїРµС€РЅРѕ РѕС‡РёС‰РµРЅС‹")
    }

    suspend fun clearNickname() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.NICKNAME)
        }
    }
}


