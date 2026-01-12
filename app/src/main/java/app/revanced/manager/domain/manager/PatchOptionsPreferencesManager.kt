package app.revanced.manager.domain.manager

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.revanced.manager.domain.manager.base.BasePreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serializable data classes for storing patch options
 */
@Serializable
data class PatchOption(
    val key: String,
    val value: String // JSON serialized value
)

@Serializable
data class PatchOptionsData(
    val patchName: String,
    val options: List<PatchOption>
)

@Serializable
data class BundleOptionsData(
    val bundleUid: Int,
    val packageName: String,
    val patches: List<PatchOptionsData>
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun bundleOptionsKey(bundleUid: Int, packageName: String): androidx.datastore.preferences.core.Preferences.Key<String> {
    return stringPreferencesKey("bundle_options_${bundleUid}_${packageName}")
}

/**
 * Universal patch options manager
 * Dynamically stores and retrieves patch options for any bundle and package
 */
class PatchOptionsPreferencesManager(
    context: Context
) : BasePreferencesManager(context, "patch_options") {

    companion object {
        // Package identifiers
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

        @Volatile
        private var INSTANCE: PatchOptionsPreferencesManager? = null

        fun getInstance(context: Context): PatchOptionsPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PatchOptionsPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Get all saved options for a bundle
     * @param bundleUid The UID of the bundle
     * @param packageName The package name
     * @return Flow of map: patch name -> options map
     */
    fun getOptionsForBundle(
        bundleUid: Int,
        packageName: String
    ): Flow<Map<String, Map<String, Any?>>> {
        return dataStore.data.map { preferences ->
            val key = bundleOptionsKey(bundleUid, packageName)
            val jsonString = preferences[key] ?: return@map emptyMap()

            try {
                val bundleData = json.decodeFromString<BundleOptionsData>(jsonString)

                // Convert to map structure: patch name -> (option key -> value)
                bundleData.patches.associate { patchData ->
                    patchData.patchName to patchData.options.associate { option ->
                        option.key to deserializeValue(option.value)
                    }
                }
            } catch (e: Exception) {
                Log.e("PatchOptionsPrefs", "Failed to parse bundle options", e)
                emptyMap()
            }
        }
    }

    /**
     * Save options for a specific patch in a bundle
     * @param bundleUid The UID of the bundle
     * @param patchName The name of the patch
     * @param options The options to save (key -> value)
     * @param packageName The package name
     */
    suspend fun saveOptionsForBundle(
        bundleUid: Int,
        patchName: String,
        options: Map<String, Any?>,
        packageName: String
    ) {
        dataStore.edit { preferences ->
            val key = bundleOptionsKey(bundleUid, packageName)

            // Get existing data
            val existingJson = preferences[key]
            val existingData = existingJson?.let {
                try {
                    json.decodeFromString<BundleOptionsData>(it)
                } catch (e: Exception) {
                    null
                }
            } ?: BundleOptionsData(bundleUid, packageName, emptyList())

            // Update or add patch options
            val updatedPatches = existingData.patches.toMutableList()
            val existingPatchIndex = updatedPatches.indexOfFirst { it.patchName == patchName }

            val patchOptions = options.map { (k, v) ->
                PatchOption(k, serializeValue(v))
            }

            val newPatchData = PatchOptionsData(patchName, patchOptions)

            if (existingPatchIndex >= 0) {
                updatedPatches[existingPatchIndex] = newPatchData
            } else {
                updatedPatches.add(newPatchData)
            }

            // Save back
            val updatedData = existingData.copy(patches = updatedPatches)
            preferences[key] = json.encodeToString(updatedData)
        }
    }

    /**
     * Reset options for a specific patch in a bundle
     * @param bundleUid The UID of the bundle
     * @param patchName The name of the patch
     * @param packageName The package name
     */
    suspend fun resetOptionsForBundle(
        bundleUid: Int,
        patchName: String,
        packageName: String
    ) {
        dataStore.edit { preferences ->
            val key = bundleOptionsKey(bundleUid, packageName)
            val existingJson = preferences[key] ?: return@edit

            val existingData = try {
                json.decodeFromString<BundleOptionsData>(existingJson)
            } catch (e: Exception) {
                return@edit
            }

            val updatedPatches = existingData.patches.filter { it.patchName != patchName }

            if (updatedPatches.isEmpty()) {
                // Remove entire bundle key if no patches left
                preferences.remove(key)
            } else {
                val updatedData = existingData.copy(patches = updatedPatches)
                preferences[key] = json.encodeToString(updatedData)
            }
        }
    }

    /**
     * Get options for a specific patch in a bundle
     * @param bundleUid The UID of the bundle
     * @param patchName The name of the patch
     * @param packageName The package name
     * @return Flow of options map or null if not customized
     */
    fun getOptionsForPatchInBundle(
        bundleUid: Int,
        patchName: String,
        packageName: String
    ): Flow<Map<String, Any?>?> {
        return getOptionsForBundle(bundleUid, packageName).map { allOptions ->
            allOptions[patchName]
        }
    }

    /**
     * Clear all options for a bundle
     * @param bundleUid The UID of the bundle
     * @param packageName The package name
     */
    suspend fun clearOptionsForBundle(
        bundleUid: Int,
        packageName: String
    ) {
        dataStore.edit { preferences ->
            val key = bundleOptionsKey(bundleUid, packageName)
            preferences.remove(key)
        }
    }

    /**
     * Export all patch options for a specific package across all bundles
     * @param packageName The package name
     * @return Map of bundle UID -> (patch name -> options)
     */
    suspend fun exportAllOptionsForPackage(
        packageName: String
    ): Map<Int, Map<String, Map<String, Any?>>> {
        val allPreferences = dataStore.data.map { it }.first()
        val result = mutableMapOf<Int, Map<String, Map<String, Any?>>>()

        allPreferences.asMap().forEach { (key, value) ->
            // Check if this is a bundle options key for the specified package
            val keyString = key.name
            if (keyString.startsWith("bundle_options_") && keyString.endsWith("_$packageName")) {
                // Extract bundle UID from key
                val parts = keyString.removePrefix("bundle_options_").removeSuffix("_$packageName")
                val bundleUid = parts.toIntOrNull() ?: return@forEach

                try {
                    val bundleData = json.decodeFromString<BundleOptionsData>(value as String)
                    val patchOptions = bundleData.patches.associate { patchData ->
                        patchData.patchName to patchData.options.associate { option ->
                            option.key to deserializeValue(option.value)
                        }
                    }
                    result[bundleUid] = patchOptions
                } catch (e: Exception) {
                    Log.e("PatchOptionsPrefs", "Failed to parse bundle $bundleUid options", e)
                }
            }
        }

        return result
    }

    /**
     * Reset all patch options for all bundles
     */
    suspend fun resetAllOptions() {
        dataStore.edit { preferences ->
            val keysToRemove = preferences.asMap().keys.filter {
                it.name.startsWith("bundle_options_")
            }
            keysToRemove.forEach { preferences.remove(it) }
        }
    }

    /**
     * Serialize value to string for storage
     */
    private fun serializeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> "boolean:$value"
            is Int -> "int:$value"
            is Long -> "long:$value"
            is Float -> "float:$value"
            is Double -> "double:$value"
            is String -> "string:$value"
            else -> "string:${value.toString()}"
        }
    }

    /**
     * Deserialize value from string
     */
    private fun deserializeValue(serialized: String): Any? {
        if (serialized == "null") return null

        val parts = serialized.split(":", limit = 2)
        if (parts.size != 2) return serialized

        val (type, value) = parts

        return when (type) {
            "boolean" -> value.toBoolean()
            "int" -> value.toIntOrNull()
            "long" -> value.toLongOrNull()
            "float" -> value.toFloatOrNull()
            "double" -> value.toDoubleOrNull()
            "string" -> value
            else -> value
        }
    }
}
