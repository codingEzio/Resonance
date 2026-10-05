package dev.resonance

/** Checkpoint indexes refer to queue occurrences, including repeated copies of one album. */
data class QueueCheckpoint(
    val entries: List<MediaEntry>,
    val currentIndex: Int,
    val positionMs: Long,
)

object QueueRules {
    private data class SavedItem(val id: Long, val chapter: Int?, val startMs: Long)

    private fun parse(value: String): SavedItem? {
        val parts = value.split(':')
        if (parts.size == 2 && parts[0] == "entry")
            return parts[1].toLongOrNull()?.takeIf { it > 0 }?.let { SavedItem(it, null, 0) }
        if (parts.size != 3) return null
        val id = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val chapter = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val start = parts[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return SavedItem(id, chapter, start)
    }

    fun migrate(
        ids: List<String>,
        current: String,
        currentIndex: Int,
        position: Long,
        resolve: (Long) -> MediaEntry?,
    ): QueueCheckpoint {
        val entries = mutableListOf<MediaEntry>()
        var previous: SavedItem? = null
        var previousCanonical: Long? = null
        val wanted =
            currentIndex.takeIf { it in ids.indices && ids[it] == current } ?: ids.indexOf(current)
        var selected = 0
        var selectedPosition = 0L
        ids.take(MediaLimits.QUEUE_ITEMS).forEachIndexed { index, value ->
            val saved = parse(value)
            val entry = saved?.let { resolve(it.id) }
            if (saved == null || entry == null) {
                previous = null
                previousCanonical = null
                return@forEachIndexed
            }
            // Only increasing legacy chapter runs collapse. A chapter-number reset starts
            // another occurrence, even when an alias resolves to the same canonical album.
            val sameRun =
                saved.chapter != null &&
                    previous?.chapter != null &&
                    previousCanonical == entry.id &&
                    saved.chapter > previous.chapter &&
                    saved.startMs >= previous.startMs
            if (!sameRun) entries.add(entry)
            if (index == wanted) {
                selected = entries.lastIndex
                val relative = position.coerceAtLeast(0)
                val absolute =
                    if (relative > Long.MAX_VALUE - saved.startMs) Long.MAX_VALUE
                    else saved.startMs + relative
                selectedPosition =
                    if (entry.durationMs > 0 && absolute >= entry.durationMs) 0 else absolute
            }
            previous = saved
            previousCanonical = entry.id
        }
        return QueueCheckpoint(entries, selected, selectedPosition)
    }

    fun validOrder(order: List<Int>, size: Int): Boolean =
        order.size == size && order.toSet().size == size && order.all { it in 0 until size }
}
