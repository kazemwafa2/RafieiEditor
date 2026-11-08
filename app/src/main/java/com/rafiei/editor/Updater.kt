package com.rafiei.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update checker for the GitHub release of this app (checklist section 8).
 *
 * How it works — everything on the user's explicit action, nothing automatic:
 *  1. The repository is read from assets/repo.txt as one line:  owner/repository
 *     (after publishing the project on GitHub, put the real value there; until then the
 *     checker reports honestly that the repository is not configured yet).
 *  2. It asks https://api.github.com/repos/<repo>/releases/latest for tag, changelog, date
 *     and the attached .apk asset.
 *  3. If the release version is newer than the installed one, the user sees version, date
 *     and the changelog and can start the download (with progress and cancel).
 *  4. After download the APK file is checked (size + it must be a readable package) and the
 *     signing certificate is compared with the installed app. Same signature = the normal
 *     Android update flow, which keeps all user data (projects, drafts, settings).
 *     A different signature is warned about clearly BEFORE installing.
 *  5. The installation itself is the standard system installer — user data is never touched
 *     by this app.
 */
object Updater {
    private const val VERSION = "2.1" // keep in sync with versionName in app/build.gradle
    const val WHATSAPP_URL = "https://wa.me/93770230338" // +93 770 230 338
    const val FACEBOOK_URL = "https://www.facebook.com/share/19wCpvTPeu/"

    class Release(val version: String, val title: String, val notes: String, val date: String, val apkUrl: String, val apkName: String, val apkSize: Long)

