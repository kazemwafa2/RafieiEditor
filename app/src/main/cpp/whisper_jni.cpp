// JNI bridge between RafieiEditor (Kotlin) and whisper.cpp. Strings are returned as raw bytes because a
// multi-byte UTF-8 character (every Persian letter) can be split across two tokens.
#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstring>
#include <thread>
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "RafieiWhisper", __VA_ARGS__)
#else
#define LOGI(...) ((void) 0)
#endif

static std::atomic<bool> g_abort(false);
static std::atomic<int> g_progress(0);

static bool abort_cb(void *) { return g_abort.load(); }
static void progress_cb(struct whisper_context *, struct whisper_state *, int p, void *) { g_progress.store(p); }

static jbyteArray bytes(JNIEnv *env, const char *s) {
    if (!s) s = "";
    jsize n = (jsize) strlen(s);
    jbyteArray a = env->NewByteArray(n);
    if (a && n > 0) env->SetByteArrayRegion(a, 0, n, (const jbyte *) s);
    return a;
}

extern "C" {

JNIEXPORT jlong JNICALL Java_com_rafiei_editor_WhisperLib_initModel(JNIEnv *env, jclass, jstring path, jint dtwPreset) {
    const char *p = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
    if (!p) return 0; // OOM: pending exception travels back to Kotlin
    struct whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    if (dtwPreset > 0) {
        cp.dtw_token_timestamps = true;
        cp.dtw_aheads_preset = (enum whisper_alignment_heads_preset) dtwPreset;
        cp.flash_attn = false;
    }
    struct whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL Java_com_rafiei_editor_WhisperLib_freeModel(JNIEnv *, jclass, jlong ctx) {
    if (ctx) whisper_free((struct whisper_context *) ctx);
}

JNIEXPORT void JNICALL Java_com_rafiei_editor_WhisperLib_abort(JNIEnv *, jclass, jboolean on) { g_abort.store(on); }
JNIEXPORT jint JNICALL Java_com_rafiei_editor_WhisperLib_progress(JNIEnv *, jclass) { return g_progress.load(); }

JNIEXPORT jint JNICALL Java_com_rafiei_editor_WhisperLib_transcribe(JNIEnv *env, jclass, jlong ctxp, jfloatArray pcm,
        jstring lang, jint threads, jboolean dtw, jstring prompt, jstring vadModel) {
    struct whisper_context *ctx = (struct whisper_context *) ctxp;
    if (!ctx) return -1;
    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples((size_t) n);
    if (n > 0) env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    const char *lg = lang ? env->GetStringUTFChars(lang, nullptr) : nullptr;
    const char *pr = prompt ? env->GetStringUTFChars(prompt, nullptr) : nullptr;
    const char *vm = vadModel ? env->GetStringUTFChars(vadModel, nullptr) : nullptr;
    std::string langS = lg ? lg : "", promptS = pr ? pr : "", vadS = vm ? vm : "";
    if (lg) env->ReleaseStringUTFChars(lang, lg);
    if (pr) env->ReleaseStringUTFChars(prompt, pr);
    if (vm) env->ReleaseStringUTFChars(vadModel, vm);

    struct whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads > 0 ? threads : 4;
    wp.language = (langS.empty() || langS == "auto") ? "auto" : langS.c_str();
    wp.detect_language = false;
    wp.translate = false;
    wp.print_progress = false; wp.print_realtime = false; wp.print_timestamps = false; wp.print_special = false;
    wp.token_timestamps = true;      // t0 / t1 per token
    wp.thold_pt = 0.01f; wp.thold_ptsum = 0.01f;
    wp.no_context = true;            // each chunk is independent: avoids repetition loops carried over
    wp.suppress_blank = true;
    wp.suppress_nst = true;
    wp.no_speech_thold = 0.6f;
    wp.entropy_thold = 2.4f;
    wp.logprob_thold = -1.0f;
    wp.greedy.best_of = 2;
    if (!promptS.empty()) wp.initial_prompt = promptS.c_str();
    if (!vadS.empty()) { wp.vad = true; wp.vad_model_path = vadS.c_str(); }
    wp.progress_callback = progress_cb;
    wp.abort_callback = abort_cb;
    g_abort.store(false);
    g_progress.store(0);
    (void) dtw;
    int rc = whisper_full(ctx, wp, samples.data(), (int) samples.size());
    if (rc != 0) return -2;
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jstring JNICALL Java_com_rafiei_editor_WhisperLib_lang(JNIEnv *env, jclass, jlong ctx) {
    int id = whisper_full_lang_id((struct whisper_context *) ctx);
    const char *s = whisper_lang_str(id);
    return env->NewStringUTF(s ? s : "");
}

JNIEXPORT jlong JNICALL Java_com_rafiei_editor_WhisperLib_segT0(JNIEnv *, jclass, jlong ctx, jint i) { return whisper_full_get_segment_t0((struct whisper_context *) ctx, i); }
JNIEXPORT jlong JNICALL Java_com_rafiei_editor_WhisperLib_segT1(JNIEnv *, jclass, jlong ctx, jint i) { return whisper_full_get_segment_t1((struct whisper_context *) ctx, i); }
JNIEXPORT jfloat JNICALL Java_com_rafiei_editor_WhisperLib_segNoSpeech(JNIEnv *, jclass, jlong ctx, jint i) { return whisper_full_get_segment_no_speech_prob((struct whisper_context *) ctx, i); }
JNIEXPORT jint JNICALL Java_com_rafiei_editor_WhisperLib_tokCount(JNIEnv *, jclass, jlong ctx, jint i) { return whisper_full_n_tokens((struct whisper_context *) ctx, i); }
JNIEXPORT jint JNICALL Java_com_rafiei_editor_WhisperLib_eot(JNIEnv *, jclass, jlong ctx) { return whisper_token_eot((struct whisper_context *) ctx); }

JNIEXPORT jbyteArray JNICALL Java_com_rafiei_editor_WhisperLib_tokBytes(JNIEnv *env, jclass, jlong ctx, jint i, jint j) {
    return bytes(env, whisper_full_get_token_text((struct whisper_context *) ctx, i, j));
}

// returns [id, t0, t1, tDtw, p*1000] as longs
JNIEXPORT jlongArray JNICALL Java_com_rafiei_editor_WhisperLib_tokData(JNIEnv *env, jclass, jlong ctx, jint i, jint j) {
    whisper_token_data d = whisper_full_get_token_data((struct whisper_context *) ctx, i, j);
    jlong v[5] = { (jlong) d.id, (jlong) d.t0, (jlong) d.t1, (jlong) d.t_dtw, (jlong) (d.p * 1000.0f) };
    jlongArray a = env->NewLongArray(5);
    if (a) env->SetLongArrayRegion(a, 0, 5, v);
    return a;
}

JNIEXPORT jstring JNICALL Java_com_rafiei_editor_WhisperLib_sysInfo(JNIEnv *env, jclass) {
    return env->NewStringUTF(whisper_print_system_info());
}

JNIEXPORT jint JNICALL Java_com_rafiei_editor_WhisperLib_cores(JNIEnv *, jclass) {
    return (jint) std::thread::hardware_concurrency();
}

}
