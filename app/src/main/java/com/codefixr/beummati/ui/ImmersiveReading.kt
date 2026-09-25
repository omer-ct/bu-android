package com.codefixr.beummati.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full-screen reading: hides app chrome (top bar, mode bars, bottom nav) and system bars
 * so ayah / hadith text can fill the display — Islam One–style.
 */
object ImmersiveReading {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(value: Boolean) {
        _active.value = value
    }

    fun toggle() {
        _active.value = !_active.value
    }
}

/**
 * Bind immersive mode for the current reading screen: hide/show system bars, and always
 * leave immersive when the screen is disposed (back / navigate away).
 */
@Composable
fun BindImmersiveReading() {
    val immersive by ImmersiveReading.active.collectAsState()
    val view = LocalView.current

    DisposableEffect(Unit) {
        onDispose { ImmersiveReading.set(false) }
    }

    DisposableEffect(immersive) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
