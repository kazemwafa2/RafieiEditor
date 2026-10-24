package com.rafiei.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/** Frame that keeps a given aspect ratio inside the space it is offered. */
open class AspectFrame(ctx: Context) : FrameLayout(ctx) {
    var aspect = 9f / 16f
        set(v) {
            if (Math.abs(field - v) > 0.0001f) {
                field = v
                requestLayout()
            }
        }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val aw = MeasureSpec.getSize(wSpec).toFloat()
        val ah = MeasureSpec.getSize(hSpec).toFloat()
        var w = aw
        var h = w / aspect
        if (h > ah) {
            h = ah
            w = h * aspect
        }
        val iw = Math.max(2, w.toInt())
        val ih = Math.max(2, h.toInt())
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (ch.layoutParams.width == MATCH && ch.layoutParams.height == MATCH) {
                ch.measure(MeasureSpec.makeMeasureSpec(iw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ih, MeasureSpec.EXACTLY))
            } else {
                ch.measure(MeasureSpec.makeMeasureSpec(ch.layoutParams.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch.layoutParams.height, MeasureSpec.EXACTLY))
            }
        }
        setMeasuredDimension(iw, ih)
    }
}

class BgView(ctx: Context) : View(ctx) {
    var project: Project? = null
    var timeMs = 0L

    override fun onDraw(c: Canvas) {
        val p = project ?: return
        if (width <= 0 || height <= 0) return
        Render.drawBackground(c, width, height, p, timeMs, false, false) { invalidate() }
    }
}

interface OverlayListener {
    fun onOverlaySelected(id: String?)
    fun onOverlayLive()
    fun onOverlayCommit()
    fun onOverlayDelete(id: String)
    fun onOverlayCopy(id: String)
    fun onOverlayEdit(id: String)
    fun onOverlayBegin()
}

/** Draws overlays/effects over the video and lets the user move, scale and rotate the selected overlay. */
class TopView(ctx: Context) : View(ctx) {
    var project: Project? = null
    var timeMs = 0L
    var selectedId: String? = null
    var interactive = true
    var listener: OverlayListener? = null

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = 0 // 0 none, 1 move, 2 scale/rotate handle, 3 pinch
    private var lastX = 0f
    private var lastY = 0f
    private var startDist = 1f
    private var startAngle = 0f
    private var startScale = 1f
    private var startRot = 0f
    private var moved = false
    private var begun = false
    private var downTime = 0L

    init {
        boxPaint.style = Paint.Style.STROKE
        boxPaint.color = 0xFFFFFFFF.toInt()
        boxPaint.strokeWidth = dpf(1.6f)
        boxPaint.pathEffect = DashPathEffect(floatArrayOf(dpf(6f), dpf(4f)), 0f)
    }

    private fun sel(): Overlay? {
        val p = project ?: return null
        val id = selectedId ?: return null
        for (o in p.overlays) if (o.id == id) return o
        return null
    }

    override fun onDraw(c: Canvas) {
        val p = project ?: return
        if (width <= 0 || height <= 0) return
        Render.drawTop(c, width, height, p, timeMs)
        val o = sel() ?: return
        if (!interactive) return
        if (timeMs < o.startMs || timeMs >= o.endMs()) return
        val corners = corners(o)
        val path = android.graphics.Path()
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()
        c.drawPath(path, boxPaint)
        drawHandle(c, corners[0], "close", 0xFFFF4D6D.toInt())
        drawHandle(c, corners[1], if (o.kind == "text") "text" else "copy", 0xFF4DA3FF.toInt())
        drawHandle(c, corners[2], "rotate", 0xFFFFFFFF.toInt())
        drawHandle(c, corners[3], "copy", 0xFF9B6BD6.toInt())
    }

    private fun drawHandle(c: Canvas, pt: PointF, icon: String, col: Int) {
        val r = dpf(13f)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = col
        c.drawCircle(pt.x, pt.y, r, fillPaint)
        fillPaint.color = if (col == 0xFFFFFFFF.toInt()) 0xFF222222.toInt() else 0xFFFFFFFF.toInt()
        Icons.draw(c, icon, pt.x, pt.y, r * 1.25f, fillPaint)
    }

