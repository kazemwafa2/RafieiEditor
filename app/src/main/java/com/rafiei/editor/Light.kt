package com.rafiei.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import org.json.JSONObject

/** Studio light of one clip (ported from part 3 of the original app). */
class ClipLight {
    var mode = "off" // off | beam | focus | spot  (strip is a thin beam)
    var lx = 0.58f
    var ly = 0.5f
    var ang = -72f
    var wid = 0.34f
    var soft = 0.06f
    var rad = 0.4f
    var dark = 0.9f
    var glow = 0.12f
    var color = 0xFFFFE6C8.toInt()
    var blur = 22f
    var anim = "none" // none | sweep | orbit | vert | rotate | pulse
    var spd = 0.5f
    var amp = 0.2f
    var dir = 1f      // 1 or -1 : motion direction
    var phase = 0f    // 0..1 start offset of the motion
    var ampY = -1f    // vertical range (<0 = same as amp)

    fun copy(): ClipLight = fromJson(toJson())

    fun toJson(): String {
        val o = JSONObject()
        o.put("m", mode); o.put("lx", lx.toDouble()); o.put("ly", ly.toDouble()); o.put("ang", ang.toDouble())
        o.put("wid", wid.toDouble()); o.put("soft", soft.toDouble()); o.put("rad", rad.toDouble())
        o.put("dark", dark.toDouble()); o.put("glow", glow.toDouble()); o.put("col", color)
        o.put("blur", blur.toDouble()); o.put("an", anim); o.put("spd", spd.toDouble()); o.put("amp", amp.toDouble())
        o.put("dir", dir.toDouble()); o.put("ph", phase.toDouble()); o.put("ay", ampY.toDouble())
        return o.toString()
    }

    /** Applies one of the studio presets, keeping the animation settings. */
    fun applyPreset(id: String) {
        val keepA = anim
        val keepS = spd
        val keepP = amp
        val keepD = dir
        val keepPh = phase
        val keepY = ampY
        val b = ClipLight()
        when (id) {
            "beam" -> { b.mode = "beam"; b.lx = 0.6f; b.ly = 0.5f; b.ang = -72f; b.wid = 0.34f; b.soft = 0.06f; b.dark = 0.9f; b.glow = 0.12f; b.color = 0xFFFFE6C8.toInt() }
            "focus" -> { b.mode = "focus"; b.lx = 0.5f; b.ly = 0.42f; b.rad = 0.36f; b.soft = 0.5f; b.blur = 22f; b.dark = 0.25f }
            "strip" -> { b.mode = "beam"; b.lx = 0.5f; b.ly = 0.47f; b.ang = 1.5f; b.wid = 0.07f; b.soft = 0.02f; b.dark = 0.4f; b.glow = 0.9f; b.color = 0xFFFFD89A.toInt() }
            "spot" -> { b.mode = "spot"; b.lx = 0.5f; b.ly = 0.4f; b.rad = 0.5f; b.soft = 0.6f; b.dark = 0.88f; b.glow = 0.1f; b.color = 0xFFFFF0D8.toInt() }
            "eyes" -> { b.mode = "beam"; b.lx = 0.5f; b.ly = 0.36f; b.ang = 0f; b.wid = 0.12f; b.soft = 0.05f; b.dark = 0.75f; b.glow = 0.55f; b.color = 0xFFFFE9C0.toInt() }
            "vbeam" -> { b.mode = "beam"; b.lx = 0.5f; b.ly = 0.5f; b.ang = 90f; b.wid = 0.22f; b.soft = 0.05f; b.dark = 0.85f; b.glow = 0.25f; b.color = 0xFFFFE6C8.toInt() }
            "diag" -> { b.mode = "beam"; b.lx = 0.5f; b.ly = 0.5f; b.ang = -45f; b.wid = 0.2f; b.soft = 0.06f; b.dark = 0.85f; b.glow = 0.3f; b.color = 0xFFFFE6C8.toInt() }
            else -> { b.mode = "off" }
        }
        mode = b.mode; lx = b.lx; ly = b.ly; ang = b.ang; wid = b.wid; soft = b.soft; rad = b.rad
        dark = b.dark; glow = b.glow; color = b.color; blur = b.blur
        anim = keepA; spd = keepS; amp = keepP; dir = keepD; phase = keepPh; ampY = keepY
    }

    fun isPreset(id: String): Boolean {
        val b = ClipLight()
        b.applyPreset(id)
        return b.mode == mode && Math.abs(b.wid - wid) < 0.001f && Math.abs(b.dark - dark) < 0.001f && Math.abs(b.rad - rad) < 0.001f && Math.abs(b.glow - glow) < 0.001f
    }

