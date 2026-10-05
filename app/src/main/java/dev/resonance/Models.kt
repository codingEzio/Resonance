package dev.resonance

data class Cue(
    val title: String,
    val startMs: Long,
    val endMs: Long = -1,
    val origin: String = "embedded",
)

data class LibraryLocation(val group: String, val folder: String) {
    val key: String
        get() = "$group/$folder"
}

data class MediaEntry(
    val id: Long,
    val uri: String,
    val filename: String,
    val title: String = filename,
    val creator: String = "",
    val album: String = "",
    val description: String = "",
    val durationMs: Long = 0,
    val bytes: Long = 0,
    val state: String = "queued",
    val error: String = "",
    val cues: List<Cue> = emptyList(),
    val contentHash: String = "",
    val playUri: String = uri,
    val remote: Boolean = false,
    val downloadState: String = "none",
    val downloadBytes: Long = 0,
    val sourceCount: Int = 1,
    val locations: List<LibraryLocation> = emptyList(),
    val remoteAvailable: Boolean = remote,
)

object ChapterRules {
    private val timestampLine =
        Regex("^\\s*[\\[(]?(\\d{1,6}:\\d{2}(?::\\d{2})?)[\\])]?[\\s\\-–—:]+(.+?)\\s*$")

    fun parseDescription(text: String, durationMs: Long): List<Cue> =
        validate(
            text
                .lineSequence()
                .take(MediaLimits.CUES_PER_ITEM)
                .mapNotNull { line ->
                    val match = timestampLine.matchEntire(line) ?: return@mapNotNull null
                    val time = parseTime(match.groupValues[1]) ?: return@mapNotNull null
                    Cue(match.groupValues[2].trim(), time, origin = "description")
                }
                .toList(),
            durationMs,
        )

    fun validate(cues: List<Cue>, durationMs: Long): List<Cue> {
        val valid =
            cues
                .filter { it.startMs >= 0 && (durationMs <= 0 || it.startMs < durationMs) }
                .distinctBy { it.startMs to it.title }
                .sortedBy { it.startMs }
        val nextStarts = valid.map { it.startMs }.distinct().zipWithNext().toMap()
        return valid.map { cue ->
            val next = nextStarts[cue.startMs]
            cue.copy(endMs = next ?: if (durationMs > 0) durationMs else cue.endMs)
        }
    }

    fun parseTime(text: String): Long? {
        val trimmed = text.trim()
        if (!trimmed.matches(Regex("\\d{1,6}:\\d{2}(?::\\d{2})?"))) return null
        val parts = trimmed.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.last() >= 60 || (parts.size == 3 && parts[1] >= 60)) return null
        val seconds =
            if (parts.size == 3) parts[0] * 3600 + parts[1] * 60 + parts[2]
            else parts[0] * 60 + parts[1]
        return seconds * 1000
    }
}

fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600)
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
