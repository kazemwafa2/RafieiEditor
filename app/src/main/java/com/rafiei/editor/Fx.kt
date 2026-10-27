package com.rafiei.editor

import android.graphics.Matrix

class FilterDef(
    val id: String, val fa: String, val en: String,
    val sat: Float = 1f, val contrast: Float = 0f, val bright: Float = 0f, val hue: Float = 0f,
    val r: Float = 1f, val g: Float = 1f, val b: Float = 1f,
    val sepia: Float = 0f, val fade: Float = 0f, val vignette: Float = 0f
) {
    fun name(): String = L(fa, en)
}

class FxDef(val id: String, val fa: String, val en: String, val overlay: Boolean, val color: Int) {
    fun name(): String = L(fa, en)
}

class NamedDef(val id: String, val fa: String, val en: String) {
    fun name(): String = L(fa, en)
}

/** 3x4 color transform: rows R,G,B each [m0 m1 m2 offset], values normalised 0..1. */
class ColorXf {
    val m = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun isIdentity(): Boolean {
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
        for (i in 0 until 12) if (Math.abs(m[i] - id[i]) > 0.0005f) return false
        return true
    }

    /** result = op * current */
    fun then(op: FloatArray): ColorXf {
        val n = FloatArray(12)
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                var s = 0f
                for (k in 0 until 3) s += op[r * 4 + k] * m[k * 4 + c]
                n[r * 4 + c] = s
            }
            var o = op[r * 4 + 3]
            for (k in 0 until 3) o += op[r * 4 + k] * m[k * 4 + 3]
            n[r * 4 + 3] = o
        }
        System.arraycopy(n, 0, m, 0, 12)
        return this
    }

    fun sat(s: Float): ColorXf {
        if (Math.abs(s - 1f) < 0.001f) return this
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f
        val i = 1f - s
        return then(floatArrayOf(
            lr * i + s, lg * i, lb * i, 0f,
            lr * i, lg * i + s, lb * i, 0f,
            lr * i, lg * i, lb * i + s, 0f
        ))
    }

    fun hue(deg: Float): ColorXf {
        if (Math.abs(deg) < 0.01f) return this
        val a = Math.toRadians(deg.toDouble())
        val c = Math.cos(a).toFloat()
        val s = Math.sin(a).toFloat()
        return then(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f
        ))
    }

    fun gain(r: Float, g: Float, b: Float): ColorXf {
        if (Math.abs(r - 1f) < 0.001f && Math.abs(g - 1f) < 0.001f && Math.abs(b - 1f) < 0.001f) return this
        return then(floatArrayOf(r, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, b, 0f))
    }

    fun sepia(a: Float): ColorXf {
        if (a < 0.001f) return this
        val i = 1f - a
        return then(floatArrayOf(
            i + a * 0.393f, a * 0.769f, a * 0.189f, 0f,
            a * 0.349f, i + a * 0.686f, a * 0.168f, 0f,
            a * 0.272f, a * 0.534f, i + a * 0.131f, 0f
        ))
    }

    fun contrast(k: Float): ColorXf {
        if (Math.abs(k - 1f) < 0.001f) return this
        val o = 0.5f * (1f - k)
        return then(floatArrayOf(k, 0f, 0f, o, 0f, k, 0f, o, 0f, 0f, k, o))
    }

    fun bright(b: Float): ColorXf {
        if (Math.abs(b) < 0.001f) return this
        return then(floatArrayOf(1f, 0f, 0f, b, 0f, 1f, 0f, b, 0f, 0f, 1f, b))
    }

    fun lift(f: Float): ColorXf {
        if (f < 0.001f) return this
        val k = 1f - f
        val o = 0.14f * f
        return then(floatArrayOf(k, 0f, 0f, o, 0f, k, 0f, o, 0f, 0f, k, o))
    }

    fun invert(a: Float): ColorXf {
        if (a < 0.001f) return this
        val k = 1f - 2f * a
        return then(floatArrayOf(k, 0f, 0f, a, 0f, k, 0f, a, 0f, 0f, k, a))
    }

    fun dark(d: Float): ColorXf {
        if (d < 0.001f) return this
        val k = 1f - clampF(d, 0f, 1f)
        return then(floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f))
    }

    fun white(w: Float): ColorXf {
        if (w < 0.001f) return this
        val k = 1f - clampF(w, 0f, 1f)
        return then(floatArrayOf(k, 0f, 0f, w, 0f, k, 0f, w, 0f, 0f, k, w))
    }

    /** Android ColorMatrix (4x5, row major, offsets in 0..255). */
    fun toAndroid(): FloatArray {
        val a = FloatArray(20)
        for (r in 0 until 3) {
            a[r * 5] = m[r * 4]
            a[r * 5 + 1] = m[r * 4 + 1]
            a[r * 5 + 2] = m[r * 4 + 2]
            a[r * 5 + 3] = 0f
            a[r * 5 + 4] = m[r * 4 + 3] * 255f
        }
        a[18] = 1f
        return a
    }

    /** OpenGL column-major 4x4 used by Media3 RgbMatrix (translation in the last column). */
    fun toGl(): FloatArray {
        val g = FloatArray(16)
        for (c in 0 until 3) for (r in 0 until 3) g[c * 4 + r] = m[r * 4 + c]
        g[12] = m[3]
        g[13] = m[7]
        g[14] = m[11]
        g[15] = 1f
        return g
    }
}

