package com.rafiei.editor

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Lang.init(this)
    }

    companion object {
        lateinit var ctx: Context
    }
}

/** Language switch: Persian (default) or English. */
object Lang {
    var fa = true
    fun init(c: Context) {
        fa = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("fa", true)
    }

    fun set(c: Context, v: Boolean) {
        fa = v
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("fa", v).apply()
    }
}

fun L(fa: String, en: String): String = if (Lang.fa) fa else en

object C {
    const val BG = 0xFF262626.toInt()
    const val BG2 = 0xFF1C1C1C.toInt()
    const val PANEL = 0xFF2F2F2F.toInt()
    const val CARD = 0xFF3A3A3A.toInt()
    const val TXT = 0xFFFFFFFF.toInt()
    const val TXT2 = 0xFFA8A8A8.toInt()
    const val ACCENT = 0xFFFF4D6D.toInt()
    const val ACCENT2 = 0xFFFF8A5B.toInt()
    const val GREEN = 0xFF00E096.toInt()
    const val PURPLE = 0xFF9B6BD6.toInt()
    const val BLUE = 0xFF4DA3FF.toInt()
    const val YELLOW = 0xFFFFD93D.toInt()
}

object Bg {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    fun run(block: () -> Unit) {
        pool.execute {
            try {
                block()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    fun postDelayed(ms: Long, block: () -> Unit) {
        main.postDelayed({ block() }, ms)
    }
}

fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(10)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun View.dp(v: Int): Int = context.dp(v)
fun View.dpf(v: Float): Float = context.dpf(v)

fun fmtTime(ms: Long): String {
    val t = if (ms < 0) 0 else ms
    val totalTenths = t / 100
    val tenths = totalTenths % 10
    val totalSec = totalTenths / 10
    val s = totalSec % 60
    val m = totalSec / 60
    return String.format(Locale.US, "%d:%02d.%d", m, s, tenths)
}

fun fmtShort(ms: Long): String {
    val totalSec = (ms + 500) / 1000
    val s = totalSec % 60
    val m = totalSec / 60
    return if (m == 0L) String.format(Locale.US, ":%02d", s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun roundedBg(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.setColor(color)
    g.cornerRadius = radiusPx
    if (strokePx > 0) g.setStroke(strokePx, strokeColor)
    return g
}

fun gradBg(c1: Int, c2: Int, radiusPx: Float, angle: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable {
    val g = GradientDrawable(angle, intArrayOf(c1, c2))
    g.cornerRadius = radiusPx
    return g
}

fun clampF(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
fun clampL(v: Long, a: Long, b: Long): Long = if (v < a) a else if (v > b) b else v
fun clampI(v: Int, a: Int, b: Int): Int = if (v < a) a else if (v > b) b else v

fun mixColor(a: Int, b: Int, t: Float): Int {
    val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
    val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
    val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    val al = (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt()
    return Color.argb(al, r, g, bl)
}

fun withAlpha(color: Int, a: Float): Int = Color.argb((clampF(a, 0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

/** Cheap deterministic pseudo-random number in 0..1 from an integer seed. */
fun hashNoise(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return ((x and 0x7fffffff) % 100000) / 100000f
}

fun dirOf(c: Context, name: String): File {
    val d = File(c.filesDir, name)
    if (!d.exists()) d.mkdirs()
    return d
}
