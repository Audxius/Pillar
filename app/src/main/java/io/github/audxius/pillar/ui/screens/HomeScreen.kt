package io.github.audxius.pillar.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.WidgetType
import io.github.audxius.pillar.data.RemovedWidgets
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameTile
import io.github.audxius.pillar.ui.components.GapIfNotEmpty
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.MultiImage
import io.github.audxius.pillar.ui.components.SystemChip
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Games the user hasn't hidden, on systems they haven't hidden. */
@Composable
fun rememberVisibleGames(): List<Game> {
    val graph = LocalGraph.current
    val games by graph.allGames.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val user = LocalUserData.current
    return remember(games, settings.hiddenSystems, user.hidden) {
        games.filter { it.system !in settings.hiddenSystems && it.id !in user.hidden }
    }
}

@Composable
fun HomeScreen() {
    if (LocalSettings.current.homeLayout == io.github.audxius.pillar.data.HomeLayout.CAROUSEL) HomeCarouselScreen() else HomeFeed()
}

@Composable
private fun HomeFeed() {
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val games = rememberVisibleGames()
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = null }
    var quick by remember { mutableStateOf<Game?>(null) }

    val nav = LocalNav.current
    val byRecent = remember(games, user.lastPlayed, user.customTitle) {
        val (played, rest) = games.partition { (user.lastPlayed[it.id] ?: 0) > 0 }
        played.sortedByDescending { user.lastPlayed[it.id] } + rest.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }
    }
    val recent = remember(games, user.lastPlayed) {
        games.filter { (user.lastPlayed[it.id] ?: 0) > 0 }.sortedByDescending { user.lastPlayed[it.id] }.take(10)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
    ) {
        val enabled = settings.homeWidgets.filter { it.enabled && it.type !in RemovedWidgets }.map { it.type }
        item {
            // Search the whole library (and RomM) without leaving home.
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp, end = 14.dp), contentAlignment = Alignment.CenterEnd) {
                RoundIcon(Icons.Rounded.Search, "Search") { nav.navigate(Routes.SEARCH) }
            }
        }
        if (games.isEmpty()) item("empty") { GapIfNotEmpty(12.dp) { EmptyLibraryCard() } }
        for ((index, type) in enabled.withIndex()) {
            item(type.name) {
                GapIfNotEmpty(if (index == 0) 0.dp else 22.dp) {
                when (type) {
                    WidgetType.HEADER -> Unit
                    WidgetType.CONTINUE -> ContinueWidget(recent.ifEmpty { games.shuffledStable().take(6) }, isRecent = recent.isNotEmpty())
                    WidgetType.PLATFORMS -> Unit
                    WidgetType.FAVORITES -> GameRowWidget("Favorites", games.filter { it.id in user.favorites }, "fav") { quick = it }
                    WidgetType.RECENTLY_ADDED -> GameRowWidget("Recently added", games.sortedByDescending { it.modified }.take(16), "new") { quick = it }
                    WidgetType.RA_PROFILE -> Unit
                    WidgetType.RA_RECENT -> Unit
                    WidgetType.RA_PLAYED -> Unit
                    WidgetType.ROMM_LATEST -> Unit
                    WidgetType.ALL_GAMES -> GameRowWidget("Recent", byRecent, "all") { quick = it }
                    WidgetType.RANDOM -> RandomWidget(games)
                    WidgetType.QUICK_APPS -> Unit
                    WidgetType.STATS -> StatsWidget(games)
                }
                }
            }
        }
    }
    quick?.let { g -> GameQuickSheet(g, "quick", onDismiss = { quick = null }) }
}

private fun List<Game>.shuffledStable(): List<Game> {
    val day = System.currentTimeMillis() / 86_400_000L
    return shuffled(java.util.Random(day))
}

@Composable
private fun EmptyLibraryCard() {
    val nav = LocalNav.current
    val scan by LocalGraph.current.library.scan.collectAsStateWithLifecycle()
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 20.dp) {
        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(if (scan.running) "Scanning your games…" else "Let's find your games", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            if (scan.running) scan.message
            else "Point Pillar at your ROMs folder. It should contain one folder per system, such as snes, gba or psx (the same layout ES-DE uses).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!scan.running) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = { nav.navigate(Routes.settingsPage(SettingsPage.LIBRARY)) }) { Text("Choose ROM folder") }
        }
    }
}

