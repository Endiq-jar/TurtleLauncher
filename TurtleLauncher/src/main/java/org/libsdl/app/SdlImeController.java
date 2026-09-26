/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package org.libsdl.app;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_NORMAL;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import com.endiq.turtlelauncher.game.sdl.SdlBridge;
import com.endiq.turtlelauncher.ui.control.input.TouchCharInput;

/**
 * Explicit controller of the SDL soft keyboard
 */
final class SdlImeController {
    enum Source { GAME, LAUNCHER, BACK }

    private static final String TAG = "SDLImeController";
    private static final int HEIGHT_PADDING = 15;

    private static SDLDummyEdit mEdit;
    private static boolean mTextInputActive;
    private static boolean mKeyboardShown;
    // Explicit launcher-invoked IME standing in for the native text channel when the game side is closed
    // Reference: Fold Craft Launcher (https://github.com/FCL-Team/FoldCraftLauncher/blob/cacd666292d9646ceb622c39b82123d8671e5b41/FCL/src/main/java/org/libsdl/app/SdlImeController.java)
    private static boolean mForcedByLauncher;

    private SdlImeController() {
    }

    static boolean isTextInputActive() {
        return mTextInputActive;
    }

    /** Whether the text channel accepts input (incl. the launcher-substituted one) */
    static boolean isInputAccepted() {
        return mTextInputActive || mForcedByLauncher;
    }

    static boolean isEditAvailable() {
        return mEdit != null;
    }

    static boolean isKeyboardShown() {
        return mKeyboardShown;
    }

