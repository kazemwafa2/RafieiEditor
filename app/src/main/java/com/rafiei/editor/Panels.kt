package com.rafiei.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

// ------------------------------------------------------------------ small building blocks

private fun EditorActivity.box(vararg views: View): LinearLayout {
    val l = lin(true)
    l.setBackgroundColor(C.PANEL)
    l.setPadding(0, dp(6), 0, dp(8))
    for (v in views) l.addView(v, lp(MATCH, WRAP))
    return l
}

private fun EditorActivity.caption(text: String): TextView {
    val t = tv(text, 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
    t.setPadding(dp(14), dp(6), dp(14), dp(2))
    return t
}

private fun EditorActivity.pillButton(text: String, active: Boolean = false, onClick: () -> Unit): TextView {
    val t = tv(text, 12.5f, Color.WHITE)
    t.setPadding(dp(14), dp(8), dp(14), dp(8))
    t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
    t.setOnClickListener { onClick() }
    return t
}

private fun EditorActivity.hScroll(row: LinearLayout): HorizontalScrollView {
    val sv = HorizontalScrollView(this)
    sv.isHorizontalScrollBarEnabled = false
    sv.addView(row)
    return sv
}

private fun fmtPct(v: Float): String = Math.round(v).toString() + "%"
private fun fmtSec(v: Float): String = String.format(Locale.US, "%.1fs", v)
private fun fmtX(v: Float): String = String.format(Locale.US, "%.2fx", v)

fun EditorActivity.swapPanel(view: View) {
    panelHost.removeAllViews()
    panelHost.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
}

private fun EditorActivity.targets(all: Boolean): List<Clip> {
    if (all) return proj.clips
    val c = targetClip() ?: return emptyList()
    return listOf(c)
}

// ------------------------------------------------------------------ canvas

private val RATIOS = listOf("orig", "9:16", "16:9", "1:1", "4:5", "5:4", "4:3", "3:4", "3:2", "2:3", "21:9", "2:1", "1:2")

fun EditorActivity.openCanvasPanel() {
    val ratioLabels = RATIOS.map { if (it == "orig") L("اصلی", "Original") else it }
    val ratioRow = ChipRow(this, ratioLabels, Math.max(0, RATIOS.indexOf(proj.ratio))) { i ->
        proj.ratio = RATIOS[i]
        changed()
    }
    val content = FrameLayout(this)
    val modes = listOf("blur", "color", "grad")
    fun showMode(m: String) {
        content.removeAllViews()
        val col = lin(true)
        if (m == "color") {
            col.addView(ColorRow(this, proj.bgColor) { c ->
                proj.bgMode = "color"
                proj.bgColor = c
                changed()
            }, lp(MATCH, WRAP))
        } else if (m == "grad") {
            col.addView(ColorRow(this, proj.bgColor) { c ->
                proj.bgMode = "grad"
                proj.bgColor = c
                changed()
            }, lp(MATCH, WRAP))
            col.addView(ColorRow(this, proj.bgColor2) { c ->
                proj.bgMode = "grad"
                proj.bgColor2 = c
                changed()
            }, lp(MATCH, WRAP))
        } else {
            col.addView(caption(L("پس‌زمینه‌ی تار از خود ویدیو ساخته می‌شود.", "A blurred copy of your clip fills the background.")), lp(MATCH, WRAP))
        }
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    }
    val modeRow = ChipRow(this, listOf(L("تار", "Blur"), L("رنگ", "Color"), L("گرادیان", "Gradient")), Math.max(0, modes.indexOf(proj.bgMode))) { i ->
        proj.bgMode = modes[i]
        showMode(modes[i])
        changed()
    }
    showMode(proj.bgMode)
    openPanel(box(caption(L("نسبت تصویر", "Aspect ratio")), ratioRow, caption(L("پس‌زمینه", "Background")), modeRow, content), true)
}

// ------------------------------------------------------------------ filters and adjust

private class FilterTile(ctx: Context, val def: FilterDef, val clip: Clip?, val timeMs: Long) : View(ctx) {
    var selectedTile = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fake = Clip()

    init {
        fake.filter = def.id
        fake.filterAmt = 1f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat() - dpf(18f)
        val r = RectF(dpf(2f), dpf(2f), w - dpf(2f), h)
        p.style = Paint.Style.FILL
        p.color = 0xFF444444.toInt()
        c.drawRoundRect(r, dpf(8f), dpf(8f), p)
        if (clip != null) {
            val path = MediaUtil.pathOf(clip.uri)
            val b = ThumbCache.frame(path, clip.isImage, timeMs, 160) { invalidate() }
            if (b != null) {
                val sc = Math.max(r.width() / b.width, r.height() / b.height)
                val bw = b.width * sc
                val bh = b.height * sc
                val dst = RectF(r.centerX() - bw / 2f, r.centerY() - bh / 2f, r.centerX() + bw / 2f, r.centerY() + bh / 2f)
                val bp = Paint(Paint.FILTER_BITMAP_FLAG)
                bp.colorFilter = ColorMatrixColorFilter(ColorMatrix(Fx.baseColor(fake).toAndroid()))
                c.save()
                val clipP = android.graphics.Path()
                clipP.addRoundRect(r, dpf(8f), dpf(8f), android.graphics.Path.Direction.CW)
                c.clipPath(clipP)
                c.drawBitmap(b, null, dst, bp)
                c.restore()
            }
        }
        if (selectedTile) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = dpf(2.5f)
            p.color = C.ACCENT
            c.drawRoundRect(r, dpf(8f), dpf(8f), p)
        }
        tp.color = if (selectedTile) C.ACCENT else C.TXT2
        tp.textSize = dpf(10.5f)
        tp.textAlign = Paint.Align.CENTER
        c.drawText(def.name(), w / 2f, height - dpf(4f), tp)
    }
}

