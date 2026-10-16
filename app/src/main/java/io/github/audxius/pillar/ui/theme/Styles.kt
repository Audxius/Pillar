package io.github.audxius.pillar.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import kotlin.random.Random
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    /**
     * Color schemes made for this style; the first is its default. What each color does depends on
     * the style (Aero: sky, grass, buttons; most others: the accent).
     */
    val palettes: List<PalettePreset> = emptyList(),
)

private fun pal(id: String, name: String, a: Long, b: Long, c: Long) = PalettePreset(id, name, Color(a), Color(b), Color(c))

val StyleSpecs = listOf(
    StyleSpec(
        UiStyle.GLASS, "Liquid Glass", "Apple-style liquid glass bending your game's art", "aurora", ThemeMode.DARK, AppFont.OUTFIT, 24, 0.75f,
        listOf(
            pal("aurora", "Aurora", 0xFF8B6CFF, 0xFF3DD6F5, 0xFFFF5CA8), pal("ocean", "Ocean", 0xFF3BA4FF, 0xFF3DF5C9, 0xFF6E7BFF),
            pal("sakura", "Sakura", 0xFFFF7EB6, 0xFFFFB86B, 0xFFC59BFF), pal("ember", "Ember", 0xFFFF6B3D, 0xFFFFC23D, 0xFFFF3D6E),
            pal("forest", "Forest", 0xFF4ADE80, 0xFFA3E635, 0xFF2DD4BF), pal("mono", "Mono", 0xFFE6E6EA, 0xFF9A9AA6, 0xFFFFFFFF),
        ),
    ),
    StyleSpec(
        UiStyle.AERO, "Frutiger Aero", "Windows 7 glass over a bright sky, glossy grass and water drops", "aero-sky", ThemeMode.LIGHT, AppFont.NUNITO, 16, 0f,
        // primary: sky, secondary: grass, tertiary: glossy buttons.
        listOf(
            pal("aero-sky", "Sky", 0xFF1C8FE3, 0xFF5CC43A, 0xFF1FA6EC), pal("aero-lagoon", "Lagoon", 0xFF0A9DB8, 0xFF34C38F, 0xFF12B5C9),
            pal("aero-meadow", "Meadow", 0xFF3A8BE6, 0xFF49B83A, 0xFF46B83A), pal("aero-sunset", "Sunset", 0xFFE9864C, 0xFF7FC245, 0xFFF29A2E),
        ),
    ),
    StyleSpec(
        UiStyle.Y2K, "Y2K", "PS2 and Dreamcast menus: grid floor, cubes, chrome", "y2k-ice", ThemeMode.DARK, AppFont.EXO, 3, 0.3f,
        listOf(
            pal("y2k-ice", "Ice", 0xFF6FD3FF, 0xFFB8ECFF, 0xFF2E6BFF), pal("y2k-dreamcast", "Dreamcast", 0xFFFF8A1F, 0xFFFFC27A, 0xFFE05A00),
            pal("y2k-jet", "Jet", 0xFFB4FF39, 0xFFDBFF9A, 0xFF5FBF00), pal("y2k-vapor", "Vapor", 0xFFC77DFF, 0xFFE6C4FF, 0xFF8A3FFC),
            pal("y2k-chrome", "Chrome", 0xFFD6DEE8, 0xFFFFFFFF, 0xFF8FA3BF),
        ),
    ),
    StyleSpec(
        UiStyle.XMB, "XMB", "PSP and PS3 menu: a slow light wave over deep color", "xmb-blue", ThemeMode.DARK, AppFont.OUTFIT, 12, 0.18f,
        // The PS3 changed its wave color each month; these are its classics.
        listOf(
            pal("xmb-blue", "Blue", 0xFF4FA3FF, 0xFF9AD0FF, 0xFFE3F1FF), pal("xmb-purple", "Purple", 0xFF9B6BFF, 0xFFC9B3FF, 0xFFEDE6FF),
            pal("xmb-red", "Red", 0xFFE5484D, 0xFFFF9EA1, 0xFFFFE3E4), pal("xmb-orange", "Orange", 0xFFF5A524, 0xFFFFD08A, 0xFFFFF1DC),
            pal("xmb-green", "Green", 0xFF3CC48A, 0xFF9AE6C4, 0xFFE2F8EE), pal("xmb-pink", "Pink", 0xFFE86AB5, 0xFFF5B3DA, 0xFFFDE8F4),
            pal("xmb-silver", "Silver", 0xFFA7B4C2, 0xFFD5DDE5, 0xFFF2F5F8),
        ),
    ),
    StyleSpec(
        UiStyle.WII, "Wii", "Wii menu: white pinstripes, rounded channels, Wii blue", "wii-blue", ThemeMode.LIGHT, AppFont.NUNITO, 22, 0f,
        listOf(
            pal("wii-blue", "Wii blue", 0xFF34BEED, 0xFF8A9BA8, 0xFF1A9FD6), pal("wii-green", "Green", 0xFF4DB84A, 0xFF8A9BA8, 0xFF3A9A38),
            pal("wii-pink", "Pink", 0xFFF06BA8, 0xFF8A9BA8, 0xFFD94C8E), pal("wii-orange", "Orange", 0xFFF59A2E, 0xFF8A9BA8, 0xFFD97F14),
            pal("wii-silver", "Silver", 0xFF7F8E9B, 0xFFB7C2CB, 0xFF5E6C78),
        ),
    ),
    StyleSpec(
        UiStyle.GRID, "Grid", "8-bit era box art: a black grid on white, one bold color", "grid-red", ThemeMode.LIGHT, AppFont.EXO, 2, 0f,
        listOf(
            pal("grid-red", "Red", 0xFFE4002B, 0xFF141414, 0xFF8A8A8A), pal("grid-blue", "Blue", 0xFF1F4FD1, 0xFF141414, 0xFF8A8A8A),
            pal("grid-green", "Green", 0xFF138A36, 0xFF141414, 0xFF8A8A8A), pal("grid-gold", "Gold", 0xFFD99A00, 0xFF141414, 0xFF8A8A8A),
        ),
    ),
)

