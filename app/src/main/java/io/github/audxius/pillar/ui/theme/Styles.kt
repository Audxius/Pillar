package io.github.audxius.pillar.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.audxius.pillar.data.AppFont
import io.github.audxius.pillar.data.ThemeMode
import io.github.audxius.pillar.data.UiStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A complete look: how the backdrop, panels, chips, buttons and dock are drawn. Colors come from
 * the palette, so each style still respects the accent the user picked.
 */
data class StyleSpec(
    val style: UiStyle,
    val label: String,
    val description: String,
    /** Settings applied when the user switches to this style (they can tweak afterwards). */
    val presetPalette: String,
    val presetMode: ThemeMode,
    val presetFont: AppFont,
    val presetCorner: Int,
)

val StyleSpecs = listOf(
    StyleSpec(UiStyle.GLASS, "Glassmorphism", "Frosted panels floating over soft color", "aurora", ThemeMode.DARK, AppFont.OUTFIT, 24),
    StyleSpec(UiStyle.AERO, "Frutiger Aero", "Glossy aqua, sky, bubbles and greenery", "ocean", ThemeMode.LIGHT, AppFont.NUNITO, 18),
    StyleSpec(UiStyle.Y2K, "Y2K", "Chrome, holographic shine and sparkles", "sakura", ThemeMode.DARK, AppFont.SPACE_GROTESK, 14),
)

fun specFor(style: UiStyle) = StyleSpecs.first { it.style == style }

val LocalStyle = staticCompositionLocalOf { UiStyle.GLASS }

private val Y2kHolo = listOf(Color(0xFFFF6AD5), Color(0xFFC774E8), Color(0xFFAD8CFF), Color(0xFF8795E8), Color(0xFF94D0FF), Color(0xFF7CFFCB), Color(0xFFFF6AD5))
private val Chrome = listOf(Color(0xFFFFFFFF), Color(0xFFDCE3EE), Color(0xFF9AA6BC), Color(0xFFE9EEF7), Color(0xFFB6C0D3))

/** Panel surface for the current style: fill, rim and highlight. */
@Composable
fun Modifier.styledSurface(shape: Shape, strong: Boolean = false): Modifier {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS -> this
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = if (strong) 0.16f else 0.10f), Color.White.copy(alpha = if (strong) 0.08f else 0.04f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.55f)))
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.9f), Color.White.copy(alpha = if (dark) 0.06f else 0.4f))), shape)
        UiStyle.AERO -> this
            .shadow(if (strong) 10.dp else 6.dp, shape, ambientColor = Color(0xFF2A8FD6), spotColor = Color(0xFF2A8FD6))
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF2B4D6E).copy(alpha = 0.85f), Color(0xFF13283D).copy(alpha = 0.85f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), Color(0xFFEAF6FF).copy(alpha = 0.85f)))
            )
            .drawWithContent {
                drawContent()
                // The glossy top half that defines the Aero look.
                drawRect(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.18f else 0.55f), 0.45f to Color.White.copy(alpha = 0f)),
                    size = Size(size.width, size.height * 0.5f),
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF7CC8FF).copy(alpha = 0.8f))), shape)
        UiStyle.Y2K -> this
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF1A1030).copy(alpha = 0.94f), Color(0xFF0B0716).copy(alpha = 0.96f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0xFFF6EEFF).copy(alpha = 0.85f)))
            )
            .border(if (strong) 2.dp else 1.5.dp, Brush.linearGradient(Y2kHolo), shape)
    }
}

/** Brush and text color for the main call-to-action (Play) in the current style. */
@Composable
fun primaryActionColors(): Pair<Brush, Color> {
    val style = LocalStyle.current
    val p = LocalPalette.current
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return when (style) {
        UiStyle.GLASS -> Brush.horizontalGradient(listOf(primary, lerp(primary, p.tertiary, 0.6f))) to primary.onColor()
        UiStyle.AERO -> Brush.verticalGradient(listOf(Color(0xFF8BEBFF), Color(0xFF29B6F6), Color(0xFF0277BD))) to Color.White
        UiStyle.Y2K -> Brush.linearGradient(Chrome) to Color(0xFF241634)
    }
}

