#include <jni.h>

#include <atomic>
#include <cmath>
#include <cstdint>
#include <exception>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>

#include "model.hpp"
#include "tensor.hpp"

namespace {
constexpr int kRequiredChannels = 2;
constexpr int kSourceCount = 6;
constexpr int kModelWindowFrames = 343980;
std::atomic_bool g_cancelled{false};
std::atomic_int g_blas_threads{1};

extern "C" {
void openblas_set_num_threads(int num_threads);
int openblas_get_num_threads(void);
}

struct Cancelled final : std::exception {
    const char *what() const noexcept override { return "Demucs inference cancelled"; }
};

struct JavaCallbackFailure final : std::exception {
    const char *what() const noexcept override { return "Java progress callback failed"; }
};

/*
 * GBW already streams one fixed 7.8 s analysis window with an outer 5.5 s core
 * and 1.15 s context on both sides. Calling demucs_inference() here would add
 * another shift/split/overlap layer to that already segmented window. For the
 * Android Demucs engine we instead reuse the exported model_inference() kernel
 * directly, keeping one set of expensive scratch buffers alive for the model
 * lifetime. The six-source checkpoint and model window remain unchanged.
 */
struct GbwDemucsContext {
    demucscpp::demucs_model model{};
    demucscpp::demucs_segment_buffers buffers{
        kRequiredChannels,
        kModelWindowFrames,
        kSourceCount,
    };
    demucscpp::stft_buffers stft{buffers.padded_segment_samples};
    std::mutex inferenceMutex;
};

bool supportedBlasThreads(int threads) {
    return threads == 1 || threads == 2;
}

GbwDemucsContext *contextFromHandle(jlong handle) {
    if (handle == 0) throw std::invalid_argument("Demucs model handle is null");
    return reinterpret_cast<GbwDemucsContext *>(static_cast<intptr_t>(handle));
}

void throwJava(JNIEnv *env, const char *className, const std::string &message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
        env->DeleteLocalRef(clazz);
    }
}

