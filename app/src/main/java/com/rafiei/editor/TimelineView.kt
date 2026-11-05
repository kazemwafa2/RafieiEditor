package com.rafiei.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View

class Sel(val kind: String, val id: String) // kind: clip | overlay | audio | fx

interface TimelineListener {
    fun onSeek(ms: Long)
    fun onSelect(sel: Sel?)
    fun onEditBegin()
    fun onEditing()
    fun onEditEnd()
    fun onAddClip()
    fun onTransition(index: Int)
}

class TimelineView(ctx: Context) : View(ctx) {
    var project: Project = Project()
    var listener: TimelineListener? = null
    var viewTime = 0L
    var selection: Sel? = null
    var photoMode = false

    private var ppm = 0.06f * resources.displayMetrics.density // pixels per millisecond
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())

    private val rulerH get() = dp(22)
    private val stripH get() = dp(54)
    private val laneH get() = dp(26)
    private val laneGap get() = dp(4)
    private val bottomH get() = dp(18)
    private val handleW get() = dp(16)

    private val laneOf = HashMap<String, Int>()
    private var laneCount = 0
    private var scrollY = 0f

    // gesture state
    private var mode = 0 // 0 idle, 1 scroll time, 2 vertical scroll, 3 handle left, 4 handle right, 5 drag item, 6 pinch
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downSel: Sel? = null
    private var moved = false
    private var longPressed = false
    private var vt: VelocityTracker? = null
    private var flingV = 0f
    private var editing = false
    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            ppm = clampF(ppm * d.scaleFactor, 0.012f * resources.displayMetrics.density, 0.7f * resources.displayMetrics.density)
            invalidate()
            return true
        }
    })

    private val longPressRunnable = Runnable {
        if (mode == 0 || (mode == 1 && !moved)) {
            val s = downSel
            if (s != null) {
                longPressed = true
                mode = 5
                if (selection?.id != s.id) {
                    selection = s
                    listener?.onSelect(s)
                }
                beginEdit()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
    }

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (Math.abs(flingV) < 15f || mode != 0) return
            val dtMs = (flingV * 0.016f) / ppm
            moveTime(-dtMs.toLong())
            flingV *= 0.93f
            handler.postDelayed(this, 16)
        }
    }

    private fun total(): Long = if (photoMode) 0L else project.totalMs()
    private fun xOf(t: Long): Float = width / 2f + (t - viewTime) * ppm
    private fun tOf(x: Float): Long = viewTime + ((x - width / 2f) / ppm).toLong()

    fun setTimeSilently(t: Long) {
        viewTime = clampL(t, 0L, total())
        invalidate()
    }

    private fun moveTime(deltaMs: Long) {
        val nt = clampL(viewTime + deltaMs, 0L, total())
        if (nt != viewTime) {
            viewTime = nt
            listener?.onSeek(nt)
            invalidate()
        }
    }

    /** Recompute lanes for overlays, effects and audio items. */
    fun relayout() {
        laneOf.clear()
        var base = 0
        base = assign(base, project.overlays.map { Triple(it.id, it.startMs, it.endMs()) })
        base = assign(base, project.fxs.map { Triple(it.id, it.startMs, it.endMs()) })
        base = assign(base, project.audios.map { Triple(it.id, it.startMs, it.endMs()) })
        laneCount = base
        invalidate()
    }

    private fun assign(base: Int, items: List<Triple<String, Long, Long>>): Int {
        val ends = ArrayList<Long>()
        val sorted = items.sortedBy { it.second }
        for (it in sorted) {
            var lane = -1
            for (i in ends.indices) {
                if (ends[i] <= it.second) {
                    lane = i
                    break
                }
            }
            if (lane < 0) {
                ends.add(it.third)
                lane = ends.size - 1
            } else {
                ends[lane] = it.third
            }
            laneOf[it.first] = base + lane
        }
        return base + ends.size
    }

    private fun contentH(): Float = rulerH + stripH + dp(8) + laneCount * (laneH + laneGap) + dp(12).toFloat()

    private fun laneTop(lane: Int): Float = rulerH + stripH + dp(8) - scrollY + lane * (laneH + laneGap)

    private fun clipTop(): Float = rulerH + dp(2).toFloat()

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        c.drawColor(C.BG)
        if (photoMode) return
        drawRuler(c)
        drawClips(c)
        drawLanes(c)
        // playhead
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val cx = width / 2f
        c.drawRect(cx - dp(1).toFloat(), 0f, cx + dp(1).toFloat(), height - bottomH.toFloat(), paint)
        // times
        textPaint.color = Color.WHITE
        textPaint.textSize = dpf(12f)
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText(fmtTime(viewTime), cx - dp(4), height - dp(4).toFloat(), textPaint)
        textPaint.isFakeBoldText = false
        textPaint.color = C.TXT2
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText(fmtTime(total()), width - dp(8).toFloat(), height - dp(4).toFloat(), textPaint)
    }

    private fun drawRuler(c: Canvas) {
        val steps = longArrayOf(100, 200, 500, 1000, 2000, 5000, 10000, 30000, 60000, 120000, 300000)
        var step = steps[steps.size - 1]
        for (s in steps) {
            if (s * ppm >= dp(56)) {
                step = s
                break
            }
        }
        val t0 = Math.max(0L, tOf(0f) / step * step - step)
        val t1 = tOf(width.toFloat())
        textPaint.textSize = dpf(10f)
        textPaint.color = C.TXT2
        textPaint.textAlign = Paint.Align.CENTER
        paint.color = C.TXT2
        paint.style = Paint.Style.FILL
        var t = t0
        while (t <= t1) {
            val x = xOf(t)
            if (t <= total() + step) {
                c.drawText(fmtShortRuler(t), x, dpf(11f), textPaint)
                c.drawCircle(x + step * ppm / 2f, dpf(16f), dpf(1.3f), paint)
            }
            t += step
        }
    }

    private fun fmtShortRuler(t: Long): String {
        val s = t / 1000
        val m = s / 60
        val sec = s % 60
        return if (t % 1000 == 0L || t >= 1000) String.format("%02d:%02d", m, sec) else String.format("%02d:%02d.%d", m, sec, (t % 1000) / 100)
    }

    private fun drawClips(c: Canvas) {
        val top = clipTop() - scrollY
        var start = 0L
        val sel = selection
        for (i in project.clips.indices) {
            val clip = project.clips[i]
            val d = clip.durMs()
            val x0 = xOf(start)
            val x1 = xOf(start + d)
            start += d
            if (x1 < -dp(40) || x0 > width + dp(40)) continue
            rect.set(x0 + 1f, top, x1 - 1f, top + stripH)
            c.save()
            val clipPath = Path()
            clipPath.addRoundRect(rect, dpf(6f), dpf(6f), Path.Direction.CW)
            c.clipPath(clipPath)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF3A3A3A.toInt()
            c.drawRect(rect, paint)
            val tileW = Math.max(dp(30).toFloat(), stripH * clampF(clip.dispW().toFloat() / clip.dispH().toFloat(), 0.5f, 2f))
            var tx = rect.left
            val path = MediaUtil.pathOf(clip.uri)
            while (tx < rect.right) {
                if (tx + tileW > 0 && tx < width) {
                    val frac = ((tx - rect.left) + tileW / 2f) / Math.max(1f, rect.width())
                    val srcT = clip.trimStart + (frac * (clip.trimEnd - clip.trimStart)).toLong()
                    val b = ThumbCache.frame(path, clip.isImage, srcT, stripH.toInt()) { invalidate() }
                    if (b != null) {
                        val dst = RectF(tx, rect.top, tx + tileW, rect.bottom)
                        val sc = Math.max(tileW / b.width, stripH / b.height.toFloat())
                        val bw = b.width * sc
                        val bh = b.height * sc
                        val rr = RectF(dst.centerX() - bw / 2f, dst.centerY() - bh / 2f, dst.centerX() + bw / 2f, dst.centerY() + bh / 2f)
                        c.save()
                        c.clipRect(dst)
                        c.drawBitmap(b, null, rr, null)
                        c.restore()
                    }
                }
                tx += tileW
            }
            // label
            paint.color = 0x99000000.toInt()
            val label = fmtDur(d) + (if (clip.speed != 1f) "  ${trimNum(clip.speed)}x" else "")
            textPaint.textSize = dpf(10f)
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.LEFT
            val lw = textPaint.measureText(label) + dp(10)
            val lr = RectF(rect.left + dp(4), rect.bottom - dp(17), rect.left + dp(4) + lw, rect.bottom - dp(3))
            c.drawRoundRect(lr, dpf(7f), dpf(7f), paint)
            c.drawText(label, lr.left + dp(5), lr.bottom - dp(4), textPaint)
            if (clip.volume == 0f && !clip.isImage) {
                iconPaint.color = Color.WHITE
                Icons.draw(c, "mute", rect.right - dp(14), rect.top + dp(14), dpf(14f), iconPaint)
            }
            c.restore()
            if (sel != null && sel.kind == "clip" && sel.id == clip.id) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpf(2f)
                paint.color = Color.WHITE
                c.drawRoundRect(rect, dpf(6f), dpf(6f), paint)
                drawHandle(c, rect.left - handleW + dp(2), rect.top, rect.bottom, true)
                drawHandle(c, rect.right - dp(2), rect.top, rect.bottom, false)
            }
            // transition marker
            if (i > 0) {
                val bx = xOf(project.clipStart(i))
                if (bx > -dp(20) && bx < width + dp(20)) {
                    val r = dp(11).toFloat()
                    val by = top + stripH / 2f
                    paint.style = Paint.Style.FILL
                    paint.color = if (clip.trans != "none") C.ACCENT else 0xFFEEEEEE.toInt()
                    c.drawRoundRect(RectF(bx - r, by - r, bx + r, by + r), dpf(5f), dpf(5f), paint)
                    paint.color = if (clip.trans != "none") Color.WHITE else 0xFF333333.toInt()
                    Icons.draw(c, "trans", bx, by, r * 1.5f, paint)
                }
            }
        }
        // add tile after the last clip
        val endX = xOf(project.totalMs()) + dp(8)
        if (endX < width + dp(60)) {
            rect.set(endX, top + dp(6), endX + dp(46), top + stripH - dp(6))
            paint.style = Paint.Style.FILL
            paint.color = 0xFF3A3A3A.toInt()
            c.drawRoundRect(rect, dpf(8f), dpf(8f), paint)
            paint.color = Color.WHITE
            Icons.draw(c, "plus", rect.centerX(), rect.centerY(), dpf(22f), paint)
        }
    }

    private fun drawHandle(c: Canvas, x: Float, top: Float, bottom: Float, left: Boolean) {
        val r = RectF(x, top, x + handleW, bottom)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawRoundRect(r, dpf(5f), dpf(5f), paint)
        paint.color = 0xFF333333.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(2f)
        paint.strokeCap = Paint.Cap.ROUND
        val cx = r.centerX()
        val cy = r.centerY()
        val d = dpf(3.5f)
        if (left) {
            c.drawLine(cx + d / 2f, cy - d * 1.6f, cx - d / 2f, cy, paint); c.drawLine(cx - d / 2f, cy, cx + d / 2f, cy + d * 1.6f, paint)
        } else {
            c.drawLine(cx - d / 2f, cy - d * 1.6f, cx + d / 2f, cy, paint); c.drawLine(cx + d / 2f, cy, cx - d / 2f, cy + d * 1.6f, paint)
        }
    }

    private fun fmtDur(ms: Long): String = String.format("%.1fs", ms / 1000f)
    private fun trimNum(f: Float): String = if (f == Math.floor(f.toDouble()).toFloat()) f.toInt().toString() else String.format("%.1f", f)

    private fun barColor(kind: String, extra: String): Int {
        return when (kind) {
            "text" -> 0xFFF2994A.toInt()
            "sticker" -> 0xFFE85D9E.toInt()
            "image" -> 0xFF2BB5A8.toInt()
            "fx" -> 0xFF5C6BC0.toInt()
            "music" -> 0xFF8E5BD1.toInt()
            "voice" -> 0xFFE5484D.toInt()
            "sfx" -> 0xFF2E9E6B.toInt()
            "tts" -> 0xFF3E8EDE.toInt()
            else -> 0xFF888888.toInt()
        }
    }

    private fun drawLanes(c: Canvas) {
        val sel = selection
        c.save()
        c.clipRect(0f, rulerH + stripH.toFloat() - scrollY, width.toFloat(), (height - bottomH).toFloat())
        for (o in project.overlays) {
            val lane = laneOf[o.id] ?: continue
            val label = if (o.kind == "text") o.text else if (o.kind == "sticker") (if (o.glyph.startsWith("shape:")) "◆" else o.glyph) else L("تصویر", "Image")
            drawBar(c, o.startMs, o.endMs(), laneTop(lane), barColor(o.kind, ""), label, sel != null && sel.id == o.id, null, 0f, 0L)
        }
        for (f in project.fxs) {
            val lane = laneOf[f.id] ?: continue
            drawBar(c, f.startMs, f.endMs(), laneTop(lane), Fx.fxDef(f.fx).color and 0x00FFFFFF or 0xFF000000.toInt(), Fx.fxDef(f.fx).name(), sel != null && sel.id == f.id, null, 0f, 0L)
        }
        for (a in project.audios) {
            val lane = laneOf[a.id] ?: continue
            drawBar(c, a.startMs, a.endMs(), laneTop(lane), barColor(a.kind, ""), a.name, sel != null && sel.id == a.id, MediaUtil.pathOf(a.uri), a.speed, a.trimStart)
        }
        c.restore()
    }

    private fun drawBar(c: Canvas, s: Long, e: Long, top: Float, color: Int, label: String, selected: Boolean, wavePath: String?, speed: Float, trimStart: Long) {
        val x0 = xOf(s)
        val x1 = xOf(e)
        if (x1 < 0 || x0 > width) return
        rect.set(x0, top, Math.max(x1, x0 + dp(8)), top + laneH)
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(rect, dpf(6f), dpf(6f), paint)
        if (wavePath != null) {
            val peaks = Waveform.peek(wavePath)
            if (peaks == null) {
                Waveform.request(wavePath) { invalidate() }
            } else if (peaks.isNotEmpty()) {
                paint.color = 0x66FFFFFF
                var x = Math.max(rect.left, 0f)
                val endX = Math.min(rect.right, width.toFloat())
                val cy = rect.centerY()
                while (x < endX) {
                    val tt = (x - x0) / ppm * speed + trimStart
                    val idx = (tt / 50f).toInt()
                    if (idx >= 0 && idx < peaks.size) {
                        val h = Math.max(dpf(1.5f), peaks[idx] * (laneH - dpf(6f)))
                        c.drawRect(x, cy - h / 2f, x + dpf(1.6f), cy + h / 2f, paint)
                    }
                    x += dpf(3f)
                }
            }
        }
        c.save()
        c.clipRect(rect)
        textPaint.color = Color.WHITE
        textPaint.textSize = dpf(11f)
        textPaint.textAlign = Paint.Align.LEFT
        c.drawText(label, Math.max(rect.left, 0f) + dp(6), rect.centerY() + dpf(4f), textPaint)
        c.restore()
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(2f)
            paint.color = Color.WHITE
            c.drawRoundRect(rect, dpf(6f), dpf(6f), paint)
            drawHandle(c, rect.left - handleW + dp(2), rect.top, rect.bottom, true)
            drawHandle(c, rect.right - dp(2), rect.top, rect.bottom, false)
        }
    }

    // ------------------------------------------------------------------ hit testing

    private fun hitItem(x: Float, y: Float): Sel? {
        if (photoMode) return null
        val clipT = clipTop() - scrollY
        if (y in clipT..(clipT + stripH)) {
            var start = 0L
            for (cl in project.clips) {
                val x0 = xOf(start)
                val x1 = xOf(start + cl.durMs())
                start += cl.durMs()
                if (x >= x0 && x <= x1) return Sel("clip", cl.id)
            }
            return null
        }
        for (a in project.audios) {
            val lane = laneOf[a.id] ?: continue
            if (barHit(a.startMs, a.endMs(), laneTop(lane), x, y)) return Sel("audio", a.id)
        }
        for (f in project.fxs) {
            val lane = laneOf[f.id] ?: continue
            if (barHit(f.startMs, f.endMs(), laneTop(lane), x, y)) return Sel("fx", f.id)
        }
        for (i in project.overlays.indices.reversed()) {
            val o = project.overlays[i]
            val lane = laneOf[o.id] ?: continue
            if (barHit(o.startMs, o.endMs(), laneTop(lane), x, y)) return Sel("overlay", o.id)
        }
        return null
    }

    private fun barHit(s: Long, e: Long, top: Float, x: Float, y: Float): Boolean {
        if (y < rulerH + stripH || y > height - bottomH) return false
        return x >= xOf(s) && x <= Math.max(xOf(e), xOf(s) + dp(8)) && y >= top && y <= top + laneH
    }

    /** Returns the time range (start, end) of the current selection or null. */
    private fun selRange(): LongArray? {
        val s = selection ?: return null
        when (s.kind) {
            "clip" -> {
                for (i in project.clips.indices) if (project.clips[i].id == s.id) return longArrayOf(project.clipStart(i), project.clipStart(i) + project.clips[i].durMs())
            }
            "overlay" -> for (o in project.overlays) if (o.id == s.id) return longArrayOf(o.startMs, o.endMs())
            "audio" -> for (a in project.audios) if (a.id == s.id) return longArrayOf(a.startMs, a.endMs())
            "fx" -> for (f in project.fxs) if (f.id == s.id) return longArrayOf(f.startMs, f.endMs())
        }
        return null
    }

    private fun selTop(): Float {
        val s = selection ?: return 0f
        if (s.kind == "clip") return clipTop() - scrollY
        val lane = laneOf[s.id] ?: return 0f
        return laneTop(lane)
    }

    private fun selHeight(): Float = if (selection?.kind == "clip") stripH.toFloat() else laneH.toFloat()

    private fun hitHandle(x: Float, y: Float): Int {
        val r = selRange() ?: return 0
        val top = selTop()
        if (y < top - dp(4) || y > top + selHeight() + dp(4)) return 0
        val xl = xOf(r[0])
        val xr = xOf(r[1])
        if (x >= xl - handleW - dp(8) && x <= xl + dp(6)) return 3
        if (x >= xr - dp(6) && x <= xr + handleW + dp(8)) return 4
        return 0
    }

    // ------------------------------------------------------------------ touch

    private fun beginEdit() {
        if (!editing) {
            editing = true
            listener?.onEditBegin()
        }
    }

    private fun endEdit() {
        if (editing) {
            editing = false
            listener?.onEditEnd()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (photoMode) return false
        scaleDetector.onTouchEvent(e)
        if (e.pointerCount > 1) {
            handler.removeCallbacks(longPressRunnable)
            if (mode == 5) endEdit()
            mode = 6
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(flingRunnable)
                flingV = 0f
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                moved = false
                longPressed = false
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                val h = hitHandle(e.x, e.y)
                if (h != 0) {
                    mode = h
                    beginEdit()
                    parent.requestDisallowInterceptTouchEvent(true)
                } else {
                    mode = 0
                    downSel = hitItem(e.x, e.y)
                    handler.postDelayed(longPressRunnable, 420)
                }
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - lastX
                val dy = e.y - lastY
                if (!moved && Math.abs(e.x - downX) + Math.abs(e.y - downY) > dp(6)) {
                    moved = true
                    if (mode == 0) {
                        handler.removeCallbacks(longPressRunnable)
                        mode = if (Math.abs(e.y - downY) > Math.abs(e.x - downX) * 1.6f && contentH() > height) 2 else 1
                    }
                }
                if (moved || mode >= 3) {
                    when (mode) {
                        1 -> moveTime(-(dx / ppm).toLong())
                        2 -> {
                            scrollY = clampF(scrollY - dy, 0f, Math.max(0f, contentH() - (height - bottomH)))
                            invalidate()
                        }
                        3, 4 -> dragHandle(mode == 3, (dx / ppm).toLong())
                        5 -> dragItem(e.x, (dx / ppm).toLong())
                    }
                }
                lastX = e.x
                lastY = e.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                vt?.addMovement(e)
                val m = mode
                if (m == 1) {
                    vt?.computeCurrentVelocity(1000)
                    flingV = vt?.xVelocity ?: 0f
                    mode = 0
                    handler.post(flingRunnable)
                }
                if (m >= 3 && m <= 5) endEdit()
                if (!moved && !longPressed && m <= 1 && e.actionMasked == MotionEvent.ACTION_UP) handleTap(e.x, e.y)
                if (m != 1) mode = 0
                vt?.recycle()
                vt = null
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        // add tile
        val top = clipTop() - scrollY
        val endX = xOf(project.totalMs()) + dp(8)
        if (y in top..(top + stripH) && x >= endX && x <= endX + dp(46)) {
            listener?.onAddClip()
            return
        }
        // transition markers
        if (y in top..(top + stripH)) {
            for (i in 1 until project.clips.size) {
                val bx = xOf(project.clipStart(i))
                if (Math.abs(x - bx) <= dp(14)) {
                    listener?.onTransition(i)
                    return
                }
            }
        }
        val s = hitItem(x, y)
        selection = s
        listener?.onSelect(s)
        invalidate()
        if (s != null) {
            // tapping an item moves the playhead to its start when it is not visible under the playhead
            val r = selRange()
            if (r != null && (viewTime < r[0] || viewTime > r[1])) {
                viewTime = clampL(r[0], 0L, total())
                listener?.onSeek(viewTime)
            }
        }
    }

    private fun dragHandle(left: Boolean, dtMs: Long) {
        val s = selection ?: return
        if (dtMs == 0L) return
        when (s.kind) {
            "clip" -> {
                val c = project.clips.firstOrNull { it.id == s.id } ?: return
                if (c.isImage) {
                    if (left) {
                        val nd = clampL(c.imgDur - dtMs, 300L, 60000L)
                        val applied = c.imgDur - nd
                        c.imgDur = nd
                        viewTime = clampL(viewTime + applied, 0L, total())
                    } else {
                        c.imgDur = clampL(c.imgDur + dtMs, 300L, 60000L)
                    }
                } else {
                    val minSrc = (300 * c.speed).toLong()
                    if (left) {
                        val nt = clampL(c.trimStart + (dtMs * c.speed).toLong(), 0L, c.trimEnd - minSrc)
                        val appliedTl = ((nt - c.trimStart) / c.speed).toLong()
                        c.trimStart = nt
                        viewTime = clampL(viewTime + appliedTl, 0L, total())
                    } else {
                        c.trimEnd = clampL(c.trimEnd + (dtMs * c.speed).toLong(), c.trimStart + minSrc, c.srcDurMs)
                    }
                }
            }
            "overlay" -> {
                val o = project.overlays.firstOrNull { it.id == s.id } ?: return
                if (left) {
                    val ns = clampL(o.startMs + dtMs, 0L, o.endMs() - 300)
                    o.durMs = o.endMs() - ns
                    o.startMs = ns
                } else {
                    o.durMs = Math.max(300L, o.durMs + dtMs)
                }
            }
            "fx" -> {
                val o = project.fxs.firstOrNull { it.id == s.id } ?: return
                if (left) {
                    val ns = clampL(o.startMs + dtMs, 0L, o.endMs() - 300)
                    o.durMs = o.endMs() - ns
                    o.startMs = ns
                } else {
                    o.durMs = Math.max(300L, o.durMs + dtMs)
                }
            }
            "audio" -> {
                val a = project.audios.firstOrNull { it.id == s.id } ?: return
                if (left) {
                    val minSrc = (300 * a.speed).toLong()
                    val nt = clampL(a.trimStart + (dtMs * a.speed).toLong(), 0L, a.trimEnd - minSrc)
                    val appliedTl = ((nt - a.trimStart) / a.speed).toLong()
                    a.trimStart = nt
                    a.startMs = Math.max(0L, a.startMs + appliedTl)
                } else {
                    a.trimEnd = clampL(a.trimEnd + (dtMs * a.speed).toLong(), a.trimStart + (300 * a.speed).toLong(), a.srcDurMs)
                }
            }
        }
        relayout()
        listener?.onEditing()
    }

    private fun dragItem(x: Float, dtMs: Long) {
        val s = selection ?: return
        when (s.kind) {
            "clip" -> {
                val from = project.clips.indexOfFirst { it.id == s.id }
                if (from < 0) return
                val to = project.clipIndexAt(tOf(x).let { clampL(it, 0L, Math.max(0L, project.totalMs() - 1)) })
                if (to != from && to >= 0) {
                    val c = project.clips.removeAt(from)
                    project.clips.add(to, c)
                    listener?.onEditing()
                    invalidate()
                }
            }
            "overlay" -> {
                val o = project.overlays.firstOrNull { it.id == s.id } ?: return
                o.startMs = Math.max(0L, o.startMs + dtMs)
            }
            "fx" -> {
                val o = project.fxs.firstOrNull { it.id == s.id } ?: return
                o.startMs = Math.max(0L, o.startMs + dtMs)
            }
            "audio" -> {
                val a = project.audios.firstOrNull { it.id == s.id } ?: return
                a.startMs = Math.max(0L, a.startMs + dtMs)
            }
        }
        if (s.kind != "clip") {
            relayout()
            listener?.onEditing()
        }
    }
}
