package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONObject

/** Equalizer overlay: registry of designs, per-overlay configuration and drawing. */
object Eq {
    /** Project used by the current drawing pass (set by Render.drawTop). */
    val cur = ThreadLocal<Project?>()

    val tpls: List<EqTpl> by lazy {
        val l = ArrayList<EqTpl>()
        val lay = eqLayeredTemplates().associateBy { it.id }
        val order = listOf("royal", "fire", "neon", "gold", "crest", "jumong")
        for (id in order) lay[id]?.let { l.add(it) }
        l.add(EqFlag())
        l.add(EqAbRing())
        l.add(EqTkRing())
        lay["classic"]?.let { l.add(it) }
        l.addAll(eqImageTemplates())
        l
    }

    fun tpl(id: String): EqTpl? = tpls.firstOrNull { it.id == id }

    class Pal(val name: String, val c: Map<String, String>)

    val palettes: List<Pal> = listOf(
        Pal("طلایی", mapOf("main" to "#f5b82e", "accent" to "#ff8a00", "text" to "#ffd24a", "text2" to "#e8921a", "letter" to "#f4cf6a", "panel" to "#0c0906", "bar1" to "#ff3b2e", "bar2" to "#ff9a1a", "bar3" to "#ffe03a", "ui" to "#ffe08a")),
        Pal("نئون صورتی-آبی", mapOf("main" to "#ff3cc8", "accent" to "#28d8ff", "text" to "#ff52d0", "text2" to "#36dcff", "letter" to "#ff9ae8", "panel" to "#070614", "bar1" to "#2fd8ff", "bar2" to "#ff3cd2", "bar3" to "#b266ff", "ui" to "#e9a6ff")),
        Pal("سلطنتی", mapOf("main" to "#f6b92a", "accent" to "#a43cff", "text" to "#ffc233", "text2" to "#f09a12", "letter" to "#f2c04a", "panel" to "#07070e", "bar1" to "#1fb7ff", "bar2" to "#ff35d0", "bar3" to "#ffae1a", "ui" to "#ffd36b")),
        Pal("یخی", mapOf("main" to "#7ad7ff", "accent" to "#4a7bff", "text" to "#d8f4ff", "text2" to "#6ec6ff", "letter" to "#bfe9ff", "panel" to "#050a14", "bar1" to "#4a7bff", "bar2" to "#3ee0ff", "bar3" to "#ffffff", "ui" to "#bfe9ff")),
        Pal("یاقوتی", mapOf("main" to "#ff3b5c", "accent" to "#ff9a3c", "text" to "#ff6b81", "text2" to "#ff2d55", "letter" to "#ffd1d9", "panel" to "#12040a", "bar1" to "#8f0f2e", "bar2" to "#ff3b5c", "bar3" to "#ffd166", "ui" to "#ffb3c1")),
        Pal("زمردی", mapOf("main" to "#2bd67b", "accent" to "#14b8a6", "text" to "#7dffb4", "text2" to "#1db868", "letter" to "#c8ffe0", "panel" to "#04100a", "bar1" to "#0a8f4e", "bar2" to "#2bd67b", "bar3" to "#eaff7a", "ui" to "#b4ffd4")),
        Pal("بنفش", mapOf("main" to "#b266ff", "accent" to "#ff5ccf", "text" to "#d9a8ff", "text2" to "#ff7ad9", "letter" to "#e6c8ff", "panel" to "#0a0614", "bar1" to "#5a3cff", "bar2" to "#b266ff", "bar3" to "#ff7ad9", "ui" to "#e6c8ff")),
        Pal("نقره‌ای", mapOf("main" to "#d8dbe8", "accent" to "#8fa0c8", "text" to "#ffffff", "text2" to "#aab4d0", "letter" to "#eef0f8", "panel" to "#08090e", "bar1" to "#8fa0c8", "bar2" to "#d8dbe8", "bar3" to "#ffffff", "ui" to "#ffffff"))
    )

    val colorLabels = mapOf(
        "main" to "رنگ اصلی (قاب و تزئینات)", "accent" to "رنگ دوم (درخشش)", "text" to "رنگ نام", "text2" to "رنگ نام (انتهای گرادیان)",
        "letter" to "رنگ حرف", "panel" to "رنگ پنل تیره", "bar1" to "میله‌ها: لبه", "bar2" to "میله‌ها: میانه", "bar3" to "میله‌ها: مرکز", "ui" to "رنگ نوار پخش"
    )

    // ------------------------------------------------------------ configuration

