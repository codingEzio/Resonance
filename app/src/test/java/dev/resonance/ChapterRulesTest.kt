package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class ChapterRulesTest {
    @Test
    fun timestampedDescriptionHasRealOffsets() {
        val cues =
            ChapterRules.parseDescription(
                "Album\n00:00 First\n1:54 - 第二首\n01:02:03 Finale",
                4_000_000,
            )
        assertEquals(listOf(0L, 114000L, 3723000L), cues.map { it.startMs })
        assertEquals("第二首", cues[1].title)
        assertEquals(114000L, cues[0].endMs)
    }

    @Test
    fun namesWithoutTimestampsNeverBecomeTimedChapters() {
        assertTrue(
            ChapterRules.parseDescription(
                    "Tracklist:\n1. Dream of Arrakis\n2. Bene Gesserit",
                    600000,
                )
                .isEmpty()
        )
    }

    @Test
    fun invalidTimesAndOutOfRangeCuesCannotSeekOutsideMedia() {
        assertNull(ChapterRules.parseTime("1:99"))
        assertNull(ChapterRules.parseTime("-1:00"))
        assertNull(ChapterRules.parseTime("99999999999999999:00"))
        assertEquals(3723000L, ChapterRules.parseTime("1:02:03"))
        val result =
            ChapterRules.validate(
                listOf(
                    Cue("A", -1),
                    Cue("B", 100),
                    Cue("B", 100),
                    Cue("Different", 100),
                    Cue("C", 1200),
                ),
                1000,
            )
        assertEquals(listOf("B", "Different"), result.map { it.title })
        assertEquals(1000L, result.last().endMs)
    }
}
