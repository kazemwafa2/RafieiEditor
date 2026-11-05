package com.rafiei.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.util.LruCache

/** Smooth small video frames for the TV of the room design: ~20 fps, fetched on demand and cached. */
object TvShots {
    private const val STEP = 50L
    private val cache = LruCache<String, Bitmap>(110)
    private val rets = HashMap<String, MediaMetadataRetriever>()
    private val pending = HashSet<String>()
    private val missing = HashSet<String>()

    private fun retriever(c: Clip): MediaMetadataRetriever? {
        synchronized(rets) {
            rets[c.id]?.let { return it }
            if (missing.contains(c.id)) return null
            return try {
                val m = MediaMetadataRetriever()
                m.setDataSource(App.ctx, Uri.parse(c.uri))
                rets[c.id] = m
                m
            } catch (e: Throwable) { missing.add(c.id); null }
        }
    }

    private fun fetch(c: Clip, bucket: Long): Bitmap? {
        val k = c.id + "#" + bucket
        synchronized(cache) { cache.get(k)?.let { return it } }
        val mr = retriever(c) ?: return null
        val src = c.trimStart + Math.round(bucket * STEP * c.speed)
        val dw = Math.max(1, c.dispW()); val dh = Math.max(1, c.dispH())
        val sc = 320f / Math.max(dw, dh)
        val tw = Math.max(16, (dw * sc).toInt()); val th = Math.max(16, (dh * sc).toInt())
        val b = try {
            synchronized(mr) { MediaUtil.frameScaled(mr, src, MediaMetadataRetriever.OPTION_CLOSEST, tw, th) }
        } catch (e: Throwable) { null } ?: return null
        val r = try { b.copy(Bitmap.Config.RGB_565, false) } catch (e: Throwable) { b }
        synchronized(cache) { cache.put(k, r) }
        return r
    }

    fun get(c: Clip, relMs: Long): Bitmap? {
        if (c.isImage) return BmpCache.get(c.uri, 320)
        val bucket = Math.max(0L, relMs) / STEP
        val k = c.id + "#" + bucket
        synchronized(cache) { cache.get(k)?.let { return it } }
        if (Looper.myLooper() != Looper.getMainLooper()) return fetch(c, bucket) ?: Seg.shotAt(c, relMs)
        // preview: never block the screen; fetch this and the next frames in the background
        for (b in bucket..bucket + 4) {
            val kk = c.id + "#" + b
            val has = synchronized(cache) { cache.get(kk) != null }
            if (has) continue
            val go = synchronized(pending) { pending.add(kk) }
            if (!go) continue
            val copy = c.copyOf().also { it.id = c.id }
            Bg.run {
                try { fetch(copy, b) } finally { synchronized(pending) { pending.remove(kk) } }
                if (b == bucket) Bg.ui { Seg.onReady?.invoke() }
            }
        }
        for (b in bucket - 1 downTo Math.max(0L, bucket - 6)) synchronized(cache) { cache.get(c.id + "#" + b) }?.let { return it }
        return Seg.shotAt(c, relMs)
    }

    private class Src(val clip: Clip?, val image: Bitmap?)
    private val srcs = HashMap<String, Src>()

    private fun srcOf(uri: String): Src {
        synchronized(srcs) { srcs[uri]?.let { return it } }
        var r: Src
        try {
            val mr = MediaMetadataRetriever()
            mr.setDataSource(App.ctx, Uri.parse(uri))
            val hasVideo = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (hasVideo) {
                val c = Clip()
                c.id = "tv_" + Math.abs(uri.hashCode())
                c.uri = uri
                c.srcDurMs = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "1000").toLong()
                c.trimStart = 0L; c.trimEnd = c.srcDurMs
                c.w = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1280").toInt()
                c.h = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "720").toInt()
                val rot = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0").toInt()
                c.rot = 0
                if (rot % 180 != 0) { val t = c.w; c.w = c.h; c.h = t }
                r = Src(c, null)
            } else r = Src(null, null)
            mr.release()
        } catch (e: Throwable) {
            r = Src(null, null)
        }
        if (r.clip == null) {
            val b = try {
                App.ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().also { o -> o.inSampleSize = 2 }) }
            } catch (e: Throwable) { null }
            r = Src(null, b)
        }
        synchronized(srcs) { srcs[uri] = r }
        return r
    }

    /** Frame of the user-chosen TV video (or picture) at [relMs] since the overlay started. */
    fun forSource(uri: String, relMs: Long, loop: Boolean): Bitmap? {
        val s = srcOf(uri)
        s.image?.let { return it }
        val c = s.clip ?: return null
        var t = relMs
        if (c.srcDurMs > 0) t = if (loop) relMs % c.srcDurMs else Math.min(relMs, c.srcDurMs - 50L)
        return get(c, t)
    }
}
