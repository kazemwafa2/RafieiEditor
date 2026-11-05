package com.rafiei.editor

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Shows the picture and collects taps (instrument, right hand, left hand, face). */
private class HzTapView(ctx: Context, val bmp: Bitmap, val onTap: (Float, Float) -> Unit) : View(ctx) {
    val r = RectF()
    val pts = ArrayList<FloatArray>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(c: Canvas) {
        val s = Math.min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val w = bmp.width * s; val h = bmp.height * s
        r.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        c.drawBitmap(bmp, null, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.CYAN
        for (q in pts) c.drawCircle(r.left + q[0] * r.width(), r.top + q[1] * r.height(), 22f, p)
        p.style = Paint.Style.FILL
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && r.width() > 0f) {
            val x = ((e.x - r.left) / r.width()).coerceIn(0f, 1f)
            val y = ((e.y - r.top) / r.height()).coerceIn(0f, 1f)
            pts.add(floatArrayOf(x, y)); invalidate(); onTap(x, y)
        }
        return true
    }
}

fun EditorActivity.hzTab(o: Overlay, changed: () -> Unit, reshow: () -> Unit): View {
    val sv = ScrollView(this)
    val col = lin(true)
    sv.addView(col)
    if (Eq.cfgOf(o).optString("tpl", "") == "tvroom") return tvTab(o, changed, reshow)
    if (Eq.cfgOf(o).optString("tpl", "") != "hazaragi") {
        col.addView(tv(L("این تب فقط برای طرح «فانتزی هزارگی ۲» است؛ ابتدا همان طرح را در تب «طرح» انتخاب کنید.", "Pick the Hazaragi design first."), 12f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(12), dp(12), dp(12), dp(12)) })
        return sv
    }
    fun hz(): JSONObject = Eq.cfgOf(o).optJSONObject("hz") ?: JSONObject()
    fun put(vararg kv: Pair<String, Any?>) {
        val c = Eq.cfgOf(o)
        val h = c.optJSONObject("hz") ?: JSONObject()
        for ((k, v) in kv) { if (v == null) h.remove(k) else h.put(k, v) }
        c.put("hz", h)
        o.cfg = c.toString()
        changed()
    }
    val cfg = HzCfg(hz())
    fun pill(text: String, on: Boolean, f: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (on) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { f() }
        return t
    }
    fun cap(s: String) = col.addView(tv(s, 12f, C.TXT2).also { it.setPadding(dp(14), dp(10), dp(14), dp(2)) }, lp(MATCH, WRAP))
    fun sliders(g: String) {
        for (s in Hz.groups[g] ?: emptyList()) {
            val cur = HzCfg(hz()).f(s.id)
            col.addView(SliderRow(this, s.label, s.mn, s.mx, cur, false, { v -> (if (s.step < 1f) String.format("%.2f", v) else Math.round(v).toString()) + s.un }, { v ->
                val q = if (s.step >= 1f) Math.round(v).toDouble() else (Math.round(v / s.step) * s.step).toDouble()
                put(s.id to q)
            }), lp(MATCH, dp(52)))
        }
    }
    fun choice(title: String, key: String, items: List<Pair<String, String>>, def: String) {
        cap(title)
        val idx = items.indexOfFirst { it.first == cfg.s(key, def) }
        col.addView(ChipRow(this, items.map { it.second }, if (idx < 0) 0 else idx) { i -> put(key to items[i].first) }, lp(MATCH, dp(44)))
    }
    fun saveImages(uris: List<android.net.Uri>, cb: (List<String>) -> Unit) {
        val ctx = applicationContext
        val pid = proj.id
        Bg.run {
            val out = ArrayList<String>()
            for (u in uris) {
                val bmp = MediaUtil.loadBitmap(ctx, u, 1600) ?: continue
                val f = File(Store.mediaDir(ctx, pid), uid() + ".jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                out.add(android.net.Uri.fromFile(f).toString())
            }
            Bg.ui { if (out.isEmpty()) toast(L("تصویر خوانده نشد", "Could not read the image")) else cb(out) }
        }
    }
    fun pickOne(cb: (String) -> Unit) = pick(arrayOf("image/*"), false, EditorActivity.REQ_STICKER_IMG) { u -> if (u.isNotEmpty()) saveImages(u.take(1)) { cb(it[0]) } }

    // 1 character
    cap(L("۱. شخصیت", "1. Character"))
    val row = lin(false); row.setPadding(dp(8), dp(2), dp(8), dp(2))
    val rowS = android.widget.HorizontalScrollView(this); rowS.addView(row)
    for (p in Hz.PRESETS) row.addView(pill(p.label, cfg.charKey == p.key) {
        val h = hz(); Hz.applyPreset(h, p.key); h.remove("charUri")
        val c = Eq.cfgOf(o); c.put("hz", h); o.cfg = c.toString(); changed(); reshow()
    }, lp(WRAP, WRAP).also { it.rightMargin = dp(6) })
    row.addView(pill(L("🖼 تصویر دلخواه", "Own picture"), cfg.charKey == "custom") {
        pickOne { u ->
            val h = hz(); Hz.applyPreset(h, "custom"); h.put("charUri", u)
            val c = Eq.cfgOf(o); c.put("hz", h); o.cfg = c.toString(); changed(); reshow()
            toast(L("حالا «تنظیم محل ساز و دست‌ها» را بزنید.", "Now set the instrument and hands."))
        }
    }, lp(WRAP, WRAP))
    col.addView(rowS, lp(MATCH, WRAP))
    choice(L("نسبت تصویر", "Ratio"), "ratio", Hz.RATIOS, "9:16")
    col.addView(pill(L("✋ تنظیم محل ساز، دست‌ها و صورت (۴ لمس)", "Set instrument, hands, face"), false) {
        val c0 = HzCfg(hz())
        val bmp = if (c0.charKey == "custom" && c0.uri("charUri").isNotEmpty()) BmpCache.get(c0.uri("charUri"), 1200) else EqAssets.bmp((Hz.preset(c0.charKey) ?: Hz.PRESETS[0]).asset)
        if (bmp == null) { toast(L("تصویر باز نشد", "Image failed")); return@pill }
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val box = FrameLayout(this)
        val info = tv("", 14f, Color.YELLOW, true, Gravity.CENTER)
        val steps = listOf("۱) مرکز بدنهٔ ساز را لمس کنید", "۲) دست راست (نوازنده) را لمس کنید", "۳) دست چپ (روی دسته) را لمس کنید", "۴) مرکز صورت را لمس کنید")
        val got = ArrayList<FloatArray>()
        info.text = steps[0]
        val tapV = HzTapView(this, bmp) { x, y ->
            got.add(floatArrayOf(x, y))
            if (got.size >= 4) {
                put("ix" to got[0][0].toDouble(), "iy" to got[0][1].toDouble(), "sx" to got[1][0].toDouble(), "sy" to got[1][1].toDouble(),
                    "fx" to got[2][0].toDouble(), "fy" to got[2][1].toDouble(), "faceX" to got[3][0].toDouble(), "faceY" to got[3][1].toDouble())
                d.dismiss()
            } else info.text = steps[got.size]
        }
        box.addView(tapV, FrameLayout.LayoutParams(MATCH, MATCH))
        box.addView(info, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).also { it.topMargin = dp(24) })
        d.setContentView(box); d.show()
    }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(6), dp(10), dp(4)) })
    sliders("char")
    choice(L("روش ترکیب شخصیت با پس‌زمینه", "Blend"), "blend", Hz.BLENDS, "lighten")
    choice(L("اندازه‌گیری", "Fit"), "fit", Hz.FITS, if (cfg.s("ratio", "9:16") == "9:16") "cover" else "contain")

    // 2 background photos
    cap(L("۲. عکس‌های پس‌زمینه (تا ۱۲ عکس)", "2. Background photos"))
    val bgs = cfg.bgList()
    col.addView(tv(L("تعداد عکس‌ها: ", "Photos: ") + bgs.size, 12f, C.TXT), lp(MATCH, WRAP).also { it.setMargins(dp(14), 0, dp(14), 0) })
    val r2 = lin(false); r2.setPadding(dp(8), dp(4), dp(8), dp(4))
    r2.addView(pill(L("🌄 افزودن عکس", "Add photos"), false) {
        pick(arrayOf("image/*"), true, EditorActivity.REQ_STICKER_IMG) { u ->
            if (u.isNotEmpty()) saveImages(u.take(12 - bgs.size)) { n ->
                val a = JSONArray(); for (b in bgs) a.put(b); for (b in n) a.put(b)
                put("bgs" to a); reshow()
            }
        }
    }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
    r2.addView(pill(L("حذف همه", "Remove all"), false) { put("bgs" to null); reshow() }, lp(0, WRAP, 1f))
    col.addView(r2, lp(MATCH, WRAP))
    sliders("bg"); sliders("bgtint")
    // 3 toggles
    cap(L("۳. گزینه‌ها", "3. Options"))
    for ((k, _) in Hz.CHK) {
        val on = cfg.b(k)
        col.addView(pill((if (on) "✓ " else "") + (Hz.CHK_LABELS[k] ?: k), on) { put(k to !on); reshow() }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(3), dp(10), dp(3)) })
    }
    // 4 light + move + fx + wm + camera
    choice(L("رنگ نور", "Light colour"), "hue", Hz.HUES, "mix")
    cap(L("۴. نور و موسیقی", "4. Light")); sliders("light")
    cap(L("۵. پروانه‌ها و حرکت", "5. Movement")); sliders("move")
    cap(L("۶. اکولایزر", "6. Equalizer")); sliders("fx")
    cap(L("۷. نوشته‌ی متحرک (متن از تب «نوشته»، رنگ از تب «رنگ»)", "7. Moving text")); sliders("wm")
    cap(L("۸. دوربین", "8. Camera"))
    choice(L("نوع حرکت دوربین", "Camera"), "camMode", Hz.CAM_MODES, "orbit360")
    sliders("cam")
    // 9 extra angles
    cap(L("۹. زاویه‌های دیگر شخصیت (هم‌اندازهٔ عکس اصلی)", "9. Extra angles"))
    val r3 = lin(false); r3.setPadding(dp(8), dp(4), dp(8), dp(4))
    for ((k, n) in listOf("viewR" to "➡ راست", "viewB" to "⬆ پشت", "viewL" to "⬅ چپ")) {
        r3.addView(pill((if (cfg.uri(k).isNotEmpty()) "✓ " else "") + n, cfg.uri(k).isNotEmpty()) { pickOne { u -> put(k to u); reshow() } }, lp(0, WRAP, 1f).also { it.rightMargin = dp(4) })
    }
    col.addView(r3, lp(MATCH, WRAP))
    col.addView(pill(L("حذف زاویه‌های اضافه", "Remove angles"), false) { put("viewR" to null, "viewB" to null, "viewL" to null); reshow() }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(4), dp(10), dp(4)) })
    col.addView(pill(L("بازگشت همهٔ تنظیمات هزارگی به حالت اول", "Reset all"), false) {
        val c = Eq.cfgOf(o); c.remove("hz"); o.cfg = c.toString(); changed(); reshow()
    }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(4), dp(10), dp(12)) })
    return sv
}

