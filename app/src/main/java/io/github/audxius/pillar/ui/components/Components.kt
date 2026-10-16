@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package io.github.audxius.pillar.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.audxius.pillar.data.BackgroundStyle
import io.github.audxius.pillar.data.CoverShape
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalAmbientArt
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalIsDark
import io.github.audxius.pillar.ui.theme.LocalPalette
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.styledSurface
import io.github.audxius.pillar.ui.theme.primaryActionFinish
import io.github.audxius.pillar.ui.theme.primaryActionOuter
import io.github.audxius.pillar.ui.theme.primaryActionFill
import io.github.audxius.pillar.ui.theme.StyleBackdrop
import io.github.audxius.pillar.ui.theme.rememberFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Tile shape. Box is 2:3, the shape of SteamGridDB covers and close to most real game boxes. */
fun CoverShape.ratio(): Float = when (this) {
    CoverShape.NATURAL -> 0.75f
    CoverShape.BOX -> 2f / 3f
    CoverShape.SQUARE -> 1f
    CoverShape.WIDE -> 4f / 3f
}

/** Shared-element modifier that silently does nothing when no transition scope is available. */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (key == null || shared == null || nav == null || !LocalSettings.current.animations) return this
    return with(shared) {
        this@sharedCover.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = nav)
    }
}

/** Press-to-shrink, controller focus glow, haptics and long-press, used by every tappable card. */
fun Modifier.tappable(
    shape: Shape? = null,
    onLongClick: (() -> Unit)? = null,
    /** No select sound (back buttons get the "back" sound from navigation instead). */
    silent: Boolean = false,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val animations = LocalSettings.current.animations
    val scale by animateFloatAsState(
        targetValue = when {
            !animations -> 1f
            pressed -> 0.95f
            focused -> 1.04f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tapScale",
    )
    val feedback = rememberFeedback()
    val corner = LocalCorner.current
    val s = shape ?: RoundedCornerShape(corner)
    val glow = MaterialTheme.colorScheme.primary
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (focused) Modifier.border(2.5.dp, glow, s) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .clip(s)
        .combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            onLongClick = onLongClick?.let { { feedback(); it() } },
            onClick = { if (!silent) feedback(); onClick() },
        )
}

@Composable
fun PillarBackground(modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    val palette = LocalPalette.current
    val dark = LocalIsDark.current
    val bg = MaterialTheme.colorScheme.background
    val ambientTarget = if (settings.ambientColor) LocalAmbient.current.value else null
    val primary by animateColorAsState(ambientTarget ?: palette.primary, tween(900), label = "ambient")
    val alpha = if (dark) 1f else 0.55f

    Box(modifier.fillMaxSize().background(bg)) {
        when (settings.background) {
            BackgroundStyle.SOLID -> Unit
            BackgroundStyle.GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to primary.copy(alpha = 0.38f * alpha),
                        0.55f to lerp(bg, palette.secondary, 0.06f),
                        1f to bg,
                    )
                )
            )
            BackgroundStyle.IMAGE -> {
                val uri = settings.backgroundImageUri
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(
                            if (settings.backgroundBlur > 0 && Build.VERSION.SDK_INT >= 31) Modifier.blur(settings.backgroundBlur.dp) else Modifier
                        ),
                    )
                    Box(Modifier.fillMaxSize().background(bg.copy(alpha = settings.backgroundDim)))
                }
            }
            BackgroundStyle.AURORA -> Aurora(primary, palette.secondary, palette.tertiary, alpha, settings.animateBackground && settings.animations)
            BackgroundStyle.STYLE -> {
                StyleBackdrop(primary, palette.secondary, palette.tertiary, settings.animateBackground && settings.animations)
                // The focused game's art shows through, as strongly as the style wants.
                val art = io.github.audxius.pillar.ui.theme.specFor(io.github.audxius.pillar.ui.theme.LocalStyle.current).artStrength
                if (art > 0f) ArtBackdrop(LocalAmbientArt.current.value, dark, bg, strength = art, topScrim = if (art >= 0.7f) 0.3f else 0.45f)
            }
            BackgroundStyle.ART -> {
                Aurora(primary, palette.secondary, palette.tertiary, alpha, settings.animateBackground && settings.animations)
                ArtBackdrop(LocalAmbientArt.current.value, dark, bg)
            }
        }
    }
}

