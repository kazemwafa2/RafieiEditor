package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build

/** An offscreen drawing surface: a bitmap with a Canvas2D-like context. */
class Cv(val w: Int, val h: Int) {
    val bmp: Bitmap = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888)
    val g = Ctx2D(Canvas(bmp))
    fun clear() {
        bmp.eraseColor(0)
    }
}

class Grad(private val linear: Boolean, private val a: FloatArray) {
    private val pos = ArrayList<Float>()
    private val col = ArrayList<Int>()

    fun addColorStop(p: Float, c: Int) {
        pos.add(clampF(p, 0f, 1f))
        col.add(c)
    }

    fun shader(): Shader? {
        if (col.isEmpty()) return null
        // positions must be non-decreasing
        val n = col.size
        val ps = FloatArray(n)
        val cs = IntArray(n)
        var last = 0f
        for (i in 0 until n) {
            last = Math.max(last, pos[i])
            ps[i] = last
            cs[i] = col[i]
        }
        if (n == 1) return null
        return if (linear) {
            LinearGradient(a[0], a[1], a[2], a[3], cs, ps, Shader.TileMode.CLAMP)
        } else {
            // a = x0,y0,r0,x1,y1,r1
            val r1 = Math.max(0.01f, a[5])
            val r0 = clampF(a[2], 0f, r1 * 0.999f)
            if (Build.VERSION.SDK_INT >= 31 && (Math.abs(a[0] - a[3]) > 0.5f || Math.abs(a[1] - a[4]) > 0.5f)) {
                RadialGradient(a[0], a[1], r0, a[3], a[4], r1, LongArray(cs.size) { Color.pack(cs[it]) }, ps, Shader.TileMode.CLAMP)
            } else if (r0 <= 0.001f) {
                RadialGradient(a[3], a[4], r1, cs, ps, Shader.TileMode.CLAMP)
            } else {
                val f = r0 / r1
                val m = n + 1
                val ps2 = FloatArray(m)
                val cs2 = IntArray(m)
                ps2[0] = 0f
                cs2[0] = cs[0]
                for (i in 0 until n) {
                    ps2[i + 1] = f + ps[i] * (1f - f)
                    cs2[i + 1] = cs[i]
                }
                RadialGradient(a[3], a[4], r1, cs2, ps2, Shader.TileMode.CLAMP)
            }
        }
    }

    fun firstColor(): Int = if (col.isEmpty()) 0 else col[0]
}

class TextMetrics2(val width: Float, val actualBoundingBoxLeft: Float, val actualBoundingBoxRight: Float, val actualBoundingBoxAscent: Float, val actualBoundingBoxDescent: Float)

private class St {
    var fill: Any = Color.BLACK
    var stroke: Any = Color.BLACK
    var lineWidth = 1f
    var cap = Paint.Cap.BUTT
    var join = Paint.Join.MITER
    var shadowBlur = 0f
    var shadowColor = 0
    var alpha = 1f
    var comp = "source-over"
    var align = "left"
    var baseline = "alphabetic"
    var fontPx = 10f
    var fontBold = false
    var rtl = false
    var dash: FloatArray? = null

    fun copy(): St {
        val s = St()
        s.fill = fill; s.stroke = stroke; s.lineWidth = lineWidth; s.cap = cap; s.join = join
        s.shadowBlur = shadowBlur; s.shadowColor = shadowColor; s.alpha = alpha; s.comp = comp
        s.align = align; s.baseline = baseline; s.fontPx = fontPx; s.fontBold = fontBold; s.rtl = rtl; s.dash = dash
        return s
    }
}

/** A small subset of the HTML Canvas 2D API on top of android.graphics.Canvas. Used to port the equalizer designs. */
class Ctx2D(var canvas: Canvas) {
    private var st = St()
    private val stack = ArrayList<St>()
    val path = Path()
    private var hasPts = false
    private var cx = 0f
    private var cy = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpM = Matrix()
    private val rectF = RectF()
    var fontFamily: Typeface = Typeface.DEFAULT

