package io.github.audxius.pillar.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

data class ScanState(val running: Boolean = false, val message: String = "", val found: Int = 0)

enum class MediaKind(val folders: List<String>) {
    COVER(listOf("covers", "3dboxes", "miximages")),
    HERO(listOf("fanart", "screenshots", "titlescreens")),
    LOGO(listOf("marquees")),
}

class LibraryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val catalog: SystemCatalog,
    private val store: SettingsStore,
) {
    private val cacheFile = File(context.filesDir, "library.json")
    private val scanMutex = Mutex()

    /** Games as scanned, titled from their file names. */
    @Volatile private var scanned: List<Game> = emptyList()
    @Volatile private var names: Map<String, String> = emptyMap()
    private val _scannedFlow = MutableStateFlow<List<Game>>(emptyList())
    val scannedGames: StateFlow<List<Game>> = _scannedFlow.asStateFlow()
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    /** Games with the names they were identified by, when known. */
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    private fun setScanned(list: List<Game>) {
        scanned = list
        _scannedFlow.value = list
        publish()
    }

    /** Applies names found by checksum (game id -> official name with tags). */
    fun applyNames(map: Map<String, String>) {
        names = map
        publish()
    }

    private fun publish() {
        val n = names
        _games.value = scanned.map { g -> n[g.id]?.let { g.copy(title = cleanTitle("$it.x"), datName = it) } ?: g }
            .sortedBy { it.title.lowercase() }
    }

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** system -> media folder -> lowercase base name -> document uri */
    @Volatile private var media: Map<String, Map<String, Map<String, String>>> = emptyMap()
    private val _mediaVersion = MutableStateFlow(0)
    val mediaVersion: StateFlow<Int> = _mediaVersion.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val cached = runCatching {
                PillarJson.decodeFromString(ListSerializer(Game.serializer()), cacheFile.readText())
            }.getOrNull()
            if (cached != null) setScanned(cached)
            indexMedia()
            // Show the cached library instantly, then pick up files added since last time.
            if (store.settings.value.scanRoots.isNotEmpty()) scan()
        }
    }

    fun gameById(id: String): Game? = _games.value.firstOrNull { it.id == id }

    fun systemsWithGames(): Map<String, List<Game>> = _games.value.groupBy { it.system }

    fun scanAsync() {
        scope.launch(Dispatchers.IO) { scan() }
    }

    suspend fun scan() = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = store.settings.value
            _scan.value = ScanState(true, "Looking for systems…", 0)
            val resolver = context.contentResolver
            val found = ArrayList<Game>()
            for (root in settings.scanRoots) {
                val tree = Uri.parse(root)
                val rootId = runCatching { Saf.rootDocId(tree) }.getOrNull() ?: continue
                val rootPath = Saf.realPath(tree, rootId)
                val rootName = Saf.displayName(resolver, tree, rootId)
                // The picked folder can be the ROMs root or a single system folder.
                val rootSystem = rootName?.let { catalog.resolveFolder(it) }
                if (rootSystem != null) {
                    scanSystem(tree, rootId, rootSystem, rootPath?.substringBeforeLast('/'), found, settings)
                } else {
                    for (child in Saf.list(resolver, tree, rootId)) {
                        if (!child.isDir) continue
                        val system = catalog.resolveFolder(child.name) ?: continue
                        _scan.value = ScanState(true, "Scanning ${catalog.meta(system).short}…", found.size)
                        scanSystem(tree, child.docId, system, rootPath, found, settings)
                    }
                }
            }
            val distinct = found.distinctBy { it.id }.sortedBy { it.title.lowercase() }
            setScanned(distinct)
            runCatching {
                cacheFile.writeText(PillarJson.encodeToString(ListSerializer(Game.serializer()), distinct))
            }
            indexMedia()
            _scan.value = ScanState(false, "Found ${distinct.size} games", distinct.size)
        }
    }

    /** Rescans a single system folder, used after a RomM download lands. */
    suspend fun rescanSystem(system: String) = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = store.settings.value
            val resolver = context.contentResolver
            val found = ArrayList<Game>()
            for (root in settings.scanRoots) {
                val tree = Uri.parse(root)
                val rootId = runCatching { Saf.rootDocId(tree) }.getOrNull() ?: continue
                val rootPath = Saf.realPath(tree, rootId)
                val rootName = Saf.displayName(resolver, tree, rootId)
                if (rootName != null && catalog.resolveFolder(rootName) == system) {
                    scanSystem(tree, rootId, system, rootPath?.substringBeforeLast('/'), found, settings)
                } else {
                    Saf.list(resolver, tree, rootId).filter { it.isDir && catalog.resolveFolder(it.name) == system }
                        .forEach { scanSystem(tree, it.docId, system, rootPath, found, settings) }
                }
            }
            val merged = (scanned.filter { it.system != system } + found)
                .distinctBy { it.id }.sortedBy { it.title.lowercase() }
            setScanned(merged)
            runCatching { cacheFile.writeText(PillarJson.encodeToString(ListSerializer(Game.serializer()), merged)) }
        }
    }

    private fun scanSystem(
        tree: Uri,
        dirId: String,
        system: String,
        rootPath: String?,
        out: MutableList<Game>,
        settings: AppSettings,
    ) {
        val def = catalog[system] ?: return
        walk(tree, dirId, def, rootPath, out, settings, depth = 0)
    }

    private fun walk(
        tree: Uri,
        dirId: String,
        def: SystemDef,
        rootPath: String?,
        out: MutableList<Game>,
        settings: AppSettings,
        depth: Int,
    ) {
        val entries = Saf.list(context.contentResolver, tree, dirId)
        val files = entries.filter { !it.isDir && !it.name.startsWith('.') && ext(it.name) in def.extensions }
        val hidden = if (settings.hideDiscTracks) discTrackNames(files) else emptySet()
        for (f in files) {
            if (f.name in hidden) continue
            out += Game(
                id = f.docId,
                system = def.id,
                title = cleanTitle(f.name),
                fileName = f.name,
                docUri = Saf.docUri(tree, f.docId).toString(),
                treeUri = tree.toString(),
                path = Saf.realPath(tree, f.docId),
                rootPath = rootPath,
                size = f.size,
                modified = f.modified,
            )
        }
        if (depth >= 3) return
        for (d in entries) {
            if (!d.isDir || d.name.startsWith('.') || d.name.lowercase() in SKIP_DIRS) continue
            walk(tree, d.docId, def, rootPath, out, settings, depth + 1)
        }
    }

    /**
     * Hides the pieces of multi-file games so each game shows once: .bin tracks behind a .cue,
     * and individual discs behind an .m3u playlist.
     */
    private fun discTrackNames(files: List<DocEntry>): Set<String> {
        val hidden = HashSet<String>()
        val cues = files.filter { ext(it.name) in setOf("cue", "gdi", "ccd") }.map { stem(it.name) }
        val m3us = files.filter { ext(it.name) == "m3u" }.map { stem(it.name) }
        for (f in files) {
            val e = ext(f.name)
            val s = stem(f.name)
            if (e in setOf("bin", "img", "sub", "raw", "wav") && cues.any { s.startsWith(it, ignoreCase = true) }) hidden += f.name
            if (e != "m3u" && m3us.any { s.startsWith(it, ignoreCase = true) && s.contains("disc", ignoreCase = true) }) hidden += f.name
        }
        return hidden
    }

    // ---- Media (ES-DE downloaded_media layout) ------------------------------------------------

    private fun indexMedia() {
        val root = store.settings.value.mediaRoot ?: run { media = emptyMap(); _mediaVersion.value++; return }
        val tree = Uri.parse(root)
        val resolver = context.contentResolver
        val rootId = runCatching { Saf.rootDocId(tree) }.getOrNull() ?: return
        val systems = _games.value.map { it.system }.toSet()
        val out = HashMap<String, Map<String, Map<String, String>>>()
        for (sysDir in Saf.list(resolver, tree, rootId)) {
            if (!sysDir.isDir) continue
            val system = catalog.resolveFolder(sysDir.name) ?: continue
            if (system !in systems) continue
            val byFolder = HashMap<String, Map<String, String>>()
            for (typeDir in Saf.list(resolver, tree, sysDir.docId)) {
                if (!typeDir.isDir) continue
                val type = typeDir.name.lowercase()
                if (MediaKind.entries.none { type in it.folders }) continue
                byFolder[type] = Saf.list(resolver, tree, typeDir.docId)
                    .filter { !it.isDir }
                    .associate { stem(it.name).lowercase() to Saf.docUri(tree, it.docId).toString() }
            }
            out[system] = byFolder
        }
        media = out
        _mediaVersion.value++
    }

    fun reindexMediaAsync() {
        scope.launch(Dispatchers.IO) { indexMedia() }
    }

    private fun mediaFor(game: Game, kind: MediaKind): List<String> {
        val bySystem = media[game.system] ?: return emptyList()
        val key = game.baseName.lowercase()
        return kind.folders.mapNotNull { bySystem[it]?.get(key) }
    }

    /**
     * Ordered image sources for a game tile; the UI tries each until one loads. The user's cover
     * style decides whether box art, SteamGridDB grids or square icons come first.
     */
    fun coverCandidates(game: Game, settings: AppSettings, user: UserData, art: ArtSet?): List<String> = buildList {
        user.customCover[game.id]?.let(::add)
        game.remoteCover?.let(::add)
        when (settings.coverStyle) {
            CoverStyle.SQUARE -> art?.sgdbSquare?.let(::add)
            CoverStyle.GRID -> art?.sgdbGrid?.let(::add)
            CoverStyle.BOXART -> {
                // Real boxes that don't fit the tile (square GBA boxes in 3:4 tiles, wide SNES ones)
                // would be framed; SteamGridDB's covers come in the tile's shape, so use those first.
                val tile = when (settings.coverShape) {
                    CoverShape.BOX -> 0.75f
                    CoverShape.SQUARE -> 1f
                    CoverShape.WIDE -> null
                    CoverShape.NATURAL -> null
                }
                if (tile != null && kotlin.math.abs(kotlin.math.ln(catalog.boxRatio(game.system) / tile)) >= 0.16f) {
                    (if (tile == 1f) art?.sgdbSquare else art?.sgdbGrid)?.let(::add)
                }
            }
        }
        addAll(mediaFor(game, MediaKind.COVER).take(1))
        user.remoteCover[game.id]?.let(::add)
        if (settings.onlineCovers) {
            when (settings.onlineArt) {
                OnlineArt.BOXART -> art?.boxart?.let(::add)
                OnlineArt.SNAP -> art?.snap?.let(::add)
                OnlineArt.TITLE -> art?.boxart?.let { add(it.replace("/Named_Boxarts/", "/Named_Titles/")) }
            }
            // Until the fuzzy lookup has run, try the exact file name.
            if (art?.libretroChecked != true) libretroUrl(game, settings.onlineArt)?.let(::add)
        }
        art?.sgdbGrid?.let(::add)
        art?.sgdbSquare?.let(::add)
    }.distinct()

    /** Wide background art for heroes and game pages. */
    fun heroCandidates(game: Game, settings: AppSettings, user: UserData, art: ArtSet?): List<String> = buildList {
        art?.sgdbHero?.let(::add)
        addAll(mediaFor(game, MediaKind.HERO))
        if (settings.onlineCovers) {
            art?.snap?.let(::add)
            if (art?.libretroChecked != true) libretroUrl(game, OnlineArt.SNAP)?.let(::add)
        }
        addAll(coverCandidates(game, settings, user, art))
    }.distinct()

    /** Transparent title logo: ES-DE marquee first, then SteamGridDB. */
    fun logo(game: Game, art: ArtSet?): String? = mediaFor(game, MediaKind.LOGO).firstOrNull() ?: art?.sgdbLogo

    fun libretroUrl(game: Game, art: OnlineArt): String? {
        val system = catalog.meta(game.system).libretro ?: return null
        val folder = when (art) {
            OnlineArt.BOXART -> "Named_Boxarts"
            OnlineArt.TITLE -> "Named_Titles"
            OnlineArt.SNAP -> "Named_Snaps"
        }
        val name = game.baseName.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
        return "https://thumbnails.libretro.com/${Uri.encode(system)}/$folder/${Uri.encode(name)}.png"
    }

    companion object {
        private val SKIP_DIRS = setOf(
            "media", "images", "videos", "manuals", "downloaded_images", "downloaded_media",
            "screenshots", "covers", "snaps", "boxart", "artwork",
        )

        fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
        fun stem(name: String) = name.substringBeforeLast('.', name)

        /** "Legend of Zelda, The - A Link to the Past (USA) [!].sfc" -> "The Legend of Zelda - A Link to the Past" */
        fun cleanTitle(fileName: String): String {
            var t = stem(fileName)
            t = t.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace('_', ' ').trim()
            val m = Regex("^(.*?), (The|A|An)(\\s+-.*|:.*)?$", RegexOption.IGNORE_CASE).find(t)
            if (m != null) t = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
            // "Pokemon - FireRed Version" reads better as "Pokemon: FireRed Version".
            t = t.replaceFirst(" - ", ": ")
            return t.replace(Regex("\\s{2,}"), " ").ifBlank { stem(fileName) }
        }

        /** Region/version tags from the file name, e.g. ["USA", "Rev 1"]. */
        fun tags(fileName: String): List<String> =
            Regex("[(\\[]([^)\\]]+)[)\\]]").findAll(stem(fileName)).map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() && it != "!" }.toList()
    }
}
