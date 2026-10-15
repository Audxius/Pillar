@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.audxius.pillar.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.LibraryLayout
import io.github.audxius.pillar.data.LibraryRepository
import io.github.audxius.pillar.data.RommRom
import io.github.audxius.pillar.data.SortMode
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.LocalSnackbar
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.EmptyState
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameTile
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.gamesLabel
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- Library --------------------------------------------------------------------------------

private enum class GameFilter(val label: String) { ALL("All"), LOCAL("On device"), REMOTE("On RomM"), FAVORITES("Favorites"), PLAYED("Played"), UNPLAYED("Not played") }

/** Library tab: every console you have games for. Pick one to browse its games. */
@Composable
fun LibraryScreen() {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val all = rememberVisibleGames()
    val systems = rememberOrderedSystems(all)
    val scan by graph.library.scan.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val rommSyncing by graph.rommRepo.syncing.collectAsStateWithLifecycle()
    var showDownloads by remember { mutableStateOf(false) }
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = null }
    if (showDownloads) DownloadsSheet { showDownloads = false }

    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader(
                    "Library",
                    if (all.isEmpty()) null else "${systems.size} systems · ${gamesLabel(all.size)}",
                ) {
                    RoundIcon(Icons.Rounded.Search, "Search") { nav.navigate(Routes.SEARCH) }
                    Spacer(Modifier.width(8.dp))
                    if (downloads.isNotEmpty()) {
                        RoundIcon(Icons.Rounded.Download, "Downloads") { showDownloads = true }
                        Spacer(Modifier.width(8.dp))
                    }
                    SpinningRefresh(scan.running || rommSyncing) {
                        graph.library.scanAsync()
                        graph.rommRepo.refresh(force = true)
                    }
                }
                if (scan.running) {
                    Text(scan.message, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        items(systems, key = { it.first }) { (sys, list) ->
            PlatformCard(sys, list) { nav.navigate(Routes.platform(sys)) }
        }
        if (all.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(
                Icons.Rounded.FolderOpen,
                if (scan.running) "Scanning…" else "No games yet",
                if (scan.running) scan.message else "Add your ROMs folder in Settings, or connect RomM to see your server's games here.",
                action = if (scan.running) null else "Choose folder",
                onAction = { nav.navigate(Routes.settingsPage(SettingsPage.LIBRARY)) },
            )
        }
    }
}

