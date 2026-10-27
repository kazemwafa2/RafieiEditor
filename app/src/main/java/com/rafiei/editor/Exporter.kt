package com.rafiei.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/** Applies volume and fades to 16-bit PCM audio. */
class GainAudioProcessor(private val gain: Float, private val fadeInMs: Long, private val fadeOutMs: Long, private val totalMs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate
        while (inputBuffer.remaining() >= 2 * ch) {
            val tMs = frames * 1000.0 / sr
            var g = gain
            if (fadeInMs > 0 && tMs < fadeInMs) g *= (tMs / fadeInMs).toFloat()
            if (fadeOutMs > 0 && totalMs > 0 && tMs > totalMs - fadeOutMs) g *= Math.max(0.0, (totalMs - tMs) / fadeOutMs).toFloat()
            for (c in 0 until ch) {
                val s = inputBuffer.short.toInt()
                var v = (s * g).toInt()
                if (v > 32767) v = 32767
                if (v < -32768) v = -32768
                out.putShort(v.toShort())
            }
            frames++
        }
        out.flip()
    }

    override fun onFlush() {
        frames = 0
    }
}

/** Overlay with background bars, effects, text and stickers, redrawn only when something changes. */
class TimelineOverlay(private val p: Project, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val bmpA = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val bmpB = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val canA = Canvas(bmpA)
    private val canB = Canvas(bmpB)
    private var useA = true
    private var lastKey: String? = null
    private var last: Bitmap = bmpA

    private fun key(t: Long): String {
        val i = p.clipIndexAt(t)
        val sb = StringBuilder()
        sb.append(i).append('|')
        if (i >= 0) {
            val c = p.clips[i]
            if (!c.fill && p.bgMode == "blur") sb.append(t / 1500L).append('|')
        }
        if (Render.hasDynamicTop(p, t)) sb.append("d").append(t / 33L) else sb.append(Render.staticKey(p, t))
        return sb.toString()
    }

    override fun getOverlaySettings(presentationTimeUs: Long): androidx.media3.effect.OverlaySettings =
        androidx.media3.effect.OverlaySettings.Builder().build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val t = presentationTimeUs / 1000L
        val k = key(t)
        if (k == lastKey) return last
        val target = if (useA) bmpA else bmpB
        val can = if (useA) canA else canB
        useA = !useA
        target.eraseColor(0)
        Render.drawBackground(can, W, H, p, t, true, true, null)
        Render.drawTop(can, W, H, p, t)
        last = target
        lastKey = k
        return target
    }
}

object Exporter {

    class Result(val composition: Composition, val totalMs: Long, val overlay: Boolean = false)

    private fun speedEffect(speed: Float): Effect? {
        return try {
            val k = Class.forName("androidx.media3.effect.SpeedChangeEffect")
            k.getConstructor(java.lang.Float.TYPE).newInstance(speed) as Effect
        } catch (e: Throwable) {
            null
        }
    }

