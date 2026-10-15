package io.github.audxius.pillar

import io.github.audxius.pillar.data.ExtraKind
import io.github.audxius.pillar.data.LaunchCommand
import io.github.audxius.pillar.data.LaunchVars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LaunchCommandTest {

    private val vars = LaunchVars(
        romPath = "/storage/emulated/0/ROMs/snes/Super Game (USA).sfc",
        romRoot = "/storage/emulated/0/ROMs",
        docUri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fsnes%2FSuper%20Game%20(USA).sfc",
        baseName = "Super Game (USA)",
    )

    private fun elements(file: String, tag: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(file))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val systemsXml = "src/main/assets/esde/es_systems.xml"
    private val rulesXml = "src/main/assets/esde/es_find_rules.xml"

    /** Mimics what GameLauncher resolves before parsing. */
    private fun resolve(raw: String, pkg: String): String {
        val id = Regex("%EMULATOR_([^%\\s]+)%").find(raw)!!.groupValues[1]
        return raw.replace("%EMULATOR_$id%", "")
            .replace("%ANDROIDPACKAGE%", pkg)
            .replace("%INTERNALDATA%", "/data/user/0")
            .replace("%EXTERNALDATA%", "/storage/emulated/0")
            .replace(Regex("%INJECT%=(\"[^\"]*\"|\\S+)"), "INJECTED")
    }

    @Test
    fun everyBundledCommandParsesCleanly() {
        val emulators = elements(rulesXml, "emulator").map { it.getAttribute("name") }.toSet()
        val commands = elements(systemsXml, "command").map { it.textContent.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("PLACEHOLDER") && !it.contains("%ANDROIDAPP%") }
        assertTrue("expected hundreds of commands, got ${commands.size}", commands.size > 500)

        for (raw in commands) {
            val id = Regex("%EMULATOR_([^%\\s]+)%").find(raw)?.groupValues?.get(1)
            assertNotNull("No emulator in: $raw", id)
            assertTrue("Emulator $id has no find rule", id in emulators)

            val spec = LaunchCommand.parse(resolve(raw, "com.example.emu"), vars)
            val values = listOfNotNull(spec.action, spec.category, spec.mimeType, spec.data) + spec.extras.map { it.value }
            for (v in values) {
                assertFalse("Unexpanded variable in \"$v\" from: $raw", Regex("%[A-Z_]+%").containsMatchIn(v))
            }
            for (e in spec.extras) {
                assertFalse("Bad extra name '${e.name}' in: $raw", e.name.contains(' ') || e.name.contains('='))
                if (e.kind == ExtraKind.INTEGER) assertNotNull("Non-integer extra in: $raw", e.value.toIntOrNull() ?: if (e.value == "INJECTED") 0 else null)
            }
        }
    }

    @Test
    fun retroArchCommandBuildsCorePaths() {
        val raw = "%EMULATOR_RETROARCH% %EXTRA_CONFIGFILE%=%EXTERNALDATA%/Android/data/%ANDROIDPACKAGE%/files/retroarch.cfg " +
            "%EXTRA_LIBRETRO%=%INTERNALDATA%/%ANDROIDPACKAGE%/cores/snes9x_libretro_android.so %EXTRA_ROM%=%ROM%"
        val spec = LaunchCommand.parse(resolve(raw, "com.retroarch.aarch64"), vars)
        val extras = spec.extras.associate { it.name to it.value }
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", extras["CONFIGFILE"])
        assertEquals("/data/user/0/com.retroarch.aarch64/cores/snes9x_libretro_android.so", extras["LIBRETRO"])
        assertEquals(vars.romPath, extras["ROM"])
        assertEquals(null, spec.action)
        assertTrue(spec.contentUris.isEmpty())
        assertTrue(LaunchCommand.needsRealPath(raw))
    }

    @Test
    fun safCommandsKeepContentUrisIntact() {
        val raw = "%EMULATOR_DOLPHIN% %ACTION%=android.intent.action.MAIN %CATEGORY%=android.intent.category.LEANBACK_LAUNCHER %EXTRA_AutoStartFile%=%ROMSAF%"
        val spec = LaunchCommand.parse(resolve(raw, "org.dolphinemu.dolphinemu"), vars)
        assertEquals("android.intent.action.MAIN", spec.action)
        assertEquals("android.intent.category.LEANBACK_LAUNCHER", spec.category)
        assertEquals(vars.docUri, spec.extras.single().value)
        assertEquals(listOf(vars.docUri), spec.contentUris)
        assertFalse(LaunchCommand.needsRealPath(raw))
    }

    @Test
    fun boolExtrasAndFlags() {
        val raw = "%EMULATOR_DUCKSTATION% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %EXTRABOOL_resumeState%=false %EXTRA_bootPath%=%ROMSAF%"
        val spec = LaunchCommand.parse(resolve(raw, "com.github.stenzek.duckstation"), vars)
        assertTrue(spec.clearTask && spec.clearTop && !spec.noHistory)
        assertEquals(ExtraKind.BOOL, spec.extras.first { it.name == "resumeState" }.kind)
        assertEquals("false", spec.extras.first { it.name == "resumeState" }.value)
        assertEquals(vars.docUri, spec.extras.first { it.name == "bootPath" }.value)
    }

    @Test
    fun quotedExtrasExpandPaths() {
        val raw = "%EMULATOR_MAME4DROID-CURRENT% %ACTION%=android.intent.action.VIEW " +
            "%EXTRA_cli_params%=\"-rompath '%GAMEDIRRAW%;%ROMPATHRAW%/adam' -cart1 '%ROMRAW%'\" %DATA%=adam"
        val spec = LaunchCommand.parse(resolve(raw, "com.seleuco.mame4d2024"), vars)
        assertEquals(
            "-rompath '/storage/emulated/0/ROMs/snes;/storage/emulated/0/ROMs/adam' -cart1 '${vars.romPath}'",
            spec.extras.single().value,
        )
        assertEquals("adam", spec.data)
    }

    @Test
    fun arrayExtras() {
        val raw = "%EMULATOR_VITA3K% %EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita"
        val spec = LaunchCommand.parse(resolve(raw, "org.vita3k.emulator"), vars)
        val e = spec.extras.single()
        assertEquals(ExtraKind.ARRAY, e.kind)
        assertEquals("AppStartParameters", e.name)
        assertEquals("-r,INJECTED", e.value)
    }
}
