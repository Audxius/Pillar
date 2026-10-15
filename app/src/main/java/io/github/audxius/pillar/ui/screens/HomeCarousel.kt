@file:OptIn(ExperimentalFoundationApi::class)

package io.github.audxius.pillar.ui.screens

import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameLogo
import io.github.audxius.pillar.ui.components.Gloss
import io.github.audxius.pillar.ui.components.PrimaryAction
import io.github.audxius.pillar.ui.theme.chipColors
import io.github.audxius.pillar.ui.theme.styledSurface
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.aspectRatio
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Dp
import io.github.audxius.pillar.ui.components.MultiImage
import io.github.audxius.pillar.ui.components.fadeBottom
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
import io.github.audxius.pillar.ui.theme.onColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class Shelf(val key: String, val label: String, val games: List<Game>)

@Composable
fun HomeCarouselScreen() {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val games = rememberVisibleGames()
    var quick by remember { mutableStateOf<Game?>(null) }

    val systems = rememberOrderedSystems(games)
    val shelves = remember(games, user.lastPlayed, user.favorites, systems) {
        buildList {
            val recent = games.filter { (user.lastPlayed[it.id] ?: 0) > 0 }.sortedByDescending { user.lastPlayed[it.id] }
            if (recent.isNotEmpty()) add(Shelf("recent", "Recent", recent.take(30)))
            val favs = games.filter { it.id in user.favorites }
            if (favs.isNotEmpty()) add(Shelf("fav", "Favorites", favs))
            add(Shelf("all", "All", games.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }))
            systems.forEach { (system, list) -> add(Shelf("sys:$system", graph.catalog.meta(system).short, list)) }
            add(Shelf("new", "New", games.sortedByDescending { it.modified }.take(30)))
        }
    }
    // Start on Recent once there's enough history to fill the carousel, otherwise on everything.
    var shelfKey by rememberSaveable {
        mutableStateOf(if ((shelves.firstOrNull { it.key == "recent" }?.games?.size ?: 0) >= 4) "recent" else "all")
    }
    val shelf = shelves.firstOrNull { it.key == shelfKey } ?: shelves.firstOrNull()

    if (games.isEmpty() || shelf == null || shelf.games.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            CarouselTopBar(shelves, shelfKey) { shelfKey = it }
            Spacer(Modifier.height(24.dp))
            EmptyLibraryCardPublic()
        }
        return
    }

    // One scroll position per shelf, so switching back keeps your place.
    val listStates = remember { HashMap<String, LazyListState>() }
    val listState = listStates.getOrPut(shelf.key) { LazyListState() }
    val selectedIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: 0
        }
    }
    val selected = shelf.games.getOrNull(selectedIndex.coerceIn(0, shelf.games.lastIndex)) ?: shelf.games.first()
    val hero = rememberHeroSources(selected)
    val ambientArt = LocalAmbientArt.current
    val ambient = LocalAmbient.current
    val selColor = systemColor(selected.system)
    LaunchedEffect(selected.id, hero) {
        ambientArt.value = hero
        ambient.value = selColor
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CarouselTopBar(shelves, shelf.key) { shelfKey = it }
        Spacer(Modifier.height(14.dp))
        // The art sits in its own frame, shown whole, with nothing drawn over it.
        val corner = LocalCorner.current
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f)
                .shadow(18.dp, RoundedCornerShape(corner), ambientColor = selColor, spotColor = selColor)
                .clip(RoundedCornerShape(corner))
        ) {
            Crossfade(targetState = selected.id to hero, animationSpec = tween(400), label = "hero") { (_, src) ->
                MultiImage(src, Modifier.fillMaxSize(), fit = true) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(selColor, lerp(selColor, Color.Black, 0.6f)))))
                }
            }
            Gloss(RoundedCornerShape(corner))
        }
        Spacer(Modifier.height(16.dp))
        GameInfo(selected)
        Spacer(Modifier.weight(1f))
        Carousel(shelf.games, listState, selectedIndex, onLongPress = { quick = it })
        Text(
            "${selectedIndex + 1} / ${shelf.games.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        )
        Spacer(Modifier.navigationBarsPadding().height(BottomBarSpace - 22.dp))
    }
    quick?.let { g -> GameQuickSheet(g, "home", onDismiss = { quick = null }) }
}

@Composable
private fun CarouselTopBar(shelves: List<Shelf>, selected: String, onSelect: (String) -> Unit) {
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(shelves, key = { _, s -> s.key }) { _, s ->
                val active = s.key == selected
                val accent = if (s.key.startsWith("sys:")) systemColor(s.key.removePrefix("sys:")) else MaterialTheme.colorScheme.primary
                StyledChip(s.label, active, accent) { onSelect(s.key) }
            }
        }
        StyledIconButton(Icons.Rounded.Search, "Search", Modifier.padding(end = 12.dp)) { nav.navigate(Routes.SEARCH) }
    }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
