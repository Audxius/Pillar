package io.github.audxius.pillar.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Original background loops for each style, synthesized on the device: no audio files, nothing
 * borrowed. Each loop is rendered once (about a second) and played gaplessly.
 */
object ThemeSongs {
    const val RATE = 22050

    enum class Inst { EPIANO, BELL, PAD, PLUCK, SAW, BASS, SUB }
    enum class Drum { KICK, SNARE, HAT, OPEN_HAT, SHAKER, RIM }

    class Builder(val bpm: Int, val bars: Int) {
        val beats get() = bars * 4
        val notes = ArrayList<Triple<Inst, FloatArray, Int>>() // (inst, [start, length, velocity], midi)
        val drums = ArrayList<Triple<Drum, Float, Float>>()
        fun note(i: Inst, midi: Int, beat: Float, len: Float, vel: Float = 0.6f) { notes += Triple(i, floatArrayOf(beat, len, vel), midi) }
        fun chord(i: Inst, midis: IntArray, beat: Float, len: Float, vel: Float = 0.5f) = midis.forEach { note(i, it, beat, len, vel) }
        fun drum(d: Drum, beat: Float, vel: Float = 0.6f) { drums += Triple(d, beat, vel) }
    }

    fun build(style: UiStyle): Builder = when (style) {
        UiStyle.GLASS -> lofi()
        UiStyle.AERO -> aero()
        UiStyle.Y2K -> trance()
        UiStyle.XMB -> ambient()
        UiStyle.WII -> bossa()
        UiStyle.LUNA -> calm()
    }

    // Lo-fi: dusty electric piano, lazy swing.
    private fun lofi() = Builder(78, 8).apply {
        val ch = listOf(intArrayOf(53, 57, 60, 64), intArrayOf(52, 55, 59, 62), intArrayOf(50, 53, 57, 60), intArrayOf(48, 52, 55, 59))
        val mel = listOf(72 to 0.5f, 69 to 1.5f, 67 to 2.5f, 64 to 3.5f)
        for (bar in 0 until bars) {
            val c = ch[bar % 4]; val b = bar * 4f
            chord(Inst.EPIANO, c, b, 1.6f, 0.42f); chord(Inst.EPIANO, c, b + 2.5f, 1.2f, 0.32f)
            note(Inst.BASS, c[0] - 12, b, 1.4f, 0.7f); note(Inst.BASS, c[0] - 12, b + 2.75f, 1f, 0.55f)
            drum(Drum.KICK, b, 0.7f); drum(Drum.KICK, b + 2.5f, 0.55f); drum(Drum.SNARE, b + 1, 0.4f); drum(Drum.SNARE, b + 3, 0.4f)
            for (k in 0 until 4) { drum(Drum.HAT, b + k, 0.22f); drum(Drum.HAT, b + k + 0.6f, 0.15f) }
            if (bar >= 4) mel.forEachIndexed { i, (m, at) -> if ((bar + i) % 3 != 0) note(Inst.BELL, m - (bar % 2) * 2, b + at, 0.9f, 0.3f) }
        }
    }

    // Aero: bright, airy pads with glassy bell arpeggios.
    private fun aero() = Builder(92, 8).apply {
        val ch = listOf(intArrayOf(48, 55, 60, 64), intArrayOf(47, 55, 62, 67), intArrayOf(45, 57, 60, 64), intArrayOf(41, 57, 60, 65))
        val arp = intArrayOf(0, 1, 2, 3, 2, 3, 1, 2)
        for (bar in 0 until bars) {
            val c = ch[bar % 4]; val b = bar * 4f
            chord(Inst.PAD, c, b, 4f, 0.28f)
            note(Inst.BASS, c[0] - 12, b, 1.8f, 0.5f); note(Inst.BASS, c[0] - 12, b + 2.5f, 1.4f, 0.4f)
            arp.forEachIndexed { i, k -> note(Inst.BELL, c[k] + 12, b + i * 0.5f, 0.8f, if (i % 2 == 0) 0.32f else 0.22f) }
            drum(Drum.KICK, b, 0.45f); drum(Drum.KICK, b + 2, 0.4f)
            for (k in 0 until 16) drum(Drum.SHAKER, b + k * 0.25f, if (k % 2 == 0) 0.12f else 0.07f)
        }
    }

