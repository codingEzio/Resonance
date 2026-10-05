package dev.resonance

/** Fixed protocol/storage bounds. Equal values have separate meanings and owners. */
object MediaLimits {
    const val CATALOG_ITEMS = 10000
    const val IMPORT_ITEMS = 10000
    const val QUEUE_ITEMS = 10000
    const val CUES_PER_ITEM = 10000
    const val PENDING_DOWNLOADS = 10000
    const val CATALOG_BYTES = 8 * 1024 * 1024
}
