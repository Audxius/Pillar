package io.github.audxius.pillar

import io.github.audxius.pillar.data.AppSettings
import io.github.audxius.pillar.data.PillarJson
import io.github.audxius.pillar.data.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun roundTrips() {
        val s = AppSettings(palette = "ocean", gridColumns = 4, customAccent = 0xFF3BA4FF, systemOrder = listOf("snes", "gba"))
        val back = PillarJson.decodeFromString(AppSettings.serializer(), PillarJson.encodeToString(AppSettings.serializer(), s))
        assertEquals(s, back)
    }

    @Test
    fun toleratesUnknownAndMissingFields() {
        val back = PillarJson.decodeFromString(AppSettings.serializer(), """{"palette":"ember","fromTheFuture":42,"themeMode":"NOT_A_MODE"}""")
        assertEquals("ember", back.palette)
        assertEquals(AppSettings().themeMode, back.themeMode)
    }

    @Test
    fun oldLayoutsGainNewWidgets() {
        val old = AppSettings(homeWidgets = listOf(
            io.github.audxius.pillar.data.WidgetConfig(WidgetType.HEADER),
            io.github.audxius.pillar.data.WidgetConfig(WidgetType.QUICK_APPS),
        ))
        val normalized = old.normalized()
        // Removed widgets are dropped, everything else is offered.
        assertEquals(WidgetType.entries.size - io.github.audxius.pillar.data.RemovedWidgets.size, normalized.homeWidgets.size)
        assertTrue(normalized.homeWidgets.none { it.type in io.github.audxius.pillar.data.RemovedWidgets })
        assertTrue(normalized.homeWidgets.drop(1).none { it.enabled && it.type != WidgetType.ALL_GAMES })
    }
}

class SettingsMigrationTest {
    @Test
    fun oldAuroraInstallsMoveToGameArt() {
        val old = PillarJson.decodeFromString(AppSettings.serializer(), """{"background":"AURORA","homeWidgets":[{"type":"QUICK_APPS"}]}""")
        val n = old.normalized()
        assertEquals(io.github.audxius.pillar.data.BackgroundStyle.STYLE, n.background)
        assertEquals(4, n.schema)
        // A user choosing aurora again later keeps it.
        assertEquals(io.github.audxius.pillar.data.BackgroundStyle.AURORA, n.copy(background = io.github.audxius.pillar.data.BackgroundStyle.AURORA).normalized().background)
    }

    @Test
    fun removedLunaStyleFallsBackToGlass() {
        val old = io.github.audxius.pillar.data.PillarJson.decodeFromString(
            io.github.audxius.pillar.data.AppSettings.serializer(), """{"uiStyle":"LUNA","palette":"luna","themeMusic":true}""",
        )
        val s = old.normalized()
        org.junit.Assert.assertEquals(io.github.audxius.pillar.data.UiStyle.GLASS, s.uiStyle)
        org.junit.Assert.assertEquals("aurora", s.palette)
    }
}
