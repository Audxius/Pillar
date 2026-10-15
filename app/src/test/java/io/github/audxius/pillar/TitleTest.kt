package io.github.audxius.pillar

import io.github.audxius.pillar.data.LibraryRepository
import io.github.audxius.pillar.data.RaClient
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleTest {
    @Test
    fun cleansNoIntroNames() {
        assertEquals("The Legend of Zelda: A Link to the Past", LibraryRepository.cleanTitle("Legend of Zelda, The - A Link to the Past (USA).sfc"))
        assertEquals("Super Mario World", LibraryRepository.cleanTitle("Super Mario World (USA) [!].sfc"))
        assertEquals("The Lost Vikings", LibraryRepository.cleanTitle("Lost Vikings, The (Europe) (En,Fr,De).sfc"))
        assertEquals("Final Fantasy VII", LibraryRepository.cleanTitle("Final Fantasy VII (USA) (Disc 1).chd"))
        assertEquals("Castlevania", LibraryRepository.cleanTitle("Castlevania.nes"))
        assertEquals("Pokemon Emerald", LibraryRepository.cleanTitle("Pokemon_Emerald.gba"))
        // A name made only of tags falls back to the raw file name.
        assertEquals("(USA)", LibraryRepository.cleanTitle("(USA).zip"))
    }

    @Test
    fun extractsTags() {
        assertEquals(listOf("USA", "Rev 1"), LibraryRepository.tags("Some Game (USA) (Rev 1) [!].zip"))
        assertEquals(emptyList<String>(), LibraryRepository.tags("Some Game.zip"))
    }

    @Test
    fun raTitleNormalizationMatchesNoIntro() {
        val local = RaClient.normalize(LibraryRepository.cleanTitle("Legend of Zelda, The - A Link to the Past (USA).sfc"))
        assertEquals(RaClient.normalize("Legend of Zelda, The: A Link to the Past"), local)
        assertEquals(RaClient.normalize("Sonic the Hedgehog"), RaClient.normalize("Sonic The Hedgehog (USA, Europe)"))
        assertEquals(RaClient.normalize("Mario & Luigi: Superstar Saga"), RaClient.normalize("Mario and Luigi - Superstar Saga"))
    }
}
