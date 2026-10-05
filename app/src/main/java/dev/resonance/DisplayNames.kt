package dev.resonance

/** UI compatibility facade over a replaceable immutable presentation catalog. */
object DisplayNames {
    fun forCollection(key: String, fallback: String): String =
        LibraryPresentation.catalog.collectionName(key, fallback)

    fun compactTitle(title: String): String = LibraryPresentation.catalog.compactTitle(title)

    fun forEntry(entry: MediaEntry): DisplayName = LibraryPresentation.catalog.name(entry)
}

val MediaEntry.displayName: DisplayName
    get() = DisplayNames.forEntry(this)
