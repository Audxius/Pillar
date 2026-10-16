@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package io.github.audxius.pillar.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.audxius.pillar.ui.components.rememberSnapSources
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.audxius.pillar.ui.components.rememberCoverSources
import io.github.audxius.pillar.ui.components.MultiImage
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.audxius.pillar.data.CoverShape
import io.github.audxius.pillar.data.DownloadState
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.ui.LocalNav
import io.github.audxius.pillar.ui.Routes
import io.github.audxius.pillar.ui.components.GameCover
import io.github.audxius.pillar.ui.components.GameLogo
import io.github.audxius.pillar.ui.components.PrimaryAction
import io.github.audxius.pillar.ui.components.gameTitle
import io.github.audxius.pillar.ui.components.ratio
import io.github.audxius.pillar.ui.components.rememberArt
import io.github.audxius.pillar.ui.components.rememberHeroSources
import io.github.audxius.pillar.ui.components.systemColor
import io.github.audxius.pillar.ui.components.systemName
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.rememberGameLauncher
import io.github.audxius.pillar.ui.theme.LocalAmbient
import io.github.audxius.pillar.ui.theme.LocalAmbientArt
import io.github.audxius.pillar.ui.theme.LocalCorner
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.chipColors
import io.github.audxius.pillar.ui.theme.chipSurface
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Gold used for everything RetroAchievements. */
val TrophyGold = Color(0xFFFFC94D)

/**
 * Home: every game, most recently played first (then the rest by name), as a cover carousel
 * under the focused game's logo. Browsing by system lives in the Library tab.
 */
@Composable
fun HomeCarouselScreen() {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val games = rememberVisibleGames()
    var quick by remember { mutableStateOf<Game?>(null) }

    val ordered = remember(games, user.lastPlayed, user.customTitle) {
        val (played, rest) = games.partition { (user.lastPlayed[it.id] ?: 0) > 0 }
        played.sortedByDescending { user.lastPlayed[it.id] } + rest.sortedBy { (user.customTitle[it.id] ?: it.title).lowercase() }
    }
    if (ordered.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(48.dp))
            EmptyLibraryCardPublic()
        }
        return
    }

    val browse = LocalSettings.current.homeBrowse
    val listState = rememberLazyListState()
    val listIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: 0
        }
    }
    // Channels selects by tapping; every other style selects whatever is in the middle.
    var chosen by rememberSaveable { mutableStateOf(0) }
    val selectedIndex = if (browse == io.github.audxius.pillar.data.HomeBrowse.CHANNELS) chosen.coerceIn(0, ordered.lastIndex) else listIndex
    val centered = ordered.getOrNull(selectedIndex.coerceIn(0, ordered.lastIndex)) ?: ordered.first()
    // A tick for every game that passes the middle.
    val sfx = io.github.audxius.pillar.ui.theme.rememberSfx()
    var lastTick by remember { mutableStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if (lastTick >= 0 && lastTick != selectedIndex) sfx(io.github.audxius.pillar.data.Sfx.MOVE)
        lastTick = selectedIndex
    }
    // While flinging, the logo, backdrop and lookups wait until a game stays centered for a moment;
    // loading them for every cover that flies past is what made fast scrolling stutter.
    var selected by remember { mutableStateOf(centered) }
    LaunchedEffect(centered.id, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) delay(260)
        selected = centered
    }
    val hero = rememberHeroSources(selected)
    val ambientArt = LocalAmbientArt.current
    val ambient = LocalAmbient.current
    val selColor = systemColor(selected.system)
    LaunchedEffect(selected.id, hero) {
        ambientArt.value = hero
        ambient.value = selColor
    }
    // Look up achievement support for whatever is in focus (cached, so cheap after the first time).
    LaunchedEffect(selected.id) { if (!selected.remote) runCatching { graph.ra.idFor(selected, graph.catalog) } }


    // Cover flow, the list and channels need the room more than the logo does.
    val compact = browse == io.github.audxius.pillar.data.HomeBrowse.COVERFLOW || browse == io.github.audxius.pillar.data.HomeBrowse.LIST ||
        browse == io.github.audxius.pillar.data.HomeBrowse.CHANNELS
    val nav = LocalNav.current
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val itemHeight = (maxHeight * 0.3f).coerceIn(130.dp, 270.dp)
        Column(Modifier.fillMaxSize()) {
            if (compact) Spacer(Modifier.height(12.dp))
            // The logo gets all the room the carousel and buttons don't need; tapping it opens the game.
            if (!compact) Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                    .clickable(interactionSource = null, indication = null) { nav.navigate(Routes.game(selected.id, "home")) },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = selected, animationSpec = tween(200), label = "logo") { g ->
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TitleOrLogo(
                            g, maxWidth = maxWidth, maxHeight = minOf(maxHeight, 240.dp),
                            alignment = Alignment.Center, big = true,
                        )
                    }
                }
            }
            GameMetaPills(selected, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Spacer(Modifier.height(12.dp))
            HomePlayButton(selected)
            Spacer(Modifier.height(6.dp))
            when (browse) {
                io.github.audxius.pillar.data.HomeBrowse.LIST -> GameWheel(ordered, listState, selectedIndex, Modifier.weight(1f), onLongPress = { quick = it })
                io.github.audxius.pillar.data.HomeBrowse.CHANNELS -> ChannelGrid(ordered, chosen, { chosen = it }, Modifier.weight(1f), onLongPress = { quick = it })
                io.github.audxius.pillar.data.HomeBrowse.SHELF -> Shelf(ordered, listState, selected.id, itemHeight, onLongPress = { quick = it })
                io.github.audxius.pillar.data.HomeBrowse.COVERFLOW -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Carousel(ordered, listState, selectedIndex, (maxHeight - 20.dp) / 1.12f, coverFlow = true, onLongPress = { quick = it })
                }
                else -> Carousel(ordered, listState, selectedIndex, itemHeight, coverFlow = false, onLongPress = { quick = it })
            }
            Text(
                "${selectedIndex + 1} / ${ordered.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
            )
            // Room for the floating dock.
            Spacer(Modifier.navigationBarsPadding().height(96.dp))
        }
    }
    quick?.let { g -> GameQuickSheet(g, "home", onDismiss = { quick = null }) }
}

