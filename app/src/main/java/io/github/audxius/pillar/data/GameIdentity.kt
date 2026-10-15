package io.github.audxius.pillar.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** One system's No-Intro database: ROM checksums mapped to official game names. */
class DatIndex(private val byCrc: Map<String, String>, private val byMd5: Map<String, String>) {
    val size: Int get() = byCrc.size

    fun lookup(sums: RomChecksums): String? =
        sums.md5s.firstNotNullOfOrNull { byMd5[it] } ?: sums.crcs.firstNotNullOfOrNull { byCrc[it] }

    /** Compact form for the on-disk cache: one "crc md5 name" line per ROM. */
    fun lines(): Sequence<String> = byCrc.asSequence().map { (crc, name) -> "$crc\t\t$name" } +
        byMd5.asSequence().map { (md5, name) -> "\t$md5\t$name" }

    companion object {
        private val crcRe = Regex("\\scrc ([0-9A-Fa-f]{8})")
        private val md5Re = Regex("\\smd5 ([0-9A-Fa-f]{32})")

        /** Reads a clrmamepro .dat as published in libretro-database's metadat/no-intro. */
        fun parseDat(lines: Sequence<String>): DatIndex {
            val crc = HashMap<String, String>()
            val md5 = HashMap<String, String>()
            var game: String? = null
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("game (") -> game = null
                    line.startsWith("name \"") && game == null -> game = line.substring(6, line.lastIndexOf('"').coerceAtLeast(6))
                    line.startsWith("rom (") && game != null -> {
                        crcRe.find(line)?.let { crc.putIfAbsent(it.groupValues[1].lowercase(), game!!) }
                        md5Re.find(line)?.let { md5.putIfAbsent(it.groupValues[1].lowercase(), game!!) }
                    }
                }
            }
            return DatIndex(crc, md5)
        }

        fun parseCache(lines: Sequence<String>): DatIndex {
            val crc = HashMap<String, String>()
            val md5 = HashMap<String, String>()
            for (l in lines) {
                val p = l.split('\t', limit = 3)
                if (p.size < 3) continue
                if (p[0].isNotEmpty()) crc[p[0]] = p[2]
                if (p[1].isNotEmpty()) md5[p[1]] = p[2]
            }
            return DatIndex(crc, md5)
        }
    }
}

/** CRC32 and MD5 candidates for a ROM: the whole file and, when it has a copier/emulator header, the data after it. */
data class RomChecksums(val crcs: List<String>, val md5s: List<String>)

object RomChecksum {
    /** Disc images and archives that can't be identified by a single file's checksum. */
    private val skip = setOf("cue", "chd", "iso", "cso", "pbp", "gdi", "cdi", "m3u", "rvz", "wbfs", "wua", "7z", "rar", "ccd", "mds", "toc", "nsp", "xci", "rpx")
    const val MAX_SIZE = 512L * 1024 * 1024

