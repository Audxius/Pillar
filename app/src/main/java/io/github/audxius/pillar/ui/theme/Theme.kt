package io.github.audxius.pillar.ui.theme

import android.os.Build
import android.view.SoundEffectConstants
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.audxius.pillar.R
import io.github.audxius.pillar.data.AppFont
import io.github.audxius.pillar.data.AppGraph
import io.github.audxius.pillar.data.AppSettings
import io.github.audxius.pillar.data.ThemeMode
import io.github.audxius.pillar.data.UiStyle
import io.github.audxius.pillar.data.UserData

data class PalettePreset(val id: String, val name: String, val primary: Color, val secondary: Color, val tertiary: Color)

val Palettes = listOf(
    PalettePreset("aurora", "Aurora", Color(0xFF8B6CFF), Color(0xFF3DD6F5), Color(0xFFFF5CA8)),
    PalettePreset("sakura", "Sakura", Color(0xFFFF7EB6), Color(0xFFFFB86B), Color(0xFFC59BFF)),
    PalettePreset("ocean", "Ocean", Color(0xFF3BA4FF), Color(0xFF3DF5C9), Color(0xFF6E7BFF)),
    PalettePreset("ember", "Ember", Color(0xFFFF6B3D), Color(0xFFFFC23D), Color(0xFFFF3D6E)),
    PalettePreset("forest", "Forest", Color(0xFF4ADE80), Color(0xFFA3E635), Color(0xFF2DD4BF)),
    PalettePreset("dmg", "Pocket", Color(0xFF9BBC0F), Color(0xFF6E9A2A), Color(0xFFC4CF8A)),
    PalettePreset("famicom", "Famicom", Color(0xFFE4002B), Color(0xFFD4A84A), Color(0xFFF2E6CE)),
    PalettePreset("aero", "Aero", Color(0xFF1E9BE8), Color(0xFF6CCB3B), Color(0xFF00C2D9)),
    PalettePreset("y2k", "Dreamcast", Color(0xFFFF7A00), Color(0xFF2E6BFF), Color(0xFFB4FF39)),
    PalettePreset("mono", "Mono", Color(0xFFE6E6EA), Color(0xFF9A9AA6), Color(0xFFFFFFFF)),
)

val AccentChoices = listOf(
    0xFF8B6CFF, 0xFF5B7CFF, 0xFF3BA4FF, 0xFF22C3E6, 0xFF2DD4BF, 0xFF4ADE80, 0xFFA3E635, 0xFFFACC15,
    0xFFFFA53D, 0xFFFF6B3D, 0xFFF43F5E, 0xFFFF5CA8, 0xFFD946EF, 0xFFA78BFA, 0xFFE6E6EA, 0xFF94A3B8,
).map { Color(it) }

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("No graph") }
val LocalSettings = staticCompositionLocalOf { AppSettings() }
val LocalUserData = staticCompositionLocalOf { UserData() }
/** Color that tints the animated background, set by the platform or game on screen. */
val LocalAmbient = staticCompositionLocalOf<MutableState<Color?>> { mutableStateOf(null) }
/** Art (image sources) of the game currently in focus, used by the game-art background. */
val LocalAmbientArt = staticCompositionLocalOf<MutableState<List<String>?>> { mutableStateOf(null) }
val LocalCorner = staticCompositionLocalOf { 22.dp }
val LocalPalette = staticCompositionLocalOf { Palettes.first() }
val LocalIsDark = staticCompositionLocalOf { true }

fun Color.onColor(): Color = if (luminance() > 0.5f) Color(0xFF111018) else Color.White

