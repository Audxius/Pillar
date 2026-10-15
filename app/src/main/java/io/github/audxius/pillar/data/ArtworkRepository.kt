package io.github.audxius.pillar.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Every kind of artwork Pillar knows for a game. Null means "looked, found nothing". */
@Serializable
data class ArtSet(
    val boxart: String? = null,
    val snap: String? = null,
    /** The libretro database name the file matched, e.g. "Final Fantasy III (USA)". */
    val matchedName: String? = null,
    val sgdbGrid: String? = null,
    val sgdbSquare: String? = null,
    val sgdbHero: String? = null,
    val sgdbLogo: String? = null,
    val resolvedAt: Long = 0,
    val libretroChecked: Boolean = false,
    val sgdbChecked: Boolean = false,
    /** Bumped when matching rules change, so older results get looked up again. */
    val v: Int = 0,
)

@Serializable
private data class SgdbGame(val id: Int, val name: String = "", val verified: Boolean = false)

@Serializable
private data class SgdbImage(
    val id: Int = 0,
    val url: String = "",
    val thumb: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val score: Int = 0,
    val nsfw: Boolean = false,
    val humor: Boolean = false,
    @SerialName("style") val style: String? = null,
)

/**
 * Finds artwork for local games: fuzzy-matched libretro thumbnails (box art and snaps) and, with
 * an API key, SteamGridDB grids, square icons, wide heroes and logos. Results are cached on disk
 * so each game is only looked up once.
 */
