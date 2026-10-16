package io.github.audxius.pillar.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import io.github.audxius.pillar.data.SyncConflict
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.data.RommSave
import io.github.audxius.pillar.data.LibraryRepository
import io.github.audxius.pillar.data.ThumbMatch
import io.github.audxius.pillar.data.LocalSaveFile
import io.github.audxius.pillar.data.SyncFileState
import io.github.audxius.pillar.data.SyncStatus
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.LocalSnackbar
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.EmptyState
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.successColor
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds the game a save belongs to. RetroArch names saves after the ROM file, so the file name
 * is tried first, then a looser title match; saves for games that aren't on this device get a
 * stand-in game (system from the emulator core's folder) so they still show a cover and a clean name.
 */
private class SaveGameFinder(games: List<Game>) {
    private val byStem = games.groupBy { it.baseName.lowercase() }
    private val byKey = HashMap<String, Game>().apply {
        games.forEach { g ->
            putIfAbsent(ThumbMatch.key(g.fileName), g)
            putIfAbsent(ThumbMatch.key("${g.title}.x"), g)
            g.datName?.let { putIfAbsent(ThumbMatch.key("$it.x"), g) }
        }
    }

    fun find(f: LocalSaveFile): Game {
        val stem = saveStem(f.name)
        byStem[stem.lowercase()]?.firstOrNull()?.let { return it }
        val key = ThumbMatch.key("$stem.x")
        if (key.isNotEmpty()) byKey[key]?.let { return it }
        // Keys look like "saves/<core folder>/<name>.srm".
        val core = f.key.split('/').let { if (it.size >= 3) it[it.size - 2] else "" }
        return Game(
            id = "save:${core.lowercase()}/${stem.lowercase()}",
            system = coreSystem(core) ?: "unknown",
            title = LibraryRepository.cleanTitle("$stem.x"),
            fileName = "$stem.x",
            docUri = "",
            treeUri = "",
        )
    }
}

/** The system a RetroArch core's save folder most likely belongs to. */
private fun coreSystem(core: String): String? {
    val c = core.lowercase()
    return when {
        "melonds" in c || "desmume" in c || "noods" in c -> "nds"
        "snes" in c || "bsnes" in c || "mesen-s" in c -> "snes"
        "mgba" in c || "gpsp" in c || "vba" in c || "meteor" in c -> "gba"
        "gambatte" in c || "sameboy" in c || "gearboy" in c || "tgbdual" in c -> "gb"
        "mupen" in c || "parallel n64" in c -> "n64"
        "genesis plus" in c || "picodrive" in c || "blastem" in c -> "genesis"
        "gearsystem" in c || "smsplus" in c -> "mastersystem"
        "pcsx" in c || "swanstation" in c || "duckstation" in c || "beetle psx" in c -> "psx"
        "ppsspp" in c -> "psp"
        "mesen" in c || "fceumm" in c || "nestopia" in c || "quicknes" in c -> "nes"
        "pce" in c -> "pcengine"
        "flycast" in c || "redream" in c -> "dreamcast"
        "saturn" in c || "yabause" in c || "kronos" in c -> "saturn"
        "dolphin" in c -> "gc"
        "citra" in c || "azahar" in c -> "n3ds"
        "stella" in c -> "atari2600"
        "handy" in c || "lynx" in c -> "atarilynx"
        "wonderswan" in c || "cygne" in c -> "wonderswan"
        "neopop" in c || "race" in c -> "ngpc"
        "pokemini" in c -> "pokemini"
        "beetle vb" in c -> "virtualboy"
        "fbneo" in c || "finalburn" in c || "mame" in c -> "arcade"
        else -> null
    }
}

/** RetroArch save and state extensions, used to tell saves and states apart in the list. */
private fun isState(name: String) = Regex("\\.state(\\d+|\\.auto)?$|\\.state\\d*\\.png$", RegexOption.IGNORE_CASE).containsMatchIn(name)

/** "Game (USA).srm" / "Game (USA).state3" -> "Game (USA)" so files can be matched to library games. */
private fun saveStem(name: String): String =
    name.replace(Regex("\\.png$", RegexOption.IGNORE_CASE), "").substringBeforeLast('.')

@Composable
fun SavesScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val status by graph.saveSync.status.collectAsStateWithLifecycle()
    val last by graph.saveSync.last.collectAsStateWithLifecycle()
    val games by graph.library.games.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf<List<LocalSaveFile>?>(null) }
    var states by remember { mutableStateOf<Map<String, SyncFileState>>(emptyMap()) }
    var refresh by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<LocalSaveFile?>(null) }
    var showBackups by remember { mutableStateOf(false) }
    val hasFolders = settings.savesDir != null || settings.statesDir != null

    LaunchedEffect(refresh, last, settings.savesDir, settings.statesDir) {
        if (!hasFolders) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val list = graph.saveSync.listLocal()
            files = list
            states = graph.saveSync.fileStates(list)
        }
    }

    val finder = remember(games) { SaveGameFinder(games) }
    val grouped = remember(files) {
        files.orEmpty().groupBy { saveStem(it.name).lowercase() }.entries
            .sortedByDescending { e -> e.value.maxOf { it.modified } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomBarSpace), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Saves", if (hasFolders) "${files?.size ?: 0} files" else null) {
                if (hasFolders) RoundIcon(Icons.Rounded.History, "Backups") { showBackups = true }
            }
        }
        if (!hasFolders) {
            item {
                EmptyState(
                    Icons.Rounded.Save, "Manage and sync your saves",
                    "Pick your RetroArch saves and states folders. Pillar lists every save by game, keeps backups, and can sync with the same WebDAV server RetroArch Cloud Sync uses, every time you open Pillar.",
                    action = "Set up", onAction = { nav.navigate(Routes.settingsPage(SettingsPage.SAVES)) },
                )
            }
            return@LazyColumn
        }

        item {
            Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val running = status as? SyncStatus.Running
                        Text(
                            when {
                                settings.webdavUrl.isBlank() -> "Sync is off"
                                running != null -> running.message
                                last?.error != null -> "Last sync failed"
                                last != null -> "Synced ${timeAgo(last!!.finishedAt).lowercase()}"
                                else -> "Not synced yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val l = last
                        Text(
                            when {
                                settings.webdavUrl.isBlank() -> "Add your WebDAV server in Settings to sync with RetroArch Cloud Sync."
                                l?.error != null -> l.error
                                l != null -> listOfNotNull(
                                    "${l.uploaded} up · ${l.downloaded} down",
                                    l.skipped.size.takeIf { it > 0 }?.let { "$it skipped" },
                                ).joinToString(" · ")
                                else -> "Syncs automatically whenever you open Pillar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (l?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (status is SyncStatus.Running) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    } else if (settings.webdavUrl.isNotBlank()) {
                        Button(onClick = { scope.launch { graph.saveSync.sync(); refresh++ } }) {
                            Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sync")
                        }
                    } else {
                        TextButton(onClick = { nav.navigate(Routes.settingsPage(SettingsPage.SAVES)) }) { Text("Set up") }
                    }
                }
            }
        }

        val conflicts = last?.conflicts.orEmpty()
        if (conflicts.isNotEmpty()) {
            item { SectionHeader("Needs your choice · ${conflicts.size}") }
            items(conflicts, key = { "c" + it.key }) { c ->
                ConflictCard(
                    c,
                    onKeep = { device ->
                        scope.launch {
                            graph.saveSync.sync(mapOf(c.key to device)); refresh++
                            snackbar.showSnackbar(if (device) "Kept this device's save" else "Kept the server's save")
                        }
                    },
                )
            }
        }

        val skipped = last?.skipped.orEmpty()
        if (skipped.isNotEmpty()) {
            item { SectionHeader("Skipped · ${skipped.size}") }
            item {
                Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 14.dp) {
                    Text(
                        "The server didn't accept these files. With RomM this usually means the save's name doesn't match a game in your RomM library.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    skipped.take(20).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        if (files == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (grouped.isEmpty()) {
            item { Text("No save files in the selected folders yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp)) }
        } else {
            item { SectionHeader("By game") }
            items(grouped, key = { it.key }) { (stem, list) ->
                SaveGroup(finder.find(list.first()), list, states, onDelete = { deleting = it })
            }
        }
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${f.name}?") },
            text = { Text("A backup is kept in Pillar. Sync never deletes anything from your server, so the server copy stays.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { graph.saveSync.deleteLocal(f); refresh++ }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (showBackups) BackupsDialog(onDismiss = { showBackups = false; refresh++ })
}

/** Changed on both sides: shows how old each copy is and lets you keep one. */
@Composable
private fun ConflictCard(c: SyncConflict, onKeep: (device: Boolean) -> Unit) {
    Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Warn, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(saveLabel(c.key.substringAfterLast('/')).first, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Text(
            saveStem(c.key.substringAfterLast('/')),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        // Mark the newer copy when both times are known.
        val newer = when {
            c.localModified <= 0 || c.serverModified <= 0 -> null
            c.localModified > c.serverModified -> true
            c.serverModified > c.localModified -> false
            else -> null
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CopyChoice(
                "This device", Icons.Rounded.PhoneAndroid, c.localHash.isEmpty(), c.localModified, c.localSize, newer == true,
                Modifier.weight(1f),
            ) { onKeep(true) }
            CopyChoice(
                "Server", Icons.Rounded.Cloud, c.serverHash.isEmpty(), c.serverModified, c.serverSize, newer == false,
                Modifier.weight(1f),
            ) { onKeep(false) }
        }
        Text(
            "The copy you don't keep is saved as a backup.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CopyChoice(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    deleted: Boolean,
    modified: Long,
    size: Long,
    newer: Boolean,
    modifier: Modifier,
    onKeep: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .border(1.dp, if (newer) successColor() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (newer) {
                Text(
                    "Newer", style = MaterialTheme.typography.labelSmall, color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(successColor()).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                deleted -> "Deleted"
                modified > 0 -> "Saved ${timeAgo(modified).lowercase()}"
                else -> "Age unknown"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        if (!deleted) {
            Text(
                listOfNotNull(
                    modified.takeIf { it > 0 }?.let { DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH) },
                    size.takeIf { it >= 0 }?.let { formatBytes(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(if (deleted) "Keep deleted" else "Keep this", maxLines = 1)
        }
    }
}

/** One game's saves: collapsed to a summary, expands to its files. */
@Composable
private fun SaveGroup(game: Game, files: List<LocalSaveFile>, states: Map<String, SyncFileState>, onDelete: (LocalSaveFile) -> Unit) {
    val nav = LocalNav.current
    var open by rememberSaveable(files.first().key) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val shown = files.filter { !it.name.endsWith(".png", true) }.sortedByDescending { it.modified }
    val fileStates = shown.map { states[it.key] }
    val (statusText, statusColor) = when {
        SyncFileState.CONFLICT in fileStates -> "Conflict" to Warn
        SyncFileState.LOCAL_CHANGED in fileStates -> "${fileStates.count { it == SyncFileState.LOCAL_CHANGED }} not synced" to MaterialTheme.colorScheme.primary
        fileStates.isNotEmpty() && fileStates.all { it == SyncFileState.SYNCED } -> "Synced" to successColor()
        SyncFileState.LOCAL_ONLY in fileStates -> "Only on this device" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().tappable { open = !open }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameCover(game, Modifier.width(52.dp), ratio = 0.75f, compact = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(gameTitle(game), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val saves = shown.count { !isState(it.name) }
                val st = shown.count { isState(it.name) }
                Text(
                    listOfNotNull(
                        if (saves > 0) "$saves save${if (saves > 1) "s" else ""}" else null,
                        if (st > 0) "$st state${if (st > 1) "s" else ""}" else null,
                        timeAgo(files.maxOf { it.modified }),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
                    }
                }
            }
            Icon(Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
                shown.forEach { f -> SaveFileRow(f, states[f.key], onDelete) }
                if (!game.id.startsWith("save:")) {
                    TextButton(onClick = { nav.navigate(Routes.game(game.id, "saves")) }) { Text("Open game") }
                }
            }
        }
    }
}

/** "Game.srm" -> ("Save", ...), "Game.state3" -> ("State · slot 3", ...). */
private fun saveLabel(name: String): Pair<String, Boolean> {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        name.lowercase().endsWith(".state.auto") -> "Auto state" to true
        ext == "state" -> "State · slot 0" to true
        ext.startsWith("state") && ext.drop(5).all(Char::isDigit) -> "State · slot ${ext.drop(5)}" to true
        ext == "srm" || ext == "sav" -> "Save" to false
        else -> "${ext.uppercase()} file" to false
    }
}

@Composable
fun SaveFileRow(f: LocalSaveFile, state: SyncFileState?, onDelete: (LocalSaveFile) -> Unit) {
    val (label, color) = when (state) {
        SyncFileState.SYNCED -> "Synced" to successColor()
        SyncFileState.LOCAL_CHANGED -> "Not synced yet" to MaterialTheme.colorScheme.primary
        SyncFileState.CONFLICT -> "Conflict" to Warn
        SyncFileState.LOCAL_ONLY -> "Only on this device" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Keys look like "saves/Snes9x/Game.srm": the middle part is the emulator core's folder.
    val parts = f.key.split('/')
    val core = parts.drop(1).dropLast(1).joinToString("/").ifBlank { null }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val (title, isState) = saveLabel(f.name)
        Icon(
            if (isState) Icons.Rounded.PhotoCamera else Icons.Rounded.Save, null,
            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.14f)).padding(7.dp),
            tint = color,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                listOfNotNull(core, formatBytes(f.size), timeAgo(f.modified), label.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Rounded.Delete, "Delete",
            Modifier.size(36.dp).clip(CircleShape).tappable(CircleShape) { onDelete(f) }.padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val Warn = Color(0xFFFFB020)

@Composable
private fun BackupsDialog(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(Unit) { backups = withContext(Dispatchers.IO) { graph.saveSync.backups() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backups") },
        text = {
            if (backups.isEmpty()) Text("Pillar backs up a save before sync replaces or deletes it. None yet.")
            else LazyColumn(Modifier.height(380.dp)) {
                items(backups, key = { it.path }) { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name.substringBeforeLast('-').substringBeforeLast('-'), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatBytes(b.length())} · ${timeAgo(b.lastModified())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val ok = runCatching { graph.saveSync.restoreBackup(b) }.isSuccess
                                snackbar.showSnackbar(if (ok) "Restored. It will sync as a local change." else "Couldn't restore that backup")
                            }
                        }) { Text("Restore") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * The game's saves on RomM, by slot. RetroArch sync always uses the newest save in the
 * "autosave" slot, so that one is the main save; any other can be played from or made main.
 */
/** A game's saves on RomM (null while loading or when the server can't list them). */
@Composable
fun rememberRommSaves(rommId: Int?, refresh: Int): List<RommSave>? {
    val graph = LocalGraph.current
    var saves by remember(rommId) { mutableStateOf<List<RommSave>?>(null) }
    LaunchedEffect(rommId, refresh) {
        if (rommId != null) saves = runCatching { graph.romm.saves(rommId) }.getOrNull()
    }
    return saves
}

/** RomM's saves for a game, by slot, to play from or make the main save. */
@Composable
fun RommSaveList(game: Game, list: List<RommSave>, onChanged: () -> Unit) {
    val graph = LocalGraph.current
    val snackbar = LocalSnackbar.current
    val launch = io.github.audxius.pillar.ui.rememberGameLauncher()
    var choosing by remember { mutableStateOf<RommSave?>(null) }
    // Newest first in each slot; the main save is the head of "autosave" (or of the unslotted ones).
    val bySlot = list.sortedByDescending { it.updatedMillis }.groupBy { it.slot }
    val mainId = (bySlot["autosave"] ?: bySlot[null])?.firstOrNull()?.id
    Column {
        Text("ON ROMM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        bySlot.entries.sortedBy { (slot, _) -> if (slot == "autosave") "" else slot ?: "~" }.forEach { (slot, versions) ->
            var expanded by remember(slot) { mutableStateOf(false) }
            Text(
                when (slot) { "autosave" -> "Main slot"; null -> "No slot"; else -> slot },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            (if (expanded) versions else versions.take(1)).forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.labels.firstOrNull() ?: s.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(s.emulator, formatBytes(s.size), s.updatedMillis.takeIf { it > 0 }?.let { timeAgo(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    if (s.id == mainId) {
                        Text(
                            "Main", style = MaterialTheme.typography.labelSmall, color = Color.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(successColor()).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    } else {
                        TextButton(onClick = { choosing = s }) { Text("Use…") }
                    }
                }
            }
            if (versions.size > 1) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "${versions.size - 1} older version${if (versions.size > 2) "s" else ""}") }
            }
        }
    }
    choosing?.let { s ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Use this save?") },
            text = {
                Text(
                    "It replaces the save on this device; the current one is backed up first (Saves → Backups). " +
                        "Once it syncs, it's your main save on RomM too, and older ones stay in their slots."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    choosing = null
                    graph.scope.launch(Dispatchers.Main) {
                        runCatching { graph.useRommSave(game, s) }
                            .onSuccess { launch(game, null) }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Couldn't use that save") }
                    }
                }) { Text("Play from it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosing = null
                    graph.scope.launch(Dispatchers.Main) {
                        runCatching { graph.useRommSave(game, s); graph.saveSync.sync() }
                            .onSuccess { snackbar.showSnackbar("That save is now your main save"); onChanged() }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Couldn't use that save") }
                    }
                }) { Text("Make it main") }
            },
        )
    }
}