fun specFor(style: UiStyle) = StyleSpecs.firstOrNull { it.style == style } ?: StyleSpecs.first()

/** The recorded wallpaper that Liquid Glass surfaces refract (set up in PillarRoot). */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * A Liquid Glass surface: blurs, saturates and bends the wallpaper behind it at the edges, with a
 * specular rim. It only ever shows the wallpaper, never content scrolling underneath, so it reads
 * as solid. Android 12 adds the blur and 13 the refraction; older versions get a denser tint.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color, blurRadius: Dp = 14.dp, refraction: Dp = 16.dp): Modifier {
    val backdrop = LocalBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < 31) {
        return this.clip(shape).background(tint.copy(alpha = maxOf(tint.alpha, 0.72f))).border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    }
    return this
        .drawBackdrop(
            backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                lens(refraction.toPx(), refraction.toPx() * 2f)
            },
            onDrawSurface = { drawRect(tint) },
        )
        .clip(shape)
}

val LocalStyle = staticCompositionLocalOf { UiStyle.GLASS }

// Each style's accent is its palette's primary color.
private val Cyan: Color @Composable get() = LocalPalette.current.primary
private val Navy = Color(0xFF07152F)
private val WiiBlue: Color @Composable get() = LocalPalette.current.primary
private val WiiRim = Color(0xFFCBD3DA)
// Grid: the 8-bit era's black-grid-on-white boxes, with one bold color.
val GridInk = Color(0xFF141414)
val GridRed: Color @Composable get() = LocalPalette.current.primary
val GridPaper = Color(0xFFF6F6F3)
// Grid's dark variant: charcoal paper, light ink.
val GridInkDark = Color(0xFFEDEDE8)
val GridPaperDark = Color(0xFF141414)
private val GridPanelDark = Color(0xFF1E1E1E)

/**
 * Panel surface for the current style: fill, rim and highlight. [solid] puts an opaque base
 * underneath, for things that must never let content show through (the dock).
 */
