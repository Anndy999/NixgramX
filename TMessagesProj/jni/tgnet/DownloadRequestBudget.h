#pragma once

#include <cstdint>
#include <list>
#include <map>
#include <utility>

// Keep pending foreground chunks ahead of older background chunks. Control
// requests keep their own ordering and all downloads still use the same caps.
template <typename Item, typename IsDownload, typename IsForeground>
void prioritizeForegroundDownloads(std::list<Item> &queue, IsDownload isDownload, IsForeground isForeground) {
    auto firstBackground = queue.end();
    std::list<Item> foreground;
    for (auto item = queue.begin(); item != queue.end();) {
        auto current = item++;
        if (!isDownload(*current)) continue;
        if (isForeground(*current)) {
            if (firstBackground != queue.end()) foreground.splice(foreground.end(), queue, current);
        } else if (firstBackground == queue.end()) {
            firstBackground = current;
        }
    }
    queue.splice(firstBackground, foreground);
}

// Rebuilt from running requests on each scheduling pass, so cancellation,
// retries and DC migration cannot leak reservations.
class DownloadRequestBudget {
public:
    explicit DownloadRequestBudget(bool premium) : limit((premium ? 16u : 2u) * 1024 * 1024) {
    }

    void account(uint32_t dc, bool small, uint32_t bytes) {
        used[{dc, small}] += bytes;
    }

    bool acquire(uint32_t dc, bool small, uint32_t bytes) {
        if (bytes == 0) {
            return true; // Control requests must not wait behind file data.
        }
        auto &current = used[{dc, small}];
        if (bytes > limit || current > limit - bytes) {
            return false;
        }
        current += bytes;
        return true;
    }

private:
    const uint32_t limit;
    std::map<std::pair<uint32_t, bool>, uint64_t> used;
};
