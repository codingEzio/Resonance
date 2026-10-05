package dev.resonance

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.inspector.MetadataRetriever
import java.util.concurrent.TimeUnit

/**
 * Read-only content-provider/Media3 adapter; import scheduling and persistence belong to the app.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ContentMediaInspector(private val context: Context) {
    fun inspect(entry: MediaEntry, continueReading: () -> Boolean): MediaEntry {
        val uri = Uri.parse(entry.uri)
        val fingerprint =
            context.contentResolver.openInputStream(uri)!!.use {
                Fingerprint.read(it, continueReading)
            }
        // One bounded retrieval at a time. No frames, waveform or audio decoding.
        MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build().use { retriever ->
            val tracks = retriever.retrieveTrackGroups().get(20, TimeUnit.SECONDS)
            val duration =
                retriever.retrieveDurationUs().get(20, TimeUnit.SECONDS).let {
                    if (it == C.TIME_UNSET) 0 else it / 1000
                }
            val builder = MediaMetadata.Builder()
            val cues = mutableListOf<Cue>()
            var hasAudio = false
            for (i in 0 until tracks.length) {
                val group = tracks[i]
                if (group.type == C.TRACK_TYPE_AUDIO) hasAudio = true
                for (j in 0 until group.length) {
                    val metadata = group.getFormat(j).metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        val value = metadata[k]
                        value.populateMediaMetadata(builder)
                        if (value is Chapter && !value.isHidden)
                            cues.add(
                                Cue(value.title?.value ?: "", value.startTimeMs, value.endTimeMs)
                            )
                    }
                }
            }
            check(hasAudio) { "no_audio" }
            val meta = builder.build()
            val description = meta.description?.toString().orEmpty()
            check(cues.size <= MediaLimits.CUES_PER_ITEM) { "capacity" }
            val chapters =
                ChapterRules.validate(cues, duration).ifEmpty {
                    ChapterRules.parseDescription(description, duration)
                }
            return entry.copy(
                title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: entry.title,
                creator = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                description = description,
                durationMs = duration,
                bytes = fingerprint.second,
                contentHash = fingerprint.first,
                state = "ready",
                cues = chapters,
            )
        }
    }
}
