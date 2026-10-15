@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.github.audxius.pillar.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.audxius.pillar.data.AppGraph
import io.github.audxius.pillar.data.EmuCommand
import io.github.audxius.pillar.data.Game
import io.github.audxius.pillar.data.LaunchResult
import io.github.audxius.pillar.ui.components.LocalNavScope
import io.github.audxius.pillar.ui.components.LocalSharedScope
import io.github.audxius.pillar.ui.components.PillarBackground
import io.github.audxius.pillar.ui.components.tappable
import io.github.audxius.pillar.ui.screens.AchievementsScreen
import io.github.audxius.pillar.ui.screens.GameDetailScreen
import io.github.audxius.pillar.ui.screens.HomeScreen
import io.github.audxius.pillar.ui.screens.LibraryScreen
import io.github.audxius.pillar.ui.screens.OnboardingScreen
import io.github.audxius.pillar.ui.screens.PlatformScreen
import io.github.audxius.pillar.ui.screens.SavesScreen
import io.github.audxius.pillar.ui.screens.SearchScreen
import io.github.audxius.pillar.ui.settings.SettingsPage
import io.github.audxius.pillar.ui.settings.SettingsScreen
import io.github.audxius.pillar.ui.theme.LocalGraph
import io.github.audxius.pillar.ui.theme.LocalIsDark
import io.github.audxius.pillar.ui.theme.LocalSettings
import io.github.audxius.pillar.ui.theme.LocalUserData
import io.github.audxius.pillar.ui.theme.PillarTheme
import io.github.audxius.pillar.ui.theme.styledSurface
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val RA = "ra"
    const val SAVES = "saves"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val ONBOARDING = "onboarding"
    fun platform(system: String) = "platform/${Uri.encode(system)}"
    fun game(id: String, src: String = "x") = "game?id=${Uri.encode(id)}&src=${Uri.encode(src)}"
    fun settingsPage(page: SettingsPage) = "settings/${page.name}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val color: Color)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home, Color(0xFFFF9F43)),
    Tab(Routes.LIBRARY, "Library", Icons.Rounded.GridView, Color(0xFF4D9DFF)),
    Tab(Routes.SAVES, "Saves", Icons.Rounded.Save, Color(0xFF9B7BFF)),
    Tab(Routes.RA, "Trophies", Icons.Rounded.EmojiEvents, Color(0xFFFFC542)),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Tune, Color(0xFFFF6B9A)),
)

val LocalNav = compositionLocalOf<NavHostController> { error("No nav") }
val LocalSnackbar = compositionLocalOf { SnackbarHostState() }

private val BottomBarHeight = 118.dp

