#include <algorithm>
#include <cmath>
#include <complex>
#include <cstddef>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include <pffft/pffft.h>

namespace {

constexpr int kNfft = 2048;
constexpr int kHop = 512;
constexpr int kPad = kNfft / 2;
constexpr int kBins = kNfft / 2 + 1;
constexpr int kInputFrames = 4096;
constexpr float kPi = 3.14159265358979323846f;

struct Golden {
  int frame;
  int bin;
  float real;
  float imag;
};

constexpr Golden kGolden[] = {
    {0, 0, 9.860671997070312f, 0.0f},
    {0, 1, -9.880537986755371f, -1.9777141346821736e-07f},
    {0, 20, 62.27203369140625f, 6.114950792834861e-06f},
    {0, 46, 79.6016845703125f, -1.7730189938447438e-07f},
    {0, 1024, -0.0017223358154296875f, 0.0f},
    {1, 0, 4.925708770751953f, 0.0f},
    {1, 1, 0.026258686557412148f, -4.935027599334717f},
    {1, 20, 94.65472412109375f, -80.96967315673828f},
    {1, 46, -71.8641357421875f, -37.399906158447266f},
    {1, 1024, -0.0008637905120849609f, 0.0f},
    {4, 0, -0.004567272961139679f, 0.0f},
    {4, 1, -0.004620021674782038f, -0.0015607269015163183f},
    {4, 20, 56.91844940185547f, 128.33250427246094f},
    {4, 46, -25.654190063476562f, 77.97554779052734f},
    {4, 1024, 6.07222318649292e-06f, 0.0f},
    {8, 0, -7.2715959548950195f, 0.0f},
    {8, 1, 7.302610397338867f, 0.02249951660633087f},
    {8, 20, -149.69654846191406f, -9.199136734008789f},
    {8, 46, -81.38983154296875f, -11.559488296508789f},
    {8, 1024, -0.02081131935119629f, 0.0f},
};

class AlignedBuffer {
 public:
  explicit AlignedBuffer(std::size_t count)
      : ptr_(static_cast<float*>(pffft_aligned_malloc(count * sizeof(float)))),
        count_(count) {
    if (!ptr_) {
      throw std::bad_alloc();
    }
    std::fill(ptr_, ptr_ + count_, 0.0f);
  }

  ~AlignedBuffer() {
    pffft_aligned_free(ptr_);
  }

  AlignedBuffer(const AlignedBuffer&) = delete;
  AlignedBuffer& operator=(const AlignedBuffer&) = delete;

  float* data() { return ptr_; }
  const float* data() const { return ptr_; }
  float& operator[](std::size_t i) { return ptr_[i]; }
  const float& operator[](std::size_t i) const { return ptr_[i]; }

 private:
  float* ptr_;
  std::size_t count_;
};

class Spectral {
 public:
  Spectral() : setup_(pffft_new_setup(kNfft, PFFFT_REAL)), time_(kNfft), freq_(kNfft), work_(kNfft) {
    if (!setup_) {
      throw std::runtime_error("pffft_new_setup failed");
    }
  }

  ~Spectral() { pffft_destroy_setup(setup_); }

  std::vector<std::complex<float>> forward(const std::vector<float>& signal) {
    const auto padded = reflectPad(signal);
    if (padded.size() < static_cast<std::size_t>(kNfft)) {
      throw std::runtime_error("input too short");
    }
    const int frames = 1 + static_cast<int>((padded.size() - kNfft) / kHop);
    std::vector<std::complex<float>> result(static_cast<std::size_t>(frames) * kBins);

    for (int frame = 0; frame < frames; ++frame) {
      const std::size_t offset = static_cast<std::size_t>(frame) * kHop;
      for (int i = 0; i < kNfft; ++i) {
        time_[i] = padded[offset + i] * hann(i);
      }
      pffft_transform_ordered(
          setup_, time_.data(), freq_.data(), work_.data(), PFFFT_FORWARD);

      auto* dst = result.data() + static_cast<std::size_t>(frame) * kBins;
      dst[0] = {freq_[0], 0.0f};
      for (int bin = 1; bin < kNfft / 2; ++bin) {
        dst[bin] = {freq_[2 * bin], freq_[2 * bin + 1]};
      }
      dst[kNfft / 2] = {freq_[1], 0.0f};
    }
    return result;
  }

  std::vector<float> inverse(
      const std::vector<std::complex<float>>& spectra,
      int frames,
      int outputFrames) {
    const int paddedFrames = kNfft + (frames - 1) * kHop;
    std::vector<float> accum(static_cast<std::size_t>(paddedFrames), 0.0f);
    std::vector<float> denom(static_cast<std::size_t>(paddedFrames), 0.0f);

    for (int frame = 0; frame < frames; ++frame) {
      const auto* src = spectra.data() + static_cast<std::size_t>(frame) * kBins;
      std::fill(freq_.data(), freq_.data() + kNfft, 0.0f);
      freq_[0] = src[0].real();
      freq_[1] = src[kNfft / 2].real();
      for (int bin = 1; bin < kNfft / 2; ++bin) {
        freq_[2 * bin] = src[bin].real();
        freq_[2 * bin + 1] = src[bin].imag();
      }

      pffft_transform_ordered(
          setup_, freq_.data(), time_.data(), work_.data(), PFFFT_BACKWARD);

      const std::size_t offset = static_cast<std::size_t>(frame) * kHop;
      for (int i = 0; i < kNfft; ++i) {
        const float w = hann(i);
        const float sample = (time_[i] / static_cast<float>(kNfft)) * w;
        accum[offset + i] += sample;
        denom[offset + i] += w * w;
      }
    }

    if (outputFrames + 2 * kPad > paddedFrames) {
      throw std::runtime_error("ISTFT geometry mismatch");
    }

    std::vector<float> output(static_cast<std::size_t>(outputFrames));
    for (int i = 0; i < outputFrames; ++i) {
      const int index = i + kPad;
      if (denom[index] <= 1.0e-11f) {
        throw std::runtime_error("ISTFT NOLA denominator is zero");
      }
      output[i] = accum[index] / denom[index];
    }
    return output;
  }