/** Instantaneous modifiers produced by animations, transitions and time based effects. */
class Dyn {
    var s = 1f
    var tx = 0f
    var ty = 0f
    var rot = 0f
    var dark = 0f
    var white = 0f
    var hue = 0f
    var sat = 1f
    var bright = 0f
    var invert = 0f
    var contrast = 1f
    var gr = 1f
    var gg = 1f
    var gb = 1f
    var sx = 1f
    var sy = 1f
    var sepia = 0f
    var lift = 0f
}

object Fx {

    val FILTERS = listOf(
        FilterDef("none", "اصلی", "Original"),
        FilterDef("vivid", "زنده", "Vivid", sat = 1.35f, contrast = 0.12f),
        FilterDef("warm", "گرم", "Warm", sat = 1.1f, r = 1.08f, b = 0.92f),
        FilterDef("cool", "سرد", "Cool", r = 0.93f, b = 1.08f),
        FilterDef("sunny", "آفتابی", "Sunny", sat = 1.15f, bright = 0.04f, r = 1.1f, g = 1.04f, b = 0.9f),
        FilterDef("mono", "سیاه‌وسفید", "Mono", sat = 0f),
        FilterDef("noir", "نوآر", "Noir", sat = 0f, contrast = 0.35f, bright = -0.04f, vignette = 0.4f),
        FilterDef("silver", "نقره‌ای", "Silver", sat = 0f, contrast = 0.15f, bright = 0.06f),
        FilterDef("sepia", "قهوه‌ای", "Sepia", sepia = 1f),
        FilterDef("vintage", "قدیمی", "Vintage", contrast = -0.1f, r = 1.05f, sepia = 0.5f, fade = 0.12f, vignette = 0.35f),
        FilterDef("fade", "محو", "Fade", sat = 0.8f, contrast = -0.1f, fade = 0.18f),
        FilterDef("matte", "مات", "Matte", sat = 0.85f, contrast = -0.12f, bright = 0.02f, fade = 0.1f),
        FilterDef("cinema", "سینمایی", "Cinema", sat = 0.9f, contrast = 0.2f, r = 1.07f, g = 0.98f, b = 0.95f, vignette = 0.2f),
        FilterDef("teal", "فیروزه‌ای", "Teal", contrast = 0.1f, r = 0.9f, g = 1.03f, b = 1.1f),
        FilterDef("sunset", "غروب", "Sunset", sat = 1.2f, r = 1.15f, g = 0.98f, b = 0.85f),
        FilterDef("mint", "نعنایی", "Mint", sat = 1.05f, r = 0.92f, g = 1.08f, b = 1.02f),
        FilterDef("pink", "صورتی", "Pink", bright = 0.03f, r = 1.1f, g = 0.95f, b = 1.05f),
        FilterDef("dream", "رویا", "Dream", sat = 1.15f, contrast = -0.15f, bright = 0.06f, r = 1.03f, b = 1.05f),
        FilterDef("drama", "دراماتیک", "Drama", sat = 1.1f, contrast = 0.3f, bright = -0.03f, vignette = 0.3f),
        FilterDef("bright", "روشن", "Bright", sat = 1.05f, contrast = 0.05f, bright = 0.12f),
        FilterDef("soft", "نرم", "Soft", contrast = -0.18f, bright = 0.05f, fade = 0.06f),
        FilterDef("chrome", "کروم", "Chrome", sat = 1.25f, contrast = 0.2f, r = 1.03f),
        FilterDef("golden", "طلایی", "Golden", sepia = 0.15f, r = 1.12f, g = 1.05f, b = 0.85f),
        FilterDef("moody", "غمگین", "Moody", sat = 0.75f, contrast = 0.2f, bright = -0.06f, b = 1.05f, vignette = 0.3f),
        FilterDef("neon", "نئون", "Neon", sat = 1.6f, contrast = 0.15f, hue = 12f),
        FilterDef("retro", "رترو", "Retro", sat = 0.9f, hue = -8f, r = 1.08f, b = 0.9f, fade = 0.15f, vignette = 0.3f)
    )

