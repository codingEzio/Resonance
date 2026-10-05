package dev.resonance

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.json.JSONArray

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val settings by lazy { appPreferences() }
    private val cap
        get() = VolumePolicy.output(1f, settings[AppSettings.VolumeLimit])

    private var stopSettingsObservation: (() -> Unit)? = null
    private val routeListener =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) {
                if (player.playWhenReady) guardSystemVolume()
            }
        }

    private fun guardSystemVolume() {
        if (!settings[AppSettings.SystemVolumeGuard]) return
        val allowed = (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * cap).toInt()
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) > allowed)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, allowed, 0) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { getSharedPreferences("playback", MODE_PRIVATE) }

    private var restoring = true
    private var observedMediaId: String? = null
    private var observedDurationMs = 0L

    override fun onCreate() {
        super.onCreate()
        val app = application as ResonanceApp
        val source =
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory())
            ) { spec ->
                if (spec.uri.scheme == "resonance") {
                    val hash = spec.uri.host.orEmpty()
                    val local = app.library.byHash(hash)?.takeUnless { it.remote }
                    if (local != null) spec.withUri(Uri.parse(local.playUri))
                    else
                        spec
                            .withUri(app.mac.streamUri(hash))
                            .withAdditionalHeaders(
                                mapOf("Authorization" to "Bearer ${app.mac.key}")
                            )
                } else spec
            }
        player =
            ExoPlayer.Builder(this, WaveformRenderersFactory(this))
                .setMediaSourceFactory(DefaultMediaSourceFactory(source))
                .build()
                .apply {
                    volume = cap
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        true,
                    )
                    setHandleAudioBecomingNoisy(true)
                    setWakeMode(C.WAKE_MODE_LOCAL)
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                            .build()
                }
        val activity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val volumeGuard =
            object : ForwardingPlayer(player) {
                override fun setVolume(volume: Float) {
                    this@PlaybackService.player.volume = VolumePolicy.output(volume, cap)
                }
            }
        val protectedPlayer =
            object : ForwardingSimpleBasePlayer(volumeGuard) {
                override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
                    if (playWhenReady) {
                        if (player.playbackState == Player.STATE_ENDED)
                            player.seekToDefaultPosition(0)
                        guardSystemVolume()
                    }
                    return super.handleSetPlayWhenReady(playWhenReady)
                }
            }
        stopSettingsObservation = settings.observe { key ->
            if (key == AppSettings.VolumeLimit.key || key == AppSettings.SystemVolumeGuard.key) {
                player.volume = cap
                if (player.playWhenReady) guardSystemVolume()
            }
        }
        audio.registerAudioDeviceCallback(routeListener, null)
        session =
            MediaSession.Builder(this, protectedPlayer)
                .setSessionActivity(activity)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            if (!controller.isTrusted && controller.packageName != packageName)
                                return MediaSession.ConnectionResult.reject()
                            // Media3 1.11's deprecated super.onConnect returns an EMPTY-command
                            // sentinel. Accepted app/trusted controllers need explicit defaults.
                            return MediaSession.ConnectionResult.AcceptedResultBuilder(
                                    session,
                                    controller,
                                )
                                .setAvailableSessionCommands(
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                        .buildUpon()
                                        .add(PlaybackCommands.RESHUFFLE)
                                        .add(PlaybackCommands.PLAY_NEXT_ORDER)
                                        .add(PlaybackCommands.ADD_TO_QUEUE)
                                        .build()
                                )
                                .setAvailablePlayerCommands(
                                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                                )
                                .build()
                        }

                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            val appendAtEnd =
                                customCommand.customAction ==
                                    PlaybackCommands.ADD_TO_QUEUE.customAction
                            if (
                                appendAtEnd ||
                                    customCommand.customAction ==
                                        PlaybackCommands.PLAY_NEXT_ORDER.customAction
                            ) {
                                val incoming =
                                    try {
                                        queueItems(args)
                                    } catch (_: RuntimeException) {
                                        return Futures.immediateFuture(
                                            SessionResult(
                                                androidx.media3.session.SessionError.ERROR_BAD_VALUE
                                            )
                                        )
                                    }
                                if (incoming.any { it.localConfiguration == null })
                                    return Futures.immediateFuture(
                                        SessionResult(
                                            androidx.media3.session.SessionError.ERROR_BAD_VALUE
                                        )
                                    )
                                val count = incoming.size
                                val start =
                                    if (appendAtEnd) player.mediaItemCount
                                    else
                                        (player.currentMediaItemIndex + 1).coerceIn(
                                            0,
                                            player.mediaItemCount,
                                        )
                                if (
                                    count <= 0 ||
                                        player.mediaItemCount + count > MediaLimits.QUEUE_ITEMS
                                )
                                    return Futures.immediateFuture(
                                        SessionResult(
                                            androidx.media3.session.SessionError.ERROR_BAD_VALUE
                                        )
                                    )
                                run {
                                    player.addMediaItems(start, incoming)
                                    val added = (start until start + count).toList()
                                    val timeline = player.currentTimeline
                                    val order = mutableListOf<Int>()
                                    var next = timeline.getFirstWindowIndex(true)
                                    repeat(timeline.windowCount) {
                                        if (next != C.INDEX_UNSET) {
                                            if (next !in start until start + count) order.add(next)
                                            next =
                                                timeline.getNextWindowIndex(
                                                    next,
                                                    Player.REPEAT_MODE_OFF,
                                                    true,
                                                )
                                        }
                                    }
                                    if (appendAtEnd) order.addAll(added)
                                    else
                                        order.addAll(
                                            (order.indexOf(player.currentMediaItemIndex) + 1)
                                                .coerceAtLeast(0),
                                            added,
                                        )
                                    player.setShuffleOrder(
                                        ShuffleOrder.DefaultShuffleOrder(
                                            order.toIntArray(),
                                            System.nanoTime(),
                                        )
                                    )
                                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                    savePosition()
                                }
                                return Futures.immediateFuture(
                                    SessionResult(SessionResult.RESULT_SUCCESS)
                                )
                            }
                            if (
                                customCommand.customAction !=
                                    PlaybackCommands.RESHUFFLE.customAction
                            )
                                return super.onCustomCommand(
                                    session,
                                    controller,
                                    customCommand,
                                    args,
                                )
                            // Updating traversal order never replaces the playing file or seeks.
                            player.setShuffleOrder(
                                ShuffleOrder.DefaultShuffleOrder(
                                    player.mediaItemCount,
                                    System.nanoTime(),
                                )
                            )
                            player.repeatMode = Player.REPEAT_MODE_ALL
                            player.shuffleModeEnabled = true
                            savePosition()
                            return Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                    }
                )
                .build()
        player.addListener(
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (restoring) return
                    // The player already exposes the incoming item here. Save PositionInfo,
                    // never currentPosition, for the outgoing file.
                    val outgoingId = oldPosition.mediaItem?.mediaId
                    val editor = preferences.edit()
                    ResumeProgress.save(
                        editor,
                        outgoingId,
                        oldPosition.positionMs,
                        if (outgoingId == observedMediaId) observedDurationMs else 0,
                        reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                    )
                    editor.commit()
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    if (restoring || item == null) return
                    if (
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    ) {
                        // Explicit nonzero seeks (for example a chapter) stay explicit.
                        val position =
                            ResumeProgress.position(preferences, item.mediaId, player.duration)
                        if (player.currentPosition == 0L && position > 0) player.seekTo(position)
                    }
                }

                override fun onEvents(p: Player, events: Player.Events) {
                    observedMediaId = p.currentMediaItem?.mediaId
                    observedDurationMs = p.duration
                    savePosition()
                }
            }
        )
        scope.launch {
            val restored =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val list = JSONArray(preferences.getString("queue", "[]"))
                            val db = (application as ResonanceApp).library
                            QueueRules.migrate(
                                (0 until list.length()).take(MediaLimits.QUEUE_ITEMS).map {
                                    list.getString(it)
                                },
                                preferences.getString("current", "").orEmpty(),
                                preferences.getInt("current_index", -1),
                                preferences.getLong("position", 0),
                                db::get,
                            )
                        }
                        .getOrDefault(QueueCheckpoint(emptyList(), 0, 0))
                }
            if (player.mediaItemCount == 0) {
                val repeat = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
                player.repeatMode =
                    repeat.takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL }
                        ?: Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
                if (restored.entries.isNotEmpty()) {
                    val items = restored.entries.flatMap(::playbackItems)
                    player.setMediaItems(
                        items,
                        restored.currentIndex,
                        if (preferences.getBoolean("ended", false)) 0 else restored.positionMs,
                    )
                    val order = runCatching {
                        val saved = JSONArray(preferences.getString("shuffle_order", "[]"))
                        (0 until saved.length()).map { saved.getInt(it) }
                    }
                        .getOrDefault(emptyList())
                    if (QueueRules.validOrder(order, items.size))
                        player.setShuffleOrder(
                            ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime())
                        )
                    player.prepare()
                }
            }
            restoring = false
            savePosition()
            var lastSaved = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                delay(
                    if (player.isPlaying && settings[AppSettings.SystemVolumeGuard]) 250 else 1000
                )
                if (player.isPlaying) {
                    guardSystemVolume()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastSaved >= 1000) {
                        saveCurrentPosition()
                        lastSaved = now
                    }
                }
            }
        }
    }

    private fun currentPositionEditor(): SharedPreferences.Editor {
        val editor = preferences.edit()
        ResumeProgress.save(
            editor,
            player.currentMediaItem?.mediaId,
            player.currentPosition,
            player.duration,
            player.playbackState == Player.STATE_ENDED,
        )
        return editor
            .putString("current", player.currentMediaItem?.mediaId)
            .putLong("position", player.currentPosition)
            .putBoolean("ended", player.playbackState == Player.STATE_ENDED)
            .putInt("current_index", player.currentMediaItemIndex)
    }

    private fun saveCurrentPosition() {
        if (!restoring) currentPositionEditor().commit()
    }

    private fun savePosition() {
        if (restoring) return
        val queue = JSONArray()
        for (i in 0 until player.mediaItemCount) queue.put(player.getMediaItemAt(i).mediaId)
        val order = JSONArray()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        // Save native traversal, including insert/remove updates, rather than a stale seed.
        repeat(timeline.windowCount) {
            if (index != C.INDEX_UNSET) {
                order.put(index)
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
        }
        currentPositionEditor()
            .putString("queue", queue.toString())
            .putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putString("shuffle_order", order.toString())
            .commit()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        savePosition()
        scope.cancel()
        stopSettingsObservation?.invoke()
        stopSettingsObservation = null
        audio.unregisterAudioDeviceCallback(routeListener)
        session.release()
        player.release()
        super.onDestroy()
    }
}

fun togglePlayback(player: Player) {
    if (player.isPlaying) player.pause()
    else {
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
        player.play()
    }
}

/** Tap: fresh file-level order + queue repeat. Long press: toggle native shuffle only. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackCommands {
    val PLAY_NEXT_ORDER = SessionCommand("dev.resonance.PLAY_NEXT_ORDER", Bundle.EMPTY)
    val ADD_TO_QUEUE = SessionCommand("dev.resonance.ADD_TO_QUEUE", Bundle.EMPTY)
    val RESHUFFLE = SessionCommand("dev.resonance.RESHUFFLE_QUEUE", Bundle.EMPTY)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun reshuffleQueue(controller: MediaController): ListenableFuture<SessionResult> =
    controller.sendCustomCommand(PlaybackCommands.RESHUFFLE, Bundle.EMPTY)

fun playbackItems(entry: MediaEntry): List<MediaItem> =
    listOf(
        MediaItem.Builder()
            .setMediaId("entry:${entry.id}")
            .setUri(entry.playUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.displayName.title)
                    .setArtist("")
                    .setAlbumArtist("")
                    .setAlbumTitle(entry.displayName.title)
                    .setExtras(Bundle().apply { putLong("entry", entry.id) })
                    .build()
            )
            .build()
    )