fun EditorActivity.openFilterPanel(startTab: Int) {
    var applyAll = false
    val base = targetClip() ?: run {
        toast(L("ابتدا یک کلیپ اضافه کنید.", "Add a clip first."))
        return
    }
    val playT = run {
        val i = proj.clipIndexAt(engine.timeMs)
        if (i >= 0) proj.clips[i].trimStart + ((engine.timeMs - proj.clipStart(i)) * proj.clips[i].speed).toLong() else 0L
    }
    val content = FrameLayout(this)
    val tabs = ChipRow(this, listOf(L("فیلتر", "Filter"), L("تنظیم", "Adjust")), startTab) { i -> showTab(this, content, i, { applyAll }, base, playT) }
    val allChip = pillButton(L("اعمال به همه", "Apply to all")) { }
    allChip.setOnClickListener {
        applyAll = !applyAll
        allChip.background = roundedBg(if (applyAll) C.ACCENT else C.CARD, dpf(16f))
        if (applyAll) {
            val src = base
            for (c in proj.clips) {
                c.filter = src.filter; c.filterAmt = src.filterAmt; c.adj = src.adj.copyOf()
            }
            changed()
        }
    }
    val head = lin(false)
    head.gravity = Gravity.CENTER_VERTICAL
    head.addView(tabs, lp(0, WRAP, 1f))
    head.addView(allChip, lp(WRAP, WRAP).also { it.rightMargin = dp(10) })
    showTab(this, content, startTab, { applyAll }, base, playT)
    openPanel(box(head, content), true)
}

private fun showTab(act: EditorActivity, content: FrameLayout, tab: Int, all: () -> Boolean, base: Clip, playT: Long) {
    content.removeAllViews()
    if (tab == 0) {
        val row = act.lin(false)
        row.setPadding(act.dp(8), act.dp(4), act.dp(8), act.dp(4))
        val tiles = ArrayList<FilterTile>()
        val amtRow = SliderRow(act, L("شدت", "Amount"), 0f, 100f, base.filterAmt * 100f, false, { fmtPct(it) }, { v ->
            for (c in act.targets(all())) c.filterAmt = v / 100f
            act.changed()
        })
        for (f in Fx.FILTERS) {
            val t = FilterTile(act, f, base, playT)
            t.selectedTile = base.filter == f.id
            t.setOnClickListener {
                for (c in act.targets(all())) {
                    c.filter = f.id
                    if (c.filterAmt <= 0.01f) c.filterAmt = 1f
                }
                for (x in tiles) {
                    x.selectedTile = x.def.id == f.id
                    x.invalidate()
                }
                amtRow.visibility = if (f.id == "none") View.INVISIBLE else View.VISIBLE
                amtRow.set(base.filterAmt * 100f)
                act.changed()
            }
            tiles.add(t)
            row.addView(t, lp(act.dp(76), act.dp(88)))
        }
        amtRow.visibility = if (base.filter == "none") View.INVISIBLE else View.VISIBLE
        val col = act.lin(true)
        col.addView(act.hScrollOf(row), lp(MATCH, WRAP))
        col.addView(amtRow, lp(MATCH, WRAP))
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    } else {
        val names = listOf(
            L("روشنایی", "Brightness"), L("کنتراست", "Contrast"), L("اشباع", "Saturation"), L("دما", "Warmth"), L("تینت", "Tint"),
            L("محو", "Fade"), L("رنگ", "Hue"), L("حاشیه تیره", "Vignette"), L("دانه", "Grain")
        )
        var cur = 0
        val col = act.lin(true)
        val sliderHolder = FrameLayout(act)
        fun buildSlider() {
            sliderHolder.removeAllViews()
            val idx = cur
            val uni = idx == Adj.VIGNETTE || idx == Adj.GRAIN
            val v = base.adj[idx]
            val row = SliderRow(act, names[idx], if (uni) 0f else -100f, 100f, v * 100f, !uni, { Math.round(it).toString() }, { x ->
                for (c in act.targets(all())) c.adj[idx] = x / 100f
                act.changed()
            })
            sliderHolder.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        val chips = ChipRow(act, names, 0) { i ->
            cur = i
            buildSlider()
        }
        val reset = act.tv(L("بازنشانی", "Reset"), 12f, C.ACCENT)
        reset.setPadding(act.dp(14), act.dp(6), act.dp(14), act.dp(6))
        reset.setOnClickListener {
            for (c in act.targets(all())) c.adj = FloatArray(Adj.COUNT)
            buildSlider()
            act.changed()
        }
        col.addView(chips, lp(MATCH, WRAP))
        col.addView(sliderHolder, lp(MATCH, WRAP))
        col.addView(reset, lp(WRAP, WRAP).also { it.leftMargin = act.dp(8) })
        buildSlider()
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    }
}

private fun EditorActivity.hScrollOf(row: LinearLayout): HorizontalScrollView = hScroll(row)

// ------------------------------------------------------------------ effects

private class FxTile(ctx: Context, val def: FxDef) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        p.style = Paint.Style.FILL
        p.color = withAlpha(def.color or 0xFF000000.toInt(), 0.9f)
        val r = RectF(dpf(6f), dpf(4f), w - dpf(6f), dpf(60f))
        c.drawRoundRect(r, dpf(12f), dpf(12f), p)
        p.color = Color.WHITE
        Icons.draw(c, if (def.overlay) "sticker" else "effect", r.centerX(), r.centerY(), dpf(26f), p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = dpf(10.5f)
        p.color = C.TXT
        c.drawText(def.name(), w / 2f, height - dpf(6f), p)
    }
}

