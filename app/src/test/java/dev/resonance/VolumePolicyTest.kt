package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class VolumePolicyTest {
    @Test
    fun everyRequestStaysWithinTheSelectedCeiling() {
        for (limit in listOf(0f, .1f, .3f, .4f)) for (request in
            listOf(-1f, 0f, .15f, .3f, 1f, Float.POSITIVE_INFINITY, Float.NaN)) {
            val output = VolumePolicy.output(request, limit)
            assertTrue(output.isFinite() && output >= 0 && output <= limit)
        }
        assertEquals(.3f, VolumePolicy.output(1f, .3f), .0001f)
        assertEquals(.4f, VolumePolicy.output(1f, 1f), .0001f)
        assertEquals(.3f, VolumePolicy.output(1f, Float.NaN), .0001f)
    }
}
