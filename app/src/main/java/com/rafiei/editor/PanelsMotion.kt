package com.rafiei.editor

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Per-clip "Motion" tool (photo/video motion presets from the old app, part 2). */
fun EditorActivity.openMotionPanel() {
    val c0 = selClip() ?: return
    var all = false
    var cat = Math.max(0, Motion.cats.indexOfFirst { p -> Motion.presets.any { it.cat == p.first && it.id == c0.motion } }.let { if (it < 0) 0 else it })
    val listHost = LinearLayout(this)
    listHost.orientation = LinearLayout.VERTICAL
    val sv = ScrollView(this)
    sv.addView(listHost, ViewGroup.LayoutParams(MATCH, WRAP))

    fun applyId(id: String) {
        val targets = if (all) proj.clips.toList() else listOf(c0)
        for (c in targets) {
            c.motion = id
            c.motionAmt = c0.motionAmt
        }
        changed()
        if (!engine.playing) {
            engine.seek(proj.clipStart(proj.clips.indexOf(c0)) + 5)
            engine.play()
        }
    }

    fun item(label: String, id: String): TextView {
        val t = tv(label, 13f, Color.WHITE, false, Gravity.START or Gravity.CENTER_VERTICAL)
        t.setPadding(dp(16), dp(10), dp(16), dp(10))
        t.background = roundedBg(if (c0.motion == id) C.ACCENT else C.CARD, dpf(10f))
        return t
    }

    fun fill() {
        listHost.removeAllViews()
        val none = item(L("بدون حرکت", "None"), "")
        none.setOnClickListener { applyId(""); fill() }
        listHost.addView(none, lp(MATCH, WRAP).also { (it as LinearLayout.LayoutParams).setMargins(dp(10), dp(2), dp(10), dp(2)) })
        val catId = Motion.cats[cat].first
        for (p in Motion.presets) {
            if (p.cat != catId) continue
            val t = item(p.name, p.id)
            t.setOnClickListener { applyId(p.id); fill() }
            listHost.addView(t, lp(MATCH, WRAP).also { (it as LinearLayout.LayoutParams).setMargins(dp(10), dp(2), dp(10), dp(2)) })
        }
    }

    val chips = ChipRow(this, Motion.cats.map { it.second }, cat) { i -> cat = i; fill() }
    val amt = SliderRow(this, L("شدت", "Intensity"), 0f, 1.5f, c0.motionAmt, false,
        { String.format(Locale.US, "%d%%", Math.round(it * 100f)) }, { v ->
            val targets = if (all) proj.clips.toList() else listOf(c0)
            for (c in targets) c.motionAmt = v
            changed()
        })
    val allChip = tv(L("اعمال به همه‌ی کلیپ‌ها", "Apply to all clips"), 12.5f, Color.WHITE)
    allChip.setPadding(dp(14), dp(8), dp(14), dp(8))
    allChip.background = roundedBg(C.CARD, dpf(16f))
    allChip.setOnClickListener {
        all = !all
        allChip.background = roundedBg(if (all) C.ACCENT else C.CARD, dpf(16f))
    }
    val allWrap = FrameLayout(this)
    allWrap.setPadding(dp(10), dp(4), dp(10), dp(4))
    allWrap.addView(allChip, FrameLayout.LayoutParams(WRAP, WRAP))

    val col = lin(true)
    col.setBackgroundColor(C.PANEL)
    col.setPadding(0, dp(6), 0, dp(8))
    col.addView(chips, lp(MATCH, WRAP))
    col.addView(sv, lp(MATCH, dp(190)))
    col.addView(amt, lp(MATCH, WRAP))
    col.addView(allWrap, lp(MATCH, WRAP))
    fill()
    openPanel(col, true)
}
