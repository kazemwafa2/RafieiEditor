package com.rafiei.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject

/**
 * "اکولایزر فانتزی هزارگی ۲": a port of the stand-alone HTML design (character with two moving hands,
 * photo slideshow, butterflies, light ribbons, grass glow, camera moves, equalizer bars, moving name).
 * Everything is a pure function of the time, so preview, scrubbing and export give identical frames.
 */
object Hz {
    class Sl(val id: String, val label: String, val mn: Float, val mx: Float, val step: Float, val df: Float, val un: String)

    private fun s(id: String, label: String, mn: Float, mx: Float, st: Float, df: Float, un: String = "") = Sl(id, label, mn, mx, st, df, un)

    val groups: LinkedHashMap<String, List<Sl>> = linkedMapOf(
        "cam" to listOf(
            s("camAmt", "شدت حرکت دوربین", 0f, 2.5f, 0.05f, 1f), s("camClose", "میزان نزدیک شدن به صورت", 1.2f, 3.2f, 0.05f, 2f, "×"),
            s("camSpd", "سرعت حرکت دوربین", 0.3f, 3f, 0.05f, 1f), s("camZoom", "زوم پایه‌ی دوربین", 100f, 135f, 1f, 104f, "%"),
            s("camPar", "عمق (جابه‌جایی پس‌زمینه نسبت به سوژه)", 0f, 100f, 1f, 75f, "%")
        ),
        "bg" to listOf(
            s("bgBright", "روشنایی", -100f, 100f, 1f, 0f), s("bgDark", "تاریکی (سایه روی عکس)", 0f, 100f, 1f, 5f, "%"),
            s("bgContrast", "کنتراست", 30f, 250f, 1f, 105f, "%"), s("bgSat", "شدت رنگ", 0f, 300f, 1f, 110f, "%"),
            s("bgHue", "تغییر فام رنگ", -180f, 180f, 1f, 0f, "°"), s("bgBlur", "محوی (بلور)", 0f, 40f, 1f, 1f),
            s("bgZoom", "اندازه عکس", 30f, 300f, 1f, 128f, "%"), s("bgX", "جابه‌جایی افقی", -100f, 100f, 1f, 0f, "%"),
            s("bgY", "جابه‌جایی عمودی", -100f, 100f, 1f, -8f, "%"), s("bgOp", "شفافیت عکس", 0f, 100f, 1f, 100f, "%"),
            s("bgFeather", "نرم‌شدن لبه‌های عکس", 0f, 100f, 1f, 0f, "%"), s("bgFadeBot", "محو شدن عکس به سمت پایین", 0f, 100f, 1f, 20f, "%"),
            s("bgVig", "تیرگی گوشه‌ها", 0f, 100f, 1f, 30f, "%"), s("bgKB", "حرکت آرام و زوم عکس", 0f, 100f, 1f, 40f, "%"),
            s("bgBeat", "واکنش عکس به ضرب آهنگ", 0f, 100f, 1f, 35f, "%"), s("bgInterval", "مدت نمایش هر عکس (ثانیه)", 3f, 40f, 1f, 9f),
            s("bgFade", "مدت محو شدن بین عکس‌ها (ثانیه)", 0.3f, 5f, 0.1f, 1.6f)
        ),
        "bgtint" to listOf(s("bgTintAmt", "شدت رنگ‌پوشش", 0f, 100f, 1f, 8f, "%")),
        "char" to listOf(
            s("charScale", "اندازه شخصیت", 50f, 200f, 1f, 100f, "%"), s("charX", "جابه‌جایی افقی", -50f, 50f, 1f, 0f, "%"),
            s("charY", "جابه‌جایی عمودی", -50f, 50f, 1f, 0f, "%"),
            s("blackPt", "حذف تاریکی صحنه (بیشتر = عکس پس‌زمینه بیشتر دیده می‌شود)", 0f, 200f, 1f, 60f),
            s("charOp", "شفافیت شخصیت", 20f, 100f, 1f, 92f, "%"), s("charSharp", "وضوح و تیزی شخصیت", 0f, 100f, 1f, 40f),
            s("sceneTop", "محو قسمت بالای صحنه شخصیت", 0f, 70f, 1f, 0f, "%")
        ),
        "light" to listOf(
            s("glow", "شدت نور و درخشش", 0f, 2f, 0.05f, 1f), s("sens", "حساسیت به موسیقی", 0.3f, 3f, 0.05f, 1.2f),
            s("neon", "ضرب نور روی خود شخصیت", 0f, 2f, 0.05f, 0.6f)
        ),
        "move" to listOf(
            s("bf", "تعداد پروانه‌ها", 0f, 24f, 1f, 12f), s("spd", "سرعت پروانه‌ها", 0.2f, 3f, 0.05f, 1f),
            s("mov", "شدت حرکت سر، بدن و ساز", 0f, 2.5f, 0.05f, 0.5f), s("handAmt", "شدت حرکت دست‌ها", 0f, 3f, 0.05f, 1.3f),
            s("handTol", "دقت جدا کردن دست از ساز (کمتر = دقیق‌تر)", 30f, 200f, 1f, 90f), s("handRad", "اندازه ناحیه‌ی دست", 4f, 18f, 0.5f, 9f, "%")
        ),
        "fx" to listOf(s("eqH", "ارتفاع نوارهای اکولایزر", 2f, 30f, 1f, 12f, "%")),
        "wm" to listOf(
            s("wmSize", "اندازه نوشته", 4f, 30f, 0.5f, 11f, "%"), s("wmWidth", "عرض نوشته (کشیدگی افقی)", 40f, 250f, 1f, 100f, "%"),
            s("wmOp", "شفافیت نوشته", 10f, 100f, 1f, 92f, "%")
        )
    )

    val DEF: HashMap<String, Float> = HashMap<String, Float>().also { m -> for (g in groups.values) for (x in g) m[x.id] = x.df }

    val CHK: LinkedHashMap<String, Boolean> = linkedMapOf(
        "bgShow" to true, "bgFlip" to false, "handsOn" to true, "eqOn" to true, "waveOn" to true, "partOn" to true, "fgOn" to true,
        "ribOn" to true, "grassOn" to true, "wmOn" to true, "camBeat" to true
    )

    val CHK_LABELS = mapOf(
        "bgShow" to "اسلایدشو خودکار پس‌زمینه با محو شدن", "bgFlip" to "برعکس کردن افقی عکس پس‌زمینه",
        "handsOn" to "حرکت جداگانه‌ی دو دست (راست می‌نوازد، چپ نت می‌چیند)", "eqOn" to "نوارهای اکولایزر",
        "waveOn" to "موج صوتی اطراف ساز", "partOn" to "ذرات نور، جرقه و حباب‌های نور",
        "fgOn" to "گیاهان جلوی صحنه (حس عمق)", "ribOn" to "روبان‌های نور پشت شخصیت", "grassOn" to "درخشش چمن و موج نور زمین",
        "wmOn" to "نمایش نوشته‌ی متحرک", "camBeat" to "ضربه‌ی زوم دوربین با ضرب آهنگ"
    )

    val CAM_MODES = listOf(
        "orbit360" to "دور میدان: دایره‌ی کامل دور سوژه (پیشنهادی)",
        "cine3d" to "سه‌بعدی سینمایی: نزدیک شدن به صورت + چپ/راست (پیشنهادی)",
        "orbit" to "چرخش دور سوژه: چپ و راست + جلو و عقب",
        "mix" to "ترکیبی سینمایی (چپ/راست + لرزش دست)",
        "slow" to "آرام: زوم و حرکت نرم",
        "cinema" to "سینمایی: نزدیک و دور شدن + چرخش ملایم",
        "hand" to "دوربین روی دست (لرزش طبیعی)",
        "off" to "بدون حرکت دوربین"
    )
    val BLENDS = listOf(
        "lighten" to "روشن‌شونده (مثل ویدیو؛ قسمت‌های تاریک شفاف می‌شوند)",
        "screen" to "نورانی (Screen؛ نرم‌تر و درخشان‌تر)",
        "source-over" to "عادی (تصویر روی پس‌زمینه را می‌پوشاند)"
    )
    val FITS = listOf("cover" to "پر کردن کادر", "contain" to "تمام‌قد داخل کادر")
    val HUES = listOf(
        "mix" to "ترکیبی (آبی، فیروزه‌ای، بنفش)", "180" to "فیروزه‌ای", "215" to "آبی", "270" to "بنفش", "320" to "صورتی",
        "140" to "سبز", "45" to "طلایی", "custom" to "رنگ دلخواه (از تب رنگ‌ها: «رنگ نور دلخواه»)"
    )
    val RATIOS = listOf("9:16" to "عمودی ۹:۱۶", "16:9" to "افقی ۱۶:۹")

    /** Position data of a built-in character (fractions of the picture). */
    class Preset(
        val key: String, val label: String, val asset: String,
        val ix: Float, val iy: Float, val sx: Float, val sy: Float, val fx: Float, val fy: Float, val tol: Float, val bp: Float, val faceX: Float, val faceY: Float
    )

    val PRESETS = listOf(
        Preset("boy", "پسر دمبوره‌نواز", "hz_boy.jpg", .31f, .55f, .30f, .516f, .64f, .43f, 90f, 30f, .275f, .31f),
        Preset("girl", "دختر دمبوره‌نواز", "hz_girl.jpg", .39f, .62f, .35f, .58f, .75f, .48f, 90f, 30f, .39f, .27f),
        Preset("forest", "پسر گیتار (جنگل)", "hz_forest.jpg", .47f, .48f, .475f, .45f, .75f, .38f, 45f, 60f, .41f, .2f),
        Preset("night", "پسر گیتار (دریاچه)", "hz_night.jpg", .44f, .50f, .425f, .445f, .66f, .38f, 55f, 60f, .39f, .22f)
    )

