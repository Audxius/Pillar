package io.github.audxius.pillar

import io.github.audxius.pillar.data.BoxArtMatcher
import io.github.audxius.pillar.data.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.zip.ZipFile

class BoxArtTest {
    private fun game(id: String, system: String, file: String) = Game(id, system, file.substringBefore(" ("), file, "", "")

    private val xml = """
        <?xml version="1.0" standalone="yes"?>
        <LaunchBox>
          <Game>
            <Name>Super Metroid</Name>
            <DatabaseID>10</DatabaseID>
            <Platform>Super Nintendo Entertainment System</Platform>
          </Game>
          <Game>
            <Name>Super Metroid</Name>
            <DatabaseID>99</DatabaseID>
            <Platform>Windows</Platform>
          </Game>
          <Game>
            <Name>Mother 2</Name>
            <DatabaseID>11</DatabaseID>
            <Platform>Super Nintendo Entertainment System</Platform>
          </Game>
          <GameAlternateName>
            <AlternateName>EarthBound</AlternateName>
            <DatabaseID>11</DatabaseID>
            <Region>North America</Region>
          </GameAlternateName>
          <GameImage>
            <DatabaseID>10</DatabaseID>
            <FileName>cart-jp.png</FileName>
            <Type>Cart - Front</Type>
            <Region>Japan</Region>
          </GameImage>
          <GameImage>
            <DatabaseID>10</DatabaseID>
            <FileName>cart-us.png</FileName>
            <Type>Cart - Front</Type>
            <Region>North America</Region>
          </GameImage>
          <GameImage>
            <DatabaseID>99</DatabaseID>
            <FileName>pc-disc.png</FileName>
            <Type>Disc</Type>
          </GameImage>
          <GameImage>
            <DatabaseID>11</DatabaseID>
            <FileName>eb-cart.jpg</FileName>
            <Type>Cart - Front</Type>
          </GameImage>
        </LaunchBox>
    """.trimIndent()

    @Test
    fun matchesByPlatformNameAndRegion() {
        val games = listOf(game("sm", "snes", "Super Metroid (USA).sfc"), game("eb", "snes", "EarthBound (USA).sfc"))
        val art = BoxArtMatcher.match(xml.reader().buffered(), BoxArtMatcher.wantedFor(games, emptyMap()))
        assertEquals(BoxArtMatcher.IMAGE_BASE + "cart-us.png", art["sm"]?.cart)
        // The Windows release's disc must not leak onto the SNES game.
        assertNull(art["sm"]?.disc)
        assertEquals(BoxArtMatcher.IMAGE_BASE + "eb-cart.jpg", art["eb"]?.cart)
    }

    /** Runs against the real database when PILLAR_LAUNCHBOX_ZIP points at a downloaded Metadata.zip. */
    @Test
    fun realDatabase() {
        val path = System.getenv("PILLAR_LAUNCHBOX_ZIP") ?: return
        val games = listOf(
            game("sm", "snes", "Super Metroid (Japan, USA) (En,Ja).sfc"),
            game("ff7", "psx", "Final Fantasy VII (USA) (Disc 1).chd"),
            game("s3k", "genesis", "Sonic & Knuckles + Sonic The Hedgehog 3 (World).md"),
            game("sotn", "psx", "Castlevania - Symphony of the Night (USA).chd"),
            game("smb3", "nes", "Super Mario Bros. 3 (USA).nes"),
        )
        val start = System.currentTimeMillis()
        ZipFile(path).use { z ->
            val art = BoxArtMatcher.match(z.getInputStream(z.getEntry("Metadata.xml")).bufferedReader(), BoxArtMatcher.wantedFor(games, emptyMap()))
            println("matched ${art.size} in ${System.currentTimeMillis() - start} ms")
            art.forEach { (k, v) -> println("$k: $v") }
        }
    }
}
