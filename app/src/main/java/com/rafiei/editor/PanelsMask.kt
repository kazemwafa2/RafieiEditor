package com.rafiei.editor

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shape mask of the selected clip. */
fun EditorActivity.openMaskPanel(fxT: FxItem? = null) {
    val c = if (fxT == null) (targetClip() ?: return) else null
    val m = ClipMask.fromJson(if (fxT != null) fxT.cfg else c!!.mask)
    val content = FrameLayout(this)

    fun push() {
        if (fxT != null) fxT.cfg = m.toJson() else c!!.mask = if (m.shape == "none") "" else m.toJson()
        stage.top.invalidate()
        engine.refresh()
    }

    fun slider(label: String, mn: Float, mx: Float, v: Float, fmt: (Float) -> String, set: (Float) -> Unit): SliderRow =
        SliderRow(this, label, mn, mx, v, false, fmt, { x -> set(x); push() })

    fun pill(text: String, active: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { onClick() }
        return t
    }

    fun pct(v: Float) = Math.round(v).toString() + "%"

    fun build() {
        content.removeAllViews()
        val col = lin(true)
        if (m.shape != "none") {
            val sv = ScrollView(this)
            val inner = lin(true)
            val isStrips = m.shape == "strips"
            val isHor = m.shape == "hor"
            if (!isStrips) {
                inner.addView(slider(L("جای ماسک: چپ/راست", "Position X"), 0f, 100f, m.cx * 100f, ::pct) { m.cx = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("جای ماسک: بالا/پایین", "Position Y"), 0f, 100f, m.cy * 100f, ::pct) { m.cy = it / 100f }, lp(MATCH, WRAP))
            }
            if (m.shape == "circle" || m.shape == "rect" || m.shape == "heart" || m.shape == "star") {
                inner.addView(slider(L("عرض", "Width"), 5f, 200f, m.sw * 100f, ::pct) { m.sw = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("ارتفاع", "Height"), 5f, 200f, m.sh * 100f, ::pct) { m.sh = it / 100f }, lp(MATCH, WRAP))
            } else if (m.shape == "mirror") {
                inner.addView(slider(L("پهنای نوار", "Band size"), 5f, 200f, m.sh * 100f, ::pct) { m.sh = it / 100f }, lp(MATCH, WRAP))
            } else if (isStrips) {
                inner.addView(slider(L("تعداد نوار", "Strips"), 2f, 16f, m.n.toFloat(), { Math.round(it).toString() }) { m.n = Math.round(it) }, lp(MATCH, WRAP))
                inner.addView(slider(L("پهنای هر نوار", "Strip width"), 5f, 95f, m.sw * 100f, ::pct) { m.sw = it / 100f }, lp(MATCH, WRAP))
            }
            if (!isStrips) inner.addView(slider(L("زاویه", "Rotation"), -180f, 180f, m.rot, { Math.round(it).toString() + "°" }) { m.rot = it }, lp(MATCH, WRAP))
            inner.addView(slider(L("نرمی لبه", "Feather"), 0f, 20f, m.feather * 100f, ::pct) { m.feather = it / 100f }, lp(MATCH, WRAP))
            val row = FrameLayout(this)
            row.setPadding(dp(10), dp(4), dp(10), dp(2))
            row.addView(pill(L("معکوس: " + (if (m.invert) "روشن" else "خاموش"), "Invert: " + (if (m.invert) "on" else "off")), m.invert) {
                m.invert = !m.invert; push(); build()
            }, FrameLayout.LayoutParams(WRAP, WRAP))
            inner.addView(row, lp(MATCH, WRAP))
            val cap = tv(L("رنگ بیرون ماسک", "Outside colour"), 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
            cap.setPadding(dp(14), dp(6), dp(14), dp(2))
            inner.addView(cap, lp(MATCH, WRAP))
            inner.addView(ColorRow(this, m.outColor) { x -> m.outColor = x; push() }, lp(MATCH, WRAP))
            val cap2 = tv(L("حرکت ماسک", "Mask motion"), 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
            cap2.setPadding(dp(14), dp(6), dp(14), dp(2))
            inner.addView(cap2, lp(MATCH, WRAP))
            val cur = Math.max(0, ClipMask.ANIMS.indexOfFirst { it.first == m.anim })
            inner.addView(ChipRow(this, ClipMask.ANIMS.map { L(it.second.first, it.second.second) }, cur) { i ->
                m.anim = ClipMask.ANIMS[i].first; push(); build()
            }, lp(MATCH, WRAP))
            if (m.anim != "none") {
                inner.addView(slider(L("سرعت", "Speed"), 10f, 300f, m.spd * 100f, { String.format(java.util.Locale.US, "%.1f", it / 100f) }) { m.spd = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("دامنه", "Range"), 2f, 100f, m.amp * 100f, ::pct) { m.amp = it / 100f }, lp(MATCH, WRAP))
            }
            val all = FrameLayout(this)
            all.setPadding(dp(10), dp(4), dp(10), dp(2))
            all.addView(pill(L("اعمال روی همه کلیپ‌ها", "Apply to all clips"), false) {
                val js = m.toJson()
                for (k in proj.clips) k.mask = js
                stage.top.invalidate(); engine.refresh()
                toast(L("روی همه کلیپ‌ها اعمال شد", "Applied to all clips"))
            }, FrameLayout.LayoutParams(WRAP, WRAP))
            if (fxT == null) inner.addView(all, lp(MATCH, WRAP))
            sv.addView(inner)
            col.addView(sv, lp(MATCH, dp(230)))
        }
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    val chips = ChipRow(this, ClipMask.SHAPES.map { L(it.second.first, it.second.second) }, Math.max(0, ClipMask.SHAPES.indexOfFirst { it.first == m.shape })) { i ->
        val id = ClipMask.SHAPES[i].first
        m.shape = id
        when (id) {
            "hor" -> { m.cx = 0.5f; m.cy = 0.5f; m.rot = 0f }
            "mirror" -> { m.cx = 0.5f; m.cy = 0.5f; m.sh = 0.5f; m.rot = 0f }
            "strips" -> { m.sw = 0.6f; m.n = 6 }
            "circle" -> { m.sw = 0.6f; m.sh = 0.6f }
            "heart", "star" -> { m.sw = 0.7f; m.sh = 0.7f }
        }
        push(); build()
    }
    build()
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    box.setPadding(0, dp(6), 0, dp(8))
    box.addView(chips, lp(MATCH, WRAP))
    box.addView(content, lp(MATCH, WRAP))
    openPanel(box, false)
}