    fun preset(k: String): Preset? = PRESETS.firstOrNull { it.key == k }

    /** Writes the positions of preset [k] (or the defaults of an own picture) into [j]. */
    fun applyPreset(j: JSONObject, k: String) {
        val p = preset(k)
        j.put("char", k)
        if (p != null) {
            j.put("ix", p.ix.toDouble()); j.put("iy", p.iy.toDouble())
            j.put("sx", p.sx.toDouble()); j.put("sy", p.sy.toDouble()); j.put("fx", p.fx.toDouble()); j.put("fy", p.fy.toDouble())
            j.put("handTol", p.tol.toDouble()); j.put("blackPt", p.bp.toDouble())
            j.put("faceX", p.faceX.toDouble()); j.put("faceY", p.faceY.toDouble())
        } else {
            j.put("ix", 0.4); j.put("iy", 0.6); j.put("sx", 0.38); j.put("sy", 0.62); j.put("fx", 0.7); j.put("fy", 0.5)
            j.put("handTol", 60.0); j.put("blackPt", 45.0); j.put("faceX", 0.45); j.put("faceY", 0.2)
        }
    }
}

/** Typed reader of the "hz" settings object (missing values fall back to the defaults). */
class HzCfg(val j: JSONObject) {
    val charKey: String = j.optString("char", "boy")
    private val pre: Hz.Preset? = Hz.preset(charKey) ?: Hz.PRESETS[0]
    fun f(id: String): Float = j.optDouble(id, (Hz.DEF[id] ?: 0f).toDouble()).toFloat()
    fun b(id: String): Boolean = j.optBoolean(id, Hz.CHK[id] ?: false)
    fun s(id: String, d: String): String = j.optString(id, d)
    val ix get() = j.optDouble("ix", pre!!.ix.toDouble()).toFloat()
    val iy get() = j.optDouble("iy", pre!!.iy.toDouble()).toFloat()
    val sx get() = j.optDouble("sx", pre!!.sx.toDouble()).toFloat()
    val sy get() = j.optDouble("sy", pre!!.sy.toDouble()).toFloat()
    val fx get() = j.optDouble("fx", pre!!.fx.toDouble()).toFloat()
    val fy get() = j.optDouble("fy", pre!!.fy.toDouble()).toFloat()
    val faceX get() = j.optDouble("faceX", pre!!.faceX.toDouble()).toFloat()
    val faceY get() = j.optDouble("faceY", pre!!.faceY.toDouble()).toFloat()
    val handTol: Float get() = j.optDouble("handTol", pre!!.tol.toDouble()).toFloat()
    val blackPt: Float get() = j.optDouble("blackPt", pre!!.bp.toDouble()).toFloat()
    fun uri(k: String): String = j.optString(k, "")
    fun bgList(): List<String> {
        val a = j.optJSONArray("bgs") ?: return emptyList()
        val l = ArrayList<String>()
        for (i in 0 until a.length()) l.add(a.optString(i))
        return l
    }
}

// ------------------------------------------------------------------ processed character

class HzHand(val bmp: Bitmap, val x0: Int, val y0: Int, val w: Int, val h: Int, val px: Float, val py: Float)

class HzChar(
    val cw: Int, val ch: Int, val iw: Int, val ih: Int, val charCv: Bitmap,
    val hands: Array<HzHand?>, val halo: Bitmap?, val views: Array<Bitmap?>
)

object HzProc {
    private const val MAXD = 960
    private val cache = LinkedHashMap<String, HzChar>()

    fun clampI(v: Int, a: Int, b: Int) = if (v < a) a else if (v > b) b else v

    private fun loadSrc(key: String, uri: String): Bitmap? =
        if (key == "custom" && uri.isNotEmpty()) BmpCache.get(uri, 1600)
        else EqAssets.bmp((Hz.preset(key) ?: Hz.PRESETS[0]).asset)

    fun get(c: HzCfg): HzChar? {
        val key = listOf(
            c.charKey, c.uri("charUri"), c.f("blackPt"), c.f("charSharp"), c.f("sceneTop"), c.s("blend", "lighten") == "source-over",
            c.uri("viewR"), c.uri("viewB"), c.uri("viewL"), c.f("handTol"), c.f("handRad"), c.sx, c.sy, c.fx, c.fy
        ).joinToString("|")
        synchronized(cache) { cache[key]?.let { return it } }
        val r = try { build(c) } catch (e: Throwable) { null }
        if (r != null) synchronized(cache) {
            cache[key] = r
            while (cache.size > 3) { val k0 = cache.keys.first(); cache.remove(k0) }
        }
        return r
    }

    private fun scaled(src: Bitmap, cw: Int, ch: Int): Bitmap =
        Bitmap.createScaledBitmap(src, cw, ch, true).copy(Bitmap.Config.ARGB_8888, true)

