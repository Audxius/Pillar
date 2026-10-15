package io.github.audxius.pillar

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.audxius.pillar.data.OrientationMode
import io.github.audxius.pillar.ui.PillarRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val graph get() = (application as PillarApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyOrientation(graph.store.settings.value.orientation)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.store.settings.map { it.orientation }.distinctUntilChanged().collect(::applyOrientation)
            }
        }
        setContent { PillarRoot(graph) }
    }

    override fun onStart() {
        super.onStart()
        graph.foreground.value = true
    }

    override fun onStop() {
        super.onStop()
        graph.foreground.value = false
    }

    override fun onResume() {
        super.onResume()
        // Coming back from a game is the moment new achievements or installs show up.
        graph.ra.refresh()
        graph.rommRepo.refresh()
        // Pull the latest saves when you open Pillar, and push new ones when you come back from a game.
        graph.saveSync.syncIfDue()
    }

    private fun applyOrientation(mode: OrientationMode) {
        requestedOrientation = when (mode) {
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    /** Controller support: A confirms like the D-pad center, B goes back. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> null
        }
        if (mapped != null) {
            return super.dispatchKeyEvent(
                KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState,
                    event.deviceId, event.scanCode, event.flags, event.source)
            )
        }
        return super.dispatchKeyEvent(event)
    }
}
