#pragma once
// Test double only: the production bridge is separately compiled against the real AAR headers.
#include <atomic>
#include <chrono>
#include <cstdint>
#include <functional>
#include <string>
#include <thread>
#include <vector>
namespace discordpp {
inline std::atomic<int> creates{0}, updates{0}, clears{0}, drops{0};
inline std::atomic<bool> dispatch{true};
inline std::atomic<bool> sawMilliseconds{false};
struct ClientResult { bool Successful() const { return true; } };
enum class ActivityTypes { Playing };
struct ActivityTimestamps { uint64_t start = 0; void SetStart(uint64_t v) { start = v; } };
struct ActivityAssets {
 void SetLargeImage(std::string) {} void SetLargeText(std::string) {}
 void SetSmallImage(std::string) {} void SetSmallText(std::string) {}
};
struct ActivityButton { void SetLabel(std::string) {} void SetUrl(std::string) {} };
struct Activity {
 void SetType(ActivityTypes) {} void SetDetails(std::string) {} void SetState(std::string) {}
 void SetTimestamps(ActivityTimestamps t) { sawMilliseconds.store(t.start > 1000000000000ULL); }
 void SetAssets(ActivityAssets) {} void AddButton(ActivityButton) {}
};
inline std::vector<std::function<void(ClientResult)>> pending;
class Client {
public:
 Client() { ++creates; std::this_thread::sleep_for(std::chrono::milliseconds(200)); }
 ~Client() { ++drops; pending.clear(); }
 void SetApplicationId(uint64_t) {}
 void ClearRichPresence() { ++clears; }
 void UpdateRichPresence(Activity, std::function<void(ClientResult)> callback) {
  ++updates; pending.push_back(std::move(callback));
 }
};
inline void RunCallbacks() {
 if (!dispatch.load()) return;
 auto callbacks = std::move(pending); pending.clear();
 for (auto& cb: callbacks) cb(ClientResult{});
}
}