/** Extra gloss/rim drawn over the primary action surface. */
@Composable
fun Modifier.primaryActionFinish(shape: Shape): Modifier = when (LocalStyle.current) {
    UiStyle.GLASS -> this.border(1.dp, Color.White.copy(alpha = 0.35f), shape)
    UiStyle.AERO -> this
        .drawWithContent {
            drawContent()
            drawRoundRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.75f), 1f to Color.White.copy(alpha = 0.05f)),
                topLeft = Offset(size.width * 0.04f, size.height * 0.06f),
                size = Size(size.width * 0.92f, size.height * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
        }
        .border(1.dp, Color(0xFF01579B).copy(alpha = 0.6f), shape)
    UiStyle.Y2K -> this.border(1.5.dp, Brush.linearGradient(Y2kHolo), shape)
}

/** Chip/pill colors: (background, content, border). */
@Composable
fun chipColors(selected: Boolean): Triple<Brush, Color, Brush> {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS -> if (selected) Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.92f else 1f)), Color(0xFF15131C), SolidBrush(Color.White))
        else Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.08f else 0.5f)), if (dark) Color.White else Color(0xFF15131C), SolidBrush(Color.White.copy(alpha = if (dark) 0.2f else 0.8f)))
        UiStyle.AERO -> if (selected) Triple(Brush.verticalGradient(listOf(Color(0xFF8BEBFF), Color(0xFF1E9BE0))), Color.White, SolidBrush(Color(0xFF0277BD)))
        else Triple(Brush.verticalGradient(listOf(Color.White, Color(0xFFE3F3FF))), Color(0xFF0B3C5D), SolidBrush(Color(0xFF9AD4FF)))
        UiStyle.Y2K -> if (selected) Triple(Brush.linearGradient(Chrome), Color(0xFF241634), Brush.linearGradient(Y2kHolo))
        else Triple(SolidBrush(Color.Black.copy(alpha = 0.35f)), Color.White, Brush.linearGradient(Y2kHolo.map { it.copy(alpha = 0.7f) }))
    }.let { if (!dark && style == UiStyle.Y2K && !selected) Triple(SolidBrush(Color.White.copy(alpha = 0.7f)), Color(0xFF241634), it.third) else it }
}

@Suppress("FunctionName")
fun SolidBrush(c: Color): Brush = Brush.linearGradient(listOf(c, c))

/** Full-screen backdrop for the current style. */
@Composable
fun StyleBackdrop(primary: Color, secondary: Color, tertiary: Color, animate: Boolean) {
    when (LocalStyle.current) {
        UiStyle.GLASS -> GlassMesh(primary, secondary, tertiary, animate)
        UiStyle.AERO -> AeroSky(animate)
        UiStyle.Y2K -> Y2kHoloSpace(primary, animate)
    }
}

@Composable
private fun drift(animate: Boolean, ms: Int, label: String): Float {
    if (!animate) return 0.5f
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = label)
    return v
}

@Composable
private fun GlassMesh(a: Color, b: Color, c: Color, animate: Boolean) {
    val dark = LocalIsDark.current
    val p1 = drift(animate, 21000, "g1")
    val p2 = drift(animate, 27000, "g2")
    val base = if (dark) Color(0xFF0B0A16) else Color(0xFFEEF0FA)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        val r = maxOf(size.width, size.height)
        val k = if (dark) 1f else 0.6f
        fun blob(color: Color, x: Float, y: Float, radius: Float, s: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = s * k), color.copy(alpha = 0f)), center = Offset(x, y), radius = radius),
            radius = radius, center = Offset(x, y),
        )
        blob(a, size.width * (0.15f + 0.3f * p1), size.height * 0.12f, r * 0.7f, 0.55f)
        blob(c, size.width * (0.95f - 0.25f * p2), size.height * 0.45f, r * 0.55f, 0.38f)
        blob(b, size.width * (0.3f + 0.3f * p2), size.height * (0.85f + 0.08f * p1), r * 0.6f, 0.35f)
    }
}

