package io.github.audxius.pillar.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Physical box art for one game: spine, back, 3D box, and the disc or cartridge itself. */
@Serializable
data class BoxArt(
    val spine: String? = null,
    val back: String? = null,
    val box3d: String? = null,
    val disc: String? = null,
    val cart: String? = null,
)

/**
 * Matches games against LaunchBox's community Games Database (spines, backs, 3D boxes, discs and
 * cartridges). The database is one big file, so it's streamed once and only the entries for your
 * own games are kept; the images themselves load from LaunchBox's image server when shown.
 */
object BoxArtMatcher {
    const val IMAGE_BASE = "https://images.launchbox-app.com/"

    /** Pillar system -> LaunchBox platform name. */
    val PLATFORMS = mapOf(
        "nes" to "Nintendo Entertainment System", "famicom" to "Nintendo Entertainment System",
        "fds" to "Nintendo Famicom Disk System", "snes" to "Super Nintendo Entertainment System",
        "snesna" to "Super Nintendo Entertainment System", "sfc" to "Super Nintendo Entertainment System",
        "n64" to "Nintendo 64", "gc" to "Nintendo GameCube", "wii" to "Nintendo Wii", "wiiu" to "Nintendo Wii U",
        "switch" to "Nintendo Switch", "gb" to "Nintendo Game Boy", "gbc" to "Nintendo Game Boy Color",
        "gba" to "Nintendo Game Boy Advance", "nds" to "Nintendo DS", "n3ds" to "Nintendo 3DS",
        "virtualboy" to "Nintendo Virtual Boy", "gameandwatch" to "Nintendo Game & Watch",
        "genesis" to "Sega Genesis", "megadrive" to "Sega Genesis", "megadrivejp" to "Sega Genesis",
        "mastersystem" to "Sega Master System", "gamegear" to "Sega Game Gear", "sega32x" to "Sega 32X",
        "segacd" to "Sega CD", "megacd" to "Sega CD", "saturn" to "Sega Saturn", "saturnjp" to "Sega Saturn",
        "dreamcast" to "Sega Dreamcast", "sg-1000" to "Sega SG-1000",
        "psx" to "Sony Playstation", "ps2" to "Sony Playstation 2", "ps3" to "Sony Playstation 3",
        "psp" to "Sony PSP", "psvita" to "Sony Playstation Vita",
        "pcengine" to "NEC TurboGrafx-16", "tg16" to "NEC TurboGrafx-16", "pcenginecd" to "NEC TurboGrafx-CD",
        "tg-cd" to "NEC TurboGrafx-CD", "neogeo" to "SNK Neo Geo AES", "ngp" to "SNK Neo Geo Pocket",
        "ngpc" to "SNK Neo Geo Pocket Color", "neogeocd" to "SNK Neo Geo CD", "atari2600" to "Atari 2600",
        "atari5200" to "Atari 5200", "atari7800" to "Atari 7800", "atarilynx" to "Atari Lynx",
        "atarijaguar" to "Atari Jaguar", "3do" to "3DO Interactive Multiplayer", "xbox" to "Microsoft Xbox",
        "colecovision" to "ColecoVision", "intellivision" to "Mattel Intellivision", "wonderswan" to "WonderSwan",
        "wonderswancolor" to "WonderSwan Color", "c64" to "Commodore 64", "amiga" to "Commodore Amiga",
    )

    private val wantedTypes = mapOf(
        "Box - Spine" to "spine", "Box - Back" to "back", "Box - 3D" to "box3d", "Disc" to "disc", "Cart - Front" to "cart",
    )

    class Wanted(val gameId: String, val system: String, val keys: Set<String>, val region: String?)

    /** "USA" -> "North America" etc., so the right region's box wins. */
    fun regionOf(tags: List<String>): String? {
        val t = tags.joinToString(",").lowercase()
        return when {
            "usa" in t || "us" in t.split(',') -> "North America"
            "europe" in t || "uk" in t -> "Europe"
            "japan" in t -> "Japan"
            else -> null
        }
    }