fun EditorActivity.openEffectPanel() {
    val replace = selFx()
    val slHolder = FrameLayout(this)
    fun buildIntensity() {
        slHolder.removeAllViews()
        val f = selFx()
        if (f != null) {
            slHolder.addView(SliderRow(this, L("شدت", "Intensity"), 5f, 100f, f.amt * 100f, false, { fmtPct(it) }, { v ->
                f.amt = v / 100f
                engine.refresh()
            }), FrameLayout.LayoutParams(MATCH, WRAP))
        } else {
            slHolder.addView(caption(L("روی یک افکت بزنید تا در جای نشانگر اضافه شود؛ سپس روی تایم‌لاین طولش را تنظیم کنید.", "Tap an effect to add it at the playhead, then trim it on the timeline.")), FrameLayout.LayoutParams(MATCH, WRAP))
        }
    }

    fun grid(overlay: Boolean): HorizontalScrollView {
        val row = lin(false)
        row.setPadding(dp(6), dp(4), dp(6), dp(4))
        for (d in Fx.FX_LIST) {
            if (d.overlay != overlay) continue
            val t = FxTile(this, d)
            t.setOnClickListener {
                val cur = selFx()
                if (replace != null && cur != null) {
                    cur.fx = d.id
                    changed()
                } else {
                    val f = FxItem()
                    f.fx = d.id
                    f.durMs = if (d.overlay) 4000 else 2000
                    addFx(f)
                }
                buildIntensity()
                if (!engine.playing) engine.play()
            }
            row.addView(t, lp(dp(78), dp(84)))
        }
        return hScroll(row)
    }

    val holder = FrameLayout(this)
    fun show(i: Int) {
        holder.removeAllViews()
        holder.addView(grid(i == 1), FrameLayout.LayoutParams(MATCH, WRAP))
    }
    val tabs = ChipRow(this, listOf(L("حرکت و رنگ", "Motion & color"), L("روکش‌ها", "Overlays")), 0) { i -> show(i) }
    show(0)
    buildIntensity()
    openPanel(box(tabs, holder, slHolder), false)
}

fun EditorActivity.openFxIntensityPanel() {
    val f = selFx() ?: return
    openPanel(box(SliderRow(this, L("شدت", "Intensity"), 5f, 100f, f.amt * 100f, false, { fmtPct(it) }, { v ->
        f.amt = v / 100f
        engine.refresh()
    })), false)
}

// ------------------------------------------------------------------ stickers

private class ShapeCell(ctx: Context, val id: String, val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = color
        c.save()
        c.translate(width / 2f, height / 2f)
        val s = Math.min(width, height) * 0.7f
        c.scale(s, s)
        c.drawPath(Shapes.path(id), p)
        c.restore()
    }
}

private val SHAPE_COLORS = intArrayOf(0xFFFF4D6D.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(), 0xFF32ADE6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF9500.toInt())

fun EditorActivity.openStickerPanel() {
    val gridHolder = FrameLayout(this)
    val labels = ArrayList<String>()
    labels.add(L("شکل‌ها", "Shapes"))
    for (c in Library.EMOJI) labels.add(c.name())
    labels.add(L("تصویر من", "My image"))

    fun showTab(i: Int) {
        gridHolder.removeAllViews()
        if (i == labels.size - 1) {
            val b = pillButton(L("انتخاب تصویر از گالری (PNG شفاف هم می‌شود)", "Pick an image from the gallery (transparent PNG works)")) { pickStickerImage() }
            val l = lin(true)
            l.gravity = Gravity.CENTER
            l.addView(b, lp(WRAP, WRAP))
            gridHolder.addView(l, FrameLayout.LayoutParams(MATCH, dp(150)))
            return
        }
        val items: List<String> = if (i == 0) Shapes.IDS else Library.EMOJI[i - 1].items
        val gv = GridView(this)
        gv.numColumns = 6
        gv.verticalSpacing = dp(2)
        gv.adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val it = items[position]
                val v: View = if (i == 0) ShapeCell(this@openStickerPanel, it, SHAPE_COLORS[position % SHAPE_COLORS.size]) else {
                    val t = this@openStickerPanel.tv(it, 26f)
                    t
                }
                v.layoutParams = AbsListViewLp(dp(54))
                return v
            }
        }
        gv.setOnItemClickListener { _, _, position, _ ->
            val o = Overlay()
            o.kind = "sticker"
            if (i == 0) {
                o.glyph = "shape:" + items[position]
                o.color = SHAPE_COLORS[position % SHAPE_COLORS.size]
            } else {
                o.glyph = items[position]
            }
            addOverlay(o)
        }
        gridHolder.addView(gv, FrameLayout.LayoutParams(MATCH, dp(172)))
    }

    val tabs = ChipRow(this, labels, 0) { i -> showTab(i) }
    showTab(0)
    openPanel(box(tabs, gridHolder), false)
}

private fun EditorActivity.AbsListViewLp(h: Int): android.widget.AbsListView.LayoutParams = android.widget.AbsListView.LayoutParams(MATCH, h)

// ------------------------------------------------------------------ text

