package io.github.audxius.pillar.ui.screens

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

    val gamesByStem = remember(games) { games.groupBy { it.baseName.lowercase() } }
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
                Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFFB020))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.key.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Changed on this device and on the server since the last sync.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch { graph.saveSync.sync(mapOf(c.key to true)); refresh++; snackbar.showSnackbar("Kept this device's save") }
                        }) { Text("Keep this device") }
                        OutlinedButton(onClick = {
                            scope.launch { graph.saveSync.sync(mapOf(c.key to false)); refresh++; snackbar.showSnackbar("Kept the server's save") }
                        }) { Text("Keep server") }
                    }
                    Text(
                        "The other copy is kept as a backup.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
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
                SaveGroup(gamesByStem[stem]?.firstOrNull(), list, states, onDelete = { deleting = it })
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

@Composable
private fun SaveGroup(game: Game?, files: List<LocalSaveFile>, states: Map<String, SyncFileState>, onDelete: (LocalSaveFile) -> Unit) {
    val nav = LocalNav.current
    Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 12.dp) {
        Row(
            Modifier.fillMaxWidth().then(if (game != null) Modifier.tappable(RoundedCornerShape(12.dp)) { nav.navigate(Routes.game(game.id, "saves")) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (game != null) {
                GameCover(game, Modifier.width(44.dp), compact = true)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(game?.let { gameTitle(it) } ?: saveStem(files.first().name), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val saves = files.count { !isState(it.name) }
                val st = files.count { isState(it.name) && !it.name.endsWith(".png", true) }
                Text(
                    listOfNotNull(
                        if (saves > 0) "$saves save${if (saves > 1) "s" else ""}" else null,
                        if (st > 0) "$st state${if (st > 1) "s" else ""}" else null,
                        "updated ${timeAgo(files.maxOf { it.modified }).lowercase()}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        files.filter { !it.name.endsWith(".png", true) }.sortedByDescending { it.modified }.forEach { f ->
            SaveFileRow(f, states[f.key], onDelete)
        }
    }
}

@Composable
fun SaveFileRow(f: LocalSaveFile, state: SyncFileState?, onDelete: (LocalSaveFile) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val (label, color) = when (state) {
            SyncFileState.SYNCED -> "Synced" to successColor()
            SyncFileState.LOCAL_CHANGED -> "Not synced yet" to MaterialTheme.colorScheme.primary
            SyncFileState.CONFLICT -> "Conflict" to Color(0xFFFFB020)
            SyncFileState.LOCAL_ONLY -> "Only on this device" to MaterialTheme.colorScheme.onSurfaceVariant
            else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.key.substringAfter('/'), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(formatBytes(f.size), timeAgo(f.modified), label).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.Delete, "Delete",
            Modifier.size(36.dp).clip(CircleShape).tappable(CircleShape) { onDelete(f) }.padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