std::string fromJString(JNIEnv *env, jstring value) {
    if (value == nullptr) throw std::invalid_argument("String argument is null");
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) throw std::runtime_error("Unable to read Java string");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jfloatArray zeroOutput(JNIEnv *env, jint frames) {
    const int64_t outputLength64 =
        static_cast<int64_t>(kSourceCount) * frames * kRequiredChannels;
    if (outputLength64 > INT32_MAX) {
        throw std::runtime_error("Demucs output chunk is too large for JNI");
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(outputLength64));
    if (result == nullptr) {
        throw std::runtime_error("Unable to allocate Demucs JNI output");
    }
    return result;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeIdentity(JNIEnv *env, jobject) {
    const std::string identity =
        std::string("demucs.cpp@") + GBW_DEMUCS_CPP_COMMIT +
        ";eigen@" + GBW_EIGEN_COMMIT +
        ";openblas@" + GBW_OPENBLAS_COMMIT +
        ";model=htdemucs_6s;sample_rate=44100;channels=2;stems=6"
        ";engine=direct-segment-v1;window_frames=343980;parallel=openblas"
        ";blas_threads=" + std::to_string(openblas_get_num_threads());
    return env->NewStringUTF(identity.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeConfigureBlasThreads(
    JNIEnv *env, jobject, jint threads) {
    try {
        if (!supportedBlasThreads(threads)) {
            throw std::invalid_argument("Demucs BLAS threads must be 1 or 2");
        }
        openblas_set_num_threads(threads);
        const int actual = openblas_get_num_threads();
        if (actual != threads) {
            throw std::runtime_error("OpenBLAS did not honor the requested thread count");
        }
        g_blas_threads.store(actual, std::memory_order_release);
        return actual;
    } catch (const std::invalid_argument &error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
        return 0;
    } catch (const std::exception &error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeCreateModel(
    JNIEnv *env, jobject, jstring modelPath) {
    try {
        const std::string path = fromJString(env, modelPath);
        openblas_set_num_threads(g_blas_threads.load(std::memory_order_acquire));
        auto context = std::make_unique<GbwDemucsContext>();
        if (!demucscpp::load_demucs_model(path, &context->model)) {
            throw std::runtime_error("Demucs model could not be loaded");
        }
        if (context->model.is_4sources) {
            throw std::runtime_error(
                "Expected htdemucs_6s, but a four-source model was loaded");
        }
        g_cancelled.store(false, std::memory_order_release);
        return static_cast<jlong>(
            reinterpret_cast<intptr_t>(context.release()));
    } catch (const std::exception &error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeDestroyModel(
    JNIEnv *, jobject, jlong handle) {
    if (handle == 0) return;
    delete reinterpret_cast<GbwDemucsContext *>(
        static_cast<intptr_t>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeCancel(JNIEnv *, jobject) {
    g_cancelled.store(true, std::memory_order_release);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_gbw_android_separation_DemucsNative_nativeSeparateChunk(
    JNIEnv *env,
    jobject,
    jlong handle,
    jfloatArray interleavedStereo,
    jint frames,
    jobject progressListener) {
    try {
        if (frames != kModelWindowFrames) {
            throw std::invalid_argument(
                "GBW optimized Demucs requires one exact 7.8 s model window");
        }
        if (interleavedStereo == nullptr) {
            throw std::invalid_argument("Demucs PCM input is null");
        }
        const jsize inputLength = env->GetArrayLength(interleavedStereo);
        const int64_t expectedLength =
            static_cast<int64_t>(frames) * kRequiredChannels;
        if (expectedLength > INT32_MAX || inputLength != expectedLength) {
            throw std::invalid_argument(
                "Demucs PCM input must be interleaved stereo");
        }
        if (g_cancelled.load(std::memory_order_acquire)) throw Cancelled();

        auto *context = contextFromHandle(handle);
        std::lock_guard<std::mutex> inferenceLock(context->inferenceMutex);
        if (context->model.is_4sources) {
            throw std::runtime_error("Loaded Demucs model is not six-source");
        }

        /*
         * Eigen::MatrixXf(2,N) is column-major and therefore laid out as
         * [L0,R0,L1,R1,...], exactly matching GBW's interleaved stereo input.
         */
        env->GetFloatArrayRegion(
            interleavedStereo,
            0,
            inputLength,
            context->buffers.mix.data());
        if (env->ExceptionCheck()) return nullptr;

        /*
         * Mirror demucs.cpp demucs_inference() normalization. Silence is handled
         * explicitly: returning six zero stems is mathematically correct and
         * avoids NaN/Inf from division by a zero standard deviation.
         */
        Eigen::VectorXf refMeanPerFrame =
            context->buffers.mix.colwise().mean();
        const float refMean = refMeanPerFrame.mean();
        const float refStd = std::sqrt(
            (refMeanPerFrame.array() - refMean).square().sum() /
            static_cast<float>(refMeanPerFrame.size() - 1));

        if (!std::isfinite(refMean) || !std::isfinite(refStd)) {
            throw std::runtime_error(
                "Demucs input window has invalid normalization statistics");
        }
        if (refStd <= 1.0e-12f) {
            return zeroOutput(env, frames);
        }

        context->buffers.mix =
            ((context->buffers.mix.array() - refMean) / refStd).matrix();

        jclass listenerClass = nullptr;
        jmethodID progressMethod = nullptr;
        if (progressListener != nullptr) {
            listenerClass = env->GetObjectClass(progressListener);
            if (listenerClass == nullptr) throw JavaCallbackFailure();
            progressMethod = env->GetMethodID(
                listenerClass,
                "onProgress",
                "(FLjava/lang/String;)V");
            if (progressMethod == nullptr) {
                env->DeleteLocalRef(listenerClass);
                throw JavaCallbackFailure();
            }
        }

        demucscpp::ProgressCallback callback =
            [env, progressListener, progressMethod](
                float progress, const std::string &message) {
                if (g_cancelled.load(std::memory_order_acquire)) {
                    throw Cancelled();
                }
                if (progressListener == nullptr || progressMethod == nullptr) {
                    return;
                }
                jstring jMessage = env->NewStringUTF(message.c_str());
                if (jMessage == nullptr) throw JavaCallbackFailure();
                env->CallVoidMethod(
                    progressListener,
                    progressMethod,
                    progress,
                    jMessage);
                env->DeleteLocalRef(jMessage);
                if (env->ExceptionCheck()) throw JavaCallbackFailure();
            };

        try {
            demucscpp::model_inference(
                context->model,
                context->buffers,
                context->stft,
                callback,
                0.0f,
                1.0f);
        } catch (...) {
            if (listenerClass != nullptr) env->DeleteLocalRef(listenerClass);
            throw;
        }
        if (listenerClass != nullptr) env->DeleteLocalRef(listenerClass);

        if (g_cancelled.load(std::memory_order_acquire)) throw Cancelled();

        const int64_t outputLength64 =
            static_cast<int64_t>(kSourceCount) * frames * kRequiredChannels;
        if (outputLength64 > INT32_MAX) {
            throw std::runtime_error("Demucs output chunk is too large for JNI");
        }
        const jsize outputLength = static_cast<jsize>(outputLength64);
        jfloatArray result = env->NewFloatArray(outputLength);
        if (result == nullptr) {
            throw std::runtime_error("Unable to allocate Demucs JNI output");
        }
        jfloat *resultData = env->GetFloatArrayElements(result, nullptr);
        if (resultData == nullptr) {
            env->DeleteLocalRef(result);
            throw std::runtime_error("Unable to map Demucs JNI output");
        }

        bool valid = true;
        for (int source = 0; source < kSourceCount && valid; ++source) {
            for (int frame = 0; frame < frames; ++frame) {
                const float left =
                    context->buffers.targets_out(source, 0, frame) *
                        refStd +
                    refMean;
                const float right =
                    context->buffers.targets_out(source, 1, frame) *
                        refStd +
                    refMean;
                if (!std::isfinite(left) || !std::isfinite(right)) {
                    valid = false;
                    break;
                }
                const size_t base =
                    (static_cast<size_t>(source) *
                         static_cast<size_t>(frames) +
                     static_cast<size_t>(frame)) *
                    2;
                resultData[base] = left;
                resultData[base + 1] = right;
            }
        }

        env->ReleaseFloatArrayElements(result, resultData, 0);
        if (!valid) {
            env->DeleteLocalRef(result);
            throw std::runtime_error("Demucs produced non-finite PCM");
        }
        return result;
    } catch (const Cancelled &) {
        throwJava(
            env,
            "java/util/concurrent/CancellationException",
            "Demucs inference cancelled");
        return nullptr;
    } catch (const JavaCallbackFailure &) {
        if (!env->ExceptionCheck()) {
            throwJava(
                env,
                "java/lang/IllegalStateException",
                "Demucs progress callback failed");
        }
        return nullptr;
    } catch (const std::exception &error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}
