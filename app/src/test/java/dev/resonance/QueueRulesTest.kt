package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class QueueRulesTest {
    private val album =
        MediaEntry(
            1,
            "file:a",
            "a",
            durationMs = 3000,
            cues = listOf(Cue("A", 0, 1000), Cue("B", 1000, 2000), Cue("C", 2000, 3000)),
        )
    private val ids = listOf("1:0:0", "1:1:1000", "1:2:2000")

    private fun migrate(
        queue: List<String> = ids,
        current: String = ids[1],
        index: Int = -1,
        position: Long = 400,
    ) =
        QueueRules.migrate(queue, current, index, position) {
            if (it == 1L || it == 9L) album else null
        }

    @Test
    fun legacyChaptersBecomeOneAlbumAndAbsolutePosition() {
        val restored = migrate()
        assertEquals(listOf(1L), restored.entries.map { it.id })
        assertEquals(1400L, restored.positionMs)
        assertEquals(2400L, migrate(current = ids[2]).positionMs)
    }

    @Test
    fun completedChapterAdvancesWithinAlbumButFileEndRestarts() {
        assertEquals(2000L, migrate(position = 1000).positionMs)
        assertEquals(0L, migrate(current = ids[2], position = 1000).positionMs)
    }

    @Test
    fun legacyAliasResolvesCanonicalEntryAndRepeatedRunsRemainDistinct() {
        val repeated = listOf("9:0:0", "9:1:1000", "9:2:2000") + ids
        val restored = migrate(repeated, ids[1], position = 250)
        assertEquals(listOf(1L, 1L), restored.entries.map { it.id })
        assertEquals(1, restored.currentIndex)
        assertEquals(1250L, restored.positionMs)
    }

    @Test
    fun fullFileOccurrencesUseSavedIndexAndMissingFilesAreSkipped() {
        val restored = migrate(listOf("entry:1", "entry:404", "entry:1"), "entry:1", 2, 800)
        assertEquals(2, restored.entries.size)
        assertEquals(1, restored.currentIndex)
        assertEquals(800L, restored.positionMs)
    }

    @Test
    fun malformedCheckpointAndShuffleOrderCannotEscapeQueue() {
        assertTrue(migrate(listOf("broken", "entry:404"), "broken").entries.isEmpty())
        assertTrue(QueueRules.validOrder(listOf(2, 0, 1), 3))
        assertFalse(QueueRules.validOrder(listOf(0, 0, 1), 3))
        assertFalse(QueueRules.validOrder(listOf(0, 1, 3), 3))
        assertFalse(QueueRules.validOrder(listOf(0, 1), 3))
    }
}
