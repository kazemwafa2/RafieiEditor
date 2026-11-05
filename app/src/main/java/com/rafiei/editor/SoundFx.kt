package com.rafiei.editor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Sound effects that are synthesised on the device (no downloads needed) and cached as WAV files. */
object SoundFx {
    class Def(val id: String, val fa: String, val en: String) {
        fun name(): String = L(fa, en)
    }

    val LIST = listOf(
        Def("click", "کلیک", "Click"),
        Def("pop", "پاپ", "Pop"),
        Def("ding", "زنگ", "Ding"),
        Def("bell", "ناقوس", "Bell"),
        Def("whoosh", "ووش", "Whoosh"),
        Def("rise", "اوج‌گیری", "Riser"),
        Def("laser", "لیزر", "Laser"),
        Def("coin", "سکه", "Coin"),
        Def("kick", "طبل بزرگ", "Kick"),
        Def("snare", "طبل کوچک", "Snare"),
        Def("shutter", "دوربین", "Shutter"),
        Def("bubble", "حباب", "Bubble"),
        Def("heartbeat", "ضربان قلب", "Heartbeat"),
        Def("magic", "جادو", "Magic"),
        Def("error", "خطا", "Error"),
        Def("success", "موفقیت", "Success"),
        Def("applause", "تشویق", "Applause"),
        Def("drumroll", "رول طبل", "Drum roll")
    )

    const val SR = 44100

    fun file(c: Context, id: String): File {
        val f = File(dirOf(c, "sfx"), "$id.wav")
        if (!f.exists() || f.length() < 100) {
            val data = generate(id)
            writeWav(f, data, SR)
        }
        return f
    }

    fun writeWav(f: File, data: FloatArray, sr: Int) {
        val n = data.size
        val bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + n * 2); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(sr); bb.putInt(sr * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(n * 2)
        for (i in 0 until n) {
            val v = clampF(data[i], -1f, 1f)
            bb.putShort((v * 32767f).toInt().toShort())
        }
        FileOutputStream(f).use { it.write(bb.array()) }
    }

    /** Silent mono 16 bit WAV of the given length (used as a gap filler by the exporter). */
    fun silence(c: Context, seconds: Int): File {
        val f = File(dirOf(c, "sfx"), "silence_$seconds.wav")
        if (!f.exists() || f.length() < 100) {
            writeWav(f, FloatArray(SR * seconds), SR)
        }
        return f
    }

    private fun env(t: Float, dur: Float, attack: Float = 0.005f): Float {
        val a = if (t < attack) t / attack else 1f
        val r = Math.exp((-5.0 * t / dur)).toFloat()
        return a * r
    }

    private fun sine(f: Float, t: Float): Float = Math.sin(2.0 * Math.PI * f * t).toFloat()

    private fun gen(sec: Float, fn: (Float, Int) -> Float): FloatArray {
        val n = (SR * sec).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = fn(i.toFloat() / SR, i)
        return out
    }

    private fun noise(i: Int, seed: Int): Float = hashNoise(i * 31 + seed) * 2f - 1f

    private fun mix(a: FloatArray, b: FloatArray, offsetSec: Float): FloatArray {
        val off = (offsetSec * SR).toInt()
        val n = Math.max(a.size, off + b.size)
        val out = FloatArray(n)
        for (i in a.indices) out[i] += a[i]
        for (i in b.indices) out[off + i] += b[i]
        return out
    }

