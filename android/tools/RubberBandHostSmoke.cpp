#include <rubberband/RubberBandStretcher.h>

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <vector>

namespace {
constexpr int kRate = 48000;
constexpr int kChannels = 2;
constexpr int kFrames = kRate * 2;
constexpr int kBlock = 4096;
constexpr double kPi = 3.14159265358979323846;

struct Rendered {
    std::vector<float> left;
    std::vector<float> right;
};

void drain(RubberBand::RubberBandStretcher &stretcher, Rendered &out) {
    while (true) {
        const int available = stretcher.available();
        if (available <= 0) return;
        const int count = std::min(available, kBlock);
        std::vector<float> left(static_cast<size_t>(count));
        std::vector<float> right(static_cast<size_t>(count));
        float *dest[kChannels] = {left.data(), right.data()};
        const size_t got = stretcher.retrieve(dest, static_cast<size_t>(count));
        if (got == 0) throw std::runtime_error("retrieve returned zero after available > 0");
        out.left.insert(out.left.end(), left.begin(), left.begin() + static_cast<long>(got));
        out.right.insert(out.right.end(), right.begin(), right.begin() + static_cast<long>(got));
    }
}

Rendered render(double semitones) {
    std::vector<float> left(kFrames);
    std::vector<float> right(kFrames);
    for (int i = 0; i < kFrames; ++i) {
        const double t = static_cast<double>(i) / kRate;
        left[static_cast<size_t>(i)] = static_cast<float>(0.35 * std::sin(2.0 * kPi * 440.0 * t));
        right[static_cast<size_t>(i)] = static_cast<float>(0.35 * std::sin(2.0 * kPi * 660.0 * t));
    }

    const double scale = std::pow(2.0, semitones / 12.0);
    RubberBand::RubberBandStretcher stretcher(
        kRate,
        kChannels,
        RubberBand::RubberBandStretcher::OptionProcessOffline |
            RubberBand::RubberBandStretcher::OptionEngineFiner |
            RubberBand::RubberBandStretcher::OptionChannelsTogether,
        1.0,
        scale
    );
    stretcher.setMaxProcessSize(kBlock);
    stretcher.setExpectedInputDuration(kFrames);
    if (stretcher.getEngineVersion() != 3) throw std::runtime_error("R3 engine is not active");

    for (int offset = 0; offset < kFrames; offset += kBlock) {
        const int count = std::min(kBlock, kFrames - offset);
        const float *src[kChannels] = {left.data() + offset, right.data() + offset};
        stretcher.study(src, static_cast<size_t>(count), offset + count == kFrames);
    }

    Rendered out;
    for (int offset = 0; offset < kFrames; offset += kBlock) {
        const int count = std::min(kBlock, kFrames - offset);
        const float *src[kChannels] = {left.data() + offset, right.data() + offset};
        stretcher.process(src, static_cast<size_t>(count), offset + count == kFrames);
        drain(stretcher, out);
    }
    drain(stretcher, out);
    if (stretcher.available() != -1) throw std::runtime_error("R3 did not reach terminal state");
    if (out.left.size() != out.right.size()) throw std::runtime_error("channel lengths diverged");
    return out;
}

double risingZeroFrequency(const std::vector<float> &samples) {
    if (samples.size() < static_cast<size_t>(kRate)) throw std::runtime_error("output too short");
    const size_t start = std::min(samples.size() / 4, static_cast<size_t>(kRate / 2));
    const size_t end = samples.size() - start;
    size_t crossings = 0;
    for (size_t i = start + 1; i < end; ++i) {
        if (samples[i - 1] <= 0.0f && samples[i] > 0.0f) ++crossings;
    }
    const double seconds = static_cast<double>(end - start) / kRate;
    return crossings / seconds;
}

void validate(double semitones) {
    const auto out = render(semitones);
    const long delta = std::labs(static_cast<long>(out.left.size()) - kFrames);
    if (delta > kRate / 50) throw std::runtime_error("duration drift exceeds 20 ms");

    const double scale = std::pow(2.0, semitones / 12.0);
    const double leftHz = risingZeroFrequency(out.left);
    const double rightHz = risingZeroFrequency(out.right);
    if (std::abs(leftHz - 440.0 * scale) > 2.0) throw std::runtime_error("left pitch golden failed");
    if (std::abs(rightHz - 660.0 * scale) > 2.0) throw std::runtime_error("right pitch golden failed");

    std::cout << (semitones > 0 ? "+" : "") << semitones
              << "st frames=" << out.left.size()
              << " left=" << leftHz << "Hz right=" << rightHz << "Hz\n";
}
} // namespace

int main() {
    try {
        validate(+3.0);
        validate(-3.0);
        std::cout << "RUBBERBAND_HOST_SMOKE_OK\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << "RUBBERBAND_HOST_SMOKE_FAIL: " << e.what() << "\n";
        return 1;
    }
}
