package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface

/**
 * Shared pieces of the equalizer designs (ported from the original HTML app, part 1).
 * All designs are drawn on a 1280x720 logical canvas through [Ctx2D].
 */

const val EQ_W = 1280
const val EQ_H = 720
const val NBAND = 70
val TAU = (Math.PI * 2).toFloat()
val PIf = Math.PI.toFloat()

typealias Rgb = IntArray

/** Scale applied to shadow blur (the canvas ignores the transform for shadows). */
var curK = 1f

fun hex2rgb(h0: String): Rgb {
    var h = h0.replace("#", "")
    if (h.length == 3) h = "" + h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
    val n = try { h.toLong(16).toInt() } catch (e: Exception) { 0 }
    return intArrayOf((n shr 16) and 255, (n shr 8) and 255, n and 255)
}

fun rgb2hex(c: Rgb): String = String.format("#%02x%02x%02x", c[0], c[1], c[2])
fun mix(a: Rgb, b: Rgb, t: Float): Rgb = intArrayOf(
    Math.round(a[0] + (b[0] - a[0]) * t), Math.round(a[1] + (b[1] - a[1]) * t), Math.round(a[2] + (b[2] - a[2]) * t)
)

val WHITE: Rgb = intArrayOf(255, 255, 255)
val BLACK: Rgb = intArrayOf(0, 0, 0)
fun lighten(c: Rgb, t: Float): Rgb = mix(c, WHITE, t)
fun darken(c: Rgb, t: Float): Rgb = mix(c, BLACK, t)
fun css(c: Rgb, a: Float = 1f): Int = Color.argb(Math.round(clampF(a, 0f, 1f) * 255f), clampI(c[0], 0, 255), clampI(c[1], 0, 255), clampI(c[2], 0, 255))

fun fmtClock(s0: Float): String {
    val s = Math.max(0, Math.floor(s0.toDouble()).toInt())
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, x) else String.format("%d:%02d", m, x)
}

fun fmt2(s0: Float): String {
    val s = Math.max(0, Math.floor(s0.toDouble()).toInt())
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, x) else String.format("%02d:%02d", m, x)
}

fun mk(w: Int, h: Int): Cv = Cv(w, h)

fun mulberry(seed: Int): () -> Float {
    var a = seed
    return {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0).toFloat()
    }
}

fun hashF(n: Float): Float {
    val x = Math.sin(n.toDouble() * 127.1 + 311.7) * 43758.5453
    return (x - Math.floor(x)).toFloat()
}

fun rr(g: Ctx2D, x: Float, y: Float, w: Float, h: Float, r: Float) {
    g.beginPath()
    g.moveTo(x + r, y)
    g.arcTo(x + w, y, x + w, y + h, r)
    g.arcTo(x + w, y + h, x, y + h, r)
    g.arcTo(x, y + h, x, y, r)
    g.arcTo(x, y, x + w, y, r)
    g.closePath()
}

fun cover(g: Ctx2D, img: Bitmap?, x: Float, y: Float, w: Float, h: Float) {
    if (img == null || img.isRecycled) return
    val iw = img.width
    val ih = img.height
    if (iw == 0 || ih == 0) return
    val s = Math.max(w / iw, h / ih)
    val dw = iw * s
    val dh = ih * s
    g.drawImage(img, x + (w - dw) / 2f, y + (h - dh) / 2f, dw, dh)
}

// ---------------------------------------------------------------- state / data

/** Everything a design needs to know (colours and switches). */
class EqState {
    var name = ""
    var letter = "K"
    var heartMode = "letter" // letter | photo
    var photo: Bitmap? = null
    var fx = true
    var barScale = 1f
    var barStyle = "grad"
    var showName = true
    var showPlayer = true
    var showLetter = true
    var hue = 0f
    var lt = floatArrayOf(-150f, 135f, 92f)
    var fade = 0f
    var dance = true
    var waveSpan = 0.26f
    var P: HashMap<String, Rgb> = HashMap()
    fun p(k: String): Rgb = P[k] ?: intArrayOf(255, 255, 255)
}

/** Per-frame audio data. */
class EqData(val t: Float, val dur: Float, val lv: FloatArray, val bass: Float, val now: Float, val wave: ByteArray?)

class EqDef(
    val col: Map<String, String>,
    val showName: Boolean = true,
    val barStyle: String = "grad",
    val showPlayer: Boolean = true,
    val showLetter: Boolean = true,
    val hue: Float = 0f,
    val bgMirror: Boolean = false,
    val lt: FloatArray = floatArrayOf(0f, 0f, 90f),
    val fade: Float = 0f
)

class Prog(val x0: Float, val x1: Float, val y: Float, val ga: String, val gb: String, val lw: Float, val r: Float)
class TimeP(val y: Float, val lx: Float, val rx: Float, val size: Float, val ca: String = "ui", val cb: String = "ui")
class Ctrl(val cx: Float, val cy: Float, val gap: Float, val r: Float)