    fun compute(fileName: String, size: Long, open: () -> InputStream): RomChecksums? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in skip || size > MAX_SIZE) return null
        if (ext == "zip") {
            ZipInputStream(open().buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: return null
                    if (e.isDirectory) continue
                    val inner = e.name.substringAfterLast('/')
                    if (inner.substringAfterLast('.', "").lowercase() in skip) return null
                    return hashStream(zip, inner, e.size)
                }
            }
        }
        return open().use { hashStream(it, fileName, size) }
    }

    /** Hashes a stream in one pass, keeping a second set of digests that starts after any header. */
    fun hashStream(input: InputStream, fileName: String, size: Long): RomChecksums {
        val buf = ByteArray(256 * 1024)
        val first = input.readNBytesCompat(buf, 0, 512)
        val head = buf.copyOf(first)
        val skip = headerSize(head, fileName, size)
        val n64 = n64Order(head, fileName)
        val fullCrc = CRC32(); val fullMd5 = MessageDigest.getInstance("MD5")
        val bodyCrc = CRC32(); val bodyMd5 = MessageDigest.getInstance("MD5")
        var offset = 0L
        var len = first
        // Bytes left over so N64 words are never split across reads.
        var carry = ByteArray(0)
        while (len >= 0) {
            var chunk = buf
            var n = len
            if (n64 != 0) {
                val joined = carry + buf.copyOf(len)
                val usable = joined.size - joined.size % 4
                carry = joined.copyOfRange(usable, joined.size)
                chunk = joined; n = usable
                swapN64(chunk, n64, n)
            }
            fullCrc.update(chunk, 0, n); fullMd5.update(chunk, 0, n)
            if (skip > 0) {
                val from = (skip - offset).coerceIn(0L, n.toLong()).toInt()
                if (from < n) { bodyCrc.update(chunk, from, n - from); bodyMd5.update(chunk, from, n - from) }
            }
            offset += n
            len = input.read(buf)
        }
        if (carry.isNotEmpty()) { fullCrc.update(carry); fullMd5.update(carry) }
        val crcs = mutableListOf("%08x".format(fullCrc.value))
        val md5s = mutableListOf(hex(fullMd5.digest()))
        if (skip > 0) { crcs += "%08x".format(bodyCrc.value); md5s += hex(bodyMd5.digest()) }
        return RomChecksums(crcs, md5s)
    }

    /** Header bytes to drop for formats No-Intro lists without one (NES lists both via the full-file sum). */
    internal fun headerSize(head: ByteArray, fileName: String, size: Long): Int {
        fun at(i: Int, s: String) = head.size >= i + s.length && s.indices.all { head[i + it] == s[it].code.toByte() }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            at(0, "NES\u001a") -> 16
            at(0, "FDS\u001a") -> 16
            at(0, "LYNX") -> 64
            at(1, "ATARI7800") -> 128
            // Copier headers: 512 extra bytes on top of a power-of-two-ish ROM size.
            ext in setOf("sfc", "smc", "swc", "fig") && size % 1024 == 512L -> 512
            ext == "pce" && size % 131072 == 512L -> 512
            else -> 0
        }
    }

    /** 0 = big-endian (.z64) or not N64; 2 = byte-swapped (.v64); 4 = little-endian (.n64). */
    internal fun n64Order(head: ByteArray, fileName: String): Int {
        if (head.size < 4) return 0
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("z64", "v64", "n64", "u64")) return 0
        return when (head[0].toInt() and 0xFF) {
            0x37 -> 2
            0x40 -> 4
            else -> 0
        }
    }

    private fun swapN64(b: ByteArray, order: Int, n: Int) {
        var i = 0
        while (i + 3 < n) {
            if (order == 2) {
                var t = b[i]; b[i] = b[i + 1]; b[i + 1] = t
                t = b[i + 2]; b[i + 2] = b[i + 3]; b[i + 3] = t
            } else {
                var t = b[i]; b[i] = b[i + 3]; b[i + 3] = t
                t = b[i + 1]; b[i + 1] = b[i + 2]; b[i + 2] = t
            }
            i += 4
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun InputStream.readNBytesCompat(b: ByteArray, off: Int, len: Int): Int {
        var n = 0
        while (n < len) {
            val r = read(b, off + n, len - n)
            if (r < 0) break
            n += r
        }
        return n
    }
}

/**
 * Identifies local games by ROM checksum against libretro's copies of the No-Intro databases, so
 * "sonic3k.md" shows up with its real name, box art and logo. Results are cached per file.
 */
class GameIdentifier(
    private val context: Context,
    private val http: OkHttpClient,
    private val catalog: SystemCatalog,
    private val scope: CoroutineScope,
) {
    @Serializable
    private data class Entry(val stamp: String, val name: String? = null)

    private val file = File(context.filesDir, "identity.json")
    private val datDir = File(context.cacheDir, "nointro").apply { mkdirs() }
    private val serializer = MapSerializer(String.serializer(), Entry.serializer())
    private val entries = ConcurrentHashMap<String, Entry>()
    private val dats = ConcurrentHashMap<String, DatIndex>()
    /** Systems without a No-Intro set (disc consoles), so they aren't fetched again this session. */
    private val noDat = ConcurrentHashMap.newKeySet<String>()
    private val mutex = Mutex()
    private val pending = AtomicReference<List<Game>?>(null)

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Game id -> No-Intro name, e.g. "Sonic & Knuckles + Sonic The Hedgehog 3 (World)". */
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { PillarJson.decodeFromString(serializer, file.readText()) }.getOrNull()?.let { entries.putAll(it) }
            publish()
        }
    }

    private fun publish() {
        _names.value = entries.mapNotNull { (id, e) -> e.name?.let { id to it } }.toMap()
    }

    /** Identifies every local game not identified yet. Calls during a run queue the newest list. */
    fun identify(games: List<Game>) {
        pending.set(games)
        scope.launch(Dispatchers.IO) {
            if (!mutex.tryLock()) return@launch
            try {
                while (true) run(pending.getAndSet(null) ?: break)
            } finally {
                mutex.unlock()
            }
            pending.get()?.let { identify(it) }
        }
    }

    private suspend fun run(games: List<Game>) {
        var changed = 0
        val todo = games.filter { !it.remote && entries[it.id]?.stamp != stamp(it) }
        for ((system, list) in todo.groupBy { it.system }) {
            val name = catalog.meta(system).libretro ?: continue
            val dat = dat(name) ?: continue
            for (g in list) {
                val sums = try {
                    RomChecksum.compute(g.fileName, g.size) { open(g) }
                } catch (e: Exception) {
                    continue // Unreadable right now; try again next time.
                }
                entries[g.id] = Entry(stamp(g), sums?.let(dat::lookup))
                if (++changed % 20 == 0) { publish(); save() }
            }
        }
        if (changed > 0) { publish(); save() }
    }

    private fun stamp(g: Game) = "${g.size}:${g.modified}"

    private fun open(g: Game): InputStream =
        if (g.docUri.isNotEmpty()) context.contentResolver.openInputStream(Uri.parse(g.docUri)) ?: error("Can't open ${g.fileName}")
        else File(g.path ?: error("No path")).inputStream()

    private fun save() {
        runCatching { file.writeText(PillarJson.encodeToString(serializer, HashMap(entries))) }
    }

    /** The No-Intro database for a libretro system name, cached for a month; null when there's none. */
    private suspend fun dat(system: String): DatIndex? {
        dats[system]?.let { return it }
        if (system in noDat) return null
        val cache = File(datDir, system.replace(Regex("[^A-Za-z0-9]"), "_") + ".tsv")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 30L * 24 * 3600 * 1000
        val index = if (fresh) {
            DatIndex.parseCache(cache.readLines().asSequence())
        } else withContext(Dispatchers.IO) {
            val url = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/no-intro/${Uri.encode(system)}.dat"
            val fetched = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    when {
                        r.code == 404 -> DatIndex(emptyMap(), emptyMap()) // Disc systems have no No-Intro set.
                        !r.isSuccessful -> error("HTTP ${r.code}")
                        else -> r.body!!.charStream().buffered().useLines { DatIndex.parseDat(it) }
                    }
                }
            }.getOrNull()
            if (fetched != null) {
                runCatching { cache.bufferedWriter().use { w -> fetched.lines().forEach { w.write(it); w.write("\n") } } }
                fetched
            } else if (cache.exists()) DatIndex.parseCache(cache.readLines().asSequence())
            else null
        } ?: return null // Offline and nothing cached: don't remember the miss, retry later.
        if (index.size == 0) {
            noDat += system
            return null
        }
        dats[system] = index
        return index
    }
}
