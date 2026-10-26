package com.rafiei.editor

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import org.json.JSONObject

/** Shape mask of one clip: only the chosen shape stays visible, the rest is painted with [outColor]. */
class ClipMask {
    var shape = "none" // none | hor | mirror | circle | rect | heart | star | strips
    var cx = 0.5f
    var cy = 0.5f
    var sw = 0.5f      // width (fraction of clip width)
    var sh = 0.5f      // height (fraction of clip height)
    var rot = 0f
    var feather = 0.02f
    var invert = false
    var outColor = 0xFF000000.toInt()
    var n = 6          // strips count
    var anim = "none"  // none | slide | pulse | rotate | strips
    var spd = 0.5f
    var amp = 0.3f

    fun toJson(): String {
        val o = JSONObject()
        o.put("s", shape); o.put("cx", cx.toDouble()); o.put("cy", cy.toDouble()); o.put("sw", sw.toDouble()); o.put("sh", sh.toDouble())
        o.put("r", rot.toDouble()); o.put("f", feather.toDouble()); o.put("i", invert); o.put("oc", outColor); o.put("n", n)
        o.put("a", anim); o.put("sp", spd.toDouble()); o.put("am", amp.toDouble())
        return o.toString()
    }

    companion object {
        private val cache = HashMap<String, ClipMask>()
        val SHAPES = listOf(
            "none" to ("هیچ" to "None"), "hor" to ("افقی" to "Horizontal"), "mirror" to ("آینه‌ای" to "Mirror"),
            "circle" to ("دایره" to "Circle"), "rect" to ("مستطیل" to "Rectangle"), "heart" to ("قلب" to "Heart"),
            "star" to ("ستاره" to "Star"), "strips" to ("نوارهای عمودی" to "Strips")
        )
        val ANIMS = listOf(
            "none" to ("ثابت" to "Still"), "slide" to ("حرکت" to "Slide"), "pulse" to ("تپش اندازه" to "Pulse"),
            "rotate" to ("چرخش" to "Rotate"), "strips" to ("نوارها متحرک" to "Strips move")
        )

        fun fromJson(s: String): ClipMask {
            val m = ClipMask()
            if (s.isEmpty()) return m
            try {
                val o = JSONObject(s)
                m.shape = o.optString("s", "none"); m.cx = o.optDouble("cx", 0.5).toFloat(); m.cy = o.optDouble("cy", 0.5).toFloat()
                m.sw = o.optDouble("sw", 0.5).toFloat(); m.sh = o.optDouble("sh", 0.5).toFloat(); m.rot = o.optDouble("r", 0.0).toFloat()
                m.feather = o.optDouble("f", 0.02).toFloat(); m.invert = o.optBoolean("i"); m.outColor = o.optInt("oc", m.outColor)
                m.n = o.optInt("n", 6); m.anim = o.optString("a", "none"); m.spd = o.optDouble("sp", 0.5).toFloat(); m.amp = o.optDouble("am", 0.3).toFloat()
            } catch (e: Exception) {
            }
            return m
        }

        fun of(c: Clip): ClipMask? {
            if (c.mask.isEmpty()) return null
            var m = cache[c.mask]
            if (m == null) {
                m = fromJson(c.mask)
                if (cache.size > 64) cache.clear()
                cache[c.mask] = m
            }
            return if (m.shape == "none") null else m
        }
    }
}

private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val clearX = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

