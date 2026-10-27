#include "NixGroupCallAudioBitrate.h"

#include <atomic>

namespace nixgramx {

namespace {
std::atomic<int> customAudioBitrateBps{0};
}

int groupCallCustomAudioBitrateBps() {
    return customAudioBitrateBps.load(std::memory_order_relaxed);
}

void setGroupCallCustomAudioBitrateBps(int bps) {
    customAudioBitrateBps.store(bps, std::memory_order_relaxed);
}

} // namespace nixgramx
