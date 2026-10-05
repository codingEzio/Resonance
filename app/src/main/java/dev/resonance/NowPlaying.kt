package dev.resonance

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlin.math.abs

internal fun playbackText(copy: Copy, id: String): String {
    val i = copy.languageIndex
    return (when (id) {
        "fullscreen" -> listOf("Toggle full screen", "切換全螢幕", "切换全屏")
        "fullscreen_on" -> listOf("System bars hidden", "系統列已隱藏", "系统栏已隐藏")
        "fullscreen_off" -> listOf("System bars visible", "系統列已顯示", "系统栏已显示")
        "remove_queue" -> listOf("Remove from queue", "從佇列移除", "从队列移除")
        "added_queue" -> listOf("Added to queue", "已加入播放佇列", "已加入播放队列")
        "shuffle" -> listOf("Shuffle · tap to mix again", "隨機播放 · 點一下重新洗牌", "随机播放 · 点一下重新洗牌")
        "shuffle_on" -> listOf("Shuffle on", "隨機播放已開啟", "随机播放已开启")
        "shuffle_off" -> listOf("Shuffle off", "隨機播放已關閉", "随机播放已关闭")
        "shuffle_toggle" -> listOf("Turn shuffle on or off", "開啟或關閉隨機播放", "开启或关闭随机播放")
        "shuffle_hint" ->
            listOf(
                "Tap Shuffle to mix and loop the queue. Hold to turn it on or off.",
                "點一下隨機播放，重新洗牌並循環佇列。長按可開啟或關閉。",
                "点一下随机播放，重新洗牌并循环队列。长按可开启或关闭。",
            )
        "repeat" -> listOf("Repeat", "循環播放", "循环播放")
        "repeat_off" -> listOf("Play once", "播放一次", "播放一次")
        "repeat_one" -> listOf("Repeat this album / file", "循環此專輯／檔案", "循环此专辑／文件")
        "repeat_all" -> listOf("Repeat the queue", "循環整個佇列", "循环整个队列")
        "modes" -> listOf("Playback order", "播放順序", "播放顺序")
        "album" -> listOf("ALBUM", "專輯", "专辑")
        "previous_album" -> listOf("Previous album / file", "上一個專輯／檔案", "上一个专辑／文件")
        "next_album" -> listOf("Next album / file", "下一個專輯／檔案", "下一个专辑／文件")
        else -> listOf(id, id, id)
    })[i]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlaying(
    copy: Copy,
    p: MediaController?,
    entry: MediaEntry?,
    position: Long,
    revision: Int,
    details: (Long) -> Unit,
    motion: Boolean,
    close: () -> Unit,
    fullScreen: Boolean,
    toggleFullScreen: () -> Unit,
    outputSettings: () -> Unit,
    browseLibrary: () -> Unit,
) {
    val item = if (revision >= 0) p?.currentMediaItem else null
    if (p == null || item == null) {
        Column(Modifier.padding(24.dp)) {
            Text(copy["empty_queue"])
            TextButton(onClick = close) { Text(copy["back"]) }
        }
        return
    }
    val playing = p.isPlaying
    val duration = p.duration.coerceAtLeast(1)
    var seeking by remember { mutableStateOf<Float?>(null) }
    var sheet by rememberSaveable { mutableStateOf("") }
    val name = entry?.displayName
    val currentCue = entry?.cues?.lastOrNull { it.startMs <= position }
    val background = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val flingThreshold = with(density) { 900.dp.toPx() }
    val shiftX = rememberMotionFloat(drag.x, if (dragging || !motion) snap() else spring())
    val shiftY = rememberMotionFloat(drag.y, if (dragging || !motion) snap() else spring())
    val limit by rememberSetting(AppSettings.VolumeLimit)
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer {
            translationX = shiftX.value
            translationY = shiftY.value
        }
    ) {
        val compact = maxHeight < 500.dp
        val controlsWidth = if (compact) maxWidth * .53f else 560.dp
        PlaybackGlyph(
            playing,
            if (compact) Modifier.fillMaxHeight().fillMaxWidth(.43f).padding(top = 50.dp)
            else Modifier.fillMaxSize(),
            motion,
            immersive = true,
        )
        // Edge fades preserve legibility while the dot field remains a full-screen surface.
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background,
                        .10f to background,
                        .20f to background.copy(alpha = .15f),
                        .52f to background.copy(alpha = 0f),
                        .60f to background.copy(alpha = .97f),
                        1f to background,
                    )
                )
        )
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = if (compact) Alignment.End else Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(if (compact) 44.dp else 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) { Icon(Icons.Default.Close, copy["back"]) }
                Column(
                    Modifier.weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = toggleFullScreen,
                            onLongClickLabel = playbackText(copy, "fullscreen"),
                        )
                        .semantics {
                            stateDescription =
                                playbackText(
                                    copy,
                                    if (fullScreen) "fullscreen_on" else "fullscreen_off",
                                )
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(playbackText(copy, "fullscreen")) {
                                        toggleFullScreen()
                                        true
                                    }
                                )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("RESONANCE", fontFamily = LocalDisplay.current, fontSize = 18.sp)
                    Text(
                        copy[if (playing) "playing_state" else "paused"],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
            // Gesture surface excludes seek bar, buttons and sheets: scrubbing keeps native
            // ownership.
            Spacer(
                Modifier.weight(1f).fillMaxWidth().pointerInput(p, threshold, flingThreshold) {
                    var axis = 0
                    val velocity = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            axis = 0
                            drag = Offset.Zero
                            velocity.resetTracking()
                        },
                        onDragCancel = {
                            dragging = false
                            drag = Offset.Zero
                        },
                        onDragEnd = {
                            val v = velocity.calculateVelocity()
                            if (axis == 1) {
                                if (
                                    (drag.x < -threshold ||
                                        (drag.x < -threshold / 3 && v.x < -flingThreshold)) &&
                                        p.hasNextMediaItem()
                                )
                                    p.seekToNextMediaItem()
                                else if (
                                    (drag.x > threshold ||
                                        (drag.x > threshold / 3 && v.x > flingThreshold)) &&
                                        p.hasPreviousMediaItem()
                                )
                                    p.seekToPreviousMediaItem()
                            } else if (
                                axis == 2 &&
                                    (drag.y > threshold ||
                                        (drag.y > threshold / 3 && v.y > flingThreshold))
                            )
                                close()
                            dragging = false
                            drag = Offset.Zero
                        },
                    ) { change, amount ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (axis == 0) axis = if (abs(amount.x) > abs(amount.y)) 1 else 2
                        drag =
                            if (axis == 1) Offset(drag.x + amount.x, 0f)
                            else Offset(0f, (drag.y + amount.y).coerceAtLeast(0f))
                        change.consume()
                    }
                }
            )
            Column(
                Modifier.widthIn(max = controlsWidth)
                    .fillMaxWidth()
                    .background(background.copy(alpha = .98f))
            ) {
                Text(
                    name?.title ?: item.mediaMetadata.title?.toString().orEmpty(),
                    style =
                        if (compact) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = currentCue?.title ?: name?.subtitle.orEmpty()
                if (subtitle.isNotBlank())
                    Text(
                        subtitle,
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val seekFinished =
                    rememberUpdatedState<() -> Unit> {
                        seeking?.let { p.seekTo((it * duration).toLong()) }
                        seeking = null
                    }
                val sliderState = rememberSliderState()
                SideEffect {
                    sliderState.value = seeking ?: (position.toFloat() / duration).coerceIn(0f, 1f)
                }
                Slider(
                    state = sliderState,
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seekFinished.value() },
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = copy["playback_position"]
                        },
                    thumb = {
                        Box(
                            Modifier.size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    },
                    track = { state ->
                        SliderDefaults.Track(
                            state,
                            modifier = Modifier.height(3.dp),
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            drawStopIndicator = null,
                        )
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        timeLabel(
                            ((seeking ?: (position.toFloat() / duration)) * duration).toLong()
                        ),
                        fontFamily = LocalTechnical.current,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        timeLabel(duration),
                        fontFamily = LocalTechnical.current,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val shuffleDescription = playbackText(copy, "shuffle")
                    val shuffleState =
                        playbackText(
                            copy,
                            if (p.shuffleModeEnabled) "shuffle_on" else "shuffle_off",
                        )
                    val toggleDescription = playbackText(copy, "shuffle_toggle")
                    Box(
                        Modifier.size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { reshuffleQueue(p) },
                                onLongClick = { p.shuffleModeEnabled = !p.shuffleModeEnabled },
                                onLongClickLabel = toggleDescription,
                            )
                            .semantics {
                                contentDescription = shuffleDescription
                                stateDescription = shuffleState
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(toggleDescription) {
                                            p.shuffleModeEnabled = !p.shuffleModeEnabled
                                            true
                                        }
                                    )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        OrderSymbol(true, p.shuffleModeEnabled)
                    }
                    IconButton(
                        onClick = { p.seekToPreviousMediaItem() },
                        enabled = p.hasPreviousMediaItem(),
                    ) {
                        SkipSymbol(
                            false,
                            Modifier.size(25.dp).semantics {
                                contentDescription = playbackText(copy, "previous_album")
                            },
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            if (playing) p.pause()
                            else {
                                if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
                                p.play()
                            }
                        },
                        modifier =
                            Modifier.size(if (compact) 52.dp else 66.dp).semantics {
                                contentDescription = copy[if (playing) "pause" else "play"]
                            },
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.background,
                            ),
                    ) {
                        PlaybackSymbol(playing, Modifier.size(28.dp), motion)
                    }
                    IconButton(
                        onClick = { p.seekToNextMediaItem() },
                        enabled = p.hasNextMediaItem(),
                    ) {
                        SkipSymbol(
                            true,
                            Modifier.size(25.dp).semantics {
                                contentDescription = playbackText(copy, "next_album")
                            },
                        )
                    }
                    IconButton(
                        onClick = { sheet = "modes" },
                        modifier =
                            Modifier.semantics {
                                contentDescription = playbackText(copy, "repeat")
                                stateDescription =
                                    playbackText(
                                        copy,
                                        when (p.repeatMode) {
                                            Player.REPEAT_MODE_ONE -> "repeat_one"
                                            Player.REPEAT_MODE_ALL -> "repeat_all"
                                            else -> "repeat_off"
                                        },
                                    )
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            OrderSymbol(false, p.repeatMode != Player.REPEAT_MODE_OFF)
                            if (p.repeatMode == Player.REPEAT_MODE_ONE) Text("1", fontSize = 9.sp)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { sheet = "queue" }) {
                        Text(
                            "${copy["queue"]} · ${p.mediaItemCount}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = outputSettings,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "${copy["output_limit"]} · ${percentLabel(copy, limit)}"
                            },
                    ) {
                        Text(
                            percentLabel(copy, limit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { sheet = "chapters" },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                    ) {
                        Text(copy["chapters"], style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (sheet.isNotEmpty())
        ModalBottomSheet(onDismissRequest = { sheet = "" }) {
            PersistentSystemBars(fullScreen)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 540.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                if (sheet == "queue")
                    item {
                        TextButton(
                            onClick = {
                                sheet = ""
                                browseLibrary()
                            }
                        ) {
                            Text(refinementText(copy, "browse_add"))
                        }
                        Text(
                            refinementText(copy, "browse_add_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                item {
                    ScreenHeading(
                        if (sheet == "modes") playbackText(copy, "modes") else copy[sheet]
                    )
                }
                when (sheet) {
                    "modes" -> {
                        for ((mode, key) in
                            listOf(
                                Player.REPEAT_MODE_OFF to "repeat_off",
                                Player.REPEAT_MODE_ONE to "repeat_one",
                                Player.REPEAT_MODE_ALL to "repeat_all",
                            )) item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { p.repeatMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(p.repeatMode == mode, { p.repeatMode = mode })
                                Text(playbackText(copy, key), Modifier.padding(start = 8.dp))
                            }
                        }
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    playbackText(
                                        copy,
                                        if (p.shuffleModeEnabled) "shuffle_on" else "shuffle_off",
                                    ),
                                    Modifier.weight(1f),
                                )
                                Switch(p.shuffleModeEnabled, { p.shuffleModeEnabled = it })
                            }
                            Text(
                                playbackText(copy, "shuffle_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    "queue" ->
                        items(p.mediaItemCount, key = { "$it:${p.getMediaItemAt(it).mediaId}" }) {
                            index ->
                            val queued = p.getMediaItemAt(index)
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        p.seekTo(
                                            index,
                                            if (index == p.currentMediaItemIndex) p.currentPosition
                                            else
                                                ResumeProgress.position(
                                                    context.getSharedPreferences("playback", 0),
                                                    queued.mediaId,
                                                ),
                                        )
                                        p.play()
                                        sheet = ""
                                    }
                                    .padding(vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (index == p.currentMediaItemIndex) "●"
                                    else "%02d".format(index + 1),
                                    Modifier.width(38.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = LocalTechnical.current,
                                )
                                Text(
                                    queued.mediaMetadata.title?.toString().orEmpty(),
                                    Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { p.removeMediaItem(index) }) {
                                    Icon(Icons.Default.Close, playbackText(copy, "remove_queue"))
                                }
                            }
                            HorizontalDivider()
                        }
                    "chapters" -> {
                        if (entry?.cues.isNullOrEmpty())
                            item { Text(copy["no_chapters"], Modifier.padding(vertical = 16.dp)) }
                        itemsIndexed(entry?.cues.orEmpty()) { _, cue ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        p.seekTo(cue.startMs)
                                        sheet = ""
                                    }
                                    .padding(vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    timeLabel(cue.startMs),
                                    Modifier.width(68.dp),
                                    fontFamily = LocalTechnical.current,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    cue.title,
                                    Modifier.weight(1f),
                                    color =
                                        if (cue == currentCue) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            HorizontalDivider()
                        }
                        if (entry != null)
                            item {
                                TextButton(
                                    onClick = {
                                        sheet = ""
                                        details(entry.id)
                                    }
                                ) {
                                    Text(copy["details"])
                                }
                            }
                    }
                }
            }
        }
}

@Composable
private fun OrderSymbol(shuffle: Boolean, active: Boolean) {
    val color =
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(23.dp)) {
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(
                color,
                Offset(size.width * x, size.height * y),
                Offset(size.width * x2, size.height * y2),
                1.7.dp.toPx(),
                StrokeCap.Round,
            )
        if (shuffle) {
            line(.1f, .25f, .3f, .25f)
            line(.3f, .25f, .7f, .75f)
            line(.7f, .75f, .9f, .75f)
            line(.1f, .75f, .3f, .75f)
            line(.3f, .75f, .7f, .25f)
            line(.7f, .25f, .9f, .25f)
            line(.77f, .12f, .9f, .25f)
            line(.77f, .38f, .9f, .25f)
            line(.77f, .62f, .9f, .75f)
            line(.77f, .88f, .9f, .75f)
        } else {
            line(.2f, .25f, .85f, .25f)
            line(.85f, .25f, .85f, .5f)
            line(.8f, .75f, .15f, .75f)
            line(.15f, .75f, .15f, .5f)
            line(.2f, .25f, .32f, .12f)
            line(.2f, .25f, .32f, .38f)
            line(.8f, .75f, .68f, .62f)
            line(.8f, .75f, .68f, .88f)
        }
    }
}
