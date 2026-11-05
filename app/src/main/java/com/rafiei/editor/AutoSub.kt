package com.rafiei.editor

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.FileOutputStream
import java.nio.ByteOrder

/** Automatic subtitles (experimental): the audio is cut into short pieces and each piece goes through the phone's speech recogniser. */
object AutoSub {
    /** Hard cap for one in-memory PCM buffer: 48M samples = 50 minutes of 16 kHz audio (~96 MB). */
    private const val MAX_PCM_SAMPLES = 48_000_000

    /**
     * Decodes the audio of [path] between [fromMs] and [toMs] to 16 kHz mono PCM.
     *
     * Correctness notes:
     *  - The container only allows seeking to a sync point at or BEFORE [fromMs]; every sample the
     *    decoder emits before [fromMs] is discarded, so recognition times line up with the selected
     *    range (previously this audio leaked in and shifted every subtitle earlier).
     *  - Output stops exactly at [toMs] at sample granularity.
     *  - Checked against [cancelFlag] continuously, so "Stop" really stops the decode.
     *  - 44.1/48 kHz sources are box-filtered while decimating to reduce aliasing into the ASR band.
     */
    fun decode16k(path: String, fromMs: Long, toMs: Long): ShortArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var ti = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { ti = i; fmt = f; break }
            }
            if (ti < 0 || fmt == null) return null
            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var ch = Math.max(1, if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2)
            ex.selectTrack(ti)
            ex.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // The extractor now sits on a sync point at or before fromMs. Compute how many source
            // frames must be dropped so the first kept sample is the one at/after fromMs.
            val syncUs = Math.max(0L, ex.sampleTime)
            var skip = if (syncUs < fromMs * 1000L) (fromMs * 1000L - syncUs) * sr / 1_000_000L else 0L
            // Last source frame (relative to the sync point) that belongs to the range.
            val lastFrame = if (toMs > fromMs) (toMs * 1000L - Math.max(syncUs, 0L)).coerceAtLeast(0L) * sr / 1_000_000L else 0L
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var outBuf = ShortArray(1 shl 18)
            var outN = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var pos = 0.0 // position in source samples, used for resampling
            var next = 0.0
            var srcFrame = 0L // source frames consumed since the sync point
            var guard = 0
            while (!outDone) {
                if (cancelFlag) return null
                if (srcFrame > lastFrame) break // sample-exact end of the requested range
                if (!inDone) {
                    val ib = codec.dequeueInputBuffer(10000)
                    if (ib >= 0) {
                        val buf = codec.getInputBuffer(ib)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > toMs * 1000L) {
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
                        val b = codec.getOutputBuffer(ob)!!
                        b.position(info.offset)
                        b.limit(info.offset + info.size)
                        val sb = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val step = sr / 16000.0
                        val w = Math.max(1, Math.floor(step).toInt()) // box-filter width (anti-aliasing)
                        var i = 0
                        val total = sb.remaining()
                        while (i + ch <= total) {
                            if (srcFrame > lastFrame) break
                            if (skip > 0) { skip--; srcFrame++; i += ch; continue }
                            if (pos >= next) {
                                var s = 0
                                if (step > 1.5) {
                                    // average w frames so high frequencies fold instead of aliasing
                                    val ww = Math.min(w, (total - i) / ch)
                                    for (c in 0 until ch) {
                                        var acc = 0
                                        for (k in 0 until ww) acc += sb.get(i + k * ch + c)
                                        s += acc / ww
                                    }
                                } else {
                                    for (c in 0 until ch) s += sb.get(i + c)
                                }
                                if (outN >= outBuf.size) {
                                    if (outBuf.size >= MAX_PCM_SAMPLES) return null // range too long for one pass
                                    outBuf = outBuf.copyOf(Math.min(MAX_PCM_SAMPLES.toLong(), outBuf.size * 2L).toInt())
                                }
                                outBuf[outN++] = (s / ch).toShort()
                                next += step
                            }
                            pos += 1.0
                            srcFrame++
                            i += ch
                        }
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                } else if (ob == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    guard++
                    if (inDone && guard > 3000) break
                }
            }
            if (cancelFlag) return null
            if (outN == 0) return null
            return outBuf.copyOf(outN)
        } catch (e: Throwable) {
            println("RafieiEditor decode16k failed: " + e.javaClass.simpleName + ": " + e.message)
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { ex.release() } catch (e: Throwable) { }
        }
    }

    /**
     * Finds the parts that contain speech (energy based) so each piece is recognised on its own and the
     * subtitle starts exactly when the speech starts. Returns [from, to) sample ranges at 16 kHz.
     */
    fun segments(pcm: ShortArray): List<IntArray> {
        val fr = 320 // 20 ms
        val n = pcm.size / fr
        if (n < 5) return emptyList()
        val e = DoubleArray(n)
        for (i in 0 until n) e[i] = rms(pcm, i * fr, i * fr + fr)
        val sorted = e.sortedArray()
        val floor = sorted[(n * 0.2).toInt()]
        val peak = sorted[Math.min(n - 1, (n * 0.95).toInt())]
        val thr = Math.max(0.006, floor + (peak - floor) * 0.15)
        val segs = ArrayList<IntArray>()
        var i = 0
        while (i < n) {
            if (e[i] > thr) {
                var j = i
                var last = i
                while (j < n && j - last < 25) { if (e[j] > thr) last = j; j++ } // silence of 0.5 s ends a piece
                segs.add(intArrayOf(i, last + 1))
                i = last + 1
            } else i++
        }
        val out = ArrayList<IntArray>()
        for (s in segs) {
            var a = s[0]
            val b = s[1]
            if (b - a < 12) continue // shorter than 0.24 s: a click, not speech
            while (b - a > 500) { // longer than 10 s: cut at the quietest frame between 7 and 10 s
                var best = a + 350
                for (k in a + 350..a + 500) if (e[k] < e[best]) best = k
                out.add(intArrayOf(Math.max(0, a - 6) * fr, Math.min(n, best + 6) * fr))
                a = best
            }
            out.add(intArrayOf(Math.max(0, a - 6) * fr, Math.min(n, b + 6) * fr))
        }
        return out
    }

    fun rms(a: ShortArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return Math.sqrt(s / Math.max(1, to - from)) / 32768.0
    }

    /** Last error code reported by a recogniser (for the final message). */
    @Volatile var lastError = 0
    /** Online (server) recognition only with the user's explicit consent for this run. */
    @Volatile var allowOnline = false
    /** Play each piece through the loudspeaker and let the system recogniser listen with the microphone. */
    @Volatile var useMic = false
    @Volatile var cancelFlag = false

    private fun recognizeMic(ctx: android.content.Context, pcm: ShortArray, from: Int, to: Int, lang: String, cb: (String?) -> Unit) {
        val rec: SpeechRecognizer? = try { if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null } catch (e: Throwable) { null }
        if (rec == null) { lastError = 5; cb(null); return }
        var finished = false
        var track: android.media.AudioTrack? = null
        fun finish(t: String?, err: Int) {
            if (finished) return
            finished = true
            try { track?.stop(); track?.release() } catch (e: Throwable) { }
            try { rec.destroy() } catch (e: Throwable) { }
            if (err != 0) lastError = err
            cb(t)
        }
        try {
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { finish(null, if (error == 0) -1 else error) }
                override fun onResults(results: Bundle?) { finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(), 0) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            rec.startListening(i)
            val n = to - from
            Bg.postDelayed(500L) {
                try {
                    val t = android.media.AudioTrack.Builder()
                        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(16000).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(n * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
                    t.write(pcm, from, n)
                    track = t
                    t.play()
                } catch (e: Throwable) { }
            }
            Bg.postDelayed(500L + n / 16L + 600L) { if (!finished) try { rec.stopListening() } catch (e: Throwable) { } }
            Bg.postDelayed(500L + n / 16L + 5000L) { finish(null, 7) }
        } catch (e: Throwable) {
            finish(null, 5)
        }
    }

    /**
     * Recognises one chunk; the callback receives the text or null. Must be called on the main thread.
     * Tries the on-device recogniser first and falls back to the system (Google) recogniser when it is missing,
     * lacks the language pack, or fails.
     */
    fun recognize(ctx: android.content.Context, pcm: ShortArray, from: Int, to: Int, lang: String, cb: (String?) -> Unit) {
        if (useMic) { recognizeMic(ctx, pcm, from, to, lang, cb); return }
        if (Build.VERSION.SDK_INT < 33) { cb(null); return }
        val modes = ArrayList<Boolean>()
        try { if (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) modes.add(true) } catch (e: Throwable) { }
        if (allowOnline) modes.add(false)
        if (modes.isEmpty()) { lastError = 5; cb(null); return }
        fun attempt(i: Int) {
            if (i >= modes.size) { cb(null); return }
            one(ctx, pcm, from, to, lang, modes[i]) { t, err ->
                if (!t.isNullOrBlank()) cb(t)
                else {
                    if (err != 0) lastError = err
                    // 6 = speech timeout, 7 = no match: genuinely nothing recognised; others -> try the next recogniser
                    if (err == 0 || err == 6 || err == 7) { if (i + 1 < modes.size && err == 7) attempt(i + 1) else cb(null) }
                    else attempt(i + 1)
                }
            }
        }
        attempt(0)
    }

    private fun one(ctx: android.content.Context, pcm: ShortArray, from: Int, to: Int, lang: String, onDevice: Boolean, cb: (String?, Int) -> Unit) {
        val rec: SpeechRecognizer? = try {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            else if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null
        } catch (e: Throwable) { null }
        if (rec == null) { cb(null, 5); return }
        var finished = false
        fun finish(t: String?, err: Int) {
            if (finished) return
            finished = true
            try { rec.destroy() } catch (e: Throwable) { }
            cb(t, err)
        }
        try {
            val pipe = ParcelFileDescriptor.createPipe()
            val writer = Thread {
                try {
                    FileOutputStream(pipe[1].fileDescriptor).use { os ->
                        val bb = java.nio.ByteBuffer.allocate((to - from) * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in from until to) bb.putShort(pcm[i])
                        os.write(bb.array())
                    }
                } catch (e: Throwable) { }
                try { pipe[1].close() } catch (e: Throwable) { }
            }
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { finish(null, if (error == 0) -1 else error) }
                override fun onResults(results: Bundle?) {
                    val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    finish(l?.firstOrNull(), 0)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            rec.startListening(i)
            writer.start()
            Bg.postDelayed(20000L) { finish(null, 3) }
        } catch (e: Throwable) {
            finish(null, 5)
        }
    }

    /** Callback state: 0 = installed (or unknown), 1 = supported but pack not installed, 2 = not supported offline. */
    fun checkOffline(ctx: android.content.Context, lang: String, cb: (Int, List<String>) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { cb(0, emptyList()); return }
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) { cb(2, emptyList()); return }
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            var done = false
            fun fin(st: Int, l: List<String>) { if (done) return; done = true; try { rec.destroy() } catch (e: Throwable) { }; Bg.ui { cb(st, l) } }
            rec.checkRecognitionSupport(i, java.util.concurrent.Executors.newSingleThreadExecutor(), object : android.speech.RecognitionSupportCallback {
                override fun onSupportResult(s: android.speech.RecognitionSupport) {
                    val inst = s.installedOnDeviceLanguages
                    val sup = s.supportedOnDeviceLanguages
                    val pend = s.pendingOnDeviceLanguages
                    val pre = lang.substringBefore('-').lowercase()
                    val has = { l: List<String> -> l.any { it.lowercase().startsWith(pre) } }
                    when {
                        has(inst) -> fin(0, inst)
                        has(sup) || has(pend) -> fin(1, inst)
                        else -> fin(2, inst)
                    }
                }
                override fun onError(error: Int) { fin(0, emptyList()) }
            })
            Bg.postDelayed(6000L) { fin(0, emptyList()) }
        } catch (e: Throwable) {
            cb(0, emptyList())
        }
    }

    fun downloadPack(ctx: android.content.Context, lang: String) {
        try {
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            rec.triggerModelDownload(i)
        } catch (e: Throwable) {
        }
    }

    fun errName(code: Int): String = when (code) {
        1 -> "ERROR_NETWORK_TIMEOUT"; 2 -> "ERROR_NETWORK"; 3 -> "ERROR_AUDIO"; 4 -> "ERROR_SERVER"; 5 -> "ERROR_CLIENT / recogniser missing"
        6 -> "ERROR_SPEECH_TIMEOUT"; 7 -> "ERROR_NO_MATCH"; 8 -> "ERROR_RECOGNIZER_BUSY"; 9 -> "ERROR_INSUFFICIENT_PERMISSIONS"
        10 -> "ERROR_TOO_MANY_REQUESTS"; 11 -> "ERROR_SERVER_DISCONNECTED"; 12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"; 13 -> "ERROR_LANGUAGE_UNAVAILABLE"
        else -> "code $code"
    }
}