class BarsP(
    val x0: Float, val x1: Float, val base: Float, val maxH: Float, val n: Int, val w: Float,
    val cen: Float = 0.5f, val envK: Float = 2f, val envMin: Float = 0.22f, val refl: Float = 0f,
    val halo: Boolean = true, val mirror: Boolean = false, val side: String = ""
) {
    val jit = FloatArray(n)

    init {
        val r = mulberry(n * 13 + 5)
        for (i in 0 until n) jit[i] = 0.8f + 0.2f * r()
    }
}

fun mkBars(x0: Float, x1: Float, base: Float, maxH: Float, n: Int, w: Float, cen: Float = 0.5f, envK: Float = 2f, envMin: Float = 0.22f,
           refl: Float = 0f, halo: Boolean = true, mirror: Boolean = false, side: String = ""): BarsP =
    BarsP(x0, x1, base, maxH, n, w, cen, envK, envMin, refl, halo, mirror, side)

/** Cached layers of a design. */
class EqLayers {
    var under: Cv? = null
    var heart: Cv? = null
    var over: Cv? = null
    var spark: Cv? = null
    var art: Cv? = null
    var dw = 0f
    var dh = 0f
    var cy = 0f
    var mid = 0.5f
    var bL: BarsP? = null
    var bR: BarsP? = null
    var disc: Cv? = null
    var FL: Cv? = null
    var PHOTO: Cv? = null
    var UI: Cv? = null
    var RING: Cv? = null
    var extra = HashMap<String, Any?>()
}

val KEYS_ALL = listOf("main", "accent", "text", "text2", "letter", "panel", "bar1", "bar2", "bar3", "ui")

/** A design. */
abstract class EqTpl(val id: String, val title: String) {
    var k = 1f
    var ox = 0f
    var oy = 0f
    var prog = Prog(260f, 1020f, 600f, "main", "accent", 5f, 10f)
    var time = TimeP(662f, 260f, 1020f, 24f)
    var ctrl = Ctrl(640f, 662f, 70f, 24f)
    var keys: List<String> = KEYS_ALL
    var labels: Map<String, String> = emptyMap()
    open var needsBadge = false
    lateinit var def: EqDef
    /** Optional tint painted over the background (for logo designs). */
    open fun bgTint(g: Ctx2D, P: Map<String, Rgb>) {}

    abstract fun build(st: EqState): EqLayers
    abstract fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData)
}

/** Design made of three cached layers (under / heart / over), animated bars and sparkles. */
abstract class EqLayered(id: String, title: String) : EqTpl(id, title) {
    var hc = floatArrayOf(460f, 520f)
    lateinit var bars: BarsP
    var bars2: BarsP? = null
    var sparks: List<FloatArray> = emptyList()
    open fun under(g: Ctx2D, st: EqState) {}
    open fun heartL(g: Ctx2D, st: EqState) {}
    open fun over(g: Ctx2D, st: EqState) {}
    open fun dyn(g: Ctx2D, st: EqState, d: EqData) {}

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = k
        val names = arrayOf("under", "heart", "over")
        for (n in names) {
            val c = mk(EQ_W, EQ_H)
            val g = c.g
            g.save()
            g.setTransform(k, 0f, 0f, k, ox, oy)
            g.lineCap = "round"
            g.lineJoin = "round"
            when (n) {
                "under" -> under(g, st)
                "heart" -> heartL(g, st)
                else -> over(g, st)
            }
            g.restore()
            when (n) {
                "under" -> L.under = c
                "heart" -> L.heart = c
                else -> L.over = c
            }
        }
        L.spark = sparkSprite(lighten(st.p("main"), 0.45f))
        curK = 1f
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        g.drawImage(L.under, 0f, 0f)
        g.save(); g.transform(k, 0f, 0f, k, ox, oy)
        drawBars(g, this, st, d, bars)
        val b2 = bars2
        if (b2 != null) drawBars(g, this, st, d, b2)
        g.restore()
        val hx = hc[0] * k + ox
        val hy = hc[1] * k + oy
        val sc = 1f + 0.018f * Math.pow(d.bass.toDouble(), 1.5).toFloat()
        g.save(); g.translate(hx, hy); g.scale(sc, sc); g.translate(-hx, -hy); g.drawImage(L.heart, 0f, 0f); g.restore()
        g.save(); g.transform(k, 0f, 0f, k, ox, oy); dyn(g, st, d); g.restore()
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) {
            g.save(); g.transform(k, 0f, 0f, k, ox, oy); playerDyn(g, this, st, d); g.restore()
        }
        if (st.fx && L.spark != null) {
            for ((i, s) in sparks.withIndex()) {
                val a = 0.35f + 0.65f * (0.5f + 0.5f * Math.sin((d.now * (1.4f + i * 0.37f) + i * 1.7f).toDouble()).toFloat())
                val r = s[2] * k * (0.7f + 0.3f * a)
                g.globalAlpha = a
                g.drawImage(L.spark, s[0] * k + ox - r, s[1] * k + oy - r, r * 2f, r * 2f)
            }
            g.globalAlpha = 1f
        }
    }
}

