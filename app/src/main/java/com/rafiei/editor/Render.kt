package com.rafiei.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import java.io.File

object Fonts {
    val BUILTIN = listOf("sans", "serif", "mono", "casual", "cursive", "condensed", "light", "medium", "black", "thin")
    private val cache = HashMap<String, Typeface>()

    fun fontsDir(c: Context): File = dirOf(c, "fonts")

    fun imported(c: Context): List<String> {
        val out = ArrayList<String>()
        val fs = fontsDir(c).listFiles() ?: return out
        for (f in fs) if (f.name.lowercase().endsWith(".ttf") || f.name.lowercase().endsWith(".otf")) out.add("file:" + f.name)
        out.sort()
        return out
    }

    fun displayName(n: String): String {
        if (n.startsWith("file:")) return n.substring(5).substringBeforeLast('.')
        return when (n) {
            "sans" -> L("ساده", "Sans")
            "serif" -> L("سریف", "Serif")
            "mono" -> L("تایپ‌رایتر", "Mono")
            "casual" -> L("دست‌نویس", "Casual")
            "cursive" -> L("خوشنویسی", "Cursive")
            "condensed" -> L("فشرده", "Condensed")
            "light" -> L("نازک", "Light")
            "medium" -> L("متوسط", "Medium")
            "black" -> L("ضخیم", "Black")
            "thin" -> L("بسیار نازک", "Thin")
            else -> n
        }
    }

    fun get(name: String, bold: Boolean, italic: Boolean): Typeface {
        val style = if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL
        val key = "$name/$style"
        val hit = cache[key]
        if (hit != null) return hit
        var tf: Typeface
        try {
            if (name.startsWith("file:")) {
                val f = File(fontsDir(App.ctx), name.substring(5))
                tf = Typeface.create(Typeface.createFromFile(f), style)
            } else {
                val fam = when (name) {
                    "serif" -> "serif"
                    "mono" -> "monospace"
                    "casual" -> "casual"
                    "cursive" -> "cursive"
                    "condensed" -> "sans-serif-condensed"
                    "light" -> "sans-serif-light"
                    "medium" -> "sans-serif-medium"
                    "black" -> "sans-serif-black"
                    "thin" -> "sans-serif-thin"
                    else -> "sans-serif"
                }
                tf = Typeface.create(fam, style)
            }
        } catch (e: Exception) {
            tf = Typeface.create("sans-serif", style)
        }
        cache[key] = tf
        return tf
    }
}

/** Colour keying (green screen / plain background removal) for overlay pictures. */
object Chroma {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun key(src: Bitmap, srcKey: String, color: Int, tol: Float, soft: Float): Bitmap {
        val k = "$srcKey|$color|$tol|$soft"
        synchronized(cache) { cache.get(k)?.let { return it } }
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val kr = Color.red(color)
        val kg = Color.green(color)
        val kb = Color.blue(color)
        val sf = Math.max(0.001f, soft)
        for (i in px.indices) {
            val v = px[i]
            val dr = Color.red(v) - kr
            val dg = Color.green(v) - kg
            val db = Color.blue(v) - kb
            val d = Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat() / 441.67f
            val f = clampF((d - tol) / sf, 0f, 1f)
            if (f < 1f) {
                val a = (Color.alpha(v) * f).toInt()
                px[i] = Color.argb(a, Color.red(v), Color.green(v), Color.blue(v))
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        synchronized(cache) { cache.put(k, out) }
        return out
    }
}

object BmpCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(uri: String, maxSide: Int): Bitmap? {
        val key = "$uri@$maxSide"
        synchronized(cache) {
            val h = cache.get(key)
            if (h != null) return h
        }
        val b = MediaUtil.loadBitmapFile(MediaUtil.pathOf(uri), maxSide) ?: return null
        synchronized(cache) { cache.put(key, b) }
        return b
    }
}

class AnimState {
    var alpha = 1f
    var scale = 1f
    var dx = 0f
    var dy = 0f
    var rot = 0f
    var reveal = 1f
}

internal class TextLayoutEntry(val fill: StaticLayout, val stroke: StaticLayout, val fillPaint: TextPaint, val strokePaint: TextPaint, val w: Float, val h: Float)

object Shapes {
    val IDS = listOf("heart", "star", "circle", "square", "triangle", "arrow", "bubble", "sparkle", "check", "cross", "moon", "sun", "cloud", "bolt", "crown", "flower", "drop", "burst")