    // Y2K: trance arpeggios, four on the floor, offbeat bass.
    private fun trance() = Builder(128, 8).apply {
        val ch = listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))
        val pat = intArrayOf(0, 1, 2, 12, 2, 1, 0, 12, 1, 2, 12, 2, 0, 1, 2, 1)
        val lead = listOf(76, 74, 72, 74, 76, 79, 76, 74)
        for (bar in 0 until bars) {
            val c = ch[bar % 4]; val b = bar * 4f
            chord(Inst.PAD, c, b, 4f, 0.16f)
            pat.forEachIndexed { i, k -> note(Inst.SAW, (if (k == 12) c[0] + 12 else c[k]) + 12, b + i * 0.25f, 0.22f, 0.3f) }
            for (k in 0 until 4) {
                drum(Drum.KICK, b + k, 0.85f); drum(Drum.OPEN_HAT, b + k + 0.5f, 0.13f)
                note(Inst.BASS, c[0] - 12, b + k + 0.5f, 0.4f, 0.7f)
            }
            drum(Drum.SNARE, b + 1, 0.45f); drum(Drum.SNARE, b + 3, 0.45f)
            if (bar >= 4) lead.forEachIndexed { i, m -> note(Inst.BELL, m, b + i * 0.5f, 0.45f, 0.28f) }
        }
    }

    // XMB: slow ambient pads and the odd bell, no drums.
    private fun ambient() = Builder(64, 8).apply {
        val ch = listOf(intArrayOf(50, 57, 61, 64, 69), intArrayOf(47, 54, 57, 61, 66), intArrayOf(43, 55, 59, 62, 66), intArrayOf(45, 52, 57, 59, 64))
        val rnd = Random(7)
        for (bar in 0 until bars) {
            val c = ch[(bar / 2) % 4]; val b = bar * 4f
            if (bar % 2 == 0) { chord(Inst.PAD, c, b, 8f, 0.3f); note(Inst.SUB, c[0] - 12, b, 8f, 0.45f) }
            repeat(2) { note(Inst.BELL, c[1 + rnd.nextInt(4)] + 12, b + rnd.nextInt(8) * 0.5f, 2.5f, 0.22f) }
        }
    }

    // Wii: plucky bossa comping, walking bass, a playful little tune.
    private fun bossa() = Builder(112, 8).apply {
        val ch = listOf(intArrayOf(60, 64, 67, 71), intArrayOf(57, 64, 67, 72), intArrayOf(62, 65, 69, 72), intArrayOf(55, 65, 67, 71))
        val roots = intArrayOf(36, 33, 38, 31)
        val tune = listOf(76 to 0f, 79 to 0.5f, 81 to 1.5f, 79 to 2f, 76 to 3f, 74 to 3.5f)
        for (bar in 0 until bars) {
            val c = ch[bar % 4]; val r = roots[bar % 4]; val b = bar * 4f
            for (k in listOf(0.5f, 1.5f, 2f, 3f, 3.5f)) chord(Inst.PLUCK, c, b + k, 0.22f, 0.26f)
            intArrayOf(0, 7, 12, 7).forEachIndexed { i, step -> note(Inst.BASS, r + step, b + i, 0.8f, 0.55f) }
            drum(Drum.KICK, b, 0.5f); drum(Drum.KICK, b + 2.75f, 0.35f); drum(Drum.RIM, b + 1.5f, 0.35f); drum(Drum.RIM, b + 3f, 0.3f)
            for (k in 0 until 8) drum(Drum.SHAKER, b + k * 0.5f, 0.1f)
            if (bar % 2 == 0) tune.forEach { (m, at) -> note(Inst.PLUCK, m + (if (bar >= 4) 2 else 0), b + at, 0.4f, 0.4f) }
        }
    }

    // Luna: calm, warm pad with a gently rolling piano.
    private fun calm() = Builder(72, 8).apply {
        val ch = listOf(intArrayOf(51, 58, 63, 67), intArrayOf(50, 58, 62, 65), intArrayOf(48, 55, 60, 63), intArrayOf(44, 56, 60, 63))
        val mel = listOf(75 to 0f, 72 to 1.5f, 70 to 2.5f)
        for (bar in 0 until bars) {
            val c = ch[bar % 4]; val b = bar * 4f
            chord(Inst.PAD, c, b, 4f, 0.22f)
            note(Inst.SUB, c[0] - 12, b, 4f, 0.4f)
            intArrayOf(0, 1, 2, 3, 2, 1, 2, 3).forEachIndexed { i, k -> note(Inst.EPIANO, c[k] + 12, b + i * 0.5f, 0.9f, 0.22f) }
            if (bar % 2 == 1) mel.forEach { (m, at) -> note(Inst.BELL, m, b + at, 1.6f, 0.26f) }
            for (k in 0 until 4) drum(Drum.SHAKER, b + k + 0.5f, 0.06f)
        }
    }

    private fun freq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    /** Renders one seamless loop: note tails and echoes wrap around to the start. */
    fun render(style: UiStyle): ShortArray {
        val s = build(style)
        val spb = RATE * 60.0 / s.bpm
        val n = (s.beats * spb).toInt()
        val mix = FloatArray(n)
        for ((inst, p, midi) in s.notes) {
            val start = (p[0] * spb).toInt()
            val len = (p[1] * spb).toInt()
            voice(mix, inst, freq(midi), start, len, p[2])
        }
        val rnd = Random(3)
        for ((d, beat, vel) in s.drums) drum(mix, d, (beat * spb).toInt(), vel, rnd)
        // Dotted-eighth echo, run over two passes so it wraps cleanly.
        val delay = (spb * 0.75).toInt()
        val wet = FloatArray(n)
        for (pass in 0 until 2) for (i in 0 until n) {
            val prev = wet[(i - delay + n) % n]
            wet[i] = mix[(i - delay + n) % n] * 0.35f + prev * 0.38f
        }
        var peak = 1e-6f
        for (i in 0 until n) { mix[i] += wet[i] * 0.55f; peak = maxOf(peak, abs(mix[i])) }
        val g = 0.85f / peak * 32767f
        return ShortArray(n) { (mix[it] * g).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun voice(out: FloatArray, inst: Inst, f: Double, start: Int, len: Int, vel: Float) {
        val n = out.size
        val release = when (inst) { Inst.PAD -> 0.9; Inst.SUB -> 0.6; Inst.BELL -> 1.4; Inst.EPIANO -> 0.5; else -> 0.12 } * RATE
        val total = len + release.toInt()
        val attack = when (inst) { Inst.PAD -> 0.5 * RATE; Inst.SUB -> 0.2 * RATE; else -> 0.004 * RATE }
        val w = 2 * PI * f / RATE
        var lp = 0.0
        var lp2 = 0.0
        val cutoff = when (inst) { Inst.SAW -> 2600.0; Inst.PAD -> 1400.0; Inst.BASS -> 700.0; else -> 6000.0 }
        val a = 1 - exp(-2 * PI * cutoff / RATE)
        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val env = (if (i < attack) i / attack else 1.0) * when (inst) {
                Inst.EPIANO -> exp(-t * 2.2)
                Inst.BELL -> exp(-t * 1.6)
                Inst.PLUCK -> exp(-t * 7.0)
                Inst.SAW -> exp(-t * 8.0)
                Inst.BASS -> exp(-t * 1.5) * 0.6 + 0.4
                Inst.PAD, Inst.SUB -> 1.0
            } * (if (i > len) exp(-(i - len) / release * 5) else 1.0)
            val ph = w * i
            val x = when (inst) {
                Inst.EPIANO -> sin(ph) + 0.25 * sin(2 * ph) * exp(-t * 6)
                Inst.BELL -> sin(ph) + 0.35 * sin(2.76 * ph) * exp(-t * 5) + 0.15 * sin(5.4 * ph) * exp(-t * 9)
                Inst.PLUCK -> tri(ph) * 0.7 + sin(2 * ph) * 0.3
                Inst.SAW -> saw(ph) * 0.6 + saw(ph * 1.005) * 0.4
                // Soft, slightly detuned triangles with a sub-octave: warm, not buzzy.
                Inst.PAD -> (tri(ph) + tri(ph * 1.004) + tri(ph * 0.996) + sin(ph * 0.5) * 0.6) / 3.6
                Inst.BASS -> sin(ph) * 0.8 + saw(ph) * 0.25
                Inst.SUB -> sin(ph)
            }
            // Two filter stages: a steeper roll-off keeps the cheap oscillators from sounding harsh.
            lp += a * (x - lp)
            lp2 += a * (lp - lp2)
            out[(start + i) % n] += (lp2 * env * vel * 0.18).toFloat()
        }
    }

    private fun drum(out: FloatArray, d: Drum, start: Int, vel: Float, rnd: Random) {
        val n = out.size
        val dur = when (d) { Drum.KICK -> 0.35; Drum.SNARE -> 0.2; Drum.OPEN_HAT -> 0.18; Drum.RIM -> 0.06; else -> 0.05 } * RATE
        var hp = 0f; var last = 0f
        var phase = 0.0
        for (i in 0 until dur.toInt()) {
            val t = i.toDouble() / RATE
            val noise = rnd.nextFloat() * 2 - 1
            // A crude high-pass so hats and shakers stay crisp rather than hissy.
            hp = 0.85f * (hp + noise - last); last = noise
            val x = when (d) {
                Drum.KICK -> { phase += 2 * PI * (45 + 90 * exp(-t * 30)) / RATE; sin(phase) * exp(-t * 14) }
                Drum.SNARE -> noise * exp(-t * 22) * 0.6 + sin(2 * PI * 190 * t) * exp(-t * 30) * 0.4
                Drum.HAT -> hp * exp(-t * 70.0)
                Drum.OPEN_HAT -> hp * exp(-t * 18.0)
                Drum.SHAKER -> hp * exp(-t * 45.0) * 0.8
                Drum.RIM -> sin(2 * PI * 1700 * t) * exp(-t * 90) + noise * exp(-t * 120) * 0.3
            }
            out[(start + i) % n] += (x * vel * 0.3).toFloat()
        }
    }

    private fun saw(ph: Double): Double { val p = (ph / (2 * PI)) % 1.0; return 2 * p - 1 }
    private fun tri(ph: Double): Double { val p = (ph / (2 * PI)) % 1.0; return 4 * abs(p - 0.5) - 1 }
}

/** Plays the current style's loop while Pillar is on screen, fading in and out. */
class ThemeMusicPlayer(private val scope: CoroutineScope) {
    private val cache = ConcurrentHashMap<UiStyle, ShortArray>()
    private var track: AudioTrack? = null
    private var playing: UiStyle? = null
    private var job: Job? = null

    /** Called whenever the wanted state changes; null stops the music. */
    fun want(style: UiStyle?) {
        if (style == playing) return
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            fadeOutAndStop()
            if (style == null) return@launch
            val pcm = cache[style] ?: withContext(Dispatchers.Default) { ThemeSongs.render(style) }.also { cache[style] = it }
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(ThemeSongs.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            t.setVolume(0f)
            t.play()
            track = t; playing = style
            for (i in 1..20) { t.setVolume(VOLUME * i / 20f); delay(40) }
        }
    }

    private suspend fun fadeOutAndStop() {
        val t = track ?: return
        for (i in 19 downTo 0) { runCatching { t.setVolume(VOLUME * i / 20f) }; delay(20) }
        runCatching { t.stop(); t.release() }
        track = null; playing = null
    }

    private companion object { const val VOLUME = 0.32f }
}
