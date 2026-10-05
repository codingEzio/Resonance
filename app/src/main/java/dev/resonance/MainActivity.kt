package dev.resonance

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.sample

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as ResonanceApp).mac.incoming.value =
            intent.data?.takeIf { it.scheme == "resonance" && it.host == "connect" }
        enableEdgeToEdge()
        setContent { Resonance(application as ResonanceApp) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun Resonance(app: ResonanceApp, initialScreen: String = "library") {
    val preferences = remember { app.appPreferences() }
    val fullScreen by rememberSetting(AppSettings.FullScreen)
    PersistentSystemBars(fullScreen)
    val theme by rememberSetting(AppSettings.Theme)
    val face by rememberSetting(AppSettings.Font)
    val language by rememberSetting(AppSettings.Language)
    val locale = LocalConfiguration.current.locales[0]
    val effectiveLanguage = resolveLanguage(language, locale)
    val copy = remember(effectiveLanguage) { Copy(effectiveLanguage) }
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var backStack by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val pageState = rememberSaveableStateHolder()
    val librarySearchEnabled by rememberSetting(AppSettings.LibrarySearch)
    val motion by rememberSetting(AppSettings.Motion)
    val materialYou by rememberSetting(AppSettings.MaterialYou)
    var librarySearchAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var libraryAvailabilityAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun navigate(target: String) {
        if (target != screen) {
            backStack = ArrayList(backStack + screen)
            screen = target
        }
    }
    var sourceFilter by rememberSaveable { mutableStateOf("all_sources") }
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playerRevision by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0) }
    var message by remember { mutableStateOf("") }
    val importError by app.importError.collectAsState()
    val incoming by app.mac.incoming.collectAsState()
    LaunchedEffect(incoming) { if (incoming != null) navigate("mac") }
    val foreground = rememberForeground()
    LaunchedEffect(foreground, entries.any { it.downloadState == "queued" }) {
        if (foreground) withContext(Dispatchers.IO) { runCatching { app.resumeDownloads() } }
    }
    var pendingDownload by rememberSaveable { mutableLongStateOf(0) }
    val downloadPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (app.mac.lanAllowed() && pendingDownload > 0) {
                app.download(pendingDownload)
                pendingDownload = 0
            } else message = "lan_permission"
        }
    fun download(id: Long) {
        val needed = mutableListOf<String>()
        if (!app.mac.lanAllowed()) needed.add("android.permission.ACCESS_LOCAL_NETWORK")
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                !preferences[AppSettings.DownloadNotificationsAsked]
        ) {
            needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
            preferences.set(AppSettings.DownloadNotificationsAsked, true)
        }
        if (needed.isEmpty()) app.download(id)
        else {
            pendingDownload = id
            downloadPermissions.launch(needed.toTypedArray())
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == android.app.Activity.RESULT_OK)
                result.data?.let { data ->
                    val uris =
                        data.clipData?.let { clip ->
                            (0 until clip.itemCount).map { clip.getItemAt(it).uri }
                        } ?: listOfNotNull(data.data)
                    app.accept(uris, data.flags)
                }
        }
    fun importFiles() {
        picker.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        )
    }
    fun play(
        items: List<MediaItem>,
        index: Int = 0,
        append: Boolean = false,
        startMs: Long? = null,
        atEnd: Boolean = false,
    ) {
        val p = controller ?: return
        if (
            items.any { it.localConfiguration?.uri?.scheme == "resonance" } && !app.mac.lanAllowed()
        ) {
            message = "lan_permission"
            navigate("permissions")
            return
        }
        if (items.size + (if (append) p.mediaItemCount else 0) > MediaLimits.QUEUE_ITEMS) {
            message = "capacity"
            return
        }
        if (append && (atEnd || p.mediaItemCount > 0)) {
            val addition =
                p.sendCustomCommand(
                    if (atEnd) PlaybackCommands.ADD_TO_QUEUE else PlaybackCommands.PLAY_NEXT_ORDER,
                    queueArguments(items),
                )
            addition.addListener(
                {
                    if (
                        runCatching { addition.get().resultCode }.getOrNull() ==
                            androidx.media3.session.SessionResult.RESULT_SUCCESS
                    )
                        scope.launch { snackbar.showSnackbar(playbackText(copy, "added_queue")) }
                    else message = "playback_error"
                },
                ContextCompat.getMainExecutor(app),
            )
        } else {
            p.setMediaItems(
                items,
                index,
                startMs
                    ?: if (p.currentMediaItem?.mediaId == items[index].mediaId) p.currentPosition
                    else
                        ResumeProgress.position(
                            app.getSharedPreferences("playback", 0),
                            items[index].mediaId,
                        ),
            )
            p.prepare()
            p.play()
            navigate("now")
        }
    }
    DisposableEffect(app) {
        val future =
            MediaController.Builder(
                    app,
                    SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                )
                .buildAsync()
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    playerRevision++
                    position = player.currentPosition
                }
            }
        future.addListener(
            {
                if (!future.isCancelled)
                    runCatching { future.get() }
                        .onSuccess {
                            controller = it
                            it.addListener(listener)
                        }
            },
            ContextCompat.getMainExecutor(app),
        )
        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
        }
    }
    LaunchedEffect(app.library) {
        entries = withContext(Dispatchers.IO) { app.library.entries() }
        loaded = true
        app.library.revision.sample(250).collect {
            entries = withContext(Dispatchers.IO) { app.library.entries() }
        }
    }
    // One additive catalog refresh adopts folder metadata after the v3 database upgrade.
    // A failed/offline refresh leaves every saved file, cue and grant untouched.
    var folderRefreshAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, foreground) {
        if (
            loaded &&
                foreground &&
                !folderRefreshAttempted &&
                app.mac.base.isNotEmpty() &&
                app.mac.lanAllowed() &&
                !app.mac.busy.value &&
                !preferences[AppSettings.FolderCatalogApplied]
        ) {
            folderRefreshAttempted = true
            app.mac.busy.value = true
            try {
                withContext(Dispatchers.IO) { app.mac.refresh(app.library::acceptRemote) }
                preferences.set(AppSettings.FolderCatalogApplied, true)
            } catch (_: Exception) {
                app.mac.status.value = "mac_unavailable"
            } finally {
                app.mac.busy.value = false
            }
        }
    }
    LaunchedEffect(controller, foreground, playerRevision) {
        position = controller?.currentPosition ?: 0
        if (foreground && controller?.isPlaying == true)
            while (isActive) {
                position = controller?.currentPosition ?: 0
                delay(250)
            }
    }
    val revision =
        playerRevision // Read to observe player events, including paused state and queue changes.
    val p = controller
    val playing = p?.isPlaying == true
    val current = if (revision >= 0) p?.currentMediaItem else null
    val currentEntry = entries.find { it.id == current?.mediaMetadata?.extras?.getLong("entry") }
    fun back() {
        if (backStack.isNotEmpty()) {
            screen = backStack.last()
            backStack = ArrayList(backStack.dropLast(1))
        } else
            screen =
                if (screen in listOf("volume", "permissions", "downloads", "motion", "features"))
                    "settings"
                else "library"
    }
    BackHandler(screen != "library") { back() }
    ResonanceTheme(theme, face, materialYou) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets =
                if (screen == "now") WindowInsets(0, 0, 0, 0)
                else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (screen != "now")
                    Column(Modifier.statusBarsPadding().displayCutoutPadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (screen != "library")
                                IconButton(onClick = { back() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, copy["back"])
                                }
                            Text(
                                "Resonance",
                                Modifier.weight(1f).padding(start = 8.dp),
                                fontFamily = LocalDisplay.current,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (screen == "library" || screen.startsWith("playlist:"))
                                IconButton(onClick = { importFiles() }) {
                                    Icon(Icons.Default.Add, copy["import"])
                                }
                            if (screen == "library" || screen.startsWith("playlist:"))
                                IconButton(onClick = { navigate("settings") }) {
                                    Icon(Icons.Default.Settings, copy["settings"])
                                }
                            if (screen == "library" || screen.startsWith("playlist:")) {
                                if (librarySearchEnabled)
                                    IconButton(onClick = { librarySearchAction?.invoke() }) {
                                        Icon(
                                            Icons.Default.Search,
                                            copy["search"],
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                IconButton(onClick = { libraryAvailabilityAction?.invoke() }) {
                                    Icon(
                                        AvailabilityIcon,
                                        libraryText(copy, "availability"),
                                        tint =
                                            if (sourceFilter == "all_sources")
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
            },
            bottomBar = {
                if (current != null && p != null && screen != "now") {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier.navigationBarsPadding()
                                .fillMaxWidth()
                                .clickable { navigate("now") }
                                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaybackGlyph(
                                playing,
                                Modifier.size(56.dp).padding(end = 10.dp),
                                motion,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    current.mediaMetadata.title?.toString().orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    timeLabel(position),
                                    fontFamily = LocalTechnical.current,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { togglePlayback(p) },
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = copy[if (playing) "pause" else "play"]
                                    },
                            ) {
                                PlaybackSymbol(
                                    playing,
                                    Modifier.size(26.dp),
                                    motion,
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val error = message.ifEmpty { importError }
                if (error.isNotEmpty() || p?.playerError != null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            copy[
                                if (p?.playerError != null) {
                                    if (currentEntry?.remote == true) "mac_unavailable"
                                    else "playback_error"
                                } else if (
                                    error in
                                        listOf(
                                            "capacity",
                                            "local_only",
                                            "permission",
                                            "lan_permission",
                                            "download_failed",
                                            "mac_unavailable",
                                            "storage_full",
                                            "integrity",
                                            "playback_error",
                                        )
                                )
                                    error
                                else "import_failed"],
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        IconButton(
                            onClick = {
                                message = ""
                                app.importError.value = ""
                                if (p?.playerError != null) p.stop()
                            }
                        ) {
                            Icon(Icons.Default.Close, copy["close"])
                        }
                    }
                }
                when {
                    screen in
                        listOf("permissions", "volume", "motion", "mac", "downloads", "features") ->
                        FeatureScreen(
                            screen,
                            app,
                            copy,
                            entries,
                            incoming,
                            ::importFiles,
                            ::download,
                            { navigate("library") },
                            actualVolume = p?.volume,
                            playing = playing,
                        )
                    screen == "settings" ->
                        SettingsScreen(
                            copy,
                            theme,
                            face,
                            language,
                            { navigate(it) },
                            {
                                preferences.set(AppSettings.Theme, it)
                            },
                            {
                                preferences.set(AppSettings.Font, it)
                            },
                            {
                                preferences.set(AppSettings.Language, it)
                            },
                        )
                    screen == "now" ->
                        NowPlaying(
                            copy,
                            p,
                            currentEntry,
                            position,
                            revision,
                            { navigate("details:${it}") },
                            motion,
                            ::back,
                            fullScreen,
                            {
                                preferences.set(
                                    AppSettings.FullScreen,
                                    !fullScreen,
                                    synchronous = true,
                                )
                            },
                            { navigate("volume") },
                            {
                                navigate(
                                    backStack.lastOrNull {
                                        it == "library" || it.startsWith("playlist:")
                                    } ?: "library"
                                )
                            },
                        )
                    screen.startsWith("details:") -> {
                        val entry = entries.find {
                            it.id == screen.substringAfter(':').toLongOrNull()
                        }
                        if (entry != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    copy[if (entry.remote) "on_mac" else "on_device"],
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                if (entry.remote)
                                    TextButton(onClick = { download(entry.id) }) {
                                        Text(copy["download"])
                                    }
                            }
                            if (entry.sourceCount > 1)
                                Text(
                                    "${entry.sourceCount} ${copy["source_count"]}",
                                    Modifier.padding(horizontal = 24.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            DetailsScreen(
                                entry,
                                copy,
                                { index ->
                                    val start = entry.cues.getOrNull(index)?.startMs ?: 0L
                                    if (
                                        p?.currentMediaItem
                                            ?.mediaMetadata
                                            ?.extras
                                            ?.getLong("entry") == entry.id
                                    ) {
                                        p.seekTo(start)
                                        p.play()
                                        navigate("now")
                                    } else play(playbackItems(entry), startMs = start)
                                },
                                { play(playbackItems(entry), append = true) },
                                { cues ->
                                    scope.launch(Dispatchers.IO) {
                                        app.library.saveCues(entry.id, cues)
                                    }
                                },
                            )
                        }
                    }
                    else -> {
                        pageState.SaveableStateProvider(screen) {
                            LibraryScreen(
                                entries,
                                copy,
                                loaded,
                                screen
                                    .takeIf { it.startsWith("playlist:") }
                                    ?.substringAfter("playlist:"),
                                sourceFilter,
                                { sourceFilter = it },
                                controller != null,
                                { navigate("playlist:$it") },
                                { navigate("details:$it") },
                                { selected, index, append ->
                                    if (selected.isNotEmpty())
                                        play(selected.flatMap(::playbackItems), index, append)
                                },
                                { selected ->
                                    if (selected.isNotEmpty())
                                        play(
                                            selected.flatMap(::playbackItems),
                                            append = true,
                                            atEnd = true,
                                        )
                                },
                                ::importFiles,
                                { scope.launch(Dispatchers.IO) { app.library.cancelPending() } },
                                { id ->
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            app.library.retry(id)
                                            app.startImport()
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                message =
                                                    if (e.message == "capacity") "capacity"
                                                    else "import_failed"
                                            }
                                        }
                                    }
                                },
                                toolbarActions = { search, availability ->
                                    librarySearchAction = search
                                    libraryAvailabilityAction = availability
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundMark(modifier: Modifier) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_resonance_foreground),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DetailsScreen(
    entry: MediaEntry,
    copy: Copy,
    play: (Int) -> Unit,
    enqueue: () -> Unit,
    save: (List<Cue>) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item {
            Text(entry.displayName.title, style = MaterialTheme.typography.headlineMedium)
            if (entry.displayName.subtitle.isNotBlank())
                Text(
                    entry.displayName.subtitle,
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Text(
                timeLabel(entry.durationMs),
                Modifier.padding(vertical = 12.dp),
                fontFamily = LocalTechnical.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { play(-1) }, enabled = entry.state == "ready") {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(copy["play"])
                }
                OutlinedButton(onClick = enqueue, enabled = entry.state == "ready") {
                    Text(copy["enqueue"])
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    copy["chapters"],
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = { showAdd = true },
                    enabled = entry.state == "ready" && entry.durationMs > 0,
                ) {
                    Text(copy["add_chapter"])
                }
            }
            if (entry.cues.isEmpty()) {
                Text(
                    copy["no_chapters"],
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    copy["no_chapters_hint"],
                    Modifier.padding(top = 6.dp, bottom = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(entry.cues.withIndex().toList(), key = { "${it.index}:${it.value.startMs}" }) {
            (index, cue) ->
            Row(
                Modifier.fillMaxWidth().clickable { play(index) }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%02d".format(index + 1),
                    Modifier.width(34.dp),
                    fontFamily = LocalTechnical.current,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        cue.title.ifBlank { "${copy["chapter"]} ${index+1}" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        copy[cue.origin],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    timeLabel(cue.startMs),
                    Modifier.padding(start = 8.dp),
                    fontFamily = LocalTechnical.current,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            Spacer(Modifier.height(28.dp))
            Text(copy["original_metadata"], style = MaterialTheme.typography.titleMedium)
            if (entry.title != entry.filename.substringBeforeLast('.'))
                Text(
                    entry.title,
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            Text(
                entry.filename,
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "%.1f MB".format(Locale.ROOT, entry.bytes / 1_000_000.0),
                fontFamily = LocalTechnical.current,
                style = MaterialTheme.typography.labelSmall,
            )
            if (entry.creator.isNotBlank()) {
                Text(
                    "${copy["source_tag"]}: ${entry.creator}",
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    copy["source_note"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.description.isNotBlank())
                Text(
                    entry.description,
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
    if (showAdd)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(copy["add_chapter"]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text(copy["title"]) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        timestamp,
                        {
                            timestamp = it
                            error = false
                        },
                        label = { Text(copy["time"]) },
                        singleLine = true,
                        isError = error,
                    )
                    if (error) Text(copy["invalid_time"], color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = ChapterRules.parseTime(timestamp)
                        if (time == null || time >= entry.durationMs || title.isBlank())
                            error = true
                        else {
                            save(
                                ChapterRules.validate(
                                    entry.cues + Cue(title.trim(), time, origin = "manual"),
                                    entry.durationMs,
                                )
                            )
                            showAdd = false
                            title = ""
                            timestamp = ""
                        }
                    }
                ) {
                    Text(copy["save"])
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(copy["cancel"]) } },
        )
}