    private fun postProc(px: IntArray, cw: Int, ch: Int, blend: String, bp: Int, top: Float, shp: Float) {
        if (blend != "source-over" && (bp > 0 || top > 0f)) {
            val lut = IntArray(256)
            val k = 255f / (255 - bp).coerceAtLeast(1)
            for (v in 0 until 256) lut[v] = clampI(Math.round((v - bp) * k), 0, 255)
            for (y in 0 until ch) {
                val f = if (top > 0f && y.toFloat() / ch < top) Math.pow((y.toFloat() / ch / top).toDouble(), 1.6).toFloat() else 1f
                val o = y * cw
                for (i in o until o + cw) {
                    val c = px[i]
                    val r = (lut[(c shr 16) and 255] * f).toInt()
                    val g = (lut[(c shr 8) and 255] * f).toInt()
                    val b = (lut[c and 255] * f).toInt()
                    px[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        if (shp > 0f) {
            val o = px.copyOf()
            for (yy in 1 until ch - 1) for (xx in 1 until cw - 1) {
                val i = yy * cw + xx
                var out = o[i] and 0xFF000000.toInt()
                for (sh in intArrayOf(16, 8, 0)) {
                    val v = (o[i] shr sh) and 255
                    val a = (((o[i - 1] shr sh) and 255) + ((o[i + 1] shr sh) and 255) + ((o[i - cw] shr sh) and 255) + ((o[i + cw] shr sh) and 255)) / 4f
                    out = out or (clampI(Math.round(v + (v - a) * shp * 1.3f), 0, 255) shl sh)
                }
                px[i] = out
            }
        }
    }

    private fun dil(m: ByteArray, w: Int, h: Int): ByteArray {
        val o = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = 0
            var j = -1
            while (j <= 1 && v == 0) {
                val yy = y + j
                if (yy in 0 until h) {
                    for (i in -1..1) {
                        val xx = x + i
                        if (xx in 0 until w && m[yy * w + xx].toInt() != 0) { v = 1; break }
                    }
                }
                j++
            }
            o[y * w + x] = v.toByte()
        }
        return o
    }

    private fun ero(m: ByteArray, w: Int, h: Int): ByteArray {
        val o = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = 1
            var j = -1
            while (j <= 1 && v == 1) {
                val yy = y + j
                if (yy in 0 until h) {
                    for (i in -1..1) {
                        val xx = x + i
                        if (xx in 0 until w && m[yy * w + xx].toInt() == 0) { v = 0; break }
                    }
                }
                j++
            }
            o[y * w + x] = (v and m[y * w + x].toInt()).toByte()
        }
        return o
    }

    private fun build(c: HzCfg): HzChar? {
        val img = loadSrc(c.charKey, c.uri("charUri")) ?: return null
        val sc = Math.min(1f, MAXD.toFloat() / Math.max(img.width, img.height))
        val cw = Math.max(8, Math.round(img.width * sc))
        val ch = Math.max(8, Math.round(img.height * sc))
        val blend = c.s("blend", "lighten")
        val bp = c.blackPt.toInt()
        val top = c.f("sceneTop") / 100f
        val shp = c.f("charSharp") / 100f
        val raw = scaled(img, cw, ch)
        val S = IntArray(cw * ch)
        raw.getPixels(S, 0, cw, 0, 0, cw, ch)
        postProc(S, cw, ch, blend, bp, top, shp)
        raw.setPixels(S, 0, cw, 0, 0, cw, ch)
        val charCv = raw.copy(Bitmap.Config.ARGB_8888, true)
        val hands = arrayOfNulls<HzHand>(2)
        val tol = c.f("handTol")
        val R = Math.round(c.f("handRad") / 100f * cw)
        val pts = arrayOf(floatArrayOf(c.sx, c.sy), floatArrayOf(c.fx, c.fy))
        for (idx in 0..1) {
            val sx = clampI(Math.round(pts[idx][0] * cw), 0, cw - 1)
            val sy = clampI(Math.round(pts[idx][1] * ch), 0, ch - 1)
            val x0 = Math.max(0, sx - R)
            val y0 = Math.max(0, sy - R)
            val x1 = Math.min(cw, sx + R + 1)
            val y1 = Math.min(ch, sy + R + 1)
            val w = x1 - x0
            val h = y1 - y0
            if (w < 8 || h < 8) continue
            val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
            val stack = IntArray(w * h + 64)
            var sp = 0
            val seen = ByteArray(w * h)
            for (j in -3..3) for (i in -3..3) {
                val xx = clampI(sx + i, 0, cw - 1)
                val yy = clampI(sy + j, 0, ch - 1)
                val p = S[yy * cw + xx]
                rs.add((p shr 16) and 255); gs.add((p shr 8) and 255); bs.add(p and 255)
                if (xx in x0 until x1 && yy in y0 until y1) {
                    val q = (yy - y0) * w + (xx - x0)
                    if (seen[q].toInt() == 0) { seen[q] = 1; stack[sp++] = q }
                }
            }
            rs.sort(); gs.sort(); bs.sort()
            val r0 = rs[rs.size shr 1]; val g0 = gs[gs.size shr 1]; val b0 = bs[bs.size shr 1]
            var m = ByteArray(w * h)
            val t2 = tol * tol
            val R2 = R.toLong() * R
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w
                val py = p / w
                val gx = x0 + px
                val gy = y0 + py
                val o = S[gy * cw + gx]
                val dr = ((o shr 16) and 255) - r0
                val dg = ((o shr 8) and 255) - g0
                val db = (o and 255) - b0
                if (2f * dr * dr + dg * dg + db * db > t2) continue
                val ex = gx - sx
                val ey = gy - sy
                if (ex.toLong() * ex + ey.toLong() * ey > R2) continue
                m[p] = 1
                if (px > 0 && seen[p - 1].toInt() == 0) { seen[p - 1] = 1; stack[sp++] = p - 1 }
                if (px < w - 1 && seen[p + 1].toInt() == 0) { seen[p + 1] = 1; stack[sp++] = p + 1 }
                if (py > 0 && seen[p - w].toInt() == 0) { seen[p - w] = 1; stack[sp++] = p - w }
                if (py < h - 1 && seen[p + w].toInt() == 0) { seen[p + w] = 1; stack[sp++] = p + w }
            }
            run { // second, looser pass near the first result
                var near = m
                for (q in 0 until 5) near = dil(near, w, h)
                val t3 = tol * tol * 2.2f
                val rmin = r0 * 0.6f
                val st2 = IntArray(w * h + 8)
                var n2 = 0
                for (i in 0 until w * h) if (m[i].toInt() != 0) st2[n2++] = i
                while (n2 > 0) {
                    val p = st2[--n2]
                    val px = p % w
                    val py = p / w
                    val nb = intArrayOf(if (px > 0) p - 1 else -1, if (px < w - 1) p + 1 else -1, if (py > 0) p - w else -1, if (py < h - 1) p + w else -1)
                    for (q in nb) {
                        if (q < 0 || m[q].toInt() != 0 || near[q].toInt() == 0) continue
                        val o = S[(y0 + q / w) * cw + x0 + q % w]
                        val rr0 = (o shr 16) and 255
                        val dr = rr0 - r0
                        val dg = ((o shr 8) and 255) - g0
                        val db = (o and 255) - b0
                        if (rr0 >= rmin && 2f * dr * dr + dg * dg + db * db <= t3) { m[q] = 1; st2[n2++] = q }
                    }
                }
            }
            m = ero(dil(dil(dil(m, w, h), w, h), w, h), w, h)
            m = dil(dil(m, w, h), w, h)
            var cnt = 0
            var mx = 0.0
            var my = 0.0
            for (i in 0 until w * h) if (m[i].toInt() != 0) { cnt++; mx += i % w; my += i / w }
            if (cnt < 40) continue
            // hand layer with a softened edge
            val lp = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var a = 0
                var n = 0
                for (j in -1..1) for (k in -1..1) {
                    val xx = x + k
                    val yy = y + j
                    if (xx in 0 until w && yy in 0 until h) { a += m[yy * w + xx]; n++ }
                }
                val o = S[(y0 + y) * cw + x0 + x]
                val al = if (m[i].toInt() != 0 || a > 0) Math.round(255f * a / n) else 0
                lp[i] = (al shl 24) or (o and 0xFFFFFF)
            }
            val lb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            lb.setPixels(lp, 0, w, 0, 0, w, h)
            // fill the hole in the instrument layer along the neck direction
            var unk = m
            for (q in 0 until (if (tol < 60f) 6 else 4)) unk = dil(unk, w, h)
            val col = FloatArray(w * h * 3)
            val known = ByteArray(w * h)
            val list = ArrayList<Int>()
            for (i in 0 until w * h) {
                if (unk[i].toInt() != 0) list.add(i) else {
                    known[i] = 1
                    val o = S[(y0 + i / w) * cw + x0 + i % w]
                    col[i * 3] = ((o shr 16) and 255).toFloat(); col[i * 3 + 1] = ((o shr 8) and 255).toFloat(); col[i * 3 + 2] = (o and 255).toFloat()
                }
            }
            var dxn = (c.fx - c.sx) * cw
            var dyn = (c.fy - c.sy) * ch
            val dl = Math.hypot(dxn.toDouble(), dyn.toDouble()).toFloat().let { if (it == 0f) 1f else it }
            dxn /= dl; dyn /= dl
            val maxL = R * 2
            for (i in list) {
                val x = i % w
                val y = i / w
                var c1 = -1; var d1 = 0; var c2 = -1; var d2 = 0
                for (s in 1..maxL) {
                    val xx = Math.round(x + dxn * s); val yy = Math.round(y + dyn * s)
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) break
                    val q = yy * w + xx
                    if (known[q].toInt() != 0) { c1 = q; d1 = s; break }
                }
                for (s in 1..maxL) {
                    val xx = Math.round(x - dxn * s); val yy = Math.round(y - dyn * s)
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) break
                    val q = yy * w + xx
                    if (known[q].toInt() != 0) { c2 = q; d2 = s; break }
                }
                var k = 0
                if (c1 >= 0 && c2 >= 0) {
                    val wa = d2.toFloat() / (d1 + d2); val wb = d1.toFloat() / (d1 + d2)
                    for (cc in 0..2) col[i * 3 + cc] = col[c1 * 3 + cc] * wa + col[c2 * 3 + cc] * wb
                    k = 1
                } else if (c1 >= 0) { for (cc in 0..2) col[i * 3 + cc] = col[c1 * 3 + cc]; k = 1 }
                else if (c2 >= 0) { for (cc in 0..2) col[i * 3 + cc] = col[c2 * 3 + cc]; k = 1 }
                known[i] = k.toByte()
            }
            val bi = IntArray(w * h)
            charCv.getPixels(bi, 0, w, x0, y0, w, h)
            for (i in 0 until w * h) if (unk[i].toInt() != 0 && known[i].toInt() != 0) {
                bi[i] = (bi[i] and 0xFF000000.toInt()) or (clampI(col[i * 3].toInt(), 0, 255) shl 16) or (clampI(col[i * 3 + 1].toInt(), 0, 255) shl 8) or clampI(col[i * 3 + 2].toInt(), 0, 255)
            }
            charCv.setPixels(bi, 0, w, x0, y0, w, h)
            hands[idx] = HzHand(lb, x0, y0, w, h, (mx / cnt).toFloat(), (my / cnt).toFloat())
        }
        // halo used for the neon pulse of the character
        val hw = Math.max(8, cw / 2)
        val hh = Math.max(8, ch / 2)
        val t = Bitmap.createScaledBitmap(charCv, hw, hh, true).copy(Bitmap.Config.ARGB_8888, true)
        val hp = IntArray(hw * hh)
        t.getPixels(hp, 0, hw, 0, 0, hw, hh)
        for (i in hp.indices) {
            val p = hp[i]
            val r = clampI(Math.round((((p shr 16) and 255) - 170) * 2.4f), 0, 255)
            val g = clampI(Math.round((((p shr 8) and 255) - 170) * 2.4f), 0, 255)
            val b = clampI(Math.round(((p and 255) - 170) * 2.4f), 0, 255)
            hp[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
        t.setPixels(hp, 0, hw, 0, 0, hw, hh)
        val sm1 = Bitmap.createScaledBitmap(t, Math.max(2, hw / 4), Math.max(2, hh / 4), true)
        val halo = Bitmap.createScaledBitmap(sm1, hw, hh, true)
        // extra viewing angles
        val views = arrayOfNulls<Bitmap>(3)
        for ((n, k) in listOf("viewR", "viewB", "viewL").withIndex()) {
            val u = c.uri(k)
            if (u.isEmpty()) continue
            val b = BmpCache.get(u, 1600) ?: continue
            val vb = scaled(b, cw, ch)
            val vp = IntArray(cw * ch)
            vb.getPixels(vp, 0, cw, 0, 0, cw, ch)
            postProc(vp, cw, ch, blend, bp, top, shp)
            vb.setPixels(vp, 0, cw, 0, 0, cw, ch)
            views[n] = vb
        }
        return HzChar(cw, ch, img.width, img.height, charCv, hands, halo, views)
    }
}

// ------------------------------------------------------------------ the design

class EqHazaragi : EqTpl("hazaragi", "فانتزی هزارگی ۲ (شخصیت، دست‌ها، پروانه)") {
    init {
        labels = mapOf("text" to "رنگ نوشته‌ی متحرک", "accent" to "رنگ‌پوشش پس‌زمینه", "main" to "رنگ نور دلخواه")
        keys = listOf("text", "accent", "main")
        def = EqDef(
            mapOf(
                "main" to "#33e0ff", "accent" to "#1a3cff", "text" to "#ffd37a", "text2" to "#8a5a14", "letter" to "#ffffff",
                "panel" to "#07080f", "bar1" to "#3ee8ff", "bar2" to "#3ee8ff", "bar3" to "#ffffff", "ui" to "#ffffff"
            ), false, "grad", false, false
        )
    }

    private class Cam {
        var x = 0f; var y = 0f; var r = 0f; var z = 1f; var orb = 0f; var dy = 0f; var dol = 0f; var fm = 0f
        var bs = 0f; var ms = 0f; var fs = 0f; var va = Float.NaN; var fx = 0f; var fy = 0f
    }

    private class Bg(val bmp: Bitmap) {
        var cache: Cv? = null
        var key = ""
    }

