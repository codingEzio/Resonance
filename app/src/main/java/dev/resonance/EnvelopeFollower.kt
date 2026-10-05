package dev.resonance

import kotlin.math.*

/** Causal PCM display envelope; rates are seconds, never a fixed per-frame increment. */
internal class EnvelopeFollower(columns: Int) {
    val values = FloatArray(columns)

    fun update(targets: FloatArray, elapsedSeconds: Float) {
        val dt = elapsedSeconds.coerceIn(0f, .1f)
        for (i in values.indices) {
            val target = targets[i].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            // Fast attack preserves beats; slower release removes quantized flicker.
            val seconds = if (target > values[i]) .025f else .09f
            values[i] += (target - values[i]) * (1f - exp(-dt / seconds))
            if (target == 0f && values[i] < .0001f) values[i] = 0f
        }
    }
}

/**
 * A coherent audio-reactive silhouette, deliberately not an oscilloscope. PCM controls presence,
 * energy and accents; low-frequency movement gives quiet music continuity without making individual
 * columns jitter. True silence closes the gate.
 */
internal class AudioReactiveField(columns: Int) {
    val values = FloatArray(columns)
    private val signal = EnvelopeFollower(columns)
    private val positions =
        FloatArray(columns) { if (columns > 1) it * 2f / (columns - 1) - 1f else 0f }
    private val taper =
        FloatArray(columns) {
            .38f + .62f * sqrt((1f - positions[it] * positions[it]).coerceAtLeast(0f))
        }
    private var energy = 0f
    private var baseline = 0f
    private var presence = 0f
    private var accent = 0f
    private var phase = 0.0

    fun reset() {
        values.fill(0f)
        signal.values.fill(0f)
        energy = 0f
        baseline = 0f
        presence = 0f
        accent = 0f
        phase = 0.0
    }

    fun update(targets: FloatArray, elapsedSeconds: Float) {
        if (values.isEmpty()) return
        val dt = elapsedSeconds.takeIf { it.isFinite() }?.coerceIn(0f, .1f) ?: 0f
        signal.update(targets, dt)
        var squareSum = 0f
        for (i in values.indices) {
            val sample = targets[i].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            squareSum += sample * sample
        }
        val inputEnergy = sqrt(squareSum / values.size)
        fun follow(current: Float, target: Float, seconds: Float) =
            current + (target - current) * (1f - exp(-dt / seconds))
        energy = follow(energy, inputEnergy, if (inputEnergy > energy) .045f else .22f)
        baseline = follow(baseline, energy, 1.1f)
        val accentTarget = ((energy - baseline) / (baseline + .04f)).coerceIn(0f, 1f)
        accent = follow(accent, accentTarget, if (accentTarget > accent) .035f else .24f)

        // A soft gate, not an idle animation: quiet passages stay alive, while digital
        // silence and the noise floor decay completely instead of inventing a signal.
        val presenceTarget = sqrt(((inputEnergy - .0005f) / .008f).coerceIn(0f, 1f))
        presence = follow(presence, presenceTarget, if (presenceTarget > presence) .12f else .23f)
        if (presenceTarget == 0f && presence < .0001f) {
            presence = 0f
            values.fill(0f)
            return
        }
        val loudness = sqrt(energy / (energy + .06f))
        // Phase advances by elapsed time and musical energy, never a fixed frame count.
        // Keep it double precision; wrapping would break continuity of the two flow rates.
        phase += dt * (.9 + 1.35 * loudness + .65 * accent) * presence
        val height = .34f + .43f * loudness + .10f * accent
        val response = 1f - exp(-dt / .055f)
        for (i in values.indices) {
            var local = 0f
            var weights = 0f
            for (offset in -4..4) {
                val weight = (5 - abs(offset)).toFloat()
                local += signal.values[(i + offset).coerceIn(values.indices)] * weight
                weights += weight
            }
            local /= weights
            val detail = ((local - energy) / (energy + .05f)).coerceIn(-.6f, .6f) * .13f
            val x = positions[i]
            val flow = .76 + .15 * sin(x * 3.2 - phase) + .08 * sin(x * 5.1 + phase * .67)
            val breath = .035 * sin(phase * .43)
            val target =
                (presence * taper[i] * (height * (flow + breath) + detail))
                    .toFloat()
                    .coerceIn(0f, 1f)
            values[i] += (target - values[i]) * response
        }
    }
}
