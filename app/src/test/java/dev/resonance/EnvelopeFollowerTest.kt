package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class EnvelopeFollowerTest {
    @Test
    fun responseUsesElapsedTimeNotFrameCount() {
        val at60 = EnvelopeFollower(1)
        val at120 = EnvelopeFollower(1)
        repeat(6) { at60.update(floatArrayOf(1f), 1f / 60) }
        repeat(12) { at120.update(floatArrayOf(1f), 1f / 120) }
        assertEquals(at60.values[0], at120.values[0], .00001f)
        assertTrue(at120.values[0] in .9f..1f)
    }

    @Test
    fun silenceNeverCreatesEnergyAndDecayIsBounded() {
        val follower = EnvelopeFollower(1)
        follower.update(floatArrayOf(0f), .01f)
        assertEquals(0f, follower.values[0], 0f)
        follower.update(floatArrayOf(1f), .008f)
        assertTrue(follower.values[0] in .01f..0.9f)
        repeat(180) { follower.update(floatArrayOf(0f), 1f / 120) }
        assertEquals(0f, follower.values[0], 0f)
        follower.update(floatArrayOf(Float.NaN), .01f)
        assertEquals(0f, follower.values[0], 0f)
    }
}
