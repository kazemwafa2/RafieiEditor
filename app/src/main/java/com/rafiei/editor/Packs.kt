package com.rafiei.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Optional packs (fonts). Nothing is downloaded unless the user starts it and confirms the size.
 * Downloads can be cancelled and resumed (".part" file + HTTP Range); a pack is installed only after the
 * file is complete, matches the optional SHA-256 and loads as a font.
 */
object Packs {
    class Item(val name: String, val url: String, val size: Long, val sha256: String, val ver: String)

    @Volatile var cancel = false

    fun catalogUrl(c: Context): String = c.getSharedPreferences("packs", 0).getString("catalog", "") ?: ""
    fun setCatalogUrl(c: Context, u: String) { c.getSharedPreferences("packs", 0).edit().putString("catalog", u).apply() }

    private fun open(u: String, from: Long): HttpURLConnection {
        val cn = URL(u).openConnection() as HttpURLConnection
        cn.connectTimeout = 15000; cn.readTimeout = 20000
        cn.instanceFollowRedirects = true
        if (from > 0) cn.setRequestProperty("Range", "bytes=$from-")
        return cn
    }

    fun sizeOf(u: String): Long = try {
        val cn = open(u, 0); cn.requestMethod = "HEAD"; val n = cn.contentLengthLong; cn.disconnect(); n
    } catch (e: Throwable) { -1L }

    /** Reads a catalog: JSON array of {name,url,size,sha256,ver}; or treats a direct link as a single item. */
    fun readCatalog(u: String): List<Item>? {
        return try {
            if (u.lowercase().endsWith(".ttf") || u.lowercase().endsWith(".otf")) {
                return listOf(Item(u.substringAfterLast('/'), u, sizeOf(u), "", ""))
            }
            val cn = open(u, 0)
            val txt = cn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            cn.disconnect()
            val a = JSONArray(txt)
            val l = ArrayList<Item>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                l.add(Item(o.optString("name"), o.optString("url"), o.optLong("size", -1), o.optString("sha256"), o.optString("ver")))
            }
            l
        } catch (e: Throwable) { null }
    }

    /** Returns null on success or a message. Resumes an earlier partial file. */
    fun download(c: Context, it: Item, progress: (Long, Long) -> Unit): String? {
        val dir = Fonts.fontsDir(c)
        val fname = (if (it.name.lowercase().endsWith(".ttf") || it.name.lowercase().endsWith(".otf")) it.name else it.name + ".ttf").replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
        val part = File(c.cacheDir, "pack_" + Integer.toHexString(it.url.hashCode()) + ".part")
        cancel = false
        try {
            var have = if (part.exists()) part.length() else 0L
            val cn = open(it.url, have)
            val code = cn.responseCode
            if (code == 200 && have > 0) { part.delete(); have = 0L }
            else if (code != 200 && code != 206) { cn.disconnect(); return "HTTP $code" }
            val total = if (cn.contentLengthLong > 0) cn.contentLengthLong + have else it.size
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(have)
                cn.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancel) return "cancelled"
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        have += n
                        progress(have, total)
                    }
                }
            }
            cn.disconnect()
            if (total > 0 && part.length() != total) return "incomplete"
            if (it.sha256.isNotBlank()) {
                val md = MessageDigest.getInstance("SHA-256")
                part.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
                val h = md.digest().joinToString("") { b -> String.format("%02x", b) }
                if (!h.equals(it.sha256.trim(), true)) { part.delete(); return "checksum" }
            }
            try { Typeface.createFromFile(part) } catch (e: Throwable) { part.delete(); return "not a font" }
            dir.mkdirs()
            val dst = File(dir, fname)
            if (!part.renameTo(dst)) { part.copyTo(dst, true); part.delete() }
            return null
        } catch (e: Throwable) {
            return "network"
        }
    }
}