 private:
  static float hann(int n) {
    // torch.hann_window(N, periodic=True): 0.5 * (1 - cos(2*pi*n/N)).
    return 0.5f * (1.0f - std::cos(2.0f * kPi * static_cast<float>(n) /
                                  static_cast<float>(kNfft)));
  }

  static std::vector<float> reflectPad(const std::vector<float>& input) {
    if (input.size() <= static_cast<std::size_t>(kPad)) {
      throw std::runtime_error("reflect padding requires input longer than pad");
    }
    std::vector<float> padded(input.size() + 2 * kPad);
    for (int i = 0; i < kPad; ++i) {
      padded[i] = input[static_cast<std::size_t>(kPad - i)];
    }
    std::copy(input.begin(), input.end(), padded.begin() + kPad);
    const std::size_t rightStart = static_cast<std::size_t>(kPad) + input.size();
    for (int i = 0; i < kPad; ++i) {
      padded[rightStart + i] = input[input.size() - 2u - static_cast<std::size_t>(i)];
    }
    return padded;
  }

  PFFFT_Setup* setup_;
  AlignedBuffer time_;
  AlignedBuffer freq_;
  AlignedBuffer work_;
};

float expectedInput(int n) {
  const float t = static_cast<float>(n);
  const float toneA = 0.31f * std::sin(2.0f * kPi * 440.0f * t / 44100.0f);
  const float toneB = 0.17f * std::cos(2.0f * kPi * 997.0f * t / 44100.0f);
  const float deterministicNoise =
      0.001f * static_cast<float>((n % 17) - 8);
  return toneA + toneB + deterministicNoise;
}

void requireNear(float actual, float expected, float absTol, const std::string& label) {
  const float error = std::abs(actual - expected);
  if (!std::isfinite(actual) || error > absTol) {
    std::cerr << std::setprecision(10) << label << " mismatch: actual=" << actual
              << " expected=" << expected << " abs_error=" << error
              << " tolerance=" << absTol << "\n";
    std::exit(1);
  }
}

}  // namespace

int main() {
  static_assert(588800 / kHop == 1150, "production chunk must align exactly to hop");
  constexpr int kProductionStftFrames = 1 + 588800 / kHop;
  static_assert(kProductionStftFrames == 1151, "unexpected production STFT frame count");

  std::vector<float> input(kInputFrames);
  for (int i = 0; i < kInputFrames; ++i) {
    input[i] = expectedInput(i);
  }

  Spectral spectral;
  const auto stft = spectral.forward(input);
  const int frames = static_cast<int>(stft.size() / kBins);
  if (frames != 9) {
    std::cerr << "STFT frame count mismatch: " << frames << " != 9\n";
    return 1;
  }

  constexpr float kGoldenTolerance = 2.0e-3f;
  for (const auto& golden : kGolden) {
    const auto actual =
        stft[static_cast<std::size_t>(golden.frame) * kBins + golden.bin];
    requireNear(actual.real(), golden.real, kGoldenTolerance,
                "STFT real frame=" + std::to_string(golden.frame) +
                    " bin=" + std::to_string(golden.bin));
    requireNear(actual.imag(), golden.imag, kGoldenTolerance,
                "STFT imag frame=" + std::to_string(golden.frame) +
                    " bin=" + std::to_string(golden.bin));
  }

  // Verify the native real/imag multiply convention used when applying each
  // BS-RoFormer mask to the mixture STFT.
  const std::complex<float> mixture(1.25f, -0.75f);
  const std::complex<float> mask(-0.2f, 0.4f);
  const auto product = mixture * mask;
  requireNear(product.real(), 0.05f, 1.0e-7f, "complex multiply real");
  requireNear(product.imag(), 0.65f, 1.0e-7f, "complex multiply imag");

  const auto reconstructed = spectral.inverse(stft, frames, kInputFrames);
  if (reconstructed.size() != input.size()) {
    std::cerr << "ISTFT output length mismatch\n";
    return 1;
  }

  float maxError = 0.0f;
  double meanError = 0.0;
  for (std::size_t i = 0; i < input.size(); ++i) {
    const float error = std::abs(reconstructed[i] - input[i]);
    maxError = std::max(maxError, error);
    meanError += error;
  }
  meanError /= static_cast<double>(input.size());
  if (maxError > 5.0e-5f) {
    std::cerr << std::setprecision(10)
              << "ISTFT reconstruction error too high: max=" << maxError
              << " mean=" << meanError << "\n";
    return 1;
  }

  std::cout << std::setprecision(10)
            << "BSROFORMER_SPECTRAL_HOST_OK"
            << " frames=" << frames
            << " bins=" << kBins
            << " production_frames=" << kProductionStftFrames
            << " max_reconstruction_error=" << maxError
            << " mean_reconstruction_error=" << meanError << "\n";
  return 0;
}
