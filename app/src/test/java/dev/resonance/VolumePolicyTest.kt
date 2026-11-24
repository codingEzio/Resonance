package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class VolumePolicyTest {
    @Test
    fun warnsWhenCrossingThreshold() {
        assertTrue(VolumePolicy.needsConfirmation(.3f, .8f, .7f))
        assertFalse(VolumePolicy.needsConfirmation(.8f, .9f, .7f))
        assertFalse(VolumePolicy.needsConfirmation(.8f, .4f, .7f))
        assertTrue(VolumePolicy.needsConfirmation(.4f, .8f, .7f))
    }
}
