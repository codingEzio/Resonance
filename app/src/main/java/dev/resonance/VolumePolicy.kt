package dev.resonance

object VolumePolicy {
    const val MAX_GAIN = .4f
    const val DEFAULT_GAIN = .3f

    fun output(requested: Float, limit: Float): Float {
        val ceiling = if (limit.isFinite()) limit.coerceIn(0f, MAX_GAIN) else DEFAULT_GAIN
        return if (requested.isNaN()) 0f else requested.coerceIn(0f, ceiling)
    }
}
