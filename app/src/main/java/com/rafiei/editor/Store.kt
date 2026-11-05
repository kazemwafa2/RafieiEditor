package com.rafiei.editor

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Drafts are stored as JSON files; imported media is copied to a per-project folder. */
object Store {
    fun draftsDir(c: Context): File = dirOf(c, "drafts")

    fun mediaDir(c: Context, pid: String): File {
        val d = File(dirOf(c, "media"), pid)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun thumbFile(c: Context, pid: String): File = File(draftsDir(c), "$pid.jpg")
    private fun jsonFile(c: Context, pid: String): File = File(draftsDir(c), "$pid.json")

    @Synchronized
    fun save(c: Context, p: Project) {
        p.modified = System.currentTimeMillis()
        val tmp = File(draftsDir(c), p.id + ".tmp")
        tmp.writeText(p.toJson().toString())
        // rename(2) replaces the destination atomically on Android (Linux); deleting the
        // destination first would open a window where a crash loses the whole draft.
        if (!tmp.renameTo(jsonFile(c, p.id))) {
            // extremely rare (cross-device rename); fall back to a direct write
            try { jsonFile(c, p.id).writeText(p.toJson().toString()) } catch (e: Throwable) { }
            tmp.delete()
        }
    }

    fun load(c: Context, id: String): Project? {
        return try {
            val f = jsonFile(c, id)
            if (!f.exists()) null else Project.fromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            println("RafieiEditor draft $id unreadable: " + e.javaClass.simpleName)
            null
        }
    }

    fun list(c: Context): List<Project> {
        val out = ArrayList<Project>()
        val files = draftsDir(c).listFiles() ?: return out
        for (f in files) {
            if (f.name.endsWith(".json")) {
                try {
                    out.add(Project.fromJson(JSONObject(f.readText())))
                } catch (e: Exception) {
                    println("RafieiEditor draft " + f.name + " corrupt: " + e.javaClass.simpleName) // visible, not silent
                }
            }
        }
        out.sortByDescending { it.modified }
        return out
    }

    fun delete(c: Context, id: String) {
        jsonFile(c, id).delete()
        thumbFile(c, id).delete()
        File(File(c.filesDir, "media"), id).deleteRecursively()
    }

    fun duplicate(c: Context, id: String): Project? {
        val p = load(c, id) ?: return null
        val oldId = p.id
        val newId = uid()
        val src = File(File(c.filesDir, "media"), oldId)
        val dst = mediaDir(c, newId)
        if (src.exists()) src.copyRecursively(dst, true)
        val json = p.toJson().toString().replace(oldId, newId)
        val q = Project.fromJson(JSONObject(json))
        q.id = newId
        q.name = (if (p.name.isEmpty()) "" else p.name + " ") + L("(کپی)", "(copy)")
        q.created = System.currentTimeMillis()
        save(c, q)
        val th = thumbFile(c, oldId)
        if (th.exists()) th.copyTo(thumbFile(c, newId), true)
        return q
    }

    fun rename(c: Context, id: String, name: String) {
        val p = load(c, id) ?: return
        val t = p.modified
        p.name = name
        save(c, p)
        p.modified = t
    }

    @Synchronized
    fun saveJson(c: Context, id: String, json: String) {
        val tmp = File(draftsDir(c), "$id.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(jsonFile(c, id))) {
            try { jsonFile(c, id).writeText(json) } catch (e: Throwable) { }
            tmp.delete()
        }
    }
}
