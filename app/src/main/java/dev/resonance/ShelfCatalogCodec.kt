package dev.resonance

import org.json.JSONObject

/** Protocol parsing is separate from fetching, pairing and committing source entries. */
object ShelfCatalogCodec {
    fun decode(json: String): List<MediaEntry> {
        val array = JSONObject(json).getJSONArray("items")
        require(array.length() <= MediaLimits.CATALOG_ITEMS) { "capacity" }
        val entries =
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val hash = item.getString("id")
                require(hash.matches(Regex("[a-f0-9]{64}"))) { "mac_unavailable" }
                val duration = item.optLong("durationMs")
                val cues = item.optJSONArray("chapters")
                require((cues?.length() ?: 0) <= MediaLimits.CUES_PER_ITEM) { "capacity" }
                MediaEntry(
                    0,
                    "resonance://$hash",
                    item.getString("filename"),
                    title = item.getString("title"),
                    creator = item.optString("creator"),
                    durationMs = duration,
                    bytes = item.getLong("bytes"),
                    state = "ready",
                    contentHash = hash,
                    locations =
                        item.optJSONArray("memberships")?.let { decodeLocations(it.toString()) }
                            ?: item
                                .optString("group")
                                .takeIf { it.isNotBlank() }
                                ?.let {
                                    listOf(LibraryLocation(it, item.optString("folder")))
                                }
                                .orEmpty(),
                    cues =
                        ChapterRules.validate(
                            (0 until (cues?.length() ?: 0)).map { i ->
                                val c = cues!!.getJSONObject(i)
                                Cue(
                                    c.optString("title"),
                                    c.getLong("startMs"),
                                    c.optLong("endMs"),
                                    "embedded",
                                )
                            },
                            duration,
                        ),
                )
            }
        return entries
    }
}
