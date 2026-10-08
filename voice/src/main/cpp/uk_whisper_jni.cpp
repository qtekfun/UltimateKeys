// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// JNI glue between com.qtekfun.ultimatekeys.voice.NativeWhisper and whisper.cpp. No logic lives here
// beyond what must be native: model handles, restricted language detection and the inference call.
// Audio and text are never written to disk or to the log.

#include <android/log.h>
#include <jni.h>
#include <sched.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#define UK_TAG "UKWhisper"
#define JNI_FN(name) JNICALL Java_com_qtekfun_ultimatekeys_voice_NativeWhisper_##name

namespace {

struct Handle {
    whisper_context *context = nullptr;
    std::atomic<bool> abort{false};
};

bool abortRequested(void *userData) {
    return static_cast<Handle *>(userData)->abort.load(std::memory_order_relaxed);
}

// Only warnings and errors reach logcat; whisper's regular output is dropped.
void logCallback(ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, UK_TAG, text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_write(ANDROID_LOG_WARN, UK_TAG, text);
    }
}

std::string toString(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

// Picks the most probable of [candidates] for the audio; empty on failure.
std::string detectAmong(whisper_context *context, const float *samples, int count, int threads,
                        const std::vector<std::string> &candidates) {
    if (whisper_pcm_to_mel(context, samples, count, threads) != 0) return {};
    std::vector<float> probabilities(whisper_lang_max_id() + 1, 0.0f);
    if (whisper_lang_auto_detect(context, 0, threads, probabilities.data()) < 0) return {};
    std::string best;
    float bestProbability = -1.0f;
    for (const std::string &candidate : candidates) {
        const int id = whisper_lang_id(candidate.c_str());
        if (id >= 0 && probabilities[id] > bestProbability) {
            bestProbability = probabilities[id];
            best = candidate;
        }
    }
    return best;
}

// whisper emits UTF-8; NewStringUTF wants modified UTF-8, so build the string from bytes.
jstring utf8ToJava(JNIEnv *env, const std::string &text) {
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID construct = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()),
                            reinterpret_cast<const jbyte *>(text.data()));
    jstring charset = env->NewStringUTF("UTF-8");
    return static_cast<jstring>(env->NewObject(stringClass, construct, bytes, charset));
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNI_FN(load)(JNIEnv *env, jclass, jstring path) {
    whisper_log_set(logCallback, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    const std::string file = toString(env, path);
    whisper_context *context = whisper_init_from_file_with_params(file.c_str(), params);
    if (context == nullptr) return 0;
    auto *handle = new Handle();
    handle->context = context;
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNI_FN(free)(JNIEnv *, jclass, jlong pointer) {
    auto *handle = reinterpret_cast<Handle *>(pointer);
    if (handle == nullptr) return;
    whisper_free(handle->context);
    delete handle;
}

// Asks a running transcription to stop at the next opportunity. Safe from any thread.
JNIEXPORT void JNI_FN(abort)(JNIEnv *, jclass, jlong pointer) {
    auto *handle = reinterpret_cast<Handle *>(pointer);
    if (handle != nullptr) handle->abort.store(true);
}

// Returns {language, text}, or null when inference failed or was aborted. [languages] has one
// entry (forced) or several (detect among them).
JNIEXPORT jobjectArray JNI_FN(transcribe)(JNIEnv *env, jclass, jlong pointer, jfloatArray audio,
                                          jobjectArray languages, jint threads) {
    auto *handle = reinterpret_cast<Handle *>(pointer);
    if (handle == nullptr) return nullptr;
    handle->abort.store(false);

    std::vector<std::string> candidates;
    const jsize languageCount = env->GetArrayLength(languages);
    for (jsize i = 0; i < languageCount; i++) {
        auto *item = static_cast<jstring>(env->GetObjectArrayElement(languages, i));
        candidates.push_back(toString(env, item));
        env->DeleteLocalRef(item);
    }
    if (candidates.empty()) return nullptr;

    const jsize count = env->GetArrayLength(audio);
    std::vector<float> samples(static_cast<size_t>(count));
    env->GetFloatArrayRegion(audio, 0, count, samples.data());

    std::string language = candidates.front();
    if (candidates.size() > 1) {
        language = detectAmong(handle->context, samples.data(), count, threads, candidates);
        if (language.empty()) return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = language.c_str();
    params.translate = false;
    params.detect_language = false;
    params.no_context = true;
    params.suppress_nst = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.abort_callback = abortRequested;
    params.abort_callback_user_data = handle;

    if (whisper_full(handle->context, params, samples.data(), count) != 0) return nullptr;
    if (handle->abort.load()) return nullptr;

    std::string text;
    const int segments = whisper_full_n_segments(handle->context);
    for (int i = 0; i < segments; i++) text += whisper_full_get_segment_text(handle->context, i);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF(language.c_str()));
    env->SetObjectArrayElement(result, 1, utf8ToJava(env, text));
    return result;
}

// Restricts the calling thread (and the worker threads it creates afterwards) to [cpus].
// Best effort: returns false when the kernel refuses.
JNIEXPORT jboolean JNI_FN(pinCurrentThread)(JNIEnv *env, jclass, jintArray cpus) {
    cpu_set_t set;
    CPU_ZERO(&set);
    const jsize count = env->GetArrayLength(cpus);
    std::vector<jint> ids(static_cast<size_t>(count));
    env->GetIntArrayRegion(cpus, 0, count, ids.data());
    for (jint id : ids) {
        if (id >= 0 && id < CPU_SETSIZE) CPU_SET(id, &set);
    }
    return sched_setaffinity(0, sizeof(set), &set) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNI_FN(systemInfo)(JNIEnv *env, jclass) {
    return env->NewStringUTF(whisper_print_system_info());
}

}  // extern "C"