fun EditorActivity.openTextPanel(existing: Overlay?, startTab: Int?) {
    val pre = proj.snapshot()
    var o = existing
    var created = false
    if (o == null) {
        val n = Overlay()
        n.kind = "text"
        n.text = L("متن", "Text")
        n.size = 0.085f
        n.bold = true
        n.outlineW = 0.3f
        addOverlay(n)
        o = n
        created = true
    }
    val t: Overlay = o
    timeline.selection = Sel("overlay", t.id)
    stage.top.selectedId = t.id
    if (!isPhoto && (engine.timeMs < t.startMs || engine.timeMs >= t.endMs())) engine.seek(t.startMs)

    val et = EditText(this)
    et.setText(t.text)
    et.setTextColor(Color.WHITE)
    et.setHintTextColor(C.TXT2)
    et.hint = L("متن را بنویسید…", "Type your text…")
    et.textSize = 16f
    et.minLines = 1
    et.maxLines = 3
    et.background = roundedBg(C.CARD, dpf(10f))
    et.setPadding(dp(12), dp(8), dp(12), dp(8))
    et.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            t.text = s?.toString() ?: ""
            stage.top.invalidate()
            timeline.relayout()
        }
    })
    val etWrap = FrameLayout(this)
    etWrap.setPadding(dp(10), dp(2), dp(10), dp(4))
    etWrap.addView(et, FrameLayout.LayoutParams(MATCH, WRAP))

    val content = FrameLayout(this)
    val tabLabels = listOf(L("استایل", "Styles"), L("فونت", "Font"), L("رنگ", "Color"), L("قالب", "Format"), L("انیمیشن", "Animate"))

    fun refresh() {
        stage.top.invalidate()
        engine.refresh()
    }

    fun showTab(i: Int) {
        content.removeAllViews()
        when (i) {
            0 -> {
                val row = lin(false)
                row.setPadding(dp(8), dp(4), dp(8), dp(4))
                for (s in Library.TEXT_STYLES) {
                    val cell = tv("Aa", 20f, s.color)
                    cell.typeface = Fonts.get(s.font, s.bold, false)
                    cell.background = roundedBg(if (s.bgColor == 0xFFFFFFFF.toInt() && s.bg > 0.5f) 0xFFDDDDDD.toInt() else C.CARD, dpf(10f))
                    if (s.color == 0xFF000000.toInt()) cell.background = roundedBg(0xFFEEEEEE.toInt(), dpf(10f))
                    cell.setOnClickListener {
                        t.color = s.color
                        t.outlineW = s.outline
                        t.outlineColor = s.outlineColor
                        t.shadow = s.shadow
                        t.bgAlpha = s.bg
                        t.bgColor = s.bgColor
                        t.bold = s.bold
                        t.font = s.font
                        refresh()
                    }
                    val lpc = lp(dp(66), dp(54))
                    lpc.setMargins(dp(4), 0, dp(4), 0)
                    row.addView(cell, lpc)
                }
                content.addView(hScroll(row), FrameLayout.LayoutParams(MATCH, WRAP))
            }
            1 -> {
                val names = ArrayList<String>()
                names.addAll(Fonts.BUILTIN)
                names.addAll(Fonts.imported(this))
                val row = lin(false)
                row.setPadding(dp(8), dp(6), dp(8), dp(6))
                for (n in names) {
                    val chip = tv(Fonts.displayName(n) + " Aa", 14f, if (t.font == n) Color.WHITE else C.TXT)
                    chip.typeface = Fonts.get(n, false, false)
                    chip.setPadding(dp(14), dp(10), dp(14), dp(10))
                    chip.background = roundedBg(if (t.font == n) C.ACCENT else C.CARD, dpf(16f))
                    chip.setOnClickListener {
                        t.font = n
                        refresh()
                        showTab(1)
                    }
                    val lpc = lp(WRAP, WRAP)
                    lpc.setMargins(dp(4), 0, dp(4), 0)
                    row.addView(chip, lpc)
                }
                val imp = pillButton(L("+ افزودن فونت (TTF)", "+ Import font (TTF)")) {
                    pick(arrayOf("*/*"), false, EditorActivity.REQ_FONT) { uris ->
                        if (uris.isNotEmpty()) {
                            val nm = MediaUtil.displayName(this, uris[0])
                            val ext = nm.substringAfterLast('.', "").lowercase()
                            if (ext != "ttf" && ext != "otf") {
                                toast(L("فقط فایل TTF یا OTF", "Only TTF or OTF files"))
                            } else {
                                val dst = File(Fonts.fontsDir(this), nm)
                                if (MediaUtil.copyTo(this, uris[0], dst)) {
                                    t.font = "file:$nm"
                                    refresh()
                                    showTab(1)
                                }
                            }
                        }
                    }
                }
                row.addView(imp, lp(WRAP, WRAP))
                content.addView(hScroll(row), FrameLayout.LayoutParams(MATCH, WRAP))
            }
            2 -> {
                val col = lin(true)
                col.addView(caption(L("رنگ متن", "Text color")), lp(MATCH, WRAP))
                col.addView(ColorRow(this, t.color) { c -> t.color = c; refresh() }, lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("کانتور", "Outline"), 0f, 100f, t.outlineW * 100f, false, { fmtPct(it) }, { v -> t.outlineW = v / 100f; refresh() }), lp(MATCH, WRAP))
                col.addView(ColorRow(this, t.outlineColor) { c -> t.outlineColor = c; refresh() }, lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("سایه", "Shadow"), 0f, 100f, t.shadow * 100f, false, { fmtPct(it) }, { v -> t.shadow = v / 100f; refresh() }), lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("پس‌زمینه", "Box"), 0f, 100f, t.bgAlpha * 100f, false, { fmtPct(it) }, { v -> t.bgAlpha = v / 100f; refresh() }), lp(MATCH, WRAP))
                col.addView(ColorRow(this, t.bgColor) { c -> t.bgColor = c; if (t.bgAlpha < 0.05f) t.bgAlpha = 0.8f; refresh() }, lp(MATCH, WRAP))
                val sv = ScrollView(this)
                sv.addView(col)
                content.addView(sv, FrameLayout.LayoutParams(MATCH, dp(150)))
            }
            3 -> {
                val col = lin(true)
                val tg = lin(false)
                tg.gravity = Gravity.CENTER
                tg.setPadding(dp(8), dp(4), dp(8), dp(4))
                fun toggle(text: String, on: Boolean, act: () -> Unit): TextView {
                    val b = pillButton(text, on) { act(); showTab(3) }
                    return b
                }
                tg.addView(toggle("B", t.bold) { t.bold = !t.bold; refresh() }, lp(WRAP, WRAP).also { it.rightMargin = dp(6) })
                tg.addView(toggle("I", t.italic) { t.italic = !t.italic; refresh() }, lp(WRAP, WRAP).also { it.rightMargin = dp(12) })
                tg.addView(toggle(L("چپ", "Left"), t.align == 0) { t.align = 0; refresh() }, lp(WRAP, WRAP).also { it.rightMargin = dp(6) })
                tg.addView(toggle(L("وسط", "Center"), t.align == 1) { t.align = 1; refresh() }, lp(WRAP, WRAP).also { it.rightMargin = dp(6) })
                tg.addView(toggle(L("راست", "Right"), t.align == 2) { t.align = 2; refresh() }, lp(WRAP, WRAP))
                col.addView(tg, lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("اندازه", "Size"), 3f, 20f, t.size * 100f, false, { String.format(Locale.US, "%.1f", it) }, { v -> t.size = v / 100f; refresh() }), lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("فاصله حروف", "Spacing"), -5f, 50f, t.spacing * 100f, false, { Math.round(it).toString() }, { v -> t.spacing = v / 100f; refresh() }), lp(MATCH, WRAP))
                col.addView(SliderRow(this, L("شفافیت", "Opacity"), 5f, 100f, t.opacity * 100f, false, { fmtPct(it) }, { v -> t.opacity = v / 100f; refresh() }), lp(MATCH, WRAP))
                val sv = ScrollView(this)
                sv.addView(col)
                content.addView(sv, FrameLayout.LayoutParams(MATCH, dp(150)))
            }
            else -> {
                content.addView(overlayAnimControls(t) { refresh() }, FrameLayout.LayoutParams(MATCH, dp(150)))
            }
        }
    }

    val tabs = ChipRow(this, tabLabels, startTab ?: 0) { i -> showTab(i) }
    showTab(startTab ?: 0)
    val panel = box(etWrap, tabs, content)
    openPanel(panel, true, pre, {
        if (t.text.trim().isEmpty()) {
            proj.overlays.removeAll { it.id == t.id }
            timeline.selection = null
        }
        hideKeyboard(et)
    }, { hideKeyboard(et) })
    if (created) {
        et.requestFocus()
        et.selectAll()
        et.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }
}