private fun buildScheme(p: PalettePreset, mode: ThemeMode, systemDark: Boolean): ColorScheme {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        else -> true
    }
    val amoled = mode == ThemeMode.AMOLED
    return if (dark) {
        val bg = if (amoled) Color.Black else lerp(Color(0xFF09080F), p.primary, 0.05f)
        val surface = if (amoled) lerp(Color(0xFF070709), p.primary, 0.04f) else lerp(Color(0xFF12111A), p.primary, 0.07f)
        darkColorScheme(
            primary = p.primary,
            onPrimary = p.primary.onColor(),
            primaryContainer = lerp(surface, p.primary, 0.28f),
            onPrimaryContainer = Color.White,
            secondary = p.secondary,
            onSecondary = p.secondary.onColor(),
            secondaryContainer = lerp(surface, p.secondary, 0.22f),
            onSecondaryContainer = Color.White,
            tertiary = p.tertiary,
            onTertiary = p.tertiary.onColor(),
            background = bg,
            onBackground = Color(0xFFF4F2FA),
            surface = surface,
            onSurface = Color(0xFFF4F2FA),
            surfaceVariant = lerp(Color(0xFF1C1A28), p.primary, 0.10f),
            onSurfaceVariant = Color(0xFFB8B3CB),
            surfaceContainerLowest = bg,
            surfaceContainerLow = lerp(surface, Color.Black, 0.2f),
            surfaceContainer = surface,
            surfaceContainerHigh = lerp(Color(0xFF1B1A26), p.primary, 0.08f),
            surfaceContainerHighest = lerp(Color(0xFF242232), p.primary, 0.10f),
            outline = Color.White.copy(alpha = 0.18f),
            outlineVariant = Color.White.copy(alpha = 0.08f),
            inverseSurface = Color(0xFFF4F2FA),
            inverseOnSurface = Color(0xFF16151F),
            error = Color(0xFFFF6B6B),
        )
    } else {
        val primary = lerp(p.primary, Color.Black, if (p.primary.luminance() > 0.6f) 0.45f else 0.1f)
        val bg = lerp(Color(0xFFF7F6FB), p.primary, 0.05f)
        lightColorScheme(
            primary = primary,
            onPrimary = primary.onColor(),
            primaryContainer = lerp(Color.White, p.primary, 0.22f),
            onPrimaryContainer = Color(0xFF1A1630),
            secondary = lerp(p.secondary, Color.Black, 0.25f),
            secondaryContainer = lerp(Color.White, p.secondary, 0.2f),
            tertiary = lerp(p.tertiary, Color.Black, 0.2f),
            background = bg,
            onBackground = Color(0xFF16141F),
            surface = Color.White,
            onSurface = Color(0xFF16141F),
            surfaceVariant = lerp(Color(0xFFEDEBF4), p.primary, 0.08f),
            onSurfaceVariant = Color(0xFF5A5570),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color(0xFFF9F8FC), p.primary, 0.03f),
            surfaceContainer = lerp(Color(0xFFF2F1F7), p.primary, 0.05f),
            surfaceContainerHigh = lerp(Color(0xFFECEAF3), p.primary, 0.06f),
            surfaceContainerHighest = lerp(Color(0xFFE5E3EE), p.primary, 0.08f),
            outline = Color.Black.copy(alpha = 0.16f),
            outlineVariant = Color.Black.copy(alpha = 0.07f),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) =
    Font(res, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun familyFor(font: AppFont): FontFamily = when (font) {
    AppFont.NUNITO -> FontFamily(listOf(400, 500, 600, 700, 800, 900).map { variable(R.font.nunito, it) })
    AppFont.OUTFIT -> FontFamily(listOf(300, 400, 500, 600, 700, 800).map { variable(R.font.outfit, it) })
    AppFont.SPACE_GROTESK -> FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.space_grotesk, it) })
    AppFont.PIXEL -> FontFamily(Font(R.font.press_start))
    AppFont.EXO -> FontFamily(
        listOf(400, 500, 600, 700, 800, 900).flatMap { listOf(variable(R.font.exo2, it), variable(R.font.exo2_italic, it, FontStyle.Italic)) }
    )
    AppFont.SYSTEM -> FontFamily.Default
}