    /** corners: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left (after scale and rotation). */
    private fun corners(o: Overlay): Array<PointF> {
        val W = width.toFloat()
        val H = height.toFloat()
        val sz = Render.overlaySize(o, W)
        val pad = dpf(6f)
        val hw = sz[0] * o.scale / 2f + pad
        val hh = sz[1] * o.scale / 2f + pad
        val a = Math.toRadians(o.rot.toDouble())
        val ca = Math.cos(a).toFloat()
        val sa = Math.sin(a).toFloat()
        val cx = o.cx * W
        val cy = o.cy * H
        fun pt(x: Float, y: Float): PointF = PointF(cx + x * ca - y * sa, cy + x * sa + y * ca)
        return arrayOf(pt(-hw, -hh), pt(hw, -hh), pt(hw, hh), pt(-hw, hh))
    }

    private fun hit(o: Overlay, x: Float, y: Float): Boolean {
        val W = width.toFloat()
        val H = height.toFloat()
        val sz = Render.overlaySize(o, W)
        val a = Math.toRadians(-o.rot.toDouble())
        val dx = x - o.cx * W
        val dy = y - o.cy * H
        val lx = (dx * Math.cos(a) - dy * Math.sin(a)).toFloat()
        val ly = (dx * Math.sin(a) + dy * Math.cos(a)).toFloat()
        val pad = dpf(10f)
        return Math.abs(lx) <= sz[0] * o.scale / 2f + pad && Math.abs(ly) <= sz[1] * o.scale / 2f + pad
    }