// ---------------------------------------------------------------- drawing primitives

fun metal(g: Ctx2D, x0: Float, y0: Float, x1: Float, y1: Float, c: Rgb): Grad {
    val gr = g.createLinearGradient(x0, y0, x1, y1)
    gr.addColorStop(0f, css(lighten(c, 0.8f)))
    gr.addColorStop(0.25f, css(lighten(c, 0.15f)))
    gr.addColorStop(0.5f, css(darken(c, 0.35f)))
    gr.addColorStop(0.72f, css(lighten(c, 0.45f)))
    gr.addColorStop(1f, css(darken(c, 0.3f)))
    return gr
}

fun goldFill(g: Ctx2D, x0: Float, y0: Float, x1: Float, y1: Float, c: Rgb): Grad {
    val gr = g.createLinearGradient(x0, y0, x1, y1)
    gr.addColorStop(0f, css(lighten(c, 0.35f)))
    gr.addColorStop(0.35f, css(c))
    gr.addColorStop(0.6f, css(darken(c, 0.28f)))
    gr.addColorStop(1f, css(lighten(c, 0.12f)))
    return gr
}

fun heartPath(g: Ctx2D, cx: Float, top: Float, w: Float, h: Float) {
    val L = cx - w / 2f
    val R = cx + w / 2f
    g.beginPath(); g.moveTo(cx, top + h * 0.11f)
    g.bezierCurveTo(cx - w * 0.06f, top - h * 0.1f, L, top - h * 0.1f, L, top + h * 0.33f)
    g.bezierCurveTo(L, top + h * 0.62f, cx - w * 0.14f, top + h * 0.84f, cx, top + h)
    g.bezierCurveTo(cx + w * 0.14f, top + h * 0.84f, R, top + h * 0.62f, R, top + h * 0.33f)
    g.bezierCurveTo(R, top - h * 0.1f, cx + w * 0.06f, top - h * 0.1f, cx, top + h * 0.11f)
    g.closePath()
}

fun bz(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray, n: Int): List<FloatArray> {
    val o = ArrayList<FloatArray>()
    for (i in 0..n) {
        val t = i.toFloat() / n
        val u = 1 - t
        o.add(floatArrayOf(
            u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
            u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]
        ))
    }
    return o
}

fun taper(g: Ctx2D, pts: List<FloatArray>, w0: Float, w1: Float, fill: Any) {
    g.fillStyle = fill
    val n = pts.size
    for (i in 0 until n) {
        val r = (w0 + (w1 - w0) * i / (n - 1)) / 2f
        g.beginPath(); g.arc(pts[i][0], pts[i][1], r, 0f, TAU); g.fill()
    }
}

fun leafAt(g: Ctx2D, x: Float, y: Float, ang: Float, len: Float, wid: Float, fill: Any) {
    g.save(); g.translate(x, y); g.rotate(ang); g.beginPath(); g.moveTo(0f, 0f)
    g.quadraticCurveTo(len * 0.5f, -wid, len, 0f); g.quadraticCurveTo(len * 0.5f, wid, 0f, 0f)
    g.fillStyle = fill; g.fill(); g.restore()
}

fun scroll(g: Ctx2D, x: Float, y: Float, s: Float, fx: Float, fill: Any, w: Float) {
    g.save(); g.translate(x, y); g.scale(fx, 1f)
    val stem = bz(floatArrayOf(0f, 0f), floatArrayOf(s * 0.30f, s * 0.10f), floatArrayOf(s * 0.62f, s * 0.02f), floatArrayOf(s * 0.80f, -s * 0.22f), 70)
    val E = stem[stem.size - 1]
    val R = s * 0.24f
    val th0 = Math.atan2(0.6, 0.8).toFloat()
    val C = floatArrayOf(E[0] - 0.8f * R, E[1] - 0.6f * R)
    val sp = ArrayList<FloatArray>()
    for (i in 0..110) {
        val t = i / 110f
        val th = th0 - t * 2.3f * PIf
        val r = R * (1 - 0.78f * t)
        sp.add(floatArrayOf(C[0] + r * Math.cos(th.toDouble()).toFloat(), C[1] + r * Math.sin(th.toDouble()).toFloat()))
    }
    taper(g, stem + sp, w, w * 0.22f, fill)
    leafAt(g, stem[22][0], stem[22][1], -1.15f, s * 0.20f, s * 0.06f, fill)
    leafAt(g, stem[40][0], stem[40][1], -0.55f, s * 0.19f, s * 0.055f, fill)
    leafAt(g, stem[30][0], stem[30][1], 0.9f, s * 0.17f, s * 0.05f, fill)
    leafAt(g, stem[12][0], stem[12][1], 0.55f, s * 0.15f, s * 0.045f, fill)
    g.restore()
}

