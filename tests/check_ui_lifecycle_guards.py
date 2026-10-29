#!/usr/bin/env python3
"""Guard the destroyed-activity Glide crash fix.

Crash (repeated, launcher v4): a RefreshVersionsEvent made
MainMenuFragment post refreshCurrentVersion() to the main Handler via
TaskExecutors.runInUIThread; the user left in the meantime, the posted
runnable still ran against the destroyed Activity, and
VersionIconUtils.start hit Glide's "You cannot start a load for a
destroyed activity". Two independent guards now prevent it; this test
keeps both:

  1. VersionIconUtils.start bails out when the ImageView's host Activity
     is finishing/destroyed, before the first Glide.with call.
  2. MainMenuFragment.refreshCurrentVersion returns early when the
     fragment is no longer added / its Activity is gone (binding is never
     nulled in that fragment, so binding==null alone is not a guard).
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "TurtleLauncher/src/main/java"

utils = (main / "com/endiq/turtlelauncher/feature/version/utils"
         / "VersionIconUtils.kt").read_text()
start = utils.index("fun start(imageView: ImageView)")
guard = utils.index("if (isDestroyedActivity(imageView.context)) return false", start)
first_glide = utils.index("Glide.with(imageView)", start)
assert guard < first_glide, "destroyed-activity guard must precede the first Glide call"
assert "activity.isFinishing || activity.isDestroyed" in utils
assert "current.baseContext" in utils, "guard must unwrap ContextWrappers to find the Activity"

fragment = (main / "net/endiq/launcher/fragments/MainMenuFragment.java").read_text()
refresh = fragment.index("private void refreshCurrentVersion()")
icon_call = fragment.index("new VersionIconUtils(version).start", refresh)
body = fragment[refresh:icon_call]
assert "binding == null || !isAdded()" in body, "refreshCurrentVersion needs an isAdded guard"
assert "activity.isFinishing() || activity.isDestroyed()" in body, \
    "refreshCurrentVersion needs a destroyed-activity guard"

account = fragment.index("private void refreshAccountButton()")
account_guard = fragment.index("binding == null || !isAdded()", account)
require_ctx = fragment.index("requireContext(),", account)  # a call site, not a comment mention
assert account_guard < require_ctx, "refreshAccountButton must check isAdded before requireContext()"

print("UI lifecycle guards passed: no Glide/bind work runs for a destroyed activity")