    private class Run(val c: HzCfg, val st: EqState, val W: Int, val H: Int) {
        val main = Cv(W, H)
        var chr: HzChar? = null
        var chBuf: Cv? = null
        var fill: Cv? = null
        val bgs = ArrayList<Bg>()
        val bf = HashMap<Int, Cv>()
        var fg: Cv? = null
        var customHue = 190f
        // per-frame values
        var t = 0f
        var beatP = 0f
        var E = 0.35f
        var m = 1f
        var bars = FloatArray(40)
        var lx = 0f; var ly = 0f; var lw = 1f; var lh = 1f
        var frontW = 1f
        val cam = Cam()
        var beats = FloatArray(0)
    }

    private fun sm(x0: Float): Float { val x = clampF(x0, 0f, 1f); return x * x * (3f - 2f * x) }
    private fun md(a: Float, b: Float): Float = ((a % b) + b) % b

    private fun hslC(h: Float, l: Float, a: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 360f
        val ll = clampF(l / 100f, 0f, 1f)
        val c = (1f - Math.abs(2f * ll - 1f))
        fun comp(n: Float): Float {
            val k = (n + hh * 12f) % 12f
            return ll - (c / 2f) * Math.max(-1f, Math.min(Math.min(k - 3f, 9f - k), 1f))
        }
        val r = comp(0f); val g = comp(8f); val b = comp(4f)
        return Color.argb(Math.round(clampF(a, 0f, 1f) * 255f), clampI(Math.round(r * 255f), 0, 255), clampI(Math.round(g * 255f), 0, 255), clampI(Math.round(b * 255f), 0, 255))
    }

    private fun hueOf(R: Run, i: Int): Float {
        val m = R.c.s("hue", "mix")
        if (m == "mix") return floatArrayOf(180f, 215f, 270f, 195f, 245f)[Math.abs(i) % 5]
        if (m == "custom") return R.customHue
        return m.toFloatOrNull() ?: 200f
    }

    // ---------------------------------------------------------------- build

    override fun build(st: EqState): EqLayers {
        val L = EqLayers()
        val c = HzCfg(st.cfg.optJSONObject("hz") ?: JSONObject())
        val portrait = c.s("ratio", "9:16") == "9:16"
        val W = if (portrait) 405 else EQ_W
        val H = EQ_H
        val R = Run(c, st, W, H)
        val mc = st.p("main")
        val mx = Math.max(mc[0], Math.max(mc[1], mc[2])) / 255f
        val mn = Math.min(mc[0], Math.min(mc[1], mc[2])) / 255f
        val dd = mx - mn
        var hh = 0f
        if (dd > 0f) {
            val r = mc[0] / 255f; val g = mc[1] / 255f; val b = mc[2] / 255f
            hh = if (mx == r) ((g - b) / dd) % 6f else if (mx == g) (b - r) / dd + 2f else (r - g) / dd + 4f
            hh = (hh * 60f + 360f) % 360f
        }
        R.customHue = if (dd < 0.05f) 200f else hh
        R.chr = HzProc.get(c)
        val chr = R.chr
        if (chr != null) {
            R.chBuf = Cv(chr.cw, chr.ch)
            // blurred dark copy of the character as the background when no photos are given
            val f = Cv(W, H)
            val g = f.g
            g.fillStyle = Color.rgb(2, 6, 15); g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
            val sw = Math.max(8, W / 14)
            val shh = Math.max(8, H / 14)
            val tmp = Cv(sw, shh)
            val cs = Math.max(sw.toFloat() / chr.cw, shh.toFloat() / chr.ch)
            tmp.g.drawImage(chr.charCv, (sw - chr.cw * cs) / 2f, (shh - chr.ch * cs) / 2f, chr.cw * cs, chr.ch * cs)
            g.drawImage(tmp, 0f, 0f, W.toFloat(), H.toFloat())
            g.fillStyle = Color.argb(150, 0, 0, 12); g.fillRect(0f, 0f, W.toFloat(), H.toFloat())
            R.fill = f
        }
        for (u in c.bgList()) {
            val b = BmpCache.get(u, 1600)
            if (b != null) R.bgs.add(Bg(b))
        }
        L.extra["run"] = R
        L.extra["missing"] = (chr == null)
        return L
    }

    // ---------------------------------------------------------------- per-frame

