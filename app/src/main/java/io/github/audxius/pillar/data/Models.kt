package io.github.audxius.pillar.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val PillarJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

@Serializable enum class ThemeMode { SYSTEM, DARK, AMOLED, LIGHT }
@Serializable enum class AppFont { NUNITO, OUTFIT, SPACE_GROTESK, SYSTEM, PIXEL, EXO }
@Serializable enum class BackgroundStyle { STYLE, ART, AURORA, GRADIENT, SOLID, IMAGE }
@Serializable enum class UiStyle { GLASS, AERO, Y2K, XMB, WII, LUNA }
@Serializable enum class HomeLayout { CAROUSEL, FEED }

/** Which artwork fills game tiles. */
@Serializable enum class CoverStyle { BOXART, GRID, SQUARE }
@Serializable enum class CoverShape { NATURAL, BOX, SQUARE, WIDE }
@Serializable enum class LibraryLayout { GRID, LIST }
@Serializable enum class PlatformLayout { CARDS, GRID, LIST }
@Serializable enum class OrientationMode { PORTRAIT, AUTO, LANDSCAPE }
@Serializable enum class SortMode { NAME, RECENTLY_PLAYED, RECENTLY_ADDED, MOST_PLAYED }
@Serializable enum class OnlineArt { BOXART, TITLE, SNAP }

@Serializable
enum class WidgetType(val title: String, val description: String) {
    HEADER("Clock & greeting", "Time, date, battery and a greeting"),
    CONTINUE("Continue playing", "Big swipeable cards of your recent games"),
    PLATFORMS("Platforms", "Your consoles as a scrolling row"),
    FAVORITES("Favorites", "Games you starred"),
    RA_PROFILE("RetroAchievements profile", "Points, presence and avatar"),
    // Removed in 0.5; kept only so older saved layouts still load.
    RA_RECENT("Recent achievements", "Removed"),
    RA_PLAYED("RetroAchievements progress", "Completion of recently played games"),
    RECENTLY_ADDED("Recently added", "Newest files in your library"),
    ROMM_LATEST("New on RomM", "Latest additions on your RomM server"),
    RANDOM("Random pick", "Can't decide? Let Pillar choose"),
    // Removed in 0.2 (Pillar is not a launcher); kept only so older saved layouts still load.
    QUICK_APPS("Quick apps", "Removed"),
    STATS("Library stats", "Counts across your collection"),
}

@Serializable
data class WidgetConfig(val type: WidgetType, val enabled: Boolean = true)

/** Widgets that no longer exist; dropped from saved layouts. */
val RemovedWidgets = setOf(WidgetType.QUICK_APPS, WidgetType.RA_RECENT)

val DefaultWidgets = listOf(
    WidgetConfig(WidgetType.HEADER),
    WidgetConfig(WidgetType.CONTINUE),
    WidgetConfig(WidgetType.PLATFORMS),
    WidgetConfig(WidgetType.RA_PROFILE),
    WidgetConfig(WidgetType.FAVORITES),
    WidgetConfig(WidgetType.RECENTLY_ADDED),
    WidgetConfig(WidgetType.ROMM_LATEST),
    WidgetConfig(WidgetType.RA_PLAYED, enabled = false),
    WidgetConfig(WidgetType.RANDOM),
    WidgetConfig(WidgetType.STATS, enabled = false),
)

@Serializable
data class SystemOverride(
    val displayName: String? = null,
    val color: Long? = null,
)

/** A user-defined launch command, written in the same syntax as ES-DE's es_systems.xml. */
@Serializable
data class CustomCommand(val label: String, val command: String)