    fun filter(id: String): FilterDef {
        for (f in FILTERS) if (f.id == id) return f
        return FILTERS[0]
    }

    val FX_LIST = listOf(
        FxDef("flash", "فلاش", "Flash", false, 0xFFFFFFFF.toInt()),
        FxDef("strobe", "استروب", "Strobe", false, 0xFFFFE066.toInt()),
        FxDef("glitch", "گلیچ", "Glitch", false, 0xFF00E5FF.toInt()),
        FxDef("shake", "لرزش", "Shake", false, 0xFFFF8A65.toInt()),
        FxDef("pulse", "ضربان", "Pulse", false, 0xFFFF5252.toInt()),
        FxDef("heartbeat", "تپش قلب", "Heartbeat", false, 0xFFFF4081.toInt()),
        FxDef("dolly", "نزدیک‌شدن", "Dolly", false, 0xFF7C4DFF.toInt()),
        FxDef("sway", "نوسان", "Sway", false, 0xFF40C4FF.toInt()),
        FxDef("spin", "چرخش", "Spin", false, 0xFF69F0AE.toInt()),
        FxDef("rainbow", "رنگین‌کمان", "Rainbow", false, 0xFFFFAB40.toInt()),
        FxDef("bw", "سیاه‌وسفید", "B&W", false, 0xFFBDBDBD.toInt()),
        FxDef("negative", "نگاتیو", "Negative", false, 0xFF80D8FF.toInt()),
        FxDef("fade_in", "ظاهر شدن", "Fade in", false, 0xFFB0BEC5.toInt()),
        FxDef("fade_out", "محو شدن", "Fade out", false, 0xFF78909C.toInt()),
        FxDef("blink", "پلک", "Blink", false, 0xFF8D6E63.toInt()),
        FxDef("dim", "تاریک", "Dim", false, 0xFF455A64.toInt()),
        FxDef("vignette", "حاشیه تیره", "Vignette", true, 0xFF607D8B.toInt()),
        FxDef("grain", "دانه فیلم", "Film grain", true, 0xFF9E9E9E.toInt()),
        FxDef("vhs", "نوار VHS", "VHS", true, 0xFFE040FB.toInt()),
        FxDef("snow", "برف", "Snow", true, 0xFFE3F2FD.toInt()),
        FxDef("rain", "باران", "Rain", true, 0xFF64B5F6.toInt()),
        FxDef("hearts", "قلب‌ها", "Hearts", true, 0xFFFF6090.toInt()),
        FxDef("sparkle", "درخشش", "Sparkle", true, 0xFFFFF176.toInt()),
        FxDef("confetti", "کاغذ رنگی", "Confetti", true, 0xFFFF7043.toInt()),
        FxDef("bokeh", "بوکه", "Bokeh", true, 0xFFFFCC80.toInt()),
        FxDef("leak", "نور نشتی", "Light leak", true, 0xFFFF9E80.toInt()),
        FxDef("cinema", "نوار سینما", "Cinema bars", true, 0xFF212121.toInt()),
        FxDef("frame", "قاب", "Frame", true, 0xFFFAFAFA.toInt()),
        FxDef("dust", "گردوغبار", "Dust", true, 0xFFD7CCC8.toInt()),
        FxDef("stars", "ستاره", "Stars", true, 0xFFFFD740.toInt()),
        FxDef("lomo", "لومو", "Lomo", false, 0xFFFF7043.toInt()),
        FxDef("bwfilm", "فیلم سیاه‌وسفید", "B&W film", false, 0xFF9E9E9E.toInt()),
        FxDef("papertear", "کاغذ پاره", "Paper tear", true, 0xFFF5F0E6.toInt()),
        FxDef("butterflies", "پروانه‌ها", "Butterflies", true, 0xFF29B6F6.toInt()),
        FxDef("elecframe", "برق دور قاب", "Electric frame", true, 0xFFE040FB.toInt()),
        FxDef("sparkhalo", "هالهٔ درخشان", "Sparkle halo", true, 0xFFFFFFFF.toInt()),
        FxDef("p_outline", "خط درخشان دور بدن (هوشمند)", "Body outline glow (smart)", true, 0xFFE040FB.toInt()),
        FxDef("p_electric", "برق دور بدن (هوشمند)", "Body lightning (smart)", true, 0xFFB388FF.toInt()),
        FxDef("p_sparkle", "درخشش دور بدن (هوشمند)", "Body sparkles (smart)", true, 0xFFFFFFFF.toInt()),
        FxDef("p_butterfly", "پروانه دور سوژه (هوشمند)", "Butterflies around person (smart)", true, 0xFF29B6F6.toInt()),
        FxDef("light", "نور استودیو (بازه‌ای)", "Studio light (range)", true, 0xFFFFE6C8.toInt()),
        FxDef("mask", "ماسک (بازه‌ای)", "Mask (range)", true, 0xFF90CAF9.toInt())
    ) + FILTERS.filter { it.id != "none" }.map { FxDef("flt_" + it.id, it.fa, it.en, false, 0xFF90A4AE.toInt()) }

