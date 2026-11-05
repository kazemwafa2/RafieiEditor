package com.rafiei.editor

import android.graphics.Color

/**
 * Five extra visualizer designs (variants): circular radial bars with mirrored side bars and player bar ("circ"),
 * photo in a frame with waveform and controls ("frame"), ring spectrum ("ring"), mirrored logo with yellow waveform ("mirror"),
 * and a compact DJ player strip ("dj").
 */
class EqViz(id: String, title: String, private val v: String) : EqTpl(id, title) {
    private val R = if (v == "ring") 120f else 135f
    private val CY = if (v == "dj") 360f else 330f

    init {
        labels = mapOf("main" to "رنگ حلقه و قاب", "accent" to "رنگ دوم (درخشش)", "letter" to "رنگ حرف لوگو", "panel" to "رنگ زمینهٔ دیسک",
            "bar1" to "رنگ میله‌ها (لبه)", "bar2" to "رنگ میله‌ها (میانه)", "bar3" to "رنگ موج / میله‌های مرکز", "ui" to "رنگ نوار پخش و زمان")
        val col = when (v) {
            "circ" -> mapOf("main" to "#ff4fb8", "accent" to "#38d9ff", "text" to "#ffffff", "text2" to "#ffd6f2", "letter" to "#ffffff", "panel" to "#0b0818",
                "bar1" to "#38d9ff", "bar2" to "#ff4fb8", "bar3" to "#ffe36a", "ui" to "#ffffff")
            "frame" -> mapOf("main" to "#f5b82e", "accent" to "#ff8a00", "text" to "#ffffff", "text2" to "#ffd24a", "letter" to "#ffffff", "panel" to "#0c0906",
                "bar1" to "#ff8a00", "bar2" to "#ffd24a", "bar3" to "#ffffff", "ui" to "#ffe08a")
            "ring" -> mapOf("main" to "#3ee0ff", "accent" to "#6a5cff", "text" to "#ffffff", "text2" to "#bfe9ff", "letter" to "#ffffff", "panel" to "#050a14",
                "bar1" to "#6a5cff", "bar2" to "#3ee0ff", "bar3" to "#ffffff", "ui" to "#bfe9ff")
            "mirror" -> mapOf("main" to "#d4161c", "accent" to "#19c83a", "text" to "#ffffff", "text2" to "#ffffff", "letter" to "#ffffff", "panel" to "#0a0a0a",
                "bar1" to "#ffe21c", "bar2" to "#f7d11a", "bar3" to "#ffffff", "ui" to "#ffffff")
            else -> mapOf("main" to "#ff3b2e", "accent" to "#ff9a1a", "text" to "#ffffff", "text2" to "#ffd24a", "letter" to "#ffffff", "panel" to "#120808",
                "bar1" to "#ff3b2e", "bar2" to "#ff9a1a", "bar3" to "#ffe03a", "ui" to "#ffe08a")
        }
        def = EqDef(col, true, "solid", true, true, 0f, v == "mirror", floatArrayOf(0f, 0f, 90f))
        val py = if (v == "dj") 450f else 600f
        prog = Prog(260f, 1020f, py, "main", "accent", 5f, 10f)
        time = TimeP(py + 34f, 260f, 1020f, 24f)
        ctrl = Ctrl(640f, py + 70f, 70f, 22f)
    }

    private fun disc(st: EqState, r: Float, square: Boolean): Cv {
        val D = (2 * (r + 30f)).toInt()
        val cv = mk(D, D)
        val g = cv.g
        g.translate(D / 2f, D / 2f)
        g.beginPath()
        if (square) rr(g, -r, -r, 2 * r, 2 * r, r * 0.16f) else g.arc(0f, 0f, r, 0f, TAU)
        g.fillStyle = css(st.p("panel")); g.shadowColor = Color.argb(204, 0, 0, 0); g.shadowBlur = 18f; g.fill(); g.shadowBlur = 0f
        if (st.heartMode == "photo" && st.photo != null) {
            g.save(); g.beginPath()
            if (square) rr(g, -r + 6f, -r + 6f, 2 * r - 12f, 2 * r - 12f, r * 0.12f) else g.arc(0f, 0f, r - 6f, 0f, TAU)
            g.clip(); cover(g, st.photo, -r, -r, 2 * r, 2 * r); g.restore()
        } else {
            val sp = letterSprite(st, r * 1.1f, r * 1.5f, st.p("letter"), null, false, 0, darken(st.p("main"), 0.5f))
            putSprite(g, sp, 0f, 0f, st.p("main"), 14f)
        }
        g.beginPath()
        if (square) rr(g, -r, -r, 2 * r, 2 * r, r * 0.16f) else g.arc(0f, 0f, r, 0f, TAU)
        g.lineWidth = 6f; g.strokeStyle = css(st.p("main")); g.shadowColor = css(st.p("accent"), 0.9f); g.shadowBlur = 14f; g.stroke()
        return cv
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        L.over = mk(EQ_W, EQ_H)
        val og = L.over!!.g
        if (st.showName) drawLabel(og, st, 640f, if (v == "dj") 120f else 80f, 34f, 760f)
        if (st.showPlayer) playerStatic(og, st, this)
        L.disc = disc(st, if (v == "frame") 190f else R, v == "frame")
        return L
    }

    private fun bar(g: Ctx2D, st: EqState, d: EqData, x: Float, y: Float, a: Float, len: Float, w: Float, idx: Float) {
        val c = mix(st.p("bar1"), st.p("bar2"), idx)
        g.save(); g.translate(x, y); g.rotate(a)
        g.fillStyle = css(c); g.shadowColor = css(c, 0.8f); g.shadowBlur = 6f
        g.beginPath(); rr(g, 0f, -w / 2f, Math.max(2f, len), w, w / 2f); g.fill()
        g.restore()
    }