private fun EditorActivity.tvTab(o: Overlay, changed: () -> Unit, reshow: () -> Unit): View {
    val sv = ScrollView(this)
    val col = lin(true)
    sv.addView(col)
    fun tvj(): JSONObject = Eq.cfgOf(o).optJSONObject("tv") ?: JSONObject()
    fun put(k: String, v: Any?) {
        val c = Eq.cfgOf(o)
        val h = c.optJSONObject("tv") ?: JSONObject()
        if (v == null) h.remove(k) else h.put(k, v)
        c.put("tv", h)
        o.cfg = c.toString()
        changed()
    }
    fun pill(text: String, on: Boolean, f: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (on) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { f() }
        return t
    }
    val cur = tvj()
    col.addView(tv(L("فیلم یا عکس داخل تلویزیون (مثلاً خیابان). فیلم خودتان (آدم جلو) همان کلیپ اصلی تایم‌لاین است و اندازه و جای آن را با ابزار خود کلیپ تنظیم کنید.", "TV content"), 12f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(12), dp(10), dp(12), dp(6)) })
    val r = lin(false); r.setPadding(dp(8), dp(4), dp(8), dp(4))
    r.addView(pill(if (cur.optString("uri", "").isEmpty()) L("🎞 انتخاب فیلم/عکس تلویزیون", "Choose TV media") else L("✓ تغییر فیلم/عکس تلویزیون", "Change"), cur.optString("uri", "").isNotEmpty()) {
        pick(arrayOf("video/*", "image/*"), false, EditorActivity.REQ_PIP) { u -> if (u.isNotEmpty()) { put("uri", u[0].toString()); reshow() } }
    }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
    r.addView(pill(L("حذف", "Remove"), false) { put("uri", null); reshow() }, lp(0, WRAP, 1f))
    col.addView(r, lp(MATCH, WRAP))
    col.addView(SliderRow(this, L("اندازه تلویزیون", "TV size"), 40f, 170f, cur.optDouble("s", 100.0).toFloat(), false, { Math.round(it).toString() + "%" }, { v -> put("s", Math.round(v).toDouble()) }), lp(MATCH, dp(52)))
    col.addView(SliderRow(this, L("جابه‌جایی افقی", "X"), -50f, 50f, cur.optDouble("x", 0.0).toFloat(), true, { Math.round(it).toString() }, { v -> put("x", Math.round(v).toDouble()) }), lp(MATCH, dp(52)))
    col.addView(SliderRow(this, L("جابه‌جایی عمودی", "Y"), -50f, 50f, cur.optDouble("y", 0.0).toFloat(), true, { Math.round(it).toString() }, { v -> put("y", Math.round(v).toDouble()) }), lp(MATCH, dp(52)))
    val loop = cur.optBoolean("loop", true)
    col.addView(pill((if (loop) "✓ " else "") + L("تکرار فیلم تلویزیون تا پایان", "Loop"), loop) { put("loop", !loop); reshow() }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(6), dp(10), dp(3)) })
    val bars = cur.optBoolean("bars", true)
    col.addView(pill((if (bars) "✓ " else "") + L("نوارهای اکولایزر روی تلویزیون", "Bars on TV"), bars) { put("bars", !bars); reshow() }, lp(MATCH, WRAP).also { it.setMargins(dp(10), dp(3), dp(10), dp(3)) })
    col.addView(tv(L("صدای فیلم تلویزیون پخش نمی‌شود؛ صدا همان صدای کلیپ اصلی است.", "TV audio is not played."), 11f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(12), dp(8), dp(12), dp(8)) })
    return sv
}