    fun fxDef(id: String): FxDef {
        for (f in FX_LIST) if (f.id == id) return f
        return FX_LIST[0]
    }

    val CLIP_ANIMS = listOf(
        NamedDef("none", "هیچ", "None"),
        NamedDef("fade", "محو", "Fade"),
        NamedDef("zoom_in", "زوم داخل", "Zoom in"),
        NamedDef("zoom_out", "زوم خارج", "Zoom out"),
        NamedDef("slide_left", "اسلاید چپ", "Slide left"),
        NamedDef("slide_right", "اسلاید راست", "Slide right"),
        NamedDef("slide_up", "اسلاید بالا", "Slide up"),
        NamedDef("slide_down", "اسلاید پایین", "Slide down"),
        NamedDef("spin", "چرخش", "Spin"),
        NamedDef("pop", "پاپ", "Pop")
    )

    val TRANSITIONS = listOf(
        NamedDef("none", "هیچ", "None"),
        NamedDef("fade", "محو", "Fade"),
        NamedDef("flash", "فلاش", "Flash"),
        NamedDef("zoom", "زوم", "Zoom"),
        NamedDef("slide", "اسلاید", "Slide"),
        NamedDef("spin", "چرخش", "Spin"),
        NamedDef("pop", "پاپ", "Pop"),
        NamedDef("slide_r", "اسلاید راست", "Slide right"),
        NamedDef("slide_u", "اسلاید بالا", "Slide up"),
        NamedDef("slide_d", "اسلاید پایین", "Slide down"),
        NamedDef("zoomout", "زوم عقب", "Zoom out"),
        NamedDef("squeeze", "فشرده افقی", "Squeeze"),
        NamedDef("squeeze_v", "فشرده عمودی", "Squeeze vertical"),
        NamedDef("whip", "شلاقی", "Whip"),
        NamedDef("glitch", "گلیچ", "Glitch"),
        NamedDef("zoom_spin", "زوم چرخان", "Zoom spin"),
        NamedDef("punch", "ضربه", "Punch"),
        NamedDef("shrink", "کوچک‌شدن", "Shrink"),
        NamedDef("swing", "تاب", "Swing"),
        NamedDef("blink", "پلک‌زدن", "Blink")
    )

