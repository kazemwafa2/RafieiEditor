package com.rafiei.editor

import org.json.JSONArray

/** Word-level subtitle data: parsing, spreading, Persian clean-up and time refinement. */
object SubWords {
    class W(var w: String, var s: Long, var e: Long)

    private val cache = HashMap<String, List<W>>()

    fun parse(js: String): List<W> {
        if (js.isEmpty()) return emptyList()
        val hit = cache[js]
        if (hit != null) return hit
        val out = ArrayList<W>()
        var ok = true
        try {
            val a = JSONArray(js)
            for (i in 0 until a.length()) {
                val r = a.getJSONArray(i)
                out.add(W(r.getString(0), r.getLong(1), r.getLong(2)))
            }
        } catch (e: Throwable) {
            ok = false // do not cache a partial word list: the data is corrupt, not merely empty
        }
        if (ok) {
            if (cache.size > 400) cache.clear()
            cache[js] = out
        }
        return out
    }

    fun toJson(ws: List<W>): String {
        val a = JSONArray()
        for (w in ws) { val r = JSONArray(); r.put(w.w); r.put(w.s); r.put(w.e); a.put(r) }
        return a.toString()
    }

    fun join(ws: List<W>): String = ws.joinToString(" ") { it.w }

    /** Spreads the words of [text] over [spanMs] in proportion to their length (used after a text edit). */
    fun spread(text: String, spanMs: Long): List<W> {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val tot = parts.sumOf { Math.max(2, it.length) }.toDouble()
        val out = ArrayList<W>()
        var acc = 0.0
        for (p in parts) {
            val d = Math.max(2, p.length) / tot
            out.add(W(p, (acc * spanMs).toLong(), ((acc + d) * spanMs).toLong()))
            acc += d
        }
        return out
    }

    /** Index of the word being spoken at relative time [rel] (the last one that has started), or -1. */
    fun current(ws: List<W>, rel: Long): Int {
        var k = -1
        for (i in ws.indices) if (rel >= ws[i].s) k = i else break
        return k
    }

    /** Persian / Dari clean-up: Arabic letter forms → Persian, no tatweel, tidy spaces. */
    fun fa(t: String): String {
        var s = t
        s = s.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک")
        s = s.replace("ـ", "").replace(Regex("[ً-ْ]"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }
}
