package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface

object EqAssets {
    private val cache = HashMap<String, Bitmap?>()

    fun bmp(name: String): Bitmap? {
        synchronized(cache) {
            if (cache.containsKey(name)) return cache[name]
        }
        val b: Bitmap? = try {
            App.ctx.assets.open("eq/$name").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        synchronized(cache) { cache[name] = b }
        return b
    }
}

fun hueMatrix(deg: Float): ColorMatrix {
    val a = Math.toRadians(deg.toDouble())
    val c = Math.cos(a).toFloat()
    val s = Math.sin(a).toFloat()
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    return ColorMatrix(floatArrayOf(
        lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
        lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
        lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
}

fun drawLabel(g: Ctx2D, st: EqState, cx: Float, cy: Float, size: Float, maxW: Float) {
    val name = st.name.trim()
    if (name.isEmpty()) return
    g.save(); g.fontFamily = Typeface.SANS_SERIF; g.direction = "rtl"; g.textAlign = "center"; g.textBaseline = "middle"
    var fs = size
    g.setFont(800, fs)
    val w = g.measureText(name).width
    if (w > maxW) { fs = Math.floor((fs * maxW / w).toDouble()).toFloat(); g.setFont(800, fs) }
    val hw = Math.min(w, maxW) / 2f
    val gr = g.createLinearGradient(cx - hw, 0f, cx + hw, 0f)
    gr.addColorStop(0f, css(st.p("text"))); gr.addColorStop(1f, css(st.p("text2")))
    g.shadowColor = Color.argb(204, 0, 0, 0); g.shadowBlur = 8 * curK; g.fillStyle = gr; g.fillText(name, cx, cy); g.restore()
}

fun drawTimeLabel(g: Ctx2D, st: EqState, t: Float, cx: Float, cy: Float, size: Float) {
    g.save(); g.fontFamily = Typeface.SANS_SERIF; g.setFont(700, size); g.textAlign = "center"; g.textBaseline = "middle"; g.direction = "ltr"
    g.lineWidth = 4f; g.strokeStyle = Color.argb(178, 0, 0, 0); g.lineJoin = "round"; g.strokeText(fmtClock(t), cx, cy)
    g.fillStyle = css(st.p("ui")); g.fillText(fmtClock(t), cx, cy); g.restore()
}

/** The round letter badge used by several designs. */
fun drawBadge(g: Ctx2D, st: EqState, baseY: Float) {
    val lt = st.lt
    val bx = 640f + lt[0]
    val by = baseY + lt[1]
    val r = lt[2] / 2f
    g.save(); g.beginPath(); g.arc(bx, by, r, 0f, TAU); g.fillStyle = css(st.p("panel"), 0.95f); g.shadowColor = css(st.p("main"), 0.9f); g.shadowBlur = 18f; g.fill(); g.shadowBlur = 0f
    g.lineWidth = Math.max(3f, r * 0.12f); g.strokeStyle = metal(g, bx - r, by - r, bx + r, by + r, st.p("main")); g.stroke()
    g.lineWidth = 1.5f; g.strokeStyle = css(lighten(st.p("main"), 0.7f), 0.8f); g.beginPath(); g.arc(bx, by, r * 0.84f, 0f, TAU); g.stroke(); g.restore()
    curK = 1f
    putSprite(g, letterSprite(st, r * 1.0f, r * 1.3f, st.p("letter"), glitter = 140, outline = darken(st.p("letter"), 0.6f)), bx, by, st.p("letter"), 10f)
}

private fun baseDef(c: Map<String, String>, showName: Boolean, barStyle: String, showPlayer: Boolean, showLetter: Boolean, bgMirror: Boolean = false, lt: FloatArray = floatArrayOf(0f, 0f, 90f)) =
    EqDef(c, showName, barStyle, showPlayer, showLetter, 0f, bgMirror, lt)

// ------------------------------------------------------------------ 45 image designs

class EqImageTpl(val m: EqImgMeta) : EqTpl(m.id, m.title) {
    init {
        labels = mapOf("main" to "رنگ قاب حرف و نوار پیشرفت", "accent" to "رنگ نوار پیشرفت (انتها)", "panel" to "رنگ زمینهٔ نشان حرف")
        def = baseDef(
            mapOf(
                "main" to m.bar2, "accent" to m.bar1, "text" to "#ffffff", "text2" to m.text2, "letter" to m.letter, "panel" to "#07080f",
                "bar1" to m.bar1, "bar2" to m.bar2, "bar3" to m.bar3, "ui" to "#ffffff"
            ), true, "grad", true, true, false, floatArrayOf(-150f, 135f, 92f)
        )
        prog = Prog(260f, 1020f, 600f, "main", "accent", 5f, 10f)
        time = TimeP(662f, 260f, 1020f, 24f)
        ctrl = Ctrl(640f, 662f, 70f, 24f)
        needsBadge = true
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        val im = EqAssets.bmp("imgdata_" + m.id + ".webp")
        L.over = mk(EQ_W, EQ_H)
        val g = L.over!!.g
        g.lineCap = "round"; g.lineJoin = "round"
        val pl = st.showPlayer
        val nm = st.showName
        var EH = if (pl) (if (nm) 360f else 420f) else (if (nm) 460f else 560f)
        val cy = if (pl) (if (nm) 215f else 240f) else (if (nm) 250f else 300f)
        if (im != null) {
            var EW = EH * im.width / im.height
            if (EW > 640f) { EW = 640f; EH = EW * im.height / im.width }
            L.dw = EW; L.dh = EH; L.cy = cy
            val art = mk(im.width, im.height)
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            if (st.hue != 0f) p.colorFilter = ColorMatrixColorFilter(hueMatrix(st.hue))
            Canvas(art.bmp).drawBitmap(im, 0f, 0f, p)
            L.art = art
            val ov = EW * 0.1f
            val xl = 640f - EW / 2f + ov
            val xr = 640f + EW / 2f - ov
            val mh = EH * 0.4f
            if (xl - 36f > 60f) {
                val n = Math.max(6, Math.floor(((xl - 36f) / 13f).toDouble()).toInt())
                L.bL = mkBars(36f, xl, cy, mh, n, 8f, cen = 1f, envK = 1.7f, envMin = 0.3f, mirror = true, side = "left")
                L.bR = mkBars(xr, 1244f, cy, mh, n, 8f, cen = 0f, envK = 1.7f, envMin = 0.3f, mirror = true, side = "right")
            }
            if (st.showLetter) drawBadge(g, st, cy)
        }
        if (nm) drawName(g, st, 640f, if (pl) 462f else 585f, 64f, 1000f)
        if (pl) playerStatic(g, st, this)
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val art = L.art
        if (art != null) {
            val bl = L.bL
            val br = L.bR
            if (bl != null && br != null) { drawBars(g, this, st, d, bl); drawBars(g, this, st, d, br) }
            val sc = 1f + 0.035f * Math.pow(d.bass.toDouble(), 1.5).toFloat()
            g.save(); g.translate(640f, L.cy); g.scale(sc, sc)
            g.drawImage(art, -L.dw / 2f, -L.dh / 2f, L.dw, L.dh)
            if (st.fx && d.bass > 0.25f) {
                g.globalCompositeOperation = "lighter"
                g.globalAlpha = Math.min(0.4f, 0.5f * Math.pow(d.bass.toDouble(), 1.3).toFloat())
                g.drawImage(art, -L.dw / 2f, -L.dh / 2f, L.dw, L.dh)
                g.globalAlpha = 1f; g.globalCompositeOperation = "source-over"
            }
            g.restore()
        }
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) playerDyn(g, this, st, d)
    }
}

// ------------------------------------------------------------------ waving flag

class EqFlag : EqTpl("flag", "پرچم افغانستان (موج‌دار)") {
    private val FX = 38.6f
    private val FY = 109.7f
    private val SW = 1280f
    private val SH = 955f

    init {
        labels = mapOf("main" to "رنگ قاب حرف و نوار پیشرفت", "accent" to "رنگ نوار پیشرفت (انتها)", "panel" to "رنگ زمینهٔ نشان حرف",
            "bar1" to "(استفاده نمی‌شود)", "bar2" to "(استفاده نمی‌شود)", "bar3" to "(استفاده نمی‌شود)")
        def = baseDef(
            mapOf("main" to "#ffffff", "accent" to "#35d36a", "text" to "#ffffff", "text2" to "#ffe9a8", "letter" to "#ffe9a8", "panel" to "#101010",
                "bar1" to "#d71f1f", "bar2" to "#d71f1f", "bar3" to "#ffffff", "ui" to "#ffffff"),
            true, "solid", true, false, false, floatArrayOf(-520f, -250f, 92f)
        )
        prog = Prog(260f, 1020f, 640f, "main", "accent", 5f, 10f)
        time = TimeP(680f, 260f, 1020f, 24f)
        ctrl = Ctrl(640f, 680f, 70f, 22f)
        needsBadge = true
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        L.over = mk(EQ_W, EQ_H)
        L.FL = mk(EQ_W, EQ_H + 240)
        val g = L.over!!.g
        g.lineCap = "round"; g.lineJoin = "round"
        if (st.showLetter) drawBadge(g, st, 360f)
        if (st.showName) drawName(g, st, 640f, if (st.showPlayer) 575f else 630f, 66f, 1000f)
        if (st.showPlayer) playerStatic(g, st, this)
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val sky = EqAssets.bmp("flagdata_sky.jpg")
        val img = EqAssets.bmp("flagdata_fg.webp")
        if (sky != null && img != null) {
            val PAD = 120f
            val dy0 = -(SH - 720f) / 2f
            val fl = L.FL!!.g
            val iw = img.width
            val ih = img.height
            val sw = 4
            g.drawImage(sky, 0f, dy0, SW, SH)
            var mean = 0f
            for (j in 0 until 40) mean += d.lv[j]
            mean /= 40f
            val en = Math.min(1.2f, 0.55f * d.bass + 0.9f * mean)
            val amp = (8f + 46f * en) * st.barScale
            val spd = 2f + 3.2f * en
            val nS = Math.ceil(iw.toDouble() / sw).toInt()
            val phs = FloatArray(nS)
            fl.globalCompositeOperation = "source-over"
            fl.clearRect(0f, 0f, EQ_W.toFloat(), EQ_H + 2 * PAD)
            for (i in 0 until nS) {
                val x = i * sw
                val u = x.toFloat() / iw
                val A = amp * (0.03f + 0.97f * Math.pow(u.toDouble(), 1.25).toFloat())
                val ph = u * 7.2f - d.now * spd
                phs[i] = ph
                val off = A * (Math.sin(ph.toDouble()).toFloat() + 0.35f * Math.sin((2.1f * ph + 1.3f).toDouble()).toFloat())
                fl.drawImage(img, x.toFloat(), 0f, sw.toFloat(), ih.toFloat(), FX + x, FY + dy0 + PAD + off, sw + 0.7f, ih.toFloat())
            }
            fl.globalCompositeOperation = "source-atop"
            for (i in 0 until nS) {
                val u = i * sw.toFloat() / iw
                val sh = Math.cos(phs[i].toDouble()).toFloat() * (0.15f + 0.85f * u)
                fl.fillStyle = if (sh > 0) Color.argb(Math.round(sh * 0.30f * 255f), 255, 255, 255) else Color.argb(Math.round(-sh * 0.42f * 255f), 0, 0, 0)
                fl.fillRect(Math.round(FX + i * sw).toFloat(), 0f, sw.toFloat(), EQ_H + 2 * PAD)
            }
            fl.globalCompositeOperation = "source-over"
            g.drawImage(L.FL, 0f, -PAD)
        }
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) playerDyn(g, this, st, d)
    }
}

// ------------------------------------------------------------------ circular logo designs

class EqAbRing : EqTpl("abring", "حلقهٔ قرمز آینه‌ای (لوگو)") {
    private val CY = 360f
    private val R = 148f