    /** Repository from assets/repo.txt, or "" while the project is not published yet. */
    fun repo(c: Context): String {
        return try {
            val txt = c.assets.open("repo.txt").bufferedReader().readText().trim()
            if (txt.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) && !txt.contains("YOUR", true)) txt else ""
        } catch (e: Throwable) { "" }
    }

    fun repoUrl(c: Context): String {
        val r = repo(c)
        return if (r.isEmpty()) "" else "https://github.com/$r"
    }

    /** Installed version, e.g. "2.1". */
    fun current(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: VERSION
    } catch (e: Throwable) { VERSION }

    /** true when [remote] is strictly newer than [local] (numeric segment comparison). */
    fun isNewer(local: String, remote: String): Boolean {
        fun segs(v: String) = v.replace(Regex("[^0-9.]"), "").split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val a = segs(local); val b = segs(remote)
        for (i in 0 until Math.max(a.size, b.size)) {
            val x = if (i < a.size) a[i] else 0
            val y = if (i < b.size) b[i] else 0
            if (y != x) return y > x
        }
        return false
    }

    /** Fetches the latest release. Returns the Release or throws with a readable message. */
    fun fetchLatest(repo: String): Release {
        val cn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        cn.connectTimeout = 15000; cn.readTimeout = 20000
        cn.setRequestProperty("Accept", "application/vnd.github+json")
        cn.setRequestProperty("User-Agent", "RafieiEditor")
        try {
            val code = cn.responseCode
            if (code == 404) throw Exception("404")
            if (code != 200) throw Exception("HTTP $code")
            val o = JSONObject(cn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            var apkUrl = ""; var apkName = ""; var apkSize = -1L
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val nm = a.optString("name", "")
                    if (nm == "RafieiEditor.apk") {
                        apkUrl = "https://github.com/$repo/releases/latest/download/RafieiEditor.apk"
                        apkName = nm
                        apkSize = a.optLong("size", -1L)
                        break
                    }
                }
            }
            return Release(
                o.optString("tag_name", "").removePrefix("v").removePrefix("V"),
                o.optString("name", ""),
                o.optString("body", ""),
                o.optString("published_at", "").take(10),
                apkUrl, apkName, apkSize
            )
        } finally {
            cn.disconnect()
        }
    }

    @Volatile var cancel = false

    /** Downloads the APK into the cache directory. Returns the file or null (cancelled/failed; err holds the reason). */
    fun downloadApk(url: String, size: Long, dst: File, progress: (Int) -> Unit): String? {
        cancel = false
        var cn: HttpURLConnection? = null
        try {
            cn = URL(url).openConnection() as HttpURLConnection
            cn.connectTimeout = 20000; cn.readTimeout = 30000
            cn.instanceFollowRedirects = true
            if (cn.responseCode != 200) return "HTTP ${cn.responseCode}"
            val total = if (cn.contentLengthLong > 0) cn.contentLengthLong else size
            var have = 0L
            cn.inputStream.use { ins ->
                java.io.FileOutputStream(dst).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        if (cancel) { dst.delete(); return "cancelled" }
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        if (total > 0) progress((have * 100L / total).toInt())
                    }
                }
            }
            if (total > 0 && dst.length() != total) { dst.delete(); return "incomplete" }
            return null
        } catch (e: Throwable) {
            dst.delete()
            return "network"
        } finally {
            try { cn?.disconnect() } catch (e: Throwable) {}
        }
    }

    /**
     * Verifies the downloaded APK: it must parse as a package, and its signing certificate
     * must match the installed app (otherwise Android would demand an uninstall first —
     * the user is told that before anything happens, never after).
     */
    fun signatureMatches(c: Context, apkPath: String): Boolean? {
        val pm = c.packageManager
        return try {
            val installed = pm.getPackageInfo(c.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            val archive = pm.getPackageArchiveInfo(apkPath, android.content.pm.PackageManager.GET_SIGNATURES)
            if (archive == null || archive.signatures.isNullOrEmpty()) null
            else archive.signatures[0].toCharsString() == installed.signatures[0].toCharsString()
        } catch (e: Throwable) { null }
    }

    /** Opens the system installer for the downloaded file through FileProvider. */
    fun install(a: Activity, f: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(a, "com.rafiei.editor.files", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(i)
        } catch (e: Throwable) {
            android.widget.Toast.makeText(a, L("اجرای نصب‌کننده ناموفق بود.", "Could not start the installer."), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Entry point: runs the whole check flow from [a]. Network on a background thread, UI on main. */
    fun check(a: Activity) {
        val r = repo(a)
        if (r.isEmpty()) {
            Dialogs.message(a, L("بررسی به‌روزرسانی", "Check for update"),
                L("مخزن گیت‌هاب هنوز تنظیم نشده است.\nبعد از انتشار پروژه، آدرس مخزن را در فایل assets/repo.txt بنویسید (یک خط: owner/repository).", "The GitHub repository is not configured yet.\nAfter publishing the project, write the repository into assets/repo.txt (one line: owner/repository)."))
            return
        }
        val dlg = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(L("در حال بررسی نسخه جدید…", "Checking for a new version…")).setCancelable(false).create()
        dlg.show()
        val ctx = a
        Bg.run {
            val rel: Release? = try { fetchLatest(r) } catch (e: Throwable) { null }
            Bg.ui {
                try { dlg.dismiss() } catch (e: Throwable) {}
                if (ctx.isFinishing || ctx.isDestroyed) return@ui
                when {
                    rel == null -> Dialogs.message(ctx, L("بررسی به‌روزرسانی", "Check for update"),
                        L("اتصال به گیت‌هاب ناموفق بود. اینترنت را بررسی کنید.", "Could not reach GitHub. Check the connection."))
                    rel.apkUrl.isEmpty() -> Dialogs.message(ctx, L("بررسی به‌روزرسانی", "Check for update"),
                        L("در آخرین انتشار گیت‌هاب فایل RafieiEditor.apk پیوست نشده بود.", "The latest GitHub release has no RafieiEditor.apk attached."))
                    !isNewer(current(ctx), rel.version) -> Dialogs.message(ctx, L("بررسی به‌روزرسانی", "Check for update"),
                        L("شما آخرین نسخه را دارید (", "You already have the latest version (") + current(ctx) + ").")
                    else -> offer(ctx, rel)
                }
            }
        }
    }

    private fun offer(a: Activity, rel: Release) {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(20), a.dp(10), a.dp(20), a.dp(4))
        col.addView(a.tv("v" + rel.version + "  •  " + rel.date, 15f, C.TXT, true), lp(WRAP, WRAP))
        if (rel.title.isNotBlank()) col.addView(a.tv(rel.title, 13f, C.ACCENT2, true), lp(WRAP, WRAP))
        val notes = a.tv(if (rel.notes.isBlank()) L("یادداشتی نوشته نشده بود.", "No notes were attached.") else rel.notes, 12.5f, C.TXT2)
        notes.setPadding(0, a.dp(8), 0, a.dp(8))
        col.addView(notes, lp(MATCH, WRAP))
        col.addView(a.tv(L("فایل APK دانلود می‌شود و نصب‌کننده اندروید باز می‌شود.", "The APK file will be downloaded and the Android installer will open."), 11.5f, C.TXT2), lp(MATCH, WRAP))
        val d = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(L("نسخه جدید موجود است", "A new version is available"))
            .setView(col)
            .setPositiveButton(L("دانلود و نصب", "Download and install")) { _, _ -> downloadFlow(a, rel) }
            .setNegativeButton(L("بعداً", "Later"), null)
            .create()
        d.show()
    }

    private fun downloadFlow(a: Activity, rel: Release) {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(6))

        val status = a.tv(L("در حال دانلود فایل APK…", "Downloading APK…"), 14f, C.TXT, true)
        col.addView(status, lp(WRAP, WRAP))

        val pb = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progress = 0
        pb.progressTintList = android.content.res.ColorStateList.valueOf(C.ACCENT)
        col.addView(pb, lp(MATCH, WRAP))

        val d = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("v" + rel.version)
            .setView(col)
            .setNegativeButton(L("لغو", "Cancel")) { _, _ -> cancel = true }
            .setCancelable(false)
            .create()
        d.show()

        val f = File(a.cacheDir, "update_" + rel.version + ".apk")

        Bg.run {
            val err = downloadApk(rel.apkUrl, rel.apkSize, f) { p ->
                Bg.ui {
                    if (d.isShowing) pb.progress = p
                }
            }

            Bg.ui {
                if (!a.isFinishing && !a.isDestroyed) {
                    try { d.dismiss() } catch (e: Throwable) {}
                }

                when {
                    err == "cancelled" -> {}
                    err != null -> Dialogs.message(
                        a,
                        L("دانلود ناموفق بود", "Download failed"),
                        L("دانلود کامل نشد. دوباره تلاش کنید.", "Download did not finish. Please try again.")
                    )
                    else -> {
                        val parseOk = try {
                            a.packageManager.getPackageArchiveInfo(f.absolutePath, 0) != null
                        } catch (e: Throwable) { false }

                        if (!parseOk) {
                            f.delete()
                            Dialogs.message(
                                a,
                                L("فایل معتبر نیست", "Invalid file"),
                                L("فایل APK معتبر نیست.", "The downloaded APK is not valid.")
                            )
                            return@ui
                        }

                        when (signatureMatches(a, f.absolutePath)) {
                            true -> install(a, f)
                            false -> Dialogs.confirm(
                                a,
                                L("امضای متفاوت", "Different signature"),
                                L("این APK با کلید دیگری امضا شده است. نصب آن ممکن است نیاز به حذف نسخه قبلی داشته باشد و اطلاعات برنامه پاک شود. ادامه می‌دهید؟",
                                  "This APK has a different signing key. Installing it may require uninstalling the previous version and may erase app data. Continue?"),
                                L("ادامه", "Continue")
                            ) { install(a, f) }
                            null -> Dialogs.confirm(
                                a,
                                L("بررسی امضا ناموفق بود", "Signature check failed"),
                                L("امضای APK قابل بررسی نبود. نصب ممکن است نیاز به حذف نسخه قبلی داشته باشد. ادامه می‌دهید؟",
                                  "The APK signature could not be checked. Installing may require uninstalling the previous version. Continue?"),
                                L("ادامه", "Continue")
                            ) { install(a, f) }
                        }
                    }
                }
            }
        }
    }

}