fun laurel(g: Ctx2D, cx: Float, cy: Float, R: Float, a0: Float, a1: Float, n: Int, len: Float, wid: Float, fill: Any) {
    for (i in 0..n) {
        val t = i.toFloat() / n
        val a = (a0 + (a1 - a0) * t) * PIf / 180f
        val x = cx + R * Math.cos(a.toDouble()).toFloat()
        val y = cy + R * Math.sin(a.toDouble()).toFloat()
        val dir = a + (if (a1 > a0) 1f else -1f) * PIf / 2f
        val l = len * (0.65f + 0.35f * Math.sin((PIf * (t * 0.8f + 0.1f)).toDouble()).toFloat())
        leafAt(g, x, y, dir - 0.6f, l, wid, fill)
        leafAt(g, x, y, dir + 0.6f, l, wid, fill)
    }
}

fun ball(g: Ctx2D, x: Float, y: Float, r: Float, c: Rgb) {
    val gr = g.createRadialGradient(x - r * 0.35f, y - r * 0.35f, r * 0.1f, x, y, r)
    gr.addColorStop(0f, css(lighten(c, 0.9f))); gr.addColorStop(0.5f, css(c)); gr.addColorStop(1f, css(darken(c, 0.45f)))
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0f, TAU); g.fill()
}

fun gem(g: Ctx2D, x: Float, y: Float, r: Float, c: Rgb) {
    g.save(); g.shadowColor = css(c, 0.9f); g.shadowBlur = 10 * curK
    g.beginPath(); g.moveTo(x, y - r); g.lineTo(x + r * 0.8f, y); g.lineTo(x, y + r * 1.1f); g.lineTo(x - r * 0.8f, y); g.closePath()
    val gr = g.createLinearGradient(x - r, y - r, x + r, y + r)
    gr.addColorStop(0f, 0xFFFFFFFF.toInt()); gr.addColorStop(0.5f, css(lighten(c, 0.3f))); gr.addColorStop(1f, css(darken(c, 0.2f)))
    g.fillStyle = gr; g.fill(); g.shadowBlur = 0f; g.lineWidth = Math.max(0.8f, r * 0.12f); g.strokeStyle = Color.argb(217, 255, 255, 255); g.stroke()
    g.beginPath(); g.moveTo(x - r * 0.8f, y); g.lineTo(x + r * 0.8f, y); g.moveTo(x, y - r); g.lineTo(x - r * 0.3f, y); g.moveTo(x, y - r); g.lineTo(x + r * 0.3f, y)
    g.lineWidth = Math.max(0.6f, r * 0.07f); g.strokeStyle = Color.argb(153, 120, 180, 220); g.stroke(); g.restore()
}

fun crown(g: Ctx2D, cx: Float, by: Float, w: Float, hr: Float, P: Map<String, Rgb>) {
    val h = w * hr
    val mc = P["main"] ?: intArrayOf(245, 185, 46)
    fun X(u: Float) = cx + u * w / 2f
    fun Y(v: Float) = by - v * h
    g.save(); g.lineJoin = "round"
    val pts = arrayOf(
        floatArrayOf(-1f, 0f), floatArrayOf(-1f, .6f), floatArrayOf(-.62f, .28f), floatArrayOf(-.5f, .84f), floatArrayOf(-.24f, .34f), floatArrayOf(0f, 1f),
        floatArrayOf(.24f, .34f), floatArrayOf(.5f, .84f), floatArrayOf(.62f, .28f), floatArrayOf(1f, .6f), floatArrayOf(1f, 0f)
    )
    g.beginPath()
    for ((i, p) in pts.withIndex()) if (i > 0) g.lineTo(X(p[0]), Y(p[1])) else g.moveTo(X(p[0]), Y(p[1]))
    g.closePath()
    g.shadowColor = css(mc, 0.9f); g.shadowBlur = 18 * curK; g.fillStyle = metal(g, cx - w / 2f, by - h, cx + w / 2f, by, mc); g.fill(); g.shadowBlur = 0f
    g.lineWidth = Math.max(1.5f, w * 0.012f); g.strokeStyle = css(darken(mc, 0.5f)); g.stroke()
    val bg = g.createLinearGradient(0f, Y(0.2f), 0f, by); bg.addColorStop(0f, css(lighten(mc, 0.55f))); bg.addColorStop(1f, css(darken(mc, 0.35f)))
    g.fillStyle = bg; g.fillRect(X(-1f), Y(0.2f), w, h * 0.2f); g.strokeRect(X(-1f), Y(0.2f), w, h * 0.2f)
    for (u in floatArrayOf(-.65f, -.22f, .22f, .65f)) gem(g, X(u), Y(0.1f), w * 0.03f, WHITE)
    gem(g, X(0f), Y(0.52f), w * 0.05f, intArrayOf(200, 235, 255))
    val balls = arrayOf(floatArrayOf(-1f, .6f, .03f), floatArrayOf(-.5f, .84f, .03f), floatArrayOf(0f, 1f, .045f), floatArrayOf(.5f, .84f, .03f), floatArrayOf(1f, .6f, .03f))
    for (b in balls) ball(g, X(b[0]), Y(b[1]), w * b[2], mc)
    g.restore()
}

