package dev.resonance

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeProgressTest {
    @Test
    fun switchingPreservesPositionButCompletionRestartsFile() {
        assertEquals(120000L, ResumeProgress.checkpoint(120000, 240000, false))
        assertEquals(0L, ResumeProgress.checkpoint(240000, 240000, true))
        assertEquals(0L, ResumeProgress.checkpoint(120000, 240000, true))
    }

    @Test
    fun invalidAndOutdatedPositionsCannotSeekOutsideFile() {
        assertEquals(0L, ResumeProgress.checkpoint(-1, 240000, false))
        assertEquals(0L, ResumeProgress.checkpoint(240001, 240000, false))
        assertEquals(120000L, ResumeProgress.checkpoint(120000, 0, false))
    }
}
