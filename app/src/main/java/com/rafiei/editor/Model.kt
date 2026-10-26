package com.rafiei.editor

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Indices of the per-clip adjust array. All values are -1..1, except vignette and grain which are 0..1. */
object Adj {
    const val BRIGHT = 0
    const val CONTRAST = 1
    const val SAT = 2
    const val WARMTH = 3
    const val TINT = 4
    const val FADE = 5
    const val HUE = 6
    const val VIGNETTE = 7
    const val GRAIN = 8
    const val COUNT = 9
}

class Clip {
    var id = uid()
    var uri = ""
    var isImage = false
    var srcDurMs = 0L
    var trimStart = 0L
    var trimEnd = 0L
    var imgDur = 4000L
    var speed = 1f
    var volume = 1f
    var w = 1080
    var h = 1920
    var hasAudio = true
    var rot = 0
    var flipH = false
    var flipV = false
    var zoom = 1f
    var panX = 0f
    var panY = 0f
    var fill = false
    var filter = "none"
    var filterAmt = 1f
    var adj = FloatArray(Adj.COUNT)
    var trans = "none"
    var transMs = 600L
    var animIn = "none"
    var animInMs = 500L
    var animOut = "none"
    var animOutMs = 500L
    var light = ""
    var motion = ""
    var motionAmt = 1f
    var kf = ""

    fun durMs(): Long = if (isImage) imgDur else Math.max(100L, ((trimEnd - trimStart) / speed).toLong())
    fun dispW(): Int = if (rot % 180 == 0) w else h
    fun dispH(): Int = if (rot % 180 == 0) h else w

    fun copyOf(): Clip = fromJson(toJson()).also { it.id = uid() }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("uri", uri); o.put("img", isImage)
        o.put("src", srcDurMs); o.put("ts", trimStart); o.put("te", trimEnd); o.put("idur", imgDur)
        o.put("speed", speed.toDouble()); o.put("vol", volume.toDouble())
        o.put("w", w); o.put("h", h); o.put("ha", hasAudio)
        o.put("rot", rot); o.put("fh", flipH); o.put("fv", flipV)
        o.put("zoom", zoom.toDouble()); o.put("px", panX.toDouble()); o.put("py", panY.toDouble())
        o.put("fill", fill); o.put("flt", filter); o.put("fa", filterAmt.toDouble())
        val a = JSONArray(); for (v in adj) a.put(v.toDouble()); o.put("adj", a)
        o.put("tr", trans); o.put("trms", transMs)
        o.put("ai", animIn); o.put("aims", animInMs); o.put("ao", animOut); o.put("aoms", animOutMs)
        if (light.isNotEmpty()) o.put("light", light)
        if (kf.isNotEmpty()) o.put("kf", kf)
        if (motion.isNotEmpty()) { o.put("mo", motion); o.put("moa", motionAmt.toDouble()) }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Clip {
            val c = Clip()
            c.id = o.optString("id", uid()); c.uri = o.optString("uri"); c.isImage = o.optBoolean("img")
            c.srcDurMs = o.optLong("src"); c.trimStart = o.optLong("ts"); c.trimEnd = o.optLong("te"); c.imgDur = o.optLong("idur", 4000)
            c.speed = o.optDouble("speed", 1.0).toFloat(); c.volume = o.optDouble("vol", 1.0).toFloat()
            c.w = o.optInt("w", 1080); c.h = o.optInt("h", 1920); c.hasAudio = o.optBoolean("ha", true)
            c.rot = o.optInt("rot"); c.flipH = o.optBoolean("fh"); c.flipV = o.optBoolean("fv")
            c.zoom = o.optDouble("zoom", 1.0).toFloat(); c.panX = o.optDouble("px", 0.0).toFloat(); c.panY = o.optDouble("py", 0.0).toFloat()
            c.fill = o.optBoolean("fill"); c.filter = o.optString("flt", "none"); c.filterAmt = o.optDouble("fa", 1.0).toFloat()
            val a = o.optJSONArray("adj")
            if (a != null) for (i in 0 until Math.min(a.length(), Adj.COUNT)) c.adj[i] = a.optDouble(i, 0.0).toFloat()
            c.trans = o.optString("tr", "none"); c.transMs = o.optLong("trms", 600)
            c.animIn = o.optString("ai", "none"); c.animInMs = o.optLong("aims", 500)
            c.animOut = o.optString("ao", "none"); c.animOutMs = o.optLong("aoms", 500)
            c.light = o.optString("light", "")
            c.kf = o.optString("kf", "")
            c.motion = o.optString("mo", ""); c.motionAmt = o.optDouble("moa", 1.0).toFloat()
            return c
        }
    }
}

