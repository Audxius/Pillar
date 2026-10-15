@file:OptIn(ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
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
import io.github.audxius.pillar.ui.components.Gloss
import io.github.audxius.pillar.ui.components.MultiImage
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
import kotlinx.coroutines.launch
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
        Spacer(Modifier.height(12.dp))
        // A slim widescreen strip filled edge to edge: no bars around wide or tall art. Tall art
        // (DS screens, box shots) is anchored to the top so the title screen stays in view.
        val corner = LocalCorner.current
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(2.3f)
                .shadow(14.dp, RoundedCornerShape(corner), ambientColor = selColor, spotColor = selColor)
                .clip(RoundedCornerShape(corner))
        ) {
            Crossfade(targetState = selected.id to hero, animationSpec = tween(350), label = "hero") { (_, src) ->
                MultiImage(src, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(selColor, lerp(selColor, Color.Black, 0.6f)))))
                }
            }
            Gloss(RoundedCornerShape(corner))
        }
        Spacer(Modifier.height(12.dp))
        GameInfo(selected)
        Spacer(Modifier.height(12.dp))
        // The carousel takes whatever room is left above the dock, so covers are never hidden.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val itemHeight = ((maxHeight - 26.dp) / 1.12f).coerceIn(96.dp, 230.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Carousel(shelf.games, listState, selectedIndex, itemHeight, onLongPress = { quick = it })
                Text(
                    "${selectedIndex + 1} / ${shelf.games.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                )
            }
        }
        // Room for the floating dock.
        Spacer(Modifier.navigationBarsPadding().height(96.dp))
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

/** Logo or title (never both), a few facts and the Play button. */
@Composable
private fun GameInfo(game: Game) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val launch = rememberGameLauncher()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()

    Crossfade(targetState = game, animationSpec = tween(180), label = "gameInfo") { g ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // Fixed height so the layout doesn't jump between logos, titles and loading.
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.CenterStart) { TitleOrLogo(g) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(systemName(g.system), systemColor(g.system))
                if (g.remote) InfoChip("On RomM", null, Icons.Rounded.CloudDownload)
                user.lastPlayed[g.id]?.let { InfoChip(timeAgo(it), null) }
                if (user.raIds[g.id] != null) InfoChip("Trophies", Color(0xFFFFC94D), Icons.Rounded.EmojiEvents)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
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
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp),
        progress = (dl as? DownloadState.Running)?.fraction?.takeIf { busy },
    ) { if (!busy) launch(game, null) }
}

/** Shows the game's logo when enabled and available, otherwise its title. Nothing flashes in between. */
@Composable
fun TitleOrLogo(g: Game, maxWidth: Dp = 260.dp, maxHeight: Dp = 72.dp, alignment: Alignment = Alignment.CenterStart) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val art = rememberArt(g)
    val logo = remember(g.id, art) { if (settings.showLogos) graph.library.logo(g, art) else null }
    var failed by remember(logo) { mutableStateOf(false) }
    // While artwork is still being looked up for a game that may have a logo, show nothing rather
    // than the plain title for a moment.
    val pending = settings.showLogos && art == null
    when {
        logo != null && !failed -> GameLogo(logo, gameTitle(g), maxWidth = maxWidth, maxHeight = maxHeight, alignment = alignment, onFailed = { failed = true })
        pending -> Unit
        else -> Text(gameTitle(g), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
