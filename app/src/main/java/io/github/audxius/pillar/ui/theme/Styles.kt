package io.github.audxius.pillar.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
    StyleSpec(UiStyle.AERO, "Frutiger Aero", "Vista-era glass, sky, aurora light and bubbles", "aero", ThemeMode.LIGHT, AppFont.NUNITO, 14),
    StyleSpec(UiStyle.Y2K, "Y2K", "Dreamcast, PS2 and Jet Set Radio era", "y2k", ThemeMode.DARK, AppFont.EXO, 12),
)

fun specFor(style: UiStyle) = StyleSpecs.first { it.style == style }

val LocalStyle = staticCompositionLocalOf { UiStyle.GLASS }

// Y2K console-era colors.
private val PsBlue = Color(0xFF2E6BFF)
private val JsrLime = Color(0xFFB4FF39)
private val DcOrange = Color(0xFFFF7A00)
private val Ink = Color(0xFF07080F)

/** Draws a hard, offset cartoon shadow behind a shape (Jet Set Radio sticker look). */
private fun Modifier.hardShadow(shape: Shape, dx: Float = 4f, dy: Float = 4f, color: Color = Ink) = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(dx.dp.toPx(), dy.dp.toPx()) { drawOutline(outline, color) }
}

/** Panel surface for the current style: fill, rim and highlight. */
@Composable
fun Modifier.styledSurface(shape: Shape, strong: Boolean = false): Modifier {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return when (style) {
        UiStyle.GLASS -> this
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = if (strong) 0.16f else 0.10f), Color.White.copy(alpha = if (strong) 0.08f else 0.04f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.55f)))
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.9f), Color.White.copy(alpha = if (dark) 0.06f else 0.4f))), shape)

        UiStyle.AERO -> this
            .shadow(if (strong) 12.dp else 8.dp, shape, ambientColor = Color(0xFF0A6CD6), spotColor = Color(0xFF0A6CD6))
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF2A5A86).copy(alpha = 0.72f), Color(0xFF0E2A47).copy(alpha = 0.78f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.62f), Color(0xFFDDF2FF).copy(alpha = 0.48f)))
            )
            .drawWithContent {
                // Vista glass: a bright reflection band across the top, capped so tall panels don't get a huge sheen.
                val band = minOf(size.height * 0.45f, 30.dp.toPx())
                drawRect(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.22f else 0.6f), 1f to Color.White.copy(alpha = 0f), endY = band),
                    size = Size(size.width, band),
                )
                drawContent()
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color(0xFF7CC6F5))), shape)

        UiStyle.Y2K -> this
            .hardShadow(shape)
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF13214A), Color(0xFF0A1330)))
                else Brush.verticalGradient(listOf(Color.White, Color(0xFFEAF0FF)))
            )
            .border(2.dp, if (strong) primary else if (dark) Color(0xFFC8DCFF).copy(alpha = 0.55f) else Ink, shape)
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
        UiStyle.AERO -> Brush.verticalGradient(0f to Color(0xFFB8F2FF), 0.45f to Color(0xFF39C3F5), 0.55f to Color(0xFF0B86D6), 1f to Color(0xFF29B4F0)) to Color.White
        UiStyle.Y2K -> Brush.linearGradient(listOf(Color(0xFFFFC400), DcOrange)) to Ink
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
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.85f), 1f to Color.White.copy(alpha = 0.08f)),
                topLeft = Offset(size.width * 0.03f, size.height * 0.04f),
                size = Size(size.width * 0.94f, size.height * 0.36f),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        .border(1.dp, Color(0xFF05578F), shape)
    UiStyle.Y2K -> this.border(2.5.dp, Ink, shape)
}

/** Wraps the primary action for styles that need something outside its bounds (Y2K's hard shadow). */
@Composable
fun Modifier.primaryActionOuter(shape: Shape): Modifier =
    if (LocalStyle.current == UiStyle.Y2K) this.hardShadow(shape, 5f, 5f) else this

/** Chip/pill colors: (background, content, border). */
@Composable
fun chipColors(selected: Boolean): Triple<Brush, Color, Brush> {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS -> if (selected) Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.92f else 1f)), Color(0xFF15131C), SolidBrush(Color.White))
        else Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.08f else 0.5f)), if (dark) Color.White else Color(0xFF15131C), SolidBrush(Color.White.copy(alpha = if (dark) 0.2f else 0.8f)))
        UiStyle.AERO -> if (selected) Triple(Brush.verticalGradient(0f to Color(0xFFB8F2FF), 0.5f to Color(0xFF39C3F5), 0.51f to Color(0xFF0B86D6), 1f to Color(0xFF29B4F0)), Color.White, SolidBrush(Color(0xFF05578F)))
        else Triple(Brush.verticalGradient(0f to Color.White, 0.5f to Color(0xFFF4FAFF), 0.51f to Color(0xFFE2F1FC), 1f to Color(0xFFEFF8FF)), Color(0xFF0D3B66), SolidBrush(Color(0xFF8CC7EE)))
        UiStyle.Y2K -> if (selected) Triple(SolidBrush(JsrLime), Ink, SolidBrush(Ink))
        else if (dark) Triple(SolidBrush(Color(0xFF13214A)), Color.White, SolidBrush(Color(0xFFC8DCFF).copy(alpha = 0.5f)))
        else Triple(SolidBrush(Color.White), Ink, SolidBrush(Ink))
    }
}