@Composable
fun Modifier.styledSurface(shape: Shape, strong: Boolean = false, solid: Boolean = false): Modifier {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    fun Modifier.base(c: Color) = if (solid) background(c) else this
    return when (style) {
        UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> this.liquidGlass(
            shape,
            // A dark tint inside the glass keeps text readable over bright art.
            tint = if (dark) Color(0xFF0B0A12).copy(alpha = if (solid) 0.5f else if (strong) 0.4f else 0.32f)
            else Color.White.copy(alpha = if (solid) 0.6f else 0.42f),
            blurRadius = if (solid || strong) 18.dp else 14.dp,
        )

        UiStyle.AERO -> {
            // Windows 7 Aero glass: blurred and see-through, with a bright sheen and a crisp white rim.
            val sky = LocalPalette.current.primary
            this
                .shadow(if (strong) 12.dp else 8.dp, shape, ambientColor = lerp(sky, Color.Black, 0.3f), spotColor = lerp(sky, Color.Black, 0.3f))
                .liquidGlass(
                    shape,
                    tint = if (dark) Color(0xFF0B2442).copy(alpha = if (solid) 0.8f else 0.6f) else Color.White.copy(alpha = if (solid) 0.72f else 0.55f),
                    blurRadius = 20.dp, refraction = 6.dp,
                )
                .drawWithContent {
                    val band = minOf(size.height * 0.42f, 28.dp.toPx())
                    drawRect(
                        Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.22f else 0.75f), 1f to Color.White.copy(alpha = 0f), endY = band),
                        size = Size(size.width, band),
                    )
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.05f else 0.14f), Color.Transparent), start = Offset(size.width * 0.5f, 0f), end = Offset(size.width * 0.9f, size.height)))
                    drawContent()
                }
                .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), lerp(sky, Color.White, 0.5f))), shape)
        }

        UiStyle.Y2K -> {
            // Square corners to match the HUD brackets; only round things stay round.
            val sq = if (shape == CircleShape) shape else RoundedCornerShape(3.dp)
            this
                .clip(sq)
                .base(if (dark) Color(0xFF061128) else Color(0xFFEAF0F8))
                .background(
                    if (dark) Brush.verticalGradient(listOf(Color(0xFF0D2148).copy(alpha = 0.78f), Navy.copy(alpha = 0.86f)))
                    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.86f), Color(0xFFE3ECF8).copy(alpha = 0.8f)))
                )
                .border(1.dp, (if (dark) Cyan else lerp(Cyan, Color.Black, 0.45f)).copy(alpha = if (strong) 0.6f else 0.38f), sq)
                .then(if (shape == CircleShape) Modifier else Modifier.techBrackets(if (dark) Cyan else lerp(Cyan, Color.Black, 0.45f)))
        }

        UiStyle.XMB -> this
            .clip(shape)
            .base(if (dark) Color(0xFF0B1424) else Color(0xFFF2F5F9))
            .background(if (dark) Color.Black.copy(alpha = if (strong) 0.58f else 0.5f) else Color.White.copy(alpha = if (strong) 0.82f else 0.74f))
            .drawWithContent {
                drawContent()
                // The XMB's thin highlight along the top edge.
                drawLine(Color.White.copy(alpha = if (dark) 0.28f else 0.9f), Offset(size.width * 0.06f, 0.5f), Offset(size.width * 0.94f, 0.5f), 1.dp.toPx())
            }
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f), shape)

        UiStyle.WII -> this
            .shadow(if (strong) 8.dp else 4.dp, shape, ambientColor = Color(0xFF5A6B78), spotColor = Color(0xFF5A6B78))
            .clip(shape)
            .background(if (dark) Color(0xFF2A3036) else Color.White)
            .border(if (strong) 2.5.dp else 1.5.dp, if (strong) WiiBlue else (if (dark) Color(0xFF46505A) else WiiRim), shape)

        UiStyle.GRID -> {
            // Square, ink-outlined panels, like the boxes' white label areas.
            val sq = if (shape == CircleShape) shape else RoundedCornerShape(2.dp)
            this
                .clip(sq)
                .background(if (dark) GridPanelDark else Color.White)
                .border(if (strong) 3.dp else 2.dp, if (dark) GridInkDark else GridInk, sq)
        }
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
        // Liquid Glass draws its own surface (see Modifier.primaryActionFill); this is its tint.
        UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> SolidBrush(primary.copy(alpha = 0.55f)) to Color.White
        UiStyle.AERO -> aeroOrb(LocalPalette.current.tertiary) to Color.White
        // Brushed chrome: bright, a dark band through the middle, bright again.
        // The dark band sits low so the label always reads on bright metal.
        UiStyle.Y2K -> Brush.verticalGradient(
            0f to Color(0xFFFFFFFF), 0.55f to Color(0xFFE3EAF4), 0.78f to Color(0xFF9DB0CB), 0.86f to Color(0xFFD5E1F1), 1f to Color(0xFFF7FAFF)
        ) to Color(0xFF071330)
        // XMB: a lit, see-through selection bar.
        UiStyle.XMB -> if (LocalIsDark.current) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.22f))) to Color.White
        else Brush.verticalGradient(listOf(lerp(LocalPalette.current.primary, Color.White, 0.15f), lerp(LocalPalette.current.primary, Color.Black, 0.15f))) to Color.White
        // Wii: a white channel button with Wii-blue lettering.
        UiStyle.WII -> Brush.verticalGradient(listOf(Color.White, Color(0xFFEAF0F4))) to Color(0xFF168FC7)
        UiStyle.GRID -> SolidBrush(GridRed) to Color.White
    }
}

