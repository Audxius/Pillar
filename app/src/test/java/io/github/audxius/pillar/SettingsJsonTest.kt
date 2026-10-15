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
        val old = AppSettings(homeWidgets = listOf(io.github.audxius.pillar.data.WidgetConfig(WidgetType.HEADER)))
        val normalized = old.normalized()
        assertEquals(WidgetType.entries.size, normalized.homeWidgets.size)
        assertTrue(normalized.homeWidgets.drop(1).none { it.enabled })
    }
}
