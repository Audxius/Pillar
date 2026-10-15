@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.audxius.pillar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.audxius.pillar.data.DownloadState
import io.github.audxius.pillar.data.Loadable
import io.github.audxius.pillar.data.RaAchievement
import io.github.audxius.pillar.data.RaGameProgress
import io.github.audxius.pillar.data.RaRecentGame
import io.github.audxius.pillar.data.raMedia
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.EmptyState
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.LinearMeter
import io.github.audxius.pillar.ui.components.ProgressRing
import io.github.audxius.pillar.ui.components.SectionHeader
import io.github.audxius.pillar.ui.components.Shimmer
import io.github.audxius.pillar.ui.components.formatBytes
import io.github.audxius.pillar.ui.components.successColor
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData

private val Gold = Color(0xFFFFC94D)

// ---- Downloads -----------------------------------------------------------------------------

@Composable
fun DownloadsSheet(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            if (downloads.isEmpty()) {
                Text("No downloads", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DownloadsPanel(downloads.values.toList())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DownloadsPanel(states: List<DownloadState>) {
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

// ---- Achievements --------------------------------------------------------------------------

@Composable
fun AchievementsScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
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
    val played by graph.ra.played.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.ra.refresh() }
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Trophies") {
                RoundIcon(Icons.Rounded.Refresh, "Refresh") { graph.ra.refresh(force = true) }
            }
        }
        item {
            when (val p = profile) {
                is Loadable.Ready -> Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            raMedia(p.value.userPic), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(CircleShape).border(2.5.dp, Gold, CircleShape),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.value.user, style = MaterialTheme.typography.titleLarge)
                            p.value.richPresence?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        BigStat("%,d".format(p.value.totalPoints), "Hardcore", Gold)
                        BigStat("%,d".format(p.value.totalSoftcorePoints), "Softcore", MaterialTheme.colorScheme.onSurface)
                        BigStat("%,d".format(p.value.totalTruePoints), "RetroPoints", MaterialTheme.colorScheme.primary)
                    }
                }
                is Loadable.Error -> Text(p.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
                else -> Shimmer(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp))
            }
        }

        item { SectionHeader("Your games") }
        when (val pl = played) {
            is Loadable.Ready -> {
                if (pl.value.isEmpty()) item {
                    Text("No games played with RetroAchievements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
                items(pl.value, key = { "g${it.gameId}" }) { g ->
                    RaGameCard(g, expanded = expanded == g.gameId) { expanded = if (expanded == g.gameId) null else g.gameId }
                }
            }
            is Loadable.Error -> item {
                Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 16.dp) {
                    Text("Couldn't load your games", style = MaterialTheme.typography.titleSmall)
                    Text(pl.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { graph.ra.refresh(force = true) }) { Text("Try again") }
                }
            }
            else -> items(3) { Shimmer(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(78.dp)) }
        }
    }
}

@Composable
private fun RaGameCard(g: RaRecentGame, expanded: Boolean, onToggle: () -> Unit) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val games by graph.allGames.collectAsStateWithLifecycle()
    val localGame = remember(games, user.raIds, g.gameId) { games.firstOrNull { user.raIds[it.id] == g.gameId } }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    // Loaded when first opened: fetching every card up front trips RetroAchievements' rate limit.
    var progress by remember { mutableStateOf<RaGameProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(g.gameId, expanded) {
        if (!expanded || progress != null) return@LaunchedEffect
        error = null
        runCatching { graph.ra.progress(g.gameId) }.onSuccess { progress = it }.onFailure { error = it.message }
    }
    Glass(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().tappable(RoundedCornerShape(20.dp), onClick = onToggle).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(raMedia(g.imageIcon), null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${g.consoleName} · ${raTimeAgo(g.lastPlayed)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (g.total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearMeter(g.progress, Modifier.weight(1f), color = Gold)
                        Spacer(Modifier.width(8.dp))
                        Text("${maxOf(g.numAchieved, g.numAchievedHardcore)}/${g.total}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ExpandMore, if (expanded) "Collapse" else "Expand", Modifier.rotate(chevron))
        }
        // Opens downward from the card header, so the top of the list shows first.
        AnimatedVisibility(
            expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                val p = progress
                when {
                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    p == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    else -> AchievementList(p)
                }
                if (localGame != null) {
                    TextButton(onClick = { nav.navigate(Routes.game(localGame.id, "ra")) }) { Text("Open in library") }
                }
            }
        }
    }
}

/** Summary plus Unlocked and Locked sections, used on Trophies and on game pages. */
@Composable
fun AchievementList(p: RaGameProgress) {
    if (p.achievements.isEmpty()) {
        Text("This game has no achievements yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val unlocked = p.achievements.filter { it.earned }.sortedByDescending { it.dateEarnedHardcore?.takeIf(String::isNotBlank) ?: it.dateEarned.orEmpty() }
    val locked = p.achievements.filter { !it.earned }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(p.progress, Modifier.size(64.dp), color = Gold, stroke = 6.dp) {
            Text("${(p.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("${p.earned} of ${p.achievements.size} unlocked", style = MaterialTheme.typography.titleSmall)
            Text("${p.points} / ${p.totalPoints} points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // What's left to do comes first, then what you've already earned.
    if (locked.isNotEmpty()) {
        ListLabel("To unlock · ${locked.size}")
        locked.forEach { AchievementRow(it) }
    }
    if (unlocked.isNotEmpty()) {
        ListLabel("Unlocked · ${unlocked.size}")
        unlocked.forEach { AchievementRow(it) }
    }
}

@Composable
private fun ListLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun AchievementRow(a: RaAchievement) {
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val hardcore = !a.dateEarnedHardcore.isNullOrBlank()
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            AsyncImage(
                if (a.earned) a.badgeUrl else a.lockedBadgeUrl, a.title,
                colorFilter = if (a.earned) null else gray,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                    .border(if (hardcore) 2.dp else 0.dp, if (hardcore) Gold else Color.Transparent, RoundedCornerShape(10.dp)),
            )
            if (!a.earned) {
                Icon(
                    Icons.Rounded.Lock, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (a.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val date = a.dateEarnedHardcore?.takeIf { it.isNotBlank() } ?: a.dateEarned?.takeIf { it.isNotBlank() }
            if (date != null) {
                Text((if (hardcore) "Hardcore · " else "") + raTimeAgo(date), style = MaterialTheme.typography.labelSmall, color = if (hardcore) Gold else MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${a.points}", style = MaterialTheme.typography.labelLarge, color = if (a.earned) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold.copy(alpha = if (a.earned) 0.16f else 0.06f)).padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun BigStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
