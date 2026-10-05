package dev.resonance

import android.content.res.AssetManager
import java.io.FileNotFoundException
import org.json.JSONObject

/**
 * Optional packaging adapter: absence is valid; malformed supplied data is not silently ignored.
 */
internal fun loadPresentationAssets(assets: AssetManager): PresentationCatalog {
    fun read(name: String): JSONObject? =
        try {
            assets.open(name).bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: FileNotFoundException) {
            null
        }
    fun mapping(data: JSONObject?): Map<String, String> =
        data?.keys()?.asSequence()?.associateWith { data.getString(it) } ?: emptyMap()
    fun path(value: String): String {
        require(value.matches(Regex("library-art/[a-z0-9-]+\\.webp"))) {
            "Invalid presentation art path"
        }
        return value
    }
    val names = read("display-names.json")
    val items = names?.getJSONArray("items")
    val overrides = buildMap {
        for (index in 0 until (items?.length() ?: 0)) {
            val row = items!!.getJSONObject(index)
            put(
                row.getString("sha256"),
                DisplayName(row.getString("title"), row.optString("subtitle")),
            )
        }
    }
    val art = read("library-art.json")
    val rules = read("presentation-rules.json")?.getJSONArray("titleReplacements")
    return PresentationCatalog(
        names = overrides,
        collectionNames = mapping(names?.optJSONObject("collections")),
        entryArt = mapping(art?.getJSONObject("entries")).mapValues { path(it.value) },
        collectionArt = mapping(art?.getJSONObject("collections")).mapValues { path(it.value) },
        albumFallback = art?.getString("default_album")?.let(::path),
        playlistFallback = art?.getString("default_playlist")?.let(::path),
        titleReplacements =
            (0 until (rules?.length() ?: 0)).map { index ->
                val row = rules!!.getJSONObject(index)
                TitleReplacement(
                    row.getString("match"),
                    row.getString("replacement"),
                    row.optBoolean("ignoreCase"),
                )
            },
    )
}
