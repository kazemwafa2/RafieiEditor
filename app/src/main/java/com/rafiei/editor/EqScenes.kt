package com.rafiei.editor

import android.graphics.Color
import android.graphics.Typeface

private fun hsvC(h: Float, s: Float, v: Float, a: Float = 1f): Int {
    val c = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))
    return Color.argb(Math.round(clampF(a, 0f, 1f) * 255f), Color.red(c), Color.green(c), Color.blue(c))
}

private fun lvAt(d: EqData, u: Float): Float {
    val x = clampF(u, 0f, 1f) * (d.lv.size - 1)
    val i = Math.floor(x.toDouble()).toInt()
    val j = Math.min(d.lv.size - 1, i + 1)
    return d.lv[i] * (1f - (x - i)) + d.lv[j] * (x - i)
}

/**
 * A room with a TV, a light stand and a camera tripod, drawn BEHIND the person of the main clip (the person is cut
 * out with the segmentation mask, so the user's own film stays in front). The TV shows a second video or picture
 * chosen by the user (a street, a city, anything); its size and place are adjustable.
 */
class EqTvRoom : EqTpl("tvroom", "اتاق و تلویزیون (فیلم شما جلو)") {
    override val behind = true
    override val defText: String? = "Kazem Rafiei"

    init {
        labels = mapOf("main" to "رنگ نوارهای تلویزیون", "accent" to "رنگ دوم نوارها", "text" to "رنگ نوشته", "text2" to "رنگ نوشته (انتها)", "panel" to "رنگ دیوار")
        keys = listOf("main", "accent", "text", "text2", "panel")
        def = EqDef(
            mapOf(
                "main" to "#2fd8ff", "accent" to "#ff3cd2", "text" to "#ffffff", "text2" to "#ffd36b", "letter" to "#ffffff",
                "panel" to "#e6d8c4", "bar1" to "#2fd8ff", "bar2" to "#ff3cd2", "bar3" to "#ffffff", "ui" to "#ffffff"
            ), true, "grad", false, false
        )
    }

    private val SX = 162f
    private val SY = 142f
    private val SW = 676f
    private val SH = 363f
    private val TCX = 500f
    private val TCY = 380f

    private class Tv(val uri: String, val dx: Float, val dy: Float, val s: Float, val loop: Boolean, val bars: Boolean)

    private fun tvOf(st: EqState): Tv {
        val j = st.cfg.optJSONObject("tv") ?: org.json.JSONObject()
        return Tv(j.optString("uri", ""), j.optDouble("x", 0.0).toFloat() / 100f * EQ_W, j.optDouble("y", 0.0).toFloat() / 100f * EQ_H,
            j.optDouble("s", 100.0).toFloat() / 100f, j.optBoolean("loop", true), j.optBoolean("bars", true))
    }