class ArtworkRepository(
    context: Context,
    private val http: OkHttpClient,
    private val catalog: SystemCatalog,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val cacheFile = File(context.filesDir, "artwork.json")
    private val indexDir = File(context.cacheDir, "libretro-index").apply { mkdirs() }
    private val mapSerializer = MapSerializer(String.serializer(), ArtSet.serializer())

    private val _art = MutableStateFlow<Map<String, ArtSet>>(emptyMap())
    val art: StateFlow<Map<String, ArtSet>> = _art.asStateFlow()

    private val aspectFile = File(context.filesDir, "cover-aspects.json")
    private val aspectSerializer = MapSerializer(String.serializer(), Float.serializer())
    private val _aspects = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** Width / height of each game's cover as last loaded, so tiles size correctly before the image arrives. */
    val aspects: StateFlow<Map<String, Float>> = _aspects.asStateFlow()
    @Volatile private var aspectsDirty = false

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    // Lookups that hit a network or API error wait a while before being retried.
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val indexes = ConcurrentHashMap<String, ThumbIndex>()
    private val indexLocks = ConcurrentHashMap<String, Mutex>()
    private val network = Semaphore(3)
    private val saveMutex = Mutex()
    @Volatile private var dirty = false

    init {
        scope.launch(Dispatchers.IO) {
            val saved = runCatching { PillarJson.decodeFromString(mapSerializer, cacheFile.readText()) }.getOrDefault(emptyMap())
                .filterValues { it.v >= ART_VERSION }
            // Merge rather than replace: lookups may already have finished while the file was loading.
            _art.update { fresh -> saved + fresh }
            val savedAspects = runCatching { PillarJson.decodeFromString(aspectSerializer, aspectFile.readText()) }.getOrDefault(emptyMap())
            _aspects.update { fresh -> savedAspects + fresh }
            // Writes are batched so resolving a big library doesn't rewrite the file per game.
            while (true) {
                delay(3000)
                if (dirty) persist()
                if (aspectsDirty) {
                    aspectsDirty = false
                    runCatching { aspectFile.writeText(PillarJson.encodeToString(aspectSerializer, _aspects.value)) }
                }
            }
        }
    }

    private suspend fun persist() = saveMutex.withLock {
        dirty = false
        runCatching { cacheFile.writeText(PillarJson.encodeToString(mapSerializer, _art.value)) }
    }

    /** Looks up artwork for a game if it hasn't been looked up yet (or SteamGridDB was enabled since). */
    fun ensure(game: Game) {
        val settings = store.settings.value
        val existing = _art.value[game.id]
        val needLibretro = existing?.libretroChecked != true && settings.onlineCovers
        val needSgdb = settings.sgdbKey.isNotBlank() && existing?.sgdbChecked != true
        if (!needLibretro && !needSgdb) return
        val failed = failedAt[game.id]
        if (failed != null && System.currentTimeMillis() - failed < 10 * 60 * 1000) return
        if (!inFlight.add(game.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                network.withPermit { resolve(game, existing ?: ArtSet(), needLibretro, needSgdb) }
            } finally {
                inFlight.remove(game.id)
            }
        }
    }

    fun ensureAll(games: List<Game>) = games.forEach(::ensure)

    fun recordAspect(gameId: String, ratio: Float) {
        if (ratio <= 0f || ratio.isNaN()) return
        val old = _aspects.value[gameId]
        if (old != null && kotlin.math.abs(old - ratio) < 0.02f) return
        _aspects.update { it + (gameId to ratio) }
        aspectsDirty = true
    }

    /** Forgets cached results so everything is looked up again (e.g. after adding an API key). */
    fun reset(gameId: String? = null) {
        _art.update { if (gameId == null) emptyMap() else it - gameId }
        if (gameId == null) failedAt.clear() else failedAt.remove(gameId)
        dirty = true
    }

    private suspend fun resolve(game: Game, start: ArtSet, libretro: Boolean, sgdb: Boolean) {
        var set = start
        if (libretro) {
            val system = catalog.meta(game.system).libretro
            val name = system?.let { sys -> index(sys)?.best(game.fileName) }
            set = set.copy(
                matchedName = name,
                boxart = name?.let { libretroUrl(system, "Named_Boxarts", it) },
                snap = name?.let { libretroUrl(system, "Named_Snaps", it) },
                // Only mark as checked when the index was available; otherwise try again later.
                libretroChecked = system == null || indexes.containsKey(system),
            )
            if (!set.libretroChecked) failedAt[game.id] = System.currentTimeMillis()
        }
        if (sgdb) {
            val found = runCatching { sgdb(game, set.matchedName) }.getOrNull()
            // A failed request (network, bad key) is retried next time instead of being cached.
            if (found != null) set = found.copy(boxart = set.boxart, snap = set.snap, matchedName = set.matchedName, libretroChecked = set.libretroChecked)
            else failedAt[game.id] = System.currentTimeMillis()
        }
        set = set.copy(resolvedAt = System.currentTimeMillis(), v = ART_VERSION)
        _art.update { it + (game.id to set) }
        dirty = true
    }

    private fun libretroUrl(system: String, folder: String, name: String): String {
        val safe = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
        return "https://thumbnails.libretro.com/${Uri.encode(system)}/$folder/${Uri.encode(safe)}.png"
    }

    /** All box-art names for a libretro system (cached on disk for a month), parsed for matching. */
    private suspend fun index(system: String): ThumbIndex? {
        indexes[system]?.let { return it }
        val lock = indexLocks.getOrPut(system) { Mutex() }
        return lock.withLock {
            indexes[system]?.let { return@withLock it }
            val file = File(indexDir, system.replace(Regex("[^A-Za-z0-9]"), "_") + ".txt")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 30L * 24 * 3600 * 1000
            val names = if (fresh) {
                file.readLines()
            } else {
                val fetched = runCatching { fetchIndex(system) }.getOrNull()
                if (fetched != null) runCatching { file.writeText(fetched.joinToString("\n")) }
                fetched ?: if (file.exists()) file.readLines() else return@withLock null
            }
            ThumbIndex(names).also { indexes[system] = it }
        }
    }

    private suspend fun fetchIndex(system: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://thumbnails.libretro.com/${Uri.encode(system)}/Named_Boxarts/"
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val html = resp.body?.string().orEmpty()
            // Plain scanning: the listings are megabytes of HTML.
            val out = ArrayList<String>()
            var i = html.indexOf("href=\"")
            while (i >= 0) {
                val start = i + 6
                val end = html.indexOf('"', start)
                if (end < 0) break
                val href = html.substring(start, end)
                if (href.endsWith(".png") && !href.startsWith("/") && !href.startsWith("?")) out += Uri.decode(href.removeSuffix(".png"))
                i = html.indexOf("href=\"", end)
            }
            out
        }
    }

    // ---- SteamGridDB ---------------------------------------------------------------------------

    private suspend fun sgdbGet(path: String, params: Map<String, String> = emptyMap()): JsonElement? = withContext(Dispatchers.IO) {
        val key = store.settings.value.sgdbKey.trim()
        val url = "https://www.steamgriddb.com/api/v2$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $key").build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) error("SteamGridDB HTTP ${resp.code}")
            val obj = PillarJson.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@withContext null
            obj["data"]
        }
    }

    private suspend fun sgdbImages(kind: String, id: Int, params: Map<String, String>): List<SgdbImage> {
        val data = sgdbGet("/$kind/game/$id", params + mapOf("nsfw" to "false", "humor" to "false")) ?: return emptyList()
        return runCatching { PillarJson.decodeFromJsonElement(ListSerializer(SgdbImage.serializer()), data) }.getOrDefault(emptyList())
    }

    /** Returns a set with only the SteamGridDB fields filled, or null if the lookup failed. */
    private suspend fun sgdb(game: Game, matchedName: String?): ArtSet? {
        // Prefer the clean database title the file matched ("Pokemon - FireRed Version") over the file name.
        val matched = matchedName?.let { LibraryRepository.cleanTitle("$it.x") }
        val title = store.userData.value.customTitle[game.id] ?: matched ?: game.title
        val results = sgdbGet("/search/autocomplete/${Uri.encode(title)}")
            ?.let { runCatching { PillarJson.decodeFromJsonElement(ListSerializer(SgdbGame.serializer()), it) }.getOrNull() }
            ?: return ArtSet(sgdbChecked = true)
        val match = SgdbMatch.pick(title, results.map { it.name })?.let { results[it] } ?: return ArtSet(sgdbChecked = true)

        fun List<SgdbImage>.pick() = maxByOrNull { it.score }?.url?.takeIf { it.isNotBlank() }
        val grid = sgdbImages("grids", match.id, mapOf("dimensions" to "600x900,342x482,660x930")).pick()
        val square = sgdbImages("grids", match.id, mapOf("dimensions" to "512x512,1024x1024")).pick()
            ?: sgdbImages("icons", match.id, emptyMap()).filter { it.width >= 128 }.pick()
        val hero = sgdbImages("heroes", match.id, emptyMap()).pick()
        val logo = sgdbImages("logos", match.id, emptyMap()).pick()
        return ArtSet(sgdbGrid = grid, sgdbSquare = square, sgdbHero = hero, sgdbLogo = logo, sgdbChecked = true)
    }

    /** Checks an API key by running a search. */
    suspend fun testSgdb(): Boolean = runCatching { sgdbGet("/search/autocomplete/mario") != null }.getOrDefault(false)
}

/** 2: accent-insensitive titles and strict SteamGridDB matching (0.3.0). */
private const val ART_VERSION = 2
