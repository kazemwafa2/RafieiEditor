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
    /** Decodes the audio of [path] between [fromMs] and [toMs] to 16 kHz mono PCM. */
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
            ex.selectTrack(ti)
            ex.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val out = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var pos = 0.0 // position in source samples, used for resampling
            var next = 0.0
            var guard = 0
            while (!outDone) {
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
                        var i = 0
                        val total = sb.remaining()
                        while (i + ch <= total) {
                            if (pos >= next) {
                                var s = 0
                                for (c in 0 until ch) s += sb.get(i + c)
                                out.add((s / ch).toShort())
                                next += step
                            }
                            pos += 1.0
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
            val arr = ShortArray(out.size)
            for (i in arr.indices) arr[i] = out[i]
            return arr
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            try { ex.release() } catch (e: Throwable) { }
        }
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

/** Makes subtitles from the speech in the selected (or first) video clip. */
fun EditorActivity.openAutoSubPanel() {
    if (Build.VERSION.SDK_INT < 33) {
        Dialogs.message(this, L("زیرنویس خودکار", "Auto subtitles"), L("این قابلیت به اندروید ۱۳ یا بالاتر نیاز دارد. می‌توانید از «زیرنویس» یا «فایل SRT» استفاده کنید.", "This needs Android 13 or newer. Use the Subtitle or SRT tools instead."))
        return
    }
    val c = selClip() ?: proj.clips.firstOrNull { !it.isImage && it.hasAudio }
    if (c == null || c.isImage || !c.hasAudio) { toast(L("کلیپ ویدیویی دارای صدا لازم است.", "A video clip with audio is needed.")); return }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), EditorActivity.REQ_MIC)
        toast(L("اجازه‌ی میکروفون را بدهید و دوباره امتحان کنید.", "Allow the microphone, then try again."))
        return
    }
    Dialogs.choose(this, L("روش تشخیص گفتار", "Recognition mode"), listOf(
        L("فقط آفلاین (روی همین گوشی، چیزی ارسال نمی‌شود)", "Offline only (nothing is sent)"),
        L("اجازه به سرویس آنلاین گوگل (ممکن است صدا به سرور ارسال شود)", "Allow Google online service (audio may be sent)"),
        L("پخش از بلندگو + شنیدن با میکروفون (آنلاین؛ هم‌زمان با پخش پیش می‌رود؛ جای ساکت و صدای گوشی زیاد)", "Loudspeaker + microphone (online, real time; quiet room, volume up)")
    )) { mi ->
    AutoSub.allowOnline = mi >= 1
    AutoSub.useMic = mi == 2
    AutoSub.cancelFlag = false
    val langs = listOf("auto" to "تشخیص خودکار زبان", "fa-IR" to "فارسی / دری", "en-US" to "English", "ps-AF" to "پشتو", "ar-SA" to "العربية", "tr-TR" to "Türkçe", "ur-PK" to "اردو", "hi-IN" to "हिन्दी", "ru-RU" to "Русский")
    Dialogs.choose(this, L("زبان گفتار", "Speech language"), langs.map { it.second }) { li ->
        var lang = langs[li].first
        val autoLang = lang == "auto"
        if (autoLang) lang = "fa-IR"
        AutoSub.lastError = 0
        fun proceed() {
        val idx = proj.clips.indexOf(c)
        val clipStart = proj.clipStart(idx)
        val path = MediaUtil.pathOf(c.uri)
        val ts = c.trimStart
        val te = c.trimEnd
        val speed = c.speed
        val dlg: AlertDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(L("در حال خواندن صدا…", "Reading audio…")).setCancelable(false)
            .setNegativeButton(L("توقف", "Stop")) { _, _ -> AutoSub.cancelFlag = true }.create().also { it.show() }
        Bg.run {
            val pcm = AutoSub.decode16k(path, ts, te)
            Bg.ui {
                if (pcm == null || pcm.size < 16000) {
                    dlg.dismiss()
                    toast(L("صدای این کلیپ خوانده نشد.", "Could not read this clip's audio."))
                } else {
                    val chunk = 16000 * 6
                    val total = (pcm.size + chunk - 1) / chunk
                    val lines = ArrayList<Triple<Long, Long, String>>()
                    var k = 0
                    lateinit var step: () -> Unit
                    step = {
                        if (k >= total || AutoSub.cancelFlag) {
                            dlg.dismiss()
                            if (lines.isEmpty()) {
                                if (AutoSub.lastError == 7) {
                                    Dialogs.confirm(this, L("زیرنویس خودکار", "Auto subtitles"),
                                        L("تشخیص گفتار تا آخر اجرا شد و درست کار کرد، ولی در هیچ‌یک از بخش‌های ویدیو گفتار قابل‌فهم پیدا نشد (ERROR_NO_MATCH). این ویدیو احتمالاً آواز، موسیقی یا همهمهٔ جمعیت است و تشخیص گفتار گوگل آن را متن نمی‌کند؛ خطای برنامه نیست.\n\nبرای آهنگ، متن را بچسبانید و با «زمان‌بندی با ضربه» زمان‌بندی کنید. پنل زیرنویس باز شود؟", "Recognition ran to the end but found no intelligible speech (likely singing/music/crowd). Open the manual subtitle tool?"), L("باز کردن زیرنویس", "Open subtitles")) { openSubtitlePanel() }
                                } else
                                Dialogs.message(this, L("زیرنویس خودکار", "Auto subtitles"), L("گفتاری تشخیص داده نشد.\nدلیل فنی: " + AutoSub.errName(AutoSub.lastError) + "\n• بسته‌ی زبان را نصب کنید: تنظیمات گوشی ← برنامه‌ها ← Google ← زبان‌های گفتار (دانلود فارسی/انگلیسی).\n• برنامه‌ی «Google» یا «Speech Services by Google» باید فعال و به‌روز باشد.\n• اینترنت را روشن کنید (گاهی تشخیص آنلاین لازم است).\n• ERROR_NO_MATCH یعنی سرویس صدا را گرفت ولی گفتاری قابل‌فهم پیدا نکرد. این ابزار برای گفتار واضح است؛ آواز، موسیقی، همهمهٔ جمعیت و کنسرت را تقریباً هرگز تشخیص نمی‌دهد (محدودیت سرویس گوگل، نه خطای برنامه).\n• صدای واضح‌تر و بدون موسیقی بهتر تشخیص داده می‌شود.\nدر غیر این صورت از «فایل SRT» استفاده کنید.", "No speech recognised.\nTechnical reason: " + AutoSub.errName(AutoSub.lastError) + "\nInstall the language pack (Google app > speech languages), keep Speech Services updated, turn on internet, or use the SRT tool."))
                            } else {
                                beginChange()
                                proj.overlays.removeAll { it.kind == "text" && it.glyph == "sub" }
                                for (l in lines) {
                                    val o = Overlay()
                                    o.kind = "text"; o.glyph = "sub"; o.text = l.third
                                    o.startMs = l.first; o.durMs = Math.max(500L, l.second - l.first)
                                    o.size = 0.05f; o.cx = 0.5f; o.cy = 0.86f; o.color = Color.WHITE
                                    o.bgColor = Color.BLACK; o.bgAlpha = 0.45f; o.bold = true
                                    o.outlineColor = Color.BLACK; o.outlineW = 0.3f
                                    proj.overlays.add(o)
                                }
                                changed()
                                toast(L("${lines.size} زیرنویس ساخته شد؛ متن و زمان را اصلاح کنید.", "${lines.size} subtitles created; fix text and timing."))
                                openSubtitlePanel()
                            }
                        } else {
                            val from = k * chunk
                            val to = Math.min(pcm.size, from + chunk)
                            dlg.setMessage(L("در حال تشخیص گفتار… ${k + 1} از $total", "Recognising speech… ${k + 1} of $total"))
                            val t0 = clipStart + ((from / 16f) / speed).toLong()
                            val t1 = clipStart + ((to / 16f) / speed).toLong()
                            k++
                            if (AutoSub.rms(pcm, from, to) < 0.004) step() else {
                                fun got(t: String?) {
                                    if (!t.isNullOrBlank()) lines.add(Triple(t0, t1, t.trim()))
                                    step()
                                }
                                if (autoLang && lines.isEmpty()) {
                                    // language not known yet: try each language on the first spoken piece, keep the one that works
                                    val cand = listOf("fa-IR", "en-US", "ps-AF", "ar-SA", "tr-TR", "ur-PK", "hi-IN", "ru-RU")
                                    fun tryLang(j: Int) {
                                        if (j >= cand.size) { got(null); return }
                                        dlg.setMessage(L("در حال شناسایی زبان… (${cand[j]})", "Detecting language… (${cand[j]})"))
                                        AutoSub.recognize(applicationContext, pcm, from, to, cand[j]) { t ->
                                            if (!t.isNullOrBlank()) { lang = cand[j]; got(t) } else tryLang(j + 1)
                                        }
                                    }
                                    tryLang(0)
                                } else {
                                    AutoSub.recognize(applicationContext, pcm, from, to, lang) { t -> got(t) }
                                }
                            }
                        }
                    }
                    step()
                }
            }
        }
        }
        if (!AutoSub.allowOnline && !autoLang) {
            AutoSub.checkOffline(this, lang) { st, installed ->
                when (st) {
                    1 -> Dialogs.confirm(this, L("بستهٔ زبان", "Language pack"),
                        L("بستهٔ آفلاین این زبان روی گوشی نصب نیست. دانلود آن از طریق سرویس گوگل انجام می‌شود (نیاز به اینترنت، فقط برای دانلود مدل زبان؛ صدای شما ارسال نمی‌شود). دانلود شود؟", "The offline pack is not installed. Download it via Google (internet only for the model; your audio is not sent)?"), L("دانلود", "Download")) {
                        AutoSub.downloadPack(this, lang)
                        Dialogs.message(this, L("بستهٔ زبان", "Language pack"), L("دانلود شروع شد. چند دقیقه بعد (با اینترنت روشن) دوباره امتحان کنید.", "Download started. Try again in a few minutes."))
                    }
                    2 -> Dialogs.message(this, L("زبان پشتیبانی نمی‌شود", "Language not supported"),
                        L("تشخیص آفلاین گوشی شما این زبان را ندارد. زبان‌های آفلاین موجود: ", "This phone's offline recogniser lacks this language. Available offline: ") + (if (installed.isEmpty()) "—" else installed.joinToString(", ")) +
                        L("\nگزینه‌ها: زبان دیگری را امتحان کنید، یا دوباره «اجازه به سرویس آنلاین» را انتخاب کنید، یا از «فایل SRT» / «زیرنویس» دستی استفاده کنید.", "\nTry another language, allow the online service, or use SRT / manual subtitles."))
                    else -> proceed()
                }
            }
        } else proceed()
    }
    }
}