    /** Path inside the unit box centred at (0,0) with side 1. */
    fun path(id: String): Path {
        val p = Path()
        when (id) {
            "heart" -> {
                p.moveTo(0f, 0.38f)
                p.cubicTo(-0.70f, -0.05f, -0.38f, -0.52f, 0f, -0.18f)
                p.cubicTo(0.38f, -0.52f, 0.70f, -0.05f, 0f, 0.38f)
                p.close()
            }
            "star" -> poly(p, 5, 0.5f, 0.2f, -90f)
            "burst" -> poly(p, 12, 0.5f, 0.33f, -90f)
            "circle" -> p.addCircle(0f, 0f, 0.5f, Path.Direction.CW)
            "square" -> p.addRoundRect(RectF(-0.46f, -0.46f, 0.46f, 0.46f), 0.08f, 0.08f, Path.Direction.CW)
            "triangle" -> {
                p.moveTo(0f, -0.46f); p.lineTo(0.5f, 0.4f); p.lineTo(-0.5f, 0.4f); p.close()
            }
            "arrow" -> {
                p.moveTo(-0.5f, -0.14f); p.lineTo(0.1f, -0.14f); p.lineTo(0.1f, -0.38f); p.lineTo(0.5f, 0f)
                p.lineTo(0.1f, 0.38f); p.lineTo(0.1f, 0.14f); p.lineTo(-0.5f, 0.14f); p.close()
            }
            "bubble" -> {
                p.addRoundRect(RectF(-0.5f, -0.4f, 0.5f, 0.2f), 0.18f, 0.18f, Path.Direction.CW)
                val t = Path()
                t.moveTo(-0.2f, 0.15f); t.lineTo(-0.28f, 0.45f); t.lineTo(0.06f, 0.15f); t.close()
                p.op(t, Path.Op.UNION)
            }
            "sparkle" -> {
                p.moveTo(0f, -0.5f)
                p.quadTo(0.05f, -0.05f, 0.5f, 0f)
                p.quadTo(0.05f, 0.05f, 0f, 0.5f)
                p.quadTo(-0.05f, 0.05f, -0.5f, 0f)
                p.quadTo(-0.05f, -0.05f, 0f, -0.5f)
                p.close()
            }
            "check" -> {
                p.moveTo(-0.45f, 0.02f); p.lineTo(-0.3f, -0.13f); p.lineTo(-0.1f, 0.07f); p.lineTo(0.3f, -0.36f)
                p.lineTo(0.45f, -0.21f); p.lineTo(-0.1f, 0.38f); p.close()
            }
            "cross" -> {
                p.moveTo(-0.4f, -0.28f); p.lineTo(-0.28f, -0.4f); p.lineTo(0f, -0.12f); p.lineTo(0.28f, -0.4f)
                p.lineTo(0.4f, -0.28f); p.lineTo(0.12f, 0f); p.lineTo(0.4f, 0.28f); p.lineTo(0.28f, 0.4f)
                p.lineTo(0f, 0.12f); p.lineTo(-0.28f, 0.4f); p.lineTo(-0.4f, 0.28f); p.lineTo(-0.12f, 0f); p.close()
            }
            "moon" -> {
                p.addCircle(0f, 0f, 0.46f, Path.Direction.CW)
                val q = Path()
                q.addCircle(0.22f, -0.12f, 0.4f, Path.Direction.CW)
                p.op(q, Path.Op.DIFFERENCE)
            }
            "sun" -> {
                p.addCircle(0f, 0f, 0.22f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val r = Path()
                    r.addRoundRect(RectF(0.30f, -0.04f, 0.48f, 0.04f), 0.04f, 0.04f, Path.Direction.CW)
                    val m = android.graphics.Matrix()
                    m.setRotate(i * 45f)
                    r.transform(m)
                    p.op(r, Path.Op.UNION)
                }
            }
            "cloud" -> {
                p.addCircle(-0.22f, 0.08f, 0.22f, Path.Direction.CW)
                val a = Path(); a.addCircle(0.02f, -0.08f, 0.28f, Path.Direction.CW)
                val b = Path(); b.addCircle(0.27f, 0.1f, 0.2f, Path.Direction.CW)
                val c = Path(); c.addRect(-0.22f, 0.05f, 0.27f, 0.3f, Path.Direction.CW)
                p.op(a, Path.Op.UNION); p.op(b, Path.Op.UNION); p.op(c, Path.Op.UNION)
            }
            "bolt" -> {
                p.moveTo(0.1f, -0.5f); p.lineTo(-0.3f, 0.06f); p.lineTo(-0.02f, 0.06f); p.lineTo(-0.12f, 0.5f)
                p.lineTo(0.32f, -0.1f); p.lineTo(0.04f, -0.1f); p.close()
            }
            "crown" -> {
                p.moveTo(-0.5f, 0.35f); p.lineTo(-0.5f, -0.25f); p.lineTo(-0.22f, 0.05f); p.lineTo(0f, -0.38f)
                p.lineTo(0.22f, 0.05f); p.lineTo(0.5f, -0.25f); p.lineTo(0.5f, 0.35f); p.close()
            }
            "flower" -> {
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0)
                    val q = Path()
                    q.addCircle((Math.cos(a) * 0.24).toFloat(), (Math.sin(a) * 0.24).toFloat(), 0.17f, Path.Direction.CW)
                    p.op(q, Path.Op.UNION)
                }
                val c = Path(); c.addCircle(0f, 0f, 0.15f, Path.Direction.CW)
                p.op(c, Path.Op.UNION)
            }
            "drop" -> {
                p.moveTo(0f, -0.5f)
                p.cubicTo(0.12f, -0.2f, 0.4f, 0.0f, 0.4f, 0.17f)
                p.cubicTo(0.4f, 0.4f, 0.2f, 0.5f, 0f, 0.5f)
                p.cubicTo(-0.2f, 0.5f, -0.4f, 0.4f, -0.4f, 0.17f)
                p.cubicTo(-0.4f, 0.0f, -0.12f, -0.2f, 0f, -0.5f)
                p.close()
            }
            else -> p.addCircle(0f, 0f, 0.4f, Path.Direction.CW)
        }
        return p
    }

    private fun poly(p: Path, n: Int, rOut: Float, rIn: Float, startDeg: Float) {
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) rOut else rIn
            val a = Math.toRadians((startDeg + i * 180.0 / n))
            val x = (Math.cos(a) * r).toFloat()
            val y = (Math.sin(a) * r).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }
}