    override fun frame(g: Ctx2D, L: EqLayers, st: EqState, d: EqData) {
        val R = L.extra["run"] as? Run ?: return
        val chr = R.chr
        if (chr == null) {
            g.fillStyle = Color.argb(160, 0, 0, 0); g.fillRect(0f, 0f, EQ_W.toFloat(), EQ_H.toFloat())
            g.fillStyle = Color.WHITE; g.textAlign = "center"; g.textBaseline = "middle"; g.setFont(700, 34f)
            g.fillText("تصویر شخصیت خوانده نشد", EQ_W / 2f, EQ_H / 2f)
            return
        }
        val c = R.c
        val W = R.W.toFloat()
        val H = R.H.toFloat()
        val t = d.t
        R.t = t
        // ----- audio-derived values
        var mean = 0f
        for (v in d.lv) mean += v
        mean /= Math.max(1, d.lv.size)
        val sens = c.f("sens")
        R.E = clampF(mean * 1.3f * sens / 1.2f, 0f, 1f)
        R.beats = d.beats
        var bp = 0f
        var i0 = 0
        while (i0 + 1 < d.beats.size) {
            val age = d.beats[i0]; val str = d.beats[i0 + 1]
            if (age < 1f) bp = Math.max(bp, str * Math.max(0f, 1f - age * 2.6f))
            i0 += 2
        }
        R.beatP = bp
        for (i in 0 until 40) {
            val j = Math.min(d.lv.size - 1, Math.round(i / 39f * 62f))
            R.bars[i] = clampF(d.lv[j] * sens / 1.2f, 0f, 1f)
        }
        R.m = sm(t / 2.5f) * sm((d.dur - t) / 2.5f)
        val m = R.m
        val gl = c.f("glow")
        val u = W / 540f
        // ----- layout of the character
        val sc0 = if (c.s("fit", if (c.s("ratio", "9:16") == "9:16") "cover" else "contain") == "cover") Math.max(W / chr.iw, H / chr.ih) else Math.min(W / chr.iw, H / chr.ih)
        val ss = sc0 * c.f("charScale") / 100f
        R.lw = chr.iw * ss; R.lh = chr.ih * ss
        R.lx = (W - R.lw) / 2f + c.f("charX") / 100f * W
        R.ly = (H - R.lh) / 2f + c.f("charY") / 100f * H
        val cx = R.lx + R.lw / 2f
        val cy = R.ly + R.lh * 0.45f
        val px = R.lx + c.ix * R.lw
        val py = R.ly + c.iy * R.lh
        camera(R)
        val cam = R.cam
        val q = R.main.g
        q.setTransform(1f, 0f, 0f, 1f, 0f, 0f)
        q.globalAlpha = 1f; q.globalCompositeOperation = "source-over"; q.shadowBlur = 0f
        q.fillStyle = Color.rgb(2, 6, 15); q.fillRect(0f, 0f, W, H)
        run {
            val fx = R.lx + c.faceX * R.lw
            val fy = R.ly + c.faceY * R.lh + H * 0.02f
            val hz = W / (2f * cam.z)
            val vz = H / (2f * cam.z)
            cam.fx = clampF(W / 2f + (fx - W / 2f) * cam.fm, hz, W - hz)
            cam.fy = clampF(H / 2f + (fy - H / 2f) * cam.fm, vz, H - vz)
        }
        q.save()
        q.translate(W / 2f + cam.x, H / 2f + cam.y); q.rotate(cam.r); q.scale(cam.z, cam.z); q.translate(-cam.fx, -cam.fy)
        val pp = c.f("camPar") / 100f
        q.save()
        run { val bz = 1f + cam.dol * 0.9f; q.translate(W / 2f - cam.orb * W * 0.085f * pp, H / 2f - cam.dy * H * 0.02f * pp); q.scale(bz, bz); q.translate(-W / 2f, -H / 2f) }
        drawBg(R)
        q.restore()
        val flash = c.f("bgBeat") / 100f * R.beatP * 0.10f
        if (flash > 0f && R.bgs.isNotEmpty()) {
            q.globalCompositeOperation = "lighter"; q.fillStyle = hslC(hueOf(R, 0), 50f, flash); q.fillRect(-W, -H, 3f * W, 3f * H); q.globalCompositeOperation = "source-over"
        }
        q.save()
        q.translate(-cam.orb * W * 0.04f * pp, -cam.dy * H * 0.01f * pp)
        q.globalCompositeOperation = "lighter"
        if (c.b("ribOn")) {
            for (r in 0 until 5) {
                val x0 = wrapX(R, W * (0.08f + 0.2f * r + 0.03f * Math.sin(t * 0.3 + r).toFloat()))
                val a = (0.07f + 0.22f * R.E + 0.2f * R.beatP) * gl * m
                for ((lw, al) in listOf(7f to 0.35f, 2f to 1f)) {
                    q.strokeStyle = hslC(hueOf(R, r), 62f, a * al); q.lineWidth = lw * u; q.beginPath()
                    for (s in 0..22) {
                        val yy = H * (0.04f + s / 22f * 0.82f)
                        val xx = x0 + Math.sin(s * 0.45 + t * 0.9 + r * 1.7).toFloat() * W * 0.035f * (1f + R.E)
                        if (s > 0) q.lineTo(xx, yy) else q.moveTo(xx, yy)
                    }
                    q.stroke()
                }
            }
        }
        if (c.b("partOn")) {
            for (i in 0 until 14) { // bokeh behind the character
                val sz = 0.025f + 0.05f * hashF(i * 3.1f + 1f)
                val v = 0.004f + 0.012f * hashF(i * 5.7f + 2f)
                val y0 = hashF(i * 7.3f + 3f)
                val ph = y0 - v * t * (1f + R.E) * 20f
                val cyc = Math.floor(ph.toDouble()).toFloat()
                val yy = (ph - cyc) * 1.2f - 0.1f
                val bx = hashF(i * 11.9f + 4f + cyc * 3.7f)
                val pphase = hashF(i * 2.3f + 5f) * 6.28f
                val r = sz * W * (1f + R.beatP * 0.15f)
                val x = wrapX(R, (bx + Math.sin(t * 0.3 + pphase).toFloat() * 0.03f) * W)
                val y = yy * H
                val hh = if (i % 3 == 0) 55f else hueOf(R, i)
                val gr = q.createRadialGradient(x, y, 0f, x, y, r)
                gr.addColorStop(0f, hslC(hh, 65f, 0.22f * gl * m)); gr.addColorStop(1f, hslC(hh, 65f, 0f))
                q.fillStyle = gr; q.fillRect(x - r, y - r, r * 2f, r * 2f)
            }
            for (i in 0 until 90) {
                val v = 0.01f + 0.03f * hashF(i * 1.7f + 11f)
                val sz = 0.6f + 2f * hashF(i * 2.9f + 12f)
                val y0 = hashF(i * 4.1f + 13f)
                val ph = y0 - v * t * (1f + R.E * 2f) * 6f
                val cyc = Math.floor(ph.toDouble()).toFloat()
                val yy = ph - cyc
                val xx = hashF(i * 9.3f + 14f + cyc * 5.1f)
                val pphase = hashF(i * 6.1f + 15f) * 6.28f
                val tw = 0.4f + 0.6f * Math.abs(Math.sin(t * 2.0 + pphase)).toFloat()
                q.fillStyle = hslC(hueOf(R, i), 75f, 0.6f * tw * Math.min(1f, gl) * m)
                q.beginPath(); q.arc(wrapX(R, xx * W + Math.sin((t + pphase).toDouble()).toFloat() * 8f * u), yy * H, sz * u * (1f + R.beatP * 0.5f), 0f, 6.283f); q.fill()
            }
        }
        q.globalCompositeOperation = "source-over"
        drawBFs(R, false, cx, cy)
        q.restore()

        drawChar(R)
        q.save()
        q.globalCompositeOperation = "lighter"
        val gr0 = R.lw * (0.22f + R.beatP * 0.1f)
        val grd = q.createRadialGradient(px, py, 0f, px, py, gr0)
        grd.addColorStop(0f, hslC(hueOf(R, 0), 60f, (0.10f + R.beatP * 0.38f) * gl * Math.min(1f, m * 2f))); grd.addColorStop(1f, hslC(hueOf(R, 0), 60f, 0f))
        q.fillStyle = grd; q.fillRect(px - gr0, py - gr0, gr0 * 2f, gr0 * 2f)
        var bi = 0
        var bidx = 0
        while (bi + 1 < R.beats.size) {
            val age = R.beats[bi]
            val str = R.beats[bi + 1]
            if (c.b("waveOn")) {
                val a = 0.9f - age * 1.3f
                if (a > 0f) {
                    q.strokeStyle = hslC(hueOf(R, bidx), 65f, a * 0.8f * Math.min(1f, gl)); q.lineWidth = (2f + a * 3f) * u
                    q.beginPath(); q.ellipse(px, py, age * R.lw * 0.55f * 1.1f, age * R.lw * 0.55f * 0.8f, -0.5f, 0f, 6.283f); q.stroke()
                }
            }
            bi += 2; bidx++
        }
        if (c.b("grassOn")) {
            val gy = Math.min(H, R.ly + R.lh) * 0.86f
            val gg = q.createLinearGradient(0f, gy - H * 0.14f, 0f, H)
            gg.addColorStop(0f, Color.argb(0, 0, 0, 0)); gg.addColorStop(1f, hslC(150f, 55f, clampF((0.10f + R.E * 0.22f + R.beatP * 0.15f) * gl * m, 0f, 1f)))
            q.fillStyle = gg; q.fillRect(0f, gy - H * 0.14f, W, H - gy + H * 0.14f)
            bi = 0; bidx = 0
            while (bi + 1 < R.beats.size) {
                val age = R.beats[bi]
                val a = 0.8f - age * 1.1f
                if (a > 0f) {
                    val rad = age * R.lw * 0.6f
                    q.strokeStyle = hslC(hueOf(R, bidx + 2), 65f, a * 0.55f * Math.min(1f, gl)); q.lineWidth = 2f * u
                    q.beginPath(); q.ellipse(cx, Math.min(H * 0.96f, R.ly + R.lh * 0.93f), rad, rad * 0.16f, 0f, 0f, 6.283f); q.stroke()
                }
                bi += 2; bidx++
            }
        }
        if (c.b("partOn")) { // sparks born on every beat
            bi = 0
            while (bi + 1 < R.beats.size) {
                val age = R.beats[bi]
                val life = 1f - age * 1.2f
                if (life > 0f) {
                    val seed = Math.round((t - age) * 100f)
                    for (i in 0 until 14) {
                        val an = hashF(seed * 0.37f + i * 1.9f) * 6.28f
                        val spd = 60f + hashF(seed * 0.71f + i * 3.3f + 9f) * 160f
                        val sx = Math.cos(an.toDouble()).toFloat() * spd * age * u
                        val sy = (Math.sin(an.toDouble()).toFloat() * spd - 40f) * age * u + 30f * age * age * u
                        q.fillStyle = hslC(hueOf(R, i), 80f, life * 0.9f * Math.min(1f, gl))
                        q.beginPath(); q.arc(px + sx, py + sy, (1f + life * 2f) * u, 0f, 6.283f); q.fill()
                    }
                }
                bi += 2
            }
        }
        q.globalCompositeOperation = "source-over"
        drawBFs(R, true, cx, cy)
        if (c.b("fgOn")) {
            if (R.fg == null) R.fg = grassTile()
            val s = W / 1200f * 1.25f
            val tw = 1200f * s
            val th = 220f * s
            val x0 = (((cam.fs % tw) + tw) % tw) - tw
            q.globalAlpha = 0.92f
            for (i in 0 until 3) q.drawImage(R.fg, x0 + i * tw, H * 1.01f - th, tw, th)
            q.globalAlpha = 1f
        }
        if (c.b("partOn")) {
            q.globalCompositeOperation = "lighter"
            val WW = W * 1.5f
            for (i in 0 until 14) {
                val sz = 0.025f + 0.05f * hashF(i * 3.1f + 1f)
                val bx = hashF(i * 11.9f + 4f)
                val by = hashF(i * 7.3f + 3f)
                val pphase = hashF(i * 2.3f + 5f) * 6.28f
                val r = sz * W * 1.8f
                val x = md(bx * WW + cam.fs + Math.sin(t * 0.4 + pphase).toFloat() * 20f, WW) - W * 0.25f
                val y = (by * 0.9f + 0.05f) * H
                val gr = q.createRadialGradient(x, y, 0f, x, y, r)
                gr.addColorStop(0f, hslC(hueOf(R, i + 2), 70f, 0.11f * gl * m)); gr.addColorStop(1f, hslC(hueOf(R, i + 2), 70f, 0f))
                q.fillStyle = gr; q.fillRect(x - r, y - r, r * 2f, r * 2f)
            }
            q.globalCompositeOperation = "source-over"
        }
        q.restore()
        q.restore()
        val vg = c.f("bgVig") / 100f
        if (vg > 0f) {
            val rg = q.createRadialGradient(W / 2f, H / 2f, Math.min(W, H) * 0.3f, W / 2f, H / 2f, Math.hypot(W.toDouble(), H.toDouble()).toFloat() * 0.55f)
            rg.addColorStop(0f, Color.argb(0, 0, 0, 10)); rg.addColorStop(1f, Color.argb(Math.round(clampF(vg * 0.85f, 0f, 1f) * 255f), 0, 0, 10))
            q.fillStyle = rg; q.fillRect(0f, 0f, W, H)
        }
        drawWM(R)
        if (c.b("eqOn")) {
            q.globalCompositeOperation = "lighter"
            val n = 40
            val bw = W / (n * 2 + 1)
            val maxH = H * c.f("eqH") / 100f * Math.min(1.6f, sens * 0.9f + 0.3f) / 0.9f
            for (i in 0 until n) {
                val v = Math.min(1f, R.bars[i] * 1.25f) * m
                val hgt = Math.max(2f * u, v * maxH)
                for (side in 0..1) {
                    val x = if (side == 1) W / 2f + i * bw + bw * 0.15f else W / 2f - (i + 1) * bw
                    val gd = q.createLinearGradient(0f, H - hgt, 0f, H)
                    gd.addColorStop(0f, hslC(hueOf(R, i), 65f, 0.85f * Math.min(1f, gl))); gd.addColorStop(1f, hslC(hueOf(R, i + 1), 60f, 0.1f))
                    q.fillStyle = gd; q.fillRect(x, H - hgt - 4f * u, bw * 0.7f, hgt)
                }
            }
            q.globalCompositeOperation = "source-over"
        }
        if (m < 1f) { q.fillStyle = Color.argb(Math.round((1f - m) * 255f), 0, 0, 0); q.fillRect(0f, 0f, W, H) }
        g.drawImage(R.main, (EQ_W - R.W) / 2f, 0f)
    }

    private fun wrapX(R: Run, x: Float): Float = md(x + R.cam.ms, R.W.toFloat())

    // ---------------------------------------------------------------- camera

