package dev.resonance

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
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
    val enabled by rememberSetting(AppSettings.Motion)
    val respectSystem by rememberSetting(AppSettings.RespectSystemMotion)
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun scale() =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).takeIf {
            it.isFinite() && it >= 0f
        } ?: 1f
    var systemScale by remember(resolver) { mutableFloatStateOf(scale()) }
    val foreground = rememberForeground()
    LaunchedEffect(foreground) { if (foreground) systemScale = scale() }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    systemScale = scale()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return MotionPolicy(enabled, respectSystem, systemScale)
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
