package com.rafiei.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/** One photo placed in a collage cell. */
class Cell(var bmp: Bitmap?) {
    var zoom = 1f
    var panX = 0f // in cell-widths
    var panY = 0f
}

object CollageLayouts {
    /** Each layout is a list of normalized rectangles (l, t, r, b). */
    fun forCount(n: Int): List<Array<FloatArray>> {
        val out = ArrayList<Array<FloatArray>>()
        fun r(l: Float, t: Float, rr: Float, b: Float) = floatArrayOf(l, t, rr, b)
        when (n) {
            1 -> out.add(arrayOf(r(0f, 0f, 1f, 1f)))
            2 -> {
                out.add(arrayOf(r(0f, 0f, .5f, 1f), r(.5f, 0f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, .5f), r(0f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .66f, 1f), r(.66f, 0f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, .66f), r(0f, .66f, 1f, 1f)))
            }
            3 -> {
                out.add(arrayOf(r(0f, 0f, 1f, .5f), r(0f, .5f, .5f, 1f), r(.5f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .5f, .5f), r(.5f, 0f, 1f, .5f), r(0f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .5f, 1f), r(.5f, 0f, 1f, .5f), r(.5f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .5f, .5f), r(0f, .5f, .5f, 1f), r(.5f, 0f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, 1f / 3f), r(0f, 1f / 3f, 1f, 2f / 3f), r(0f, 2f / 3f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f / 3f, 1f), r(1f / 3f, 0f, 2f / 3f, 1f), r(2f / 3f, 0f, 1f, 1f)))
            }
            4 -> {
                out.add(arrayOf(r(0f, 0f, .5f, .5f), r(.5f, 0f, 1f, .5f), r(0f, .5f, .5f, 1f), r(.5f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, .5f), r(0f, .5f, 1f / 3f, 1f), r(1f / 3f, .5f, 2f / 3f, 1f), r(2f / 3f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .6f, 1f), r(.6f, 0f, 1f, 1f / 3f), r(.6f, 1f / 3f, 1f, 2f / 3f), r(.6f, 2f / 3f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, .25f), r(0f, .25f, 1f, .5f), r(0f, .5f, 1f, .75f), r(0f, .75f, 1f, 1f)))
            }
            5 -> {
                out.add(arrayOf(r(0f, 0f, .5f, .5f), r(.5f, 0f, 1f, .5f), r(0f, .5f, 1f / 3f, 1f), r(1f / 3f, .5f, 2f / 3f, 1f), r(2f / 3f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f / 3f, .5f), r(1f / 3f, 0f, 2f / 3f, .5f), r(2f / 3f, 0f, 1f, .5f), r(0f, .5f, .5f, 1f), r(.5f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, 1f, .4f), r(0f, .4f, .25f, 1f), r(.25f, .4f, .5f, 1f), r(.5f, .4f, .75f, 1f), r(.75f, .4f, 1f, 1f)))
            }
            else -> {
                out.add(arrayOf(r(0f, 0f, 1f / 3f, .5f), r(1f / 3f, 0f, 2f / 3f, .5f), r(2f / 3f, 0f, 1f, .5f), r(0f, .5f, 1f / 3f, 1f), r(1f / 3f, .5f, 2f / 3f, 1f), r(2f / 3f, .5f, 1f, 1f)))
                out.add(arrayOf(r(0f, 0f, .5f, 1f / 3f), r(.5f, 0f, 1f, 1f / 3f), r(0f, 1f / 3f, .5f, 2f / 3f), r(.5f, 1f / 3f, 1f, 2f / 3f), r(0f, 2f / 3f, .5f, 1f), r(.5f, 2f / 3f, 1f, 1f)))
            }
        }
        return out
    }
}

