package dev.resonance

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.*

@Composable
fun rememberForeground(): Boolean {
    val owner = LocalActivity.current as? LifecycleOwner
    var active by
        remember(owner) {
            mutableStateOf(
                owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
            )
        }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return active
}

/** Music-driven dot field: real PCM energy with a continuous, art-directed silhouette. */
@Composable
fun PlaybackGlyph(
    playing: Boolean,
    modifier: Modifier = Modifier,
    motion: Boolean = true,
    immersive: Boolean = false,
) {
    val foreground = rememberForeground()
    val animate = foreground && motion && LocalMotionPolicy.current.animate
    val targets = remember { FloatArray(35) }
    val follower = remember { AudioReactiveField(35) }
    val samples = follower.values
    val frame = remember { mutableLongStateOf(0L) }
    var signalReady by remember { mutableStateOf(false) }
    val live = playing && animate
    DisposableEffect(live) {
        val observation = if (live) AudioWaveform.observe() else null
        onDispose { observation?.close() }
    }
    LaunchedEffect(live) {
        signalReady = false
        if (live) {
            follower.reset()
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    signalReady = AudioWaveform.read(targets)
                    if (previous != 0L) follower.update(targets, (now - previous) / 1_000_000_000f)
                    previous = now
                    frame.longValue++
                }
            }
        }
    }
    // Visible audio motion opts out of Android's normal-rate power policy. OEM
    // scheduling can still limit the actual rate; restore both settings on exit.
    val activityWindow = LocalActivity.current?.window
    DisposableEffect(live, activityWindow) {
        val previousRate = activityWindow?.attributes?.preferredRefreshRate ?: 0f
        val previousBalance =
            if (Build.VERSION.SDK_INT >= 35) activityWindow?.isFrameRatePowerSavingsBalanced
            else null
        if (live)
            activityWindow?.let { window ->
                window.attributes =
                    window.attributes.apply {
                        preferredRefreshRate = 120f
                    }
                if (Build.VERSION.SDK_INT >= 35) window.isFrameRatePowerSavingsBalanced = false
            }
        onDispose {
            if (live)
                activityWindow?.let { window ->
                    window.attributes =
                        window.attributes.apply {
                            preferredRefreshRate = previousRate
                        }
                    if (Build.VERSION.SDK_INT >= 35 && previousBalance != null)
                        window.isFrameRatePowerSavingsBalanced = previousBalance
                }
        }
    }
    val activity =
        rememberMotionFloat(
            if (live && signalReady) 1f else 0f,
            if (animate) tween(350, easing = FastOutSlowInEasing) else snap(),
        )
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Box(
        modifier
            .then(if (live) Modifier.preferredFrameRate(120f) else Modifier)
            .clipToBounds()
            .drawWithCache {
                val step = min(size.width / 35f, size.height / 21f)
                val rows = if (immersive) (size.height / step / 2).toInt() else 10
                val waveRows = if (immersive) (rows * .66f).coerceAtLeast(10f) else 9f
                val center = Offset(size.width / 2, size.height * if (immersive) .36f else .5f)
                val all = mutableListOf<Float>()
                val disc = mutableListOf<Float>()
                val arrow = mutableListOf<Float>()
                for (column in -17..17) for (row in -rows..rows) {
                    val x = center.x + column * step
                    val y = center.y + row * step
                    all.add(x)
                    all.add(y)
                    val radius = sqrt((column * column + row * row).toFloat())
                    if (radius in (if (immersive) 10.3f..13.5f else 6.3f..8.5f)) {
                        disc.add(x)
                        disc.add(y)
                    }
                    if (column in -3..5 && abs(row) <= (5 - column) * .65f) {
                        arrow.add(x)
                        arrow.add(y)
                    }
                }
                val backgroundPoints = all.toFloatArray()
                val discPoints = disc.toFloatArray()
                val arrowPoints = arrow.toFloatArray()
                val inkPoints = FloatArray(backgroundPoints.size)
                val redPoints = FloatArray(backgroundPoints.size)
                val edges = Array(16) { FloatArray(35 * 4) }
                val edgeCounts = IntArray(16)
                val paint =
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        strokeWidth = step * .62f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                onDrawBehind {
                    frame.longValue // Invalidate drawing only; no frame-driven recomposition.
                    val blend = activity.value
                    val canvas = drawContext.canvas.nativeCanvas
                    fun dots(
                        points: FloatArray,
                        count: Int,
                        color: androidx.compose.ui.graphics.Color,
                        alpha: Float,
                    ) {
                        if (count == 0 || alpha <= 0f) return
                        paint.color = color.toArgb()
                        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                        canvas.drawPoints(points, 0, count, paint)
                    }
                    dots(backgroundPoints, backgroundPoints.size, ink, .045f + blend * .075f)
                    dots(discPoints, discPoints.size, ink, 1f - blend)
                    dots(arrowPoints, arrowPoints.size, accent, 1f - blend)
                    if (blend > 0f) {
                        var blackCount = 0
                        var redCount = 0
                        edgeCounts.fill(0)
                        for (column in -17..17) {
                            // Shared energy and broad spatial flow keep one continuous contour.
                            // The field itself closes to zero when the PCM signal is silent.
                            val amplitude = samples[column + 17].coerceIn(0f, 1f)
                            if (amplitude <= .001f) continue
                            val exactHeight = waveRows * amplitude
                            val height = exactHeight.toInt()
                            val fraction = exactHeight - height
                            // Fade the next pixel row instead of snapping whole rows on/off.
                            val edgeBucket = (fraction * 8).toInt().coerceIn(0, 7)
                            if (fraction > .01f) {
                                val bucket = edgeBucket + if (abs(column) < 3) 8 else 0
                                for (edge in 0..1) {
                                    val sign = if (edge == 0) -1 else 1
                                    edges[bucket][edgeCounts[bucket]++] = center.x + column * step
                                    edges[bucket][edgeCounts[bucket]++] =
                                        center.y + sign * (height + 1) * step
                                }
                            }
                            for (row in -height..height) {
                                val px = center.x + column * step
                                val py = center.y + row * step
                                if (abs(column) < 3) {
                                    redPoints[redCount++] = px
                                    redPoints[redCount++] = py
                                } else {
                                    inkPoints[blackCount++] = px
                                    inkPoints[blackCount++] = py
                                }
                            }
                        }
                        dots(inkPoints, blackCount, ink, blend)
                        dots(redPoints, redCount, accent, blend)
                        for (bucket in edges.indices) dots(
                            edges[bucket],
                            edgeCounts[bucket],
                            if (bucket < 8) ink else accent,
                            blend * ((bucket % 8 + 1) / 8f),
                        )
                    }
                }
            }
    )
}

