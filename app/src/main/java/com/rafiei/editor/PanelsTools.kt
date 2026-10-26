package com.rafiei.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

private fun EditorActivity.tlPill(text: String, active: Boolean = false, onClick: () -> Unit): TextView {
    val t = tv(text, 12.5f, Color.WHITE)
    t.setPadding(dp(14), dp(8), dp(14), dp(8))
    t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
    t.setOnClickListener { onClick() }
    return t
}

private fun EditorActivity.tlCaption(text: String): TextView {
    val t = tv(text, 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
    t.setPadding(dp(14), dp(6), dp(14), dp(2))
    return t
}

private fun EditorActivity.tlRow(vararg views: View): LinearLayout {
    val r = lin(false)
    r.setPadding(dp(8), dp(4), dp(8), dp(4))
    for (v in views) {
        val l = lp(0, WRAP, 1f)
        l.setMargins(dp(3), 0, dp(3), 0)
        r.addView(v, l)
    }
    return r
}

// ====================================================================== beats

/** Beat detection on top of the offline spectrum analysis (same data the equalizer uses). */
object Beats {
    /** Beat times in milliseconds of the source file. */
    fun detect(t: EqAudio.Track): List<Long> {
        val n = t.n
        val e = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (j in 0 until 14) s += t.lv[i * NBAND + j]
            e[i] = s / 14f
        }
        val flux = FloatArray(n)
        for (i in 1 until n) flux[i] = Math.max(0f, e[i] - e[i - 1])
        val out = ArrayList<Long>()
        var last = -100
        for (i in 2 until n - 2) {
            var m = 0f
            var c = 0
            var k = Math.max(0, i - 20)
            while (k < Math.min(n, i + 20)) { m += flux[k]; c++; k++ }
            m /= Math.max(1, c)
            if (flux[i] > m * 1.6f + 0.02f && flux[i] >= flux[i - 1] && flux[i] >= flux[i + 1] && i - last >= 9) {
                out.add((i * 1000L) / EqAudio.FPS)
                last = i
            }
        }
        return out
    }

    fun bpm(beats: List<Long>): Int {
        if (beats.size < 4) return 0
        val d = ArrayList<Long>()
        for (i in 1 until beats.size) d.add(beats[i] - beats[i - 1])
        d.sort()
        val med = d[d.size / 2]
        if (med <= 0) return 0
        var b = 60000.0 / med
        while (b < 70) b *= 2
        while (b > 180) b /= 2
        return Math.round(b).toInt()
    }

    /** Beats mapped to timeline time (first audible music track, otherwise the video clips' own audio). */
    fun timeline(p: Project): List<Long> {
        val res = ArrayList<Long>()
        val total = p.totalMs()
        for (a in p.audios) {
            if (a.volume <= 0.05f || a.uri.isEmpty()) continue
            val tr = EqAudio.get(MediaUtil.pathOf(a.uri)) ?: continue
            for (s in detect(tr)) {
                if (s < a.trimStart || s > a.trimEnd) continue
                val tl = a.startMs + ((s - a.trimStart) / a.speed).toLong()
                if (tl in 0..total) res.add(tl)
            }
            if (res.isNotEmpty()) return res.sorted()
        }
        if (!p.muteClips) {
            for (i in p.clips.indices) {
                val c = p.clips[i]
                if (c.isImage || !c.hasAudio) continue
                val tr = EqAudio.get(MediaUtil.pathOf(c.uri)) ?: continue
                val st = p.clipStart(i)
                for (s in detect(tr)) {
                    if (s < c.trimStart || s > c.trimEnd) continue
                    res.add(st + ((s - c.trimStart) / c.speed).toLong())
                }
            }
        }
        return res.sorted()
    }
}