private fun shapePath(m: ClipMask, w: Float, h: Float, tl: Float): Path {
    val p = Path()
    var cx = m.cx * w
    var cy = m.cy * h
    var sw = m.sw * w
    var sh = m.sh * h
    var rot = m.rot
    val t = tl * m.spd * 2f
    val sn = Math.sin(t.toDouble()).toFloat()
    when (m.anim) {
        "slide" -> { cx += sn * m.amp * w; if (m.shape == "hor") cy += sn * m.amp * h }
        "pulse" -> { val k = 1f + sn * m.amp; sw *= k; sh *= k }
        "rotate" -> rot += tl * m.spd * 90f
    }
    val big = Math.hypot(w.toDouble(), h.toDouble()).toFloat() * 2f
    when (m.shape) {
        "hor" -> p.addRect(-big, -big, big, 0f, Path.Direction.CW) // half plane, placed below
        "mirror" -> p.addRect(-big, -sh / 2f, big, sh / 2f, Path.Direction.CW)
        "circle" -> p.addOval(RectF(-sw / 2f, -sh / 2f, sw / 2f, sh / 2f), Path.Direction.CW)
        "rect" -> p.addRect(-sw / 2f, -sh / 2f, sw / 2f, sh / 2f, Path.Direction.CW)
        "heart" -> {
            val a = sw / 2f
            val b = sh / 2f
            p.moveTo(0f, b)
            p.cubicTo(-a * 1.3f, b * 0.2f, -a * 0.9f, -b * 1.0f, 0f, -b * 0.35f)
            p.cubicTo(a * 0.9f, -b * 1.0f, a * 1.3f, b * 0.2f, 0f, b)
            p.close()
        }
        "star" -> {
            val ro = Math.min(sw, sh) / 2f
            val ri = ro * 0.42f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) ro else ri
                val a = (-Math.PI / 2 + i * Math.PI / 5)
                val x = (Math.cos(a) * r).toFloat()
                val y = (Math.sin(a) * r).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
        "strips" -> {
            val n = Math.max(2, m.n)
            val cell = w / n
            val off = if (m.anim == "strips") sn * m.amp * cell else 0f
            val width = cell * clampF(m.sw, 0.05f, 0.95f) * (if (m.anim == "strips") 1f + 0.4f * Math.cos(t.toDouble()).toFloat() * m.amp else 1f)
            val wd = clampF(width, 1f, cell)
            for (i in 0 until n) {
                val x = i * cell + (cell - wd) / 2f + off * (if (i % 2 == 0) 1f else -1f)
                p.addRect(x - w / 2f, -big, x + wd - w / 2f, big, Path.Direction.CW)
            }
            val mt2 = android.graphics.Matrix()
            mt2.postTranslate(w / 2f, h / 2f)
            p.transform(mt2)
            return p
        }
    }
    val mt = android.graphics.Matrix()
    mt.postRotate(rot)
    mt.postTranslate(cx, cy)
    p.transform(mt)
    return p
}

fun drawMask(c: Canvas, rect: FloatArray, m: ClipMask, clipRelMs: Long) {
    val w = rect[2]
    val h = rect[3]
    if (w < 2f || h < 2f) return
    val tl = clipRelMs / 1000f
    val path = shapePath(m, w, h, tl)
    val sc = c.saveLayer(rect[0], rect[1], rect[0] + w, rect[1] + h, null)
    c.translate(rect[0], rect[1])
    c.clipRect(0f, 0f, w, h)
    val p = maskPaint
    p.reset(); p.isAntiAlias = true
    p.color = m.outColor
    p.style = Paint.Style.FILL
    c.drawRect(0f, 0f, w, h, p)
    // cut the visible shape out of the cover colour (inverted: cut everything but the shape)
    p.reset(); p.isAntiAlias = true
    p.color = 0xFF000000.toInt()
    p.xfermode = clearX
    val f = m.feather * Math.min(w, h)
    if (f > 0.5f) p.maskFilter = BlurMaskFilter(f, BlurMaskFilter.Blur.NORMAL)
    if (m.invert) {
        // visible = outside the shape: cover the shape instead
        c.restoreToCount(sc)
        val sc2 = c.saveLayer(rect[0], rect[1], rect[0] + w, rect[1] + h, null)
        c.translate(rect[0], rect[1])
        c.clipRect(0f, 0f, w, h)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.color = m.outColor
        if (f > 0.5f) q.maskFilter = BlurMaskFilter(f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, q)
        c.restoreToCount(sc2)
        return
    }
    c.drawPath(path, p)
    c.restoreToCount(sc)
}
