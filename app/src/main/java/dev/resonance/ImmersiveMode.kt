package dev.resonance

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Window-wide preference; content independently respects camera and touch-safe insets. */
@Composable
internal fun PersistentSystemBars(hidden: Boolean) {
    val context = LocalContext.current
    val activity =
        remember(context) {
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle, hidden) {
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        fun apply() {
            bars?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) bars?.hide(WindowInsetsCompat.Type.systemBars())
            else bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        apply()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