fun EditorActivity.openBeatPanel() {
    val status = tv(L("در حال تحلیل صدا…", "Analysing audio…"), 13f, C.TXT, false, Gravity.START or Gravity.CENTER_VERTICAL)
    status.setPadding(dp(14), dp(8), dp(14), dp(4))
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    box.addView(status, lp(MATCH, WRAP))
    val actions = lin(true)
    box.addView(actions, lp(MATCH, WRAP))
    var beats: List<Long> = emptyList()
    var perClip = 2
    var fxId = "pulse"

    fun cutPhotos() {
        val imgs = proj.clips.count { it.isImage }
        if (imgs == 0) { toast(L("عکسی در پروژه نیست.", "There are no photos.")); return }
        beginChange()
        var cursor = 0L
        var j = 0
        val avg = if (beats.size > 1) (beats.last() - beats.first()) / (beats.size - 1) else 500L
        for (c in proj.clips) {
            if (c.isImage) {
                val target = j + perClip
                val end = if (target < beats.size) beats[target] else cursor + avg * perClip
                c.imgDur = Math.max(300L, end - cursor)
                cursor += c.imgDur
                while (j < beats.size && beats[j] <= cursor) j++
                j = Math.max(0, j - 1)
            } else {
                cursor += c.durMs()
                while (j < beats.size && beats[j] < cursor) j++
            }
        }
        changed()
        refreshToolbar()
        toast(L("طول عکس‌ها روی ضرب تنظیم شد.", "Photo lengths snapped to the beat."))
    }

    fun addFxOnBeats() {
        beginChange()
        proj.fxs.removeAll { it.id.startsWith("bt") }
        val total = proj.totalMs()
        var n = 0
        for (b in beats) {
            if (n >= 80) break
            val f = FxItem()
            f.id = "bt" + uid()
            f.fx = fxId
            f.startMs = b
            f.durMs = Math.min(220L, Math.max(100L, total - b))
            f.amt = 0.6f
            proj.fxs.add(f)
            n++
        }
        changed()
        toast(L("$n افکت روی ضرب‌ها گذاشته شد.", "$n effects placed on the beats."))
    }

    fun build() {
        actions.removeAllViews()
        if (beats.size < 3) {
            actions.addView(tlCaption(L("ضرب مشخصی پیدا نشد. ابتدا موسیقی یا ویدیوی دارای صدا اضافه کنید.", "No clear beat found. Add music or a clip with audio first.")), lp(MATCH, WRAP))
            return
        }
        actions.addView(tlCaption(L("هر عکس چند ضرب نمایش داده شود؟", "Beats per photo")), lp(MATCH, WRAP))
        actions.addView(ChipRow(this, listOf("1", "2", "4", "8"), listOf(1, 2, 4, 8).indexOf(perClip)) { perClip = listOf(1, 2, 4, 8)[it] }, lp(MATCH, dp(44)))
        actions.addView(tlRow(
            tlPill(L("برش عکس‌ها روی ضرب", "Cut photos on beat"), true) { cutPhotos() },
            tlPill(L("حذف افکت‌های ضرب", "Clear beat FX")) {
                beginChange(); proj.fxs.removeAll { it.id.startsWith("bt") }; changed()
            }
        ), lp(MATCH, WRAP))
        val ids = listOf("pulse", "flash", "shake", "glitch", "strobe", "heartbeat")
        val names = ids.map { Fx.fxDef(it).name() }
        actions.addView(tlCaption(L("افکت هماهنگ با ضرب", "Effect on every beat")), lp(MATCH, WRAP))
        actions.addView(ChipRow(this, names, ids.indexOf(fxId)) { fxId = ids[it] }, lp(MATCH, dp(44)))
        actions.addView(tlRow(tlPill(L("گذاشتن افکت روی همه ضرب‌ها", "Place effect on beats"), true) { addFxOnBeats() }), lp(MATCH, WRAP))
    }

    val busy = EqAudio.sourcesOf(proj).isEmpty()
    if (busy) {
        status.text = L("صدایی در پروژه نیست.", "There is no audio in the project.")
    } else {
        val snapProj = proj
        Bg.run {
            EqAudio.prepareBlocking(snapProj)
            Bg.ui {
                beats = Beats.timeline(proj)
                val bpm = Beats.bpm(beats)
                status.text = if (beats.size < 3) L("ضرب مشخصی پیدا نشد.", "No clear beat found.")
                else L("${beats.size} ضرب پیدا شد  •  حدود $bpm ضرب در دقیقه", "${beats.size} beats found  •  about $bpm BPM")
                build()
            }
        }
    }
    openPanel(box, true)
}

