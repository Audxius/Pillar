package io.github.audxius.pillar.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import io.github.audxius.pillar.BuildConfig
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready<T>)?.value

/** Wires every repository together; created once by [io.github.audxius.pillar.PillarApp]. */
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Pillar/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()

    val store = SettingsStore(context, scope)
    val catalog = SystemCatalog(context)
    val library = LibraryRepository(context, scope, catalog, store)
    val launcher = GameLauncher(context, catalog)
    val romm = RommClient(http) { store.settings.value }
    val raClient = RaClient(context, http) { store.settings.value }
    val ra = RaRepository(context, raClient, store, catalog, scope)
    val identifier = GameIdentifier(context, http, catalog, scope)
    val rommRepo = RommRepository(context, romm, store, catalog, scope)
    val downloads = Downloads(context, http, romm, catalog, store, library)
    val artwork = ArtworkRepository(context, http, catalog, store, scope)
    val saveSync = SaveSync(context, http, store, scope)

    /**
     * The one library: games on this device plus RomM games that aren't downloaded yet. A RomM game
     * already on the device (linked by a download, or same system and file name) shows once.
     */
    val allGames: StateFlow<List<Game>> = kotlinx.coroutines.flow.combine(
        library.games, rommRepo.games, store.userData, store.settings,
    ) { local, catalogRoms, user, s ->
        if (!s.rommInLibrary || !s.rommConfigured || catalogRoms.isEmpty()) return@combine local
        // Only links whose local file still exists hide the RomM entry.
        val localIds = local.mapTo(HashSet()) { it.id }
        val linked = user.rommIds.filterKeys { it in localIds }.values.toSet()
        val localKeys = HashSet<String>()
        local.forEach { g ->
            localKeys += g.system + "/" + g.fileName.lowercase()
            localKeys += g.system + "/" + g.baseName.lowercase()
        }
        val remote = catalogRoms.mapNotNull { rom ->
            if (rom.id in linked) return@mapNotNull null
            val system = catalog.resolveRommPlatform(rom.platformSlug, rom.platformFsSlug)
                ?: rom.platformFsSlug?.takeIf { it.isNotBlank() } ?: rom.platformSlug ?: "romm"
            val name = rom.fsName.lowercase()
            if ("$system/$name" in localKeys || "$system/${name.substringBeforeLast('.')}" in localKeys) return@mapNotNull null
            Game(
                id = "romm:${rom.id}",
                system = system,
                title = rom.title,
                fileName = rom.fsName,
                docUri = "",
                treeUri = "",
                size = rom.fsSizeBytes,
                modified = rom.createdAt?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0,
                rommId = rom.id,
                remoteCover = romm.coverUrl(rom),
            )
        }
        (local + remote).sortedBy { it.title.lowercase() }
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun gameById(id: String): Game? = allGames.value.firstOrNull { it.id == id } ?: library.gameById(id)
        // A RomM entry that has just been downloaded now lives under its local id.
        ?: id.removePrefix("romm:").takeIf { id.startsWith("romm:") }?.toIntOrNull()?.let { rid ->
            localFor(rid) ?: (downloads.states.value[rid] as? DownloadState.Done)?.gameId?.let(library::gameById)
        }

    /** The local copy of a RomM game, once it has been downloaded. */
    fun localFor(rommId: Int): Game? {
        val user = store.userData.value
        return library.games.value.firstOrNull { user.rommIds[it.id] == rommId }
    }

    /** Starts a RomM download and launches the game as soon as it lands. */
    fun downloadAndPlay(game: Game) {
        val rom = game.rommId?.let { rommRepo.rom(it) } ?: return
        downloads.enqueue(rom, playAfter = true)
    }

    @OptIn(FlowPreview::class)
    private fun matchRetroAchievements() = scope.launch {
        // Re-run when the library, the RA account or the matching switch changes, or matches are forgotten.
        combine(
            library.games,
            store.settings.map { Triple(it.raUser, it.raApiKey, it.raMatchTitles) }.distinctUntilChanged(),
            store.userData.map { it.raIds.size }.distinctUntilChanged(),
        ) { games, _, _ -> games }
            .debounce(3000)
            .collect { ra.matchLibrary(it) }
    }

    /** Names games by checksum: No-Intro first, RetroAchievements' hash match as a fallback. */
    private fun identifyGames() {
        scope.launch { library.scannedGames.collect { if (it.isNotEmpty()) identifier.identify(it) } }
        scope.launch {
            combine(identifier.names, ra.hashTitles) { noIntro, raTitles -> raTitles + noIntro }
                .distinctUntilChanged()
                .collect { library.applyNames(it) }
        }
    }

    init {
        identifyGames()
        matchRetroAchievements()
        scope.launch {
            downloads.states.collect { states ->
                for (s in states.values) {
                    if (s !is DownloadState.Done || !downloads.consumePlayAfter(s.rom.id)) continue
                    val local = s.gameId?.let { library.gameById(it) } ?: localFor(s.rom.id) ?: continue
                    val settingsNow = store.settings.value
                    val cmd = launcher.selectCommand(local, settingsNow, store.userData.value) ?: continue
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (launcher.launch(local, cmd) == LaunchResult.Started) markPlayed(local)
                    }
                }
            }
        }
    }

    fun markPlayed(game: Game) {
        scope.launch {
            store.updateUser { u ->
                u.copy(
                    lastPlayed = u.lastPlayed + (game.id to System.currentTimeMillis()),
                    playCount = u.playCount + (game.id to ((u.playCount[game.id] ?: 0) + 1)),
                )
            }
        }
    }
}

