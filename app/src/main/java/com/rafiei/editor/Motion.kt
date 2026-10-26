package com.rafiei.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject

/**
 * Photo-motion library (ported from part 2 of the old HTML app). Every preset is a list of "layers";
 * each layer evaluates one base effect (zoom, pan, shake, ...) into a [MState] at a given time.
 * The transform part is applied through [Fx.dyn]/[Fx.xform] (preview and export alike);
 * the "post" part (flash, light sweep, vignette ...) is drawn by [Motion.drawPost] over the clip.
 */

class MPost {
    var blurK = 0.0; var blur = 0.0; var bdx = 1.0; var bdy = 0.0
    var rgb = 0.0; var rgba = 0.0; var chroma = 0.0; var glitch = 0.0; var grain = 0.0; var vig = 0.0; var exp = 0.0
    var flash = 0.0; var fcol = doubleArrayOf(1.0, 1.0, 1.0)
    var sweep = 0.0; var swp = 0.0; var swW = 0.08; var swA = 0.6; var swcol = doubleArrayOf(1.0, 1.0, 1.0)
    var flare = 0.0; var flx = 0.7; var fly = 0.25
    var leak = 0.0; var burn = 0.0; var glow = 0.0; var soft = 0.0
    var wt = 0; var wamp = 0.0; var wfreq = 3.0; var wph = 0.0; var wcx = 0.5; var wcy = 0.5; var wrad = 0.5
    var kal = 0.0; var kalr = 0.0; var mir = 0.0
    var fo = 0.0; var fox = 0.5; var foy = 0.5; var forad = 0.3; var foratio = 1.0; var fofe = 0.25
    var sp = 0.0; var spx = 0.5; var spy = 0.5; var spw = 0.25; var sph = 0.25; var spb = 0.0
    var strk = 0.0; var strkx = 0.5; var strky = 0.5; var strkth = 0.03; var strklen = 0.46; var strkang = 0.0
    var strkcol = doubleArrayOf(1.0, 0.85, 0.62); var strkspill = 0.7; var strkbands = 1.0
    var beam = 0.0; var beamx = 0.5; var beamy = 0.5; var beamang = 1.7; var beamw = 0.13; var beamsoft = 0.03; var beamdim = 0.9; var beamwarm = 0.6
}

/** Result of evaluating a preset: transform (fractions of the frame, rotation in radians) and post effects. */
class MState {
    var tx = 0.0; var ty = 0.0; var sc = 1.0; var scx = 1.0; var scy = 1.0
    var rot = 0.0; var skx = 0.0; var sky = 0.0; var flx = 1.0; var fly = 1.0; var rx = 0.0; var ry = 0.0
    var alpha = 1.0
    var free = false
    var maskT = 0; var maskP = 1.0
    val post = MPost()
    val anch = ArrayList<(MState) -> Unit>()
}

class MotionPreset(val id: String, val cat: Int, val name: String, val base: String, val params: HashMap<String, Any>, val combo: List<String>?)

/** Parameter bag of one layer. */
class Pm(val m: HashMap<String, Any>) {
    var I = 1.0; var AX = 1.0; var AY = 1.0; var KS = 1.0; var KR = 1.0
    fun d(k: String, def: Double = 0.0): Double {
        val v = m[k]
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: def
            is Boolean -> if (v) 1.0 else 0.0
            else -> def
        }
    }
    fun s(k: String, def: String = ""): String {
        val v = m[k]
        return when (v) {
            null -> def
            is String -> v
            is Number -> { val x = v.toDouble(); if (x == Math.floor(x) && Math.abs(x) < 1e9) x.toLong().toString() else x.toString() }
            else -> v.toString()
        }
    }
    fun i(k: String): Int = d(k).toInt()
    fun b(k: String): Boolean = d(k) != 0.0
    fun arr(k: String): JSONArray? = m[k] as? JSONArray
}

private class MLayer(val base: String, val p: HashMap<String, Any>)
private class LProg(val p: Double, val u: Double, val lt: Double, val env: Double, val am: Double)
private class MCtx(val p: Double, val u: Double, val lt: Double, val env: Double, val t: Double, val dur: Double, val beat: Double, val W: Double, val H: Double) {
    val qw = W; val qh = H; val qwF = 1.0; val qhF = 1.0
}

object Motion {
    val cats: List<Pair<Int, String>> = listOf(
        1 to "زوم و حرکت دوربین", 2 to "چرخش و حرکت زنده", 3 to "ورود و خروج", 4 to "تغییر شکل و عمق", 5 to "نور، تاری و ظاهر",
        6 to "ترکیب افکت‌ها", 7 to "پرتره سینمایی (سوژه)", 8 to "مسیر و عمق", 9 to "حرکت‌های خلاقانه"
    )

    private const val D2R = Math.PI / 180.0
    private const val TAUD = Math.PI * 2.0
    private val DIRS = arrayOf(
        doubleArrayOf(-1.0, 0.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, -1.0), doubleArrayOf(0.0, 1.0),
        doubleArrayOf(-0.7071, -0.7071), doubleArrayOf(0.7071, -0.7071), doubleArrayOf(-0.7071, 0.7071), doubleArrayOf(0.7071, 0.7071)
    )

