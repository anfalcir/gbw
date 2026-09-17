#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <exception>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <rubberband/RubberBandStretcher.h>

namespace {

using RubberBand::RubberBandStretcher;

struct Session {
    std::unique_ptr<RubberBandStretcher> stretcher;
    int channels = 0;
    std::atomic_bool cancelled{false};
};

void throwException(JNIEnv *env, const char *className, const std::string &message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

Session *sessionFrom(jlong handle) {
    return reinterpret_cast<Session *>(static_cast<intptr_t>(handle));
}

bool validateSession(JNIEnv *env, Session *session) {
    if (session == nullptr || session->stretcher == nullptr) {
        throwException(env, "java/lang/IllegalStateException", "Rubber Band session is closed or invalid");
        return false;
    }
    return true;
}

bool validateBlock(JNIEnv *env, Session *session, jfloatArray input, jint frames) {
    if (!validateSession(env, session)) return false;
    if (frames < 0) {
        throwException(env, "java/lang/IllegalArgumentException", "frames must be non-negative");
        return false;
    }
    if (frames == 0) return true;
    if (input == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "input must not be null when frames > 0");
        return false;
    }
    const jsize length = env->GetArrayLength(input);
    const int64_t expected = static_cast<int64_t>(frames) * static_cast<int64_t>(session->channels);
    if (expected > static_cast<int64_t>(INT32_MAX) || length != expected) {
        throwException(env, "java/lang/IllegalArgumentException", "interleaved input length does not match frames * channels");
        return false;
    }
    return true;
}

std::vector<std::vector<float>> deinterleave(const jfloat *input, int channels, int frames) {
    std::vector<std::vector<float>> planar(static_cast<size_t>(channels));
    for (int channel = 0; channel < channels; ++channel) {
        planar[static_cast<size_t>(channel)].resize(static_cast<size_t>(frames));
    }
    for (int frame = 0; frame < frames; ++frame) {
        for (int channel = 0; channel < channels; ++channel) {
            planar[static_cast<size_t>(channel)][static_cast<size_t>(frame)] =
                input[static_cast<size_t>(frame) * static_cast<size_t>(channels) + static_cast<size_t>(channel)];
        }
    }
    return planar;
}

std::vector<const float *> constPointers(const std::vector<std::vector<float>> &planar) {
    std::vector<const float *> pointers;
    pointers.reserve(planar.size());
    for (const auto &channel : planar) pointers.push_back(channel.data());
    return pointers;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_gbw_android_audio_RubberBandNative_libraryIdentity(JNIEnv *env, jobject) {
    const std::string identity = std::string("Rubber Band ") + RUBBERBAND_VERSION +
        " (GBW pinned commit " + GBW_RUBBERBAND_COMMIT + ")";
    return env->NewStringUTF(identity.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gbw_android_audio_RubberBandNative_create(
    JNIEnv *env,
    jobject,
    jint sampleRate,
    jint channels,
    jdouble semitones,
    jboolean preserveFormants,
    jlong expectedFrames
) {
    if (sampleRate < 8000 || sampleRate > 192000) {
        throwException(env, "java/lang/IllegalArgumentException", "sampleRate must be between 8000 and 192000 Hz");
        return 0;
    }
    if (channels < 1 || channels > 8) {
        throwException(env, "java/lang/IllegalArgumentException", "channels must be between 1 and 8");
        return 0;
    }
    if (!std::isfinite(semitones) || semitones < -24.0 || semitones > 24.0) {
        throwException(env, "java/lang/IllegalArgumentException", "semitones must be finite and within +/-24");
        return 0;
    }

    try {
        auto session = std::make_unique<Session>();
        session->channels = channels;

        RubberBandStretcher::Options options =
            RubberBandStretcher::OptionProcessOffline |
            RubberBandStretcher::OptionEngineFiner |
            RubberBandStretcher::OptionChannelsTogether |
            RubberBandStretcher::OptionPitchHighQuality;
        if (preserveFormants == JNI_TRUE) {
            options |= RubberBandStretcher::OptionFormantPreserved;
        }

        const double pitchScale = std::pow(2.0, semitones / 12.0);
        session->stretcher = std::make_unique<RubberBandStretcher>(
            static_cast<size_t>(sampleRate),
            static_cast<size_t>(channels),
            options,
            1.0,
            pitchScale
        );
        session->stretcher->setMaxProcessSize(4096);
        if (expectedFrames > 0) {
            session->stretcher->setExpectedInputDuration(static_cast<size_t>(expectedFrames));
        }
        if (session->stretcher->getEngineVersion() != 3) {
            throw std::runtime_error("Rubber Band did not activate the R3 engine");
        }

        return static_cast<jlong>(reinterpret_cast<intptr_t>(session.release()));
    } catch (const std::exception &e) {
        throwException(env, "java/lang/IllegalStateException", e.what());
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gbw_android_audio_RubberBandNative_engineVersion(JNIEnv *env, jobject, jlong handle) {
    Session *session = sessionFrom(handle);
    if (!validateSession(env, session)) return 0;
    return session->stretcher->getEngineVersion();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_audio_RubberBandNative_study(
    JNIEnv *env,
    jobject,
    jlong handle,
    jfloatArray input,
    jint frames,
    jboolean finalBlock
) {
    Session *session = sessionFrom(handle);
    if (!validateBlock(env, session, input, frames)) return;
    if (session->cancelled.load(std::memory_order_relaxed)) {
        throwException(env, "java/util/concurrent/CancellationException", "Rubber Band session cancelled");
        return;
    }

    try {
        if (frames == 0) {
            session->stretcher->study(nullptr, 0, finalBlock == JNI_TRUE);
            return;
        }
        jfloat *raw = env->GetFloatArrayElements(input, nullptr);
        if (raw == nullptr) throw std::runtime_error("Unable to access input float array");
        auto planar = deinterleave(raw, session->channels, frames);
        env->ReleaseFloatArrayElements(input, raw, JNI_ABORT);
        auto pointers = constPointers(planar);
        session->stretcher->study(pointers.data(), static_cast<size_t>(frames), finalBlock == JNI_TRUE);
    } catch (const std::exception &e) {
        throwException(env, "java/lang/IllegalStateException", e.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_audio_RubberBandNative_process(
    JNIEnv *env,
    jobject,
    jlong handle,
    jfloatArray input,
    jint frames,
    jboolean finalBlock
) {
    Session *session = sessionFrom(handle);
    if (!validateBlock(env, session, input, frames)) return;
    if (session->cancelled.load(std::memory_order_relaxed)) {
        throwException(env, "java/util/concurrent/CancellationException", "Rubber Band session cancelled");
        return;
    }

    try {
        if (frames == 0) {
            session->stretcher->process(nullptr, 0, finalBlock == JNI_TRUE);
            return;
        }
        jfloat *raw = env->GetFloatArrayElements(input, nullptr);
        if (raw == nullptr) throw std::runtime_error("Unable to access input float array");
        auto planar = deinterleave(raw, session->channels, frames);
        env->ReleaseFloatArrayElements(input, raw, JNI_ABORT);
        auto pointers = constPointers(planar);
        session->stretcher->process(pointers.data(), static_cast<size_t>(frames), finalBlock == JNI_TRUE);
    } catch (const std::exception &e) {
        throwException(env, "java/lang/IllegalStateException", e.what());
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gbw_android_audio_RubberBandNative_available(JNIEnv *env, jobject, jlong handle) {
    Session *session = sessionFrom(handle);
    if (!validateSession(env, session)) return 0;
    return session->stretcher->available();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_gbw_android_audio_RubberBandNative_retrieve(
    JNIEnv *env,
    jobject,
    jlong handle,
    jint maxFrames
) {
    Session *session = sessionFrom(handle);
    if (!validateSession(env, session)) return nullptr;
    if (maxFrames <= 0) {
        throwException(env, "java/lang/IllegalArgumentException", "maxFrames must be positive");
        return nullptr;
    }
    if (session->cancelled.load(std::memory_order_relaxed)) {
        throwException(env, "java/util/concurrent/CancellationException", "Rubber Band session cancelled");
        return nullptr;
    }

    try {
        const int available = session->stretcher->available();
        if (available <= 0) return env->NewFloatArray(0);
        const int frames = std::min(available, maxFrames);

        std::vector<std::vector<float>> planar(static_cast<size_t>(session->channels));
        std::vector<float *> pointers;
        pointers.reserve(static_cast<size_t>(session->channels));
        for (int channel = 0; channel < session->channels; ++channel) {
            auto &samples = planar[static_cast<size_t>(channel)];
            samples.resize(static_cast<size_t>(frames));
            pointers.push_back(samples.data());
        }

        const size_t retrieved = session->stretcher->retrieve(pointers.data(), static_cast<size_t>(frames));
        const size_t sampleCount = retrieved * static_cast<size_t>(session->channels);
        std::vector<float> interleaved(sampleCount);
        for (size_t frame = 0; frame < retrieved; ++frame) {
            for (int channel = 0; channel < session->channels; ++channel) {
                interleaved[frame * static_cast<size_t>(session->channels) + static_cast<size_t>(channel)] =
                    planar[static_cast<size_t>(channel)][frame];
            }
        }

        jfloatArray result = env->NewFloatArray(static_cast<jsize>(sampleCount));
        if (result == nullptr) throw std::runtime_error("Unable to allocate output float array");
        if (!interleaved.empty()) {
            env->SetFloatArrayRegion(result, 0, static_cast<jsize>(interleaved.size()), interleaved.data());
        }
        return result;
    } catch (const std::exception &e) {
        throwException(env, "java/lang/IllegalStateException", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_audio_RubberBandNative_cancel(JNIEnv *, jobject, jlong handle) {
    Session *session = sessionFrom(handle);
    if (session != nullptr) session->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gbw_android_audio_RubberBandNative_isCancelled(JNIEnv *, jobject, jlong handle) {
    Session *session = sessionFrom(handle);
    return session != nullptr && session->cancelled.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_audio_RubberBandNative_destroy(JNIEnv *, jobject, jlong handle) {
    delete sessionFrom(handle);
}