    private val SHOT = arrayOf(
        floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(.12f, 0f, -1f, .3f), floatArrayOf(.26f, .2f, -.6f, .2f), floatArrayOf(.42f, 1f, 0f, -.1f),
        floatArrayOf(.56f, 1f, .7f, -.15f), floatArrayOf(.70f, .9f, 1f, 0f), floatArrayOf(.84f, .25f, .5f, .2f), floatArrayOf(1f, 0f, 0f, 0f)
    )
    private val CAMM = mapOf(
        "orbit" to floatArrayOf(1.2f, .18f, 0f, .012f), "mix" to floatArrayOf(.85f, .12f, .5f, .01f), "slow" to floatArrayOf(.45f, .07f, 0f, .004f),
        "cinema" to floatArrayOf(1f, .24f, 0f, .016f), "hand" to floatArrayOf(.15f, .02f, 1f, .004f)
    )

    private fun shotAt(u: Float): FloatArray {
        var i = 0
        while (i < SHOT.size - 2 && u > SHOT[i + 1][0]) i++
        val a = SHOT[i]
        val b = SHOT[i + 1]
        val f = sm((u - a[0]) / (b[0] - a[0]))
        return floatArrayOf(a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f)
    }

    private fun camera(R: Run) {
        val c = R.c
        val W = R.W.toFloat()
        val H = R.H.toFloat()
        val mode = c.s("camMode", "orbit360")
        val a = c.f("camAmt")
        val t = R.t * c.f("camSpd")
        var x = 0f; var y = 0f; var r = 0f; var orb = 0f; var dy = 0f; var dol = 0f; var k = 0f
        var bs = 0f; var ms = 0f; var fs = 0f
        val cam = R.cam
        cam.va = Float.NaN
        val vch = R.chr
        val hasViews = vch != null && vch.views.any { v -> v != null }
        val shake = { q: Float ->
            x = (Math.sin(t * 1.7) + Math.sin(t * 2.9 + 1.3) * .6).toFloat() * 0.003f * W * q * a
            y = (Math.sin(t * 2.1 + .5) + Math.sin(t * 3.3) * .6).toFloat() * 0.003f * H * q * a
        }
        if (mode == "orbit360") {
            val th = t * (Math.PI * 2 / 24).toFloat()
            orb = Math.sin(th.toDouble()).toFloat() * 1.4f * a
            if (hasViews) { cam.va = th; orb = 0f }
            dy = Math.sin(th * 0.7).toFloat() * 0.25f * a
            r = Math.sin(th.toDouble()).toFloat() * 0.012f * a
            bs = -th / (Math.PI * 2).toFloat() * W * 2.2f * a; ms = bs * 0.45f; fs = -bs * 0.9f
            shake(0.5f)
        } else if (mode == "cine3d") {
            val s = shotAt(((t / 20f) % 1f))
            k = s[0] * Math.min(1f, a); orb = s[1] * a; dy = s[2] * a
            dol = (0.5f + 0.5f * Math.sin(t * 0.7).toFloat()) * 0.05f * a * (1f - k); r = Math.sin(t * 0.31).toFloat() * 0.008f * a
            shake(0.5f)
            if (c.b("camBeat")) dol += R.beatP * 0.02f
        } else {
            val P = CAMM[mode]
            if (P != null) {
                orb = Math.sin(t * 0.45).toFloat() * P[0] * a; dy = Math.sin(t * 0.3 + 0.7).toFloat() * P[0] * 0.5f * a
                dol = (0.5f + 0.5f * Math.sin(t * 0.32 + 1).toFloat()) * P[1] * a; r = Math.sin(t * 0.4).toFloat() * P[3] * a
                if (P[2] > 0f) { shake(P[2]); r += Math.sin(t * 1.3 + 2).toFloat() * 0.0035f * P[2] * a }
                if (c.b("camBeat")) dol += R.beatP * 0.025f
            }
        }
        val zc = 1f + (c.f("camClose") - 1f) * k
        cam.x = x; cam.y = y; cam.r = r; cam.orb = orb; cam.dy = dy; cam.dol = dol; cam.bs = bs; cam.ms = ms
        cam.fs = if (fs != 0f) fs else orb * W * 0.2f
        cam.fm = k; cam.z = c.f("camZoom") / 100f * (1f + dol) * zc
    }

    // ---------------------------------------------------------------- background photos

    private fun bgKey(R: Run): String {
        val c = R.c
        return listOf(R.W, R.H, c.f("bgBright"), c.f("bgContrast"), c.f("bgSat"), c.f("bgHue"), c.f("bgBlur"), c.f("bgFeather"), c.f("bgFadeBot"), c.b("bgFlip")).joinToString("|")
    }

