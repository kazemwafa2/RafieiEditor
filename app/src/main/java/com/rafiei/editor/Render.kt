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

    /** Real fonts bundled in assets/fonts (downloaded at build time). */
    private val FA_NAMES = linkedMapOf(
        "Vazirmatn" to ("وزیرمتن (ساده و مدرن)" to "Vazirmatn"),
        "NotoNastaliqUrdu" to ("نستعلیق" to "Nastaliq"),
        "Amiri" to ("امیری (نسخ کلاسیک)" to "Amiri Naskh"),
        "AmiriBold" to ("امیری ضخیم" to "Amiri Bold"),
        "ScheherazadeNew" to ("شهرزاد (نسخ)" to "Scheherazade"),
        "NotoNaskhArabic" to ("نسخ" to "Naskh"),
        "ArefRuqaa" to ("خوش‌نویسی رقعه" to "Ruqaa calligraphy"),
        "ArefRuqaaBold" to ("رقعه ضخیم" to "Ruqaa bold"),
        "Katibeh" to ("خوش‌نویسی کاتبه" to "Katibeh calligraphy"),
        "Lalezar" to ("لاله‌زار (تیتر تزئینی)" to "Lalezar display"),
        "Rakkas" to ("رکاس (تیتر ضخیم)" to "Rakkas"),
        "ReemKufi" to ("کوفی" to "Kufi"),
        "Harmattan" to ("هرماتان" to "Harmattan"),
        "MarkaziText" to ("مرکزی (سریف)" to "Markazi serif"),
        "Mirza" to ("میرزا" to "Mirza"),
        "Tajawal" to ("تجوال" to "Tajawal"),
        "TajawalBold" to ("تجوال ضخیم" to "Tajawal bold"),
        "Lateef" to ("لطیف" to "Lateef"),
        "ElMessiri" to ("المسیری" to "El Messiri"),
        "Jomhuria" to ("جمهوریه" to "Jomhuria")
    )

    fun assetFonts(c: Context): List<String> {
        return try {
            val have = (c.assets.list("fonts") ?: emptyArray()).map { it.substringBeforeLast('.') }.toSet()
            val out = ArrayList<String>()
            for (k in FA_NAMES.keys) if (k in have) out.add("asset:$k")
            for (k in have.sorted()) if (!FA_NAMES.containsKey(k)) out.add("asset:$k")
            out
        } catch (e: Throwable) { emptyList() }
    }

    /** All fonts the user can pick: real bundled fonts first, then the system ones, then imported. */
    fun all(c: Context): List<String> = assetFonts(c) + BUILTIN + imported(c)

    /** Good default for Persian text: Vazirmatn when it is bundled. */
    fun defaultFa(c: Context): String = if (assetFonts(c).contains("asset:Vazirmatn")) "asset:Vazirmatn" else "sans"

    fun imported(c: Context): List<String> {
        val out = ArrayList<String>()
        val fs = fontsDir(c).listFiles() ?: return out
        for (f in fs) if (f.name.lowercase().endsWith(".ttf") || f.name.lowercase().endsWith(".otf")) out.add("file:" + f.name)
        out.sort()
        return out
    }

    fun displayName(n: String): String {
        if (n.startsWith("file:")) return n.substring(5).substringBeforeLast('.')
        if (n.startsWith("asset:")) { val k = n.substring(6); val p = FA_NAMES[k]; return if (p != null) L(p.first, p.second) else k }
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
            if (name.startsWith("asset:")) {
                tf = Typeface.create(Typeface.createFromAsset(App.ctx.assets, "fonts/" + name.substring(6) + ".ttf"), style)
            } else if (name.startsWith("file:")) {
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
        if (o.kind == "text" && o.tfx == "ring") { drawRingText(c, o, W, H, tMs, alpha); return }
        if (o.kind == "eq" && Eq.tplOf(o).behind) { drawEqBehind(c, o, W, H, tMs, alpha); return }
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

    /** Scene design drawn over the whole clip but cut out where the person is, so the user's film stays in front. */
    /** Background removal: everything except the person is covered by a colour, gradient or picture. */
    private fun drawCut(c: Canvas, vr: FloatArray, clip: Clip, relMs: Long, W: Float, H: Float) {
        val j = try { org.json.JSONObject(clip.cut) } catch (e: Exception) { return }
        val lk = Seg.lookAt(clip, relMs, segSync())
        val mask = lk?.fill ?: return
        val dst = RectF(vr[0], vr[1], vr[0] + vr[2], vr[1] + vr[3])
        val sc = c.saveLayer(dst, null)
        c.clipRect(dst)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val mode = j.optString("m", "color")
        val c1 = j.optInt("c1", 0xFF00B140.toInt())
        val c2 = j.optInt("c2", 0xFF0A1030.toInt())
        var drew = false
        if (mode == "image") {
            val b = BmpCache.get(j.optString("uri", ""), 1280)
            if (b != null) {
                val s = Math.max(dst.width() / b.width, dst.height() / b.height)
                val w = b.width * s; val h = b.height * s
                c.drawBitmap(b, null, RectF(dst.centerX() - w / 2f, dst.centerY() - h / 2f, dst.centerX() + w / 2f, dst.centerY() + h / 2f), p)
                drew = true
            }
        }
        if (!drew) {
            if (mode == "grad") p.shader = android.graphics.LinearGradient(dst.left, dst.top, dst.left, dst.bottom, c1, c2, android.graphics.Shader.TileMode.CLAMP)
            else p.color = c1
            c.drawRect(dst, p)
            p.shader = null
        }
        val m = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        m.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawBitmap(mask, null, dst, m)
        c.restoreToCount(sc)
    }

    private fun drawEqBehind(c: Canvas, o: Overlay, W: Float, H: Float, tMs: Long, alpha: Float) {
        val rect = curRect.get() ?: return
        val lk = curClip.get()?.let { Seg.lookAt(it.first, it.second, segSync()) }
        val mask = lk?.fill ?: return
        val s = Math.max(W / EQ_W, H / EQ_H)
        val full = RectF(0f, 0f, W, H)
        val dst = RectF(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3])
        val sc = c.saveLayer(full, null)
        c.save()
        c.translate(W / 2f, H / 2f)
        Eq.draw(c, o, EQ_W * s, tMs, alpha)
        c.restore()
        val m = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        m.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawBitmap(mask, null, dst, m)
        c.restoreToCount(sc)
    }

    private fun drawText(c: Canvas, o: Overlay, W: Float, a: AnimState, alpha: Float, tMs: Long = 0L) {
        var txt = o.text
        val ws = if (o.glyph == "sub" && o.words.isNotEmpty() && o.smode != "line") SubWords.parse(o.words) else emptyList()
        var curW = -1
        var wordAge = 0L
        if (ws.isNotEmpty()) {
            val rel = tMs - o.startMs
            curW = SubWords.current(ws, rel)
            if (curW >= 0) wordAge = rel - ws[curW].s
            if (o.smode == "word") {
                if (curW < 0) return
                txt = ws[curW].w
                // pop-in of every new word
                val k = clampF(wordAge / 140f, 0f, 1f)
                val sc = 0.7f + 0.3f * k + 0.12f * Math.sin(k * Math.PI).toFloat()
                c.scale(sc, sc)
            }
        }
        if (a.reveal < 0.999f) {
            val n = Math.round(txt.length * clampF(a.reveal, 0f, 1f))
            txt = txt.substring(0, clampI(n, 0, txt.length))
        }
        val e = textEntry(o, W, txt) ?: return
        val full = if (o.smode == "word" && ws.isNotEmpty()) e else (textEntry(o, W, if (o.text.isEmpty()) " " else o.text) ?: e)
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
        val tt = (tMs - o.startMs) / 1000f
        if (fx == "glitch") {
            val tick = (tt * 14f).toInt()
            val j = (((tick * 7919) % 7) - 3) * px * 0.012f
            c.save(); c.translate(j + px * 0.03f, 0f)
            e.fillPaint.color = withAlpha(0xFF00E5FF.toInt(), alpha * 0.8f); e.fill.draw(c); c.restore()
            c.save(); c.translate(-j - px * 0.03f, 0f)
            e.fillPaint.color = withAlpha(0xFFFF2D95.toInt(), alpha * 0.8f); e.fill.draw(c); c.restore()
        }
        if (o.outlineW > 0.01f) {
            e.strokePaint.color = withAlpha(o.outlineColor, alpha)
            e.stroke.draw(c)
        }
        if (fx == "rainbow") {
            val cols = intArrayOf(0xFFFF3B3B.toInt(), 0xFFFFD93D.toInt(), 0xFF3BFF7A.toInt(), 0xFF3BC8FF.toInt(), 0xFFB45BFF.toInt(), 0xFFFF3B3B.toInt())
            val sh = LinearGradient(0f, 0f, Math.max(10f, e.w), 0f, cols, null, Shader.TileMode.REPEAT)
            val m = android.graphics.Matrix(); m.setTranslate(tt * e.w * 0.6f, 0f); sh.setLocalMatrix(m)
            e.fillPaint.shader = sh
            e.fillPaint.color = withAlpha(Color.WHITE, alpha)
            e.fill.draw(c)
            e.fillPaint.shader = null
        } else {
            e.fillPaint.color = withAlpha(o.color, alpha)
            e.fill.draw(c)
        }
        if (fx == "sweep") {
            val x0 = ((tt * 0.7f) % 1.6f - 0.3f) * e.w
            val sh = LinearGradient(x0, 0f, x0 + e.w * 0.25f, e.h * 0.2f, intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            e.fillPaint.shader = sh
            e.fillPaint.color = withAlpha(Color.WHITE, alpha)
            e.fill.draw(c)
            e.fillPaint.shader = null
        }
        if (ws.isNotEmpty() && curW >= 0 && (o.smode == "kar" || o.smode == "hl") && SubWords.join(ws) == txt) {
            var st = 0
            for (i in 0 until curW) st += ws[i].w.length + 1
            val en = st + ws[curW].w.length
            val path = Path()
            e.fill.getSelectionPath(if (o.smode == "kar") 0 else st, en, path)
            c.save()
            c.clipPath(path)
            e.fillPaint.color = withAlpha(o.hiColor, alpha)
            e.fill.draw(c)
            c.restore()
        }
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

    private val curClip = object : ThreadLocal<Pair<Clip, Long>?>() {
        override fun initialValue(): Pair<Clip, Long>? = null
    }

    /** True while the final render (video export overlay pass or photo render) draws on this thread.
     *  On that path a missing person mask is recomputed synchronously instead of silently skipping the design. */
    val exportRender = ThreadLocal<Boolean>()
    private fun segSync(): Boolean = exportRender.get() == true

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
            curRect.set(vr)
            if (clip.cut.isNotEmpty()) drawCut(c, vr, clip, ct[1], Wf, Hf)
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
        if (ct[0] < 0) { curClip.set(null); curRect.set(null) }
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
                if (o.kind == "eq" || o.kind == "comp" || (o.kind == "image" && o.nf > 0) || (o.kind == "text" && (o.tfx == "echo" || o.tfx == "echoh" || o.tfx == "ring" || o.tfx == "glitch" || o.tfx == "rainbow" || o.tfx == "sweep" || (o.words.isNotEmpty() && o.smode != "line")))) return true
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
            if (p.clips[ct[0].toInt()].cut.isNotEmpty()) return true
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
            "p_flame", "p_bluefire", "p_colorflame", "p_blaze" -> {
                flames(c, p, f.fx, lk, x0, y0, w, h, t, W, a, f.cfg.toIntOrNull())
            }
            "p_spiral" -> neonSpiral(c, p, lk, x0, y0, w, h, t, W, a, f.cfg.toIntOrNull() ?: 0xFF18E6FF.toInt())
            "p_butterfly" -> {
                val b = lk.box
                val cx = (b[0] + b[2]) / 2f
                val cy = b[1] + (b[3] - b[1]) * 0.3f
                drawButterflies(c, p, x0, y0, w, h, t, a, W, cx, cy, Math.max(0.12f, (b[2] - b[0]) * 0.75f), Math.max(0.12f, (b[3] - b[1]) * 0.5f))
            }
        }
    }

    private val curRect = object : ThreadLocal<FloatArray?>() {
        override fun initialValue(): FloatArray? = null
    }

    private fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, clampF(s, 0f, 1f), clampF(v, 0f, 1f)))

    private fun flameColor(kind: String, u: Float, i: Int, t: Float, custom: Int?): Int {
        if (custom != null) {
            val k = (1f - u) * 0.6f
            return Color.rgb((Color.red(custom) + (255 - Color.red(custom)) * k).toInt(), (Color.green(custom) + (255 - Color.green(custom)) * k).toInt(), (Color.blue(custom) + (255 - Color.blue(custom)) * k).toInt())
        }
        return when (kind) {
            "p_bluefire" -> hsv(190f + 50f * u, 0.25f + 0.75f * u, 1f)
            "p_colorflame" -> hsv(t * 90f + i * 7f + u * 80f, 0.85f, 1f)
            "p_blaze" -> hsv(48f - 40f * u, 0.4f + 0.6f * u, 1f)
            else -> hsv(58f - 58f * u, 0.45f + 0.55f * u, 1f)
        }
    }

    /** Fire / blue fire / rainbow fire licking up from the edge of the person; "blaze" bursts outward. */
    private fun flames(c: Canvas, p: Paint, kind: String, lk: Seg.Look, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float, custom: Int?) {
        val m = lk.pts.size / 2
        if (m == 0) return
        val n = (70 + 150 * a).toInt()
        val b = lk.box
        val bcx = x0 + (b[0] + b[2]) / 2f * w
        val bcy = y0 + (b[1] + b[3]) / 2f * h
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val sp = 0.6f + 0.9f * hashNoise(i * 3 + 1)
            val u = (t * sp * 0.9f + hashNoise(i * 7 + 2)) % 1f
            val pi = (hashNoise(i * 5 + 4) * m).toInt().coerceIn(0, m - 1)
            var px = x0 + lk.pts[pi * 2] * w
            var py = y0 + lk.pts[pi * 2 + 1] * h
            if (kind == "p_blaze") {
                val dx = px - bcx
                val dy = py - bcy
                val len = Math.max(1f, Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat())
                px += dx / len * u * 0.16f * w
                py += dy / len * u * 0.16f * w
            } else {
                px += Math.sin((t * 3f + i).toDouble()).toFloat() * 0.014f * w * u
                py -= u * 0.17f * h * (0.5f + hashNoise(i * 11 + 5))
            }
            val r = Math.max(1f, W * 0.022f * (1f - u * 0.8f) * (0.6f + hashNoise(i * 13 + 6)))
            val col = flameColor(kind, u, i, t, custom)
            val al = (1f - u) * (0.45f + 0.5f * a)
            p.shader = RadialGradient(px, py, r, intArrayOf(withAlpha(col, al), withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(px, py, r, p)
        }
        p.shader = null
    }

    private fun neonSpiral(c: Canvas, p: Paint, lk: Seg.Look, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float, col: Int) {
        val fill = lk.fill ?: return
        val b = lk.box
        val bx = x0 + b[0] * w
        val by = y0 + b[1] * h
        val bw = (b[2] - b[0]) * w
        val bh = (b[3] - b[1]) * h
        val dst = RectF(x0, y0, x0 + w, y0 + h)
        val sc = c.saveLayer(dst, null)
        p.style = Paint.Style.STROKE
        p.strokeWidth = Math.max(2f, W * 0.009f)
        p.strokeCap = Paint.Cap.ROUND
        val bands = 8
        val steps = 28
        for (k in 0 until bands) {
            val yb = by + bh * (((k + t * 0.5f) % bands) / bands)
            var lx = bx
            var ly = yb
            for (s in 1..steps) {
                val x = bx + bw * s / steps
                val ph = s.toFloat() / steps * 6.2832f * 1.1f + t * 3f
                val y = yb + Math.sin(ph.toDouble()).toFloat() * bh * 0.03f
                val front = Math.cos(ph.toDouble()).toFloat() > 0f
                p.color = withAlpha(col, if (front) 1f else 0.3f)
                p.setShadowLayer(W * 0.02f, 0f, 0f, col)
                c.drawLine(lx, ly, x, y, p)
                lx = x; ly = y
            }
        }
        p.clearShadowLayer()
        val m = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        m.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawBitmap(fill, null, dst, m)
        c.restoreToCount(sc)
        p.style = Paint.Style.FILL
        if (lk.glow != null) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            bp.colorFilter = android.graphics.PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
            bp.alpha = (255 * clampF(0.3f + a * 0.5f, 0f, 1f)).toInt()
            c.drawBitmap(lk.glow, null, dst, bp)
        }
    }

    /** Text wrapped around the person like a ring: the far half passes behind the body, the near half in front. */
    private fun drawRingText(c: Canvas, o: Overlay, W: Float, H: Float, tMs: Long, alpha: Float) {
        val txt0 = o.text.trim()
        if (txt0.isEmpty()) return
        val txt = txt0.uppercase()
        val px = Math.max(8f, o.size * W)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = Fonts.get(o.font, true, o.italic)
        tp.textSize = px
        val adv = FloatArray(txt.length) { Math.max(px * 0.2f, tp.measureText(txt.substring(it, it + 1))) }
        val tw = adv.sum() + px * 0.8f // text + gap
        val R = W * 0.30f * o.scale
        val ry = R * 0.26f
        val circ = 6.2832f * R
        val k = Math.max(1, Math.floor((circ / tw).toDouble()).toInt())
        val unit = circ / k / tw
        val t = (tMs - o.startMs) / 1000f
        val phase = t * 0.9f
        val cx = o.cx * W
        val cy = o.cy * H
        val lk = curClip.get()?.let { Seg.lookAt(it.first, it.second, segSync()) }
        val rect = curRect.get() ?: floatArrayOf(0f, 0f, W, H)
        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.typeface = tp.typeface; sp.textSize = px
        sp.style = Paint.Style.STROKE
        sp.strokeJoin = Paint.Join.ROUND
        sp.strokeWidth = Math.max(2f, px * 0.12f)
        sp.color = withAlpha(if (o.outlineW > 0.01f) o.outlineColor else 0xFF808080.toInt(), alpha)
        fun glyphs(front: Boolean) {
            var pos = 0f
            for (rep in 0 until k) {
                pos = rep * tw * unit
                for (ci in txt.indices) {
                    val ang = (pos + adv[ci] * unit / 2f) / R + phase
                    pos += adv[ci] * unit
                    val z = Math.cos(ang.toDouble()).toFloat()
                    if ((z > 0f) != front) continue
                    val sx = z
                    if (Math.abs(sx) < 0.07f) continue
                    val x = cx + R * Math.sin(ang.toDouble()).toFloat()
                    val y = cy + ry * z
                    val dep = 0.8f + 0.2f * z
                    c.save()
                    c.translate(x, y)
                    c.rotate(clampF(Math.toDegrees(Math.atan2((-ry * Math.sin(ang.toDouble())), (R * Math.max(0.3f, Math.abs(z))).toDouble())).toFloat(), -25f, 25f))
                    c.scale(sx * dep, dep)
                    val ch = txt.substring(ci, ci + 1)
                    val yy = -(tp.ascent() + tp.descent()) / 2f
                    c.drawText(ch, -adv[ci] / 2f, yy, sp)
                    tp.color = withAlpha(o.color, alpha * (if (front) 1f else 0.8f))
                    c.drawText(ch, -adv[ci] / 2f, yy, tp)
                    c.restore()
                }
            }
        }
        if (lk?.fill != null) {
            val bounds = RectF(cx - R - px * 2, cy - ry - px * 2, cx + R + px * 2, cy + ry + px * 2)
            val sc = c.saveLayer(bounds, null)
            glyphs(false)
            val m = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            m.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            c.drawBitmap(lk.fill, null, RectF(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]), m)
            c.restoreToCount(sc)
        } else glyphs(false)
        glyphs(true)
    }

    private fun drawClubLights(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, a: Float) {
        val n = 6
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val ang = Math.sin((t * (0.7f + 0.23f * i) + i * 1.3f).toDouble()).toFloat() * 0.55f
            val ox = x0 + w * (i + 0.5f) / n
            val len = h * 1.1f
            val spread = 0.075f
            val ax = ox + Math.sin((ang - spread).toDouble()).toFloat() * len
            val ay = y0 + Math.cos((ang - spread).toDouble()).toFloat() * len
            val bx = ox + Math.sin((ang + spread).toDouble()).toFloat() * len
            val by = y0 + Math.cos((ang + spread).toDouble()).toFloat() * len
            val col = hsv(i * 60f + t * 50f, 0.8f, 1f)
            val path = Path()
            path.moveTo(ox, y0); path.lineTo(ax, ay); path.lineTo(bx, by); path.close()
            p.shader = LinearGradient(ox, y0, (ax + bx) / 2f, (ay + by) / 2f, withAlpha(col, 0.6f * a), withAlpha(col, 0f), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null
        val beat = Math.pow(Math.max(0.0, Math.sin(t * 8.0)), 8.0).toFloat()
        if (beat > 0.02f) {
            p.color = withAlpha(Color.WHITE, 0.22f * a * beat)
            c.drawRect(x0, y0, x0 + w, y0 + h, p)
        }
    }

    private fun drawFireplace(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y0 + h, 0f, y0 + h * 0.6f, withAlpha(0xFFFF6A00.toInt(), 0.5f * a), withAlpha(0xFFFF6A00.toInt(), 0f), Shader.TileMode.CLAMP)
        c.drawRect(x0, y0 + h * 0.6f, x0 + w, y0 + h, p)
        val n = (60 + 100 * a).toInt()
        for (i in 0 until n) {
            val sp = 0.5f + hashNoise(i * 3 + 1)
            val u = (t * sp * 0.8f + hashNoise(i * 7 + 2)) % 1f
            val x = x0 + w * hashNoise(i * 5 + 4) + Math.sin((t * 3f + i).toDouble()).toFloat() * 0.02f * w * u
            val y = y0 + h * (1.02f - u * (0.18f + 0.3f * hashNoise(i * 9 + 8)))
            val r = Math.max(2f, W * 0.04f * (1f - u * 0.85f) * (0.6f + hashNoise(i * 13 + 6)))
            val col = flameColor("p_flame", u, i, t, null)
            p.shader = RadialGradient(x, y, r, intArrayOf(withAlpha(col, (1f - u) * 0.8f * a), withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        p.shader = null
    }

    private fun drawCrossFlash(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float) {
        val period = 0.55f
        val k = (t / period).toInt()
        val ph = (t % period) / period
        val pulse = Math.max(0f, 1f - ph * 3f)
        if (pulse <= 0.01f) return
        val fx = x0 + w * (0.2f + 0.6f * hashNoise(k * 7 + 1))
        val fy = y0 + h * (0.15f + 0.6f * hashNoise(k * 11 + 3))
        p.style = Paint.Style.FILL
        p.color = withAlpha(Color.WHITE, 0.30f * a * pulse)
        c.drawRect(x0, y0, x0 + w, y0 + h, p)
        val rad = W * 0.32f * pulse * (0.5f + 0.5f * a)
        p.shader = RadialGradient(fx, fy, rad * 0.6f, intArrayOf(withAlpha(Color.WHITE, 0.9f * pulse), withAlpha(Color.WHITE, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(fx, fy, rad * 0.6f, p)
        p.shader = null
        p.color = withAlpha(Color.WHITE, 0.95f * pulse)
        p.setShadowLayer(rad * 0.2f, 0f, 0f, Color.WHITE)
        star4(c, fx, fy, rad, p)
        p.clearShadowLayer()
    }

    private fun drawSyncHud(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float, durS: Float) {
        val cx = x0 + w / 2f
        val cone = Path()
        cone.moveTo(cx - w * 0.06f, y0); cone.lineTo(cx + w * 0.06f, y0)
        cone.lineTo(cx + w * 0.40f, y0 + h * 0.80f); cone.lineTo(cx - w * 0.40f, y0 + h * 0.80f); cone.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y0, 0f, y0 + h * 0.8f, withAlpha(0xFFBFD8FF.toInt(), 0.40f * a), withAlpha(0xFFBFD8FF.toInt(), 0f), Shader.TileMode.CLAMP)
        c.drawPath(cone, p)
        p.shader = null
        val pct = clampF(t / Math.max(0.5f, durS), 0f, 1f)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        tp.textSize = W * 0.036f
        tp.letterSpacing = 0.35f
        tp.color = withAlpha(Color.WHITE, 0.9f * Math.min(1f, a + 0.3f))
        tp.textAlign = Paint.Align.CENTER
        c.drawText("SYNC " + Math.round(pct * 100f) + "%", cx, y0 + h * 0.13f, tp)
        val bw = w * 0.42f
        val by = y0 + h * 0.15f
        p.color = withAlpha(Color.WHITE, 0.22f)
        c.drawRect(cx - bw / 2f, by, cx + bw / 2f, by + W * 0.004f, p)
        p.color = withAlpha(Color.WHITE, 0.95f)
        c.drawRect(cx - bw / 2f, by, cx - bw / 2f + bw * pct, by + W * 0.004f, p)
    }

    private fun drawGlowGrid(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float) {
        p.style = Paint.Style.FILL
        val rows = 9
        val cols = 13
        for (r in 0 until rows) {
            val z = ((r + (t * 0.35f) % 1f) / rows)
            val zz = Math.pow(z.toDouble(), 1.5).toFloat()
            val y = y0 + h * (0.52f + 0.46f * zz)
            for (cI in 0 until cols) {
                val x = x0 + w * 0.5f + (cI - (cols - 1) / 2f) * w * 0.115f * (0.25f + zz * 1.3f)
                if (x < x0 - 20f || x > x0 + w + 20f) continue
                val rad = Math.max(1.5f, W * 0.013f * (0.25f + zz * 1.4f))
                val tw = 0.55f + 0.45f * Math.sin((t * 2f + r * 1.7f + cI * 2.3f).toDouble()).toFloat()
                p.shader = RadialGradient(x, y, rad * 2.2f, intArrayOf(withAlpha(Color.WHITE, (0.35f + 0.65f * zz) * tw * a), withAlpha(0xFFBFD8FF.toInt(), 0f)), null, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, rad * 2.2f, p)
            }
        }
        p.shader = null
    }

    private fun drawNeonRing(c: Canvas, p: Paint, x0: Float, y0: Float, w: Float, h: Float, t: Float, W: Float, a: Float) {
        val cx = x0 + w / 2f
        val cy = y0 + h / 2f
        val R = Math.min(w, h) * 0.13f * (1f + 0.03f * Math.sin(t * 2.0).toFloat())
        // flowing light streaks
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val starts = arrayOf(floatArrayOf(0f, 0.30f), floatArrayOf(1f, 0.55f), floatArrayOf(0f, 0.85f), floatArrayOf(1f, 0.15f), floatArrayOf(0.2f, 1f), floatArrayOf(0.8f, 0f))
        for ((i, s) in starts.withIndex()) {
            val sx = x0 + s[0] * w
            val sy = y0 + s[1] * h
            val path = Path()
            path.moveTo(sx, sy)
            path.quadTo(cx + (sx - cx) * 0.3f, sy + (cy - sy) * 0.9f, cx + (sx - cx) * 0.18f, cy + (sy - cy) * 0.18f)
            val col = if (i % 2 == 0) 0xFF4D7CFF.toInt() else 0xFFE040FB.toInt()
            p.strokeWidth = Math.max(1.5f, W * 0.004f * (1f + (i % 3)))
            p.color = withAlpha(col, 0.85f * a)
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(W * 0.25f, W * 0.12f), -t * W * 0.5f + i * 40f)
            p.setShadowLayer(W * 0.012f, 0f, 0f, col)
            c.drawPath(path, p)
        }
        p.pathEffect = null
        // the ring
        val sw = R * 0.14f
        p.strokeWidth = sw
        val sh = android.graphics.SweepGradient(cx, cy, intArrayOf(0xFFFF4DD8.toInt(), 0xFF4D7CFF.toInt(), 0xFFB26BFF.toInt(), 0xFFFF4DD8.toInt()), null)
        val m = android.graphics.Matrix()
        m.setRotate(t * 40f, cx, cy)
        sh.setLocalMatrix(m)
        p.shader = sh
        p.setShadowLayer(R * 0.5f, 0f, 0f, 0xFFB26BFF.toInt())
        c.drawCircle(cx, cy, R, p)
        p.strokeWidth = sw * 0.4f
        c.drawCircle(cx, cy, R * 1.12f, p)
        p.shader = null
        p.clearShadowLayer()
        // bright dot travelling around the ring
        val ang = t * 1.6f
        val dx = cx + Math.cos(ang.toDouble()).toFloat() * R * 1.06f
        val dy = cy + Math.sin(ang.toDouble()).toFloat() * R * 1.06f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(dx, dy, R * 0.45f, intArrayOf(withAlpha(Color.WHITE, 0.95f), withAlpha(0xFF8FA8FF.toInt(), 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(dx, dy, R * 0.45f, p)
        p.shader = null
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
            "clublights" -> drawClubLights(c, p, x0, y0, w, h, t, a)
            "fireplace" -> drawFireplace(c, p, x0, y0, w, h, t, W, a)
            "crossflash" -> drawCrossFlash(c, p, x0, y0, w, h, t, W, a)
            "synchud" -> drawSyncHud(c, p, x0, y0, w, h, t, W, a, f.durMs / 1000f)
            "glowgrid" -> drawGlowGrid(c, p, x0, y0, w, h, t, W, a)
            "neonring" -> drawNeonRing(c, p, x0, y0, w, h, t, W, a)
            "p_outline", "p_electric", "p_sparkle", "p_butterfly", "p_flame", "p_bluefire", "p_colorflame", "p_blaze", "p_spiral" -> {
                val cc = curClip.get()
                if (cc != null) {
                    val lk = Seg.lookAt(cc.first, cc.second, segSync())
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
            if (Fx.vignetteOf(c) > 0.01f || Fx.grainOf(c) > 0.01f || ClipLight.of(c) != null || ClipMask.of(c) != null || c.cut.isNotEmpty()) return false
            if (c.motion.isNotEmpty() && Motion.hasOverlayPost(c.motion)) return false
        }
        return true
    }
}
