package com.rafiei.editor

import android.app.Activity
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local, private language bank (words, poems, speech, songs) for low-resource languages such as Hazaragi.
 * Everything stays on the phone (filesDir/langbank.json); nothing is uploaded. Storing text does NOT train any model.
 */
object LangBank {
    val LANGS = listOf("hzg" to "هزارگی", "fa" to "فارسی", "prs" to "دری", "ps" to "پشتو", "ar" to "العربية", "en" to "English", "unk" to "نامشخص")
    val KINDS = listOf("word" to "واژه / اصطلاح", "poem" to "شعر", "speech" to "گفتار", "song" to "آواز")
    val SOURCES = listOf("typed" to "متن تایپ‌شده", "audio" to "فایل صوتی", "video" to "ویدیو", "fixed" to "متن اصلاح‌شده")

    class Entry(val id: String) {
        var ver = 1
        var lang = "unk"
        var kind = "word"
        var text = ""
        var source = "typed"
        var verified = false
        var consentLocal = true   // may be kept locally for future use
        var license = ""
        var variants = ""         // other spellings / regional pronunciations (free text)
        var created = System.currentTimeMillis()
        var updated = created
        val history = ArrayList<String>() // earlier texts, newest last

        fun toJson(): JSONObject {
            val o = JSONObject()
            o.put("id", id); o.put("ver", ver); o.put("lang", lang); o.put("kind", kind); o.put("text", text); o.put("src", source)
            o.put("ok", verified); o.put("consent", consentLocal); o.put("lic", license); o.put("var", variants)
            o.put("c", created); o.put("u", updated)
            val h = JSONArray(); for (s in history) h.put(s); o.put("hist", h)
            return o
        }
    }

    private fun file(c: android.content.Context) = File(c.filesDir, "langbank.json")

    fun load(c: android.content.Context): ArrayList<Entry> {
        val l = ArrayList<Entry>()
        try {
            val f = file(c)
            if (!f.exists()) return l
            val a = JSONArray(f.readText(Charsets.UTF_8))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val e = Entry(o.optString("id", uid()))
                e.ver = o.optInt("ver", 1); e.lang = o.optString("lang", "unk"); e.kind = o.optString("kind", "word"); e.text = o.optString("text")
                e.source = o.optString("src", "typed"); e.verified = o.optBoolean("ok"); e.consentLocal = o.optBoolean("consent", true)
                e.license = o.optString("lic"); e.variants = o.optString("var"); e.created = o.optLong("c"); e.updated = o.optLong("u")
                val h = o.optJSONArray("hist")
                if (h != null) for (k in 0 until h.length()) e.history.add(h.optString(k))
                l.add(e)
            }
        } catch (e: Throwable) {
        }
        return l
    }

    fun save(c: android.content.Context, l: List<Entry>) {
        try {
            val a = JSONArray()
            for (e in l) a.put(e.toJson())
            val tmp = File(c.filesDir, "langbank.json.tmp")
            tmp.writeText(a.toString(), Charsets.UTF_8)
            tmp.renameTo(file(c))
        } catch (e: Throwable) {
        }
    }

    fun nameOf(list: List<Pair<String, String>>, id: String) = list.firstOrNull { it.first == id }?.second ?: id

    /** Exports the bank to Downloads/RafieiEditor (a file the user can read, edit or delete). */
    fun export(c: android.content.Context, l: List<Entry>): String? {
        return try {
            val a = JSONArray()
            for (e in l) a.put(e.toJson())
            val name = "RafieiEditor_langbank_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".json"
            val v = ContentValues()
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RafieiEditor")
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { it.write(a.toString(2).toByteArray(Charsets.UTF_8)) }
            "Download/RafieiEditor/$name"
        } catch (e: Throwable) {
            null
        }
    }
}