/** Extra gloss/rim drawn over the primary action surface. */
@Composable
fun Modifier.primaryActionFinish(shape: Shape): Modifier = when (LocalStyle.current) {
    UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> this
    UiStyle.AERO -> this
        .drawWithContent {
            drawContent()
            drawRoundRect(
                // A thin streak along the top edge only, clear of the label.
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.75f), 1f to Color.White.copy(alpha = 0.05f)),
                topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                size = Size(size.width * 0.9f, size.height * 0.22f),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        .border(1.dp, lerp(LocalPalette.current.tertiary, Color.Black, 0.45f), shape)
    UiStyle.Y2K -> this.border(1.5.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF6E86AA))), shape)
    UiStyle.XMB -> this.border(1.5.dp, Color.White.copy(alpha = if (LocalIsDark.current) 0.8f else 0.6f), shape)
    UiStyle.WII -> this.border(3.dp, WiiBlue, shape)
    UiStyle.GRID -> this.border(2.5.dp, if (LocalIsDark.current) GridInkDark else GridInk, shape)
}

/** Things drawn outside the button's bounds: Grid's hard ink shadow. */
@Composable
fun Modifier.primaryActionOuter(shape: Shape): Modifier =
    if (LocalStyle.current == UiStyle.GRID) {
        val shadow = if (LocalIsDark.current) Color.Black else GridInk
        this.drawBehind {
            val o = shape.createOutline(size, layoutDirection, this)
            translate(4.dp.toPx(), 4.dp.toPx()) { drawOutline(o, shadow) }
        }
    } else this

/** The Play button's face: real liquid glass for Liquid Glass, the style's brush otherwise. */
@Composable
fun Modifier.primaryActionFill(shape: Shape, brush: Brush): Modifier = when (LocalStyle.current) {
    UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> this.liquidGlass(shape, androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), blurRadius = 10.dp, refraction = 20.dp)
    else -> this.clip(shape).background(brush)
}

/** Chip/pill surface: liquid glass for Liquid Glass, flat colors for the rest. */
@Composable
fun Modifier.chipSurface(shape: Shape, selected: Boolean): Modifier {
    val (bg, _, rim) = chipColors(selected)
    val style = LocalStyle.current
    return if ((style == UiStyle.GLASS || style == UiStyle.LUNA || style == UiStyle.POCKET) && !selected) {
        this.liquidGlass(shape, Color(0xFF0B0A12).copy(alpha = if (LocalIsDark.current) 0.3f else 0.08f), blurRadius = 8.dp, refraction = 8.dp)
    } else this.clip(shape).background(bg).border(if (style == UiStyle.GRID) 2.dp else 1.dp, rim, shape)
}

