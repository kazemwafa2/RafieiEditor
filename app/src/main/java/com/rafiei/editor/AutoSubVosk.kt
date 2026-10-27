package com.rafiei.editor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Offline speech models for Vosk (Kaldi): unpacked under filesDir/vosk/<name>/. */
object VoskBank {
    class Item(val id: String, val fa: String, val en: String, val url: String, val mb: Int)

    val DOWNLOADS = listOf(
        Item("fa", "فارسی / دری", "Persian / Dari", "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip", 53),
        Item("en", "English", "English", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40),
        Item("tr", "Türkçe", "Turkish", "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip", 35),
        Item("ru", "Русский", "Russian", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45),
        Item("hi", "हिन्दी", "Hindi", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", 42)
    )

    fun root(c: Context) = File(c.filesDir, "vosk")

    fun findModelDir(d: File): File? {
        if (File(d, "am").isDirectory && File(d, "conf").isDirectory) return d
        val kids = d.listFiles() ?: return null
        for (k in kids) if (k.isDirectory) { val r = findModelDir(k); if (r != null) return r }
        return null
    }

    fun installed(c: Context): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val ds = root(c).listFiles() ?: return out
        for (d in ds.sortedBy { it.name }) { val m = findModelDir(d); if (m != null) out.add(d.name to m) }
        return out
    }

    fun label(id: String): String {
        val it = DOWNLOADS.firstOrNull { x -> x.id == id }
        return if (it != null) L(it.fa, it.en) else id
    }

    fun hasBundled(c: Context): Boolean = try { (c.assets.list("vosk") ?: emptyArray()).contains("fa.zip") } catch (e: Throwable) { false }

    /** Unzips a model archive into filesDir/vosk/<id>; returns true when a usable model folder exists afterwards. */
    fun unzip(c: Context, ins: InputStream, id: String): Boolean {
        val dst = File(root(c), id)
        val tmp = File(root(c), "$id.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            val base = tmp.canonicalPath + File.separator
            ZipInputStream(ins.buffered(256 * 1024)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val f = File(tmp, e.name)
                    if (!f.canonicalPath.startsWith(base)) continue // zip-slip guard
                    if (e.isDirectory) { f.mkdirs(); continue }
                    f.parentFile?.mkdirs()
                    f.outputStream().use { o -> z.copyTo(o, 256 * 1024) }
                }
            }
            if (findModelDir(tmp) == null) { tmp.deleteRecursively(); return false }
            dst.deleteRecursively()
            return tmp.renameTo(dst)
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            return false
        }
    }

    fun unpackBundled(c: Context): Boolean = try { c.assets.open("vosk/fa.zip").use { unzip(c, it, "fa") } } catch (e: Throwable) { false }

    /** Downloads [it] to the cache and installs it. Returns null on success or an error text. */
    fun download(c: Context, it: Item, progress: (Long, Long) -> Unit): String? {
        val tmp = File(c.cacheDir, "vosk_" + it.id + ".zip")
        try {
            AutoSub.cancelFlag = false
            val cn = URL(it.url).openConnection() as HttpURLConnection
            cn.connectTimeout = 15000; cn.readTimeout = 25000
            if (cn.responseCode != 200) return "HTTP " + cn.responseCode
            val total = cn.contentLengthLong
            var have = 0L
            cn.inputStream.use { ins ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (AutoSub.cancelFlag) return "cancelled"
                        val n = ins.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        have += n
                        progress(have, total)
                    }
                }
            }
            if (total > 0 && tmp.length() != total) { tmp.delete(); return "incomplete" }
            val ok = tmp.inputStream().use { s -> unzip(c, s, it.id) }
            tmp.delete()
            return if (ok) null else "bad model file"
        } catch (e: Throwable) {
            tmp.delete()
            return "network"
        }
    }
}