    init {
        labels = mapOf("main" to "رنگ حلقه", "accent" to "(استفاده نمی‌شود)", "letter" to "رنگ حرف لوگو", "panel" to "رنگ دیسک لوگو",
            "bar1" to "(استفاده نمی‌شود)", "bar2" to "رنگ میله‌های گرد", "bar3" to "رنگ جرقه‌ها", "ui" to "رنگ تایمر")
        def = baseDef(
            mapOf("main" to "#e01822", "accent" to "#ff4a4a", "text" to "#ffffff", "text2" to "#ffd0d0", "letter" to "#d01820", "panel" to "#d9d9dd",
                "bar1" to "#9a0c14", "bar2" to "#ff1e1e", "bar3" to "#ffffff", "ui" to "#e6e6ee"),
            false, "solid", false, false, true
        )
        prog = Prog(260f, 1020f, 640f, "main", "bar3", 5f, 10f)
        time = TimeP(680f, 260f, 1020f, 24f)
        ctrl = Ctrl(640f, 680f, 70f, 22f)
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        L.over = mk(EQ_W, EQ_H)
        val og = L.over!!.g
        if (st.showName) drawLabel(og, st, 640f, CY + R + 150f, 40f, 900f)
        if (st.showPlayer) playerStatic(og, st, this)
        val D = (2 * (R + 30f)).toInt()
        L.disc = mk(D, D)
        val g = L.disc!!.g
        g.translate(D / 2f, D / 2f)
        val gr = g.createRadialGradient(-R * 0.25f, -R * 0.3f, R * 0.1f, 0f, 0f, R)
        gr.addColorStop(0f, css(lighten(st.p("panel"), 0.5f))); gr.addColorStop(1f, css(darken(st.p("panel"), 0.25f)))
        g.beginPath(); g.arc(0f, 0f, R, 0f, TAU); g.fillStyle = gr; g.shadowColor = css(st.p("main"), 0.9f); g.shadowBlur = 24f; g.fill(); g.shadowBlur = 0f
        if (st.heartMode == "photo" && st.photo != null) {
            g.save(); g.beginPath(); g.arc(0f, 0f, R - 6f, 0f, TAU); g.clip(); cover(g, st.photo, -R, -R, 2 * R, 2 * R); g.restore()
        } else {
            putSprite(g, letterSprite(st, R * 0.72f, R * 1.35f, st.p("letter"), outline = darken(st.p("letter"), 0.65f)), 0f, 0f, st.p("letter"), 8f)
        }
        g.beginPath(); g.arc(0f, 0f, R + 5f, 0f, TAU); g.lineWidth = 9f; g.strokeStyle = css(st.p("main")); g.shadowColor = css(st.p("main"), 1f); g.shadowBlur = 18f; g.stroke(); g.shadowBlur = 0f
        g.lineWidth = 2f; g.strokeStyle = Color.argb(217, 255, 255, 255); g.beginPath(); g.arc(0f, 0f, R - 3f, 0f, TAU); g.stroke()
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val cx = 640f
        val cy = CY
        val lv = d.lv
        val N = 128
        g.save(); g.lineCap = "round"; g.beginPath()
        val sp = ArrayList<FloatArray>()
        for (k in 0 until N) {
            val ph = (k + 0.5f) / N * TAU
            val sx = Math.sin(ph.toDouble()).toFloat()
            val sy = Math.cos(ph.toDouble()).toFloat()
            val th = Math.min(ph, TAU - ph) / PIf
            val bi = Math.min((NBAND - 1).toFloat(), th * 52f)
            val i0 = Math.floor(bi.toDouble()).toInt()
            val i1 = Math.min(NBAND - 1, i0 + 1)
            val f = bi - i0
            val lev = lv[i0] * (1 - f) + lv[i1] * f
            val env = 0.3f + 0.7f * Math.pow((1 - th).toDouble(), 1.3).toFloat()
            val len = (6f + 66f * lev * env) * st.barScale
            val r0 = R + 16f
            g.moveTo(cx + sx * r0, cy + sy * r0); g.lineTo(cx + sx * (r0 + len), cy + sy * (r0 + len))
            sp.add(floatArrayOf(sx, sy, len * (0.5f + 0.6f * hashF(k * 3.1f)), r0))
        }
        g.strokeStyle = css(st.p("bar2")); g.lineWidth = 4f; g.shadowColor = css(st.p("bar2"), 0.9f); g.shadowBlur = 12f; g.stroke(); g.shadowBlur = 0f
        g.beginPath()
        for (s in sp) {
            g.moveTo(cx + s[0] * (s[3] + 4f), cy + s[1] * (s[3] + 4f)); g.lineTo(cx + s[0] * (s[3] + 4f + s[2]), cy + s[1] * (s[3] + 4f + s[2]))
        }
        g.strokeStyle = css(st.p("bar3"), 0.85f); g.lineWidth = 1.4f; g.stroke()
        for (i in 0 until 22) {
            val q = d.now * 2f + i
            val fr = q - Math.floor(q.toDouble()).toFloat()
            val sd = hashF(i * 13.7f + Math.floor(q.toDouble()).toFloat())
            val an = hashF(sd * 7.3f) * TAU
            val rd = R + 34f + hashF(sd * 3.9f) * 90f * (0.5f + d.bass)
            g.globalAlpha = (1 - fr) * 0.9f; g.strokeStyle = css(st.p("bar3")); g.lineWidth = 2f; g.beginPath()
            g.moveTo(cx + Math.cos(an.toDouble()).toFloat() * rd, cy + Math.sin(an.toDouble()).toFloat() * rd)
            g.lineTo(cx + Math.cos(an.toDouble()).toFloat() * (rd + 9f), cy + Math.sin(an.toDouble()).toFloat() * (rd + 9f)); g.stroke()
        }
        g.globalAlpha = 1f; g.restore()
        val sc = 1f + 0.03f * Math.pow(d.bass.toDouble(), 1.5).toFloat()
        val D = L.disc!!.w.toFloat()
        g.save(); g.translate(cx, cy); g.scale(sc, sc); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
        drawTimeLabel(g, st, d.t, cx, cy + R + 100f, 26f)
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) playerDyn(g, this, st, d)
    }
}