fun star5(g: Ctx2D, x: Float, y: Float, r: Float, c: Rgb) {
    g.save(); g.beginPath()
    for (i in 0 until 10) {
        val a = -PIf / 2f + i * PIf / 5f
        val q = if (i % 2 == 1) r * 0.42f else r
        val px = x + q * Math.cos(a.toDouble()).toFloat()
        val py = y + q * Math.sin(a.toDouble()).toFloat()
        if (i > 0) g.lineTo(px, py) else g.moveTo(px, py)
    }
    g.closePath(); g.shadowColor = css(c, 0.9f); g.shadowBlur = 12 * curK; g.fillStyle = metal(g, x - r, y - r, x + r, y + r, c); g.fill(); g.shadowBlur = 0f
    g.lineWidth = Math.max(1f, r * 0.07f); g.strokeStyle = css(darken(c, 0.5f)); g.stroke(); g.restore()
}

fun note(g: Ctx2D, x: Float, y: Float, s: Float, c: Rgb, rot: Float = 0f) {
    g.save(); g.translate(x, y); g.rotate(rot); g.shadowColor = css(c, 0.9f); g.shadowBlur = 10 * curK
    g.fillStyle = metal(g, 0f, -s * 0.2f, s, s, c)
    g.beginPath(); g.ellipse(0f, s * 0.9f, s * 0.28f, s * 0.2f, -0.4f, 0f, TAU); g.fill()
    g.beginPath(); g.ellipse(s * 0.72f, s * 0.7f, s * 0.28f, s * 0.2f, -0.4f, 0f, TAU); g.fill()
    g.fillRect(s * 0.2f, 0f, s * 0.07f, s * 0.95f); g.fillRect(s * 0.92f, -s * 0.2f, s * 0.07f, s * 0.9f)
    g.beginPath(); g.moveTo(s * 0.2f, 0f); g.lineTo(s * 0.99f, -s * 0.2f); g.lineTo(s * 0.99f, s * 0.05f); g.lineTo(s * 0.2f, s * 0.26f); g.closePath(); g.fill(); g.restore()
}

fun miniHeart(g: Ctx2D, x: Float, y: Float, s: Float, c: Rgb, outline: Boolean) {
    g.save(); heartPath(g, x, y - s * 0.5f, s, s * 0.92f)
    g.shadowColor = css(c, 0.95f); g.shadowBlur = 10 * curK
    if (outline) {
        g.lineWidth = Math.max(1.5f, s * 0.09f); g.strokeStyle = css(c); g.stroke(); g.shadowBlur = 0f
        g.lineWidth = Math.max(0.8f, s * 0.03f); g.strokeStyle = css(lighten(c, 0.7f)); g.stroke()
    } else {
        g.fillStyle = metal(g, x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f, c); g.fill(); g.shadowBlur = 0f
        g.lineWidth = Math.max(1f, s * 0.06f); g.strokeStyle = css(lighten(c, 0.65f), 0.9f); g.stroke()
    }
    g.restore()
}

fun swirl(g: Ctx2D, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, a0: Float, a1: Float, c: Rgb, lw: Float, blur: Float) {
    g.save(); g.beginPath(); g.ellipse(cx, cy, rx, ry, rot, a0, a1); g.lineCap = "round"
    g.strokeStyle = css(c); g.lineWidth = lw; g.shadowColor = css(c, 0.95f); g.shadowBlur = blur * curK; g.stroke()
    g.shadowBlur = 0f; g.lineWidth = lw * 0.35f; g.strokeStyle = css(lighten(c, 0.75f)); g.stroke(); g.restore()
}

fun glowStroke(g: Ctx2D, c: Rgb, lw: Float, blur: Float, a: Float = 1f) {
    g.save(); g.strokeStyle = css(c, a); g.lineWidth = lw; g.shadowColor = css(c, 1f); g.shadowBlur = blur * curK; g.stroke(); g.restore()
}

fun dotSprite(c: Rgb): Cv {
    val sz = 48
    val s = mk(sz, sz)
    val g = s.g
    val gr = g.createRadialGradient(sz * 0.38f, sz * 0.34f, sz * 0.02f, sz / 2f, sz / 2f, sz * 0.5f)
    gr.addColorStop(0f, css(lighten(c, 0.88f))); gr.addColorStop(0.35f, css(lighten(c, 0.25f))); gr.addColorStop(0.75f, css(c)); gr.addColorStop(1f, css(darken(c, 0.55f)))
    g.fillStyle = gr; g.beginPath(); g.arc(sz / 2f, sz / 2f, sz * 0.47f, 0f, TAU); g.fill()
    return s
}

