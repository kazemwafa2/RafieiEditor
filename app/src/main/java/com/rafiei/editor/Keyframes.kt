package com.rafiei.editor

import android.graphics.Color
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Keyframes of a clip: "t,zoom,panX,panY,rot;..." (t in ms from the clip start; values relative to the clip's own transform). */
object Keyframes {
    private val cache = HashMap<String, Array<FloatArray>>()

    fun parse(s: String): Array<FloatArray> {
        if (s.isEmpty()) return emptyArray()
        synchronized(cache) {
            cache[s]?.let { return it }
        }
        val out = ArrayList<FloatArray>()
        for (part in s.split(";")) {
            val f = part.split(",")
            if (f.size < 5) continue
            try {
                out.add(floatArrayOf(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat()))
            } catch (e: Exception) { }
        }
        out.sortBy { it[0] }
        val arr = out.toTypedArray()
        synchronized(cache) {
            if (cache.size > 200) cache.clear()
            cache[s] = arr
        }
        return arr
    }

    fun write(keys: List<FloatArray>): String =
        keys.sortedBy { it[0] }.joinToString(";") { String.format(Locale.US, "%.0f,%.4f,%.4f,%.4f,%.2f", it[0], it[1], it[2], it[3], it[4]) }

    /** Interpolated [zoom, panX, panY, rot] at clip time [ltMs]. */
    fun valueAt(keys: Array<FloatArray>, ltMs: Long): FloatArray {
        val t = ltMs.toFloat()
        if (keys.isEmpty()) return floatArrayOf(1f, 0f, 0f, 0f)
        if (t <= keys[0][0]) return floatArrayOf(keys[0][1], keys[0][2], keys[0][3], keys[0][4])
        val last = keys[keys.size - 1]
        if (t >= last[0]) return floatArrayOf(last[1], last[2], last[3], last[4])
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (t >= a[0] && t <= b[0]) {
                var u = if (b[0] - a[0] < 1f) 1f else (t - a[0]) / (b[0] - a[0])
                u = u * u * (3f - 2f * u)
                return floatArrayOf(a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u, a[4] + (b[4] - a[4]) * u)
            }
        }
        return floatArrayOf(1f, 0f, 0f, 0f)
    }

    fun applyToDyn(c: Clip, ltMs: Long, d: Dyn) {
        val k = parse(c.kf)
        if (k.isEmpty()) return
        val v = valueAt(k, ltMs)
        d.s *= v[0]
        d.tx += v[1]
        d.ty += v[2]
        d.rot += v[3]
    }
}