class EqTkRing : EqTpl("tkring", "لوگوی TK با موج زرد (آینه‌ای)") {
    private val CY = 355f
    private val R = 125f

    init {
        labels = mapOf("main" to "رنگ قرمز (حلقه و حرف آخر)", "accent" to "رنگ سبز (حلقه و سایه پس‌زمینه)", "letter" to "رنگ حرف لوگو", "panel" to "رنگ دیسک لوگو",
            "bar1" to "رنگ موج پایین", "bar2" to "رنگ شعله‌ها و حلقه", "bar3" to "(استفاده نمی‌شود)", "ui" to "رنگ تایمر")
        def = baseDef(
            mapOf("main" to "#d4161c", "accent" to "#19c83a", "text" to "#ffffff", "text2" to "#ffffff", "letter" to "#ffffff", "panel" to "#0a0a0a",
                "bar1" to "#f4e61c", "bar2" to "#f7d11a", "bar3" to "#ffffff", "ui" to "#ffffff"),
            true, "solid", false, false, true
        )
        prog = Prog(260f, 1020f, 672f, "main", "accent", 5f, 10f)
        time = TimeP(700f, 260f, 1020f, 22f)
        ctrl = Ctrl(640f, 700f, 70f, 18f)
    }

    override fun bgTint(g: Ctx2D, P: Map<String, Rgb>) {
        val main = P["main"] ?: return
        val acc = P["accent"] ?: return
        val W = EQ_W.toFloat()
        val H = EQ_H.toFloat()
        val gr = g.createLinearGradient(0f, 0f, 0f, H)
        gr.addColorStop(0f, css(main, 0.55f)); gr.addColorStop(0.3f, css(main, 0.1f)); gr.addColorStop(0.48f, css(acc, 0.42f)); gr.addColorStop(0.8f, css(acc, 0.38f)); gr.addColorStop(1f, css(main, 0.35f))
        g.fillStyle = gr; g.fillRect(0f, 0f, W, H)
        for (x in floatArrayOf(W * 0.22f, W * 0.78f)) {
            val r = g.createRadialGradient(x, H * 0.5f, 10f, x, H * 0.5f, 330f)
            r.addColorStop(0f, css(acc, 0.4f)); r.addColorStop(1f, css(acc, 0f))
            g.fillStyle = r; g.fillRect(0f, 0f, W, H)
        }
    }