@Composable
private fun Aurora(a: Color, b: Color, c: Color, alpha: Float, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "aurora")
    fun spec(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    val p1 by if (animate) t.animateFloat(0f, 1f, spec(17000), label = "p1") else remember { mutableStateOf(0.3f) }
    val p2 by if (animate) t.animateFloat(0f, 1f, spec(23000), label = "p2") else remember { mutableStateOf(0.6f) }
    val p3 by if (animate) t.animateFloat(0f, 1f, spec(29000), label = "p3") else remember { mutableStateOf(0.5f) }
    val bg = MaterialTheme.colorScheme.background
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        fun blob(color: Color, x: Float, y: Float, radius: Float, strength: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = strength * alpha), color.copy(alpha = 0f)),
                    center = Offset(x, y), radius = radius,
                ),
                radius = radius, center = Offset(x, y),
            )
        }
        blob(a, w * (0.1f + 0.5f * p1), h * (0.02f + 0.18f * p2), r * 0.62f, 0.55f)
        blob(b, w * (0.95f - 0.4f * p2), h * (0.30f + 0.2f * p3), r * 0.5f, 0.32f)
        blob(c, w * (0.2f + 0.6f * p3), h * (0.75f + 0.15f * p1), r * 0.55f, 0.26f)
        // Fade toward the bottom so lists stay readable.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.6f to bg.copy(alpha = 0.35f), 1f to bg.copy(alpha = 0.85f)))
    }
}

/** Frosted surface used for cards and panels. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    tint: Color? = null,
    padding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = shape ?: RoundedCornerShape(LocalCorner.current)
    Column(
        modifier
            .styledSurface(s)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.10f)) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * Tries each image source in order and falls back to [fallback] when none load. Optionally
 * reports the dominant color of the first image that loads.
 */
