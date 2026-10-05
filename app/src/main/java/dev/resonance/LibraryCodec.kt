package dev.resonance

import org.json.JSONArray
import org.json.JSONObject

/** Stable persisted JSON shapes, independent of SQLite and shelf HTTP. */
fun encodeCues(cues: List<Cue>): String =
    JSONArray()
        .apply {
            cues.forEach { cue ->
                put(
                    JSONObject().apply {
                        put("title", cue.title)
                        put("start", cue.startMs)
                        put("end", cue.endMs)
                        put("origin", cue.origin)
                    }
                )
            }
        }
        .toString()

fun decodeCues(json: String): List<Cue> =
    JSONArray(json).let { a ->
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                Cue(
                    it.getString("title"),
                    it.getLong("start"),
                    it.optLong("end", -1),
                    it.optString("origin", "embedded"),
                )
            }
        }
    }

fun encodeLocations(locations: List<LibraryLocation>): String =
    JSONArray()
        .apply {
            locations.distinct().forEach { location ->
                put(JSONObject().put("group", location.group).put("folder", location.folder))
            }
        }
        .toString()

fun decodeLocations(json: String): List<LibraryLocation> =
    JSONArray(json).let { array ->
        (0 until array.length())
            .map { i ->
                array.getJSONObject(i).let {
                    LibraryLocation(it.getString("group"), it.optString("folder"))
                }
            }
            .distinct()
    }