class RaRepository(
    context: Context,
    private val client: RaClient,
    private val store: SettingsStore,
    private val catalog: SystemCatalog,
    private val scope: CoroutineScope,
) {
    private val hashes = RomHashCache(context)
    private val matchMutex = Mutex()
    private val pending = AtomicReference<List<Game>?>(null)
    /** Games already looked at this process, so misses aren't hashed and searched again. */
    private val tried: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _profile = MutableStateFlow<Loadable<RaProfile>>(Loadable.Idle)
    val profile: StateFlow<Loadable<RaProfile>> = _profile.asStateFlow()
    private val _played = MutableStateFlow<Loadable<List<RaRecentGame>>>(Loadable.Idle)
    val played: StateFlow<Loadable<List<RaRecentGame>>> = _played.asStateFlow()

    private var lastRefresh = 0L
    private val progressCache = HashMap<Int, Pair<Long, RaGameProgress>>()

    init {
        // Credentials changed: drop everything so stale data from another account never shows.
        scope.launch {
            store.settings.map { it.raUser to it.raApiKey }.distinctUntilChanged().drop(1).collect {
                lastRefresh = 0
                progressCache.clear()
                tried.clear()
                _profile.value = Loadable.Idle; _played.value = Loadable.Idle
                refresh(force = true)
            }
        }
        // Matches were forgotten in Settings: earlier misses deserve another try too.
        scope.launch {
            var before = Int.MAX_VALUE
            store.userData.map { it.raIds.size }.distinctUntilChanged().collect { n ->
                if (n < before) tried.clear()
                before = n
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!store.settings.value.raConfigured) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 2 * 60 * 1000) return
        lastRefresh = now
        scope.launch {
            // A failed refresh (often RA's rate limit) keeps what was already loaded.
            if (_profile.value !is Loadable.Ready) _profile.value = Loadable.Loading
            runCatching { client.profile() }
                .onSuccess { _profile.value = Loadable.Ready(it) }
                .onFailure { if (_profile.value !is Loadable.Ready) _profile.value = Loadable.Error(it.message ?: "Failed") }
            if (_played.value !is Loadable.Ready) _played.value = Loadable.Loading
            runCatching { client.recentlyPlayed(15) }
                .onSuccess { _played.value = Loadable.Ready(it) }
                .onFailure { if (_played.value !is Loadable.Ready) _played.value = Loadable.Error(it.message ?: "Failed") }
        }
    }

    suspend fun progress(gameId: Int, force: Boolean = false): RaGameProgress {
        val cached = progressCache[gameId]
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 5 * 60 * 1000) return cached.second
        return client.gameProgress(gameId).also { progressCache[gameId] = System.currentTimeMillis() to it }
    }

    /** Known id (from RomM, manual entry or a previous match), otherwise a fresh match. */
    suspend fun idFor(game: Game, catalog: SystemCatalog): Int? {
        store.userData.value.raIds[game.id]?.let { return it }
        val settings = store.settings.value
        if (!settings.raConfigured || !settings.raMatchTitles) return null
        val console = catalog.meta(game.system).raConsole ?: return null
        val index = runCatching { client.gameIndex(console) }.getOrNull() ?: return null
        val id = withContext(Dispatchers.IO) { match(game, console, index) }.also { hashes.save(); saveTitles() } ?: return null
        save(mapOf(game.id to id))
        return id
    }

    /** ROM hash first, like RetroArch; then the user's custom title and the file name. */
    private fun match(game: Game, console: Int, index: RaGameIndex): Int? {
        val byHash = index.byHash(hashes.hashFor(game, console))
        if (byHash != null) {
            // A hash match also names the game, for files No-Intro doesn't know.
            index.title(byHash)?.let { t -> if (_hashTitles.value[game.id] != t) _hashTitles.update { it + (game.id to t) } }
            return byHash
        }
        return index.byTitle(store.userData.value.customTitle[game.id])
            ?: game.datName?.let { index.byFileName("$it.x") }
            ?: index.byFileName(game.fileName)
    }

    private val titlesFile = File(context.filesDir, "ra-titles.json")
    private val titlesSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
    private val _hashTitles = MutableStateFlow<Map<String, String>>(
        runCatching { PillarJson.decodeFromString(titlesSerializer, titlesFile.readText()) }.getOrDefault(emptyMap())
    )
    /** Game id -> RetroAchievements title, for games identified by ROM hash. */
    val hashTitles: StateFlow<Map<String, String>> = _hashTitles.asStateFlow()

    private fun saveTitles() {
        runCatching { titlesFile.writeText(PillarJson.encodeToString(titlesSerializer, _hashTitles.value)) }
    }

    private suspend fun save(found: Map<String, Int>) {
        if (found.isEmpty()) return
        // Ids that appeared meanwhile (RomM, manual entry) win over ours.
        store.updateUser { u -> u.copy(raIds = found + u.raIds) }
    }

    /**
     * Matches every local game without a RetroAchievements id in the background, one game list
     * request per console. Calls while a run is going queue the newest list for the next run.
     */
    fun matchLibrary(games: List<Game>) {
        val s = store.settings.value
        if (!s.raConfigured || !s.raMatchTitles) return
        pending.set(games)
        scope.launch(Dispatchers.IO) {
            if (!matchMutex.tryLock()) return@launch
            try {
                while (true) runMatch(pending.getAndSet(null) ?: break)
            } finally {
                matchMutex.unlock()
            }
            pending.get()?.let { matchLibrary(it) }
        }
    }

    private suspend fun runMatch(games: List<Game>) {
        val known = store.userData.value.raIds
        val named = _hashTitles.value
        // Already-matched games are hashed too when nothing has named them yet.
        val byConsole = games
            .filter { !it.remote && it.id !in tried && (it.id !in known || (it.datName == null && it.id !in named)) }
            .mapNotNull { g -> catalog.meta(g.system).raConsole?.let { it to g } }
            .groupBy({ it.first }, { it.second })
        val found = HashMap<String, Int>()
        try {
            for ((console, list) in byConsole) {
                val index = try {
                    client.gameIndex(console)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    continue // Not marked as tried: the next library change retries.
                }
                for (game in list) {
                    val s = store.settings.value
                    if (!s.raConfigured || !s.raMatchTitles) return
                    match(game, console, index)?.let { found[game.id] = it }
                    tried += game.id
                    if (found.size >= 25) { save(found.toMap()); found.clear(); hashes.save() }
                }
            }
        } finally {
            withContext(NonCancellable) { save(found) }
            hashes.save()
            saveTitles()
        }
    }
}

