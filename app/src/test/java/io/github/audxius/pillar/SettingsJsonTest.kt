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
        // The removed Quick apps widget is dropped, everything else is offered.
        assertEquals(WidgetType.entries.size - 1, normalized.homeWidgets.size)
        assertTrue(normalized.homeWidgets.none { it.type == WidgetType.QUICK_APPS })
        assertTrue(normalized.homeWidgets.drop(1).none { it.enabled })
    }
}

class SettingsMigrationTest {
    @Test
    fun oldAuroraInstallsMoveToGameArt() {
        val old = PillarJson.decodeFromString(AppSettings.serializer(), """{"background":"AURORA","homeWidgets":[{"type":"QUICK_APPS"}]}""")
        val n = old.normalized()
        assertEquals(io.github.audxius.pillar.data.BackgroundStyle.ART, n.background)
        assertEquals(2, n.schema)
        // A user choosing aurora again later keeps it.
        assertEquals(io.github.audxius.pillar.data.BackgroundStyle.AURORA, n.copy(background = io.github.audxius.pillar.data.BackgroundStyle.AURORA).normalized().background)
    }
}
