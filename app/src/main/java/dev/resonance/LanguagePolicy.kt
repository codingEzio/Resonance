package dev.resonance

import java.util.Locale

/** Shared by foreground copy and background notifications, independent of translated labels. */
internal fun resolveLanguage(selected: String, system: Locale): String =
    if (selected != "system") selected
    else if (system.language == "zh") {
        if (system.script == "Hant" || system.country in listOf("TW", "HK", "MO")) "zh-Hant"
        else "zh-Hans"
    } else "en"
