package io.github.audxius.pillar

import io.github.audxius.pillar.data.DatIndex
import io.github.audxius.pillar.data.RomChecksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GameIdentityTest {
    private fun crc(b: ByteArray) = "%08x".format(CRC32().apply { update(b) }.value)
    private fun rom(n: Int, seed: Int = 7) = ByteArray(n) { ((it * 31 + seed) and 0xFF).toByte() }

    private val dat = """
        clrmamepro (
        	name "Test"
        )

        game (
        	name "Sonic & Knuckles + Sonic The Hedgehog 3 (World)"
        	region "World"
        	rom ( name "Sonic & Knuckles + Sonic The Hedgehog 3 (World).md" size 4096 crc %s md5 0123456789ABCDEF0123456789ABCDEF )
        )

        game (
        	name "Headerless Game (USA)"
        	rom ( name "Headerless Game (USA).sfc" size 2048 crc %s )
        )
    """.trimIndent()

    @Test
    fun datLookupByCrcAndMd5() {
        val md = rom(4096)
        val snes = rom(2048, 3)
        val index = DatIndex.parseDat(dat.format(crc(md).uppercase(), crc(snes).uppercase()).lineSequence())
        val sums = RomChecksum.compute("sonic3knuckles.md", md.size.toLong()) { md.inputStream() }!!
        assertEquals("Sonic & Knuckles + Sonic The Hedgehog 3 (World)", index.lookup(sums))
        // The compact cache round-trips.
        val cached = DatIndex.parseCache(index.lines())
        assertEquals("Sonic & Knuckles + Sonic The Hedgehog 3 (World)", cached.lookup(sums))
    }

    @Test
    fun snesCopierHeaderIsSkipped() {
        val body = rom(2048, 3)
        val withHeader = ByteArray(512) + body
        val sums = RomChecksum.compute("game.smc", withHeader.size.toLong()) { withHeader.inputStream() }!!
        assertEquals(crc(body), sums.crcs[1])
    }

    @Test
    fun nesKeepsFullFileAndHeaderlessSums() {
        val body = rom(1024)
        val file = "NES\u001a".toByteArray() + ByteArray(12) + body
        val sums = RomChecksum.compute("game.nes", file.size.toLong()) { file.inputStream() }!!
        assertEquals(listOf(crc(file), crc(body)), sums.crcs)
    }

    @Test
    fun n64ByteOrdersMatchBigEndian() {
        val z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40) + rom(4092)
        val v64 = ByteArray(z64.size).also { for (i in z64.indices step 2) { it[i] = z64[i + 1]; it[i + 1] = z64[i] } }
        val n64 = ByteArray(z64.size).also { for (i in z64.indices step 4) for (j in 0..3) it[i + j] = z64[i + 3 - j] }
        val want = crc(z64)
        assertEquals(want, RomChecksum.compute("a.z64", z64.size.toLong()) { z64.inputStream() }!!.crcs[0])
        assertEquals(want, RomChecksum.compute("a.v64", v64.size.toLong()) { v64.inputStream() }!!.crcs[0])
        assertEquals(want, RomChecksum.compute("a.n64", n64.size.toLong()) { n64.inputStream() }!!.crcs[0])
    }

    @Test
    fun zipHashesFirstEntry() {
        val inner = rom(3000)
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("Game (USA).gba")); z.write(inner); z.closeEntry() }
        }.toByteArray()
        assertEquals(crc(inner), RomChecksum.compute("game.zip", zip.size.toLong()) { zip.inputStream() }!!.crcs[0])
    }

    @Test
    fun discImagesAreSkipped() {
        assertNull(RomChecksum.compute("game.chd", 10) { ByteArray(10).inputStream() })
    }
}
