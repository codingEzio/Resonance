package dev.resonance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.withContext

internal data class MotionPolicy(
    val enabled: Boolean = true,
    val respectSystem: Boolean = true,
    val systemScale: Float = 1f,
) {
    val animate: Boolean
        get() = enabled && (!respectSystem || systemScale > 0f)
}

internal val LocalMotionPolicy = staticCompositionLocalOf { MotionPolicy() }

@Composable
internal fun rememberMotionPolicy(): MotionPolicy {
    return MotionPolicy(enabled = true, respectSystem = false, systemScale = 1f)
}

private object PlaybackAnimationScale : MotionDurationScale {
    override val scaleFactor = 1f
}

/**
 * Override only Resonance's playback motion; Android and native control animations keep their
 * policy.
 */
@Composable
internal fun rememberMotionFloat(target: Float, animation: AnimationSpec<Float>): State<Float> {
    val policy = LocalMotionPolicy.current
    val value = remember { Animatable(target) }
    LaunchedEffect(target, policy.enabled, policy.respectSystem, policy.systemScale, animation) {
        if (!policy.animate) value.snapTo(target)
        else if (policy.respectSystem) value.animateTo(target, animation)
        else withContext(PlaybackAnimationScale) { value.animateTo(target, animation) }
    }
    return value.asState()
}
