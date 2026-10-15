package io.github.audxius.pillar.data

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** One way of launching a system's games, e.g. "Snes9x - Current" via RetroArch. */
data class EmuCommand(val label: String, val raw: String, val custom: Boolean = false) {
    /** The ES-DE emulator id referenced by the command, e.g. RETROARCH. Null for app launches. */
    val emulatorId: String? = Regex("%EMULATOR_([^%\\s]+)%").find(raw)?.groupValues?.get(1)
}

data class SystemDef(
    val id: String,
    val fullName: String,
    val extensions: Set<String>,
    val commands: List<EmuCommand>,
)

/** Visual and cross-service metadata Pillar adds on top of ES-DE's definitions. */
data class SystemMeta(
    val short: String,
    val maker: String? = null,
    val year: Int? = null,
    val color: Long = 0xFF7C5CFF,
    val libretro: String? = null,
    val raConsole: Int? = null,
)

/**
 * System and emulator definitions come from ES-DE's Android configuration (MIT licensed, bundled
 * in assets/esde). That gives Pillar the same 190+ systems and several hundred emulator launch
 * commands ES-DE users rely on.
 */
class SystemCatalog(context: Context) {

    val systems: Map<String, SystemDef>
    /** ES-DE emulator id -> candidate "package/activity" entries, in priority order. */
    val findRules: Map<String, List<String>>

    init {
        systems = context.assets.open("esde/es_systems.xml").use { parseSystems(it) }
        findRules = context.assets.open("esde/es_find_rules.xml").use { parseFindRules(it) }
    }

    operator fun get(id: String): SystemDef? = systems[id]

    fun meta(id: String): SystemMeta = META[id] ?: SystemMeta(
        short = systems[id]?.fullName?.let(::shortFromFullName) ?: id.uppercase(),
        color = colorFromId(id),
    )

    fun fullName(id: String): String = systems[id]?.fullName ?: id

    /** Matches a folder name to a system, accepting ES-DE ids plus common alternative names. */
    fun resolveFolder(name: String): String? {
        val n = name.lowercase().trim()
        if (n in systems) return n
        ALIASES[n]?.let { if (it in systems) return it }
        return null
    }

    /** Picks the local system folder for a RomM platform. */
    fun resolveRommPlatform(slug: String?, fsSlug: String?): String? {
        fsSlug?.let { resolveFolder(it) }?.let { return it }
        slug?.let { s -> ROMM_SLUGS[s]?.let { if (it in systems) return it } }
        slug?.let { resolveFolder(it) }?.let { return it }
        return null
    }