private fun GameInfo(game: Game) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val launch = rememberGameLauncher()
    val scope = rememberCoroutineScope()
    val art = rememberArt(game)
    val logo = remember(game.id, art) { graph.library.logo(game, art) }
    val fav = game.id in user.favorites
    val color = systemColor(game.system)
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = game,
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith fadeOut(tween(120)) },
        contentKey = { it.id },
        label = "gameInfo",
    ) { g ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(118.dp), verticalArrangement = Arrangement.Center) {
            var logoFailed by remember(g.id) { mutableStateOf(false) }
            val gLogo = if (g.id == game.id) logo else null
            if (settings.showLogos && gLogo != null && !logoFailed) {
                // Same visual weight for every logo, whatever its shape or padding.
                GameLogo(gLogo, gameTitle(g), maxWidth = 260.dp, maxHeight = 78.dp, onFailed = { logoFailed = true })
            } else {
                Text(gameTitle(g), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(systemName(g.system), color)
                if (g.remote) InfoChip("On RomM", null, Icons.Rounded.CloudDownload)
                user.lastPlayed[g.id]?.let { InfoChip(timeAgo(it), null) }
                if (user.raIds[g.id] != null) InfoChip("Trophies", Color(0xFFFFC94D), Icons.Rounded.EmojiEvents)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        val dl = game.rommId?.let { downloads[it] }
        val busy = game.remote && (dl is io.github.audxius.pillar.data.DownloadState.Queued || dl is io.github.audxius.pillar.data.DownloadState.Running)
        PrimaryAction(
            label = when {
                busy && dl is io.github.audxius.pillar.data.DownloadState.Running -> "${(dl.fraction * 100).toInt()}%"
                busy -> "Waiting"
                game.remote -> "Download & play"
                else -> "Play"
            },
            icon = if (game.remote) Icons.Rounded.CloudDownload else Icons.Rounded.PlayArrow,
            modifier = Modifier.weight(1f).height(54.dp),
            progress = (dl as? io.github.audxius.pillar.data.DownloadState.Running)?.fraction?.takeIf { busy },
        ) { if (!busy) launch(game, null) }
        Spacer(Modifier.width(10.dp))
        StyledIconButton(Icons.Rounded.Info, "Details", size = 54.dp) { nav.navigate(Routes.game(game.id, "home")) }
        Spacer(Modifier.width(10.dp))
        StyledIconButton(
            if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Favorite", size = 54.dp,
            tint = if (fav) Color(0xFFFFC94D) else MaterialTheme.colorScheme.onSurface,
        ) {
            scope.launch { graph.store.updateUser { u -> u.copy(favorites = if (fav) u.favorites - game.id else u.favorites + game.id) } }
        }
    }
}

@Composable
private fun InfoChip(text: String, dot: Color?, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val (bg, fg, rim) = chipColors(false)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, rim, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = dot ?: fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        } else if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
private fun Carousel(games: List<Game>, state: LazyListState, selectedIndex: Int, onLongPress: (Game) -> Unit) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val launch = rememberGameLauncher()
    val scope = rememberCoroutineScope()
    val corner = LocalCorner.current
    val aspects by graph.artwork.aspects.collectAsStateWithLifecycle()
    val itemHeight = 196.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(itemHeight * 1.14f + 10.dp)) {
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
                val scale by animateFloatAsState(if (focused) 1.12f else 0.9f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "s")
                val alpha by animateFloatAsState(if (focused) 1f else 0.65f, label = "a")
                val glow = systemColor(g.system)
                // Uniform tiles by default; with "Real shape" each cover sizes to its art.
                val ratio = if (settings.coverShape == io.github.audxius.pillar.data.CoverShape.NATURAL) {
                    (aspects[g.id] ?: graph.catalog.boxRatio(g.system)).coerceIn(0.5f, 1.8f)
                } else settings.coverShape.ratio()
                GameCover(
                    g,
                    Modifier.height(itemHeight).width(itemHeight * ratio)
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                        .then(
                            if (focused) Modifier.shadow(20.dp, RoundedCornerShape(corner * 0.7f), ambientColor = glow, spotColor = glow)
                            else Modifier
                        )
                        .tappable(RoundedCornerShape(corner * 0.7f), onLongClick = { onLongPress(g) }) {
                            if (focused) launch(g, null) else scope.launch { state.animateScrollToItem(index) }
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
