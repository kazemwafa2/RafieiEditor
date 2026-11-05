package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface

/** Hand-drawn equalizer designs (royal, gold fire, neon love, gold, royal gold heart, Jumong crow, classic photo frame). */

fun eqLayeredTemplates(): List<EqTpl> = listOf(EqRoyal(), EqFire(), EqNeon(), EqGold(), EqCrest(), EqJumong(), EqClassic())

private fun cs(x: Double): Float = x.toFloat()
private fun sinf(x: Float): Float = Math.sin(x.toDouble()).toFloat()
private fun cosf(x: Float): Float = Math.cos(x.toDouble()).toFloat()
private fun powf(x: Float, y: Float): Float = Math.pow(x.toDouble(), y.toDouble()).toFloat()
private fun col(h: String): Rgb = hex2rgb(h)
private val WHITE_A85 = Color.argb(217, 255, 255, 255)

private fun pillFrame(g: Ctx2D, st: EqState, x: Float, y: Float, w: Float, h: Float) {
    val main = st.p("main")
    g.save(); rr(g, x, y, w, h, h / 2f)
    var gr = g.createLinearGradient(0f, y, 0f, y + h)
    gr.addColorStop(0f, css(mix(st.p("panel"), st.p("accent"), 0.16f))); gr.addColorStop(0.5f, css(st.p("panel"))); gr.addColorStop(1f, css(mix(st.p("panel"), st.p("accent"), 0.1f)))
    g.fillStyle = gr; g.shadowColor = css(main, 0.9f); g.shadowBlur = 30 * curK; g.fill(); g.shadowBlur = 0f
    g.lineWidth = 8f; g.strokeStyle = metal(g, x, y, x + w, y + h, main); g.shadowColor = css(main, 1f); g.shadowBlur = 22 * curK; g.stroke(); g.shadowBlur = 0f
    g.lineWidth = 2.5f; g.strokeStyle = css(lighten(main, 0.7f), 0.85f); rr(g, x + 12f, y + 12f, w - 24f, h - 24f, h / 2f - 12f); g.stroke(); g.restore()
}

private fun dottedHeart(g: Ctx2D, st: EqState, cx: Float, top: Float, w: Float, h: Float, crownW: Float, lh: Float) {
    val main = st.p("main")
    g.save(); heartPath(g, cx, top, w, h); g.fillStyle = css(darken(main, 0.8f)); g.shadowColor = css(main, 0.9f); g.shadowBlur = 26 * curK; g.fill(); g.restore()
    g.save(); heartPath(g, cx, top, w, h); g.clip()
    val spr = dotSprite(main)
    val d = w / 15.5f
    val rh = d * 0.88f
    var r = -1
    var y = top - d
    while (y < top + h + d) {
        val off = if ((r and 1) != 0) d / 2f else 0f
        var x = cx - w / 2f - d + off
        while (x < cx + w / 2f + d) {
            g.drawImage(spr, x - d * 0.53f, y - d * 0.53f, d * 1.06f, d * 1.06f)
            x += d
        }
        r++; y += rh
    }
    g.restore()
    val iw = w * 0.7f
    val ih = h * 0.6f
    val it = top + h * 0.1f
    g.save(); heartPath(g, cx, it, iw, ih)
    val gr = g.createRadialGradient(cx, it + ih * 0.45f, 10f, cx, it + ih * 0.45f, iw * 0.6f)
    gr.addColorStop(0f, css(darken(st.p("panel"), 0f))); gr.addColorStop(1f, css(mix(st.p("panel"), st.p("accent"), 0.22f)))
    g.fillStyle = gr; g.fill()
    g.lineWidth = 3f; g.strokeStyle = css(lighten(main, 0.5f), 0.7f); g.stroke(); g.restore()
    heartContent(g, st, cx, it, iw, ih, HeartOpts(lh = lh, ly = 0.45f, c1 = st.p("letter"), glitter = 520, glow = main, blur = 14f, outline = darken(st.p("letter"), 0.55f), edge = main))
    heartPath(g, cx, top, w, h); glowStroke(g, lighten(main, 0.6f), 4f, 18f, 0.95f)
    crown(g, cx, top + h * 0.13f, crownW, 0.62f, st.P)
}

private fun mkDef(cols: Map<String, String>, showName: Boolean = true, barStyle: String = "grad") = EqDef(cols, showName = showName, barStyle = barStyle)