    private fun silenceItems(ctx: Context, durMs: Long): List<EditedMediaItem> {
        val out = ArrayList<EditedMediaItem>()
        val f = SoundFx.silence(ctx, 30)
        var left = durMs
        while (left > 0) {
            val piece = Math.min(left, 30000L)
            val mi = MediaItem.Builder().setUri(Uri.fromFile(f))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(piece).build())
                .build()
            out.add(EditedMediaItem.Builder(mi).build())
            left -= piece
        }
        return out
    }

    private fun needsMatrix(p: Project, i: Int): Boolean {
        val c = p.clips[i]
        if (Math.abs(c.zoom - 1f) > 0.001f || Math.abs(c.panX) > 0.001f || Math.abs(c.panY) > 0.001f) return true
        return Fx.isDynamic(p, i)
    }

    private fun relMs(tUs: Long, startMs: Long): Long {
        val t = tUs / 1000L
        return if (startMs > 0 && t >= startMs - 150) t - startMs else t
    }

    /** Copy of the project that can be used from other threads. */
    fun copyProject(p: Project): Project = Project.fromJson(JSONObject(p.snapshot()))

    fun build(ctx: Context, src: Project, outW: Int, outH: Int, safe: Boolean, fps: Int = 0): Result {
        val p = copyProject(src)
        if (p.fxs.any { Seg.isPerson(it.fx) }) Seg.prepareBlocking(p)
        if (p.overlays.any { it.kind == "eq" } || p.audios.any { it.duck > 0.01f }) EqAudio.prepareBlocking(p)
        val total = p.totalMs()
        val videoItems = ArrayList<EditedMediaItem>()
        var start = 0L
        for (i in p.clips.indices) {
            val c = p.clips[i]
            val clipStart = start
            start += c.durMs()
            val mib = MediaItem.Builder().setUri(Uri.parse(c.uri))
            if (!c.isImage) {
                mib.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.trimStart).setEndPositionMs(c.trimEnd).build()
                )
            }
            val fx = ArrayList<Effect>()
            if (!safe && !c.isImage && Math.abs(c.speed - 1f) > 0.001f) {
                val se = speedEffect(c.speed)
                if (se != null) fx.add(se)
            }
            if (c.flipH || c.flipV) {
                fx.add(ScaleAndRotateTransformation.Builder().setScale(if (c.flipH) -1f else 1f, if (c.flipV) -1f else 1f).build())
            }
            if (c.rot != 0) {
                fx.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-c.rot.toFloat()).build())
            }
            if (!safe && needsMatrix(p, i)) {
                val aspect = c.dispW().toFloat() / c.dispH().toFloat()
                val idx = i
                fx.add(MatrixTransformation { tUs ->
                    val rel = relMs(tUs, clipStart)
                    val d = Fx.dyn(p, idx, rel, clipStart + rel)
                    val xf = Fx.xform(c, d)
                    Fx.ndcMatrix(xf[0], xf[1], xf[2], xf[3], aspect, xf[4], xf[5])
                })
            }
            if (!safe) {
                val base = Fx.baseColor(c)
                if (Fx.isDynamic(p, i)) {
                    val idx = i
                    fx.add(RgbMatrix { tUs, _ ->
                        val rel = relMs(tUs, clipStart)
                        Fx.colorAt(p, idx, rel, clipStart + rel).toGl()
                    })
                } else if (!base.isIdentity()) {
                    val arr = base.toGl()
                    fx.add(RgbMatrix { _, _ -> arr })
                }
            }
            fx.add(
                Presentation.createForWidthAndHeight(
                    outW, outH,
                    if (c.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
            val b = EditedMediaItem.Builder(mib.build()).setRemoveAudio(true).setEffects(Effects(ArrayList<AudioProcessor>(), fx))
            if (c.isImage) {
                b.setDurationUs(c.imgDur * 1000L)
                b.setFrameRate(30)
            }
            videoItems.add(b.build())
        }

        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence(videoItems))

        // audio that belongs to the clips
        var anyClipAudio = false
        val clipAudio = ArrayList<EditedMediaItem>()
        for (c in p.clips) {
            val useAudio = !p.muteClips && !c.isImage && c.hasAudio && c.volume > 0.001f
            if (useAudio) {
                anyClipAudio = true
                val mi = MediaItem.Builder().setUri(Uri.parse(c.uri))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.trimStart).setEndPositionMs(c.trimEnd).build())
                    .build()
                val procs = ArrayList<AudioProcessor>()
                if (Math.abs(c.speed - 1f) > 0.001f && !safe && speedEffect(c.speed) != null) {
                    val s = SonicAudioProcessor()
                    s.setSpeed(c.speed)
                    s.setPitch(1f)
                    procs.add(s)
                }
                if (Math.abs(c.volume - 1f) > 0.001f) procs.add(GainAudioProcessor(c.volume, 0, 0, 0))
                clipAudio.add(EditedMediaItem.Builder(mi).setRemoveVideo(true).setEffects(Effects(procs, ArrayList<Effect>())).build())
            } else {
                clipAudio.addAll(silenceItems(ctx, c.durMs()))
            }
        }
        var anyMusic = false
        val lanes = ArrayList<ArrayList<AudioItem>>()
        val sorted = p.audios.filter { it.startMs < total && it.volume > 0.001f }.sortedBy { it.startMs }
        for (a in sorted) {
            var placed = false
            for (lane in lanes) {
                if (lane[lane.size - 1].endMs() <= a.startMs) {
                    lane.add(a)
                    placed = true
                    break
                }
            }
            if (!placed) {
                val l = ArrayList<AudioItem>()
                l.add(a)
                lanes.add(l)
            }
        }
        val laneSeqs = ArrayList<EditedMediaItemSequence>()
        for (lane in lanes) {
            val items = ArrayList<EditedMediaItem>()
            var cursor = 0L
            for (a in lane) {
                val gap = a.startMs - cursor
                if (gap > 20) items.addAll(silenceItems(ctx, gap))
                val maxDur = Math.min(a.durMs(), total - a.startMs)
                if (maxDur < 100) continue
                val srcEnd = Math.min(a.trimEnd, a.trimStart + (maxDur * a.speed).toLong())
                val mi = MediaItem.Builder().setUri(Uri.parse(a.uri))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(a.trimStart).setEndPositionMs(srcEnd).build())
                    .build()
                val procs = ArrayList<AudioProcessor>()
                if (!safe && a.hasFx()) procs.add(FxAudioProcessor(a.eqL, a.eqM, a.eqH, a.denoise, a.voice))
                if (Math.abs(a.speed - 1f) > 0.001f || Math.abs(a.pitch - 1f) > 0.001f) {
                    val s = SonicAudioProcessor()
                    s.setSpeed(a.speed)
                    s.setPitch(a.pitch)
                    procs.add(s)
                }
                if (Math.abs(a.volume - 1f) > 0.001f || a.fadeIn > 0 || a.fadeOut > 0) {
                    procs.add(GainAudioProcessor(a.volume, a.fadeIn, a.fadeOut, maxDur))
                }
                if (!safe && a.duck > 0.01f) {
                    val pp = p
                    val aa = a
                    procs.add(DuckProcessor(a.startMs) { t -> Duck.gain(pp, aa, t) })
                }
                items.add(EditedMediaItem.Builder(mi).setRemoveVideo(true).setEffects(Effects(procs, ArrayList<Effect>())).build())
                cursor = a.startMs + maxDur
                anyMusic = true
            }
            if (items.isNotEmpty()) laneSeqs.add(EditedMediaItemSequence(items))
        }
        if (anyClipAudio) sequences.add(EditedMediaItemSequence(clipAudio))
        if (anyMusic) sequences.addAll(laneSeqs)

        val builder = Composition.Builder(sequences)
        val compEffects = ArrayList<Effect>()
        var overlayUsed = false
        if (!safe && needsOverlay(p, outW, outH)) {
            overlayUsed = true
            val overlay = TimelineOverlay(p, outW, outH)
            compEffects.add(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))
        }
        if (fps > 0) {
            try {
                val m = Class.forName("androidx.media3.effect.FrameDropEffect").getMethod("createDefaultFrameDropEffect", java.lang.Float.TYPE)
                compEffects.add(m.invoke(null, fps.toFloat()) as Effect)
            } catch (e: Throwable) {
            }
        }
        if (compEffects.isNotEmpty()) builder.setEffects(Effects(ArrayList<AudioProcessor>(), compEffects))
        return Result(builder.build(), total, overlayUsed)
    }

    private fun needsOverlay(p: Project, W: Int, H: Int): Boolean {
        if (p.overlays.isNotEmpty()) return true
        for (f in p.fxs) if (Fx.fxDef(f.fx).overlay) return true
        for (c in p.clips) {
            if (Fx.vignetteOf(c) > 0.01f || Fx.grainOf(c) > 0.01f || ClipLight.of(c) != null || ClipMask.of(c) != null) return true
            if (c.motion.isNotEmpty() && Motion.hasOverlayPost(c.motion)) return true
            if (!c.fill) {
                val r = Geo.clipRect(c, W.toFloat(), H.toFloat())
                val bars = Math.abs(r[2] - W) > 1.5f || Math.abs(r[3] - H) > 1.5f
                if (bars && !(p.bgMode == "color" && p.bgColor == android.graphics.Color.BLACK)) return true
            }
        }
        return false
    }

    fun bitrateFor(w: Int, h: Int, quality: Int): Int {
        val bpp = when (quality) {
            0 -> 0.06f
            1 -> 0.09f
            2 -> 0.13f
            else -> 0.20f
        }
        val v = (w.toLong() * h * 30L * bpp).toLong()
        return clampL(v, 800_000L, 80_000_000L).toInt()
    }

    /** Renders a single image project (photo mode) into a bitmap. */
    fun renderPhoto(ctx: Context, src: Project, W: Int, H: Int): Bitmap? {
        val p = copyProject(src)
        if (p.clips.isEmpty()) return null
        val clip = p.clips[0]
        val bmp = MediaUtil.loadBitmapFile(MediaUtil.pathOf(clip.uri), 4096) ?: return null
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        Render.drawBackground(c, W, H, p, 0, false, true, null)
        val r = Geo.clipRect(clip, W.toFloat(), H.toFloat())
        val s = r[2] / clip.dispW().toFloat()
        c.save()
        c.clipRect(r[0], r[1], r[0] + r[2], r[1] + r[3])
        c.translate(r[0] + r[2] / 2f, r[1] + r[3] / 2f)
        c.translate(clip.panX * r[2], clip.panY * r[3])
        c.scale(clip.zoom, clip.zoom)
        c.rotate(clip.rot.toFloat())
        c.scale(if (clip.flipH) -1f else 1f, if (clip.flipV) -1f else 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cx = Fx.baseColor(clip)
        if (!cx.isIdentity()) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(cx.toAndroid()))
        val dw = clip.w * s
        val dh = clip.h * s
        c.drawBitmap(bmp, null, android.graphics.RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f), paint)
        c.restore()
        Render.drawTop(c, W, H, p, 0)
        bmp.recycle()
        return out
    }

    fun savePhoto(ctx: Context, bmp: Bitmap, png: Boolean): android.net.Uri? {
        val f = File(ctx.cacheDir, "photo_" + System.currentTimeMillis() + (if (png) ".png" else ".jpg"))
        FileOutputStream(f).use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        val name = "RafieiEditor_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + (if (png) ".png" else ".jpg")
        val uri = MediaUtil.saveToGallery(ctx, f, name, if (png) "image/png" else "image/jpeg", false)
        f.delete()
        return uri
    }

    @Suppress("unused")
    private fun unusedMatrix(): Matrix = Matrix()
}
