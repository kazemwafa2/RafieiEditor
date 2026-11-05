package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Background removal of the selected clip (video or photo): the person stays, the rest is replaced. */
fun EditorActivity.openCutPanel() {
    val c = targetClip() ?: return
    val snap = proj.snapshot()
    val j = try { if (c.cut.isEmpty()) JSONObject() else JSONObject(c.cut) } catch (e: Exception) { JSONObject() }
    val root = lin(true)
    root.setBackgroundColor(C.PANEL)
    val holder = lin(true)

    fun push(on: Boolean = true) {
        c.cut = if (on) j.toString() else ""
        if (on) Seg.retry(c) // a previously failed attempt must be retriable
        Seg.request(c)
        stage.top.invalidate()
        engine.refresh()
        changed()
    }
    fun pill(text: String, active: Boolean, f: () -> Unit): TextView {
        val t = tv(text, 12.5f, Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.background = roundedBg(if (active) C.ACCENT else C.CARD, dpf(16f))
        t.setOnClickListener { f() }
        return t
    }
    lateinit var build: () -> Unit
    build = {
        holder.removeAllViews()
        val on = c.cut.isNotEmpty()
        holder.addView(tv(L("شخص با هوش مصنوعی روی گوشی جدا می‌شود (آفلاین) و بقیه با رنگ، گرادیان یا عکس عوض می‌شود. بار اول چند ثانیه طول می‌کشد.", "Person is cut out on-device; the rest is replaced."), 11.5f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(12), dp(8), dp(12), dp(4)) })
        val r = lin(false); r.setPadding(dp(8), dp(4), dp(8), dp(4))
        r.addView(pill(if (on) L("✓ روشن", "On") else L("روشن کردن", "Turn on"), on) { if (!on) { j.put("m", "color"); push() } else { push(false) }; build() }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
        val m = j.optString("m", "color")
        r.addView(pill(L("رنگ", "Colour"), on && m == "color") { j.put("m", "color"); push(); build() }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
        r.addView(pill(L("گرادیان", "Gradient"), on && m == "grad") { j.put("m", "grad"); push(); build() }, lp(0, WRAP, 1f).also { it.rightMargin = dp(6) })
        r.addView(pill(L("عکس", "Picture"), on && m == "image") {
            pick(arrayOf("image/*"), false, EditorActivity.REQ_STICKER_IMG) { us ->
                if (us.isNotEmpty()) {
                    val ctx = applicationContext; val pid = proj.id
                    Bg.run {
                        val bmp = MediaUtil.loadBitmap(ctx, us[0], 1600)
                        var uri = ""
                        if (bmp != null) {
                            val f = File(Store.mediaDir(ctx, pid), uid() + ".jpg")
                            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            uri = android.net.Uri.fromFile(f).toString()
                        }
                        Bg.ui { if (uri.isEmpty()) toast(L("تصویر خوانده نشد", "Could not read")) else { j.put("m", "image"); j.put("uri", uri); push(); build() } }
                    }
                }
            }
        }, lp(0, WRAP, 1f))
        holder.addView(r, lp(MATCH, WRAP))
        if (on && m != "image") {
            holder.addView(tv(L("رنگ اول", "Colour 1"), 12f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(14), dp(4), dp(14), 0) })
            holder.addView(ColorRow(this, j.optInt("c1", 0xFF00B140.toInt())) { v -> j.put("c1", v or 0xFF000000.toInt()); push() }, lp(MATCH, dp(44)))
            if (m == "grad") {
                holder.addView(tv(L("رنگ دوم", "Colour 2"), 12f, C.TXT2), lp(MATCH, WRAP).also { it.setMargins(dp(14), dp(4), dp(14), 0) })
                holder.addView(ColorRow(this, j.optInt("c2", 0xFF0A1030.toInt())) { v -> j.put("c2", v or 0xFF000000.toInt()); push() }, lp(MATCH, dp(44)))
            }
        }
    }
    build()
    root.addView(holder, lp(MATCH, WRAP))
    openPanel(root, false, snap)
}
