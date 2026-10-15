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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.audxius.pillar.data.AppFont
import io.github.audxius.pillar.data.ThemeMode
import io.github.audxius.pillar.data.UiStyle
import kotlin.math.PI
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
    /** How strongly the focused game's blurred art shows through the style's own backdrop. */
    val artStrength: Float,
)

val StyleSpecs = listOf(
    StyleSpec(UiStyle.GLASS, "Glassmorphism", "Your game's art behind calm, frosted panels", "aurora", ThemeMode.DARK, AppFont.OUTFIT, 24, 0.75f),
    StyleSpec(UiStyle.AERO, "Frutiger Aero", "Vista sky, glossy glass, light rays and bubbles", "aero", ThemeMode.LIGHT, AppFont.NUNITO, 16, 0f),
    StyleSpec(UiStyle.Y2K, "Y2K", "PS2 and Dreamcast menus: grid floor, cubes, chrome", "y2k", ThemeMode.DARK, AppFont.EXO, 10, 0.3f),
)

fun specFor(style: UiStyle) = StyleSpecs.first { it.style == style }

val LocalStyle = staticCompositionLocalOf { UiStyle.GLASS }

// Y2K colors.
private val Cyan = Color(0xFF6FD3FF)
private val Navy = Color(0xFF07152F)

/** Panel surface for the current style: fill, rim and highlight. */
@Composable
fun Modifier.styledSurface(shape: Shape, strong: Boolean = false): Modifier {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS -> this
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = if (strong) 0.13f else 0.085f), Color.White.copy(alpha = if (strong) 0.08f else 0.045f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.6f)))
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.24f else 0.95f), Color.White.copy(alpha = if (dark) 0.05f else 0.45f))), shape)

        UiStyle.AERO -> this
            .shadow(if (strong) 10.dp else 6.dp, shape, ambientColor = Color(0xFF0A5FB8), spotColor = Color(0xFF0A5FB8))
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF2B6A9E).copy(alpha = 0.6f), Color(0xFF0D2F52).copy(alpha = 0.72f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color(0xFFE6F6FF).copy(alpha = 0.5f)))
            )
            .drawWithContent {
                // Vista glass: a bright reflection band across the top, capped so tall panels stay clean.
                val band = minOf(size.height * 0.45f, 26.dp.toPx())
                drawRect(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.25f else 0.7f), 1f to Color.White.copy(alpha = 0f), endY = band),
                    size = Size(size.width, band),
                )
                drawContent()
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f), Color(0xFF9AD6FA))), shape)

        UiStyle.Y2K -> this
            .clip(shape)
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF0D2148).copy(alpha = 0.78f), Navy.copy(alpha = 0.86f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.86f), Color(0xFFE3ECF8).copy(alpha = 0.8f)))
            )
            .border(1.dp, (if (dark) Cyan else Color(0xFF2C6FD1)).copy(alpha = if (strong) 0.6f else 0.38f), shape)
            .then(if (shape == CircleShape) Modifier else Modifier.techBrackets(if (dark) Cyan else Color(0xFF2C6FD1)))
    }
}

/** Little HUD corner brackets on larger panels, like a PS2-era menu. */
private fun Modifier.techBrackets(color: Color) = drawWithContent {
    drawContent()
    if (size.height < 52.dp.toPx()) return@drawWithContent
    val l = 12.dp.toPx(); val i = 5.dp.toPx(); val sw = 2.dp.toPx()
    val c = color.copy(alpha = 0.95f)
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * l, y), sw, StrokeCap.Square)
        drawLine(c, Offset(x, y), Offset(x, y + dy * l), sw, StrokeCap.Square)
    }
    corner(i, i, 1f, 1f); corner(size.width - i, i, -1f, 1f)
    corner(i, size.height - i, 1f, -1f); corner(size.width - i, size.height - i, -1f, -1f)
}

