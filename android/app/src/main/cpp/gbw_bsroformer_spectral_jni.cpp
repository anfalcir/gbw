#include <jni.h>

#include <algorithm>
#include <cmath>
#include <complex>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

#include <pffft/pffft.h>

namespace {

constexpr int kChannels = 2;
constexpr int kStems = 6;
constexpr int kNfft = 2048;
constexpr int kHop = 512;
constexpr int kCenterPad = kNfft / 2;
constexpr int kBins = kNfft / 2 + 1;
constexpr int kChunkFrames = 588800;
constexpr int kTimeFrames = 1 + kChunkFrames / kHop;
constexpr int kPaddedFrames = kNfft + (kTimeFrames - 1) * kHop;
constexpr int kComplex = 2;
constexpr float kPi = 3.14159265358979323846f;

constexpr std::size_t kStftFloats =
    static_cast<std::size_t>(kChannels) * kBins * kTimeFrames * kComplex;
constexpr std::size_t kMaskFloats =
    static_cast<std::size_t>(kStems) * (kBins * kChannels) * kTimeFrames * kComplex;
constexpr std::size_t kOutputFloats =
    static_cast<std::size_t>(kStems) * kChunkFrames * kChannels;

static_assert(kTimeFrames == 1151, "BS-RoFormer production STFT shape changed");
static_assert(kPaddedFrames - 2 * kCenterPad == kChunkFrames, "ISTFT geometry changed");

void throwJava(JNIEnv* env, const char* className, const std::string& message) {
    jclass cls = env->FindClass(className);
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
        env->DeleteLocalRef(cls);
    }
}

class AlignedBuffer {
public:
    explicit AlignedBuffer(std::size_t count)
        : ptr_(static_cast<float*>(pffft_aligned_malloc(count * sizeof(float)))) {
        if (ptr_ == nullptr) {
            throw std::bad_alloc();
        }
        std::fill(ptr_, ptr_ + count, 0.0f);
    }

    ~AlignedBuffer() {
        pffft_aligned_free(ptr_);
    }

    AlignedBuffer(const AlignedBuffer&) = delete;
    AlignedBuffer& operator=(const AlignedBuffer&) = delete;

    float* data() { return ptr_; }
    float& operator[](std::size_t index) { return ptr_[index]; }
    const float& operator[](std::size_t index) const { return ptr_[index]; }

private:
    float* ptr_;
};

class SpectralKernel {
public:
    SpectralKernel()
        : setup_(pffft_new_setup(kNfft, PFFFT_REAL)),
          time_(kNfft),
          freq_(kNfft),
          work_(kNfft),
          window_(kNfft),
          denominator_(kPaddedFrames, 0.0f) {
        if (setup_ == nullptr) {
            throw std::runtime_error("pffft_new_setup failed");
        }

        for (int i = 0; i < kNfft; ++i) {
            window_[i] =
                0.5f * (1.0f - std::cos(2.0f * kPi * static_cast<float>(i) /
                                        static_cast<float>(kNfft)));
        }

        for (int frame = 0; frame < kTimeFrames; ++frame) {
            const int offset = frame * kHop;
            for (int i = 0; i < kNfft; ++i) {
                const float w = window_[i];
                denominator_[static_cast<std::size_t>(offset + i)] += w * w;
            }
        }
    }

    ~SpectralKernel() {
        if (setup_ != nullptr) {
            pffft_destroy_setup(setup_);
        }
    }

