package dev.resonance

object VolumePolicy {
    const val MAX_GAIN = 1f
    const val DEFAULT_GAIN = .3f
    const val DEFAULT_WARNING = .7f

    fun needsConfirmation(current: Float, target: Float, threshold: Float): Boolean =
        current <= threshold && target > threshold
}