class RommRepository(
    context: Context,
    private val client: RommClient,
    private val store: SettingsStore,
    private val catalog: SystemCatalog,
    private val scope: CoroutineScope,
) {
    private val _platforms = MutableStateFlow<Loadable<List<RommPlatform>>>(Loadable.Idle)
    val platforms: StateFlow<Loadable<List<RommPlatform>>> = _platforms.asStateFlow()
    private val catalogFile = File(context.filesDir, "romm-catalog.json")
    private val _games = MutableStateFlow<List<RommRom>>(emptyList())
    /** Every game on the RomM server, cached on the device so the library works offline too. */
    val games: StateFlow<List<RommRom>> = _games.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var lastRefresh = 0L
    private val mutex = kotlinx.coroutines.sync.Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            val cached = runCatching {
                PillarJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(RommRom.serializer()), catalogFile.readText())
            }.getOrNull()
            if (cached != null && _games.value.isEmpty()) _games.value = cached
        }
        scope.launch {
            store.settings.map { listOf(it.rommUrl, it.rommUser, it.rommPassword, it.rommToken) }
                .distinctUntilChanged().drop(1).collect {
                    lastRefresh = 0
                    _platforms.value = Loadable.Idle
                    refresh(force = true)
                }
        }
    }

    fun rom(id: Int): RommRom? = _games.value.firstOrNull { it.id == id }

    fun refresh(force: Boolean = false) {
        if (!store.settings.value.rommConfigured) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 10 * 60 * 1000) return
        lastRefresh = now
        scope.launch(Dispatchers.IO) {
            if (!mutex.tryLock()) return@launch
            try {
                _syncing.value = true
                val plats = runCatching { client.platforms() }
                _platforms.value = plats.fold({ Loadable.Ready(it) }, { Loadable.Error(it.message ?: "Failed") })
                plats.getOrNull()?.forEach { p ->
                    if (catalog.resolveRommPlatform(p.slug, p.fsSlug) == null) catalog.extraNames[p.fsSlug.ifBlank { p.slug }] = p.title
                }
                val all = ArrayList<RommRom>()
                var offset = 0
                while (true) {
                    val page = client.roms(offset = offset, limit = 500)
                    all += page.items
                    offset += page.items.size
                    if (page.items.isEmpty() || offset >= page.total) break
                }
                _games.value = all
                _error.value = null
                runCatching {
                    catalogFile.writeText(PillarJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(RommRom.serializer()), all))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Couldn't reach RomM"
            } finally {
                _syncing.value = false
                mutex.unlock()
            }
        }
    }
}

