package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

/**
 * Person segmentation (on-device, offline: the ML Kit selfie model is bundled in the app) used by the
 * "around the body" effects. Masks are computed at low resolution, a few per second of video, and cached.
 */
object Seg {
    val IDS = setOf("p_outline", "p_electric", "p_sparkle", "p_butterfly", "p_flame", "p_bluefire", "p_colorflame", "p_blaze", "p_spiral")
    fun isPerson(fx: String) = fx in IDS

    class Frames(val w: Int, val h: Int, val stepMs: Long, val masks: ArrayList<ByteArray>, val shots: ArrayList<Bitmap?> = ArrayList())
    class Look(val core: Bitmap?, val glow: Bitmap?, val pts: FloatArray, val box: FloatArray, val fill: Bitmap? = null)

    private val frames = LinkedHashMap<String, Frames>() // insertion-ordered, capped below: one entry can hold ~25 MB
    private val MAX_CACHED = 4
    private val failed = HashSet<String>()
    private val busy = HashSet<String>()
    private val looks = LruCache<String, Look>(32)
    @Volatile var onReady: (() -> Unit)? = null

    fun key(c: Clip) = c.id + "|" + c.trimStart + "|" + c.trimEnd

    /** Stores a computed Frames and evicts the oldest clips when over the cap
     *  (references dropped only — recycled bitmaps could still be drawn by the render thread). */
    private fun store(k: String, f: Frames) {
        synchronized(this) {
            frames[k] = f
            while (frames.size > MAX_CACHED) {
                val it = frames.entries.iterator()
                it.next()
                it.remove()
            }
        }
    }

    /** Clears a previous failure so toggling the effect on again really retries
     *  (e.g. after a transient OOM or a codec hiccup). */
    fun retry(c: Clip) { synchronized(this) { failed.remove(key(c)) } }

    private fun segOne(seg: com.google.mlkit.vision.segmentation.Segmenter, src: Bitmap): Triple<Int, Int, ByteArray>? {
        return try {
            val bmp = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
            val mask = Tasks.await(seg.process(InputImage.fromBitmap(bmp, 0)))
            val buf = mask.buffer
            val w = mask.width
            val h = mask.height
            val out = ByteArray(w * h)
            buf.rewind()
            for (i in 0 until w * h) {
                val v = buf.getFloat()
                out[i] = (Math.max(0f, Math.min(1f, v)) * 255f).toInt().toByte()
            }
            Triple(w, h, out)
        } catch (e: Throwable) {
            null
        }
    }

