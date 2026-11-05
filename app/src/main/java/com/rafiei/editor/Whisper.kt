package com.rafiei.editor

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Native whisper.cpp (MIT) bridge; the library is built from app/src/main/cpp. Missing library = engine unavailable. */
object WhisperLib {
    @JvmField var loaded = false

    init {
        try { System.loadLibrary("rafiei_whisper"); loaded = true } catch (t: Throwable) { loaded = false }
    }

    @JvmStatic external fun initModel(path: String, dtwPreset: Int): Long
    @JvmStatic external fun freeModel(ctx: Long)
    @JvmStatic external fun abort(on: Boolean)
    @JvmStatic external fun progress(): Int
    @JvmStatic external fun transcribe(ctx: Long, pcm: FloatArray, lang: String, threads: Int, dtw: Boolean, prompt: String, vadModel: String): Int
    @JvmStatic external fun lang(ctx: Long): String
    @JvmStatic external fun segT0(ctx: Long, i: Int): Long
    @JvmStatic external fun segT1(ctx: Long, i: Int): Long
    @JvmStatic external fun segNoSpeech(ctx: Long, i: Int): Float
    @JvmStatic external fun tokCount(ctx: Long, i: Int): Int
    @JvmStatic external fun eot(ctx: Long): Int
    @JvmStatic external fun tokBytes(ctx: Long, i: Int, j: Int): ByteArray
    @JvmStatic external fun tokData(ctx: Long, i: Int, j: Int): LongArray
    @JvmStatic external fun sysInfo(): String
    @JvmStatic external fun cores(): Int
}

/** The ggml model files (downloaded once, with the user's consent) and the optional Silero VAD model. */
object WhisperBank {
    class Item(val id: String, val file: String, val fa: String, val en: String, val mb: Int, val dtw: Int, val ramMb: Int)

    // dtw = whisper_alignment_heads_preset value of the matching model (for word-time alignment)
    val MODELS = listOf(
        Item("tiny", "ggml-tiny-q5_1.bin", "خیلی سریع (ضعیف برای فارسی)", "Tiny – fastest", 31, 4, 300),
        Item("base", "ggml-base-q5_1.bin", "سریع و سبک", "Base – fast", 57, 6, 400),
        Item("small", "ggml-small-q5_1.bin", "متوسط – پیشنهادی برای فارسی", "Small – recommended", 181, 8, 800),
        Item("medium", "ggml-medium-q5_0.bin", "دقیق (کند، به رم زیاد نیاز دارد)", "Medium – accurate, slow", 514, 10, 1800),
        Item("turbo", "ggml-large-v3-turbo-q5_0.bin", "بهترین دقت (سنگین)", "Large-v3-turbo – best, heavy", 547, 14, 2200)
    )
    const val BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    const val VAD_FILE = "ggml-silero-v5.1.2.bin"
    const val VAD_URL = "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin"

    fun dir(c: Context) = File(c.filesDir, "whisper").also { it.mkdirs() }
    fun file(c: Context, it: Item) = File(dir(c), it.file)
    /** "Installed" means the file exists and is at least 85 % of the advertised size,
     *  so a truncated download is not silently treated as a working model. */
    fun installed(c: Context): List<Item> = MODELS.filter { file(c, it).let { f -> f.exists() && f.length() > 1_000_000 && (it.mb <= 0 || f.length() >= it.mb * 1048576L * 85L / 100L) } }
    fun vadFile(c: Context) = File(dir(c), VAD_FILE)
    fun delete(c: Context, it: Item) { file(c, it).delete(); File(dir(c), it.file + ".part").delete() }

    /** Downloads with resume support. Returns null on success or an error text. */
    fun download(c: Context, url: String, dst: File, expectMb: Int, progress: (Long, Long) -> Unit): String? {
        val part = File(dst.path + ".part")
        try {
            AutoSub.cancelFlag = false
            var have = if (part.exists()) part.length() else 0L
            val cn = URL(url).openConnection() as HttpURLConnection
            cn.connectTimeout = 20000; cn.readTimeout = 30000
            if (have > 0) cn.setRequestProperty("Range", "bytes=$have-")
            val code = cn.responseCode
            if (code != 200 && code != 206) return "HTTP $code"
            if (code == 200) have = 0L
            val startHave = have
            val serverTotal = cn.contentLengthLong // bytes remaining in this response (<= 0 when unknown)
            val total = if (serverTotal > 0) serverTotal + startHave else expectMb * 1048576L
            cn.inputStream.use { ins ->
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(have)
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        if (AutoSub.cancelFlag) return "cancelled (partial file kept, will resume)"
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        have += n
                        progress(have, total)
                    }
                    raf.setLength(have)
                }
            }
            // integrity: the file must match the size the server promised, otherwise a
            // truncated stream would be renamed into place and only fail later at model load.
            if (serverTotal > 0 && have != serverTotal + startHave) return "incomplete (kept for resume)"
            if (have < 500_000) { part.delete(); return "incomplete" }
            dst.delete()
            return if (part.renameTo(dst)) null else "rename failed"
        } catch (e: Throwable) {
            return "network (a partial file is kept so the download can resume)"
        }
    }
}

