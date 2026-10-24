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
    private val handler = Handler(Looper.getMainLooper())
    private var outFile: File? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var expectedMs = 0L
    private var cancelled = false

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

        if (!isPhoto) {
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
        go.setOnClickListener { if (isPhoto) exportPhoto() else startVideo(false) }
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
                res = Exporter.build(this, proj, sz[0], sz[1], safe)
            } catch (e: Throwable) {
                Bg.ui { showError(L("آماده‌سازی ناموفق بود: ", "Preparation failed: ") + (e.message ?: e.javaClass.simpleName)) }
                return@run
            }
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