/** Brush and text color for the main call-to-action (Play) in the current style. */
@Composable
fun primaryActionColors(): Pair<Brush, Color> {
    val style = LocalStyle.current
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return when (style) {
        UiStyle.GLASS -> Brush.verticalGradient(listOf(lerp(primary, Color.White, 0.14f), primary)) to primary.onColor()
        UiStyle.AERO -> Brush.verticalGradient(0f to Color(0xFFB8F2FF), 0.45f to Color(0xFF39C3F5), 0.55f to Color(0xFF0B86D6), 1f to Color(0xFF29B4F0)) to Color.White
        // Brushed chrome: bright, a dark band through the middle, bright again.
        UiStyle.Y2K -> Brush.verticalGradient(
            0f to Color(0xFFFFFFFF), 0.42f to Color(0xFFC9D6E8), 0.5f to Color(0xFF8297B6), 0.58f to Color(0xFFD5E1F1), 1f to Color(0xFFF7FAFF)
        ) to Color(0xFF071330)
    }
}

/** Extra gloss/rim drawn over the primary action surface. */
@Composable
fun Modifier.primaryActionFinish(shape: Shape): Modifier = when (LocalStyle.current) {
    UiStyle.GLASS -> this.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))), shape)
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
    UiStyle.Y2K -> this.border(1.5.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF6E86AA))), shape)
}

/** Kept for styles that draw outside the button's bounds; none do at the moment. */
@Composable
fun Modifier.primaryActionOuter(@Suppress("UNUSED_PARAMETER") shape: Shape): Modifier = this

/** Chip/pill colors: (background, content, border). */
@Composable
fun chipColors(selected: Boolean): Triple<Brush, Color, Brush> {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS -> if (selected) Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.92f else 1f)), Color(0xFF15131C), SolidBrush(Color.White))
        else Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.08f else 0.55f)), if (dark) Color.White else Color(0xFF15131C), SolidBrush(Color.White.copy(alpha = if (dark) 0.16f else 0.85f)))
        UiStyle.AERO -> if (selected) Triple(Brush.verticalGradient(0f to Color(0xFFB8F2FF), 0.5f to Color(0xFF39C3F5), 0.51f to Color(0xFF0B86D6), 1f to Color(0xFF29B4F0)), Color.White, SolidBrush(Color(0xFF05578F)))
        else Triple(Brush.verticalGradient(0f to Color.White, 0.5f to Color(0xFFF4FAFF), 0.51f to Color(0xFFE2F1FC), 1f to Color(0xFFEFF8FF)), Color(0xFF0D3B66), SolidBrush(Color(0xFF8CC7EE)))
        UiStyle.Y2K -> if (selected) Triple(SolidBrush(Cyan), Navy, SolidBrush(Color.White))
        else if (dark) Triple(SolidBrush(Navy.copy(alpha = 0.7f)), Color.White, SolidBrush(Cyan.copy(alpha = 0.45f)))
        else Triple(SolidBrush(Color.White.copy(alpha = 0.85f)), Navy, SolidBrush(Color(0xFF2C6FD1).copy(alpha = 0.5f)))
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
        UiStyle.Y2K -> ConsoleMenu(animate)
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

// ---- Glassmorphism ---------------------------------------------------------------------------

@Composable
private fun GlassMesh(a: Color, b: Color, c: Color, animate: Boolean) {
    val dark = LocalIsDark.current
    val p1 = drift(animate, 24000, "g1")
    val p2 = drift(animate, 31000, "g2")
    val base = if (dark) Color(0xFF08080D) else Color(0xFFEEF0F7)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        val r = maxOf(size.width, size.height)
        val k = if (dark) 1f else 0.55f
        fun blob(color: Color, x: Float, y: Float, radius: Float, s: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = s * k), color.copy(alpha = 0f)), center = Offset(x, y), radius = radius),
            radius = radius, center = Offset(x, y),
        )
        // Soft color that the game art (drawn on top) tints further.
        blob(a, size.width * (0.2f + 0.3f * p1), size.height * 0.08f, r * 0.65f, 0.4f)
        blob(c, size.width * (0.9f - 0.2f * p2), size.height * 0.55f, r * 0.5f, 0.22f)
        blob(b, size.width * (0.25f + 0.3f * p2), size.height * 0.95f, r * 0.5f, 0.2f)
    }
}

// ---- Frutiger Aero ---------------------------------------------------------------------------

