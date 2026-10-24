package com.rafiei.editor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

fun Context.tv(text: String, sp: Float = 13f, color: Int = C.TXT, bold: Boolean = false, gravity: Int = Gravity.CENTER): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = sp
    t.setTextColor(color)
    t.gravity = gravity
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun Context.lin(vertical: Boolean): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    return l
}

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams {
    return if (weight > 0f) LinearLayout.LayoutParams(w, h, weight) else LinearLayout.LayoutParams(w, h)
}

fun fl(w: Int, h: Int, gravity: Int = Gravity.TOP or Gravity.START): FrameLayout.LayoutParams {
    val p = FrameLayout.LayoutParams(w, h)
    p.gravity = gravity
    return p
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun View.onTap(block: () -> Unit) {
    setOnClickListener { block() }
}

/** Thin white slider similar to the one used by InShot. */
class FancySlider(ctx: Context) : View(ctx) {
    var min = 0f
    var max = 100f
    var value = 0f
    var centered = false
    var onChange: ((Float, Boolean) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setRange(mn: Float, mx: Float, v: Float, center: Boolean = false) {
        min = mn; max = mx; value = v; centered = center
        invalidate()
    }

    private fun frac(): Float = if (max > min) (value - min) / (max - min) else 0f

    override fun onDraw(c: Canvas) {
        val pad = dpf(14f)
        val w = width - pad * 2
        val cy = height / 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dpf(3f)
        p.color = 0xFF5A5A5A.toInt()
        c.drawLine(pad, cy, pad + w, cy, p)
        val x = pad + w * frac()
        p.color = Color.WHITE
        if (centered) {
            c.drawLine(pad + w / 2f, cy, x, cy, p)
            p.color = 0xFF8A8A8A.toInt()
            c.drawLine(pad + w / 2f, cy - dpf(5f), pad + w / 2f, cy + dpf(5f), p)
            p.color = Color.WHITE
        } else {
            c.drawLine(pad, cy, x, cy, p)
        }
        c.drawCircle(x, cy, dpf(10f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pad = dpf(14f)
        val w = width - pad * 2
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                update(e.x, pad, w)
            }
            MotionEvent.ACTION_MOVE -> update(e.x, pad, w)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onStop?.invoke()
            }
        }
        return true
    }

    private fun update(x: Float, pad: Float, w: Float) {
        val f = clampF((x - pad) / w, 0f, 1f)
        var v = min + (max - min) * f
        if (centered && Math.abs(f - 0.5f) < 0.02f) v = (min + max) / 2f
        value = v
        invalidate()
        onChange?.invoke(v, true)
    }
}

/** Label + slider + value text on one row. */
class SliderRow(ctx: Context, label: String, mn: Float, mx: Float, v: Float, center: Boolean, val fmt: (Float) -> String,
                onChange: (Float) -> Unit, onStop: () -> Unit = {}) : LinearLayout(ctx) {
    val slider = FancySlider(ctx)
    private val valueText = ctx.tv(fmt(v), 12f, C.TXT2)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val l = ctx.tv(label, 12.5f, C.TXT, false, Gravity.START or Gravity.CENTER_VERTICAL)
        addView(l, lp(ctx.dp(78), ctx.dp(44)))
        slider.setRange(mn, mx, v, center)
        slider.onChange = { x, _ ->
            valueText.text = fmt(x)
            onChange(x)
        }
        slider.onStop = onStop
        addView(slider, lp(0, ctx.dp(44), 1f))
        addView(valueText, lp(ctx.dp(52), ctx.dp(44)))
        setPadding(ctx.dp(12), 0, ctx.dp(8), 0)
    }

    fun set(v: Float) {
        slider.value = v
        slider.invalidate()
        valueText.text = fmt(v)
    }
}

/** Horizontal tool button: icon over label. */
class ToolBtn(ctx: Context, icon: String, label: String, val widthDp: Int = 66, tint: Int = Color.WHITE, onClick: () -> Unit) : LinearLayout(ctx) {
    val iconView = IconView(ctx, icon, tint)
    val labelView = ctx.tv(label, 10.5f, C.TXT2)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(iconView, lp(ctx.dp(28), ctx.dp(28)))
        labelView.maxLines = 1
        addView(labelView, lp(WRAP, WRAP))
        setOnClickListener { onClick() }
        isClickable = true
    }

    fun setActive(a: Boolean) {
        iconView.set(iconView.kind, if (a) C.ACCENT else Color.WHITE)
        labelView.setTextColor(if (a) C.ACCENT else C.TXT2)
    }
}

