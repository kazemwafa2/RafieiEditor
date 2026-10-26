package com.rafiei.editor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Offline spectrum analysis used by the equalizer overlay. The audio is decoded once, split into
 * 30 fps frames and reduced to NBAND levels per frame (the same adaptive normalisation the original
 * web version applied in real time). Preview and export read exactly the same data.
 */
object EqAudio {
    const val FPS = 30

    class Track(val n: Int, val lv: FloatArray, val bass: FloatArray)

    private val cache = HashMap<String, Track?>()
    private val loading = HashSet<String>()
    private val done = HashSet<String>()
    @Volatile
    var listener: (() -> Unit)? = null

    fun get(path: String): Track? = synchronized(cache) { cache[path] }

    fun isBusy(): Boolean = synchronized(cache) { loading.isNotEmpty() }

    fun ensure(path: String) {
        synchronized(cache) {
            if (done.contains(path) || loading.contains(path)) return
            loading.add(path)
        }
        Bg.run {
            val t = analyze(path)
            synchronized(cache) {
                cache[path] = t
                loading.remove(path)
                done.add(path)
            }
            Bg.ui { listener?.invoke() }
        }
    }

    /** Sources (file paths) of the audio that can be heard in the project. */
    fun sourcesOf(p: Project): List<String> {
        val out = ArrayList<String>()
        for (a in p.audios) if (a.uri.isNotEmpty()) out.add(MediaUtil.pathOf(a.uri))
        if (!p.muteClips) for (c in p.clips) if (!c.isImage && c.hasAudio) out.add(MediaUtil.pathOf(c.uri))
        return out.distinct()
    }

    /** Blocks until every source of the project has been analysed (used before export). */
    fun prepareBlocking(p: Project) {
        for (path in sourcesOf(p)) {
            var run = false
            synchronized(cache) {
                if (!done.contains(path) && !loading.contains(path)) { loading.add(path); run = true }
            }
            if (run) {
                val t = analyze(path)
                synchronized(cache) { cache[path] = t; loading.remove(path); done.add(path) }
            }
        }
        // another thread may still be analysing one of them
        var guard = 0
        while (guard < 600 && synchronized(cache) { sourcesOf(p).any { loading.contains(it) } }) {
            try { Thread.sleep(100) } catch (e: Exception) { }
            guard++
        }
    }

    /**
     * Fills [out] (size NBAND + 1: levels, then bass) for project time [tMs].
     * Returns false when no analysed audio is available (the caller then uses an idle animation).
     */
    fun fill(p: Project, tMs: Long, out: FloatArray): Boolean {
        var path: String? = null
        var srcMs = 0L
        for (a in p.audios) {
            if (a.volume > 0.05f && tMs >= a.startMs && tMs < a.endMs() && a.uri.isNotEmpty()) {
                path = MediaUtil.pathOf(a.uri)
                srcMs = a.trimStart + ((tMs - a.startMs) * a.speed).toLong()
                break
            }
        }
        if (path == null && !p.muteClips) {
            val ci = p.clipIndexAt(tMs)
            if (ci >= 0) {
                val c = p.clips[ci]
                if (!c.isImage && c.hasAudio && c.volume > 0.05f) {
                    path = MediaUtil.pathOf(c.uri)
                    srcMs = c.trimStart + ((tMs - p.clipStart(ci)) * c.speed).toLong()
                }
            }
        }
        if (path == null) return false
        val t = get(path)
        if (t == null) {
            ensure(path)
            return false
        }
        if (t.n < 2) return false
        val pos = clampF(srcMs / 1000f * FPS, 0f, (t.n - 1).toFloat())
        val i0 = pos.toInt()
        val i1 = Math.min(t.n - 1, i0 + 1)
        val f = pos - i0
        for (j in 0 until NBAND) out[j] = t.lv[i0 * NBAND + j] * (1 - f) + t.lv[i1 * NBAND + j] * f
        out[NBAND] = t.bass[i0] * (1 - f) + t.bass[i1] * f
        return true
    }