    private fun place(g: Ctx2D, tv: Tv) { g.translate(TCX + tv.dx, TCY + tv.dy); g.scale(tv.s, tv.s); g.translate(-TCX, -TCY) }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        val tv = tvOf(st)
        val c = mk(EQ_W, EQ_H)
        val g = c.g
        val wall = st.p("panel")
        val w1 = g.createLinearGradient(0f, 0f, 0f, 560f)
        w1.addColorStop(0f, css(lighten(wall, 0.08f))); w1.addColorStop(1f, css(darken(wall, 0.12f)))
        g.fillStyle = w1; g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat())
        g.strokeStyle = css(darken(wall, 0.25f), 0.5f); g.lineWidth = 3f
        var x = -40f
        while (x < EQ_W) { g.strokeRect(x + 14f, 70f, 300f, 480f); g.strokeRect(x + 26f, 82f, 276f, 456f); x += 336f }
        g.fillStyle = css(darken(wall, 0.2f)); g.fillRect(0f, 40f, EQ_W.toFloat(), 14f)
        val fl = g.createLinearGradient(0f, 560f, 0f, 720f)
        fl.addColorStop(0f, Color.rgb(34, 31, 30)); fl.addColorStop(1f, Color.rgb(10, 9, 9))
        g.fillStyle = fl; g.fillRect(0f, 560f, EQ_W.toFloat(), 160f)
        val r = mulberry(11)
        for (i in 0 until 700) {
            g.fillStyle = Color.argb(60, if (r() > 0.5f) 200 else 90, 90, 90)
            g.fillRect(r() * EQ_W, 560f + r() * 160f, 3f, 2f)
        }
        // TV with its console (moves and scales with the settings)
        g.save(); place(g, tv)
        g.fillStyle = Color.rgb(8, 8, 9); g.fillRect(70f, 520f, 840f, 110f)
        g.fillStyle = Color.argb(70, 255, 255, 255); g.fillRect(70f, 520f, 840f, 3f)
        g.fillStyle = Color.rgb(20, 20, 22); g.fillRect(70f, 630f, 840f, 8f)
        g.fillStyle = Color.rgb(4, 4, 5); rr(g, SX - 14f, SY - 14f, SW + 28f, SH + 28f, 8f); g.fill()
        g.fillStyle = Color.rgb(30, 30, 34); g.fillRect(480f, SY + SH + 14f, 40f, 12f)
        g.restore()
        // light stand with panel light
        g.fillStyle = Color.rgb(14, 14, 15); g.fillRect(996f, 140f, 9f, 560f)
        g.fillStyle = Color.rgb(20, 20, 22); rr(g, 900f, 20f, 200f, 130f, 8f); g.fill()
        g.strokeStyle = Color.rgb(90, 90, 96); g.lineWidth = 4f; g.beginPath(); g.moveTo(900f, 110f); g.quadraticCurveTo(1000f, 190f, 1100f, 110f); g.stroke()
        g.fillStyle = Color.rgb(60, 220, 70); g.fillRect(968f, 52f, 64f, 40f)
        g.fillStyle = Color.rgb(40, 40, 44); g.fillRect(940f, 100f, 120f, 8f)
        g.strokeStyle = Color.rgb(12, 12, 13); g.lineWidth = 7f
        for (dx in floatArrayOf(-110f, 0f, 110f)) { g.beginPath(); g.moveTo(1000f, 560f); g.lineTo(1000f + dx, 712f); g.stroke() }
        // camera tripod
        g.strokeStyle = Color.rgb(12, 12, 13); g.lineWidth = 8f
        g.beginPath(); g.moveTo(1140f, 430f); g.lineTo(1140f, 520f); g.stroke()
        for (dx in floatArrayOf(-90f, 0f, 90f)) { g.beginPath(); g.moveTo(1140f, 520f); g.lineTo(1140f + dx, 710f); g.stroke() }
        g.fillStyle = Color.rgb(16, 16, 18); rr(g, 1080f, 350f, 120f, 78f, 8f); g.fill()
        g.fillStyle = Color.rgb(5, 5, 6); g.beginPath(); g.arc(1070f, 390f, 34f, 0f, 6.283f); g.fill()
        g.strokeStyle = Color.rgb(70, 70, 76); g.lineWidth = 4f; g.beginPath(); g.arc(1070f, 390f, 22f, 0f, 6.283f); g.stroke()
        g.fillStyle = Color.rgb(50, 70, 90); g.fillRect(1120f, 362f, 70f, 40f)
        L.under = c
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        g.drawImage(L.under, 0f, 0f)
        val tv = tvOf(st)
        val gl = Math.pow(d.bass.toDouble(), 1.4).toFloat()
        g.save(); place(g, tv)
        g.save(); g.globalCompositeOperation = "lighter"
        val sp = g.createRadialGradient(SX + SW / 2f, SY + SH / 2f, 80f, SX + SW / 2f, SY + SH / 2f, 560f)
        sp.addColorStop(0f, css(st.p("main"), 0.08f + 0.2f * gl)); sp.addColorStop(1f, css(st.p("main"), 0f))
        g.fillStyle = sp; g.fillRect(-400f, -300f, EQ_W + 800f, 900f)
        g.restore()
        g.save()
        rr(g, SX, SY, SW, SH, 4f); g.clip()
        val bg = g.createLinearGradient(0f, SY, 0f, SY + SH)
        bg.addColorStop(0f, Color.rgb(8, 14, 40)); bg.addColorStop(1f, Color.rgb(28, 10, 46))
        g.fillStyle = bg; g.fillRect(SX, SY, SW, SH)
        val shot = if (tv.uri.isNotEmpty()) TvShots.forSource(tv.uri, (d.t * 1000f).toLong(), tv.loop) else null
        if (shot != null) {
            val sc = Math.max(SW / shot.width, SH / shot.height)
            g.drawImage(shot, SX + (SW - shot.width * sc) / 2f, SY + (SH - shot.height * sc) / 2f, shot.width * sc, shot.height * sc)
            g.fillStyle = Color.argb(30, 0, 0, 20); g.fillRect(SX, SY, SW, SH)
        }
        if (tv.bars || shot == null) {
            val n = 48
            val bw = SW / n
            g.globalCompositeOperation = "lighter"
            for (i in 0 until n) {
                val v = clampF(lvAt(d, i / (n - 1f) * 0.9f) * 1.15f, 0.04f, 1f)
                val h = v * SH * (if (shot != null) 0.22f else 0.62f)
                val c = mix(st.p("main"), st.p("accent"), i / (n - 1f))
                val gr = g.createLinearGradient(0f, SY + SH - h, 0f, SY + SH)
                gr.addColorStop(0f, css(lighten(c, 0.45f))); gr.addColorStop(1f, css(c, 0.5f))
                g.fillStyle = gr; g.fillRect(SX + i * bw + 2f, SY + SH - h, bw - 4f, h)
            }
            g.globalCompositeOperation = "source-over"
        }
        if (shot != null) drawName(g, st, SX + SW / 2f, SY + SH * 0.12f, 46f, SW * 0.8f) else drawName(g, st, SX + SW / 2f, SY + SH * 0.36f, 74f, SW * 0.86f)
        g.fillStyle = Color.argb(14, 255, 255, 255)
        g.beginPath(); g.moveTo(SX, SY); g.lineTo(SX + SW * 0.45f, SY); g.lineTo(SX + SW * 0.15f, SY + SH); g.lineTo(SX, SY + SH); g.closePath(); g.fill()
        g.restore()
        g.restore()
    }
}

