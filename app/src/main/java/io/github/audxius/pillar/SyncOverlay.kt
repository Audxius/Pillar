package io.github.audxius.pillar

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.audxius.pillar.data.AppGraph
import io.github.audxius.pillar.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A small pill in the top corner, drawn over the game, while saves sync with Pillar in the
 * background (e.g. you went straight back into a game). Needs "display over other apps" and the
 * setting to be on; inside Pillar the app shows its own indicator instead.
 */
class SyncOverlay(private val context: Context, private val graph: AppGraph, private val scope: CoroutineScope) {
    /** True while one of Pillar's screens is visible. */
    val foreground = MutableStateFlow(false)

    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var label: TextView? = null
    private var spinner: ProgressBar? = null
    private var hideJob: Job? = null

    fun start() {
        scope.launch(Dispatchers.Main) {
            var wasRunning = false
            combine(graph.saveSync.status, foreground, graph.store.settings) { status, fg, s -> Triple(status, fg, s.syncOverlay) }
                .collect { (status, fg, enabled) ->
                    val allowed = enabled && !fg && canDraw()
                    when {
                        status is SyncStatus.Running && allowed -> { show(status.message, busy = true); wasRunning = true }
                        status is SyncStatus.Running -> remove()
                        wasRunning && allowed -> {
                            // Finished while you were playing: say so briefly, then get out of the way.
                            val failed = graph.saveSync.last.value?.error != null
                            show(if (failed) "Save sync failed" else "Saves synced", busy = false)
                            hideJob?.cancel()
                            hideJob = scope.launch(Dispatchers.Main) { delay(2500); remove() }
                            wasRunning = false
                        }
                        else -> { remove(); wasRunning = false }
                    }
                }
        }
    }

    private fun canDraw() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private fun show(text: String, busy: Boolean) {
        hideJob?.cancel()
        if (view == null) {
            val bar = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            }
            val tv = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(7f), dp(14f), dp(7f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20f).toFloat()
                    setColor(0xE6141824.toInt())
                    setStroke(dp(1f), 0x40FFFFFF)
                }
                addView(bar, LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) })
                addView(tv)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(12f); y = dp(36f)
            }
            if (runCatching { wm.addView(root, params) }.isFailure) return
            view = root; label = tv; spinner = bar
        }
        label?.text = text
        spinner?.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun remove() {
        hideJob?.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null; label = null; spinner = null
    }
}