/** Runs whisper.cpp over 16 kHz PCM and returns timed words (seconds from the start of the PCM). */
object WhisperEngine {
    @Volatile var chunk = 0
    @Volatile var chunks = 1
    /** False until the first transcribe of the current run starts, so a stale native
     *  progress value from a previous run is never shown as the new run's percent. */
    @Volatile var started = false

    fun overallPercent(): Int = if (!started) 0 else Math.min(100, (chunk * 100 + WhisperLib.progress()) / Math.max(1, chunks))

    private const val SR = 16000
    private const val PROMPT_FA = "سلام. این یک متن فارسی است، با نقطه و ویرگول درست."

    private fun splitPoints(pcm: ShortArray): List<Int> {
        val target = SR * 480 // about 8 minutes per chunk keeps memory low
        val out = ArrayList<Int>()
        var start = 0
        while (pcm.size - start > target + SR * 30) {
            val lo = start + target - SR * 15
            val hi = start + target
            var best = hi
            var bestE = Double.MAX_VALUE
            var i = lo
            while (i < hi) {
                val e = AutoSub.rms(pcm, i, Math.min(pcm.size, i + 320))
                if (e < bestE) { bestE = e; best = i }
                i += 320
            }
            out.add(best)
            start = best
        }
        return out
    }

    /** Returns words (or an error text). */
    fun run(pcm: ShortArray, model: File, dtwPreset: Int, lang: String, vadPath: String, threads: Int): Pair<List<SubDsp.Word>, String?> {
        if (!WhisperLib.loaded) return Pair(emptyList(), "native library not available")
        val ctx = WhisperLib.initModel(model.absolutePath, dtwPreset)
        if (ctx == 0L) return Pair(emptyList(), "model could not be loaded (not enough memory or a damaged file)")
        val words = ArrayList<SubDsp.Word>()
        try {
            val cuts = ArrayList<Int>()
            cuts.add(0)
            cuts.addAll(splitPoints(pcm))
            cuts.add(pcm.size)
            chunks = cuts.size - 1
            for (ci in 0 until cuts.size - 1) {
                if (AutoSub.cancelFlag) break // do not even start the next chunk
                chunk = ci
                val a = cuts[ci]
                val b = cuts[ci + 1]
                val f = FloatArray(b - a) { pcm[a + it] / 32768f }
                val prompt = if (lang == "fa") PROMPT_FA else ""
                started = true
                val ns = WhisperLib.transcribe(ctx, f, lang, threads, dtwPreset > 0, prompt, vadPath)
                started = false
                if (ns < 0) { if (AutoSub.cancelFlag) break else return Pair(words, "decode failed ($ns)") }
                collect(ctx, ns, a / SR.toDouble(), dtwPreset > 0, words)
                if (AutoSub.cancelFlag) break
            }
        } catch (t: Throwable) {
            return Pair(words, t.message ?: t.javaClass.simpleName)
        } finally {
            WhisperLib.freeModel(ctx)
        }
        cleanup(words)
        return Pair(words, null)
    }

    private fun collect(ctx: Long, ns: Int, off: Double, dtw: Boolean, out: ArrayList<SubDsp.Word>) {
        val eot = WhisperLib.eot(ctx)
        for (si in 0 until ns) {
            if (WhisperLib.segNoSpeech(ctx, si) > 0.85f) continue
            val nt = WhisperLib.tokCount(ctx, si)
            val seg = ArrayList<SubDsp.Word>()
            var buf = ByteArrayOutputStream()
            var ws = -1.0
            var we = 0.0
            fun flush() {
                if (buf.size() == 0) return
                val t = String(buf.toByteArray(), Charsets.UTF_8).trim()
                if (t.isNotEmpty()) seg.add(SubDsp.Word(t, ws, Math.max(we, ws + 0.08)))
                buf = ByteArrayOutputStream()
                ws = -1.0
            }
            for (tj in 0 until nt) {
                val d = WhisperLib.tokData(ctx, si, tj)
                if (d[0] >= eot) continue
                val b = WhisperLib.tokBytes(ctx, si, tj)
                if (b.isEmpty()) continue
                if (b[0] == 0x20.toByte()) flush()
                if (ws < 0) ws = off + (if (dtw && d[3] > 0) d[3] else d[1]) / 100.0
                buf.write(b)
                we = off + Math.max(d[1], d[2]) / 100.0
            }
            flush()
            // word end = start of the next word when they touch (DTW gives starts only)
            for (i in 0 until seg.size - 1) {
                val nx = seg[i + 1].s
                if (nx - seg[i].s < 1.2) seg[i].e = Math.min(Math.max(seg[i].e, seg[i].s + 0.08), nx)
                if (seg[i].e > nx) seg[i].e = nx
            }
            out.addAll(seg)
        }
    }

    /** Drops obvious hallucinations (the same word many times in a row) and fixes ordering. */
    private fun cleanup(w: ArrayList<SubDsp.Word>) {
        var i = 0
        while (i < w.size) {
            var j = i
            while (j + 1 < w.size && w[j + 1].w == w[i].w) j++
            if (j - i >= 4) { for (k in j downTo i + 1) w.removeAt(k); i++ } else i = j + 1
        }
        for (k in 1 until w.size) {
            if (w[k].s < w[k - 1].s) w[k].s = w[k - 1].s
            if (w[k].e < w[k].s + 0.08) w[k].e = w[k].s + 0.08
        }
    }
}