class AudioItem {
    var id = uid()
    var uri = ""
    var name = ""
    var kind = "music" // music | sfx | voice | tts
    var startMs = 0L
    var srcDurMs = 0L
    var trimStart = 0L
    var trimEnd = 0L
    var volume = 1f
    var speed = 1f
    var fadeIn = 0L
    var fadeOut = 0L
    var eqL = 0f
    var eqM = 0f
    var eqH = 0f
    var denoise = 0f
    var voice = false
    var duck = 0f
    var pitch = 1f

    fun hasFx(): Boolean = Math.abs(eqL) > 0.05f || Math.abs(eqM) > 0.05f || Math.abs(eqH) > 0.05f || denoise > 0.01f || voice
    fun fxKey(): String = "$eqL|$eqM|$eqH|$denoise|$voice"

    fun durMs(): Long = Math.max(100L, ((trimEnd - trimStart) / speed).toLong())
    fun endMs(): Long = startMs + durMs()

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("uri", uri); o.put("name", name); o.put("kind", kind)
        o.put("st", startMs); o.put("src", srcDurMs); o.put("ts", trimStart); o.put("te", trimEnd)
        o.put("vol", volume.toDouble()); o.put("speed", speed.toDouble()); o.put("fi", fadeIn); o.put("fo", fadeOut)
        if (hasFx()) { o.put("eqL", eqL.toDouble()); o.put("eqM", eqM.toDouble()); o.put("eqH", eqH.toDouble()); o.put("dn", denoise.toDouble()); o.put("vc", voice) }
        if (duck > 0f) o.put("duck", duck.toDouble())
        if (Math.abs(pitch - 1f) > 0.001f) o.put("pt", pitch.toDouble())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): AudioItem {
            val a = AudioItem()
            a.id = o.optString("id", uid()); a.uri = o.optString("uri"); a.name = o.optString("name"); a.kind = o.optString("kind", "music")
            a.startMs = o.optLong("st"); a.srcDurMs = o.optLong("src"); a.trimStart = o.optLong("ts"); a.trimEnd = o.optLong("te")
            a.volume = o.optDouble("vol", 1.0).toFloat(); a.speed = o.optDouble("speed", 1.0).toFloat()
            a.fadeIn = o.optLong("fi"); a.fadeOut = o.optLong("fo")
            a.eqL = o.optDouble("eqL", 0.0).toFloat(); a.eqM = o.optDouble("eqM", 0.0).toFloat(); a.eqH = o.optDouble("eqH", 0.0).toFloat()
            a.denoise = o.optDouble("dn", 0.0).toFloat(); a.voice = o.optBoolean("vc"); a.duck = o.optDouble("duck", 0.0).toFloat(); a.pitch = o.optDouble("pt", 1.0).toFloat()
            return a
        }
    }

    fun copyOf(): AudioItem = fromJson(toJson()).also { it.id = uid() }
}

class Overlay {
    var id = uid()
    var kind = "text" // text | sticker | image
    var startMs = 0L
    var durMs = 3000L
    var cx = 0.5f
    var cy = 0.5f
    var scale = 1f
    var rot = 0f
    var flipH = false
    var opacity = 1f

    // text
    var text = ""
    var font = "sans"
    var color = Color.WHITE
    var size = 0.075f
    var bold = false
    var italic = false
    var align = 1 // 0 left, 1 center, 2 right
    var spacing = 0f
    var outlineColor = Color.BLACK
    var outlineW = 0f
    var shadow = 0f
    var shadowColor = Color.BLACK
    var bgColor = Color.BLACK
    var bgAlpha = 0f

    // sticker / image
    var glyph = ""
    var uri = ""
    var imgAspect = 1f
    var cfg = "" // equalizer configuration (JSON)
    var frames = "" // PIP video: directory with extracted frames f0.jpg, f1.jpg ...
    var fps = 10
    var nf = 0
    var chroma = 0 // colour key (0 = off)
    var chromaTol = 0.3f
    var chromaSoft = 0.12f

    var animIn = "none"
    var animInMs = 500L
    var animOut = "none"
    var animOutMs = 500L