/** Pill chip drawn in the current style. */
@Composable
fun StyledChip(label: String, active: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val (bg, fg, rim) = chipColors(active)
    Row(
        Modifier.chipSurface(RoundedCornerShape(50), active)
            .tappable(RoundedCornerShape(50), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = fg)
    }
}

@Composable
fun StyledIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).styledSurface(CircleShape).tappable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.46f)) }
}

/**
 * System and last played as a centered row of pills, plus warnings only when something's missing:
 * no RetroAchievements set for the game, or a local game that isn't on your RomM server.
 */
@Composable
fun GameMetaPills(g: Game, modifier: Modifier = Modifier, extra: List<String> = emptyList()) {
    val graph = LocalGraph.current
    val user = LocalUserData.current
    val settings = LocalSettings.current
    val noAch by graph.ra.noAchievements.collectAsStateWithLifecycle()
    val links by graph.rommLinks.collectAsStateWithLifecycle()
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaPill(systemName(g.system), systemColor(g.system), dotted = true)
        val last = user.lastPlayed[g.id] ?: 0L
        MetaPill(if (last > 0) timeAgo(last) else "Not played yet", null, Icons.Rounded.Schedule)
        if (settings.raConfigured && user.raIds[g.id] == null && g.id in noAch) MetaPill("No achievements", null, Icons.Rounded.EmojiEvents)
        if (settings.rommConfigured && !g.remote && g.id !in links) MetaPill("Not on RomM", null, Icons.Rounded.CloudOff)
        extra.forEach { MetaPill(it, null) }
    }
}