private fun typographyFor(font: AppFont, style: UiStyle): Typography {
    val f = familyFor(font)
    val base = Typography()
    // Y2K headings are heavy and slanted, like late-90s game box type.
    val y2k = style == UiStyle.Y2K
    fun TextStyle.with(weight: FontWeight, spacing: Float? = null) =
        copy(fontFamily = f, fontWeight = weight, letterSpacing = spacing?.em ?: letterSpacing)
    fun TextStyle.heading(weight: FontWeight, spacing: Float? = null) =
        with(if (y2k) FontWeight.Black else weight, spacing).let { if (y2k) it.copy(fontStyle = FontStyle.Italic) else it }
    return Typography(
        displayLarge = base.displayLarge.heading(FontWeight.Black, -0.02f),
        displayMedium = base.displayMedium.heading(FontWeight.Black, -0.02f),
        displaySmall = base.displaySmall.heading(FontWeight.ExtraBold, -0.015f),
        headlineLarge = base.headlineLarge.heading(FontWeight.ExtraBold, -0.01f),
        headlineMedium = base.headlineMedium.heading(FontWeight.ExtraBold, -0.01f),
        headlineSmall = base.headlineSmall.heading(FontWeight.Bold, -0.005f),
        titleLarge = base.titleLarge.heading(FontWeight.ExtraBold),
        titleMedium = base.titleMedium.with(FontWeight.Bold),
        titleSmall = base.titleSmall.with(FontWeight.Bold),
        bodyLarge = base.bodyLarge.with(FontWeight.Medium),
        bodyMedium = base.bodyMedium.with(FontWeight.Medium),
        bodySmall = base.bodySmall.with(FontWeight.Medium),
        labelLarge = base.labelLarge.with(FontWeight.Bold),
        labelMedium = base.labelMedium.with(FontWeight.Bold),
        labelSmall = base.labelSmall.with(FontWeight.Bold, 0.02f),
    )
}

fun paletteFor(settings: AppSettings): PalettePreset {
    val preset = Palettes.firstOrNull { it.id == settings.palette } ?: Palettes.first()
    return settings.customAccent?.let { preset.copy(primary = Color(it.toInt())) } ?: preset
}

@Composable
fun PillarTheme(graph: AppGraph, settings: AppSettings, user: UserData, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val palette = paletteFor(settings)
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        else -> true
    }
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val dyn = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (settings.themeMode == ThemeMode.AMOLED) dyn.copy(background = Color.Black, surface = Color(0xFF080808)) else dyn
    } else {
        buildScheme(palette, settings.themeMode, systemDark)
    }
    val effectivePalette = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        PalettePreset("dynamic", "Dynamic", scheme.primary, scheme.secondary, scheme.tertiary)
    } else palette
    val corner = settings.cornerRadius.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(corner * 0.3f),
        small = RoundedCornerShape(corner * 0.5f),
        medium = RoundedCornerShape(corner * 0.75f),
        large = RoundedCornerShape(corner),
        extraLarge = RoundedCornerShape(corner * 1.3f),
    )
    val ambient = remember { mutableStateOf<Color?>(null) }
    val ambientArt = remember { mutableStateOf<List<String>?>(null) }
    val density = LocalDensity.current
    // The pixel font renders much larger than the others at the same size.
    val fontScale = settings.textScale * (if (settings.font == AppFont.PIXEL) 0.72f else 1f)
    CompositionLocalProvider(
        LocalGraph provides graph,
        LocalSettings provides settings,
        LocalUserData provides user,
        LocalAmbient provides ambient,
        LocalAmbientArt provides ambientArt,
        LocalStyle provides settings.uiStyle,
        LocalCorner provides corner,
        LocalPalette provides effectivePalette,
        LocalIsDark provides dark,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typographyFor(settings.font, settings.uiStyle), shapes = shapes) {
            // No root Surface, so set the default text/icon color explicitly.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/** Haptic tick + optional click sound, honoring the user's feedback settings. */
@Composable
fun rememberFeedback(): () -> Unit {
    val settings = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(settings.haptics, settings.uiSounds) {
        {
            if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (settings.uiSounds) view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }
}
