package io.github.audxius.pillar

import io.github.audxius.pillar.data.ThumbMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbMatchTest {
    private val snes = listOf(
        "Super Mario World (USA)", "Super Mario World (Japan)", "Super Mario World (Europe) (Rev 1)",
        "Super Mario World 2 - Yoshi's Island (USA)", "Super Mario Kart (USA)",
        "Legend of Zelda, The - A Link to the Past (USA)", "Final Fantasy III (USA)", "Final Fantasy II (USA)",
        "Super Mario World (USA) (Beta)",
    )

    @Test fun exactNoIntroName() = assertEquals("Super Mario World (USA)", ThumbMatch.best("Super Mario World (USA).sfc", snes))

    @Test fun untaggedPrefersUsaOverBetaAndOtherRegions() =
        assertEquals("Super Mario World (USA)", ThumbMatch.best("Super Mario World.sfc", snes))

    @Test fun regionTagIsRespected() =
        assertEquals("Super Mario World (Japan)", ThumbMatch.best("Super Mario World (Japan).sfc", snes))

    @Test fun articleAndPunctuationDifferences() =
        assertEquals("Legend of Zelda, The - A Link to the Past (USA)", ThumbMatch.best("The Legend of Zelda A Link to the Past.smc", snes))

    @Test fun sequelsNeverCrossMatch() {
        assertEquals("Final Fantasy III (USA)", ThumbMatch.best("Final Fantasy 3.sfc", snes))
        assertNull(ThumbMatch.best("Final Fantasy IV.sfc", snes))
    }

    @Test fun looseNamesStillFindTheGame() {
        val gba = listOf("Pokemon - Emerald Version (USA, Europe)", "Pokemon - Ruby Version (USA)")
        assertEquals("Pokemon - Emerald Version (USA, Europe)", ThumbMatch.best("Pokemon Emerald.gba", gba))
        val md = listOf("Sonic The Hedgehog 2 (World)", "Sonic The Hedgehog (USA, Europe)")
        assertEquals("Sonic The Hedgehog 2 (World)", ThumbMatch.best("sonic_2.md", md))
    }

    @Test fun vagueNamesDontGuess() = assertNull(ThumbMatch.best("Zelda.sfc", snes))

    @Test fun dotsInTitlesSurvive() =
        assertEquals("Super Mario Bros. 3 (USA)", ThumbMatch.best("Super Mario Bros. 3.nes", listOf("Super Mario Bros. 3 (USA)", "Super Mario Bros. (World)")))
}