fun sparkSprite(c: Rgb): Cv {
    val s = mk(128, 128)
    val g = s.g
    g.translate(64f, 64f)
    val gr = g.createRadialGradient(0f, 0f, 0f, 0f, 0f, 64f)
    gr.addColorStop(0f, css(c, 0.55f)); gr.addColorStop(0.25f, css(c, 0.18f)); gr.addColorStop(1f, css(c, 0f))
    g.fillStyle = gr; g.fillRect(-64f, -64f, 128f, 128f)
    g.beginPath()
    for (k in 0 until 8) {
        val r = if (k % 2 == 1) 6f else 56f
        val a = k * PIf / 4f - PIf / 2f
        val px = r * Math.cos(a.toDouble()).toFloat()
        val py = r * Math.sin(a.toDouble()).toFloat()
        if (k > 0) g.lineTo(px, py) else g.moveTo(px, py)
    }
    g.closePath(); g.shadowColor = css(c, 1f); g.shadowBlur = 10f; g.fillStyle = 0xFFFFFFFF.toInt(); g.fill()
    return s
}

// ---------------------------------------------------------------- text

fun drawName(g: Ctx2D, st: EqState, cx: Float, cy: Float, size: Float, maxW: Float) {
    val name = st.name.trim()
    if (name.isEmpty()) return
    g.save(); g.fontFamily = Typeface.SANS_SERIF; g.direction = "rtl"; g.textAlign = "center"; g.textBaseline = "middle"
    var fs = size
    g.setFont(800, fs)
    var w = g.measureText(name).width
    if (w > maxW) { fs = Math.floor((fs * maxW / w).toDouble()).toFloat(); g.setFont(800, fs); w = g.measureText(name).width }
    val dark = darken(mix(st.p("text"), st.p("text2"), 0.5f), 0.62f)
    val step = fs * 0.011f
    g.fillStyle = css(dark)
    for (i in 7 downTo 1) g.fillText(name, cx + i * step * 0.4f, cy + i * step)
    g.shadowColor = css(st.p("text"), 0.85f); g.shadowBlur = fs * 0.22f * curK
    val gr = g.createLinearGradient(cx - w / 2f, 0f, cx + w / 2f, 0f); gr.addColorStop(0f, css(st.p("text"))); gr.addColorStop(1f, css(st.p("text2")))
    g.fillStyle = gr; g.fillText(name, cx, cy); g.shadowBlur = 0f
    g.lineWidth = Math.max(1f, fs * 0.022f); g.strokeStyle = css(lighten(st.p("text"), 0.7f), 0.5f); g.lineJoin = "round"; g.strokeText(name, cx, cy)
    g.restore()
}

class Sprite(val c: Cv, val w: Float, val h: Float)

fun letterSprite(st: EqState, hgt: Float, maxW: Float, c1: Rgb, c2: Rgb? = null, h2: Boolean = false, glitter: Int = 0, outline: Rgb? = null): Sprite? {
    val ch = st.letter.trim()
    if (ch.isEmpty()) return null
    val sc = curK
    val m0 = mk(4, 4).g
    m0.fontFamily = Typeface.SERIF
    var fs = hgt / 0.68f * sc
    m0.setFont(900, fs)
    var m = m0.measureText(ch)
    fun bw() = m.actualBoundingBoxLeft + m.actualBoundingBoxRight
    fun bh() = m.actualBoundingBoxAscent + m.actualBoundingBoxDescent
    val k = Math.min(1f, Math.min(hgt * sc * 1.1f / Math.max(1f, bh()), maxW * sc / Math.max(1f, bw())))
    if (k < 1f) { fs *= k; m0.setFont(900, fs); m = m0.measureText(ch) }
    val pad = Math.ceil((fs * 0.12f).toDouble()).toInt()
    val w = Math.ceil((bw() + pad * 2).toDouble()).toInt()
    val h = Math.ceil((bh() + pad * 2).toDouble()).toInt()
    val c = mk(Math.max(2, w), Math.max(2, h))
    val g = c.g
    g.fontFamily = Typeface.SERIF
    g.setFont(900, fs); g.textAlign = "left"; g.textBaseline = "alphabetic"
    val x = pad + m.actualBoundingBoxLeft
    val y = pad + m.actualBoundingBoxAscent
    val gr: Grad
    if (h2 && c2 != null) {
        gr = g.createLinearGradient(pad.toFloat(), 0f, (w - pad).toFloat(), 0f); gr.addColorStop(0f, css(c1)); gr.addColorStop(1f, css(c2))
    } else {
        gr = g.createLinearGradient(0f, pad.toFloat(), 0f, (h - pad).toFloat())
        gr.addColorStop(0f, css(lighten(c1, 0.55f))); gr.addColorStop(0.45f, css(c1)); gr.addColorStop(0.55f, css(darken(c1, 0.18f))); gr.addColorStop(1f, css(lighten(c1, 0.25f)))
    }
    g.fillStyle = gr; g.fillText(ch, x, y)
    if (glitter > 0) {
        g.globalCompositeOperation = "source-atop"
        val r = mulberry(11)
        for (i in 0 until glitter) {
            val px = r() * w
            val py = r() * h
            val pr = (0.6f + r() * 1.8f) * sc
            g.fillStyle = if (r() < 0.5f) Color.argb(217, 255, 255, 255) else css(darken(c1, 0.5f), 0.5f)
            g.beginPath(); g.arc(px, py, pr, 0f, TAU); g.fill()
        }
        g.globalCompositeOperation = "source-over"
    }
    if (outline != null) {
        g.globalCompositeOperation = "destination-over"; g.lineJoin = "round"; g.lineWidth = fs * 0.05f; g.strokeStyle = css(outline); g.strokeText(ch, x, y); g.globalCompositeOperation = "source-over"
    }
    return Sprite(c, w / sc, h / sc)
}