// ====================================================================== SRT

private fun parseSrtTime(s: String): Long {
    val m = Regex("(\\d+):(\\d+):(\\d+)[,.](\\d+)").find(s.trim()) ?: return -1L
    val ms = m.groupValues[4].padEnd(3, '0').take(3).toLong()
    return m.groupValues[1].toLong() * 3600000L + m.groupValues[2].toLong() * 60000L + m.groupValues[3].toLong() * 1000L + ms
}

private fun srtStamp(ms: Long): String {
    val h = ms / 3600000L
    val m = (ms / 60000L) % 60
    val s = (ms / 1000L) % 60
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms % 1000L)
}

fun EditorActivity.importSrtText(text: String): Int {
    val clean = text.replace("﻿", "").replace("\r\n", "\n").replace("\r", "\n")
    var count = 0
    beginChange()
    proj.overlays.removeAll { it.kind == "text" && it.glyph == "sub" }
    for (block in clean.split(Regex("\n\\s*\n"))) {
        val lines = block.trim().split("\n")
        val ti = lines.indexOfFirst { it.contains("-->") }
        if (ti < 0) continue
        val parts = lines[ti].split("-->")
        if (parts.size < 2) continue
        val st = parseSrtTime(parts[0])
        val en = parseSrtTime(parts[1])
        if (st < 0 || en <= st) continue
        val body = lines.drop(ti + 1).joinToString("\n") { it.replace(Regex("<[^>]*>"), "") }.trim()
        if (body.isEmpty()) continue
        val o = Overlay()
        o.kind = "text"
        o.glyph = "sub"
        o.text = body
        o.startMs = st
        o.durMs = Math.max(300L, en - st)
        o.size = 0.05f; o.cx = 0.5f; o.cy = 0.86f; o.color = Color.WHITE
        o.bgColor = Color.BLACK; o.bgAlpha = 0.45f; o.bold = true
        o.outlineColor = Color.BLACK; o.outlineW = 0.3f
        proj.overlays.add(o)
        count++
    }
    changed()
    return count
}

fun EditorActivity.exportSrtText(): String {
    val subs = proj.overlays.filter { it.kind == "text" && it.glyph == "sub" }.sortedBy { it.startMs }
    val sb = StringBuilder()
    for ((i, o) in subs.withIndex()) {
        sb.append(i + 1).append('\n')
        sb.append(srtStamp(o.startMs)).append(" --> ").append(srtStamp(o.startMs + o.durMs)).append('\n')
        sb.append(o.text).append("\n\n")
    }
    return sb.toString()
}

fun EditorActivity.openSrtMenu() {
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    box.addView(tlCaption(L("فایل زیرنویس استاندارد (SRT)", "Standard subtitle file (SRT)")), lp(MATCH, WRAP))
    box.addView(tlRow(
        tlPill(L("وارد کردن فایل SRT", "Import SRT"), true) {
            pick(arrayOf("*/*"), false, 120) { uris ->
                if (uris.isNotEmpty()) {
                    val uri = uris[0]
                    Bg.run {
                        val txt = try {
                            contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                        } catch (e: Exception) { "" }
                        Bg.ui {
                            val n = if (txt.isEmpty()) 0 else importSrtText(txt)
                            if (n == 0) toast(L("زیرنویسی در فایل پیدا نشد.", "No subtitles found in the file."))
                            else { toast(L("$n زیرنویس وارد شد.", "$n subtitles imported.")); openSubtitlePanel() }
                        }
                    }
                }
            }
        },
        tlPill(L("خروجی SRT", "Export SRT")) {
            val txt = exportSrtText()
            if (txt.isEmpty()) toast(L("زیرنویسی وجود ندارد.", "There are no subtitles.")) else {
                try {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("srt", txt))
                } catch (e: Exception) { }
                try {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, txt)
                    startActivity(Intent.createChooser(i, "SRT"))
                } catch (e: Exception) { }
            }
        }
    ), lp(MATCH, WRAP))
    box.addView(tlCaption(L("متن SRT هنگام خروجی کپی هم می‌شود تا در یک فایل .srt بچسبانید.", "The SRT text is also copied so you can paste it into a .srt file.")), lp(MATCH, WRAP))
    openPanel(box, true)
}

