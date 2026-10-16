// Native bridge between DiscordSocialBridge.kt and the Discord Social SDK's token-free
// Rich Presence RPC (discordpp::Client::SetApplicationId + Client::UpdateRichPresence).
//
// No Client::Connect() call, no OAuth token, no Discord account credential of any kind
// is used here - this only talks to the Discord app installed and signed in on the same
// device, over the SDK's native Android RPC channel (Social SDK 1.10+). See:
// https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence#rich-presence-without-authentication
//
// Threading model: discordpp::Client and discordpp::RunCallbacks() are only ever touched
// from one dedicated background thread (RpcThreadMain). JNI calls from Kotlin just hand
// off a command (an activity to publish, or "clear") across a mutex-guarded slot and
// return immediately; the RPC thread picks it up, applies it, and pumps RunCallbacks()
// on a short period so the SDK's internal (and this call's) callbacks get serviced.

#include <jni.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

#include <android/log.h>

#include <discordpp.h>

namespace {

constexpr const char* kLogTag = "Turtle-DiscordRPC-Native";
constexpr auto kPumpInterval = std::chrono::milliseconds(150);

struct ButtonPair {
    std::string label;
    std::string url;
};

struct PresenceCommand {
    bool clear = false;

    std::string name;
    std::string details;
    std::string state;
    int activityType = 0;

    bool hasTimestamp = false;
    int64_t startTimestampSec = 0;

    std::string largeImage;
    std::string largeText;
    std::string smallImage;
    std::string smallText;

    std::vector<ButtonPair> buttons;
};

std::unique_ptr<discordpp::Client> gClient;
std::thread gThread;
std::mutex gMutex;
std::condition_variable gCv;
std::optional<PresenceCommand> gPending;
bool gShutdownRequested = false;

std::atomic<bool> gRunning{false};
std::atomic<bool> gInitialized{false};
// 0 = no result yet, 1 = last UpdateRichPresence call succeeded, 2 = it failed.
std::atomic<int> gLastResult{0};

std::string JStringToStd(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void ApplyCommand(const PresenceCommand& cmd) {
    if (!gClient) return;

    if (cmd.clear) {
        gClient->ClearRichPresence();
        return;
    }

    discordpp::Activity activity;
    activity.SetType(static_cast<discordpp::ActivityTypes>(cmd.activityType));
    if (!cmd.name.empty()) activity.SetName(cmd.name);
    if (!cmd.details.empty()) activity.SetDetails(cmd.details);
    if (!cmd.state.empty()) activity.SetState(cmd.state);

    if (cmd.hasTimestamp) {
        discordpp::ActivityTimestamps timestamps;
        timestamps.SetStart(static_cast<uint64_t>(cmd.startTimestampSec));
        activity.SetTimestamps(timestamps);
    }

    if (!cmd.largeImage.empty() || !cmd.smallImage.empty()) {
        discordpp::ActivityAssets assets;
        if (!cmd.largeImage.empty()) {
            assets.SetLargeImage(cmd.largeImage);
            if (!cmd.largeText.empty()) assets.SetLargeText(cmd.largeText);
        }
        if (!cmd.smallImage.empty()) {
            assets.SetSmallImage(cmd.smallImage);
            if (!cmd.smallText.empty()) assets.SetSmallText(cmd.smallText);
        }
        activity.SetAssets(assets);
    }

    for (const auto& button : cmd.buttons) {
        discordpp::ActivityButton activityButton;
        activityButton.SetLabel(button.label);
        activityButton.SetUrl(button.url);
        activity.AddButton(activityButton);
    }

    gClient->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        gLastResult.store(result.Successful() ? 1 : 2);
        if (!result.Successful()) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "UpdateRichPresence failed.");
        }
    });
}