/** Chip/pill colors: (background, content, border). */
@Composable
fun chipColors(selected: Boolean): Triple<Brush, Color, Brush> {
    val style = LocalStyle.current
    val dark = LocalIsDark.current
    return when (style) {
        UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> if (selected) Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.92f else 1f)), Color(0xFF15131C), SolidBrush(Color.White))
        else Triple(SolidBrush(Color.White.copy(alpha = if (dark) 0.08f else 0.55f)), if (dark) Color.White else Color(0xFF15131C), SolidBrush(Color.White.copy(alpha = if (dark) 0.16f else 0.85f)))
        UiStyle.AERO -> {
            val p = LocalPalette.current
            if (selected) Triple(aeroOrb(p.tertiary), Color.White, SolidBrush(lerp(p.tertiary, Color.Black, 0.45f)))
            else Triple(Brush.verticalGradient(0f to Color.White, 0.5f to Color(0xFFF4FAFF), 0.51f to lerp(p.primary, Color.White, 0.85f), 1f to Color(0xFFEFF8FF)), lerp(p.primary, Color.Black, 0.65f), SolidBrush(lerp(p.primary, Color.White, 0.45f)))
        }
        UiStyle.Y2K -> if (selected) Triple(SolidBrush(Cyan), Navy, SolidBrush(Color.White))
        else if (dark) Triple(SolidBrush(Navy.copy(alpha = 0.7f)), Color.White, SolidBrush(Cyan.copy(alpha = 0.45f)))
        else Triple(SolidBrush(Color.White.copy(alpha = 0.85f)), Navy, SolidBrush(Color(0xFF2C6FD1).copy(alpha = 0.5f)))
        UiStyle.XMB -> if (dark) {
            if (selected) Triple(SolidBrush(Color.White.copy(alpha = 0.92f)), Color(0xFF0B1424), SolidBrush(Color.White))
            else Triple(SolidBrush(Color.Black.copy(alpha = 0.48f)), Color.White, SolidBrush(Color.White.copy(alpha = 0.25f)))
        } else {
            val p = LocalPalette.current.primary
            if (selected) Triple(SolidBrush(p), Color.White, SolidBrush(p))
            else Triple(SolidBrush(Color.White.copy(alpha = 0.78f)), Color(0xFF1B2433), SolidBrush(Color.Black.copy(alpha = 0.1f)))
        }
        UiStyle.WII -> if (selected) Triple(SolidBrush(WiiBlue), Color.White, SolidBrush(Color(0xFF1A9FD6)))
        else if (dark) Triple(SolidBrush(Color(0xFF2A3036)), Color.White, SolidBrush(Color(0xFF46505A)))
        else Triple(SolidBrush(Color.White), Color(0xFF4A5560), SolidBrush(WiiRim))
        UiStyle.GRID -> {
            val ink = if (dark) GridInkDark else GridInk
            val paper = if (dark) GridPanelDark else Color.White
            if (selected) Triple(SolidBrush(ink), paper, SolidBrush(ink)) else Triple(SolidBrush(paper), ink, SolidBrush(ink))
        }
    }
}

@Suppress("FunctionName")
fun SolidBrush(c: Color): Brush = Brush.linearGradient(listOf(c, c))

/** Aero's glossy "jelly" orb fill: bright top, a hard shine line through the middle, deep below. */
private fun aeroOrb(c: Color): Brush = Brush.verticalGradient(
    0f to lerp(c, Color.White, 0.6f), 0.46f to lerp(c, Color.White, 0.12f), 0.54f to lerp(c, Color.Black, 0.2f), 1f to lerp(c, Color.White, 0.25f),
)

