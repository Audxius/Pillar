@file:OptIn(ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.BuildConfig
import io.github.audxius.pillar.data.AppFont
import io.github.audxius.pillar.data.BackgroundStyle
import io.github.audxius.pillar.data.CoverShape
import io.github.audxius.pillar.data.CoverStyle
import io.github.audxius.pillar.data.HomeLayout
import io.github.audxius.pillar.data.CustomCommand
import io.github.audxius.pillar.data.OnlineArt
import io.github.audxius.pillar.data.OrientationMode
import io.github.audxius.pillar.data.Saf
import io.github.audxius.pillar.data.SystemOverride
import io.github.audxius.pillar.data.ThemeMode
import io.github.audxius.pillar.ui.BottomBarSpace
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.LocalSnackbar
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.Glass
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.screens.EmulatorSheet
import io.github.audxius.pillar.ui.screens.ScreenHeader
import io.github.audxius.pillar.ui.screens.rememberVisibleGames
import io.github.audxius.pillar.ui.theme.AccentChoices
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.styledSurface
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

const val REPO_URL = "https://github.com/Audxius/Pillar"

enum class SettingsPage(val title: String, val description: String) {
    APPEARANCE("Appearance", "Theme, colors, fonts, background, covers"),
    HOME("Home screen", "Choose and reorder widgets"),
    LIBRARY("Library", "ROM folders, artwork, scanning"),
    PLATFORMS("Platforms", "Show, hide, rename, recolor and reorder"),
    EMULATORS("Emulators", "Default emulator per system, custom commands"),
    SAVES("Saves & sync", "Save folders and RetroArch-compatible WebDAV sync"),
    ROMM("RomM", "Connect your RomM server"),
    RA("RetroAchievements", "Profile and achievement progress"),
    BEHAVIOR("Behavior", "Orientation, haptics, backups"),
    ABOUT("About", "Version, licenses and credits"),
}

private fun SettingsPage.icon() = when (this) {
    SettingsPage.APPEARANCE -> Icons.Rounded.Palette
    SettingsPage.HOME -> Icons.Rounded.Dashboard
    SettingsPage.LIBRARY -> Icons.Rounded.Folder
    SettingsPage.PLATFORMS -> Icons.Rounded.ViewModule
    SettingsPage.EMULATORS -> Icons.Rounded.SportsEsports
    SettingsPage.SAVES -> Icons.Rounded.Save
    SettingsPage.ROMM -> Icons.Rounded.Cloud
    SettingsPage.RA -> Icons.Rounded.EmojiEvents
    SettingsPage.BEHAVIOR -> Icons.Rounded.TouchApp
    SettingsPage.ABOUT -> Icons.Rounded.Info
}

@Composable
fun SettingsScreen(page: SettingsPage?) {
    val nav = LocalNav.current
    val ambient = LocalAmbient.current
    androidx.compose.runtime.LaunchedEffect(Unit) { ambient.value = null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
    ) {
        item {
            if (page == null) ScreenHeader("Settings", "Make Pillar yours")
            else ScreenHeader(page.title, onBack = { nav.popBackStack() })
        }
        item {
            when (page) {
                null -> SettingsIndex()
                SettingsPage.APPEARANCE -> AppearancePage()
                SettingsPage.HOME -> HomePage()
                SettingsPage.LIBRARY -> LibraryPage()
                SettingsPage.PLATFORMS -> PlatformsPage()
                SettingsPage.EMULATORS -> EmulatorsPage()
                SettingsPage.SAVES -> SavesPage()
                SettingsPage.ROMM -> RommPage()
                SettingsPage.RA -> RaPage()
                SettingsPage.BEHAVIOR -> BehaviorPage()
                SettingsPage.ABOUT -> AboutPage()
            }
        }
    }
}

@Composable
private fun SettingsIndex() {
    val nav = LocalNav.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))
        SettingsPage.entries.forEach { p ->
            Glass(Modifier.fillMaxWidth()) {
                SettingRow(p.title, p.description, p.icon(), onClick = { nav.navigate(Routes.settingsPage(p)) })
            }
        }
    }
}

// ---- Appearance ----------------------------------------------------------------------------