// ====================================================================== one motion, several results

fun EditorActivity.openVariantsPanel() {
    val c0 = selClip() ?: targetClip()
    if (c0 == null) { toast(L("ابتدا یک کلیپ را انتخاب کنید.", "Select a clip first.")); return }
    var cat = 0
    var cur: MotionPreset? = Motion.byId(c0.motion) ?: Motion.presets.firstOrNull()
    if (cur != null) cat = Math.max(0, Motion.cats.indexOfFirst { it.first == cur!!.cat })

    val holder = lin(true)
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)

    fun applyTo(id: String, amt: Float, all: Boolean) {
        beginChange()
        val targets = if (all) proj.clips.toList() else listOf(c0)
        for (c in targets) { c.motion = id; c.motionAmt = amt }
        changed()
        if (!all && !engine.playing) {
            engine.seek(proj.clipStart(proj.clips.indexOf(c0)) + 5)
            engine.play()
        }
    }

    fun smartAssign() {
        val pool = Motion.presets.filter { it.cat == Motion.cats[cat].first }
        if (pool.isEmpty()) return
        beginChange()
        val start = Math.max(0, pool.indexOf(cur))
        for ((i, c) in proj.clips.withIndex()) {
            c.motion = pool[(start + i * 3) % pool.size].id
            c.motionAmt = 0.8f + (i % 3) * 0.2f
        }
        changed()
        toast(L("برای هر کلیپ یک حرکت متفاوت گذاشته شد.", "Each clip got a different motion."))
    }

    fun build() {
        holder.removeAllViews()
        val p = cur
        if (p == null) return
        holder.addView(tlCaption(L("حرکت پایه: ", "Base motion: ") + p.name), lp(MATCH, WRAP))
        val pool = Motion.presets.filter { it.cat == p.cat }
        val idx = Math.max(0, pool.indexOf(p))
        val variants = ArrayList<Triple<String, String, Float>>() // label, id, amt
        variants.add(Triple(L("ملایم", "Soft") + " · " + p.name, p.id, 0.6f))
        variants.add(Triple(L("استاندارد", "Standard") + " · " + p.name, p.id, 1.0f))
        variants.add(Triple(L("قوی", "Strong") + " · " + p.name, p.id, 1.4f))
        for (k in 1..3) {
            if (pool.size > k) {
                val q = pool[(idx + k) % pool.size]
                variants.add(Triple(q.name, q.id, 1.0f))
            }
        }
        val sv = ScrollView(this)
        val list = lin(true)
        for ((i, v) in variants.withIndex()) {
            val t = tv("${i + 1}.  ${v.first}   (${Math.round(v.third * 100)}%)", 13f, Color.WHITE, false, Gravity.START or Gravity.CENTER_VERTICAL)
            t.setPadding(dp(16), dp(10), dp(16), dp(10))
            t.background = roundedBg(if (c0.motion == v.second && Math.abs(c0.motionAmt - v.third) < 0.01f) C.ACCENT else C.CARD, dpf(10f))
            t.setOnClickListener { applyTo(v.second, v.third, false); build() }
            val l = lp(MATCH, WRAP)
            l.setMargins(dp(10), dp(2), dp(10), dp(2))
            list.addView(t, l)
        }
        sv.addView(list)
        holder.addView(sv, lp(MATCH, dp(170)))
        holder.addView(tlRow(
            tlPill(L("اعمال روی همه‌ی کلیپ‌ها", "Apply to all clips"), true) {
                val cm = c0.motion
                val ca = c0.motionAmt
                if (cm.isEmpty()) toast(L("ابتدا یک نسخه را انتخاب کنید.", "Pick a version first.")) else { applyTo(cm, ca, true); toast(L("روی همه اعمال شد.", "Applied to all.")) }
            },
            tlPill(L("حرکت متفاوت برای هر کلیپ", "Different motion per clip")) { smartAssign() }
        ), lp(MATCH, WRAP))
    }

    box.addView(ChipRow(this, Motion.cats.map { it.second }, cat) { i ->
        cat = i
        cur = Motion.presets.firstOrNull { it.cat == Motion.cats[i].first }
        build()
    }, lp(MATCH, dp(44)))
    box.addView(tlRow(tlPill(L("انتخاب حرکت دیگر از این دسته", "Choose another motion from this category")) {
        val pool = Motion.presets.filter { it.cat == Motion.cats[cat].first }
        Dialogs.choose(this, L("حرکت", "Motion"), pool.map { it.name }) { i -> cur = pool[i]; build() }
    }), lp(MATCH, WRAP))
    box.addView(holder, lp(MATCH, WRAP))
    build()
    openPanel(box, true)
}

