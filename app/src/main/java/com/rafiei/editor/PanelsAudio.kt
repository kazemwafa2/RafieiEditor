package com.rafiei.editor

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Audio "Effects" panel: equaliser, noise reduction, speech enhancement and auto-ducking for the selected audio item. */
fun EditorActivity.openAudioFxPanel() {
    val a = selAudio()
    if (a == null) { toast(L("ابتدا یک صدا را در تایم‌لاین انتخاب کنید.", "Select an audio item first.")); return }
    val snap = proj.snapshot()
    val col = lin(true)
    col.setBackgroundColor(C.PANEL)
    val inner = lin(true)

    fun cap(t: String): TextView {
        val v = tv(t, 12f, C.TXT2, false, Gravity.START or Gravity.CENTER_VERTICAL)
        v.setPadding(dp(14), dp(6), dp(14), dp(2))
        return v
    }

    lateinit var rebuild: () -> Unit

    fun slider(label: String, mn: Float, mx: Float, v: Float, fmt: (Float) -> String, set: (Float) -> Unit) {
        var tmp = v
        inner.addView(SliderRow(this, label, mn, mx, v, mn < 0f, fmt, { x -> tmp = x }, { set(tmp); changed() }), lp(MATCH, dp(44)))
    }

    fun pill(text: String, active: Boolean, click: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { click() }
        return t
    }

    fun db(x: Float) = String.format(Locale.US, "%+.0f dB", x)
    fun pc(x: Float) = Math.round(x * 100f).toString() + "%"

    rebuild = {
        inner.removeAllViews()
        inner.addView(cap(L("حالت‌های آماده", "Presets")), lp(MATCH, WRAP))
        val names = listOf(L("طبیعی", "Flat"), L("گفتار شفاف", "Clear voice"), L("بیس قوی", "Bass boost"), L("روشن", "Bright"), L("تلفنی", "Telephone"))
        inner.addView(ChipRow(this, names, -1) { i ->
            when (i) {
                0 -> { a.eqL = 0f; a.eqM = 0f; a.eqH = 0f; a.voice = false; a.denoise = 0f }
                1 -> { a.eqL = -4f; a.eqM = 3f; a.eqH = 2f; a.voice = true; a.denoise = 0.4f }
                2 -> { a.eqL = 8f; a.eqM = -1f; a.eqH = 0f }
                3 -> { a.eqL = -2f; a.eqM = 1f; a.eqH = 7f }
                else -> { a.eqL = -12f; a.eqM = 6f; a.eqH = -8f }
            }
            changed()
            rebuild()
        }, lp(MATCH, dp(44)))
        inner.addView(cap(L("اکولایزر (تنظیم فرکانس‌ها)", "Equaliser")), lp(MATCH, WRAP))
        slider(L("بم (زیر ۲۰۰ هرتز)", "Bass"), -12f, 12f, a.eqL, ::db) { a.eqL = it }
        slider(L("میانه (حدود ۱.۴ کیلوهرتز)", "Mid"), -12f, 12f, a.eqM, ::db) { a.eqM = it }
        slider(L("زیر (بالای ۴.۵ کیلوهرتز)", "Treble"), -12f, 12f, a.eqH, ::db) { a.eqH = it }
        inner.addView(cap(L("گفتار", "Speech")), lp(MATCH, WRAP))
        slider(L("کاهش نویز پس‌زمینه", "Noise reduction"), 0f, 1f, a.denoise, ::pc) { a.denoise = it }
        val row = lin(false)
        row.setPadding(dp(10), dp(4), dp(10), dp(4))
        row.addView(pill((if (a.voice) "✓ " else "") + L("بهبود وضوح و بلندی گوینده", "Enhance voice clarity and loudness"), a.voice) { a.voice = !a.voice; changed(); rebuild() }, lp(MATCH, WRAP))
        inner.addView(row, lp(MATCH, WRAP))
        inner.addView(cap(L("تغییر صدا", "Voice changer")), lp(MATCH, WRAP))
        slider(L("زیر و بمی (نرمال = ۱.۰)", "Pitch (normal = 1.0)"), 0.6f, 1.6f, a.pitch, { String.format(Locale.US, "×%.2f", it) }) { a.pitch = it }
        inner.addView(cap(L("کاهش خودکار صدا هنگام صحبت", "Auto-duck while someone speaks")), lp(MATCH, WRAP))
        slider(L("میزان کاهش", "Ducking amount"), 0f, 0.9f, a.duck, ::pc) { a.duck = it; if (it > 0.01f) for (p in EqAudio.sourcesOf(proj)) EqAudio.ensure(p) }
        inner.addView(cap(L("کاهش صدا با گفتار داخل ویدیو یا صدای ضبط‌شده/گفتار تشخیص داده می‌شود.", "Speech is detected from the video's own audio and from voice/TTS tracks.")), lp(MATCH, WRAP))
    }
    rebuild()
    val sv = ScrollView(this)
    sv.addView(inner)
    col.addView(sv, lp(MATCH, dp(300)))
    openPanel(col, true, snap)
}