fun putSprite(g: Ctx2D, sp: Sprite?, cx: Float, cy: Float, glow: Rgb? = null, blur: Float = 0f) {
    if (sp == null) return
    g.save()
    if (glow != null) { g.shadowColor = css(glow, 0.9f); g.shadowBlur = blur * curK }
    g.drawImage(sp.c, cx - sp.w / 2f, cy - sp.h / 2f, sp.w, sp.h)
    g.restore()
}

class HeartOpts(
    val lh: Float, val ly: Float = 0.45f, val c1: Rgb, val glitter: Int = 0, val glow: Rgb? = null, val blur: Float = 14f,
    val outline: Rgb? = null, val edge: Rgb? = null
)

fun heartContent(g: Ctx2D, st: EqState, cx: Float, top: Float, w: Float, h: Float, o: HeartOpts) {
    val P = st.P
    if (st.heartMode == "photo" && st.photo != null) {
        g.save(); heartPath(g, cx, top, w, h); g.clip(); cover(g, st.photo, cx - w / 2f, top, w, h); g.restore()
        g.save(); heartPath(g, cx, top, w, h); g.lineWidth = 3f; g.strokeStyle = css(lighten(o.edge ?: st.p("main"), 0.6f), 0.85f); g.stroke(); g.restore()
        return
    }
    putSprite(g, letterSprite(st, o.lh, w * 0.62f, o.c1, glitter = o.glitter, outline = o.outline), cx, top + h * o.ly, o.glow, o.blur)
}

// ---------------------------------------------------------------- player and bars

fun icoPrev(g: Ctx2D, x: Float, y: Float, s: Float) {
    g.beginPath(); g.moveTo(x + s * 0.55f, y - s * 0.62f); g.lineTo(x - s * 0.35f, y); g.lineTo(x + s * 0.55f, y + s * 0.62f); g.closePath(); g.fill()
    g.fillRect(x - s * 0.7f, y - s * 0.62f, s * 0.18f, s * 1.24f)
}

fun icoNext(g: Ctx2D, x: Float, y: Float, s: Float) {
    g.beginPath(); g.moveTo(x - s * 0.55f, y - s * 0.62f); g.lineTo(x + s * 0.35f, y); g.lineTo(x - s * 0.55f, y + s * 0.62f); g.closePath(); g.fill()
    g.fillRect(x + s * 0.52f, y - s * 0.62f, s * 0.18f, s * 1.24f)
}

fun playerStatic(g: Ctx2D, st: EqState, T: EqTpl) {
    val pr = T.prog
    val ct = T.ctrl
    g.save(); g.lineCap = "round"; g.strokeStyle = css(st.p(pr.ga), 0.28f); g.lineWidth = pr.lw; g.beginPath(); g.moveTo(pr.x0, pr.y); g.lineTo(pr.x1, pr.y); g.stroke()
    g.shadowColor = css(st.p("ui"), 0.8f); g.shadowBlur = 10 * curK; g.fillStyle = css(st.p("ui")); g.strokeStyle = css(st.p("ui"))
    val s = ct.r * 0.55f
    icoPrev(g, ct.cx - ct.gap, ct.cy, s); icoNext(g, ct.cx + ct.gap, ct.cy, s)
    g.lineWidth = Math.max(2f, ct.r * 0.09f); g.beginPath(); g.arc(ct.cx, ct.cy, ct.r, 0f, TAU); g.stroke()
    g.fillRect(ct.cx - s * 0.42f, ct.cy - s * 0.62f, s * 0.28f, s * 1.24f); g.fillRect(ct.cx + s * 0.14f, ct.cy - s * 0.62f, s * 0.28f, s * 1.24f)
    g.restore()
}