// ====================================================================== speed ramps (variable speed)

private class Ramp(val fa: String, val en: String, val speeds: FloatArray)

/** Splits the selected video clip into parts with different speeds (slow motion peak, acceleration, pulses...). */
fun EditorActivity.openSpeedRampPanel() {
    val c0 = selClip() ?: targetClip()
    if (c0 == null || c0.isImage) { toast(L("یک ویدیو را انتخاب کنید.", "Select a video clip.")); return }
    val ramps = listOf(
        Ramp("اوج آهسته", "Slow-mo peak", floatArrayOf(1f, 0.35f, 1f)),
        Ramp("شتاب‌گیری", "Speed up", floatArrayOf(0.5f, 1f, 2f)),
        Ramp("ترمز", "Slow down", floatArrayOf(2f, 1f, 0.4f)),
        Ramp("ضربان (تند و کند)", "Pulse", floatArrayOf(1f, 0.4f, 1f, 0.4f, 1f)),
        Ramp("پرش سریع", "Fast burst", floatArrayOf(1f, 3f, 1f)),
        Ramp("حرکت آهسته‌ی کامل", "Full slow-mo", floatArrayOf(0.25f))
    )
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    box.addView(tlCaption(L("کلیپ به چند بخش با سرعت‌های متفاوت تقسیم می‌شود. هر بخش را بعداً می‌توانید جدا ویرایش کنید.", "The clip is split into parts with different speeds. Each part stays editable.")), lp(MATCH, WRAP))
    val sv = ScrollView(this)
    val list = lin(true)
    for (r in ramps) {
        val t = tv(L(r.fa, r.en) + "   " + r.speeds.joinToString(" → ") { "×" + (if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()) }, 13f, Color.WHITE, false, Gravity.START or Gravity.CENTER_VERTICAL)
        t.setPadding(dp(16), dp(10), dp(16), dp(10))
        t.background = roundedBg(C.CARD, dpf(10f))
        t.setOnClickListener {
            val i = proj.clips.indexOf(c0)
            if (i < 0) return@setOnClickListener
            beginChange()
            val span = c0.trimEnd - c0.trimStart
            val n = r.speeds.size
            val parts = ArrayList<Clip>()
            for (k in 0 until n) {
                val part = if (k == 0) c0 else c0.copyOf()
                part.trimStart = c0.trimStart + span * k / n
                part.trimEnd = if (k == n - 1) c0.trimEnd else c0.trimStart + span * (k + 1) / n
                part.speed = r.speeds[k]
                if (k > 0) { part.trans = "none"; part.animIn = "none" }
                if (k < n - 1) part.animOut = "none"
                parts.add(part)
            }
            proj.clips.removeAt(i)
            proj.clips.addAll(i, parts)
            timeline.selection = Sel("clip", parts[0].id)
            changed()
            refreshToolbar()
            engine.seek(proj.clipStart(i) + 5)
            engine.play()
        }
        val l = lp(MATCH, WRAP)
        l.setMargins(dp(10), dp(2), dp(10), dp(2))
        list.addView(t, l)
    }
    sv.addView(list)
    box.addView(sv, lp(MATCH, dp(230)))
    openPanel(box, true)
}

// ====================================================================== PIP with video + colour key

