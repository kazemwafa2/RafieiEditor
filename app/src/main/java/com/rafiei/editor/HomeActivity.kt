package com.rafiei.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Start screen in the style of InShot: create new (video / photo / collage), what's new, drafts. */
class HomeActivity : Activity() {
    private lateinit var root: FrameLayout
    private var sheet: FrameLayout? = null
    private lateinit var draftsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFFFF7A45.toInt()
        window.navigationBarColor = C.BG2
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshDraftCount()
        if (sheet != null) showDrafts()
    }

    private fun build() {
        root = FrameLayout(this)
        root.setBackgroundColor(C.BG2)

        val col = lin(true)
        val head = FrameLayout(this)
        val title = tv(L("رفیعی ادیتور", "Rafiei Editor"), 22f, Color.WHITE, true)
        head.addView(title, fl(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START).also { it.leftMargin = dp(20) })
        val gear = IconView(this, "gear")
        gear.setOnClickListener { openSettings() }
        head.addView(gear, fl(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.END).also { it.rightMargin = dp(8) })

        // create-new card
        val card = lin(true)
        card.background = roundedBg(0xFFFFFFFF.toInt(), dpf(22f))
        card.setPadding(dp(12), dp(16), dp(12), dp(18))
        card.addView(tv(L("ساخت پروژه جدید", "CREATE NEW"), 12f, 0xFF8A8A8A.toInt(), true), lp(MATCH, dp(26)))
        val row = lin(false)
        row.addView(createBtn("video", L("ویدیو", "Video"), 0xFFFF6B4A.toInt(), 0xFFFF3D6E.toInt()) { newProject("video") }, lp(0, WRAP, 1f))
        row.addView(createBtn("photo", L("عکس", "Photo"), 0xFF4DA3FF.toInt(), 0xFF6B6BFF.toInt()) { newProject("photo") }, lp(0, WRAP, 1f))
        row.addView(createBtn("collage", L("کولاژ", "Collage"), 0xFF00C98D.toInt(), 0xFF00A8C8.toInt()) {
            startActivity(Intent(this, CollageActivity::class.java))
        }, lp(0, WRAP, 1f))
        card.addView(row, lp(MATCH, WRAP))
        val band = lin(true)
        band.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF7A45.toInt(), 0xFFFF4D6D.toInt()))
        band.addView(head, lp(MATCH, dp(56)))
        band.addView(card, lp(MATCH, WRAP).also { it.setMargins(dp(16), dp(6), dp(16), dp(26)) })
        col.addView(band, lp(MATCH, WRAP))

        // what's new
        val sv = ScrollView(this)
        val body = lin(true)
        body.setPadding(dp(16), dp(18), dp(16), dp(90))
        body.addView(tv(L("تازه‌ها", "What's new"), 16f, C.TXT, true, Gravity.START), lp(MATCH, dp(32)))
        val tiles = listOf(
            Triple("anim", L("انیمیشن ورود و خروج", "In / Out animations"), L("برای کلیپ، متن و استیکر", "For clips, text and stickers")),
            Triple("effect", L("۳۰ افکت و ۲۶ فیلتر", "30 effects, 26 filters"), L("لرزش، گلیچ، ذرات و ...", "Shake, glitch, particles…")),
            Triple("audiomusic", L("صدا، ضبط و افکت صوتی", "Audio, record, sound FX"), L("ضبط صدا و گفتار متن", "Voice record and AI speech")),
            Triple("speed", L("سرعت و فریز", "Speed and freeze"), L("برش، تقسیم و جابه‌جایی", "Trim, split and reorder"))
        )
        var i = 0
        while (i < tiles.size) {
            val r = lin(false)
            for (j in 0..1) {
                val t = tiles[i + j]
                r.addView(tile(t.first, t.second, t.third), lp(0, dp(110), 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            body.addView(r, lp(MATCH, WRAP))
            i += 2
        }
        sv.addView(body)
        col.addView(sv, lp(MATCH, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))

        // drafts strip at the bottom
        draftsLabel = tv("", 13f, Color.WHITE, true)
        val strip = FrameLayout(this)
        strip.background = roundedBg(0xFF3A3A3A.toInt(), dpf(26f))
        strip.addView(draftsLabel, fl(WRAP, WRAP, Gravity.CENTER))
        strip.setOnClickListener { showDrafts() }
        root.addView(strip, fl(MATCH, dp(52), Gravity.BOTTOM).also { it.setMargins(dp(16), 0, dp(16), dp(18)) })
        setContentView(root)
        refreshDraftCount()
    }

    private fun tile(icon: String, title: String, sub: String): View {
        val t = lin(true)
        t.background = gradBg(0xFF3A3A3A.toInt(), 0xFF2C2C2C.toInt(), dpf(16f))
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        t.gravity = Gravity.CENTER_VERTICAL
        t.addView(IconView(this, icon, C.ACCENT2), lp(dp(34), dp(34)))
        t.addView(tv(title, 13f, C.TXT, true, Gravity.START), lp(MATCH, WRAP).also { it.topMargin = dp(6) })
        t.addView(tv(sub, 11f, C.TXT2, false, Gravity.START), lp(MATCH, WRAP).also { it.topMargin = dp(2) })
        return t
    }

    private fun createBtn(icon: String, label: String, c1: Int, c2: Int, action: () -> Unit): View {
        val col = lin(true)
        col.gravity = Gravity.CENTER_HORIZONTAL
        val circle = FrameLayout(this)
        circle.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).also { it.shape = GradientDrawable.OVAL }
        val ic = IconView(this, icon, Color.WHITE)
        ic.padFrac = 0.22f
        circle.addView(ic, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(circle, lp(dp(68), dp(68)))
        col.addView(tv(label, 13f, 0xFF333333.toInt(), true), lp(WRAP, WRAP).also { it.topMargin = dp(8) })
        col.setOnClickListener { action() }
        return col
    }

    private fun newProject(type: String) {
        val p = Project()
        p.type = type
        Store.save(this, p)
        val i = Intent(this, EditorActivity::class.java)
        i.putExtra("pid", p.id)
        i.putExtra("pick", true)
        startActivity(i)
    }

    private fun openSettings() {
        Dialogs.choose(this, L("تنظیمات", "Settings"), listOf("فارسی", "English")) { idx ->
            Lang.set(this, idx == 0)
            recreate()
        }
    }

    private fun refreshDraftCount() {
        val n = Store.list(this).size
        draftsLabel.text = if (Lang.fa) "$n پیش‌نویس  ›" else "$n DRAFTS  ›"
    }

    // ------------------------------------------------------------ drafts sheet

    private fun hideDrafts() {
        sheet?.let { root.removeView(it) }
        sheet = null
    }

    private fun showDrafts() {
        hideDrafts()
        val s = FrameLayout(this)
        s.setBackgroundColor(0xAA000000.toInt())
        s.setOnClickListener { hideDrafts() }
        val panel = lin(true)
        panel.background = roundedBg(0xFF2A2A2A.toInt(), dpf(22f))
        panel.setPadding(dp(14), dp(14), dp(14), dp(14))
        panel.setOnClickListener { }
        val top = FrameLayout(this)
        top.addView(tv(L("پیش‌نویس‌ها", "DRAFTS"), 15f, Color.WHITE, true), fl(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START))
        val nw = tv(L("جدید", "NEW"), 13f, Color.BLACK, true)
        nw.background = roundedBg(C.GREEN, dpf(16f))
        nw.setPadding(dp(18), 0, dp(18), 0)
        nw.setOnClickListener { hideDrafts(); newProject("video") }
        top.addView(nw, fl(WRAP, dp(32), Gravity.CENTER_VERTICAL or Gravity.END))
        panel.addView(top, lp(MATCH, dp(40)))

        val list = Store.list(this)
        val sv = ScrollView(this)
        val lc = lin(true)
        if (list.isEmpty()) {
            lc.addView(tv(L("هنوز پیش‌نویسی ندارید.", "No drafts yet."), 13f, C.TXT2), lp(MATCH, dp(90)))
        }
        for (p in list) lc.addView(draftRow(p), lp(MATCH, dp(76)))
        sv.addView(lc)
        panel.addView(sv, lp(MATCH, WRAP))
        val ph = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        s.addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).also { it.setMargins(dp(10), 0, dp(10), dp(10)); it.height = FrameLayout.LayoutParams.WRAP_CONTENT })
        panel.minimumHeight = Math.min(ph, dp(200))
        root.addView(s, FrameLayout.LayoutParams(MATCH, MATCH))
        sheet = s
    }

    private fun draftRow(p: Project): View {
        val r = lin(false)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(4), 0, dp(4))
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = roundedBg(C.CARD, dpf(10f))
        img.clipToOutline = true
        img.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dpf(10f))
            }
        }
        r.addView(img, lp(dp(64), dp(64)))
        val f = Store.thumbFile(this, p.id)
        Bg.run {
            var b: Bitmap? = null
            if (f.exists()) b = BitmapFactory.decodeFile(f.absolutePath)
            if (b != null) Bg.ui { img.setImageBitmap(b) }
        }
        val info = lin(true)
        val nm = if (p.name.isEmpty()) L("پروژه", "Project") else p.name
        info.addView(tv(nm, 14f, Color.WHITE, true, Gravity.START), lp(MATCH, WRAP))
        val date = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(Date(p.modified))
        info.addView(tv(date, 11f, C.TXT2, false, Gravity.START), lp(MATCH, WRAP))
        val dur = if (p.type == "photo") L("عکس", "Photo") else fmtShort(p.totalMs())
        info.addView(tv(dur, 11f, C.TXT2, false, Gravity.START), lp(MATCH, WRAP))
        val il = lp(0, WRAP, 1f)
        il.leftMargin = dp(12)
        r.addView(info, il)
        val more = IconView(this, "more")
        more.setOnClickListener { draftMenu(p) }
        r.addView(more, lp(dp(44), dp(44)))
        r.setOnClickListener {
            val i = Intent(this, EditorActivity::class.java)
            i.putExtra("pid", p.id)
            startActivity(i)
        }
        return r
    }

    private fun draftMenu(p: Project) {
        Dialogs.choose(this, L("پیش‌نویس", "Draft"), listOf(L("تغییر نام", "Rename"), L("کپی", "Duplicate"), L("حذف", "Delete"))) { idx ->
            when (idx) {
                0 -> Dialogs.input(this, L("نام پروژه", "Project name"), p.name) { n ->
                    Store.rename(this, p.id, n)
                    showDrafts()
                }
                1 -> Bg.run {
                    Store.duplicate(this, p.id)
                    Bg.ui { showDrafts(); refreshDraftCount() }
                }
                2 -> Dialogs.confirm(this, L("حذف پیش‌نویس؟", "Delete draft?"), L("این کار قابل بازگشت نیست.", "This cannot be undone."), L("حذف", "Delete")) {
                    Store.delete(this, p.id)
                    showDrafts()
                    refreshDraftCount()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (sheet != null) hideDrafts() else super.onBackPressed()
    }
}
