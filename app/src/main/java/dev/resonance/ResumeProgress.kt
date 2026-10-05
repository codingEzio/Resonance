package dev.resonance

import android.content.SharedPreferences

/** File progress is independent of queue membership and playback source. */
object ResumeProgress {
    private fun key(mediaId: String) = "resume_$mediaId"

    fun checkpoint(positionMs: Long, durationMs: Long, completed: Boolean): Long =
        if (completed || positionMs < 0 || (durationMs > 0 && positionMs >= durationMs)) 0
        else positionMs

    fun position(preferences: SharedPreferences, mediaId: String, durationMs: Long = 0): Long =
        checkpoint(preferences.getLong(key(mediaId), 0), durationMs, false)

    fun save(
        editor: SharedPreferences.Editor,
        mediaId: String?,
        positionMs: Long,
        durationMs: Long = 0,
        completed: Boolean = false,
    ) {
        if (!mediaId.isNullOrEmpty())
            editor.putLong(key(mediaId), checkpoint(positionMs, durationMs, completed))
    }
}