@Composable
private fun AeroSky(animate: Boolean) {
    val dark = LocalIsDark.current
    val p = drift(animate, 30000, "aero")
    val waves = drift(animate, 9000, "aeroWaves", linear = true)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(
            if (dark) Brush.verticalGradient(0f to Color(0xFF02142E), 0.6f to Color(0xFF06396B), 1f to Color(0xFF0A5C80))
            else Brush.verticalGradient(0f to Color(0xFF5CB8F7), 0.35f to Color(0xFF9ED9FF), 0.7f to Color(0xFFD9F2FF), 1f to Color(0xFFF4FBFF))
        )
        // Soft light rays fanning out from the top-left corner.
        val origin = Offset(-w * 0.1f, -h * 0.05f)
        for (i in 0 until 5) {
            val a0 = 0.18f + i * 0.17f + 0.03f * sin((p + i * 0.3f) * 2 * PI.toFloat())
            val spread = 0.06f
            val len = h * 1.3f
            val ray = Path().apply {
                moveTo(origin.x, origin.y)
                lineTo(origin.x + len * kotlin.math.cos(a0 - spread), origin.y + len * sin(a0 - spread))
                lineTo(origin.x + len * kotlin.math.cos(a0 + spread), origin.y + len * sin(a0 + spread))
                close()
            }
            drawPath(ray, Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.16f), Color.White.copy(alpha = 0f)), center = origin, radius = len))
        }
        // Aurora light ribbon across the sky.
        fun ribbon(y0: Float, amp: Float, thickness: Float, color: Color, phase: Float) {
            val path = Path()
            val steps = 40
            for (i in 0..steps) {
                val x = w * i / steps
                val y = y0 + amp * sin((i.toFloat() / steps) * PI.toFloat() * 1.2f + phase)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in steps downTo 0) {
                val x = w * i / steps
                path.lineTo(x, y0 + thickness + amp * 0.8f * sin((i.toFloat() / steps) * PI.toFloat() * 1.2f + phase + 0.4f))
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)), startY = y0 - amp, endY = y0 + thickness + amp))
        }
        ribbon(h * 0.34f, h * 0.05f, h * 0.08f, Color.White.copy(alpha = if (dark) 0.14f else 0.32f), 0.6f + p * 0.6f)
        ribbon(h * 0.42f, h * 0.04f, h * 0.05f, Color(0xFFA8FFC4).copy(alpha = if (dark) 0.16f else 0.26f), 1.8f + p * 0.5f)
        // Out-of-focus bokeh.
        listOf(
            floatArrayOf(0.85f, 0.16f, 0.13f), floatArrayOf(0.1f, 0.3f, 0.09f), floatArrayOf(0.62f, 0.5f, 0.16f),
            floatArrayOf(0.3f, 0.66f, 0.07f), floatArrayOf(0.92f, 0.72f, 0.1f),
        ).forEachIndexed { i, (bx, by, br) ->
            val c = Offset(w * bx, h * by - h * 0.015f * sin((p + i * 0.21f) * 2 * PI.toFloat()))
            val r = w * br
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.2f), Color.White.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
        }
        // A few crisp bubbles.
        listOf(floatArrayOf(0.8f, 0.36f, 0.05f), floatArrayOf(0.14f, 0.55f, 0.032f), floatArrayOf(0.7f, 0.66f, 0.022f)).forEachIndexed { i, (bx, by, br) ->
            drawBubble(Offset(w * bx, h * by - h * 0.025f * sin((p + i * 0.33f) * 2 * PI.toFloat())), w * br)
        }
        // Glossy water along the bottom.
        val top = h * 0.84f
        val water = Path().apply {
            moveTo(0f, h); lineTo(0f, top)
            val steps = 30
            for (i in 0..steps) {
                val x = w * i / steps
                lineTo(x, top + h * 0.008f * sin((i.toFloat() / steps) * 4 * PI.toFloat() + waves * 2 * PI.toFloat()))
            }
            lineTo(w, h); close()
        }
        drawPath(water, Brush.verticalGradient(listOf(Color(0xFF34D3E8), Color(0xFF0B8FC2), Color(0xFF075E99)), startY = top, endY = h))
        drawPath(water, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), startY = top - h * 0.01f, endY = top + h * 0.03f))
    }
}