    private fun clamp(v: Double, a: Double, b: Double) = Math.max(a, Math.min(b, v))
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    private fun fract(x: Double) = x - Math.floor(x)
    private fun hsh(n: Double): Double { val x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x) }
    private fun vn(x: Double, seed: Double): Double {
        val s = seed * 17.3
        val i = Math.floor(x)
        val f = x - i
        val u = f * f * (3 - 2 * f)
        return lerp(hsh(i + s), hsh(i + 1 + s), u) * 2 - 1
    }
    private fun hex2f(h0: String): DoubleArray {
        var h = h0.replace("#", "")
        if (h.length == 3) h = "" + h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
        val n = try { h.toLong(16).toInt() } catch (e: Exception) { 0xffffff }
        return doubleArrayOf(((n shr 16) and 255) / 255.0, ((n shr 8) and 255) / 255.0, (n and 255) / 255.0)
    }

    // ------------------------------------------------------------ easing
    private fun ease(name: String, t: Double): Double {
        return when (name) {
            "linear" -> t
            "in" -> t * t
            "out" -> 1 - (1 - t) * (1 - t)
            "inout" -> if (t < .5) 2 * t * t else 1 - Math.pow(-2 * t + 2, 2.0) / 2
            "sine" -> -(Math.cos(Math.PI * t) - 1) / 2
            "cubicIn" -> t * t * t
            "cubicOut" -> 1 - Math.pow(1 - t, 3.0)
            "cubicInOut" -> if (t < .5) 4 * t * t * t else 1 - Math.pow(-2 * t + 2, 3.0) / 2
            "expoIn" -> if (t == 0.0) 0.0 else Math.pow(2.0, 10 * t - 10)
            "expoOut" -> if (t >= 1) 1.0 else 1 - Math.pow(2.0, -10 * t)
            "expoInOut" -> if (t <= 0) 0.0 else if (t >= 1) 1.0 else if (t < .5) Math.pow(2.0, 20 * t - 10) / 2 else (2 - Math.pow(2.0, -20 * t + 10)) / 2
            "backIn" -> { val c = 1.70158; (c + 1) * t * t * t - c * t * t }
            "backOut" -> { val c = 1.70158; 1 + (c + 1) * Math.pow(t - 1, 3.0) + c * Math.pow(t - 1, 2.0) }
            "elastic" -> if (t <= 0) 0.0 else if (t >= 1) 1.0 else Math.pow(2.0, -10 * t) * Math.sin((t * 10 - .75) * (TAUD / 3)) + 1
            "bounce" -> {
                val n = 7.5625
                val d = 2.75
                var x = t
                if (x < 1 / d) n * x * x
                else if (x < 2 / d) { x -= 1.5 / d; n * x * x + .75 }
                else if (x < 2.5 / d) { x -= 2.25 / d; n * x * x + .9375 }
                else { x -= 2.625 / d; n * x * x + .984375 }
            }
            "smooth" -> t * t * (3 - 2 * t)
            else -> -(Math.cos(Math.PI * t) - 1) / 2
        }
    }

    private fun bez(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
        if (t <= 0) return 0.0
        if (t >= 1) return 1.0
        val cx = 3 * x1; val bx = 3 * (x2 - x1) - cx; val ax = 1 - cx - bx
        val cy = 3 * y1; val by = 3 * (y2 - y1) - cy; val ay = 1 - cy - by
        var s = t
        for (i in 0 until 8) {
            val e = ((ax * s + bx) * s + cx) * s - t
            if (Math.abs(e) < 1e-5) break
            val d = (3 * ax * s + 2 * bx) * s + cx
            if (Math.abs(d) < 1e-6) break
            s -= e / d
        }
        s = clamp(s, 0.0, 1.0)
        return ((ay * s + by) * s + cy) * s
    }

    private fun easeOf(P: Pm, t0: Double): Double {
        val t = clamp(t0, 0.0, 1.0)
        if (P.s("ease") == "bezier") {
            val b = P.arr("bez")
            return if (b != null && b.length() >= 4) bez(b.optDouble(0), b.optDouble(1), b.optDouble(2), b.optDouble(3), t) else bez(.25, .1, .25, 1.0, t)
        }
        return ease(P.s("ease", "sine"), t)
    }

    // ------------------------------------------------------------ definitions
    private fun mp(vararg p: Pair<String, Any>): HashMap<String, Any> {
        val h = HashMap<String, Any>()
        for ((k, v) in p) h[k] = if (v is Int) v.toDouble() else if (v is Float) v.toDouble() else v
        return h
    }

    private val SUBJ_DEF = arrayOf<Pair<String, Any>>("su" to 0.5, "sv" to 0.46, "eu" to 0.5, "ev" to 0.46, "track" to 1.0)

    private val CD: HashMap<String, Any> = mp(
        "opacity" to 100, "inten" to 100, "ax" to 100, "ay" to 100, "ks" to 100, "kr" to 100, "beat" to 0, "speed" to 1, "repeat" to 1,
        "loop" to "once", "end" to "hold", "ease" to "sine", "t0" to 0, "t1" to 1
    )

    private val DEFS: HashMap<String, HashMap<String, Any>> by lazy {
        val d = HashMap<String, HashMap<String, Any>>()
        d["zoom"] = mp("zoom" to 30, "zdir" to 1, "fx" to .5, "fy" to .5, "cf" to 0, "fadeEnd" to 0)
        d["zoomosc"] = mp("zoom" to 8, "mode" to "pulse", "freq" to 2, "lock" to 0, "fx" to .5, "fy" to .5)
        d["push"] = mp("zoom" to 25, "zdir" to 1, "tilt" to 4)
        d["pan"] = mp("dist" to 12, "dir" to 1, "usePts" to 0, "sx" to .4, "sy" to .5, "ex" to .6, "ey" to .5)
        d["kenburns"] = mp("zoom" to 25, "z0" to 0, "sx" to .38, "sy" to .42, "ex" to .62, "ey" to .55)
        d["randpan"] = mp("dist" to 10, "freq" to .35, "seed" to 1)
        d["arc"] = mp("dist" to 16, "dir" to 1, "bend" to .6)
        d["rotate"] = mp("angle" to 15, "fx" to .5, "fy" to .5, "steps" to 0)
        d["tilt"] = mp("angle" to 8, "axis" to "z", "sign" to 1)
        d["swing"] = mp("angle" to 6, "freq" to .6, "px" to .5, "py" to 0)
        d["spiral"] = mp("turns" to 1, "radius" to 10, "zoom" to 20, "angle" to 30, "stages" to 1)
        d["orbit"] = mp("orx" to 4, "ory" to 3, "ofx" to .25, "ofy" to .35, "phx" to 0, "phy" to 90, "angle" to 1.5, "zoom" to 6)
        d["shake"] = mp("shake" to 100, "mode" to "normal", "freq" to 22, "zshake" to 0, "decay" to 6, "seed" to 3)
        d["wiggle"] = mp("shake" to 100, "freq" to 9, "step" to 0, "seed" to 2)
        d["bounce"] = mp("dist" to 6, "hops" to 1, "mode" to "bounce", "decay" to 2.2, "freq" to 1.6)
        d["spring"] = mp("dist" to 5, "freq" to 3, "damp" to 3, "target" to "y")
        d["sway"] = mp("dx" to 1.2, "dy" to 1.8, "freq" to .35, "rotamp" to .6)
        d["pulse"] = mp("zoom" to 3, "pmode" to "heart", "freq" to 1.2, "mode" to "heart")
        d["vibe"] = mp("shake" to 60, "freq" to 60, "beatlock" to 0)
        d["fade"] = mp("io" to "in")
        d["slide"] = mp("io" to "in", "dir" to 0, "dist" to 100)
        d["rise"] = mp("io" to "in", "dist" to 35, "sign" to 1)
        d["pop"] = mp("io" to "in", "zoom" to 100)
        d["vortex"] = mp("io" to "in", "angle" to 360, "zoom" to 100)
        d["flipio"] = mp("io" to "in", "axis" to "y", "angle" to 90)
        d["scalein"] = mp("io" to "in", "zoom" to 60, "sk" to "both")
        d["stretchin"] = mp("io" to "in", "sk" to "x")
        d["reveal"] = mp("io" to "in", "rk" to "wipe", "dir" to 1, "soft" to 12)
        d["unfold"] = mp("io" to "in", "anchor" to -1, "angle" to 90)
        d["swipe"] = mp("io" to "in", "dir" to 0, "dist" to 120, "angle" to 8, "blur" to 14)
        d["pause"] = mp()
        d["stretch"] = mp("amt" to 25, "sk" to "x", "anchor" to 0)
        d["squash"] = mp("amt" to 20)
        d["skew"] = mp("amt" to 15, "sk" to "x")
        d["warp"] = mp("wtype" to "wave", "amt" to 40, "wfreq" to 3, "wspeed" to 3, "wrad" to 60, "fx" to .5, "fy" to .5)
        d["flipanim"] = mp("axis" to "y", "angle" to 180)
        d["flipstatic"] = mp("axis" to "y")
        d["persp"] = mp("tilt" to 18, "axis" to "y", "sign" to 1, "loop" to 0)
        d["orbit3d"] = mp("tilt" to 20, "freq" to .25, "depth" to 100)
        d["fold"] = mp("io" to "out", "anchor" to 1, "angle" to 90)
        d["depth"] = mp("dmotion" to "drift", "dist" to 6, "dir" to 1, "freq" to .3, "depth3" to 100, "depthm" to 30, "depthf" to 12, "near" to 1, "ddx" to .6, "ddy" to 0)
        d["kaleido"] = mp("nsec" to 6, "kspeed" to .4)
        d["mirror"] = mp("mtype" to 1, "nsec" to 4, "kspeed" to .1)
        d["pinch"] = mp("wtype" to "pinch", "amt" to 50, "wrad" to 70, "fx" to .5, "fy" to .5)
        d["blur"] = mp("blur" to 22, "bk" to "gauss", "bio" to "in", "dir" to 1, "freq" to 2)
        d["focus"] = mp("blur" to 26, "focio" to "in", "spot" to 1, "rad" to 28, "fx" to .5, "fy" to .45)
        d["flick"] = mp("fmode" to "flash", "amt" to 70, "freq" to 2, "lock" to 0, "color" to "#ffffff")
        d["oppulse"] = mp("amt" to 40, "freq" to 1)
        d["trail"] = mp("n" to 4, "dt" to .08, "tdecay" to 55, "off" to 0)
        d["glitch"] = mp("gmode" to "both", "amt" to 60, "freq" to 6, "ang" to 0)
        d["rgb"] = mp("amt" to 60, "ang" to 0, "freq" to 2, "lock" to 0, "gmode" to "rgb")
        d["chroma"] = mp("amt" to 60)
        d["film"] = mp("lmode" to "leak", "amt" to 60, "freq" to .4)
        d["glow"] = mp("gl" to "glow", "amt" to 60, "freq" to .7, "lock" to 0)
        d["sweep"] = mp("amt" to 70, "swa" to 60, "sww" to 9, "color" to "#ffffff", "sdark" to 0)
        d["flare"] = mp("amt" to 70, "fx" to .72, "fy" to .25)
        d["vig"] = mp("amt" to 60, "freq" to .8)
        d["cflash"] = mp("amt" to 60, "freq" to 2, "lock" to 0, "color" to "#ff3c78")
        val pathDef = mp("zpath" to 30, "follow" to 0)
        pathDef["segs"] = JSONArray("[{\"pts\":[[0.3,0.4],[0.5,0.55],[0.7,0.45]],\"speed\":1}]")
        d["path"] = pathDef
        val poiDef = mp("hold" to 35)
        poiDef["pts"] = JSONArray("[[0.3,0.35,180],[0.7,0.4,170],[0.5,0.7,200]]")
        d["poi"] = poiDef
        d["tunnel"] = mp("n" to 5, "fw" to 70, "loops" to 2, "tdecay" to 50)
        d["window"] = mp("sx" to .3, "sy" to .5, "ex" to .7, "ey" to .5, "ww" to 34, "wh" to 44, "dim" to 65, "wblur" to 10)
        d["domino"] = mp("io" to "in", "cols" to 6, "rows" to 4, "spread" to 60, "dmode" to "flip")
        d["glass"] = mp("n" to 3, "off" to 4, "freq" to .4, "tdecay" to 30)
        d["scan"] = mp("zoom" to 30, "glow" to 70, "sww" to 5)
        d["lightpath"] = mp("sx" to .1, "sy" to .7, "ex" to .9, "ey" to .3, "glow" to 80, "sww" to 7, "color" to "#fff3c0", "speedm" to 1)
        d["touchwave"] = mp("amt" to 70, "wfreq" to 9, "wspeed" to 5, "decay" to 1.6, "fx" to .5, "fy" to .5)
        val kfDef = HashMap<String, Any>()
        kfDef["kf"] = JSONArray("[{\"t\":0,\"s\":100,\"x\":0,\"y\":0,\"r\":0,\"o\":100},{\"t\":1,\"s\":125,\"x\":3,\"y\":-2,\"r\":0,\"o\":100}]")
        d["keyframes"] = kfDef
        val sf = mp("srad" to 25, "sratio" to 1.9, "sfeather" to 28, "bamt" to 48, "bang" to 0, "bkind" to "motion"); for ((k, v) in SUBJ_DEF) sf[k] = v
        d["subjfocus"] = sf
        val sk = mp("sint" to 115, "sthick" to 3.4, "slen" to 46, "color" to "#ffd7a0", "sspill" to 70, "sangle" to 0, "sbands" to 1, "soff" to -2); for ((k, v) in SUBJ_DEF) sk[k] = v
        d["streak"] = sk
        val bm = mp("bwidth" to 13, "bsoft" to 3, "bdim" to 90, "bangle" to 100, "bwarm" to 60, "bsweep" to 0, "boff" to 0); for ((k, v) in SUBJ_DEF) bm[k] = v
        d["beam"] = bm
        d
    }

    // ------------------------------------------------------------ presets
    private const val PRESET_SRC = """
1|zoomIn|Zoom In|zoom|
1|zoomOut|Zoom Out|zoom|zdir=-1
1|zoomSlow|Slow Zoom|zoom|zoom=15;ease=linear
1|zoomFast|Fast Zoom|zoom|zoom=45;t1=.3;ease=expoOut
1|zoomSmooth|Smooth Zoom|zoom|ease=sine
1|zoomDyn|Dynamic Zoom|zoom|zoom=45;ease=backOut;t1=.5
1|pushIn|Push In|push|
1|pullOut|Pull Out|push|zdir=-1
1|zoomPulse|Zoom Pulse|zoomosc|mode=pulse;loop=loop
1|zoomBounce|Zoom Bounce|zoomosc|mode=bounce;loop=loop;zoom=10
1|zoomLoop|Zoom In–Out Loop|zoomosc|mode=sine;loop=loop;zoom=15;freq=.5
1|zoomHold|Zoom Hold|zoom|t1=.4;end=hold;ease=expoOut
1|zoomSnap|Zoom Snap|zoom|t1=.12;end=snap;zoom=40;ease=expoOut
1|zoomThrough|Zoom Through|zoom|zoom=300;ease=cubicIn;fadeEnd=100;t0=.4
1|zoomCenter|Center Zoom|zoom|fx=.5;fy=.5
1|zoomOff|Off-Center Zoom|zoom|fx=.3;fy=.35
1|faceZoom|Face Zoom|zoom|zoom=60;fx=.5;fy=.36;cf=100
1|detailZoom|Detail Zoom|zoom|zoom=120;fx=.66;fy=.55;cf=100
1|zoomReturn|Zoom-and-Return|zoom|end=return;t1=.6
1|zoomFreeze|Zoom-and-Freeze|zoom|t1=.45;end=hold
1|zoomSnapBack|Zoom Snap Back|zoom|t1=.5;end=snap
1|panL|Pan Left|pan|dir=0
1|panR|Pan Right|pan|dir=1
1|panU|Pan Up|pan|dir=2
1|panD|Pan Down|pan|dir=3
1|panDiag|Diagonal Pan|pan|dir=5;dist=16
1|panSlow|Slow Pan|pan|dist=8;ease=linear
1|panFast|Fast Pan|pan|dist=22;t1=.4;ease=expoOut
1|panZoom|Pan and Zoom|kenburns|zoom=20
1|kenburns|Ken Burns|kenburns|
1|driftL|Drift Left|pan|dir=0;dist=6;ease=linear
1|driftR|Drift Right|pan|dir=1;dist=6;ease=linear
1|driftU|Drift Up|pan|dir=2;dist=6;ease=linear
1|driftD|Drift Down|pan|dir=3;dist=6;ease=linear
1|corner|Corner-to-Corner|kenburns|sx=.32;sy=.32;ex=.68;ey=.68;zoom=40
1|follow|Follow Subject|kenburns|sx=.35;sy=.5;ex=.62;ey=.44;zoom=45
1|panHold|Pan-and-Hold|pan|dir=1;dist=14;t1=.5;end=hold;ease=expoOut
1|randPan|Random Gentle Pan|randpan|
1|arcMove|Arc Move|arc|
1|curvedPath|Curved Path|arc|bend=1.4;dist=22
1|diagSweep|Diagonal Sweep|combo|c=panDiag+tiltR
2|rotCW|Rotate Clockwise|rotate|angle=15
2|rotCCW|Rotate Counterclockwise|rotate|angle=-15
2|quarter|Quarter Turn|rotate|angle=90
2|half|Half Turn|rotate|angle=180
2|fullSpin|Full Spin|rotate|angle=360
2|dblSpin|Double Spin|rotate|angle=720
2|rotSlow|Slow Rotation|rotate|angle=12;ease=linear
2|rotFast|Fast Rotation|rotate|angle=90;t1=.35;ease=expoOut
2|tiltL|Tilt Left|tilt|angle=8;sign=-1
2|tiltR|Tilt Right|tilt|angle=8;sign=1
2|tiltU|Tilt Up|tilt|angle=10;axis=x;sign=1
2|tiltD|Tilt Down|tilt|angle=10;axis=x;sign=-1
2|swing|Swing|swing|angle=6
2|pendulum|Pendulum|swing|angle=12;freq=.4;loop=loop
2|rocking|Rocking|swing|angle=3;py=1;freq=.8;loop=loop
2|rotZoom|Rotate and Zoom|combo|c=rotCW+zoomIn
2|tiltPan|Tilt and Pan|combo|c=tiltR+panR
2|spiralRot|Spiral Rotation|spiral|
2|rotPause|Rotation with Pause|rotate|angle=180;steps=3
2|microRot|Micro Rotation|rotate|angle=2.5;ease=linear
2|shake|Shake|shake|mode=normal;loop=loop
2|microShake|Micro Shake|shake|mode=micro;loop=loop
2|strongShake|Strong Shake|shake|mode=strong;loop=loop
2|shakeZoom|Shake Zoom|shake|mode=normal;zshake=1;loop=loop
2|wiggle|Wiggle|wiggle|loop=loop
2|jitter|Jitter|wiggle|step=1;freq=12;loop=loop
2|bounce|Bounce|bounce|loop=loop
2|dblBounce|Double Bounce|bounce|hops=2
2|elBounce|Elastic Bounce|bounce|mode=elastic
2|spring|Spring Motion|spring|
2|wobble|Wobble|spring|target=rot;dist=8
2|sway|Sway|sway|loop=loop
2|float|Float|sway|dx=.4;dy=2.4;rotamp=.3;loop=loop
2|hover|Hover|sway|dx=.2;dy=1.4;rotamp=.1;freq=.5;loop=loop
2|vibration|Vibration|vibe|loop=loop
2|beatShake|Beat Shake|shake|mode=beat;loop=loop
2|heartbeat|Heartbeat Motion|pulse|mode=heart;loop=loop;zoom=4
2|breathing|Breathing Motion|pulse|mode=breath;freq=.25;zoom=3;loop=loop
2|floatPortrait|Floating Portrait|combo|c=float+breathing
2|softRock|Soft Rock|swing|angle=2.2;py=.5;freq=.6;loop=loop
2|impactShake|Impact Shake|shake|mode=impact;t1=.25;shake=160
2|shakeSettle|Shake-and-Settle|shake|mode=settle;t1=.5;shake=120
3|fadeIn|Fade In|fade|io=in;t0=0;t1=.2
3|fadeOut|Fade Out|fade|io=out;t0=.8;t1=1
3|slideInL|Slide In Left|slide|io=in;dir=0;t1=.25;ease=expoOut
3|slideInR|Slide In Right|slide|io=in;dir=1;t1=.25;ease=expoOut
3|slideInU|Slide In Up|slide|io=in;dir=2;t1=.25;ease=expoOut
3|slideInD|Slide In Down|slide|io=in;dir=3;t1=.25;ease=expoOut
3|slideOut|Slide Out|slide|io=out;dir=0;t0=.75;t1=1;ease=expoIn
3|riseIn|Rise In|rise|io=in;t1=.3;ease=expoOut
3|dropIn|Drop In|rise|io=in;sign=-1;t1=.4;ease=bounce
3|popIn|Pop In|pop|io=in;t1=.25;ease=backOut
3|popOut|Pop Out|pop|io=out;t0=.8;t1=1;ease=backIn
3|bounceIn|Bounce In|pop|io=in;t1=.4;ease=bounce
3|vortexIn|Vortex In|vortex|io=in;t1=.4;ease=cubicOut
3|vortexOut|Vortex Out|vortex|io=out;t0=.7;t1=1;ease=cubicIn
3|flipIn|Flip In|flipio|io=in;t1=.3;ease=cubicOut
3|flipOut|Flip Out|flipio|io=out;t0=.7;t1=1;ease=cubicIn
3|scaleIn|Scale In|scalein|io=in;t1=.3;ease=expoOut
3|stretchIn|Stretch In|stretchin|io=in;t1=.3;ease=expoOut
3|reveal|Reveal|reveal|rk=wipe;t1=.35
3|wipeReveal|Wipe Reveal|reveal|rk=wipe;dir=0;t1=.4
3|maskReveal|Mask Reveal|reveal|rk=diamond;t1=.4
3|pause|Pause|pause|t0=.4;t1=.6
3|curtain|Curtain Reveal|reveal|rk=curtain;t1=.4
3|irisIn|Iris In|reveal|rk=iris;t1=.4
3|irisOut|Iris Out|reveal|rk=iris;io=out;t0=.6;t1=1
3|unfoldIn|Unfold In|unfold|io=in;t1=.35;anchor=-1;ease=cubicOut
3|foldOut|Fold Out|fold|io=out;t0=.65;t1=1;anchor=1;ease=cubicIn
3|swipeIn|Swipe In|swipe|io=in;t1=.3;ease=expoOut
4|stretch|Stretch|stretch|amt=25;sk=x
4|stretchL|Stretch Left|stretch|amt=25;sk=x;anchor=-1
4|stretchR|Stretch Right|stretch|amt=25;sk=x;anchor=1
4|squash|Squash|squash|
4|skew|Skew|skew|
4|warp|Warp|warp|wtype=wave;loop=loop
4|ripple|Ripple|warp|wtype=ripple;loop=loop;amt=60
4|wave|Wave|warp|wtype=wave;loop=loop
4|twist|Twist|warp|wtype=twist;loop=loop;amt=30
4|whirl|Whirl|warp|wtype=whirl;loop=loop;amt=30
4|flipH|Flip Horizontal|flipanim|axis=y;t1=.35
4|flipV|Flip Vertical|flipanim|axis=x;t1=.35
4|perspTilt|Perspective Tilt|persp|
4|tilt3d|3D Tilt|persp|axis=x;tilt=22
4|rot3d|3D Rotation|orbit3d|loop=loop
4|cardFlip|Card Flip|flipanim|angle=180;t1=.6;ease=cubicInOut
4|foldB|Fold|fold|io=out;t0=.6;t1=1
4|unfoldB|Unfold|unfold|io=in;t1=.4
4|parallax|Parallax|depth|dmotion=drift
4|depthShift|Depth Shift|depth|dmotion=orbit;loop=loop
4|perspPush|Perspective Push|push|tilt=8
4|perspPull|Perspective Pull|push|zdir=-1;tilt=8
4|kalMotion|Kaleidoscope Motion|kaleido|loop=loop
4|mirrorFlip|Mirror Flip|mirror|mtype=1
4|pinch|Pinch|pinch|wtype=pinch;t1=.6
4|expand|Expand|pinch|wtype=expand;t1=.6
5|blurIn|Blur In|blur|bio=in;t1=.35
5|blurOut|Blur Out|blur|bio=out;t0=.65;t1=1
5|zoomBlur|Zoom Blur|blur|bk=zoom;bio=pulse;blur=30;loop=loop
5|motionBlur|Motion Blur|blur|bk=motion;bio=hold;blur=18
5|dirBlur|Directional Blur|blur|bk=motion;bio=in;dir=1;t1=.5
5|radialBlur|Radial Blur|blur|bk=radial;bio=hold;blur=18
5|focusIn|Focus In|focus|focio=in;t1=.4;spot=0
5|focusOut|Focus Out|focus|focio=out;t0=.6;t1=1;spot=0
5|flicker|Flicker|flick|fmode=flicker;loop=loop
5|flash|Flash|flick|fmode=flash;loop=loop
5|blink|Blink|flick|fmode=blink;loop=loop;freq=1.5
5|oppulse|Opacity Pulse|oppulse|loop=loop
5|ghost|Ghost Trail|trail|n=5
5|dblImage|Double Image|trail|n=1;off=2;dt=.12
5|glitch|Glitch|glitch|gmode=glitch;loop=loop
5|rgbSplit|RGB Split|rgb|ang=0;loop=loop
5|chromShift|Chromatic Shift|chroma|loop=loop
5|lightLeak|Light Leak|film|lmode=leak;loop=loop
5|filmBurn|Film Burn|film|lmode=burn;loop=loop
5|grain|Grain|film|lmode=grain;loop=loop
5|glowPulse|Glow Pulse|glow|gl=glow;loop=loop
5|lightSweep|Light Sweep|sweep|t1=.7
5|lensFlare|Lens Flare|flare|t1=.8
5|vigPulse|Vignette Pulse|vig|loop=loop
5|colorFlash|Color Flash|cflash|loop=loop
5|expPulse|Exposure Pulse|glow|gl=exp;loop=loop
5|softFocus|Soft Focus|glow|gl=soft;amt=70;loop=loop
5|shadowSweep|Shadow Sweep|sweep|sdark=1;color=#000000;t1=.8
6|zoomRotate|Zoom + Rotate|combo|c=zoomIn+rotCW
6|zoomBlurC|Zoom + Blur|combo|c=zoomIn+blurIn
6|zoomShakeC|Zoom + Shake|combo|c=zoomIn+microShake
6|panTilt|Pan + Tilt|combo|c=panR+tiltR
6|rotFade|Rotate + Fade|combo|c=rotCW+fadeIn
6|stretchShake|Stretch + Shake|combo|c=stretch+shake
6|zoomFlash|Zoom + Flash|combo|c=zoomIn+flash
6|zoomFocus|Zoom + Focus|combo|c=zoomIn+focusIn
6|photo3d|3D Photo Motion|combo|c=perspTilt+parallax+zoomSlow
6|spiralZoom|Spiral Zoom|combo|c=spiralRot+zoomIn
6|zoomRgb|Zoom + RGB Split|combo|c=zoomIn+rgbSplit
6|panBlur|Pan + Blur|combo|c=panR+dirBlur
6|bounceRot|Bounce + Rotate|combo|c=bounce+rotCW
6|fadeScale|Fade + Scale|combo|c=fadeIn+scaleIn
6|slideBlur|Slide + Motion Blur|combo|c=slideInR+motionBlur
6|floatParallax|Float + Parallax|combo|c=float+parallax
6|shakeGlitch|Shake + Glitch|combo|c=shake+glitch
6|rotSweep|Rotate + Light Sweep|combo|c=rotCW+lightSweep
6|zoomGrain|Slow Zoom + Film Grain|combo|c=zoomSlow+grain
6|customCombo|Custom Combo|combo|c=zoomIn+rotCW+blurIn
8|touchPath|Touchable Camera Path|path|
8|pathMulti|Multi-Path Camera|path|segs=[{"pts":[[0.25,0.35],[0.45,0.55]],"speed":1},{"pts":[[0.45,0.55],[0.75,0.4],[0.6,0.7]],"speed":1}]
8|pathSpeed|Path Speed Zones|path|segs=[{"pts":[[0.2,0.4],[0.5,0.5]],"speed":2.2},{"pts":[[0.5,0.5],[0.8,0.45]],"speed":0.45}]
8|depthDrag|Finger Depth Drag|depth|dmotion=drift;dist=10
8|layerDepth|Manual Layer Depth|depth|dmotion=orbit;loop=loop;depth3=100;depthm=35;depthf=15
8|lightPath|Independent Light Path|lightpath|
8|frameTunnel|Animated Frame Tunnel|tunnel|
8|movingWindow|Moving Window|window|
8|domino|Domino Motion|domino|
8|multiSpiral|Multi-Stage Spiral|spiral|stages=3;turns=1.5;radius=12
8|glassLayers|Glass Layers|glass|
8|cinScan|Cinematic Scan|scan|
8|trailCtl|Controllable Trail|trail|n=6;dt=.06;tdecay=60;off=0
8|poiTour|Tour Between Key Points|poi|
9|mirrorMulti|Multi-Section Mirror Motion|mirror|mtype=4;nsec=6;loop=loop
9|orbits|Independent Orbits|orbit|loop=loop
9|touchWave|Touch Wave|touchwave|loop=once
9|cinZoomStop|Cinematic Zoom Stop|combo|c=zoomHold+vigPulse
9|threeLayer|Three-Layer Depth|depth|dmotion=drift;near=1
9|softSpiral|Soft Spiral Camera|spiral|turns=.75;radius=7;zoom=15;angle=8
9|detailFull|Detail-to-Full Reveal|zoom|zdir=-1;zoom=120;fx=.66;fy=.55;cf=100;t1=.7;ease=cubicOut
9|moveFreeze|Move-Freeze-Continue|combo|c=panR+pause
9|carousel3dFocus|3D Carousel Focus|combo|c=rot3d+slideInR
9|breathFloat|Breathing Float|combo|c=breathing+float
9|impactZoom|Impact Zoom Return|combo|c=zoomSnap+impactShake
9|orbitSubject|Orbit Around Subject|orbit|orx=5;ory=3;zoom=10;angle=2;loop=loop
9|slowPushTilt|Slow Push with Tilt|combo|c=pushIn+microRot
9|cornerReveal|Corner Reveal Zoom|zoom|zdir=-1;zoom=100;fx=.1;fy=.1;cf=0;t1=.7;ease=cubicOut
9|waveDrift|Wave Drift|combo|c=driftR+wave
9|pendulumZoom|Pendulum Zoom|combo|c=pendulum+zoomLoop
9|parallaxOrbit|Parallax Orbit|combo|c=depthShift+orbitSubject
9|focusPullPan|Focus Pull with Pan|combo|c=focusIn+panR
9|softLanding|Soft Landing|rise|io=in;sign=-1;dist=25;t1=.5;ease=expoOut
9|elasticReturn|Elastic Camera Return|zoom|end=settle;t1=.5;zoom=30;ease=cubicOut
9|rhythmZoom|Rhythmic Zoom Pattern|zoomosc|mode=pattern;loop=loop;freq=2;zoom=10
9|reverseReveal|Reverse Motion Reveal|combo|c=detailFull+reveal
9|holdRelease|Cinematic Hold and Release|zoom|zoom=60;t0=.45;t1=.8;ease=expoInOut
9|depthPop|Depth Pop|depth|dmotion=pop;near=1;depth3=100
9|lightReveal|Light-Tracked Reveal|combo|c=wipeReveal+lightSweep
9|zoomThroughFrame|Zoom Through Frame|tunnel|n=1;loops=1
9|layeredDrift|Layered Parallax Drift|combo|c=parallax+driftR
9|customPath|Custom Motion Path|path|
9|keyframes|Keyframes|keyframes|
7|lightStreak|Light Streak Across Eyes|streak|
7|spotBeam|Spotlight Beam Portrait|beam|
7|sharpSubject|Sharp Subject + Motion Background|subjfocus|
7|softSubject|Sharp Subject + Soft Background|subjfocus|bkind=gauss;bamt=34;sratio=1.5
7|streakFollow|Light Streak + Camera Follow|combo|c=kenburns+lightStreak
7|beamFollow|Spotlight Beam + Camera Follow|combo|c=panDiag+spotBeam
7|sharpFollow|Sharp Subject + Camera Follow|combo|c=kenburns+sharpSubject
"""

    val presets: List<MotionPreset> by lazy { parsePresets() }
    private val byIdMap: HashMap<String, MotionPreset> by lazy { val h = HashMap<String, MotionPreset>(); for (p in presets) h[p.id] = p; h }
    private val layerCache = HashMap<String, List<MLayer>>()

    private fun parseVal(v0: String): Any {
        val v = v0.trim()
        if (v.startsWith("[")) { try { return JSONArray(v) } catch (e: Exception) { return v } }
        if (v.startsWith("{")) { try { return JSONObject(v) } catch (e: Exception) { return v } }
        val n = v.toDoubleOrNull()
        return if (v.isNotEmpty() && n != null) n else v
    }

    private fun parsePresets(): List<MotionPreset> {
        val out = ArrayList<MotionPreset>()
        for (line in PRESET_SRC.trim().split("\n")) {
            val f = line.split("|")
            if (f.size < 4) continue
            val params = HashMap<String, Any>()
            var combo: List<String>? = null
            if (f.size > 4) {
                // the parameter list may itself contain '|' only inside JSON, which the source never does
                for (kv in f[4].split(";")) {
                    if (kv.isEmpty()) continue
                    val i = kv.indexOf('=')
                    if (i < 0) continue
                    val k = kv.substring(0, i)
                    val v = kv.substring(i + 1)
                    if (k == "c") combo = v.split("+") else params[k] = parseVal(v)
                }
            }
            out.add(MotionPreset(f[1], f[0].trim().toIntOrNull() ?: 9, f[2], f[3], params, combo))
        }
        return out
    }

    fun byId(id: String): MotionPreset? = byIdMap[id]
    fun nameOf(id: String): String = byIdMap[id]?.name ?: id

    @Synchronized
    private fun layersOf(id: String): List<MLayer> {
        layerCache[id]?.let { return it }
        val pr = byIdMap[id]
        val out = ArrayList<MLayer>()
        if (pr != null) {
            if (pr.combo != null) {
                for (c in pr.combo) out.addAll(layersOf(c))
            } else {
                val p = HashMap<String, Any>(CD)
                DEFS[pr.base]?.let { for ((k, v) in it) p[k] = v }
                for ((k, v) in pr.params) p[k] = v
                out.add(MLayer(pr.base, p))
            }
        }
        layerCache[id] = out
        return out
    }

    private val OVERLAY_BASES = setOf(
        "flick", "cflash", "film", "glow", "sweep", "flare", "vig", "glitch", "rgb", "chroma", "blur", "focus", "window", "scan",
        "lightpath", "streak", "beam", "subjfocus", "swipe", "trail", "glass", "touchwave", "warp", "pinch"
    )

    /** True when the preset draws something over the picture (flash, light sweep, vignette ...). */
    fun hasOverlayPost(id: String): Boolean {
        for (l in layersOf(id)) if (OVERLAY_BASES.contains(l.base)) return true
        return false
    }

    // ------------------------------------------------------------ evaluation
    private fun layerProg(P: Pm, t: Double, dur: Double): LProg {
        val t0 = P.d("t0") * dur
        val t1v = P.d("t1", 1.0)
        val t1 = Math.max(t0 + .05, (if (t1v <= 0) 1.0 else t1v) * dur)
        val speed = Math.max(.05, P.d("speed", 1.0))
        val span = Math.max(.05, t1 - t0) / Math.max(.05, speed)
        val rep = Math.max(1, Math.round(P.d("repeat", 1.0)).toInt())
        val u = (t - t0) / span
        val lt = Math.max(0.0, (t - t0) * speed)
        var raw = 0.0
        var am = 1.0
        var fin = false
        val uR = u * rep
        val loop = P.s("loop", "once")
        if (u < 0) raw = 0.0
        else if (loop == "loop") raw = fract(uR)
        else if (loop == "pingpong") { val m = uR % 2; raw = if (m <= 1) m else 2 - m }
        else if (u >= 1) { fin = true; raw = 1.0 }
        else raw = fract(uR)
        var e = easeOf(P, raw)
        val end = P.s("end", "hold")
        if (fin) {
            val q = u - 1
            if (end == "return") e = 1 - ease("sine", clamp(q / .35, 0.0, 1.0))
            else if (end == "snap") e = 0.0
            else if (end == "settle") e = 1 + Math.exp(-clamp(q / .6, 0.0, 1.0) * 5) * Math.sin(clamp(q / .6, 0.0, 1.0) * 14) * .12 * (if (q < .6) 1.0 else 0.0)
            else if (end == "fadeout") am = 1 - clamp(q / .25, 0.0, 1.0)
            else if (end == "loopback") e = 1 - ease("sine", clamp(q / .5, 0.0, 1.0))
        }
        val env = if (u < 0) 0.0 else if (loop != "once") 1.0 else if (u < 1) 1.0 else clamp(1 - (u - 1) * 8, 0.0, 1.0)
        return LProg(e, u, lt, env, am)
    }

    private fun zoomAbout(st: MState, s: Double, fx: Double, fy: Double, cf: Double, c: MCtx) {
        st.sc *= s
        val dx = (fx - .5) * c.qwF
        val dy = (fy - .5) * c.qhF
        st.tx += lerp(dx * (1 - s), -dx * s, cf)
        st.ty += lerp(dy * (1 - s), -dy * s, cf)
    }

    private fun rotAbout(st: MState, th: Double, px: Double, py: Double, c: MCtx) {
        st.rot += th
        val X = (px - .5) * c.qw
        val Y = (py - .5) * c.qh
        val cs = Math.cos(th)
        val sn = Math.sin(th)
        st.tx += (X - (X * cs - Y * sn)) / c.W
        st.ty += (Y - (X * sn + Y * cs)) / c.H
    }

    private fun hid(P: Pm, c: MCtx): Double = if (P.s("io") == "out") c.p else 1 - c.p

    private fun subjScreen(st: MState, W: Double, H: Double, u: Double, v: Double): DoubleArray {
        val w = W * st.sc * st.scx * st.flx
        val h = H * st.sc * st.scy * st.fly
        val x = (u - .5) * w
        val y = (v - .5) * h
        val cs = Math.cos(st.rot)
        val sn = Math.sin(st.rot)
        return doubleArrayOf((W / 2 + st.tx * W + x * cs - y * sn) / W, (H / 2 + st.ty * H + x * sn + y * cs) / H)
    }

    private fun subjAnchor(st: MState, P: Pm, c: MCtx, fn: (MState, Double, Double) -> Unit) {
        val q = clamp(c.u, 0.0, 1.0)
        val u = lerp(P.d("su", .5), P.d("eu", .5), q)
        val v = lerp(P.d("sv", .46), P.d("ev", .46), q)
        val W = c.W
        val H = c.H
        val track = P.b("track")
        st.anch.add { s ->
            val f = if (track) subjScreen(s, W, H, u, v) else doubleArrayOf((W / 2 + (u - .5) * W) / W, (H / 2 + (v - .5) * H) / H)
            fn(s, f[0], f[1])
        }
    }

    private fun applyBase(base: String, st: MState, P: Pm, c: MCtx) {
        val I = P.I
        val po = st.post
        when (base) {
            "zoom" -> {
                val a = P.d("zoom") / 100 * I * P.KS
                val s = if (P.d("zdir") > 0) 1 + a * c.p else 1 + a * (1 - c.p)
                zoomAbout(st, s, P.d("fx", .5), P.d("fy", .5), P.d("cf") / 100, c)
                if (P.d("fadeEnd") != 0.0) st.alpha *= 1 - clamp((c.p - .6) / .4, 0.0, 1.0) * P.d("fadeEnd") / 100
            }
            "zoomosc" -> {
                val a = P.d("zoom") / 100 * I * P.KS
                val freq = P.d("freq", 2.0)
                val f = fract(c.lt * freq)
                val mode = P.s("mode", "pulse")
                val w: Double
                if (mode == "pulse") w = if (P.b("lock")) c.beat else Math.exp(-f * 5)
                else if (mode == "bounce") w = Math.abs(Math.sin(Math.PI * c.lt * freq))
                else if (mode == "sine") w = .5 - .5 * Math.cos(TAUD * c.lt * freq)
                else if (mode == "snap") w = if (f < .5) 0.0 else 1.0
                else {
                    val pat = doubleArrayOf(1.0, .4, .7, .4, 1.0, .4, .7, .9)
                    w = pat[Math.floor(c.lt * freq).toInt().mod(8)] * Math.exp(-f * 5)
                }
                zoomAbout(st, 1 + a * w * c.env, P.d("fx", .5), P.d("fy", .5), 0.0, c)
            }
            "push" -> {
                val a = P.d("zoom") / 100 * I
                val w = if (P.d("zdir") > 0) c.p else 1 - c.p
                st.sc *= 1 + a * w
                st.rx += P.d("tilt") * D2R * w * I * P.KR
            }
            "pan" -> {
                if (P.b("usePts")) {
                    st.tx += (.5 - lerp(P.d("sx"), P.d("ex"), c.p)) * c.qwF * P.AX * I
                    st.ty += (.5 - lerp(P.d("sy"), P.d("ey"), c.p)) * c.qhF * P.AY * I
                } else {
                    val d = P.d("dist") / 100 * I
                    val v = DIRS[clampI(P.i("dir"), 0, 7)]
                    st.tx += -v[0] * (c.p - .5) * d * P.AX
                    st.ty += -v[1] * (c.p - .5) * d * P.AY
                }
            }
            "kenburns" -> {
                val s = lerp(1 + P.d("z0") / 100, 1 + P.d("zoom") / 100 * I * P.KS, c.p)
                val cu = lerp(P.d("sx"), P.d("ex"), c.p)
                val cv = lerp(P.d("sy"), P.d("ey"), c.p)
                st.sc *= s
                st.tx += (.5 - cu) * c.qwF * s * P.AX
                st.ty += (.5 - cv) * c.qhF * s * P.AY
            }
            "randpan" -> {
                val d = P.d("dist") / 100 * I * c.env
                val fr = P.d("freq")
                val sd = P.d("seed")
                st.tx += vn(c.t * fr, sd) * d * P.AX
                st.ty += vn(c.t * fr + 7.3, sd) * d * P.AY * .8
            }
            "arc" -> {
                val d = P.d("dist") / 100 * I
                val v = DIRS[clampI(P.i("dir"), 0, 7)]
                val n0 = -v[1]
                val n1 = v[0]
                val q = (c.p - .5) * 2
                val perp = ((1 - q * q) - .5) * P.d("bend") * d * .5
                st.tx += (-v[0] * (c.p - .5) * d + n0 * perp) * P.AX
                st.ty += (-v[1] * (c.p - .5) * d + n1 * perp) * P.AY
            }
            "rotate" -> {
                var p = c.p
                val steps = P.d("steps")
                if (steps > 0) {
                    val k = steps
                    val f = p * k
                    p = (Math.floor(Math.min(f, k - 1e-6)) + ease("sine", clamp(fract(f) * 2.2, 0.0, 1.0))) / k
                }
                rotAbout(st, P.d("angle") * D2R * I * P.KR * p, P.d("fx", .5), P.d("fy", .5), c)
            }
            "tilt" -> {
                val th = P.d("angle") * D2R * I * P.KR * c.p * P.d("sign", 1.0)
                when (P.s("axis", "z")) { "z" -> st.rot += th; "x" -> st.rx += th; else -> st.ry += th }
            }
            "swing" -> rotAbout(st, P.d("angle") * D2R * I * P.KR * Math.sin(TAUD * c.lt * P.d("freq")) * c.env, P.d("px", .5), P.d("py"), c)
            "spiral" -> {
                val p = c.p
                val ph = TAUD * P.d("turns") * P.d("stages", 1.0) * p
                val r = P.d("radius") / 100 * I * (1 - p)
                st.tx += Math.cos(ph) * r * P.AX
                st.ty += Math.sin(ph) * r * P.AY * .8
                st.sc *= 1 + P.d("zoom") / 100 * I * p * P.KS
                st.rot += P.d("angle") * D2R * I * P.KR * p
            }
            "orbit" -> {
                val e = c.env * I
                st.tx += Math.cos(TAUD * P.d("ofx") * c.lt + P.d("phx") * D2R) * P.d("orx") / 100 * e * P.AX
                st.ty += Math.sin(TAUD * P.d("ofy") * c.lt + P.d("phy") * D2R) * P.d("ory") / 100 * e * P.AY
                st.rot += Math.sin(TAUD * P.d("ofx") * c.lt) * P.d("angle") * D2R * e * P.KR
                st.sc *= 1 + P.d("zoom") / 100 * e * P.KS
            }
            "shake" -> {
                val mode = P.s("mode", "normal")
                val base0 = when (mode) { "micro" -> .25; "normal" -> 1.0; "strong" -> 2.4; "beat" -> 1.4; "impact" -> 2.2; "settle" -> 1.6; else -> 1.0 }
                var e = c.env
                if (mode == "impact") e *= Math.exp(-c.lt * P.d("decay") * .7)
                if (mode == "settle") e *= Math.exp(-c.lt * P.d("decay") * .4)
                if (mode == "beat") e *= c.beat
                val A = P.d("shake") / 100 * I * base0 * e
                val f = P.d("freq")
                val t = c.lt
                val sd = P.d("seed")
                st.tx += vn(t * f, sd) * .01 * A * P.AX
                st.ty += vn(t * f, sd + 5) * .01 * A * P.AY
                st.rot += vn(t * f, sd + 9) * .5 * D2R * A * P.KR
                if (P.b("zshake")) st.sc *= 1 + Math.abs(vn(t * f, sd + 13)) * .02 * A * P.KS
            }
            "wiggle" -> {
                val A = P.d("shake") / 100 * I * c.env
                val fr = P.d("freq")
                val sd = P.d("seed")
                if (P.b("step")) {
                    val k = Math.floor(c.lt * fr)
                    st.tx += (hsh(k + sd) - .5) * .012 * A * P.AX
                    st.ty += (hsh(k + sd + 4) - .5) * .012 * A * P.AY
                    st.rot += (hsh(k + sd + 8) - .5) * 1.2 * D2R * A * P.KR
                } else {
                    val t = c.lt * fr
                    st.rot += Math.sin(TAUD * t) * 1.2 * D2R * A * P.KR
                    st.tx += Math.sin(TAUD * t * 1.3 + 1) * .006 * A * P.AX
                }
            }
            "bounce" -> {
                val d = P.d("dist") / 100 * I
                val t = c.lt * P.d("freq")
                var h = 0.0
                if (P.s("mode") == "elastic") h = 1 - ease("elastic", clamp(t / 2, 0.0, 1.0))
                else if (t < P.d("hops")) h = Math.abs(Math.sin(Math.PI * t)) * Math.exp(-P.d("decay") * t / Math.max(1.0, P.d("hops")))
                st.ty += -h * d * P.AY * c.env
            }
            "spring" -> {
                val d = Math.exp(-c.lt * P.d("damp")) * Math.cos(TAUD * c.lt * P.d("freq")) * c.env
                val A = I * P.d("dist") / 100
                when (P.s("target", "y")) {
                    "y" -> st.ty += d * A * P.AY
                    "x" -> st.tx += d * A * P.AX
                    "rot" -> st.rot += d * A * 10 * D2R * P.KR
                    else -> st.sc *= 1 + d * A * P.KS
                }
            }
            "sway" -> {
                val t = TAUD * c.lt * P.d("freq")
                val e = c.env * I
                st.tx += Math.sin(t) * P.d("dx") / 100 * e * P.AX
                st.ty += Math.sin(t * .8 + 1) * P.d("dy") / 100 * e * P.AY
                st.rot += Math.sin(t * .6 + 2) * P.d("rotamp") * D2R * e * P.KR
            }
            "pulse" -> {
                val a = P.d("zoom") / 100 * I * P.KS
                val ph = fract(c.lt * P.d("freq"))
                fun bump(x: Double) = Math.exp(-x * x / .004)
                val w = if (P.s("mode", "heart") == "heart") bump(ph) + .6 * bump(ph - .2) else .5 + .5 * Math.sin(TAUD * c.lt * P.d("freq"))
                st.sc *= 1 + a * w * c.env
            }
            "vibe" -> {
                val A = P.d("shake") / 100 * I * c.env * (if (P.b("lock")) c.beat else 1.0)
                val t = c.lt * P.d("freq")
                st.tx += Math.sin(TAUD * t) * .003 * A * P.AX
                st.ty += Math.sin(TAUD * t * 1.17 + 2) * .003 * A * P.AY
            }
            // ---- entry / exit
            "fade" -> st.alpha *= 1 - clamp(hid(P, c), 0.0, 1.0)
            "slide" -> {
                val v = hid(P, c)
                val d = DIRS[clampI(P.i("dir"), 0, 7)]
                st.tx += d[0] * v * P.d("dist") / 100
                st.ty += d[1] * v * P.d("dist") / 100
                st.free = true
            }
            "rise" -> {
                val v = hid(P, c)
                st.ty += v * P.d("dist") / 100 * P.d("sign", 1.0)
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .9
                st.free = true
            }
            "pop" -> {
                val v = hid(P, c)
                st.sc *= Math.max(.001, 1 - v * P.d("zoom") / 100)
                st.alpha *= 1 - clamp(v * 1.2, 0.0, 1.0) * .6
                st.free = true
            }
            "vortex" -> {
                val v = hid(P, c)
                st.rot += v * P.d("angle") * D2R
                st.sc *= Math.max(.001, 1 - v * P.d("zoom") / 100)
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .7
                st.free = true
            }
            "flipio" -> {
                val v = hid(P, c)
                val th = v * P.d("angle") * D2R
                if (P.s("axis", "y") == "y") st.ry += th else st.rx += th
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .5
                st.free = true
            }
            "scalein" -> {
                val v = hid(P, c)
                st.sc *= 1 - v * P.d("zoom") / 100
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .8
                st.free = true
            }
            "stretchin" -> {
                val v = hid(P, c)
                if (P.s("sk", "x") == "x") st.scx *= Math.max(.001, 1 - v) else st.scy *= Math.max(.001, 1 - v)
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .6
                st.free = true
            }
            "reveal" -> {
                // the native pipeline has no animated mask: approximate with fade and a small scale-up
                val v = hid(P, c)
                val rk = P.s("rk", "wipe")
                val d = DIRS[clampI(P.i("dir"), 0, 7)]
                st.alpha *= clamp(1 - v * 1.15, 0.0, 1.0)
                if (rk == "iris" || rk == "diamond") st.sc *= 1 - v * .25
                else if (rk == "curtain") st.scx *= Math.max(.2, 1 - v * .5)
                else { st.tx += d[0] * v * .08; st.ty += d[1] * v * .08 }
                st.maskT = 1; st.maskP = 1 - v
            }
            "unfold", "fold" -> {
                val v = hid(P, c)
                val th = v * P.d("angle") * D2R
                val an = if (P.d("anchor") == 0.0) (if (base == "fold") 1.0 else 1.0) else P.d("anchor")
                st.ry += th * an
                st.tx += -P.d("anchor") * .5 * c.qwF * st.sc * (1 - Math.cos(th))
                st.alpha *= 1 - clamp(v, 0.0, 1.0) * .4
                st.free = true
            }
            "swipe" -> {
                val v = hid(P, c)
                val d = DIRS[clampI(P.i("dir"), 0, 7)]
                st.tx += d[0] * v * P.d("dist") / 100
                st.ty += d[1] * v * P.d("dist") / 100
                st.rot += d[0] * v * P.d("angle") * D2R
                st.free = true
                po.blurK = 1.0
                po.blur += P.d("blur") * v * Math.sin(Math.PI * Math.min(1.0, v * 1.2))
                po.bdx = Math.abs(d[0]); po.bdy = Math.abs(d[1])
            }
            "pause" -> {}
            // ---- deform
            "stretch" -> {
                val a = P.d("amt") / 100 * I * c.p
                if (P.s("sk", "x") == "x") { st.scx *= 1 + a; st.tx += P.d("anchor") * c.qwF * st.sc * a * .5 }
                else { st.scy *= 1 + a; st.ty += P.d("anchor") * c.qhF * st.sc * a * .5 }
            }
            "squash" -> {
                val a = P.d("amt") / 100 * I * Math.sin(Math.PI * c.p)
                st.scy *= 1 - a
                st.scx *= 1 + a * .5
            }
            "skew" -> {
                val a = P.d("amt") / 100 * I * c.p * .6
                if (P.s("sk", "x") == "x") st.skx += a else st.sky += a
            }
            "warp" -> {
                val t = when (P.s("wtype", "wave")) { "wave" -> 1; "ripple" -> 2; "twist" -> 3; "whirl" -> 4; "pinch" -> 5; "expand" -> 6; else -> 1 }
                val k = P.d("amt") / 100 * I * c.env
                po.wt = t
                po.wamp += k * when (t) { 1 -> .012; 2 -> .02; 3 -> 1.5; 4 -> 1.5; 5 -> .6; 6 -> .6; else -> .01 }
                po.wfreq = P.d("wfreq"); po.wph = c.lt * P.d("wspeed"); po.wcx = P.d("fx", .5); po.wcy = P.d("fy", .5); po.wrad = P.d("wrad") / 100
            }
            "flipanim" -> {
                val th = P.d("angle") * D2R * c.p
                if (P.s("axis", "y") == "y") st.ry += th else st.rx += th
                st.free = true
            }
            "flipstatic" -> { if (P.s("axis", "y") == "y") st.flx *= -1 else st.fly *= -1 }
            "persp" -> {
                val th = P.d("tilt") * D2R * I * P.KR * c.p * P.d("sign", 1.0)
                when (P.s("axis", "y")) { "y" -> st.ry += th; "x" -> st.rx += th; else -> st.rot += th }
            }
            "orbit3d" -> {
                val e = c.env * I * P.d("depth", 100.0) / 100
                st.ry += Math.sin(TAUD * c.lt * P.d("freq")) * P.d("tilt") * D2R * e
                st.rx += Math.cos(TAUD * c.lt * P.d("freq")) * P.d("tilt") * .4 * D2R * e
            }
            "depth" -> {
                val d = DIRS[clampI(P.i("dir"), 0, 7)]
                val dm = P.s("dmotion", "drift")
                val m: DoubleArray = when (dm) {
                    "orbit" -> doubleArrayOf(Math.cos(TAUD * c.lt * P.d("freq")), Math.sin(TAUD * c.lt * P.d("freq")) * .7)
                    "pop" -> doubleArrayOf(0.0, 0.0)
                    "drag" -> doubleArrayOf(P.d("ddx") * (c.p - .5) * 2, P.d("ddy") * (c.p - .5) * 2)
                    else -> doubleArrayOf(-d[0] * (c.p - .5) * 2, -d[1] * (c.p - .5) * 2)
                }
                // native approximation of the 3-layer parallax: move the picture by the middle layer and add a little depth zoom
                val mid = P.d("depthm", 30.0) / 100 + .5
                st.tx += m[0] * P.d("dist") / 100 * I * mid
                st.ty += m[1] * P.d("dist") / 100 * I * mid
                st.sc *= 1 + .04 * I + (if (dm == "pop") c.p * I * .14 * P.d("depth3", 100.0) / 100 else 0.0)
            }
            "kaleido" -> {
                po.kal = P.d("nsec"); po.kalr = c.lt * P.d("kspeed") * I
                st.sc *= 1.06
                st.rot += Math.sin(po.kalr * 2) * .05
            }
            "mirror" -> {
                val m = P.i("mtype")
                if (m == 5 || m == 4) {
                    po.kal = P.d("nsec"); po.kalr = c.lt * P.d("kspeed")
                    st.sc *= 1.05
                    st.rot += Math.sin(po.kalr * 2) * .04
                } else {
                    po.mir = m.toDouble()
                    st.flx *= if (fract(c.lt * .5) < .5) 1.0 else -1.0
                }
            }
            "pinch" -> {
                po.wt = if (P.s("wtype") == "expand") 6 else 5
                po.wamp += P.d("amt") / 100 * I * .6 * Math.sin(Math.PI * clamp(c.p, 0.0, 1.0)) * Math.max(c.env, .0001)
                po.wcx = P.d("fx", .5); po.wcy = P.d("fy", .5); po.wrad = P.d("wrad") / 100
            }
            // ---- light, blur, appearance
            "blur" -> {
                po.blurK = when (P.s("bk", "gauss")) { "gauss" -> 3.0; "motion" -> 1.0; "zoom" -> 2.0; "radial" -> 4.0; else -> 3.0 }
                val bio = P.s("bio", "in")
                val v = when (bio) { "in" -> 1 - c.p; "out" -> c.p; "pulse" -> Math.exp(-fract(c.lt * P.d("freq")) * 4) * c.env; else -> 1.0 }
                po.blur += P.d("blur") * I * v
                val d = DIRS[clampI(P.i("dir"), 0, 7)]
                po.bdx = d[0]; po.bdy = d[1]
            }
            "focus" -> {
                val v = if (P.s("focio", "in") == "in") 1 - c.p else c.p
                po.blurK = 3.0
                po.blur += P.d("blur") * I * v
                if (P.b("spot")) { po.fo = 1.0; po.fox = P.d("fx", .5); po.foy = P.d("fy", .45); po.forad = P.d("rad") / 100 }
            }
            "flick" -> {
                val A = P.d("amt") / 100 * I * c.env
                val f = fract(c.lt * P.d("freq"))
                when (P.s("fmode", "flash")) {
                    "flash" -> { po.flash += A * (if (P.b("lock")) c.beat else Math.exp(-f * 8)); po.fcol = hex2f(P.s("color", "#ffffff")) }
                    "flicker" -> po.exp += (hsh(Math.floor(c.lt * P.d("freq") * 5)) - .5) * .8 * A
                    else -> st.alpha *= if (f < .5) 1.0 else 1 - A
                }
            }
            "oppulse" -> st.alpha *= 1 - P.d("amt") / 100 * I * (.5 + .5 * Math.sin(TAUD * c.lt * P.d("freq"))) * c.env
            "trail" -> {
                // no real ghost frames natively: a soft haze + slight echo scale pulse gives a similar feel
                po.soft += .25 * Math.min(1.0, P.d("n") / 4.0)
                st.sc *= 1 + .01 * Math.sin(TAUD * c.lt * 3)
            }
            "glitch" -> {
                val gate = if (hsh(Math.floor(c.lt * P.d("freq")) + 3.3) > .45) 1.0 else 0.0
                val A = P.d("amt") / 100 * I * c.env * gate
                val gm = P.s("gmode", "both")
                if (gm != "rgb") po.glitch += A
                if (gm != "glitch") { po.rgb += A * .012; po.rgba = P.d("ang") * D2R }
                val k = Math.floor(c.lt * P.d("freq") * 6)
                st.tx += (hsh(k + 1.0) - .5) * .05 * A
                st.ty += (hsh(k + 5.0) - .5) * .01 * A
            }
            "rgb" -> { po.rgb += P.d("amt") / 100 * I * .014 * c.env * (if (P.b("lock")) c.beat else 1.0); po.rgba = P.d("ang") * D2R }
            "chroma" -> po.chroma += P.d("amt") / 100 * I * .02 * c.env
            "film" -> {
                val A = P.d("amt") / 100 * I * c.env
                when (P.s("lmode", "leak")) {
                    "leak" -> po.leak += A * (.6 + .4 * Math.sin(c.lt * P.d("freq") * TAUD))
                    "burn" -> po.burn += A * (if (hsh(Math.floor(c.lt * 10)) > .35) 1.0 else .4)
                    else -> po.grain += A
                }
            }
            "glow" -> {
                val A = P.d("amt") / 100 * I * c.env
                val w = if (P.b("lock")) c.beat else (.5 + .5 * Math.sin(TAUD * c.lt * P.d("freq")))
                when (P.s("gl", "glow")) {
                    "glow" -> po.glow += A * w
                    "soft" -> po.soft += A * (.5 + .5 * w)
                    "exp" -> po.exp += A * .8 * (w - .3)
                    else -> po.vig += A * w
                }
            }
            "sweep" -> {
                po.sweep += (if (P.b("sdark")) -1.0 else 1.0) * P.d("amt") / 100 * I
                po.swp = lerp(-.1, 1.1, c.p); po.swW = P.d("sww") / 100; po.swA = P.d("swa") * D2R; po.swcol = hex2f(P.s("color", "#ffffff"))
            }
            "flare" -> {
                po.flare += P.d("amt") / 100 * I * Math.sin(Math.PI * clamp(c.p, 0.0, 1.0))
                po.flx = lerp(P.d("fx") - .15, P.d("fx") + .15, c.p); po.fly = P.d("fy")
            }
            "vig" -> po.vig += P.d("amt") / 100 * I * (.7 + .3 * Math.sin(TAUD * c.lt * P.d("freq")))
            "cflash" -> {
                po.flash += P.d("amt") / 100 * I * c.env * (if (P.b("lock")) c.beat else Math.exp(-fract(c.lt * P.d("freq")) * 6)) * .7
                po.fcol = hex2f(P.s("color", "#ff3c78"))
            }
            // ---- creative / path / depth tools
            "path" -> {
                val segsJ = P.arr("segs")
                val segs = ArrayList<JSONObject>()
                if (segsJ != null) for (i in 0 until segsJ.length()) {
                    val s = segsJ.optJSONObject(i)
                    if (s != null && s.optJSONArray("pts") != null && s.optJSONArray("pts")!!.length() > 1) segs.add(s)
                }
                if (segs.isEmpty()) return
                var tot = 0.0
                val costs = DoubleArray(segs.size)
                for ((si, s) in segs.withIndex()) {
                    val pts = s.getJSONArray("pts")
                    var l = 0.0
                    for (i in 1 until pts.length()) {
                        l += Math.hypot(pts.getJSONArray(i).optDouble(0) - pts.getJSONArray(i - 1).optDouble(0), pts.getJSONArray(i).optDouble(1) - pts.getJSONArray(i - 1).optDouble(1))
                    }
                    val k = l / Math.max(.1, s.optDouble("speed", 1.0))
                    costs[si] = k
                    tot += k
                }
                var target = c.p * tot
                val p0 = segs[0].getJSONArray("pts").getJSONArray(0)
                var u = p0.optDouble(0)
                var v = p0.optDouble(1)
                var ang = 0.0
                for (si in segs.indices) {
                    val segCost = costs[si]
                    if (target > segCost && si < segs.size - 1) { target -= segCost; continue }
                    val f = clamp(target / Math.max(1e-6, segCost), 0.0, 1.0)
                    val pts = segs[si].getJSONArray("pts")
                    var L = 0.0
                    val ls = ArrayList<Double>()
                    for (i in 1 until pts.length()) {
                        val l = Math.hypot(pts.getJSONArray(i).optDouble(0) - pts.getJSONArray(i - 1).optDouble(0), pts.getJSONArray(i).optDouble(1) - pts.getJSONArray(i - 1).optDouble(1))
                        ls.add(l); L += l
                    }
                    var d = f * L
                    for (i in ls.indices) {
                        if (d <= ls[i] || i == ls.size - 1) {
                            val q = clamp(d / Math.max(1e-6, ls[i]), 0.0, 1.0)
                            val a = pts.getJSONArray(i)
                            val b = pts.getJSONArray(i + 1)
                            u = lerp(a.optDouble(0), b.optDouble(0), q); v = lerp(a.optDouble(1), b.optDouble(1), q)
                            ang = Math.atan2(b.optDouble(1) - a.optDouble(1), b.optDouble(0) - a.optDouble(0))
                            break
                        }
                        d -= ls[i]
                    }
                    break
                }
                val s = 1 + P.d("zpath") / 100 * I * P.KS
                st.sc *= s
                st.tx += (.5 - u) * c.qwF * s * P.AX
                st.ty += (.5 - v) * c.qhF * s * P.AY
                if (P.b("follow")) st.rot += Math.sin(ang) * .08 * P.d("follow") / 100 * P.KR
            }
            "poi" -> {
                val pts = P.arr("pts") ?: return
                val n = pts.length()
                if (n == 0) return
                val f = c.p * n
                val i = Math.min(n - 1, Math.floor(f).toInt())
                val q = f - i
                val trav = 1 - P.d("hold") / 100
                val prev = if (i == 0) doubleArrayOf(.5, .5, 100.0) else doubleArrayOf(pts.getJSONArray(i - 1).optDouble(0), pts.getJSONArray(i - 1).optDouble(1), pts.getJSONArray(i - 1).optDouble(2, 100.0))
                val cur = doubleArrayOf(pts.getJSONArray(i).optDouble(0), pts.getJSONArray(i).optDouble(1), pts.getJSONArray(i).optDouble(2, 100.0))
                val k = ease("sine", clamp(q / Math.max(.05, trav), 0.0, 1.0))
                val u = lerp(prev[0], cur[0], k)
                val v = lerp(prev[1], cur[1], k)
                val s = lerp(prev[2], cur[2], k) / 100
                val sc = 1 + (s - 1) * I
                st.sc *= sc
                st.tx += (.5 - u) * c.qwF * sc * P.AX
                st.ty += (.5 - v) * c.qhF * sc * P.AY
            }
            "tunnel" -> {
                val ph = c.p * P.d("loops", 2.0)
                st.sc *= 1 + .10 * Math.sin(TAUD * ph)
                st.free = true
                po.vig += .15
            }
            "window" -> {
                po.sp = P.d("dim") / 100 * I; po.spx = lerp(P.d("sx"), P.d("ex"), c.p); po.spy = lerp(P.d("sy"), P.d("ey"), c.p)
                po.spw = P.d("ww") / 200; po.sph = P.d("wh") / 200; po.spb = P.d("wblur")
            }
            "domino" -> {
                val q = if (P.s("io", "in") == "in") c.p else 1 - c.p
                st.alpha *= clamp(q * 1.4, 0.0, 1.0)
                st.sc *= .85 + .15 * q
                if (P.s("dmode") == "flip") st.scx *= Math.max(.15, Math.abs(Math.cos((1 - q) * Math.PI / 2)))
                st.free = true
            }
            "glass" -> {
                po.soft += .15 * Math.min(3.0, P.d("n") / 3.0)
                st.sc *= 1 + .02 * Math.sin(TAUD * c.lt * P.d("freq") * 2)
            }
            "scan" -> {
                val s = 1 + P.d("zoom") / 100 * I
                st.sc *= s
                st.tx += (1 - c.p * 2) * .5 * (s - 1) / s * s * .5 * P.AX
                po.sweep += P.d("glow") / 100 * I; po.swp = lerp(0.0, 1.0, c.p); po.swA = 0.0; po.swW = P.d("sww") / 100; po.swcol = doubleArrayOf(.7, .9, 1.0)
            }
            "lightpath" -> {
                val dx = P.d("ex") - P.d("sx")
                val dy = P.d("ey") - P.d("sy")
                po.sweep += P.d("glow") / 100 * I; po.swA = Math.atan2(-dy, dx); po.swp = lerp(-.1, 1.1, c.p); po.swW = P.d("sww") / 100; po.swcol = hex2f(P.s("color", "#fff3c0"))
            }
            "touchwave" -> {
                po.wt = 2
                po.wamp += P.d("amt") / 100 * I * .03 * Math.exp(-c.lt * P.d("decay")) * c.env
                po.wfreq = P.d("wfreq"); po.wph = c.lt * P.d("wspeed") * 3; po.wcx = P.d("fx", .5); po.wcy = P.d("fy", .5); po.wrad = 1.0
            }
            "keyframes" -> {
                val kj = P.arr("kf") ?: return
                val k = ArrayList<JSONObject>()
                for (i in 0 until kj.length()) kj.optJSONObject(i)?.let { k.add(it) }
                if (k.isEmpty()) return
                k.sortBy { it.optDouble("t") }
                val u = clamp(c.u, 0.0, 1.0)
                var a = k[0]
                var b = k[k.size - 1]
                for (i in 0 until k.size - 1) if (u >= k[i].optDouble("t") && u <= k[i + 1].optDouble("t")) { a = k[i]; b = k[i + 1]; break }
                if (u < k[0].optDouble("t")) b = a
                if (u > k[k.size - 1].optDouble("t")) a = b
                val f = if (b.optDouble("t") > a.optDouble("t")) ease("sine", clamp((u - a.optDouble("t")) / (b.optDouble("t") - a.optDouble("t")), 0.0, 1.0)) else 1.0
                st.sc *= lerp(a.optDouble("s", 100.0), b.optDouble("s", 100.0), f) / 100
                st.tx += lerp(a.optDouble("x"), b.optDouble("x"), f) / 100 * P.AX
                st.ty += lerp(a.optDouble("y"), b.optDouble("y"), f) / 100 * P.AY
                st.rot += lerp(a.optDouble("r"), b.optDouble("r"), f) * D2R * P.KR
                st.alpha *= lerp(a.optDouble("o", 100.0), b.optDouble("o", 100.0), f) / 100
            }
            "subjfocus" -> {
                po.blurK = when (P.s("bkind", "motion")) { "motion" -> 1.0; "gauss" -> 3.0; "zoom" -> 2.0; "radial" -> 4.0; else -> 1.0 }
                po.blur += P.d("bamt") * I * c.env
                val a = P.d("bang") * D2R
                po.bdx = Math.cos(a); po.bdy = Math.sin(a)
                subjAnchor(st, P, c) { s, fx, fy ->
                    po.fo = 1.0; po.fox = fx; po.foy = fy; po.forad = P.d("srad") / 100 * s.sc; po.foratio = P.d("sratio", 1.9); po.fofe = P.d("sfeather") / 100 * s.sc * .6 + .01
                }
            }
            "streak" -> {
                po.strk += P.d("sint") / 100 * I * c.env * (1 + .06 * Math.sin(c.lt * 9))
                po.strkang = P.d("sangle") * D2R; po.strkcol = hex2f(P.s("color", "#ffd7a0")); po.strkspill = P.d("sspill") / 100; po.strkbands = if (P.b("sbands")) 1.0 else 0.0
                subjAnchor(st, P, c) { s, fx, fy ->
                    po.strkx = fx; po.strky = clamp(fy + P.d("soff") / 100, 0.0, 1.0); po.strkth = P.d("sthick") / 100 * s.sc; po.strklen = P.d("slen") / 100 * Math.min(1.6, s.sc)
                }
            }
            "beam" -> {
                po.beam += I * c.env; po.beamdim = P.d("bdim") / 100; po.beamwarm = P.d("bwarm") / 100
                po.beamang = (P.d("bangle") + Math.sin(c.lt * .6) * P.d("bsweep")) * D2R
                subjAnchor(st, P, c) { s, fx, fy ->
                    po.beamx = fx + P.d("boff") / 100; po.beamy = fy; po.beamw = P.d("bwidth") / 100 * s.sc; po.beamsoft = P.d("bsoft") / 100 * s.sc + .002
                }
            }
        }
    }

    private fun clampI(v: Int, a: Int, b: Int) = if (v < a) a else if (v > b) b else v

    /**
     * Evaluate a preset. [tNorm] is the position inside the clip (0..1), [durSec] its duration,
     * [amt] the global intensity (1 = as designed) and [aspect] the frame width/height.
     */
    fun eval(id: String, tNorm: Float, durSec: Float, amt: Float, aspect: Float = 1f): MState {
        val st = MState()
        val layers = layersOf(id)
        if (layers.isEmpty()) return st
        val dur = Math.max(0.1, durSec.toDouble())
        val t = clamp(tNorm.toDouble(), 0.0, 1.0) * dur
        val W = Math.max(0.2, aspect.toDouble())
        val H = 1.0
        // pause windows freeze time for the other layers
        var te = t
        for (l in layers) if (l.base == "pause") {
            val P = Pm(l.p)
            val a = P.d("t0") * dur
            val b = Math.max(a + .05, P.d("t1", 1.0) * dur)
            if (t > a) te -= Math.min(t, b) - a
        }
        val beat = Math.exp(-fract(te * 2.0) * 5)
        for (l in layers) {
            if (l.base == "pause") continue
            val P = Pm(l.p)
            val pr = layerProg(P, te, dur)
            P.I = (P.d("inten", 100.0) / 100) * amt.toDouble() * (1 + P.d("beat") / 100 * beat * 1.2) * pr.am
            P.AX = P.d("ax", 100.0) / 100; P.AY = P.d("ay", 100.0) / 100; P.KS = P.d("ks", 100.0) / 100; P.KR = P.d("kr", 100.0) / 100
            val c = MCtx(pr.p, pr.u, pr.lt, pr.env, te, dur, beat, W, H)
            if (pr.am < 1) st.alpha *= pr.am
            applyBase(l.base, st, P, c)
            val op = P.d("opacity", 100.0)
            if (op < 100) st.alpha *= op / 100
        }
        for (f in st.anch) f(st)
        // warps are approximated by a small wobble
        val po = st.post
        if (po.wt > 0) {
            val k = Math.min(.08, Math.abs(po.wamp) * (if (po.wt >= 3) .04 else 2.0))
            st.sc *= 1 + k * (.5 + .5 * Math.sin(po.wph * 2))
            st.rot += Math.sin(po.wph * 1.3) * k * .6
        }
        return st
    }

    /** Folds the motion of [c] at clip-local time [ltMs] into [d] (used by preview and export alike). */
    fun applyToDyn(c: Clip, ltMs: Long, d: Dyn) {
        val id = c.motion
        if (id.isEmpty()) return
        val dur = c.durMs()
        val aspect = c.dispW().toFloat() / Math.max(1, c.dispH()).toFloat()
        val s = eval(id, clampF(ltMs.toFloat() / Math.max(1L, dur).toFloat(), 0f, 1f), dur / 1000f, c.motionAmt, aspect)
        d.s *= s.sc.toFloat()
        d.tx += s.tx.toFloat()
        d.ty += s.ty.toFloat()
        d.rot += Math.toDegrees(s.rot).toFloat()
        // 3D tilts become a foreshortening of the flat picture
        val cy = Math.max(.04, Math.abs(Math.cos(s.ry)))
        val cx = Math.max(.04, Math.abs(Math.cos(s.rx)))
        d.sx *= (s.scx * s.flx * cy).toFloat()
        d.sy *= (s.scy * s.fly * cx).toFloat()
        d.dark += clampF((1.0 - s.alpha).toFloat(), 0f, 1f)
        d.bright += clampF(s.post.exp.toFloat(), -1f, 1f) * 0.5f
    }

    // ------------------------------------------------------------ post effects drawn over the picture
    private val pp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pth = Path()
    private val rf = RectF()

    private fun col(c: DoubleArray, a: Double): Int =
        Color.argb((clamp(a, 0.0, 1.0) * 255).toInt(), (clamp(c[0], 0.0, 1.0) * 255).toInt(), (clamp(c[1], 0.0, 1.0) * 255).toInt(), (clamp(c[2], 0.0, 1.0) * 255).toInt())

    /** [vr] = x, y, w, h of the visible picture area, [ltMs] = time inside the clip. */
    fun drawPost(c: Canvas, vr: FloatArray, clip: Clip, ltMs: Long, W: Float) {
        val id = clip.motion
        if (id.isEmpty() || !hasOverlayPost(id)) return
        val dur = clip.durMs()
        val aspect = clip.dispW().toFloat() / Math.max(1, clip.dispH()).toFloat()
        val s = eval(id, clampF(ltMs.toFloat() / Math.max(1L, dur).toFloat(), 0f, 1f), dur / 1000f, clip.motionAmt, aspect)
        val po = s.post
        val x = vr[0]; val y = vr[1]; val w = vr[2]; val h = vr[3]
        if (w < 2f || h < 2f) return
        val tSec = ltMs / 1000.0
        c.save()
        c.clipRect(x, y, x + w, y + h)
        pp.reset(); pp.isAntiAlias = true
        // soft haze (blur approximation)
        val haze = clamp(po.soft * .30 + po.blur / 100.0 * .35, 0.0, .6)
        if (haze > .01) {
            pp.style = Paint.Style.FILL
            if (po.fo > 0.5) {
                val r = Math.max(.05, po.forad) * h * Math.max(1.0, po.foratio * .5)
                pp.shader = RadialGradient(x + (po.fox * w).toFloat(), y + (po.foy * h).toFloat(), r.toFloat() * 2f, intArrayOf(0, 0, col(doubleArrayOf(.9, .9, .95), haze)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            } else pp.color = col(doubleArrayOf(.92, .92, .96), haze * .8)
            c.drawRect(x, y, x + w, y + h, pp)
            pp.shader = null
        }
        // glow
        if (po.glow > .01) {
            pp.style = Paint.Style.FILL
            pp.shader = RadialGradient(x + w / 2f, y + h * .45f, Math.max(w, h) * .7f, intArrayOf(col(doubleArrayOf(1.0, .97, .9), clamp(po.glow * .35, 0.0, .6)), 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w, y + h, pp); pp.shader = null
        }
        // film leak / burn
        if (po.leak > .01) {
            pp.style = Paint.Style.FILL
            pp.shader = LinearGradient(x, y, x + w * .6f, y + h * .3f, col(doubleArrayOf(1.0, .45, .1), clamp(po.leak * .5, 0.0, .7)), 0, Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w, y + h, pp); pp.shader = null
        }
        if (po.burn > .01) {
            pp.style = Paint.Style.FILL
            pp.shader = RadialGradient(x + w * .85f, y + h * .15f, Math.max(w, h) * .8f, intArrayOf(col(doubleArrayOf(1.0, .8, .4), clamp(po.burn * .75, 0.0, .9)), col(doubleArrayOf(1.0, .3, .05), clamp(po.burn * .35, 0.0, .5)), 0), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w, y + h, pp); pp.shader = null
        }
        // light sweep (or shadow sweep)
        if (Math.abs(po.sweep) > .01) {
            c.save()
            c.translate(x + w / 2f, y + h / 2f)
            c.rotate(Math.toDegrees(-po.swA).toFloat())
            val diag = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
            val bw = Math.max(.02, po.swW) * diag
            val cx0 = ((po.swp - .5) * diag).toFloat()
            val dark = po.sweep < 0
            val cc = if (dark) doubleArrayOf(0.0, 0.0, 0.0) else po.swcol
            val a = clamp(Math.abs(po.sweep) * .75, 0.0, .9)
            pp.style = Paint.Style.FILL
            pp.shader = LinearGradient(cx0 - bw.toFloat(), 0f, cx0 + bw.toFloat(), 0f, intArrayOf(0, col(cc, a), 0), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(-diag, -diag, diag, diag, pp)
            pp.shader = null
            c.restore()
        }
        // lens flare
        if (po.flare > .01) {
            val fx = x + (po.flx * w).toFloat()
            val fy = y + (po.fly * h).toFloat()
            pp.style = Paint.Style.FILL
            pp.shader = RadialGradient(fx, fy, Math.max(w, h) * .35f, intArrayOf(col(doubleArrayOf(1.0, .95, .8), clamp(po.flare * .9, 0.0, 1.0)), col(doubleArrayOf(1.0, .8, .5), clamp(po.flare * .25, 0.0, .5)), 0), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w, y + h, pp); pp.shader = null
            pp.style = Paint.Style.STROKE; pp.strokeWidth = Math.max(2f, w * .004f); pp.color = col(doubleArrayOf(1.0, .9, .7), clamp(po.flare * .5, 0.0, .6))
            c.drawCircle(x + w - (fx - x), y + h - (fy - y), w * .06f, pp)
            pp.style = Paint.Style.FILL
        }
        // light streak across the subject
        if (po.strk > .01) {
            val sx = x + (po.strkx * w).toFloat()
            val sy = y + (po.strky * h).toFloat()
            val len = (po.strklen * w).toFloat()
            val th = Math.max(2.0, po.strkth * h).toFloat()
            c.save(); c.translate(sx, sy); c.rotate(Math.toDegrees(po.strkang).toFloat())
            pp.style = Paint.Style.FILL
            pp.shader = LinearGradient(-len, 0f, len, 0f, intArrayOf(0, col(po.strkcol, clamp(po.strk * .9, 0.0, 1.0)), 0), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(-len, -th / 2f, len, th / 2f, pp)
            pp.shader = LinearGradient(-len, 0f, len, 0f, intArrayOf(0, col(po.strkcol, clamp(po.strk * po.strkspill * .35, 0.0, .6)), 0), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(-len, -th * 3f, len, th * 3f, pp)
            pp.shader = null
            c.restore()
        }
        // spotlight beam: darken outside a tilted band
        if (po.beam > .01) {
            c.save()
            val bx = x + (po.beamx * w).toFloat()
            val by = y + (po.beamy * h).toFloat()
            c.translate(bx, by)
            c.rotate(Math.toDegrees(po.beamang).toFloat() - 90f)
            val diag = Math.hypot(w.toDouble(), h.toDouble()).toFloat()
            val bw = Math.max(.01, po.beamw) * w
            val sf = Math.max(.002, po.beamsoft) * w
            val dim = col(doubleArrayOf(0.0, 0.0, 0.0), clamp(po.beamdim * po.beam, 0.0, .95))
            val warm = col(doubleArrayOf(1.0, .9, .65), clamp(po.beamwarm * .25 * po.beam, 0.0, .5))
            val a0 = (-bw - sf).toFloat() / diag
            pp.style = Paint.Style.FILL
            pp.shader = LinearGradient(-diag, 0f, diag, 0f,
                intArrayOf(dim, dim, warm, warm, dim, dim),
                floatArrayOf(0f, (.5 + (-bw - sf) / (2 * diag)).toFloat().coerceIn(0.001f, .49f), (.5 + (-bw) / (2 * diag)).toFloat().coerceIn(0.002f, .495f), (.5 + bw / (2 * diag)).toFloat().coerceIn(.505f, .998f), (.5 + (bw + sf) / (2 * diag)).toFloat().coerceIn(.51f, .999f), 1f),
                Shader.TileMode.CLAMP)
            if (a0 < 10f) c.drawRect(-diag, -diag, diag, diag, pp)
            pp.shader = null
            c.restore()
        }
        // moving window: dim everything outside
        if (po.sp > .01) {
            val cx = x + (po.spx * w).toFloat()
            val cy = y + (po.spy * h).toFloat()
            val hw = (po.spw * 2 * w * .5).toFloat()
            val hh = (po.sph * 2 * h * .5).toFloat()
            pth.reset()
            pth.addRect(x, y, x + w, y + h, Path.Direction.CW)
            rf.set(cx - hw, cy - hh, cx + hw, cy + hh)
            pth.addRoundRect(rf, Math.min(hw, hh) * .3f, Math.min(hw, hh) * .3f, Path.Direction.CCW)
            pth.fillType = Path.FillType.EVEN_ODD
            pp.style = Paint.Style.FILL; pp.color = col(doubleArrayOf(0.0, 0.0, 0.0), clamp(po.sp, 0.0, .95))
            c.drawPath(pth, pp)
        }
        // vignette
        if (po.vig > .01) {
            pp.style = Paint.Style.FILL
            pp.shader = RadialGradient(x + w / 2f, y + h / 2f, Math.hypot(w.toDouble(), h.toDouble()).toFloat() * .55f, intArrayOf(0, 0, col(doubleArrayOf(0.0, 0.0, 0.0), clamp(po.vig * .8, 0.0, .9))), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + w, y + h, pp); pp.shader = null
        }
        // glitch bars and rgb fringes
        if (po.glitch > .02) {
            val k = Math.floor(tSec * 14.0)
            pp.style = Paint.Style.FILL
            for (i in 0 until 4) {
                val by = y + (hsh(k + i * 3.1) * .9 * h).toFloat()
                val bh = (.01 + hsh(k + i * 7.7) * .05) * h
                val cc = if (i % 2 == 0) doubleArrayOf(0.0, 1.0, 1.0) else doubleArrayOf(1.0, 0.0, .8)
                pp.color = col(cc, clamp(po.glitch * .35, 0.0, .5))
                val off = ((hsh(k + i) - .5) * .2 * w).toFloat()
                c.drawRect(x + off, by, x + w + off, by + bh.toFloat(), pp)
            }
        }
        if (po.rgb > .001 || po.chroma > .001) {
            val e = (Math.max(po.rgb, po.chroma) * 6.0 * w).toFloat()
            pp.style = Paint.Style.FILL
            pp.shader = LinearGradient(x, 0f, x + Math.max(2f, e * 3f), 0f, col(doubleArrayOf(1.0, 0.0, 0.1), .35), 0, Shader.TileMode.CLAMP)
            c.drawRect(x, y, x + Math.max(2f, e * 3f), y + h, pp)
            pp.shader = LinearGradient(x + w, 0f, x + w - Math.max(2f, e * 3f), 0f, col(doubleArrayOf(0.0, .9, 1.0), .35), 0, Shader.TileMode.CLAMP)
            c.drawRect(x + w - Math.max(2f, e * 3f), y, x + w, y + h, pp)
            pp.shader = null
        }
        // grain
        if (po.grain > .01) {
            val k = Math.floor(tSec * 24.0)
            pp.style = Paint.Style.FILL
            val n = (160 * clamp(po.grain, 0.0, 1.5)).toInt()
            for (i in 0 until n) {
                val gx = x + (hsh(k + i * 1.37) * w).toFloat()
                val gy = y + (hsh(k + i * 2.71 + 9.0) * h).toFloat()
                pp.color = if (i % 2 == 0) Color.argb(60, 255, 255, 255) else Color.argb(60, 0, 0, 0)
                c.drawRect(gx, gy, gx + w * .004f + 1f, gy + w * .004f + 1f, pp)
            }
        }
        // flash (colour flash on top of everything)
        if (po.flash > .01) {
            pp.style = Paint.Style.FILL
            pp.color = col(po.fcol, clamp(po.flash * .9, 0.0, .95))
            c.drawRect(x, y, x + w, y + h, pp)
        }
        c.restore()
    }
}