    private fun lvAt(d: EqData, u: Float): Float {
        val n = d.lv.size
        val i = Math.min(n - 1, Math.max(0, (u * (n - 1)).toInt()))
        return clampF(d.lv[i], 0f, 1.2f)
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val sc = 1f + 0.035f * Math.pow(d.bass.toDouble(), 1.5).toFloat()
        val D = L.disc!!.w.toFloat()
        val bs = st.barScale
        when (v) {
            "circ", "ring" -> {
                val n = if (v == "ring") 96 else 72
                val inner = R + (if (v == "ring") 16f else 24f)
                for (i in 0 until n) {
                    val u = i.toFloat() / n
                    val mirrorU = if (u < 0.5f) u * 2f else (1f - u) * 2f
                    val lv = lvAt(d, mirrorU)
                    val len = (8f + lv * (if (v == "ring") 70f else 95f)) * bs
                    bar(g, st, d, 640f + Math.cos((u * TAU - TAU / 4).toDouble()).toFloat() * inner, CY + Math.sin((u * TAU - TAU / 4).toDouble()).toFloat() * inner, u * TAU - TAU / 4, len, if (v == "ring") 5f else 7f, mirrorU)
                }
                if (v == "circ") {
                    // mirrored vertical side bars
                    for (side in intArrayOf(-1, 1)) for (i in 0 until 14) {
                        val lv = lvAt(d, (i / 14f) * 0.8f)
                        val h = (14f + lv * 150f) * bs
                        val x = 640f + side * (330f + i * 17f)
                        val c = mix(st.p("bar1"), st.p("bar2"), i / 14f)
                        g.fillStyle = css(c); g.shadowColor = css(c, 0.8f); g.shadowBlur = 6f
                        g.beginPath(); rr(g, x - 5f, CY - h / 2f, 10f, h, 5f); g.fill()
                    }
                }
                g.shadowBlur = 0f
                g.save(); g.translate(640f, CY); g.scale(sc, sc); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
            }
            "frame" -> {
                g.save(); g.translate(640f, CY - 20f); g.scale(sc, sc); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
                val n = 44
                for (i in 0 until n) {
                    val u = i.toFloat() / (n - 1)
                    val lv = lvAt(d, if (u < 0.5f) u * 2f else (1f - u) * 2f)
                    val h = (6f + lv * 70f) * bs
                    val x = 260f + u * 760f
                    val c = mix(st.p("bar2"), st.p("bar1"), u)
                    g.fillStyle = css(c); g.beginPath(); rr(g, x - 6f, 560f - h, 12f, h, 5f); g.fill()
                }
            }
            "mirror" -> {
                val x0 = 300f
                val x1 = 980f
                val M = 200
                for (side in intArrayOf(-1, 1)) {
                    g.beginPath()
                    for (i in 0..M) {
                        val u = i.toFloat() / M
                        val tp = Math.pow(Math.sin((PIf * u).toDouble()), 0.6).toFloat()
                        val w = (d.lv[0] * Math.sin((u * 38f + d.now * 7f).toDouble()).toFloat() + d.lv[Math.min(d.lv.size - 1, 10)] * 0.7f * Math.sin((u * 115f - d.now * 10f).toDouble()).toFloat()) * 0.9f
                        val x = x0 + u * (x1 - x0)
                        val y = (CY + 150f) + side * Math.max(-1.4f, Math.min(1.4f, w)) * 48f * bs * tp
                        if (i > 0) g.lineTo(x, y) else g.moveTo(x, y)
                    }
                    g.lineJoin = "round"; g.lineCap = "round"; g.strokeStyle = css(st.p("bar1")); g.lineWidth = 3.4f
                    g.shadowColor = css(st.p("bar1"), 0.9f); g.shadowBlur = 10f; g.stroke()
                }
                g.shadowBlur = 0f
                g.save(); g.translate(640f, CY - 40f); g.scale(sc, sc); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
            }
            else -> { // dj strip
                g.save(); g.translate(300f, CY); g.scale(sc, sc); g.scale(0.62f, 0.62f); g.drawImage(L.disc, -D / 2f, -D / 2f); g.restore()
                val n = 56
                for (i in 0 until n) {
                    val u = i.toFloat() / (n - 1)
                    val lv = lvAt(d, u * 0.85f)
                    val h = (8f + lv * 120f) * bs
                    val c = mix(st.p("bar1"), st.p("bar3"), u)
                    g.fillStyle = css(c); g.shadowColor = css(c, 0.7f); g.shadowBlur = 5f
                    g.beginPath(); rr(g, 420f + i * 10.8f - 3f, CY - h / 2f, 6f, h, 3f); g.fill()
                }
                g.shadowBlur = 0f
            }
        }
        drawTimeLabel(g, st, d.t, 640f, if (v == "dj") 540f else 660f, 26f)
        g.drawImage(L.over, 0f, 0f)
        if (st.showPlayer) playerDyn(g, this, st, d)
    }
}

fun eqVizTemplates(): List<EqTpl> = listOf(
    EqViz("vizcirc", "دایره با میله‌های آینه‌ای + نوار پخش", "circ"),
    EqViz("vizframe", "قاب عکس با موج و کنترل", "frame"),
    EqViz("vizring", "حلقه اسپکتروم", "ring"),
    EqViz("vizmirror", "لوگوی مرکزی با موج زرد آینه‌ای", "mirror"),
    EqViz("vizdj", "پلیر فشرده DJ", "dj")
)