/** Automatic subtitles: fully offline speech recognition (Vosk) with exact word times. */
fun EditorActivity.openAutoSubPanel() {
    val c = selClip() ?: proj.clips.firstOrNull { !it.isImage && it.hasAudio }
    if (c == null || c.isImage || !c.hasAudio) { toast(L("کلیپ ویدیویی دارای صدا لازم است.", "A video clip with audio is needed.")); return }
    val ctx = applicationContext

    fun waitDialog(msg: String, stoppable: Boolean): AlertDialog {
        val b = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage(msg).setCancelable(false)
        if (stoppable) b.setNegativeButton(L("توقف", "Stop")) { _, _ -> AutoSub.cancelFlag = true }
        val d = b.create()
        d.show()
        return d
    }

    fun recognise(modelDir: File) {
        try { engine.pause() } catch (e: Throwable) { }
        AutoSub.cancelFlag = false
        val idx = proj.clips.indexOf(c)
        val clipStart = proj.clipStart(idx)
        val path = MediaUtil.pathOf(c.uri)
        val ts = c.trimStart
        val te = c.trimEnd
        val speed = c.speed
        val dlg = waitDialog(L("در حال خواندن صدا…", "Reading audio…"), true)
        Bg.run {
            val pcm = AutoSub.decode16k(path, ts, te)
            if (pcm == null || pcm.size < 8000) {
                Bg.ui { dlg.dismiss(); toast(L("صدای این کلیپ خوانده نشد.", "Could not read this clip's audio.")) }
                return@run
            }
            class W(val s: Double, val e: Double, val w: String)
            val words = ArrayList<W>()
            fun collect(js: String?) {
                if (js == null) return
                try {
                    val a = JSONObject(js).optJSONArray("result") ?: return
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        val w = o.optString("word")
                        if (w.isNotBlank()) words.add(W(o.optDouble("start"), o.optDouble("end"), w))
                    }
                } catch (e: Throwable) { }
            }
            var err: String? = null
            var model: Model? = null
            var rec: Recognizer? = null
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Bg.ui { dlg.setMessage(L("در حال بارگذاری مدل گفتار…", "Loading speech model…")) }
                model = Model(modelDir.absolutePath)
                rec = Recognizer(model, 16000.0f)
                rec.setWords(true)
                val step = 8000
                val buf = ByteArray(step * 2)
                var i = 0
                var tick = 0
                while (i < pcm.size && !AutoSub.cancelFlag) {
                    val n = Math.min(step, pcm.size - i)
                    for (k in 0 until n) {
                        val v = pcm[i + k].toInt()
                        buf[k * 2] = (v and 0xFF).toByte()
                        buf[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    if (rec.acceptWaveForm(buf, n * 2)) collect(rec.result)
                    i += n
                    if (++tick % 6 == 0) {
                        val pct = (100L * i / pcm.size).toInt()
                        Bg.ui { dlg.setMessage(L("در حال تشخیص گفتار… $pct٪", "Recognising speech… $pct%")) }
                    }
                }
                collect(rec.finalResult)
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
            } finally {
                try { rec?.close() } catch (e: Throwable) { }
                try { model?.close() } catch (e: Throwable) { }
            }
            Bg.ui {
                dlg.dismiss()
                if (err != null && words.isEmpty()) {
                    Dialogs.message(this, L("زیرنویس خودکار", "Auto subtitles"), L("اجرای مدل گفتار ناموفق بود: ", "Speech model failed: ") + err)
                } else if (words.isEmpty()) {
                    Dialogs.confirm(this, L("زیرنویس خودکار", "Auto subtitles"),
                        L("گفتاری پیدا نشد. این روش برای صحبت و دکلمهٔ واضح ساخته شده؛ آواز، موسیقی بلند یا همهمه را خوب تشخیص نمی‌دهد.\n\nبرای آهنگ، متن را در پنل زیرنویس بچسبانید و با «زمان‌بندی با ضربه» زمان‌بندی کنید. پنل باز شود؟", "No speech found. This works for clear speech, not singing/loud music. Open the manual subtitle tool?"),
                        L("باز کردن زیرنویس", "Open subtitles")) { openSubtitlePanel() }
                } else {
                    // group words into lines using the real word times
                    val lines = ArrayList<Triple<Long, Long, String>>()
                    var cur = ArrayList<W>()
                    fun flush() {
                        if (cur.isEmpty()) return
                        val a = cur[0].s
                        val b = cur[cur.size - 1].e
                        val t0 = clipStart + ((a * 1000.0) / speed).toLong()
                        val t1 = clipStart + ((b * 1000.0) / speed).toLong()
                        lines.add(Triple(Math.max(0L, t0 - 40L), t1 + 120L, cur.joinToString(" ") { it.w }))
                        cur = ArrayList()
                    }
                    for ((k, w) in words.withIndex()) {
                        if (cur.isNotEmpty()) {
                            val gap = w.s - cur[cur.size - 1].e
                            val len = cur.sumOf { it.w.length + 1 }
                            if (gap > 0.7 || cur.size >= 7 || len + w.w.length > 38 || w.e - cur[0].s > 5.0) flush()
                        }
                        cur.add(w)
                    }
                    flush()
                    // no overlaps
                    for (k in 0 until lines.size - 1) if (lines[k].second > lines[k + 1].first) lines[k] = Triple(lines[k].first, lines[k + 1].first - 10L, lines[k].third)
                    beginChange()
                    proj.overlays.removeAll { it.kind == "text" && it.glyph == "sub" }
                    for (l in lines) {
                        val o = Overlay()
                        o.kind = "text"; o.glyph = "sub"; o.text = l.third; o.font = Fonts.defaultFa(ctx)
                        o.startMs = l.first; o.durMs = Math.max(400L, l.second - l.first)
                        o.size = 0.05f; o.cx = 0.5f; o.cy = 0.86f; o.color = Color.WHITE
                        o.bgColor = Color.BLACK; o.bgAlpha = 0.45f; o.bold = true
                        o.outlineColor = Color.BLACK; o.outlineW = 0.3f
                        proj.overlays.add(o)
                    }
                    changed()
                    toast(L("${lines.size} زیرنویس ساخته شد؛ متن را اصلاح و رنگ و فونت را انتخاب کنید.", "${lines.size} subtitles created; fix text, pick colour and font."))
                    openSubtitlePanel()
                }
            }
        }
    }

    fun install(then: () -> Unit) {
        if (VoskBank.hasBundled(ctx)) {
            val d = waitDialog(L("در حال نصب مدل فارسی (یک‌بار)…", "Installing the Persian model (once)…"), false)
            Bg.run {
                val ok = VoskBank.unpackBundled(ctx)
                Bg.ui { d.dismiss(); if (ok) then() else Dialogs.message(this, L("مدل گفتار", "Speech model"), L("نصب مدل ناموفق بود (فضای خالی را بررسی کنید).", "Model install failed (check free space).")) }
            }
            return
        }
        val opts = ArrayList<String>()
        for (m in VoskBank.DOWNLOADS) opts.add(L("دانلود مدل ", "Download ") + L(m.fa, m.en) + " (" + m.mb + " MB)")
        opts.add(L("انتخاب فایل zip مدل از گوشی", "Pick a model .zip from the phone"))
        Dialogs.choose(this, L("مدل گفتار آفلاین لازم است", "An offline speech model is needed"), opts) { i ->
            if (i < VoskBank.DOWNLOADS.size) {
                val m = VoskBank.DOWNLOADS[i]
                Dialogs.confirm(this, L("دانلود مدل", "Download model"), L(m.fa, m.en) + "\n" + L("حجم: حدود ", "Size: about ") + m.mb + " MB\n" + L("منبع: ", "Source: ") + m.url + "\n" + L("فقط یک‌بار دانلود می‌شود؛ بعد از آن کاملاً آفلاین کار می‌کند و هیچ صدایی ارسال نمی‌شود.", "Downloaded once; afterwards fully offline, no audio is sent."), L("دانلود", "Download")) {
                    val d = waitDialog(L("در حال دانلود…", "Downloading…"), true)
                    Bg.run {
                        val e = VoskBank.download(ctx, m) { a, b -> Bg.ui { d.setMessage(L("در حال دانلود… ", "Downloading… ") + (a / 1048576) + " MB" + (if (b > 0) " / " + (b / 1048576) + " MB" else "")) } }
                        Bg.ui {
                            d.dismiss()
                            if (e == null) then() else Dialogs.message(this, L("مدل گفتار", "Speech model"), L("دانلود/نصب نشد: ", "Not installed: ") + e + "\n" + L("اگر دانلود از این آدرس ممکن نیست، فایل zip را جای دیگر دانلود کنید و «انتخاب فایل zip» را بزنید.", "If this host is unreachable, download the zip elsewhere and pick it from the phone."))
                        }
                    }
                }
            } else {
                pick(arrayOf("application/zip", "application/octet-stream", "*/*"), false, EditorActivity.REQ_FONT) { uris ->
                    val u = uris.firstOrNull()
                    if (u != null) {
                        val d = waitDialog(L("در حال نصب مدل…", "Installing model…"), false)
                        Bg.run {
                            val name = (u.lastPathSegment ?: "custom").substringAfterLast('/').substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_.-]"), "_").ifEmpty { "custom" }
                            val ok = try { contentResolver.openInputStream(u)?.use { VoskBank.unzip(ctx, it, name) } ?: false } catch (e: Throwable) { false }
                            Bg.ui { d.dismiss(); if (ok) then() else Dialogs.message(this, L("مدل گفتار", "Speech model"), L("این فایل مدل Vosk معتبر نبود (باید پوشه‌های am و conf داشته باشد).", "Not a valid Vosk model (needs am and conf folders).")) }
                        }
                    }
                }
            }
        }
    }

    fun choose() {
        val inst = VoskBank.installed(ctx)
        if (inst.isEmpty()) { install { choose() }; return }
        if (inst.size == 1) { recognise(inst[0].second); return }
        val names = inst.map { VoskBank.label(it.first) }.toMutableList()
        names.add(L("＋ افزودن مدل زبان دیگر", "+ Add another language"))
        Dialogs.choose(this, L("زبان گفتار", "Speech language"), names) { i ->
            if (i < inst.size) recognise(inst[i].second) else install { choose() }
        }
    }
    choose()
}
