#include <cassert>
#include <iostream>
#include "../../TurtleLauncher/src/main/native/discord/discord_rpc_bridge.cpp"

template<class F> void until(F predicate) {
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
    while (!predicate()) {
        assert(std::chrono::steady_clock::now() < deadline);
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
}
void queue(PresenceCommand cmd) {
    { std::lock_guard<std::mutex> lock(gMutex); gPending = std::move(cmd); }
    gCv.notify_one();
}
int main() {
    auto before = std::chrono::steady_clock::now();
    assert(Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeInit(nullptr, nullptr, 1560576927419338863LL));
    assert(std::chrono::steady_clock::now() - before < std::chrono::milliseconds(150));
    // Queuing before construction completes must not lose the initial activity.
    PresenceCommand cmd; cmd.details = "Playing 1.21.1"; cmd.hasTimestamp = true; cmd.startTimestampMs = 1792140000000LL;
    queue(cmd);
    until([] { return gLastResult.load() == 1; });
    assert(discordpp::sawMilliseconds.load());
    // A callback for an obsolete activity must not turn "clear" back into connected.
    discordpp::dispatch.store(false);
    int oldUpdates = discordpp::updates.load();
    queue(cmd);
    until([&] { return discordpp::updates.load() > oldUpdates; });
    PresenceCommand clear; clear.clear = true;
    int oldClears = discordpp::clears.load(); queue(clear);
    until([&] { return discordpp::clears.load() > oldClears; });
    discordpp::dispatch.store(true);
    std::this_thread::sleep_for(std::chrono::milliseconds(250));
    assert(gLastResult.load() == 0);
    Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeShutdown(nullptr, nullptr);
    assert(!gRunning.load() && !gInitialized.load() && !gThread.joinable());
    assert(discordpp::drops.load() == 1);
    // Release/reacquire must construct a fresh client, never a second concurrent worker.
    assert(Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeInit(nullptr, nullptr, 1560576927419338863LL));
    queue(cmd); until([] { return gLastResult.load() == 1; });
    Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeShutdown(nullptr, nullptr);
    assert(discordpp::drops.load() == 2 && discordpp::creates.load() == 2);
    Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeShutdown(nullptr, nullptr);
    assert(!gRunning.load() && gLastResult.load() == 0);
    std::cout << "Discord native worker queue, callback generation, milliseconds and shutdown checks passed (stub SDK)\n";
}
