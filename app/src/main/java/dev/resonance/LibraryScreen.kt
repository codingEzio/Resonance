package dev.resonance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun libraryText(copy: Copy, key: String): String {
    val language = copy.languageIndex
    return when (key) {
        "library" -> listOf("Library", "媒體庫", "媒体库")
        "albums" -> listOf("Albums", "專輯", "专辑")
        "playlists" -> listOf("Playlists", "播放清單", "播放列表")
        "availability" -> listOf("Availability", "可用來源", "可用来源")
        "append" -> listOf("Play next", "接著播放", "接着播放")
        "queue_add" -> listOf("Add to queue", "加入佇列", "加入队列")
        "details" -> listOf("File details", "檔案詳細資訊", "文件详细信息")
        else -> listOf(key, key, key)
    }[language]
}

internal const val LegacyPlaylistKey = "virtual:legacy"

internal fun LibraryLocation.isLegacyCollection(): Boolean =
    folder.substringAfterLast('/').equals("Legacy", ignoreCase = true)

/** A collection is a source folder, independent of the URI used to play its files. */
private fun MediaEntry.collectionLocations() = locations.filter { it.folder.isNotBlank() }

@Composable
internal fun LibraryScreen(
    entries: List<MediaEntry>,
    copy: Copy,
    loaded: Boolean,
    playlist: String?,
    sourceFilter: String,
    setSourceFilter: (String) -> Unit,
    canPlay: Boolean,
    openPlaylist: (String) -> Unit,
    details: (Long) -> Unit,
    play: (List<MediaEntry>, Int, Boolean) -> Unit,
    addToQueue: (List<MediaEntry>) -> Unit,
    importFiles: () -> Unit,
    cancelImport: () -> Unit,
    retry: (Long) -> Unit,
    toolbarActions: ((() -> Unit)?, (() -> Unit)?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchEnabled by rememberSetting(AppSettings.LibrarySearch)
    val showDuration by rememberSetting(AppSettings.LibraryDuration)
    val showLegacy by rememberSetting(AppSettings.LibraryLegacy)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchEnabled) {
        if (!searchEnabled) {
            query = ""
            searchOpen = false
        }
    }
    var filters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    DisposableEffect(Unit) {
        toolbarActions(
            {
                searchOpen = !searchOpen
                if (!searchOpen) query = "" else listState.requestScrollToItem(0)
            },
            { filters = true },
        )
        onDispose { toolbarActions(null, null) }
    }
    val available =
        remember(entries, sourceFilter) {
            entries.filter {
                when (sourceFilter) {
                    "on_device" -> !it.remote
                    "on_mac" -> it.remoteAvailable
                    else -> true
                }
            }
        }
    val folders =
        remember(available) {
            available
                .flatMap { entry -> entry.collectionLocations().map { it.key to it } }
                .toMap()
                .values
                .sortedBy { it.folder.lowercase() }
        }
    val legacyFolders = folders.filter { it.isLegacyCollection() }
    val displayedFolders =
        when (playlist) {
            LegacyPlaylistKey -> legacyFolders
            null ->
                folders.filterNot { it.isLegacyCollection() } +
                    if (showLegacy && legacyFolders.isNotEmpty())
                        listOf(LibraryLocation("virtual", "legacy"))
                    else emptyList()
            else -> emptyList()
        }
    val location = entries.flatMap { it.collectionLocations() }.find { it.key == playlist }
    val scoped =
        remember(available, playlist) {
            available
                .filter {
                    if (playlist == LegacyPlaylistKey) false
                    else if (playlist != null)
                        it.collectionLocations().any { location -> location.key == playlist }
                    else
                        it.collectionLocations().isEmpty() ||
                            it.locations.any { location -> location.folder.isBlank() }
                }
                .sortedWith(compareBy<MediaEntry> { it.filename.lowercase() }.thenBy { it.id })
        }
    fun matches(entry: MediaEntry) =
        query.isBlank() ||
            entry.title.contains(query, true) ||
            entry.displayName.title.contains(query, true) ||
            entry.displayName.subtitle.contains(query, true) ||
            entry.filename.contains(query, true) ||
            entry.cues.any { it.title.contains(query, true) }
    val visible = scoped.filter(::matches)
    // Search narrows the view, not the folder queue: selecting a member still plays its whole
    // folder.
    val ready = scoped.filter { it.state == "ready" }
    val pending = entries.count { it.state == "queued" || it.state == "running" }
    if (filters)
        AlertDialog(
            onDismissRequest = { filters = false },
            title = { Text(libraryText(copy, "availability")) },
            text = {
                Column {
                    for (filter in listOf("all_sources", "on_device", "on_mac")) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                setSourceFilter(filter)
                                filters = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                sourceFilter == filter,
                                onClick = {
                                    setSourceFilter(filter)
                                    filters = false
                                },
                            )
                            Text(copy[filter])
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { filters = false }) { Text(copy["close"]) } },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "heading") {
            if (playlist != null)
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        DisplayNames.forCollection(
                            playlist,
                            if (playlist == LegacyPlaylistKey) "Legacy"
                            else if (location?.isLegacyCollection() == true)
                                location.folder.substringBefore('/')
                            else
                                location?.folder?.substringAfterLast('/')?.ifBlank {
                                    location.group
                                } ?: playlist,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (playlist != LegacyPlaylistKey)
                        Text(
                            (if (location?.isLegacyCollection() == true) "Legacy · "
                            else
                                location
                                    ?.folder
                                    ?.takeIf { it.contains('/') }
                                    ?.substringBeforeLast('/')
                                    ?.plus(" · ") ?: "") + "${scoped.size} ${copy["files"]}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            if (sourceFilter != "all_sources")
                TextButton(
                    onClick = { filters = true },
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    Text(copy[sourceFilter])
                }
            if (searchEnabled && searchOpen)
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    placeholder = { Text(copy["search"]) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty())
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, copy["close"])
                            }
                    },
                )
        }
        if (pending > 0)
            item(key = "pending") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${copy["importing"]} · $pending",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = cancelImport) { Text(copy["cancel_import"]) }
                }
            }
        if (visible.isNotEmpty())
            item(key = "albums") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (playlist == null) libraryText(copy, "albums") else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = { play(ready, 0, false) },
                        enabled = canPlay && ready.isNotEmpty(),
                    ) {
                        Text(copy["play_all"])
                    }
                    if (playlist != null)
                        TextButton(
                            onClick = { addToQueue(ready) },
                            enabled = canPlay && ready.isNotEmpty(),
                        ) {
                            Text(libraryText(copy, "queue_add"))
                        }
                }
            }
        items(visible, key = { it.id }) { entry ->
            fun playEntry() {
                if (playlist != null)
                    play(ready, ready.indexOfFirst { it.id == entry.id }.coerceAtLeast(0), false)
                else play(listOf(entry), 0, false)
            }
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        enabled = canPlay && entry.state == "ready",
                        onClick = { playEntry() },
                        onLongClickLabel = libraryText(copy, "append"),
                        onLongClick = { play(listOf(entry), 0, true) },
                    )
                    .heightIn(min = 48.dp)
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryThumbnail(listOf(entry))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.displayName.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (showDuration) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (entry.state == "ready")
                                "${timeLabel(entry.durationMs)}${if (entry.cues.isNotEmpty()) " · ${entry.cues.size} ${copy["chapters"]}" else ""}"
                            else if (entry.state == "failed")
                                "${copy[entry.state]} · ${copy[entry.error]}"
                            else copy[entry.state],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = LocalTechnical.current,
                            color =
                                if (entry.state == "failed") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (
                    !showDuration &&
                        entry.state in listOf("queued", "running", "failed", "cancelled")
                )
                    Icon(
                        if (entry.state == "failed") Icons.Default.Warning
                        else if (entry.state == "cancelled") Icons.Default.Close
                        else Icons.Default.Refresh,
                        if (entry.state == "failed") "${copy[entry.state]} · ${copy[entry.error]}"
                        else copy[entry.state],
                        Modifier.size(20.dp),
                        tint =
                            if (entry.state == "failed") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (entry.state in listOf("failed", "cancelled"))
                    TextButton(onClick = { retry(entry.id) }) { Text(copy["retry"]) }
                if (entry.state == "ready")
                    IconButton(
                        onClick = { addToQueue(listOf(entry)) },
                        enabled = canPlay,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "${libraryText(copy, "queue_add")} · ${entry.displayName.title}"
                            },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            libraryText(copy, "details"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (entry.state != "ready") {
                            Text(
                                if (entry.state == "failed")
                                    "${copy[entry.state]} · ${copy[entry.error]}"
                                else copy[entry.state],
                                Modifier.widthIn(max = 280.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (entry.state == "failed") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(libraryText(copy, "details")) },
                            onClick = {
                                menu = false
                                details(entry.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(libraryText(copy, "append")) },
                            enabled = canPlay && entry.state == "ready",
                            onClick = {
                                menu = false
                                play(listOf(entry), 0, true)
                            },
                        )
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (playlist == null || playlist == LegacyPlaylistKey) {
            val visibleFolders = displayedFolders.filter { folder ->
                (if (folder.group == "virtual") "Legacy" else folder.folder).contains(
                    query,
                    true,
                ) ||
                    DisplayNames.forCollection(folder.key, folder.folder).contains(query, true) ||
                    available.any { entry ->
                        entry.collectionLocations().any {
                            if (folder.group == "virtual") it.isLegacyCollection()
                            else it.key == folder.key
                        } && matches(entry)
                    }
            }
            if (visibleFolders.isNotEmpty())
                item(key = "playlists") {
                    Text(
                        libraryText(copy, "playlists"),
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            items(visibleFolders, key = { "folder:${it.key}" }) { folder ->
                val members = available.filter { entry ->
                    entry.collectionLocations().any {
                        if (folder.group == "virtual") it.isLegacyCollection()
                        else it.key == folder.key
                    }
                }
                val label =
                    DisplayNames.forCollection(
                        folder.key,
                        when {
                            folder.group == "virtual" -> "Legacy"
                            playlist == LegacyPlaylistKey -> folder.folder.substringBefore('/')
                            else -> folder.folder.substringAfterLast('/').ifBlank { folder.group }
                        },
                    )
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                openPlaylist(
                                    if (folder.group == "virtual") LegacyPlaylistKey else folder.key
                                )
                            },
                            onLongClickLabel = libraryText(copy, "append"),
                            onLongClick = {
                                val playable = members.filter { it.state == "ready" }
                                if (canPlay && playable.isNotEmpty()) play(playable, 0, true)
                            },
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryThumbnail(members, folder.key)
                    Text(
                        DisplayNames.compactTitle(label),
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        if (entries.isEmpty())
            item(key = "empty") {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        if (loaded) copy["empty"] else copy["running"],
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(copy["empty_hint"], color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = importFiles) { Text(copy["import"]) }
                }
            }
        else if (
            visible.isEmpty() &&
                (playlist != null && playlist != LegacyPlaylistKey ||
                    displayedFolders.isEmpty() ||
                    query.isNotBlank())
        )
            item(key = "matches") {
                Text(copy["matches"], Modifier.padding(24.dp))
            }
    }
}

internal val AvailabilityIcon =
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "Availability",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
            addPath(
                pathData =
                    androidx.compose.ui.graphics.vector
                        .PathParser()
                        .parsePathString(
                            "M3,6 L8,6 M12,6 L21,6 M3,12 L14,12 M18,12 L21,12 M3,18 L6,18 M10,18 L21,18 M10,3 L10,9 M16,9 L16,15 M8,15 L8,21"
                        )
                        .toNodes(),
                stroke =
                    androidx.compose.ui.graphics.SolidColor(
                        androidx.compose.ui.graphics.Color.Black
                    ),
                strokeLineWidth = 1.8f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        .build()