    val OVERLAY_ANIMS = listOf(
        NamedDef("none", "هیچ", "None"),
        NamedDef("fade", "محو", "Fade"),
        NamedDef("pop", "پاپ", "Pop"),
        NamedDef("bounce", "جهش", "Bounce"),
        NamedDef("zoom", "زوم", "Zoom"),
        NamedDef("slide_left", "اسلاید چپ", "Slide left"),
        NamedDef("slide_right", "اسلاید راست", "Slide right"),
        NamedDef("slide_up", "اسلاید بالا", "Slide up"),
        NamedDef("slide_down", "اسلاید پایین", "Slide down"),
        NamedDef("spin", "چرخش", "Spin"),
        NamedDef("typewriter", "تایپ", "Typewriter")
    )

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Color transform of a clip from its filter and adjust values (no time dependent part). */
    fun baseColor(c: Clip): ColorXf {
        val x = ColorXf()
        val f = filter(c.filter)
        val a = clampF(c.filterAmt, 0f, 1f)
        if (f.id != "none" && a > 0.001f) {
            x.sat(lerp(1f, f.sat, a))
            x.hue(f.hue * a)
            x.gain(lerp(1f, f.r, a), lerp(1f, f.g, a), lerp(1f, f.b, a))
            x.sepia(f.sepia * a)
            x.contrast(1f + f.contrast * a)
            x.bright(f.bright * a)
            x.lift(f.fade * a)
        }
        val adj = c.adj
        x.gain(1f + 0.25f * adj[Adj.WARMTH], 1f - 0.12f * adj[Adj.TINT], 1f - 0.25f * adj[Adj.WARMTH])
        x.gain(1f + 0.05f * adj[Adj.TINT], 1f, 1f + 0.05f * adj[Adj.TINT])
        x.sat(1f + adj[Adj.SAT])
        x.hue(adj[Adj.HUE] * 90f)
        val k = if (adj[Adj.CONTRAST] >= 0f) 1f + adj[Adj.CONTRAST] * 0.9f else 1f + adj[Adj.CONTRAST] * 0.7f
        x.contrast(k)
        x.bright(adj[Adj.BRIGHT] * 0.35f)
        x.lift(Math.max(0f, adj[Adj.FADE]) * 0.5f)
        return x
    }

    fun vignetteOf(c: Clip): Float {
        val f = filter(c.filter)
        return clampF(f.vignette * c.filterAmt + c.adj[Adj.VIGNETTE], 0f, 1f)
    }

    fun grainOf(c: Clip): Float = clampF(c.adj[Adj.GRAIN], 0f, 1f)

    fun hasStaticColor(c: Clip): Boolean = !baseColor(c).isIdentity()

    private fun applyE(d: Dyn, kind: String, e: Float, isIn: Boolean) {
        if (e <= 0f) return
        when (kind) {
            "fade" -> d.dark += e
            "flash" -> d.white += e
            "zoom_in" -> { d.s *= 1f + 0.5f * e; d.dark += 0.5f * e }
            "zoom_out" -> { d.s *= 1f - 0.4f * e; d.dark += 0.5f * e }
            "slide_left" -> d.tx += if (isIn) e else -e
            "slide_right" -> d.tx += if (isIn) -e else e
            "slide_up" -> d.ty += if (isIn) e else -e
            "slide_down" -> d.ty += if (isIn) -e else e
            "spin" -> { d.rot += (if (isIn) -e else e) * 180f; d.s *= 1f - 0.5f * e }
            "pop" -> d.s *= Math.max(0.02f, 1f - e)
            "squeeze" -> { d.sx *= Math.max(0.03f, 1f - e); d.dark += 0.3f * e }
            "blink" -> { d.sy *= Math.max(0.02f, 1f - e * e); d.dark += 0.55f * e; d.white += 0.15f * e }
            "squeeze_v" -> { d.sy *= Math.max(0.03f, 1f - e); d.dark += 0.3f * e }
            "whip" -> { d.tx += (if (isIn) e else -e) * 1.3f; d.sx *= 1f + 0.9f * e }
            "glitch" -> { d.tx += Math.sin((e * 47f).toDouble()).toFloat() * e * 0.06f; d.hue += e * 70f; d.dark += 0.25f * e; d.sx *= 1f + 0.1f * e }
            "zoom_spin" -> { d.rot += (if (isIn) -e else e) * 120f; d.s *= 1f + 0.8f * e; d.dark += 0.6f * e }
            "punch" -> { d.s *= 1f + 0.35f * e; d.white += 0.5f * e }
            "shrink" -> { d.s *= Math.max(0.02f, 1f - 0.9f * e); d.dark += 0.4f * e }
            "swing" -> { d.rot += (if (isIn) e else -e) * 25f; d.tx += (if (isIn) e else -e) * 0.6f; d.dark += 0.2f * e }
        }
    }

