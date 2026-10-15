@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.audxius.pillar.data.DownloadState
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.Loadable
import io.github.audxius.pillar.data.RommPlatform
import io.github.audxius.pillar.data.RommRom
import io.github.audxius.pillar.data.raMedia
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.CoverPlaceholder
import io.github.audxius.pillar.ui.components.EmptyState
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.fadeBottom
import io.github.audxius.pillar.ui.components.gamesLabel
import io.github.audxius.pillar.ui.components.successColor
import io.github.audxius.pillar.ui.components.LinearMeter
import io.github.audxius.pillar.ui.components.MultiImage
import io.github.audxius.pillar.ui.components.Pill
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.Shimmer
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// ---- RomM home -----------------------------------------------------------------------------

@Composable
fun RommScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = null }

    if (!settings.rommConfigured) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("RomM")
            EmptyState(
                Icons.Rounded.Cloud, "Connect your RomM server",
                "Browse your self-hosted library, read game info and download straight into your ROM folders.",
                action = "Set up RomM", onAction = { nav.navigate(Routes.settingsPage(SettingsPage.ROMM)) },
            )
        }
        return
    }
    val platforms by graph.rommRepo.platforms.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.rommRepo.refresh() }

    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            ScreenHeader("RomM", graph.romm.host()) {
                RoundIcon(Icons.Rounded.Search, "Search") { nav.navigate(Routes.SEARCH) }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Rounded.Refresh, "Refresh") { graph.rommRepo.refresh(force = true) }
            }
        }
        if (downloads.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) { DownloadsPanel(downloads.values.toList()) }
        }
        when (val p = platforms) {
            is Loadable.Ready -> {
                if (p.value.isEmpty()) item(span = { GridItemSpan(2) }) {
                    Text("Your RomM server has no games yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(p.value, key = { it.id }) { plat -> RommPlatformTile(plat) { nav.navigate(Routes.rommPlatform(plat.id, plat.title)) } }
            }
            is Loadable.Error -> item(span = { GridItemSpan(2) }) {
                Glass(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Text("Couldn't reach RomM", style = MaterialTheme.typography.titleMedium)
                    Text(p.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(onClick = { graph.rommRepo.refresh(force = true) }) { Text("Retry") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { nav.navigate(Routes.settingsPage(SettingsPage.ROMM)) }) { Text("Settings") }
                    }
                }
            }
            else -> items(6) { Shimmer(Modifier.fillMaxWidth().height(110.dp)) }
        }
    }
}

