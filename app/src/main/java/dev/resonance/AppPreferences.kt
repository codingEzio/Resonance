package dev.resonance

import android.content.Context
import android.content.SharedPreferences

/** Thin storage adapter; existing keys/store and legacy defaults are preserved. */
internal class AppPreferences(private val storage: SharedPreferences) {
    operator fun get(setting: ToggleSetting): Boolean =
        try {
            storage.getBoolean(setting.key, setting.default)
        } catch (_: ClassCastException) {
            setting.default
        }

    operator fun get(setting: ChoiceSetting): String =
        setting.normalize(
            try {
                storage.getString(setting.key, setting.default)
            } catch (_: ClassCastException) {
                null
            }
        )

    operator fun get(setting: FloatSetting): Float =
        setting.normalize(
            try {
                storage.getFloat(setting.key, setting.default)
            } catch (_: ClassCastException) {
                setting.default
            }
        )

    fun set(setting: ToggleSetting, value: Boolean, synchronous: Boolean = false) {
        val editor = storage.edit().putBoolean(setting.key, value)
        if (synchronous) editor.commit() else editor.apply()
    }

    fun set(setting: ChoiceSetting, value: String) {
        storage.edit().putString(setting.key, setting.normalize(value)).apply()
    }

    fun set(setting: FloatSetting, value: Float) {
        storage.edit().putFloat(setting.key, setting.normalize(value)).apply()
    }

    /** Retain the existing v4 migration, which previously lived in the main Composable. */
    fun applyLegacyDefaults() {
        if (!get(AppSettings.PixelDefaultApplied)) {
            storage
                .edit()
                .putString(AppSettings.Font.key, AppSettings.Font.default)
                .putBoolean(AppSettings.PixelDefaultApplied.key, true)
                .apply()
        }
    }

    fun observe(changed: (String?) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> changed(key) }
        storage.registerOnSharedPreferenceChangeListener(listener)
        return { storage.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

internal fun Context.appPreferences(): AppPreferences =
    AppPreferences(getSharedPreferences(AppSettings.STORE, Context.MODE_PRIVATE))