    private fun flameShape(g: Ctx2D, s: Float) {
        g.beginPath(); g.moveTo(0f, 0f)
        g.bezierCurveTo(s * 0.2f, -s * 0.5f, s * 0.7f, -s * 0.6f, s, -s * 0.25f)
        g.bezierCurveTo(s * 0.8f, -s * 0.3f, s * 0.6f, -s * 0.2f, s * 0.62f, 0f)
        g.bezierCurveTo(s * 0.8f, s * 0.1f, s * 0.95f, s * 0.35f, s * 0.7f, s * 0.55f)
        g.bezierCurveTo(s * 0.5f, s * 0.3f, s * 0.25f, s * 0.2f, 0f, 0f)
        g.closePath()
    }

    private fun twoToneLetters(g: Ctx2D, st: EqState, hgt: Float, maxW: Float, cA: Rgb, cB: Rgb) {
        val chars = st.letter.trim().codePoints().toArray().map { String(Character.toChars(it)) }
        if (chars.isEmpty()) return
        val sps = ArrayList<Sprite?>()
        for ((i, ch) in chars.withIndex()) {
            val s2 = EqState()
            s2.letter = ch
            val c1 = if (i == chars.size - 1 && chars.size > 1) cB else cA
            sps.add(letterSprite(s2, hgt, maxW / chars.size, c1, outline = darken(cA, 0.75f)))
        }
        var tot = -4f * (chars.size - 1)
        for (sp in sps) tot += sp?.w ?: 0f
        var x = -tot / 2f
        for (sp in sps) {
            if (sp == null) continue
            putSprite(g, sp, x + sp.w / 2f, 0f, cB, 8f)
            x += sp.w - 4f
        }
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        L.over = mk(EQ_W, EQ_H)
        val og = L.over!!.g
        if (st.showName) drawLabel(og, st, 640f, 95f, 34f, 700f)
        if (st.showPlayer) playerStatic(og, st, this)
        val D = (2 * (R + 40f)).toInt()
        L.disc = mk(D, D)
        val g = L.disc!!.g
        g.translate(D / 2f, D / 2f); g.lineJoin = "round"
        g.beginPath(); g.arc(0f, 0f, R, 0f, TAU); g.fillStyle = css(st.p("panel")); g.shadowColor = Color.argb(204, 0, 0, 0); g.shadowBlur = 20f; g.fill(); g.shadowBlur = 0f
        if (st.heartMode == "photo" && st.photo != null) {
            g.save(); g.beginPath(); g.arc(0f, 0f, R - 8f, 0f, TAU); g.clip(); cover(g, st.photo, -R, -R, 2 * R, 2 * R); g.restore()
        } else {
            for (a in floatArrayOf(-45f, 45f, 135f, 225f)) {
                g.save(); g.rotate(a * PIf / 180f); g.translate(R * 0.4f, -R * 0.04f); flameShape(g, R * 0.5f)
                g.fillStyle = css(st.p("bar2")); g.fill(); g.lineWidth = 2.5f; g.strokeStyle = css(darken(st.p("main"), 0.4f)); g.stroke(); g.restore()
            }
            g.save(); g.translate(0f, R * 0.14f); twoToneLetters(g, st, R * 0.62f, R * 1.3f, st.p("letter"), st.p("main")); g.restore()
            crown(g, 0f, -R * 0.2f, R * 0.5f, 0.55f, mapOf("main" to intArrayOf(250, 250, 250)))
        }
        val segs = listOf(Triple(130f, 230f, "accent"), Triple(230f, 375f, "main"), Triple(15f, 130f, "bar2"))
        for ((a0, a1, key) in segs) {
            val c = st.p(key)
            g.beginPath(); g.arc(0f, 0f, R + 9f, a0 * PIf / 180f, a1 * PIf / 180f); g.lineWidth = 15f; g.lineCap = "butt"; g.strokeStyle = css(c)
            g.shadowColor = css(c, 0.9f); g.shadowBlur = 12f; g.stroke()
        }
        g.shadowBlur = 0f; g.lineWidth = 2f; g.strokeStyle = Color.argb(128, 255, 255, 255); g.beginPath(); g.arc(0f, 0f, R + 17f, 0f, TAU); g.stroke()
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val cx = 640f
        val cy = CY
        val x0 = 365f
        val x1 = 915f
        val wy = 636f
        val M = 220
        val amp = 40f * st.barScale
        g.save(); g.beginPath()
        for (i in 0..M) {
            val u = i.toFloat() / M
            val tp = Math.pow(Math.sin((PIf * u).toDouble()), 0.55).toFloat()
            val lv = d.lv
            val v = (lv[0] * Math.sin((u * 38f + d.now * 7f).toDouble()).toFloat() + lv[10] * 0.7f * Math.sin((u * 115f - d.now * 10f).toDouble()).toFloat() +
                lv[26] * 0.5f * Math.sin((u * 270f + d.now * 14f).toDouble()).toFloat()) * 0.9f
            val x = x0 + u * (x1 - x0)
            val y = wy - Math.max(-1.4f, Math.min(1.4f, v)) * amp * tp
            if (i > 0) g.lineTo(x, y) else g.moveTo(x, y)
        }
        val fw = st.fade / 100f * 0.5f
        val gr = g.createLinearGradient(x0, 0f, x1, 0f)
        val cA = css(st.p("bar1"), 0f)
        val cB = css(st.p("bar1"), 1f)
        if (fw > 0f) { gr.addColorStop(0f, cA); gr.addColorStop(fw, cB); gr.addColorStop(1 - fw, cB); gr.addColorStop(1f, cA) } else { gr.addColorStop(0f, cB); gr.addColorStop(1f, cB) }
        g.lineJoin = "round"; g.lineCap = "round"; g.strokeStyle = gr; g.lineWidth = 3.4f; g.shadowColor = css(st.p("bar1"), 0.9f); g.shadowBlur = 10f; g.stroke(); g.restore()
        val sc = 1f + 0.03f * Math.pow(d.bass.toDouble(), 1.5).toFloat()
        val D = L.disc!!.w.toFloat()
        g.save(); g.translate(cx, cy); g.scale(sc, sc); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
        drawTimeLabel(g, st, d.t, cx, cy + R + 83f, 30f)
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) playerDyn(g, this, st, d)
    }
}

fun eqImageTemplates(): List<EqTpl> = EQ_IMG_META.map { EqImageTpl(it) }