/** A labelled pill: a colored dot or an icon, then text. */
@Composable
fun MetaPill(text: String, color: Color?, icon: ImageVector? = null, dotted: Boolean = false) {
    val (bg, fg, rim) = chipColors(false)
    Row(
        Modifier.height(32.dp).chipSurface(RoundedCornerShape(50), false)
            .then(if (color != null && dotted) Modifier.background(color.copy(alpha = 0.16f)) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> {
                Icon(icon, null, tint = color ?: fg.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            dotted && color != null -> {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                Spacer(Modifier.width(7.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun HomePlayButton(game: Game) {
    val graph = LocalGraph.current
    val launch = rememberGameLauncher()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val dl = game.rommId?.let { downloads[it] }
    val busy = game.remote && (dl is DownloadState.Queued || dl is DownloadState.Running)
    PrimaryAction(
        label = when {
            busy && dl is DownloadState.Running -> "Downloading ${(dl.fraction * 100).toInt()}%"
            busy -> "Waiting to download"
            game.remote -> "Download & play"
            else -> "Play"
        },
        icon = if (game.remote) Icons.Rounded.CloudDownload else Icons.Rounded.PlayArrow,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(54.dp),
        progress = (dl as? DownloadState.Running)?.fraction?.takeIf { busy },
    ) { if (!busy) launch(game, null) }
}

/** Shows the game's logo when enabled and available, otherwise its title. Nothing flashes in between. */
@Composable
fun TitleOrLogo(g: Game, maxWidth: Dp = 260.dp, maxHeight: Dp = 72.dp, alignment: Alignment = Alignment.CenterStart, big: Boolean = false) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val art = rememberArt(g)
    val logo = remember(g.id, art) { if (settings.showLogos) graph.library.logo(g, art) else null }
    var failed by remember(logo) { mutableStateOf(false) }
    // While artwork is still being looked up for a game that may have a logo, show nothing rather
    // than the plain title for a moment.
    val pending = settings.showLogos && art == null
    when {
        logo != null && !failed -> GameLogo(
            logo, gameTitle(g), maxWidth = maxWidth, maxHeight = maxHeight, alignment = alignment,
            // A big logo fills most of its box; small ones keep the shared visual weight.
            area = if (big) maxWidth.value * maxHeight.value * 0.62f else 13000f,
            onFailed = { failed = true },
        )
        pending -> Unit
        else -> Text(
            gameTitle(g),
            style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
            maxLines = if (big) 3 else 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (alignment == Alignment.Center) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun Carousel(games: List<Game>, state: LazyListState, selectedIndex: Int, itemHeight: Dp, coverFlow: Boolean, onLongPress: (Game) -> Unit) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val corner = LocalCorner.current
    // One scroll at a time: a new tap replaces a scroll that's still running.
    var tapJob by remember { mutableStateOf<Job?>(null) }
    // A touch that lands while the list is still sliding only stops it; it isn't a tap.
    var pressedWhileMoving by remember { mutableStateOf(false) }
    val catchMoving = Modifier.pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            pressedWhileMoving = state.isScrollInProgress && tapJob?.isActive != true
        }
    }
    // Cover flow: WiiFlow-style 3D covers with reflections on a glossy floor.
    val floor = MaterialTheme.colorScheme.background
    val coverH = if (coverFlow) itemHeight * 0.9f else itemHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(itemHeight * 1.12f + 8.dp).graphicsLayer()) {
        // Side padding that lets the first and last cover sit in the middle.
        val side = ((maxWidth - coverH * settings.coverShape.ratio()) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(if (coverFlow) 4.dp else 14.dp),
            verticalAlignment = if (coverFlow) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().then(catchMoving),
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, g ->
                val focused = index == selectedIndex
                val scale by animateFloatAsState(if (focused) 1.1f else 0.9f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "s")
                // Uniform tiles by default; with "Real shape" each cover sizes to its art.
                val ratio = if (settings.coverShape == CoverShape.NATURAL) {
                    (io.github.audxius.pillar.ui.components.rememberAspect(g) ?: graph.catalog.boxRatio(g.system)).coerceIn(0.5f, 1.8f)
                } else settings.coverShape.ratio()
                val reflection = if (coverFlow) androidx.compose.ui.graphics.rememberGraphicsLayer() else null
                GameCover(
                    g,
                    Modifier.height(coverH).width(coverH * ratio)
                        .then(
                            if (coverFlow) Modifier
                                .padding(top = itemHeight * 0.02f)
                                // Nearer the middle draws on top, so neighbours tuck behind the centre cover.
                                .zIndex(-abs(index - selectedIndex).toFloat())
                                .graphicsLayer {
                                    // How far this cover is from the centre, in cover widths.
                                    val info = state.layoutInfo
                                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                    val d = item?.let { (it.offset + it.size / 2f - center) / it.size } ?: 0f
                                    val c = d.coerceIn(-1.6f, 1.6f)
                                    rotationY = (-c * 55f).coerceIn(-62f, 62f)
                                    translationX = -kotlin.math.sign(c) * minOf(abs(c), 1f) * size.width * 0.3f
                                    // The centre cover is full size (never bigger than its slot, so it never covers Play).
                                    val sc = 1f - minOf(abs(c), 1f) * 0.2f
                                    scaleX = sc; scaleY = sc
                                    cameraDistance = 14f * density
                                }
                                .drawWithContent {
                                    // The cover, then its mirror image fading into the floor below it.
                                    val layer = reflection!!
                                    layer.record { this@drawWithContent.drawContent() }
                                    drawLayer(layer)
                                    withTransform({ scale(1f, -1f, pivot = androidx.compose.ui.geometry.Offset(0f, size.height)) }) {
                                        drawLayer(layer)
                                    }
                                    // A short, faint reflection: the cover is the point, not the floor.
                                    drawRect(
                                        Brush.verticalGradient(0f to floor.copy(alpha = 0.7f), 0.22f to floor, startY = size.height, endY = size.height * 2),
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height), size = size,
                                    )
                                }
                            else Modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (focused) 1f else 0.7f }
                        )
                        .tappable(RoundedCornerShape(corner * 0.7f), onLongClick = { onLongPress(g) }) {
                            // Tapping the centered game opens its page; tapping another one brings it to the center.
                            if (pressedWhileMoving) pressedWhileMoving = false
                            // Only a settled, centered cover opens; while anything moves a tap just aims.
                            else if (focused && !state.isScrollInProgress) nav.navigate(Routes.game(g.id, "home"))
                            else { tapJob?.cancel(); tapJob = scope.launch { state.centerOn(index) } }
                        },
                    sharedKey = "cover-home-${g.id}",
                    ratio = ratio,
                    compact = true,
                )
            }
        }
    }
}

/** Scrolls so the item's center lands exactly on the viewport's center, whatever the padding or item sizes. */
private suspend fun LazyListState.centerOn(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    animateScrollBy((item.offset + item.size / 2 - center).toFloat(), spring(dampingRatio = 1f, stiffness = 700f))
}

/** Vertical alternative to the cover carousel: a wheel of rows, the focused one in the middle. */
@Composable
private fun GameWheel(games: List<Game>, state: LazyListState, selectedIndex: Int, modifier: Modifier, onLongPress: (Game) -> Unit) {
    val nav = LocalNav.current
    val user = LocalUserData.current
    val scope = rememberCoroutineScope()
    var tapJob by remember { mutableStateOf<Job?>(null) }
    // A touch that lands while the list is still sliding only stops it; it isn't a tap.
    var pressedWhileMoving by remember { mutableStateOf(false) }
    val catchMoving = Modifier.pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            pressedWhileMoving = state.isScrollInProgress && tapJob?.isActive != true
        }
    }
    val rowHeight = 76.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pad = ((maxHeight - rowHeight) / 2).coerceAtLeast(0.dp)
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = pad, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Rows fade out toward the edges instead of being cut off under the Play button.
            modifier = Modifier.fillMaxSize().then(catchMoving)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, g ->
                val focused = index == selectedIndex
                val scale by animateFloatAsState(if (focused) 1f else 0.92f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "w")
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (focused) 1f else 0.55f }
                        .then(if (focused) Modifier.styledSurface(shape, strong = true) else Modifier)
                        .tappable(shape, onLongClick = { onLongPress(g) }) {
                            if (pressedWhileMoving) pressedWhileMoving = false
                            // Only a settled, centered cover opens; while anything moves a tap just aims.
                            else if (focused && !state.isScrollInProgress) nav.navigate(Routes.game(g.id, "home"))
                            else { tapJob?.cancel(); tapJob = scope.launch { state.centerOn(index) } }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameCover(g, Modifier.height(rowHeight - 12.dp), ratio = 0.75f, compact = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(gameTitle(g), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val last = user.lastPlayed[g.id] ?: 0L
                        Text(
                            listOf(systemName(g.system), if (last > 0) timeAgo(last) else "Not played yet").joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shelf: games stand on a shelf spine-out, using their real spine art when there is some. The game
 * in focus turns out of the shelf (its real 3D box when available) and its actual disc or
 * cartridge rises out of it. Tap it to turn it around and read the back.
 */
@Composable
private fun Shelf(games: List<Game>, state: LazyListState, expandedId: String, itemHeight: Dp, onLongPress: (Game) -> Unit) {
    val graph = LocalGraph.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var pressedWhileMoving by remember { mutableStateOf(false) }
    val catchMoving = Modifier.pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            pressedWhileMoving = state.isScrollInProgress && tapJob?.isActive != true
        }
    }
    val caseH = itemHeight * 0.74f
    val spineW = 34.dp
    var flipped by remember(expandedId) { mutableStateOf(false) }
    val expandedIndex = games.indexOfFirst { it.id == expandedId }
    LaunchedEffect(expandedId) {
        delay(320)
        if (!state.isScrollInProgress && expandedIndex >= 0) state.centerOn(expandedIndex)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(itemHeight * 1.12f + 8.dp)) {
        val side = ((maxWidth - spineW) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxHeight().then(catchMoving),
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { index, g ->
                val open = g.id == expandedId
                val art = remember(g.id, open) { graph.shelfArt(g) }
                val coverW = caseH * settings.coverShape.ratio()
                val width by androidx.compose.animation.core.animateDpAsState(if (open) coverW + 18.dp else spineW, spring(dampingRatio = 0.85f, stiffness = 320f), label = "case")
                val turn by animateFloatAsState(if (open && flipped) 180f else 0f, tween(450), label = "flip")
                val lift by animateFloatAsState(if (open && art.media != null && !flipped) 1f else 0f, tween(420, delayMillis = if (open) 250 else 0), label = "media")
                Box(
                    Modifier.width(width).fillMaxHeight()
                        .tappable(RoundedCornerShape(4.dp), onLongClick = { onLongPress(g) }) {
                            when {
                                pressedWhileMoving -> pressedWhileMoving = false
                                open && !state.isScrollInProgress && (art.back != null || flipped) -> flipped = !flipped
                                open && !state.isScrollInProgress -> nav.navigate(Routes.game(g.id, "home"))
                                else -> { tapJob?.cancel(); tapJob = scope.launch { state.centerOn(index) } }
                            }
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // The real disc or cartridge art, rising out of the top of the case.
                    if (lift > 0.01f && art.media != null) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = caseH * 0.3f).size(coverW * 0.86f)
                                .graphicsLayer { translationY = -lift * (itemHeight * 1.12f - caseH).toPx(); alpha = lift },
                        ) {
                            MultiImage(listOf(art.media), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, decodeSize = 500) {}
                        }
                    }
                    Box(Modifier.height(caseH).fillMaxWidth()) {
                        if (width > spineW + 6.dp) {
                            OpenCase(g, art, turn)
                        } else {
                            CaseSpine(g, art.spine)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The case in focus, turned a little so its spine shows: the real 3D box render when there is one,
 * otherwise the front cover with a spine edge. [turn] spins it round to the back cover.
 */
@Composable
private fun OpenCase(g: Game, art: io.github.audxius.pillar.data.AppGraph.ShelfArt, turn: Float) {
    val back = turn > 90f
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationY = if (back) turn - 180f else turn
            cameraDistance = 16f * density
        },
    ) {
        when {
            back && art.back != null -> MultiImage(listOf(art.back), Modifier.fillMaxSize().shadow(10.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit, decodeSize = 700) {}
            art.box3d != null -> MultiImage(listOf(art.box3d), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, decodeSize = 700) {
                FlatCase(g, art.spine)
            }
            else -> FlatCase(g, art.spine)
        }
    }
}

/** Front cover with a slim spine along its left edge, angled slightly toward the viewer. */
@Composable
private fun FlatCase(g: Game, spine: String?) {
    Row(Modifier.fillMaxSize().graphicsLayer { rotationY = 14f; cameraDistance = 16f * density }) {
        Box(Modifier.width(16.dp).fillMaxHeight().graphicsLayer { scaleX = 0.85f }) { CaseSpine(g, spine) }
        GameCover(g, Modifier.weight(1f).fillMaxHeight().shadow(10.dp, RoundedCornerShape(3.dp)), ratio = null, compact = true)
    }
}

/** A case's spine: the real spine scan when there is one, else the console's color with the title. */
@Composable
private fun CaseSpine(g: Game, spineArt: String? = null) {
    val color = systemColor(g.system)
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp))
            .background(Brush.horizontalGradient(listOf(lerp(color, Color.Black, 0.55f), lerp(color, Color.White, 0.08f), lerp(color, Color.Black, 0.4f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp)),
    ) {
        if (spineArt != null) {
            MultiImage(listOf(spineArt), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, decodeSize = 400) { GeneratedSpine(g) }
        } else {
            GeneratedSpine(g)
        }
        // A little shine, like the plastic sleeve.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.15f)))))
    }
}

@Composable
private fun BoxScope.GeneratedSpine(g: Game) {
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(22.dp).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Text(systemName(g.system).take(4).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
    Text(
        gameTitle(g), color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(top = 22.dp).sidewaysText(),
    )
}

/** Lays text out sideways, reading bottom to top like a case spine. */
private fun Modifier.sidewaysText() = layout { m, c ->
    val p = m.measure(androidx.compose.ui.unit.Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
    }
}

/** Channels: pages of wide banners, like the Homebrew Channel. Tap once to select, again to open. */
@Composable
private fun ChannelGrid(games: List<Game>, chosen: Int, onChoose: (Int) -> Unit, modifier: Modifier, onLongPress: (Game) -> Unit) {
    val nav = LocalNav.current
    val corner = LocalCorner.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = 2
        val tileW = (maxWidth - 16.dp * 2 - 10.dp) / cols
        val tileH = tileW * 9f / 16f
        val rows = ((maxHeight - 24.dp) / (tileH + 10.dp)).toInt().coerceAtLeast(1)
        val perPage = rows * cols
        val pages = (games.size + perPage - 1) / perPage
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = chosen / perPage) { pages }
        // Flipping pages selects the first game on the new page.
        LaunchedEffect(pager.currentPage) { if (chosen / perPage != pager.currentPage) onChoose(pager.currentPage * perPage) }
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until cols) {
                            val i = page * perPage + r * cols + c
                            val g = games.getOrNull(i)
                            if (g == null) Spacer(Modifier.width(tileW)) else {
                                val sel = i == chosen
                                val scale by animateFloatAsState(if (sel) 1.05f else 1f, label = "ch")
                                val shape = RoundedCornerShape(corner * 0.6f)
                                Box(
                                    Modifier.width(tileW).height(tileH).graphicsLayer { scaleX = scale; scaleY = scale }
                                        .shadow(if (sel) 10.dp else 2.dp, shape)
                                        .clip(shape)
                                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f), shape)
                                        .tappable(shape, onLongClick = { onLongPress(g) }) { if (sel) nav.navigate(Routes.game(g.id, "home")) else onChoose(i) },
                                ) {
                                    MultiImage(rememberSnapSources(g), Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, decodeSize = 500) {
                                        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(lerp(systemColor(g.system), Color.White, 0.1f), lerp(systemColor(g.system), Color.Black, 0.5f)))))
                                    }
                                    Box(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) { Text(gameTitle(g), color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
            }
            // Page dots.
            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pIdx in 0 until minOf(pages, 12)) {
                    Box(Modifier.size(if (pIdx == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (pIdx == pager.currentPage) 0.9f else 0.3f)))
                }
            }
        }
    }
}

/** The "pick your ROM folder" card, shared with the feed layout. */
@Composable
fun EmptyLibraryCardPublic() {
    val nav = LocalNav.current
    io.github.audxius.pillar.ui.components.EmptyState(
        Icons.Rounded.PlayArrow,
        "Let's find your games",
        "Point Pillar at your ROMs folder (one folder per system, such as snes, gba or psx), or connect RomM to see your whole server here.",
        action = "Choose ROM folder",
        onAction = { nav.navigate(Routes.settingsPage(io.github.audxius.pillar.ui.settings.SettingsPage.LIBRARY)) },
    )
}
