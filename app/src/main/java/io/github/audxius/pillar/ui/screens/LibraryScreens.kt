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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
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
import io.github.audxius.pillar.data.PlatformLayout
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- Library (platform list) ---------------------------------------------------------------

@Composable
fun LibraryScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val games = rememberVisibleGames()
    val systems = rememberOrderedSystems(games)
    val scan by graph.library.scan.collectAsStateWithLifecycle()
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = null }

    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val rommSyncing by graph.rommRepo.syncing.collectAsStateWithLifecycle()
    var showDownloads by remember { mutableStateOf(false) }
    if (showDownloads) DownloadsSheet { showDownloads = false }
    val header: @Composable () -> Unit = {
        val remoteCount = games.count { it.remote }
        ScreenHeader(
            "Library",
            listOfNotNull("${games.size} games", "${systems.size} systems", remoteCount.takeIf { it > 0 }?.let { "$it on RomM" }).joinToString(" · "),
        ) {
            if (downloads.isNotEmpty()) {
                RoundIcon(Icons.Rounded.Download, "Downloads") { showDownloads = true }
                Spacer(Modifier.width(8.dp))
            }
            RoundIcon(Icons.Rounded.Search, "Search") { nav.navigate(Routes.SEARCH) }
            Spacer(Modifier.width(8.dp))
            SpinningRefresh(scan.running || rommSyncing) {
                graph.library.scanAsync()
                graph.rommRepo.refresh(force = true)
            }
        }
        if (scan.running) {
            Text(
                scan.message, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
    }

    if (games.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            header()
            EmptyState(
                Icons.Rounded.FolderOpen,
                if (scan.running) "Scanning…" else "No games yet",
                if (scan.running) scan.message else "Add your ROMs folder in Settings. Pillar looks for system folders like snes, gba, psx inside it.",
                action = if (scan.running) null else "Choose folder",
                onAction = { nav.navigate(Routes.settingsPage(SettingsPage.LIBRARY)) },
            )
        }
        return
    }

    when (settings.platformLayout) {
        PlatformLayout.GRID -> LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { Column { header() } }
            items(systems, key = { it.first }) { (system, list) ->
                PlatformTile(system, list.size, list, Modifier.fillMaxWidth()) { nav.navigate(Routes.platform(system)) }
            }
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(if (settings.platformLayout == PlatformLayout.CARDS) 14.dp else 8.dp),
        ) {
            item { Column { header() } }
            items(systems, key = { it.first }) { (system, list) ->
                if (settings.platformLayout == PlatformLayout.CARDS) {
                    PlatformCard(system, list) { nav.navigate(Routes.platform(system)) }
                } else {
                    PlatformRow(system, list.size) { nav.navigate(Routes.platform(system)) }
                }
            }
        }
    }
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