/** Full-screen backdrop for the current style. */
@Composable
fun StyleBackdrop(primary: Color, secondary: Color, tertiary: Color, animate: Boolean) = androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().graphicsLayer()) {
    when (LocalStyle.current) {
        // Glass styles keep their wallpaper still: every glass surface re-blurs whatever is behind
        // it, so a moving wallpaper made all of them redraw every frame. Motion lives in StyleOverlay.
        UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> GlassMesh(primary, secondary, tertiary, animate = false)
        UiStyle.AERO -> AeroScene()
        UiStyle.Y2K -> ConsoleMenu(animate)
        UiStyle.XMB -> XmbWave(LocalPalette.current.primary, animate, LocalIsDark.current)
        UiStyle.WII -> WiiStripes(WiiBlue)
        UiStyle.GRID -> GridPaperBackdrop(GridRed, LocalIsDark.current)
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

/**
 * A Frutiger Aero / Windows 7 scene: deep-to-bright sky with a sun flare, glossy light swooshes, a
 * lush glossy field, glass water drops and rising bubbles. Sky, grass and accents follow the palette.
 */
@Composable
private fun AeroScene() {
    val p = LocalPalette.current
    val dark = LocalIsDark.current
    val sway = 0.5f
    val blades = remember { Random(5).let { r -> List(160) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val drops = remember { Random(11).let { r -> List(8) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val sky = p.primary
        val grass = p.secondary
        drawRect(
            if (dark) Brush.verticalGradient(0f to lerp(sky, Color.Black, 0.82f), 0.5f to lerp(sky, Color.Black, 0.55f), 1f to lerp(sky, Color.Black, 0.35f))
            else Brush.verticalGradient(0f to lerp(sky, Color.Black, 0.18f), 0.32f to sky, 0.62f to lerp(sky, Color.White, 0.55f), 0.8f to lerp(sky, Color.White, 0.88f))
        )
        // Sun with a soft halo, an anamorphic streak and a few lens-flare rings.
        val sun = Offset(w * 0.84f, h * 0.09f)
        drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = if (dark) 0.35f else 0.95f), 0.2f to Color.White.copy(alpha = if (dark) 0.12f else 0.45f), 1f to Color.White.copy(alpha = 0f), center = sun, radius = w * 0.55f), radius = w * 0.55f, center = sun)
        drawOval(
            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = if (dark) 0.25f else 0.6f), Color.White.copy(alpha = 0f)), startX = sun.x - w * 0.7f, endX = sun.x + w * 0.5f),
            topLeft = Offset(sun.x - w * 0.7f, sun.y - 2.dp.toPx()), size = Size(w * 1.2f, 4.dp.toPx()),
        )
        listOf(0.22f to 0.035f, 0.38f to 0.06f, 0.55f to 0.025f, 0.7f to 0.08f).forEachIndexed { i, (t, r) ->
            val c = Offset(sun.x + (w * 0.15f - sun.x) * t, sun.y + (h * 0.6f - sun.y) * t)
            val tint = if (i % 2 == 0) Color.White else lerp(sky, Color.White, 0.6f)
            drawCircle(tint.copy(alpha = if (dark) 0.06f else 0.12f), radius = w * r, center = c)
            drawCircle(Color.White.copy(alpha = if (dark) 0.08f else 0.18f), radius = w * r, center = c, style = Stroke(1.dp.toPx()))
        }
        // Glossy light swooshes sweeping across the sky.
        fun swoosh(y0: Float, rise: Float, thick: Float, alpha: Float, phase: Float) {
            val off = h * 0.015f * sin((sway + phase) * PI.toFloat())
            val top = Path().apply {
                moveTo(-w * 0.1f, y0 + off)
                cubicTo(w * 0.3f, y0 - rise + off, w * 0.65f, y0 + rise * 0.4f + off, w * 1.1f, y0 - rise * 0.8f + off)
            }
            val band = Path().apply {
                addPath(top)
                lineTo(w * 1.1f, y0 - rise * 0.8f + thick + off)
                cubicTo(w * 0.65f, y0 + rise * 0.4f + thick * 1.4f + off, w * 0.3f, y0 - rise + thick * 0.6f + off, -w * 0.1f, y0 + thick + off)
                close()
            }
            drawPath(band, Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)), startY = y0 - rise, endY = y0 + thick * 1.5f))
            drawPath(top, Color.White.copy(alpha = alpha * 1.6f), style = Stroke(1.5.dp.toPx()))
        }
        val k = if (dark) 0.45f else 1f
        swoosh(h * 0.36f, h * 0.08f, h * 0.07f, 0.34f * k, 0f)
        swoosh(h * 0.47f, h * 0.06f, h * 0.05f, 0.22f * k, 0.6f)
        swoosh(h * 0.27f, h * 0.05f, h * 0.04f, 0.16f * k, 1.2f)
        // A lush, glossy field with grass blades along its crest.
        fun crest(x: Float) = h * 0.79f - h * 0.035f * sin(PI.toFloat() * (x / w) * 0.9f + 0.25f)
        val field = Path().apply {
            moveTo(0f, h)
            for (i in 0..32) { val x = w * i / 32; lineTo(x, crest(x)) }
            lineTo(w, h); close()
        }
        drawPath(field, Brush.verticalGradient(
            if (dark) listOf(lerp(grass, Color.Black, 0.45f), lerp(grass, Color.Black, 0.75f))
            else listOf(lerp(grass, Color.White, 0.3f), grass, lerp(grass, Color.Black, 0.45f)),
            startY = h * 0.75f, endY = h,
        ))
        drawPath(field, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.1f else 0.45f), Color.White.copy(alpha = 0f)), startY = h * 0.75f, endY = h * 0.82f))
        val blade = lerp(grass, Color.White, if (dark) 0.15f else 0.45f)
        for ((bx, by, bh) in blades) {
            val x = w * bx
            val base = crest(x) + h * 0.012f * by
            val len = h * (0.01f + 0.022f * bh)
            drawLine(blade.copy(alpha = 0.55f), Offset(x, base), Offset(x + (bh - 0.5f) * len * 0.5f, base - len), 1.4.dp.toPx(), StrokeCap.Round)
        }
        // Glass water drops, as if on the screen itself.
        for ((dx, dy, dr) in drops) {
            val c = Offset(w * (0.06f + 0.88f * dx), h * (0.14f + 0.55f * dy))
            val r = w * (0.012f + 0.022f * dr)
            drawCircle(Color.Black.copy(alpha = if (dark) 0.2f else 0.1f), radius = r, center = c + Offset(r * 0.18f, r * 0.25f))
            drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.05f), 0.75f to lerp(sky, Color.White, 0.6f).copy(alpha = 0.25f), 1f to Color.White.copy(alpha = 0.7f), center = c, radius = r), radius = r, center = c)
            drawOval(Color.White.copy(alpha = 0.85f), topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.6f), size = Size(r * 0.55f, r * 0.35f))
            drawArc(Color.White.copy(alpha = 0.5f), 20f, 70f, false, topLeft = Offset(c.x - r * 0.7f, c.y - r * 0.7f), size = Size(r * 1.4f, r * 1.4f), style = Stroke(r * 0.12f))
        }
    }
}