private fun EditorActivity.hideKeyboard(v: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(v.windowToken, 0)
}

// ------------------------------------------------------------------ animation controls

private fun EditorActivity.animSection(label: String, list: List<NamedDef>, cur: String, dur: Long, onPick: (String) -> Unit, onDur: (Long) -> Unit): LinearLayout {
    val col = lin(true)
    col.addView(caption(label), lp(MATCH, WRAP))
    col.addView(ChipRow(this, list.map { it.name() }, Math.max(0, list.indexOfFirst { it.id == cur })) { i -> onPick(list[i].id) }, lp(MATCH, WRAP))
    col.addView(SliderRow(this, L("مدت", "Duration"), 0.1f, 3f, dur / 1000f, false, { fmtSec(it) }, { v -> onDur((v * 1000).toLong()) }), lp(MATCH, WRAP))
    return col
}

private fun EditorActivity.overlayAnimControls(o: Overlay, onChange: () -> Unit): View {
    val col = lin(true)
    col.addView(animSection(L("ورود", "In"), Fx.OVERLAY_ANIMS, o.animIn, o.animInMs, { id ->
        o.animIn = id
        onChange()
        if (!engine.playing) { engine.seek(o.startMs + 10); engine.play() }
    }, { ms -> o.animInMs = ms; onChange() }), lp(MATCH, WRAP))
    col.addView(animSection(L("خروج", "Out"), Fx.OVERLAY_ANIMS, o.animOut, o.animOutMs, { id ->
        o.animOut = id
        onChange()
        if (!engine.playing) { engine.seek(Math.max(0L, o.endMs() - o.animOutMs - 200)); engine.play() }
    }, { ms -> o.animOutMs = ms; onChange() }), lp(MATCH, WRAP))
    val sv = ScrollView(this)
    sv.addView(col)
    return sv
}

fun EditorActivity.openOverlayAnimPanel() {
    val o = selOverlay() ?: return
    val v = overlayAnimControls(o) { engine.refresh() }
    openPanel(box(FrameLayout(this).also { it.addView(v, FrameLayout.LayoutParams(MATCH, dp(190))) }), true)
}

fun EditorActivity.openAnimPanel() {
    val c = selClip() ?: return
    val col = lin(true)
    col.addView(animSection(L("ورود", "In"), Fx.CLIP_ANIMS, c.animIn, c.animInMs, { id ->
        c.animIn = id
        changed()
        if (!engine.playing) { engine.seek(proj.clipStart(proj.clips.indexOf(c)) + 5); engine.play() }
    }, { ms -> c.animInMs = Math.min(ms, c.durMs() / 2); changed() }), lp(MATCH, WRAP))
    col.addView(animSection(L("خروج", "Out"), Fx.CLIP_ANIMS, c.animOut, c.animOutMs, { id ->
        c.animOut = id
        changed()
        if (!engine.playing) { engine.seek(Math.max(0L, proj.clipStart(proj.clips.indexOf(c)) + c.durMs() - c.animOutMs - 200)); engine.play() }
    }, { ms -> c.animOutMs = Math.min(ms, c.durMs() / 2); changed() }), lp(MATCH, WRAP))
    val sv = ScrollView(this)
    sv.addView(col)
    openPanel(box(FrameLayout(this).also { it.addView(sv, FrameLayout.LayoutParams(MATCH, dp(200))) }), true)
}

fun EditorActivity.openTransitionPanel(index: Int) {
    if (index <= 0 || index >= proj.clips.size) return
    val c = proj.clips[index]
    var all = false
    val row = ChipRow(this, Fx.TRANSITIONS.map { it.name() }, Math.max(0, Fx.TRANSITIONS.indexOfFirst { it.id == c.trans })) { i ->
        val id = Fx.TRANSITIONS[i].id
        for (k in 1 until proj.clips.size) if (all || k == index) proj.clips[k].trans = id
        changed()
        val st = proj.clipStart(index)
        engine.seek(Math.max(0L, st - c.transMs / 2 - 200))
        engine.play()
    }
    val dur = SliderRow(this, L("مدت", "Duration"), 0.2f, 2f, c.transMs / 1000f, false, { fmtSec(it) }, { v ->
        for (k in 1 until proj.clips.size) if (all || k == index) proj.clips[k].transMs = (v * 1000).toLong()
        changed()
    })
    val allChip = pillButton(L("اعمال به همه‌ی گذارها", "Apply to all")) { }
    allChip.setOnClickListener {
        all = !all
        allChip.background = roundedBg(if (all) C.ACCENT else C.CARD, dpf(16f))
    }
    val wrap = FrameLayout(this)
    wrap.setPadding(dp(10), dp(4), dp(10), dp(4))
    wrap.addView(allChip, FrameLayout.LayoutParams(WRAP, WRAP))
    openPanel(box(caption(L("گذار بین کلیپ‌ها", "Transition between clips")), row, dur, wrap), true)
}