/** Language bank screen: list, add, edit, verify, delete, export. */
fun Activity.openLangBank() {
    val ctx = applicationContext
    lateinit var show: () -> Unit

    fun editEntry(e: LangBank.Entry, all: ArrayList<LangBank.Entry>) {
        val items = listOf(
            L("ویرایش متن", "Edit text"), L("ویرایش شکل‌های دیگر نوشتن / تلفظ منطقه‌ای", "Edit variants / regional pronunciation"),
            L("تغییر زبان", "Change language"), L("تغییر نوع", "Change type"),
            (if (e.verified) L("برداشتن «تأیید شده»", "Unmark verified") else L("علامت «متن تأیید شده»", "Mark verified")),
            (if (e.consentLocal) L("لغو اجازهٔ نگهداری برای آینده", "Withdraw permission to keep") else L("اجازهٔ نگهداری محلی برای آینده", "Allow local keeping")),
            L("ثبت مجوز / محدودیت استفاده", "Set license / restriction"), L("حذف این نمونه", "Delete this sample")
        )
        Dialogs.choose(this, e.text.take(40), items) { i ->
            fun done() { e.updated = System.currentTimeMillis(); LangBank.save(ctx, all); show() }
            when (i) {
                0 -> Dialogs.input(this, L("متن", "Text"), e.text) { t -> if (t != e.text) { e.history.add(e.text); e.ver++; e.text = t }; done() }
                1 -> Dialogs.input(this, L("شکل‌های دیگر نوشتن / تلفظ", "Variants"), e.variants) { t -> e.variants = t; done() }
                2 -> Dialogs.choose(this, L("زبان یا گویش", "Language"), LangBank.LANGS.map { it.second }) { k -> e.lang = LangBank.LANGS[k].first; done() }
                3 -> Dialogs.choose(this, L("نوع", "Type"), LangBank.KINDS.map { it.second }) { k -> e.kind = LangBank.KINDS[k].first; done() }
                4 -> { e.verified = !e.verified; done() }
                5 -> { e.consentLocal = !e.consentLocal; done() }
                6 -> Dialogs.input(this, L("مجوز یا محدودیت", "License"), e.license) { t -> e.license = t; done() }
                7 -> Dialogs.confirm(this, L("حذف", "Delete"), L("این نمونه از گوشی حذف شود؟ (نسخه‌های پشتیبانِ قبلیِ ذخیره‌شده در Downloads جداگانه باید خودتان پاک شوند.)", "Delete this sample? Exported copies in Downloads must be deleted by you."), L("حذف", "Delete")) {
                    all.remove(e); LangBank.save(ctx, all); show()
                }
            }
        }
    }

    fun addEntry(all: ArrayList<LangBank.Entry>) {
        Dialogs.choose(this, L("زبان یا گویش (خودتان انتخاب کنید)", "Language"), LangBank.LANGS.map { it.second }) { li ->
            Dialogs.choose(this, L("نوع نمونه", "Type"), LangBank.KINDS.map { it.second }) { ki ->
                Dialogs.input(this, L("متن دقیق (همان‌طور که می‌نویسید ذخیره می‌شود)", "Exact text"), "") { t ->
                    if (t.isBlank()) return@input
                    Dialogs.choose(this, L("منبع نمونه", "Source"), LangBank.SOURCES.map { it.second }) { si ->
                        Dialogs.confirm(this, L("اجازهٔ نگهداری", "Permission"),
                            L("این نمونه فقط روی همین گوشی و به‌صورت خصوصی ذخیره می‌شود و به هیچ سرور یا مجموعهٔ مشترکی ارسال نمی‌شود. اجازه می‌دهید برای استفادهٔ آیندهٔ خودتان نگهداری شود؟", "Stored privately on this phone only; never uploaded. Keep it for future use?"),
                            L("اجازه می‌دهم", "Allow")) {
                            val e = LangBank.Entry(uid())
                            e.lang = LangBank.LANGS[li].first; e.kind = LangBank.KINDS[ki].first; e.text = t.trim(); e.source = LangBank.SOURCES[si].first
                            e.consentLocal = true
                            all.add(e); LangBank.save(ctx, all); show()
                        }
                    }
                }
            }
        }
    }

    show = {
        val all = LangBank.load(ctx)
        val items = ArrayList<String>()
        items.add(L("＋ افزودن نمونه (واژه، شعر، گفتار، آواز)", "+ Add sample"))
        for (e in all) {
            items.add((if (e.verified) "✔ " else "• ") + LangBank.nameOf(LangBank.LANGS, e.lang) + " · " + LangBank.nameOf(LangBank.KINDS, e.kind) + ": " + e.text.replace("\n", " ").take(36))
        }
        items.add(L("خروجی فایل JSON (پشتیبان)", "Export JSON backup"))
        items.add(L("حریم خصوصی و توضیح", "Privacy & notes"))
        Dialogs.choose(this, L("بانک زبان‌ها (${all.size} نمونه)", "Language bank (${all.size})"), items) { i ->
            when {
                i == 0 -> addEntry(all)
                i == items.size - 2 -> {
                    val where = LangBank.export(ctx, all)
                    Dialogs.message(this, L("خروجی", "Export"), if (where == null) L("ذخیره ناموفق بود.", "Export failed.") else L("ذخیره شد در: ", "Saved to: ") + where)
                }
                i == items.size - 1 -> Dialogs.message(this, L("حریم خصوصی", "Privacy"), L(
                    "• همهٔ نمونه‌ها فقط روی همین گوشی نگهداری می‌شوند و هیچ‌چیز ارسال نمی‌شود.\n• این برنامه از نمونه‌ها مدل هوش مصنوعی نمی‌سازد و ادعا نمی‌کند هزارگی را یاد گرفته است؛ ذخیرهٔ واژه و شعر فقط یک بانک دادهٔ شخصی (و پایه‌ای برای املا و اصلاح دستی در آینده) است.\n• صوت بدون متن دقیق و تأییدشده برای آموزش تشخیص گفتار کافی نیست.\n• برای فایل‌های حاوی صدای دیگران، رضایت و حقوق آن‌ها را رعایت کنید.\n• هر نمونه را می‌توانید ببینید، اصلاح کنید یا حذف کنید. حذف از برنامه، فایل‌های JSON قبلاً خروجی‌گرفته‌شده در Downloads را پاک نمی‌کند.",
                    "Everything stays on this phone. No AI model is trained from the samples. Samples can be viewed, edited and deleted."))
                else -> editEntry(all[i - 1], all)
            }
        }
    }
    show()
}
