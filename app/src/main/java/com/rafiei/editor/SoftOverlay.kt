package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Last-resort exporter that does not depend on the GPU overlay of Media3: every frame of an already rendered video is
 * read, the whole overlay layer (background bars, effects, designs, text, stickers) is drawn on it with the same
 * Render code as the preview and the photo export, and the result is encoded again. The audio is copied untouched.
 * Slower than the normal export, but the effects are always the ones drawn by Render.
 */
object SoftOverlay {
    @Volatile var cancel = false

    fun run(src: Project, input: File, output: File, W0: Int, H0: Int, bitrate: Int, onProgress: (Int) -> Unit): Boolean {
        cancel = false
        val W = W0 and 1.inv()
        val H = H0 and 1.inv()
        val p = Exporter.copyProject(src)
        if (Seg.needed(p)) Seg.prepareBlocking(p)
        if (p.overlays.any { it.kind == "eq" } || p.audios.any { it.duck > 0.01f }) EqAudio.prepareBlocking(p)
        Render.exportRender.set(true)
        val mr = MediaMetadataRetriever()
        var enc: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var ext: MediaExtractor? = null
        try {
            mr.setDataSource(input.absolutePath)
            val durMs = (mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toLong()
            if (durMs <= 0) return false
            val fps = 30
            val frames = Math.max(1, (durMs * fps / 1000L).toInt())
            // audio track of the input
            ext = MediaExtractor()
            ext.setDataSource(input.absolutePath)
            var aTrack = -1
            for (i in 0 until ext.trackCount) {
                val f = ext.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { aTrack = i; break }
            }
            if (aTrack >= 0) ext.selectTrack(aTrack)
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H)
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var vTrack = -1
            var mTrack = -1
            var started = false
            val info = MediaCodec.BufferInfo()
            val frameBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val work = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val ov = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val cw = Canvas(work)
            val co = Canvas(ov)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val argb = IntArray(W * H)
            val aBuf = ByteBuffer.allocate(512 * 1024)
            val aInfo = MediaCodec.BufferInfo()
            var aDone = aTrack < 0

            fun pumpAudio(untilUs: Long) {
                if (aDone || !started || mTrack < 0) return
                while (true) {
                    val t = ext.sampleTime
                    if (t < 0) { aDone = true; return }
                    if (t > untilUs) return
                    aBuf.clear()
                    val n = ext.readSampleData(aBuf, 0)
                    if (n < 0) { aDone = true; return }
                    aInfo.set(0, n, t, if ((ext.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(mTrack, aBuf, aInfo)
                    ext.advance()
                }
            }

            fun drain(end: Boolean, lastPts: Long) {
                while (true) {
                    val idx = enc.dequeueOutputBuffer(info, if (end) 10000L else 0L)
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        vTrack = muxer.addTrack(enc.outputFormat)
                        if (aTrack >= 0) mTrack = muxer.addTrack(ext.getTrackFormat(aTrack))
                        muxer.start(); started = true
                        continue
                    }
                    if (idx < 0) continue
                    val out = enc.getOutputBuffer(idx)
                    if (out != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && started) {
                        out.position(info.offset); out.limit(info.offset + info.size)
                        muxer.writeSampleData(vTrack, out, info)
                        pumpAudio(info.presentationTimeUs)
                    }
                    enc.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }

            for (n in 0 until frames) {
                if (cancel) return false
                val tMs = (n * 1000L) / fps
                val fr = mr.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: mr.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                cw.drawColor(0xFF000000.toInt())
                if (fr != null) {
                    cw.drawBitmap(fr, null, android.graphics.Rect(0, 0, W, H), paint)
                    fr.recycle()
                }
                ov.eraseColor(0)
                Render.drawBackground(co, W, H, p, tMs, true, true, null)
                Render.drawTop(co, W, H, p, tMs)
                cw.drawBitmap(ov, 0f, 0f, null)
                work.getPixels(argb, 0, W, 0, 0, W, H)
                // feed the encoder
                var inIdx = -1
                while (inIdx < 0) {
                    if (cancel) return false
                    inIdx = enc.dequeueInputBuffer(10000L)
                    if (inIdx < 0) drain(false, 0)
                }
                val img = enc.getInputImage(inIdx) ?: return false
                argbToImage(argb, W, H, img)
                enc.queueInputBuffer(inIdx, 0, 0, n * 1000000L / fps, 0)
                drain(false, 0)
                onProgress(Math.min(99, n * 100 / frames))
            }
            var e = -1
            while (e < 0) { e = enc.dequeueInputBuffer(10000L); if (e < 0) drain(false, 0) }
            enc.queueInputBuffer(e, 0, 0, frames * 1000000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true, 0)
            pumpAudio(Long.MAX_VALUE - 1)
            frameBmp.recycle(); work.recycle(); ov.recycle()
            return started
        } catch (e: Throwable) {
            println("RafieiEditor soft overlay failed: " + e.javaClass.simpleName + ": " + e.message)
            return false
        } finally {
            try { enc?.stop() } catch (e: Throwable) { }
            try { enc?.release() } catch (e: Throwable) { }
            try { muxer?.stop() } catch (e: Throwable) { }
            try { muxer?.release() } catch (e: Throwable) { }
            try { ext?.release() } catch (e: Throwable) { }
            try { mr.release() } catch (e: Throwable) { }
        }
    }

    private fun argbToImage(px: IntArray, W: Int, H: Int, img: android.media.Image) {
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yb = yP.buffer; val ub = uP.buffer; val vb = vP.buffer
        for (y in 0 until H) {
            val row = y * W
            val yo = y * yP.rowStride
            for (x in 0 until W) {
                val c = px[row + x]
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                yb.put(yo + x * yP.pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
            }
        }
        for (y in 0 until H / 2) {
            for (x in 0 until W / 2) {
                val i = (y * 2) * W + x * 2
                var r = 0; var g = 0; var b = 0
                for (d in intArrayOf(0, 1, W, W + 1)) { val c = px[i + d]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255 }
                r = r shr 2; g = g shr 2; b = b shr 2
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                ub.put(y * uP.rowStride + x * uP.pixelStride, u.coerceIn(0, 255).toByte())
                vb.put(y * vP.rowStride + x * vP.pixelStride, v.coerceIn(0, 255).toByte())
            }
        }
    }
}