    companion object {
        private val cache = HashMap<String, ClipLight>()

        val PRESETS = listOf(
            "beam" to "پرتو اریب",
            "focus" to "تمرکز و حاشیه محو",
            "strip" to "نوار نور",
            "eyes" to "نوار روی چشم",
            "vbeam" to "پرتو عمودی",
            "diag" to "پرتو مورب",
            "spot" to "نورافکن",
            "off" to "بدون نور"
        )

        fun fromJson(s: String): ClipLight {
            val l = ClipLight()
            if (s.isEmpty()) return l
            try {
                val o = JSONObject(s)
                l.mode = o.optString("m", "off")
                l.lx = o.optDouble("lx", 0.58).toFloat(); l.ly = o.optDouble("ly", 0.5).toFloat(); l.ang = o.optDouble("ang", -72.0).toFloat()
                l.wid = o.optDouble("wid", 0.34).toFloat(); l.soft = o.optDouble("soft", 0.06).toFloat(); l.rad = o.optDouble("rad", 0.4).toFloat()
                l.dark = o.optDouble("dark", 0.9).toFloat(); l.glow = o.optDouble("glow", 0.12).toFloat(); l.color = o.optInt("col", l.color)
                l.blur = o.optDouble("blur", 22.0).toFloat(); l.anim = o.optString("an", "none")
                l.spd = o.optDouble("spd", 0.5).toFloat(); l.amp = o.optDouble("amp", 0.2).toFloat()
                l.dir = o.optDouble("dir", 1.0).toFloat(); l.phase = o.optDouble("ph", 0.0).toFloat(); l.ampY = o.optDouble("ay", -1.0).toFloat()
            } catch (e: Exception) {
            }
            return l
        }

        /** Parsed, cached light of a clip (null when off). */
        fun of(c: Clip): ClipLight? {
            if (c.light.isEmpty()) return null
            val key = c.light
            var l = cache[key]
            if (l == null) {
                l = fromJson(key)
                if (cache.size > 64) cache.clear()
                cache[key] = l
            }
            return if (l.mode == "off") null else l
        }
    }
}

private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

