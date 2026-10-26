package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File

/** Makes a reversed copy of a video segment (picture only, up to 20 s). Frames are read backwards and re-encoded with the hardware encoder. */
object Reverser {
    class Out(val w: Int, val h: Int, val durMs: Long)

    fun reverse(path: String, trimStartMs: Long, trimEndMs: Long, out: File, onProgress: (Int) -> Unit): Out? {
        val mmr = MediaMetadataRetriever()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            mmr.setDataSource(path)
            val seg = trimEndMs - trimStartMs
            if (seg < 200L || seg > 20000L) return null
            val fps = 24
            val n = Math.max(2, (seg * fps / 1000L).toInt())

            fun frameAt(i: Int): Bitmap? {
                val ms = Math.max(trimStartMs.toDouble(), trimEndMs - (i + 0.5) * 1000.0 / fps)
                var b = mmr.getScaledFrameAtTime((ms * 1000.0).toLong(), MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280) ?: return null
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
                        if (guard > 600) break
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
            try { muxer.stop() } catch (e: Throwable) { }
            return Out(w, h, written * 1000L / fps)
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { muxer?.release() } catch (e: Throwable) { }
            try { mmr.release() } catch (e: Throwable) { }
        }
    }
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
    val out = java.io.File(Store.mediaDir(ctx, pid), "rev_" + uid() + ".mp4")
    Bg.run {
        val r = Reverser.reverse(path, ts, te, out) { pct -> Bg.ui { dlg.setMessage(L("در حال معکوس‌کردن… $pct٪", "Reversing… $pct%")) } }
        Bg.ui {
            dlg.dismiss()
            if (r == null) {
                out.delete()
                toast(L("معکوس‌کردن روی این ویدیو ممکن نشد.", "Could not reverse this video."))
            } else {
                beginChange()
                c.uri = android.net.Uri.fromFile(out).toString()
                c.trimStart = 0L
                c.trimEnd = r.durMs
                c.srcDurMs = r.durMs
                c.hasAudio = false
                c.w = r.w
                c.h = r.h
                changed()
                refreshToolbar()
                toast(L("ویدیو معکوس شد (بدون صدا).", "Reversed (the copy has no audio)."))
            }
        }
    }
}