class CollageView(ctx: android.content.Context) : View(ctx) {
    var cells = ArrayList<Cell>()
    var layout: Array<FloatArray> = arrayOf()
    var spacing = 0.012f
    var radius = 0.02f
    var bgColor = Color.WHITE
    var ratio = 1f
    var selected = 0
    var onSelect: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val scale = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (selected in cells.indices) {
                val c = cells[selected]
                c.zoom = clampF(c.zoom * d.scaleFactor, 1f, 6f)
                invalidate()
            }
            return true
        }
    })

    /** Canvas rectangle where the collage is drawn inside this view. */
    private fun frame(): RectF {
        val aw = width.toFloat()
        val ah = height.toFloat()
        var w = aw
        var h = w / ratio
        if (h > ah) { h = ah; w = h * ratio }
        return RectF((aw - w) / 2f, (ah - h) / 2f, (aw + w) / 2f, (ah + h) / 2f)
    }

    fun cellRect(i: Int, W: Float, H: Float): RectF {
        val l = layout[i]
        val sp = spacing * Math.min(W, H)
        return RectF(l[0] * W + (if (l[0] <= 0f) sp else sp / 2f), l[1] * H + (if (l[1] <= 0f) sp else sp / 2f),
            l[2] * W - (if (l[2] >= 1f) sp else sp / 2f), l[3] * H - (if (l[3] >= 1f) sp else sp / 2f))
    }

    fun draw(c: Canvas, W: Float, H: Float, sel: Int) {
        c.drawColor(bgColor)
        val rad = radius * Math.min(W, H)
        for (i in layout.indices) {
            if (i >= cells.size) break
            val r = cellRect(i, W, H)
            val cell = cells[i]
            val b = cell.bmp
            c.save()
            val path = Path()
            path.addRoundRect(r, rad, rad, Path.Direction.CW)
            c.clipPath(path)
            if (b != null) {
                val s = Math.max(r.width() / b.width, r.height() / b.height) * cell.zoom
                val dw = b.width * s
                val dh = b.height * s
                val maxPx = Math.max(0f, (dw - r.width()) / 2f)
                val maxPy = Math.max(0f, (dh - r.height()) / 2f)
                val cx = r.centerX() + clampF(cell.panX * r.width(), -maxPx, maxPx)
                val cy = r.centerY() + clampF(cell.panY * r.height(), -maxPy, maxPy)
                c.drawBitmap(b, null, RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), paint)
            } else {
                paint.color = 0xFFCCCCCC.toInt()
                c.drawRect(r, paint)
            }
            c.restore()
            if (i == sel) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.style = Paint.Style.STROKE
                p.strokeWidth = context.dpf(3f)
                p.color = C.ACCENT
                c.drawRoundRect(r, rad, rad, p)
            }
        }
    }

    override fun onDraw(c: Canvas) {
        if (layout.isEmpty()) return
        val f = frame()
        c.save()
        c.translate(f.left, f.top)
        c.clipRect(0f, 0f, f.width(), f.height())
        draw(c, f.width(), f.height(), selected)
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scale.onTouchEvent(e)
        val f = frame()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; dragging = true
                val x = e.x - f.left
                val y = e.y - f.top
                for (i in layout.indices) {
                    if (cellRect(i, f.width(), f.height()).contains(x, y)) {
                        selected = i
                        onSelect?.invoke(i)
                        break
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scale.isInProgress && e.pointerCount == 1 && selected in cells.indices && selected < layout.size) {
                    val r = cellRect(selected, f.width(), f.height())
                    val c = cells[selected]
                    c.panX += (e.x - lastX) / r.width()
                    c.panY += (e.y - lastY) / r.height()
                    invalidate()
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    fun render(outW: Int): Bitmap {
        val outH = Math.round(outW / ratio)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), outW.toFloat(), outH.toFloat(), -1)
        return bmp
    }
}