@Composable
private fun RommPlatformTile(p: RommPlatform, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val corner = LocalCorner.current
    val system = remember(p.id) { graph.catalog.resolveRommPlatform(p.slug, p.fsSlug) }
    val color = if (system != null) systemColor(system) else MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(110.dp)
            .shadow(10.dp, RoundedCornerShape(corner), ambientColor = color, spotColor = color)
            .tappable(RoundedCornerShape(corner), onClick = onClick)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.05f), lerp(color, Color.Black, 0.6f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(corner))
            .padding(14.dp)
    ) {
        Text(p.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
            Text(gamesLabel(p.romCount), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            if (system == null) {
                Spacer(Modifier.width(6.dp))
                Text("· no local folder match", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun DownloadsPanel(states: List<DownloadState>) {
    val graph = LocalGraph.current
    Glass(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (states.any { it is DownloadState.Done || it is DownloadState.Failed }) {
                TextButton(onClick = { graph.downloads.clearFinished() }) { Text("Clear finished") }
            }
        }
        states.forEach { s ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.rom.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (s) {
                        is DownloadState.Queued -> Text("Waiting…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is DownloadState.Running -> {
                            Spacer(Modifier.height(4.dp))
                            LinearMeter(s.fraction, Modifier.fillMaxWidth())
                            Text("${formatBytes(s.bytes)} of ${formatBytes(s.total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is DownloadState.Done -> Text("Saved to ${s.system}", style = MaterialTheme.typography.labelSmall, color = successColor())
                        is DownloadState.Failed -> Text(s.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (s is DownloadState.Queued || s is DownloadState.Running) {
                    Icon(Icons.Rounded.Close, "Cancel", Modifier.size(36.dp).clip(CircleShape).tappable(CircleShape) { graph.downloads.cancel(s.rom.id) }.padding(6.dp))
                }
            }
        }
    }
}

// ---- RomM platform -------------------------------------------------------------------------

@Composable
fun RommPlatformScreen(platformId: Int, name: String) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val items = remember { mutableStateListOf<RommRom>() }
    var total by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    val grid = rememberLazyGridState()

    suspend fun loadMore() {
        if (loading || (total >= 0 && items.size >= total)) return
        loading = true
        runCatching { graph.romm.roms(platformId, appliedQuery.ifBlank { null }, offset = items.size, limit = 60) }
            .onSuccess { page ->
                items.addAll(page.items.filter { n -> items.none { it.id == n.id } })
                total = if (page.items.isEmpty()) items.size else page.total
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(appliedQuery) {
        items.clear(); total = -1
        loadMore()
    }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= items.size - 12 }
            .collect { loadMore() }
    }

    LazyVerticalGrid(
        GridCells.Fixed(settings.gridColumns.coerceIn(2, 6)),
        Modifier.fillMaxSize(),
        state = grid,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenHeader(name, if (total >= 0) gamesLabel(total) else null, onBack = { nav.popBackStack() })
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query != appliedQuery) TextButton(onClick = { appliedQuery = query.trim() }) { Text("Go") }
                    },
                    shape = RoundedCornerShape(50),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { appliedQuery = query.trim() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                )
            }
        }
        items(items, key = { it.id }) { rom -> RommTile(rom) { nav.navigate(Routes.rommRom(rom.id)) } }
        if (loading) items(6) { Shimmer(Modifier.fillMaxWidth().height(160.dp)) }
        error?.let { msg ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

// ---- RomM game -----------------------------------------------------------------------------

@Composable
fun RommRomScreen(romId: Int) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val corner = LocalCorner.current
    val launch = rememberGameLauncher()
    val games by graph.library.games.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val user = LocalUserData.current
    var rom by remember { mutableStateOf<RommRom?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ambient = LocalAmbient.current

    LaunchedEffect(romId) {
        runCatching { graph.romm.rom(romId) }.onSuccess { rom = it }.onFailure { error = it.message }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val r = rom
    if (r == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("RomM", onBack = { nav.popBackStack() })
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            else Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val system = remember(r.id) { graph.downloads.targetSystem(r) }
    val color = if (system != null) systemColor(system) else MaterialTheme.colorScheme.primary
    LaunchedEffect(color) { ambient.value = color }
    val local: Game? = remember(games, user.rommIds, r.id) {
        games.firstOrNull { user.rommIds[it.id] == r.id }
            ?: games.firstOrNull { it.system == system && (it.fileName == r.fsName || it.id.contains("/${r.fsName}/")) }
    }
    val state = downloads[r.id]
    val cover = remember(r.id) { listOfNotNull(graph.romm.coverUrl(r)) }
    val hero = remember(r.id) { r.screenshots.mapNotNull { graph.romm.resourceUrl(it) } + cover }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(380.dp)) {
                MultiImage(hero, Modifier.fillMaxWidth().height(280.dp).fadeBottom()) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.7f)))))
                }
                Box(
                    Modifier.fillMaxWidth().height(120.dp).background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent)
                    )
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).width(170.dp).height(226.dp)
                        .shadow(24.dp, RoundedCornerShape(corner * 0.7f), ambientColor = color, spotColor = color)
                        .clip(RoundedCornerShape(corner * 0.7f))
                ) {
                    MultiImage(cover, Modifier.matchParentSize(), contentScale = ContentScale.Crop) {
                        CoverPlaceholder(r.title, system ?: r.platformSlug.orEmpty())
                    }
                }
            }
            Column(Modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Text(r.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(r.platformDisplayName ?: r.platformSlug.orEmpty())
                    r.releaseYear?.let { Pill(it.toString()) }
                    Pill(formatBytes(r.fsSizeBytes))
                }
                Spacer(Modifier.height(20.dp))
                when {
                    local != null -> Button(
                        onClick = { launch(local, null) },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play", style = MaterialTheme.typography.titleMedium)
                    }
                    state is DownloadState.Running || state is DownloadState.Queued -> Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (state is DownloadState.Running) "Downloading…" else "Queued", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                LinearMeter((state as? DownloadState.Running)?.fraction ?: 0f, Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = { graph.downloads.cancel(r.id) }) { Text("Cancel") }
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                graph.downloads.enqueue(r)
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(8.dp))
                            Text(if (state is DownloadState.Failed) "Retry download" else "Download", style = MaterialTheme.typography.titleMedium)
                        }
                        if (state is DownloadState.Failed) {
                            Spacer(Modifier.height(6.dp))
                            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (local == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (system != null) "Saves to your ${system} folder" else "No matching system folder; it will be saved to a \"${r.platformFsSlug ?: r.platformSlug}\" folder",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = successColor(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { nav.navigate(Routes.game(local.id, "romm")) }) { Text("On this device · open details") }
                    }
                }
                Spacer(Modifier.height(20.dp))
                val facts = listOfNotNull(
                    r.metadatum?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Genres" to it },
                    r.metadatum?.companies?.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", ")?.let { "Companies" to it },
                    r.metadatum?.playerCount?.takeIf { it.isNotBlank() }?.let { "Players" to it },
                    r.regions.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Regions" to it },
                    r.fsName.let { "File" to it },
                )
                if (!r.summary.isNullOrBlank() || facts.isNotEmpty()) {
                    Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
                        r.summary?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                        }
                        facts.forEach { (k, v) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(92.dp))
                                Text(v, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
        Row(Modifier.statusBarsPadding().padding(12.dp)) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { nav.popBackStack() }
        }
    }
}