@Composable
private fun AeroSky(animate: Boolean) {
    val dark = LocalIsDark.current
    val p = drift(animate, 30000, "aero")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (dark) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF06284A), Color(0xFF0B4F7A), Color(0xFF0E6E5A))))
        } else {
            drawRect(Brush.verticalGradient(0f to Color(0xFF39B7FF), 0.45f to Color(0xFFBFE9FF), 0.72f to Color(0xFFF2FBFF), 1f to Color(0xFFB8E986)))
        }
        // Rolling green hills at the bottom.
        fun hill(yBase: Float, amp: Float, color: Brush, phase: Float) {
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, yBase)
                var x = 0f
                while (x <= w) {
                    lineTo(x, yBase - amp * sin((x / w) * PI.toFloat() * 1.6f + phase).toFloat())
                    x += w / 40f
                }
                lineTo(w, h); close()
            }
            drawPath(path, color)
        }
        hill(h * 0.86f, h * 0.04f, Brush.verticalGradient(listOf(Color(0xFF9BE15D).copy(alpha = if (dark) 0.35f else 0.9f), Color(0xFF3FA34D).copy(alpha = if (dark) 0.35f else 0.9f))), 0.6f)
        hill(h * 0.92f, h * 0.03f, Brush.verticalGradient(listOf(Color(0xFF6CC24A).copy(alpha = if (dark) 0.4f else 1f), Color(0xFF237A3B).copy(alpha = if (dark) 0.4f else 1f))), 2.2f)
        // Sun glow and light rays.
        val sun = Offset(w * 0.82f, h * 0.08f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)), center = sun, radius = w * 0.45f), radius = w * 0.45f, center = sun)
        // Floating soap bubbles.
        val bubbles = listOf(0.12f to 0.32f, 0.28f to 0.6f, 0.7f to 0.38f, 0.86f to 0.68f, 0.5f to 0.2f, 0.4f to 0.78f)
        bubbles.forEachIndexed { i, (bx, by) ->
            val rr = w * (0.05f + 0.025f * (i % 3))
            val c = Offset(w * bx, h * by - h * 0.03f * sin((p + i * 0.3f) * 2 * PI.toFloat()))
            drawBubble(c, rr)
        }
    }
}

private fun DrawScope.drawBubble(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0f), Color(0xFFBFF3FF).copy(alpha = 0.18f), Color.White.copy(alpha = 0.55f)), center = c, radius = r), radius = r, center = c)
    drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.22f, center = Offset(c.x - r * 0.4f, c.y - r * 0.4f))
}

@Composable
private fun Y2kHoloSpace(accent: Color, animate: Boolean) {
    val dark = LocalIsDark.current
    val p = drift(animate, 16000, "y2k")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(if (dark) Brush.verticalGradient(listOf(Color(0xFF07020F), Color(0xFF1A0B2E), Color(0xFF0A0418))) else Brush.verticalGradient(listOf(Color(0xFFFDF2FF), Color(0xFFEDE4FF), Color(0xFFE2F6FF))))
        val k = if (dark) 1f else 0.7f
        fun glow(color: Color, x: Float, y: Float, r: Float, s: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = s * k), color.copy(alpha = 0f)), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y),
        )
        glow(Color(0xFFFF6AD5), w * (0.1f + 0.4f * p), h * 0.15f, w * 0.9f, 0.45f)
        glow(Color(0xFF7CFFCB), w * 0.95f, h * (0.4f + 0.2f * p), w * 0.7f, 0.28f)
        glow(Color(0xFF94D0FF), w * 0.2f, h * 0.8f, w * 0.8f, 0.3f)
        glow(accent, w * 0.6f, h * 0.6f, w * 0.6f, 0.2f)
        // Retro perspective grid fading in at the bottom.
        val horizon = h * 0.78f
        val gridColor = (if (dark) Color(0xFFFF6AD5) else Color(0xFFB45CFF)).copy(alpha = 0.22f)
        for (i in -10..10) {
            drawLine(gridColor, Offset(w / 2 + i * w * 0.03f, horizon), Offset(w / 2 + i * w * 0.22f, h), strokeWidth = 1.5f)
        }
        var y = horizon; var step = h * 0.012f
        while (y < h) { drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.5f); y += step; step *= 1.35f }
        // Four-point sparkles.
        val stars = listOf(0.12f to 0.1f, 0.82f to 0.18f, 0.35f to 0.32f, 0.9f to 0.52f, 0.08f to 0.6f, 0.62f to 0.08f, 0.5f to 0.45f)
        stars.forEachIndexed { i, (sx, sy) ->
            val twinkle = 0.5f + 0.5f * sin((p * 2 + i * 0.7f) * PI.toFloat())
            drawSparkle(Offset(w * sx, h * sy), w * (0.006f + 0.004f * (i % 3)) * (0.7f + 0.3f * twinkle), (if (dark) Color.White else Color(0xFFB45CFF)).copy(alpha = 0.25f + 0.35f * twinkle))
        }
    }
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val ang = i * PI / 4
        val rad = if (i % 2 == 0) r * 2.4f else r * 0.35f
        val x = c.x + (rad * cos(ang)).toFloat(); val yy = c.y + (rad * sin(ang)).toFloat()
        if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
    }
    path.close()
    drawPath(path, color)
}