/** Golden sound-wave with spikes and a large gold name on a dark blue sparkling background. */
class EqGoldWave : EqTpl("goldwave", "موج طلایی روی تیره (نوشتهٔ طلایی)") {
    override val defText: String? = "Kazem Rafiei"

    init {
        labels = mapOf("main" to "طلایی روشن", "accent" to "بنفش موج", "text" to "رنگ نوشته", "text2" to "رنگ نوشته (انتها)", "panel" to "رنگ زمینه")
        keys = listOf("main", "accent", "text", "text2", "panel")
        def = EqDef(
            mapOf(
                "main" to "#f2c86a", "accent" to "#7a4cff", "text" to "#ffe3a0", "text2" to "#c98a2a", "letter" to "#f2c86a",
                "panel" to "#0a1230", "bar1" to "#f2c86a", "bar2" to "#f2c86a", "bar3" to "#ffffff", "ui" to "#ffffff"
            ), true, "grad", false, false
        )
    }

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        val c = mk(EQ_W, EQ_H)
        val g = c.g
        val p = st.p("panel")
        val gr = g.createRadialGradient(640f, 360f, 40f, 640f, 360f, 760f)
        gr.addColorStop(0f, css(lighten(p, 0.18f))); gr.addColorStop(1f, css(darken(p, 0.65f)))
        g.fillStyle = gr; g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat())
        val h = g.createRadialGradient(640f, 560f, 10f, 640f, 560f, 520f)
        h.addColorStop(0f, css(st.p("main"), 0.28f)); h.addColorStop(1f, css(st.p("main"), 0f))
        g.save(); g.translate(0f, 560f); g.scale(1f, 0.16f); g.translate(0f, -560f); g.fillStyle = h; g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat() * 8f); g.restore()
        L.under = c
        L.spark = sparkSprite(lighten(st.p("main"), 0.4f))
        return L
    }

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        g.drawImage(L.under, 0f, 0f)
        val t = d.now
        val gold = st.p("main")
        // gold dust
        val sp = L.spark
        if (sp != null) for (i in 0 until 90) {
            val x = hashF(i * 3.1f + 1f) * EQ_W
            val y = hashF(i * 5.7f + 2f) * EQ_H * 0.92f
            val a = 0.15f + 0.85f * (0.5f + 0.5f * Math.sin((t * (0.6f + hashF(i * 1.3f)) + i).toDouble()).toFloat())
            val r = 3f + 7f * hashF(i * 9.1f + 3f)
            g.globalAlpha = a * 0.7f; g.drawImage(sp, x - r, y - r, r * 2f, r * 2f)
        }
        g.globalAlpha = 1f
        val cy = 372f
        val bass = Math.pow(d.bass.toDouble(), 1.2).toFloat()
        // ribbons
        g.globalCompositeOperation = "lighter"
        for (k in 0 until 3) {
            val steps = 90
            val top = FloatArray(steps + 1)
            val bot = FloatArray(steps + 1)
            for (s in 0..steps) {
                val u = s / steps.toFloat()
                val x = u * EQ_W
                val env = Math.exp((-Math.pow(((x - 640f) / 400f).toDouble(), 2.0))).toFloat()
                val lv = lvAt(d, Math.abs(u - 0.5f) * 1.6f)
                val a = env * (30f + 120f * lv + 40f * bass) * (1f - k * 0.18f)
                val y0 = cy + Math.sin((x * 0.011 + t * (1.2 + k * 0.4) + k * 1.7)).toFloat() * a * 0.55f
                top[s] = y0 - a * (0.25f + 0.1f * k)
                bot[s] = y0 + a * (0.25f + 0.1f * k)
            }
            g.beginPath(); g.moveTo(0f, top[0])
            for (s in 1..steps) g.lineTo(s * EQ_W / steps.toFloat(), top[s])
            for (s in steps downTo 0) g.lineTo(s * EQ_W / steps.toFloat(), bot[s])
            g.closePath()
            val fg = g.createLinearGradient(0f, 0f, EQ_W.toFloat(), 0f)
            fg.addColorStop(0f, css(st.p("accent"), 0.0f)); fg.addColorStop(0.35f, css(st.p("accent"), 0.35f)); fg.addColorStop(0.55f, css(gold, 0.45f)); fg.addColorStop(1f, css(gold, 0f))
            g.fillStyle = fg; g.fill()
            g.strokeStyle = css(lighten(gold, 0.3f), 0.55f); g.lineWidth = 2f; g.stroke()
        }
        // spikes
        val n = 70
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val x = 430f + u * 420f
            val env = Math.exp((-Math.pow(((x - 640f) / 170f).toDouble(), 2.0))).toFloat()
            val v = clampF(lvAt(d, Math.abs(u - 0.5f) * 1.8f) * 1.2f, 0.05f, 1f)
            val h = (14f + v * 230f * (0.35f + 0.65f * env))
            val gr = g.createLinearGradient(0f, cy - h, 0f, cy + h)
            gr.addColorStop(0f, css(gold, 0f)); gr.addColorStop(0.5f, css(lighten(gold, 0.5f), 0.95f)); gr.addColorStop(1f, css(gold, 0f))
            g.strokeStyle = gr; g.lineWidth = 2.4f
            g.beginPath(); g.moveTo(x, cy - h); g.lineTo(x, cy + h); g.stroke()
        }
        g.globalCompositeOperation = "source-over"
        // big gold name
        val name = st.name.trim()
        if (name.isNotEmpty()) {
            g.save(); g.fontFamily = Typeface.SERIF; g.direction = "ltr"; g.textAlign = "center"; g.textBaseline = "middle"
            var fs = 150f
            g.setFont(700, fs)
            val w = g.measureText(name).width
            if (w > 960f) { fs = Math.floor((fs * 960f / w).toDouble()).toFloat(); g.setFont(700, fs) }
            val tw = g.measureText(name).width
            val fg = g.createLinearGradient(0f, cy - fs / 2f, 0f, cy + fs / 2f)
            fg.addColorStop(0f, css(lighten(st.p("text"), 0.45f))); fg.addColorStop(0.5f, css(st.p("text"))); fg.addColorStop(1f, css(st.p("text2")))
            g.shadowColor = css(gold, 0.8f); g.shadowBlur = fs * 0.25f * (1f + 0.5f * bass)
            g.fillStyle = fg; g.fillText(name, 640f, cy)
            g.shadowBlur = 0f; g.lineWidth = Math.max(1f, fs * 0.012f); g.strokeStyle = css(lighten(st.p("text"), 0.7f), 0.5f); g.strokeText(name, 640f, cy)
            g.restore()
            if (st.showLetter && st.letter.isNotBlank()) {
                g.save(); g.fontFamily = Typeface.SERIF; g.textAlign = "center"; g.textBaseline = "middle"; g.setFont(600, 30f)
                g.fillStyle = css(st.p("letter"), 0.9f); g.fillText(st.letter, 640f, cy + fs * 0.62f + 10f); g.restore()
            }
        }
        // horizon
        val hl = g.createLinearGradient(0f, 0f, EQ_W.toFloat(), 0f)
        hl.addColorStop(0f, css(gold, 0f)); hl.addColorStop(0.5f, css(lighten(gold, 0.4f), 0.9f)); hl.addColorStop(1f, css(gold, 0f))
        g.fillStyle = hl; g.fillRect(100f, 560f, EQ_W - 200f, 2.5f)
    }
}