private fun darkSpot(c: Canvas, W: Float, H: Float, cx: Float, cy: Float, R: Float, soft: Float, dark: Float) {
    val r0 = R * Math.max(0f, 1f - soft) * 0.5f
    val d = clampF(dark, 0f, 1f)
    val p = lightPaint
    p.reset()
    p.isAntiAlias = true
    val f0 = clampF(r0 / R, 0f, 0.95f)
    val cols = intArrayOf(0, 0, Color.argb((d * 0.12f * 255).toInt(), 0, 0, 0), Color.argb((d * 0.6f * 255).toInt(), 0, 0, 0), Color.argb((d * 255).toInt(), 0, 0, 0))
    val pos = floatArrayOf(0f, f0, f0 + (1f - f0) * 0.4f, f0 + (1f - f0) * 0.75f, 1f)
    p.shader = RadialGradient(cx, cy, Math.max(1f, R), cols, pos, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
}

private fun addGlow(p: Paint) {
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
}

/**
 * Draws the light over the clip. [rect] = x, y, w, h of the clip area on the canvas;
 * [clipRelMs] = time inside the clip; [W] is the canvas width (unused scale hint).
 */
fun drawLight(c: Canvas, rect: FloatArray, L: ClipLight, tMs: Long, clipRelMs: Long, W: Float) {
    if (L.mode == "off") return
    val w = rect[2]
    val h = rect[3]
    if (w < 2f || h < 2f) return
    val tl = clipRelMs / 1000f
    var lx = L.lx
    var ly = L.ly
    var rad = L.rad
    var ang = L.ang
    var wid = L.wid
    val s = L.spd * tl * L.dir
    val ph = (L.phase * 2.0 * Math.PI).toFloat()
    val ax = L.amp
    val ay = if (L.ampY < 0f) L.amp else L.ampY
    val t2 = s * 2f + ph
    fun sn(x: Float) = Math.sin(x.toDouble()).toFloat()
    fun cs(x: Float) = Math.cos(x.toDouble()).toFloat()
    // triangle wave -1..1 (linear bounce) and sawtooth wrap -1..1
    fun tri(x: Float): Float { val k = (x / (2f * Math.PI.toFloat())) % 1f; val u = if (k < 0f) k + 1f else k; return if (u < 0.5f) u * 4f - 1f else 3f - u * 4f }
    fun saw(x: Float): Float { val k = (x / (2f * Math.PI.toFloat())) % 1f; val u = if (k < 0f) k + 1f else k; return u * 2f - 1f }
    when (L.anim) {
        "sweep" -> lx += sn(t2) * ax
        "vert" -> ly += sn(t2) * ay
        "orbit" -> { lx += cs(t2) * ax * 0.7f; ly += sn(t2) * ay * 0.7f }
        "diag" -> { lx += sn(t2) * ax; ly += sn(t2) * ay }
        "diag2" -> { lx += sn(t2) * ax; ly -= sn(t2) * ay }
        "fig8" -> { lx += sn(t2) * ax; ly += sn(t2 * 2f) * ay * 0.6f }
        "bounce" -> { lx += tri(t2) * ax; ly += tri(t2 * 1.37f) * ay }
        "wander" -> { lx += (sn(t2 * 0.9f) + 0.5f * sn(t2 * 2.3f + 1f)) * ax * 0.7f; ly += (cs(t2 * 1.1f) + 0.5f * sn(t2 * 1.9f + 2f)) * ay * 0.7f }
        "scanx" -> lx += saw(t2) * ax * 1.6f
        "scany" -> ly += saw(t2) * ay * 1.6f
        "rotate" -> ang += sn(t2) * ax * 60f
        "spin" -> ang += s * 60f + L.phase * 360f
        "orbitspin" -> { lx += cs(t2) * ax * 0.7f; ly += sn(t2) * ay * 0.7f; ang += s * 40f }
        "pulse" -> { val k = 1f + 0.25f * sn(s * 3f + ph); rad *= k; wid *= k }
        "breathe" -> { val k = 1f + 0.5f * sn(s * 2f + ph); rad *= k; wid *= k }
    }
    val cx = lx * w
    val cy = ly * h
    val m = Math.min(w, h)
    val mx = Math.max(w, h)
    c.save()
    c.clipRect(rect[0], rect[1], rect[0] + w, rect[1] + h)
    c.translate(rect[0], rect[1])
    val p = lightPaint
    when (L.mode) {
        "spot" -> {
            val R = Math.max(2f, rad * mx)
            darkSpot(c, w, h, cx, cy, R, L.soft, L.dark)
            if (L.glow > 0.001f) {
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(cx, cy, Math.max(1f, R * 0.8f), withAlpha(L.color, L.glow), withAlpha(L.color, 0f), Shader.TileMode.CLAMP)
                addGlow(p)
                c.drawRect(0f, 0f, w, h, p)
            }
        }
        "beam" -> {
            val diag = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
            val sp = Math.max(0.5f, L.soft * m)
            val bw = Math.max(1f, wid * m)
            c.save()
            c.translate(cx, cy)
            c.rotate(ang)
            // dark mask with the beam cut out (soft edges)
            val half = bw / 2f
            val e0 = -diag
            val e1 = -half - sp
            val e2 = -half + sp
            val e3 = half - sp
            val e4 = half + sp
            val span = diag * 2f
            val d = clampF(L.dark, 0f, 1f)
            val dk = Color.argb((d * 255).toInt(), 0, 0, 0)
            val cl = 0
            val ys = floatArrayOf(e0, e1, e2, e3, e4, diag)
            val cols = intArrayOf(dk, dk, cl, cl, dk, dk)
            val ps = FloatArray(6)
            var last = 0f
            for (i in 0 until 6) {
                last = Math.max(last, clampF((ys[i] + diag) / span, 0f, 1f))
                ps[i] = last
            }
            p.reset(); p.isAntiAlias = true
            p.shader = LinearGradient(0f, -diag, 0f, diag, cols, ps, Shader.TileMode.CLAMP)
            c.drawRect(-diag, -diag, diag, diag, p)
            if (L.glow > 0.001f) {
                p.reset(); p.isAntiAlias = true
                val gc = intArrayOf(withAlpha(L.color, 0f), withAlpha(L.color, L.glow), withAlpha(L.color, 0f))
                p.shader = LinearGradient(0f, -half - sp * 0.8f, 0f, half + sp * 0.8f, gc, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                addGlow(p)
                c.drawRect(-diag, -half - sp * 0.8f, diag, half + sp * 0.8f, p)
            }
            c.restore()
        }
        "focus" -> {
            val R = Math.max(2f, rad * mx)
            val i = Math.max(0f, 1f - L.soft)
            // surround: darker and (approximately) defocused by a soft veil
            val veil = clampF(L.blur / 120f, 0f, 0.5f)
            val cols = intArrayOf(0, 0, Color.argb((veil * 255).toInt(), 0, 0, 0))
            val ps = floatArrayOf(0f, Math.min(0.99f, i), 1f)
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(cx, cy, R, cols, ps, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            if (L.dark > 0.001f) darkSpot(c, w, h, cx, cy, R * 1.5f, 0.7f, L.dark)
        }
    }
    c.restore()
}