// ------------------------------------------------------------------ speed, volume, crop, duration, opacity

private fun speedFromSlider(t: Float): Float = (0.1 * Math.pow(100.0, t.toDouble())).toFloat()
private fun sliderFromSpeed(s: Float): Float = (Math.log((s / 0.1f).toDouble()) / Math.log(100.0)).toFloat()

fun EditorActivity.openSpeedPanel() {
    val c = selClip() ?: return
    if (c.isImage) return
    val holder = FrameLayout(this)
    var row: SliderRow? = null
    fun setSpeed(s: Float) {
        c.speed = clampF(s, 0.1f, 10f)
        changed()
        row?.set(sliderFromSpeed(c.speed) * 100f)
        row?.slider?.invalidate()
    }
    row = SliderRow(this, L("سرعت", "Speed"), 0f, 100f, sliderFromSpeed(c.speed) * 100f, false, { fmtX(speedFromSlider(it / 100f)) }, { v ->
        var s = speedFromSlider(v / 100f)
        if (Math.abs(s - 1f) < 0.04f) s = 1f
        c.speed = s
        changed()
    })
    holder.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
    val presets = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f, 10f)
    val chips = ChipRow(this, presets.map { fmtX(it).replace(".00", "") }, -1) { i -> setSpeed(presets[i]) }
    openPanel(box(caption(L("سرعت پخش کلیپ (صدا هم همراه می‌شود)", "Clip speed (audio follows)")), holder, chips), true)
}

fun EditorActivity.openDurationPanel() {
    val c = selClip() ?: return
    openPanel(box(SliderRow(this, L("مدت", "Duration"), 0.5f, 20f, c.imgDur / 1000f, false, { fmtSec(it) }, { v ->
        c.imgDur = (v * 1000).toLong()
        changed()
    })), true)
}

fun EditorActivity.openVolumePanel() {
    val c = selClip() ?: return
    val row = SliderRow(this, L("صدا", "Volume"), 0f, 100f, c.volume * 100f, false, { fmtPct(it) }, { v ->
        c.volume = v / 100f
        engine.refresh()
    })
    val mute = pillButton(L("بی‌صدا", "Mute")) { c.volume = 0f; row.set(0f); engine.refresh() }
    val w = FrameLayout(this)
    w.setPadding(dp(10), dp(2), dp(10), dp(2))
    w.addView(mute, FrameLayout.LayoutParams(WRAP, WRAP))
    openPanel(box(row, w), true)
}

fun EditorActivity.openAudioVolumePanel() {
    val a = selAudio() ?: return
    val col = lin(true)
    col.addView(SliderRow(this, L("صدا", "Volume"), 0f, 100f, a.volume * 100f, false, { fmtPct(it) }, { v -> a.volume = v / 100f; engine.refresh() }), lp(MATCH, WRAP))
    col.addView(SliderRow(this, L("ظهور تدریجی", "Fade in"), 0f, 10f, a.fadeIn / 1000f, false, { fmtSec(it) }, { v -> a.fadeIn = (v * 1000).toLong() }), lp(MATCH, WRAP))
    col.addView(SliderRow(this, L("محو تدریجی", "Fade out"), 0f, 10f, a.fadeOut / 1000f, false, { fmtSec(it) }, { v -> a.fadeOut = (v * 1000).toLong() }), lp(MATCH, WRAP))
    openPanel(box(col), true)
}

fun EditorActivity.openAudioSpeedPanel() {
    val a = selAudio() ?: return
    openPanel(box(SliderRow(this, L("سرعت", "Speed"), 0.25f, 3f, a.speed, false, { fmtX(it) }, { v ->
        a.speed = v
        changed()
    })), true)
}

fun EditorActivity.openOpacityPanel() {
    val o = selOverlay() ?: return
    openPanel(box(SliderRow(this, L("شفافیت", "Opacity"), 5f, 100f, o.opacity * 100f, false, { fmtPct(it) }, { v ->
        o.opacity = v / 100f
        stage.top.invalidate()
    })), true)
}

fun EditorActivity.openCropPanel() {
    val c = targetClip() ?: return
    val col = lin(true)
    lateinit var panX: SliderRow
    lateinit var panY: SliderRow
    fun limit(): Float = Math.max(0f, (c.zoom - 1f) / 2f)
    fun clampPan() {
        c.panX = clampF(c.panX, -limit(), limit())
        c.panY = clampF(c.panY, -limit(), limit())
    }
    val zoom = SliderRow(this, L("زوم", "Zoom"), 100f, 400f, c.zoom * 100f, false, { fmtX(it / 100f) }, { v ->
        c.zoom = v / 100f
        clampPan()
        panX.set(c.panX * 200f)
        panY.set(c.panY * 200f)
        changed()
    })
    panX = SliderRow(this, L("جابه‌جایی افقی", "Pan X"), -100f, 100f, c.panX * 200f, true, { Math.round(it).toString() }, { v ->
        c.panX = v / 200f
        clampPan()
        changed()
    })
    panY = SliderRow(this, L("جابه‌جایی عمودی", "Pan Y"), -100f, 100f, c.panY * 200f, true, { Math.round(it).toString() }, { v ->
        c.panY = v / 200f
        clampPan()
        changed()
    })
    val btns = lin(false)
    btns.gravity = Gravity.CENTER
    btns.setPadding(dp(8), dp(2), dp(8), dp(2))
    fun addB(text: String, act: () -> Unit) {
        val b = pillButton(text) { act() }
        btns.addView(b, lp(WRAP, WRAP).also { it.setMargins(dp(4), 0, dp(4), 0) })
    }
    addB(L("چرخش ۹۰°", "Rotate 90°")) { c.rot = (c.rot + 90) % 360; changed(); refreshPanelLater(this) }
    addB(L("آینه افقی", "Flip H")) { c.flipH = !c.flipH; changed() }
    addB(L("آینه عمودی", "Flip V")) { c.flipV = !c.flipV; changed() }
    addB(L("بازنشانی", "Reset")) {
        c.zoom = 1f; c.panX = 0f; c.panY = 0f
        zoom.set(100f); panX.set(0f); panY.set(0f)
        changed()
    }
    col.addView(zoom, lp(MATCH, WRAP))
    col.addView(panX, lp(MATCH, WRAP))
    col.addView(panY, lp(MATCH, WRAP))
    col.addView(hScroll(btns), lp(MATCH, WRAP))
    openPanel(box(col), true)
}