    // ------------------------------------------------------------ analysis

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = Math.cos(ang).toFloat()
            val wi = Math.sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun analyze(path: String): Track? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var ti = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { ti = i; fmt = f; break }
            }
            if (ti < 0 || fmt == null) return null
            ex.selectTrack(ti)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()

            val N = 2048
            val ring = FloatArray(N)
            var count = 0L
            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var hop = Math.max(1, sr / FPS)
            val win = FloatArray(N) { (0.5 - 0.5 * Math.cos(2.0 * Math.PI * it / (N - 1))).toFloat() }
            val re = FloatArray(N)
            val im = FloatArray(N)
            val hi = FloatArray(NBAND) { -45f }
            val lo = FloatArray(NBAND) { -100f }
            val sm = FloatArray(NBAND) { 0.05f }
            var bassLv = 0f
            val frames = ArrayList<FloatArray>()
            val basses = ArrayList<Float>()
            val bandLo = IntArray(NBAND)
            val bandHi = IntArray(NBAND)
            fun setupBands() {
                val binHz = sr / 2f / (N / 2)
                for (j in 0 until NBAND) {
                    val f0 = 40.0 * Math.pow(14000.0 / 40.0, j.toDouble() / NBAND)
                    val f1 = 40.0 * Math.pow(14000.0 / 40.0, (j + 1.0) / NBAND)
                    val i0 = Math.floor(f0 / binHz).toInt()
                    bandLo[j] = i0
                    bandHi[j] = Math.max(i0 + 1, Math.floor(f1 / binHz).toInt())
                }
            }
            setupBands()

            fun processFrame() {
                // window ending at the current sample
                val start = (count % N).toInt()
                for (i in 0 until N) {
                    re[i] = ring[(start + i) % N] * win[i]
                    im[i] = 0f
                }
                fft(re, im)
                val lv = FloatArray(NBAND)
                for (j in 0 until NBAND) {
                    var sum = 0.0
                    var cnt = 0
                    var i = bandLo[j]
                    while (i < bandHi[j] && i < N / 2) {
                        val m = Math.hypot(re[i].toDouble(), im[i].toDouble()) / (N / 4.0)
                        sum += m * m
                        cnt++
                        i++
                    }
                    val db = (10.0 * Math.log10(sum / Math.max(1, cnt) + 1e-12)).toFloat()
                    if (db > hi[j]) hi[j] = db else hi[j] -= 0.06f
                    if (db < lo[j]) lo[j] = db else lo[j] += 0.06f
                    if (hi[j] - lo[j] < 22f) lo[j] = hi[j] - 22f
                    var l = clampF((db - lo[j]) / (hi[j] - lo[j]), 0f, 1f)
                    l = Math.pow(l.toDouble(), 1.25).toFloat()
                    sm[j] = Math.max(l, sm[j] * 0.64f)
                    lv[j] = sm[j]
                }
                var b = 0f
                for (j in 0 until 6) b += sm[j]
                bassLv += (b / 6f - bassLv) * 0.75f
                frames.add(lv)
                basses.add(bassLv)
            }

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var guard = 0
            while (!outDone) {
                if (!inDone) {
                    val ib = codec.dequeueInputBuffer(10000)
                    if (ib >= 0) {
                        val buf = codec.getInputBuffer(ib)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(ib, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 10000)
                if (ob >= 0) {
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(ob)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val sb = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val total = sb.remaining()
                        var i = 0
                        while (i + ch <= total) {
                            var s = 0f
                            for (c in 0 until ch) s += sb.get(i + c)
                            ring[(count % N).toInt()] = s / ch / 32768f
                            count++
                            if (count >= N && count % hop == 0L) processFrame()
                            i += ch
                        }
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    hop = Math.max(1, sr / FPS)
                    setupBands()
                } else if (ob == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    guard++
                    if (inDone && guard > 3000) break
                }
            }
            val n = frames.size
            if (n < 2) return null
            val lvAll = FloatArray(n * NBAND)
            val bs = FloatArray(n)
            for (i in 0 until n) {
                System.arraycopy(frames[i], 0, lvAll, i * NBAND, NBAND)
                bs[i] = basses[i]
            }
            return Track(n, lvAll, bs)
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { ex.release() } catch (e: Throwable) { }
        }
    }
}