    void forwardStereo(const float* interleavedStereo, float* destination) {
        for (int channel = 0; channel < kChannels; ++channel) {
            for (int frame = 0; frame < kTimeFrames; ++frame) {
                const int paddedOffset = frame * kHop;
                for (int i = 0; i < kNfft; ++i) {
                    const int paddedIndex = paddedOffset + i;
                    const int sourceFrame = reflectCenteredIndex(paddedIndex);
                    time_[i] =
                        interleavedStereo[
                            static_cast<std::size_t>(sourceFrame) * kChannels + channel
                        ] * window_[i];
                }

                pffft_transform_ordered(
                    setup_,
                    time_.data(),
                    freq_.data(),
                    work_.data(),
                    PFFFT_FORWARD);

                writeComplex(destination, channel, 0, frame, freq_[0], 0.0f);
                for (int bin = 1; bin < kNfft / 2; ++bin) {
                    writeComplex(
                        destination,
                        channel,
                        bin,
                        frame,
                        freq_[2 * bin],
                        freq_[2 * bin + 1]);
                }
                writeComplex(
                    destination,
                    channel,
                    kNfft / 2,
                    frame,
                    freq_[1],
                    0.0f);
            }
        }
    }

    void masksToInterleavedAudio(
        const float* mixtureStft,
        const float* masks,
        float* destination) {
        std::vector<float> accum(kPaddedFrames, 0.0f);

        for (int stem = 0; stem < kStems; ++stem) {
            for (int channel = 0; channel < kChannels; ++channel) {
                std::fill(accum.begin(), accum.end(), 0.0f);

                for (int frame = 0; frame < kTimeFrames; ++frame) {
                    std::fill(freq_.data(), freq_.data() + kNfft, 0.0f);

                    for (int bin = 0; bin < kBins; ++bin) {
                        const auto mixture = readMixture(mixtureStft, channel, bin, frame);
                        const auto mask = readMask(masks, stem, channel, bin, frame);
                        const auto product = mixture * mask;

                        if (bin == 0) {
                            freq_[0] = product.real();
                        } else if (bin == kNfft / 2) {
                            freq_[1] = product.real();
                        } else {
                            freq_[2 * bin] = product.real();
                            freq_[2 * bin + 1] = product.imag();
                        }
                    }

                    pffft_transform_ordered(
                        setup_,
                        freq_.data(),
                        time_.data(),
                        work_.data(),
                        PFFFT_BACKWARD);

                    const int offset = frame * kHop;
                    for (int i = 0; i < kNfft; ++i) {
                        const float sample =
                            (time_[i] / static_cast<float>(kNfft)) * window_[i];
                        accum[static_cast<std::size_t>(offset + i)] += sample;
                    }
                }

                for (int frame = 0; frame < kChunkFrames; ++frame) {
                    const int paddedIndex = frame + kCenterPad;
                    const float denom = denominator_[static_cast<std::size_t>(paddedIndex)];
                    if (!(denom > 1.0e-11f)) {
                        throw std::runtime_error("ISTFT NOLA denominator is zero");
                    }
                    const float sample =
                        accum[static_cast<std::size_t>(paddedIndex)] / denom;
                    if (!std::isfinite(sample)) {
                        throw std::runtime_error("BS-RoFormer ISTFT produced non-finite audio");
                    }
                    destination[
                        (static_cast<std::size_t>(stem) * kChunkFrames + frame) *
                            kChannels +
                        channel
                    ] = sample;
                }
            }
        }
    }

private:
    static int reflectCenteredIndex(int paddedIndex) {
        if (paddedIndex < kCenterPad) {
            return kCenterPad - paddedIndex;
        }
        if (paddedIndex < kCenterPad + kChunkFrames) {
            return paddedIndex - kCenterPad;
        }
        const int rightOffset = paddedIndex - (kCenterPad + kChunkFrames);
        return kChunkFrames - 2 - rightOffset;
    }

    static std::size_t stftIndex(
        int channel,
        int bin,
        int frame,
        int component) {
        return (
            (
                (static_cast<std::size_t>(channel) * kBins + bin) *
                    kTimeFrames +
                frame
            ) *
                kComplex +
            component
        );
    }

    static std::size_t maskIndex(
        int stem,
        int channel,
        int bin,
        int frame,
        int component) {
        const int mergedFrequency = bin * kChannels + channel;
        return (
            (
                (
                    static_cast<std::size_t>(stem) * (kBins * kChannels) +
                    mergedFrequency
                ) *
                    kTimeFrames +
                frame
            ) *
                kComplex +
            component
        );
    }

