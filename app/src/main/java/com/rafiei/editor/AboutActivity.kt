package com.rafiei.editor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * About screen (checklist section 12): app name, developer, version, copyright and the
 * contact buttons. The WhatsApp/Facebook links are the official ones given by the developer;
 * if WhatsApp is not installed the user is told so instead of a silent failure.
 */
class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFFFF7A45.toInt()
        window.navigationBarColor = C.BG2

        val root = FrameLayout(this)
        root.setBackgroundColor(C.BG2)
        val col = lin(true)
        col.setPadding(dp(20), dp(10), dp(20), dp(20))

        // header
        val head = FrameLayout(this)
        val back = IconView(this, "back")
        back.setOnClickListener { finish() }
        head.addView(back, fl(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.START))
        head.addView(tv(L("درباره ما", "About us"), 17f, C.TXT, true), fl(WRAP, WRAP, Gravity.CENTER))
        col.addView(head, lp(MATCH, dp(52)))

        val sv = ScrollView(this)
        val body = lin(true)
        body.setPadding(0, dp(8), 0, 0)
        body.gravity = Gravity.CENTER_HORIZONTAL

        // logo bubble
        val logo = FrameLayout(this)
        logo.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF7A45.toInt(), 0xFFFF4D6D.toInt())).also { it.shape = GradientDrawable.OVAL }
        val li = IconView(this, "video", Color.WHITE)
        li.padFrac = 0.28f
        logo.addView(li, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(logo, lp(dp(84), dp(84)).also { (it as LinearLayout.LayoutParams).topMargin = dp(10) })

        body.addView(tv(L("رفیعی ادیتور", "Rafiei Editor"), 22f, C.TXT, true), lp(WRAP, WRAP).also { (it as LinearLayout.LayoutParams).topMargin = dp(12) })
        body.addView(tv(L("ویرایشگر ویدیو و عکس — رایگان، بدون واترمارک و بدون تبلیغات", "Video and photo editor — free, no watermark, no ads"), 12.5f, C.TXT2), lp(WRAP, WRAP).also { (it as LinearLayout.LayoutParams).topMargin = dp(4) })

        // info card
        val card = lin(true)
        card.background = roundedBg(C.CARD, dpf(16f))
        card.setPadding(dp(16), dp(6), dp(16), dp(10))
        val v = try { packageManager.getPackageInfo(packageName, 0) } catch (e: Throwable) { null }
        val rows = listOf(
            L("توسعه‌دهنده", "Developer") to "Rafi'i (رفیعی)",
            L("نسخه", "Version") to ((v?.versionName ?: "2.1") + "  (" + (v?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode } ?: 3) + ")"),
            L("حق نشر", "Copyright") to "© 2026 Rafi'i",
            L("پلتفرم", "Platform") to L("اندروید ۷٫۰ تا ۱۳+", "Android 7.0 to 13+")
        )
        for ((k, vv) in rows) {
            val r = lin(false)
            r.setPadding(0, dp(7), 0, dp(7))
            r.addView(tv(k, 13f, C.TXT2, false, Gravity.START), lp(0, WRAP, 1f))
            r.addView(tv(vv, 13f, C.TXT, true, Gravity.END), lp(WRAP, WRAP))
            card.addView(r, lp(MATCH, WRAP))
        }
        val cardLp = lp(MATCH, WRAP)
        (cardLp as LinearLayout.LayoutParams).setMargins(0, dp(16), 0, 0)
        body.addView(card, cardLp)

        body.addView(tv(L("تماس با ما", "Contact us"), 15f, C.TXT, true, Gravity.START), lp(MATCH, WRAP).also { (it as LinearLayout.LayoutParams).topMargin = dp(18); (it as LinearLayout.LayoutParams).bottomMargin = dp(6) })

        contactBtn(body, "واتساپ / WhatsApp", "+93 770 230 338", 0xFF25D366.toInt()) { openWhatsApp() }
        contactBtn(body, "فیس‌بوک / Facebook", L("صفحه رسمی رفیعی", "Official Rafi'i page"), 0xFF1877F2.toInt()) { openUrl(Updater.FACEBOOK_URL) }
        val gh = Updater.repoUrl(this)
        if (gh.isNotEmpty()) contactBtn(body, "گیت‌هاب / GitHub", gh.substringAfter("https://"), 0xFFEDEDED.toInt()) { openUrl(gh) }

        body.addView(tv(L("برای سفارش تدوین، گزارش اشکال یا پیشنهاد ویژگی جدید، از دکمه‌های بالا استفاده کنید.", "For editing orders, bug reports or feature ideas, use the buttons above."), 11.5f, C.TXT2, false, Gravity.START), lp(MATCH, WRAP).also { (it as LinearLayout.LayoutParams).topMargin = dp(12) })

        // update check
        val up = tv(L("بررسی به‌روزرسانی برنامه", "Check for app updates"), 14f, C.TXT, true, Gravity.CENTER_VERTICAL or Gravity.START)
        up.background = roundedBg(C.CARD, dpf(14f))
        up.setPadding(dp(14), 0, dp(14), 0)
        up.setOnClickListener { Updater.check(this) }
        val upLp = lp(MATCH, dp(52))
        (upLp as LinearLayout.LayoutParams).setMargins(0, dp(16), 0, 0)
        body.addView(up, upLp)

        sv.addView(body)
        col.addView(sv, lp(MATCH, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    private fun contactBtn(parent: LinearLayout, title: String, sub: String, color: Int, action: () -> Unit) {
        val r = lin(false)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = roundedBg(C.CARD, dpf(14f))
        r.setPadding(dp(12), dp(8), dp(12), dp(8))
        val dot = View(this)
        dot.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, color)).also { it.shape = GradientDrawable.OVAL }
        r.addView(dot, lp(dp(34), dp(34)))
        val t = lin(true)
        t.addView(tv(title, 14f, C.TXT, true, Gravity.START), lp(MATCH, WRAP))
        t.addView(tv(sub, 11.5f, C.TXT2, false, Gravity.START), lp(MATCH, WRAP))
        val tl = lp(0, WRAP, 1f); (tl as LinearLayout.LayoutParams).leftMargin = dp(12)
        r.addView(t, tl)
        r.addView(tv("›", 20f, C.TXT2, true), lp(WRAP, WRAP))
        r.setOnClickListener { action() }
        val rl = lp(MATCH, WRAP); (rl as LinearLayout.LayoutParams).topMargin = dp(8)
        parent.addView(r, rl)
    }

    /** WhatsApp first tries the installed app; when it is missing the user is told clearly. */
    private fun openWhatsApp() {
        val pm = packageManager
        val installed = try { pm.getLaunchIntentForPackage("com.whatsapp") != null } catch (e: Throwable) { false }
        if (!installed) {
            Dialogs.message(this, L("واتساپ نصب نیست", "WhatsApp is not installed"),
                L("واتساپ روی این گوشی نصب نیست. می‌توانید شماره را یادداشت کنید: +93 770 230 338", "WhatsApp is not installed on this phone. You can note the number: +93 770 230 338"))
            return
        }
        openUrl(Updater.WHATSAPP_URL)
    }

    private fun openUrl(u: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, L("برنامه‌ای برای باز کردن این پیوند نیست.", "No app can open this link."), android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            android.widget.Toast.makeText(this, L("باز کردن پیوند ناموفق بود.", "Could not open the link."), android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
