package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaExtractor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

/** Makes a reversed copy of a video segment (up to 20 s). Frames are read backwards and re-encoded with
 *  the hardware encoder. The clip speed is honoured: the reversed copy keeps the same timeline duration
 *  and the same motion pace. The audio can be reversed separately (PCM) so it is not lost. */
object Reverser {
    class Out(val w: Int, val h: Int, val durMs: Long)

    fun reverse(path: String, trimStartMs: Long, trimEndMs: Long, speed: Float, out: File, onProgress: (Int) -> Unit): Out? {
        val mmr = MediaMetadataRetriever()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            mmr.setDataSource(path)
            val seg = trimEndMs - trimStartMs
            if (seg < 200L || seg > 20000L) return null
            val spd = if (speed > 0.05f) speed else 1f
            val fps = 24
            // n frames at [fps] cover seg/spd of source time -> the reversed clip lasts seg/spd on the
            // timeline, exactly like the original clip.
            val n = Math.max(2, (seg.toDouble() * fps / 1000.0 / spd).toInt().coerceAtMost(20 * fps))

            fun frameAt(i: Int): Bitmap? {
                val ms = Math.max(trimStartMs.toDouble(), trimEndMs - (i + 0.5) * spd * 1000.0 / fps)
                var b = MediaUtil.frameScaled(mmr, ms.toLong(), MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280)
                if (b == null) b = MediaUtil.frameScaled(mmr, ms.toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 1280) ?: return null
                if (b.width > 1280 || b.height > 1280) {
                    val s = 1280f / Math.max(b.width, b.height)
                    b = Bitmap.createScaledBitmap(b, Math.max(2, (b.width * s).toInt()), Math.max(2, (b.height * s).toInt()), true)
                }
                return b
            }

            val first = frameAt(0) ?: return null
            val w = Math.max(2, first.width and 1.inv())
            val h = Math.max(2, first.height and 1.inv())
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(2_000_000, (w * h * fps * 0.15f).toInt()))
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            if (out.exists()) out.delete()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var written = 0L
            val info = MediaCodec.BufferInfo()

            fun drain(end: Boolean) {
                var guard = 0
                while (true) {
                    val idx = codec!!.dequeueOutputBuffer(info, if (end) 10000L else 0L)
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!end) break
                        guard++
                        if (guard > 2000) break // up to ~20 s: slow encoders must still flush fully
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer!!.addTrack(codec!!.outputFormat)
                        muxer!!.start()
                        started = true
                    } else if (idx >= 0) {
                        val buf = codec!!.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started && buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            info.presentationTimeUs = written * 1000000L / fps
                            written++
                            muxer!!.writeSampleData(track, buf, info)
                        }
                        codec!!.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for (i in 0 until n) {
                val b = if (i == 0) first else (frameAt(i) ?: first)
                val cv = surface.lockCanvas(null)
                cv.drawColor(0xFF000000.toInt())
                cv.drawBitmap(b, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
                surface.unlockCanvasAndPost(cv)
                if (b !== first) b.recycle()
                drain(false)
                onProgress((i + 1) * 100 / n)
            }
            codec.signalEndOfInputStream()
            drain(true)
            if (written <= 0) return null // nothing encoded: never report success
            try { muxer.stop() } catch (e: Throwable) { return null } // a failed stop = a broken file
            return Out(w, h, written * 1000L / fps)
        } catch (e: Throwable) {
            println("RafieiEditor reverse failed: " + e.javaClass.simpleName + ": " + e.message)
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { muxer?.release() } catch (e: Throwable) { }
            try { mmr.release() } catch (e: Throwable) { }
        }
    }

    /** Decodes the audio of the given range, reverses it frame-exact (stays L/R correct) and
     *  writes a 16-bit PCM WAV. Returns the duration in ms, or null. Ranges are bounded by the caller. */
    fun reverseAudio(path: String, fromMs: Long, toMs: Long, out: File, onProgress: (Int) -> Unit): Long? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var ti = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { ti = i; fmt = f; break }
            }
            if (ti < 0 || fmt == null) return null
            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var ch = Math.max(1, if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2)
            ex.selectTrack(ti)
            ex.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val syncUs = Math.max(0L, ex.sampleTime)
            var skip = if (syncUs < fromMs * 1000L) (fromMs * 1000L - syncUs) * sr / 1_000_000L else 0L
            val lastFrame = if (toMs > fromMs) Math.max(0L, toMs * 1000L - syncUs) * sr / 1_000_000L else 0L
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var buf = ShortArray(1 shl 18)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var srcFrame = 0L
            var guard = 0
            while (!outDone) {
                if (srcFrame > lastFrame) break
                if (!inDone) {
                    val ib = codec.dequeueInputBuffer(10000)
                    if (ib >= 0) {
                        val b = codec.getInputBuffer(ib)!!
                        val cnt = ex.readSampleData(b, 0)
                        if (cnt < 0 || ex.sampleTime > toMs * 1000L) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(ib, 0, cnt, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 10000)
                if (ob >= 0) {
                    if (info.size > 0) {
                        val b = codec.getOutputBuffer(ob)!!
                        b.position(info.offset)
                        b.limit(info.offset + info.size)
                        val sb = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var i = 0
                        val total = sb.remaining()
                        while (i + ch <= total) {
                            if (srcFrame > lastFrame) break
                            if (skip > 0) { skip--; srcFrame++; i += ch; continue }
                            if (n + ch > buf.size) buf = buf.copyOf(buf.size * 2)
                            for (k in 0 until ch) buf[n++] = sb.get(i + k)
                            srcFrame++
                            i += ch
                        }
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                } else if (ob == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    guard++
                    if (inDone && guard > 3000) break
                }
            }
            if (n < ch) return null
            // reverse frame order (a frame = ch samples) so stereo stays correct
            var a = 0
            var b2 = n - ch
            while (a < b2) {
                for (k in 0 until ch) { val t = buf[a + k]; buf[a + k] = buf[b2 + k]; buf[b2 + k] = t }
                a += ch; b2 -= ch
            }
            writeWav(out, buf, n, sr, ch)
            return (n / ch) * 1000L / sr
        } catch (e: Throwable) {
            println("RafieiEditor reverseAudio failed: " + e.javaClass.simpleName + ": " + e.message)
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { ex.release() } catch (e: Throwable) { }
        }
    }

    private fun writeWav(f: File, pcm: ShortArray, n: Int, sr: Int, ch: Int) {
        val dataLen = n * 2
        f.outputStream().use { o ->
            val h = ByteArray(44)
            putStr(h, 0, "RIFF"); putInt(h, 4, 36 + dataLen); putStr(h, 8, "WAVE")
            putStr(h, 12, "fmt "); putInt(h, 16, 16); putShort(h, 20, 1); putShort(h, 22, ch)
            putInt(h, 24, sr); putInt(h, 28, sr * ch * 2); putShort(h, 32, ch * 2); putShort(h, 34, 16)
            putStr(h, 36, "data"); putInt(h, 40, dataLen)
            o.write(h)
            val bb = java.nio.ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                if (bb.position() + 2 > bb.capacity()) { o.write(bb.array(), 0, bb.position()); bb.clear() }
                bb.putShort(pcm[i])
            }
            o.write(bb.array(), 0, bb.position())
        }
    }
    private fun putStr(b: ByteArray, off: Int, s: String) { for (i in s.indices) b[off + i] = s[i].toByte() }
    private fun putInt(b: ByteArray, off: Int, v: Int) { b[off] = v.toByte(); b[off + 1] = (v shr 8).toByte(); b[off + 2] = (v shr 16).toByte(); b[off + 3] = (v shr 24).toByte() }
    private fun putShort(b: ByteArray, off: Int, v: Int) { b[off] = v.toByte(); b[off + 1] = (v shr 8).toByte() }
}