@Composable
private fun PlatformCard(system: String, games: List<Game>, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val color = systemColor(system)
    val corner = LocalCorner.current
    val meta = graph.catalog.meta(system)
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(156.dp)
            .shadow(14.dp, RoundedCornerShape(corner), ambientColor = color, spotColor = color)
            .tappable(RoundedCornerShape(corner), onClick = onClick)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.06f), lerp(color, Color.Black, 0.6f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(corner))
    ) {
        val sample = remember(games) { games.shuffled(java.util.Random(system.hashCode().toLong())).take(3) }
        sample.withIndex().reversed().forEach { (i, g) ->
            GameCover(
                g,
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp + (i * 30).dp).width(86.dp - (i * 8).dp)
                    .graphicsLayer { rotationZ = 6f - i * 6f; alpha = 1f - i * 0.2f }
                    .shadow(10.dp, RoundedCornerShape(10.dp)),
                compact = true,
            )
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 150.dp)) {
            Text(systemName(system), style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                graph.catalog.fullName(system), style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(gamesLabel(games.size), meta.year?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PlatformRow(system: String, count: Int, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val color = systemColor(system)
    Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth().tappable(onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(systemName(system), style = MaterialTheme.typography.titleMedium)
                Text(graph.catalog.fullName(system), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- Platform (game grid) ------------------------------------------------------------------

private enum class GameFilter(val label: String) { ALL("All"), LOCAL("On device"), REMOTE("On RomM"), FAVORITES("Favorites"), PLAYED("Played"), UNPLAYED("Not played") }

@Composable
fun PlatformScreen(system: String) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val all = rememberVisibleGames()
    val ambient = LocalAmbient.current
    val color = systemColor(system)
    LaunchedEffect(system) { ambient.value = color }
    val platformGames = remember(all, system) { all.filter { it.system == system } }
    val showcase = remember(platformGames) { platformGames.randomOrNull() }
    showcase?.let { g ->
        val heroSrc = io.github.audxius.pillar.ui.components.rememberHeroSources(g)
        val ambientArt = io.github.audxius.pillar.ui.theme.LocalAmbientArt.current
        LaunchedEffect(g.id, heroSrc) { ambientArt.value = heroSrc }
    }

    var filter by rememberSaveable { mutableStateOf(GameFilter.ALL) }
    var sortOpen by remember { mutableStateOf(false) }
    var emuSheet by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf<Game?>(null) }

    val games = remember(all, system, filter, settings.sortMode, user) {
        val base = all.filter { it.system == system }.filter {
            when (filter) {
                GameFilter.ALL -> true
                GameFilter.LOCAL -> !it.remote
                GameFilter.REMOTE -> it.remote
                GameFilter.FAVORITES -> it.id in user.favorites
                GameFilter.PLAYED -> (user.lastPlayed[it.id] ?: 0) > 0
                GameFilter.UNPLAYED -> (user.lastPlayed[it.id] ?: 0) == 0L
            }
        }
        when (settings.sortMode) {
            SortMode.NAME -> base.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }
            SortMode.RECENTLY_PLAYED -> base.sortedByDescending { user.lastPlayed[it.id] ?: 0 }
            SortMode.RECENTLY_ADDED -> base.sortedByDescending { it.modified }
            SortMode.MOST_PLAYED -> base.sortedByDescending { user.playCount[it.id] ?: 0 }
        }
    }
    val total = remember(all, system) { all.count { it.system == system } }
    val currentEmu = remember(system, settings, user) {
        settings.defaultEmulators[system] ?: graph.launcher.automaticCommand(system, settings)?.takeIf { graph.launcher.isCommandAvailable(it) }?.label
    }

    val header: @Composable () -> Unit = {
        Column {
            ScreenHeader(systemName(system), listOfNotNull(graph.catalog.meta(system).maker, gamesLabel(total)).joinToString(" · "), onBack = { nav.popBackStack() }) {
                Box {
                    RoundIcon(Icons.AutoMirrored.Rounded.Sort, "Sort") { sortOpen = true }
                    DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label()) },
                                leadingIcon = { if (mode == settings.sortMode) Icon(Icons.Rounded.Check, null) },
                                onClick = {
                                    sortOpen = false
                                    scope.launch { graph.store.update { it.copy(sortMode = mode) } }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                RoundIcon(
                    if (settings.libraryLayout == LibraryLayout.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    "Layout",
                ) {
                    scope.launch {
                        graph.store.update { it.copy(libraryLayout = if (it.libraryLayout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID) }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.18f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .tappable(RoundedCornerShape(50)) { emuSheet = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SportsEsports, null, modifier = Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                Text(currentEmu ?: "Choose emulator", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GameFilter.entries.filter { f -> (f != GameFilter.LOCAL && f != GameFilter.REMOTE) || all.any { it.remote } }) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (settings.libraryLayout == LibraryLayout.GRID) {
        // Staggered so covers keep their real shapes without gaps.
        LazyVerticalStaggeredGrid(
            StaggeredGridCells.Fixed(settings.gridColumns.coerceIn(2, 6)),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 16.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { header() }
            staggeredItems(games, key = { it.id }) { g ->
                GameTile(g, sharedKey = "cover-p-${g.id}", onLongClick = { quick = g }) { nav.navigate(Routes.game(g.id, "p")) }
            }
            if (games.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                Text("Nothing here with this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { header() }
            items(games, key = { it.id }) { g -> GameListRow(g, "p", onLong = { quick = g }) }
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
                graph.store.update { s ->
                    s.copy(defaultEmulators = if (label == null) s.defaultEmulators - system else s.defaultEmulators + (system to label))
                }
            }
            emuSheet = false
        }
    }
    quick?.let { g -> GameQuickSheet(g, "p", onDismiss = { quick = null }) }
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

    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Fixed(settings.gridColumns.coerceIn(2, 6)),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
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
        staggeredItems(results, key = { it.id }) { g ->
            GameTile(g, sharedKey = "cover-s-${g.id}", showSystem = true) { nav.navigate(Routes.game(g.id, "s")) }
        }
        if (query.trim().length >= 2 && results.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text("No matches for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    }
}
