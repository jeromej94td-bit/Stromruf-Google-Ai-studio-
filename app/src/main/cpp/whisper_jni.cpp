#include <jni.h>
#include <whisper.h>
#include <string>
#include <vector>
#include <algorithm>

struct AbortState { JNIEnv *env; jobject owner; jmethodID method; };
static bool aborted(void *ptr) {
    auto *s = static_cast<AbortState *>(ptr);
    const bool result = s->env->CallBooleanMethod(s->owner, s->method);
    return result || s->env->ExceptionCheck();
}

// Standard UTF-8 conversion, including characters outside modified JNI UTF-8.
static jstring javaString(JNIEnv *env, const std::string &text) {
    auto bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()),
                           reinterpret_cast<const jbyte *>(text.data()));
    auto cls = env->FindClass("java/lang/String");
    auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(cls, constructor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(cls);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_transcription_offline_WhisperNative_open(JNIEnv *env, jobject, jstring path) {
    auto chars = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto context = whisper_init_from_file_with_params(chars, params);
    env->ReleaseStringUTFChars(path, chars);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_transcription_offline_WhisperNative_transcribe(
    JNIEnv *env, jobject owner, jlong handle, jfloatArray input, jint skipBeforeMs) {
    auto context = reinterpret_cast<whisper_context *>(handle);
    if (!context) return nullptr;
    const int count = env->GetArrayLength(input);
    std::vector<float> samples(count);
    env->GetFloatArrayRegion(input, 0, count, samples.data());
    auto cls = env->GetObjectClass(owner);
    AbortState abort{env, owner, env->GetMethodID(cls, "shouldAbort", "()Z")};
    env->DeleteLocalRef(cls);
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language = "de";
    p.translate = false;
    p.detect_language = false;
    p.n_threads = 2;
    p.no_context = true;
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.suppress_blank = true;
    p.suppress_nst = true;
    p.temperature_inc = 0.0f;
    p.abort_callback = aborted;
    p.abort_callback_user_data = &abort;
    if (aborted(&abort) || whisper_full(context, p, samples.data(), count) != 0 || aborted(&abort))
        return nullptr;
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(context); ++i) {
        // Adjacent windows share context; assign each segment to one window.
        const int64_t midpointMs = (whisper_full_get_segment_t0(context, i) +
                                   whisper_full_get_segment_t1(context, i)) * 5;
        if (midpointMs < skipBeforeMs) continue;
        std::string segment = whisper_full_get_segment_text(context, i);
        text += segment;
    }
    return javaString(env, text);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_transcription_offline_WhisperNative_close(JNIEnv *, jobject, jlong handle) {
    if (handle) whisper_free(reinterpret_cast<whisper_context *>(handle));
}