fun EditorActivity.reverseSelectedClip() {
    val c = selClip() ?: targetClip()
    if (c == null || c.isImage) { toast(L("یک ویدیو را انتخاب کنید.", "Select a video clip.")); return }
    if (c.trimEnd - c.trimStart > 20000L) { toast(L("برای معکوس‌کردن، کلیپ را تا ۲۰ ثانیه برش بدهید.", "Trim the clip to 20 s or less to reverse it.")); return }
    val dlg = progressDialog(L("در حال معکوس‌کردن… ۰٪", "Reversing… 0%"))
    val ctx = applicationContext
    val pid = proj.id
    val path = MediaUtil.pathOf(c.uri)
    val ts = c.trimStart
    val te = c.trimEnd
    val spd = if (c.speed > 0.05f) c.speed else 1f
    val hadAudio = c.hasAudio
    val vol = c.volume
    val out = java.io.File(Store.mediaDir(ctx, pid), "rev_" + uid() + ".mp4")
    Bg.run {
        val r = Reverser.reverse(path, ts, te, spd, out) { pct -> Bg.ui { dlg.setMessage(L("در حال معکوس‌کردن… $pct٪", "Reversing… $pct%")) } }
        var audioRev: File? = null
        var audioDur = 0L
        if (r != null && hadAudio) {
            Bg.ui { dlg.setMessage(L("در حال معکوس‌کردن صدا…", "Reversing audio…")) }
            val af = File(Store.mediaDir(ctx, pid), "rev_" + uid() + ".wav")
            val ad = try { Reverser.reverseAudio(path, ts, te, af) { } } catch (e: Throwable) { null }
            if (ad != null && ad > 200L) { audioRev = af; audioDur = ad } else af.delete()
        }
        Bg.ui {
            dlg.dismiss()
            if (r == null) {
                out.delete(); audioRev?.delete()
                toast(L("معکوس‌کردن روی این ویدیو ممکن نشد.", "Could not reverse this video."))
            } else {
                beginChange()
                val idx = proj.clips.indexOf(c)
                c.uri = android.net.Uri.fromFile(out).toString()
                c.trimStart = 0L
                c.trimEnd = r.durMs
                c.srcDurMs = r.durMs
                c.speed = 1f // the reversed file already has the original timeline length
                c.w = r.w
                c.h = r.h
                if (audioRev != null) {
                    c.hasAudio = false
                    val a = AudioItem()
                    a.uri = android.net.Uri.fromFile(audioRev).toString()
                    a.name = L("صدای معکوس‌شده", "Reversed audio")
                    a.kind = "music"
                    a.startMs = if (idx >= 0) proj.clipStart(idx) else 0L
                    a.srcDurMs = audioDur
                    a.trimStart = 0L
                    a.trimEnd = audioDur
                    a.volume = vol
                    proj.audios.add(a)
                }
                changed()
                refreshToolbar()
                toast(if (audioRev != null) L("ویدیو و صدای آن معکوس شد.", "Video and audio were reversed.")
                      else L("ویدیو معکوس شد (صدایی برای معکوس‌کردن نبود).", "Reversed (there was no audio to reverse)."))
            }
        }
    }
}