    static void reset() {
        if (mEdit != null) {
            ViewParent parent = mEdit.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mEdit);
            }
            mEdit = null;
        }
        mTextInputActive = false;
        mKeyboardShown = false;
        mForcedByLauncher = false;
    }

    static void requestShow(Source source) {
        requestShow(source, TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_NORMAL, -1, -1, -1, -1);
    }

    static boolean requestShow(Source source, int inputType, int x, int y, int w, int h) {
        Log.i(TAG, "IME: show requested by " + source);
        if (source == Source.GAME) {
            mTextInputActive = true;
        }
        return post(() -> doShow(source, inputType, x, y, w, h));
    }

    static void requestHide(Source source) {
        Log.i(TAG, "IME: hide requested by " + source);
        if (source == Source.GAME) {
            mTextInputActive = false;
        }
        post(() -> doHide(source));
    }

    /**
     * IME visibility reported by system insets
     * @param visible IME visibility
     */
    static void notifyVisibilityChanged(boolean visible) {
        if (!SdlBridge.getSdlEnabled()) return;
        if (visible && isUnwantedImeVisible()) {
            // When the IME pops itself after channel closure, force it back
            Log.w(TAG, "IME: unwanted visibility while text input channel is closed, forcing hide");
            forceHideIme();
            return;
        }
        if (mKeyboardShown == visible) {
            return;
        }
        mKeyboardShown = visible;
        Log.i(TAG, "IME: visibility changed to " + (visible ? "shown" : "hidden"));
        if (visible) {
            SDLActivity.onNativeScreenKeyboardShown();
        } else {
            SDLActivity.onNativeScreenKeyboardHidden();
        }
    }

    private static boolean post(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
            return true;
        }
        return SDLActivity.commandHandler.post(task);
    }

    private static void doShow(Source source, int inputType, int x, int y, int w, int h) {
        if (SDLActivity.mLayout == null) {
            Log.w(TAG, "IME: no layout available, show by " + source + " ignored");
            return;
        }

        if (source == Source.GAME) {
            TouchCharInput.disableActiveInput();
        } else if (!mTextInputActive && !mForcedByLauncher) {
            // When the game's text channel is closed (mod-drawn input UIs close it themselves),
            // the launcher-invoked IME must activate the native channel, or typed text never reaches the game
            if (!SdlBridge.setNativeTextInputActive(true)) {
                Log.w(TAG, "IME: show by " + source + " rejected, native text input unavailable");
                return;
            }
            mForcedByLauncher = true;
            Log.i(TAG, "IME: native text input force-activated by " + source);
        }

        // Closing the auto-pop delays the edit view, but focusing the hidden editor wakes the soft keyboard on later hardware keys
        boolean autoShow = source != Source.GAME || SdlBridge.getSdlImeAutoShowEnabled();
        if (!autoShow && mEdit == null) {
            Log.i(TAG, "IME: auto show suppressed by launcher setting, editor deferred");
            return;
        }

        if (mEdit == null) {
            mEdit = new SDLDummyEdit(SDLActivity.getContext());
            SDLActivity.mLayout.addView(mEdit, makeParams(x, y, w, h));
        } else if (x >= 0 && w > 0) {
            // Update the position only with an explicitly given region, keeping the launcher's request off the game's input box
            mEdit.setLayoutParams(makeParams(x, y, w, h));
        }
        mEdit.setInputType(inputType);
        mEdit.setFocusable(true);
        mEdit.setFocusableInTouchMode(true);

        mEdit.setVisibility(View.VISIBLE);
        if (!mEdit.hasFocus()) {
            mEdit.requestFocus();
        }
        SdlBridge.requestComposeFocus();

        if (mKeyboardShown) {
            Log.i(TAG, "IME: already visible, show by " + source + " ignored");
            return;
        }
        if (!autoShow) {
            Log.i(TAG, "IME: auto show suppressed by launcher setting");
            return;
        }

        InputMethodManager imm = (InputMethodManager) SDLActivity.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mEdit, 0);
        if (imm.isAcceptingText()) {
            mKeyboardShown = true;
            Log.i(TAG, "IME: shown by " + source);
            SDLActivity.onNativeScreenKeyboardShown();
        }
    }

    private static FrameLayout.LayoutParams makeParams(int x, int y, int w, int h) {
        if (x < 0 || w <= 0) {
            x = 0;
            y = 0;
            w = 1;
            h = 1;
        }
        if (h + HEIGHT_PADDING <= 0) {
            h = 1 - HEIGHT_PADDING;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(w, h + HEIGHT_PADDING);
        params.leftMargin = x;
        params.topMargin = y;
        return params;
    }

    private static void doHide(Source source) {
        if (mForcedByLauncher && source != Source.GAME) {
            // Restore the launcher-substituted native channel (no-op when the game closed it itself)
            SdlBridge.setNativeTextInputActive(false);
        }
        mForcedByLauncher = false;

        if (mEdit == null) {
            Log.i(TAG, "IME: no text edit available, hide ignored");
            SdlBridge.requestComposeFocus();
            return;
        }
        forceHideIme();
        if (mKeyboardShown) {
            mKeyboardShown = false;
            Log.i(TAG, "IME: hidden");
            SDLActivity.onNativeScreenKeyboardHidden();
        }

        if (!isInputAccepted()) {
            // Some IMEs bounce back after hiding: add one suppression when the channel closes
            SDLActivity.commandHandler.postDelayed(SdlImeController::recheckHidden, 300);
        }
    }

    private static void forceHideIme() {
        if (mEdit != null) {
            InputMethodManager imm = (InputMethodManager) SDLActivity.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (mEdit.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(mEdit.getWindowToken(), 0);
            }
            ViewParent parent = mEdit.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mEdit);
            }
            mEdit = null;
            Log.i(TAG, "IME: text edit removed from view tree");
        }
        ViewGroup layout = SDLActivity.mLayout;
        if (layout != null && layout.getWindowToken() != null) {
            //Window-level fallback: push back stubborn IMEs that bounce despite removal
            InputMethodManager imm = (InputMethodManager) SDLActivity.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(layout.getWindowToken(), 0);
        }
        SdlBridge.requestComposeFocus();
    }

    private static boolean isUnwantedImeVisible() {
        if (!SdlBridge.getSdlEnabled() || isInputAccepted()) {
            return false;
        }
        return mEdit != null && mEdit.hasFocus();
    }

    private static void recheckHidden() {
        if (mTextInputActive || mKeyboardShown) {
            return;
        }
        if (!isUnwantedImeVisible()) {
            return;
        }
        Log.i(TAG, "IME: re-hide to suppress stubborn IME");
        forceHideIme();
    }
}