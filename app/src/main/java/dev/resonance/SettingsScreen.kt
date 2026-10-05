package dev.resonance

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    copy: Copy,
    theme: String,
    font: String,
    language: String,
    navigate: (String) -> Unit,
    setTheme: (String) -> Unit,
    setFont: (String) -> Unit,
    setLanguage: (String) -> Unit,
) {
    var notices by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val preferences = remember(context) { context.appPreferences() }
    val materialYou by rememberSetting(AppSettings.MaterialYou)
    val motion = LocalMotionPolicy.current
    val volumeLimit by rememberSetting(AppSettings.VolumeLimit)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        ScreenHeading(copy["settings"])
        SectionLabel(refinementText(copy, "appearance"))
        Text(
            refinementText(copy, "theme"),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        val themes = AppSettings.Theme.values
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            themes.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = theme == value,
                    onClick = { setTheme(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(copy[value], maxLines = 1)
                }
            }
        }
        Text(refinementText(copy, "colors"), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            listOf(false, true).forEachIndexed { index, value ->
                SegmentedButton(
                    selected = materialYou == value,
                    onClick = { preferences.set(AppSettings.MaterialYou, value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        refinementText(copy, if (value) "material_colors" else "resonance_colors"),
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            refinementText(
                copy,
                if (materialYou && Build.VERSION.SDK_INT < 31) "color_older" else "color_hint",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            refinementText(copy, "language"),
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for ((value, label) in
                listOf(
                    "system" to refinementText(copy, "language_system"),
                    "en" to "EN",
                    "zh-Hant" to "繁中",
                    "zh-Hans" to "简中",
                )) {
                FilterChip(
                    selected = language == value,
                    onClick = { setLanguage(value) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier =
                        Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                            contentDescription =
                                when (value) {
                                    "en" -> "English"
                                    "zh-Hant" -> "繁體中文"
                                    "zh-Hans" -> "简体中文"
                                    else -> copy["system"]
                                }
                        },
                )
            }
        }
        var fontMenu by remember { mutableStateOf(false) }
        val fontChoices =
            listOf(
                Triple("pixel", refinementText(copy, "font_pixel"), "Geist Pixel + Doto"),
                Triple("nothing", refinementText(copy, "font_clean"), "Geist + Doto"),
                Triple("compact", refinementText(copy, "font_compact"), "DeparturePixelZh Compact"),
                Triple("doto", refinementText(copy, "font_dots"), "Doto Sans"),
                Triple("roboto", "Roboto", "Roboto Sans"),
                Triple("system", copy["system_font"], copy["system"]),
            )
        Box {
            SettingsLink(
                copy["font"],
                fontChoices.firstOrNull { it.first == font }?.second ?: font,
            ) {
                fontMenu = true
            }
            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                for ((value, label, detail) in fontChoices) DropdownMenuItem(
                    text = {
                        Column {
                            Text(label)
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { RadioButton(font == value, onClick = null) },
                    onClick = {
                        setFont(value)
                        fontMenu = false
                    },
                )
            }
        }
        SettingsDivider()
        SectionLabel(refinementText(copy, "playback"))
        SettingsLink(
            copy["volume"],
            "${refinementText(copy, "volume_summary")} · ${percentLabel(copy, volumeLimit)}",
        ) {
            navigate("volume")
        }
        SettingsLink(
            copy["motion"],
            refinementText(
                copy,
                when {
                    !motion.enabled -> "animation_off"
                    !motion.respectSystem -> "animation_forced"
                    else -> "animation_on"
                },
            ),
        ) {
            navigate("motion")
        }
        SettingsDivider()
        SectionLabel(libraryPreferenceText(copy, "library"))
        for ((setting, label) in
            listOf(
                AppSettings.LibrarySearch to "search",
                AppSettings.LibraryDuration to "duration",
                AppSettings.LibraryLegacy to "legacy",
            )) {
            val enabled by rememberSetting(setting)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                    preferences.set(setting, !enabled)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = 12.dp)) {
                    Text(
                        libraryPreferenceText(copy, label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        refinementText(copy, "${label}_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { preferences.set(setting, it) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = libraryPreferenceText(copy, label)
                        },
                )
            }
        }
        SettingsDivider()
        SectionLabel(refinementText(copy, "connections"))
        for ((destination, summary) in
            listOf(
                "mac" to "mac_summary",
                "downloads" to "downloads_summary",
                "permissions" to "permissions_summary",
            )) SettingsLink(copy[destination], refinementText(copy, summary)) {
            navigate(destination)
        }
        SettingsDivider()
        SectionLabel(refinementText(copy, "more"))
        SettingsLink(copy["features"], refinementText(copy, "features_summary")) {
            navigate("features")
        }
        Text(
            copy["about"],
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = {
                val paths =
                    listOf(
                        "licenses/geist/OFL.txt",
                        "licenses/geist-pixel/OFL.txt",
                        "licenses/doto/OFL.txt",
                        "licenses/roboto/OFL-Roboto.txt",
                        "licenses/roboto/OFL-RobotoMono.txt",
                        "licenses/roboto/OFL-RobotoCondensed.txt",
                        "licenses/compact/NOTICE.md",
                        "licenses/compact/OFL.txt",
                    )
                notices =
                    paths.joinToString("\n\n") { name ->
                        name +
                            "\n" +
                            context.assets.open(name).bufferedReader().use { it.readText() }
                    }
            }
        ) {
            Text(copy["licenses"])
        }
        Spacer(Modifier.height(24.dp))
    }
    notices?.let { text ->
        AlertDialog(
            onDismissRequest = { notices = null },
            title = { Text(copy["licenses"]) },
            text = {
                Text(
                    text,
                    Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { notices = null }) { Text(copy["close"]) } },
        )
    }
}

@Composable
private fun SettingsLink(title: String, summary: String, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp, end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(vertical = 16.dp))
}