    /**
     * Streams Metadata.xml (one field per line) and returns game id -> art for every wanted game.
     * Only a few small maps are kept in memory, so the 500 MB file is no problem.
     */
    fun match(reader: BufferedReader, wanted: List<Wanted>): Map<String, BoxArt> {
        // platform -> name key -> wanted games
        val index = HashMap<String, HashMap<String, MutableList<Wanted>>>()
        for (w in wanted) {
            val p = PLATFORMS[w.system] ?: continue
            val byKey = index.getOrPut(p) { HashMap() }
            w.keys.forEach { k -> byKey.getOrPut(k) { ArrayList() } += w }
        }
        val dbToGames = HashMap<String, MutableList<Wanted>>()
        // Database id -> platform, for platforms you have games on (alternate names carry no platform).
        val platformOf = HashMap<String, String>()
        // image candidates: game id -> type -> (region, file)
        val found = HashMap<String, HashMap<String, Pair<String?, String>>>()
        var section = ""
        var name: String? = null; var platform: String? = null; var dbId: String? = null
        var file: String? = null; var type: String? = null; var region: String? = null
        var altName: String? = null
        // A value can span lines (rare); then only its first line is kept, which is enough for names.
        fun value(line: String): String {
            val start = line.indexOf('>') + 1
            val end = line.lastIndexOf('<').takeIf { it >= start } ?: line.length
            return decode(line.substring(start, end))
        }
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            when {
                line == "<Game>" || line == "<GameImage>" || line == "<GameAlternateName>" -> {
                    section = line; name = null; platform = null; dbId = null; file = null; type = null; region = null; altName = null
                }
                line == "</Game>" -> {
                    val p = platform; val n = name; val id = dbId
                    if (p != null && n != null && id != null && p in index) {
                        platformOf[id] = p
                        index[p]?.get(ThumbMatch.key("$n.x"))?.let { dbToGames.getOrPut(id) { ArrayList() } += it }
                    }
                }
                line == "</GameAlternateName>" -> {
                    // Regional and alternate titles, on the platform of their main entry.
                    val n = altName; val id = dbId
                    val p = id?.let { platformOf[it] }
                    if (n != null && id != null && p != null) {
                        index[p]?.get(ThumbMatch.key("$n.x"))?.let { hits ->
                            val list = dbToGames.getOrPut(id) { ArrayList() }
                            hits.forEach { if (it !in list) list += it }
                        }
                    }
                }
                line == "</GameImage>" -> {
                    val games = dbId?.let { dbToGames[it] }
                    val slot = type?.let { wantedTypes[it] }
                    val f = file
                    if (games != null && slot != null && f != null) for (g in games) {
                        val byType = found.getOrPut(g.gameId) { HashMap() }
                        val prev = byType[slot]
                        fun score(r: String?) = when { r == g.region && r != null -> 3; r == null || r == "World" -> 2; r == "North America" -> 1; else -> 0 }
                        if (prev == null || score(region) > score(prev.first)) byType[slot] = region to f
                    }
                }
                line.startsWith("<Name>") && section == "<Game>" -> name = value(line)
                line.startsWith("<Platform>") && section == "<Game>" -> platform = value(line)
                line.startsWith("<AlternateName>") -> altName = value(line)
                line.startsWith("<DatabaseID>") -> dbId = value(line)
                line.startsWith("<FileName>") -> file = value(line)
                line.startsWith("<Type>") -> type = value(line)
                line.startsWith("<Region>") -> region = value(line)
            }
        }
        return found.mapValues { (_, t) ->
            fun u(k: String) = t[k]?.second?.let { IMAGE_BASE + it }
            BoxArt(spine = u("spine"), back = u("back"), box3d = u("box3d"), disc = u("disc"), cart = u("cart"))
        }
    }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

    fun wantedFor(games: List<Game>, customTitle: Map<String, String>): List<Wanted> = games.mapNotNull { g ->
        if (g.system !in PLATFORMS) return@mapNotNull null
        val keys = listOfNotNull("${g.title}.x", g.datName?.let { "$it.x" }, g.fileName, customTitle[g.id]?.let { "$it.x" })
            .map { ThumbMatch.key(it) }.filter { it.isNotEmpty() }.toSet()
        Wanted(g.id, g.system, keys, regionOf(LibraryRepository.tags(g.datName?.let { "$it.x" } ?: g.fileName)))
    }
}

/** Downloads and matches the LaunchBox database on request, and keeps the result on the device. */
class BoxArtDb(context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    sealed interface Status {
        data object Idle : Status
        data class Running(val progress: Float, val message: String) : Status
        data class Done(val matched: Int, val at: Long) : Status
        data class Failed(val message: String) : Status
    }

    private val file = File(context.filesDir, "boxart.json")
    private val serializer = MapSerializer(String.serializer(), BoxArt.serializer())
    private val _art = MutableStateFlow<Map<String, BoxArt>>(emptyMap())
    val art: StateFlow<Map<String, BoxArt>> = _art.asStateFlow()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { PillarJson.decodeFromString(serializer, file.readText()) }.getOrNull()?.let {
                _art.value = it
                _status.value = Status.Done(it.size, file.lastModified())
            }
        }
    }

    val downloaded: Boolean get() = file.exists()

    fun download(games: List<Game>, customTitle: Map<String, String>) {
        if (_status.value is Status.Running) return
        scope.launch(Dispatchers.IO) {
            _status.value = Status.Running(0f, "Connecting…")
            runCatching { fetch(BoxArtMatcher.wantedFor(games, customTitle)) }
                .onSuccess { m ->
                    _art.value = m
                    runCatching { file.writeText(PillarJson.encodeToString(serializer, m)) }
                    _status.value = Status.Done(m.size, System.currentTimeMillis())
                }
                .onFailure { _status.value = Status.Failed(it.message ?: "Download failed") }
        }
    }

    private suspend fun fetch(wanted: List<BoxArtMatcher.Wanted>): Map<String, BoxArt> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://gamesdb.launchbox-app.com/Metadata.zip").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("LaunchBox returned HTTP ${r.code}")
            val body = r.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: 110_000_000L
            val counting = CountingStream(body.byteStream()) { n -> _status.value = Status.Running((n.toFloat() / total).coerceIn(0f, 1f), "Reading the box art database…") }
            ZipInputStream(counting.buffered(1 shl 16)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: error("Metadata.xml missing from the download")
                    if (e.name == "Metadata.xml") return@withContext BoxArtMatcher.match(zip.bufferedReader(), wanted)
                }
                @Suppress("UNREACHABLE_CODE") emptyMap()
            }
        }
    }

    private class CountingStream(private val inner: InputStream, private val onCount: (Long) -> Unit) : InputStream() {
        private var n = 0L
        private var lastReport = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        private fun bump(k: Long) { n += k; if (n - lastReport > 512 * 1024) { lastReport = n; onCount(n) } }
        override fun close() = inner.close()
    }
}