class CollageActivity : Activity() {
    private lateinit var view: CollageView
    private lateinit var layoutRow: LinearLayout
    private lateinit var panelHost: FrameLayout
    private var layouts: List<Array<FloatArray>> = ArrayList()
    private var layoutIdx = 0
    private val REQ = 301

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG2
        val root = lin(true)
        root.setBackgroundColor(C.BG)
        val top = FrameLayout(this)
        val back = IconView(this, "back")
        back.setOnClickListener { finish() }
        top.addView(back, fl(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        top.addView(tv(L("کولاژ", "Collage"), 16f, C.TXT, true), fl(WRAP, WRAP, Gravity.CENTER))
        val save = tv(L("ذخیره", "Save"), 14f, Color.WHITE, true)
        save.background = gradBg(C.ACCENT2, C.ACCENT, dpf(18f), android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
        save.setPadding(dp(18), 0, dp(18), 0)
        save.setOnClickListener { saveCollage() }
        top.addView(save, fl(WRAP, dp(34), Gravity.END or Gravity.CENTER_VERTICAL).also { it.rightMargin = dp(12) })
        root.addView(top, lp(MATCH, dp(52)))

        view = CollageView(this)
        root.addView(view, lp(MATCH, 0, 1f))

        panelHost = FrameLayout(this)
        root.addView(panelHost, lp(MATCH, WRAP))
        setContentView(root)
        view.ratio = 1f
        buildToolbar()
        window.decorView.post { pickPhotos() }
    }

    private fun pickPhotos() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(i, REQ)
        } catch (e: Exception) {
            Toast.makeText(this, L("انتخاب‌گر فایل پیدا نشد", "No file picker available"), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ + 1) {
            val u = if (resultCode == RESULT_OK) data?.data else null
            if (u != null && replaceIdx in view.cells.indices) {
                val idx = replaceIdx
                Bg.run {
                    val b = MediaUtil.loadBitmap(this, u, 1600)
                    Bg.ui { view.cells[idx] = Cell(b); view.invalidate() }
                }
            }
            return
        }
        if (requestCode != REQ) return
        val uris = ArrayList<Uri>()
        if (resultCode == RESULT_OK && data != null) {
            val cd = data.clipData
            if (cd != null) for (i in 0 until Math.min(cd.itemCount, 6)) uris.add(cd.getItemAt(i).uri)
            else if (data.data != null) uris.add(data.data!!)
        }
        if (uris.isEmpty()) {
            if (view.cells.isEmpty()) finish()
            return
        }
        Bg.run {
            val cells = ArrayList<Cell>()
            for (u in uris) cells.add(Cell(MediaUtil.loadBitmap(this, u, 1600)))
            Bg.ui {
                view.cells = cells
                layouts = CollageLayouts.forCount(cells.size)
                layoutIdx = 0
                view.layout = layouts[0]
                view.selected = 0
                buildLayoutRow()
                view.invalidate()
            }
        }
    }

    private fun buildToolbar() {
        val bar = lin(true)
        bar.setBackgroundColor(C.BG2)
        layoutRow = lin(false)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(layoutRow)
        bar.addView(hs, lp(MATCH, dp(64)))
        val tools = lin(false)
        fun add(icon: String, label: String, action: () -> Unit) {
            val b = ToolBtn(this, icon, label, 78, Color.WHITE, action)
            tools.addView(b, lp(0, dp(64), 1f))
        }
        add("ratio", L("نسبت", "Ratio")) { showRatio() }
        add("layers", L("فاصله", "Border")) { showBorder() }
        add("bg", L("پس‌زمینه", "Background")) { showBg() }
        add("replace", L("جایگزین", "Replace")) { replaceSelected() }
        add("import", L("افزودن", "Add")) { pickPhotos() }
        bar.addView(tools, lp(MATCH, dp(64)))
        panelHost.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    private fun buildLayoutRow() {
        layoutRow.removeAllViews()
        for (i in layouts.indices) {
            val v = object : View(this) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: Canvas) {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    p.style = Paint.Style.FILL
                    p.color = if (i == layoutIdx) C.ACCENT else 0xFF555555.toInt()
                    for (r in layouts[i]) {
                        c.drawRoundRect(RectF(r[0] * w + 3, r[1] * h + 3, r[2] * w - 3, r[3] * h - 3), 4f, 4f, p)
                    }
                }
            }
            v.setOnClickListener {
                layoutIdx = i
                view.layout = layouts[i]
                view.selected = 0
                for (k in 0 until layoutRow.childCount) layoutRow.getChildAt(k).invalidate()
                view.invalidate()
            }
            val l = lp(dp(46), dp(46))
            l.setMargins(dp(8), dp(9), dp(4), dp(9))
            layoutRow.addView(v, l)
        }
    }

    private fun replaceSelected() {
        val idx = view.selected
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        replaceIdx = idx
        try { startActivityForResult(i, REQ + 1) } catch (e: Exception) {}
    }

    private var replaceIdx = 0

    private fun showSheet(content: View) {
        val host = lin(true)
        host.setBackgroundColor(C.BG2)
        val head = FrameLayout(this)
        val ok = IconView(this, "check")
        ok.setOnClickListener { panelHost.removeView(host) }
        head.addView(ok, fl(dp(44), dp(44), Gravity.CENTER))
        host.addView(content, lp(MATCH, WRAP))
        host.addView(head, lp(MATCH, dp(44)))
        panelHost.addView(host, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
    }

    private fun showRatio() {
        val names = listOf("1:1", "4:5", "9:16", "16:9", "3:4", "4:3")
        val vals = floatArrayOf(1f, 4f / 5f, 9f / 16f, 16f / 9f, 3f / 4f, 4f / 3f)
        val cur = vals.indexOfFirst { Math.abs(it - view.ratio) < 0.001f }
        showSheet(ChipRow(this, names, if (cur < 0) 0 else cur) { view.ratio = vals[it]; view.invalidate() })
    }

    private fun showBorder() {
        val col = lin(true)
        col.addView(SliderRow(this, L("فاصله", "Spacing"), 0f, 40f, view.spacing * 1000f, false, { "${it.toInt()}" }, { v ->
            view.spacing = v / 1000f; view.invalidate()
        }))
        col.addView(SliderRow(this, L("گردی گوشه", "Corners"), 0f, 100f, view.radius * 1000f, false, { "${it.toInt()}" }, { v ->
            view.radius = v / 1000f; view.invalidate()
        }))
        showSheet(col)
    }

    private fun showBg() {
        showSheet(ColorRow(this, view.bgColor) { view.bgColor = it; view.invalidate() })
    }

    private fun saveCollage() {
        if (view.cells.isEmpty()) return
        val dlg = android.app.ProgressDialog(this)
        dlg.setMessage(L("در حال ذخیره…", "Saving…"))
        dlg.setCancelable(false)
        dlg.show()
        Bg.run {
            val bmp = view.render(2160)
            val uri = Exporter.savePhoto(this, bmp, false)
            bmp.recycle()
            Bg.ui {
                dlg.dismiss()
                if (uri == null) {
                    Toast.makeText(this, L("ذخیره ناموفق بود", "Save failed"), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, L("در گالری ذخیره شد", "Saved to gallery"), Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
