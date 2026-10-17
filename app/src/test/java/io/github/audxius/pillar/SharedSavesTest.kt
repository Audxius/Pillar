package io.github.audxius.pillar

import io.github.audxius.pillar.data.SharedSaves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedSavesTest {
    @Test
    fun recognisesConsoleSaveStorage() {
        assertEquals("dreamcast", SharedSaves.systemOf("system/dc/vmu_save_A1.bin"))
        assertEquals("ps2", SharedSaves.systemOf("saves/LRPS2/Mcd001.ps2"))
        assertEquals("ps2", SharedSaves.systemOf("system/pcsx2/memcards/Mcd002.ps2"))
        assertEquals("gc", SharedSaves.systemOf("system/dolphin-emu/User/GC/USA/Card A/01-GALE-SuperSmashBros.gci"))
        assertEquals("wii", SharedSaves.systemOf("system/dolphin-emu/User/Wii/title/00010000/52534245/data/banner.bin"))
        assertEquals("psp", SharedSaves.systemOf("system/PPSSPP/PSP/SAVEDATA/ULUS10041/PARAM.SFO"))
    }

    @Test
    fun neverTouchesBiosOrPerGameSaves() {
        assertFalse(SharedSaves.isSystemSave("system/dc/dc_boot.bin"))
        assertFalse(SharedSaves.isSystemSave("system/scph5501.bin"))
        assertFalse(SharedSaves.isSystemSave("system/pcsx2/bios/ps2-0230a-20080220.bin"))
        assertNull(SharedSaves.systemOf("saves/Flycast/Sonic Adventure (USA).A1.bin"))
        assertTrue(SharedSaves.isSystemSave("system/dc/vmu_save_B1.bin"))
    }

    @Test
    fun perGameStemsPeelExtraSuffixes() {
        assertTrue("Sonic Adventure (USA)" in SharedSaves.stems("Sonic Adventure (USA).A1.bin"))
        assertTrue("Game (USA)" in SharedSaves.stems("Game (USA).state3.png"))
        // Title text with dots stays whole.
        assertEquals(listOf("Super Mario Bros. 3 (USA)"), SharedSaves.stems("Super Mario Bros. 3 (USA).srm"))
    }
}