/** Circular neon spectrum: bars radiate from a glowing ring, the name sits in the middle. */
class EqNeonRing : EqTpl("neonring", "حلقهٔ طیف نئونی (نام وسط)") {
    override val defText: String? = "Kazem Rafiei"
    init {
        labels = mapOf("main" to "رنگ اول", "accent" to "رنگ دوم", "text" to "رنگ نوشته", "text2" to "رنگ نوشته (انتها)", "panel" to "رنگ زمینه")
        keys = listOf("main", "accent", "text", "text2", "panel")
        def = EqDef(mapOf("main" to "#2fd8ff", "accent" to "#ff3cd2", "text" to "#ffffff", "text2" to "#bfe9ff", "letter" to "#ffffff",
            "panel" to "#05060f", "bar1" to "#2fd8ff", "bar2" to "#ff3cd2", "bar3" to "#ffffff", "ui" to "#ffffff"), true, "grad", false, false)
    }
    override fun build(st: EqState): EqLayers {
        val L = EqLayers(); val c = mk(EQ_W, EQ_H); val g = c.g
        val gr = g.createRadialGradient(640f, 360f, 20f, 640f, 360f, 760f)
        gr.addColorStop(0f, css(lighten(st.p("panel"), 0.12f))); gr.addColorStop(1f, css(st.p("panel")))
        g.fillStyle = gr; g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat())
        L.under = c; return L
    }
    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        g.drawImage(L.under, 0f, 0f)
        val cx = 640f; val cy = 360f
        val R = 190f + 14f * Math.pow(d.bass.toDouble(), 1.4).toFloat()
        g.save(); g.globalCompositeOperation = "lighter"; g.lineCap = "round"
        val n = 96
        for (i in 0 until n) {
            val u = i / n.toFloat()
            val v = clampF(lvAt(d, Math.abs(u * 2f - 1f) * 0.95f) * 1.2f, 0.04f, 1f)
            val a = u * 6.2832f - 1.5708f + d.now * 0.12f
            val h = 12f + v * 150f
            val col = mix(st.p("main"), st.p("accent"), Math.abs(u * 2f - 1f))
            g.strokeStyle = css(col, 0.95f); g.lineWidth = 5f
            g.shadowColor = css(col, 0.9f); g.shadowBlur = 14f
            g.beginPath()
            g.moveTo(cx + Math.cos(a.toDouble()).toFloat() * R, cy + Math.sin(a.toDouble()).toFloat() * R)
            g.lineTo(cx + Math.cos(a.toDouble()).toFloat() * (R + h), cy + Math.sin(a.toDouble()).toFloat() * (R + h)); g.stroke()
        }
        g.shadowBlur = 0f
        g.strokeStyle = css(lighten(st.p("main"), 0.4f), 0.9f); g.lineWidth = 4f
        g.beginPath(); g.arc(cx, cy, R - 10f, 0f, 6.2832f); g.stroke()
        g.strokeStyle = css(st.p("accent"), 0.5f); g.lineWidth = 2f
        g.beginPath(); g.arc(cx, cy, R - 28f, 0f, 6.2832f); g.stroke()
        g.restore()
        drawName(g, st, cx, cy, 64f, 2f * (R - 50f))
    }
}