private fun refreshPanelLater(a: EditorActivity) {
    a.engine.refresh()
}

// ------------------------------------------------------------------ audio

private class BigAudioBtn(ctx: Context, icon: String, label: String, color: Int, onClick: () -> Unit) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val iv = IconView(ctx, icon, color)
        iv.padFrac = 0.12f
        addView(iv, lp(ctx.dp(34), ctx.dp(34)))
        addView(ctx.tv(label, 11f, color), lp(WRAP, WRAP))
        setOnClickListener { onClick() }
    }
}

fun EditorActivity.openAudioMenu() {
    val row = lin(false)
    row.gravity = Gravity.CENTER
    row.setPadding(0, dp(8), 0, dp(8))
    fun add(v: View) {
        row.addView(v, lp(0, dp(64), 1f))
    }
    add(BigAudioBtn(this, "music", L("موسیقی", "Music"), 0xFFB07CF0.toInt()) {
        pickAudio { a ->
            a.startMs = Math.min(engine.timeMs, Math.max(0L, proj.totalMs() - 500))
            beginChange()
            proj.audios.add(a)
            timeline.selection = null
            changed()
            toast(L("به تایم‌لاین اضافه شد.", "Added to the timeline."))
        }
    })
    add(BigAudioBtn(this, "sfx", L("افکت صوتی", "Effects"), 0xFFE04F9A.toInt()) { openSfxPanel() })
    add(BigAudioBtn(this, "mic", L("ضبط صدا", "Record"), 0xFFF2685A.toInt()) { openRecordPanel() })
    add(BigAudioBtn(this, "tts", L("گفتار", "AI Speech"), 0xFFF29A4D.toInt()) { openTtsPanel() })
    openPanel(box(row), false)
}

private var previewPlayer: MediaPlayer? = null

private fun stopPreviewSound() {
    try {
        previewPlayer?.stop()
        previewPlayer?.release()
    } catch (e: Exception) {
    }
    previewPlayer = null
}

private fun EditorActivity.addAudioAtPlayhead(a: AudioItem) {
    a.startMs = Math.min(engine.timeMs, Math.max(0L, proj.totalMs() - 300))
    beginChange()
    proj.audios.add(a)
    changed()
}

fun EditorActivity.openSfxPanel() {
    var chosen: SoundFx.Def? = null
    val row = lin(false)
    row.setPadding(dp(8), dp(6), dp(8), dp(6))
    val chips = ArrayList<TextView>()
    for (d in SoundFx.LIST) {
        val b = tv(d.name(), 12.5f, Color.WHITE)
        b.setPadding(dp(16), dp(12), dp(16), dp(12))
        b.background = roundedBg(C.CARD, dpf(14f))
        b.setOnClickListener {
            chosen = d
            for (x in chips) x.background = roundedBg(C.CARD, dpf(14f))
            b.background = roundedBg(C.ACCENT, dpf(14f))
            Bg.run {
                val f = SoundFx.file(applicationContext, d.id)
                Bg.ui {
                    stopPreviewSound()
                    try {
                        val mp = MediaPlayer()
                        mp.setDataSource(f.absolutePath)
                        mp.prepare()
                        mp.start()
                        previewPlayer = mp
                    } catch (e: Exception) {
                    }
                }
            }
        }
        val lpc = lp(WRAP, WRAP)
        lpc.setMargins(dp(4), 0, dp(4), 0)
        row.addView(b, lpc)
        chips.add(b)
    }
    val add = pillButton(L("افزودن به تایم‌لاین", "Add to timeline"), true) {
        val d = chosen
        if (d == null) toast(L("یک افکت صوتی انتخاب کنید.", "Pick a sound first.")) else {
            Bg.run {
                val f = SoundFx.file(applicationContext, d.id)
                val dst = File(Store.mediaDir(applicationContext, proj.id), uid() + ".wav")
                f.copyTo(dst, true)
                val a = MediaUtil.audioFromFile(dst, d.name(), "sfx")
                Bg.ui {
                    if (a != null) {
                        addAudioAtPlayhead(a)
                        toast(L("اضافه شد.", "Added."))
                    }
                }
            }
        }
    }
    val w = FrameLayout(this)
    w.setPadding(dp(10), dp(2), dp(10), dp(4))
    w.addView(add, FrameLayout.LayoutParams(WRAP, WRAP))
    swapPanel(box(caption(L("روی افکت بزنید تا پیش‌شنود شود", "Tap a sound to preview")), hScroll(row), w))
}

private var recorder: MediaRecorder? = null
private var recFile: File? = null
private var recStartTl = 0L
private var recStartWall = 0L