/** Video on video: the first 10 seconds are sampled at 10 fps into small pictures that play as an overlay (same in preview and export). */
fun EditorActivity.importPipVideo(uri: android.net.Uri) {
    val dlg = progressDialog(L("در حال آماده‌سازی ویدیوی پیپ…", "Preparing PIP video…"))
    val ctx = applicationContext
    val pid = proj.id
    Bg.run {
        var dirPath = ""
        var n = 0
        var aspect = 1f
        var hasAudio = false
        var audio: AudioItem? = null
        val fps = 10
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, uri)
            val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasAudio = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val total = Math.min(dur, 10000L)
            val want = Math.max(1, (total * fps / 1000L).toInt())
            val dir = java.io.File(Store.mediaDir(ctx, pid), "pip_" + uid())
            dir.mkdirs()
            for (i in 0 until want) {
                var b = mmr.getScaledFrameAtTime(i * 1000000L / fps, android.media.MediaMetadataRetriever.OPTION_CLOSEST, 480, 480) ?: continue
                if (b.width > 480 || b.height > 480) {
                    val s = 480f / Math.max(b.width, b.height)
                    b = android.graphics.Bitmap.createScaledBitmap(b, Math.max(1, (b.width * s).toInt()), Math.max(1, (b.height * s).toInt()), true)
                }
                if (n == 0) aspect = b.width.toFloat() / b.height
                java.io.FileOutputStream(java.io.File(dir, "f$n.jpg")).use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
                n++
            }
            dirPath = dir.absolutePath
        } catch (e: Throwable) {
            n = 0
        } finally {
            try { mmr.release() } catch (e: Throwable) { }
        }
        if (n > 0 && hasAudio) audio = MediaUtil.importAudio(ctx, uri, pid, "music")
        Bg.ui {
            dlg.dismiss()
            if (n == 0) toast(L("این ویدیو خوانده نشد.", "Could not read this video.")) else {
                beginChange()
                val o = Overlay()
                o.kind = "image"
                o.frames = dirPath
                o.fps = fps
                o.nf = n
                o.imgAspect = aspect
                o.uri = android.net.Uri.fromFile(java.io.File(dirPath, "f0.jpg")).toString()
                addOverlay(o)
                o.durMs = Math.max(500L, n * 1000L / fps)
                o.scale = 0.8f
                o.cx = 0.72f; o.cy = 0.25f
                if (audio != null) {
                    audio.startMs = o.startMs
                    audio.trimEnd = Math.min(audio.trimEnd, audio.trimStart + o.durMs)
                    proj.audios.add(audio)
                }
                changed()
                refreshToolbar()
                toast(L("ویدیوی پیپ اضافه شد (تا ۱۰ ثانیه).", "PIP video added (up to 10 s)."))
            }
        }
    }
}

/** Colour key for a picture or PIP video overlay (green screen, or removal of a plain background). */
fun EditorActivity.openChromaPanel() {
    val o = selOverlay()
    if (o == null || o.kind != "image") { toast(L("یک عکس یا ویدیوی پیپ را انتخاب کنید.", "Select a PIP picture or video.")); return }
    val snap = proj.snapshot()
    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    box.addView(tlCaption(L("رنگ پس‌زمینه‌ی حذف‌شدنی را انتخاب کنید (پرده سبز یا هر رنگ ساده).", "Pick the colour to remove (green screen or any plain colour).")), lp(MATCH, WRAP))
    val current = if (o.chroma != 0) o.chroma else 0xFF00B140.toInt()
    box.addView(ColorRow(this, current, true) { col ->
        o.chroma = if (col == 0) 0 else (col or 0xFF000000.toInt())
        changed()
    }, lp(MATCH, dp(44)))
    box.addView(SliderRow(this, L("دامنه‌ی رنگ", "Tolerance"), 0f, 0.8f, o.chromaTol, false, { Math.round(it * 100f).toString() + "%" }, { v -> o.chromaTol = v; changed() }), lp(MATCH, dp(44)))
    box.addView(SliderRow(this, L("نرمی لبه", "Edge softness"), 0.01f, 0.4f, o.chromaSoft, false, { Math.round(it * 100f).toString() + "%" }, { v -> o.chromaSoft = v; changed() }), lp(MATCH, dp(44)))
    box.addView(tlCaption(L("اگر پس‌زمینه یک‌دست نباشد، حذف کامل نمی‌شود.", "Works best on an even background colour.")), lp(MATCH, WRAP))
    openPanel(box, true, snap)
}

