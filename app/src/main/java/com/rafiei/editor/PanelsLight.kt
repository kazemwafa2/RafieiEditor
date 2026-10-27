package com.rafiei.editor

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

// ------------------------------------------------------------------ helpers (private to this file)

private fun EditorActivity.ltBox(vararg views: View): LinearLayout {
    val l = lin(true)
    l.setBackgroundColor(C.PANEL)
    l.setPadding(0, dp(6), 0, dp(8))
    for (v in views) l.addView(v, lp(MATCH, WRAP))
    return l
}

private fun EditorActivity.ltCaption(text: String): TextView {
    val t = tv(text, 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
    t.setPadding(dp(14), dp(6), dp(14), dp(2))
    return t
}

private fun EditorActivity.ltPill(text: String, active: Boolean = false, onClick: () -> Unit): TextView {
    val t = tv(text, 12.5f, Color.WHITE)
    t.setPadding(dp(14), dp(8), dp(14), dp(8))
    t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
    t.setOnClickListener { onClick() }
    return t
}

private fun EditorActivity.ltEdit(hint: String, text: String, multi: Boolean): EditText {
    val et = EditText(this)
    et.setText(text)
    et.setTextColor(Color.WHITE)
    et.setHintTextColor(C.TXT2)
    et.hint = hint
    et.textSize = 14f
    if (multi) {
        et.minLines = 3
        et.maxLines = 5
        et.gravity = Gravity.TOP or Gravity.START
    } else {
        et.maxLines = 2
    }
    et.background = roundedBg(C.CARD, dpf(10f))
    et.setPadding(dp(12), dp(8), dp(12), dp(8))
    return et
}

private fun EditText.onText(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            block(s?.toString() ?: "")
        }
    })
}

private fun fmtP(v: Float): String = Math.round(v * 100f).toString() + "%"
private fun fmtD(v: Float): String = Math.round(v).toString() + "°"
private fun fmtS(v: Float): String = String.format(Locale.US, "%.1f", v)

// ------------------------------------------------------------------ light panel

private val LIGHT_ANIMS = listOf(
    "none" to ("ثابت" to "Still"),
    "sweep" to ("رفت‌وبرگشت افقی" to "Sweep"),
    "vert" to ("بالا و پایین" to "Up / down"),
    "orbit" to ("چرخش دایره‌ای" to "Orbit"),
    "diag" to ("مورب ↗" to "Diagonal ↗"),
    "diag2" to ("مورب ↘" to "Diagonal ↘"),
    "fig8" to ("عدد ۸" to "Figure 8"),
    "bounce" to ("پرش در کادر" to "Bounce"),
    "wander" to ("حرکت آزاد" to "Wander"),
    "scanx" to ("اسکن افقی" to "Scan X"),
    "scany" to ("اسکن عمودی" to "Scan Y"),
    "rotate" to ("نوسان زاویه" to "Swing"),
    "spin" to ("چرخش پرتو" to "Spin"),
    "orbitspin" to ("دایره + چرخش" to "Orbit + spin"),
    "pulse" to ("تپش" to "Pulse"),
    "breathe" to ("تنفس اندازه" to "Breathe")
)