fun EditorActivity.openRecordPanel() {
    val status = tv(L("برای شروع ضبط، دکمه را بزنید", "Tap to start recording"), 13f, C.TXT2)
    val btn = View(this)
    btn.background = roundedBg(0xFFE5484D.toInt(), dpf(30f))
    val col = lin(true)
    col.gravity = Gravity.CENTER_HORIZONTAL
    col.setPadding(0, dp(8), 0, dp(8))
    col.addView(btn, lp(dp(60), dp(60)))
    col.addView(status, lp(WRAP, WRAP).also { it.topMargin = dp(6) })
    fun stopRec() {
        val r = recorder ?: return
        engine.pause()
        try {
            r.stop()
        } catch (e: Exception) {
        }
        try {
            r.release()
        } catch (e: Exception) {
        }
        recorder = null
        val f = recFile
        btn.background = roundedBg(0xFFE5484D.toInt(), dpf(30f))
        status.text = L("برای ضبط دوباره، دکمه را بزنید", "Tap to record again")
        if (f != null) {
            val a = MediaUtil.audioFromFile(f, L("ضبط صدا", "Voice"), "voice")
            if (a != null) {
                a.startMs = recStartTl
                beginChange()
                proj.audios.add(a)
                changed()
            }
        }
    }
    btn.setOnClickListener {
        if (recorder != null) {
            stopRec()
        } else {
            withMicPermission {
                try {
                    val f = File(Store.mediaDir(this, proj.id), uid() + ".m4a")
                    val r = MediaRecorder()
                    r.setAudioSource(MediaRecorder.AudioSource.MIC)
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setAudioSamplingRate(44100)
                    r.setAudioEncodingBitRate(128000)
                    r.setOutputFile(f.absolutePath)
                    r.prepare()
                    r.start()
                    recorder = r
                    recFile = f
                    recStartTl = engine.timeMs
                    recStartWall = System.currentTimeMillis()
                    btn.background = roundedBg(0xFFFFFFFF.toInt(), dpf(10f))
                    status.text = L("در حال ضبط… برای توقف دوباره بزنید", "Recording… tap to stop")
                    engine.play()
                } catch (e: Exception) {
                    toast(L("ضبط شروع نشد", "Could not start recording"))
                }
            }
        }
    }
    swapPanel(box(col))
}

fun EditorActivity.openTtsPanel() {
    val et = EditText(this)
    et.setTextColor(Color.WHITE)
    et.setHintTextColor(C.TXT2)
    et.hint = L("متنی که باید خوانده شود…", "Text to be spoken…")
    et.textSize = 15f
    et.minLines = 2
    et.maxLines = 4
    et.background = roundedBg(C.CARD, dpf(10f))
    et.setPadding(dp(12), dp(8), dp(12), dp(8))
    val wrap = FrameLayout(this)
    wrap.setPadding(dp(10), dp(4), dp(10), dp(4))
    wrap.addView(et, FrameLayout.LayoutParams(MATCH, WRAP))
    val langs = listOf(Locale("fa", "IR"), Locale.US, Locale("ar"), Locale("tr", "TR"), Locale("hi", "IN"), Locale("ur", "PK"))
    val langNames = listOf(L("فارسی", "Persian"), "English", L("عربی", "Arabic"), L("ترکی", "Turkish"), L("هندی", "Hindi"), L("اردو", "Urdu"))
    var li = 0
    val chips = ChipRow(this, langNames, 0) { i -> li = i }
    var rate = 1f
    var pitch = 1f
    val s1 = SliderRow(this, L("سرعت", "Rate"), 0.5f, 2f, 1f, false, { fmtX(it) }, { v -> rate = v })
    val s2 = SliderRow(this, L("زیر و بمی", "Pitch"), 0.5f, 2f, 1f, false, { fmtX(it) }, { v -> pitch = v })
    val status = tv("", 12f, C.TXT2)
    val go = pillButton(L("ساخت صدا", "Create speech"), true) { }
    go.setOnClickListener {
        val text = et.text.toString().trim()
        if (text.isEmpty()) {
            toast(L("متن را بنویسید.", "Type some text."))
            return@setOnClickListener
        }
        go.isEnabled = false
        status.text = L("در حال ساخت…", "Working…")
        val out = File(Store.mediaDir(this, proj.id), uid() + ".wav")
        var tts: TextToSpeech? = null
        tts = TextToSpeech(applicationContext) { st ->
            val t = tts
            if (st != TextToSpeech.SUCCESS || t == null) {
                status.text = L("موتور گفتار در دسترس نیست", "No speech engine available")
                go.isEnabled = true
                return@TextToSpeech
            }
            val r = t.setLanguage(langs[li])
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                status.text = L("این زبان روی گوشی نصب نیست (Google TTS را بررسی کنید)", "This language is not installed on the phone")
                go.isEnabled = true
                t.shutdown()
                return@TextToSpeech
            }
            t.setSpeechRate(rate)
            t.setPitch(pitch)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    Bg.ui {
                        val a = MediaUtil.audioFromFile(out, L("گفتار", "Speech"), "tts")
                        if (a != null) {
                            addAudioAtPlayhead(a)
                            status.text = L("به تایم‌لاین اضافه شد.", "Added to the timeline.")
                        } else {
                            status.text = L("ساخت صدا ناموفق بود", "Speech failed")
                        }
                        go.isEnabled = true
                        t.shutdown()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Bg.ui {
                        status.text = L("ساخت صدا ناموفق بود", "Speech failed")
                        go.isEnabled = true
                        t.shutdown()
                    }
                }
            })
            val b = Bundle()
            t.synthesizeToFile(text, b, out, "u1")
        }
    }
    val bw = lin(false)
    bw.gravity = Gravity.CENTER_VERTICAL
    bw.setPadding(dp(10), dp(2), dp(10), dp(2))
    bw.addView(go, lp(WRAP, WRAP))
    bw.addView(status, lp(WRAP, WRAP).also { it.leftMargin = dp(10) })
    val col = lin(true)
    col.addView(wrap, lp(MATCH, WRAP))
    col.addView(chips, lp(MATCH, WRAP))
    col.addView(s1, lp(MATCH, WRAP))
    col.addView(s2, lp(MATCH, WRAP))
    col.addView(bw, lp(MATCH, WRAP))
    val sv = ScrollView(this)
    sv.addView(col)
    val f = FrameLayout(this)
    f.addView(sv, FrameLayout.LayoutParams(MATCH, dp(230)))
    swapPanel(box(f))
}
