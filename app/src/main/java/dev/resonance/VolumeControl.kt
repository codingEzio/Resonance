package dev.resonance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun VolumeControl(copy: Copy) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) { context.appPreferences() }
    val gain by rememberSetting(AppSettings.VolumeLimit)
    val threshold by rememberSetting(AppSettings.VolumeWarning)
    var pending by remember { mutableStateOf<Float?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    fun text(en: String, tw: String, cn: String) = listOf(en, tw, cn)[copy.languageIndex]
    val volumeLabel = text("App volume", "應用程式音量", "应用音量")
    val warningLabel = text("Warning threshold", "音量提醒閾值", "音量提醒阈值")
    Text("$volumeLabel · ${percentLabel(copy, gain)}", style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = gain,
        onValueChange = { value ->
            if (pending == null) {
                if (VolumePolicy.needsConfirmation(gain, value, threshold)) {
                    confirmed = false
                    pending = value
                } else settings.set(AppSettings.VolumeLimit, value)
            }
        },
        modifier = Modifier.semantics { contentDescription = volumeLabel },
    )
    Text(
        "$warningLabel · ${percentLabel(copy, threshold)}",
        style = MaterialTheme.typography.bodyLarge,
    )
    Slider(
        value = threshold,
        onValueChange = { settings.set(AppSettings.VolumeWarning, it) },
        valueRange = .05f..1f,
        modifier = Modifier.semantics { contentDescription = warningLabel },
    )
    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(text("High volume", "音量較高", "音量较高")) },
            text = {
                Row {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(text("I understand and want to continue", "我了解並要繼續", "我已了解，继续调整"))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmed,
                    onClick = {
                        settings.set(AppSettings.VolumeLimit, target)
                        pending = null
                    },
                ) {
                    Text(text("Continue", "繼續", "继续"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(text("Cancel", "取消", "取消")) }
            },
        )
    }
}