/** Canvas based renderer shared by the live preview and the exporter. */
object Render {
    private val layoutCache = LruCache<String, TextLayoutEntry>(40)
    private val shapeCache = HashMap<String, Path>()
    private val clearPaint = Paint().also { it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val noiseTiles = ArrayList<Bitmap>()

    private fun ease(x: Float): Float {
        val t = clampF(x, 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun animState(o: Overlay, tMs: Long): AnimState {
        val a = AnimState()
        val local = tMs - o.startMs
        val remaining = o.endMs() - tMs
        if (o.animIn != "none" && o.animInMs > 0 && local < o.animInMs) {
            applyOv(a, o.animIn, ease(1f - local.toFloat() / o.animInMs), true)
        }
        if (o.animOut != "none" && o.animOutMs > 0 && remaining < o.animOutMs) {
            applyOv(a, o.animOut, ease(1f - remaining.toFloat() / o.animOutMs), false)
        }
        return a
    }

    private fun applyOv(a: AnimState, kind: String, e: Float, isIn: Boolean) {
        if (e <= 0f) return
        when (kind) {
            "fade" -> a.alpha *= 1f - e
            "pop" -> a.scale *= Math.max(0.01f, 1f - e)
            "bounce" -> a.scale *= Math.max(0.01f, 1f - e + 0.18f * Math.sin(e * Math.PI).toFloat())
            "zoom" -> { a.scale *= 1f + 0.8f * e; a.alpha *= 1f - e }
            "slide_left" -> a.dx += if (isIn) 0.7f * e else -0.7f * e
            "slide_right" -> a.dx += if (isIn) -0.7f * e else 0.7f * e
            "slide_up" -> a.dy += if (isIn) 0.7f * e else -0.7f * e
            "slide_down" -> a.dy += if (isIn) -0.7f * e else 0.7f * e
            "spin" -> { a.rot += (if (isIn) -1f else 1f) * e * 360f; a.scale *= Math.max(0.01f, 1f - e); a.alpha *= 1f - 0.5f * e }
            "typewriter" -> if (isIn) a.reveal = 1f - e
        }
    }

    private fun textEntry(o: Overlay, W: Float, txt: String): TextLayoutEntry? {
        if (txt.isEmpty()) return null
        val px = Math.max(4f, o.size * W)
        val wrap = Math.max(40f, W * 0.9f).toInt()
        val key = txt + "|" + o.font + "|" + px + "|" + o.bold + "|" + o.italic + "|" + o.align + "|" + o.spacing + "|" + wrap + "|" + o.outlineW
        val hit = layoutCache.get(key)
        if (hit != null) return hit
        val tf = Fonts.get(o.font, o.bold, o.italic)
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fp.typeface = tf
        fp.textSize = px
        fp.letterSpacing = o.spacing
        val sp = TextPaint(fp)
        sp.style = Paint.Style.STROKE
        sp.strokeJoin = Paint.Join.ROUND
        sp.strokeWidth = Math.max(0.1f, px * o.outlineW * 0.35f)
        val al = when (o.align) {
            0 -> Layout.Alignment.ALIGN_NORMAL
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val first = StaticLayout.Builder.obtain(txt, 0, txt.length, fp, wrap).setAlignment(al).setIncludePad(false).build()
        var maxLine = 1f
        for (i in 0 until first.lineCount) maxLine = Math.max(maxLine, first.getLineWidth(i))
        val width = Math.ceil(maxLine.toDouble()).toInt() + 2
        val fill = StaticLayout.Builder.obtain(txt, 0, txt.length, fp, width).setAlignment(al).setIncludePad(false).build()
        val stroke = StaticLayout.Builder.obtain(txt, 0, txt.length, sp, width).setAlignment(al).setIncludePad(false).build()
        val e = TextLayoutEntry(fill, stroke, fp, sp, width.toFloat(), fill.height.toFloat())
        layoutCache.put(key, e)
        return e
    }

    /** Unscaled size (px) of an overlay on a canvas of width W. */
    fun overlaySize(o: Overlay, W: Float): FloatArray {
        if (o.kind == "text") {
            val e = textEntry(o, W, if (o.text.isEmpty()) " " else o.text)
            val pad = o.size * W * 0.25f
            if (e == null) return floatArrayOf(10f, 10f)
            return floatArrayOf(e.w + pad * 2f, e.h + pad * 1.2f)
        }
        if (o.kind == "eq") return Eq.size(W)
        if (o.kind == "comp") return floatArrayOf(W * 0.6f, W * 0.9f)
        val base = W * 0.30f
        if (o.kind == "image") {
            val asp = if (o.imgAspect <= 0f) 1f else o.imgAspect
            return if (asp >= 1f) floatArrayOf(base * 1.4f, base * 1.4f / asp) else floatArrayOf(base * 1.4f * asp, base * 1.4f)
        }
        return floatArrayOf(base, base)
    }

    fun drawOverlay(c: Canvas, o: Overlay, W: Float, H: Float, tMs: Long) {
        val a = animState(o, tMs)
        val alpha = clampF(o.opacity * a.alpha, 0f, 1f)
        if (alpha <= 0.003f) return
        if (o.kind == "comp") { Comp.draw(c, o, W, H, tMs, alpha); return }
        c.save()
        c.translate((o.cx + a.dx) * W, (o.cy + a.dy) * H)
        c.rotate(o.rot + a.rot)
        val sc = o.scale * a.scale
        c.scale(if (o.flipH) -sc else sc, sc)
        when (o.kind) {
            "text" -> drawText(c, o, W, a, alpha, tMs)
            "image" -> drawImage(c, o, W, alpha, tMs)
            "eq" -> Eq.draw(c, o, W, tMs, alpha)
            else -> drawSticker(c, o, W, alpha)
        }
        c.restore()
    }

    private fun drawText(c: Canvas, o: Overlay, W: Float, a: AnimState, alpha: Float, tMs: Long = 0L) {
        var txt = o.text
        if (a.reveal < 0.999f) {
            val n = Math.round(txt.length * clampF(a.reveal, 0f, 1f))
            txt = txt.substring(0, clampI(n, 0, txt.length))
        }
        val e = textEntry(o, W, txt) ?: return
        val full = textEntry(o, W, if (o.text.isEmpty()) " " else o.text) ?: e
        val pad = o.size * W * 0.25f
        val bw = full.w + pad * 2f
        val bh = full.h + pad * 1.2f
        c.translate(-bw / 2f, -bh / 2f)
        if (o.bgAlpha > 0.01f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = withAlpha(o.bgColor, o.bgAlpha * alpha)
            c.drawRoundRect(RectF(0f, 0f, bw, bh), pad * 0.8f, pad * 0.8f, p)
        }
        // lines are aligned inside the full-width box
        val xoff = when (o.align) {
            0 -> pad
            2 -> bw - pad - e.w
            else -> (bw - e.w) / 2f
        }
        c.translate(xoff, pad * 0.6f)
        if (o.shadow > 0.01f) {
            e.fillPaint.setShadowLayer(o.size * W * 0.12f * o.shadow + 1f, o.size * W * 0.06f * o.shadow, o.size * W * 0.06f * o.shadow, withAlpha(o.shadowColor, alpha * 0.9f))
        } else {
            e.fillPaint.clearShadowLayer()
        }
        val fx = o.tfx
        val px = o.size * W
        if (fx == "echo" || fx == "echoh") {
            // big repeated / echoing text: faded outline copies above and below (or left/right) that slowly breathe
            val t = (tMs - o.startMs) / 1000f
            val n = 5
            val sw = Math.max(1.5f, px * 0.03f)
            val old = e.strokePaint.strokeWidth
            e.strokePaint.strokeWidth = sw
            for (k in n downTo 1) {
                val breathe = 1f + 0.12f * Math.sin((t * 2.2f - k * 0.5f).toDouble()).toFloat()
                val off = k * e.h * 0.62f * breathe
                for (sgn in intArrayOf(-1, 1)) {
                    c.save()
                    if (fx == "echo") c.translate(0f, sgn * off) else c.translate(sgn * k * e.w * 0.12f * breathe, 0f)
                    e.strokePaint.color = withAlpha(o.color, alpha * (0.55f - k * 0.09f))
                    e.stroke.draw(c)
                    c.restore()
                }
            }
            e.strokePaint.strokeWidth = old
        }
        if (fx == "glass") {
            val sw = Math.max(2f, px * 0.035f)
            val old = e.strokePaint.strokeWidth
            e.strokePaint.strokeWidth = sw
            e.strokePaint.color = withAlpha(Color.WHITE, alpha * 0.9f)
            e.stroke.draw(c)
            e.strokePaint.strokeWidth = old
            e.fillPaint.color = withAlpha(o.color, alpha * 0.22f)
            e.fill.draw(c)
            // gloss: lighter upper half
            c.save()
            c.clipRect(0f, 0f, e.w, e.h * 0.45f)
            e.fillPaint.color = withAlpha(Color.WHITE, alpha * 0.28f)
            e.fill.draw(c)
            c.restore()
            e.fillPaint.clearShadowLayer()
            return
        }
        if (fx == "neon") {
            e.fillPaint.setShadowLayer(px * 0.35f, 0f, 0f, withAlpha(o.color, alpha))
            e.fillPaint.color = withAlpha(Color.WHITE, alpha)
            e.fill.draw(c)
            e.fillPaint.color = withAlpha(o.color, alpha * 0.9f)
            e.fill.draw(c)
            e.fillPaint.clearShadowLayer()
            return
        }
        if (o.outlineW > 0.01f) {
            e.strokePaint.color = withAlpha(o.outlineColor, alpha)
            e.stroke.draw(c)
        }
        e.fillPaint.color = withAlpha(o.color, alpha)
        e.fill.draw(c)
        e.fillPaint.clearShadowLayer()
    }

    private fun drawImage(c: Canvas, o: Overlay, W: Float, alpha: Float, tMs: Long) {
        var key = o.uri
        var b: Bitmap?
        if (o.nf > 0) {
            val i = ((Math.max(0L, tMs - o.startMs) * o.fps / 1000L) % o.nf).toInt()
            key = android.net.Uri.fromFile(java.io.File(o.frames, "f$i.jpg")).toString()
            b = BmpCache.get(key, 640)
        } else {
            b = BmpCache.get(o.uri, 1024)
        }
        if (b == null) return
        if (o.chroma != 0) b = Chroma.key(b, key, o.chroma, o.chromaTol, o.chromaSoft)
        val sz = overlaySize(o, W)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.alpha = (alpha * 255f).toInt()
        c.drawBitmap(b, null, RectF(-sz[0] / 2f, -sz[1] / 2f, sz[0] / 2f, sz[1] / 2f), p)
    }

    private fun drawSticker(c: Canvas, o: Overlay, W: Float, alpha: Float) {
        val base = W * 0.30f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (o.glyph.startsWith("shape:")) {
            val id = o.glyph.substring(6)
            val path = shapeCache.getOrPut(id) { Shapes.path(id) }
            c.save()
            c.scale(base, base)
            p.style = Paint.Style.FILL
            p.color = withAlpha(if (o.color == Color.WHITE && id == "heart") 0xFFFF4D6D.toInt() else o.color, alpha)
            c.drawPath(path, p)
            c.restore()
        } else {
            p.textAlign = Paint.Align.CENTER
            p.textSize = base * 0.85f
            p.alpha = (alpha * 255f).toInt()
            val fm = p.fontMetrics
            c.drawText(o.glyph, 0f, -(fm.ascent + fm.descent) / 2f, p)
        }
    }

    fun clipTime(p: Project, tMs: Long): LongArray {
        val i = p.clipIndexAt(tMs)
        if (i < 0) return longArrayOf(-1, 0)
        return longArrayOf(i.toLong(), tMs - p.clipStart(i))
    }

    /** Draws the background that surrounds the clip. When [hole] is true the clip area is punched out (export). */
    fun drawBackground(c: Canvas, W: Int, H: Int, p: Project, tMs: Long, hole: Boolean, allowSync: Boolean, onLate: (() -> Unit)?) {
        val ct = clipTime(p, tMs)
        val idx = ct[0].toInt()
        if (idx < 0) {
            c.drawColor(Color.BLACK)
            return
        }
        val clip = p.clips[idx]
        if (clip.fill && hole) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (p.bgMode) {
            "color" -> c.drawColor(p.bgColor)
            "grad" -> {
                paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), p.bgColor, p.bgColor2, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
            }
            else -> {
                val srcT = clip.trimStart + (ct[1] * clip.speed).toLong()
                val path = MediaUtil.pathOf(clip.uri)
                var bmp: Bitmap? = ThumbCache.peek("blur:$path@" + (if (clip.isImage) 0L else (srcT / 1500L) * 1500L))
                if (bmp == null) {
                    if (allowSync) {
                        bmp = ThumbCache.blurredSync(path, clip.isImage, srcT)
                    } else {
                        Bg.run {
                            ThumbCache.blurredSync(path, clip.isImage, srcT)
                            if (onLate != null) Bg.ui { onLate() }
                        }
                    }
                }
                c.drawColor(0xFF111111.toInt())
                if (bmp != null) {
                    val s = Math.max(W.toFloat() / bmp.width, H.toFloat() / bmp.height)
                    val bw = bmp.width * s
                    val bh = bmp.height * s
                    val dst = RectF((W - bw) / 2f, (H - bh) / 2f, (W + bw) / 2f, (H + bh) / 2f)
                    val bp = Paint(Paint.FILTER_BITMAP_FLAG)
                    c.drawBitmap(bmp, null, dst, bp)
                    c.drawColor(0x55000000)
                }
            }
        }
        if (hole) {
            val r = Geo.clipRect(clip, W.toFloat(), H.toFloat())
            c.drawRect(r[0] + 1f, r[1] + 1f, r[0] + r[2] - 1f, r[1] + r[3] - 1f, clearPaint)
        }
    }

    private val curClip = ThreadLocal<Pair<Clip, Long>?>()

    /** Everything that is drawn above the video: clip vignette/grain, overlay effects, text and stickers. */
    fun drawTop(c: Canvas, W: Int, H: Int, p: Project, tMs: Long) {
        val Wf = W.toFloat()
        val Hf = H.toFloat()
        val ct = clipTime(p, tMs)
        var vr = floatArrayOf(0f, 0f, Wf, Hf)
        if (ct[0] >= 0) {
            val clip = p.clips[ct[0].toInt()]
            val r = Geo.clipRect(clip, Wf, Hf)
            val l = Math.max(0f, r[0])
            val t = Math.max(0f, r[1])
            val rr = Math.min(Wf, r[0] + r[2])
            val bb = Math.min(Hf, r[1] + r[3])
            vr = floatArrayOf(l, t, rr - l, bb - t)
            curClip.set(Pair(clip, ct[1]))
            val mk = ClipMask.of(clip)
            if (mk != null) drawMask(c, vr, mk, ct[1])
            val lt = ClipLight.of(clip)
            if (lt != null) drawLight(c, vr, lt, tMs, ct[1], Wf)
            if (clip.motion.isNotEmpty()) Motion.drawPost(c, vr, clip, ct[1], Wf)
            val vg = Fx.vignetteOf(clip)
            if (vg > 0.01f) drawVignette(c, vr, vg)
            val gr = Fx.grainOf(clip)
            if (gr > 0.01f) drawGrain(c, vr, gr, tMs)
        }
        if (ct[0] < 0) curClip.set(null)
        for (f in p.fxs) {
            if (tMs >= f.startMs && tMs < f.endMs()) {
                val d = Fx.fxDef(f.fx)
                if (d.overlay) drawFx(c, f, vr, tMs, Wf)
            }
        }
        Eq.cur.set(p)
        for (o in p.overlays) {
            if (tMs >= o.startMs && tMs < o.endMs()) drawOverlay(c, o, Wf, Hf, tMs)
        }
    }

    fun hasDynamicTop(p: Project, tMs: Long): Boolean {
        for (f in p.fxs) if (tMs >= f.startMs && tMs < f.endMs()) return true
        for (o in p.overlays) {
            if (tMs >= o.startMs && tMs < o.endMs()) {
                if (o.kind == "eq" || o.kind == "comp" || (o.kind == "image" && o.nf > 0) || (o.kind == "text" && (o.tfx == "echo" || o.tfx == "echoh"))) return true
                if (o.animIn != "none" && tMs - o.startMs < o.animInMs) return true
                if (o.animOut != "none" && o.endMs() - tMs < o.animOutMs) return true
            }
        }
        val ct = clipTime(p, tMs)
        if (ct[0] >= 0 && Fx.grainOf(p.clips[ct[0].toInt()]) > 0.01f) return true
        if (ct[0] >= 0 && Motion.hasOverlayPost(p.clips[ct[0].toInt()].motion)) return true
        if (ct[0] >= 0) {
            val lt = ClipLight.of(p.clips[ct[0].toInt()])
            if (lt != null && lt.anim != "none") return true
            val mk = ClipMask.of(p.clips[ct[0].toInt()])
            if (mk != null && mk.anim != "none") return true
        }
        return false
    }

    /** A signature of everything static that is visible at tMs (used to avoid redrawing identical frames). */
    fun staticKey(p: Project, tMs: Long): String {
        val sb = StringBuilder()
        val ct = clipTime(p, tMs)
        sb.append(ct[0])
        if (ct[0] >= 0) sb.append(p.clips[ct[0].toInt()].light).append(p.clips[ct[0].toInt()].mask)
        for (o in p.overlays) if (tMs >= o.startMs && tMs < o.endMs()) sb.append('|').append(o.id).append(o.hashCode())
        return sb.toString()
    }

    private fun drawVignette(c: Canvas, r: FloatArray, amt: Float) {
        val cx = r[0] + r[2] / 2f
        val cy = r[1] + r[3] / 2f
        val rad = Math.hypot((r[2] / 2f).toDouble(), (r[3] / 2f).toDouble()).toFloat()
        val p = Paint()
        p.shader = RadialGradient(cx, cy, rad, intArrayOf(0x00000000, 0x00000000, withAlpha(Color.BLACK, 0.85f * amt)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], p)
    }

    private fun noiseTile(i: Int): Bitmap {
        if (noiseTiles.isEmpty()) {
            for (k in 0 until 4) {
                val s = 128
                val px = IntArray(s * s)
                for (j in px.indices) {
                    val n = hashNoise(j * 7 + k * 100003)
                    px[j] = if (n > 0.5f) Color.argb((n * 120).toInt(), 255, 255, 255) else Color.argb(((1f - n) * 120).toInt(), 0, 0, 0)
                }
                val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                b.setPixels(px, 0, s, 0, 0, s, s)
                noiseTiles.add(b)
            }
        }
        return noiseTiles[Math.abs(i) % noiseTiles.size]
    }

    private fun drawGrain(c: Canvas, r: FloatArray, amt: Float, tMs: Long) {
        val frame = (tMs / 66L).toInt()
        val tile = noiseTile(frame)
        val p = Paint()
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        p.alpha = (clampF(amt, 0f, 1f) * 255f).toInt()
        c.save()
        c.clipRect(r[0], r[1], r[0] + r[2], r[1] + r[3])
        c.translate(hashNoise(frame) * 64f, hashNoise(frame + 5) * 64f)
        c.drawRect(-64f, -64f, r[0] + r[2] + 64f, r[1] + r[3] + 64f, p)
        c.restore()
    }

    private fun star4(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = Path()
        path.moveTo(cx, cy - r)
        path.quadTo(cx + r * 0.1f, cy - r * 0.1f, cx + r, cy)
        path.quadTo(cx + r * 0.1f, cy + r * 0.1f, cx, cy + r)
        path.quadTo(cx - r * 0.1f, cy + r * 0.1f, cx - r, cy)
        path.quadTo(cx - r * 0.1f, cy - r * 0.1f, cx, cy - r)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawButterflies(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, a: Float, W: Float, ccx: Float, ccy: Float, rx: Float, ry: Float) {
        val n = (3 + 5 * a).toInt()
        for (i in 0 until n) {
            val ph = t * (0.25f + 0.2f * hashNoise(i + 3)) + hashNoise(i * 9) * 6.28f
            val bx = ccx + rx * Math.sin(ph.toDouble()).toFloat() * (0.6f + 0.4f * hashNoise(i * 3 + 1))
            val by = ccy + ry * Math.sin((ph * 1.31f + i).toDouble()).toFloat()
            val sz = (0.05f + 0.04f * hashNoise(i * 3 + 2)) * W
            val flap = Math.abs(Math.cos((t * 9f + i * 1.7f).toDouble())).toFloat() * 0.8f + 0.2f
            val ang = 25f * Math.cos((ph * 1.31f + i).toDouble()).toFloat()
            c.save()
            c.translate(x0 + bx * w, y0 + by * h)
            c.rotate(ang)
            p.style = Paint.Style.FILL
            p.setShadowLayer(sz * 0.6f, 0f, 0f, 0xFF29B6F6.toInt())
            for (side in intArrayOf(-1, 1)) {
                c.save(); c.scale(side * flap, 1f)
                p.color = withAlpha(0xFF1E88E5.toInt(), 0.9f)
                c.drawOval(RectF(0f, -sz * 0.9f, sz, 0f), p)
                p.color = withAlpha(0xFF80D8FF.toInt(), 0.9f)
                c.drawOval(RectF(0f, -sz * 0.1f, sz * 0.75f, sz * 0.7f), p)
                c.restore()
            }
            p.clearShadowLayer()
            p.color = withAlpha(0xFF0D47A1.toInt(), 0.95f)
            c.drawOval(RectF(-sz * 0.04f, -sz * 0.5f, sz * 0.04f, sz * 0.4f), p)
            c.restore()
        }
    }

    private fun personFx(c: Canvas, f: FxItem, lk: Seg.Look, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float, p: Paint) {
        if (lk.core == null || lk.glow == null) return
        val col = f.cfg.toIntOrNull() ?: when (f.fx) { "p_outline" -> 0xFFE040FB.toInt(); "p_electric" -> 0xFFB388FF.toInt(); else -> Color.WHITE }
        val dst = RectF(x0, y0, x0 + w, y0 + h)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (f.fx) {
            "p_outline" -> {
                bp.colorFilter = android.graphics.PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
                bp.alpha = (255 * clampF(0.35f + a * 0.65f, 0f, 1f)).toInt()
                c.drawBitmap(lk.glow, null, dst, bp)
                bp.colorFilter = null
                bp.alpha = 255
                c.drawBitmap(lk.core, null, dst, bp)
            }
            "p_electric" -> {
                val k = (t * 16f).toInt()
                bp.colorFilter = android.graphics.PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
                bp.alpha = (255 * clampF(0.35f + a * 0.65f, 0f, 1f)).toInt()
                c.drawBitmap(lk.glow, null, dst, bp)
                for (j in 0 until 3) {
                    val ox = (hashNoise(k * 7 + j * 13) - 0.5f) * W * 0.012f
                    val oy = (hashNoise(k * 11 + j * 17) - 0.5f) * W * 0.012f
                    c.save()
                    c.translate(ox, oy)
                    bp.colorFilter = if (j == 0) null else android.graphics.PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
                    bp.alpha = if (j == 0) 255 else 200
                    c.drawBitmap(lk.core, null, dst, bp)
                    c.restore()
                }
            }
            "p_sparkle" -> {
                val n = (20 + 60 * a).toInt()
                val m = lk.pts.size / 2
                if (m > 0) for (i in 0 until n) {
                    val pi = (hashNoise(i * 5 + 1) * m).toInt().coerceIn(0, m - 1)
                    val px = lk.pts[pi * 2]
                    val py = lk.pts[pi * 2 + 1]
                    val tw = Math.max(0f, Math.sin((t * (2f + 3f * hashNoise(i + 40)) + i * 1.7f).toDouble()).toFloat())
                    val rad = 0.02f * W * (0.5f + hashNoise(i * 5 + 3)) * (0.4f + tw)
                    p.style = Paint.Style.FILL
                    p.color = withAlpha(col, 0.3f + 0.7f * tw)
                    p.setShadowLayer(rad, 0f, 0f, col)
                    star4(c, x0 + px * w, y0 + py * h, rad, p)
                    p.clearShadowLayer()
                }
            }
            "p_butterfly" -> {
                val b = lk.box
                val cx = (b[0] + b[2]) / 2f
                val cy = b[1] + (b[3] - b[1]) * 0.3f
                drawButterflies(c, p, x0, y0, w, h, t, a, W, cx, cy, Math.max(0.12f, (b[2] - b[0]) * 0.75f), Math.max(0.12f, (b[3] - b[1]) * 0.5f))
            }
        }
    }

    private fun drawFx(c: Canvas, f: FxItem, r: FloatArray, tMs: Long, W: Float) {
        val t = (tMs - f.startMs) / 1000f
        val a = f.amt
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.clipRect(r[0], r[1], r[0] + r[2], r[1] + r[3])
        val x0 = r[0]
        val y0 = r[1]
        val w = r[2]
        val h = r[3]
        when (f.fx) {
            "vignette" -> drawVignette(c, r, a)
            "grain" -> drawGrain(c, r, a, tMs)
            "light" -> if (f.cfg.isNotEmpty()) { c.restore(); drawLight(c, r, ClipLight.cached(f.cfg), tMs, (tMs - f.startMs), W); c.save() }
            "mask" -> if (f.cfg.isNotEmpty()) { c.restore(); drawMask(c, r, ClipMask.cached(f.cfg), (tMs - f.startMs)); c.save() }
            "papertear" -> {
                // two torn paper edges (left and right); outside them the picture is veiled in grey
                val wob = 0.012f * Math.sin((t * 0.8f).toDouble()).toFloat()
                val edges = floatArrayOf(0.17f + wob, 0.83f - wob)
                for ((ei, ex) in edges.withIndex()) {
                    val path = Path()
                    val steps = 40
                    val pts = FloatArray(steps + 1)
                    for (k in 0..steps) pts[k] = (hashNoise(k * 7 + ei * 101) - 0.5f) * 0.035f * w + (k - steps / 2f) * (if (ei == 0) 0.0045f else -0.0045f) * w
                    val sw = 0.03f * w
                    // outside veil
                    val veil = Path()
                    if (ei == 0) veil.moveTo(x0, y0) else veil.moveTo(x0 + w, y0)
                    for (k in 0..steps) veil.lineTo(x0 + ex * w + pts[k], y0 + h * k / steps)
                    if (ei == 0) { veil.lineTo(x0, y0 + h) } else { veil.lineTo(x0 + w, y0 + h) }
                    veil.close()
                    p.style = Paint.Style.FILL
                    p.color = withAlpha(0xFF808080.toInt(), 0.5f * a)
                    c.drawPath(veil, p)
                    // paper strip
                    path.moveTo(x0 + ex * w + pts[0] - sw, y0)
                    for (k in 0..steps) path.lineTo(x0 + ex * w + pts[k] - sw * (0.6f + hashNoise(k + ei * 55)), y0 + h * k / steps)
                    for (k in steps downTo 0) path.lineTo(x0 + ex * w + pts[k] + sw * (0.6f + hashNoise(k + 200 + ei * 55)), y0 + h * k / steps)
                    path.close()
                    p.color = withAlpha(0xFFF5F0E6.toInt(), 0.95f)
                    p.setShadowLayer(W * 0.012f, 0f, 0f, withAlpha(Color.BLACK, 0.5f))
                    c.drawPath(path, p)
                    p.clearShadowLayer()
                }
            }
            "butterflies" -> drawButterflies(c, p, x0, y0, w, h, t, a, W, 0.5f, 0.5f, 0.38f, 0.38f)
            "p_outline", "p_electric", "p_sparkle", "p_butterfly" -> {
                val cc = curClip.get()
                if (cc != null) {
                    val lk = Seg.lookAt(cc.first, cc.second)
                    if (lk != null) personFx(c, f, lk, x0, y0, w, h, t, W, a, p)
                }
            }
            "sparkhalo" -> {
                // arch of twinkling sparkles over the head and down both sides (fixed to the frame, not tracking a person)
                val n = (24 + 36 * a).toInt()
                for (i in 0 until n) {
                    val ang = Math.PI.toFloat() * (1.0f + hashNoise(i * 5 + 1)) + 0.12f * Math.sin((t * 0.6f + i).toDouble()).toFloat()
                    val rr = 0.27f + 0.09f * hashNoise(i * 5 + 2)
                    val x = 0.5f + Math.cos(ang.toDouble()).toFloat() * rr * 1.15f
                    val y = 0.40f + Math.sin(ang.toDouble()).toFloat() * rr * 1.1f + 0.10f * hashNoise(i + 70) * (if (hashNoise(i + 3) > 0.7f) 2f else 0f)
                    val tw = Math.max(0f, Math.sin((t * (2f + 3f * hashNoise(i + 40)) + i * 1.7f).toDouble()).toFloat())
                    val rad = 0.022f * W * (0.5f + hashNoise(i * 5 + 3)) * (0.4f + tw)
                    p.color = withAlpha(Color.WHITE, 0.3f + 0.7f * tw)
                    p.setShadowLayer(rad, 0f, 0f, Color.WHITE)
                    star4(c, x0 + x * w, y0 + y * h, rad, p)
                    p.clearShadowLayer()
                }
            }
            "elecframe" -> {
                // glowing lightning-like outline that jitters along the frame edges
                val k = (t * 14f).toInt()
                val path = Path()
                val m = 0.035f * Math.min(w, h)
                val segs = 36
                fun pt(side: Int, i: Int): FloatArray {
                    val u = i.toFloat() / segs
                    val j = (hashNoise(k * 977 + side * 131 + i * 7) - 0.5f) * 2f * m
                    return when (side) {
                        0 -> floatArrayOf(x0 + m + u * (w - 2 * m), y0 + m + j)
                        1 -> floatArrayOf(x0 + w - m + j, y0 + m + u * (h - 2 * m))
                        2 -> floatArrayOf(x0 + w - m - u * (w - 2 * m), y0 + h - m + j)
                        else -> floatArrayOf(x0 + m + j, y0 + h - m - u * (h - 2 * m))
                    }
                }
                for (side in 0..3) for (i in 0..segs) { val q = pt(side, i); if (side == 0 && i == 0) path.moveTo(q[0], q[1]) else path.lineTo(q[0], q[1]) }
                path.close()
                p.style = Paint.Style.STROKE
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = W * 0.012f * (0.6f + a)
                p.color = withAlpha(0xFFE040FB.toInt(), 0.9f)
                p.setShadowLayer(W * 0.03f, 0f, 0f, 0xFFE040FB.toInt())
                c.drawPath(path, p)
                p.clearShadowLayer()
                p.strokeWidth = W * 0.004f
                p.color = Color.WHITE
                c.drawPath(path, p)
            }
            "vhs" -> {
                p.color = withAlpha(Color.BLACK, 0.16f * (0.4f + a))
                var y = y0
                while (y < y0 + h) {
                    c.drawRect(x0, y, x0 + w, y + 1.5f, p)
                    y += 4f
                }
                val band = ((t * 0.25f) % 1.4f - 0.2f) * h + y0
                val gp = Paint()
                gp.shader = LinearGradient(0f, band, 0f, band + h * 0.12f, intArrayOf(0x00FFFFFF, withAlpha(Color.WHITE, 0.14f * a), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(x0, band, x0 + w, band + h * 0.12f, gp)
                val k = (t * 12f).toInt()
                if (hashNoise(k) > 0.6f) {
                    p.color = withAlpha(Color.WHITE, 0.35f * a)
                    val yy = y0 + hashNoise(k + 1) * h
                    c.drawRect(x0, yy, x0 + w * (0.3f + hashNoise(k + 2) * 0.7f), yy + 2f + hashNoise(k + 3) * 4f, p)
                }
                p.color = withAlpha(0xFFFF0050.toInt(), 0.06f * a)
                c.drawRect(x0, y0, x0 + w, y0 + h, p)
            }
            "snow" -> {
                val n = (30 + 90 * a).toInt()
                for (i in 0 until n) {
                    val sp = 0.06f + 0.14f * hashNoise(i * 3 + 1)
                    val y = (hashNoise(i * 7 + 5) + t * sp) % 1f
                    val x = hashNoise(i * 3) + 0.03f * Math.sin((t * 1.3f + i).toDouble()).toFloat()
                    val rad = (0.003f + 0.007f * hashNoise(i * 3 + 2)) * W
                    p.color = withAlpha(Color.WHITE, 0.5f + 0.45f * hashNoise(i + 77))
                    c.drawCircle(x0 + x * w, y0 + y * h, rad, p)
                }
            }
            "rain" -> {
                p.strokeWidth = Math.max(1.5f, W * 0.0018f)
                val n = (40 + 100 * a).toInt()
                for (i in 0 until n) {
                    val sp = 1.1f + 0.8f * hashNoise(i * 3 + 1)
                    val y = (hashNoise(i * 7 + 5) + t * sp) % 1f
                    val x = hashNoise(i * 3) + 0.0f
                    p.color = withAlpha(0xFFBBDEFB.toInt(), 0.35f + 0.25f * hashNoise(i + 9))
                    val len = h * 0.05f
                    c.drawLine(x0 + x * w, y0 + y * h, x0 + x * w - len * 0.2f, y0 + y * h + len, p)
                }
            }
            "hearts" -> {
                val n = (6 + 14 * a).toInt()
                val hp = Shapes.path("heart")
                for (i in 0 until n) {
                    val sp = 0.10f + 0.12f * hashNoise(i * 3 + 1)
                    val ph = (hashNoise(i * 7 + 5) + t * sp) % 1f
                    val y = 1.1f - ph * 1.2f
                    val x = hashNoise(i * 3) + 0.04f * Math.sin((t * 1.5f + i).toDouble()).toFloat()
                    val sz = (0.03f + 0.06f * hashNoise(i * 3 + 2)) * W
                    p.color = withAlpha(if (i % 3 == 0) 0xFFFF4D6D.toInt() else if (i % 3 == 1) 0xFFFF8FAB.toInt() else 0xFFFFC2D1.toInt(), 0.85f * Math.min(1f, y * 4f))
                    c.save()
                    c.translate(x0 + x * w, y0 + y * h)
                    c.rotate(15f * Math.sin((t * 2f + i).toDouble()).toFloat())
                    c.scale(sz * 2f, sz * 2f)
                    c.drawPath(hp, p)
                    c.restore()
                }
            }
            "sparkle", "stars" -> {
                val n = (10 + 22 * a).toInt()
                for (i in 0 until n) {
                    val x = hashNoise(i * 5 + 1)
                    val y = hashNoise(i * 5 + 2)
                    val tw = Math.max(0f, Math.sin((t * (2f + 3f * hashNoise(i + 40)) + i * 1.7f).toDouble()).toFloat())
                    val rad = (if (f.fx == "stars") 0.025f else 0.014f) * W * (0.5f + hashNoise(i * 5 + 3)) * (0.4f + tw)
                    p.color = withAlpha(if (f.fx == "stars") 0xFFFFD740.toInt() else Color.WHITE, tw)
                    star4(c, x0 + x * w, y0 + y * h, rad, p)
                }
            }
            "confetti" -> {
                val cols = intArrayOf(0xFFFF5252.toInt(), 0xFFFFD740.toInt(), 0xFF69F0AE.toInt(), 0xFF40C4FF.toInt(), 0xFFE040FB.toInt(), 0xFFFF9100.toInt())
                val n = (25 + 60 * a).toInt()
                for (i in 0 until n) {
                    val sp = 0.18f + 0.3f * hashNoise(i * 3 + 1)
                    val y = ((hashNoise(i * 7 + 5) + t * sp) % 1.1f) - 0.05f
                    val x = hashNoise(i * 3) + 0.05f * Math.sin((t * 2f + i).toDouble()).toFloat()
                    p.color = cols[i % cols.size]
                    c.save()
                    c.translate(x0 + x * w, y0 + y * h)
                    c.rotate(t * 200f * hashNoise(i + 3) + i * 40f)
                    val s = W * 0.012f
                    c.drawRect(-s, -s * 0.5f, s, s * 0.5f, p)
                    c.restore()
                }
            }
            "bokeh" -> {
                val cols = intArrayOf(0xFFFFE0B2.toInt(), 0xFFFFCC80.toInt(), 0xFFF8BBD0.toInt(), 0xFFFFFFFF.toInt())
                val n = (6 + 12 * a).toInt()
                for (i in 0 until n) {
                    val x = (hashNoise(i * 4) + t * 0.02f * (hashNoise(i + 1) - 0.5f)) % 1f
                    val y = (hashNoise(i * 4 + 1) + t * 0.03f * (hashNoise(i + 2) - 0.2f)) % 1f
                    val rad = (0.03f + 0.08f * hashNoise(i * 4 + 2)) * W
                    p.color = withAlpha(cols[i % cols.size], 0.12f + 0.12f * hashNoise(i + 90))
                    c.drawCircle(x0 + Math.abs(x) * w, y0 + Math.abs(y) * h, rad, p)
                }
            }
            "leak" -> {
                val pos = ((t * 0.18f) % 1.6f - 0.3f) * w + x0
                val gp = Paint()
                gp.shader = LinearGradient(pos - w * 0.35f, 0f, pos + w * 0.35f, 0f, intArrayOf(0x00FF8A50, withAlpha(0xFFFF8A50.toInt(), 0.45f * a), withAlpha(0xFFFFD27F.toInt(), 0.35f * a), 0x00FFFFFF), floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(x0, y0, x0 + w, y0 + h, gp)
            }
            "cinema" -> {
                p.color = Color.BLACK
                val bar = h * 0.125f
                c.drawRect(x0, y0, x0 + w, y0 + bar, p)
                c.drawRect(x0, y0 + h - bar, x0 + w, y0 + h, p)
            }
            "frame" -> {
                p.color = 0xFFFAFAFA.toInt()
                val th = Math.min(w, h) * 0.04f
                c.drawRect(x0, y0, x0 + w, y0 + th, p)
                c.drawRect(x0, y0 + h - th * 3f, x0 + w, y0 + h, p)
                c.drawRect(x0, y0, x0 + th, y0 + h, p)
                c.drawRect(x0 + w - th, y0, x0 + w, y0 + h, p)
            }
            "dust" -> {
                val frame = (t * 12f).toInt()
                p.strokeWidth = Math.max(1f, W * 0.0015f)
                for (i in 0 until 26) {
                    val x = hashNoise(frame * 31 + i * 2)
                    val y = hashNoise(frame * 31 + i * 2 + 1)
                    p.color = withAlpha(0xFFFFF8E1.toInt(), 0.35f + 0.4f * hashNoise(i + frame))
                    val len = W * 0.004f * (1f + 3f * hashNoise(i + 400))
                    c.drawLine(x0 + x * w, y0 + y * h, x0 + x * w + len, y0 + y * h + len * 0.6f, p)
                }
                if (hashNoise(frame + 900) > 0.8f) {
                    p.color = withAlpha(Color.WHITE, 0.25f)
                    val x = x0 + hashNoise(frame + 901) * w
                    c.drawLine(x, y0, x, y0 + h, p)
                }
            }
        }
        c.restore()
    }

    /** True when something other than the plain video would be drawn at tMs. */
    fun isEmptyAt(p: Project, tMs: Long): Boolean {
        for (o in p.overlays) if (tMs >= o.startMs && tMs < o.endMs()) return false
        for (f in p.fxs) if (tMs >= f.startMs && tMs < f.endMs() && Fx.fxDef(f.fx).overlay) return false
        val ct = clipTime(p, tMs)
        if (ct[0] >= 0) {
            val c = p.clips[ct[0].toInt()]
            if (Fx.vignetteOf(c) > 0.01f || Fx.grainOf(c) > 0.01f || ClipLight.of(c) != null || ClipMask.of(c) != null) return false
            if (c.motion.isNotEmpty() && Motion.hasOverlayPost(c.motion)) return false
        }
        return true
    }
}