    private fun transToAnim(t: String): String {
        return when (t) {
            "fade" -> "fade"
            "flash" -> "flash"
            "zoom" -> "zoom_in"
            "slide" -> "slide_left"
            "spin" -> "spin"
            "pop" -> "pop"
            "slide_r" -> "slide_right"
            "slide_u" -> "slide_up"
            "slide_d" -> "slide_down"
            "zoomout" -> "zoom_out"
            "squeeze", "squeeze_v", "whip", "glitch", "zoom_spin", "punch", "shrink", "swing", "blink" -> t
            else -> "none"
        }
    }

    private fun ease(x: Float): Float {
        val t = clampF(x, 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Dynamics for clip [i] at local time [lt] (ms since clip start on the timeline) and global time [tg].
     */
    fun dyn(p: Project, i: Int, lt: Long, tg: Long): Dyn {
        val d = Dyn()
        if (i < 0 || i >= p.clips.size) return d
        val c = p.clips[i]
        val dur = c.durMs()
        if (c.animIn != "none" && c.animInMs > 0 && lt < c.animInMs) {
            applyE(d, c.animIn, ease(1f - lt.toFloat() / c.animInMs), true)
        }
        if (c.animOut != "none" && c.animOutMs > 0 && lt > dur - c.animOutMs) {
            applyE(d, c.animOut, ease((lt - (dur - c.animOutMs)).toFloat() / c.animOutMs), false)
        }
        if (i > 0 && c.trans != "none") {
            val prev = p.clips[i - 1]
            val h = Math.min(c.transMs / 2, Math.min(dur / 2, prev.durMs() / 2))
            if (h > 0 && lt < h) applyE(d, transToAnim(c.trans), ease(1f - lt.toFloat() / h), true)
        }
        if (i < p.clips.size - 1) {
            val nx = p.clips[i + 1]
            if (nx.trans != "none") {
                val h = Math.min(nx.transMs / 2, Math.min(dur / 2, nx.durMs() / 2))
                if (h > 0 && lt > dur - h) applyE(d, transToAnim(nx.trans), ease((lt - (dur - h)).toFloat() / h), false)
            }
        }
        for (f in p.fxs) {
            if (tg >= f.startMs && tg < f.endMs()) applyFx(d, f, tg)
        }
        if (c.motion.isNotEmpty()) Motion.applyToDyn(c, lt, d)
        if (c.kf.isNotEmpty()) Keyframes.applyToDyn(c, lt, d)
        return d
    }

    private fun applyFx(d: Dyn, f: FxItem, tg: Long) {
        val tt = (tg - f.startMs).toFloat()
        val u = clampF(tt / Math.max(1L, f.durMs).toFloat(), 0f, 1f)
        val a = f.amt
        if (f.fx.startsWith("flt_")) {
            // a filter that lasts only for this part of the timeline
            val fd = filter(f.fx.substring(4))
            val k = clampF(a, 0f, 1f)
            d.sat *= lerp(1f, fd.sat, k)
            d.hue += fd.hue * k
            d.gr *= lerp(1f, fd.r, k); d.gg *= lerp(1f, fd.g, k); d.gb *= lerp(1f, fd.b, k)
            d.sepia += fd.sepia * k
            d.contrast *= 1f + fd.contrast * k
            d.bright += fd.bright * k
            d.lift += fd.fade * k
            return
        }
        when (f.fx) {
            "flash" -> {
                val ph = (tt % 700f) / 700f
                if (ph < 0.18f) d.white += a * 0.85f * (1f - ph / 0.18f)
            }
            "strobe" -> {
                if (((tt / 110f).toInt()) % 2 == 0) d.white += a * 0.8f
            }
            "glitch" -> {
                val k = (tt / 90f).toInt()
                val r = hashNoise(k)
                if (r > 0.55f) {
                    d.tx += (hashNoise(k + 7) - 0.5f) * 0.10f * a
                    d.ty += (hashNoise(k + 11) - 0.5f) * 0.03f * a
                    d.hue += (hashNoise(k + 3) - 0.5f) * 160f * a
                    d.sat *= 1f + 0.5f * a
                }
            }
            "shake" -> {
                d.tx += Math.sin(tt * 0.055).toFloat() * 0.025f * a
                d.ty += Math.cos(tt * 0.071).toFloat() * 0.025f * a
                d.rot += Math.sin(tt * 0.043).toFloat() * 1.6f * a
                d.s *= 1f + 0.05f * a
            }
            "pulse" -> {
                val ph = (tt % 520f) / 520f
                d.s *= 1f + 0.10f * a * (0.5f - 0.5f * Math.cos(ph * 2f * Math.PI).toFloat())
            }
            "heartbeat" -> {
                val ph = (tt % 900f) / 900f
                val b1 = Math.exp(-Math.pow(((ph - 0.12f) / 0.06f).toDouble(), 2.0)).toFloat()
                val b2 = Math.exp(-Math.pow(((ph - 0.32f) / 0.07f).toDouble(), 2.0)).toFloat()
                d.s *= 1f + 0.09f * a * (b1 + 0.7f * b2)
            }
            "dolly" -> d.s *= 1f + 0.35f * a * u
            "sway" -> {
                d.rot += Math.sin(tt / 1200.0 * 2.0 * Math.PI).toFloat() * 4f * a
                d.s *= 1f + 0.06f * a
            }
            "spin" -> {
                d.rot += 360f * u
                d.s *= 1f + 0.35f * a
            }
            "rainbow" -> d.hue += tt * 0.18f
            "bw" -> d.sat *= 1f - clampF(a * 1.4f, 0f, 1f)
            "negative" -> d.invert += a
            "fade_in" -> d.dark += 1f - u
            "fade_out" -> d.dark += u
            "blink" -> {
                val ph = (tt % 1400f) / 1400f
                if (ph > 0.9f) d.dark += a * (1f - Math.abs(ph - 0.95f) / 0.05f)
            }
            "dim" -> d.dark += 0.55f * a
            "lomo" -> { d.sat *= 1f + 0.5f * a; d.dark += 0.06f * a; d.hue += 6f * a }
            "bwfilm" -> { d.sat *= 1f - clampF(a * 1.5f, 0f, 1f); d.dark += 0.05f * a }
        }
    }

    /** Color transform for a clip at the given time, combining filter, adjust and dynamics. */
    fun colorAt(p: Project, i: Int, lt: Long, tg: Long): ColorXf {
        val c = p.clips[i]
        val x = baseColor(c)
        val d = dyn(p, i, lt, tg)
        applyDynColor(x, d)
        return x
    }

    fun applyDynColor(x: ColorXf, d: Dyn) {
        if (Math.abs(d.gr - 1f) > 0.001f || Math.abs(d.gg - 1f) > 0.001f || Math.abs(d.gb - 1f) > 0.001f) x.gain(d.gr, d.gg, d.gb)
        if (d.sepia > 0.001f) x.sepia(clampF(d.sepia, 0f, 1f))
        if (Math.abs(d.contrast - 1f) > 0.001f) x.contrast(d.contrast)
        if (d.lift > 0.001f) x.lift(d.lift)
        x.hue(d.hue)
        x.sat(d.sat)
        x.invert(d.invert)
        x.dark(d.dark)
        x.white(d.white)
        if (Math.abs(d.bright) > 0.001f) x.bright(d.bright)
    }

    fun isDynamic(p: Project, i: Int): Boolean {
        val c = p.clips[i]
        if (c.motion.isNotEmpty() || c.kf.isNotEmpty()) return true
        if (c.animIn != "none" || c.animOut != "none") return true
        if (c.trans != "none" && i > 0) return true
        if (i < p.clips.size - 1 && p.clips[i + 1].trans != "none") return true
        return !p.fxs.isEmpty()
    }

    /** scale, tx, ty, rotation for the clip (user zoom/pan included). */
    fun xform(c: Clip, d: Dyn): FloatArray {
        return floatArrayOf(c.zoom * d.s, c.panX + d.tx, c.panY + d.ty, d.rot, d.sx, d.sy)
    }

    /** Matrix in normalised device coordinates for Media3's MatrixTransformation. */
    fun ndcMatrix(scale: Float, tx: Float, ty: Float, rot: Float, aspect: Float, sx: Float = 1f, sy: Float = 1f): Matrix {
        val m = Matrix()
        if (Math.abs(rot) > 0.001f) {
            m.postScale(aspect, 1f)
            m.postRotate(-rot)
            m.postScale(1f / aspect, 1f)
        }
        if (Math.abs(sx - 1f) > 0.001f || Math.abs(sy - 1f) > 0.001f) m.postScale(sx, sy)
        m.postScale(scale, scale)
        m.postTranslate(tx * 2f, -ty * 2f)
        return m
    }
}