/** A console in the library: its name, maker and year, game count and a fan of its covers. */
@Composable
private fun PlatformCard(system: String, games: List<Game>, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val meta = graph.catalog.meta(system)
    val color = systemColor(system)
    val corner = LocalCorner.current
    val shape = RoundedCornerShape(corner * 0.8f)
    // The games you played last stand in for the console; otherwise the first few alphabetically.
    val picks = remember(games, user.lastPlayed) {
        games.sortedWith(compareByDescending<Game> { user.lastPlayed[it.id] ?: 0L }.thenBy { it.title.lowercase() }).take(3)
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(0.92f)
            .styledSurface(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0.04f))))
            .tappable(shape, onClick = onClick),
    ) {
        val coverW = maxWidth * 0.42f
        // Most recent in front, the next two fanned out behind it, bleeding off the bottom edge.
        listOf(2 to 1f, 1 to -1f, 0 to 0f).forEach { (i, side) ->
            val g = picks.getOrNull(i) ?: return@forEach
            GameCover(
                g,
                Modifier.align(Alignment.BottomCenter).width(coverW)
                    .graphicsLayer {
                        translationX = side * coverW.toPx() * 0.62f
                        translationY = coverW.toPx() * (if (i == 0) 0.12f else 0.26f)
                        rotationZ = side * 9f
                        val s = if (i == 0) 1f else 0.9f
                        scaleX = s; scaleY = s
                    },
                ratio = 0.75f,
                compact = true,
            )
        }
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    systemName(system), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${games.size}",
                    style = MaterialTheme.typography.labelLarge, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
            val sub = listOfNotNull(meta.maker, meta.year?.toString()).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** One console's games: search, sort, filters, its emulator and a uniform cover grid. */
@Composable
fun PlatformScreen(system: String) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val all = rememberVisibleGames()
    val ambient = LocalAmbient.current
    val sysColor = systemColor(system)
    LaunchedEffect(system) { ambient.value = sysColor }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(GameFilter.ALL) }
    var sortOpen by remember { mutableStateOf(false) }
    var emuSheet by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf<Game?>(null) }

    val mine = remember(all, system) { all.filter { it.system == system } }
    val games = remember(mine, filter, query, settings.sortMode, user) {
        val q = query.trim().lowercase()
        val base = mine.filter { g ->
            (q.isEmpty() || (user.customTitle[g.id] ?: g.title).lowercase().contains(q) || g.fileName.lowercase().contains(q)) &&
                when (filter) {
                    GameFilter.ALL -> true
                    GameFilter.LOCAL -> !g.remote
                    GameFilter.REMOTE -> g.remote
                    GameFilter.FAVORITES -> g.id in user.favorites
                    GameFilter.PLAYED -> (user.lastPlayed[g.id] ?: 0) > 0
                    GameFilter.UNPLAYED -> (user.lastPlayed[g.id] ?: 0) == 0L
                }
        }
        when (settings.sortMode) {
            SortMode.NAME -> base.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }
            SortMode.RECENTLY_PLAYED -> base.sortedByDescending { user.lastPlayed[it.id] ?: 0L }
            SortMode.RECENTLY_ADDED -> base.sortedByDescending { it.modified }
            SortMode.MOST_PLAYED -> base.sortedByDescending { user.playCount[it.id] ?: 0 }
        }
    }

    LazyVerticalGrid(
        GridCells.Fixed(settings.gridColumns.coerceIn(2, 6)),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader(
                    graph.catalog.fullName(system),
                    listOfNotNull(
                        gamesLabel(mine.size),
                        mine.count { it.remote }.takeIf { it > 0 }?.let { "$it on RomM" },
                    ).joinToString(" · "),
                    onBack = { nav.popBackStack() },
                )
                // Search filters as you type.
                Row(
                    Modifier.padding(top = 4.dp).fillMaxWidth().height(48.dp).styledSurface(RoundedCornerShape(50)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search ${systemName(system)} games", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Close, "Clear", Modifier.size(32.dp).clip(CircleShape).tappable(CircleShape) { query = "" }.padding(6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Sort, the emulator for this console, and filters.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item(key = "sort") {
                        Box {
                            StyledChip("Sort: ${settings.sortMode.label()}", false) { sortOpen = true }
                            DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label()) },
                                        leadingIcon = { if (mode == settings.sortMode) Icon(Icons.Rounded.Check, null) },
                                        onClick = { sortOpen = false; scope.launch { graph.store.update { it.copy(sortMode = mode) } } },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "emu") {
                        val emu = remember(system, settings, user) {
                            settings.defaultEmulators[system]
                                ?: graph.launcher.automaticCommand(system, settings)?.takeIf { graph.launcher.isCommandAvailable(it) }?.label
                        }
                        StyledChip("Emulator: ${emu ?: "choose"}", false, sysColor) { emuSheet = true }
                    }
                    val hasRemote = mine.any { it.remote }
                    items(GameFilter.entries.filter { it != GameFilter.ALL && (hasRemote || (it != GameFilter.LOCAL && it != GameFilter.REMOTE)) }, key = { it.name }) { f ->
                        StyledChip(f.label, filter == f) { filter = if (filter == f) GameFilter.ALL else f }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(games, key = { it.id }) { g ->
            GameTile(g, sharedKey = "cover-lib-${g.id}", showSystem = false, onLongClick = { quick = g }) {
                nav.navigate(Routes.game(g.id, "lib"))
            }
        }
        if (games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Nothing matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }

    if (emuSheet) {
        EmulatorSheet(
            system = system,
            selected = settings.defaultEmulators[system],
            title = "Default emulator for ${systemName(system)}",
            onDismiss = { emuSheet = false },
        ) { label ->
            scope.launch {
                graph.store.update { s -> s.copy(defaultEmulators = if (label == null) s.defaultEmulators - system else s.defaultEmulators + (system to label)) }
            }
            emuSheet = false
        }
    }
    quick?.let { g -> GameQuickSheet(g, "lib", onDismiss = { quick = null }) }
}

@Composable
private fun SpinningRefresh(spinning: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    RoundIcon(
        Icons.Rounded.Refresh, "Rescan",
        modifier = Modifier.graphicsLayer { rotationZ = if (spinning) angle else 0f },
        onClick = { if (!spinning) onClick() },
    )
}

private fun SortMode.label() = when (this) {
    SortMode.NAME -> "Name"
    SortMode.RECENTLY_PLAYED -> "Recently played"
    SortMode.RECENTLY_ADDED -> "Recently added"
    SortMode.MOST_PLAYED -> "Most played"
}

@Composable
fun GameListRow(game: Game, src: String, showSystem: Boolean = false, onLong: (() -> Unit)? = null) {
    val nav = LocalNav.current
    val user = LocalUserData.current
    Glass(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().tappable(onLongClick = onLong) { nav.navigate(Routes.game(game.id, src)) },
        padding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameCover(game, Modifier.width(52.dp), sharedKey = "cover-$src-${game.id}", compact = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(gameTitle(game), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tags = LibraryRepository.tags(game.fileName).take(3).joinToString(" · ")
                val sub = listOfNotNull(
                    if (showSystem) systemName(game.system) else null,
                    tags.ifBlank { null },
                    user.lastPlayed[game.id]?.let { "Played ${timeAgo(it)}" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Lists every launch command for a system, installed emulators first. */
@Composable
fun EmulatorSheet(
    system: String,
    selected: String?,
    title: String,
    onDismiss: () -> Unit,
    allowReset: Boolean = true,
    onPick: (String?) -> Unit,
) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val commands = remember(system, settings.customCommands) {
        graph.launcher.commandsFor(system, settings).map { it to graph.launcher.isCommandAvailable(it) }
            .sortedByDescending { it.second }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "Installed emulators are listed first. Commands come from ES-DE's configuration, and you can add your own in Settings → Emulators.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(12.dp)) {
            if (allowReset) item {
                EmuRow("Automatic", "First installed emulator", selected == null, available = true) { onPick(null) }
            }
            items(commands, key = { it.first.label + it.first.custom }) { (cmd, available) ->
                val emuName = cmd.emulatorId?.let { id -> graph.launcher.resolveEmulator(id)?.label }
                val sub = when {
                    cmd.custom -> "Custom command"
                    available -> "via ${emuName ?: "installed app"}"
                    else -> "Not installed"
                }
                EmuRow(cmd.label, sub, cmd.label == selected, available) { onPick(cmd.label) }
            }
            if (commands.isEmpty()) item {
                Text("No launch commands are known for this system.", modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun EmuRow(label: String, sub: String, selected: Boolean, available: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tappable(RoundedCornerShape(16.dp), onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (available) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---- Search --------------------------------------------------------------------------------

@Composable
fun SearchScreen() {
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val games = rememberVisibleGames()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Searches everything: games on this device and the RomM library.
    val results = remember(query, games, user.customTitle) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else games.filter { (user.customTitle[it.id] ?: it.title).lowercase().contains(q) || it.fileName.lowercase().contains(q) }.take(200)
    }

    LazyVerticalGrid(
        GridCells.Fixed(settings.gridColumns.coerceIn(2, 6)),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader("Search", onBack = { nav.popBackStack() })
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search all your games") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", Modifier.clip(CircleShape).tappable(CircleShape) { query = "" })
                    },
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
                )
                if (results.isNotEmpty()) SectionHeader("${results.size} found", Modifier.padding(top = 10.dp))
            }
        }
        items(results, key = { it.id }) { g ->
            GameTile(g, sharedKey = "cover-s-${g.id}", showSystem = true) { nav.navigate(Routes.game(g.id, "s")) }
        }
        if (query.trim().length >= 2 && results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No matches for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    }
}
