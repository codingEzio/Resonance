package dev.resonance

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FeatureScreen(
    kind: String,
    app: ResonanceApp,
    copy: Copy,
    entries: List<MediaEntry>,
    incoming: Uri?,
    importFiles: () -> Unit,
    download: (Long) -> Unit,
    leave: () -> Unit,
    actualVolume: Float?,
    playing: Boolean,
) {
    val scope = rememberCoroutineScope()
    val settings = remember { app.appPreferences() }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionRevision++
        }
    val resumed = rememberForeground()
    LaunchedEffect(resumed) { if (resumed) permissionRevision++ }
    val lanAllowed = remember(permissionRevision) { app.mac.lanAllowed() }
    val notifications =
        remember(permissionRevision) {
            app.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
    if (kind == "downloads") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeading(copy["downloads"])
            }
            item {
                BulkDownloadSection(app, copy, entries, showTitle = false)
            }
            val downloads = entries.filter { it.downloadState != "none" }
            if (downloads.isEmpty()) item { Text(designText(copy, "empty_downloads")) }
            items(downloads, key = { it.id }) { entry ->
                HorizontalDivider()
                Text(entry.displayName.title, style = MaterialTheme.typography.titleLarge)
                if (entry.displayName.subtitle.isNotBlank())
                    Text(
                        entry.displayName.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                Text(
                    copy[
                        if (entry.error.isNotEmpty()) entry.error
                        else "download_${entry.downloadState}"],
                    style = MaterialTheme.typography.bodySmall,
                )
                if (entry.downloadState in listOf("queued", "running")) {
                    LinearProgressIndicator(
                        progress = {
                            if (entry.bytes > 0)
                                (entry.downloadBytes.toFloat() / entry.bytes).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) { app.library.cancelDownload(entry.id) }
                        }
                    ) {
                        Text(copy["cancel"])
                    }
                } else if (entry.downloadState in listOf("paused", "failed"))
                    Button(onClick = { download(entry.id) }) { Text(copy["retry"]) }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel(designText(copy, "sources"))
                Text(
                    copy["identity_hint"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(copy[kind])
        when (kind) {
            "permissions" -> {
                Text(copy["permissions_intro"], style = MaterialTheme.typography.bodyMedium)
                SectionLabel(designText(copy, "selected_files"))
                FeatureSection(copy["file_access"], copy["file_access_hint"])
                Text(
                    "${app.contentResolver.persistedUriPermissions.size} · ${designText(copy, "selected_files")}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = LocalTechnical.current,
                )
                Button(onClick = importFiles) { Text(copy["import"]) }
                HorizontalDivider()
                FeatureSection(
                    copy["lan_access"],
                    copy[if (lanAllowed) "granted" else "lan_permission"],
                )
                if (!lanAllowed)
                    Button(
                        onClick = { permission.launch("android.permission.ACCESS_LOCAL_NETWORK") }
                    ) {
                        Text(copy["request"])
                    }
                HorizontalDivider()
                FeatureSection(copy["notifications"], copy["notification_hint"])
                Text(
                    copy[if (notifications) "granted" else "not_granted"],
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!notifications && Build.VERSION.SDK_INT >= 33)
                    Button(
                        onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    ) {
                        Text(copy["request"])
                    }
                HorizontalDivider()
                FeatureSection(copy["battery"], copy["battery_hint"])
                OutlinedButton(
                    onClick = {
                        app.startActivity(
                            Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${app.packageName}"),
                                )
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) {
                    Text(copy["system_settings"])
                }
            }
            "volume" -> {
                var limit by remember {
                    mutableFloatStateOf(VolumePolicy.output(1f, settings[AppSettings.VolumeLimit]))
                }
                var systemGuard by remember {
                    mutableStateOf(settings[AppSettings.SystemVolumeGuard])
                }
                SectionLabel(designText(copy, "gain"))
                Text(
                    percentLabel(copy, limit),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    fontFamily = LocalTechnical.current,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(copy["output_limit"], style = MaterialTheme.typography.titleMedium)
                Slider(
                    modifier = Modifier.semantics { contentDescription = copy["output_limit"] },
                    value = limit,
                    onValueChange = {
                        limit = it
                        settings.set(AppSettings.VolumeLimit, it)
                    },
                    valueRange = 0f..VolumePolicy.MAX_GAIN,
                    steps = 7,
                )
                Text(copy["volume_hint"], style = MaterialTheme.typography.bodyMedium)
                if (actualVolume != null)
                    Text(
                        "${refinementText(copy, "current_output")} · ${percentLabel(copy, actualVolume)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(copy["system_guard"], Modifier.weight(1f))
                    Switch(
                        systemGuard,
                        {
                            systemGuard = it
                            settings.set(AppSettings.SystemVolumeGuard, it)
                        },
                        modifier = Modifier.semantics { contentDescription = copy["system_guard"] },
                    )
                }
                Text(
                    copy["system_guard_hint"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "motion" -> {
                val enabled by rememberSetting(AppSettings.Motion)
                val respectSystem by rememberSetting(AppSettings.RespectSystemMotion)
                val policy = LocalMotionPolicy.current
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(designText(copy, "animate"), Modifier.weight(1f))
                    Switch(
                        enabled,
                        {
                            settings.set(AppSettings.Motion, it)
                        },
                        modifier = Modifier.semantics { contentDescription = copy["motion"] },
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(refinementText(copy, "respect_motion"), Modifier.weight(1f))
                    Switch(
                        checked = respectSystem,
                        onCheckedChange = {
                            settings.set(AppSettings.RespectSystemMotion, it)
                        },
                        enabled = enabled,
                        modifier =
                            Modifier.semantics {
                                contentDescription = refinementText(copy, "respect_motion")
                            },
                    )
                }
                Text(
                    refinementText(copy, "respect_motion_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (policy.systemScale == 0f)
                    Text(
                        refinementText(copy, "system_motion_off"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                HorizontalDivider()
                SectionLabel(designText(copy, "preview"))
                PlaybackGlyph(playing, Modifier.fillMaxWidth().height(176.dp), motion = enabled)
                if (!playing)
                    Text(
                        refinementText(copy, "preview_play"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                Text(copy["motion_hint"], style = MaterialTheme.typography.bodyMedium)
            }
            "mac" -> {
                var address by
                    remember(incoming) {
                        mutableStateOf(incoming?.getQueryParameter("base") ?: app.mac.base)
                    }
                var key by
                    remember(incoming) {
                        mutableStateOf(incoming?.getQueryParameter("key") ?: app.mac.key)
                    }
                val busy by app.mac.busy.collectAsState()
                val status by app.mac.status.collectAsState()
                var pendingConnect by remember { mutableStateOf(false) }
                fun connect() {
                    scope.launch {
                        app.mac.busy.value = true
                        try {
                            withContext(Dispatchers.IO) {
                                app.mac.connect(address, key, app.library::acceptRemote)
                            }
                            val hash = incoming?.getQueryParameter("download")
                            if (hash != null) {
                                val target =
                                    withContext(Dispatchers.IO) { app.library.byHash(hash) }
                                if (target != null) download(target.id)
                            }
                            app.mac.incoming.value = null
                            leave()
                        } catch (error: Exception) {
                            app.mac.status.value =
                                if (
                                    error.message in
                                        listOf(
                                            "mac_key",
                                            "mac_address",
                                            "lan_permission",
                                            "capacity",
                                        )
                                )
                                    error.message!!
                                else "mac_unavailable"
                        } finally {
                            app.mac.busy.value = false
                        }
                    }
                }
                val lanRequest =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        permissionRevision++
                        if (granted && pendingConnect) {
                            pendingConnect = false
                            connect()
                        } else if (!granted) {
                            pendingConnect = false
                            app.mac.status.value = "lan_permission"
                        }
                    }
                Text(
                    copy["mac_intro"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel(designText(copy, "connection"))
                OutlinedTextField(
                    address,
                    { address = it },
                    label = { Text(copy["address"]) },
                    placeholder = { Text("http://192.168.1.2:12345") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    key,
                    { key = it },
                    label = { Text(copy["connection_key"]) },
                    visualTransformation =
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (!app.mac.lanAllowed()) {
                            pendingConnect = true
                            lanRequest.launch("android.permission.ACCESS_LOCAL_NETWORK")
                        } else connect()
                    },
                    enabled = !busy && address.isNotBlank() && key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy[if (busy) "connecting" else "connect"])
                }
                if (status.isNotEmpty())
                    Text(copy[status], color = MaterialTheme.colorScheme.primary)
                if (app.mac.base.isNotEmpty())
                    OutlinedButton(
                        onClick = {
                            app.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(app.mac.base))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) {
                        Text(copy["open_shelf"])
                    }
                HorizontalDivider()
                BulkDownloadSection(app, copy, entries)
                HorizontalDivider()
                SectionLabel(designText(copy, "sources"))
                Text(
                    copy["identity_hint"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "features" -> {
                SectionLabel(designText(copy, "available"))
                for (id in
                    listOf(
                        "feature_local",
                        "feature_chapters",
                        "feature_dedupe",
                        "feature_mac",
                        "feature_safety",
                        "feature_style",
                    )) Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("•", Modifier.width(24.dp), color = MaterialTheme.colorScheme.primary)
                    Text(copy[id], style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
                SectionLabel(designText(copy, "sources"))
                Text(
                    copy["identity_hint"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                SectionLabel(designText(copy, "wishlist"))
                var wishes by remember { mutableStateOf(app.wishlist.load()) }
                var wish by remember { mutableStateOf("") }
                OutlinedTextField(
                    wish,
                    { wish = it.take(2000) },
                    label = { Text(copy["wish"]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = wish.isNotBlank(),
                    onClick = {
                        wishes = app.wishlist.add(wish)
                        wish = ""
                    },
                ) {
                    Text(copy["add_wish"])
                }
                for ((i, item) in wishes.withIndex()) {
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            item.done,
                            { checked ->
                                wishes = app.wishlist.setDone(i, checked)
                            },
                            modifier = Modifier.semantics { contentDescription = item.text },
                        )
                        Text(item.text, Modifier.weight(1f).padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureSection(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BulkDownloadSection(
    app: ResonanceApp,
    copy: Copy,
    entries: List<MediaEntry>,
    showTitle: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf(DownloadSummary()) }
    var accepting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val stopped by app.downloadStopReason.collectAsState()
    LaunchedEffect(entries) {
        summary = withContext(Dispatchers.IO) { app.library.downloadSummary() }
    }
    fun acceptBatch() {
        if (accepting) return
        accepting = true
        message = ""
        scope.launch {
            try {
                val receipt = app.downloadAll()
                summary = receipt.summary
                message = "batch_accepted"
            } catch (error: Exception) {
                message =
                    if (error.message in listOf("capacity", "storage_full", "lan_permission"))
                        error.message!!
                    else "download_start_failed"
            } finally {
                accepting = false
            }
        }
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Notifications are optional for Android FGS. LAN access is needed for the transfer.
            if (app.mac.lanAllowed()) acceptBatch() else message = "lan_permission"
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) SectionLabel(copy["downloads"])
        Text(
            downloadText(copy, "batch_intro"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.total > 0) {
            Text(
                "${summary.complete} / ${summary.total}",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = LocalTechnical.current,
            )
            Text(
                "${downloadText(copy, "batch_complete")} ${summary.complete} · ${downloadText(copy, "batch_waiting")} ${summary.waiting} · ${downloadText(copy, "batch_failed")} ${summary.failed} · ${downloadText(copy, "batch_paused")} ${summary.paused}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${downloadText(copy, "batch_bytes")}  ${Formatter.formatShortFileSize(app, summary.transferredBytes)} / ${Formatter.formatShortFileSize(app, summary.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = LocalTechnical.current,
            )
            LinearProgressIndicator(
                progress = {
                    if (summary.totalBytes > 0)
                        (summary.transferredBytes.toFloat() / summary.totalBytes).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (summary.total > 0 && summary.remaining == 0) {
            Text(downloadText(copy, "batch_done"), style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(
                enabled = !accepting && summary.total > 0 && app.mac.base.isNotBlank(),
                onClick = {
                    val needed = buildList {
                        if (!app.mac.lanAllowed()) add("android.permission.ACCESS_LOCAL_NETWORK")
                        if (
                            Build.VERSION.SDK_INT >= 33 &&
                                !app.getSystemService(NotificationManager::class.java)
                                    .areNotificationsEnabled()
                        )
                            add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (needed.isEmpty()) acceptBatch()
                    else permissions.launch(needed.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (accepting) copy["download_queued"]
                    else
                        downloadText(
                            copy,
                            if (
                                summary.waiting + summary.failed + summary.paused > 0 ||
                                    stopped.isNotEmpty()
                            )
                                "resume_all"
                            else "download_all",
                        )
                )
            }
        }
        val status = stopped.ifEmpty { message }
        if (status.isNotEmpty())
            Text(
                downloadText(copy, status),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (status == "batch_accepted") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
            )
        Text(
            downloadText(copy, "batch_limit"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
