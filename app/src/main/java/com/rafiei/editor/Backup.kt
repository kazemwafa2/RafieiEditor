package com.rafiei.editor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Whole-library backup (all drafts and their media) to a single zip in Downloads/RafieiEditor, and restore from it. */
object Backup {
    fun create(c: Context): String? {
        return try {
            val tmp = File(c.cacheDir, "backup_" + System.currentTimeMillis() + ".zip")
            ZipOutputStream(FileOutputStream(tmp).buffered(256 * 1024)).use { z ->
                z.setLevel(1)
                for (top in listOf("drafts", "media")) {
                    val root = File(c.filesDir, top)
                    if (!root.exists()) continue
                    root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                        val rel = top + "/" + f.relativeTo(root).path.replace('\\', '/')
                        z.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(z, 256 * 1024) }
                        z.closeEntry()
                    }
                }
            }
            val name = "RafieiEditor_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".zip"
            // Android 7.0-9 (API < 29): no scoped storage — write into Downloads directly and index it
            if (Build.VERSION.SDK_INT < 29) {
                return try {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RafieiEditor")
                    if (!dir.exists() && !dir.mkdirs()) return null
                    val dst = File(dir, name)
                    tmp.copyTo(dst, true)
                    tmp.delete()
                    MediaScannerConnection.scanFile(c, arrayOf(dst.absolutePath), arrayOf("application/zip"), null)
                    "Downloads/RafieiEditor/$name"
                } catch (e: Throwable) {
                    tmp.delete()
                    null
                }
            }
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RafieiEditor")
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) { tmp.delete(); return null }
            val os = c.contentResolver.openOutputStream(uri)
            if (os == null) {
                // nothing will be written: report failure instead of a fake success path
                try { c.contentResolver.delete(uri, null, null) } catch (e: Throwable) { }
                tmp.delete()
                return null
            }
            os.use { out -> tmp.inputStream().use { it.copyTo(out, 256 * 1024) } }
            tmp.delete()
            return "Downloads/RafieiEditor/$name"
        } catch (e: Throwable) {
            println("RafieiEditor backup failed: " + e.javaClass.simpleName + ": " + e.message)
            null
        }
    }

    /** Returns the number of restored files, or -1 on failure. */
    fun restore(c: Context, uri: Uri): Int {
        return try {
            var n = 0
            val base = c.filesDir.canonicalPath + File.separator
            c.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins.buffered(256 * 1024)).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory) continue
                        if (!(e.name.startsWith("drafts/") || e.name.startsWith("media/"))) continue
                        val dst = File(c.filesDir, e.name)
                        if (!dst.canonicalPath.startsWith(base)) continue // zip-slip guard
                        dst.parentFile?.mkdirs()
                        FileOutputStream(dst).use { z.copyTo(it, 256 * 1024) }
                        n++
                    }
                }
            }
            n
        } catch (e: Throwable) {
            println("RafieiEditor restore failed: " + e.javaClass.simpleName + ": " + e.message)
            -1
        }
    }
}