/**
 * Motion drawn above the wallpaper but outside what glass surfaces refract, so it never makes them
 * redraw: Aero's rising bubbles.
 */
@Composable
fun StyleOverlay(animate: Boolean) {
    if (LocalStyle.current != UiStyle.AERO || !animate) return
    val rise = drift(true, 20000, "aeroRise", linear = true)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        for (i in 0 until 7) {
            val t = (rise + i * 0.143f) % 1f
            val x = w * (0.08f + 0.84f * ((i * 0.37f) % 1f)) + w * 0.02f * sin((t * 4 + i) * PI.toFloat())
            val y = h * (1.02f - t * 1.1f)
            drawBubble(Offset(x, y), w * (0.018f + 0.012f * (i % 3)))
        }
    }
}

private fun DrawScope.drawBubble(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(0.65f to Color.White.copy(alpha = 0.02f), 0.93f to Color(0xFFD8F6FF).copy(alpha = 0.32f), 1f to Color.White.copy(alpha = 0.75f), center = c, radius = r), radius = r, center = c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), center = Offset(c.x - r * 0.4f, c.y - r * 0.42f), radius = r * 0.3f), radius = r * 0.3f, center = Offset(c.x - r * 0.4f, c.y - r * 0.42f))
}

// ---- Y2K: PS2 / Dreamcast menu ---------------------------------------------------------------

