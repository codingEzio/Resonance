package dev.resonance

/** Stable IDs/defaults are policy; Android persistence and UI observation are separate adapters. */
internal data class ToggleSetting(val key: String, val default: Boolean)

internal data class ChoiceSetting(val key: String, val default: String, val values: List<String>) {
    fun normalize(value: String?): String = value?.takeIf { it in values } ?: default
}

internal data class FloatSetting(
    val key: String,
    val default: Float,
    val min: Float,
    val max: Float,
) {
    fun normalize(value: Float): Float = if (value.isFinite()) value.coerceIn(min, max) else default
}

internal object AppSettings {
    const val STORE = "settings"
    val Theme = ChoiceSetting("theme", "system", listOf("system", "light", "dark"))
    val Font =
        ChoiceSetting(
            "font",
            "pixel",
            listOf("pixel", "nothing", "compact", "doto", "roboto", "system"),
        )
    val Language = ChoiceSetting("language", "system", listOf("system", "en", "zh-Hant", "zh-Hans"))
    val MaterialYou = ToggleSetting("material_you", false)
    val Motion = ToggleSetting("motion", true)
    val RespectSystemMotion = ToggleSetting("respect_system_motion", true)
    val FullScreen = ToggleSetting("full_screen", false)
    val LibrarySearch = ToggleSetting("library_search", true)
    val LibraryDuration = ToggleSetting("library_duration", false)
    val LibraryLegacy = ToggleSetting("library_legacy", false)
    val SystemVolumeGuard = ToggleSetting("system_volume_guard", false)
    val VolumeLimit =
        FloatSetting("volume_limit", VolumePolicy.DEFAULT_GAIN, 0f, VolumePolicy.MAX_GAIN)

    // Existing one-time state stays under its original keys; these are not user choices.
    val PixelDefaultApplied = ToggleSetting("pixel_default_v4", false)
    val DownloadNotificationsAsked = ToggleSetting("asked_download_notifications", false)
    val FolderCatalogApplied = ToggleSetting("folder_catalog_v3", false)
}