    fun generate(id: String): FloatArray {
        return when (id) {
            "click" -> gen(0.06f) { t, i -> noise(i, 1) * env(t, 0.03f, 0.0005f) * 0.8f }
            "pop" -> gen(0.18f) { t, _ ->
                val f = 150f + 500f * Math.exp((-t * 30f).toDouble()).toFloat()
                sine(f, t) * env(t, 0.1f, 0.002f) * 0.9f
            }
            "ding" -> gen(1.4f) { t, _ ->
                (sine(1318f, t) + 0.35f * sine(2636f, t) + 0.15f * sine(3954f, t)) * env(t, 1.1f, 0.002f) * 0.5f
            }
            "bell" -> gen(2.2f) { t, _ ->
                (sine(523f, t) + 0.6f * sine(1046f * 1.0f, t) + 0.4f * sine(1480f, t) + 0.3f * sine(2093f, t)) * env(t, 1.8f, 0.002f) * 0.4f
            }
            "whoosh" -> {
                var lp = 0f
                gen(1.0f) { t, i ->
                    val bell = Math.sin(Math.PI * t / 1.0).toFloat()
                    val cutoff = 0.02f + 0.5f * bell * bell
                    lp += cutoff * (noise(i, 5) - lp)
                    lp * bell * 2.2f
                }
            }
            "rise" -> gen(1.6f) { t, _ ->
                val f = 180f + 1200f * (t / 1.6f) * (t / 1.6f)
                val ph = 2.0 * Math.PI * (180f * t + 1200f * t * t * t / (3f * 1.6f * 1.6f))
                Math.sin(ph).toFloat() * (t / 1.6f) * 0.5f + 0f * f
            }
            "laser" -> gen(0.3f) { t, _ ->
                val ph = 2.0 * Math.PI * (1800f * t - 2600f * t * t)
                Math.sin(ph).toFloat() * env(t, 0.2f, 0.001f) * 0.6f
            }
            "coin" -> {
                val a = gen(0.1f) { t, _ -> Math.signum(sine(988f, t)) * env(t, 0.08f, 0.001f) * 0.25f }
                val b = gen(0.45f) { t, _ -> Math.signum(sine(1319f, t)) * env(t, 0.3f, 0.001f) * 0.25f }
                mix(a, b, 0.09f)
            }
            "kick" -> gen(0.45f) { t, _ ->
                val ph = 2.0 * Math.PI * (45f * t + 105f * (1f - Math.exp((-t * 30f).toDouble()).toFloat()) / 30f)
                Math.sin(ph).toFloat() * env(t, 0.25f, 0.001f) * 1.0f
            }
            "snare" -> gen(0.3f) { t, i -> (noise(i, 9) * 0.7f + sine(190f, t) * 0.5f) * env(t, 0.12f, 0.001f) }
            "shutter" -> {
                val a = gen(0.05f) { t, i -> noise(i, 2) * env(t, 0.02f, 0.0003f) * 0.8f }
                val b = gen(0.08f) { t, i -> noise(i, 3) * env(t, 0.04f, 0.0003f) * 0.6f }
                mix(a, b, 0.07f)
            }
            "bubble" -> {
                var acc = FloatArray(0)
                for (k in 0 until 3) {
                    val base = 300f + 150f * k
                    val b = gen(0.14f) { t, _ -> sine(base + 900f * t, t) * env(t, 0.07f, 0.002f) * 0.6f }
                    acc = mix(acc, b, k * 0.11f)
                }
                acc
            }
            "heartbeat" -> {
                val a = gen(0.25f) { t, _ -> sine(55f, t) * env(t, 0.1f, 0.003f) }
                val b = gen(0.25f) { t, _ -> sine(48f, t) * env(t, 0.1f, 0.003f) * 0.8f }
                mix(mix(a, b, 0.22f), mix(a, b, 0.22f), 0.9f)
            }
            "magic" -> {
                val notes = floatArrayOf(523f, 659f, 784f, 1047f, 1319f, 1568f)
                var acc = FloatArray(0)
                for (k in notes.indices) {
                    val f = notes[k]
                    val n = gen(0.5f) { t, _ -> (sine(f, t) + 0.3f * sine(f * 2f, t)) * env(t, 0.35f, 0.003f) * 0.3f }
                    acc = mix(acc, n, k * 0.07f)
                }
                acc
            }
            "error" -> gen(0.35f) { t, _ ->
                val v = Math.signum(sine(140f, t)) * 0.3f
                v * (if (t < 0.15f || t > 0.19f) 1f else 0f) * env(t, 0.5f, 0.002f)
            }
            "success" -> {
                val a = gen(0.2f) { t, _ -> sine(523f, t) * env(t, 0.15f, 0.002f) * 0.5f }
                val b = gen(0.7f) { t, _ -> (sine(784f, t) + 0.3f * sine(1568f, t)) * env(t, 0.5f, 0.002f) * 0.5f }
                mix(a, b, 0.14f)
            }
            "applause" -> gen(2.4f) { t, i ->
                val slow = 0.5f + 0.5f * hashNoise((t * 14f).toInt() + 3)
                val fade = Math.min(1f, t / 0.3f) * Math.min(1f, (2.4f - t) / 0.6f)
                noise(i, 11) * slow * fade * 0.7f
            }
            "drumroll" -> gen(1.6f) { t, i ->
                val rate = 14f + 22f * t / 1.6f
                val ph = (t * rate) % 1f
                val hit = Math.exp((-ph * 14f).toDouble()).toFloat()
                (noise(i, 4) * 0.6f + sine(200f, t) * 0.3f) * hit * (0.4f + 0.6f * t / 1.6f)
            }
            else -> gen(0.2f) { t, _ -> sine(440f, t) * env(t, 0.1f) }
        }
    }
}
