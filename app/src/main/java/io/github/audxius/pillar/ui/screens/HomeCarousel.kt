@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.data.CoverShape
import io.github.audxius.pillar.data.DownloadState
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameLogo
import io.github.audxius.pillar.ui.components.PrimaryAction
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.ratio
import io.github.audxius.pillar.ui.components.rememberArt
import io.github.audxius.pillar.ui.components.rememberHeroSources
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalAmbientArt
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.chipColors
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Gold used for everything RetroAchievements. */
val TrophyGold = Color(0xFFFFC94D)

/**
 * Home: every game, most recently played first (then the rest by name), as a cover carousel
 * under the focused game's logo. Browsing by system lives in the Library tab.
 */
@Composable
fun HomeCarouselScreen() {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val games = rememberVisibleGames()
    var quick by remember { mutableStateOf<Game?>(null) }

    val ordered = remember(games, user.lastPlayed, user.customTitle) {
        val (played, rest) = games.partition { (user.lastPlayed[it.id] ?: 0) > 0 }
        played.sortedByDescending { user.lastPlayed[it.id] } + rest.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }
    }
    if (ordered.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(48.dp))
            EmptyLibraryCardPublic()
        }
        return
    }

    val listState = rememberLazyListState()
    val selectedIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: 0
        }
    }
    val centered = ordered.getOrNull(selectedIndex.coerceIn(0, ordered.lastIndex)) ?: ordered.first()
    // While flinging, the logo, backdrop and lookups wait until a game stays centered for a moment;
    // loading them for every cover that flies past is what made fast scrolling stutter.
    var selected by remember { mutableStateOf(centered) }
    LaunchedEffect(centered.id, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) delay(260)
        selected = centered
    }
    val hero = rememberHeroSources(selected)
    val ambientArt = LocalAmbientArt.current
    val ambient = LocalAmbient.current
    val selColor = systemColor(selected.system)
    LaunchedEffect(selected.id, hero) {
        ambientArt.value = hero
        ambient.value = selColor
    }
    // Look up achievement support for whatever is in focus (cached, so cheap after the first time).
    LaunchedEffect(selected.id) { if (!selected.remote) runCatching { graph.ra.idFor(selected, graph.catalog) } }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val itemHeight = (maxHeight * 0.3f).coerceIn(130.dp, 270.dp)
        Column(Modifier.fillMaxSize()) {
            // The logo gets all the room the carousel and buttons don't need.
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = selected, animationSpec = tween(200), label = "logo") { g ->
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TitleOrLogo(
                            g, maxWidth = maxWidth, maxHeight = minOf(maxHeight, 240.dp),
                            alignment = Alignment.Center, big = true,
                        )
                    }
                }
            }
            GameMetaPills(selected, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Spacer(Modifier.height(12.dp))
            HomePlayButton(selected)
            Spacer(Modifier.height(6.dp))
            Carousel(ordered, listState, selectedIndex, itemHeight, onLongPress = { quick = it })
            Text(
                "${selectedIndex + 1} / ${ordered.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
            )
            // Room for the floating dock.
            Spacer(Modifier.navigationBarsPadding().height(96.dp))
        }
    }
    quick?.let { g -> GameQuickSheet(g, "home", onDismiss = { quick = null }) }
}

/** Pill chip drawn in the current style. */
@Composable
fun StyledChip(label: String, active: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val (bg, fg, rim) = chipColors(active)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, rim, RoundedCornerShape(50))
            .tappable(RoundedCornerShape(50), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = fg)
    }
}

@Composable
fun StyledIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).styledSurface(CircleShape).tappable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.46f)) }
}

/** System, last played, achievements and RomM status as a centered row of pills. */
@Composable
fun GameMetaPills(g: Game, modifier: Modifier = Modifier, extra: List<String> = emptyList()) {
    val user = LocalUserData.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaPill(systemName(g.system), systemColor(g.system), dotted = true)
        val last = user.lastPlayed[g.id] ?: 0L
        MetaPill(if (last > 0) timeAgo(last) else "Not played yet", null, Icons.Rounded.Schedule)
        if (user.raIds[g.id] != null) MetaPill("Achievements", TrophyGold, Icons.Rounded.EmojiEvents)
        if (g.remote) MetaPill("On RomM", null, Icons.Rounded.CloudDownload)
        extra.forEach { MetaPill(it, null) }
    }
}

