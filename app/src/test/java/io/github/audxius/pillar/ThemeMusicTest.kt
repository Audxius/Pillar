package io.github.audxius.pillar

import io.github.audxius.pillar.data.ThemeSongs
import io.github.audxius.pillar.data.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class ThemeMusicTest {
    @Test
    fun everyStyleRendersAUsableLoop() {
        val dump = System.getenv("PILLAR_WAV_DIR")
        for (style in UiStyle.entries) {
            val started = System.currentTimeMillis()
            val pcm = ThemeSongs.render(style)
            val ms = System.currentTimeMillis() - started
            val seconds = pcm.size / ThemeSongs.RATE.toFloat()
            val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
            val peak = pcm.maxOf { abs(it.toInt()) }
            println("$style: %.1fs loop, rms %.0f, peak %d, rendered in %d ms".format(seconds, rms, peak, ms))
            assertTrue("$style loop length", seconds in 10f..40f)
            assertTrue("$style is audible", rms > 1500)
            assertTrue("$style doesn't clip", peak < 32767)
            if (dump != null) writeWav(File(dump, "${style.name.lowercase()}.wav"), pcm)
        }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(ThemeSongs.RATE); putInt(ThemeSongs.RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        f.writeBytes(h + data)
    }
}