// ======================================================= royal
private class EqRoyal : EqLayered("royal", "سلطنتی نئونی") {
    init {
        k = 0.766f; ox = 0f; oy = 30f; hc = floatArrayOf(460f, 520f)
        def = mkDef(mapOf("main" to "#f6b92a", "accent" to "#a43cff", "text" to "#ffc233", "text2" to "#f09a12", "letter" to "#f2c04a", "panel" to "#07070e", "bar1" to "#1fb7ff", "bar2" to "#ff35d0", "bar3" to "#ffae1a", "ui" to "#ffd36b"))
        bars = mkBars(735f, 1640f, 435f, 232f, 58, 9f, cen = 0.46f, refl = 0.28f)
        prog = Prog(660f, 1445f, 630f, "accent", "bar1", 5f, 11f); time = TimeP(668f, 658f, 1448f, 30f); ctrl = Ctrl(1040f, 668f, 100f, 38f)
        sparks = listOf(floatArrayOf(462f, 100f, 44f), floatArrayOf(262f, 440f, 30f), floatArrayOf(665f, 430f, 30f), floatArrayOf(460f, 742f, 38f), floatArrayOf(1155f, 430f, 44f),
            floatArrayOf(1395f, 322f, 30f), floatArrayOf(890f, 265f, 26f), floatArrayOf(130f, 395f, 26f), floatArrayOf(1500f, 420f, 30f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        g.save(); g.beginPath(); g.ellipse(860f, 485f, 790f, 255f, 0f, 0f, TAU)
        var gr = g.createLinearGradient(0f, 230f, 0f, 740f)
        gr.addColorStop(0f, css(mix(st.p("panel"), st.p("bar1"), 0.2f), 0.35f)); gr.addColorStop(0.5f, css(mix(st.p("panel"), st.p("accent"), 0.25f), 0.8f)); gr.addColorStop(1f, css(st.p("panel"), 0.97f))
        g.fillStyle = gr; g.fill()
        gr = g.createLinearGradient(50f, 0f, 1650f, 0f)
        val cl = listOf(st.p("accent"), st.p("bar1"), st.p("bar2"), st.p("bar3"), st.p("bar2"), st.p("bar1"), st.p("accent"))
        for ((i, c) in cl.withIndex()) gr.addColorStop(i.toFloat() / (cl.size - 1), css(c))
        g.strokeStyle = gr; g.lineWidth = 7f; g.shadowColor = css(st.p("accent"), 0.9f); g.shadowBlur = 26 * curK; g.stroke(); g.shadowBlur = 0f
        g.lineWidth = 2.5f; g.globalAlpha = 0.75f; g.beginPath(); g.ellipse(850f, 480f, 830f, 285f, -0.02f, 0f, TAU); g.stroke()
        g.beginPath(); g.ellipse(870f, 500f, 770f, 235f, 0.03f, 0f, TAU); g.strokeStyle = css(st.p("bar2"), 0.8f); g.stroke(); g.restore()
        g.save(); g.lineCap = "round"; gr = g.createLinearGradient(735f, 0f, 1640f, 0f); gr.addColorStop(0f, css(st.p("bar1"))); gr.addColorStop(0.5f, css(st.p("bar3"))); gr.addColorStop(1f, css(st.p("bar1")))
        g.strokeStyle = gr; g.lineWidth = 3f; g.shadowColor = css(st.p("bar3"), 0.9f); g.shadowBlur = 14 * curK; g.beginPath(); g.moveTo(735f, 437f); g.lineTo(1640f, 437f); g.stroke(); g.restore()
        if (st.fx) {
            swirl(g, 170f, 520f, 170f, 150f, 0.6f, 2.4f, 5.2f, st.p("accent"), 5f, 18f)
            swirl(g, 150f, 560f, 150f, 110f, 0.2f, 1.5f, 4.0f, st.p("bar2"), 4f, 16f)
            swirl(g, 1500f, 560f, 170f, 110f, -0.2f, -1f, 1.8f, st.p("bar3"), 4f, 16f)
        }
    }

    override fun heartL(g: Ctx2D, st: EqState) {
        val P = st.p("main")
        val cx = 460f
        val gold = goldFill(g, 200f, 300f, 720f, 760f, P)
        scroll(g, 300f, 640f, 230f, -1f, gold, 15f); scroll(g, 620f, 640f, 230f, 1f, gold, 15f); scroll(g, 305f, 430f, 170f, -1f, gold, 13f); scroll(g, 615f, 430f, 170f, 1f, gold, 13f)
        laurel(g, cx, 500f, 232f, 118f, 242f, 13, 56f, 16f, gold); laurel(g, cx, 500f, 232f, 62f, -62f, 13, 56f, 16f, gold)
        g.save(); heartPath(g, cx, 300f, 420f, 455f); g.shadowColor = css(P, 0.95f); g.shadowBlur = 24 * curK
        var gr = g.createRadialGradient(cx - 70f, 400f, 20f, cx, 520f, 300f)
        gr.addColorStop(0f, css(mix(st.p("panel"), intArrayOf(90, 90, 110), 0.35f))); gr.addColorStop(0.5f, css(st.p("panel"))); gr.addColorStop(1f, css(darken(st.p("panel"), 0.3f)))
        g.fillStyle = gr; g.fill(); g.shadowBlur = 0f; g.lineWidth = 26f; g.strokeStyle = metal(g, 230f, 300f, 690f, 760f, P); g.stroke(); g.restore()
        g.save(); heartPath(g, cx, 314f, 392f, 426f); g.lineWidth = 3f; g.strokeStyle = css(lighten(P, 0.75f), 0.85f); g.stroke(); g.restore()
        g.save(); g.globalAlpha = 0.16f; g.fillStyle = css(WHITE); g.beginPath(); g.ellipse(375f, 385f, 95f, 55f, -0.5f, 0f, TAU); g.fill(); g.restore()
        heartContent(g, st, cx, 330f, 375f, 405f, HeartOpts(lh = 200f, ly = 0.4f, c1 = st.p("letter"), glitter = 420, glow = P, blur = 16f, outline = darken(st.p("letter"), 0.55f), edge = P))
        crown(g, cx, 352f, 290f, 0.64f, st.P)
        if (st.heartMode != "photo" || st.photo == null) crown(g, cx, 382f, 135f, 0.45f, st.P)
        if (st.fx) {
            val gc = intArrayOf(220, 245, 255)
            gem(g, 225f, 540f, 13f, gc); gem(g, 690f, 580f, 12f, gc); gem(g, 300f, 250f, 11f, gc)
            note(g, 70f, 262f, 38f, P, -0.3f); note(g, 86f, 640f, 42f, P, 0.1f); note(g, 1475f, 205f, 42f, P, 0.3f); note(g, 1565f, 585f, 42f, P, 0.2f); note(g, 1500f, 650f, 36f, P, 0.1f); note(g, 550f, 690f, 30f, P, -0.2f)
            miniHeart(g, 862f, 250f, 56f, P, false); miniHeart(g, 1562f, 320f, 36f, P, false)
        }
    }

    override fun over(g: Ctx2D, st: EqState) {
        val P = st.p("main")
        val gold = goldFill(g, 820f, 480f, 1560f, 600f, P)
        scroll(g, 880f, 545f, 150f, -1f, gold, 10f); scroll(g, 1385f, 545f, 150f, 1f, gold, 10f)
        if (st.showName) drawName(g, st, 1085f, 520f, 118f, 500f)
        g.save(); rr(g, 620f, 605f, 860f, 108f, 54f); g.fillStyle = css(st.p("panel"), 0.96f); g.shadowColor = css(P, 0.9f); g.shadowBlur = 22 * curK; g.fill(); g.shadowBlur = 0f
        g.lineWidth = 6f; g.strokeStyle = metal(g, 620f, 605f, 1480f, 713f, P); g.stroke()
        g.lineWidth = 2f; g.strokeStyle = css(st.p("accent"), 0.8f); rr(g, 632f, 617f, 836f, 84f, 42f); g.stroke(); g.restore()
        playerStatic(g, st, this)
    }
}

// ======================================================= gold fire
private class EqFire : EqLayered("fire", "آتش طلایی") {
    init {
        k = 0.602f; ox = 10f; oy = 140f; hc = floatArrayOf(375f, 420f)
        def = mkDef(mapOf("main" to "#ffc21a", "accent" to "#ff7a00", "text" to "#ffc21a", "text2" to "#ff8a10", "letter" to "#fff0a0", "panel" to "#120707", "bar1" to "#e8281e", "bar2" to "#ff9a1a", "bar3" to "#ffe033", "ui" to "#ffe08a"))
        bars = mkBars(690f, 1880f, 350f, 290f, 50, 13f, cen = 0.49f, refl = 0.3f)
        prog = Prog(690f, 1780f, 552f, "main", "bar3", 5f, 15f); time = TimeP(598f, 690f, 1778f, 42f); ctrl = Ctrl(1218f, 608f, 120f, 42f)
        sparks = listOf(floatArrayOf(380f, 40f, 50f), floatArrayOf(130f, 320f, 34f), floatArrayOf(600f, 330f, 34f), floatArrayOf(1270f, 70f, 44f), floatArrayOf(1850f, 300f, 36f),
            floatArrayOf(1980f, 420f, 34f), floatArrayOf(660f, 640f, 30f), floatArrayOf(375f, 640f, 34f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        pillFrame(g, st, 520f, 232f, 1520f, 428f)
        g.save(); g.strokeStyle = css(st.p("bar1"), 0.7f); g.setLineDash(floatArrayOf(2f, 6f)); g.lineWidth = 2.5f; g.beginPath(); g.moveTo(690f, 352f); g.lineTo(1880f, 352f); g.stroke(); g.restore()
        if (st.fx) {
            swirl(g, 350f, 430f, 330f, 200f, -0.15f, 0.3f, 5.6f, st.p("main"), 5f, 22f); swirl(g, 360f, 410f, 300f, 235f, 0.25f, 3.6f, 8.6f, st.p("accent"), 4f, 18f)
            swirl(g, 1925f, 440f, 150f, 215f, 0f, -1.6f, 1.7f, st.p("main"), 5f, 22f); swirl(g, 1890f, 450f, 175f, 190f, 0.3f, -1.9f, 2f, st.p("bar3"), 3f, 16f)
            swirl(g, 120f, 420f, 110f, 170f, 0f, 1.5f, 4.7f, st.p("main"), 3f, 14f)
        }
    }

    override fun heartL(g: Ctx2D, st: EqState) {
        val P = st.p("main")
        dottedHeart(g, st, 375f, 205f, 490f, 440f, 235f, 215f)
        if (st.fx) { miniHeart(g, 927f, 205f, 40f, P, false); miniHeart(g, 1103f, 180f, 42f, P, false); miniHeart(g, 1470f, 163f, 40f, P, false); miniHeart(g, 1798f, 265f, 46f, P, false) }
    }

    override fun over(g: Ctx2D, st: EqState) {
        val gold = goldFill(g, 700f, 430f, 900f, 520f, st.p("main"))
        scroll(g, 900f, 498f, 190f, -1f, gold, 9f); scroll(g, 1590f, 498f, 190f, 1f, gold, 9f)
        if (st.showName) drawName(g, st, 1245f, 448f, 128f, 600f)
        playerStatic(g, st, this)
    }
}

// ======================================================= neon love
private class EqNeon : EqLayered("neon", "نئون عاشقانه") {
    init {
        k = 0.651f; ox = 10f; oy = 103f; hc = floatArrayOf(505f, 390f)
        def = mkDef(mapOf("main" to "#ff3cc8", "accent" to "#28d8ff", "text" to "#ff52d0", "text2" to "#36dcff", "letter" to "#ff9ae8", "panel" to "#070614", "bar1" to "#2fd8ff", "bar2" to "#ff3cd2", "bar3" to "#b266ff", "ui" to "#e9a6ff"), barStyle = "alt")
        bars = mkBars(20f, 1915f, 400f, 215f, 78, 13f, cen = 0.6f, envK = 1.7f, envMin = 0.18f, refl = 0.4f)
        prog = Prog(605f, 1525f, 562f, "main", "accent", 7f, 15f); time = TimeP(612f, 607f, 1522f, 40f, "main", "accent"); ctrl = Ctrl(1058f, 632f, 140f, 50f)
        sparks = listOf(floatArrayOf(360f, 215f, 40f), floatArrayOf(670f, 300f, 34f), floatArrayOf(505f, 640f, 44f), floatArrayOf(1160f, 215f, 36f), floatArrayOf(1500f, 330f, 30f),
            floatArrayOf(255f, 480f, 28f), floatArrayOf(800f, 470f, 28f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        g.save(); g.lineCap = "round"
        val gr = g.createLinearGradient(20f, 0f, 1915f, 0f); gr.addColorStop(0f, css(st.p("main"))); gr.addColorStop(0.5f, css(st.p("bar3"))); gr.addColorStop(1f, css(st.p("accent")))
        g.strokeStyle = gr; g.lineWidth = 5f; g.shadowColor = css(st.p("main"), 0.9f); g.shadowBlur = 20 * curK
        g.beginPath(); g.moveTo(18f, 402f); g.bezierCurveTo(200f, 432f, 380f, 540f, 490f, 640f); g.stroke()
        g.beginPath(); g.moveTo(790f, 478f); g.bezierCurveTo(1100f, 545f, 1500f, 545f, 1905f, 402f); g.stroke()
        g.lineWidth = 2f; g.globalAlpha = 0.7f; g.beginPath(); g.moveTo(800f, 500f); g.bezierCurveTo(1100f, 570f, 1500f, 560f, 1900f, 415f); g.stroke(); g.restore()
    }

    override fun heartL(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        val acc = st.p("accent")
        val cx = 505f
        if (st.fx) { swirl(g, 490f, 395f, 345f, 105f, -0.28f, 3.3f, 6.1f, main, 4f, 16f); swirl(g, 520f, 395f, 345f, 105f, -0.28f, 0.1f, 3.3f, acc, 4f, 16f) }
        g.save(); heartPath(g, cx, 135f, 520f, 505f)
        val gr = g.createRadialGradient(cx, 380f, 20f, cx, 380f, 330f)
        gr.addColorStop(0f, css(darken(st.p("panel"), 0f), 0.97f)); gr.addColorStop(1f, css(mix(st.p("panel"), st.p("bar3"), 0.16f), 0.97f))
        g.fillStyle = gr; g.fill(); g.clip()
        var y = 150f
        var row = 0
        while (y < 640f) {
            var x = 260f
            while (x < 760f) {
                val dx = (x - cx) / 250f
                val dy = (y - 380f) / 250f
                val dd = Math.hypot(dx.toDouble(), (dy * 0.9f).toDouble()).toFloat()
                val a = Math.min(0.9f, Math.max(0f, dd - 0.25f) * 1.3f)
                if (a >= 0.05f) {
                    g.fillStyle = css(lighten(if (x < cx) main else acc, 0.15f), a)
                    g.beginPath(); g.arc(x + (if ((row and 1) != 0) 7f else 0f), y, 3.2f * (0.6f + dd * 0.6f), 0f, TAU); g.fill()
                }
                x += 15f
            }
            y += 15f; row++
        }
        g.restore()
        fun lg(): Grad {
            val q = g.createLinearGradient(245f, 0f, 770f, 0f)
            q.addColorStop(0f, css(main)); q.addColorStop(0.49f, css(main)); q.addColorStop(0.51f, css(acc)); q.addColorStop(1f, css(acc)); return q
        }
        g.save(); heartPath(g, cx, 135f, 520f, 505f); g.lineWidth = 18f; g.globalAlpha = 0.35f; g.strokeStyle = lg(); g.shadowColor = css(st.p("bar3"), 1f); g.shadowBlur = 34 * curK; g.stroke()
        g.globalAlpha = 1f; g.lineWidth = 8f; g.shadowBlur = 16 * curK; g.shadowColor = css(main, 1f); g.stroke(); g.shadowBlur = 0f; g.lineWidth = 2.5f; g.strokeStyle = WHITE_A85; g.stroke()
        heartPath(g, cx, 152f, 486f, 472f); g.lineWidth = 3f; g.strokeStyle = lg(); g.globalAlpha = 0.8f; g.stroke(); g.restore()
        heartContentH2(g, st, cx, 160f, 420f, 440f, 215f, 0.42f, st.p("letter"), acc, 700, main, 16f, darken(main, 0.7f), main)
        if (st.fx) {
            miniHeart(g, 880f, 325f, 70f, main, true); miniHeart(g, 1633f, 325f, 66f, acc, true); miniHeart(g, 245f, 510f, 60f, main, true)
            miniHeart(g, 756f, 510f, 50f, acc, true); miniHeart(g, 1620f, 490f, 36f, main, true); miniHeart(g, 1807f, 350f, 30f, st.p("bar3"), true)
        }
    }

    override fun over(g: Ctx2D, st: EqState) {
        if (st.showName) drawName(g, st, 1195f, 445f, 140f, 720f)
        g.save(); rr(g, 555f, 540f, 1020f, 150f, 70f); g.fillStyle = css(st.p("panel"), 0.93f); g.shadowColor = css(st.p("bar3"), 0.9f); g.shadowBlur = 24 * curK; g.fill(); g.shadowBlur = 0f
        val gr = g.createLinearGradient(555f, 0f, 1575f, 0f); gr.addColorStop(0f, css(st.p("main"))); gr.addColorStop(0.5f, css(st.p("bar3"))); gr.addColorStop(1f, css(st.p("accent")))
        g.lineWidth = 5f; g.strokeStyle = gr; g.shadowColor = css(st.p("bar3"), 1f); g.shadowBlur = 16 * curK; g.stroke(); g.restore()
        playerStatic(g, st, this)
    }
}

/** heartContent variant with a horizontal two-colour letter (the neon design). */
private fun heartContentH2(g: Ctx2D, st: EqState, cx: Float, top: Float, w: Float, h: Float, lh: Float, ly: Float, c1: Rgb, c2: Rgb, glitter: Int, glow: Rgb?, blur: Float, outline: Rgb?, edge: Rgb) {
    if (st.heartMode == "photo" && st.photo != null) {
        g.save(); heartPath(g, cx, top, w, h); g.clip(); cover(g, st.photo, cx - w / 2f, top, w, h); g.restore()
        g.save(); heartPath(g, cx, top, w, h); g.lineWidth = 3f; g.strokeStyle = css(lighten(edge, 0.6f), 0.85f); g.stroke(); g.restore()
        return
    }
    putSprite(g, letterSprite(st, lh, w * 0.62f, c1, c2, true, glitter, outline), cx, top + h * ly, glow, blur)
}

// ======================================================= gold (simple)
private class EqGold : EqLayered("gold", "طلایی ساده") {
    init {
        k = 0.77f; ox = -3f; oy = 16f; hc = floatArrayOf(340f, 500f)
        def = mkDef(mapOf("main" to "#ffb81c", "accent" to "#ff7a00", "text" to "#ffd24a", "text2" to "#ff9a1a", "letter" to "#ffeaa0", "panel" to "#120808", "bar1" to "#ff2a22", "bar2" to "#ff9a1a", "bar3" to "#ffe033", "ui" to "#ffe9b0"), showName = false)
        bars = mkBars(555f, 1530f, 527f, 215f, 46, 12f, cen = 0.46f, refl = 0.3f)
        prog = Prog(540f, 1490f, 598f, "main", "bar3", 5f, 14f); time = TimeP(643f, 540f, 1488f, 36f); ctrl = Ctrl(985f, 655f, 125f, 42f)
        sparks = listOf(floatArrayOf(342f, 205f, 44f), floatArrayOf(150f, 420f, 30f), floatArrayOf(540f, 430f, 30f), floatArrayOf(1003f, 320f, 40f), floatArrayOf(1420f, 420f, 28f),
            floatArrayOf(1580f, 520f, 26f), floatArrayOf(210f, 610f, 26f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        pillFrame(g, st, 130f, 398f, 1490f, 300f)
        if (st.fx) {
            swirl(g, 330f, 500f, 310f, 190f, -0.1f, 0.3f, 5.6f, st.p("main"), 4f, 20f); swirl(g, 345f, 495f, 285f, 205f, 0.3f, 3.4f, 8.4f, st.p("accent"), 3f, 16f)
            swirl(g, 1560f, 545f, 95f, 150f, 0f, -1.6f, 1.7f, st.p("main"), 4f, 18f)
        }
    }

    override fun heartL(g: Ctx2D, st: EqState) {
        dottedHeart(g, st, 340f, 312f, 420f, 380f, 200f, 170f)
        if (st.fx) {
            val hs = listOf(floatArrayOf(709f, 345f, 26f), floatArrayOf(852f, 420f, 28f), floatArrayOf(1105f, 330f, 26f), floatArrayOf(1160f, 432f, 20f), floatArrayOf(1213f, 370f, 24f), floatArrayOf(1414f, 408f, 28f))
            for (h in hs) miniHeart(g, h[0], h[1], h[2], st.p("main"), false)
        }
    }

    override fun over(g: Ctx2D, st: EqState) {
        if (st.showName) drawName(g, st, 1040f, 566f, 46f, 700f)
        playerStatic(g, st, this)
    }
}

// ======================================================= royal gold heart (crest)
private class EqCrest : EqLayered("crest", "قلب سلطنتی طلایی") {
    init {
        k = 0.766f; ox = 0f; oy = 0f; hc = floatArrayOf(390f, 510f)
        def = mkDef(mapOf("main" to "#f4b928", "accent" to "#ffd24a", "text" to "#ffc233", "text2" to "#f09a12", "letter" to "#0b0b0b", "panel" to "#070608", "bar1" to "#20b8ff", "bar2" to "#ff2ed0", "bar3" to "#ff5ce0", "ui" to "#ffd36b"), barStyle = "alt")
        bars = mkBars(700f, 1640f, 445f, 190f, 62, 9f, cen = 0.42f, envK = 1.8f, refl = 0.35f)
        prog = Prog(655f, 1470f, 670f, "bar2", "bar1", 5f, 12f); time = TimeP(703f, 653f, 1478f, 26f); ctrl = Ctrl(1045f, 708f, 92f, 32f)
        sparks = listOf(floatArrayOf(388f, 255f, 40f), floatArrayOf(200f, 300f, 30f), floatArrayOf(580f, 300f, 30f), floatArrayOf(1095f, 300f, 36f), floatArrayOf(1640f, 445f, 40f),
            floatArrayOf(390f, 720f, 34f), floatArrayOf(130f, 470f, 26f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        g.save(); g.translate(390f, 505f)
        val r = mulberry(21)
        for (i in 0 until 84) {
            val a = i / 84f * TAU
            val long = 330f + r() * 90f * (if (i % 3 == 0) 1.3f else 0.7f)
            val w0 = 0.016f
            val gr = g.createRadialGradient(0f, 0f, 100f, 0f, 0f, long); gr.addColorStop(0f, css(lighten(main, 0.4f), 0.9f)); gr.addColorStop(1f, css(main, 0f)); g.fillStyle = gr
            g.beginPath(); g.moveTo(cosf(a - w0) * 110f, sinf(a - w0) * 110f); g.lineTo(cosf(a) * long, sinf(a) * long); g.lineTo(cosf(a + w0) * 110f, sinf(a + w0) * 110f); g.closePath(); g.fill()
        }
        g.restore()
        g.save(); g.beginPath(); g.moveTo(705f, 445f); g.lineTo(1632f, 445f); g.lineTo(1530f, 612f); g.lineTo(705f, 612f); g.closePath()
        val gr = g.createLinearGradient(0f, 445f, 0f, 612f); gr.addColorStop(0f, css(mix(st.p("panel"), st.p("bar1"), 0.18f), 0.95f)); gr.addColorStop(1f, css(st.p("panel"), 0.97f)); g.fillStyle = gr; g.fill()
        g.lineWidth = 3f; g.strokeStyle = metal(g, 705f, 445f, 1632f, 612f, main); g.shadowColor = css(main, 0.9f); g.shadowBlur = 14 * curK
        g.beginPath(); g.moveTo(705f, 445f); g.lineTo(1635f, 445f); g.lineTo(1530f, 612f); g.stroke(); g.restore()
        g.save(); g.fillStyle = css(st.p("accent")); g.shadowColor = css(st.p("accent"), 1f); g.shadowBlur = 14 * curK
        g.beginPath(); g.moveTo(1640f, 445f); g.lineTo(1622f, 433f); g.lineTo(1604f, 445f); g.lineTo(1622f, 457f); g.closePath(); g.fill(); g.restore()
    }

    override fun heartL(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        val cx = 390f
        val gold = goldFill(g, 100f, 300f, 700f, 760f, main)
        scroll(g, 250f, 640f, 220f, -1f, gold, 14f); scroll(g, 530f, 640f, 220f, 1f, gold, 14f); scroll(g, 245f, 430f, 170f, -1f, gold, 12f); scroll(g, 535f, 430f, 170f, 1f, gold, 12f)
        scroll(g, 300f, 330f, 150f, -1f, gold, 10f); scroll(g, 480f, 330f, 150f, 1f, gold, 10f)
        star5(g, 388f, 250f, 34f, main); star5(g, 390f, 305f, 22f, main); star5(g, 195f, 292f, 18f, main); star5(g, 598f, 292f, 18f, main)
        g.save(); heartPath(g, cx, 338f, 372f, 355f); g.shadowColor = css(main, 0.95f); g.shadowBlur = 26 * curK; g.fillStyle = metal(g, 200f, 340f, 580f, 700f, main); g.fill(); g.shadowBlur = 0f
        g.lineWidth = 4f; g.strokeStyle = css(lighten(main, 0.8f), 0.9f); g.stroke()
        heartPath(g, cx, 356f, 332f, 318f)
        val gr = g.createRadialGradient(cx - 40f, 450f, 10f, cx, 520f, 230f); gr.addColorStop(0f, css(lighten(main, 0.55f))); gr.addColorStop(0.6f, css(main)); gr.addColorStop(1f, css(darken(main, 0.25f)))
        g.fillStyle = gr; g.fill(); g.lineWidth = 3f; g.strokeStyle = css(darken(main, 0.4f), 0.8f); g.stroke(); g.restore()
        heartContent(g, st, cx, 360f, 326f, 310f, HeartOpts(lh = 165f, ly = 0.42f, c1 = st.p("letter"), glitter = 0, glow = null, edge = main))
        if (st.fx) { gem(g, 225f, 470f, 10f, intArrayOf(255, 240, 200)); gem(g, 555f, 470f, 10f, intArrayOf(255, 240, 200)) }
    }

    override fun over(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        val gold = goldFill(g, 720f, 540f, 1580f, 620f, main)
        scroll(g, 858f, 600f, 160f, -1f, gold, 9f); scroll(g, 1318f, 600f, 240f, 1f, gold, 9f)
        g.save(); g.lineCap = "round"; g.strokeStyle = css(main); g.lineWidth = 2.5f; g.shadowColor = css(main, 0.9f); g.shadowBlur = 8 * curK
        g.beginPath(); g.moveTo(880f, 612f); g.lineTo(1050f, 612f); g.moveTo(1115f, 612f); g.lineTo(1290f, 612f); g.stroke()
        g.fillStyle = css(st.p("accent")); g.beginPath(); g.moveTo(1082f, 594f); g.lineTo(1100f, 612f); g.lineTo(1082f, 630f); g.lineTo(1064f, 612f); g.closePath(); g.fill(); g.restore()
        if (st.showName) drawName(g, st, 1090f, 527f, 118f, 560f)
        g.save(); rr(g, 595f, 650f, 950f, 88f, 44f); g.fillStyle = css(st.p("panel"), 0.97f); g.shadowColor = css(main, 0.9f); g.shadowBlur = 18 * curK; g.fill(); g.shadowBlur = 0f
        g.lineWidth = 5f; g.strokeStyle = metal(g, 595f, 650f, 1545f, 738f, main); g.stroke(); g.restore()
        playerStatic(g, st, this)
    }
}

// ======================================================= Jumong crow
private fun featherPath(g: Ctx2D, len: Float, wid: Float) {
    g.beginPath(); g.moveTo(0f, 0f); g.bezierCurveTo(len * 0.25f, -wid, len * 0.75f, -wid * 0.9f, len, 0f); g.bezierCurveTo(len * 0.75f, wid * 0.9f, len * 0.25f, wid, 0f, 0f); g.closePath()
}

private fun drawCrow(g: Ctx2D, st: EqState, bass: Float, now: Float) {
    val dark = st.p("letter")
    val gold = st.p("main")
    val flap = (bass * 18f + sinf(now * 2.2f) * 3f) * PIf / 180f
    fun fth(len: Float, wid: Float) {
        val gr = g.createLinearGradient(0f, 0f, len, 0f)
        gr.addColorStop(0f, css(lighten(dark, 0.1f))); gr.addColorStop(0.6f, css(dark)); gr.addColorStop(1f, css(mix(dark, gold, 0.5f)))
        g.fillStyle = gr; featherPath(g, len, wid); g.fill(); g.lineWidth = 2.2f; g.strokeStyle = css(gold, 0.95f); g.stroke()
    }
    g.lineJoin = "round"; g.lineCap = "round"
    for (sg in floatArrayOf(-1f, 1f)) {
        g.save(); g.translate(sg * 14f, -24f); g.scale(sg, 1f)
        val n = 9
        for (i in n - 1 downTo 0) {
            val t = i.toFloat() / (n - 1)
            val a = (-80f + t * 104f) * PIf / 180f - flap * (1 - t * 0.35f)
            val len = 98f + 46f * sinf(PIf * (0.12f + 0.72f * t))
            g.save(); g.rotate(a); fth(len, 16f); g.restore()
        }
        val m = 7
        for (i in m - 1 downTo 0) {
            val t = i.toFloat() / (m - 1)
            val a = (-62f + t * 80f) * PIf / 180f - flap * 0.7f
            val len = 50f + 26f * sinf(PIf * (0.15f + 0.7f * t))
            g.save(); g.rotate(a); fth(len, 13f); g.restore()
        }
        g.restore()
    }
    for (k in -2..2) {
        g.save(); g.translate(0f, 52f); g.rotate(PIf / 2f + k * 0.21f + sinf(now * 1.6f + k) * 0.03f); fth(78f + (2 - Math.abs(k)) * 11f, 12f); g.restore()
    }
    g.strokeStyle = css(gold); g.lineWidth = 5f
    for (x in floatArrayOf(-20f, 0f, 20f)) {
        g.beginPath(); g.moveTo(x * 0.6f, 62f); g.lineTo(x, 102f); g.stroke(); g.lineWidth = 3f
        g.beginPath(); g.moveTo(x - 10f, 116f); g.lineTo(x, 102f); g.lineTo(x + 10f, 116f); g.moveTo(x, 119f); g.lineTo(x, 102f); g.stroke(); g.lineWidth = 5f
    }
    val bg = g.createLinearGradient(0f, -30f, 0f, 70f); bg.addColorStop(0f, css(lighten(dark, 0.12f))); bg.addColorStop(1f, css(mix(dark, gold, 0.35f)))
    g.fillStyle = bg; g.beginPath(); g.ellipse(0f, 14f, 27f, 52f, 0f, 0f, TAU); g.fill(); g.lineWidth = 2.4f; g.strokeStyle = css(gold); g.stroke()
    g.lineWidth = 1.6f; g.strokeStyle = css(gold, 0.7f)
    for (i in 0 until 4) { g.beginPath(); g.arc(0f, 8f + i * 14f, 18f - i * 2f, 0.25f * PIf, 0.75f * PIf); g.stroke() }
    g.save(); g.translate(0f, -66f); fth(26f, 7f); g.rotate(-0.5f); fth(24f, 6f); g.rotate(1f); fth(24f, 6f); g.restore()
    g.fillStyle = css(dark); g.beginPath(); g.arc(0f, -50f, 17f, 0f, TAU); g.fill(); g.lineWidth = 2.4f; g.strokeStyle = css(gold); g.stroke()
    g.fillStyle = css(lighten(gold, 0.25f)); g.beginPath(); g.moveTo(-9f, -45f); g.lineTo(9f, -45f); g.lineTo(0f, -24f); g.closePath(); g.fill()
    g.fillStyle = css(lighten(gold, 0.6f))
    for (x in floatArrayOf(-7f, 7f)) { g.beginPath(); g.ellipse(x, -54f, 3.2f, 2.2f, if (x < 0) 0.4f else -0.4f, 0f, TAU); g.fill() }
}

private class EqJumong : EqLayered("jumong", "پرنده سپاه جومونگ (سه‌پا)") {
    init {
        k = 1f; ox = 0f; oy = 0f; hc = floatArrayOf(640f, 285f)
        def = mkDef(mapOf("main" to "#f5b82e", "accent" to "#d6291a", "text" to "#ffcc3c", "text2" to "#ef8a14", "letter" to "#140806", "panel" to "#0a0505", "bar1" to "#ff3b2e", "bar2" to "#ff9a1a", "bar3" to "#ffd84a", "ui" to "#ffe08a"))
        bars = mkBars(50f, 455f, 285f, 135f, 30, 8f, cen = 1f, envK = 1.6f, envMin = 0.3f, mirror = true, side = "left")
        bars2 = mkBars(825f, 1230f, 285f, 135f, 30, 8f, cen = 0f, envK = 1.6f, envMin = 0.3f, mirror = true, side = "right")
        prog = Prog(330f, 950f, 628f, "accent", "bar3", 5f, 12f); time = TimeP(675f, 330f, 950f, 28f); ctrl = Ctrl(640f, 675f, 90f, 28f)
        sparks = listOf(floatArrayOf(640f, 100f, 40f), floatArrayOf(470f, 200f, 30f), floatArrayOf(810f, 200f, 30f), floatArrayOf(640f, 470f, 34f), floatArrayOf(250f, 285f, 26f), floatArrayOf(1030f, 285f, 26f))
    }

    override fun under(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        val acc = st.p("accent")
        val cx = 640f
        val cy = 285f
        val R = 170f
        var gr = g.createRadialGradient(cx, cy, R * 0.5f, cx, cy, R * 2.2f); gr.addColorStop(0f, css(acc, 0.55f)); gr.addColorStop(1f, css(acc, 0f)); g.fillStyle = gr; g.fillRect(0f, 0f, 1280f, 720f)
        g.save(); g.lineCap = "round"
        val r = mulberry(9)
        val N = 56
        for (i in 0 until N) {
            val a = i.toFloat() / N * TAU
            val lg = R + 50f + r() * 80f * (if (i % 2 == 1) 1f else 0.6f)
            val w0 = 0.028f
            gr = g.createRadialGradient(cx, cy, R, cx, cy, lg); gr.addColorStop(0f, css(lighten(main, 0.3f), 0.95f)); gr.addColorStop(1f, css(acc, 0f)); g.fillStyle = gr
            g.beginPath(); g.moveTo(cx + cosf(a - w0) * R, cy + sinf(a - w0) * R); g.lineTo(cx + cosf(a) * lg, cy + sinf(a) * lg); g.lineTo(cx + cosf(a + w0) * R, cy + sinf(a + w0) * R); g.closePath(); g.fill()
        }
        g.restore()
        g.save(); g.beginPath(); g.arc(cx, cy, R, 0f, TAU)
        gr = g.createRadialGradient(cx, cy - 30f, 10f, cx, cy, R); gr.addColorStop(0f, css(lighten(acc, 0.65f))); gr.addColorStop(0.45f, css(lighten(acc, 0.1f))); gr.addColorStop(1f, css(darken(acc, 0.55f)))
        g.fillStyle = gr; g.shadowColor = css(main, 0.9f); g.shadowBlur = 30 * curK; g.fill(); g.shadowBlur = 0f
        g.lineWidth = 9f; g.strokeStyle = metal(g, cx - R, cy - R, cx + R, cy + R, main); g.stroke()
        g.lineWidth = 2f; g.strokeStyle = css(lighten(main, 0.7f), 0.8f); g.beginPath(); g.arc(cx, cy, R - 12f, 0f, TAU); g.stroke(); g.beginPath(); g.arc(cx, cy, R + 14f, 0f, TAU); g.stroke(); g.restore()
        g.save(); g.lineCap = "round"
        for (ab in listOf(floatArrayOf(50f, 455f), floatArrayOf(825f, 1230f))) {
            val a = ab[0]
            val b = ab[1]
            val q = g.createLinearGradient(a, 0f, b, 0f)
            q.addColorStop(0f, css(st.p("bar1"), if (a < 400f) 0.1f else 1f)); q.addColorStop(1f, css(st.p("bar1"), if (a < 400f) 1f else 0.1f))
            g.strokeStyle = q; g.lineWidth = 2.5f; g.beginPath(); g.moveTo(a, 285f); g.lineTo(b, 285f); g.stroke()
        }
        g.restore()
    }

    override fun over(g: Ctx2D, st: EqState) {
        val main = st.p("main")
        val gold = goldFill(g, 300f, 500f, 980f, 600f, main)
        scroll(g, 300f, 550f, 140f, -1f, gold, 10f); scroll(g, 980f, 550f, 140f, 1f, gold, 10f)
        if (st.showName) drawName(g, st, 640f, 535f, 80f, 640f)
        g.save(); rr(g, 290f, 598f, 700f, 110f, 40f); g.fillStyle = css(st.p("panel"), 0.78f); g.fill(); g.lineWidth = 3f; g.strokeStyle = metal(g, 290f, 598f, 990f, 708f, main); g.stroke(); g.restore()
        playerStatic(g, st, this)
    }

    override fun dyn(g: Ctx2D, st: EqState, d: EqData) {
        g.save(); g.translate(640f, 285f)
        val sc = 1f + 0.03f * powf(d.bass, 1.5f)
        g.scale(sc, sc)
        g.save(); g.rotate(d.now * 0.15f); g.fillStyle = css(lighten(st.p("main"), 0.3f), 0.9f)
        for (i in 0 until 72) {
            val a = i / 72f * TAU
            g.beginPath(); g.arc(cosf(a) * 198f, sinf(a) * 198f, if (i % 6 != 0) 1.6f else 3.2f, 0f, TAU); g.fill()
        }
        g.restore()
        drawCrow(g, st, d.bass, d.now); g.restore()
    }
}

// ======================================================= classic photo frame
private class EqClassic : EqTpl("classic", "کلاسیک (قاب عکس)") {
    private val sx = EQ_W / 720f
    private fun P_(x: Float, y: Float) = floatArrayOf(x * sx, (y - 9f) * sx)
    private val p0 = P_(70f, 98f)
    private val p1 = P_(303f, 330f)
    private val PX0 = p0[0]; private val PY0 = p0[1]; private val PX1 = p1[0]; private val PY1 = p1[1]
    private val PW = PX1 - PX0; private val PHt = PY1 - PY0
    private val b0 = P_(351f, 177f)
    private val b1 = P_(664f, 190f)
    private val BX0 = b0[0]; private val BY0 = b0[1]; private val BX1 = b1[0]; private val BY1 = b1[1]
    private val RR = 142f * sx
    private val RCx = P_(186f, 215f)[0]
    private val RCy = P_(186f, 215f)[1]
    private val NB = 140
    private val MAXH = Math.round(40f * sx * 1.05f).toFloat()
    private val BAR_X0 = BX0 + 4f
    private val BAR_X1 = BX1 - 4f
    private val BAR_Y = Math.round(BY1 + 3f).toFloat()
    private var rot = 0f
    private var lastNow = -1f
    private var place: Bitmap? = null

    init {
        keys = listOf("bar2", "ui", "text")
        labels = mapOf("bar2" to "رنگ میله‌ها و نوار", "ui" to "رنگ قاب و آیکن‌ها", "text" to "رنگ نام")
        def = EqDef(mapOf("main" to "#eed624", "accent" to "#50c8ff", "text" to "#ffffff", "text2" to "#ffffff", "letter" to "#ffffff", "panel" to "#000000", "bar1" to "#eed624", "bar2" to "#eed624", "bar3" to "#eed624", "ui" to "#ffffff"),
            showName = true, barStyle = "solid")
    }

    private fun placeholder(): Bitmap {
        var p = place
        if (p == null) {
            val c = mk(800, 800)
            val g = c.g
            val gr = g.createLinearGradient(0f, 0f, 800f, 800f); gr.addColorStop(0f, css(col("#3b2a66"))); gr.addColorStop(1f, css(col("#10304f")))
            g.fillStyle = gr; g.fillRect(0f, 0f, 800f, 800f)
            g.fillStyle = Color.argb(56, 255, 255, 255); g.beginPath(); g.arc(400f, 330f, 130f, 0f, TAU); g.fill(); g.beginPath(); g.ellipse(400f, 700f, 260f, 230f, 0f, PIf, 0f); g.fill()
            p = c.bmp; place = p
        }
        return p!!
    }

    private fun drawBuds(g: Ctx2D, U: Int) {
        val X = 835f
        val Y = 117f
        val S = 169f / 570f
        g.save(); g.translate(X, Y); g.scale(S, S)
        val dark = css(col("#111111"))
        fun ell(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, fill: Int?, stroke: Int?, lw: Float) {
            g.beginPath(); g.ellipse(cx, cy, rx, ry, r, 0f, TAU)
            if (fill != null) { g.fillStyle = fill; g.fill() }
            if (stroke != null) { g.lineWidth = lw; g.strokeStyle = stroke; g.stroke() }
        }
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Int) { rr(g, x, y, w, h, r); g.fillStyle = fill; g.fill() }
        rrect(118f, 170f, 58f, 215f, 22f, U); ell(150f, 128f, 92f, 80f, -0.12f, U, null, 0f); ell(224f, 184f, 60f, 50f, 0.7f, U, dark, 9f); rrect(133f, 112f, 26f, 84f, 13f, dark)
        rrect(372f, 330f, 58f, 195f, 22f, U); ell(392f, 270f, 98f, 74f, -0.45f, U, null, 0f)
        g.save(); g.translate(426f, 244f); g.rotate(-0.45f); rrect(-38f, -12f, 76f, 24f, 12f, dark); g.restore()
        ell(298f, 322f, 60f, 52f, -0.4f, U, dark, 9f); ell(280f, 340f, 24f, 21f, -0.4f, null, dark, 7f); ell(280f, 340f, 10f, 8f, -0.4f, css(col("#777777")), null, 0f)
        g.restore()
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        curK = 1f
        val U = css(st.p("ui"))
        val Pt = st.P
        L.PHOTO = mk(EQ_W, EQ_H)
        var g = L.PHOTO!!.g
        g.save(); rr(g, PX0, PY0, PW, PHt, 14f); g.clip(); cover(g, st.photo ?: placeholder(), PX0, PY0, PW, PHt)
        var gr = g.createLinearGradient(0f, PY0, 0f, PY0 + PHt * 0.18f); gr.addColorStop(0f, Color.argb(153, 0, 0, 0)); gr.addColorStop(1f, Color.argb(0, 0, 0, 0)); g.fillStyle = gr; g.fillRect(PX0, PY0, PW, PHt * 0.18f)
        gr = g.createLinearGradient(0f, PY0 + PHt * 0.76f, 0f, PY1); gr.addColorStop(0f, Color.argb(0, 0, 0, 0)); gr.addColorStop(1f, Color.argb(191, 0, 0, 0)); g.fillStyle = gr; g.fillRect(PX0, PY0 + PHt * 0.76f, PW, PHt * 0.24f); g.restore()
        g.strokeStyle = U; g.fillStyle = U; g.lineCap = "round"; g.lineJoin = "round"
        val arm = 30f
        val off = 8f
        g.lineWidth = 3.5f
        val corners = arrayOf(floatArrayOf(PX0 + off, PY0 + off, 1f, 1f), floatArrayOf(PX1 - off, PY0 + off, -1f, 1f), floatArrayOf(PX0 + off, PY1 - off, 1f, -1f), floatArrayOf(PX1 - off, PY1 - off, -1f, -1f))
        for (c in corners) { g.beginPath(); g.moveTo(c[0] + c[2] * arm, c[1]); g.lineTo(c[0], c[1]); g.lineTo(c[0], c[1] + c[3] * arm); g.stroke() }
        val ty = PY0 + 30f
        for (i in 0 until 4) g.fillRect(PX1 - 205f + i * 9f, ty + 10f - i * 4f, 6f, 4f + i * 4f)
        g.lineWidth = 2.6f
        for (r in floatArrayOf(4f, 9f, 14f)) { g.beginPath(); g.arc(PX1 - 142f, ty + 14f, r, 215f * PIf / 180f, 325f * PIf / 180f); g.stroke() }
        g.beginPath(); g.arc(PX1 - 142f, ty + 14f, 2.2f, 0f, TAU); g.fill()
        g.lineWidth = 2.4f; rr(g, PX1 - 110f, ty - 2f, 62f, 22f, 6f); g.stroke(); rr(g, PX1 - 107f, ty + 1f, 56f, 16f, 4f); g.fill(); g.fillRect(PX1 - 46f, ty + 5f, 3f, 8f)
        val ny = PY1 - 34f
        g.lineWidth = 2.8f
        for (i in 0 until 5) {
            val cx = PX0 + 67f + 71f * i
            when (i) {
                0 -> {
                    g.beginPath(); g.moveTo(cx - 14f, ny + 2f); g.lineTo(cx, ny - 12f); g.lineTo(cx + 14f, ny + 2f); g.stroke()
                    g.beginPath(); g.moveTo(cx - 10f, ny + 1f); g.lineTo(cx - 10f, ny + 14f); g.lineTo(cx + 10f, ny + 14f); g.lineTo(cx + 10f, ny + 1f); g.stroke(); g.fillRect(cx - 3f, ny + 6f, 6f, 8f)
                }
                1 -> {
                    g.beginPath(); g.arc(cx - 3f, ny - 2f, 10f, 0f, TAU); g.stroke()
                    g.beginPath(); g.moveTo(cx + 5f, ny + 6f); g.lineTo(cx + 13f, ny + 14f); g.lineWidth = 3f; g.stroke(); g.lineWidth = 2.8f
                }
                2 -> {
                    rr(g, cx - 13f, ny - 13f, 26f, 26f, 5f); g.stroke()
                    g.beginPath(); g.moveTo(cx - 6f, ny); g.lineTo(cx + 6f, ny); g.moveTo(cx, ny - 6f); g.lineTo(cx, ny + 6f); g.stroke()
                }
                3 -> {
                    g.beginPath()
                    for (k in 0..60) {
                        val t = k / 60f * 2f * PIf
                        val X = 16f * powf(sinf(t), 3f)
                        val Y = 13f * cosf(t) - 5f * cosf(2f * t) - 2f * cosf(3f * t) - cosf(4f * t)
                        val px = cx + X * 0.85f
                        val py = ny - Y * 0.85f + 1f
                        if (k > 0) g.lineTo(px, py) else g.moveTo(px, py)
                    }
                    g.closePath(); g.stroke()
                }
                else -> {
                    g.beginPath(); g.arc(cx, ny - 6f, 6f, 0f, TAU); g.stroke(); g.beginPath(); g.arc(cx, ny + 15f, 13f, PIf, 2f * PIf); g.stroke()
                }
            }
        }
        L.UI = mk(EQ_W, EQ_H); g = L.UI!!.g
        g.lineCap = "round"; g.lineJoin = "round"; g.fillStyle = U; g.strokeStyle = U
        drawBuds(g, U)
        val cy = P_(0f, 263f)[1]
        fun tri(cx: Float, y: Float, w: Float, h: Float, d: Float) {
            g.beginPath(); g.moveTo(cx + d * w / 2f, y); g.lineTo(cx - d * w / 2f, y - h / 2f); g.lineTo(cx - d * w / 2f, y + h / 2f); g.closePath(); g.fill()
        }
        for (cd in listOf(floatArrayOf(P_(445f, 0f)[0], -1f), floatArrayOf(P_(570f, 0f)[0], 1f))) {
            tri(cd[0] - cd[1] * 11f, cy, 22f, 28f, cd[1]); tri(cd[0] + cd[1] * 11f, cy, 22f, 28f, cd[1])
        }
        val px = P_(507f, 0f)[0]
        g.fillRect(px - 17f, cy - 22f, 11f, 44f); g.fillRect(px + 6f, cy - 22f, 11f, 44f)
        g.lineWidth = 2.6f; rr(g, BX0, BY0, BX1 - BX0, BY1 - BY0, 3f); g.stroke()
        val name = st.name.trim()
        g.fontFamily = Typeface.SANS_SERIF
        g.direction = "rtl"; g.textAlign = "center"; g.textBaseline = "middle"; g.setFont(700, 36f)
        val nc = P_(480f, 319f)
        val cx0 = nc[0] - 22f
        val cy0 = nc[1]
        var tw = g.measureText(name).width
        if (tw > 330f) { val kk = 330f / tw; g.setFont(700, Math.floor((36f * kk).toDouble()).toFloat()); tw = 330f }
        g.fillStyle = css(st.p("text")); g.shadowColor = css(st.p("text"), 0.55f); g.shadowBlur = 10f; g.fillText(name, cx0, cy0); g.shadowBlur = 0f; g.fillText(name, cx0, cy0); g.fillStyle = U
        val hx = cx0 + tw / 2f + 30f
        val hy = cy0 - 6f
        g.lineWidth = 3.2f; g.beginPath(); g.arc(hx, hy - 2f, 18f, PIf, 2f * PIf); g.stroke()
        rr(g, hx - 22f, hy - 2f, 10f, 18f, 4f); g.fill(); rr(g, hx + 12f, hy - 2f, 10f, 18f, 4f); g.fill()
        g.lineWidth = 4.2f
        val q1 = P_(403f, 337f); val q2 = P_(570f, 337f); val q3 = P_(585f, 323f); val q4 = P_(625f, 323f)
        g.beginPath(); g.moveTo(q1[0], q1[1]); g.lineTo(q2[0], q2[1]); g.lineTo(q3[0], q3[1]); g.lineTo(q4[0], q4[1]); g.stroke()
        g.lineWidth = 1.6f
        val a1 = P_(575f, 331f); val a2 = P_(662f, 326f)
        g.beginPath(); g.moveTo(a1[0], a1[1]); g.lineTo(a2[0], a2[1]); g.stroke(); g.beginPath(); g.arc(a2[0], a2[1], 3.6f, 0f, TAU); g.fill()
        val dots = listOf(floatArrayOf(337f, 358f, 2.5f), floatArrayOf(362f, 359f, 1.8f), floatArrayOf(676f, 329f, 2.2f), floatArrayOf(436f, 288f, 1.6f))
        val dcol = listOf("#46e6e6", "#3cc8d2", "#46dce6", "#ffffff")
        for ((i, d) in dots.withIndex()) { g.fillStyle = css(col(dcol[i])); val p = P_(d[0], d[1]); g.beginPath(); g.arc(p[0], p[1], d[2], 0f, TAU); g.fill() }

        val RS = 2
        val RC = Math.round(RR) + 60
        val RD = RC * 2
        L.RING = mk(RD * RS, RD * RS); L.extra["RD"] = RD.toFloat(); g = L.RING!!.g
        g.scale(RS.toFloat(), RS.toFloat()); g.translate(RC.toFloat(), RC.toFloat()); g.fillStyle = U; g.strokeStyle = U; g.lineCap = "round"; g.lineWidth = 2.2f; g.globalAlpha = 0.92f
        var kd = 0
        while (kd < 360) { g.beginPath(); g.arc(0f, 0f, RR, kd * PIf / 180f, (kd + 7) * PIf / 180f); g.stroke(); kd += 10 }
        g.globalAlpha = 0.66f
        kd = 0
        while (kd < 360) { val a = kd * PIf / 180f; g.beginPath(); g.arc(cosf(a) * (RR - 14f), sinf(a) * (RR - 14f), 1.2f, 0f, TAU); g.fill(); kd += 4 }
        g.globalAlpha = 1f
        fun star4(x: Float, y: Float, r: Float, rt: Float) {
            g.beginPath()
            for (k in 0 until 8) {
                val q = if (k % 2 == 0) r else r * 0.28f
                val a = (rt + k * 45f) * PIf / 180f
                val px2 = x + q * cosf(a)
                val py2 = y + q * sinf(a)
                if (k > 0) g.lineTo(px2, py2) else g.moveTo(px2, py2)
            }
            g.closePath(); g.fillStyle = U; g.fill()
        }
        fun gm(x: Float, y: Float, r: Float, rt: Float) {
            g.beginPath()
            for (k in 0 until 4) {
                val a = (rt + 45f + 90f * k) * PIf / 180f
                val px2 = x + r * cosf(a)
                val py2 = y + r * sinf(a)
                if (k > 0) g.lineTo(px2, py2) else g.moveTo(px2, py2)
            }
            g.closePath(); g.fillStyle = css(col("#141414")); g.fill(); g.lineWidth = 2.4f; g.strokeStyle = U; g.stroke(); star4(x, y, r * 0.62f, rt)
        }
        val orn = listOf(
            Triple(-125f, "gem", 17f), Triple(-82f, "star4", 24f), Triple(-38f, "dot", 5f), Triple(2f, "star4", 15f), Triple(24f, "gem", 15f), Triple(58f, "dot", 4f),
            Triple(98f, "star4", 20f), Triple(136f, "gem", 16f), Triple(172f, "star4", 17f), Triple(212f, "dot", 5f), Triple(238f, "gem", 19f), Triple(-160f, "star4", 13f),
            Triple(-58f, "dot", 3.5f), Triple(80f, "dot", 3.5f), Triple(195f, "dot", 3f)
        )
        for ((ang, typ, sz) in orn) {
            val rad = RR + (if (typ == "dot") 10f else 0f)
            val a = ang * PIf / 180f
            val x = cosf(a) * rad
            val y = sinf(a) * rad
            if (typ == "star4") star4(x, y, sz, ang) else if (typ == "gem") gm(x, y, sz, ang) else { g.fillStyle = U; g.beginPath(); g.arc(x, y, sz * 0.5f, 0f, TAU); g.fill() }
        }
        val rnd = mulberry(5)
        for (k in 0 until 26) {
            val a = rnd() * PIf * 2f
            val r = RR + (rnd() * 50f - 24f)
            val s = 1.2f + rnd() * 1.6f
            g.globalAlpha = 0.5f + rnd() * 0.5f; g.beginPath(); g.arc(cosf(a) * r, sinf(a) * r, s, 0f, TAU); g.fill()
        }
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val theme = st.p("bar2")
        val dt = if (lastNow < 0f) 0f else Math.max(0f, d.now - lastNow)
        rot += Math.min(0.1f, dt) * 20f; lastNow = d.now
        val RD = (L.extra["RD"] as? Float) ?: 1f
        g.save(); g.translate(RCx, RCy); g.rotate(-rot * PIf / 180f); val sc = 1f + 0.022f * powf(d.bass, 1.5f); g.scale(sc, sc); g.drawImage(L.RING, -RD / 2f, -RD / 2f, RD, RD); g.restore()
        g.drawImage(L.PHOTO, 0f, 0f)
        val frac = if (d.dur > 0f) Math.min(1f, d.t / d.dur) else 0f
        val fw = Math.max(8f, frac * (BX1 - BX0 - 6f))
        g.fillStyle = css(theme); rr(g, BX0 + 3f, BY0 + 3f, fw, BY1 - BY0 - 5f, 2f); g.fill()
        val gr = g.createLinearGradient(0f, BAR_Y, 0f, BAR_Y + MAXH); gr.addColorStop(0f, css(theme, 1f)); gr.addColorStop(1f, css(theme, 0.38f)); g.fillStyle = gr
        for (i in 0 until NB) {
            val j = Math.min(NBAND - 1, Math.floor(Math.abs(i - (NB - 1) / 2f).toDouble()).toInt())
            val env = 0.22f + 0.78f * Math.exp(-Math.pow((j / 34f).toDouble(), 2.0)).toFloat()
            val h = Math.round(MAXH * env * (0.05f + 0.95f * d.lv[j]) * st.barScale)
            val x = Math.round(BAR_X0 + (i + 0.5f) * (BAR_X1 - BAR_X0) / NB)
            if (h > 0) { g.globalAlpha = fadeE(Math.min(i, NB - 1 - i).toFloat() / (NB - 1), st.fade); g.fillRect(x - 1f, BAR_Y, 2f, h.toFloat()) }
        }
        g.globalAlpha = 1f
        g.drawImage(L.UI, 0f, 0f)
        g.fontFamily = Typeface.SANS_SERIF
        g.setFont(700, 27f); g.textBaseline = "middle"; g.direction = "ltr"; g.fillStyle = css(mix(st.p("ui"), intArrayOf(236, 237, 196), 0.5f))
        val ty = P_(0f, 167f)[1]
        g.textAlign = "left"; g.fillText(fmtClock(d.t), P_(366f, 0f)[0], ty); g.textAlign = "right"; g.fillText(fmtClock(d.dur), P_(653f, 0f)[0], ty)
    }
}