/** Space screens leave at the bottom so content can scroll above the floating bar and the system nav bar under it. */
val BottomBarSpace: Dp
    @Composable get() = BottomBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun PillarRoot(graph: AppGraph) {
    val settings by graph.store.settings.collectAsStateWithLifecycle()
    val user by graph.store.userData.collectAsStateWithLifecycle()
    PillarTheme(graph, settings, user) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showBar = route in Tabs.map { it.route }
        val dark = LocalIsDark.current
        // Decided once so finishing onboarding doesn't rebuild the nav graph underneath us.
        val startRoute = remember { if (settings.onboarded) Routes.HOME else Routes.ONBOARDING }

        val view = LocalView.current
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }

        CompositionLocalProvider(LocalNav provides nav, LocalSnackbar provides snackbar) {
            Box(Modifier.fillMaxSize()) {
                PillarBackground()
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedScope provides this) {
                        PillarNavHost(nav, start = startRoute)
                    }
                }
                // Keeps the clock and icons readable over content scrolling underneath the status bar.
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val bg = MaterialTheme.colorScheme.background
                Box(
                    Modifier.fillMaxWidth().height(statusTop + 14.dp)
                        .background(Brush.verticalGradient(0f to bg.copy(alpha = 0.92f), 0.65f to bg.copy(alpha = 0.6f), 1f to Color.Transparent))
                )
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BottomBar(route) { target ->
                        if (target == route) return@BottomBar
                        nav.navigate(target) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = if (showBar) BottomBarHeight - 20.dp else 16.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PillarNavHost(nav: NavHostController, start: String) {
    val animations = LocalSettings.current.animations
    val enter = if (animations) fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f) else fadeIn(tween(0))
    val exit = if (animations) fadeOut(tween(200)) else fadeOut(tween(0))
    val popEnter = if (animations) fadeIn(tween(260)) else fadeIn(tween(0))
    val popExit = if (animations) fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.97f) else fadeOut(tween(0))

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit },
    ) {
        fun scoped(content: @Composable () -> Unit): @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = {
            CompositionLocalProvider(LocalNavScope provides this) { content() }
        }
        composable(Routes.ONBOARDING, content = scoped { OnboardingScreen() })
        composable(Routes.HOME, content = scoped { HomeScreen() })
        composable(Routes.LIBRARY, content = scoped { LibraryScreen() })
        composable(Routes.RA, content = scoped { AchievementsScreen() })
        composable(Routes.SAVES, content = scoped { SavesScreen() })
        composable(Routes.SETTINGS, content = scoped { SettingsScreen(null) })
        composable(Routes.SEARCH, content = scoped { SearchScreen() })
        composable(
            "settings/{page}",
            arguments = listOf(navArgument("page") { type = NavType.StringType }),
        ) { entry ->
            val page = runCatching { SettingsPage.valueOf(entry.arguments?.getString("page").orEmpty()) }.getOrNull()
            CompositionLocalProvider(LocalNavScope provides this) { SettingsScreen(page) }
        }
        composable(
            "platform/{system}",
            arguments = listOf(navArgument("system") { type = NavType.StringType }),
        ) { entry ->
            CompositionLocalProvider(LocalNavScope provides this) {
                PlatformScreen(entry.arguments?.getString("system").orEmpty())
            }
        }
        composable(
            "game?id={id}&src={src}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("src") { type = NavType.StringType; defaultValue = "x" },
            ),
        ) { entry ->
            CompositionLocalProvider(LocalNavScope provides this) {
                GameDetailScreen(entry.arguments?.getString("id").orEmpty(), entry.arguments?.getString("src") ?: "x")
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    val settings = LocalSettings.current
    val dark = LocalIsDark.current
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .styledSurface(shape, strong = true)
            .background(MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.55f else 0.35f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tabs.forEach { tab ->
            val selected = current == tab.route
            // Each tab has its own color, like a handheld's home-menu dock.
            val bubble by animateColorAsState(if (selected) tab.color else tab.color.copy(alpha = 0.14f), label = "tabBubble")
            val glyph by animateColorAsState(if (selected) Color.White else tab.color, label = "tabGlyph")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .tappable(RoundedCornerShape(20.dp)) { onSelect(tab.route) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(bubble)
                        .then(if (selected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(tab.icon, tab.label, tint = glyph, modifier = Modifier.size(22.dp)) }
                if (settings.navLabels) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Launches a game with its configured emulator, reporting problems in the snackbar. */
@Composable
fun rememberGameLauncher(): (Game, EmuCommand?) -> Unit {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val user = LocalUserData.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember(settings, user) {
        play@{ game: Game, override: EmuCommand? ->
            if (game.remote) {
                // Not on this device yet: download from RomM, then it starts by itself.
                graph.downloadAndPlay(game)
                scope.launch { snackbar.showSnackbar("Downloading ${game.title}. It will start when it's ready.") }
                return@play
            }
            val cmd = override ?: graph.launcher.selectCommand(game, settings, user)
            if (cmd == null) {
                scope.launch { snackbar.showSnackbar("No emulator is set up for ${graph.catalog.fullName(game.system)}") }
            } else {
                scope.launch {
                    // Never start a game while its save might still be downloading.
                    graph.saveSync.awaitIdle()
                    when (val r = graph.launcher.launch(game, cmd)) {
                        LaunchResult.Started -> graph.markPlayed(game)
                        is LaunchResult.Failed -> snackbar.showSnackbar(r.message)
                    }
                }
            }
        }
    }
}
