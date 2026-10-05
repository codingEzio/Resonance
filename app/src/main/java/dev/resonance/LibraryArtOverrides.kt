package dev.resonance

/** UI compatibility facade; artwork is optional presentation data, never media identity. */
internal object LibraryArtOverrides {
    val albumFallback: String?
        get() = LibraryPresentation.catalog.albumFallback

    val playlistFallback: String?
        get() = LibraryPresentation.catalog.playlistFallback

    fun forEntry(entry: MediaEntry): String? =
        LibraryPresentation.catalog.entryArt[entry.contentHash]

    fun forCollection(key: String): String? = LibraryPresentation.catalog.artForCollection(key)
}