/** A labelled pill: a colored dot or an icon, then text. */
@Composable
fun MetaPill(text: String, color: Color?, icon: ImageVector? = null, dotted: Boolean = false) {
    val (bg, fg, rim) = chipColors(false)
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(bg)
            .then(if (color != null && dotted) Modifier.background(color.copy(alpha = 0.16f)) else Modifier)
            .border(1.dp, rim, RoundedCornerShape(50))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> {
                Icon(icon, null, tint = color ?: fg.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            dotted && color != null -> {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                Spacer(Modifier.width(7.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun HomePlayButton(game: Game) {
    val graph = LocalGraph.current
    val launch = rememberGameLauncher()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val dl = game.rommId?.let { downloads[it] }
    val busy = game.remote && (dl is DownloadState.Queued || dl is DownloadState.Running)
    PrimaryAction(
        label = when {
            busy && dl is DownloadState.Running -> "Downloading ${(dl.fraction * 100).toInt()}%"
            busy -> "Waiting to download"
            game.remote -> "Download & play"
            else -> "Play"
        },
        icon = if (game.remote) Icons.Rounded.CloudDownload else Icons.Rounded.PlayArrow,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(54.dp),
        progress = (dl as? DownloadState.Running)?.fraction?.takeIf { busy },
    ) { if (!busy) launch(game, null) }
}

/** Shows the game's logo when enabled and available, otherwise its title. Nothing flashes in between. */
@Composable
fun TitleOrLogo(g: Game, maxWidth: Dp = 260.dp, maxHeight: Dp = 72.dp, alignment: Alignment = Alignment.CenterStart, big: Boolean = false) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val art = rememberArt(g)
    val logo = remember(g.id, art) { if (settings.showLogos) graph.library.logo(g, art) else null }
    var failed by remember(logo) { mutableStateOf(false) }
    // While artwork is still being looked up for a game that may have a logo, show nothing rather
    // than the plain title for a moment.
    val pending = settings.showLogos && art == null
    when {
        logo != null && !failed -> GameLogo(
            logo, gameTitle(g), maxWidth = maxWidth, maxHeight = maxHeight, alignment = alignment,
            // A big logo fills most of its box; small ones keep the shared visual weight.
            area = if (big) maxWidth.value * maxHeight.value * 0.62f else 13000f,
            onFailed = { failed = true },
        )
        pending -> Unit
        else -> Text(
            gameTitle(g),
            style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
            maxLines = if (big) 3 else 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.Center) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun Carousel(games: List<Game>, state: LazyListState, selectedIndex: Int, itemHeight: Dp, onLongPress: (Game) -> Unit) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val corner = LocalCorner.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(itemHeight * 1.12f + 8.dp)) {
        // Side padding that lets the first and last cover sit in the middle.
        val side = ((maxWidth - itemHeight * settings.coverShape.ratio()) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight(),
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, g ->
                val focused = index == selectedIndex
                val scale by animateFloatAsState(if (focused) 1.1f else 0.9f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "s")
                // Uniform tiles by default; with "Real shape" each cover sizes to its art.
                val ratio = if (settings.coverShape == CoverShape.NATURAL) {
                    (io.github.audxius.pillar.ui.components.rememberAspect(g) ?: graph.catalog.boxRatio(g.system)).coerceIn(0.5f, 1.8f)
                } else settings.coverShape.ratio()
                GameCover(
                    g,
                    Modifier.height(itemHeight).width(itemHeight * ratio)
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (focused) 1f else 0.7f }
                        .tappable(RoundedCornerShape(corner * 0.7f), onLongClick = { onLongPress(g) }) {
                            // Tapping the centered game opens its page; tapping another one brings it to the center.
                            if (focused) nav.navigate(Routes.game(g.id, "home")) else scope.launch { state.animateScrollToItem(index) }
                        },
                    sharedKey = "cover-home-${g.id}",
                    ratio = ratio,
                    compact = true,
                )
            }
        }
    }
}

/** The "pick your ROM folder" card, shared with the feed layout. */
@Composable
fun EmptyLibraryCardPublic() {
    val nav = LocalNav.current
    io.github.audxius.pillar.ui.components.EmptyState(
        Icons.Rounded.PlayArrow,
        "Let's find your games",
        "Point Pillar at your ROMs folder (one folder per system, such as snes, gba or psx), or connect RomM to see your whole server here.",
        action = "Choose ROM folder",
        onAction = { nav.navigate(Routes.settingsPage(io.github.audxius.pillar.ui.settings.SettingsPage.LIBRARY)) },
    )
}