private fun DrawScope.drawBubble(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(0.65f to Color.White.copy(alpha = 0.02f), 0.93f to Color(0xFFD8F6FF).copy(alpha = 0.32f), 1f to Color.White.copy(alpha = 0.75f), center = c, radius = r), radius = r, center = c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = Offset(c.x - r * 0.4f, c.y - r * 0.42f), radius = r * 0.3f), radius = r * 0.3f, center = Offset(c.x - r * 0.4f, c.y - r * 0.42f))
}

// ---- Y2K: PS2 / Dreamcast menu ---------------------------------------------------------------

@Composable
private fun ConsoleMenu(animate: Boolean) {
    val dark = LocalIsDark.current
    val scroll = drift(animate, 6000, "y2kGrid", linear = true)
    val bob = drift(animate, 11000, "y2kBob")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val line = if (dark) Cyan else Color(0xFF2C6FD1)
        drawRect(
            if (dark) Brush.verticalGradient(0f to Color(0xFF01030A), 0.55f to Color(0xFF051434), 1f to Color(0xFF0A2350))
            else Brush.verticalGradient(0f to Color(0xFFF6F9FD), 0.6f to Color(0xFFE2EBF6), 1f to Color(0xFFCFDDF0))
        )
        val horizon = h * 0.5f
        // Fog glow on the horizon.
        drawOval(
            Brush.radialGradient(listOf(line.copy(alpha = if (dark) 0.28f else 0.22f), line.copy(alpha = 0f)), center = Offset(w / 2, horizon), radius = w * 0.8f),
            topLeft = Offset(-w * 0.3f, horizon - h * 0.2f), size = Size(w * 1.6f, h * 0.4f),
        )
        // Perspective floor grid sliding toward the viewer.
        val vanish = Offset(w / 2, horizon)
        for (i in -9..9) {
            val xb = w / 2 + i * w * 0.16f
            drawLine(
                Brush.verticalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.32f)), startY = horizon, endY = h),
                vanish, Offset(xb + i * w * 0.4f, h), 1.dp.toPx(),
            )
        }
        val rows = 12
        for (k in 0 until rows) {
            val t = (k + scroll) / rows
            val y = horizon + (h - horizon) * t * t
            drawLine(line.copy(alpha = 0.32f * t), Offset(0f, y), Offset(w, y), 1.dp.toPx())
        }
        // Translucent floating cubes.
        listOf(floatArrayOf(0.78f, 0.2f, 0.11f, 0f), floatArrayOf(0.16f, 0.36f, 0.075f, 0.4f), floatArrayOf(0.6f, 0.47f, 0.05f, 0.7f)).forEach { (cx, cy, cs, ph) ->
            val c = Offset(w * cx, h * cy + h * 0.012f * sin((bob + ph) * 2 * PI.toFloat()))
            drawIsoCube(c, w * cs, line, dark)
        }
    }
}

/** A see-through isometric cube with lit top and shaded sides. */
private fun DrawScope.drawIsoCube(c: Offset, s: Float, edge: Color, dark: Boolean) {
    val dx = s * 0.866f; val dy = s * 0.5f
    val top = Offset(c.x, c.y - s); val left = Offset(c.x - dx, c.y - dy); val right = Offset(c.x + dx, c.y - dy)
    val bl = Offset(c.x - dx, c.y + dy); val br = Offset(c.x + dx, c.y + dy); val bottom = Offset(c.x, c.y + s)
    fun face(p: List<Offset>) = Path().apply { moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    val a = if (dark) 1f else 0.8f
    drawPath(face(listOf(top, right, c, left)), Color.White.copy(alpha = 0.16f * a))
    drawPath(face(listOf(left, c, bottom, bl)), edge.copy(alpha = 0.14f * a))
    drawPath(face(listOf(c, right, br, bottom)), edge.copy(alpha = 0.07f * a))
    val stroke = Stroke(1.2.dp.toPx())
    drawPath(face(listOf(top, right, br, bottom, bl, left)), edge.copy(alpha = 0.55f), style = stroke)
    drawLine(edge.copy(alpha = 0.45f), left, c, 1.2.dp.toPx()); drawLine(edge.copy(alpha = 0.45f), right, c, 1.2.dp.toPx())
    drawLine(edge.copy(alpha = 0.45f), c, bottom, 1.2.dp.toPx())
}