/** Pack manager: installed fonts (remove) and optional downloads. */
fun Activity.openPackManager() {
    val ctx = applicationContext
    val installed = Fonts.imported(ctx)
    val items = ArrayList<String>()
    items.add(L("دانلود فونت (با اجازهٔ شما)", "Download a font (with your permission)"))
    items.add(L("تنظیم آدرس فهرست بسته‌ها", "Set pack catalog address"))
    for (f in installed) items.add(L("حذف فونت: ", "Remove font: ") + Fonts.displayName(f))
    Dialogs.choose(this, L("بسته‌ها (فونت)", "Packs (fonts)") + " — " + installed.size, items) { i ->
        when (i) {
            0 -> {
                val cat = Packs.catalogUrl(ctx)
                Dialogs.input(this, L("آدرس فهرست JSON یا لینک مستقیم فونت (.ttf/.otf)", "Catalog JSON or direct font link"), cat) { u ->
                    if (u.isBlank()) return@input
                    Packs.setCatalogUrl(ctx, u.trim())
                    val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage(L("در حال خواندن فهرست…", "Reading catalog…")).setCancelable(false).create()
                    dlg.show()
                    Bg.run {
                        val l = Packs.readCatalog(u.trim())
                        Bg.ui {
                            dlg.dismiss()
                            if (l == null || l.isEmpty()) {
                                Dialogs.message(this, L("بسته‌ها", "Packs"), L("فهرست خوانده نشد. اینترنت و آدرس را بررسی کنید. برنامه بدون اینترنت هم کامل کار می‌کند.", "Could not read the catalog. The app works fully offline anyway."))
                            } else {
                                Dialogs.choose(this, L("یک بسته انتخاب کنید", "Choose a pack"), l.map { it.name + (if (it.ver.isNotBlank()) " v" + it.ver else "") + (if (it.size > 0) "  (" + String.format(java.util.Locale.US, "%.1f", it.size / 1048576f) + " MB)" else "") }) { k ->
                                    val p = l[k]
                                    val free = ctx.filesDir.usableSpace
                                    if (p.size > 0 && free < p.size * 2) { Dialogs.message(this, L("فضا", "Space"), L("فضای خالی کافی نیست.", "Not enough free space.")); return@choose }
                                    Dialogs.confirm(this, L("دانلود", "Download"), p.name + "\n" + L("کاربرد: فونت زیرنویس و متن", "Use: subtitle/text font") + "\n" + L("حجم: ", "Size: ") + (if (p.size > 0) String.format(java.util.Locale.US, "%.1f MB", p.size / 1048576f) else L("نامشخص", "unknown")) + "\n" + L("منبع: ", "Source: ") + p.url + "\n" + L("دانلود فقط با اجازهٔ شما انجام می‌شود و می‌توانید لغو کنید؛ دفعهٔ بعد از همان‌جا ادامه می‌یابد.", "Only with your permission; cancel any time and resume later."), L("دانلود", "Download")) {
                                        val d2 = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage(L("در حال دانلود…", "Downloading…"))
                                            .setCancelable(false).setNegativeButton(L("لغو", "Cancel")) { _, _ -> Packs.cancel = true }.create()
                                        d2.show()
                                        Bg.run {
                                            val err = Packs.download(ctx, p) { a, b -> Bg.ui { d2.setMessage(L("در حال دانلود… ", "Downloading… ") + (a / 1024) + " KB" + (if (b > 0) " / " + (b / 1024) + " KB" else "")) } }
                                            Bg.ui {
                                                d2.dismiss()
                                                Dialogs.message(this, L("بسته‌ها", "Packs"), if (err == null) L("نصب شد. در انتخاب فونت دیده می‌شود.", "Installed. It shows in the font list.")
                                                else if (err == "cancelled") L("متوقف شد؛ دفعهٔ بعد ادامه می‌یابد. هیچ فایل ناقصی نصب نشد.", "Paused; it will resume next time. Nothing partial was installed.")
                                                else L("نصب نشد (" + err + "). هیچ فایل ناقص یا خرابی نصب نشد و پروژه‌ها سالم‌اند.", "Not installed (" + err + "). Nothing broken was installed; projects are safe."))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> Dialogs.input(this, L("آدرس فهرست بسته‌ها", "Catalog address"), Packs.catalogUrl(ctx)) { u -> Packs.setCatalogUrl(ctx, u.trim()) }
            else -> {
                val f = installed[i - 2]
                Dialogs.confirm(this, L("حذف فونت", "Remove font"), L("پروژه‌هایی که از آن استفاده می‌کنند با فونت ساده نمایش داده می‌شوند و خراب نمی‌شوند.", "Projects using it fall back to the default font."), L("حذف", "Remove")) {
                    java.io.File(Fonts.fontsDir(ctx), f.substring(5)).delete()
                }
            }
        }
    }
}
