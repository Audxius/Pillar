package io.github.audxius.pillar.ui.screens

import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.Loadable
import io.github.audxius.pillar.data.RommRom
import io.github.audxius.pillar.data.WidgetType
import io.github.audxius.pillar.data.raMedia
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.AppIcon
import io.github.audxius.pillar.ui.components.CoverPlaceholder
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.ratio
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameTile
import io.github.audxius.pillar.ui.components.GapIfNotEmpty
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.gamesLabel
import io.github.audxius.pillar.ui.components.LinearMeter
import io.github.audxius.pillar.ui.components.MultiImage
import io.github.audxius.pillar.ui.components.Shimmer
import io.github.audxius.pillar.ui.components.SystemChip
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/** Games the user hasn't hidden, on systems they haven't hidden. */
@Composable
fun rememberVisibleGames(): List<Game> {
    val graph = LocalGraph.current
    val games by graph.library.games.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val user = LocalUserData.current
    return remember(games, settings.hiddenSystems, user.hidden) {
        games.filter { it.system !in settings.hiddenSystems && it.id !in user.hidden }
    }
}

@Composable
fun HomeScreen() {
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val games = rememberVisibleGames()
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = null }
    var quick by remember { mutableStateOf<Game?>(null) }

    val recent = remember(games, user.lastPlayed) {
        games.filter { (user.lastPlayed[it.id] ?: 0) > 0 }.sortedByDescending { user.lastPlayed[it.id] }.take(10)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
    ) {
        val enabled = settings.homeWidgets.filter { it.enabled }.map { it.type }
        if (WidgetType.HEADER !in enabled) {
            item { Spacer(Modifier.statusBarsPadding()) }
            if (games.isEmpty()) item("empty") { GapIfNotEmpty(12.dp) { EmptyLibraryCard() } }
        }
        for ((index, type) in enabled.withIndex()) {
            if (index > 0 && enabled[index - 1] == WidgetType.HEADER && games.isEmpty()) {
                item("empty") { GapIfNotEmpty(22.dp) { EmptyLibraryCard() } }
            }
            item(type.name) {
                GapIfNotEmpty(if (index == 0) 0.dp else 22.dp) {
                when (type) {
                    WidgetType.HEADER -> HeaderWidget()
                    WidgetType.CONTINUE -> ContinueWidget(recent.ifEmpty { games.shuffledStable().take(6) }, isRecent = recent.isNotEmpty())
                    WidgetType.PLATFORMS -> PlatformsWidget(games)
                    WidgetType.FAVORITES -> GameRowWidget("Favorites", games.filter { it.id in user.favorites }, "fav") { quick = it }
                    WidgetType.RECENTLY_ADDED -> GameRowWidget("Recently added", games.sortedByDescending { it.modified }.take(16), "new") { quick = it }
                    WidgetType.RA_PROFILE -> RaProfileWidget()
                    WidgetType.RA_RECENT -> RaRecentWidget()
                    WidgetType.RA_PLAYED -> RaPlayedWidget()
                    WidgetType.ROMM_LATEST -> RommLatestWidget()
                    WidgetType.RANDOM -> RandomWidget(games)
                    WidgetType.QUICK_APPS -> QuickAppsWidget()
                    WidgetType.STATS -> StatsWidget(games)
                }
                }
            }
        }
        if (enabled.lastOrNull() == WidgetType.HEADER && games.isEmpty()) {
            item("empty") { GapIfNotEmpty(22.dp) { EmptyLibraryCard() } }
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
private fun HeaderWidget() {
    val settings = LocalSettings.current
    val nav = LocalNav.current
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
    val time = SimpleDateFormat(if (settings.clock24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val ampm = if (settings.clock24h) "" else SimpleDateFormat("a", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Late night session"
    }
    Column(Modifier.statusBarsPadding().padding(start = 22.dp, end = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, style = MaterialTheme.typography.displayLarge)
                    if (ampm.isNotEmpty()) {
                        Text(ampm, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                    }
                }
                Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                RoundIcon(Icons.Rounded.Apps, "Apps") { nav.navigate(Routes.APPS) }
                RoundIcon(Icons.Rounded.Search, "Search") { nav.navigate(Routes.SEARCH) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = settings.greetingName.ifBlank { settings.raUser }
            Text(
                if (name.isBlank()) greeting else "$greeting, $name",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (settings.showBattery && battery >= 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null,
                        modifier = Modifier.size(16.dp).rotate(90f),
                        tint = if (battery <= 15 && !charging) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$battery%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ---- Continue playing ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
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
            val hero = remember(game.id, user.customCover[game.id], user.remoteCover[game.id]) {
                graph.library.heroCandidates(game, settings, user)
            }
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
private fun PlatformsWidget(games: List<Game>) {
    val nav = LocalNav.current
    val ordered = rememberOrderedSystems(games)
    if (ordered.isEmpty()) return
    Column {
        SectionHeader("Platforms", action = "See all") { nav.navigate(Routes.LIBRARY) }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ordered, key = { it.first }) { (system, list) ->
                PlatformTile(system, list.size, list) { nav.navigate(Routes.platform(system)) }
            }
        }
    }
}

@Composable
fun PlatformTile(system: String, count: Int, games: List<Game>, modifier: Modifier = Modifier.width(138.dp), onClick: () -> Unit) {
    val color = systemColor(system)
    val corner = LocalCorner.current
    val graph = LocalGraph.current
    val maker = graph.catalog.meta(system).maker
    Box(
        modifier.height(168.dp)
            .shadow(10.dp, RoundedCornerShape(corner), ambientColor = color, spotColor = color)
            .tappable(RoundedCornerShape(corner), onClick = onClick)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.08f), lerp(color, Color.Black, 0.55f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(corner))
    ) {
        // A fanned stack of three covers peeking from the corner.
        val sample = remember(games) { games.take(3) }
        sample.withIndex().reversed().forEach { (i, g) ->
            GameCover(
                g,
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp + (i * 6).dp, end = 10.dp + (i * 16).dp).width(54.dp)
                    .graphicsLayer { rotationZ = 8f - i * 7f; alpha = 1f - i * 0.18f }
                    .shadow(6.dp, RoundedCornerShape(8.dp)),
                compact = true,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(systemName(system), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(maker, gamesLabel(count)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1,
            )
        }
    }
}

/** Systems with games, in the user's custom order (unlisted systems follow alphabetically). */
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
private fun ConnectCard(title: String, body: String, page: SettingsPage) {
    val nav = LocalNav.current
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth().tappable { nav.navigate(Routes.settingsPage(page)) }, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (page == SettingsPage.RA) Icons.Rounded.EmojiEvents else Icons.Rounded.CloudOff, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RaProfileWidget() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    if (!settings.raConfigured) {
        ConnectCard("Connect RetroAchievements", "See your points, progress and latest unlocks here.", SettingsPage.RA)
        return
    }
    val profile by graph.ra.profile.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.ra.refresh() }
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth().tappable { nav.navigate(Routes.RA) }, padding = 16.dp) {
        when (val p = profile) {
            is Loadable.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    raMedia(p.value.userPic), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(58.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.value.user, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${"%,d".format(p.value.totalPoints)} pts", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFC94D))
                        if (p.value.totalSoftcorePoints > 0) {
                            Text("${"%,d".format(p.value.totalSoftcorePoints)} softcore", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    p.value.richPresence?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(28.dp))
            }
            is Loadable.Error -> Text(p.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Shimmer(Modifier.size(58.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Shimmer(Modifier.width(120.dp).height(16.dp)); Spacer(Modifier.height(8.dp)); Shimmer(Modifier.width(180.dp).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun RaRecentWidget() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    if (!settings.raConfigured) return
    val recent by graph.ra.recent.collectAsStateWithLifecycle()
    val list = (recent as? Loadable.Ready)?.value ?: return
    if (list.isEmpty()) return
    Column {
        SectionHeader("Recent unlocks", action = "All") { nav.navigate(Routes.RA) }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list.take(15), key = { "${it.id}-${it.date}" }) { a ->
                Glass(Modifier.width(150.dp), padding = 12.dp) {
                    AsyncImage(
                        raMedia(a.badgeUrl), a.title,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                            .border(2.dp, if (a.hardcore == 1) Color(0xFFFFC94D) else Color.Transparent, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(a.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(a.gameTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${a.points} pts · ${raTimeAgo(a.date)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RaPlayedWidget() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    if (!settings.raConfigured) return
    val played by graph.ra.played.collectAsStateWithLifecycle()
    val list = (played as? Loadable.Ready)?.value?.filter { it.total > 0 }?.take(4) ?: return
    if (list.isEmpty()) return
    Column {
        SectionHeader("Achievement progress")
        Spacer(Modifier.height(8.dp))
        Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 14.dp) {
            list.forEachIndexed { i, g ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(raMedia(g.imageIcon), null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        LinearMeter(g.progress, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${maxOf(g.numAchieved, g.numAchievedHardcore)}/${g.total}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ---- RomM ----------------------------------------------------------------------------------

@Composable
private fun RommLatestWidget() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    if (!settings.rommConfigured) {
        ConnectCard("Connect your RomM server", "Browse and download games from your own library.", SettingsPage.ROMM)
        return
    }
    val latest by graph.rommRepo.latest.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.rommRepo.refresh() }
    val list = (latest as? Loadable.Ready)?.value
    Column {
        SectionHeader("New on RomM", action = "Browse") { nav.navigate(Routes.ROMM) }
        Spacer(Modifier.height(8.dp))
        when {
            list != null -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { rom -> RommTile(rom, Modifier.width(112.dp)) { nav.navigate(Routes.rommRom(rom.id)) } }
            }
            latest is Loadable.Error -> Text(
                (latest as Loadable.Error).message, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp),
            )
            else -> Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { Shimmer(Modifier.width(112.dp).height(150.dp)) }
            }
        }
    }
}

@Composable
fun RommTile(rom: RommRom, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val corner = LocalCorner.current
    val system = remember(rom.id) { graph.downloads.targetSystem(rom) }
    val cover = remember(rom.id, settings.rommUrl) { listOfNotNull(graph.romm.coverUrl(rom)) }
    Column(modifier.tappable(RoundedCornerShape(corner * 0.7f), onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatioCover().clip(RoundedCornerShape(corner * 0.7f))
        ) {
            MultiImage(cover, Modifier.matchParentSize()) {
                CoverPlaceholder(rom.title, system ?: rom.platformSlug.orEmpty(), compact = true)
            }
        }
        if (settings.showGameTitles) {
            Spacer(Modifier.height(6.dp))
            Text(rom.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                rom.platformDisplayName ?: rom.platformSlug.orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
fun Modifier.aspectRatioCover(): Modifier = this.aspectRatio(LocalSettings.current.coverShape.ratio())

// ---- Misc widgets --------------------------------------------------------------------------

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
private fun QuickAppsWidget() {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    Column {
        SectionHeader("Quick apps", action = "Edit") { nav.navigate(Routes.APPS) }
        Spacer(Modifier.height(8.dp))
        if (user.pinnedApps.isEmpty()) {
            TextButton(onClick = { nav.navigate(Routes.APPS) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Long-press any app in the Apps drawer to pin it here")
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(user.pinnedApps, key = { it }) { pkg ->
                    Column(
                        Modifier.width(72.dp).tappable(RoundedCornerShape(16.dp)) { graph.launcher.launchApp(pkg) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(pkg, Modifier.size(54.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(graph.launcher.appLabel(pkg), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
