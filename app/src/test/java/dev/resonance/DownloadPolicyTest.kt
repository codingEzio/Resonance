package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun singleAndTenThousandAreAcceptedButOverflowIsRejected() {
        assertTrue(DownloadPolicy.accepts(0, 1))
        assertTrue(DownloadPolicy.accepts(0, 10000))
        assertTrue(DownloadPolicy.accepts(10000, 0))
        assertFalse(DownloadPolicy.accepts(9999, 2))
        assertFalse(DownloadPolicy.accepts(0, 10001))
        assertFalse(DownloadPolicy.accepts(-1, 1))
    }
}