/** Keyframe editor: zoom, position and rotation animated over the clip. */
fun EditorActivity.openKeyframePanel() {
    val c = selClip() ?: targetClip()
    if (c == null) { toast(L("ابتدا یک کلیپ را انتخاب کنید.", "Select a clip first.")); return }
    val snap = proj.snapshot()
    val idx = proj.clips.indexOf(c)
    val clipStart = proj.clipStart(idx)
    val keys = ArrayList<FloatArray>()
    for (k in Keyframes.parse(c.kf)) keys.add(k)
    var z = 1f; var px = 0f; var py = 0f; var rt = 0f

    val col = lin(true)
    col.setBackgroundColor(C.PANEL)
    val inner = lin(true)
    lateinit var rebuild: () -> Unit

    fun save() {
        c.kf = Keyframes.write(keys)
        changed()
    }

    fun pill(text: String, active: Boolean = false, click: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { click() }
        return t
    }

    fun loadAt(ms: Long) {
        val v = Keyframes.valueAt(keys.toTypedArray(), ms)
        z = v[0]; px = v[1]; py = v[2]; rt = v[3]
    }

    rebuild = {
        inner.removeAllViews()
        val rel = Math.max(0L, Math.min(c.durMs(), engine.timeMs - clipStart))
        inner.addView(tv(L("لحظه‌ی فعلی در کلیپ: ", "Playhead in clip: ") + String.format(Locale.US, "%.2f s", rel / 1000f), 12.5f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL).also { it.setPadding(dp(14), dp(6), dp(14), dp(2)) }, lp(MATCH, WRAP))
        inner.addView(SliderRow(this, L("زوم", "Zoom"), 0.5f, 4f, z, false, { String.format(Locale.US, "×%.2f", it) }, { v -> z = v }), lp(MATCH, dp(44)))
        inner.addView(SliderRow(this, L("جابه‌جایی افقی", "Pan X"), -0.8f, 0.8f, px, true, { String.format(Locale.US, "%.2f", it) }, { v -> px = v }), lp(MATCH, dp(44)))
        inner.addView(SliderRow(this, L("جابه‌جایی عمودی", "Pan Y"), -0.8f, 0.8f, py, true, { String.format(Locale.US, "%.2f", it) }, { v -> py = v }), lp(MATCH, dp(44)))
        inner.addView(SliderRow(this, L("چرخش", "Rotate"), -90f, 90f, rt, true, { Math.round(it).toString() + "°" }, { v -> rt = v }), lp(MATCH, dp(44)))
        val r = lin(false)
        r.setPadding(dp(8), dp(4), dp(8), dp(4))
        r.addView(pill(L("◆ افزودن/به‌روزرسانی کلید در این لحظه", "◆ Set key at playhead"), true) {
            val t = Math.max(0L, Math.min(c.durMs(), engine.timeMs - clipStart)).toFloat()
            keys.removeAll { Math.abs(it[0] - t) < 60f }
            keys.add(floatArrayOf(t, z, px, py, rt))
            keys.sortBy { it[0] }
            save()
            rebuild()
        }, lp(0, WRAP, 1f))
        inner.addView(r, lp(MATCH, WRAP))
        inner.addView(tv(L("کلیدها (روی هر کدام بزنید تا بپرد)", "Keys (tap to jump)"), 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL).also { it.setPadding(dp(14), dp(8), dp(14), dp(2)) }, lp(MATCH, WRAP))
        for ((i, k) in keys.withIndex()) {
            val row = lin(false)
            row.setPadding(dp(10), dp(2), dp(10), dp(2))
            val t = tv(String.format(Locale.US, "◆ %.2fs   ×%.2f   (%.2f, %.2f)   %.0f°", k[0] / 1000f, k[1], k[2], k[3], k[4]), 12.5f, Color.WHITE, false, Gravity.START or Gravity.CENTER_VERTICAL)
            t.setPadding(dp(12), dp(8), dp(12), dp(8))
            t.background = roundedBg(C.CARD, dpf(10f))
            t.setOnClickListener {
                engine.pause()
                engine.seek(clipStart + k[0].toLong())
                z = k[1]; px = k[2]; py = k[3]; rt = k[4]
                rebuild()
            }
            row.addView(t, lp(0, WRAP, 1f))
            val del = tv("✕", 14f, C.ACCENT, true)
            del.setPadding(dp(14), dp(8), dp(14), dp(8))
            del.setOnClickListener { keys.removeAt(i); save(); rebuild() }
            row.addView(del, lp(WRAP, WRAP))
            inner.addView(row, lp(MATCH, WRAP))
        }
        if (keys.isNotEmpty()) {
            val r2 = lin(false)
            r2.setPadding(dp(8), dp(6), dp(8), dp(4))
            r2.addView(pill(L("حذف همه‌ی کلیدها", "Clear all keys")) { keys.clear(); save(); rebuild() }, lp(0, WRAP, 1f))
            inner.addView(r2, lp(MATCH, WRAP))
        }
    }
    loadAt(Math.max(0L, engine.timeMs - clipStart))
    rebuild()
    val sv = ScrollView(this)
    sv.addView(inner)
    col.addView(sv, lp(MATCH, dp(320)))
    openPanel(col, true, snap)
}