@Composable
fun MultiImage(
    sources: List<String>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    /** Show the whole image over a blurred, darkened copy of itself instead of cropping. */
    fit: Boolean = false,
    /** In fit mode, inset the image so it sits like a case in a frame. */
    fitInset: Dp = 0.dp,
    onColor: ((Color) -> Unit)? = null,
    onAspect: ((Float) -> Unit)? = null,
    /** Decode at most this many pixels on the long side (for backdrops that get blurred anyway). */
    decodeSize: Int? = null,
    fallback: @Composable BoxScope.() -> Unit,
) {
    var index by remember(sources) { mutableIntStateOf(0) }
    var loaded by remember(sources) { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier) {
        if (!loaded) fallback()
        if (index < sources.size) {
            val src = sources[index]
            var bitmapForColor by remember(src) { mutableStateOf<Bitmap?>(null) }
            if (fit && loaded) {
                // A tiny decode stretched to fill looks soft and costs almost nothing, unlike a live blur
                // on every tile.
                AsyncImage(
                    model = remember(src) { ImageRequest.Builder(context).data(src).size(24).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.9f },
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            AsyncImage(
                model = remember(src, decodeSize) {
                    ImageRequest.Builder(context).data(src).apply {
                        if (onColor != null) allowHardware(false)
                        decodeSize?.let { size(it) }
                    }.build()
                },
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else contentScale,
                alignment = alignment,
                modifier = Modifier.matchParentSize().then(if (fit && fitInset > 0.dp) Modifier.padding(fitInset) else Modifier),
                onSuccess = { state ->
                    loaded = true
                    val img = state.result.image
                    if (onAspect != null && img.height > 0) onAspect(img.width.toFloat() / img.height)
                    if (onColor != null) bitmapForColor = runCatching { state.result.image.toBitmap() }.getOrNull()
                },
                onError = { index++ },
            )
            if (onColor != null) {
                LaunchedEffect(bitmapForColor) {
                    val bmp = bitmapForColor ?: return@LaunchedEffect
                    val color = withContext(Dispatchers.Default) {
                        val p = Palette.from(bmp).maximumColorCount(16).generate()
                        (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb
                    }
                    color?.let { onColor(Color(it)) }
                }
            }
        }
    }
}

@Composable
fun systemColor(system: String): Color {
    val graph = LocalGraph.current
    val override = LocalSettings.current.systemOverrides[system]?.color
    return Color((override ?: graph.catalog.meta(system).color).toInt())
}

@Composable
fun systemName(system: String): String {
    val graph = LocalGraph.current
    return LocalSettings.current.systemOverrides[system]?.displayName?.takeIf { it.isNotBlank() }
        ?: graph.catalog.meta(system).short
}

/** A console's full everyday name ("Super Nintendo"), unless the user renamed it. */
@Composable
fun systemDisplayName(system: String): String {
    val graph = LocalGraph.current
    return LocalSettings.current.systemOverrides[system]?.displayName?.takeIf { it.isNotBlank() }
        ?: graph.catalog.displayName(system)
}

/**
 * Custom name, else the name the game was identified by checksum, else the closest thumbnail
 * name (proper spelling and casing), else the file name.
 */
@Composable
fun gameTitle(game: Game): String {
    LocalUserData.current.customTitle[game.id]?.let { return it }
    if (game.datName != null) return game.title
    val matched = rememberArt(game)?.matchedName
    return matched?.let { io.github.audxius.pillar.data.LibraryRepository.cleanTitle("$it.x") } ?: game.title
}

/** Placeholder cover: platform-colored gradient with a big watermark and the title. */
@Composable
fun BoxScope.CoverPlaceholder(title: String, system: String, compact: Boolean = false) {
    val color = systemColor(system)
    val short = systemName(system)
    Box(
        Modifier.matchParentSize().background(
            Brush.linearGradient(listOf(lerp(color, Color.Black, 0.15f), lerp(color, Color.Black, 0.65f)))
        )
    ) {
        Text(
            short.uppercase(),
            color = Color.White.copy(alpha = 0.12f),
            fontSize = if (compact) 28.sp else 46.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp).rotate(-8f),
        )
        Text(
            title,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(10.dp),
        )
    }
}

@Composable
fun GameCover(
    game: Game,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    ratio: Float? = null,
    compact: Boolean = false,
    /** Square art (SteamGridDB's square grids) ahead of the usual covers. */
    preferSquare: Boolean = false,
) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val cover = rememberCoverSources(game)
    val squareArt = if (preferSquare) rememberArt(game)?.sgdbSquare else null
    val sources = remember(cover, squareArt) { listOfNotNull(squareArt) + cover }
    val title = gameTitle(game)
    val natural = ratio == null && settings.coverShape == CoverShape.NATURAL
    val known = rememberAspect(game)
    // Natural shape: every cover keeps its real proportions (square GBA boxes, wide SNES boxes...).
    val target = ratio ?: if (natural) (known ?: graph.catalog.boxRatio(game.system)).coerceIn(0.5f, 1.8f) else settings.coverShape.ratio()
    val r by animateFloatAsState(target, tween(250), label = "coverRatio")
    val shape = RoundedCornerShape(LocalCorner.current * 0.7f)
    Box(modifier.aspectRatio(r).sharedCover(sharedKey).clip(shape)) {
        // Uniform tiles: art close to the tile's shape fills it (a barely visible trim); art of a
        // clearly different shape is shown whole, framed over a blurred copy of itself.
        val fill = settings.coverCrop || (known != null && (natural || kotlin.math.abs(kotlin.math.ln(known / target)) < io.github.audxius.pillar.data.COVER_FILL_TOLERANCE))
        MultiImage(
            sources, Modifier.matchParentSize(),
            fit = !fill,
            fitInset = if (compact) 5.dp else 9.dp,
            onAspect = { a -> graph.artwork.recordAspect(game.id, a) },
        ) { CoverPlaceholder(title, game.system, compact) }
        Gloss(shape)
        if (game.remote) {
            // Lives on RomM: downloads when you play it.
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CloudDownload, "On RomM", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (game.id in user.favorites) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Cover with its title underneath, the building block of every game grid and row. */
@Composable
fun GameTile(
    game: Game,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    showTitle: Boolean = LocalSettings.current.showGameTitles,
    showSystem: Boolean = false,
    /** Square tiles with square art, for big two-column grids. */
    square: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier.tappable(RoundedCornerShape(LocalCorner.current * 0.7f), onLongClick, onClick = onClick)) {
        GameCover(
            game,
            Modifier.fillMaxWidth(),
            sharedKey = sharedKey,
            ratio = if (square) 1f else null,
            compact = !square,
            preferSquare = square,
        )
        if (showTitle) {
            Spacer(Modifier.height(6.dp))
            Text(
                gameTitle(game),
                style = MaterialTheme.typography.labelLarge,
                // Always two lines tall so rows of tiles line up whatever the title length.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            if (showSystem) {
                Text(
                    systemName(game.system),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun SystemChip(system: String, modifier: Modifier = Modifier) {
    val color = systemColor(system)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(systemName(system), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).tappable(RoundedCornerShape(50), onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(sw))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                -90f, 360f * animated, false, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun LinearMeter(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(modifier.height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(animated).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Soft pulsing block shown while content loads. */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.05f, 0.14f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(LocalCorner.current * 0.7f)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = a)))
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}

fun gamesLabel(n: Int): String = if (n == 1) "1 game" else "$n games"

/**
 * Adds [gap] above its content only when the content actually draws something, so home widgets
 * that have nothing to show don't leave empty space behind.
 */
@Composable
fun GapIfNotEmpty(gap: Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}
        val top = gap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOf { it.width }
        val height = top + placeables.sumOf { it.height }
        layout(width, height) {
            var y = top
            placeables.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}

/** Fades the bottom of an image to transparent so it melts into whatever background is behind it. */
fun Modifier.fadeBottom(start: Float = 0.45f): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(start to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/** Green that stays readable on both dark and light backgrounds. */
@Composable
fun successColor(): Color = if (LocalIsDark.current) Color(0xFF4ADE80) else Color(0xFF15803D)

/** Artwork known for a game; asks the artwork repository to look it up the first time it's shown. */
@Composable
fun rememberArt(game: Game): io.github.audxius.pillar.data.ArtSet? {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    // Watch only this game's entry, so one finished lookup doesn't redraw every tile on screen.
    val flow = remember(game.id) {
        graph.artwork.art.map { it[game.id] }.distinctUntilChanged()
    }
    val art by flow.collectAsStateWithLifecycle(graph.artwork.art.value[game.id])
    LaunchedEffect(game.id, settings.sgdbKey, settings.onlineCovers) { graph.artwork.ensure(game) }
    return art
}

@Composable
fun rememberAspect(game: Game): Float? {
    val graph = LocalGraph.current
    val flow = remember(game.id) { graph.artwork.aspects.map { it[game.id] }.distinctUntilChanged() }
    val aspect by flow.collectAsStateWithLifecycle(graph.artwork.aspects.value[game.id])
    return aspect
}

@Composable
fun rememberCoverSources(game: Game): List<String> {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val art = rememberArt(game)
    val mediaVersion by graph.library.mediaVersion.collectAsStateWithLifecycle()
    return remember(game.id, art, settings.coverStyle, settings.onlineCovers, settings.onlineArt, settings.coverShape, user.customCover[game.id], user.remoteCover[game.id], mediaVersion) {
        graph.library.coverCandidates(game, settings, user, art)
    }
}

@Composable
fun rememberHeroSources(game: Game): List<String> {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val art = rememberArt(game)
    val mediaVersion by graph.library.mediaVersion.collectAsStateWithLifecycle()
    return remember(game.id, art, settings.coverStyle, settings.onlineCovers, user.customCover[game.id], user.remoteCover[game.id], mediaVersion) {
        graph.library.heroCandidates(game, settings, user, art)
    }
}

/** Glassy highlight and hairline rim that give tiles a polished, console-menu look. */
@Composable
fun BoxScope.Gloss(shape: Shape) {
    Box(
        Modifier.matchParentSize()
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.35f to Color.Transparent))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))), shape)
    )
}

/**
 * Blurred, dimmed game art behind everything, crossfading as the focused game changes. Falls
 * back to whatever is drawn underneath when there's no art.
 */
@Composable
private fun ArtBackdrop(sources: List<String>?, dark: Boolean, bg: Color, strength: Float = 1f, topScrim: Float = 0.45f) {
    androidx.compose.animation.Crossfade(
        targetState = sources?.firstOrNull()?.let { sources },
        animationSpec = tween(700),
        label = "artBackdrop",
    ) { current ->
        if (current == null) return@Crossfade
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = strength }) {
            // A small decode is all a heavily blurred backdrop needs, and keeps focus changes cheap.
            MultiImage(
                current,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier),
                decodeSize = 160,
            ) {}
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = if (dark) topScrim else topScrim + 0.1f),
                        0.5f to bg.copy(alpha = if (dark) topScrim + 0.17f else topScrim + 0.25f),
                        1f to bg.copy(alpha = if (dark) 0.92f else 0.9f),
                    )
                )
            )
        }
    }
}