    private fun parseSystems(input: java.io.InputStream): Map<String, SystemDef> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        val out = LinkedHashMap<String, SystemDef>()
        var name = ""; var fullName = ""; var ext = ""
        var commands = mutableListOf<EmuCommand>()
        var label: String? = null
        val cmdText = StringBuilder()
        var tag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "system" -> { name = ""; fullName = ""; ext = ""; commands = mutableListOf() }
                        "command" -> { label = parser.getAttributeValue(null, "label"); cmdText.clear() }
                    }
                }
                XmlPullParser.TEXT -> when (tag) {
                    "name" -> name += parser.text
                    "fullname" -> fullName += parser.text
                    "extension" -> ext += parser.text
                    "command" -> cmdText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "command") {
                        val raw = cmdText.toString().trim()
                        if (raw.isNotEmpty() && !raw.startsWith("PLACEHOLDER")) commands += EmuCommand(label ?: "Default", raw)
                    }
                    if (parser.name == "system" && name.isNotBlank()) {
                        val id = name.trim()
                        out[id] = SystemDef(
                            id = id,
                            fullName = fullName.trim(),
                            extensions = ext.split(' ', '\n', '\t').map { it.trim().lowercase().removePrefix(".") }
                                .filter { it.isNotEmpty() }.toSet(),
                            commands = commands.toList(),
                        )
                    }
                    tag = null
                }
            }
        }
        return out
    }

    private fun parseFindRules(input: java.io.InputStream): Map<String, List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = LinkedHashMap<String, MutableList<String>>()
        var emulator: String? = null
        var inEntry = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "emulator" -> emulator = parser.getAttributeValue(null, "name")
                    "entry" -> inEntry = true
                }
                XmlPullParser.TEXT -> if (inEntry && emulator != null) {
                    val entry = parser.text.trim()
                    if (entry.isNotEmpty()) out.getOrPut(emulator) { mutableListOf() } += entry
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") inEntry = false
            }
        }
        return out
    }

    companion object {
        private fun shortFromFullName(full: String): String =
            full.substringBefore('(').trim().let { if (it.length <= 14) it else it.split(' ').takeLast(2).joinToString(" ") }

        fun colorFromId(id: String): Long {
            // Stable, pleasant hue per unknown system.
            val hue = (id.hashCode().toLong() and 0xFFFFFFFFL) % 360
            val c = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.9f))
            return c.toLong() and 0xFFFFFFFFL
        }

        val ALIASES = mapOf(
            "ps1" to "psx", "playstation" to "psx", "ps" to "psx",
            "playstation2" to "ps2", "playstation3" to "ps3", "vita" to "psvita",
            "gamecube" to "gc", "ngc" to "gc", "gcn" to "gc",
            "sms" to "mastersystem", "md" to "megadrive", "gen" to "genesis",
            "gg" to "gamegear", "32x" to "sega32x", "scd" to "segacd", "mcd" to "megacd",
            "dc" to "dreamcast", "3ds" to "n3ds", "ds" to "nds",
            "pce" to "pcengine", "turbografx16" to "tg16", "turbografx" to "tg16",
            "pcecd" to "pcenginecd", "tgcd" to "tg-cd", "sgx" to "supergrafx",
            "fc" to "famicom", "sfam" to "sfc", "superfamicom" to "sfc", "supernes" to "snes",
            "gameboy" to "gb", "gameboycolor" to "gbc", "gameboyadvance" to "gba",
            "vb" to "virtualboy", "pokemonmini" to "pokemini", "pm" to "pokemini",
            "lynx" to "atarilynx", "jaguar" to "atarijaguar", "jaguarcd" to "atarijaguarcd",
            "a2600" to "atari2600", "a5200" to "atari5200", "a7800" to "atari7800",
            "ws" to "wonderswan", "wsc" to "wonderswancolor",
            "neogeopocket" to "ngp", "neogeopocketcolor" to "ngpc",
            "sg1000" to "sg-1000", "zxs" to "zxspectrum", "spectrum" to "zxspectrum",
            "cpc" to "amstradcpc", "x68k" to "x68000", "ns" to "switch", "nsw" to "switch",
            "fba" to "fbneo", "neo-geo" to "neogeo",
        )

        /** RomM platform slugs (backend/utils/platform_slugs.py) -> ES-DE system ids. */
        val ROMM_SLUGS = mapOf(
            "3do" to "3do", "acpc" to "amstradcpc", "amiga" to "amiga", "arcade" to "arcade",
            "arduboy" to "arduboy", "atari-st" to "atarist", "atari2600" to "atari2600",
            "atari5200" to "atari5200", "atari7800" to "atari7800", "c64" to "c64",
            "colecovision" to "colecovision", "cps1" to "cps1", "cps2" to "cps2", "cps3" to "cps3",
            "dc" to "dreamcast", "dos" to "dos", "famicom" to "famicom", "fds" to "fds",
            "gamegear" to "gamegear", "gb" to "gb", "gba" to "gba", "gbc" to "gbc",
            "genesis" to "genesis", "intellivision" to "intellivision", "jaguar" to "atarijaguar",
            "lynx" to "atarilynx", "msx" to "msx", "msx2" to "msx2", "3ds" to "n3ds", "n64" to "n64",
            "64dd" to "n64dd", "nds" to "nds", "neo-geo-cd" to "neogeocd", "neo-geo-pocket" to "ngp",
            "neo-geo-pocket-color" to "ngpc", "neogeoaes" to "neogeo", "neogeomvs" to "neogeo",
            "nes" to "nes", "ngc" to "gc", "odyssey-2" to "odyssey2", "pc-8800-series" to "pc88",
            "pc-fx" to "pcfx", "pokemon-mini" to "pokemini", "ps2" to "ps2", "ps3" to "ps3",
            "psp" to "psp", "psvita" to "psvita", "psx" to "psx", "satellaview" to "satellaview",
            "saturn" to "saturn", "scummvm" to "scummvm", "sega32" to "sega32x", "segacd" to "segacd",
            "sfam" to "sfc", "sg1000" to "sg-1000", "sharp-x68000" to "x68000", "sms" to "mastersystem",
            "snes" to "snes", "sufami-turbo" to "sufami", "supergrafx" to "supergrafx",
            "supervision" to "supervision", "switch" to "switch", "tg16" to "tg16", "tic-80" to "tic80",
            "turbografx-cd" to "tg-cd", "vectrex" to "vectrex", "videopac-g7400" to "videopac",
            "virtualboy" to "virtualboy", "wii" to "wii", "wiiu" to "wiiu", "wonderswan" to "wonderswan",
            "wonderswan-color" to "wonderswancolor", "xbox" to "xbox", "zxs" to "zxspectrum",
        )

        // Libretro thumbnail folder names match RomM's libretro_handler.py table.
        // RetroAchievements console ids come from rcheevos' rc_consoles.h.
        val META: Map<String, SystemMeta> = mapOf(
            "3do" to SystemMeta("3DO", "Panasonic", 1993, 0xFFE8B04B, "The 3DO Company - 3DO", 43),
            "amiga" to SystemMeta("Amiga", "Commodore", 1985, 0xFFE85D3C, "Commodore - Amiga", 35),
            "amstradcpc" to SystemMeta("CPC", "Amstrad", 1984, 0xFFD33A3A, "Amstrad - CPC", 37),
            "arcade" to SystemMeta("Arcade", "Various", null, 0xFFFF4D6D, "MAME", 27),
            "mame" to SystemMeta("MAME", "Various", null, 0xFFFF4D6D, "MAME", 27),
            "fbneo" to SystemMeta("FBNeo", "Various", null, 0xFFFF6B4D, "FBNeo - Arcade Games", 27),
            "cps1" to SystemMeta("CPS-1", "Capcom", 1988, 0xFFF5B83D, "FBNeo - Arcade Games", 27),
            "cps2" to SystemMeta("CPS-2", "Capcom", 1993, 0xFF3D8BF5, "FBNeo - Arcade Games", 27),
            "cps3" to SystemMeta("CPS-3", "Capcom", 1996, 0xFF9B5CF5, "FBNeo - Arcade Games", 27),
            "neogeo" to SystemMeta("Neo Geo", "SNK", 1990, 0xFFE0B43A, "SNK - Neo Geo", 27),
            "naomi" to SystemMeta("NAOMI", "Sega", 1998, 0xFFF27B35, "Sega - Naomi", 27),
            "atomiswave" to SystemMeta("Atomiswave", "Sammy", 2003, 0xFF35B6F2, "Atomiswave", 27),
            "atari2600" to SystemMeta("2600", "Atari", 1977, 0xFFC8763B, "Atari - 2600", 25),
            "atari5200" to SystemMeta("5200", "Atari", 1982, 0xFF5B7BD5, "Atari - 5200", 50),
            "atari7800" to SystemMeta("7800", "Atari", 1986, 0xFFD5655B, "Atari - 7800", 51),
            "atarijaguar" to SystemMeta("Jaguar", "Atari", 1993, 0xFFD9312B, "Atari - Jaguar", 17),
            "atarijaguarcd" to SystemMeta("Jaguar CD", "Atari", 1995, 0xFFB52A25, "Atari - Jaguar", 77),
            "atarilynx" to SystemMeta("Lynx", "Atari", 1989, 0xFFE2A33B, "Atari - Lynx", 13),
            "atarist" to SystemMeta("ST", "Atari", 1985, 0xFF7C8B99, "Atari - ST", 36),
            "c64" to SystemMeta("C64", "Commodore", 1982, 0xFF8A7DD8, "Commodore - 64", 30),
            "colecovision" to SystemMeta("Coleco", "Coleco", 1982, 0xFF4A6BD6, "Coleco - ColecoVision", 44),
            "dos" to SystemMeta("DOS", "IBM PC", 1981, 0xFF5A9F6A, "DOS", 26),
            "dreamcast" to SystemMeta("Dreamcast", "Sega", 1998, 0xFFF2701D, "Sega - Dreamcast", 40),
            "famicom" to SystemMeta("Famicom", "Nintendo", 1983, 0xFFC8102E, "Nintendo - Nintendo Entertainment System", 7),
            "fds" to SystemMeta("FDS", "Nintendo", 1986, 0xFFD6A13B, "Nintendo - Family Computer Disk System", 81),
            "gameandwatch" to SystemMeta("G&W", "Nintendo", 1980, 0xFFC9A227, "Handheld Electronic Game", 60),
            "gamegear" to SystemMeta("Game Gear", "Sega", 1990, 0xFF2D9CDB, "Sega - Game Gear", 15),
            "gb" to SystemMeta("Game Boy", "Nintendo", 1989, 0xFF8BAC0F, "Nintendo - Game Boy", 4),
            "gba" to SystemMeta("GBA", "Nintendo", 2001, 0xFF5B3FD9, "Nintendo - Game Boy Advance", 5),
            "gbc" to SystemMeta("GBC", "Nintendo", 1998, 0xFF18B6A6, "Nintendo - Game Boy Color", 6),
            "gc" to SystemMeta("GameCube", "Nintendo", 2001, 0xFF6A5ACD, "Nintendo - GameCube", 16),
            "genesis" to SystemMeta("Genesis", "Sega", 1989, 0xFF3B6FE0, "Sega - Mega Drive - Genesis", 1),
            "megadrive" to SystemMeta("Mega Drive", "Sega", 1988, 0xFF3B6FE0, "Sega - Mega Drive - Genesis", 1),
            "megadrivejp" to SystemMeta("Mega Drive", "Sega", 1988, 0xFF3B6FE0, "Sega - Mega Drive - Genesis", 1),
            "mastersystem" to SystemMeta("Master System", "Sega", 1985, 0xFFD7263D, "Sega - Master System - Mark III", 11),
            "mark3" to SystemMeta("Mark III", "Sega", 1985, 0xFFD7263D, "Sega - Master System - Mark III", 11),
            "segacd" to SystemMeta("Sega CD", "Sega", 1991, 0xFF4C8CE8, "Sega - Mega-CD - Sega CD", 9),
            "megacd" to SystemMeta("Mega-CD", "Sega", 1991, 0xFF4C8CE8, "Sega - Mega-CD - Sega CD", 9),
            "megacdjp" to SystemMeta("Mega-CD", "Sega", 1991, 0xFF4C8CE8, "Sega - Mega-CD - Sega CD", 9),
            "sega32x" to SystemMeta("32X", "Sega", 1994, 0xFFE04646, "Sega - 32X", 10),
            "saturn" to SystemMeta("Saturn", "Sega", 1994, 0xFF6B7BD8, "Sega - Saturn", 39),
            "saturnjp" to SystemMeta("Saturn", "Sega", 1994, 0xFF6B7BD8, "Sega - Saturn", 39),
            "sg-1000" to SystemMeta("SG-1000", "Sega", 1983, 0xFFB0413E, "Sega - SG-1000", 33),
            "msx" to SystemMeta("MSX", "Microsoft", 1983, 0xFF6CA0DC, "Microsoft - MSX", 29),
            "msx1" to SystemMeta("MSX", "Microsoft", 1983, 0xFF6CA0DC, "Microsoft - MSX", 29),
            "msx2" to SystemMeta("MSX2", "Microsoft", 1985, 0xFF5C8FD0, "Microsoft - MSX2", 29),
            "n3ds" to SystemMeta("3DS", "Nintendo", 2011, 0xFFD12E2E, "Nintendo - Nintendo 3DS", 62),
            "n64" to SystemMeta("N64", "Nintendo", 1996, 0xFF2E9E48, "Nintendo - Nintendo 64", 2),
            "n64dd" to SystemMeta("64DD", "Nintendo", 1999, 0xFF2E7E48, "Nintendo - Nintendo 64DD", 2),
            "nds" to SystemMeta("DS", "Nintendo", 2004, 0xFF5A8DEE, "Nintendo - Nintendo DS", 18),
            "nes" to SystemMeta("NES", "Nintendo", 1985, 0xFFC8102E, "Nintendo - Nintendo Entertainment System", 7),
            "snes" to SystemMeta("SNES", "Nintendo", 1990, 0xFF7B5EA7, "Nintendo - Super Nintendo Entertainment System", 3),
            "snesna" to SystemMeta("SNES", "Nintendo", 1991, 0xFF7B5EA7, "Nintendo - Super Nintendo Entertainment System", 3),
            "sfc" to SystemMeta("Super Famicom", "Nintendo", 1990, 0xFFB64AC8, "Nintendo - Super Nintendo Entertainment System", 3),
            "satellaview" to SystemMeta("Satellaview", "Nintendo", 1995, 0xFF8E6EC8, "Nintendo - Satellaview", 3),
            "sufami" to SystemMeta("Sufami Turbo", "Bandai", 1996, 0xFF8E6EC8, "Nintendo - Sufami Turbo", 3),
            "virtualboy" to SystemMeta("Virtual Boy", "Nintendo", 1995, 0xFFD0021B, "Nintendo - Virtual Boy", 28),
            "pokemini" to SystemMeta("Poké Mini", "Nintendo", 2001, 0xFFF2C14E, "Nintendo - Pokemon Mini", 24),
            "wii" to SystemMeta("Wii", "Nintendo", 2006, 0xFF3FB7E8, "Nintendo - Wii", 19),
            "wiiu" to SystemMeta("Wii U", "Nintendo", 2012, 0xFF009AC7, "Nintendo - Wii U", 20),
            "switch" to SystemMeta("Switch", "Nintendo", 2017, 0xFFE60012, null, null),
            "ngp" to SystemMeta("NGP", "SNK", 1998, 0xFF4E5D6C, "SNK - Neo Geo Pocket", 14),
            "ngpc" to SystemMeta("NGPC", "SNK", 1999, 0xFF3D7DD8, "SNK - Neo Geo Pocket Color", 14),
            "neogeocd" to SystemMeta("Neo Geo CD", "SNK", 1994, 0xFFD0A030, "SNK - Neo Geo CD", 56),
            "odyssey2" to SystemMeta("Odyssey²", "Magnavox", 1978, 0xFF8A6D3B, "Magnavox - Odyssey2", 23),
            "videopac" to SystemMeta("Videopac", "Philips", 1978, 0xFF8A6D3B, "Magnavox - Odyssey2", 23),
            "pcengine" to SystemMeta("PC Engine", "NEC", 1987, 0xFFF25C54, "NEC - PC Engine - TurboGrafx 16", 8),
            "tg16" to SystemMeta("TG-16", "NEC", 1989, 0xFFF25C54, "NEC - PC Engine - TurboGrafx 16", 8),
            "pcenginecd" to SystemMeta("PCE CD", "NEC", 1988, 0xFFE0524A, "NEC - PC Engine CD - TurboGrafx-CD", 76),
            "tg-cd" to SystemMeta("TG-CD", "NEC", 1989, 0xFFE0524A, "NEC - PC Engine CD - TurboGrafx-CD", 76),
            "supergrafx" to SystemMeta("SuperGrafx", "NEC", 1989, 0xFFC94C4C, "NEC - PC Engine SuperGrafx", 8),
            "pcfx" to SystemMeta("PC-FX", "NEC", 1994, 0xFF9C6ADE, "NEC - PC-FX", 49),
            "pc88" to SystemMeta("PC-88", "NEC", 1981, 0xFF7E8C9C, null, 47),
            "pc98" to SystemMeta("PC-98", "NEC", 1982, 0xFF7E8C9C, "NEC - PC-98", 48),
            "psx" to SystemMeta("PlayStation", "Sony", 1994, 0xFFA8A8C8, "Sony - PlayStation", 12),
            "ps2" to SystemMeta("PS2", "Sony", 2000, 0xFF2350C8, "Sony - PlayStation 2", 21),
            "ps3" to SystemMeta("PS3", "Sony", 2006, 0xFF4A55A0, "Sony - PlayStation 3", 82),
            "psp" to SystemMeta("PSP", "Sony", 2004, 0xFF7A8AA0, "Sony - PlayStation Portable", 41),
            "psvita" to SystemMeta("PS Vita", "Sony", 2011, 0xFF1E4FD8, "Sony - PlayStation Vita", null),
            "scummvm" to SystemMeta("ScummVM", null, 2001, 0xFF4CAF50, "ScummVM", null),
            "vectrex" to SystemMeta("Vectrex", "GCE", 1982, 0xFF607D8B, "GCE - Vectrex", 46),
            "intellivision" to SystemMeta("Intellivision", "Mattel", 1979, 0xFFB08A4F, "Mattel - Intellivision", 45),
            "wonderswan" to SystemMeta("WonderSwan", "Bandai", 1999, 0xFF8E9AA6, "Bandai - WonderSwan", 53),
            "wonderswancolor" to SystemMeta("WS Color", "Bandai", 2000, 0xFF4FA3D9, "Bandai - WonderSwan Color", 53),
            "x68000" to SystemMeta("X68000", "Sharp", 1987, 0xFF6D7F92, "Sharp - X68000", 52),
            "zxspectrum" to SystemMeta("ZX Spectrum", "Sinclair", 1982, 0xFFE94F37, "Sinclair - ZX Spectrum", 59),
            "zx81" to SystemMeta("ZX81", "Sinclair", 1981, 0xFF8F8F8F, "Sinclair - ZX 81", 31),
            "xbox" to SystemMeta("Xbox", "Microsoft", 2001, 0xFF52B043, "Microsoft - XBOX", 22),
            "arduboy" to SystemMeta("Arduboy", "Arduboy", 2015, 0xFF00B7C3, "Arduboy Inc - Arduboy", 71),
            "channelf" to SystemMeta("Channel F", "Fairchild", 1976, 0xFFB5651D, "Fairchild - Channel F", 57),
            "supervision" to SystemMeta("Supervision", "Watara", 1992, 0xFF7A8C5A, "Watara - Supervision", 63),
            "megaduck" to SystemMeta("Mega Duck", "Welback", 1993, 0xFFE8C547, "Welback - Mega Duck", 69),
            "tic80" to SystemMeta("TIC-80", null, 2017, 0xFF41A6F6, "TIC-80", 65),
            "wasm4" to SystemMeta("WASM-4", null, 2021, 0xFF7B68EE, "WASM-4", 72),
            "uzebox" to SystemMeta("Uzebox", null, 2008, 0xFF6D8B74, "Uzebox", 80),
            "pico8" to SystemMeta("PICO-8", "Lexaloffle", 2015, 0xFFFF004D, null, null),
            "ports" to SystemMeta("Ports", null, null, 0xFF26A69A, null, null),
            "androidgames" to SystemMeta("Android", "Google", 2008, 0xFF3DDC84, null, null),
            "androidapps" to SystemMeta("Apps", "Google", 2008, 0xFF3DDC84, null, null),
        )
    }
}
