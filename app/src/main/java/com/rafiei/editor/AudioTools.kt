package com.rafiei.editor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/** Second-order IIR filter (RBJ cookbook), one state per channel. */
class Biquad(ch: Int) {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private val z1 = DoubleArray(ch)
    private val z2 = DoubleArray(ch)

    fun run(c: Int, x: Double): Double {
        val y = b0 * x + z1[c]
        z1[c] = b1 * x - a1 * y + z2[c]
        z2[c] = b2 * x - a2 * y
        return y
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    fun peak(f: Double, gainDb: Double, q: Double, sr: Double) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w = 2 * Math.PI * f / sr
        val al = Math.sin(w) / (2 * q)
        val cs = Math.cos(w)
        set(1 + al * a, -2 * cs, 1 - al * a, 1 + al / a, -2 * cs, 1 - al / a)
    }

    fun lowShelf(f: Double, gainDb: Double, sr: Double) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w = 2 * Math.PI * f / sr
        val cs = Math.cos(w)
        val al = Math.sin(w) / 2 * Math.sqrt(2.0)
        val sq = 2 * Math.sqrt(a) * al
        set(a * ((a + 1) - (a - 1) * cs + sq), 2 * a * ((a - 1) - (a + 1) * cs), a * ((a + 1) - (a - 1) * cs - sq),
            (a + 1) + (a - 1) * cs + sq, -2 * ((a - 1) + (a + 1) * cs), (a + 1) + (a - 1) * cs - sq)
    }

    fun highShelf(f: Double, gainDb: Double, sr: Double) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w = 2 * Math.PI * f / sr
        val cs = Math.cos(w)
        val al = Math.sin(w) / 2 * Math.sqrt(2.0)
        val sq = 2 * Math.sqrt(a) * al
        set(a * ((a + 1) + (a - 1) * cs + sq), -2 * a * ((a - 1) + (a + 1) * cs), a * ((a + 1) + (a - 1) * cs - sq),
            (a + 1) - (a - 1) * cs + sq, 2 * ((a - 1) - (a + 1) * cs), (a + 1) - (a - 1) * cs - sq)
    }

    fun highpass(f: Double, q: Double, sr: Double) {
        val w = 2 * Math.PI * f / sr
        val cs = Math.cos(w)
        val al = Math.sin(w) / (2 * q)
        set((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + al, -2 * cs, 1 - al)
    }
}

/** 3-band equaliser, noise gate ("denoise") and speech enhancement on 16-bit PCM. Used by preview and export. */
class FxAudioProcessor(
    private val eqL: Float, private val eqM: Float, private val eqH: Float,
    private val denoise: Float, private val voice: Boolean
) : BaseAudioProcessor() {
    private var filters: List<Biquad> = emptyList()
    private var env = 0.0
    private var floor = 0.01
    private var gate = 1.0
    private var comp = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate.toDouble()
        val l = ArrayList<Biquad>()
        if (denoise > 0.01f || voice) { val b = Biquad(ch); b.highpass(if (voice) 95.0 else 70.0, 0.707, sr); l.add(b) }
        if (Math.abs(eqL) > 0.05f) { val b = Biquad(ch); b.lowShelf(180.0, eqL.toDouble(), sr); l.add(b) }
        if (Math.abs(eqM) > 0.05f) { val b = Biquad(ch); b.peak(1400.0, eqM.toDouble(), 0.8, sr); l.add(b) }
        if (Math.abs(eqH) > 0.05f) { val b = Biquad(ch); b.highShelf(4500.0, eqH.toDouble(), sr); l.add(b) }
        if (voice) { val b = Biquad(ch); b.peak(3000.0, 3.0, 1.0, sr); l.add(b) }
        filters = l
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate.toDouble()
        val envA = 1.0 - Math.exp(-1.0 / (sr * 0.005))
        val envR = 1.0 - Math.exp(-1.0 / (sr * 0.12))
        val gA = 1.0 - Math.exp(-1.0 / (sr * 0.01))
        val gR = 1.0 - Math.exp(-1.0 / (sr * 0.15))
        val x = DoubleArray(ch)
        while (inputBuffer.remaining() >= 2 * ch) {
            var peak = 0.0
            for (c in 0 until ch) {
                var v = inputBuffer.short / 32768.0
                for (f in filters) v = f.run(c, v)
                x[c] = v
                val a = Math.abs(v)
                if (a > peak) peak = a
            }
            var g = 1.0
            if (denoise > 0.01f) {
                env += (peak - env) * (if (peak > env) envA else envR)
                if (env < floor) floor += (env - floor) * 0.002 else floor += (env - floor) * 0.00002
                if (floor < 0.0008) floor = 0.0008
                val open = env > floor * (2.2 + 2.0 * denoise) + 0.002
                val target = if (open) 1.0 else 1.0 - 0.92 * denoise
                gate += (target - gate) * (if (target > gate) gA else gR)
                g *= gate
            }
            if (voice) {
                val lvl = Math.max(peak, 1e-4)
                val want = if (lvl > 0.18) Math.pow(0.18 / lvl, 0.5) else 1.0
                comp += (want - comp) * (if (want < comp) gA else gR * 0.3)
                g *= comp * 1.5
            }
            for (c in 0 until ch) {
                var v = (x[c] * g * 32768.0).toInt()
                if (v > 32767) v = 32767
                if (v < -32768) v = -32768
                out.putShort(v.toShort())
            }
        }
        out.flip()
    }

    override fun onFlush() {
        env = 0.0; floor = 0.01; gate = 1.0; comp = 1.0
        for (f in filters) { /* state is rebuilt on configure */ }
    }
}