/** Studio light ("نور") of the selected clip. */
fun EditorActivity.openLightPanel(fxT: FxItem? = null) {
    val c = if (fxT == null) (targetClip() ?: return) else null
    val light = ClipLight.fromJson(if (fxT != null) fxT.cfg else c!!.light)
    val content = FrameLayout(this)

    fun push() {
        if (fxT != null) fxT.cfg = light.toJson() else c!!.light = if (light.mode == "off") "" else light.toJson()
        stage.top.invalidate()
        engine.refresh()
    }

    fun slider(label: String, mn: Float, mx: Float, v: Float, fmt: (Float) -> String, set: (Float) -> Unit): SliderRow =
        SliderRow(this, label, mn, mx, v, false, fmt, { x -> set(x); push() })

    fun build() {
        content.removeAllViews()
        val col = lin(true)
        if (light.mode == "off") {
            col.addView(ltCaption(L("یکی از حالت‌های نور را انتخاب کنید.", "Choose a light mode above.")), lp(MATCH, WRAP))
        } else {
            val sv = ScrollView(this)
            val inner = lin(true)
            val isBeam = light.mode == "beam"
            inner.addView(ltCaption(L("حرکت نور", "Light motion")), lp(MATCH, WRAP))
            val names = LIGHT_ANIMS.map { L(it.second.first, it.second.second) }
            val cur = Math.max(0, LIGHT_ANIMS.indexOfFirst { it.first == light.anim })
            inner.addView(ChipRow(this, names, cur) { i ->
                light.anim = LIGHT_ANIMS[i].first
                if (light.anim != "none") {
                    if (light.spd < 0.2f) light.spd = 0.6f
                    if (light.amp < 0.05f) light.amp = 0.3f
                }
                push()
                build()
                if (light.anim != "none" && !engine.playing) engine.play()
            }, lp(MATCH, WRAP))
            if (light.anim != "none") {
                inner.addView(slider(L("سرعت", "Speed"), 10f, 300f, light.spd * 100f, { fmtS(it / 100f) }) { light.spd = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("دامنه افقی", "Range X"), 2f, 80f, light.amp * 100f, { Math.round(it).toString() }) { light.amp = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("دامنه عمودی", "Range Y"), 2f, 80f, (if (light.ampY < 0f) light.amp else light.ampY) * 100f, { Math.round(it).toString() }) { light.ampY = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("نقطه شروع حرکت", "Start phase"), 0f, 100f, light.phase * 100f, ::fmtP2) { light.phase = it / 100f }, lp(MATCH, WRAP))
                val dw = FrameLayout(this)
                dw.setPadding(dp(10), dp(4), dp(10), dp(2))
                dw.addView(ltPill(L("جهت حرکت: " + (if (light.dir > 0) "عادی" else "معکوس"), "Direction: " + (if (light.dir > 0) "normal" else "reverse")), true) {
                    light.dir = -light.dir
                    push()
                    build()
                }, FrameLayout.LayoutParams(WRAP, WRAP))
                inner.addView(dw, lp(MATCH, WRAP))
            }
            inner.addView(slider(L("جای نور: چپ/راست", "Light X"), 0f, 100f, light.lx * 100f, { Math.round(it).toString() }) { light.lx = it / 100f }, lp(MATCH, WRAP))
            inner.addView(slider(L("جای نور: بالا/پایین", "Light Y"), 0f, 100f, light.ly * 100f, { Math.round(it).toString() }) { light.ly = it / 100f }, lp(MATCH, WRAP))
            if (isBeam) {
                inner.addView(slider(L("زاویه پرتو", "Beam angle"), -90f, 90f, light.ang, ::fmtD) { light.ang = it }, lp(MATCH, WRAP))
                inner.addView(slider(L("پهنای پرتو", "Beam width"), 2f, 100f, light.wid * 100f, { Math.round(it).toString() }) { light.wid = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("نرمی لبه", "Edge softness"), 0f, 30f, light.soft * 100f, { Math.round(it).toString() }) { light.soft = it / 100f }, lp(MATCH, WRAP))
            } else {
                inner.addView(slider(L("اندازه نور", "Light size"), 10f, 120f, light.rad * 100f, { Math.round(it).toString() }) { light.rad = it / 100f }, lp(MATCH, WRAP))
                inner.addView(slider(L("نرمی لبه", "Edge softness"), 0f, 100f, light.soft * 100f, { Math.round(it).toString() }) { light.soft = it / 100f }, lp(MATCH, WRAP))
            }
            if (light.mode == "focus") {
                inner.addView(slider(L("محوی حاشیه", "Edge blur"), 0f, 60f, light.blur, { Math.round(it).toString() }) { light.blur = it }, lp(MATCH, WRAP))
            }
            inner.addView(slider(L("تاریکی اطراف", "Darkness"), 0f, 100f, light.dark * 100f, ::fmtP2) { light.dark = it / 100f }, lp(MATCH, WRAP))
            if (light.mode != "focus") {
                inner.addView(slider(L("درخشش نور", "Glow"), 0f, 100f, light.glow * 100f, ::fmtP2) { light.glow = it / 100f }, lp(MATCH, WRAP))
                inner.addView(ltCaption(L("رنگ نور", "Light colour")), lp(MATCH, WRAP))
                inner.addView(ColorRow(this, light.color) { col2 -> light.color = col2; push() }, lp(MATCH, WRAP))
            }
            val w = FrameLayout(this)
            w.setPadding(dp(10), dp(4), dp(10), dp(2))
            w.addView(ltPill(L("اعمال این نور روی همه کلیپ‌ها", "Apply to all clips")) {
                val js = light.toJson()
                for (k in proj.clips) k.light = if (light.mode == "off") "" else js
                stage.top.invalidate()
                engine.refresh()
                toast(L("روی همه کلیپ‌ها اعمال شد", "Applied to all clips"))
            }, FrameLayout.LayoutParams(WRAP, WRAP))
            if (fxT == null) inner.addView(w, lp(MATCH, WRAP))
            sv.addView(inner)
            col.addView(sv, lp(MATCH, dp(280)))
        }
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    // ready designs: light shape + automatic motion, everything adjustable afterwards
    class Design(val name: String, val preset: String, val anim: String, val spd: Float, val amp: Float)
    val designs = listOf(
        Design(L("نوار بالا↕پایین", "Band up/down"), "eyes", "vert", 0.6f, 0.30f),
        Design(L("اسکن عمودی", "Scan down"), "eyes", "scany", 0.4f, 0.30f),
        Design(L("پرتو چپ↔راست", "Beam left/right"), "vbeam", "sweep", 0.6f, 0.30f),
        Design(L("پرتو چرخان", "Spinning beam"), "beam", "spin", 0.6f, 0.2f),
        Design(L("پرتو مورب", "Diagonal beam"), "diag", "diag", 0.6f, 0.3f),
        Design(L("نورافکن دایره‌ای", "Spot orbit"), "spot", "orbit", 0.6f, 0.3f),
        Design(L("نورافکن پرش", "Spot bounce"), "spot", "bounce", 0.7f, 0.3f),
        Design(L("نورافکن آزاد", "Spot wander"), "spot", "wander", 0.5f, 0.35f),
        Design(L("تمرکز تپنده", "Breathing focus"), "focus", "breathe", 0.6f, 0.2f),
        Design(L("نوار عدد ۸", "Figure 8"), "strip", "fig8", 0.6f, 0.3f)
    )
    val dChips = ChipRow(this, designs.map { it.name }, -1) { i ->
        val d = designs[i]
        light.applyPreset(d.preset)
        light.anim = d.anim; light.spd = d.spd; light.amp = d.amp; light.ampY = -1f; light.phase = 0f; light.dir = 1f
        push()
        build()
        if (!engine.playing) engine.play()
    }

    val ids = ClipLight.PRESETS.map { it.first }
    val labels = ClipLight.PRESETS.map { it.second }
    var curI = ids.indexOfFirst { it != "off" && light.mode != "off" && light.isPreset(it) }
    if (light.mode == "off") curI = ids.indexOf("off")
    val chips = ChipRow(this, labels, curI) { i ->
        val id = ids[i]
        light.applyPreset(id)
        push()
        build()
    }
    build()
    openPanel(ltBox(dChips, chips, content), false)
}

private fun fmtP2(v: Float): String = Math.round(v).toString() + "%"

// ------------------------------------------------------------------ subtitles / lyrics

private class SubLine(var startMs: Long, var text: String)

/** Timed lyrics ("زیرنویس"): every line becomes a text overlay tagged with glyph = "sub". */
fun EditorActivity.openSubtitlePanel() {
    val existing = proj.overlays.filter { it.kind == "text" && it.glyph == "sub" }.sortedBy { it.startMs }
    val lines = ArrayList<SubLine>()
    for (o in existing) lines.add(SubLine(o.startMs, o.text))
    // style, taken from the first existing subtitle
    val first = existing.firstOrNull()
    var size = first?.size ?: 0.05f
    var cy = first?.cy ?: 0.86f
    var cx = first?.cx ?: 0.5f
    var color = first?.color ?: Color.WHITE
    var bgA = first?.bgAlpha ?: 0.45f
    var font = first?.font ?: Fonts.defaultFa(this)
    val total = Math.max(proj.totalMs(), 1000L)
    var tapIndex = -1

    fun subs(): List<Overlay> = proj.overlays.filter { it.kind == "text" && it.glyph == "sub" }

    fun styleOne(o: Overlay) {
        o.size = size; o.cx = cx; o.cy = cy; o.color = color; o.bgColor = Color.BLACK; o.bgAlpha = bgA
        o.font = font; o.bold = true; o.outlineColor = Color.BLACK; o.outlineW = 0.3f; o.scale = 1f; o.rot = 0f
        o.animIn = "none"; o.animOut = "none"
    }

    fun rebuild() {
        proj.overlays.removeAll { it.kind == "text" && it.glyph == "sub" }
        val timed = lines.filter { it.startMs >= 0 && it.text.isNotBlank() }.sortedBy { it.startMs }
        for ((i, ln) in timed.withIndex()) {
            val o = Overlay()
            o.kind = "text"
            o.glyph = "sub"
            o.text = ln.text.trim()
            o.startMs = ln.startMs
            val nextStart = if (i + 1 < timed.size) timed[i + 1].startMs else Math.min(total, ln.startMs + 4000L)
            o.durMs = Math.max(300L, Math.min(10000L, nextStart - ln.startMs))
            styleOne(o)
            proj.overlays.add(o)
        }
        timeline.relayout()
        stage.top.invalidate()
        engine.refresh()
    }

    fun restyle() {
        for (o in subs()) styleOne(o)
        stage.top.invalidate()
    }

    val content = FrameLayout(this)
    lateinit var rowsHost: LinearLayout
    lateinit var tapBox: LinearLayout
    lateinit var input: EditText

    fun textLines(): List<String> = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun refreshRows() {
        rowsHost.removeAllViews()
        for ((idx, ln) in lines.withIndex()) {
            val r = lin(false)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(8), dp(2), dp(8), dp(2))
            val t = ltEdit("—", if (ln.startMs >= 0) String.format(Locale.US, "%.1f", ln.startMs / 1000f) else "", false)
            t.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            t.onText { s ->
                val v = s.toFloatOrNull()
                ln.startMs = if (v == null) -1L else Math.max(0L, (v * 1000f).toLong())
                rebuild()
            }
            val x = ltEdit("", ln.text, false)
            x.onText { s -> ln.text = s; rebuild() }
            val menu = ltPill("⋮") {
                Dialogs.choose(this, L("خط زیرنویس", "Subtitle line"), listOf(
                    L("کپی همین خط", "Duplicate"), L("تقسیم به دو خط (از وسط)", "Split in two"),
                    L("ادغام با خط بعدی", "Merge with next"), L("حذف", "Delete"))) { k ->
                    when (k) {
                        0 -> lines.add(idx + 1, SubLine(if (ln.startMs >= 0) ln.startMs + 1000L else -1L, ln.text))
                        1 -> {
                            val w = ln.text.trim().split(" ")
                            val h = Math.max(1, w.size / 2)
                            if (w.size > 1) {
                                val nextT = if (idx + 1 < lines.size && lines[idx + 1].startMs > ln.startMs) lines[idx + 1].startMs else ln.startMs + 3000L
                                val mid = if (ln.startMs >= 0) (ln.startMs + nextT) / 2 else -1L
                                ln.text = w.subList(0, h).joinToString(" ")
                                lines.add(idx + 1, SubLine(mid, w.subList(h, w.size).joinToString(" ")))
                            }
                        }
                        2 -> if (idx + 1 < lines.size) { ln.text = ln.text + " " + lines[idx + 1].text; lines.removeAt(idx + 1) }
                        3 -> lines.removeAt(idx)
                    }
                    rebuild(); refreshRows()
                }
            }
            val go = ltPill("▶") {
                if (ln.startMs >= 0) {
                    engine.pause()
                    engine.seek(ln.startMs)
                }
            }
            val lt = lp(dp(64), WRAP)
            lt.rightMargin = dp(6)
            r.addView(t, lt)
            r.addView(x, lp(0, WRAP, 1f))
            val lg = lp(WRAP, WRAP)
            lg.leftMargin = dp(6)
            r.addView(go, lg)
            r.addView(menu, lp(WRAP, WRAP).also { it.leftMargin = dp(4) })
            rowsHost.addView(r, lp(MATCH, WRAP))
            if (idx > 200) break
        }
    }

    fun refreshTap() {
        tapBox.removeAllViews()
        if (tapIndex < 0 || tapIndex >= lines.size) return
        tapBox.addView(ltCaption(L("هنگام پخش، درست لحظه‌ی شروع هر خط دکمه را بزنید:", "While playing, tap exactly when each line starts:")), lp(MATCH, WRAP))
        tapBox.addView(tv(lines[tapIndex].text, 15f, Color.WHITE, true), lp(MATCH, WRAP))
        val row = lin(false)
        row.setPadding(dp(10), dp(6), dp(10), dp(2))
        row.addView(ltPill(L("ثبت زمان این خط ◀", "Mark this line"), true) {
            lines[tapIndex].startMs = engine.timeMs
            tapIndex++
            if (tapIndex >= lines.size) {
                tapIndex = -1
                engine.pause()
            }
            rebuild()
            refreshRows()
            refreshTap()
        }, lp(0, WRAP, 1f))
        tapBox.addView(row, lp(MATCH, WRAP))
    }

    fun loadFromInput(timedImport: Boolean) {
        val tl = textLines()
        if (tl.isEmpty()) {
            toast(L("ابتدا متن را بنویسید.", "Type the lyrics first."))
            return
        }
        lines.clear()
        val re = Regex("^\\[?(\\d+):(\\d+(?:[.,]\\d+)?)\\]?\\s*(.*)$")
        var anyTimed = false
        val parsed = ArrayList<SubLine>()
        for (s in tl) {
            val m = re.find(s)
            if (m != null && m.groupValues[3].isNotEmpty()) {
                val sec = m.groupValues[1].toInt() * 60f + m.groupValues[2].replace(',', '.').toFloat()
                parsed.add(SubLine((sec * 1000f).toLong(), m.groupValues[3]))
                anyTimed = true
            } else {
                parsed.add(SubLine(-1L, s))
            }
        }
        if (timedImport && anyTimed) {
            lines.addAll(parsed)
        } else {
            val n = tl.size
            val start = engine.timeMs
            val span = Math.max(1000L, total - start)
            for ((i, s) in tl.withIndex()) {
                val txt = re.find(s)?.groupValues?.get(3)?.takeIf { it.isNotEmpty() } ?: s
                lines.add(SubLine(start + span * i / n, txt))
            }
        }
        tapIndex = -1
        rebuild()
        refreshRows()
        refreshTap()
    }


    /** One button: paste all lyrics, the app finds the singing/speech and the pauses in the audio and places every line itself. */
    fun autoSync() {
        val tl = textLines().map { Regex("^\\[?\\d+:\\d+(?:[.,]\\d+)?\\]?\\s*").replace(it, "") }.filter { it.isNotBlank() }
        if (tl.isEmpty()) { toast(L("ابتدا متن را بنویسید یا بچسبانید.", "Paste the lyrics first.")); return }
        var path = ""; var from = 0L; var to = 0L; var base = 0L; var speed = 1f
        val au = proj.audios.firstOrNull { it.kind == "music" } ?: proj.audios.firstOrNull { it.kind == "voice" } ?: proj.audios.firstOrNull()
        if (au != null) {
            path = MediaUtil.pathOf(au.uri); from = au.trimStart; to = if (au.trimEnd > au.trimStart) au.trimEnd else au.srcDurMs
            base = au.startMs; speed = if (au.speed > 0.05f) au.speed else 1f
        } else {
            val ci = proj.clips.indexOfFirst { !it.isImage && it.hasAudio }
            if (ci < 0) { toast(L("آهنگ یا کلیپ دارای صدا لازم است.", "A song or a clip with audio is needed.")); return }
            val c = proj.clips[ci]
            path = MediaUtil.pathOf(c.uri); from = c.trimStart; to = if (c.trimEnd > c.trimStart) c.trimEnd else c.srcDurMs
            base = proj.clipStart(ci); speed = if (c.speed > 0.05f) c.speed else 1f
        }
        val playhead = engine.timeMs
        try { engine.pause() } catch (e: Throwable) { }
        val dlg = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(L("در حال تحلیل صدا و جای‌گذاری خطوط…", "Analysing the audio and placing the lines…")).setCancelable(false).create()
        dlg.show()
        val p0 = path; val f0 = from; val t0 = to; val b0 = base; val sp0 = speed
        Bg.run {
            val pcm = AutoSub.decode16k(p0, f0, t0)
            val res = ArrayList<Long>()
            if (pcm != null && pcm.size > 16000) {
                val fr = 320
                val n = pcm.size / fr
                val raw = DoubleArray(n)
                for (i in 0 until n) raw[i] = AutoSub.rms(pcm, i * fr, i * fr + fr)
                val e = DoubleArray(n)
                for (i in 0 until n) { var a = 0.0; var k = 0; for (j in Math.max(0, i - 2)..Math.min(n - 1, i + 2)) { a += raw[j]; k++ }; e[i] = a / k }
                val sorted = e.sortedArray()
                val floor = sorted[(n * 0.1).toInt()]
                val peak = sorted[Math.min(n - 1, (n * 0.95).toInt())]
                val thr = floor + (peak - floor) * 0.12
                var s0 = Math.max(0L, ((playhead - b0) * sp0 / 20f).toLong()).toInt()
                if (s0 >= n - 10) s0 = 0
                while (s0 < n - 1 && e[s0] <= thr) s0++
                var e0 = n - 1
                while (e0 > s0 + 10 && e[e0] <= thr) e0--
                val span = Math.max(1, e0 - s0)
                val w = DoubleArray(tl.size) { Math.max(3, tl[it].replace(" ", "").length).toDouble() }
                val tot = w.sum()
                val lineF = span.toDouble() / tl.size
                val win = Math.max(20.0, Math.min(250.0, lineF * 0.4)).toInt()
                val minLine = Math.max(15, (lineF * 0.25).toInt())
                val bounds = IntArray(tl.size)
                bounds[0] = s0
                var cum = 0.0
                for (k in 1 until tl.size) {
                    cum += w[k - 1]
                    val ideal = s0 + (span * cum / tot).toInt()
                    var best = -1
                    var bestScore = Double.MAX_VALUE
                    val lo = Math.max(bounds[k - 1] + minLine, ideal - win)
                    val hi = Math.min(e0 - 5, ideal + win)
                    var i = lo
                    while (i <= hi) {
                        val sc = e[i] / Math.max(1e-6, peak) + 0.35 * Math.abs(i - ideal).toDouble() / win
                        if (sc < bestScore) { bestScore = sc; best = i }
                        i++
                    }
                    if (best < 0) best = Math.max(bounds[k - 1] + minLine, Math.min(n - 1, ideal))
                    // the line starts where sound returns after the pause
                    var j = best
                    val lim = Math.min(n - 1, best + 40)
                    while (j < lim && e[j] < e[best] + (peak - floor) * 0.12) j++
                    bounds[k] = Math.max(bounds[k - 1] + minLine, j)
                }
                for (k in 0 until tl.size) res.add(b0 + (bounds[k] * 20L / sp0).toLong())
            }
            Bg.ui {
                dlg.dismiss()
                if (res.size != tl.size) {
                    toast(L("صدا خوانده نشد؛ خطوط مساوی پخش شد.", "Could not read the audio; spread evenly."))
                    loadFromInput(false)
                } else {
                    lines.clear()
                    for (k in tl.indices) lines.add(SubLine(res[k], tl[k]))
                    tapIndex = -1
                    rebuild(); refreshRows(); refreshTap()
                    toast(L("${tl.size} خط روی آهنگ قرار گرفت. اگر خطی جابه‌جاست، فقط عدد زمانش را اصلاح کنید یا «زمان‌بندی با ضربه» را بزنید.", "${tl.size} lines placed. Fix any time by editing its number, or use Tap to time."))
                }
            }
        }
    }

    val col = lin(true)
    val sv = ScrollView(this)
    val inner = lin(true)

    input = ltEdit(L("متن آهنگ را اینجا بنویسید یا بچسبانید؛ هر خط یک زیرنویس. می‌توانید «00:12.5 متن» یا فرمت LRC هم بچسبانید.", "Paste lyrics, one line per subtitle. Lines like \"00:12.5 text\" or LRC are timed automatically."),
        lines.joinToString("\n") { it.text }, true)
    val inW = FrameLayout(this)
    inW.setPadding(dp(10), dp(2), dp(10), dp(4))
    inW.addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
    inner.addView(inW, lp(MATCH, WRAP))

    val btns = lin(false)
    btns.setPadding(dp(6), dp(2), dp(6), dp(4))
    fun bl() = lp(0, WRAP, 1f).also { it.setMargins(dp(3), 0, dp(3), 0) }
    btns.addView(ltPill(L("توزیع مساوی", "Spread evenly")) { loadFromInput(false) }, bl())
    btns.addView(ltPill(L("از متن زمان‌دار", "From timed text")) { loadFromInput(true) }, bl())
    btns.addView(ltPill(L("زمان‌بندی با ضربه", "Tap to time"), true) {
        val tl = textLines()
        if (tl.isEmpty()) {
            toast(L("ابتدا متن را بنویسید.", "Type the lyrics first."))
        } else {
            lines.clear()
            for (s in tl) lines.add(SubLine(-1L, s))
            rebuild()
            tapIndex = 0
            engine.seek(0)
            engine.play()
            refreshRows()
            refreshTap()
        }
    }, bl())
    val btnsA = FrameLayout(this)
    btnsA.setPadding(dp(10), dp(2), dp(10), dp(4))
    btnsA.addView(ltPill(L("✨ همگام‌سازی خودکار با آهنگ", "✨ Auto-sync to the song"), true) { autoSync() }, FrameLayout.LayoutParams(MATCH, WRAP))
    inner.addView(btnsA, lp(MATCH, WRAP))
    inner.addView(ltCaption(L("متن را بچسبانید، نشانگر را روی شروع خواندن بگذارید و دکمه را بزنید؛ خودش شروع هر خط را از روی مکث‌های آهنگ پیدا می‌کند.", "Paste lyrics, put the playhead where singing starts, tap the button; line starts are found from the pauses in the song.")), lp(MATCH, WRAP))
    inner.addView(btns, lp(MATCH, WRAP))
    tapBox = lin(true)
    inner.addView(tapBox, lp(MATCH, WRAP))

    val btns2 = FrameLayout(this)
    btns2.setPadding(dp(10), dp(0), dp(10), dp(4))
    btns2.addView(ltPill(L("پاک کردن همه زیرنویس‌ها", "Remove all subtitles")) {
        lines.clear()
        tapIndex = -1
        rebuild()
        refreshRows()
        refreshTap()
    }, FrameLayout.LayoutParams(WRAP, WRAP))
    inner.addView(btns2, lp(MATCH, WRAP))

    inner.addView(ltCaption(L("ظاهر زیرنویس", "Subtitle style")), lp(MATCH, WRAP))
    inner.addView(SliderRow(this, L("اندازه", "Size"), 20f, 120f, size * 1000f, false, { Math.round(it).toString() }, { v -> size = v / 1000f; restyle() }), lp(MATCH, WRAP))
    inner.addView(SliderRow(this, L("بالا / پایین", "Position Y"), 5f, 97f, cy * 100f, false, { Math.round(it).toString() }, { v -> cy = v / 100f; restyle() }), lp(MATCH, WRAP))
    inner.addView(SliderRow(this, L("چپ / راست", "Position X"), 10f, 90f, cx * 100f, false, { Math.round(it).toString() }, { v -> cx = v / 100f; restyle() }), lp(MATCH, WRAP))
    inner.addView(SliderRow(this, L("پس‌زمینه تیره", "Background"), 0f, 100f, bgA * 100f, false, { Math.round(it).toString() + "%" }, { v -> bgA = v / 100f; restyle() }), lp(MATCH, WRAP))
    inner.addView(ColorRow(this, color) { c2 -> color = c2; restyle() }, lp(MATCH, WRAP))
    val fonts = Fonts.all(this)
    inner.addView(ChipRow(this, fonts.map { Fonts.displayName(it) + " ابپ" }, Math.max(0, fonts.indexOf(font)), fonts.map { Fonts.get(it, false, false) }) { i -> font = fonts[i]; restyle() }, lp(MATCH, WRAP))

    // saved favourite looks
    val prefs = getSharedPreferences("subtpl", 0)
    fun tpls(): org.json.JSONArray = try { org.json.JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { org.json.JSONArray() }
    inner.addView(ltCaption(L("قالب‌های موردعلاقه", "Saved styles")), lp(MATCH, WRAP))
    val ta = tpls()
    val tNames = ArrayList<String>()
    tNames.add(L("＋ ذخیرهٔ ظاهر فعلی", "+ Save current look"))
    for (i in 0 until ta.length()) tNames.add(ta.getJSONObject(i).optString("n"))
    val tRow = ChipRow(this, tNames, -1) { i ->
        if (i == 0) {
            Dialogs.input(this, L("نام قالب", "Style name"), L("قالب ", "Style ") + (ta.length() + 1)) { nm ->
                val o = org.json.JSONObject()
                o.put("n", nm.ifBlank { "style" }); o.put("size", size.toDouble()); o.put("cx", cx.toDouble()); o.put("cy", cy.toDouble())
                o.put("color", color); o.put("bg", bgA.toDouble()); o.put("font", font)
                val nl = tpls(); nl.put(o)
                prefs.edit().putString("list", nl.toString()).apply()
                toast(L("قالب ذخیره شد", "Style saved"))
                openSubtitlePanel()
            }
        } else {
            val o = ta.getJSONObject(i - 1)
            size = o.optDouble("size", 0.05).toFloat(); cx = o.optDouble("cx", 0.5).toFloat(); cy = o.optDouble("cy", 0.86).toFloat()
            color = o.optInt("color", Color.WHITE); bgA = o.optDouble("bg", 0.45).toFloat(); font = o.optString("font", "sans")
            restyle()
            openSubtitlePanel()
        }
    }
    inner.addView(tRow, lp(MATCH, WRAP))
    if (ta.length() > 0) {
        val dw = FrameLayout(this)
        dw.setPadding(dp(10), dp(2), dp(10), dp(2))
        dw.addView(ltPill(L("حذف همهٔ قالب‌ها", "Delete saved styles")) { prefs.edit().putString("list", "[]").apply(); openSubtitlePanel() }, FrameLayout.LayoutParams(WRAP, WRAP))
        inner.addView(dw, lp(MATCH, WRAP))
    }

    inner.addView(ltCaption(L("خط‌ها و زمان‌ها (ثانیه)", "Lines and times (seconds)")), lp(MATCH, WRAP))
    rowsHost = lin(true)
    inner.addView(rowsHost, lp(MATCH, WRAP))
    sv.addView(inner)
    col.addView(sv, lp(MATCH, dp(300)))
    content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    refreshRows()
    refreshTap()
    openPanel(ltBox(content), true)
}
