package com.rafiei.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.os.StatFs
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

/** Export screen: choose size/quality, render with the hardware encoder, save to the gallery. */
class ExportActivity : Activity() {
    private lateinit var proj: Project
    private lateinit var content: FrameLayout
    private var resIdx = 1
    private var qualIdx = 2
    private val resList = intArrayOf(480, 720, 1080, 1440, 2160)
    private var transformer: Transformer? = null
    private var running = false
    private var safeMode = false
    private var overlayNote = ""
    private val handler = Handler(Looper.getMainLooper())
    private var outFile: File? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var expectedMs = 0L
    private var cancelled = false
    private var frameIdx = 0
    private var fillFrame = false
    private var fpsIdx = 0
    private var origRatio = ""
    private val origFill = HashMap<String, Boolean>()
    private val frameRatios = listOf("", "9:16", "4:5", "1:1", "16:9")
    private val fpsList = intArrayOf(0, 24, 30)

    private val poll = object : Runnable {
        override fun run() {
            val t = transformer
            if (!running || t == null) return
            val h = ProgressHolder()
            val st = t.getProgress(h)
            if (st == Transformer.PROGRESS_STATE_AVAILABLE) {
                progressBar?.progress = h.progress
                progressText?.text = "${h.progress}%"
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getStringExtra("pid") ?: ""
        val p = Store.load(this, pid)
        if (p == null) {
            finish()
            return
        }
        proj = p
        origRatio = p.ratio
        for (c in p.clips) origFill[c.id] = c.fill
        content = FrameLayout(this)
        content.setBackgroundColor(C.BG)
        setContentView(content)
        showOptions()
    }

    private fun header(title: String, onBack: (() -> Unit)?): LinearLayout {
        val col = lin(true)
        val bar = FrameLayout(this)
        if (onBack != null) {
            val b = IconView(this, "back")
            b.setOnClickListener { onBack() }
            bar.addView(b, fl(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        }
        bar.addView(tv(title, 16f, C.TXT, true), fl(WRAP, WRAP, Gravity.CENTER))
        col.addView(bar, lp(MATCH, dp(52)))
        return col
    }

    private fun chips(labels: List<String>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val row = lin(false)
        val views = ArrayList<TextView>()
        for (i in labels.indices) {
            val t = tv(labels[i], 14f, if (i == sel) Color.BLACK else C.TXT, true)
            t.background = roundedBg(if (i == sel) Color.WHITE else C.CARD, dpf(18f))
            t.setOnClickListener {
                for (j in views.indices) {
                    views[j].setTextColor(if (j == i) Color.BLACK else C.TXT)
                    views[j].background = roundedBg(if (j == i) Color.WHITE else C.CARD, dpf(18f))
                }
                onSel(i)
            }
            views.add(t)
            val l = lp(0, dp(40), 1f)
            l.setMargins(dp(3), 0, dp(3), 0)
            row.addView(t, l)
        }
        return row
    }

    /** Applies the chosen social-media frame to the in-memory copy of the project (never saved). */
    private fun applyFrame() {
        proj.ratio = if (frameRatios[frameIdx].isEmpty()) origRatio else frameRatios[frameIdx]
        for (c in proj.clips) c.fill = if (fillFrame) true else (origFill[c.id] ?: c.fill)
    }

    private fun freeBytes(): Long = try {
        val st = StatFs(cacheDir.absolutePath)
        st.availableBytes
    } catch (e: Throwable) { Long.MAX_VALUE }

    private fun neededBytes(): Long {
        val sz = Geo.outSize(proj, resList[resIdx])
        val br = Exporter.bitrateFor(sz[0], sz[1], qualIdx)
        val sec = proj.totalMs() / 1000.0
        val one = (br / 8.0 + 24000.0) * sec
        return (one * 2.3).toLong() + 40L * 1048576L
    }

    private fun frameBox(): View {
        val v = object : View(this) {
            override fun onDraw(c: android.graphics.Canvas) {
                val a = Geo.aspect(proj)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val maxW = width - dp(8).toFloat()
                val maxH = height - dp(8).toFloat()
                var w = maxH * a
                var h = maxH
                if (w > maxW) { w = maxW; h = maxW / a }
                val l = (width - w) / 2f
                val t = (height - h) / 2f
                p.color = 0xFF111111.toInt()
                c.drawRoundRect(l, t, l + w, t + h, dpf(6f), dpf(6f), p)
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = dpf(2f)
                p.color = C.ACCENT
                c.drawRoundRect(l, t, l + w, t + h, dpf(6f), dpf(6f), p)
            }
        }
        return v
    }

    private fun estimate(): String {
        val sz = Geo.outSize(proj, resList[resIdx])
        val br = Exporter.bitrateFor(sz[0], sz[1], qualIdx)
        val sec = proj.totalMs() / 1000.0
        val mb = (br / 8.0 + 16000.0) * sec / 1048576.0
        return "${sz[0]}×${sz[1]}  •  ${fmtShort(proj.totalMs())}  •  ≈ ${String.format("%.1f", mb)} MB"
    }

    private fun showOptions() {
        content.removeAllViews()
        val root = lin(true)
        root.addView(header(L("خروجی", "Export")) { finish() })
        val sv = ScrollView(this)
        val body = lin(true)
        body.setPadding(dp(16), dp(8), dp(16), dp(16))

        val isPhoto = proj.type == "photo"
        val info = tv("", 13f, C.TXT2)
        info.setPadding(0, dp(6), 0, dp(14))

        val resLabels = ArrayList<String>()
        for (r in resList) resLabels.add(if (r == 2160) "4K" else "${r}p")
        body.addView(tv(L("وضوح تصویر", "Resolution"), 14f, C.TXT, true, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(34)))
        body.addView(chips(resLabels, resIdx) { resIdx = it; info.text = estimate() }, lp(MATCH, dp(44)))

        val frameLabels = listOf(L("اصلی", "Original"), "TikTok / Reels / Shorts 9:16", L("اینستاگرام 4:5", "Instagram 4:5"), L("مربع 1:1", "Square 1:1"), "YouTube / Facebook 16:9")
        body.addView(tv(L("قالب شبکه‌های اجتماعی", "Social format"), 14f, C.TXT, true, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(34)))
        val frameHolder = FrameLayout(this)
        val fv = frameBox()
        frameHolder.addView(fv, fl(dp(64), dp(64), Gravity.CENTER))
        val chipScroll = android.widget.HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chipRow = lin(false)
        val frameChipViews = ArrayList<TextView>()
        for (i in frameLabels.indices) {
            val t = tv(frameLabels[i], 12.5f, if (i == frameIdx) Color.BLACK else C.TXT, true)
            t.setPadding(dp(14), 0, dp(14), 0)
            t.background = roundedBg(if (i == frameIdx) Color.WHITE else C.CARD, dpf(18f))
            t.setOnClickListener {
                frameIdx = i
                applyFrame()
                for (j in frameChipViews.indices) {
                    frameChipViews[j].setTextColor(if (j == i) Color.BLACK else C.TXT)
                    frameChipViews[j].background = roundedBg(if (j == i) Color.WHITE else C.CARD, dpf(18f))
                }
                fv.invalidate()
                info.text = if (isPhoto) { val z = Geo.outSize(proj, resList[resIdx]); "${z[0]}×${z[1]}" } else estimate()
            }
            frameChipViews.add(t)
            val l = lp(WRAP, dp(40))
            l.setMargins(dp(3), 0, dp(3), 0)
            chipRow.addView(t, l)
        }
        chipScroll.addView(chipRow)
        body.addView(chipScroll, lp(MATCH, dp(44)))
        body.addView(frameHolder, lp(MATCH, dp(70)))
        body.addView(chips(listOf(L("جا دادن (با حاشیه)", "Fit (bars)"), L("بازکادربندی خودکار (پر کردن)", "Auto reframe (fill)")), if (fillFrame) 1 else 0) {
            fillFrame = it == 1
            applyFrame()
            fv.invalidate()
            info.text = if (isPhoto) { val z = Geo.outSize(proj, resList[resIdx]); "${z[0]}×${z[1]}" } else estimate()
        }, lp(MATCH, dp(44)))

        if (!isPhoto) {
            body.addView(tv(L("نرخ فریم", "Frame rate"), 14f, C.TXT, true, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(34)))
            body.addView(chips(listOf(L("اصلی", "Source"), "24 fps", "30 fps"), fpsIdx) { fpsIdx = it }, lp(MATCH, dp(44)))
            body.addView(tv(L("کیفیت", "Quality"), 14f, C.TXT, true, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(44)))
            body.addView(
                chips(listOf(L("کم", "Low"), L("متوسط", "Medium"), L("بالا", "High"), L("حداکثر", "Max")), qualIdx) { qualIdx = it; info.text = estimate() },
                lp(MATCH, dp(44))
            )
            info.text = estimate()
            body.addView(info, lp(MATCH, WRAP))
            val note = tv(
                L("مدت ویدیوی خروجی دقیقاً برابر مدت تایم‌لاین است و فایل با رمزگذار سخت‌افزاری ساخته می‌شود تا پخش روان باشد.", "The exported duration equals the timeline duration. The file is made with the hardware encoder for smooth playback."),
                12f, C.TXT2, false, Gravity.START
            )
            body.addView(note, lp(MATCH, WRAP))
        } else {
            body.addView(info, lp(MATCH, WRAP))
            val sz = Geo.outSize(proj, resList[resIdx])
            info.text = "${sz[0]}×${sz[1]}"
        }
        sv.addView(body)
        root.addView(sv, lp(MATCH, 0, 1f))

        val go = tv(L("شروع خروجی", "Export"), 16f, Color.WHITE, true)
        go.background = gradBg(C.ACCENT2, C.ACCENT, dpf(26f), android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
        go.setOnClickListener {
            applyFrame()
            if (isPhoto) exportPhoto()
            else if (freeBytes() < neededBytes()) {
                val need = neededBytes() / 1048576L
                val have = freeBytes() / 1048576L
                Dialogs.confirm(this, L("فضای خالی کم است", "Low storage"),
                    L("برای خروجی حدود $need مگابایت فضا لازم است ولی فقط $have مگابایت آزاد است. ادامه می‌دهید؟", "About $need MB are needed but only $have MB are free. Continue?"),
                    L("ادامه", "Continue")) { startVideo(false) }
            } else startVideo(false)
        }
        val gl = lp(MATCH, dp(52))
        gl.setMargins(dp(16), dp(8), dp(16), dp(20))
        root.addView(go, gl)
        content.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------ photo

    private fun exportPhoto() {
        showProgress(L("در حال ساخت عکس…", "Rendering photo…"), false)
        val sz = Geo.outSize(proj, resList[resIdx])
        Bg.run {
            val bmp = Exporter.renderPhoto(this, proj, sz[0], sz[1])
            if (bmp == null) {
                Bg.ui { showError(L("ساخت عکس ناموفق بود.", "Could not render the photo.")) }
            } else {
                val uri = Exporter.savePhoto(this, bmp, false)
                bmp.recycle()
                Bg.ui {
                    if (uri == null) showError(L("ذخیره در گالری ناموفق بود.", "Could not save to gallery."))
                    else showDone(uri, null, false)
                }
            }
        }
    }

    // ------------------------------------------------------------ video

    private fun startVideo(safe: Boolean) {
        safeMode = safe
        cancelled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showProgress(L("در حال آماده‌سازی…", "Preparing…"), true)
        val sz = Geo.outSize(proj, resList[resIdx])
        val br = Exporter.bitrateFor(sz[0], sz[1], qualIdx)
        Bg.run {
            val res: Exporter.Result
            try {
                res = Exporter.build(this, proj, sz[0], sz[1], safe, fpsList[fpsIdx])
            } catch (e: Throwable) {
                Bg.ui { showError(L("آماده‌سازی ناموفق بود: ", "Preparation failed: ") + (e.message ?: e.javaClass.simpleName)) }
                return@run
            }
            overlayNote = if (safe) L("حالت ایمن: افکت‌ها و لایه‌ها اعمال نشدند", "Safe mode: effects/layers NOT applied")
                else if (res.overlay) L("افکت‌ها و لایه‌ها (اکولایزر، نور، افکت، متن) روی خروجی اعمال شد", "Effects/layers were rendered into the video")
                else L("پروژه افکت یا لایهٔ روی تصویر نداشت", "The project had no effects/layers")
            Bg.ui { runTransformer(res, br) }
        }
    }

    private fun runTransformer(res: Exporter.Result, bitrate: Int) {
        if (cancelled) return
        expectedMs = res.totalMs
        val f = File(cacheDir, "export_" + System.currentTimeMillis() + ".mp4")
        outFile = f
        try {
            val enc = DefaultEncoderFactory.Builder(this)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                .setEnableFallback(true)
                .build()
            val t = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(enc)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        running = false
                        onRendered(f)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        running = false
                        if (cancelled) return
                        f.delete()
                        showError(
                            L("خطا در ساخت ویدیو:\n", "Export failed:\n") + (exportException.message ?: "") + " (" + exportException.errorCodeName + ")",
                            !safeMode
                        )
                    }
                })
                .build()
            transformer = t
            running = true
            progressText?.text = "0%"
            t.start(res.composition, f.absolutePath)
            handler.postDelayed(poll, 300)
        } catch (e: Throwable) {
            running = false
            showError(L("شروع خروجی ناموفق بود: ", "Could not start export: ") + (e.message ?: e.javaClass.simpleName), !safeMode)
        }
    }

    private fun onRendered(f: File) {
        progressText?.text = L("در حال ذخیره…", "Saving…")
        Bg.run {
            val dur = durationOf(f)
            val name = "RafieiEditor_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
            val uri = MediaUtil.saveToGallery(this, f, name, "video/mp4", true)
            Bg.ui {
                if (uri == null) {
                    showError(L("ذخیره در گالری ناموفق بود.", "Could not save to gallery."))
                } else {
                    f.delete()
                    showDone(uri, dur, true)
                }
            }
        }
    }

    private fun durationOf(f: File): Long {
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(f.absolutePath)
            (m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toLong()
        } catch (e: Exception) {
            0L
        } finally {
            try { m.release() } catch (e: Exception) {}
        }
    }

    // ------------------------------------------------------------ screens

    private fun showProgress(msg: String, cancellable: Boolean) {
        content.removeAllViews()
        val col = lin(true)
        col.gravity = Gravity.CENTER
        col.setPadding(dp(32), 0, dp(32), 0)
        col.addView(tv(msg, 16f, C.TXT, true), lp(WRAP, WRAP))
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progress = 0
        pb.progressTintList = android.content.res.ColorStateList.valueOf(C.ACCENT)
        progressBar = pb
        val l = lp(MATCH, dp(10)); l.topMargin = dp(24)
        col.addView(pb, l)
        val pt = tv("", 22f, C.TXT, true)
        progressText = pt
        val l2 = lp(WRAP, WRAP); l2.topMargin = dp(14)
        col.addView(pt, l2)
        val hint = tv(L("برنامه را باز نگه دارید. صفحه خاموش نمی‌شود.", "Keep the app open. The screen stays on."), 12f, C.TXT2)
        val l3 = lp(WRAP, WRAP); l3.topMargin = dp(20)
        col.addView(hint, l3)
        if (cancellable) {
            val c = tv(L("لغو", "Cancel"), 15f, C.TXT, true)
            c.background = roundedBg(C.CARD, dpf(24f))
            c.setOnClickListener {
                cancelled = true
                running = false
                try { transformer?.cancel() } catch (e: Throwable) {}
                outFile?.delete()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                showOptions()
            }
            val l4 = lp(dp(160), dp(48)); l4.topMargin = dp(36)
            col.addView(c, l4)
        }
        content.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun showDone(uri: Uri, durMs: Long?, video: Boolean) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        content.removeAllViews()
        val col = lin(true)
        col.gravity = Gravity.CENTER
        col.setPadding(dp(32), 0, dp(32), 0)
        col.addView(tv("✓", 56f, C.GREEN, true), lp(WRAP, WRAP))
        col.addView(tv(L("با موفقیت ذخیره شد", "Saved successfully"), 18f, C.TXT, true), lp(WRAP, WRAP))
        if (video) {
            val real = if (durMs != null && durMs > 0) durMs else expectedMs
            val t = tv(L("مدت ویدیو: ", "Duration: ") + fmtTime(real), 14f, C.TXT2)
            val l = lp(WRAP, WRAP); l.topMargin = dp(8)
            col.addView(t, l)
        }
        if (video && overlayNote.isNotEmpty()) {
            val nt = tv(overlayNote, 12f, C.TXT2)
            val ln = lp(WRAP, WRAP); ln.topMargin = dp(4)
            col.addView(nt, ln)
        }
        val where = tv(if (video) L("گالری ← Movies/RafieiEditor", "Gallery → Movies/RafieiEditor") else L("گالری ← Pictures/RafieiEditor", "Gallery → Pictures/RafieiEditor"), 12f, C.TXT2)
        val lw = lp(WRAP, WRAP); lw.topMargin = dp(4)
        col.addView(where, lw)

        fun btn(text: String, primary: Boolean, action: () -> Unit) {
            val b = tv(text, 15f, if (primary) Color.WHITE else C.TXT, true)
            b.background = if (primary) gradBg(C.ACCENT2, C.ACCENT, dpf(26f), android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT) else roundedBg(C.CARD, dpf(26f))
            b.setOnClickListener { action() }
            val l = lp(MATCH, dp(52)); l.topMargin = dp(14)
            col.addView(b, l)
        }
        btn(L("پخش", "Play"), true) {
            try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(uri, if (video) "video/mp4" else "image/jpeg")
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(i)
            } catch (e: Exception) {
            }
        }
        btn(L("اشتراک‌گذاری", "Share"), false) {
            try {
                val i = Intent(Intent.ACTION_SEND)
                i.type = if (video) "video/mp4" else "image/jpeg"
                i.putExtra(Intent.EXTRA_STREAM, uri)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, L("اشتراک‌گذاری", "Share")))
            } catch (e: Exception) {
            }
        }
        btn(L("بازگشت به ویرایشگر", "Back to editor"), false) { finish() }
        content.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun showError(msg: String, offerSafe: Boolean = false) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        content.removeAllViews()
        val col = lin(true)
        col.gravity = Gravity.CENTER
        col.setPadding(dp(28), 0, dp(28), 0)
        col.addView(tv("!", 48f, C.ACCENT, true), lp(WRAP, WRAP))
        col.addView(tv(msg, 13f, C.TXT, false), lp(MATCH, WRAP))
        if (offerSafe) {
            val b = tv(L("تلاش دوباره (حالت ایمن، بدون افکت‌های سنگین)", "Retry in safe mode (no heavy effects)"), 14f, Color.WHITE, true)
            b.background = gradBg(C.ACCENT2, C.ACCENT, dpf(26f), android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
            b.setPadding(dp(12), 0, dp(12), 0)
            b.setOnClickListener { startVideo(true) }
            val l = lp(MATCH, dp(52)); l.topMargin = dp(20)
            col.addView(b, l)
        }
        val back = tv(L("بازگشت", "Back"), 15f, C.TXT, true)
        back.background = roundedBg(C.CARD, dpf(26f))
        back.setOnClickListener { showOptions() }
        val l2 = lp(MATCH, dp(52)); l2.topMargin = dp(12)
        col.addView(back, l2)
        content.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override fun onBackPressed() {
        if (running) {
            Dialogs.confirm(this, L("لغو خروجی؟", "Cancel export?"), L("خروجی در حال ساخت است.", "The export is in progress."), L("لغو", "Cancel export")) {
                cancelled = true
                running = false
                try { transformer?.cancel() } catch (e: Throwable) {}
                outFile?.delete()
                finish()
            }
        } else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (running) {
            try { transformer?.cancel() } catch (e: Throwable) {}
            outFile?.delete()
        }
    }
}