/** Export-time gain automation that lowers music while somebody speaks. */
class DuckProcessor(private val offsetMs: Long, private val gainAt: (Long) -> Float) : BaseAudioProcessor() {
    private var frames = 0L
    private var g = 1.0
    private var target = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate
        val k = 1.0 - Math.exp(-1.0 / (sr * 0.03))
        while (inputBuffer.remaining() >= 2 * ch) {
            if (frames % 512L == 0L) target = gainAt(offsetMs + frames * 1000L / sr).toDouble()
            g += (target - g) * k
            for (c in 0 until ch) {
                var v = (inputBuffer.short * g).toInt()
                if (v > 32767) v = 32767
                if (v < -32768) v = -32768
                out.putShort(v.toShort())
            }
            frames++
        }
        out.flip()
    }

    override fun onFlush() {
        frames = 0; g = 1.0; target = 1.0
    }
}

/** Speech detection (from the offline analysis) used to duck music in preview and export. */
object Duck {
    private fun levelAt(path: String, srcMs: Long): Float {
        val t = EqAudio.get(path) ?: run { EqAudio.ensure(path); return 0f }
        if (t.n < 2) return 0f
        val i = clampF(srcMs / 1000f * EqAudio.FPS, 0f, (t.n - 1).toFloat()).toInt()
        var s = 0f
        for (j in 11..55) s += t.lv[i * NBAND + j]
        return s / 45f
    }

    fun speech(p: Project, tMs: Long, except: String): Float {
        var best = 0f
        for (a in p.audios) {
            if (a.id == except || a.uri.isEmpty() || a.volume < 0.05f) continue
            if (a.kind != "voice" && a.kind != "tts") continue
            if (tMs < a.startMs || tMs >= a.endMs()) continue
            best = Math.max(best, levelAt(MediaUtil.pathOf(a.uri), a.trimStart + ((tMs - a.startMs) * a.speed).toLong()))
        }
        if (!p.muteClips) {
            val ci = p.clipIndexAt(tMs)
            if (ci >= 0) {
                val c = p.clips[ci]
                if (!c.isImage && c.hasAudio && c.volume > 0.05f) {
                    best = Math.max(best, levelAt(MediaUtil.pathOf(c.uri), c.trimStart + ((tMs - p.clipStart(ci)) * c.speed).toLong()))
                }
            }
        }
        return best
    }

    fun gain(p: Project, a: AudioItem, tMs: Long): Float {
        if (a.duck <= 0.01f) return 1f
        var s = 0f
        for (d in longArrayOf(-300, -150, 0, 150)) s = Math.max(s, speech(p, Math.max(0L, tMs + d), a.id))
        val k = clampF((s - 0.16f) / 0.12f, 0f, 1f)
        return 1f - a.duck * k
    }
}
