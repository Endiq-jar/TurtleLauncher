#!/usr/bin/env python3
"""Guard the switchable launch engine (on-device JVM vs the Horizon engine).

Horizon is a separate native runtime that ships as its own Android app; the launcher can only
detect it and hand the Play press over to it. That makes three things easy to break silently,
so this check pins them down:

  * the engine choice must be a real, persisted setting whose default is still the on-device
    engine (nothing about existing launches may change for users who never open the setting);
  * Phone settings must actually expose the switch (rows, radios, status text and the
    install/pick/open actions), with every string it references defined;
  * the launch path must branch *before* the Java download/JRE path, and a missing or
    unsupported Horizon install must fall back to the on-device engine instead of failing
    the Play button.
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
java = main / "java"
engine_dir = java / "com/endiq/turtlelauncher/feature/engine"
all_settings = java / "com/endiq/turtlelauncher/setting/AllSettings.kt"
phone_fragment = java / "com/endiq/turtlelauncher/ui/fragment/settings/PhoneSettingsFragment.kt"
launcher_activity = java / "net/endiq/launcher/LauncherActivity.java"
layout = main / "res/layout/settings_fragment_phone.xml"
strings = main / "res/values/strings.xml"
manifest = main / "AndroidManifest.xml"

errors = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing file: {path.relative_to(repo)}")
        return ""
    return path.read_text()


def require(path: Path, needle: str, what: str) -> None:
    if needle not in read(path):
        errors.append(f"{path.name}: missing {what} (`{needle}`)")


engine = engine_dir / "GameEngine.kt"
horizon = engine_dir / "HorizonEngine.kt"
manager = engine_dir / "GameEngineManager.kt"

engine_src = read(engine)
horizon_src = read(horizon)
manager_src = read(manager)
settings_src = read(all_settings)

# ── 1. The setting exists, defaults to the on-device engine, and persists ────────────────
if 'StringSettingUnit("gameEngine", "device")' not in settings_src:
    errors.append("AllSettings: gameEngine must default to \"device\" so existing launches are unchanged")
if 'StringSettingUnit("horizonEnginePackage", "")' not in settings_src:
    errors.append("AllSettings: horizonEnginePackage must exist (blank = auto-detect)")

for member, engine_id in (("DEVICE", "device"), ("HORIZON", "horizon")):
    if not re.search(rf"{member}\(\s*\"{engine_id}\"", engine_src):
        errors.append(f"GameEngine.kt: enum member {member} must carry id \"{engine_id}\"")
# Unknown/legacy ids must degrade to the default engine rather than throw.
if "fun fromId(value: String?): GameEngine" not in engine_src or "?: DEVICE" not in engine_src:
    errors.append("GameEngine.kt: fromId() must fall back to DEVICE for unknown values")

# ── 2. Horizon discovery/launch stays defensive (never throws) ──────────────────────────
for needle, what in (
    ('const val KNOWN_PACKAGE: String = "dev.horizon.android"', "the known Horizon package id"),
    ("fun isDeviceSupported()", "an ARM64/Vulkan capability probe"),
    ("fun resolve(context: Context)", "installed-build resolution"),
    ("fun candidates(context: Context)", "the installed-app picker source"),
    ("fun launchIntent(", "the engine start intent"),
    ("fun launch(", "the engine launch helper"),
    ("Result<Unit>", "a Result-based launch that cannot throw"),
    ("runCatching", "exception-guarded package/activity lookups"),
    ("fun installIntent(uri: Uri)", "the APK install hand-off"),
):
    if needle not in horizon_src:
        errors.append(f"HorizonEngine.kt: missing {what} (`{needle}`)")

if "getValue().trim()" not in horizon_src:
    errors.append("HorizonEngine.resolve() must honour the user-picked package before the known ids")
if "getLaunchIntentForPackage" not in horizon_src:
    errors.append("HorizonEngine must obtain the engine's own launch intent")
if "FLAG_ACTIVITY_NEW_TASK" not in horizon_src:
    errors.append("HorizonEngine launch intents need FLAG_ACTIVITY_NEW_TASK (the engine runs in another task)")

# ── 3. Manager: selection read/write + fallback result ──────────────────────────────────
for needle, what in (
    ("fun selected(): GameEngine", "an accessor for the stored engine"),
    ("fun isHorizonSelected(): Boolean", "a convenience predicate"),
    ("fun select(engine: GameEngine)", "a persisting setter"),
    ("fun selectHorizonPackage(packageName: String)", "a setter for the chosen engine app"),
    ("fun horizonStatus(context: Context)", "a status model for the settings screen"),
    ("fun attemptHorizonLaunch(context: Context, version: Version?)", "the launch hand-off entry point"),
    ("NOT_SELECTED", "the not-selected outcome"),
    ("LAUNCHED", "the launched outcome"),
    ("FAILED", "the failed outcome"),
    ("R.string.engine_fallback_not_installed", "an explainer for a missing engine"),
    ("R.string.engine_fallback_unsupported", "an explainer for an unsupported device"),
    ("R.string.engine_fallback_failed", "an explainer for a failed hand-off"),
):
    if needle not in manager_src:
        errors.append(f"GameEngineManager.kt: missing {what} (`{needle}`)")

for forbidden in ("MinecraftDownloader", "LaunchGame", "launch.JvmArguments"):
    if forbidden in manager_src or forbidden in horizon_src:
        errors.append(f"engine package must not depend on the JVM launch stack ({forbidden})")

# ── 4. Phone settings expose the switch ─────────────────────────────────────────────────
layout_src = read(layout)
if "phone_engine_category" not in layout_src:
    errors.append("settings_fragment_phone.xml: the engine card (phone_engine_category) is missing")

engine_ids = [
    "engine_device_layout",
    "engine_device_title",
    "engine_device_summary",
    "engine_device_radio",
    "engine_horizon_layout",
    "engine_horizon_title",
    "engine_horizon_summary",
    "engine_horizon_radio",
    "engine_horizon_status",
    "engine_horizon_actions",
    "engine_horizon_pick",
    "engine_horizon_install",
    "engine_horizon_open",
]
for view_id in engine_ids:
    if f'@+id/{view_id}"' not in layout_src:
        errors.append(f"settings_fragment_phone.xml: missing view id {view_id}")

if layout_src.count("MiuixRadioButton") < 2:
    errors.append("settings_fragment_phone.xml: both engine rows need a selection indicator")

fragment_src = read(phone_fragment)
for needle, what in (
    ("setupEngineSection()", "engine section set-up in onViewCreated"),
    ("renderEngineState()", "state rendering for the engine rows"),
    ("binding.engineDeviceLayout.setOnClickListener", "selecting the on-device engine"),
    ("binding.engineHorizonLayout.setOnClickListener", "selecting the Horizon engine"),
    ("binding.engineHorizonPick.setOnClickListener", "the engine app picker trigger"),
    ("binding.engineHorizonInstall.setOnClickListener", "the APK install trigger"),
    ("binding.engineHorizonOpen.setOnClickListener", "the open-engine trigger"),
    ("GameEngineManager.select(GameEngine.DEVICE)", "persisting the on-device choice"),
    ("GameEngineManager.select(GameEngine.HORIZON)", "persisting the Horizon choice"),
    ("HorizonEngine.candidates(", "listing installed engine apps"),
    ("HorizonEngine.installIntent(", "handing a picked APK to the system installer"),
    ("ActivityResultContracts.GetContent()", "picking a downloaded Horizon APK"),
    ("isAdded", "a detached-fragment guard when re-rendering state"),
):
    if needle not in fragment_src:
        errors.append(f"PhoneSettingsFragment.kt: missing {what} (`{needle}`)")

if "renderEngineState()" not in fragment_src.split("override fun onChange()", 1)[-1].split("}", 1)[0]:
    errors.append("PhoneSettingsFragment.onChange() must refresh the engine rows")

# Every string the engine card references must exist.
defined = set()
for element in ET.parse(strings).getroot().iter("string"):
    defined.add(element.get("name"))
referenced = set(re.findall(r'@string/([A-Za-z0-9_]+)', layout_src))
referenced |= set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', manager_src + fragment_src + engine_src))
missing = sorted(name for name in referenced if name not in defined)
if missing:
    errors.append(f"strings.xml: undefined string resources referenced by the engine UI: {missing}")

for name in ("engine_device_title", "engine_horizon_title", "setting_category_phone_engine",
             "engine_status_missing", "engine_status_unsupported_abi",
             "engine_pick_title", "engine_install_unavailable", "engine_open_failed"):
    if name not in defined:
        errors.append(f"strings.xml: missing {name}")

# ── 5. The launch path branches before the Java download/JRE path ───────────────────────
launcher_src = read(launcher_activity)
launch_body = launcher_src.split("private void launchGame(Version version)", 1)
if len(launch_body) != 2:
    errors.append("LauncherActivity: launchGame(Version) not found")
else:
    body = launch_body[1].split("private void checkNotice()", 1)[0]
    hand_off = body.find("attemptHorizonLaunch")
    account_gate = body.find("LocalAccountUtils.checkUsageAllowed")
    java_path = body.find("preLaunch(")
    if hand_off < 0:
        errors.append("LauncherActivity.launchGame must call GameEngineManager.attemptHorizonLaunch")
    else:
        if 0 <= account_gate < hand_off:
            errors.append("the engine hand-off must happen before the launcher-account prompt")
        if 0 <= java_path < hand_off:
            errors.append("the engine hand-off must happen before the Java launch path (preLaunch)")
        if "HorizonLaunchOutcome.LAUNCHED" not in body:
            errors.append("launchGame must stop once Horizon was launched")
        if "return;" not in body.split("HorizonLaunchOutcome.LAUNCHED", 1)[-1][:200]:
            errors.append("launchGame must return after a successful Horizon hand-off")
        if "getMessage()" not in body:
            errors.append("launchGame must surface the fallback reason when the hand-off fails")

# ── 6. The engine is a first-class, launcher-managed install ────────────────────────────
# "Built in" cannot mean embedding Rust into this APK, so it must mean the launcher owns the
# whole lifecycle: resolve the release, pick this device's ABI, verify, install, detect.
installer = engine_dir / "HorizonEngineInstaller.kt"
flow = engine_dir / "HorizonEngineInstallFlow.kt"
installer_src = read(installer)
flow_src = read(flow)

for needle, what in (
    ("fun latestRelease(repo: String = sourceRepo()): Result<EngineRelease>", "release resolution"),
    ("https://api.github.com", "the GitHub release API"),
    ("fun pickAsset(assets: JsonArray): JsonObject?", "ABI-aware asset selection"),
    ("Build.SUPPORTED_ABIS", "the device ABI probe"),
    ("fun downloadLatest(context: Context", "the download entry point"),
    ("fun download(context: Context, release: EngineRelease", "the streaming download"),
    ("fun install(context: Context, apk: File): Result<String>", "the install hand-off"),
    ("fun installIntent(context: Context, apk: File): Intent", "the installer intent"),
    ("getPackageArchiveInfo", "reading the package id out of the APK"),
    ("GameEngineManager.selectHorizonPackage", "remembering the downloaded engine's package"),
    ("FileProvider.getUriForFile", "the FileProvider hand-off"),
    ("SHA256SUMS.txt", "checksum verification"),
    ("MessageDigest.getInstance(\"SHA-256\")", "SHA-256 hashing"),
    ("fun uninstallIntent(packageName: String): Intent", "uninstall support"),
    ("fun setSourceRepo(repo: String)", "the configurable engine source"),
):
    if needle not in installer_src:
        errors.append(f"HorizonEngineInstaller.kt: missing {what} (`{needle}`)")

if 'StringSettingUnit("horizonEngineSource"' not in settings_src:
    errors.append("AllSettings: horizonEngineSource must exist so the feed is configurable")
if "Vasysik/horizon-android" not in settings_src:
    errors.append("AllSettings: the engine source needs a working default feed")
if "ACTION_DELETE" not in installer_src:
    errors.append("HorizonEngineInstaller: uninstall must go through ACTION_DELETE")

# Play must offer the install instead of only failing over silently.
if "NOT_INSTALLED" not in manager_src:
    errors.append("GameEngineManager: attemptHorizonLaunch must report NOT_INSTALLED separately")
for needle, what in (
    ("HorizonLaunchOutcome.NOT_INSTALLED", "the install offer on Play"),
    ("HorizonEngineInstallFlow.offerInstall", "the built-in install flow hook"),
    ("startOnDeviceEngine(version)", "the fallback into the Java path"),
    ("private void startOnDeviceEngine(Version version)", "the extracted on-device path"),
):
    if needle not in launcher_src:
        errors.append(f"LauncherActivity.java: missing {what} (`{needle}`)")

if "downloadLatest" not in flow_src or "engine_download_progress" not in flow_src:
    errors.append("HorizonEngineInstallFlow.kt: the flow needs download progress feedback")
if "onDeclined.run()" not in flow_src:
    errors.append("HorizonEngineInstallFlow.kt: declining must fall back to the on-device engine")

# Phone settings own the same lifecycle: download, source override, uninstall.
for needle, what in (
    ("downloadHorizonEngine()", "the built-in download action"),
    ("HorizonEngineInstaller.downloadLatest(", "the downloader call"),
    ("HorizonEngineInstaller.setSourceRepo(", "editing the engine source"),
    ("HorizonEngineInstaller.uninstallIntent(", "the uninstall action"),
    ("binding.engineHorizonUninstall.visibility", "uninstall visibility"),
    ("binding.engineHorizonSource.text", "the source label"),
    ("R.string.engine_download_progress", "download progress in the status line"),
):
    if needle not in fragment_src:
        errors.append(f"PhoneSettingsFragment.kt: missing {what} (`{needle}`)")

for view_id in ("engine_horizon_source", "engine_horizon_uninstall"):
    if f'@+id/{view_id}"' not in layout_src:
        errors.append(f"settings_fragment_phone.xml: missing view id {view_id}")

# Android will not install an APK on our behalf without this permission.
if "android.permission.REQUEST_INSTALL_PACKAGES" not in read(manifest):
    errors.append("AndroidManifest.xml: REQUEST_INSTALL_PACKAGES is required to install the engine")
if 'android:authorities="${applicationId}.provider"' not in read(manifest):
    errors.append("AndroidManifest.xml: the FileProvider used for the APK hand-off is missing")

# Package visibility for the engine app (Android 11+ hides other apps otherwise).
manifest_src = read(manifest)
if 'android:name="dev.horizon.android"' not in manifest_src:
    errors.append("AndroidManifest.xml: <queries> must list dev.horizon.android to detect the engine")

if errors:
    print("Game engine checks failed:", flush=True)
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

print("Game engine checks passed: persisted switch, built-in engine install, defensive hand-off, fallback")
