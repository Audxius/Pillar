@file:OptIn(ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import io.github.audxius.pillar.data.LibraryRepository
import io.github.audxius.pillar.data.RaAchievement
import io.github.audxius.pillar.data.RaGameProgress
import io.github.audxius.pillar.data.RommRom
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.Glass
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
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(id: String, src: String) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val game = remember(id) { graph.library.gameById(id) }
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
    GameDetail(game, src)
}

@Composable
private fun GameDetail(game: Game, src: String) {
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

    var emuSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    var confirmLaunch by remember { mutableStateOf(false) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch { graph.store.updateUser { it.copy(customCover = it.customCover + (game.id to uri.toString())) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Hero art with the cover floating over its bottom edge.
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                MultiImage(hero, Modifier.fillMaxWidth().height(300.dp).fadeBottom(), onColor = { c -> accent = c; if (settings.ambientColor) ambient.value = c }) {
                    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(sysColor, lerp(sysColor, Color.Black, 0.7f)))))
                }
                Box(
                    Modifier.fillMaxWidth().height(120.dp).background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent)
                    )
                )
                GameCover(
                    game,
                    Modifier.align(Alignment.BottomCenter).width(178.dp)
                        .shadow(28.dp, RoundedCornerShape(corner * 0.7f), ambientColor = accent, spotColor = accent)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(corner * 0.7f)),
                    sharedKey = "cover-$src-${game.id}",
                )
            }

            Column(Modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                if (logo != null) {
                    AsyncImage(logo, gameTitle(game), Modifier.fillMaxWidth(0.75f).height(70.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(6.dp))
                    Text(gameTitle(game), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(gameTitle(game), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemChip(game.system)
                    LibraryRepository.tags(game.fileName).take(4).forEach { Pill(it) }
                }
                Spacer(Modifier.height(20.dp))

                // Play
                Row(
                    Modifier.fillMaxWidth().height(62.dp)
                        .shadow(16.dp, RoundedCornerShape(50), ambientColor = accent, spotColor = accent)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, 0.55f))))
                        .tappable(RoundedCornerShape(50)) { if (settings.confirmLaunch) confirmLaunch = true else launch(game, null) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val on = MaterialTheme.colorScheme.primary.onColor()
                    Icon(Icons.Rounded.PlayArrow, null, tint = on, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play", style = MaterialTheme.typography.titleLarge, color = on)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).tappable(RoundedCornerShape(50)) { emuSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val custom = user.emulatorOverride[game.id] != null
                    val installed = remember(command) { command != null && graph.launcher.isCommandAvailable(command) }
                    Text(
                        (command?.label ?: "No emulator available") +
                            (if (custom) " · this game only" else "") +
                            (if (command != null && !installed) " · not installed" else ""),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (installed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Quick facts
                Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Fact(user.lastPlayed[game.id]?.let { timeAgo(it) } ?: "Never", "Last played")
                        Fact("${user.playCount[game.id] ?: 0}", "Launches")
                        Fact(formatBytes(game.size), "Size")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (fav) "Favorited" else "Favorite", Modifier.weight(1f), highlight = fav) {
                        scope.launch { graph.store.updateUser { u -> u.copy(favorites = if (fav) u.favorites - game.id else u.favorites + game.id) } }
                    }
                    ActionButton(Icons.Rounded.Image, "Cover", Modifier.weight(1f)) { pickCover.launch(arrayOf("image/*")) }
                    ActionButton(Icons.Rounded.Edit, "Rename", Modifier.weight(1f)) { renaming = true }
                    ActionButton(Icons.Rounded.VisibilityOff, "Hide", Modifier.weight(1f)) { confirmHide = true }
                }
                if (user.customCover[game.id] != null) {
                    TextButton(onClick = { scope.launch { graph.store.updateUser { it.copy(customCover = it.customCover - game.id) } } }) {
                        Text("Reset to automatic cover")
                    }
                }

                GameSaves(game)
                user.rommIds[game.id]?.let { rommId -> RommInfo(rommId) }
                if (settings.raConfigured) AchievementsSection(game)

                Spacer(Modifier.height(16.dp))
                Text(
                    game.path ?: game.fileName,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // Floating top bar
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { nav.popBackStack() }
        }
    }

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
private fun RommInfo(rommId: Int) {
    val graph = LocalGraph.current
    var rom by remember { mutableStateOf<RommRom?>(null) }
    LaunchedEffect(rommId) { rom = runCatching { graph.romm.rom(rommId) }.getOrNull() }
    val r = rom ?: return
    Spacer(Modifier.height(20.dp))
    Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        val facts = listOfNotNull(
            r.releaseYear?.toString(),
            r.metadatum?.genres?.take(3)?.joinToString(", ")?.ifBlank { null },
            r.metadatum?.companies?.firstOrNull(),
            r.metadatum?.averageRating?.let { "★ ${"%.0f".format(it)}" },
        )
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        r.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            Text(
                it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.tappable { expanded = !expanded },
            )
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
            p.achievements.isEmpty() -> Text("This game has no achievements yet.", style = MaterialTheme.typography.bodySmall)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(p.progress, Modifier.size(76.dp), color = Color(0xFFFFC94D), stroke = 7.dp) {
                        Text("${(p.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${p.earned} of ${p.achievements.size} unlocked", style = MaterialTheme.typography.titleSmall)
                        Text("${p.points} / ${p.totalPoints} points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(p.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 6) {
                    p.achievements.sortedByDescending { it.earned }.forEach { a ->
                        AsyncImage(
                            if (a.earned) a.badgeUrl else a.lockedBadgeUrl, a.title,
                            colorFilter = if (a.earned) null else gray,
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .border(if (a.earned) 2.dp else 0.dp, if (a.dateEarnedHardcore.isNullOrBlank()) Color.Transparent else Color(0xFFFFC94D), RoundedCornerShape(10.dp))
                                .tappable(RoundedCornerShape(10.dp)) { selected = a },
                        )
                    }
                    // Pad the last row so badges keep a consistent size.
                    repeat((6 - p.achievements.size % 6) % 6) { Spacer(Modifier.weight(1f)) }
                }
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
@Composable
private fun GameSaves(game: Game) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    if (settings.savesDir == null && settings.statesDir == null) return
    val last by graph.saveSync.last.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf<List<io.github.audxius.pillar.data.LocalSaveFile>>(emptyList()) }
    var states by remember { mutableStateOf<Map<String, io.github.audxius.pillar.data.SyncFileState>>(emptyMap()) }
    LaunchedEffect(game.id, last) {
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
    if (files.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Glass(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saves", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { nav.navigate(io.github.audxius.pillar.ui.Routes.SAVES) }) { Text("Manage") }
        }
        files.forEach { f -> SaveFileRow(f, states[f.key], onDelete = { nav.navigate(io.github.audxius.pillar.ui.Routes.SAVES) }) }
    }
}