/** Horizontally scrolling row of text chips. */
class ChipRow(ctx: Context, items: List<String>, selected: Int, val onSelect: (Int) -> Unit) : HorizontalScrollView(ctx) {
    private val row = ctx.lin(false)
    private val chips = ArrayList<TextView>()
    var sel = selected

    init {
        isHorizontalScrollBarEnabled = false
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4))
        for (i in items.indices) {
            val t = ctx.tv(items[i], 12.5f, C.TXT)
            t.setPadding(ctx.dp(14), ctx.dp(7), ctx.dp(14), ctx.dp(7))
            t.setOnClickListener {
                select(i)
                onSelect(i)
            }
            val lpp = lp(WRAP, WRAP)
            lpp.setMargins(ctx.dp(4), 0, ctx.dp(4), 0)
            row.addView(t, lpp)
            chips.add(t)
        }
        addView(row)
        select(selected)
    }

    fun select(i: Int) {
        sel = i
        for (k in chips.indices) {
            val on = k == i
            chips[k].background = roundedBg(if (on) C.ACCENT else C.CARD, context.dpf(16f))
            chips[k].setTextColor(if (on) Color.WHITE else C.TXT)
        }
    }
}

/** Horizontal strip of colour dots. */
class ColorRow(ctx: Context, var selected: Int, val allowNone: Boolean = false, val onPick: (Int) -> Unit) : HorizontalScrollView(ctx) {
    private val row = ctx.lin(false)
    private val dots = ArrayList<View>()
    private val colors = ArrayList<Int>()

    companion object {
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(),
            0xFF00C7BE.toInt(), 0xFF32ADE6.toInt(), 0xFF007AFF.toInt(), 0xFF5856D6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt(),
            0xFFA2845E.toInt(), 0xFF8E8E93.toInt(), 0xFFFFB6C1.toInt(), 0xFF98FF98.toInt(), 0xFFFFE4B5.toInt(), 0xFFB0E0E6.toInt(),
            0xFFE6E6FA.toInt(), 0xFF800020.toInt(), 0xFF1B3A4B.toInt(), 0xFF2E7D32.toInt(), 0xFFD4AF37.toInt(), 0xFF212121.toInt()
        )
    }

    init {
        isHorizontalScrollBarEnabled = false
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4))
        for (col in PALETTE) colors.add(col)
        for (col in colors) {
            val v = View(ctx)
            val lpp = lp(ctx.dp(30), ctx.dp(30))
            lpp.setMargins(ctx.dp(5), 0, ctx.dp(5), 0)
            v.setOnClickListener {
                selected = col
                refresh()
                onPick(col)
            }
            row.addView(v, lpp)
            dots.add(v)
        }
        addView(row)
        refresh()
    }

    fun refresh() {
        for (i in dots.indices) {
            val on = colors[i] == selected
            dots[i].background = roundedBg(colors[i], context.dpf(15f), if (on) C.ACCENT else 0xFF666666.toInt(), if (on) context.dp(3) else context.dp(1))
        }
    }
}

object Dialogs {
    fun confirm(ctx: Context, title: String, msg: String, ok: String, onOk: () -> Unit) {
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setMessage(msg)
            .setPositiveButton(ok) { _, _ -> onOk() }
            .setNegativeButton(L("انصراف", "Cancel"), null)
            .show()
    }

    fun input(ctx: Context, title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(ctx)
        et.setText(initial)
        et.setSingleLine(true)
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(12))
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setView(et)
            .setPositiveButton(L("تایید", "OK")) { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton(L("انصراف", "Cancel"), null)
            .show()
    }

    fun choose(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, i -> onPick(i) }
            .show()
    }

    fun message(ctx: Context, title: String, msg: String) {
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setMessage(msg)
            .setPositiveButton(L("باشه", "OK"), null)
            .show()
    }
}

fun rectOf(l: Float, t: Float, r: Float, b: Float): RectF = RectF(l, t, r, b)