// ---- RetroAchievements ---------------------------------------------------------------------

@Composable
fun AchievementsScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val ambient = LocalAmbient.current
    LaunchedEffect(Unit) { ambient.value = Color(0xFFFFB020) }

    if (!settings.raConfigured) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Trophies")
            EmptyState(
                Icons.Rounded.EmojiEvents, "Connect RetroAchievements",
                "Add your username and web API key to see your profile, recent unlocks and per-game progress.",
                action = "Set up", onAction = { nav.navigate(Routes.settingsPage(SettingsPage.RA)) },
            )
        }
        return
    }
    val profile by graph.ra.profile.collectAsStateWithLifecycle()
    val recent by graph.ra.recent.collectAsStateWithLifecycle()
    val played by graph.ra.played.collectAsStateWithLifecycle()
    val games by graph.library.games.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.ra.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader("Trophies") {
                RoundIcon(Icons.Rounded.Refresh, "Refresh") { graph.ra.refresh(force = true) }
            }
        }
        item {
            when (val p = profile) {
                is Loadable.Ready -> Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = 20.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            raMedia(p.value.userPic), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(CircleShape).border(3.dp, Color(0xFFFFC94D), CircleShape),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(p.value.user, style = MaterialTheme.typography.headlineSmall)
                        p.value.motto?.takeIf { it.isNotBlank() }?.let {
                            Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            BigStat("%,d".format(p.value.totalPoints), "Hardcore", Color(0xFFFFC94D))
                            BigStat("%,d".format(p.value.totalSoftcorePoints), "Softcore", MaterialTheme.colorScheme.onSurface)
                            BigStat("%,d".format(p.value.totalTruePoints), "RetroPoints", MaterialTheme.colorScheme.primary)
                        }
                        p.value.richPresence?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(14.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is Loadable.Error -> Text(p.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
                else -> Shimmer(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(220.dp))
            }
        }
        (played as? Loadable.Ready)?.value?.takeIf { it.isNotEmpty() }?.let { list ->
            item { SectionHeader("Recently played") }
            items(list, key = { "g${it.gameId}" }) { g ->
                val localGame = remember(games, user.raIds, g.gameId) { games.firstOrNull { user.raIds[it.id] == g.gameId } }
                Glass(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                        .then(if (localGame != null) Modifier.tappable { nav.navigate(Routes.game(localGame.id, "ra")) } else Modifier),
                    padding = 12.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(raMedia(g.imageIcon), null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${g.consoleName} · ${raTimeAgo(g.lastPlayed)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                            if (g.total > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearMeter(g.progress, Modifier.fillMaxWidth(), color = Color(0xFFFFC94D))
                            }
                        }
                        if (g.total > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("${maxOf(g.numAchieved, g.numAchievedHardcore)}/${g.total}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        (recent as? Loadable.Ready)?.value?.let { list ->
            item { SectionHeader("Unlocked in the last 2 weeks") }
            if (list.isEmpty()) item {
                Text("Nothing yet. Go earn some!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(list, key = { "a${it.id}-${it.date}" }) { a ->
                Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        raMedia(a.badgeUrl), null,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                            .border(2.dp, if (a.hardcore == 1) Color(0xFFFFC94D) else Color.Transparent, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${a.gameTitle} · ${raTimeAgo(a.date)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${a.points}", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFC94D))
                }
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