void RpcThreadMain(uint64_t applicationId) {
    gClient = std::make_unique<discordpp::Client>();
    gClient->SetApplicationId(applicationId);
    gInitialized.store(true);

    while (true) {
        std::optional<PresenceCommand> cmd;
        {
            std::unique_lock<std::mutex> lock(gMutex);
            gCv.wait_for(lock, kPumpInterval, [] { return gPending.has_value() || gShutdownRequested; });
            if (gShutdownRequested) break;
            if (gPending.has_value()) {
                cmd = std::move(gPending);
                gPending.reset();
            }
        }
        if (cmd.has_value()) ApplyCommand(*cmd);
        discordpp::RunCallbacks();
    }

    // Best-effort: clear the activity and give the SDK a couple of pumps to flush it
    // before tearing the client down, so presence doesn't linger after the launcher
    // and the game both go away.
    if (gClient) {
        gClient->ClearRichPresence();
        for (int i = 0; i < 3; ++i) {
            discordpp::RunCallbacks();
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
    }
    gClient.reset();
    gInitialized.store(false);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeInit(
        JNIEnv*, jclass, jlong applicationId) {
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (gRunning.load()) return gInitialized.load() ? JNI_TRUE : JNI_FALSE;
        gShutdownRequested = false;
        gPending.reset();
        gLastResult.store(0);
        gRunning.store(true);
    }

    gThread = std::thread(RpcThreadMain, static_cast<uint64_t>(applicationId));

    // Give the client a brief moment to come up so an immediate isConfigured() check
    // right after init() reflects reality; the thread keeps running either way.
    for (int i = 0; i < 50 && !gInitialized.load(); ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    return gInitialized.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeUpdatePresence(
        JNIEnv* env, jclass,
        jstring jName, jstring jDetails, jstring jState, jint jActivityType,
        jboolean jHasTimestamp, jlong jStartTimestampSec,
        jstring jLargeImage, jstring jLargeText, jstring jSmallImage, jstring jSmallText,
        jobjectArray jButtonLabels, jobjectArray jButtonUrls) {
    if (!gRunning.load()) return;

    PresenceCommand cmd;
    cmd.clear = false;
    cmd.name = JStringToStd(env, jName);
    cmd.details = JStringToStd(env, jDetails);
    cmd.state = JStringToStd(env, jState);
    cmd.activityType = static_cast<int>(jActivityType);
    cmd.hasTimestamp = (jHasTimestamp == JNI_TRUE);
    cmd.startTimestampSec = static_cast<int64_t>(jStartTimestampSec);
    cmd.largeImage = JStringToStd(env, jLargeImage);
    cmd.largeText = JStringToStd(env, jLargeText);
    cmd.smallImage = JStringToStd(env, jSmallImage);
    cmd.smallText = JStringToStd(env, jSmallText);

    const jsize count = jButtonLabels ? env->GetArrayLength(jButtonLabels) : 0;
    cmd.buttons.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto labelObj = static_cast<jstring>(env->GetObjectArrayElement(jButtonLabels, i));
        auto urlObj = static_cast<jstring>(env->GetObjectArrayElement(jButtonUrls, i));
        ButtonPair pair{JStringToStd(env, labelObj), JStringToStd(env, urlObj)};
        if (labelObj) env->DeleteLocalRef(labelObj);
        if (urlObj) env->DeleteLocalRef(urlObj);
        if (!pair.label.empty() && !pair.url.empty()) cmd.buttons.push_back(std::move(pair));
    }

    {
        std::lock_guard<std::mutex> lock(gMutex);
        gPending = std::move(cmd);
    }
    gCv.notify_one();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeClear(
        JNIEnv*, jclass) {
    if (!gRunning.load()) return;
    PresenceCommand cmd;
    cmd.clear = true;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        gPending = std::move(cmd);
    }
    gCv.notify_one();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeShutdown(
        JNIEnv*, jclass) {
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (!gRunning.load()) return;
        gShutdownRequested = true;
    }
    gCv.notify_one();
    if (gThread.joinable()) gThread.join();
    gRunning.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_endiq_turtlelauncher_feature_discord_DiscordSocialBridge_nativeLastResultOk(
        JNIEnv*, jclass) {
    return gLastResult.load() == 1 ? JNI_TRUE : JNI_FALSE;
}
