package io.github.audxius.pillar.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Menu sound effects. */
enum class Sfx { SELECT, BACK, MOVE, TOGGLE }

/**
 * Short menu sounds in each style's flavor (glassy, bubbly, digital, soft, clicky, 8-bit),
 * synthesized on the device the first time a style needs them and played through a SoundPool.
 */
class UiSounds(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = ConcurrentHashMap<String, Int>()
    private val dir = File(context.cacheDir, "sfx").apply { mkdirs() }

    fun play(style: UiStyle, sfx: Sfx) {
        val key = "${style.name}-${sfx.name}-v1"
        val id = ids.getOrPut(key) {
            val f = File(dir, "$key.wav")
            if (!f.exists()) runCatching { f.writeBytes(wav(SfxSynth.render(style, sfx))) }
            pool.load(f.path, 1)
        }
        // The first play of a sound may land before it's loaded; that one is silently skipped.
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    /** Loads a style's sounds ahead of time so the first tap isn't silent. */
    fun warmUp(style: UiStyle) = Sfx.entries.forEach { sfx ->
        val key = "${style.name}-${sfx.name}-v1"
        if (!ids.containsKey(key)) {
            val f = File(dir, "$key.wav")
            if (!f.exists()) runCatching { f.writeBytes(wav(SfxSynth.render(style, sfx))) }
            ids[key] = pool.load(f.path, 1)
        }
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(SfxSynth.RATE); putInt(SfxSynth.RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return h + data
    }

    private companion object { const val VOLUME = 0.55f }
}

/** The sounds themselves: a few tones (and a little noise) with fast envelopes. */
object SfxSynth {
    const val RATE = 44100

    private enum class Wave { SINE, SQUARE, TRIANGLE }

    /** One tone: frequency sliding from [f0] to [f1] over [dur] seconds, starting at [at]. */
    private class Tone(val f0: Double, val f1: Double, val at: Double, val dur: Double, val wave: Wave, val decay: Double, val gain: Double, val bell: Double = 0.0)

    fun render(style: UiStyle, sfx: Sfx): ShortArray {
        val tones = when (style) {
            // Liquid Glass: glassy "tinks" with a bell-like overtone.
            UiStyle.GLASS, UiStyle.LUNA, UiStyle.POCKET -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(1760.0, 1760.0, 0.0, 0.12, Wave.SINE, 32.0, 0.5, 0.35), Tone(2637.0, 2637.0, 0.035, 0.12, Wave.SINE, 30.0, 0.35, 0.3))
                Sfx.BACK -> listOf(Tone(2349.0, 2349.0, 0.0, 0.1, Wave.SINE, 34.0, 0.4, 0.3), Tone(1568.0, 1568.0, 0.035, 0.12, Wave.SINE, 30.0, 0.45, 0.3))
                Sfx.MOVE -> listOf(Tone(3136.0, 3136.0, 0.0, 0.04, Wave.SINE, 90.0, 0.25, 0.2))
                Sfx.TOGGLE -> listOf(Tone(2093.0, 2093.0, 0.0, 0.08, Wave.SINE, 45.0, 0.4, 0.3))
            }
            // Aero: round, bubbly pops.
            UiStyle.AERO -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(520.0, 1300.0, 0.0, 0.09, Wave.SINE, 28.0, 0.6))
                Sfx.BACK -> listOf(Tone(1300.0, 520.0, 0.0, 0.09, Wave.SINE, 28.0, 0.55))
                Sfx.MOVE -> listOf(Tone(900.0, 1500.0, 0.0, 0.035, Wave.SINE, 80.0, 0.3))
                Sfx.TOGGLE -> listOf(Tone(700.0, 1100.0, 0.0, 0.06, Wave.SINE, 40.0, 0.5))
            }
            // Y2K: crisp digital bleeps.
            UiStyle.Y2K -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(988.0, 988.0, 0.0, 0.045, Wave.SQUARE, 25.0, 0.22), Tone(1976.0, 1976.0, 0.045, 0.07, Wave.SQUARE, 30.0, 0.2))
                Sfx.BACK -> listOf(Tone(1976.0, 1976.0, 0.0, 0.045, Wave.SQUARE, 25.0, 0.2), Tone(988.0, 988.0, 0.045, 0.07, Wave.SQUARE, 30.0, 0.22))
                Sfx.MOVE -> listOf(Tone(2637.0, 2637.0, 0.0, 0.02, Wave.SQUARE, 120.0, 0.12))
                Sfx.TOGGLE -> listOf(Tone(1319.0, 1319.0, 0.0, 0.05, Wave.SQUARE, 40.0, 0.2))
            }
            // XMB: soft, rounded tones with a gentle attack.
            UiStyle.XMB -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(1047.0, 1047.0, 0.0, 0.2, Wave.SINE, 14.0, 0.5), Tone(1568.0, 1568.0, 0.0, 0.2, Wave.SINE, 16.0, 0.2))
                Sfx.BACK -> listOf(Tone(784.0, 784.0, 0.0, 0.2, Wave.SINE, 14.0, 0.5))
                Sfx.MOVE -> listOf(Tone(1319.0, 1319.0, 0.0, 0.06, Wave.SINE, 45.0, 0.22))
                Sfx.TOGGLE -> listOf(Tone(1175.0, 1175.0, 0.0, 0.12, Wave.SINE, 22.0, 0.4))
            }
            // Wii: light clicks and a friendly two-note chime.
            UiStyle.WII -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(1047.0, 1047.0, 0.0, 0.07, Wave.TRIANGLE, 30.0, 0.45), Tone(1319.0, 1319.0, 0.05, 0.1, Wave.TRIANGLE, 26.0, 0.45))
                Sfx.BACK -> listOf(Tone(1319.0, 1319.0, 0.0, 0.07, Wave.TRIANGLE, 30.0, 0.45), Tone(988.0, 988.0, 0.05, 0.1, Wave.TRIANGLE, 26.0, 0.45))
                Sfx.MOVE -> listOf(Tone(2400.0, 1800.0, 0.0, 0.015, Wave.TRIANGLE, 160.0, 0.3))
                Sfx.TOGGLE -> listOf(Tone(1568.0, 1568.0, 0.0, 0.06, Wave.TRIANGLE, 40.0, 0.4))
            }
            // Grid: 8-bit square-wave arpeggios.
            UiStyle.GRID -> when (sfx) {
                Sfx.SELECT -> listOf(Tone(523.0, 523.0, 0.0, 0.035, Wave.SQUARE, 8.0, 0.2), Tone(784.0, 784.0, 0.035, 0.035, Wave.SQUARE, 8.0, 0.2), Tone(1047.0, 1047.0, 0.07, 0.05, Wave.SQUARE, 20.0, 0.2))
                Sfx.BACK -> listOf(Tone(1047.0, 1047.0, 0.0, 0.035, Wave.SQUARE, 8.0, 0.2), Tone(784.0, 784.0, 0.035, 0.035, Wave.SQUARE, 8.0, 0.2), Tone(523.0, 523.0, 0.07, 0.05, Wave.SQUARE, 20.0, 0.2))
                Sfx.MOVE -> listOf(Tone(1568.0, 1568.0, 0.0, 0.018, Wave.SQUARE, 30.0, 0.13))
                Sfx.TOGGLE -> listOf(Tone(659.0, 659.0, 0.0, 0.03, Wave.SQUARE, 10.0, 0.2), Tone(988.0, 988.0, 0.03, 0.04, Wave.SQUARE, 20.0, 0.2))
            }
        }
        val end = tones.maxOf { it.at + it.dur } + 0.02
        val n = (end * RATE).toInt()
        val out = DoubleArray(n)
        for (t in tones) {
            val start = (t.at * RATE).toInt()
            val len = (t.dur * RATE).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val x = i.toDouble() / len
                val f = t.f0 + (t.f1 - t.f0) * x
                phase += 2 * PI * f / RATE
                val sec = i.toDouble() / RATE
                // 3 ms attack and a short fade at the very end so nothing clicks.
                val env = minOf(1.0, sec / 0.003) * exp(-sec * t.decay) * minOf(1.0, (len - i) / (0.004 * RATE))
                val v = when (t.wave) {
                    Wave.SINE -> sin(phase) + t.bell * sin(phase * 2.76) * exp(-sec * 40)
                    Wave.SQUARE -> if (sin(phase) >= 0) 0.8 else -0.8
                    Wave.TRIANGLE -> 2 / PI * kotlin.math.asin(sin(phase))
                }
                if (start + i < n) out[start + i] += v * env * t.gain
            }
        }
        // Wii's hover has a tiny bit of noise for a "click".
        if (style == UiStyle.WII && sfx == Sfx.MOVE) {
            val rnd = Random(1)
            for (i in 0 until minOf(n, (0.006 * RATE).toInt())) out[i] += (rnd.nextDouble() * 2 - 1) * 0.12 * (1 - i / (0.006 * RATE))
        }
        return ShortArray(n) { (out[it].coerceIn(-1.0, 1.0) * 32000).toInt().toShort() }
    }
}