    static void writeComplex(
        float* destination,
        int channel,
        int bin,
        int frame,
        float real,
        float imag) {
        const std::size_t index = stftIndex(channel, bin, frame, 0);
        destination[index] = real;
        destination[index + 1] = imag;
    }

    static std::complex<float> readMixture(
        const float* source,
        int channel,
        int bin,
        int frame) {
        const std::size_t index = stftIndex(channel, bin, frame, 0);
        return {source[index], source[index + 1]};
    }

    static std::complex<float> readMask(
        const float* source,
        int stem,
        int channel,
        int bin,
        int frame) {
        const std::size_t index = maskIndex(stem, channel, bin, frame, 0);
        return {source[index], source[index + 1]};
    }

    PFFFT_Setup* setup_;
    AlignedBuffer time_;
    AlignedBuffer freq_;
    AlignedBuffer work_;
    std::vector<float> window_;
    std::vector<float> denominator_;
};

float* requireDirectFloatBuffer(
    JNIEnv* env,
    jobject buffer,
    std::size_t expectedFloats,
    const char* label) {
    if (buffer == nullptr) {
        throw std::invalid_argument(std::string(label) + " buffer is null");
    }
    void* address = env->GetDirectBufferAddress(buffer);
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    const jlong expectedBytes =
        static_cast<jlong>(expectedFloats * sizeof(float));
    if (address == nullptr || capacity < expectedBytes) {
        throw std::invalid_argument(
            std::string(label) + " must be a direct ByteBuffer with at least " +
            std::to_string(expectedBytes) + " bytes");
    }
    return static_cast<float*>(address);
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_separation_BsRoformerSpectralNative_nativeStft(
    JNIEnv* env,
    jobject,
    jfloatArray input,
    jobject destinationBuffer) {
    try {
        if (input == nullptr) {
            throw std::invalid_argument("BS-RoFormer input array is null");
        }
        const jsize expectedSamples = kChunkFrames * kChannels;
        if (env->GetArrayLength(input) != expectedSamples) {
            throw std::invalid_argument(
                "BS-RoFormer STFT requires exactly " +
                std::to_string(kChunkFrames) + " stereo frames");
        }

        float* destination =
            requireDirectFloatBuffer(env, destinationBuffer, kStftFloats, "STFT");

        jboolean isCopy = JNI_FALSE;
        jfloat* source = env->GetFloatArrayElements(input, &isCopy);
        if (source == nullptr) {
            throw std::runtime_error("Could not access BS-RoFormer input samples");
        }

        try {
            SpectralKernel kernel;
            kernel.forwardStereo(source, destination);
        } catch (...) {
            env->ReleaseFloatArrayElements(input, source, JNI_ABORT);
            throw;
        }
        env->ReleaseFloatArrayElements(input, source, JNI_ABORT);
    } catch (const std::invalid_argument& error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gbw_android_separation_BsRoformerSpectralNative_nativeApplyMasksAndIstft(
    JNIEnv* env,
    jobject,
    jobject stftBuffer,
    jobject maskBuffer,
    jobject outputBuffer) {
    try {
        float* stft =
            requireDirectFloatBuffer(env, stftBuffer, kStftFloats, "STFT");
        float* masks =
            requireDirectFloatBuffer(env, maskBuffer, kMaskFloats, "mask");
        float* output =
            requireDirectFloatBuffer(env, outputBuffer, kOutputFloats, "output");

        SpectralKernel kernel;
        kernel.masksToInterleavedAudio(stft, masks, output);
    } catch (const std::invalid_argument& error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gbw_android_separation_BsRoformerSpectralNative_nativeIdentity(
    JNIEnv* env,
    jobject) {
    const std::string identity =
        std::string("pffft/") + GBW_PFFFT_COMMIT +
        ";stft=2048/512;T=1151;channels=2;stems=6";
    return env->NewStringUTF(identity.c_str());
}
