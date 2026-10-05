package dev.resonance

/** One serial transfer; accepted requests are persisted, with no automatic retry loop. */
object DownloadPolicy {
    const val MAX_PENDING = MediaLimits.PENDING_DOWNLOADS
    // Same reserve as the existing single-file path. This is headroom, not a storage guarantee.
    const val STORAGE_RESERVE_BYTES = 16L * 1024 * 1024
    // Renew at 30-second checkpoints; release between items and whenever the service stops.
    const val WAKE_LOCK_TIMEOUT_MS = 60_000L

    fun accepts(active: Int, added: Int): Boolean =
        active >= 0 && added >= 0 && active <= MAX_PENDING && added <= MAX_PENDING - active
}

data class DownloadSummary(
    val total: Int = 0,
    val complete: Int = 0,
    val waiting: Int = 0,
    val failed: Int = 0,
    val paused: Int = 0,
    val remaining: Int = 0,
    val totalBytes: Long = 0,
    val completedBytes: Long = 0,
    val transferredBytes: Long = 0,
)

data class DownloadBatchReceipt(val accepted: Int, val summary: DownloadSummary)