@Composable
fun PlaybackSymbol(playing: Boolean, modifier: Modifier = Modifier, motion: Boolean = true) {
    val enabled = rememberForeground() && motion && LocalMotionPolicy.current.animate
    val state =
        rememberMotionFloat(
            if (playing) 1f else 0f,
            if (enabled) tween(220) else snap(),
        )
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val value = state.value
        val unit = min(size.width, size.height) / 24f
        val path =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(7 * unit, 3 * unit)
                lineTo(22 * unit, 12 * unit)
                lineTo(7 * unit, 21 * unit)
                close()
            }
        drawPath(path, color.copy(alpha = 1f - value))
        for (left in listOf(5f, 15f)) drawRoundRect(
            color.copy(alpha = value),
            Offset(left * unit, 3 * unit),
            androidx.compose.ui.geometry.Size(4 * unit, 18 * unit),
            androidx.compose.ui.geometry.CornerRadius(unit),
        )
    }
}

@Composable
fun SkipSymbol(next: Boolean, modifier: Modifier = Modifier) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val u = min(size.width, size.height) / 24f
        fun x(value: Float) = (if (next) value else 24f - value) * u
        val path =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(x(5f), 5 * u)
                lineTo(x(16f), 12 * u)
                lineTo(x(5f), 19 * u)
                close()
            }
        drawPath(path, color)
        drawLine(
            color,
            Offset(x(19f), 5 * u),
            Offset(x(19f), 19 * u),
            2.5f * u,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
