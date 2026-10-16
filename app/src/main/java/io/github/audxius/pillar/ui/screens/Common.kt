@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.audxius.pillar.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.SystemChip
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                // Sits straight on the backdrop, so it follows the main text color rather than a dim grey.
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f), maxLines = 1,
                )
            }
        }
        actions()
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color? = null, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).styledSurface(CircleShape)
            .tappable(CircleShape, silent = description == "Back", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

fun timeAgo(millis: Long): String = when {
    millis <= 0 -> "Never"
    System.currentTimeMillis() - millis < 60_000 -> "Just now"
    else -> DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

/** RetroAchievements timestamps are "yyyy-MM-dd HH:mm:ss" in UTC. */
fun raTimeAgo(date: String?): String {
    if (date.isNullOrBlank()) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val t = runCatching { fmt.parse(date)?.time }.getOrNull() ?: return date
    return timeAgo(t)
}

/** Long-press menu for a game: play, details, favorite, hide. */
@Composable
fun GameQuickSheet(game: Game, src: String, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val nav = LocalNav.current
    val launch = rememberGameLauncher()
    val scope = rememberCoroutineScope()
    val fav = game.id in user.favorites
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GameCover(game, Modifier.width(64.dp), compact = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(gameTitle(game), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                SystemChip(game.system)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetAction(Icons.Rounded.PlayArrow, "Play") { onDismiss(); launch(game, null) }
            SheetAction(Icons.Rounded.Info, "Details") { onDismiss(); nav.navigate(Routes.game(game.id, src)) }
            SheetAction(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (fav) "Remove from favorites" else "Add to favorites") {
                scope.launch { graph.store.updateUser { u -> u.copy(favorites = if (fav) u.favorites - game.id else u.favorites + game.id) } }
                onDismiss()
            }
            SheetAction(Icons.Rounded.VisibilityOff, "Hide game") {
                scope.launch { graph.store.updateUser { u -> u.copy(hidden = u.hidden + game.id) } }
                onDismiss()
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tappable(RoundedCornerShape(16.dp), onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
