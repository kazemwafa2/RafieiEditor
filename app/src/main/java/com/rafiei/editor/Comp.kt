package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/** Animated multi-photo compositions (reel, grid, stack, carousel, polaroid ...) drawn over the whole frame as an overlay. */
object Comp {
    val MODES = listOf(
        Pair("reel", "ریل عکس|Photo reel"), Pair("grid", "شبکه‌ی متحرک|Moving grid"), Pair("split", "تقسیم صفحه|Split screen"),
        Pair("stack", "پشته‌ی عکس|Photo stack"), Pair("carousel", "چرخ‌وفلک|Carousel"), Pair("polaroid", "پولاروید|Polaroid"),
        Pair("wall", "دیوار عکس|Photo wall"), Pair("filmstrip", "نوار فیلم|Film strip"), Pair("onebyone", "نمایش یکی‌یکی|One by one"),
        Pair("zoomthrough", "عبور با زوم|Zoom through"), Pair("mosaic", "موزاییک|Mosaic"), Pair("dump", "عکس‌پاشی|Photo dump")
    )

    fun modeName(id: String): String {
        val s = MODES.firstOrNull { it.first == id }?.second ?: id
        val p = s.split("|")
        return if (Lang.fa) p[0] else p.getOrElse(1) { p[0] }
    }

    class Cfg(val mode: String, val uris: List<String>, val per: Float, val bg: Int)

    private val cfgCache = HashMap<String, Cfg>()

    fun cfgOf(o: Overlay): Cfg {
        synchronized(cfgCache) { cfgCache[o.cfg]?.let { return it } }
        var mode = "reel"
        var per = 1.2f
        var bg = 0xFF101018.toInt()
        val uris = ArrayList<String>()
        try {
            val j = JSONObject(o.cfg)
            mode = j.optString("mode", "reel")
            per = j.optDouble("per", 1.2).toFloat()
            bg = j.optInt("bg", bg)
            val a = j.optJSONArray("ph") ?: JSONArray()
            for (i in 0 until a.length()) uris.add(a.getString(i))
        } catch (e: Exception) { }
        val c = Cfg(mode, uris, Math.max(0.2f, per), bg)
        synchronized(cfgCache) {
            if (cfgCache.size > 50) cfgCache.clear()
            cfgCache[o.cfg] = c
        }
        return c
    }

    fun toJson(c: Cfg): String {
        val j = JSONObject()
        j.put("mode", c.mode); j.put("per", c.per.toDouble()); j.put("bg", c.bg)
        val a = JSONArray()
        for (u in c.uris) a.put(u)
        j.put("ph", a)
        return j.toString()
    }

    private val tmpRect = RectF()

    private fun card(c: Canvas, b: Bitmap, cx: Float, cy: Float, w: Float, h: Float, rot: Float, a: Float, r: Float, border: Float = 0f) {
        if (a <= 0.01f || w < 2f || h < 2f) return
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var iw = w
        var ih = h
        var ix = -w / 2f
        var iy = -h / 2f
        if (border > 0f) {
            p.color = Color.argb((90 * a).toInt(), 0, 0, 0)
            tmpRect.set(-w / 2f + 4f, -h / 2f + 8f, w / 2f + 4f, h / 2f + 8f)
            c.drawRoundRect(tmpRect, r, r, p)
            p.color = Color.argb((255 * a).toInt(), 250, 250, 245)
            tmpRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
            c.drawRoundRect(tmpRect, r, r, p)
            ix = -w / 2f + border; iy = -h / 2f + border
            iw = w - border * 2f; ih = h - border * 2f - border * 1.2f
        }
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        val tr = iw / ih
        val src = Rect()
        if (bw / bh > tr) {
            val sw = bh * tr
            src.set(((bw - sw) / 2f).toInt(), 0, ((bw + sw) / 2f).toInt(), b.height)
        } else {
            val sh = bw / tr
            src.set(0, ((bh - sh) / 2f).toInt(), b.width, ((bh + sh) / 2f).toInt())
        }
        c.save()
        val path = Path()
        tmpRect.set(ix, iy, ix + iw, iy + ih)
        path.addRoundRect(tmpRect, r, r, Path.Direction.CW)
        c.clipPath(path)
        p.alpha = (a * 255f).toInt()
        c.drawBitmap(b, src, RectF(ix, iy, ix + iw, iy + ih), p)
        c.restore()
        c.restore()
    }

