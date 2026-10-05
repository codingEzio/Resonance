package dev.resonance

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Compose observes typed settings; it does not define storage IDs/defaults or migrations. */
@Composable
internal fun rememberSetting(setting: ToggleSetting): State<Boolean> =
    rememberPreference(setting.key) { it[setting] }

@Composable
internal fun rememberSetting(setting: ChoiceSetting): State<String> =
    rememberPreference(setting.key) { it[setting] }

@Composable
internal fun rememberSetting(setting: FloatSetting): State<Float> =
    rememberPreference(setting.key) { it[setting] }

@Composable
private fun <T> rememberPreference(key: String, read: (AppPreferences) -> T): State<T> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.appPreferences() }
    val value = remember(preferences, key) { mutableStateOf(read(preferences)) }
    DisposableEffect(preferences, key) {
        val stop = preferences.observe { changed ->
            if (changed == key || changed == null) value.value = read(preferences)
        }
        onDispose { stop() }
    }
    return value
}