@Suppress("FunctionName")
fun SolidBrush(c: Color): Brush = Brush.linearGradient(listOf(c, c))

/** Full-screen backdrop for the current style. */
@Composable
fun StyleBackdrop(primary: Color, secondary: Color, tertiary: Color, animate: Boolean) {
    when (LocalStyle.current) {
        UiStyle.GLASS -> GlassMesh(primary, secondary, tertiary, animate)
        UiStyle.AERO -> AeroSky(animate)
        UiStyle.Y2K -> ConsoleEra(animate)
    }
}

@Composable
private fun drift(animate: Boolean, ms: Int, label: String, linear: Boolean = false): Float {
    if (!animate) return 0.5f
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(ms, easing = if (linear) LinearEasing else FastOutSlowInEasing), if (linear) RepeatMode.Restart else RepeatMode.Reverse),
        label = label,
    )
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

// ---- Frutiger Aero ---------------------------------------------------------------------------

@Composable
private fun AeroSky(animate: Boolean) {
    val dark = LocalIsDark.current
    val p = drift(animate, 26000, "aero")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // Deep blue sky fading to a bright horizon.
        drawRect(
            if (dark) Brush.verticalGradient(listOf(Color(0xFF021B3D), Color(0xFF07407A), Color(0xFF0B6A8C)))
            else Brush.verticalGradient(0f to Color(0xFF0A6CD6), 0.38f to Color(0xFF3AA8F2), 0.7f to Color(0xFFA9E1FF), 1f to Color(0xFFEAF9FF))
        )
        // Sweeping aurora light ribbons (Windows Vista wallpaper).
        fun ribbon(y0: Float, amp: Float, thickness: Float, color: Color, phase: Float) {
            val path = Path()
            val steps = 48
            for (i in 0..steps) {
                val x = w * i / steps
                val y = y0 + amp * sin((i.toFloat() / steps) * PI.toFloat() * 1.3f + phase)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in steps downTo 0) {
                val x = w * i / steps
                val y = y0 + thickness + amp * 0.8f * sin((i.toFloat() / steps) * PI.toFloat() * 1.3f + phase + 0.35f)
                path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)), startY = y0 - amp, endY = y0 + thickness + amp))
        }
        ribbon(h * 0.30f, h * 0.06f, h * 0.10f, Color.White.copy(alpha = if (dark) 0.18f else 0.45f), 0.4f + p * 0.6f)
        ribbon(h * 0.40f, h * 0.05f, h * 0.06f, Color(0xFF9DFFB0).copy(alpha = if (dark) 0.22f else 0.4f), 1.6f + p * 0.5f)
        ribbon(h * 0.22f, h * 0.04f, h * 0.05f, Color(0xFFB9F0FF).copy(alpha = if (dark) 0.15f else 0.35f), 2.6f - p * 0.4f)
        // Sun and lens flare.
        val sun = Offset(w * 0.18f, h * 0.07f)
        drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.9f), 0.25f to Color.White.copy(alpha = 0.35f), 1f to Color.White.copy(alpha = 0f), center = sun, radius = w * 0.3f), radius = w * 0.3f, center = sun)
        listOf(0.25f to 0.05f, 0.42f to 0.03f, 0.6f to 0.07f).forEach { (t, r) ->
            val c = Offset(sun.x + (w * 0.9f - sun.x) * t, sun.y + (h * 0.5f - sun.y) * t)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = w * r, center = c)
        }
        // Glossy green hill with a bright rim.
        val hill = Path().apply {
            moveTo(0f, h); lineTo(0f, h * 0.84f)
            cubicTo(w * 0.3f, h * 0.76f, w * 0.7f, h * 0.78f, w, h * 0.86f)
            lineTo(w, h); close()
        }
        drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF9BE15D), Color(0xFF3FA535), Color(0xFF1E6B2A)), startY = h * 0.76f, endY = h))
        drawPath(hill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), startY = h * 0.76f, endY = h * 0.83f))
        // A few crisp soap bubbles.
        listOf(Triple(0.82f, 0.30f, 0.07f), Triple(0.12f, 0.52f, 0.045f), Triple(0.68f, 0.62f, 0.035f), Triple(0.9f, 0.7f, 0.025f)).forEachIndexed { i, (bx, by, br) ->
            val r = w * br
            val c = Offset(w * bx, h * by - h * 0.02f * sin((p + i * 0.25f) * 2 * PI.toFloat()))
            drawBubble(c, r)
        }
    }
}

