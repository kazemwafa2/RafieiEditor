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
        Item("fa", "فارسی (دری را تا حدی می‌شناسد)", "Persian (partially covers Dari)", "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip", 53),
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

    fun delete(c: Context, id: String) { File(root(c), id).deleteRecursively() }

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

    /** Downloads [it] to the cache and installs it. The .part file is kept when the
     *  download is cancelled or interrupted, so the next attempt resumes with a Range request.
     *  Returns null on success or an error text. */
    fun download(c: Context, it: Item, progress: (Long, Long) -> Unit): String? {
        val part = File(c.cacheDir, "vosk_" + it.id + ".zip.part")
        try {
            AutoSub.cancelFlag = false
            var have = if (part.exists()) part.length() else 0L
            val cn = URL(it.url).openConnection() as HttpURLConnection
            cn.connectTimeout = 15000; cn.readTimeout = 25000
            if (have > 0L) cn.setRequestProperty("Range", "bytes=$have-")
            if (cn.responseCode != 200 && cn.responseCode != 206) { part.delete(); return "HTTP " + cn.responseCode }
            // 206 = server honoured the range -> append. 200 = it ignored the range -> restart.
            val resume = cn.responseCode == 206 && have > 0L
            if (!resume) have = 0L
            var total = cn.contentLengthLong
            if (total > 0L && resume) total += have
            cn.inputStream.use { ins ->
                java.io.FileOutputStream(part, resume).use { o ->
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
            if (total > 0 && part.length() != total) {
                if (part.length() > total) part.delete()
                return "incomplete"
            }
            val ok = part.inputStream().use { s -> unzip(c, s, it.id) }
            if (ok) part.delete()
            return if (ok) null else "bad model file"
        } catch (e: Throwable) {
            return "network"
        }
    }
}

/** Audio clean-up and word-time refinement used by the automatic subtitles. */
object SubDsp {
    /** Removes rumble (high-pass), then lifts quiet recordings to a healthy level (limited gain, so noise is not blown up). */
    fun prepare(pcm: ShortArray): ShortArray {
        val out = ShortArray(pcm.size)
        var px = 0.0
        var py = 0.0
        val a = 0.985
        for (i in pcm.indices) {
            val x = pcm[i].toDouble()
            val y = a * (py + x - px)
            px = x; py = y
            out[i] = Math.max(-32768.0, Math.min(32767.0, y)).toInt().toShort()
        }
        // robust peak: 99.5th percentile of |x| from a sample of the data
        val step = Math.max(1, out.size / 40000)
        val sm = ArrayList<Int>()
        var i = 0
        while (i < out.size) { sm.add(Math.abs(out[i].toInt())); i += step }
        sm.sort()
        val p = Math.max(300, sm[Math.min(sm.size - 1, (sm.size * 0.995).toInt())])
        val g = Math.min(8.0, Math.max(0.5, 0.7 * 32767.0 / p))
        if (Math.abs(g - 1.0) > 0.05) for (k in out.indices) out[k] = Math.max(-32768.0, Math.min(32767.0, out[k] * g)).toInt().toShort()
        return out
    }

    class Word(var w: String, var s: Double, var e: Double)

    /** Moves word starts / ends onto the real sound onset / offset around pauses (the recogniser is often 0.1-0.3 s off). */
    fun refine(words: List<Word>, pcm: ShortArray) {
        if (words.isEmpty()) return
        val fr = 320
        val n = pcm.size / fr
        if (n < 20) return
        val raw = DoubleArray(n)
        for (i in 0 until n) raw[i] = AutoSub.rms(pcm, i * fr, i * fr + fr)
        val e = DoubleArray(n)
        for (i in 0 until n) { var a = 0.0; var k = 0; for (j in Math.max(0, i - 1)..Math.min(n - 1, i + 1)) { a += raw[j]; k++ }; e[i] = a / k }
        val sorted = e.sortedArray()
        val floor = sorted[(n * 0.1).toInt()]
        val peak = Math.max(1e-4, sorted[Math.min(n - 1, (n * 0.95).toInt())])
        val thr = floor + (peak - floor) * 0.18
        for (i in words.indices) {
            val w = words[i]
            val prevEnd = if (i > 0) words[i - 1].e else -10.0
            val nextStart = if (i + 1 < words.size) words[i + 1].s else 1e9
            if (w.s - prevEnd > 0.15) {
                // onset: first frame near the claimed start where the energy has risen above the threshold
                val c = (w.s * 50).toInt()
                val lo = Math.max(0, c - 12)
                val hi = Math.min(n - 1, c + 10)
                var f = -1
                for (k in lo..hi) if (e[k] > thr) { f = k; break }
                if (f >= 0) {
                    while (f > lo && e[f - 1] > thr * 0.7) f--
                    val ns = f * 0.02
                    if (ns >= prevEnd) w.s = ns
                }
            }
            if (nextStart - w.e > 0.15) {
                val c = (w.e * 50).toInt()
                val lo = Math.max(0, c - 10)
                val hi = Math.min(n - 1, c + 12)
                var f = -1
                for (k in hi downTo lo) if (e[k] > thr) { f = k; break }
                if (f >= 0) w.e = Math.min(nextStart, (f + 1) * 0.02)
            }
            if (w.e < w.s + 0.08) w.e = w.s + 0.08
        }
    }
}

/** One audio the subtitles can be made from. */
private class SubSrc(val label: String, val path: String, val from: Long, val to: Long, val base: Long, val speed: Float)

/** Automatic subtitles: fully offline speech recognition (Vosk) with exact word times. */
fun EditorActivity.openAutoSubPanel() {
    val ctx = applicationContext
    val srcs = ArrayList<SubSrc>()
    val sel = selClip()
    for ((i, c) in proj.clips.withIndex()) {
        if (c.isImage || !c.hasAudio) continue
        val sp = if (c.speed > 0.05f) c.speed else 1f
        val s = SubSrc(L("کلیپ ", "Clip ") + (i + 1), MediaUtil.pathOf(c.uri), c.trimStart, if (c.trimEnd > c.trimStart) c.trimEnd else c.srcDurMs, proj.clipStart(i), sp)
        if (sel != null && sel.id == c.id) srcs.add(0, s) else srcs.add(s)
    }
    for (a in proj.audios) {
        val sp = if (a.speed > 0.05f) a.speed else 1f
        srcs.add(SubSrc((if (a.name.isNotEmpty()) a.name else L("صدا", "Audio")).take(24), MediaUtil.pathOf(a.uri), a.trimStart, if (a.trimEnd > a.trimStart) a.trimEnd else a.srcDurMs, a.startMs, sp))
    }
    if (srcs.isEmpty()) { toast(L("کلیپ ویدیویی یا صدای واردشده لازم است.", "A clip with audio or an imported audio is needed.")); return }

    fun waitDialog(msg: String, stoppable: Boolean): AlertDialog {
        val b = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage(msg).setCancelable(false)
        if (stoppable) b.setNegativeButton(L("توقف و نگه‌داشتن نتیجه", "Stop and keep result")) { _, _ -> AutoSub.cancelFlag = true; if (WhisperLib.loaded) try { WhisperLib.abort(true) } catch (e: Throwable) { } }
        val d = b.create()
        d.show()
        return d
    }

    /** Turns timed words into subtitle lines (UI thread). [fa] gates the Persian
     *  character normalisation so Arabic / Pashto / Urdu output is not rewritten. */
    fun makeSubs(src: SubSrc, words: List<SubDsp.Word>, fa: Boolean) {
            // group words into lines using the real word times
            class Line(val ws: List<SubDsp.Word>)
            val lines = ArrayList<Line>()
            var cur = ArrayList<SubDsp.Word>()
            fun flush() { if (cur.isNotEmpty()) { lines.add(Line(cur)); cur = ArrayList() } }
            for (w in words) {
                if (cur.isNotEmpty()) {
                    val gap = w.s - cur[cur.size - 1].e
                    val len = cur.sumOf { it.w.length + 1 }
                    if (gap > 0.6 || Regex("[.؟!?۔…]$").containsMatchIn(cur[cur.size - 1].w) || cur.size >= 7 || len + w.w.length > 36 || w.e - cur[0].s > 5.0) flush()
                }
                cur.add(w)
            }
            flush()
            beginChange()
            // Remove only the subtitles that fall inside THIS source's project-time window,
            // so generating subs for a second clip/track no longer destroys the first one's lines.
            val winEnd = src.base + (Math.max(0L, src.to - src.from).toDouble() / src.speed).toLong() + 250L
            proj.overlays.removeAll { it.kind == "text" && (it.glyph == "sub" || it.glyph == "sub2") && it.startMs < winEnd && it.startMs + it.durMs > src.base }
            for ((li, l) in lines.withIndex()) {
                val a = l.ws[0].s
                val b = l.ws[l.ws.size - 1].e
                val t0l = src.base + ((a * 1000.0) / src.speed).toLong()
                var t1l = src.base + ((b * 1000.0) / src.speed).toLong() + 150L
                if (li + 1 < lines.size) {
                    val nx = src.base + ((lines[li + 1].ws[0].s * 1000.0) / src.speed).toLong()
                    if (t1l > nx - 10L) t1l = Math.max(t0l + 300L, nx - 10L)
                }
                val ws = ArrayList<SubWords.W>()
                for (w in l.ws) ws.add(SubWords.W(if (fa) SubWords.fa(w.w) else w.w, (((w.s - a) * 1000.0) / src.speed).toLong(), (((w.e - a) * 1000.0) / src.speed).toLong()))
                val o = Overlay()
                o.kind = "text"; o.glyph = "sub"; o.text = SubWords.join(ws); o.font = Fonts.defaultFa(ctx)
                o.words = SubWords.toJson(ws); o.smode = "line"
                o.startMs = Math.max(0L, t0l - 30L); o.durMs = Math.max(400L, t1l - o.startMs)
                o.size = 0.05f; o.cx = 0.5f; o.cy = 0.86f; o.color = Color.WHITE
                o.bgColor = Color.BLACK; o.bgAlpha = 0.45f; o.bold = true
                o.outlineColor = Color.BLACK; o.outlineW = 0.3f
                proj.overlays.add(o)
            }
            changed()
            toast(L("${lines.size} زیرنویس ساخته شد؛ در پنل: متن را اصلاح کنید، حالت «کلمه‌به‌کلمه/کاراوکه» و رنگ و فونت را انتخاب کنید.", "${lines.size} subtitles created; in the panel fix text and pick word-by-word / karaoke, colour and font."))
            openSubtitlePanel()
    }

    fun recognise(src: SubSrc, modelDir: File, isFa: Boolean) {
        try { engine.pause() } catch (e: Throwable) { }
        AutoSub.cancelFlag = false
        val dlg = waitDialog(L("در حال خواندن صدا…", "Reading audio…"), true)
        Bg.run {
            val raw = AutoSub.decode16k(src.path, src.from, src.to)
            if (raw == null || raw.size < 8000) {
                Bg.ui { dlg.dismiss(); toast(L("صدا خوانده نشد.", "Could not read the audio.")) }
                return@run
            }
            val pcm = SubDsp.prepare(raw)
            val words = ArrayList<SubDsp.Word>()
            fun collect(js: String?) {
                if (js == null) return
                try {
                    val a = JSONObject(js).optJSONArray("result") ?: return
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        val w = o.optString("word").trim()
                        val s = o.optDouble("start", -1.0)
                        val e2 = o.optDouble("end", -1.0)
                        if (w.isNotEmpty() && o.optDouble("conf", 1.0) > 0.15 && !s.isNaN() && !e2.isNaN() && e2 > s && s >= 0.0) words.add(SubDsp.Word(w, s, e2))
                    }
                } catch (e: Throwable) { }
            }
            var err: String? = null
            var model: Model? = null
            var rec: Recognizer? = null
            val t0 = System.currentTimeMillis()
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Bg.ui { dlg.setMessage(L("در حال بارگذاری مدل گفتار…", "Loading speech model…")) }
                model = Model(modelDir.absolutePath)
                rec = Recognizer(model, 16000.0f)
                rec.setWords(true)
                val step = 4000
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
                    if (++tick % 12 == 0) {
                        val pct = (100L * i / pcm.size).toInt()
                        val el = (System.currentTimeMillis() - t0) / 1000
                        val left = if (pct > 3) el * (100 - pct) / pct else -1L
                        Bg.ui { dlg.setMessage(L("در حال تشخیص گفتار… $pct٪", "Recognising speech… $pct%") + (if (left >= 0) L("\nحدود ${left + 1} ثانیهٔ دیگر (تقریبی)", "\n≈ ${left + 1} s left (estimate)") else "")) }
                    }
                }
                collect(rec.finalResult)
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
            } finally {
                try { rec?.close() } catch (e: Throwable) { }
                try { model?.close() } catch (e: Throwable) { }
            }
            if (words.isNotEmpty()) SubDsp.refine(words, pcm)
            Bg.ui {
                dlg.dismiss()
                if (err != null && words.isEmpty()) {
                    Dialogs.message(this, L("زیرنویس خودکار", "Auto subtitles"), L("اجرای مدل گفتار ناموفق بود: ", "Speech model failed: ") + err + "\n" + L("اگر حافظهٔ گوشی کم است، برنامه‌های دیگر را ببندید.", "If the phone is low on memory, close other apps."))
                } else if (words.isEmpty()) {
                    Dialogs.confirm(this, L("زیرنویس خودکار", "Auto subtitles"),
                        L("گفتاری پیدا نشد. این روش برای صحبت و دکلمهٔ واضح ساخته شده؛ آواز، موسیقی بلند یا همهمه را خوب تشخیص نمی‌دهد.\n\nبرای آهنگ، متن را در پنل زیرنویس بچسبانید و «همگام‌سازی خودکار» را بزنید. پنل باز شود؟", "No speech found. This works for clear speech, not singing/loud music. Open the subtitle tool to paste lyrics and auto-sync?"),
                        L("باز کردن زیرنویس", "Open subtitles")) { openSubtitlePanel() }
                } else {
                    makeSubs(src, words, isFa)
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
        opts.add(L("انتخاب فایل zip مدل از گوشی (مثلاً مدل بزرگ‌تر و دقیق‌تر)", "Pick a model .zip from the phone (e.g. a bigger, more accurate one)"))
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

    fun chooseModel(src: SubSrc) {
        val inst = VoskBank.installed(ctx)
        if (inst.isEmpty()) { install { chooseModel(src) }; return }
        if (inst.size == 1) { recognise(src, inst[0].second, inst[0].first == "fa"); return }
        val names = inst.map { VoskBank.label(it.first) }.toMutableList()
        names.add(L("＋ افزودن مدل زبان دیگر", "+ Add another language"))
        names.add(L("🗑 حذف مدل‌های نصب‌شده", "🗑 Delete installed models"))
        Dialogs.choose(this, L("زبان گفتار", "Speech language"), names) { i ->
            if (i < inst.size) recognise(src, inst[i].second, inst[i].first == "fa")
            else if (i == inst.size) install { chooseModel(src) }
            else Dialogs.choose(this, L("کدام مدل حذف شود؟", "Delete which model?"), inst.map { VoskBank.label(it.first) }) { k -> VoskBank.delete(ctx, inst[k].first); toast(L("حذف شد", "Deleted")) }
        }
    }

    fun runWhisper(src: SubSrc, item: WhisperBank.Item, lang: String) {
        try { engine.pause() } catch (e: Throwable) { }
        AutoSub.cancelFlag = false
        val dlg = waitDialog(L("در حال خواندن صدا…", "Reading audio…"), true)
        Bg.run {
            val raw = AutoSub.decode16k(src.path, src.from, src.to)
            if (raw == null || raw.size < 8000) { Bg.ui { dlg.dismiss(); toast(L("صدا خوانده نشد.", "Could not read the audio.")) }; return@run }
            val pcm = SubDsp.prepare(raw)
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val t0 = System.currentTimeMillis()
            Thread {
                while (running.get()) {
                    try { Thread.sleep(900) } catch (e: Throwable) { }
                    val pct = WhisperEngine.overallPercent()
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val left = if (pct > 4) el * (100 - pct) / pct else -1L
                    Bg.ui { dlg.setMessage(L("Whisper در حال تشخیص گفتار… $pct٪", "Whisper recognising… $pct%") + (if (left >= 0) L("\nحدود ${left + 1} ثانیهٔ دیگر (تقریبی)", "\n≈ ${left + 1} s left (estimate)") else L("\nبارگذاری مدل و شروع…", "\nLoading model…"))) }
                }
            }.start()
            val vad = WhisperBank.vadFile(ctx).let { if (it.exists() && it.length() > 100_000) it.absolutePath else "" }
            val threads = Math.max(2, Math.min(4, WhisperLib.cores() - 1))
            val res = WhisperEngine.run(pcm, WhisperBank.file(ctx, item), item.dtw, lang, vad, threads)
            running.set(false)
            val words = res.first
            if (words.isNotEmpty() && item.dtw <= 0) SubDsp.refine(words, pcm)
            // Persian normalisation only for Persian — it would corrupt Arabic/Pashto/Urdu glyphs.
            if (lang == "fa") for (w in words) w.w = SubWords.fa(w.w)
            Bg.ui {
                dlg.dismiss()
                if (res.second != null && words.isEmpty()) Dialogs.message(this, L("زیرنویس خودکار", "Auto subtitles"), L("Whisper ناموفق بود: ", "Whisper failed: ") + res.second + "\n" + L("مدل کوچک‌تر را امتحان کنید یا برنامه‌های دیگر را ببندید.", "Try a smaller model or close other apps."))
                else if (words.isEmpty()) Dialogs.confirm(this, L("زیرنویس خودکار", "Auto subtitles"),
                    L("گفتاری پیدا نشد (آواز و موسیقی بلند را هم نمی‌شناسد). برای آهنگ، متن را بچسبانید و «همگام‌سازی خودکار» را بزنید. پنل باز شود؟", "No speech found. For songs paste the lyrics and use auto-sync. Open the panel?"),
                    L("باز کردن زیرنویس", "Open subtitles")) { openSubtitlePanel() }
                else makeSubs(src, words, lang == "fa")
            }
        }
    }

    fun whisperDownload(src: SubSrc, lang: String) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramMb = mi.totalMem / 1048576L
        val names = WhisperBank.MODELS.map { L(it.fa, it.en) + " — " + it.mb + " MB" + (if (it.ramMb > ramMb * 0.6) L("  ⚠ رم کم", "  ⚠ low RAM") else "") }
        Dialogs.choose(this, L("مدل Whisper (دانلود یک‌بار)", "Whisper model (one-time download)"), names) { i ->
            val m = WhisperBank.MODELS[i]
            Dialogs.confirm(this, L("دانلود مدل", "Download model"),
                L(m.fa, m.en) + "\n" + L("حجم: حدود ", "Size: about ") + m.mb + " MB\n" + L("منبع: huggingface.co (ggerganov/whisper.cpp)\nیک‌بار دانلود می‌شود، قابل ادامه پس از قطع شدن است، بعد از آن کاملاً آفلاین کار می‌کند و صدایی ارسال نمی‌شود. بهتر است به وای‌فای وصل باشید.", "Source: huggingface.co (ggerganov/whisper.cpp)\nDownloaded once, resumable; afterwards fully offline, no audio is sent. Wi-Fi recommended."),
                L("دانلود", "Download")) {
                val d = waitDialog(L("در حال دانلود…", "Downloading…"), true)
                Bg.run {
                    var e = WhisperBank.download(ctx, WhisperBank.BASE + m.file, WhisperBank.file(ctx, m), m.mb) { a, b -> Bg.ui { d.setMessage(L("در حال دانلود… ", "Downloading… ") + (a / 1048576) + " MB / " + (b / 1048576) + " MB") } }
                    if (e == null && !WhisperBank.vadFile(ctx).exists()) WhisperBank.download(ctx, WhisperBank.VAD_URL, WhisperBank.vadFile(ctx), 1) { _, _ -> }
                    Bg.ui {
                        d.dismiss()
                        if (e == null) runWhisper(src, m, lang)
                        else Dialogs.message(this, L("دانلود مدل", "Model download"), L("کامل نشد: ", "Not finished: ") + e)
                    }
                }
            }
        }
    }

    fun chooseWhisper(src: SubSrc) {
        if (!WhisperLib.loaded) {
            Dialogs.message(this, "Whisper", L("موتور Whisper در این نسخهٔ برنامه ساخته نشده (ساخت بومی انجام نشد). از Vosk استفاده کنید.", "The Whisper engine is not part of this build (native build failed). Use Vosk."))
            return
        }
        val langs = listOf("fa" to L("فارسی (دری را تا حدی می‌شناسد)", "Persian (partially covers Dari)"), "ps" to L("پشتو", "Pashto"), "auto" to L("تشخیص خودکار", "Auto-detect"), "en" to "English", "ar" to "العربية", "tr" to "Türkçe", "ru" to "Русский", "hi" to "हिन्दी", "ur" to "اردو")
        Dialogs.choose(this, L("زبان گفتار", "Speech language"), langs.map { it.second }) { li ->
            val lang = langs[li].first
            val inst = WhisperBank.installed(ctx)
            if (inst.isEmpty()) { whisperDownload(src, lang); return@choose }
            val names = inst.map { L(it.fa, it.en) + " (" + it.mb + " MB)" }.toMutableList()
            names.add(L("＋ دانلود مدل دیگر", "+ Download another model"))
            names.add(L("🗑 حذف مدل‌های Whisper", "🗑 Delete Whisper models"))
            Dialogs.choose(this, L("کدام مدل؟", "Which model?"), names) { k ->
                if (k < inst.size) runWhisper(src, inst[k], lang)
                else if (k == inst.size) whisperDownload(src, lang)
                else Dialogs.choose(this, L("کدام مدل حذف شود؟", "Delete which model?"), inst.map { L(it.fa, it.en) }) { z -> WhisperBank.delete(ctx, inst[z]); toast(L("حذف شد", "Deleted")) }
            }
        }
    }

    fun chooseEngine(src: SubSrc) {
        val opts = listOf(
            L("Whisper — دقیق‌تر، زمان کلمه‌ها بهتر (کندتر، یک مدل جدا دانلود می‌شود)", "Whisper — more accurate words and timing (slower, separate model download)"),
            L("Vosk — سریع و سبک (دقت کمتر)", "Vosk — fast and light (less accurate)")
        )
        Dialogs.choose(this, L("موتور تشخیص گفتار", "Speech engine"), opts) { i -> if (i == 0) chooseWhisper(src) else chooseModel(src) }
    }

    if (srcs.size == 1 || sel != null) chooseEngine(srcs[0])
    else Dialogs.choose(this, L("زیرنویس از کدام صدا؟", "Subtitles from which audio?"), srcs.map { it.label }) { i -> chooseEngine(srcs[i]) }
}