// ====================================================================== multi-photo compositions

fun EditorActivity.openCompPanel(existing: Overlay?) {
    val snap = proj.snapshot()
    val o: Overlay
    if (existing == null) {
        o = Overlay()
        o.kind = "comp"
        o.cx = 0.5f; o.cy = 0.5f
        o.cfg = Comp.toJson(Comp.Cfg("reel", emptyList(), 1.2f, 0xFF101018.toInt()))
        addOverlay(o)
        o.startMs = 0L
        o.durMs = Math.max(2000L, proj.totalMs())
        changed()
    } else {
        o = existing
        timeline.selection = Sel("overlay", o.id)
    }
    fun cur(): Comp.Cfg = Comp.cfgOf(o)
    fun put(mode: String = cur().mode, uris: List<String> = cur().uris, per: Float = cur().per, bg: Int = cur().bg) {
        o.cfg = Comp.toJson(Comp.Cfg(mode, uris, per, bg))
        changed()
    }

    val box = lin(true)
    box.setBackgroundColor(C.PANEL)
    val info = tv("", 12.5f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
    info.setPadding(dp(14), dp(6), dp(14), dp(2))
    fun refreshInfo() { info.text = L("${cur().uris.size} عکس انتخاب شده (حداکثر ۱۲)", "${cur().uris.size} photos (max 12)") }
    refreshInfo()
    box.addView(info, lp(MATCH, WRAP))
    box.addView(tlRow(tlPill(L("افزودن عکس‌ها", "Add photos"), true) {
        pick(arrayOf("image/*"), true, 121) { uris ->
            if (uris.isNotEmpty()) {
                val dlg = progressDialog(L("در حال وارد کردن عکس‌ها…", "Importing photos…"))
                val ctx = applicationContext
                val pid = proj.id
                Bg.run {
                    val list = ArrayList<String>(cur().uris)
                    for (u in uris) {
                        if (list.size >= 12) break
                        val bmp = MediaUtil.loadBitmap(ctx, u, 900) ?: continue
                        val f = java.io.File(Store.mediaDir(ctx, pid), uid() + ".jpg")
                        java.io.FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                        list.add(android.net.Uri.fromFile(f).toString())
                        bmp.recycle()
                    }
                    Bg.ui { dlg.dismiss(); put(uris = list); refreshInfo() }
                }
            }
        }
    }, tlPill(L("پاک کردن عکس‌ها", "Clear photos")) { put(uris = emptyList()); refreshInfo() }), lp(MATCH, WRAP))
    val ids = Comp.MODES.map { it.first }
    box.addView(ChipRow(this, ids.map { Comp.modeName(it) }, Math.max(0, ids.indexOf(cur().mode))) { i -> put(mode = ids[i]) }, lp(MATCH, dp(44)))
    var tmp = cur().per
    box.addView(SliderRow(this, L("زمان هر عکس (ثانیه)", "Seconds per photo"), 0.3f, 4f, cur().per, false, { String.format(java.util.Locale.US, "%.1f", it) }, { v -> tmp = v }, { put(per = tmp) }), lp(MATCH, dp(44)))
    box.addView(tlCaption(L("رنگ پس‌زمینه", "Background colour")), lp(MATCH, WRAP))
    box.addView(ColorRow(this, cur().bg) { col -> put(bg = col or 0xFF000000.toInt()) }, lp(MATCH, dp(44)))
    box.addView(tlRow(tlPill(L("پخش تا پایان پروژه", "Until the end")) {
        o.startMs = 0L
        o.durMs = Math.max(2000L, proj.totalMs())
        changed()
    }), lp(MATCH, WRAP))
    openPanel(box, true, snap)
}