// ---- Header --------------------------------------------------------------------------------

@Composable
private fun ContinueWidget(games: List<Game>, isRecent: Boolean) {
    if (games.isEmpty()) return
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val launch = rememberGameLauncher()
    val pager = rememberPagerState { games.size }
    val corner = LocalCorner.current
    Column {
        SectionHeader(if (isRecent) "Jump back in" else "Picked for you")
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            key = { games[it].id },
        ) { page ->
            val game = games[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val color = systemColor(game.system)
            val hero = io.github.audxius.pillar.ui.components.rememberHeroSources(game)
            Box(
                Modifier
                    .graphicsLayer {
                        val s = 1f - 0.07f * offset
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.35f * offset
                    }
                    .fillMaxWidth()
                    .height(236.dp)
                    .shadow(18.dp, RoundedCornerShape(corner), ambientColor = color, spotColor = color)
                    .tappable(RoundedCornerShape(corner)) { nav.navigate(Routes.game(game.id, "hero")) }
            ) {
                MultiImage(hero, Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.7f)))))
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.88f))
                    )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    GameCover(
                        game,
                        Modifier.width(86.dp).shadow(12.dp, RoundedCornerShape(corner * 0.7f)),
                        sharedKey = "cover-hero-${game.id}",
                        compact = true,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SystemChip(game.system)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            gameTitle(game), style = MaterialTheme.typography.titleLarge, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val last = user.lastPlayed[game.id]
                        if (last != null) {
                            Text("Played ${timeAgo(last)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .tappable(CircleShape) { launch(game, null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        if (games.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(games.size) { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (active) 20.dp else 6.dp).clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }
}

// ---- Platforms -----------------------------------------------------------------------------

@Composable
fun rememberOrderedSystems(games: List<Game>): List<Pair<String, List<Game>>> {
    val settings = LocalSettings.current
    val graph = LocalGraph.current
    return remember(games, settings.systemOrder, settings.systemOverrides) {
        val grouped = games.groupBy { it.system }
        val order = settings.systemOrder
        grouped.entries.sortedWith(
            compareBy<Map.Entry<String, List<Game>>> { e -> order.indexOf(e.key).let { if (it < 0) Int.MAX_VALUE else it } }
                .thenBy { e -> (settings.systemOverrides[e.key]?.displayName ?: graph.catalog.meta(e.key).short).lowercase() }
        ).map { it.key to it.value }
    }
}

// ---- Game rows -----------------------------------------------------------------------------

@Composable
private fun GameRowWidget(title: String, games: List<Game>, src: String, onLong: (Game) -> Unit) {
    if (games.isEmpty()) return
    val nav = LocalNav.current
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games, key = { it.id }) { g ->
                GameTile(
                    g, Modifier.width(112.dp), sharedKey = "cover-$src-${g.id}", showSystem = true,
                    onLongClick = { onLong(g) },
                ) { nav.navigate(Routes.game(g.id, src)) }
            }
        }
    }
}

// ---- RetroAchievements ---------------------------------------------------------------------

@Composable
private fun RandomWidget(games: List<Game>) {
    if (games.isEmpty()) return
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Casino, null, tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(42.dp).graphicsLayer { rotationZ = spin.value },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Can't decide?", style = MaterialTheme.typography.titleMedium)
                Text("Roll the dice across ${games.size} games", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = {
                scope.launch {
                    spin.animateTo(spin.value + 720f, tween(650))
                    nav.navigate(Routes.game(games.random().id, "dice"))
                }
            }) { Text("Roll") }
        }
    }
}

@Composable
private fun StatsWidget(games: List<Game>) {
    val user = LocalUserData.current
    val systems = remember(games) { games.map { it.system }.toSet().size }
    val size = remember(games) { games.sumOf { it.size } }
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 18.dp) {
        Text("Your collection", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${games.size}", "games")
            Stat("$systems", "systems")
            Stat("${user.favorites.size}", "favorites")
            Stat(formatBytes(size), "on disk")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