    private fun fract(x: Float): Float = x - Math.floor(x.toDouble()).toFloat()
    private fun ease(t: Float): Float { val u = clampF(t, 0f, 1f); return u * u * (3f - 2f * u) }
    private fun back(t: Float): Float { val u = clampF(t, 0f, 1f) - 1f; return u * u * (2.7f * u + 1.7f) + 1f }

    fun draw(c: Canvas, o: Overlay, W: Float, H: Float, tMs: Long, alpha: Float) {
        val cfg = cfgOf(o)
        val bmps = ArrayList<Bitmap>()
        for (u in cfg.uris) BmpCache.get(u, 600)?.let { bmps.add(it) }
        val n = bmps.size
        c.save()
        c.clipRect(0f, 0f, W, H)
        val bgp = Paint()
        bgp.color = Color.argb((255 * alpha).toInt(), Color.red(cfg.bg), Color.green(cfg.bg), Color.blue(cfg.bg))
        c.drawRect(0f, 0f, W, H, bgp)
        if (n == 0) { c.restore(); return }
        val T = Math.max(0L, tMs - o.startMs) / 1000f
        val D = cfg.per
        val a = alpha
        val r = Math.min(W, H) * 0.02f
        when (cfg.mode) {
            "reel" -> {
                val cw = Math.min(W * 0.72f, H * 0.5f)
                val ch = cw * 0.68f
                val per = ch * 1.08f
                val total = per * Math.max(n, Math.ceil((H / per + 2f).toDouble()).toInt())
                val off = (T / D * per) % total
                val k = Math.max(n, Math.ceil((H / per + 2f).toDouble()).toInt())
                for (i in 0 until k) {
                    var y = i * per - off
                    if (y < -per) y += total
                    card(c, bmps[i % n], W / 2f, y + ch / 2f, cw, ch, 0f, a, r)
                }
            }
            "grid" -> {
                val cols = 3
                val cw = W / cols
                val ch = cw * 0.68f
                val rows = Math.ceil((H / ch).toDouble()).toInt() + 2
                val ox = Math.sin(T * 0.35).toFloat() * cw * 0.25f
                val oy = (T * ch * 0.25f) % ch
                for (rr in 0 until rows) for (cc in 0 until cols + 1) {
                    val idx = (rr * cols + cc + (T / D).toInt()) % n
                    card(c, bmps[idx], cc * cw + ox - cw / 2f + cw * 0.5f, rr * ch - oy, cw * 0.94f, ch * 0.94f, 0f, a, r * 0.6f)
                }
            }
            "split" -> {
                val pn = Math.min(4, Math.max(2, n))
                val w = W / pn
                for (i in 0 until pn) {
                    val step = ((T / D) + i * 0.5f)
                    val idx = Math.floor(step.toDouble()).toInt()
                    val f = ease(fract(step) * 3f)
                    val b1 = bmps[(idx + i) % n]
                    val b0 = bmps[(idx + i - 1 + n * 4) % n]
                    c.save()
                    c.clipRect(i * w, 0f, (i + 1) * w, H)
                    card(c, b0, i * w + w / 2f, H / 2f - H * f, w, H, 0f, a, 0f)
                    card(c, b1, i * w + w / 2f, H / 2f + H * (1f - f), w, H, 0f, a, 0f)
                    c.restore()
                }
            }
            "stack" -> {
                val cw = Math.min(W * 0.78f, H * 0.5f)
                val ch = cw * 1.2f
                val step = T / D
                val idx = Math.floor(step.toDouble()).toInt()
                val f = ease(fract(step) * 2f)
                for (k in 3 downTo 0) {
                    val b = bmps[(idx + k) % n]
                    val rot = (if (k % 2 == 0) 1 else -1) * (k * 3f + 2f)
                    if (k == 0) card(c, b, W / 2f + W * 0.9f * f, H / 2f - H * 0.1f * f, cw, ch, rot + 25f * f, a * (1f - f * 0.6f), r, cw * 0.03f)
                    else card(c, b, W / 2f, H / 2f, cw, ch, rot * (1f - (if (k == 1) f else 0f) * 0.5f), a, r, cw * 0.03f)
                }
            }
            "carousel" -> {
                val cw = Math.min(W * 0.58f, H * 0.4f)
                val ch = cw * 1.25f
                val pos = T / D
                val order = ArrayList<Pair<Float, Int>>()
                for (i in 0 until Math.max(n, 5)) {
                    var d = (i - pos) % Math.max(n, 5)
                    if (d > Math.max(n, 5) / 2f) d -= Math.max(n, 5)
                    if (d < -Math.max(n, 5) / 2f) d += Math.max(n, 5)
                    order.add(Pair(d, i))
                }
                order.sortByDescending { Math.abs(it.first) }
                for ((d, i) in order) {
                    val s = 1f - Math.min(1f, Math.abs(d)) * 0.35f
                    card(c, bmps[i % n], W / 2f + d * cw * 0.62f, H / 2f, cw * s, ch * s, d * 4f, a * (1f - Math.min(0.7f, Math.abs(d) * 0.18f)), r, 0f)
                }
            }
            "polaroid" -> {
                val cw = Math.min(W * 0.62f, H * 0.38f)
                val ch = cw * 1.15f
                val step = T / D
                val cnt = Math.min(Math.floor(step.toDouble()).toInt() + 1, 40)
                for (i in 0 until cnt) {
                    val born = i.toFloat()
                    val f = back((step - born) * 2.2f)
                    val h1 = Math.sin(i * 12.9898) * 43758.5453
                    val rx = ((h1 - Math.floor(h1)) - 0.5).toFloat()
                    val h2 = Math.sin(i * 78.233) * 12543.1357
                    val ry = ((h2 - Math.floor(h2)) - 0.5).toFloat()
                    val dropY = (1f - Math.min(1f, f)) * -H * 0.7f
                    card(c, bmps[i % n], W / 2f + rx * W * 0.28f, H / 2f + ry * H * 0.22f + dropY, cw, ch, rx * 26f, a * Math.min(1f, f * 2f), r * 0.4f, cw * 0.05f)
                }
            }
            "wall" -> {
                c.save()
                c.rotate(-8f, W / 2f, H / 2f)
                val cw = W * 0.42f
                val ch = cw * 0.7f
                val rows = Math.ceil((H * 1.4f / ch).toDouble()).toInt() + 1
                for (rr in -1 until rows) {
                    val dir = if (rr % 2 == 0) 1f else -1f
                    val off = (T * cw * 0.25f * dir) % (cw * 1.05f)
                    for (cc in -2..4) {
                        val idx = ((rr + 2) * 3 + cc + 4 + n * 10) % n
                        card(c, bmps[idx], cc * cw * 1.05f + off + W * 0.1f, rr * ch * 1.06f + H * 0.05f, cw, ch, 0f, a, r * 0.6f)
                    }
                }
                c.restore()
            }
            "filmstrip" -> {
                val fh = Math.min(H * 0.34f, W * 0.5f)
                val fw = fh * 1.3f
                val gap = fw * 0.08f
                val per = fw + gap
                val off = (T / D * per) % per
                val sp = Paint(Paint.ANTI_ALIAS_FLAG)
                sp.color = Color.argb((255 * a).toInt(), 20, 20, 20)
                c.drawRect(0f, H / 2f - fh / 2f - fh * 0.14f, W, H / 2f + fh / 2f + fh * 0.14f, sp)
                sp.color = Color.argb((255 * a).toInt(), 235, 235, 230)
                val holes = (W / (fh * 0.16f)).toInt() + 3
                for (i in 0 until holes) {
                    val hx = i * fh * 0.16f - (off % (fh * 0.16f))
                    tmpRect.set(hx, H / 2f - fh / 2f - fh * 0.1f, hx + fh * 0.08f, H / 2f - fh / 2f - fh * 0.03f)
                    c.drawRoundRect(tmpRect, 3f, 3f, sp)
                    tmpRect.set(hx, H / 2f + fh / 2f + fh * 0.03f, hx + fh * 0.08f, H / 2f + fh / 2f + fh * 0.1f)
                    c.drawRoundRect(tmpRect, 3f, 3f, sp)
                }
                val k = (W / per).toInt() + 3
                val base = (T / D).toInt()
                for (i in 0 until k) card(c, bmps[(base + i) % n], i * per - off - per / 2f + fw / 2f, H / 2f, fw, fh, 0f, a, 4f)
            }
            "onebyone" -> {
                val cols = if (n <= 4) 2 else 3
                val rows = Math.ceil(n.toDouble() / cols).toInt()
                val cw = W / cols
                val ch = Math.min(H / rows, cw * 1.1f)
                val top = (H - ch * rows) / 2f
                for (i in 0 until n) {
                    val f = back((T / D - i) * 2f)
                    if (T / D < i) continue
                    val rr = i / cols
                    val cc = i % cols
                    card(c, bmps[i], cc * cw + cw / 2f, top + rr * ch + ch / 2f, cw * 0.94f * Math.min(1f, f), ch * 0.94f * Math.min(1f, f), 0f, a * Math.min(1f, f * 2f), r)
                }
            }
            "zoomthrough" -> {
                val step = T / D
                val base = Math.floor(step.toDouble()).toInt()
                for (k in -1..3) {
                    val u = step - base - k
                    val s = Math.pow(2.2, (-u).toDouble()).toFloat()
                    val al = clampF(1f - Math.abs(u + 0.5f - 0.5f) * 0.0f, 0f, 1f) * (if (u > 0.6f) clampF(1f - (u - 0.6f) * 2.5f, 0f, 1f) else 1f)
                    val b = bmps[((base + k) % n + n) % n]
                    val w = W * 0.82f * s
                    val h = w * 0.7f
                    if (s < 0.05f || s > 8f) continue
                    card(c, b, W / 2f, H / 2f, w, h, 0f, a * al * clampF(s * 3f, 0f, 1f), r * s)
                }
            }
            "mosaic" -> {
                val cols = 4
                val cw = W / cols
                val rows = Math.ceil((H / cw).toDouble()).toInt()
                for (rr in 0 until rows) for (cc in 0 until cols) {
                    val cell = rr * cols + cc
                    val ph = T / D + cell * 0.23f
                    val idx = (Math.floor(ph.toDouble()).toInt() + cell) % n
                    val f = fract(ph)
                    val flip = Math.abs(Math.cos(Math.min(1f, f * 3f) * Math.PI / 2 * 2)).toFloat()
                    card(c, bmps[idx], cc * cw + cw / 2f, rr * cw + cw / 2f, cw * 0.96f * Math.max(0.05f, if (f < 0.33f) flip else 1f), cw * 0.96f, 0f, a, r * 0.4f)
                }
            }
            else -> { // dump: photos thrown onto the screen one after another
                val cw = Math.min(W * 0.5f, H * 0.3f)
                val ch = cw * 1.2f
                val step = T / D
                val cnt = Math.min(Math.floor(step.toDouble()).toInt() + 1, 60)
                for (i in 0 until cnt) {
                    val f = back((step - i) * 2.5f)
                    val h1 = Math.sin(i * 91.17) * 43758.5453
                    val h2 = Math.sin(i * 17.33) * 23421.631
                    val h3 = Math.sin(i * 51.7) * 9871.1
                    val x = W * (0.18f + 0.64f * (h1 - Math.floor(h1)).toFloat())
                    val y = H * (0.15f + 0.7f * (h2 - Math.floor(h2)).toFloat())
                    val rot = ((h3 - Math.floor(h3)).toFloat() - 0.5f) * 40f
                    card(c, bmps[i % n], x, y, cw * Math.min(1.2f, 0.3f + 0.7f * f), ch * Math.min(1.2f, 0.3f + 0.7f * f), rot, a * Math.min(1f, f * 2f), r * 0.5f, cw * 0.03f)
                }
            }
        }
        c.restore()
    }
}
