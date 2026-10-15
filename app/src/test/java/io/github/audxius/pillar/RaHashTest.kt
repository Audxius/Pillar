package io.github.audxius.pillar

import io.github.audxius.pillar.data.RaGameIndex
import io.github.audxius.pillar.data.RaHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RaHashTest {
    private fun md5(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        parts.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun bytes(n: Int, seed: Int = 7) = ByteArray(n) { ((it * 31 + seed) and 0xFF).toByte() }
    private fun hash(console: Int, name: String, data: ByteArray) =
        RaHash.hash(console, name, data.size.toLong()) { ByteArrayInputStream(data) }

    @Test fun plainCartridgeIsWholeFile() {
        val rom = bytes(40000)
        assertEquals(md5(rom), hash(5, "Game.gba", rom))
    }

    @Test fun nesHeaderIsSkipped() {
        val body = bytes(32768)
        val header = "NES\u001a".toByteArray() + ByteArray(12) { 1 }
        assertEquals(md5(body), hash(7, "Game (USA).nes", header + body))
        // Headerless dumps hash as they are.
        assertEquals(md5(body), hash(7, "Game (USA).nes", body))
    }

    @Test fun fdsHeaderIsSkipped() {
        val body = bytes(65500)
        assertEquals(md5(body), hash(81, "Game.fds", "FDS\u001a".toByteArray() + ByteArray(12) + body))
    }

    @Test fun snesCopierHeaderIsSkipped() {
        val body = bytes(8192 * 64)
        assertEquals(md5(body), hash(3, "Game.smc", ByteArray(512) { 9 } + body))
        assertEquals(md5(body), hash(3, "Game.sfc", body))
    }

    @Test fun lynxAnd7800HeadersAreSkipped() {
        val body = bytes(4096)
        val lynx = "LYNX\u0000".toByteArray() + ByteArray(59) { 2 }
        assertEquals(md5(body), hash(13, "Game.lnx", lynx + body))
        val a78 = ByteArray(128).also { "ATARI7800".toByteArray().copyInto(it, 1) }
        assertEquals(md5(body), hash(51, "Game.a78", a78 + body))
    }

    @Test fun n64ByteOrdersHashTheSame() {
        val z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40) + bytes(1 shl 17)
        val v64 = z64.copyOf().also { b -> for (i in b.indices step 2) { val t = b[i]; b[i] = b[i + 1]; b[i + 1] = t } }
        val n64 = z64.copyOf().also { b ->
            for (i in b.indices step 4) { b.reverse(i, i + 4) }
        }
        val expected = md5(z64)
        assertEquals(expected, hash(2, "Game.z64", z64))
        assertEquals(expected, hash(2, "Game.v64", v64))
        assertEquals(expected, hash(2, "Game.n64", n64))
    }

    @Test fun ndsHashesHeaderCodeAndIcon() {
        val rom = bytes(0x3000)
        fun put(off: Int, v: Int) { for (i in 0..3) rom[off + i] = (v shr (8 * i)).toByte() }
        put(0x20, 0x400); put(0x2C, 0x300)
        put(0x30, 0x800); put(0x3C, 0x120)
        put(0x68, 0x1000)
        val expected = md5(
            rom.copyOfRange(0, 0x160), rom.copyOfRange(0x400, 0x700), rom.copyOfRange(0x800, 0x920), rom.copyOfRange(0x1000, 0x1A00),
        )
        assertEquals(expected, hash(18, "Game.nds", rom))

        put(0x68, 0)
        assertEquals(md5(rom.copyOfRange(0, 0x160), rom.copyOfRange(0x400, 0x700), rom.copyOfRange(0x800, 0x920)), hash(18, "Game.nds", rom))
    }

    @Test fun arcadeHashesTheFileName() {
        assertEquals(md5("sf2".toByteArray()), hash(27, "sf2.zip", bytes(100)))
    }

    @Test fun zipHashesFirstFile() {
        val body = bytes(32768)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("folder/")); z.closeEntry()
            z.putNextEntry(ZipEntry("folder/Game (USA).nes")); z.write("NES\u001a".toByteArray() + ByteArray(12) + body); z.closeEntry()
            z.putNextEntry(ZipEntry("readme.txt")); z.write("hi".toByteArray()); z.closeEntry()
        }
        assertEquals(md5(body), hash(7, "Game (USA).zip", out.toByteArray()))
    }

    @Test fun discsAndArchivesAreNotHashed() {
        assertNull(hash(12, "Game.bin", bytes(100)))
        assertNull(hash(8, "Game.cue", bytes(100)))
        assertNull(hash(1, "Game.7z", bytes(100)))
    }

    private val index = RaGameIndex.parse(
        """
        [{"Title":"Pokemon Red Version | Pocket Monsters Aka","ID":1,"ConsoleID":4,"NumAchievements":50,"Hashes":["AABBCCDDEEFF00112233445566778899"]},
         {"Title":"~Hack~ Pokemon Red Version Plus","ID":2,"NumAchievements":10,"Hashes":["00000000000000000000000000000002"]},
         {"Title":"Super Mario Bros. 3","ID":3,"NumAchievements":80,"Hashes":[]},
         {"Title":"Super Mario Bros.","ID":4,"NumAchievements":70},
         {"Title":"~Homebrew~ Tetris","ID":5,"NumAchievements":5},
         {"Title":"Super Mario Bros. 3 [Subset - Bonus]","ID":6,"NumAchievements":5}]
        """,
    )

    @Test fun hashLookupIgnoresCase() {
        assertEquals(1, index.byHash("aabbccddeeff00112233445566778899"))
        assertNull(index.byHash("00000000000000000000000000000002"))
    }

    @Test fun titleFallbackUsesAlternates() {
        assertEquals(1, index.byFileName("Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb"))
        assertEquals(1, index.byFileName("Pocket Monsters - Aka (Japan).gb"))
        assertEquals(1, index.byTitle("Pocket Monsters Aka"))
    }

    @Test fun titleFallbackSkipsHacksAndSubsets() {
        assertNull(index.byTitle("Tetris"))
        assertEquals(3, index.byFileName("Super Mario Bros. 3 (USA) (Rev 1).nes"))
        assertEquals(3, index.byTitle("Super Mario Bros. 3"))
        assertEquals(4, index.byFileName("Super Mario Bros. (World).nes"))
    }
}
