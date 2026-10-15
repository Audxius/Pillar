package io.github.audxius.pillar.ui.screens

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.audxius.pillar.R
import io.github.audxius.pillar.data.Saf
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.settings.rememberFolderPicker
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalPalette
import io.github.audxius.pillar.ui.theme.LocalSettings
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()

    val pickRoms = rememberFolderPicker { uri ->
        scope.launch { graph.store.update { it.copy(romRoots = (it.romRoots + uri.toString()).distinct()) } }
    }
    val pickMedia = rememberFolderPicker { uri -> scope.launch { graph.store.update { it.copy(mediaRoot = uri.toString()) } } }

    fun finish() {
        scope.launch {
            graph.store.update { it.copy(onboarded = true) }
            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            graph.library.scan()
        }
    }

    val t = rememberInfiniteTransition(label = "logo")
    val float by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(112.dp).graphicsLayer { translationY = float * density }
                .shadow(30.dp, RoundedCornerShape(32.dp), ambientColor = palette.primary, spotColor = palette.primary)
                .clip(RoundedCornerShape(32.dp))
                .background(palette.primary),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.matchParentSize())
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Pillar", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "A good-looking, portrait-first home for your game collection. Three quick steps and you're playing.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Step(
            Icons.Rounded.Folder, "1 · Your ROMs",
            settings.romRoots.firstOrNull()?.let { "Using ${Saf.describeTree(Uri.parse(it))}" }
                ?: "Pick the folder with one sub-folder per system (snes, gba, psx…), like ES-DE uses.",
            done = settings.romRoots.isNotEmpty(),
            action = if (settings.romRoots.isEmpty()) "Choose folder" else "Add another",
            onAction = pickRoms,
        )
        Step(
            Icons.Rounded.Image, "2 · Artwork (optional)",
            settings.mediaRoot?.let { "Using ${Saf.describeTree(Uri.parse(it))}" }
                ?: "Covers are fetched online automatically. Already scraped with ES-DE? Pick its downloaded_media folder for fanart and logos too.",
            done = settings.mediaRoot != null,
            action = "Choose media folder",
            onAction = pickMedia,
        )
        Step(
            Icons.Rounded.Cloud, "3 · Online (optional)",
            "Connect a RomM server and RetroAchievements now or any time later in Settings.",
            done = settings.rommConfigured || settings.raConfigured,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { nav.navigate(Routes.settingsPage(SettingsPage.ROMM)) }) {
                    Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("RomM")
                }
                TextButton(onClick = { nav.navigate(Routes.settingsPage(SettingsPage.RA)) }) {
                    Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("RetroAchievements")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::finish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(50),
        ) { Text(if (settings.romRoots.isEmpty()) "Skip for now" else "Let's go", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Step(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Glass(Modifier.fillMaxWidth().padding(vertical = 6.dp), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (done) Color(0xFF4ADE80).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) Icons.Rounded.CheckCircle else icon, null, tint = if (done) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .tappable(RoundedCornerShape(50), onClick = onAction).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        extra?.invoke()
    }
}
