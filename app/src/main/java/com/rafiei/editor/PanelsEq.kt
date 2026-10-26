package com.rafiei.editor

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private fun EditorActivity.eqPill(text: String, active: Boolean, onClick: () -> Unit): TextView {
    val t = tv(text, 12.5f, Color.WHITE)
    t.setPadding(dp(14), dp(8), dp(14), dp(8))
    t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
    t.setOnClickListener { onClick() }
    return t
}

private fun EditorActivity.eqCaption(text: String): TextView {
    val t = tv(text, 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
    t.setPadding(dp(14), dp(6), dp(14), dp(2))
    return t
}

/** Equalizer panel: pick a design, texts, colours, bars and background. New overlays are added at the playhead. */
fun EditorActivity.openEqPanel(existing: Overlay?) {
    val snap = proj.snapshot()
    val o: Overlay
    if (existing == null) {
        o = Eq.newOverlay("royal", L("کاظم رفیعی", "Your name"))
        addOverlay(o)
        val total = Math.max(proj.totalMs(), 1000L)
        o.durMs = Math.max(1000L, total - o.startMs)
    } else {
        o = existing
        timeline.selection = Sel("overlay", o.id)
        stage.top.selectedId = o.id
    }
    EqAudio.listener = { engine.refresh() }
    for (p in EqAudio.sourcesOf(proj)) EqAudio.ensure(p)

    fun cfg(): JSONObject = Eq.cfgOf(o)
    fun put(k: String, v: Any) {
        val c = cfg()
        c.put(k, v)
        o.cfg = c.toString()
        changed()
    }

    fun tpl(): EqTpl = Eq.tplOf(o)

    val root = lin(true)
    root.setBackgroundColor(C.PANEL)
    val holder = FrameLayout(this)
    val tabs = listOf(L("طرح", "Design"), L("نوشته", "Text"), L("رنگ", "Colors"), L("میله‌ها", "Bars"), L("پس‌زمینه", "Background"), L("اندازه", "Size"))
    lateinit var show: (Int) -> Unit

    fun scrollBox(v: View): View {
        val sv = ScrollView(this)
        sv.addView(v)
        return sv
    }

    fun designTab(): View {
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        val row = lin(false)
        row.setPadding(dp(8), dp(6), dp(8), dp(6))
        val curId = cfg().optString("tpl", "royal")
        val views = ArrayList<Pair<String, ImageView>>()
        for (t in Eq.tpls) {
            val cell = lin(true)
            cell.gravity = Gravity.CENTER_HORIZONTAL
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val sel = t.id == curId
            iv.background = roundedBg(0xFF111111.toInt(), dpf(8f), if (sel) C.ACCENT else 0xFF444444.toInt(), if (sel) dp(3) else dp(1))
            iv.setPadding(dp(2), dp(2), dp(2), dp(2))
            iv.setOnClickListener {
                put("tpl", t.id)
                Eq.applyTemplate(o, t.id)
                changed()
                show(0)
            }
            cell.addView(iv, lp(dp(132), dp(76)))
            val lab = tv(t.title, 10f, if (sel) C.ACCENT else C.TXT2)
            lab.maxLines = 1
            cell.addView(lab, lp(dp(132), WRAP))
            val l = lp(dp(132), WRAP)
            l.setMargins(dp(4), 0, dp(4), 0)
            row.addView(cell, l)
            views.add(Pair(t.id, iv))
        }
        sv.addView(row)
        Bg.run {
            for ((id, iv) in views) {
                val b = Eq.thumb(id)
                if (b != null) Bg.ui { iv.setImageBitmap(b) }
            }
        }
        return sv
    }

    fun textTab(): View {
        val col = lin(true)
        val c = cfg()
        val t = tpl()
        val row1 = lin(false)
        row1.setPadding(dp(8), dp(8), dp(8), dp(4))
        val nameBtn = eqPill(L("نام: ", "Name: ") + o.text, false) {
            Dialogs.input(this, L("نام", "Name"), o.text) { s -> o.text = s; changed(); show(1) }
        }
        val letBtn = eqPill(L("حرف: ", "Letter: ") + c.optString("letter", "K"), false) {
            Dialogs.input(this, L("حرف داخل قاب", "Letter"), c.optString("letter", "K")) { s -> put("letter", s.take(3)); show(1) }
        }
        row1.addView(nameBtn, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
        row1.addView(letBtn, lp(0, WRAP, 1f))
        col.addView(row1, lp(MATCH, WRAP))
        val row2 = lin(false)
        row2.setPadding(dp(8), dp(4), dp(8), dp(4))
        fun toggle(label: String, key: String, def: Boolean) {
            val on = c.optBoolean(key, def)
            val p = eqPill((if (on) "✓ " else "") + label, on) { put(key, !on); show(1) }
            row2.addView(p, lp(0, WRAP, 1f).also { it.rightMargin = dp(4) })
        }
        toggle(L("نام", "Name"), "showName", t.def.showName)
        toggle(L("نوار پخش", "Player"), "showPlayer", t.def.showPlayer)
        toggle(L("حرف", "Letter"), "showLetter", t.def.showLetter)
        toggle(L("جلوه‌ها", "FX"), "fx", true)
        col.addView(row2, lp(MATCH, WRAP))
        val row3 = lin(false)
        row3.setPadding(dp(8), dp(4), dp(8), dp(4))
        val photoOn = c.optString("heartMode", "letter") == "photo"
        row3.addView(eqPill(L("عکس داخل قاب", "Photo inside"), photoOn) {
            pick(arrayOf("image/*"), false, EditorActivity.REQ_STICKER_IMG) { uris ->
                if (uris.isNotEmpty()) {
                    val ctx = applicationContext
                    val pid = proj.id
                    Bg.run {
                        val bmp = MediaUtil.loadBitmap(ctx, uris[0], 1024)
                        var uri = ""
                        if (bmp != null) {
                            val f = File(Store.mediaDir(ctx, pid), uid() + ".jpg")
                            FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                            uri = android.net.Uri.fromFile(f).toString()
                        }
                        Bg.ui {
                            if (uri.isEmpty()) toast(L("تصویر خوانده نشد", "Could not read the image")) else {
                                o.uri = uri
                                put("heartMode", "photo")
                                show(1)
                            }
                        }
                    }
                }
            }
        }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
        row3.addView(eqPill(L("نمایش حرف", "Use letter"), !photoOn) { put("heartMode", "letter"); show(1) }, lp(0, WRAP, 1f))
        col.addView(row3, lp(MATCH, WRAP))
        return scrollBox(col)
    }

    fun colorsTab(): View {
        val col = lin(true)
        val t = tpl()
        val c = cfg()
        val cols = c.optJSONObject("col") ?: JSONObject()
        val palNames = ArrayList<String>()
        for (p in Eq.palettes) palNames.add(p.name)
        col.addView(eqCaption(L("رنگ‌های آماده", "Presets")))
        col.addView(ChipRow(this, palNames, -1) { i ->
            val pal = Eq.palettes[i]
            val cc = JSONObject()
            for (k in t.def.col.keys) cc.put(k, pal.c[k] ?: t.def.col[k])
            put("col", cc)
            show(2)
        }, lp(MATCH, dp(44)))
        col.addView(eqCaption(L("رنگ هر بخش", "Individual colours")))
        val keys = t.keys.filter { t.def.col.containsKey(it) }
        val labels = keys.map { k -> t.labels[k] ?: Eq.colorLabels[k] ?: k }
        var idx = 0
        val picker = FrameLayout(this)
        fun showPicker() {
            picker.removeAllViews()
            val k = keys[idx]
            val hex = cfg().optJSONObject("col")?.optString(k, t.def.col[k]!!) ?: t.def.col[k]!!
            val cur = Color.parseColor(hex)
            picker.addView(ColorRow(this, cur) { v ->
                val cc = cfg().optJSONObject("col") ?: JSONObject()
                cc.put(k, String.format("#%06x", v and 0xFFFFFF))
                put("col", cc)
            }, FrameLayout.LayoutParams(MATCH, dp(44)))
        }
        col.addView(ChipRow(this, labels, 0) { i -> idx = i; showPicker() }, lp(MATCH, dp(44)))
        col.addView(picker, lp(MATCH, dp(44)))
        showPicker()
        if (cols.length() > 0) {
            col.addView(eqPill(L("بازگشت به رنگ‌های اصلی طرح", "Reset colours"), false) {
                val cc = cfg(); cc.remove("col"); o.cfg = cc.toString(); changed(); show(2)
            }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(6), dp(10), dp(6)) })
        }
        return scrollBox(col)
    }

    fun barsTab(): View {
        val col = lin(true)
        val c = cfg()
        val t = tpl()
        col.addView(eqCaption(L("حالت رنگ میله‌ها", "Bar colour mode")))
        val styles = listOf("grad", "alt", "solid")
        val cur = styles.indexOf(c.optString("barStyle", t.def.barStyle))
        col.addView(ChipRow(this, listOf(L("گرادیان", "Gradient"), L("متناوب", "Alternate"), L("تک‌رنگ", "Solid")), if (cur < 0) 0 else cur) { put("barStyle", styles[it]) }, lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("ارتفاع", "Height"), 40f, 130f, c.optDouble("barScale", 1.0).toFloat() * 100f, false, { Math.round(it).toString() }, { v -> put("barScale", v / 100.0) }), lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("محو انتها", "Fade"), 0f, 100f, c.optDouble("fade", t.def.fade.toDouble()).toFloat(), false, { Math.round(it).toString() }, { v -> put("fade", v.toDouble()) }), lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("تغییر رنگ طرح", "Hue"), 0f, 360f, c.optDouble("hue", 0.0).toFloat(), false, { Math.round(it).toString() }, { v -> put("hue", v.toDouble()) }), lp(MATCH, dp(44)))
        return scrollBox(col)
    }

    fun bgTab(): View {
        val col = lin(true)
        val c = cfg()
        val t = tpl()
        val names = ArrayList<String>()
        val ids = ArrayList<String>()
        names.add(L("بدون", "None")); ids.add("none")
        names.add(L("تیره", "Dark")); ids.add("dark")
        for ((id, n) in EQ_BG_META) { names.add(n); ids.add("bg_$id") }
        val cur = ids.indexOf(c.optString("bg", "none"))
        col.addView(eqCaption(L("پس‌زمینه‌ی طرح (روی ویدیو قرار می‌گیرد)", "Design background")))
        col.addView(ChipRow(this, names, if (cur < 0) 0 else cur) { put("bg", ids[it]) }, lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("تاری", "Blur"), 0f, 40f, c.optDouble("bgBlur", 0.0).toFloat(), false, { Math.round(it).toString() }, { v -> put("bgBlur", v.toDouble()) }), lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("تیرگی", "Dim"), 0f, 85f, c.optDouble("bgDim", 25.0).toFloat(), false, { Math.round(it).toString() }, { v -> put("bgDim", v.toDouble()) }), lp(MATCH, dp(44)))
        val mir = c.optBoolean("mirror", t.def.bgMirror)
        col.addView(eqPill((if (mir) "✓ " else "") + L("حالت آینه‌ای", "Mirror"), mir) { put("mirror", !mir); show(4) }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(6), dp(10), dp(4)) })
        col.addView(eqCaption(L("رنگ ساده", "Plain colour")))
        col.addView(ColorRow(this, Color.parseColor(c.optString("bgColor", "#101018"))) { v -> put("bgColor", String.format("#%06x", v and 0xFFFFFF)) }, lp(MATCH, dp(44)))
        return scrollBox(col)
    }

    fun sizeTab(): View {
        val col = lin(true)
        col.addView(SliderRow(this, L("اندازه", "Scale"), 20f, 300f, o.scale * 100f, false, { Math.round(it).toString() + "%" }, { v -> o.scale = v / 100f; changed() }), lp(MATCH, dp(44)))
        col.addView(SliderRow(this, L("شفافیت", "Opacity"), 5f, 100f, o.opacity * 100f, false, { Math.round(it).toString() + "%" }, { v -> o.opacity = v / 100f; changed() }), lp(MATCH, dp(44)))
        col.addView(eqPill(L("پخش تا پایان ویدیو", "Until the end"), false) {
            o.durMs = Math.max(1000L, Math.max(proj.totalMs(), 1000L) - o.startMs)
            changed()
        }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(8), dp(10), dp(4)) })
        col.addView(tv(L("با انگشت روی تصویر، طرح را جابه‌جا و بزرگ کنید.", "Drag and pinch on the preview to move or resize."), 11f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(6), dp(10), dp(6)) })
        return scrollBox(col)
    }

    show = { i ->
        holder.removeAllViews()
        val v = when (i) {
            0 -> designTab()
            1 -> textTab()
            2 -> colorsTab()
            3 -> barsTab()
            4 -> bgTab()
            else -> sizeTab()
        }
        holder.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }
    root.addView(ChipRow(this, tabs, 0) { show(it) }, lp(MATCH, dp(46)))
    root.addView(holder, lp(MATCH, dp(176)))
    show(0)
    openPanel(root, true, snap)
}