@Composable
private fun AppearancePage() {
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            update { it.copy(backgroundImageUri = uri.toString(), background = BackgroundStyle.IMAGE) }
        }
    }

    SettingsGroup("Style") {
        Text(
            "Each style changes the backdrop, panels, buttons and dock, and picks a matching palette and font you can still change below.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        io.github.audxius.pillar.ui.theme.StyleSpecs.chunked(3).forEach { rowSpecs ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowSpecs.forEach { spec ->
                val selected = s.uiStyle == spec.style
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .tappable(RoundedCornerShape(18.dp)) {
                            update {
                                it.copy(
                                    uiStyle = spec.style, palette = spec.presetPalette, themeMode = spec.presetMode,
                                    font = spec.presetFont, cornerRadius = spec.presetCorner, customAccent = null,
                                    dynamicColor = false, background = BackgroundStyle.STYLE,
                                )
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A small preview of the style as it comes: its own mode and default colors, so it
                    // doesn't change with your current light/dark or palette choice.
                    val look = spec.palettes.first()
                    CompositionLocalProvider(
                        io.github.audxius.pillar.ui.theme.LocalStyle provides spec.style,
                        io.github.audxius.pillar.ui.theme.LocalIsDark provides (spec.presetMode != ThemeMode.LIGHT),
                        io.github.audxius.pillar.ui.theme.LocalPalette provides look,
                        io.github.audxius.pillar.ui.theme.LocalBackdrop provides null,
                    ) {
                        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))) {
                            io.github.audxius.pillar.ui.theme.StyleBackdrop(look.primary, look.secondary, look.tertiary, animate = false)
                            Box(
                                Modifier.align(Alignment.Center).size(width = 70.dp, height = 40.dp).styledSurface(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                    Text(spec.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
                    Text(spec.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp))
                }
            }
        }
        }
    }

    SettingsGroup("Theme") {
        SettingChoice(
            "Mode",
            listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dark", ThemeMode.AMOLED to "AMOLED black", ThemeMode.LIGHT to "Light"),
            s.themeMode,
        ) { m -> update { it.copy(themeMode = m) } }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Palette", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Only the color schemes made for the current style.
                val activePalette = io.github.audxius.pillar.ui.theme.paletteFor(s.copy(customAccent = null)).id
                io.github.audxius.pillar.ui.theme.specFor(s.uiStyle).palettes.forEach { p ->
                    val selected = p.id == activePalette && s.customAccent == null && !s.dynamicColor
                    Column(
                        Modifier.width(68.dp).tappable(RoundedCornerShape(16.dp)) {
                            update { it.copy(palette = p.id, customAccent = null, dynamicColor = false) }
                        }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(p.primary, p.secondary, p.tertiary, p.primary)))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                        Spacer(Modifier.height(4.dp))
                        Text(p.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Accent color", style = MaterialTheme.typography.titleSmall)
            Text("Overrides the palette's main color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentChoices.forEach { c ->
                    val argb = c.toArgb().toLong() and 0xFFFFFFFFL
                    val selected = s.customAccent == argb
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .tappable(CircleShape) { update { it.copy(customAccent = argb, dynamicColor = false) } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            var hue by remember { mutableFloatStateOf(0f) }
            Text("Custom hue", style = MaterialTheme.typography.labelLarge)
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.65f, 1f) }))
                )
                Slider(
                    value = hue, onValueChange = { hue = it }, valueRange = 0f..359f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.hsv(hue, 0.65f, 1f),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                    onValueChangeFinished = {
                        val argb = Color.hsv(hue, 0.6f, 1f).toArgb().toLong() and 0xFFFFFFFFL
                        update { it.copy(customAccent = argb, dynamicColor = false) }
                    },
                )
            }
            if (s.customAccent != null) {
                TextButton(onClick = { update { it.copy(customAccent = null) } }) { Text("Use palette color") }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SettingSwitch("Material You", "Use colors from your wallpaper", s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
        }
    }

    SettingsGroup("Text") {
        SettingChoice(
            "Font",
            listOf(AppFont.NUNITO to "Nunito", AppFont.OUTFIT to "Outfit", AppFont.SPACE_GROTESK to "Space Grotesk", AppFont.EXO to "Exo 2", AppFont.PIXEL to "Pixel", AppFont.SYSTEM to "System"),
            s.font,
        ) { f -> update { it.copy(font = f) } }
        SettingSlider("Text size", s.textScale, 0.85f..1.3f, steps = 8, label = { "${(it * 100).toInt()}%" }) { v ->
            update { it.copy(textScale = v) }
        }
    }

    SettingsGroup("Background") {
        SettingChoice(
            "Style",
            listOf(BackgroundStyle.STYLE to "Style default", BackgroundStyle.ART to "Game art", BackgroundStyle.AURORA to "Aurora", BackgroundStyle.GRADIENT to "Gradient", BackgroundStyle.SOLID to "Solid", BackgroundStyle.IMAGE to "Image"),
            s.background,
        ) { b ->
            if (b == BackgroundStyle.IMAGE && s.backgroundImageUri == null) pickImage.launch(arrayOf("image/*"))
            else update { it.copy(background = b) }
        }
        if (s.background == BackgroundStyle.IMAGE) {
            SettingRow("Choose image", "Any picture from your device", onClick = { pickImage.launch(arrayOf("image/*")) })
            if (Build.VERSION.SDK_INT >= 31) {
                SettingSlider("Blur", s.backgroundBlur.toFloat(), 0f..40f, label = { "${it.toInt()}" }) { v -> update { it.copy(backgroundBlur = v.toInt()) } }
            }
            SettingSlider("Dim", s.backgroundDim, 0f..0.9f, label = { "${(it * 100).toInt()}%" }) { v -> update { it.copy(backgroundDim = v) } }
        }
        if (s.background == BackgroundStyle.AURORA || s.background == BackgroundStyle.ART || s.background == BackgroundStyle.STYLE) {
            SettingSwitch("Animate", "Slowly drifting colors", s.animateBackground) { v -> update { it.copy(animateBackground = v) } }
        }
        SettingSwitch("Ambient color", "Tint the background with the current platform or game", s.ambientColor) { v -> update { it.copy(ambientColor = v) } }
        SettingSwitch("Frosted cards", "Translucent panels over the background", s.glassCards) { v -> update { it.copy(glassCards = v) } }
    }

    SettingsGroup("Shapes & layout") {
        SettingSlider("Corner roundness", s.cornerRadius.toFloat(), 0f..36f, label = { "${it.toInt()} dp" }) { v -> update { it.copy(cornerRadius = v.toInt()) } }
        SettingChoice(
            "Cover art",
            listOf(CoverStyle.BOXART to "Box art", CoverStyle.GRID to "SteamGridDB covers", CoverStyle.SQUARE to "Square icons"),
            s.coverStyle,
            description = "SteamGridDB options need an API key (Settings → Library). Square icons look best with the square shape.",
        ) { c -> update { it.copy(coverStyle = c, coverShape = if (c == CoverStyle.SQUARE) CoverShape.SQUARE else it.coverShape) } }
        SettingSwitch("Crop to fill", "Off shows the whole image over a blurred backdrop", s.coverCrop) { v -> update { it.copy(coverCrop = v) } }
        SettingSwitch("Game logos", "Show logos instead of plain titles where available (sized to match each other)", s.showLogos) { v -> update { it.copy(showLogos = v) } }
        SettingChoice(
            "Cover shape",
            listOf(CoverShape.NATURAL to "Real shape", CoverShape.BOX to "Box (2:3)", CoverShape.SQUARE to "Square", CoverShape.WIDE to "Wide (4:3)"),
            s.coverShape,
        ) { c -> update { it.copy(coverShape = c) } }
        SettingSlider("Grid columns", s.gridColumns.toFloat(), 2f..5f, steps = 2, label = { "${it.toInt()}" }) { v -> update { it.copy(gridColumns = v.toInt()) } }
        SettingSwitch("Show game titles", "Under covers in grids and rows", s.showGameTitles) { v -> update { it.copy(showGameTitles = v) } }
        SettingSwitch("Navigation labels", "Text under the bottom bar icons", s.navLabels) { v -> update { it.copy(navLabels = v) } }
    }
}

