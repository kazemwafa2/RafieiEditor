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
    val IDS = setOf("p_outline", "p_electric", "p_sparkle", "p_butterfly")
    fun isPerson(fx: String) = fx in IDS

    class Frames(val w: Int, val h: Int, val stepMs: Long, val masks: ArrayList<ByteArray>)
    class Look(val core: Bitmap?, val glow: Bitmap?, val pts: FloatArray, val box: FloatArray)

    private val frames = HashMap<String, Frames>()
    private val failed = HashSet<String>()
    private val busy = HashSet<String>()
    private val looks = LruCache<String, Look>(32)
    @Volatile var onReady: (() -> Unit)? = null

    fun key(c: Clip) = c.id + "|" + c.trimStart + "|" + c.trimEnd

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
                    val fr = mr.getScaledFrameAtTime((c.trimStart + t) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, tw, th)
                    var m: ByteArray? = null
                    if (fr != null) {
                        val r = segOne(seg, fr)
                        if (r != null) { w = r.first; h = r.second; m = r.third }
                    }
                    if (m == null) {
                        if (list.isEmpty()) { t += step; continue }
                        m = list[list.size - 1]
                    }
                    list.add(m)
                    t += step
                }
                if (list.isEmpty() || w == 0) return null
                return Frames(w, h, step, list)
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
            synchronized(this) {
                if (f != null) frames[k] = f else failed.add(k)
                busy.remove(k)
            }
            Bg.ui { onReady?.invoke() }
        }
    }

    /** Computes everything needed for the export (call off the main thread). */
    fun prepareBlocking(p: Project) {
        val need = p.fxs.filter { isPerson(it.fx) }
        if (need.isEmpty()) return
        var start = 0L
        for (c in p.clips) {
            val s = start
            val e = start + c.durMs()
            start = e
            if (need.none { it.startMs < e && it.endMs() > s }) continue
            val k = key(c)
            if (frames.containsKey(k) || failed.contains(k)) continue
            val f = try { compute(c, 300) } catch (e2: Throwable) { null }
            synchronized(this) { if (f != null) frames[k] = f else failed.add(k) }
        }
    }

    fun lookAt(c: Clip, relMs: Long): Look? {
        val k = key(c)
        val fr = synchronized(this) { frames[k] } ?: run { request(c); return null }
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
        val cb = Bitmap.createBitmap(core, w, h, Bitmap.Config.ARGB_8888)
        val gb = Bitmap.createBitmap(glow, w, h, Bitmap.Config.ARGB_8888)
        return Look(cb, gb, pts.toFloatArray(), floatArrayOf(minX.toFloat() / w, minY.toFloat() / h, (maxX + 1f) / w, (maxY + 1f) / h))
    }
}
