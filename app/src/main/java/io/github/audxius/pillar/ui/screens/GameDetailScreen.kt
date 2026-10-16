@file:OptIn(ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.DownloadState
import io.github.audxius.pillar.data.LibraryRepository
import io.github.audxius.pillar.data.RaAchievement
import io.github.audxius.pillar.data.RaGameProgress
import io.github.audxius.pillar.data.RommRom
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.GameLogo
import io.github.audxius.pillar.ui.components.Gloss
import io.github.audxius.pillar.ui.components.PrimaryAction
import io.github.audxius.pillar.ui.components.rememberArt
import io.github.audxius.pillar.ui.components.rememberHeroSources
import io.github.audxius.pillar.ui.components.fadeBottom
import io.github.audxius.pillar.ui.components.MultiImage
import io.github.audxius.pillar.ui.components.Pill
import io.github.audxius.pillar.ui.components.ProgressRing
import io.github.audxius.pillar.ui.components.Shimmer
import io.github.audxius.pillar.ui.components.SystemChip
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.onColor
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(id: String, src: String, openEmulators: Boolean = false) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val all by graph.allGames.collectAsStateWithLifecycle()
    val user = LocalUserData.current
    // A RomM game that has since been downloaded shows its local copy.
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val game = remember(id, all, user.rommIds, downloads) {
        val g = graph.gameById(id)
        g?.rommId?.takeIf { g.remote }?.let { graph.localFor(it) } ?: g
    }
    if (game == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Game not found", onBack = { nav.popBackStack() })
            Text(
                "This game is no longer in your library. It may have been moved or the folder was removed.",
                modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    GameDetail(game, src, openEmulators)
}

@Composable
private fun GameDetail(game: Game, src: String, openEmulators: Boolean) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launch = rememberGameLauncher()
    val corner = LocalCorner.current
    val ambient = LocalAmbient.current
    val sysColor = systemColor(game.system)
    var accent by remember { mutableStateOf(sysColor) }
    LaunchedEffect(game.id) { ambient.value = sysColor }

    val art = rememberArt(game)
    val hero = rememberHeroSources(game)
    val logo = remember(game.id, art) { graph.library.logo(game, art) }
    val ambientArt = io.github.audxius.pillar.ui.theme.LocalAmbientArt.current
    LaunchedEffect(game.id, hero) { ambientArt.value = hero }
    val fav = game.id in user.favorites
    val command = remember(game.id, settings, user) { graph.launcher.selectCommand(game, settings, user) }

    var emuSheet by remember { mutableStateOf(openEmulators) }
    var renaming by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var resetRa by remember { mutableStateOf(false) }
    val links by graph.rommLinks.collectAsStateWithLifecycle()
    val rommId = game.rommId ?: links[game.id]
    val noAch by graph.ra.noAchievements.collectAsStateWithLifecycle()
    var confirmLaunch by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch { graph.store.updateUser { it.copy(customCover = it.customCover + (game.id to uri.toString())) } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Top bar: back, and everything secondary in one menu.
        Row(Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { nav.popBackStack() }
            Spacer(Modifier.weight(1f))
            Box {
                var menu by remember { mutableStateOf(false) }
                RoundIcon(Icons.Rounded.MoreVert, "More") { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (fav) "Remove from favorites" else "Add to favorites") },
                        leadingIcon = { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, null) },
                        onClick = {
                            menu = false
                            scope.launch { graph.store.updateUser { u -> u.copy(favorites = if (fav) u.favorites - game.id else u.favorites + game.id) } }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Change cover") }, leadingIcon = { Icon(Icons.Rounded.Image, null) },
                        onClick = { menu = false; pickCover.launch(arrayOf("image/*")) },
                    )
                    if (user.customCover[game.id] != null) {
                        DropdownMenuItem(
                            text = { Text("Use automatic cover") }, leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = { menu = false; scope.launch { graph.store.updateUser { it.copy(customCover = it.customCover - game.id) } } },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Hide game") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                        onClick = { menu = false; confirmHide = true },
                    )
                    if (settings.raConfigured && user.raIds[game.id] != null) {
                        DropdownMenuItem(
                            text = { Text("Reset achievements…") }, leadingIcon = { Icon(Icons.Rounded.EmojiEvents, null) },
                            onClick = { menu = false; resetRa = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete…") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameCover(
                    game,
                    Modifier.width(132.dp)
                        .shadow(16.dp, RoundedCornerShape(corner * 0.7f), ambientColor = sysColor, spotColor = sysColor),
                    sharedKey = "cover-$src-${game.id}",
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    // The logo or the title, never both.
                    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                        TitleOrLogo(game, maxWidth = 200.dp, maxHeight = 90.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SystemChip(game.system)
                        LibraryRepository.tags(game.fileName).take(3).forEach { Pill(it) }
                        if (settings.raConfigured && user.raIds[game.id] == null && game.id in noAch) Pill("No achievements")
                        if (settings.rommConfigured && !game.remote && rommId == null) Pill("Not on RomM")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))


            // Play, or download from RomM first.
            val downloads by graph.downloads.states.collectAsStateWithLifecycle()
            val dl = game.rommId?.let { downloads[it] }
            when {
                game.remote && (dl is DownloadState.Queued || dl is DownloadState.Running) -> PrimaryAction(
                    if (dl is DownloadState.Running) "Downloading ${(dl.fraction * 100).toInt()}%" else "Waiting to download",
                    Icons.Rounded.CloudDownload, Modifier.fillMaxWidth().height(58.dp),
                    progress = (dl as? DownloadState.Running)?.fraction ?: 0f,
                ) { graph.downloads.cancel(game.rommId!!) }
                game.remote -> PrimaryAction("Download & play", Icons.Rounded.CloudDownload, Modifier.fillMaxWidth().height(58.dp)) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    graph.downloadAndPlay(game)
                }
                // The arrow opens the emulator list; which one is picked only shows there.
                else -> PrimaryAction(
                    "Play", Icons.Rounded.PlayArrow, Modifier.fillMaxWidth().height(62.dp),
                    onMenu = { emuSheet = true },
                ) {
                    // No emulator known to work yet: choose one rather than launch blind.
                    if (command == null) emuSheet = true
                    else if (settings.confirmLaunch) confirmLaunch = true else launch(game, null)
                }
            }
            if (dl is DownloadState.Failed) {
                Text(dl.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (settings.rommConfigured && !game.remote && rommId == null) UploadToRomm(game)
            if (game.remote) {
                Text(
                    "On your RomM server · ${formatBytes(game.size)}. It downloads to your ${systemName(game.system)} folder and starts when it's ready.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Fact(user.lastPlayed[game.id]?.let { timeAgo(it) } ?: "Never", "Last played")
                    Fact("${user.playCount[game.id] ?: 0}", "Launches")
                    Fact(formatBytes(game.size), "Size")
                }
            }

            GameSaves(game, rommId)
            if (settings.raConfigured) AchievementsSection(game)

            Spacer(Modifier.height(16.dp))
            Text(
                if (game.remote) "RomM · ${game.fileName}" else game.path ?: game.fileName,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (resetRa) ResetRaProgressDialog { resetRa = false }
    if (deleting) DeleteGameDialog(game, rommId, onDone = { deleted -> deleting = false; if (deleted) nav.popBackStack() })
    if (emuSheet) {
        EmulatorSheet(
            system = game.system,
            selected = user.emulatorOverride[game.id],
            title = "Emulator for this game",
            onDismiss = { emuSheet = false },
        ) { label ->
            scope.launch {
                graph.store.updateUser { u ->
                    u.copy(emulatorOverride = if (label == null) u.emulatorOverride - game.id else u.emulatorOverride + (game.id to label))
                }
            }
            emuSheet = false
        }
    }
    if (renaming) {
        val initialTitle = gameTitle(game)
        var text by remember { mutableStateOf(initialTitle) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    scope.launch {
                        graph.store.updateUser { u ->
                            u.copy(customTitle = if (t.isEmpty() || t == game.title) u.customTitle - game.id else u.customTitle + (game.id to t))
                        }
                    }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmHide) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text("Hide this game?") },
            text = { Text("It won't show anywhere in Pillar. You can bring hidden games back in Settings → Library.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { graph.store.updateUser { it.copy(hidden = it.hidden + game.id) } }
                    confirmHide = false
                    nav.popBackStack()
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { confirmHide = false }) { Text("Cancel") } },
        )
    }
    if (confirmLaunch) {
        AlertDialog(
            onDismissRequest = { confirmLaunch = false },
            title = { Text("Launch ${gameTitle(game)}?") },
            text = { Text("Using ${command?.label ?: "the default emulator"}") },
            confirmButton = { TextButton(onClick = { confirmLaunch = false; launch(game, null) }) { Text("Play") } },
            dismissButton = { TextButton(onClick = { confirmLaunch = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Glass(modifier.tappable(onClick = onClick), padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (highlight) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AchievementsSection(game: Game) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val scope = rememberCoroutineScope()
    var raId by remember { mutableStateOf(user.raIds[game.id]) }
    var searched by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<RaGameProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RaAchievement?>(null) }

    LaunchedEffect(game.id, user.raIds[game.id]) {
        raId = graph.ra.idFor(game, graph.catalog)
        searched = true
    }
    LaunchedEffect(raId, refreshTick) {
        val id = raId ?: return@LaunchedEffect
        error = null
        progress = runCatching { graph.ra.progress(id, force = refreshTick > 0) }.onFailure { error = it.message }.getOrNull()
    }

    Spacer(Modifier.height(20.dp))
    Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFFFC94D))
            Spacer(Modifier.width(8.dp))
            Text("Achievements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (raId != null) {
                Icon(
                    Icons.Rounded.Refresh, "Refresh",
                    Modifier.size(36.dp).clip(CircleShape).tappable(CircleShape) { refreshTick++ }.padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val p = progress
        when {
            !searched -> Shimmer(Modifier.fillMaxWidth().height(60.dp))
            raId == null -> {
                Text(
                    "No RetroAchievements match for this title. If it has achievements, enter its game ID from retroachievements.org.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { manual = true }) { Text("Set game ID") }
            }
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            p == null -> Shimmer(Modifier.fillMaxWidth().height(60.dp))
            else -> {
                AchievementList(p)
            }
        }
    }

    selected?.let { a ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { AsyncImage(if (a.earned) a.badgeUrl else a.lockedBadgeUrl, null, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))) },
            title = { Text(a.title, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(a.description, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${a.points} points" + when {
                            !a.dateEarnedHardcore.isNullOrBlank() -> " · Hardcore ${raTimeAgo(a.dateEarnedHardcore)}"
                            !a.dateEarned.isNullOrBlank() -> " · Unlocked ${raTimeAgo(a.dateEarned)}"
                            else -> " · Locked"
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
    if (manual) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("RetroAchievements game ID") },
            text = {
                Column {
                    Text("Open the game on retroachievements.org; the number at the end of the address is its ID.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.filter(Char::isDigit) }, singleLine = true, placeholder = { Text("e.g. 1446") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { id -> scope.launch { graph.store.updateUser { it.copy(raIds = it.raIds + (game.id to id)) } } }
                    manual = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } },
        )
    }
}

/** This game's save files and states from the synced folders. */
/**
 * Saves on this device and on RomM in one card, folded to a one-line summary so achievements
 * aren't pushed off the page; tap to see and use them.
 */
@Composable
private fun GameSaves(game: Game, rommId: Int?) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val last by graph.saveSync.last.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf<List<io.github.audxius.pillar.data.LocalSaveFile>>(emptyList()) }
    var states by remember { mutableStateOf<Map<String, io.github.audxius.pillar.data.SyncFileState>>(emptyMap()) }
    var refresh by remember { mutableStateOf(0) }
    val remote = rememberRommSaves(if (settings.rommConfigured) rommId else null, refresh).orEmpty()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(game.id, last) {
        if (settings.savesDir == null && settings.statesDir == null) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val stem = game.baseName.lowercase()
            val mine = graph.saveSync.listLocal().filter {
                val n = it.name.lowercase().removeSuffix(".png")
                n.substringBeforeLast('.') == stem
            }
            files = mine.filter { !it.name.endsWith(".png", true) }.sortedByDescending { it.modified }
            states = graph.saveSync.fileStates(mine)
        }
    }
    if (files.isEmpty() && remote.isEmpty()) return
    val synced = files.isNotEmpty() && files.all { states[it.key] == io.github.audxius.pillar.data.SyncFileState.SYNCED }
    val summary = listOfNotNull(
        files.size.takeIf { it > 0 }?.let { "$it on this device" + if (synced) " · synced" else "" },
        remote.size.takeIf { it > 0 }?.let { n -> "$n on RomM" + remote.map { it.slot }.distinct().size.let { s -> if (s > 1) " in $s slots" else "" } },
    ).joinToString(" · ")
    val chevron by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "savesChevron")
    Spacer(Modifier.height(16.dp))
    Glass(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().tappable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saves", style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(chevron))
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                if (files.isNotEmpty()) {
                    files.forEach { f -> SaveFileRow(f, states[f.key], onDelete = { nav.navigate(io.github.audxius.pillar.ui.Routes.SAVES) }) }
                    TextButton(onClick = { nav.navigate(io.github.audxius.pillar.ui.Routes.SAVES) }) { Text("Manage saves") }
                }
                if (remote.isNotEmpty()) RommSaveList(game, remote) { refresh++ }
            }
        }
    }
}

/** Sends a local game to the matching platform on your RomM server. */
@Composable
private fun UploadToRomm(game: Game) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val snackbar = io.github.audxius.pillar.ui.LocalSnackbar.current
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            if (progress != null) return@OutlinedButton
            error = null
            progress = 0f
            scope.launch {
                runCatching { graph.uploadToRomm(game) { p -> progress = p } }
                    .onSuccess { snackbar.showSnackbar("Uploaded ${game.title} to RomM") }
                    .onFailure { error = it.message ?: "Upload failed" }
                progress = null
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(progress?.let { "Uploading ${(it * 100).toInt()}%" } ?: "Upload to RomM")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
}

/** Removes a game from this device, RomM, or both (handy for duplicates). */
@Composable
private fun DeleteGameDialog(game: Game, rommId: Int?, onDone: (deleted: Boolean) -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val snackbar = io.github.audxius.pillar.ui.LocalSnackbar.current
    var device by remember { mutableStateOf(!game.remote) }
    var server by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val title = gameTitle(game)
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        title = { Text("Delete $title?") },
        text = {
            Column {
                if (!game.remote) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().tappable { device = !device }) {
                        Checkbox(device, { device = it })
                        Column {
                            Text("From this device", style = MaterialTheme.typography.bodyLarge)
                            Text(game.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (rommId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().tappable { server = !server }) {
                        Checkbox(server, { server = it })
                        Column {
                            Text("From RomM", style = MaterialTheme.typography.bodyLarge)
                            Text("Also deletes the file on your server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "This can't be undone. Saves aren't touched.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (device || server),
                onClick = {
                    busy = true
                    // Leave the page right away (it would otherwise show "not found" as the game
                    // disappears), and let the delete finish and report back on its own.
                    onDone(true)
                    graph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val problem = graph.deleteGame(game, device, server)
                        snackbar.showSnackbar(problem ?: "Deleted $title")
                    }
                },
            ) { Text(if (busy) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { onDone(false) }) { Text("Cancel") } },
    )
}