    private fun buildBg(R: Run, it: Bg) {
        val c = R.c
        val im = it.bmp
        val sc = Math.max(R.W.toFloat() / im.width, R.H.toFloat() / im.height)
        val bw = Math.ceil((im.width * sc).toDouble()).toInt()
        val bh = Math.ceil((im.height * sc).toDouble()).toInt()
        val bl = c.f("bgBlur") * R.W / 540f
        val p = Math.round(Math.max(bw, bh) * 0.03f + bl * 2f)
        val cw = Math.max(8, bw + 2 * p)
        val chh = Math.max(8, bh + 2 * p)
        var cv = Cv(cw, chh)
        val cm = ColorMatrix()
        val b = (100f + c.f("bgBright")) / 100f
        cm.postConcat(ColorMatrix(floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val k = c.f("bgContrast") / 100f
        val tr = 128f * (1f - k)
        cm.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, tr, 0f, k, 0f, 0f, tr, 0f, 0f, k, 0f, tr, 0f, 0f, 0f, 1f, 0f)))
        val sat = ColorMatrix(); sat.setSaturation(c.f("bgSat") / 100f); cm.postConcat(sat)
        if (c.f("bgHue") != 0f) cm.postConcat(hueMatrix(c.f("bgHue")))
        val pt = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        pt.colorFilter = ColorMatrixColorFilter(cm)
        val cn = Canvas(cv.bmp)
        if (c.b("bgFlip")) { cn.translate(cw.toFloat(), 0f); cn.scale(-1f, 1f) }
        cn.drawBitmap(im, null, RectF(0f, 0f, cw.toFloat(), chh.toFloat()), pt)
        if (bl > 1f) {
            val kk = 1f / (1f + bl / 3f)
            val sw = Math.max(8, Math.round(cw * kk))
            val sh = Math.max(8, Math.round(chh * kk))
            val small = Bitmap.createScaledBitmap(cv.bmp, sw, sh, true)
            val out = Cv(cw, chh)
            out.g.drawImage(small, 0f, 0f, cw.toFloat(), chh.toFloat())
            cv = out
        }
        val x = cv.g
        val f = c.f("bgFeather") / 100f * Math.min(cw, chh) * 0.5f
        x.globalCompositeOperation = "destination-in"
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
            val gd = x.createLinearGradient(x0, y0, x1, y1)
            gd.addColorStop(0f, Color.argb(0, 0, 0, 0)); gd.addColorStop(1f, Color.argb(255, 0, 0, 0))
            x.fillStyle = gd; x.fillRect(0f, 0f, cw.toFloat(), chh.toFloat())
        }
        if (f > 1f) { edge(0f, 0f, f, 0f); edge(cw.toFloat(), 0f, cw - f, 0f); edge(0f, 0f, 0f, f); edge(0f, chh.toFloat(), 0f, chh - f) }
        val fb = c.f("bgFadeBot") / 100f * chh * 0.8f
        if (fb > 1f) edge(0f, chh.toFloat(), 0f, chh - fb)
        x.globalCompositeOperation = "source-over"
        it.cache = cv
        it.key = bgKey(R)
    }

    private fun bgSlide(R: Run, i: Int, a: Float) {
        val it = R.bgs.getOrNull(i) ?: return
        if (a <= 0f) return
        if (it.cache == null || it.key != bgKey(R)) buildBg(R, it)
        val cache = it.cache ?: return
        val c = R.c
        val q = R.main.g
        val W = R.W.toFloat(); val H = R.H.toFloat()
        val kb = c.f("bgKB") / 100f
        val t = R.t
        val z = c.f("bgZoom") / 100f * (1f + kb * 0.07f * (0.5f + 0.5f * Math.sin(t * 0.17 + i * 1.7).toFloat())) * (1f + c.f("bgBeat") / 100f * R.beatP * 0.035f)
        val dx = Math.sin(t * 0.13 + i * 2).toFloat() * kb * 0.03f * W
        val dy = Math.cos(t * 0.11 + i * 1.3).toFloat() * kb * 0.03f * H
        q.save(); q.globalAlpha = clampF(a * c.f("bgOp") / 100f, 0f, 1f)
        q.translate(W / 2f + c.f("bgX") / 100f * W + dx, H / 2f + c.f("bgY") / 100f * H + dy); q.scale(z, z)
        val cw2 = cache.w.toFloat(); val ch2 = cache.h.toFloat()
        if (R.cam.bs != 0f) {
            val sc = md(R.cam.bs / z, 2f * cw2)
            for (k in -3..2) {
                val pos = sc + k * cw2 - cw2 / 2f
                if (((k % 2) + 2) % 2 == 1) { q.save(); q.translate(pos + cw2, 0f); q.scale(-1f, 1f); q.drawImage(cache, 0f, -ch2 / 2f); q.restore() }
                else q.drawImage(cache, pos, -ch2 / 2f)
            }
        } else q.drawImage(cache, -cw2 / 2f, -ch2 / 2f)
        q.restore()
    }

    private fun drawBg(R: Run) {
        val c = R.c
        val q = R.main.g
        val W = R.W.toFloat(); val H = R.H.toFloat()
        q.globalCompositeOperation = "source-over"; q.globalAlpha = 1f
        q.fillStyle = Color.rgb(2, 6, 15); q.fillRect(-W, -H, 3f * W, 3f * H)
        if (R.bgs.isEmpty()) { q.drawImage(R.fill, -W * 0.15f, -H * 0.15f, W * 1.3f, H * 1.3f); return }
        val n = R.bgs.size
        val iv = c.f("bgInterval")
        val fd = Math.min(c.f("bgFade"), iv * 0.5f)
        var idx = Math.min(c.j.optInt("bgSel", 0), n - 1)
        var nxt = -1
        var mix = 0f
        if (n > 1 && c.b("bgShow")) {
            idx = Math.floor((R.t / iv).toDouble()).toInt() % n
            val lt = R.t % iv
            if (lt > iv - fd) { nxt = (idx + 1) % n; mix = sm((lt - (iv - fd)) / fd) }
        }
        bgSlide(R, idx, 1f)
        if (nxt >= 0) bgSlide(R, nxt, mix)
        for (i in 0 until n) if (i != idx && i != nxt) R.bgs[i].cache = null
        val d = c.f("bgDark") / 100f
        if (d > 0f) { q.fillStyle = Color.argb(Math.round(d * 255f), 0, 0, 8); q.fillRect(-W, -H, 3f * W, 3f * H) }
        val ta = c.f("bgTintAmt") / 100f
        if (ta > 0f) {
            val tc = R.st.p("accent")
            q.globalAlpha = ta * 0.7f; q.fillStyle = Color.rgb(tc[0], tc[1], tc[2]); q.globalCompositeOperation = "soft-light"
            q.fillRect(-W, -H, 3f * W, 3f * H)
            q.globalCompositeOperation = "source-over"; q.globalAlpha = ta * 0.25f; q.fillRect(-W, -H, 3f * W, 3f * H); q.globalAlpha = 1f
        }
    }

    // ---------------------------------------------------------------- butterflies

    private fun bfSprite(R: Run, h0: Float): Cv {
        val h = Math.round(h0 / 5f) * 5
        R.bf[h]?.let { return it }
        val cv = Cv(192, 192)
        val x = cv.g
        x.translate(96f, 96f)
        x.shadowColor = hslC(h.toFloat(), 60f, 0.9f); x.shadowBlur = 12f
        for (side in intArrayOf(-1, 1)) {
            x.save(); x.scale(side * 68f, 68f)
            x.fillStyle = hslC(h.toFloat(), 70f, 0.45f); x.strokeStyle = hslC(h.toFloat(), 88f, 0.95f); x.lineWidth = 2.2f / 68f
            x.beginPath(); x.moveTo(0f, 0f); x.bezierCurveTo(.2f, -.6f, 1.1f, -.9f, 1f, -.2f); x.bezierCurveTo(.95f, .15f, .35f, .2f, 0f, 0f); x.fill(); x.stroke()
            x.beginPath(); x.moveTo(0f, 0f); x.bezierCurveTo(.4f, .15f, .9f, .35f, .65f, .75f); x.bezierCurveTo(.45f, .95f, .12f, .5f, 0f, 0f); x.fill(); x.stroke()
            x.restore()
        }
        x.shadowBlur = 0f; x.strokeStyle = hslC(h.toFloat(), 95f, 0.9f); x.lineWidth = 3f
        x.beginPath(); x.moveTo(0f, -15f); x.lineTo(0f, 34f); x.stroke()
        R.bf[h] = cv
        return cv
    }

    private fun bfPos(R: Run, i: Int, a: Float, cx: Float, cy: Float): FloatArray {
        val type = i % 3
        val off = hashF(i * 5.3f + 21f) * 6.28f
        val yo = hashF(i * 8.1f + 22f)
        val iw = R.lw; val ih = R.lh
        val W = R.W.toFloat(); val H = R.H.toFloat()
        if (type == 0) return floatArrayOf(cx + Math.cos(a.toDouble()).toFloat() * iw * (0.55f + 0.1f * Math.sin(off.toDouble()).toFloat()), cy + (yo - 0.5f) * ih * 0.5f + Math.sin(a.toDouble()).toFloat() * ih * 0.2f)
        if (type == 1) return floatArrayOf(W * (0.5f + 0.58f * Math.sin((a * 0.5 + off).toDouble()).toFloat()), H * (0.12f + 0.65f * yo) + Math.sin((a * 2 + off).toDouble()).toFloat() * H * 0.05f)
        val r = iw * (0.3f + 0.28f * Math.sin((a * 0.3 + off).toDouble()).toFloat())
        return floatArrayOf(cx + Math.cos(a.toDouble()).toFloat() * r * 1.15f, cy - ih * 0.08f + Math.sin(a.toDouble()).toFloat() * r * 0.85f + (yo - 0.5f) * ih * 0.3f)
    }

    private fun drawBFs(R: Run, front: Boolean, cx: Float, cy: Float) {
        val c = R.c
        val q = R.main.g
        val gl = c.f("glow")
        val u = R.W / 540f
        val n = Math.round(c.f("bf")) * (if (R.m > 0.02f) 1 else 0)
        val spd = c.f("spd")
        val t = R.t
        q.globalCompositeOperation = "lighter"
        for (i in 0 until Math.min(24, n)) {
            val sz = 0.8f + 0.6f * hashF(i * 3.7f + 23f)
            val off = hashF(i * 5.3f + 21f) * 6.28f
            val fl0 = hashF(i * 1.3f + 24f) * 6f
            val rate = spd * (0.55f + 0.5f * 1f) * (0.7f + 0.3f * sz)
            fun aAt(tt: Float): Float = off + tt * rate
            val a = aAt(t)
            val isFront = if (i % 3 == 1) i % 2 == 0 else Math.sin(a.toDouble()) > 0
            if (isFront != front) continue
            val h = hueOf(R, i)
            val trN = 18
            var prev: FloatArray? = null
            for (k in 0 until trN) {
                val tt = t - (trN - 1 - k) / 60f
                val p = bfPos(R, i, aAt(tt), cx, cy)
                if (prev != null) {
                    val f = k.toFloat() / trN
                    q.strokeStyle = hslC(h, 65f, f * 0.55f * Math.min(1f, gl) * R.m); q.lineWidth = (1f + f * 2.5f) * u
                    q.beginPath(); q.moveTo(prev[0], prev[1]); q.lineTo(p[0], p[1]); q.stroke()
                }
                prev = p
            }
            val p0 = bfPos(R, i, a, cx, cy)
            val p1 = bfPos(R, i, a + 0.02f, cx, cy)
            val ang = Math.atan2((p1[1] - p0[1]).toDouble(), (p1[0] - p0[0]).toDouble()).toFloat() + Math.PI.toFloat() / 2f
            val s = (20f + 10f * sz) * u * (1f + R.beatP * 0.15f)
            val flap = fl0 + t * (9f + R.E * 14f)
            val w = 0.2f + 0.8f * Math.abs(Math.cos(flap.toDouble())).toFloat()
            val kk = s / 68f
            q.save(); q.translate(p0[0], p0[1]); q.rotate(ang); q.scale(kk * w, kk)
            q.globalAlpha = clampF(0.55f + 0.45f * gl, 0f, 1f) * clampF(R.m * 3f, 0f, 1f)
            q.drawImage(bfSprite(R, h), -96f, -96f)
            q.restore()
        }
        q.globalAlpha = 1f; q.globalCompositeOperation = "source-over"
    }

    private fun grassTile(): Cv {
        val cv = Cv(1200, 220)
        val q = cv.g
        var sd = 7L
        fun rnd(): Float { sd = (sd * 16807L) % 2147483647L; return sd.toFloat() / 2147483647f }
        for (i in 0 until 110) {
            val bx = rnd() * 1200f
            val bh = 50f + rnd() * 165f
            val bw = 7f + rnd() * 13f
            val lean = (rnd() - 0.5f) * 60f
            for (off in floatArrayOf(-1200f, 0f, 1200f)) {
                q.beginPath(); q.moveTo(bx + off - bw, 220f)
                q.quadraticCurveTo(bx + off - bw * 0.2f, 220f - bh * 0.55f, bx + off + lean, 220f - bh)
                q.quadraticCurveTo(bx + off + bw * 0.2f, 220f - bh * 0.5f, bx + off + bw, 220f)
                q.closePath()
                q.fillStyle = if (i % 5 == 0) Color.argb(242, 6, 40, 34) else Color.argb(245, 3, 18, 16); q.fill()
                if (i % 3 == 0) { q.strokeStyle = Color.argb(71, 90, 230, 255); q.lineWidth = 1.5f; q.stroke() }
            }
        }
        return cv
    }

    // ---------------------------------------------------------------- character

    private fun drawHand(bx: Ctx2D, hl: HzHand?, mdx: Float, mdy: Float, ang: Float, rip: Float, dxA: FloatArray, dyA: FloatArray, sh: Float) {
        if (hl == null) return
        val n = dxA.size
        val k = HzProc.clampI(Math.floor(((hl.y0 + hl.py) / sh).toDouble()).toInt(), 0, n - 1)
        bx.save(); bx.translate(hl.x0 + hl.px + dxA[k] + mdx, hl.y0 + hl.py + dyA[k] + mdy); bx.rotate(ang)
        bx.scale(1f + rip, 1f - rip * 0.8f)
        bx.drawImage(hl.bmp, -hl.px, -hl.py)
        bx.restore()
    }

    private class VP(val bmp: Bitmap, val flip: Boolean, val w: Float, val sx: Float)

    private fun viewParts(R: Run, chr: HzChar): List<VP>? {
        R.frontW = 1f
        val cam = R.cam
        if (cam.va.isNaN()) return null
        val PI = Math.PI.toFloat()
        class E(val a: Float, val bmp: Bitmap, val flip: Boolean)
        val L = ArrayList<E>()
        L.add(E(0f, R.chBuf!!.bmp, false))
        val vr = chr.views[0]; val vb = chr.views[1]; val vl = chr.views[2]
        if (vr != null) L.add(E(PI / 2f, vr, false))
        if (vb != null) L.add(E(PI, vb, false))
        if (vl != null) L.add(E(PI * 1.5f, vl, false)) else if (vr != null) L.add(E(PI * 1.5f, vr, true))
        if (L.size < 2) return null
        val a = if (vb != null) md(cam.va, 2f * PI) else md(Math.sin(cam.va.toDouble()).toFloat() * PI / 2f, 2f * PI)
        L.sortBy { it.a }
        var i = 0
        while (i < L.size - 1 && L[i + 1].a <= a) i++
        val lo = L[i]
        val hi = L[(i + 1) % L.size]
        val hiA = if (hi.a <= lo.a) hi.a + 2f * PI else hi.a
        val phi = clampF((a - lo.a) / (hiA - lo.a), 0f, 1f)
        val P = listOf(
            VP(lo.bmp, lo.flip, 1f - sm(phi), Math.max(0.15f, Math.cos((phi * PI / 2f).toDouble()).toFloat())),
            VP(hi.bmp, hi.flip, sm(phi), Math.max(0.15f, Math.sin((phi * PI / 2f).toDouble()).toFloat()))
        )
        var fw = 0f
        for (p in P) if (p.bmp === R.chBuf!!.bmp) fw += p.w
        R.frontW = fw
        return P
    }

    private fun drawChar(R: Run) {
        val chr = R.chr ?: return
        val buf = R.chBuf ?: return
        val c = R.c
        val q = R.main.g
        val t = R.t
        val mv = c.f("mov")
        val cw = chr.cw.toFloat(); val ch = chr.ch.toFloat()
        val N = 96
        val sh = ch / N
        val iy = c.iy * ch
        val sway = Math.sin(t * 1.6).toFloat()
        val nod = Math.sin(t * 1.6 + 1.2).toFloat()
        val breath = Math.sin(t * 1.1).toFloat()
        val bx = buf.g
        buf.clear()
        val dxA = FloatArray(N)
        val dyA = FloatArray(N)
        val E = R.E
        for (k in 0 until N) {
            val y = (k + 0.5f) / N
            val h = 1f - y
            val hh = Math.pow(h.toDouble(), 1.7).toFloat()
            var dx = sway * cw * 0.0055f * mv * hh
            if (y < 0.35f) dx += nod * cw * 0.0045f * mv * Math.pow((1f - y / 0.35f).toDouble(), 2.0).toFloat()
            val zy = Math.abs((k * sh + sh / 2f) - iy) / (ch * 0.22f)
            if (zy < 1f) { val z = 1f - zy; dx += (Math.sin(t * 28.0) * 0.35 + Math.sin(t * 11.0)).toFloat() * cw * 0.0022f * mv * z * (R.beatP * 1.6f + 0.25f * E) }
            dxA[k] = dx; dyA[k] = breath * ch * 0.0018f * mv * hh
            bx.drawImage(chr.charCv, 0f, k * sh, cw, sh + 0.6f, dx, k * sh + dyA[k], cw, sh + 1.2f)
        }
        run {
            val T = (Math.PI * 2).toFloat()
            val on = c.b("handsOn")
            val phi = t * T / (0.62f / 2f)
            var nx = (c.fx - c.sx) * cw
            var ny = (c.fy - c.sy) * ch
            val ln = Math.hypot(nx.toDouble(), ny.toDouble()).toFloat().let { if (it == 0f) 1f else it }
            nx /= ln; ny /= ln
            var qx = -ny; var qy = nx
            if (qy < 0f) { qx = -qx; qy = -qy }
            val act = if (on) clampF(0.3f + 0.7f * Math.min(1f, E * 2f + R.beatP), 0f, 1f) * c.f("handAmt") else 0f
            val A = 0.022f * cw * act
            val sp = Math.sin(phi.toDouble()).toFloat()
            val cphi = Math.cos(phi.toDouble()).toFloat()
            drawHand(bx, chr.hands[0], qx * sp * A + nx * cphi * A * 0.35f, qy * sp * A + ny * cphi * A * 0.35f, sp * 0.16f * Math.min(1.5f, act), Math.sin(phi * 2.0).toFloat() * 0.012f * Math.min(1.5f, act), dxA, dyA, sh)
            // left hand slides along the neck on the beats
            val tgts = floatArrayOf(-1f, .35f, 1f, -.45f, .7f, -.8f)
            var lastAge = 99f
            var idxB = 0
            var bi = 0
            while (bi + 1 < R.beats.size) { if (R.beats[bi] < lastAge) { lastAge = R.beats[bi] }; bi += 2 }
            val bt = t - (if (lastAge < 99f) lastAge else 99f)
            idxB = Math.floor((bt * 3f).toDouble()).toInt()
            val cur = tgts[Math.abs(idxB) % 6]
            val prv = tgts[Math.abs(idxB - 1) % 6]
            val fretPos = if (lastAge < 99f) prv + (cur - prv) * (1f - Math.exp((-9f * lastAge).toDouble()).toFloat()) else cur
            val gg = 0.016f * cw * act * fretPos
            val pr = (0.003f * cw * Math.sin(t * 11.0 + 1).toFloat() * (E + R.beatP) + 0.012f * cw * R.beatP) * act
            drawHand(bx, chr.hands[1], nx * gg + qx * pr, ny * gg + qy * pr, fretPos * 0.025f * Math.min(1.5f, act), Math.sin(t * 10.0).toFloat() * 0.012f * Math.min(1.5f, act) * (0.3f + E + R.beatP), dxA, dyA, sh)
        }
        q.save()
        q.globalCompositeOperation = c.s("blend", "lighten")
        q.globalAlpha = c.f("charOp") / 100f * clampF(R.m * 1.5f, 0f, 1f)
        val sc = 1f + R.beatP * 0.008f * mv
        val iw = R.lw; val ih = R.lh
        q.translate(R.lx + iw / 2f, R.ly + ih); q.scale(sc, sc); q.translate(-(R.lx + iw / 2f), -(R.ly + ih))
        val vp = viewParts(R, chr)
        if (vp == null) {
            val K = 28
            val col = cw / K
            val pp = c.f("camPar") / 100f
            for (i in 0 until K) {
                val u = (i + 0.5f) / K
                val d = Math.exp((-Math.pow(((u - 0.45f) / 0.24f).toDouble(), 2.0))).toFloat()
                val dx = R.cam.orb * R.W * 0.024f * pp * d
                q.drawImage(buf, i * col, 0f, col + 1f, ch, R.lx + i * iw / K + dx, R.ly, iw / K + 1.3f, ih)
            }
        } else {
            val cxm = R.lx + iw * 0.5f
            val a0 = q.globalAlpha
            for (p in vp) {
                if (p.w < 0.01f) continue
                q.save(); q.globalAlpha = a0 * p.w
                q.translate(cxm, 0f); q.scale(p.sx * (if (p.flip) -1f else 1f), 1f); q.translate(-cxm, 0f)
                q.drawImage(p.bmp, R.lx, R.ly, iw, ih)
                q.restore()
            }
        }
        q.restore()
        val halo = chr.halo
        if (halo != null && c.f("neon") > 0f) {
            q.save(); q.globalCompositeOperation = "lighter"
            q.globalAlpha = clampF((0.05f + R.beatP * 0.35f + E * 0.18f) * c.f("neon") * c.f("glow") * R.m * c.f("charOp") / 100f * R.frontW, 0f, 1f)
            q.drawImage(halo, R.lx, R.ly, iw, ih)
            q.restore()
        }
    }

    // ---------------------------------------------------------------- moving name

    private fun drawWM(R: Run) {
        val c = R.c
        val txt = R.st.name.trim()
        if (!c.b("wmOn") || txt.isEmpty()) return
        val q = R.main.g
        val W = R.W.toFloat(); val H = R.H.toFloat()
        val t = R.t
        val fs = c.f("wmSize") / 100f * W
        q.save()
        val arabic = txt.any { it.code in 0x0600..0x06FF }
        q.fontFamily = Typeface.SANS_SERIF; q.direction = "rtl"; q.setFont(700, fs)
        q.textAlign = "center"; q.textBaseline = "middle"
        val x = W / 2f + Math.sin(t * 0.31).toFloat() * W * 0.12f
        val y = H * (0.5f + 0.38f * Math.sin(t * 0.17 + 1).toFloat())
        q.translate(x, y); q.rotate(Math.sin(t * 0.4).toFloat() * 0.03f)
        if (!arabic) q.transform(1f, 0f, -0.2f, 1f, 0f, 0f)
        q.scale(c.f("wmWidth") / 100f, 1f)
        val col = R.st.p("text")
        val gd = q.createLinearGradient(0f, -fs * 0.5f, 0f, fs * 0.5f)
        gd.addColorStop(0f, Color.WHITE); gd.addColorStop(0.45f, Color.rgb(col[0], col[1], col[2])); gd.addColorStop(1f, Color.rgb(0x8a, 0x5a, 0x14))
        q.globalAlpha = c.f("wmOp") / 100f
        q.shadowColor = Color.rgb(col[0], col[1], col[2]); q.shadowBlur = fs * 0.35f * (1f + R.beatP * 0.6f)
        q.lineWidth = fs * 0.07f; q.strokeStyle = Color.argb(217, 50, 25, 0); q.lineJoin = "round"
        q.strokeText(txt, 0f, 0f)
        q.shadowBlur = 0f; q.fillStyle = gd; q.fillText(txt, 0f, 0f)
        q.restore()
    }
}