// ---- Home ----------------------------------------------------------------------------------

@Composable
private fun HomePage() {
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    SettingsGroup("Layout") {
        SettingChoice(
            "Home style",
            listOf(HomeLayout.CAROUSEL to "Carousel", HomeLayout.FEED to "Widget feed"),
            s.homeLayout,
            description = "Carousel: full-screen art with a cover carousel. Feed: scrolling widgets you arrange below.",
        ) { l -> update { it.copy(homeLayout = l) } }
        if (s.homeLayout == HomeLayout.CAROUSEL) SettingChoice(
            "Browse games",
            listOf(
                io.github.audxius.pillar.data.HomeBrowse.COVERS to "Covers",
                io.github.audxius.pillar.data.HomeBrowse.COVERFLOW to "Cover flow",
                io.github.audxius.pillar.data.HomeBrowse.GRID to "Grid",
                io.github.audxius.pillar.data.HomeBrowse.SHELF to "Shelf",
                io.github.audxius.pillar.data.HomeBrowse.LIST to "List",
            ),
            s.homeBrowse,
            description = "Covers: a sideways carousel. Cover flow: covers turning in 3D. Grid: icons in a grid. Shelf: covers scrolling up and down, each one's disc or cartridge sliding out. List: titles scrolling up and down.",
        ) { v -> update { it.copy(homeBrowse = v) } }
        if (s.homeLayout == HomeLayout.CAROUSEL && s.homeBrowse == io.github.audxius.pillar.data.HomeBrowse.GRID) {
            SettingSlider("Grid columns", s.homeGridColumns.toFloat(), 2f..5f, steps = 2, label = { "${it.toInt()}" }) { v -> update { it.copy(homeGridColumns = v.toInt()) } }
        }
    }
    SettingsGroup("Widgets") {
        Text(
            "Turn widgets on or off and use the arrows to change their order.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        s.homeWidgets.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.type.title, style = MaterialTheme.typography.titleSmall)
                    Text(w.type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ArrowButton(Icons.Rounded.KeyboardArrowUp, enabled = i > 0) {
                    update { st -> st.copy(homeWidgets = st.homeWidgets.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                }
                ArrowButton(Icons.Rounded.KeyboardArrowDown, enabled = i < s.homeWidgets.lastIndex) {
                    update { st -> st.copy(homeWidgets = st.homeWidgets.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                }
                Switch(checked = w.enabled, onCheckedChange = { v ->
                    update { st -> st.copy(homeWidgets = st.homeWidgets.map { if (it.type == w.type) it.copy(enabled = v) else it }) }
                })
            }
        }
        TextButton(
            onClick = { update { it.copy(homeWidgets = io.github.audxius.pillar.data.DefaultWidgets) } },
            modifier = Modifier.padding(6.dp),
        ) { Text("Reset to default layout") }
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).then(if (enabled) Modifier.tappable(CircleShape, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    }
}

// ---- Library -------------------------------------------------------------------------------

@Composable
private fun LibraryPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val user = LocalUserData.current
    val update = rememberSettingsUpdater()
    val scope = rememberCoroutineScope()
    val scan by graph.library.scan.collectAsStateWithLifecycle()
    val games by graph.library.games.collectAsStateWithLifecycle()

    val addRoot = rememberFolderPicker { uri ->
        scope.launch {
            graph.store.update { it.copy(romRoots = (it.romRoots + uri.toString()).distinct()) }
            graph.library.scan()
        }
    }
    val pickMedia = rememberFolderPicker { uri ->
        scope.launch {
            graph.store.update { it.copy(mediaRoot = uri.toString()) }
            graph.library.reindexMediaAsync()
        }
    }
    val pickDownload = rememberFolderPicker { uri ->
        scope.launch {
            graph.store.update { it.copy(downloadRoot = uri.toString()) }
            graph.library.scan()
        }
    }

    SettingsGroup("ROM folders") {
        Text(
            "Pick the folder that holds one sub-folder per system (snes, gba, psx, …), the same layout ES-DE uses. You can also pick a single system folder.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        s.romRoots.forEach { root ->
            SettingRow(Saf.describeTree(Uri.parse(root)), null, Icons.Rounded.Folder) {
                Icon(
                    Icons.Rounded.Delete, "Remove",
                    Modifier.size(40.dp).clip(CircleShape).tappable(CircleShape) {
                        scope.launch {
                            graph.store.update { it.copy(romRoots = it.romRoots - root) }
                            graph.library.scan()
                        }
                    }.padding(8.dp),
                )
            }
        }
        SettingRow("Add folder", null, Icons.Rounded.Add, onClick = addRoot)
        SettingRow(
            if (scan.running) "Scanning…" else "Rescan library",
            if (scan.running) scan.message else "${games.size} games found",
            onClick = if (scan.running) null else ({ graph.library.scanAsync() }),
        ) { if (scan.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        SettingSwitch("Hide disc tracks", "Show multi-disc and .cue games once instead of every track", s.hideDiscTracks) { v ->
            update { it.copy(hideDiscTracks = v) }
            graph.library.scanAsync()
        }
    }

    SettingsGroup("Artwork") {
        SettingSwitch("Online covers", "Fetch box art from the libretro thumbnail server by file name", s.onlineCovers) { v -> update { it.copy(onlineCovers = v) } }
        if (s.onlineCovers) {
            SettingChoice(
                "Online art type",
                listOf(OnlineArt.BOXART to "Box art", OnlineArt.TITLE to "Title screen", OnlineArt.SNAP to "Gameplay"),
                s.onlineArt,
            ) { a -> update { it.copy(onlineArt = a) } }
        }
        SettingRow(
            "Media folder",
            s.mediaRoot?.let { Saf.describeTree(Uri.parse(it)) } ?: "Optional: ES-DE's downloaded_media folder for covers, fanart and logos",
            Icons.Rounded.Folder,
            onClick = pickMedia,
        )
        if (s.mediaRoot != null) {
            SettingRow("Remove media folder", null, onClick = {
                scope.launch {
                    graph.store.update { it.copy(mediaRoot = null) }
                    graph.library.reindexMediaAsync()
                }
            })
        }
    }

    SettingsGroup("Box art database") {
        val status by graph.boxArt.status.collectAsStateWithLifecycle()
        val allGames by graph.allGames.collectAsStateWithLifecycle()
        val user = io.github.audxius.pillar.ui.theme.LocalUserData.current
        Text(
            "Real disc and cartridge art for the Shelf, from LaunchBox's community Games Database. " +
                "It's one ~110 MB download that Pillar reads once, keeping only your games' entries. Run it again after adding games.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        when (val st = status) {
            is io.github.audxius.pillar.data.BoxArtDb.Status.Running -> Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(st.message, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
            }
            else -> SettingRow(
                if (st is io.github.audxius.pillar.data.BoxArtDb.Status.Done) "Update box art" else "Download box art database",
                when (st) {
                    is io.github.audxius.pillar.data.BoxArtDb.Status.Done -> "Found art for ${st.matched} games"
                    is io.github.audxius.pillar.data.BoxArtDb.Status.Failed -> st.message
                    else -> "About 110 MB, best on Wi-Fi"
                },
                Icons.Rounded.Folder,
                onClick = { graph.boxArt.download(allGames, user.customTitle) },
            )
        }
    }

    SettingsGroup("SteamGridDB") {
        var key by remember { mutableStateOf(s.sgdbKey) }
        var sgdbStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
        Text(
            "Optional. Adds polished covers, square icons, wide backgrounds and logos for most games. Get a free key at steamgriddb.com → Preferences → API.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        SettingField("API key", key, { key = it }, secret = true)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                scope.launch {
                    graph.store.update { it.copy(sgdbKey = key.trim()) }
                    sgdbStatus = if (key.isBlank()) null else if (graph.artwork.testSgdb()) "Key works" to true else "SteamGridDB rejected this key" to false
                }
            }) { Text("Save & test") }
        }
        sgdbStatus?.let { (m, ok) -> StatusLine(m, ok) }
        SettingRow("Refresh all artwork", "Look every game up again", onClick = { graph.artwork.reset() })
    }

    SettingsGroup("Downloads") {
        SettingRow(
            "RomM download folder",
            s.downloadRoot?.let { Saf.describeTree(Uri.parse(it)) } ?: "Uses your first ROM folder",
            Icons.Rounded.Folder,
            onClick = pickDownload,
        )
        if (s.downloadRoot != null) SettingRow("Use first ROM folder", null, onClick = { update { it.copy(downloadRoot = null) } })
    }

    if (user.hidden.isNotEmpty()) {
        SettingsGroup("Hidden games") {
            user.hidden.forEach { id ->
                val g = games.firstOrNull { it.id == id }
                SettingRow(g?.title ?: id.substringAfterLast('/'), g?.let { systemName(it.system) }) {
                    TextButton(onClick = { scope.launch { graph.store.updateUser { it.copy(hidden = it.hidden - id) } } }) { Text("Show") }
                }
            }
        }
    }
}

// ---- Platforms -----------------------------------------------------------------------------

@Composable
private fun PlatformsPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    val games by graph.library.games.collectAsStateWithLifecycle()
    val counts = remember(games) { games.groupingBy { it.system }.eachCount() }
    // Hidden systems stay listed here so they can be shown again.
    val systems = remember(counts, s.systemOrder) {
        val order = s.systemOrder
        counts.keys.sortedWith(compareBy<String> { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { graph.catalog.meta(it).short.lowercase() })
    }
    var editing by remember { mutableStateOf<String?>(null) }

    SettingsGroup {
        Text(
            "Reorder with the arrows, tap a platform to rename or recolor it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        systems.forEachIndexed { i, system ->
            val hidden = system in s.hiddenSystems
            Row(
                Modifier.fillMaxWidth().tappable(RoundedCornerShape(16.dp)) { editing = system }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(systemColor(system)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(systemName(system), style = MaterialTheme.typography.titleSmall, color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text("${counts[system] ?: 0} games", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ArrowButton(Icons.Rounded.KeyboardArrowUp, i > 0) {
                    update { st -> st.copy(systemOrder = systems.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                }
                ArrowButton(Icons.Rounded.KeyboardArrowDown, i < systems.lastIndex) {
                    update { st -> st.copy(systemOrder = systems.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                }
                ArrowButton(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, true) {
                    update { st -> st.copy(hiddenSystems = if (hidden) st.hiddenSystems - system else st.hiddenSystems + system) }
                }
            }
        }
        if (systems.isEmpty()) Text("No platforms yet. Add a ROM folder first.", modifier = Modifier.padding(14.dp))
        if (s.systemOrder.isNotEmpty() || s.systemOverrides.isNotEmpty()) {
            TextButton(onClick = { update { it.copy(systemOrder = emptyList(), systemOverrides = emptyMap()) } }, modifier = Modifier.padding(6.dp)) {
                Text("Reset order, names and colors")
            }
        }
    }

    editing?.let { system ->
        val current = s.systemOverrides[system]
        var name by remember(system) { mutableStateOf(current?.displayName.orEmpty()) }
        var color by remember(system) { mutableStateOf(current?.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(graph.catalog.fullName(system)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Display name") }, placeholder = { Text(graph.catalog.meta(system).short) }, singleLine = true)
                    Spacer(Modifier.height(14.dp))
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf(Color(graph.catalog.meta(system).color.toInt())) + AccentChoices).forEachIndexed { idx, c ->
                            val argb = c.toArgb().toLong() and 0xFFFFFFFFL
                            val selected = if (idx == 0) color == null else color == argb
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(c)
                                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .tappable(CircleShape) { color = if (idx == 0) null else argb }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val o = SystemOverride(name.trim().ifBlank { null }, color)
                    update { st ->
                        st.copy(systemOverrides = if (o.displayName == null && o.color == null) st.systemOverrides - system else st.systemOverrides + (system to o))
                    }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ---- Emulators -----------------------------------------------------------------------------

@Composable
private fun EmulatorsPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val user = LocalUserData.current
    val update = rememberSettingsUpdater()
    val games = rememberVisibleGames()
    val systems = remember(games) { games.map { it.system }.distinct().sortedBy { graph.catalog.meta(it).short.lowercase() } }
    var picking by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }

    SettingsGroup("Default emulator per system") {
        if (systems.isEmpty()) Text("Add a ROM folder to configure emulators.", modifier = Modifier.padding(14.dp))
        systems.forEach { system ->
            val chosen = s.defaultEmulators[system]
            val auto = remember(system, s.customCommands) {
                graph.launcher.automaticCommand(system, s)?.takeIf { graph.launcher.isCommandAvailable(it) }?.label
            }
            SettingRow(
                systemName(system),
                chosen ?: auto?.let { "Automatic · $it" } ?: "No installed emulator found",
                onClick = { picking = system },
            )
        }
    }
    SettingsGroup("Custom launch commands") {
        Text(
            "Add your own commands in ES-DE syntax, e.g.\n%EMULATOR_RETROARCH% %EXTRA_CONFIGFILE%=… %EXTRA_LIBRETRO%=… %EXTRA_ROM%=%ROM%\nor any app: %EMULATOR_PPSSPP% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        s.customCommands.forEach { (system, list) ->
            list.forEach { cmd ->
                SettingRow("${systemName(system)} · ${cmd.label}", cmd.command) {
                    Icon(
                        Icons.Rounded.Delete, "Delete",
                        Modifier.size(40.dp).clip(CircleShape).tappable(CircleShape) {
                            update { st ->
                                val remaining = st.customCommands[system].orEmpty() - cmd
                                st.copy(customCommands = if (remaining.isEmpty()) st.customCommands - system else st.customCommands + (system to remaining))
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
        SettingRow("Add command", null, Icons.Rounded.Add, onClick = { adding = systems.firstOrNull() ?: "snes" })
    }
    SettingsGroup {
        SettingSwitch("Confirm before launching", "Ask before starting a game from its page", s.confirmLaunch) { v -> update { it.copy(confirmLaunch = v) } }
        if (user.emulatorOverride.isNotEmpty()) {
            SettingRow("Clear per-game emulator choices", "${user.emulatorOverride.size} games use their own emulator", onClick = {
                graph.scope.launch { graph.store.updateUser { it.copy(emulatorOverride = emptyMap()) } }
            })
        }
    }

    picking?.let { system ->
        EmulatorSheet(system, s.defaultEmulators[system], "Default for ${systemName(system)}", onDismiss = { picking = null }) { label ->
            update { st -> st.copy(defaultEmulators = if (label == null) st.defaultEmulators - system else st.defaultEmulators + (system to label)) }
            picking = null
        }
    }
    adding?.let { initial ->
        var system by remember { mutableStateOf(initial) }
        var label by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Custom command") },
            text = {
                Column {
                    OutlinedTextField(system, { system = it.trim().lowercase() }, label = { Text("System folder (e.g. snes)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(command, { command = it }, label = { Text("Command") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = label.isNotBlank() && command.contains("%") && graph.catalog[system] != null,
                    onClick = {
                        val c = CustomCommand(label.trim(), command.trim())
                        update { st -> st.copy(customCommands = st.customCommands + (system to (st.customCommands[system].orEmpty().filter { it.label != c.label } + c))) }
                        adding = null
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}

// ---- RomM ----------------------------------------------------------------------------------

@Composable
private fun RommPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(s.rommUrl) }
    var user by remember { mutableStateOf(s.rommUser) }
    var pass by remember { mutableStateOf(s.rommPassword) }
    var token by remember { mutableStateOf(s.rommToken) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var testing by remember { mutableStateOf(false) }

    SettingsGroup("Server") {
        SettingField("Server address", url, { url = it }, placeholder = "https://romm.example.com", keyboard = androidx.compose.ui.text.input.KeyboardType.Uri)
        SettingField("Username", user, { user = it })
        SettingField("Password", pass, { pass = it }, secret = true)
        Text(
            "Or use a RomM client API token instead of a password (Profile → Client API Tokens in RomM). A token takes priority.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        SettingField("API token (optional)", token, { token = it }, secret = true)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !testing && url.isNotBlank(), onClick = {
                scope.launch {
                    graph.store.update { it.copy(rommUrl = url.trim(), rommUser = user.trim(), rommPassword = pass, rommToken = token.trim()) }
                    testing = true
                    status = runCatching { graph.romm.test() }.fold(
                        { v -> "Connected · RomM $v" to true },
                        { e -> (e.message ?: "Connection failed") to false },
                    )
                    testing = false
                }
            }) { Text("Save & test") }
            Spacer(Modifier.width(12.dp))
            if (testing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            if (s.rommConfigured) {
                OutlinedButton(onClick = {
                    url = ""; user = ""; pass = ""; token = ""; status = null
                    scope.launch { graph.store.update { it.copy(rommUrl = "", rommUser = "", rommPassword = "", rommToken = "") } }
                }) { Text("Disconnect") }
            }
        }
        status?.let { (msg, ok) -> StatusLine(msg, ok) }
    }
    if (s.rommConfigured) {
        val update = rememberSettingsUpdater()
        val romCount by graph.rommRepo.games.collectAsStateWithLifecycle()
        val syncing by graph.rommRepo.syncing.collectAsStateWithLifecycle()
        val error by graph.rommRepo.error.collectAsStateWithLifecycle()
        SettingsGroup("Library") {
            SettingSwitch(
                "Show RomM games in my library",
                "Games that aren't on this device appear everywhere with a cloud badge. Play downloads them first.",
                s.rommInLibrary,
            ) { v -> update { it.copy(rommInLibrary = v) } }
            SettingRow(
                if (syncing) "Refreshing…" else "Refresh RomM library",
                error ?: "${romCount.size} games on the server",
                onClick = if (syncing) null else ({ graph.rommRepo.refresh(force = true) }),
            ) { if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        }
    }
}

// ---- RetroAchievements ---------------------------------------------------------------------

@Composable
private fun RaPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var user by remember { mutableStateOf(s.raUser) }
    var key by remember { mutableStateOf(s.raApiKey) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var testing by remember { mutableStateOf(false) }

    SettingsGroup("Account") {
        SettingField("Username", user, { user = it })
        SettingField("Web API key", key, { key = it }, secret = true)
        SettingRow("Get your web API key", "retroachievements.org → Settings → Keys", onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/settings")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !testing && user.isNotBlank() && key.isNotBlank(), onClick = {
                scope.launch {
                    graph.store.update { it.copy(raUser = user.trim(), raApiKey = key.trim()) }
                    testing = true
                    status = runCatching { graph.raClient.profile() }.fold(
                        { p -> "Signed in as ${p.user} · ${"%,d".format(p.totalPoints)} points" to true },
                        { e -> (e.message ?: "Couldn't sign in") to false },
                    )
                    testing = false
                }
            }) { Text("Save & test") }
            Spacer(Modifier.width(12.dp))
            if (testing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            if (s.raConfigured) {
                OutlinedButton(onClick = {
                    user = ""; key = ""; status = null
                    scope.launch { graph.store.update { it.copy(raUser = "", raApiKey = "") } }
                }) { Text("Sign out") }
            }
        }
        status?.let { (msg, ok) -> StatusLine(msg, ok) }
    }
    SettingsGroup("Matching") {
        SettingSwitch(
            "Match local games",
            "Find local games on RetroAchievements by ROM hash, like RetroArch, falling back to the title. Games downloaded from RomM use RomM's match.",
            s.raMatchTitles,
        ) { v -> update { it.copy(raMatchTitles = v) } }
        SettingRow("Re-match games", "Forget automatic matches and look every game up again", onClick = {
            scope.launch { graph.store.updateUser { it.copy(raIds = it.raIds.filterKeys { id -> id in it.rommIds }) } }
        })
    }
}

// ---- Behavior ------------------------------------------------------------------------------

@Composable
private fun BehaviorPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var includeSecrets by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(graph.store.exportJson(includeSecrets).toByteArray()) }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Settings exported" else "Export failed")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val ok = json != null && graph.store.importJson(json)
            snackbar.showSnackbar(if (ok) "Settings imported" else "That file isn't a Pillar backup")
            if (ok) graph.library.scanAsync()
        }
    }

    SettingsGroup("Screen") {
        SettingChoice(
            "Orientation",
            listOf(OrientationMode.PORTRAIT to "Portrait", OrientationMode.AUTO to "Auto-rotate", OrientationMode.LANDSCAPE to "Landscape"),
            s.orientation,
        ) { o -> update { it.copy(orientation = o) } }
        SettingSwitch("Animations", "Transitions, shared covers and motion", s.animations) { v -> update { it.copy(animations = v) } }
    }
    SettingsGroup("Feedback") {
        SettingSwitch("Haptics", "Subtle vibration on taps", s.haptics) { v -> update { it.copy(haptics = v) } }
        SettingSwitch("Menu sounds", "Short sounds for taps, going back and scrolling, in each style's own flavor", s.menuSounds) { v -> update { it.copy(menuSounds = v) } }
    }
    SettingsGroup("Backup") {
        SettingSwitch("Include passwords and keys", "Only turn on for backups you keep private", includeSecrets) { includeSecrets = it }
        SettingRow("Export settings", "Theme, layout, folders and emulator choices", onClick = { exportLauncher.launch("pillar-settings.json") })
        SettingRow("Import settings", null, onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) })
    }
}

// ---- About ---------------------------------------------------------------------------------

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val graph = LocalGraph.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    SettingsGroup {
        SettingRow("Pillar ${BuildConfig.VERSION_NAME}", "A portrait-first emulation frontend")
        SettingRow("Source code & updates", REPO_URL, onClick = { open(REPO_URL) })
        SettingRow("Systems known", "${graph.catalog.systems.size} systems · ${graph.catalog.findRules.size} emulators")
    }
    SettingsGroup("Credits & licenses") {
        SettingRow("ES-DE", "System and emulator definitions from ES-DE (MIT License)", onClick = { open("https://es-de.org") })
        SettingRow("RomM", "Self-hosted ROM manager API", onClick = { open("https://romm.app") })
        SettingRow("RetroAchievements", "Achievement data and images", onClick = { open("https://retroachievements.org") })
        SettingRow("libretro thumbnails", "Online box art", onClick = { open("https://thumbnails.libretro.com") })
        SettingRow("Fonts", "Nunito, Outfit, Space Grotesk, Press Start 2P (SIL Open Font License)")
    }
    Text(
        "Pillar is not affiliated with any of the projects above, or with console makers. It does not include games or emulators.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        overflow = TextOverflow.Clip,
        modifier = Modifier.padding(24.dp),
    )
}

// ---- Saves & sync --------------------------------------------------------------------------

@Composable
private fun SavesPage() {
    val graph = LocalGraph.current
    val s = LocalSettings.current
    val update = rememberSettingsUpdater()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(s.webdavUrl) }
    var user by remember { mutableStateOf(s.webdavUser) }
    var pass by remember { mutableStateOf(s.webdavPassword) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val pickSaves = rememberFolderPicker { uri -> update { it.copy(savesDir = uri.toString()) } }
    val pickStates = rememberFolderPicker { uri -> update { it.copy(statesDir = uri.toString()) } }

    SettingsGroup("How it works") {
        Text(
            "Pillar syncs the same folders and WebDAV server as RetroArch's Cloud Sync, using RetroArch's own manifest format, so the two never fight. It syncs every time you open Pillar and when you come back from a game.\n\n" +
                "1. In RetroArch: Settings → Directory → set Save Files and Save States to shared folders, e.g. /storage/emulated/0/RetroArch/saves and …/states (Android blocks other apps from RetroArch's private folder).\n" +
                "2. Pick those same folders below.\n" +
                "3. Enter the WebDAV address, username and password you use in RetroArch → Settings → Cloud Sync. For RomM that's https://your-romm/api/sync/retroarch with your RomM login.\n\n" +
                "Pillar never deletes anything on the server. Before it replaces a save (on the server or here) it keeps a backup you can restore from the Saves tab.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
    SettingsGroup("Folders") {
        SettingRow("Save files", s.savesDir?.let { Saf.describeTree(Uri.parse(it)) } ?: "Not set", Icons.Rounded.Folder, onClick = pickSaves)
        SettingRow("Save states", s.statesDir?.let { Saf.describeTree(Uri.parse(it)) } ?: "Not set (optional)", Icons.Rounded.Folder, onClick = pickStates)
    }
    SettingsGroup("WebDAV server") {
        if (s.rommConfigured) {
            SettingRow(
                "Use my RomM server",
                "Fills in RomM's RetroArch sync address and your RomM login",
                Icons.Rounded.Cloud,
                onClick = {
                    url = s.rommUrl.trim().trimEnd('/') + "/api/sync/retroarch"
                    user = s.rommUser
                    pass = s.rommPassword
                },
            )
        }
        SettingField("Address", url, { url = it }, placeholder = "https://cloud.example.com/remote.php/dav/files/me/retroarch", keyboard = androidx.compose.ui.text.input.KeyboardType.Uri)
        SettingField("Username", user, { user = it })
        SettingField("Password", pass, { pass = it }, secret = true)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !testing && url.isNotBlank(), onClick = {
                scope.launch {
                    graph.store.update { it.copy(webdavUrl = url.trim(), webdavUser = user.trim(), webdavPassword = pass) }
                    testing = true
                    status = runCatching { graph.saveSync.testConnection() }.fold({ it to true }, { (it.message ?: "Couldn't connect") to false })
                    testing = false
                }
            }) { Text("Save & test") }
            Spacer(Modifier.width(12.dp))
            if (testing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        status?.let { (msg, ok) -> StatusLine(msg, ok) }
        SettingSwitch("Sync when Pillar opens", "Also runs when you return from a game", s.syncOnOpen) { v -> update { it.copy(syncOnOpen = v) } }
        val context = androidx.compose.ui.platform.LocalContext.current
        SettingSwitch(
            "Show sync status over games",
            "A small indicator in the corner if saves are still syncing after you've gone back into a game. Needs \"Display over other apps\".",
            s.syncOverlay,
        ) { v ->
            update { it.copy(syncOverlay = v) }
            if (v && Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(context)) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}
