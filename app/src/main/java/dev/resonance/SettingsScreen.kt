package dev.resonance

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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: ResonanceApp,
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
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("") }
    val mirrorStatus by app.mirror.status.collectAsState()
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
        if (BuildConfig.PRESENTATION_PROFILE != "bundled") {
            var fontMenu by remember { mutableStateOf(false) }
            val fontChoices =
                listOf(
                    Triple("pixel", refinementText(copy, "font_pixel"), "Geist Pixel + Doto"),
                    Triple("nothing", refinementText(copy, "font_clean"), "Geist + Doto"),
                    Triple(
                        "compact",
                        refinementText(copy, "font_compact"),
                        "DeparturePixelZh Compact",
                    ),
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
        }
        SettingsDivider()
        SectionLabel(refinementText(copy, "playback"))
        VolumeControl(copy)
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
        SectionLabel(listOf("Sync", "同步", "同步")[copy.languageIndex])
        Button(
            enabled = !syncing && mirrorStatus != "syncing",
            onClick = {
                syncing = true
                syncStatus = ""
                app.mirror.status.value = "syncing"
                scope.launch {
                    try {
                        app.syncFrom()
                        syncStatus = ""
                    } catch (error: Exception) {
                        app.mirror.status.value = "retry"
                        syncStatus =
                            if (error.message == "storage_full")
                                listOf("Not enough storage.", "儲存空間不足。", "存储空间不足。")[
                                    copy.languageIndex]
                            else
                                listOf(
                                    "Sync failed. Check the Mac and retry.",
                                    "同步失敗，請檢查 Mac 後重試。",
                                    "同步失败，请检查 Mac 后重试。",
                                )[copy.languageIndex]
                    } finally {
                        syncing = false
                    }
                }
            },
        ) {
            Text(listOf("Sync now", "立即同步", "立即同步")[copy.languageIndex])
        }
        val message = syncStatus.ifEmpty {
            when (mirrorStatus) {
                "syncing" -> listOf("Syncing…", "同步中…", "同步中…")[copy.languageIndex]
                "complete" -> listOf("Synced", "已同步", "已同步")[copy.languageIndex]
                "waiting" ->
                    listOf("Waiting for playback to finish.", "等待播放結束。", "等待播放结束。")[
                        copy.languageIndex]
                "retry" ->
                    listOf("Sync interrupted. Retry.", "同步中斷，請重試。", "同步中断，请重试。")[copy.languageIndex]
                else -> ""
            }
        }
        androidx.compose.animation.AnimatedContent(targetState = message, label = "sync status") {
            text ->
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall)
        }
        SettingsLink(copy["downloads"], "") { navigate("downloads") }
        SettingsDivider()
        SectionLabel(refinementText(copy, "more"))
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
                        "licenses/roboto/Roboto-NOTICE.md",
                        "licenses/roboto/Apache-2.0.txt",
                        "licenses/roboto/OFL-Roboto.txt",
                        "licenses/roboto/OFL-RobotoMono.txt",
                        "licenses/roboto/OFL-RobotoCondensed.txt",
                        "licenses/compact/NOTICE.md",
                        "licenses/compact/NOTICE-CORRECTIONS.md",
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
            if (summary.isNotBlank())
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
