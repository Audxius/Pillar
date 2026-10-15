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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        // Crisp art across the top; the blurred global backdrop continues it below.
        Crossfade(targetState = selected.id to hero, animationSpec = tween(500), label = "hero") { (_, src) ->
            MultiImage(src, Modifier.fillMaxWidth().height(screenH * 0.62f).fadeBottom(0.55f)) {}
        }
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) { CarouselTopBar(shelves, shelf.key) { shelfKey = it } }
            Spacer(Modifier.weight(1f))
            GameInfo(selected)
            Spacer(Modifier.height(18.dp))
            Carousel(shelf.games, listState, selectedIndex, onLongPress = { quick = it })
            Text(
                "${selectedIndex + 1} / ${shelf.games.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
            Spacer(Modifier.navigationBarsPadding().height(BottomBarSpace - 20.dp))
        }
    }
    quick?.let { g -> GameQuickSheet(g, "home", onDismiss = { quick = null }) }
}

@Composable
private fun CarouselTopBar(shelves: List<Shelf>, selected: String, onSelect: (String) -> Unit) {
    val nav = LocalNav.current
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ClockPill()
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .tappable(CircleShape) { nav.navigate(Routes.SEARCH) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Search, "Search", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(shelves, key = { _, s -> s.key }) { _, s ->
                val active = s.key == selected
                val accent = if (s.key.startsWith("sys:")) systemColor(s.key.removePrefix("sys:")) else MaterialTheme.colorScheme.primary
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (active) Color.White else Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, if (active) Color.White else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .tappable(RoundedCornerShape(50)) { onSelect(s.key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        s.label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        color = if (active) Color(0xFF15131C) else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockPill() {
    val settings = LocalSettings.current
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            context.getSystemService(BatteryManager::class.java)?.let { bm ->
                battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: -1
                charging = bm.isCharging
            }
            delay(1000L * (60 - Calendar.getInstance().get(Calendar.SECOND)).coerceAtLeast(1))
        }
    }
    val time = SimpleDateFormat(if (settings.clock24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now))
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text("  |  $date", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        if (settings.showBattery && battery >= 0) {
            Text("  |  ", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Icon(
                if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null,
                tint = if (battery <= 15 && !charging) Color(0xFFFF6B6B) else Color.White,
                modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = 90f },
            )
            Text(" $battery%", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

@Composable
private fun GameInfo(game: Game) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val launch = rememberGameLauncher()
    val scope = rememberCoroutineScope()
    val art = rememberArt(game)
    val logo = remember(game.id, art) { graph.library.logo(game, art) }
    val fav = game.id in user.favorites
    val color = systemColor(game.system)

    AnimatedContent(
        targetState = game,
        transitionSpec = { (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 6 }) togetherWith fadeOut(tween(150)) },
        contentKey = { it.id },
        label = "gameInfo",
    ) { g ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            var logoFailed by remember(g.id) { mutableStateOf(false) }
            if (logo != null && !logoFailed) {
                AsyncImage(
                    logo, gameTitle(g),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxWidth(0.8f).height(84.dp),
                )
            } else {
                Text(
                    gameTitle(g),
                    style = MaterialTheme.typography.displaySmall.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 16f)),
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(systemName(g.system), color)
                user.lastPlayed[g.id]?.let { InfoChip(timeAgo(it), null) }
                if (user.raIds[g.id] != null) InfoChip("Achievements", Color(0xFFFFC94D), Icons.Rounded.EmojiEvents)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        val primary = MaterialTheme.colorScheme.primary
        Row(
            Modifier.height(54.dp).shadow(14.dp, RoundedCornerShape(50), ambientColor = primary, spotColor = primary)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(primary, lerp(primary, MaterialTheme.colorScheme.tertiary, 0.5f))))
                .tappable(RoundedCornerShape(50)) { launch(game, null) }
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = primary.onColor(), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play", style = MaterialTheme.typography.titleMedium, color = primary.onColor())
        }
        Spacer(Modifier.width(10.dp))
        GlassCircle(Icons.Rounded.Info, "Details") { nav.navigate(Routes.game(game.id, "home")) }
        Spacer(Modifier.width(10.dp))
        GlassCircle(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Favorite", tint = if (fav) Color(0xFFFFD54F) else Color.White) {
            scope.launch { graph.store.updateUser { u -> u.copy(favorites = if (fav) u.favorites - game.id else u.favorites + game.id) } }
        }
    }
}

@Composable
private fun InfoChip(text: String, dot: Color?, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = dot ?: Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        } else if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun GlassCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .tappable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun Carousel(games: List<Game>, state: LazyListState, selectedIndex: Int, onLongPress: (Game) -> Unit) {
    val settings = LocalSettings.current
    val launch = rememberGameLauncher()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val corner = LocalCorner.current
    val itemHeight = 168.dp
    val itemWidth = itemHeight * settings.coverShape.ratio()
    BoxWithConstraints(Modifier.fillMaxWidth().height(itemHeight * 1.16f + 8.dp)) {
        // Side padding that lets the first and last cover sit in the middle.
        val side = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
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
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    if (focused) 1.14f else 0.92f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 400f), label = "s",
                )
                val alpha by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0.7f, label = "a")
                val glow = systemColor(g.system)
                GameCover(
                    g,
                    Modifier.width(itemWidth)
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                        .then(
                            if (focused) Modifier.shadow(22.dp, RoundedCornerShape(corner * 0.7f), ambientColor = glow, spotColor = glow)
                                .border(2.5.dp, Color.White, RoundedCornerShape(corner * 0.7f))
                            else Modifier
                        )
                        .tappable(RoundedCornerShape(corner * 0.7f), onLongClick = { onLongPress(g) }) {
                            if (focused) launch(g, null) else scope.launch { state.animateScrollToItem(index) }
                        },
                    sharedKey = "cover-home-${g.id}",
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
        "Point Pillar at your ROMs folder. It should contain one folder per system, such as snes, gba or psx.",
        action = "Choose ROM folder",
        onAction = { nav.navigate(Routes.settingsPage(io.github.audxius.pillar.ui.settings.SettingsPage.LIBRARY)) },
    )
}