@Composable
private fun ConsoleMenu(animate: Boolean) {
    val accent = Cyan
    val dark = LocalIsDark.current
    val scroll = drift(animate, 6000, "y2kGrid", linear = true)
    val bob = drift(animate, 11000, "y2kBob")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val line = if (dark) accent else lerp(accent, Color.Black, 0.45f)
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

// ---- XMB (PSP / PS3) ---------------------------------------------------------------------------

@Composable
private fun XmbWave(tint: Color, animate: Boolean, dark: Boolean) {
    val t = drift(animate, 26000, "xmb", linear = true)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // Deep, slightly tinted gradient, brighter toward the middle like the XMB.
        drawRect(
            if (dark) Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.72f), lerp(tint, Color.Black, 0.45f), lerp(tint, Color.Black, 0.8f)))
            // The PSP's light months: a pale wash with the wave drawn in the month's color.
            else Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.72f), lerp(tint, Color.White, 0.5f), lerp(tint, Color.White, 0.85f)))
        )
        val ink = if (dark) Color.White else lerp(tint, Color.White, 0.95f)
        // High enough to sweep behind the logo instead of hiding behind the carousel.
        val mid = h * 0.3f
        val phase = t * 2 * PI.toFloat()
        // The wave: a soft glowing band plus a bundle of fine lines drifting through each other.
        // Whole-number speeds only, so every line is back where it started when the loop restarts.
        fun y(x: Float, k: Int, off: Float) = mid + h * 0.07f * sin(x / w * 2.2f * PI.toFloat() + phase * k + off) + h * 0.03f * sin(x / w * 5f * PI.toFloat() - phase + off)
        val band = Path()
        val steps = 48
        for (i in 0..steps) { val x = w * i / steps; if (i == 0) band.moveTo(x, y(x, 1, 0f) - h * 0.05f) else band.lineTo(x, y(x, 1, 0f) - h * 0.05f) }
        for (i in steps downTo 0) { val x = w * i / steps; band.lineTo(x, y(x, 1, 0.6f) + h * 0.06f) }
        band.close()
        drawPath(band, Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = if (dark) 0.22f else 0.55f), ink.copy(alpha = 0f)), startY = mid - h * 0.15f, endY = mid + h * 0.15f))
        for (j in 0 until 7) {
            val line = Path()
            for (i in 0..steps) {
                val x = w * i / steps
                val yy = y(x, 1 + j % 2, j * 0.35f)
                if (i == 0) line.moveTo(x, yy) else line.lineTo(x, yy)
            }
            drawPath(line, ink.copy(alpha = (if (dark) 0.18f else 0.45f) + 0.06f * (j % 3)), style = Stroke(1.2.dp.toPx()))
        }
    }
}

// ---- Wii ---------------------------------------------------------------------------------------

@Composable
private fun WiiStripes(accent: Color) {
    val dark = LocalIsDark.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(if (dark) Brush.verticalGradient(listOf(Color(0xFF1C2126), Color(0xFF14181C))) else Brush.verticalGradient(listOf(Color(0xFFFBFCFD), Color(0xFFE9EEF2))))
        // Fine horizontal pinstripes, the Wii menu's signature.
        val gap = 4.dp.toPx()
        var y = 0f
        val stripe = if (dark) Color.White.copy(alpha = 0.035f) else Color(0xFFDCE3E9).copy(alpha = 0.7f)
        while (y < h) { drawLine(stripe, Offset(0f, y), Offset(w, y), 1.dp.toPx()); y += gap }
        // Soft Wii-blue glow rising from the bottom.
        drawOval(
            Brush.radialGradient(listOf(accent.copy(alpha = if (dark) 0.2f else 0.16f), accent.copy(alpha = 0f)), center = Offset(w / 2, h * 1.02f), radius = w * 0.9f),
            topLeft = Offset(-w * 0.4f, h * 0.65f), size = Size(w * 1.8f, h * 0.75f),
        )
    }
}

// ---- Grid (8-bit era box art) -----------------------------------------------------------------

@Composable
private fun GridPaperBackdrop(accent: Color, dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(if (dark) GridPaperDark else GridPaper)
        // The black grid the boxes were known for: even squares across the whole screen.
        val cell = w / 9f
        val line = if (dark) GridInkDark.copy(alpha = 0.28f) else GridInk.copy(alpha = 0.78f)
        val stroke = 1.6.dp.toPx()
        var x = cell / 2
        while (x < w) { drawLine(line, Offset(x, 0f), Offset(x, h), stroke); x += cell }
        var y = cell / 2
        while (y < h) { drawLine(line, Offset(0f, y), Offset(w, y), stroke); y += cell }
        // A single red stripe across the top, like the logo band.
        drawRect(accent, topLeft = Offset(0f, 0f), size = Size(w, 6.dp.toPx()))
    }
}

