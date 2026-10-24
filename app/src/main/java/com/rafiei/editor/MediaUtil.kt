package com.rafiei.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import java.util.concurrent.Executors

object MediaUtil {

    fun displayName(c: Context, uri: Uri): String {
        try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val s = cur.getString(0)
                    if (s != null) return s
                }
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "media"
    }

    fun isImage(c: Context, uri: Uri): Boolean {
        val t = try {
            c.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        if (t != null) return t.startsWith("image/")
        val n = (uri.lastPathSegment ?: "").lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".heic")
    }

    private fun extOf(name: String, def: String): String {
        val i = name.lastIndexOf('.')
        if (i < 0 || i >= name.length - 1 || name.length - i > 6) return def
        return name.substring(i)
    }

    fun copyTo(c: Context, uri: Uri, dst: File): Boolean {
        return try {
            c.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dst).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            dst.exists() && dst.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Decodes an image from a uri, applies EXIF rotation and limits its longest side. */
    fun loadBitmap(c: Context, uri: Uri, maxSide: Int): Bitmap? {
        try {
            val bo = BitmapFactory.Options()
            bo.inJustDecodeBounds = true
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bo) }
            if (bo.outWidth <= 0 || bo.outHeight <= 0) return null
            var sample = 1
            while (Math.max(bo.outWidth, bo.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                c.contentResolver.openInputStream(uri)?.use {
                    orientation = ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            } catch (e: Exception) {
            }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            val big = Math.max(bmp.width, bmp.height)
            if (big > maxSide) {
                val s = maxSide.toFloat() / big
                val r = Bitmap.createScaledBitmap(bmp, Math.max(1, (bmp.width * s).toInt()), Math.max(1, (bmp.height * s).toInt()), true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            return bmp
        } catch (e: Throwable) {
            return null
        }
    }

    fun loadBitmapFile(path: String, maxSide: Int): Bitmap? {
        return try {
            val bo = BitmapFactory.Options()
            bo.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, bo)
            var sample = 1
            while (Math.max(bo.outWidth, bo.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            BitmapFactory.decodeFile(path, o2)
        } catch (e: Throwable) {
            null
        }
    }

    /** Imports an image or video into the project's media folder and returns a ready Clip. */
    fun importClip(c: Context, uri: Uri, pid: String): Clip? {
        val name = displayName(c, uri)
        val clip = Clip()
        if (isImage(c, uri)) {
            val bmp = loadBitmap(c, uri, 2560) ?: return null
            val f = File(Store.mediaDir(c, pid), clip.id + ".jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
            clip.uri = Uri.fromFile(f).toString()
            clip.isImage = true
            clip.w = bmp.width
            clip.h = bmp.height
            clip.hasAudio = false
            clip.imgDur = 4000
            bmp.recycle()
            return clip
        }
        val f = File(Store.mediaDir(c, pid), clip.id + extOf(name, ".mp4"))
        if (!copyTo(c, uri, f)) return null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(f.absolutePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (!hasVideo || w <= 0 || h <= 0 || dur <= 0) {
                f.delete()
                return null
            }
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            clip.uri = Uri.fromFile(f).toString()
            clip.isImage = false
            clip.srcDurMs = dur
            clip.trimStart = 0
            clip.trimEnd = dur
            clip.w = w
            clip.h = h
            clip.hasAudio = hasAudio
            return clip
        } catch (e: Exception) {
            f.delete()
            return null
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Imports an audio file (or the audio of a video) into the project's media folder. */
    fun importAudio(c: Context, uri: Uri, pid: String, kind: String): AudioItem? {
        val name = displayName(c, uri)
        val a = AudioItem()
        val f = File(Store.mediaDir(c, pid), a.id + extOf(name, ".m4a"))
        if (!copyTo(c, uri, f)) return null
        return audioFromFile(f, name.substringBeforeLast('.'), kind, a)
    }

    fun audioFromFile(f: File, name: String, kind: String, a: AudioItem = AudioItem()): AudioItem? {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(f.absolutePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (dur <= 0) return null
            a.uri = Uri.fromFile(f).toString()
            a.name = name
            a.kind = kind
            a.srcDurMs = dur
            a.trimStart = 0
            a.trimEnd = dur
            return a
        } catch (e: Exception) {
            return null
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }
    }

    fun pathOf(uri: String): String = Uri.parse(uri).path ?: uri

    /** Grab one video frame (scaled so that the width is at most maxW). */
    fun frameAt(path: String, timeMs: Long, maxW: Int, exact: Boolean = false): Bitmap? {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(path)
            val opt = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            val b = mmr.getFrameAtTime(timeMs * 1000L, opt) ?: return null
            if (b.width <= maxW) return b
            val s = maxW.toFloat() / b.width
            val r = Bitmap.createScaledBitmap(b, maxW, Math.max(1, (b.height * s).toInt()), true)
            if (r !== b) b.recycle()
            return r
        } catch (e: Exception) {
            return null
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Cheap box blur used for the blurred canvas background. */
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        for (pass in 0 until 2) {
            boxH(px, tmp, w, h, radius)
            boxV(tmp, px, w, h, radius)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (y in 0 until h) {
            var ra = 0; var ga = 0; var ba = 0
            val row = y * w
            for (i in -r..r) {
                val p = src[row + clampI(i, 0, w - 1)]
                ra += (p shr 16) and 255; ga += (p shr 8) and 255; ba += p and 255
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((ra / div) shl 16) or ((ga / div) shl 8) or (ba / div)
                val add = src[row + clampI(x + r + 1, 0, w - 1)]
                val sub = src[row + clampI(x - r, 0, w - 1)]
                ra += ((add shr 16) and 255) - ((sub shr 16) and 255)
                ga += ((add shr 8) and 255) - ((sub shr 8) and 255)
                ba += (add and 255) - (sub and 255)
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (x in 0 until w) {
            var ra = 0; var ga = 0; var ba = 0
            for (i in -r..r) {
                val p = src[clampI(i, 0, h - 1) * w + x]
                ra += (p shr 16) and 255; ga += (p shr 8) and 255; ba += p and 255
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((ra / div) shl 16) or ((ga / div) shl 8) or (ba / div)
                val add = src[clampI(y + r + 1, 0, h - 1) * w + x]
                val sub = src[clampI(y - r, 0, h - 1) * w + x]
                ra += ((add shr 16) and 255) - ((sub shr 16) and 255)
                ga += ((add shr 8) and 255) - ((sub shr 8) and 255)
                ba += (add and 255) - (sub and 255)
            }
        }
    }

    /** Saves a finished file into the public gallery (Movies/RafieiEditor or Pictures/RafieiEditor). */
    fun saveToGallery(c: Context, file: File, displayName: String, mime: String, video: Boolean): Uri? {
        return try {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            val rel = if (video) Environment.DIRECTORY_MOVIES + "/RafieiEditor" else Environment.DIRECTORY_PICTURES + "/RafieiEditor"
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = c.contentResolver.insert(collection, values) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            val v2 = ContentValues()
            v2.put(MediaStore.MediaColumns.IS_PENDING, 0)
            c.contentResolver.update(uri, v2, null, null)
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun saveThumb(c: Context, p: Project) {
        if (p.clips.isEmpty()) return
        val cl = p.clips[0]
        val b: Bitmap? = if (cl.isImage) loadBitmapFile(pathOf(cl.uri), 320) else frameAt(pathOf(cl.uri), cl.trimStart + 200, 320)
        if (b != null) {
            try {
                FileOutputStream(Store.thumbFile(c, p.id)).use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (e: Exception) {
            }
            b.recycle()
        }
    }
}

/** Asynchronous cache for small timeline thumbnails and blurred backgrounds. */
object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val exec = Executors.newSingleThreadExecutor()
    private val pending = HashSet<String>()

    fun peek(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, b: Bitmap) {
        synchronized(cache) { cache.put(key, b) }
    }

    /** Request a video frame; onReady is invoked on the UI thread when it is cached. */
    fun frame(path: String, isImage: Boolean, timeMs: Long, heightPx: Int, onReady: () -> Unit): Bitmap? {
        val bucket = if (isImage) 0L else (timeMs / 500L) * 500L
        val key = "$path@$bucket@$heightPx"
        val hit = peek(key)
        if (hit != null) return hit
        synchronized(pending) {
            if (pending.contains(key)) return null
            pending.add(key)
        }
        exec.execute {
            try {
                val raw: Bitmap? = if (isImage) MediaUtil.loadBitmapFile(path, heightPx * 2) else MediaUtil.frameAt(path, bucket, heightPx * 2)
                if (raw != null) {
                    val s = heightPx.toFloat() / raw.height
                    val b = if (raw.height == heightPx) raw else Bitmap.createScaledBitmap(raw, Math.max(1, (raw.width * s).toInt()), heightPx, true)
                    if (b !== raw) raw.recycle()
                    put(key, b)
                    Bg.ui { onReady() }
                }
            } catch (e: Throwable) {
            } finally {
                synchronized(pending) { pending.remove(key) }
            }
        }
        return null
    }

    /** Blurred background frame, computed synchronously (call off the main thread, or accept a short stall). */
    fun blurredSync(path: String, isImage: Boolean, timeMs: Long): Bitmap? {
        val bucket = if (isImage) 0L else (timeMs / 1500L) * 1500L
        val key = "blur:$path@$bucket"
        val hit = peek(key)
        if (hit != null) return hit
        val raw: Bitmap? = if (isImage) MediaUtil.loadBitmapFile(path, 160) else MediaUtil.frameAt(path, bucket, 160)
        if (raw == null) return null
        val small = if (raw.width > 96) Bitmap.createScaledBitmap(raw, 96, Math.max(1, raw.height * 96 / raw.width), true) else raw
        val b = MediaUtil.blur(small, 4)
        if (small !== raw) small.recycle()
        raw.recycle()
        put(key, b)
        return b
    }
}

/** Audio waveform peaks (one value per 50 ms) computed in the background. */
object Waveform {
    private val cache = HashMap<String, FloatArray>()
    private val pending = HashSet<String>()
    private val exec = Executors.newSingleThreadExecutor()

    fun peek(path: String): FloatArray? = synchronized(cache) { cache[path] }

    fun request(path: String, onReady: () -> Unit) {
        if (peek(path) != null) return
        synchronized(pending) {
            if (pending.contains(path)) return
            pending.add(path)
        }
        exec.execute {
            try {
                val r = compute(path)
                synchronized(cache) { cache[path] = r ?: FloatArray(0) }
                Bg.ui { onReady() }
            } catch (e: Throwable) {
                synchronized(cache) { cache[path] = FloatArray(0) }
            } finally {
                synchronized(pending) { pending.remove(path) }
            }
        }
    }

    private fun compute(path: String): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    track = i; fmt = f; break
                }
            }
            if (track < 0 || fmt == null) return null
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val codecInst = MediaCodec.createDecoderByType(mime)
            codec = codecInst
            codecInst.configure(fmt, null, null, 0)
            codecInst.start()
            val info = MediaCodec.BufferInfo()
            val window = Math.max(1, sampleRate / 20)
            val peaks = ArrayList<Float>()
            var curMax = 0
            var cnt = 0
            var inDone = false
            var outDone = false
            var guard = 0
            while (!outDone && guard < 400000) {
                guard++
                if (!inDone) {
                    val ii = codecInst.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codecInst.getInputBuffer(ii)
                        val n = if (buf != null) ex.readSampleData(buf, 0) else -1
                        if (n < 0) {
                            codecInst.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codecInst.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codecInst.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val ob = codecInst.getOutputBuffer(oi)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        while (sb.remaining() >= channels) {
                            var m = 0
                            for (ch in 0 until channels) {
                                val v = Math.abs(sb.get().toInt())
                                if (v > m) m = v
                            }
                            if (m > curMax) curMax = m
                            cnt++
                            if (cnt >= window) {
                                peaks.add(curMax / 32768f)
                                curMax = 0
                                cnt = 0
                            }
                        }
                    }
                    codecInst.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                }
            }
            val arr = FloatArray(peaks.size)
            var mx = 0.0001f
            for (i in arr.indices) {
                arr[i] = peaks[i]
                if (arr[i] > mx) mx = arr[i]
            }
            for (i in arr.indices) arr[i] = Math.sqrt((arr[i] / mx).toDouble()).toFloat()
            return arr
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Throwable) {
            }
            try {
                codec?.release()
            } catch (e: Throwable) {
            }
            try {
                ex.release()
            } catch (e: Throwable) {
            }
        }
    }
}