/**
 * The big call to action (Play / Download & play), drawn in the current style. Shows progress
 * when given; with [onMenu] the right end becomes a separate dropdown segment (e.g. emulator choice).
 */
@Composable
fun PrimaryAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (brush, content) = io.github.audxius.pillar.ui.theme.primaryActionColors()
    val shape = RoundedCornerShape(50)
    val primary = MaterialTheme.colorScheme.primary
    val light = content == Color.White
    // White labels get a firm dark shadow so they stay legible over glossy streaks.
    val shadow = androidx.compose.ui.graphics.Shadow(
        if (light) Color(0xFF00284D).copy(alpha = 0.7f) else Color.Transparent,
        offset = androidx.compose.ui.geometry.Offset(0f, 2f), blurRadius = 5f,
    )
    Box(
        modifier
            .primaryActionOuter(shape)
            .shadow(14.dp, shape, ambientColor = primary, spotColor = primary)
            .primaryActionFill(shape, brush)
            .then(if (onMenu == null) Modifier.tappable(shape, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.25f)))
        }
        Box(Modifier.matchParentSize().primaryActionFinish(shape))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (onMenu != null) Modifier.tappable(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50), onClick = onClick) else Modifier)
                    .padding(start = if (onMenu != null) 48.dp else 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = content, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        label, maxLines = 1, color = content,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black, shadow = shadow),
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                        )
                    }
                }
            }
            if (onMenu != null) {
                Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(content.copy(alpha = 0.35f)))
                Box(
                    Modifier.width(56.dp).fillMaxHeight()
                        .tappable(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50), onClick = onMenu),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ExpandMore, "Choose emulator", tint = content, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}