    private fun near(pt: PointF, x: Float, y: Float): Boolean = Math.hypot((pt.x - x).toDouble(), (pt.y - y).toDouble()) <= dpf(20f)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = project ?: return false
        if (!interactive) return false
        val W = width.toFloat()
        val H = height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moved = false
                begun = false
                downTime = System.currentTimeMillis()
                lastX = e.x
                lastY = e.y
                val s = sel()
                if (s != null && timeMs >= s.startMs && timeMs < s.endMs()) {
                    val cs = corners(s)
                    if (near(cs[0], e.x, e.y)) {
                        listener?.onOverlayDelete(s.id)
                        mode = 0
                        return true
                    }
                    if (near(cs[1], e.x, e.y)) {
                        if (s.kind == "text") listener?.onOverlayEdit(s.id) else listener?.onOverlayCopy(s.id)
                        mode = 0
                        return true
                    }
                    if (near(cs[3], e.x, e.y)) {
                        listener?.onOverlayCopy(s.id)
                        mode = 0
                        return true
                    }
                    if (near(cs[2], e.x, e.y)) {
                        mode = 2
                        val dx = e.x - s.cx * W
                        val dy = e.y - s.cy * H
                        startDist = Math.max(1f, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat())
                        startAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        startScale = s.scale
                        startRot = s.rot
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                // pick the top most overlay under the finger
                var found: Overlay? = null
                for (i in p.overlays.indices.reversed()) {
                    val o = p.overlays[i]
                    if (timeMs >= o.startMs && timeMs < o.endMs() && hit(o, e.x, e.y)) {
                        found = o
                        break
                    }
                }
                if (found != null) {
                    if (selectedId != found.id) {
                        selectedId = found.id
                        listener?.onOverlaySelected(found.id)
                    }
                    mode = 1
                    parent.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                mode = 0
                if (selectedId != null) {
                    selectedId = null
                    listener?.onOverlaySelected(null)
                    invalidate()
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val s = sel()
                if (s != null && e.pointerCount >= 2) {
                    mode = 3
                    val dx = e.getX(1) - e.getX(0)
                    val dy = e.getY(1) - e.getY(0)
                    startDist = Math.max(1f, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat())
                    startAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    startScale = s.scale
                    startRot = s.rot
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = sel() ?: return false
                if (!begun && mode != 0) {
                    begun = true
                    listener?.onOverlayBegin()
                }
                if (mode == 1 && e.pointerCount == 1) {
                    val dx = e.x - lastX
                    val dy = e.y - lastY
                    if (Math.abs(dx) + Math.abs(dy) > 1f) moved = true
                    s.cx = clampF(s.cx + dx / W, -0.2f, 1.2f)
                    s.cy = clampF(s.cy + dy / H, -0.2f, 1.2f)
                    lastX = e.x
                    lastY = e.y
                    invalidate()
                    listener?.onOverlayLive()
                } else if (mode == 2) {
                    val dx = e.x - s.cx * W
                    val dy = e.y - s.cy * H
                    val d = Math.max(1f, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat())
                    val ang = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    s.scale = clampF(startScale * d / startDist, 0.1f, 8f)
                    s.rot = startRot + (ang - startAngle)
                    moved = true
                    invalidate()
                    listener?.onOverlayLive()
                } else if (mode == 3 && e.pointerCount >= 2) {
                    val dx = e.getX(1) - e.getX(0)
                    val dy = e.getY(1) - e.getY(0)
                    val d = Math.max(1f, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat())
                    val ang = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    s.scale = clampF(startScale * d / startDist, 0.1f, 8f)
                    s.rot = startRot + (ang - startAngle)
                    moved = true
                    invalidate()
                    listener?.onOverlayLive()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                val wasMoved = moved
                val m = mode
                mode = 0
                moved = false
                if (wasMoved && m != 0) listener?.onOverlayCommit()
                else if (m == 1 && System.currentTimeMillis() - downTime < 300) {
                    val s = sel()
                    if (s != null && s.kind == "text") {
                        // second tap on an already selected text opens the editor
                    }
                }
                return true
            }
        }
        return true
    }
}

/** The live stage: background, current clip(s) and overlays. */
class PreviewStage(ctx: Context) : AspectFrame(ctx) {
    val bg = BgView(ctx)
    val frame = FrameLayout(ctx)
    val zoomLayer = FrameLayout(ctx)
    val tvA = TextureView(ctx)
    val tvB = TextureView(ctx)
    val img = ImageView(ctx)
    val top = TopView(ctx)

    init {
        clipChildren = true
        setBackgroundColor(0xFF000000.toInt())
        addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.clipChildren = true
        zoomLayer.clipChildren = false
        frame.addView(zoomLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        val lpc = { FrameLayout.LayoutParams(10, 10, Gravity.CENTER) }
        zoomLayer.addView(tvA, lpc())
        zoomLayer.addView(tvB, lpc())
        img.scaleType = ImageView.ScaleType.FIT_XY
        zoomLayer.addView(img, lpc())
        addView(frame, FrameLayout.LayoutParams(10, 10, Gravity.CENTER))
        addView(top, FrameLayout.LayoutParams(MATCH, MATCH))
        tvA.visibility = View.INVISIBLE
        tvB.visibility = View.INVISIBLE
        img.visibility = View.INVISIBLE
    }
}

interface EngineListener {
    fun onTime(ms: Long)
    fun onPlayState(playing: Boolean)
}

/** Plays the timeline: video through two ExoPlayers, photos through an ImageView, audio items through small players. */
class PreviewEngine(val ctx: Context, val stage: PreviewStage, val projectProvider: () -> Project, val listener: EngineListener) : Choreographer.FrameCallback {
    private val players = arrayOfNulls<ExoPlayer>(2)
    private val keys = arrayOfNulls<String>(2)
    private var activeSlot = 0
    var timeMs = 0L
        private set
    var playing = false
        private set
    private var lastFrameNs = 0L
    private var shownClipId = ""
    private var shownSig = ""
    private var lastImgUri = ""
    private var released = false
    private val audioPlayers = HashMap<String, ExoPlayer>()
    private var lastUiNotify = 0L
    private var colorSig = ""

    private fun tv(slot: Int): TextureView = if (slot == 0) stage.tvA else stage.tvB

    private fun player(slot: Int): ExoPlayer {
        var p = players[slot]
        if (p == null) {
            p = ExoPlayer.Builder(ctx).build()
            p.setVideoTextureView(tv(slot))
            p.playWhenReady = false
            p.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            players[slot] = p
        }
        return p
    }

    private fun keyOf(c: Clip): String = c.uri + "|" + c.trimStart + "|" + c.trimEnd

    private fun load(slot: Int, c: Clip) {
        val p = player(slot)
        val mi = MediaItem.Builder().setUri(Uri.parse(c.uri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.trimStart).setEndPositionMs(c.trimEnd).build()
            ).build()
        p.setMediaItem(mi)
        p.prepare()
        p.playWhenReady = false
        keys[slot] = keyOf(c)
    }

    fun release() {
        released = true
        Choreographer.getInstance().removeFrameCallback(this)
        for (i in 0 until 2) {
            try {
                players[i]?.release()
            } catch (e: Exception) {
            }
            players[i] = null
            keys[i] = null
        }
        for (a in audioPlayers.values) {
            try {
                a.release()
            } catch (e: Exception) {
            }
        }
        audioPlayers.clear()
    }

    fun pausePlayersOnly() {
        for (p in players) p?.playWhenReady = false
        for (a in audioPlayers.values) a.playWhenReady = false
    }

    fun play() {
        val pr = projectProvider()
        if (pr.clips.isEmpty()) return
        if (timeMs >= pr.totalMs() - 30) timeMs = 0
        playing = true
        lastFrameNs = 0
        listener.onPlayState(true)
        apply(timeMs, true, true)
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        if (!playing) return
        playing = false
        Choreographer.getInstance().removeFrameCallback(this)
        pausePlayersOnly()
        listener.onPlayState(false)
        apply(timeMs, false, true)
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    /** Move the playhead. Used when the user scrubs or after an edit. */
    fun seek(ms: Long) {
        val pr = projectProvider()
        timeMs = clampL(ms, 0L, Math.max(0L, pr.totalMs()))
        if (playing) {
            apply(timeMs, true, true)
        } else {
            apply(timeMs, false, true)
        }
        listener.onTime(timeMs)
    }

    /** Re-render the current frame after the project was edited. */
    fun refresh() {
        val pr = projectProvider()
        if (timeMs > pr.totalMs()) timeMs = pr.totalMs()
        shownSig = ""
        apply(timeMs, playing, true)
        listener.onTime(timeMs)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (released || !playing) return
        val pr = projectProvider()
        val total = pr.totalMs()
        val dtMs = if (lastFrameNs == 0L) 0L else (frameTimeNanos - lastFrameNs) / 1_000_000L
        lastFrameNs = frameTimeNanos
        val i = pr.clipIndexAt(timeMs)
        var newT = timeMs
        if (i >= 0) {
            val c = pr.clips[i]
            val start = pr.clipStart(i)
            val dur = c.durMs()
            if (c.isImage) {
                newT = timeMs + Math.min(dtMs, 100L)
            } else {
                val slot = slotOf(c)
                val pl = if (slot >= 0) players[slot] else null
                if (pl != null && pl.playbackState == Player.STATE_READY && pl.playWhenReady) {
                    val pos = pl.currentPosition
                    val lt = (pos / c.speed).toLong()
                    newT = start + lt
                    if (newT < timeMs - 200) newT = timeMs
                } else if (pl != null && pl.playbackState == Player.STATE_ENDED) {
                    newT = start + dur
                } else {
                    // buffering: hold the clock
                    newT = timeMs
                }
            }
            if (newT >= start + dur - 8 && i < pr.clips.size - 1) newT = start + dur
        }
        if (newT >= total) {
            timeMs = total
            pause()
            listener.onTime(timeMs)
            return
        }
        timeMs = newT
        apply(timeMs, true, false)
        val now = System.currentTimeMillis()
        if (now - lastUiNotify >= 30) {
            lastUiNotify = now
            listener.onTime(timeMs)
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun slotOf(c: Clip): Int {
        val k = keyOf(c)
        if (keys[activeSlot] == k) return activeSlot
        if (keys[1 - activeSlot] == k) return 1 - activeSlot
        return -1
    }

    private fun applyAudio(pr: Project, t: Long, playingNow: Boolean) {
        val active = HashSet<String>()
        if (playingNow) {
            for (a in pr.audios) {
                if (t >= a.startMs && t < a.endMs()) {
                    active.add(a.id)
                    var pl = audioPlayers[a.id]
                    val srcPos = ((t - a.startMs) * a.speed).toLong()
                    if (pl == null) {
                        pl = ExoPlayer.Builder(ctx).build()
                        pl.setMediaItem(
                            MediaItem.Builder().setUri(Uri.parse(a.uri)).setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(a.trimStart).setEndPositionMs(a.trimEnd).build()
                            ).build()
                        )
                        pl.prepare()
                        pl.seekTo(srcPos)
                        pl.playWhenReady = true
                        audioPlayers[a.id] = pl
                    } else {
                        if (!pl.playWhenReady) pl.playWhenReady = true
                        if (Math.abs(pl.currentPosition - srcPos) > 400 && pl.playbackState == Player.STATE_READY) pl.seekTo(srcPos)
                    }
                    if (Math.abs(pl.playbackParameters.speed - a.speed) > 0.001f) pl.playbackParameters = PlaybackParameters(a.speed)
                    var v = a.volume
                    val lt = t - a.startMs
                    val rem = a.endMs() - t
                    if (a.fadeIn > 0 && lt < a.fadeIn) v *= lt.toFloat() / a.fadeIn
                    if (a.fadeOut > 0 && rem < a.fadeOut) v *= rem.toFloat() / a.fadeOut
                    pl.volume = clampF(v, 0f, 1f)
                }
            }
        }
        val it = audioPlayers.entries.iterator()
        while (it.hasNext()) {
            val en = it.next()
            if (!active.contains(en.key)) {
                try {
                    en.value.release()
                } catch (e: Exception) {
                }
                it.remove()
            }
        }
    }

    /** Core: show the right clip at time t and update transforms, colours, overlays. */
    private fun apply(t: Long, playingNow: Boolean, forceSeek: Boolean) {
        val pr = projectProvider()
        stage.aspect = Geo.aspect(pr)
        stage.bg.project = pr
        stage.bg.timeMs = t
        stage.bg.invalidate()
        stage.top.project = pr
        stage.top.timeMs = t
        stage.top.invalidate()
        applyAudio(pr, t, playingNow)
        val i = pr.clipIndexAt(t)
        if (i < 0) {
            stage.tvA.visibility = View.INVISIBLE
            stage.tvB.visibility = View.INVISIBLE
            stage.img.visibility = View.INVISIBLE
            return
        }
        val c = pr.clips[i]
        val lt = t - pr.clipStart(i)
        layoutClip(pr, c)
        if (c.isImage) {
            for (p in players) p?.playWhenReady = false
            stage.tvA.visibility = View.INVISIBLE
            stage.tvB.visibility = View.INVISIBLE
            if (lastImgUri != c.uri) {
                val b = BmpCache.get(c.uri, 1600)
                stage.img.setImageBitmap(b)
                lastImgUri = c.uri
            }
            stage.img.visibility = View.VISIBLE
        } else {
            stage.img.visibility = View.INVISIBLE
            var slot = slotOf(c)
            if (slot < 0) {
                slot = 1 - activeSlot
                load(slot, c)
                shownSig = ""
            }
            activeSlot = slot
            val pl = player(slot)
            tv(slot).visibility = View.VISIBLE
            tv(1 - slot).visibility = View.INVISIBLE
            val other = players[1 - slot]
            if (other != null) other.playWhenReady = false
            val want = (lt * c.speed).toLong()
            if (Math.abs(pl.playbackParameters.speed - c.speed) > 0.001f) pl.playbackParameters = PlaybackParameters(c.speed)
            pl.volume = if (pr.muteClips) 0f else clampF(c.volume, 0f, 1f)
            if (forceSeek || Math.abs(pl.currentPosition - want) > 350) {
                pl.setSeekParameters(if (playingNow) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
                pl.seekTo(clampL(want, 0L, Math.max(0L, c.trimEnd - c.trimStart - 1)))
            }
            pl.playWhenReady = playingNow
            // preload the next video clip into the idle slot
            if (playingNow && i + 1 < pr.clips.size) {
                val nx = pr.clips[i + 1]
                if (!nx.isImage && keys[1 - slot] != keyOf(nx)) load(1 - slot, nx)
            }
        }
        applyVisual(pr, i, lt, t, c)
    }

    private fun layoutClip(pr: Project, c: Clip) {
        val W = stage.width.toFloat()
        val H = stage.height.toFloat()
        if (W < 2f || H < 2f) return
        val sig = c.id + "|" + c.rot + "|" + c.flipH + "|" + c.flipV + "|" + c.fill + "|" + W + "x" + H + "|" + c.w + "x" + c.h
        if (sig == shownSig) return
        shownSig = sig
        val r = Geo.clipRect(c, W, H)
        val fp = stage.frame.layoutParams as FrameLayout.LayoutParams
        fp.width = Math.max(2, Math.round(r[2]))
        fp.height = Math.max(2, Math.round(r[3]))
        fp.gravity = Gravity.CENTER
        stage.frame.layoutParams = fp
        val s = r[2] / c.dispW().toFloat()
        val vw = Math.max(2, Math.round(c.w * s))
        val vh = Math.max(2, Math.round(c.h * s))
        for (v in arrayOf<View>(stage.tvA, stage.tvB, stage.img)) {
            val lpv = v.layoutParams as FrameLayout.LayoutParams
            lpv.width = vw
            lpv.height = vh
            lpv.gravity = Gravity.CENTER
            v.layoutParams = lpv
            v.rotation = c.rot.toFloat()
            v.scaleX = if (c.flipH) -1f else 1f
            v.scaleY = if (c.flipV) -1f else 1f
        }
    }

    private fun applyVisual(pr: Project, i: Int, lt: Long, t: Long, c: Clip) {
        val d = Fx.dyn(pr, i, lt, t)
        val xf = Fx.xform(c, d)
        val fw = stage.frame.layoutParams.width.toFloat()
        val fh = stage.frame.layoutParams.height.toFloat()
        stage.zoomLayer.scaleX = xf[0]
        stage.zoomLayer.scaleY = xf[0]
        stage.zoomLayer.translationX = xf[1] * fw
        stage.zoomLayer.translationY = xf[2] * fh
        stage.zoomLayer.rotation = xf[3]
        val cx = Fx.baseColor(c)
        Fx.applyDynColor(cx, d)
        val v: View = if (c.isImage) stage.img else tv(activeSlot)
        val sig = (if (c.isImage) "I" else "V" + activeSlot) + cx.m.joinToString(",") { String.format("%.3f", it) }
        if (sig != colorSig) {
            colorSig = sig
            if (cx.isIdentity()) {
                stage.img.colorFilter = null
                tv(0).setLayerType(View.LAYER_TYPE_NONE, null)
                tv(1).setLayerType(View.LAYER_TYPE_NONE, null)
            } else {
                val f = ColorMatrixColorFilter(ColorMatrix(cx.toAndroid()))
                if (c.isImage) {
                    stage.img.colorFilter = f
                } else {
                    val paint = Paint()
                    paint.colorFilter = f
                    tv(activeSlot).setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                    tv(1 - activeSlot).setLayerType(View.LAYER_TYPE_NONE, null)
                    stage.img.colorFilter = null
                }
            }
        }
    }
}