fun playerDyn(g: Ctx2D, T: EqTpl, st: EqState, d: EqData) {
    val pr = T.prog
    val tm = T.time
    val f = if (d.dur > 0f) Math.min(1f, d.t / d.dur) else 0f
    val x = pr.x0 + (pr.x1 - pr.x0) * f
    g.lineCap = "round"
    if (f > 0f) {
        val gr = g.createLinearGradient(pr.x0, 0f, pr.x1, 0f); gr.addColorStop(0f, css(st.p(pr.ga))); gr.addColorStop(1f, css(st.p(pr.gb)))
        g.strokeStyle = gr; g.lineWidth = pr.lw; g.beginPath(); g.moveTo(pr.x0, pr.y); g.lineTo(x, pr.y); g.stroke()
    }
    g.fillStyle = css(st.p(pr.ga), 0.28f); g.beginPath(); g.arc(x, pr.y, pr.r * 1.9f, 0f, TAU); g.fill()
    g.fillStyle = css(lighten(st.p(pr.ga), 0.7f)); g.beginPath(); g.arc(x, pr.y, pr.r, 0f, TAU); g.fill()
    g.fontFamily = Typeface.SANS_SERIF
    g.setFont(700, tm.size); g.textBaseline = "middle"; g.direction = "ltr"
    g.textAlign = "left"; g.fillStyle = css(st.p(tm.ca)); g.fillText(fmt2(d.t), tm.lx, tm.y)
    g.textAlign = "right"; g.fillStyle = css(st.p(tm.cb)); g.fillText(fmt2(d.dur), tm.rx, tm.y)
}

fun colAt(dd: Float, i: Int, style: String, st: EqState): Rgb {
    if (style == "solid") return st.p("bar2")
    if (style == "alt") return if (dd < 0.1f) st.p("bar3") else if (((i shr 1) and 1) == 1) st.p("bar1") else st.p("bar2")
    return if (dd < 0.5f) mix(st.p("bar3"), st.p("bar2"), dd / 0.5f) else mix(st.p("bar2"), st.p("bar1"), (dd - 0.5f) / 0.5f)
}

fun fadeE(e: Float, f: Float): Float {
    if (f == 0f) return 1f
    val w = f / 100f * 0.5f
    val t = Math.min(1f, e / w)
    return t * t * (3 - 2 * t)
}

fun drawBars(g: Ctx2D, T: EqTpl, st: EqState, d: EqData, B: BarsP) {
    val n = B.n
    val lv = d.lv
    val bw = B.w
    for (i in 0 until n) {
        val u = (i + 0.5f) / n
        val dd = if (u < B.cen) (B.cen - u) / B.cen else (u - B.cen) / (1 - B.cen)
        val bi = Math.min((NBAND - 1).toFloat(), dd * 58f)
        val i0 = Math.floor(bi.toDouble()).toInt()
        val i1 = Math.min(NBAND - 1, i0 + 1)
        val f = bi - i0
        val level = lv[i0] * (1 - f) + lv[i1] * f
        val env = B.envMin + (1 - B.envMin) * Math.exp(-Math.pow((dd * B.envK).toDouble(), 2.0)).toFloat()
        var h = B.maxH * env * (0.05f + 0.95f * level) * st.barScale * B.jit[i]
        h = Math.min(h, B.maxH * 1.15f)
        if (h < 3f) h = 3f
        val x = B.x0 + (i + 0.5f) * (B.x1 - B.x0) / n - bw / 2f
        val c = colAt(dd, i, st.barStyle, st)
        val fe = fadeE(if (B.side == "left") u else if (B.side == "right") 1 - u else Math.min(u, 1 - u), st.fade)
        g.globalAlpha = fe
        if (fe < 0.02f) continue
        if (B.halo) {
            g.globalCompositeOperation = "lighter"; g.fillStyle = css(c, 0.16f); g.fillRect(x - bw * 0.55f, B.base - h - bw * 0.5f, bw * 2.1f, h + bw * 0.5f); g.globalCompositeOperation = "source-over"
        }
        g.fillStyle = css(c); rr(g, x, B.base - h, bw, h, bw * 0.3f); g.fill()
        g.fillStyle = css(lighten(c, 0.55f), 0.5f); g.fillRect(x, B.base - h, bw, Math.min(h, Math.max(bw, h * 0.3f)))
        if (B.mirror) { g.fillStyle = css(c, 0.8f); rr(g, x, B.base + 2f, bw, h, bw * 0.3f); g.fill() }
        if (B.refl > 0f) {
            val rh = h * B.refl
            val seg = 5
            for (s in 0 until seg) { g.fillStyle = css(c, 0.32f * (1 - s.toFloat() / seg)); g.fillRect(x, B.base + 2f + s * rh / seg, bw, rh / seg + 0.5f) }
        }
    }
    g.globalAlpha = 1f
}