    fun cfgOf(o: Overlay): JSONObject = try { if (o.cfg.isEmpty()) JSONObject() else JSONObject(o.cfg) } catch (e: Exception) { JSONObject() }

    fun tplOf(o: Overlay): EqTpl = tpl(cfgOf(o).optString("tpl", "royal")) ?: tpls[0]

    /** Resets the overlay to the defaults of design [id], keeping the texts. */
    fun applyTemplate(o: Overlay, id: String) {
        val t = tpl(id) ?: return
        val old = cfgOf(o)
        val c = JSONObject()
        c.put("tpl", id)
        c.put("letter", old.optString("letter", "K"))
        c.put("heartMode", old.optString("heartMode", "letter"))
        c.put("fx", old.optBoolean("fx", true))
        c.put("bg", old.optString("bg", "none"))
        o.cfg = c.toString()
    }

    fun newOverlay(id: String, name: String): Overlay {
        val o = Overlay()
        o.kind = "eq"
        o.text = name
        o.cx = 0.5f; o.cy = 0.5f; o.scale = 1f
        applyTemplate(o, id)
        return o
    }

    fun stateOf(o: Overlay, t: EqTpl): EqState {
        val c = cfgOf(o)
        val d = t.def
        val st = EqState()
        st.name = o.text
        st.letter = c.optString("letter", "K")
        st.heartMode = c.optString("heartMode", "letter")
        if (st.heartMode == "photo" && o.uri.isNotEmpty()) st.photo = BmpCache.get(o.uri, 1024)
        st.fx = c.optBoolean("fx", true)
        st.barScale = c.optDouble("barScale", 1.0).toFloat()
        st.barStyle = c.optString("barStyle", d.barStyle)
        st.showName = c.optBoolean("showName", d.showName)
        st.showPlayer = c.optBoolean("showPlayer", d.showPlayer)
        st.showLetter = c.optBoolean("showLetter", d.showLetter)
        st.hue = c.optDouble("hue", d.hue.toDouble()).toFloat()
        st.fade = c.optDouble("fade", d.fade.toDouble()).toFloat()
        st.lt = floatArrayOf(
            c.optDouble("ltx", d.lt[0].toDouble()).toFloat(), c.optDouble("lty", d.lt[1].toDouble()).toFloat(), c.optDouble("lts", d.lt[2].toDouble()).toFloat()
        )
        val col = c.optJSONObject("col")
        for ((k, v) in d.col) st.P[k] = hex2rgb(col?.optString(k, v) ?: v)
        return st
    }

    // ------------------------------------------------------------ drawing

    private class Built(val key: String, val tpl: EqTpl, val st: EqState, val L: EqLayers, val frame: Cv, val bg: Cv?)

    private val builtCache = object : ThreadLocal<HashMap<String, Built>>() {
        override fun initialValue(): HashMap<String, Built> = HashMap()
    }

    private fun paintBg(t: EqTpl, st: EqState, c: JSONObject): Cv? {
        val mode = c.optString("bg", "none")
        if (mode == "none") return null
        val W = EQ_W
        val H = EQ_H
        val cv = mk(W, H)
        val g = cv.g
        val bgBmp = if (mode.startsWith("bg_")) EqAssets.bmp("bgdata_" + mode.substring(3) + ".jpg") else null
        if (bgBmp != null) {
            val blur = c.optDouble("bgBlur", 0.0).toFloat()
            if (blur <= 0f) {
                cover(g, bgBmp, 0f, 0f, W.toFloat(), H.toFloat())
            } else {
                val sw = Math.max(24, Math.round(W / (1f + blur * 0.9f)))
                val sh = Math.max(14, Math.round(sw.toFloat() * H / W))
                val small = mk(sw, sh)
                cover(small.g, bgBmp, 0f, 0f, sw.toFloat(), sh.toFloat())
                g.drawImage(small, 0f, 0f, W.toFloat(), H.toFloat())
            }
            val dim = c.optDouble("bgDim", 25.0).toFloat()
            g.fillStyle = Color.argb(Math.round(dim / 100f * 255f), 0, 0, 0); g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
        } else {
            val col = hex2rgb(c.optString("bgColor", "#101018"))
            val gr = g.createRadialGradient(W / 2f, H * 0.5f, 50f, W / 2f, H * 0.5f, W * 0.7f)
            gr.addColorStop(0f, css(lighten(col, 0.12f))); gr.addColorStop(1f, css(darken(col, 0.35f)))
            g.fillStyle = gr; g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
        }
        if (c.optBoolean("mirror", t.def.bgMirror)) {
            val tmp = mk(W / 2, H)
            tmp.g.drawImage(cv.bmp, 0f, 0f, (W / 2).toFloat(), H.toFloat(), 0f, 0f, (W / 2).toFloat(), H.toFloat())
            g.save(); g.translate(W.toFloat(), 0f); g.scale(-1f, 1f); g.drawImage(tmp, 0f, 0f); g.restore()
        }
        t.bgTint(g, st.P)
        val v = g.createRadialGradient(W / 2f, H / 2f, H * 0.35f, W / 2f, H / 2f, W * 0.62f)
        v.addColorStop(0f, Color.argb(0, 0, 0, 0)); v.addColorStop(1f, Color.argb(128, 0, 0, 0))
        g.fillStyle = v; g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
        return cv
    }