    fun endMs(): Long = startMs + durMs

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("kind", kind); o.put("st", startMs); o.put("du", durMs)
        o.put("cx", cx.toDouble()); o.put("cy", cy.toDouble()); o.put("sc", scale.toDouble()); o.put("rot", rot.toDouble())
        o.put("fh", flipH); o.put("op", opacity.toDouble())
        o.put("text", text); o.put("font", font); o.put("color", color); o.put("size", size.toDouble())
        o.put("b", bold); o.put("i", italic); o.put("al", align); o.put("sp", spacing.toDouble())
        o.put("oc", outlineColor); o.put("ow", outlineW.toDouble()); o.put("sh", shadow.toDouble()); o.put("shc", shadowColor)
        o.put("bc", bgColor); o.put("ba", bgAlpha.toDouble())
        o.put("glyph", glyph); o.put("uri", uri); o.put("cfg", cfg); o.put("asp", imgAspect.toDouble())
        if (nf > 0) { o.put("frd", frames); o.put("fps", fps); o.put("nf", nf) }
        if (chroma != 0) { o.put("ck", chroma); o.put("ckt", chromaTol.toDouble()); o.put("cks", chromaSoft.toDouble()) }
        o.put("ai", animIn); o.put("aims", animInMs); o.put("ao", animOut); o.put("aoms", animOutMs)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Overlay {
            val v = Overlay()
            v.id = o.optString("id", uid()); v.kind = o.optString("kind", "text"); v.startMs = o.optLong("st"); v.durMs = o.optLong("du", 3000)
            v.cx = o.optDouble("cx", 0.5).toFloat(); v.cy = o.optDouble("cy", 0.5).toFloat()
            v.scale = o.optDouble("sc", 1.0).toFloat(); v.rot = o.optDouble("rot", 0.0).toFloat()
            v.flipH = o.optBoolean("fh"); v.opacity = o.optDouble("op", 1.0).toFloat()
            v.text = o.optString("text"); v.font = o.optString("font", "sans"); v.color = o.optInt("color", Color.WHITE)
            v.size = o.optDouble("size", 0.075).toFloat(); v.bold = o.optBoolean("b"); v.italic = o.optBoolean("i")
            v.align = o.optInt("al", 1); v.spacing = o.optDouble("sp", 0.0).toFloat()
            v.outlineColor = o.optInt("oc", Color.BLACK); v.outlineW = o.optDouble("ow", 0.0).toFloat()
            v.shadow = o.optDouble("sh", 0.0).toFloat(); v.shadowColor = o.optInt("shc", Color.BLACK)
            v.bgColor = o.optInt("bc", Color.BLACK); v.bgAlpha = o.optDouble("ba", 0.0).toFloat()
            v.glyph = o.optString("glyph"); v.uri = o.optString("uri"); v.cfg = o.optString("cfg"); v.imgAspect = o.optDouble("asp", 1.0).toFloat()
            v.frames = o.optString("frd"); v.fps = o.optInt("fps", 10); v.nf = o.optInt("nf", 0)
            v.chroma = o.optInt("ck", 0); v.chromaTol = o.optDouble("ckt", 0.3).toFloat(); v.chromaSoft = o.optDouble("cks", 0.12).toFloat()
            v.animIn = o.optString("ai", "none"); v.animInMs = o.optLong("aims", 500)
            v.animOut = o.optString("ao", "none"); v.animOutMs = o.optLong("aoms", 500)
            return v
        }
    }

    fun copyOf(): Overlay = fromJson(toJson()).also { it.id = uid() }
}

class FxItem {
    var id = uid()
    var fx = "flash"
    var startMs = 0L
    var durMs = 2000L
    var amt = 0.7f
    fun endMs(): Long = startMs + durMs

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("fx", fx); o.put("st", startMs); o.put("du", durMs); o.put("amt", amt.toDouble())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): FxItem {
            val f = FxItem()
            f.id = o.optString("id", uid()); f.fx = o.optString("fx", "flash"); f.startMs = o.optLong("st")
            f.durMs = o.optLong("du", 2000); f.amt = o.optDouble("amt", 0.7).toFloat()
            return f
        }
    }

    fun copyOf(): FxItem = fromJson(toJson()).also { it.id = uid() }
}

class Project {
    var id = uid()
    var name = ""
    var type = "video" // video | photo
    var created = System.currentTimeMillis()
    var modified = System.currentTimeMillis()
    var ratio = "orig"
    var bgMode = "blur" // color | blur | grad
    var bgColor = Color.BLACK
    var bgColor2 = 0xFF2C3E50.toInt()
    var muteClips = false
    var clips = ArrayList<Clip>()
    var audios = ArrayList<AudioItem>()
    var overlays = ArrayList<Overlay>()
    var fxs = ArrayList<FxItem>()

    fun totalMs(): Long {
        var t = 0L
        for (c in clips) t += c.durMs()
        return t
    }

    fun clipStart(index: Int): Long {
        var t = 0L
        for (i in 0 until index) t += clips[i].durMs()
        return t
    }

