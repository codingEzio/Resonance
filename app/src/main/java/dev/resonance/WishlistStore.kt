package dev.resonance

import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class Wish(val text: String, val done: Boolean)

/** User records are not settings. Retain their existing key/JSON and all unrecognized fields. */
internal class WishlistStore(private val storage: SharedPreferences) {
    private val key = "wishes"

    private fun read(): JSONArray = JSONArray(storage.getString(key, "[]")!!)

    private fun items(data: JSONArray): List<Wish> =
        (0 until data.length()).map { index ->
            val row = data.getJSONObject(index)
            Wish(row.getString("text"), row.optBoolean("done"))
        }

    fun load(): List<Wish> = items(read())

    fun add(text: String): List<Wish> {
        val data = read()
        data.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("text", text.trim())
                .put("done", false)
                .put("createdAt", System.currentTimeMillis())
        )
        return save(data)
    }

    fun setDone(index: Int, done: Boolean): List<Wish> {
        val data = read()
        data.getJSONObject(index).put("done", done)
        return save(data)
    }

    private fun save(data: JSONArray): List<Wish> {
        storage.edit().putString(key, data.toString()).apply()
        return items(data)
    }
}