    private fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val s = maxSide.toFloat() / Math.max(b.width, b.height)
        if (s >= 1f) return b
        return Bitmap.createScaledBitmap(b, Math.max(1, (b.width * s).toInt()), Math.max(1, (b.height * s).toInt()), true)
    }

    private fun compute(c: Clip, maxFrames: Int): Frames? {
        val seg = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        try {
            val list = ArrayList<ByteArray>()
            val shots = ArrayList<Bitmap?>()
            var w = 0
            var h = 0
            if (c.isImage) {
                val b = BmpCache.get(c.uri, 256) ?: return null
                val r = segOne(seg, fit(b, 256)) ?: return null
                w = r.first; h = r.second; list.add(r.third)
                return Frames(w, h, 1000L, list)
            }
            val mr = MediaMetadataRetriever()
            try {
                mr.setDataSource(App.ctx, Uri.parse(c.uri))
                val span = Math.max(1L, c.trimEnd - c.trimStart)
                val step = Math.max(150L, span / maxFrames)
                val dw = Math.max(1, c.dispW())
                val dh = Math.max(1, c.dispH())
                val sc = 256f / Math.max(dw, dh)
                val tw = Math.max(16, (dw * sc).toInt())
                val th = Math.max(16, (dh * sc).toInt())
                var t = 0L
                while (t < span) {
                    val fr = MediaUtil.frameScaled(mr, c.trimStart + t, MediaMetadataRetriever.OPTION_CLOSEST, tw, th)
                    var m: ByteArray? = null
                    var shot: Bitmap? = null
                    if (fr != null) {
                        try { shot = fit(fr, 200).copy(Bitmap.Config.RGB_565, false) } catch (e: Throwable) { }
                        val r = segOne(seg, fr)
                        if (r != null) { w = r.first; h = r.second; m = r.third }
                    }
                    if (m == null) {
                        if (list.isEmpty()) { t += step; continue }
                        m = list[list.size - 1]
                    }
                    list.add(m)
                    shots.add(shot ?: shots.lastOrNull())
                    t += step
                }
                if (list.isEmpty() || w == 0) return null
                return Frames(w, h, step, list, shots)
            } finally {
                try { mr.release() } catch (e: Throwable) { }
            }
        } catch (e: Throwable) {
            return null
        } finally {
            try { seg.close() } catch (e: Throwable) { }
        }
    }

    /** Starts the computation in the background (preview). */
    fun request(c: Clip) {
        val k = key(c)
        synchronized(this) {
            if (frames.containsKey(k) || failed.contains(k) || !busy.add(k)) return
        }
        val copy = c.copyOf().also { it.id = c.id }
        Bg.run {
            val f = try { compute(copy, 300) } catch (e: Throwable) { null }
            if (f != null) store(k, f) else synchronized(this) { failed.add(k) }
            busy.remove(k)
            Bg.ui { onReady?.invoke() }
        }
    }

    /** Computes everything needed for the export (call off the main thread). */
    fun needed(p: Project): Boolean = p.fxs.any { isPerson(it.fx) } || p.clips.any { it.cut.isNotEmpty() } || p.overlays.any { (it.kind == "text" && it.tfx == "ring") || (it.kind == "eq" && Eq.tplOf(it).behind) }

    fun prepareBlocking(p: Project) {
        class Iv(val s: Long, val e: Long)
        val need = ArrayList<Iv>()
        for (f in p.fxs) if (isPerson(f.fx)) need.add(Iv(f.startMs, f.endMs()))
        for (o in p.overlays) if ((o.kind == "text" && o.tfx == "ring") || (o.kind == "eq" && Eq.tplOf(o).behind)) need.add(Iv(o.startMs, o.endMs()))
        val anyCut = p.clips.any { it.cut.isNotEmpty() }
        if (need.isEmpty() && !anyCut) return
        var start = 0L
        for (c in p.clips) {
            val s = start
            val e = start + c.durMs()
            start = e
            if (c.cut.isEmpty() && need.none { it.s < e && it.e > s }) continue
            val k = key(c)
            if (frames.containsKey(k) || failed.contains(k)) continue
            val f = try { compute(c, 300) } catch (e2: Throwable) { null }
            if (f != null) store(k, f) else synchronized(this) { failed.add(k) }
        }
    }

    /** Small copy of the video frame near [relMs] (used by the TV of the room design). */
    fun shotAt(c: Clip, relMs: Long): Bitmap? {
        val fr = synchronized(this) { frames[key(c)] } ?: return null
        if (fr.shots.isEmpty()) return null
        val idx = Math.max(0, Math.min(fr.shots.size - 1, (((relMs * c.speed).toLong()) / fr.stepMs).toInt()))
        return fr.shots[idx]
    }

    /**
     * Person look for the frame at [relMs]. When [sync] is true (final render) a cache miss is
     * recomputed on the spot instead of returning nothing — a silently missing mask would leave
     * the design out of the exported file. Preview keeps the async path.
     */
    fun lookAt(c: Clip, relMs: Long, sync: Boolean = false): Look? {
        val k = key(c)
        var fr = synchronized(this) { frames[k] }
        if (fr == null) {
            if (!sync) {
                request(c)
                return null
            }
            val go = synchronized(this) { !failed.contains(k) && busy.add(k) }
            if (!go) return null
            val f = try { compute(c, 300) } catch (e: Throwable) { null }
            if (f != null) store(k, f) else synchronized(this) { failed.add(k) }
            synchronized(this) { busy.remove(k) }
            fr = synchronized(this) { frames[k] } ?: return null
        }
        val idx = if (c.isImage) 0 else Math.max(0, Math.min(fr.masks.size - 1, (((relMs * c.speed).toLong()) / fr.stepMs).toInt()))
        val lk = k + "#" + idx
        var l = looks.get(lk)
        if (l == null) {
            l = build(fr.masks[idx], fr.w, fr.h)
            looks.put(lk, l)
        }
        return l
    }

    private fun build(m: ByteArray, w: Int, h: Int): Look {
        val n = w * h
        val inside = BooleanArray(n)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((m[y * w + x].toInt() and 0xFF) > 128) {
                inside[y * w + x] = true
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return Look(null, null, FloatArray(0), floatArrayOf(0f, 0f, 0f, 0f))
        val edge = BooleanArray(n)
        val pts = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!inside[i]) continue
            val e = x == 0 || y == 0 || x == w - 1 || y == h - 1 || !inside[i - 1] || !inside[i + 1] || !inside[i - w] || !inside[i + w]
            if (e) { edge[i] = true }
        }
        val core = IntArray(n)
        val glow = IntArray(n)
        var cnt = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!edge[i]) continue
            core[i] = Color.WHITE
            cnt++
            for (dy in -2..2) for (dx in -2..2) {
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                val d = Math.abs(dx) + Math.abs(dy)
                val a = if (d == 0) 255 else if (d <= 2) 150 else 70
                val j = yy * w + xx
                if (Color.alpha(glow[j]) < a) glow[j] = Color.argb(a, 255, 255, 255)
            }
        }
        val stride = Math.max(1, cnt / 300)
        var k = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (edge[y * w + x]) {
                if (k % stride == 0) { pts.add((x + 0.5f) / w); pts.add((y + 0.5f) / h) }
                k++
            }
        }
        val fillPx = IntArray(n)
        for (i in 0 until n) if (inside[i]) fillPx[i] = Color.WHITE
        val fb = Bitmap.createBitmap(fillPx, w, h, Bitmap.Config.ARGB_8888)
        val cb = Bitmap.createBitmap(core, w, h, Bitmap.Config.ARGB_8888)
        val gb = Bitmap.createBitmap(glow, w, h, Bitmap.Config.ARGB_8888)
        return Look(cb, gb, pts.toFloatArray(), floatArrayOf(minX.toFloat() / w, minY.toFloat() / h, (maxX + 1f) / w, (maxY + 1f) / h), fb)
    }
}
