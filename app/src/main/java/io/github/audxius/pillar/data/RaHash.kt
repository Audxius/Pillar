package io.github.audxius.pillar.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * RetroAchievements ROM hashes, following rcheevos' rc_hash rules for cartridge systems. Disc
 * systems aren't hashed; they fall back to title matching.
 */
object RaHash {
    const val MAX_SIZE = 128L shl 20
    const val ARCADE = 27
    private const val N64 = 2
    private const val SNES = 3
    private const val NES = 7
    private const val PCE = 8
    private const val LYNX = 13
    private const val NDS = 18
    private const val ATARI7800 = 51
    private const val FDS = 81

    /** Disc-based or otherwise specially hashed consoles that only get title matching. */
    private val noHash = setOf(9, 12, 16, 19, 20, 21, 22, 26, 39, 40, 41, 43, 47, 48, 49, 56, 62, 76, 77, 82)
    private val noHashExt = setOf("cue", "chd", "iso", "cso", "pbp", "gdi", "cdi", "m3u", "rvz", "wbfs", "7z", "rar", "ccd", "mds", "toc")

    /** The lowercase MD5 RetroAchievements knows this ROM by, or null when it can't be hashed. */
    fun hash(consoleId: Int, fileName: String, size: Long, open: () -> InputStream): String? {
        if (consoleId == ARCADE) return md5(fileName.substringAfterLast('/').substringBeforeLast('.').toByteArray())
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (consoleId in noHash || ext in noHashExt) return null
        if (ext == "zip") return fromZip(consoleId, open)
        if (consoleId == NDS) return nds(open)
        if (size > MAX_SIZE) return null
        return open().use { stream(consoleId, size, it) }
    }

    /** Hashes the first file in a zip as if it had been extracted. */
    private fun fromZip(consoleId: Int, open: () -> InputStream): String? {
        val (name, bytes) = open().use { raw ->
            val zip = ZipInputStream(raw)
            var entry = zip.nextEntry
            while (entry != null && entry.isDirectory) entry = zip.nextEntry
            if (entry == null) return null
            entry.name.substringAfterLast('/') to (readCapped(zip, MAX_SIZE) ?: return null)
        }
        if (name.substringAfterLast('.', "").equals("zip", ignoreCase = true)) return null
        return hash(consoleId, name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }

    private fun stream(consoleId: Int, size: Long, input: InputStream): String {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(1 shl 16)
        var n = readFully(input, buf, 0, 512)
        val skip = headerSkip(consoleId, buf, n, size)
        val order = if (consoleId == N64 && n > 0) buf[0].toInt() and 0xFF else 0x80
        var first = true
        while (n > 0) {
            when (order) {
                0x37 -> for (i in 0 until n - 1 step 2) swap(buf, i, i + 1)
                0x40 -> for (i in 0 until n - 3 step 4) { swap(buf, i, i + 3); swap(buf, i + 1, i + 2) }
            }
            val off = if (first) minOf(skip, n) else 0
            md.update(buf, off, n - off)
            first = false
            n = readFully(input, buf, 0, buf.size)
        }
        return hex(md.digest())
    }

    private fun headerSkip(consoleId: Int, b: ByteArray, n: Int, size: Long): Int = when (consoleId) {
        NES, FDS -> if (starts(b, n, "NES\u001a") || starts(b, n, "FDS\u001a")) 16 else 0
        SNES -> if (size % 8192 == 512L) 512 else 0
        PCE -> if (size % 131072 == 512L) 512 else 0
        LYNX -> if (starts(b, n, "LYNX\u0000")) 64 else 0
        ATARI7800 -> if (n >= 10 && String(b, 1, 9, Charsets.ISO_8859_1) == "ATARI7800") 128 else 0
        else -> 0
    }

    /** Header, ARM9 and ARM7 code and the icon/title block, like rcheevos does for DS cartridges. */
    private fun nds(open: () -> InputStream): String? {
        var header = readRange(open, 0, 0x160)
        if (header.size < 0x160) return null
        var base = 0L
        // SuperCard dumps carry an extra 0x200-byte loader in front of the real header.
        if (u32(header, 0) == 0xEA00002EL && u32(header, 0xB0) == 0x00964644L) {
            base = 0x200
            header = readRange(open, base, 0x160)
            if (header.size < 0x160) return null
        }
        val arm9Off = u32(header, 0x20); val arm9Size = u32(header, 0x2C)
        val arm7Off = u32(header, 0x30); val arm7Size = u32(header, 0x3C)
        val iconOff = u32(header, 0x68)
        if (arm9Size + arm7Size > 16L shl 20) return null
        val md = MessageDigest.getInstance("MD5")
        md.update(header)
        md.update(readRange(open, base + arm9Off, arm9Size.toInt()).copyOf(arm9Size.toInt()))
        md.update(readRange(open, base + arm7Off, arm7Size.toInt()).copyOf(arm7Size.toInt()))
        if (iconOff != 0L) md.update(readRange(open, base + iconOff, 0xA00).copyOf(0xA00))
        return hex(md.digest())
    }

    private fun readRange(open: () -> InputStream, offset: Long, len: Int): ByteArray = open().use { input ->
        var left = offset
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped
            else if (input.read() < 0) return ByteArray(0) else left--
        }
        val out = ByteArray(len)
        out.copyOf(readFully(input, out, 0, len))
    }

    private fun readCapped(input: InputStream, max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var read = 0
        while (read < len) {
            val n = input.read(buf, off + read, len - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun starts(b: ByteArray, n: Int, magic: String): Boolean =
        n >= magic.length && magic.indices.all { b[it] == magic[it].code.toByte() }

    private fun swap(b: ByteArray, i: Int, j: Int) { val t = b[i]; b[i] = b[j]; b[j] = t }

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/**
 * Hashes local games for RetroAchievements and remembers each result on disk with the size and
 * modification time it was computed for, so the library isn't read again on every launch.
 */
class RomHashCache(private val context: Context) {
    private val file = File(context.filesDir, "ra-hashes.json")
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val cache: MutableMap<String, String> by lazy {
        runCatching { HashMap(PillarJson.decodeFromString(serializer, file.readText())) }.getOrElse { HashMap() }
    }
    private var dirty = false

    /** The game's RA hash; null when it can't be hashed (disc games, unreadable or huge files). */
    @Synchronized
    fun hashFor(game: Game, consoleId: Int): String? {
        if (game.remote) return null
        val stamp = "${game.size}|${game.modified}|$consoleId|"
        cache[game.id]?.takeIf { it.startsWith(stamp) }?.let { return it.removePrefix(stamp).ifEmpty { null } }
        val hash = try {
            RaHash.hash(consoleId, game.fileName, game.size) { open(game) }
        } catch (e: IOException) {
            return null // Not cached: the file may just be temporarily unavailable.
        } catch (e: SecurityException) {
            return null
        }
        cache[game.id] = stamp + hash.orEmpty()
        dirty = true
        return hash
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        runCatching { file.writeText(PillarJson.encodeToString(serializer, cache)) }
        dirty = false
    }

    private fun open(game: Game): InputStream =
        if (game.docUri.isNotEmpty()) {
            context.contentResolver.openInputStream(Uri.parse(game.docUri)) ?: throw IOException("Can't open ${game.fileName}")
        } else {
            FileInputStream(game.path ?: throw IOException("No path for ${game.fileName}"))
        }
}