    // ------------------------------------------------------------ state properties
    var fillStyle: Any
        get() = st.fill
        set(v) { st.fill = v }
    var strokeStyle: Any
        get() = st.stroke
        set(v) { st.stroke = v }
    var lineWidth: Float
        get() = st.lineWidth
        set(v) { st.lineWidth = v }
    var shadowBlur: Float
        get() = st.shadowBlur
        set(v) { st.shadowBlur = v }
    var shadowColor: Int
        get() = st.shadowColor
        set(v) { st.shadowColor = v }
    var globalAlpha: Float
        get() = st.alpha
        set(v) { st.alpha = v }
    var globalCompositeOperation: String
        get() = st.comp
        set(v) { st.comp = v }
    var textAlign: String
        get() = st.align
        set(v) { st.align = v }
    var textBaseline: String
        get() = st.baseline
        set(v) { st.baseline = v }
    var direction: String
        get() = if (st.rtl) "rtl" else "ltr"
        set(v) { st.rtl = v == "rtl" }
    var lineCap: String
        get() = ""
        set(v) { st.cap = when (v) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT } }
    var lineJoin: String
        get() = ""
        set(v) { st.join = when (v) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER } }

    /** Accepts e.g. "800 64px ..." or "700 30px ...". */
    fun setFont(weight: Int, px: Float) {
        st.fontBold = weight >= 600
        st.fontPx = px
    }

    fun setLineDash(a: FloatArray?) {
        st.dash = if (a == null || a.isEmpty()) null else a
    }

    fun save() {
        stack.add(st.copy())
        canvas.save()
    }

    fun restore() {
        if (stack.isNotEmpty()) {
            st = stack.removeAt(stack.size - 1)
            canvas.restore()
        }
    }

    // ------------------------------------------------------------ transforms
    fun translate(x: Float, y: Float) { canvas.translate(x, y) }
    fun rotate(a: Float) { canvas.rotate(a * 180f / Math.PI.toFloat()) }
    fun scale(x: Float, y: Float) { canvas.scale(x, y) }
    fun transform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        tmpM.setValues(floatArrayOf(a, c, e, b, d, f, 0f, 0f, 1f))
        canvas.concat(tmpM)
    }

    fun setTransform(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        canvas.setMatrix(null)
        transform(a, b, c, d, e, f)
    }

    // ------------------------------------------------------------ gradients
    fun createLinearGradient(x0: Float, y0: Float, x1: Float, y1: Float): Grad = Grad(true, floatArrayOf(x0, y0, x1, y1))
    fun createRadialGradient(x0: Float, y0: Float, r0: Float, x1: Float, y1: Float, r1: Float): Grad = Grad(false, floatArrayOf(x0, y0, r0, x1, y1, r1))

    // ------------------------------------------------------------ paint setup
    private fun setup(p: Paint, style: Any, fill: Boolean, shadow: Boolean = true) {
        p.reset()
        p.isAntiAlias = true
        p.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        if (!fill) {
            p.strokeWidth = st.lineWidth
            p.strokeCap = st.cap
            p.strokeJoin = st.join
            p.strokeMiter = 10f
            val d = st.dash
            if (d != null) p.pathEffect = android.graphics.DashPathEffect(d, 0f)
        }
        if (style is Grad) {
            val sh = style.shader()
            if (sh != null) {
                p.shader = sh
                p.alpha = (st.alpha * 255f + 0.5f).toInt()
            } else {
                p.color = withAlpha(style.firstColor(), st.alpha * (Color.alpha(style.firstColor()) / 255f))
            }
        } else {
            val c = style as Int
            p.color = withAlpha(c, st.alpha * (Color.alpha(c) / 255f))
        }
        applyComp(p)
        if (shadow && st.shadowBlur > 0.5f && Color.alpha(st.shadowColor) > 0) {
            p.setShadowLayer(Math.max(0.5f, st.shadowBlur * 0.5f), 0f, 0f, st.shadowColor)
        }
    }

    private fun applyComp(p: Paint) {
        val m: PorterDuff.Mode? = when (st.comp) {
            "lighter" -> PorterDuff.Mode.ADD
            "source-atop" -> PorterDuff.Mode.SRC_ATOP
            "destination-over" -> PorterDuff.Mode.DST_OVER
            "destination-out" -> PorterDuff.Mode.DST_OUT
            "destination-in" -> PorterDuff.Mode.DST_IN
            "screen" -> PorterDuff.Mode.SCREEN
            "multiply" -> PorterDuff.Mode.MULTIPLY
            "overlay" -> PorterDuff.Mode.OVERLAY
            else -> null
        }
        p.xfermode = if (m != null) PorterDuffXfermode(m) else null
    }

    // ------------------------------------------------------------ paths
    fun beginPath() { path.reset(); hasPts = false }
    fun closePath() { path.close() }

    fun moveTo(x: Float, y: Float) { path.moveTo(x, y); hasPts = true; cx = x; cy = y }
    fun lineTo(x: Float, y: Float) {
        if (!hasPts) path.moveTo(x, y) else path.lineTo(x, y)
        hasPts = true; cx = x; cy = y
    }

    fun bezierCurveTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        if (!hasPts) { path.moveTo(x1, y1); hasPts = true }
        path.cubicTo(x1, y1, x2, y2, x, y); cx = x; cy = y
    }

    fun quadraticCurveTo(x1: Float, y1: Float, x: Float, y: Float) {
        if (!hasPts) { path.moveTo(x1, y1); hasPts = true }
        path.quadTo(x1, y1, x, y); cx = x; cy = y
    }

    fun rect(x: Float, y: Float, w: Float, h: Float) {
        path.addRect(x, y, x + w, y + h, Path.Direction.CW)
        hasPts = true
    }

    fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float, ccw: Boolean = false) {
        ellipse(x, y, r, r, 0f, a0, a1, ccw)
    }

    fun ellipse(x: Float, y: Float, rx: Float, ry: Float, rot: Float, a0: Float, a1: Float, ccw: Boolean = false) {
        val tau = (Math.PI * 2).toFloat()
        var sweep = a1 - a0
        if (!ccw) {
            if (sweep >= tau) sweep = tau else while (sweep < 0f) sweep += tau
        } else {
            if (sweep <= -tau) sweep = -tau else while (sweep > 0f) sweep -= tau
        }
        val sd = Math.toDegrees(sweep.toDouble()).toFloat()
        val sa = Math.toDegrees(a0.toDouble()).toFloat()
        val sub = Path()
        rectF.set(x - rx, y - ry, x + rx, y + ry)
        if (Math.abs(sd) >= 359.99f) {
            // full ellipse: start at a0 as the canvas does
            sub.arcTo(rectF, sa, if (sd > 0) 359.99f else -359.99f, true)
            sub.close()
        } else {
            sub.arcTo(rectF, sa, sd, true)
        }
        if (Math.abs(rot) > 0.0001f) {
            tmpM.setRotate(Math.toDegrees(rot.toDouble()).toFloat(), x, y)
            sub.transform(tmpM)
        }
        if (hasPts) {
            // connect the current point to the arc start like the canvas does
            val pts = FloatArray(2)
            val pm = android.graphics.PathMeasure(sub, false)
            pm.getPosTan(0f, pts, null)
            path.lineTo(pts[0], pts[1])
        }
        path.addPath(sub)
        hasPts = true
    }

    /** Tangent arc as in the canvas API (used for rounded rectangles). */
    fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, r: Float) {
        if (!hasPts) { path.moveTo(x1, y1); hasPts = true; cx = x1; cy = y1 }
        val x0 = cx
        val y0 = cy
        val v1x = x0 - x1
        val v1y = y0 - y1
        val v2x = x2 - x1
        val v2y = y2 - y1
        val l1 = Math.hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
        val l2 = Math.hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
        if (l1 < 1e-4f || l2 < 1e-4f || r <= 0f) { path.lineTo(x1, y1); cx = x1; cy = y1; return }
        val n1x = v1x / l1
        val n1y = v1y / l1
        val n2x = v2x / l2
        val n2y = v2y / l2
        val cross = n1x * n2y - n1y * n2x
        val dot = n1x * n2x + n1y * n2y
        if (Math.abs(cross) < 1e-5f) { path.lineTo(x1, y1); cx = x1; cy = y1; return }
        val ang = Math.acos(clampF(dot, -1f, 1f).toDouble()).toFloat()
        val t = (r / Math.tan((ang / 2f).toDouble())).toFloat()
        val p1x = x1 + n1x * t
        val p1y = y1 + n1y * t
        val p2x = x1 + n2x * t
        val p2y = y1 + n2y * t
        path.lineTo(p1x, p1y)
        // centre of the circle
        val bx = n1x + n2x
        val by = n1y + n2y
        val bl = Math.hypot(bx.toDouble(), by.toDouble()).toFloat()
        val d = (r / Math.sin((ang / 2f).toDouble())).toFloat()
        val ccx = x1 + bx / bl * d
        val ccy = y1 + by / bl * d
        val a0 = Math.atan2((p1y - ccy).toDouble(), (p1x - ccx).toDouble()).toFloat()
        val a1 = Math.atan2((p2y - ccy).toDouble(), (p2x - ccx).toDouble()).toFloat()
        var sw = Math.toDegrees((a1 - a0).toDouble()).toFloat()
        while (sw > 180f) sw -= 360f
        while (sw < -180f) sw += 360f
        rectF.set(ccx - r, ccy - r, ccx + r, ccy + r)
        path.arcTo(rectF, Math.toDegrees(a0.toDouble()).toFloat(), sw, false)
        cx = p2x; cy = p2y
    }

    // ------------------------------------------------------------ drawing
    fun fill() {
        setup(paint, st.fill, true)
        canvas.drawPath(path, paint)
    }

    fun stroke() {
        setup(paint, st.stroke, false)
        canvas.drawPath(path, paint)
    }

    fun clip() {
        canvas.clipPath(path)
    }

    fun fillRect(x: Float, y: Float, w: Float, h: Float) {
        setup(paint, st.fill, true)
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    fun strokeRect(x: Float, y: Float, w: Float, h: Float) {
        setup(paint, st.stroke, false)
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    fun clearRect(x: Float, y: Float, w: Float, h: Float) {
        paint.reset()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.xfermode = null
    }

    private fun imgPaint(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.alpha = (st.alpha * 255f + 0.5f).toInt()
        applyComp(paint)
        if (st.shadowBlur > 0.5f && Color.alpha(st.shadowColor) > 0) {
            paint.setShadowLayer(Math.max(0.5f, st.shadowBlur * 0.5f), 0f, 0f, st.shadowColor)
        }
        return paint
    }

    fun drawImage(b: Bitmap?, x: Float, y: Float) {
        if (b == null || b.isRecycled) return
        canvas.drawBitmap(b, x, y, imgPaint())
    }

    fun drawImage(b: Bitmap?, x: Float, y: Float, w: Float, h: Float) {
        if (b == null || b.isRecycled) return
        rectF.set(x, y, x + w, y + h)
        canvas.drawBitmap(b, null, rectF, imgPaint())
    }

    fun drawImage(b: Bitmap?, sx: Float, sy: Float, sw: Float, sh: Float, x: Float, y: Float, w: Float, h: Float) {
        if (b == null || b.isRecycled) return
        val src = Rect(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh))
        if (src.width() <= 0 || src.height() <= 0) return
        rectF.set(x, y, x + w, y + h)
        canvas.drawBitmap(b, src, rectF, imgPaint())
    }

    fun drawImage(c: Cv?, x: Float, y: Float) = drawImage(c?.bmp, x, y)
    fun drawImage(c: Cv?, x: Float, y: Float, w: Float, h: Float) = drawImage(c?.bmp, x, y, w, h)
    fun drawImage(c: Cv?, sx: Float, sy: Float, sw: Float, sh: Float, x: Float, y: Float, w: Float, h: Float) = drawImage(c?.bmp, sx, sy, sw, sh, x, y, w, h)

    // ------------------------------------------------------------ text
    private fun textPaint(fill: Boolean): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        setup(p, if (fill) st.fill else st.stroke, fill)
        p.textSize = st.fontPx
        p.typeface = Typeface.create(fontFamily, if (st.fontBold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = when (st.align) {
            "center" -> Paint.Align.CENTER
            "right", "end" -> if (st.rtl && st.align == "end") Paint.Align.LEFT else Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        return p
    }

    private fun baseY(p: Paint, y: Float): Float {
        val fm = p.fontMetrics
        return when (st.baseline) {
            "middle" -> y - (fm.ascent + fm.descent) / 2f
            "top", "hanging" -> y - fm.ascent
            "bottom" -> y - fm.descent
            else -> y
        }
    }

    fun fillText(t: String, x: Float, y: Float) {
        val p = textPaint(true)
        canvas.drawText(t, x, baseY(p, y), p)
    }

    fun strokeText(t: String, x: Float, y: Float) {
        val p = textPaint(false)
        canvas.drawText(t, x, baseY(p, y), p)
    }

    fun measureText(t: String): TextMetrics2 {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = st.fontPx
        p.typeface = Typeface.create(fontFamily, if (st.fontBold) Typeface.BOLD else Typeface.NORMAL)
        val b = Rect()
        p.getTextBounds(t, 0, t.length, b)
        return TextMetrics2(p.measureText(t), (-b.left).toFloat(), b.right.toFloat(), (-b.top).toFloat(), b.bottom.toFloat())
    }
}