@Serializable
data class AppSettings(
    val onboarded: Boolean = false,
    /** Settings format version, used to move older installs onto new defaults. */
    val schema: Int = 0,

    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,
    val palette: String = "aurora",
    val customAccent: Long? = null,
    val dynamicColor: Boolean = false,
    val font: AppFont = AppFont.NUNITO,
    val textScale: Float = 1f,
    val cornerRadius: Int = 22,
    val uiStyle: UiStyle = UiStyle.GLASS,
    val background: BackgroundStyle = BackgroundStyle.STYLE,
    val backgroundImageUri: String? = null,
    val backgroundBlur: Int = 12,
    val backgroundDim: Float = 0.5f,
    val animateBackground: Boolean = true,
    val ambientColor: Boolean = true,
    val glassCards: Boolean = true,
    val gridColumns: Int = 3,
    val coverShape: CoverShape = CoverShape.BOX,
    /** Replace titles with game logos (ES-DE marquees / SteamGridDB) where available. */
    val showLogos: Boolean = true,
    /** Show RomM games that aren't on this device in the library, ready to download. */
    val rommInLibrary: Boolean = true,
    val coverStyle: CoverStyle = CoverStyle.BOXART,
    /** Crop art to fill tiles instead of showing all of it over a blurred backdrop. */
    val coverCrop: Boolean = false,
    val homeLayout: HomeLayout = HomeLayout.CAROUSEL,
    /** Home browses games in a vertical list of titles instead of the sideways cover carousel. */
    val homeVertical: Boolean = false,
    /** Each style's own background music. */
    val themeMusic: Boolean = false,
    val sgdbKey: String = "",
    val showGameTitles: Boolean = true,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val platformLayout: PlatformLayout = PlatformLayout.CARDS,
    val sortMode: SortMode = SortMode.NAME,
    val navLabels: Boolean = true,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val uiSounds: Boolean = false,
    val orientation: OrientationMode = OrientationMode.PORTRAIT,

    // Home
    val homeWidgets: List<WidgetConfig> = DefaultWidgets,
    val greetingName: String = "",
    val clock24h: Boolean = true,
    val showBattery: Boolean = true,

    // Library
    val romRoots: List<String> = emptyList(),
    val mediaRoot: String? = null,
    val downloadRoot: String? = null,
    val hiddenSystems: Set<String> = emptySet(),
    val systemOrder: List<String> = emptyList(),
    val systemOverrides: Map<String, SystemOverride> = emptyMap(),
    val defaultEmulators: Map<String, String> = emptyMap(),
    val customCommands: Map<String, List<CustomCommand>> = emptyMap(),
    val showEmptySystems: Boolean = false,
    val onlineCovers: Boolean = true,
    val onlineArt: OnlineArt = OnlineArt.BOXART,
    val hideDiscTracks: Boolean = true,
    val confirmLaunch: Boolean = false,

    // RomM
    val rommUrl: String = "",
    val rommUser: String = "",
    val rommPassword: String = "",
    val rommToken: String = "",

    // Save sync (WebDAV, compatible with RetroArch Cloud Sync)
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val savesDir: String? = null,
    val statesDir: String? = null,
    val syncOnOpen: Boolean = true,
    /** Show a small sync indicator over games while saves sync in the background. */
    val syncOverlay: Boolean = false,

    // RetroAchievements
    val raUser: String = "",
    val raApiKey: String = "",
    val raMatchTitles: Boolean = true,
) {
    val rommConfigured get() = rommUrl.isNotBlank() && (rommToken.isNotBlank() || rommUser.isNotBlank())
    val raConfigured get() = raUser.isNotBlank() && raApiKey.isNotBlank()

    /** ROM folders plus a separate RomM download folder, so downloads always show up. */
    val scanRoots get() = (romRoots + listOfNotNull(downloadRoot)).distinct()

    /** Adds widgets introduced in newer versions so old saved layouts still show them in settings. */
    fun normalized(): AppSettings {
        val kept = homeWidgets.filter { it.type !in RemovedWidgets }
        val known = kept.map { it.type }.toSet()
        val missing = WidgetType.entries.filter { it !in known && it !in RemovedWidgets }.map { WidgetConfig(it, enabled = false) }
        var s = copy(homeWidgets = kept + missing)
        // 0.2: game-art background replaced the aurora as the default look.
        if (s.schema < 2) s = s.copy(schema = 2, background = if (s.background == BackgroundStyle.AURORA) BackgroundStyle.ART else s.background)
        // 0.3: styles own the backdrop, covers keep their real shape, the Glass look is the new default.
        if (s.schema < 3) s = s.copy(
            schema = 3,
            background = if (s.background == BackgroundStyle.ART || s.background == BackgroundStyle.AURORA) BackgroundStyle.STYLE else s.background,
            font = if (s.font == AppFont.NUNITO) AppFont.OUTFIT else s.font,
            cornerRadius = if (s.cornerRadius == 22) 24 else s.cornerRadius,
        )
        return s
    }
}

/** A game file found in one of the user's ROM folders. */
@Serializable
data class Game(
    val id: String,
    val system: String,
    val title: String,
    val fileName: String,
    val docUri: String,
    val treeUri: String,
    val path: String? = null,
    val rootPath: String? = null,
    val size: Long = 0,
    val modified: Long = 0,
    /** Set for games that live on the RomM server and aren't on this device yet. */
    val rommId: Int? = null,
    val remoteCover: String? = null,
    /** Official name found by checksum (No-Intro, or RetroAchievements), e.g. "Sonic & Knuckles + Sonic The Hedgehog 3 (World)". */
    val datName: String? = null,
) {
    val baseName: String get() = fileName.substringBeforeLast('.', fileName)
    val remote: Boolean get() = docUri.isEmpty() && rommId != null
}

/** Everything the user does to games, kept separate from the scan cache so rescans never lose it. */
@Serializable
data class UserData(
    val favorites: Set<String> = emptySet(),
    val lastPlayed: Map<String, Long> = emptyMap(),
    val playCount: Map<String, Int> = emptyMap(),
    val emulatorOverride: Map<String, String> = emptyMap(),
    val customCover: Map<String, String> = emptyMap(),
    val customTitle: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val raIds: Map<String, Int> = emptyMap(),
    val rommIds: Map<String, Int> = emptyMap(),
    val remoteCover: Map<String, String> = emptyMap(),
)