/** Mirrored rainbow spectrum with a reflection and the name underneath. */
class EqRainbowMirror : EqTpl("rainbowmirror", "طیف رنگین‌کمان آینه‌ای") {
    override val defText: String? = "Kazem Rafiei"
    init {
        labels = mapOf("text" to "رنگ نوشته", "text2" to "رنگ نوشته (انتها)", "panel" to "رنگ زمینه")
        keys = listOf("text", "text2", "panel")
        def = EqDef(mapOf("main" to "#ffffff", "accent" to "#ffffff", "text" to "#ffffff", "text2" to "#ffd36b", "letter" to "#ffffff",
            "panel" to "#07070f", "bar1" to "#ffffff", "bar2" to "#ffffff", "bar3" to "#ffffff", "ui" to "#ffffff"), true, "grad", false, false)
    }
    override fun build(st: EqState): EqLayers {
        val L = EqLayers(); val c = mk(EQ_W, EQ_H); val g = c.g
        g.fillStyle = css(st.p("panel")); g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat())
        L.under = c; return L
    }
    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        g.drawImage(L.under, 0f, 0f)
        val n = 56; val bw = 1100f / n; val x0 = 90f; val base = 400f
        g.save(); g.globalCompositeOperation = "lighter"
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val v = clampF(lvAt(d, u * 0.9f) * 1.2f, 0.03f, 1f)
            val h = 8f + v * 280f
            val col = hsvC(u * 300f + d.now * 20f, 0.85f, 1f)
            val gr = g.createLinearGradient(0f, base - h, 0f, base)
            gr.addColorStop(0f, col); gr.addColorStop(1f, hsvC(u * 300f + d.now * 20f, 0.9f, 0.55f))
            g.fillStyle = gr; g.fillRect(x0 + i * bw + 2f, base - h, bw - 4f, h)
            val rg = g.createLinearGradient(0f, base + 6f, 0f, base + 6f + h * 0.5f)
            rg.addColorStop(0f, hsvC(u * 300f + d.now * 20f, 0.85f, 1f, 0.35f)); rg.addColorStop(1f, hsvC(u * 300f, 0.85f, 1f, 0f))
            g.fillStyle = rg; g.fillRect(x0 + i * bw + 2f, base + 6f, bw - 4f, h * 0.5f)
        }
        g.restore()
        drawName(g, st, 640f, 600f, 70f, 1000f)
    }
}
