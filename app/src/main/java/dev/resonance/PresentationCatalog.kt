package dev.resonance

/** Immutable display data; source filenames, content identities and embedded tags stay separate. */
data class DisplayName(val title: String, val subtitle: String = "")

data class TitleReplacement(
    val match: String,
    val replacement: String,
    val ignoreCase: Boolean = false,
)

data class PresentationCatalog(
    val names: Map<String, DisplayName> = emptyMap(),
    val collectionNames: Map<String, String> = emptyMap(),
    val entryArt: Map<String, String> = emptyMap(),
    val collectionArt: Map<String, String> = emptyMap(),
    val albumFallback: String? = null,
    val playlistFallback: String? = null,
    val titleReplacements: List<TitleReplacement> = emptyList(),
) {
    fun compactTitle(title: String): String =
        titleReplacements.fold(title) { value, rule ->
            value.replace(rule.match, rule.replacement, rule.ignoreCase)
        }

    fun name(entry: MediaEntry): DisplayName {
        val original =
            names[entry.contentHash]
                ?: DisplayName(
                    entry.title
                        .ifBlank { entry.filename.substringBeforeLast('.') }
                        .replace('_', ' ')
                        .trim()
                )
        return original.copy(title = compactTitle(original.title))
    }

    fun collectionName(key: String, fallback: String): String =
        collectionNames.matchCollection(key) ?: fallback

    fun artForCollection(key: String): String? = collectionArt.matchCollection(key)
}

private fun <T> Map<String, T>.matchCollection(key: String): T? =
    get(key) ?: entries.filter { key.startsWith(it.key + "/") }.maxByOrNull { it.key.length }?.value

/**
 * One startup-installed snapshot backs the existing presentation facade; never a data authority.
 */
internal object LibraryPresentation {
    var catalog: PresentationCatalog = PresentationCatalog()
        private set

    fun install(value: PresentationCatalog) {
        catalog = value
    }
}