    private fun built(o: Overlay): Built? {
        val key = o.cfg + "|" + o.text + "|" + o.uri
        val cache = builtCache.get()!!
        val b = cache[o.id]
        if (b != null && b.key == key) return b
        return try {
            val t = tplOf(o)
            val st = stateOf(o, t)
            val L = t.build(st)
            val nb = Built(key, t, st, L, mk(EQ_W, EQ_H), paintBg(t, st, cfgOf(o)))
            cache[o.id] = nb
            nb
        } catch (e: Throwable) {
            null
        }
    }

    private fun dataAt(p: Project?, o: Overlay, tMs: Long, tplId: String): EqData {
        val now = tMs / 1000f
        val arr = FloatArray(NBAND + 1)
        val real = p != null && EqAudio.fill(p, tMs, arr)
        val raw = FloatArray(NBAND)
        var bass: Float
        if (real) {
            System.arraycopy(arr, 0, raw, 0, NBAND)
            bass = arr[NBAND]
        } else {
            for (j in 0 until NBAND) raw[j] = 0.34f + 0.12f * Math.sin((now * 2.2f + j * 0.35f).toDouble()).toFloat() + 0.06f * Math.sin((now * 3.7f + j * 0.9f).toDouble()).toFloat()
            var b = 0f
            for (j in 0 until 6) b += raw[j]
            bass = b / 6f
        }
        val lv = FloatArray(NBAND)
        for (j in 0 until NBAND) lv[j] = 0.25f * raw[Math.max(0, j - 1)] + 0.5f * raw[j] + 0.25f * raw[Math.min(NBAND - 1, j + 1)]
        val rel = Math.max(0L, tMs - o.startMs) / 1000f
        return EqData(rel, Math.max(0.1f, o.durMs / 1000f), lv, bass, now, null)
    }

    /** Draws the overlay centred on the origin of [c] with width [W]. */
    fun draw(c: Canvas, o: Overlay, W: Float, tMs: Long, alpha: Float) {
        val b = built(o) ?: return
        val p = cur.get()
        b.frame.clear()
        val g = b.frame.g
        if (b.bg != null) g.drawImage(b.bg, 0f, 0f)
        try {
            b.tpl.frame(g, b.L, b.st, dataAt(p, o, tMs, b.tpl.id))
        } catch (e: Throwable) {
        }
        val pt = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        pt.alpha = Math.round(clampF(alpha, 0f, 1f) * 255f)
        val h = W * EQ_H / EQ_W
        c.drawBitmap(b.frame.bmp, null, RectF(-W / 2f, -h / 2f, W / 2f, h / 2f), pt)
    }

    fun size(W: Float): FloatArray = floatArrayOf(W, W * EQ_H / EQ_W)

    // ------------------------------------------------------------ thumbnails

    private val thumbs = HashMap<String, Bitmap?>()

    fun thumb(id: String): Bitmap? {
        synchronized(thumbs) { if (thumbs.containsKey(id)) return thumbs[id] }
        val t = tpl(id) ?: return null
        var out: Bitmap? = null
        try {
            val o = newOverlay(id, "کاظم رفیعی")
            o.cfg = JSONObject(o.cfg).put("bg", "dark").toString()
            val b = built(o)
            if (b != null) {
                b.frame.clear()
                val g = b.frame.g
                if (b.bg != null) g.drawImage(b.bg, 0f, 0f)
                b.tpl.frame(g, b.L, b.st, dataAt(null, o, 1500L, id))
                out = Bitmap.createScaledBitmap(b.frame.bmp, 256, 144, true)
            }
        } catch (e: Throwable) {
        }
        synchronized(thumbs) { thumbs[id] = out }
        return out
    }
}