private fun DrawScope.drawBubble(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(0.6f to Color.White.copy(alpha = 0.02f), 0.92f to Color(0xFFCFF6FF).copy(alpha = 0.35f), 1f to Color.White.copy(alpha = 0.8f), center = c, radius = r), radius = r, center = c)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.18f, center = Offset(c.x - r * 0.42f, c.y - r * 0.42f))
    drawArc(Color.White.copy(alpha = 0.6f), 200f, 70f, false, topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f), size = Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.08f))
}

// ---- Y2K console era ---------------------------------------------------------------------------

@Composable
private fun ConsoleEra(animate: Boolean) {
    val dark = LocalIsDark.current
    val p = drift(animate, 14000, "y2k", linear = true)
    val pulse = drift(animate, 3200, "y2kPulse")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(
            if (dark) Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF06173D), Color(0xFF020617)))
            else Brush.verticalGradient(listOf(Color(0xFFEFF4FF), Color(0xFFD9E6FF), Color(0xFFF4F8FF)))
        )
        // PS2 boot-screen light pillars rising from the floor.
        val heights = listOf(0.30f, 0.52f, 0.22f, 0.64f, 0.38f, 0.48f, 0.26f, 0.58f, 0.34f)
        heights.forEachIndexed { i, hf ->
            val x = w * (0.06f + i * 0.11f)
            val ph = h * hf * (0.85f + 0.15f * sin((pulse + i * 0.37f) * PI.toFloat()))
            val bw = w * 0.035f
            drawRect(
                Brush.verticalGradient(listOf(PsBlue.copy(alpha = 0f), PsBlue.copy(alpha = if (dark) 0.55f else 0.35f), Color(0xFF9FD4FF).copy(alpha = if (dark) 0.75f else 0.5f)), startY = h - ph, endY = h),
                topLeft = Offset(x - bw / 2, h - ph), size = Size(bw, ph),
            )
            drawRect(Color.White.copy(alpha = if (dark) 0.5f else 0.7f), topLeft = Offset(x - bw * 0.08f, h - ph), size = Size(bw * 0.16f, ph))
        }
        // Floating outlined cubes.
        listOf(Triple(0.9f, 0.08f, 0.045f), Triple(0.9f, 0.5f, 0.035f), Triple(0.07f, 0.58f, 0.03f)).forEachIndexed { i, (cx, cy, cs) ->
            val c = Offset(w * cx, h * cy + h * 0.015f * sin((p * 2 + i) * PI.toFloat()))
            val s = w * cs
            rotate(degrees = p * 360f * (if (i % 2 == 0) 1 else -1) + i * 20f, pivot = c) {
                drawRect(Color(0xFF9FD4FF).copy(alpha = if (dark) 0.5f else 0.6f), topLeft = Offset(c.x - s, c.y - s), size = Size(s * 2, s * 2), style = Stroke(3f))
                drawRect(Color(0xFF9FD4FF).copy(alpha = 0.12f), topLeft = Offset(c.x - s, c.y - s), size = Size(s * 2, s * 2))
            }
        }
        // Jet Set Radio halftone burst in the corner.
        val origin = Offset(w * 0.98f, h * 0.02f)
        val dot = w * 0.026f
        var y = 0f
        while (y < h * 0.3f) {
            var x = w * 0.5f
            while (x < w) {
                val d = (Offset(x, y) - origin).getDistance() / (w * 0.6f)
                if (d < 1f) drawCircle(DcOrange.copy(alpha = if (dark) 0.55f else 0.4f), radius = dot * 0.5f * (1f - d), center = Offset(x, y))
                x += dot
            }
            y += dot
        }
        // Faint Dreamcast swirl.
        val swirl = Path()
        val sc = Offset(w * 0.12f, h * 0.9f)
        for (i in 0..120) {
            val t = i / 120f
            val ang = t * 4.2f * PI.toFloat()
            val r = w * 0.03f + t * w * 0.2f
            val pt = Offset(sc.x + r * cos(ang), sc.y + r * sin(ang))
            if (i == 0) swirl.moveTo(pt.x, pt.y) else swirl.lineTo(pt.x, pt.y)
        }
        drawPath(swirl, DcOrange.copy(alpha = if (dark) 0.35f else 0.45f), style = Stroke(w * 0.012f))
    }
}