    /** Returns index of clip at timeline time t (clamped to last clip), or -1 when there are no clips. */
    fun clipIndexAt(t: Long): Int {
        if (clips.isEmpty()) return -1
        var acc = 0L
        for (i in clips.indices) {
            val d = clips[i].durMs()
            if (t < acc + d) return i
            acc += d
        }
        return clips.size - 1
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("name", name); o.put("type", type); o.put("created", created); o.put("modified", modified)
        o.put("ratio", ratio); o.put("bgMode", bgMode); o.put("bgColor", bgColor); o.put("bgColor2", bgColor2); o.put("mute", muteClips)
        val c = JSONArray(); for (x in clips) c.put(x.toJson()); o.put("clips", c)
        val a = JSONArray(); for (x in audios) a.put(x.toJson()); o.put("audios", a)
        val v = JSONArray(); for (x in overlays) v.put(x.toJson()); o.put("overlays", v)
        val f = JSONArray(); for (x in fxs) f.put(x.toJson()); o.put("fxs", f)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Project {
            val p = Project()
            p.id = o.optString("id", uid()); p.name = o.optString("name"); p.type = o.optString("type", "video")
            p.created = o.optLong("created"); p.modified = o.optLong("modified")
            p.ratio = o.optString("ratio", "orig"); p.bgMode = o.optString("bgMode", "blur")
            p.bgColor = o.optInt("bgColor", Color.BLACK); p.bgColor2 = o.optInt("bgColor2", 0xFF2C3E50.toInt())
            p.muteClips = o.optBoolean("mute")
            val c = o.optJSONArray("clips"); if (c != null) for (i in 0 until c.length()) p.clips.add(Clip.fromJson(c.getJSONObject(i)))
            val a = o.optJSONArray("audios"); if (a != null) for (i in 0 until a.length()) p.audios.add(AudioItem.fromJson(a.getJSONObject(i)))
            val v = o.optJSONArray("overlays"); if (v != null) for (i in 0 until v.length()) p.overlays.add(Overlay.fromJson(v.getJSONObject(i)))
            val f = o.optJSONArray("fxs"); if (f != null) for (i in 0 until f.length()) p.fxs.add(FxItem.fromJson(f.getJSONObject(i)))
            return p
        }
    }

    fun snapshot(): String = toJson().toString()
    fun restore(s: String) {
        val q = fromJson(JSONObject(s))
        ratio = q.ratio; bgMode = q.bgMode; bgColor = q.bgColor; bgColor2 = q.bgColor2; muteClips = q.muteClips
        clips = q.clips; audios = q.audios; overlays = q.overlays; fxs = q.fxs
    }
}

/** Canvas size helpers */
object Geo {
    /** Aspect ratio (w/h) of the project canvas. */
    fun aspect(p: Project): Float {
        return when (p.ratio) {
            "9:16" -> 9f / 16f
            "16:9" -> 16f / 9f
            "1:1" -> 1f
            "4:5" -> 4f / 5f
            "5:4" -> 5f / 4f
            "4:3" -> 4f / 3f
            "3:4" -> 3f / 4f
            "3:2" -> 3f / 2f
            "2:3" -> 2f / 3f
            "21:9" -> 21f / 9f
            "2:1" -> 2f
            "1:2" -> 0.5f
            "orig" -> {
                val c = if (p.clips.isEmpty()) null else p.clips[0]
                if (c == null) 9f / 16f else c.dispW().toFloat() / c.dispH().toFloat()
            }
            else -> 9f / 16f
        }
    }

    /** Output size where the shorter side equals [shortSide]; both dimensions are even. */
    fun outSize(p: Project, shortSide: Int): IntArray {
        val a = aspect(p)
        var w: Int
        var h: Int
        if (a >= 1f) {
            h = shortSide; w = Math.round(shortSide * a)
        } else {
            w = shortSide; h = Math.round(shortSide / a)
        }
        w = w and 1.inv(); h = h and 1.inv()
        if (w < 2) w = 2
        if (h < 2) h = 2
        return intArrayOf(w, h)
    }

    /** Rectangle the clip's frame occupies inside a canvas of size W x H (x, y, w, h). */
    fun clipRect(c: Clip, W: Float, H: Float): FloatArray {
        val cw = c.dispW().toFloat()
        val ch = c.dispH().toFloat()
        val s = if (c.fill) Math.max(W / cw, H / ch) else Math.min(W / cw, H / ch)
        val w = cw * s
        val h = ch * s
        return floatArrayOf((W - w) / 2f, (H - h) / 2f, w, h)
    }
}
