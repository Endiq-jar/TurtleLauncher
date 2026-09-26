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

package com.endiq.turtlelauncher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.COPY_LABEL_THROWABLE_STACK
import com.endiq.turtlelauncher.ui.base.AbstractAppCompatActivity
import com.endiq.turtlelauncher.ui.theme.showThemed
import com.endiq.turtlelauncher.utils.copyText
import com.endiq.turtlelauncher.utils.getSerializableSafely
import dagger.hilt.android.AndroidEntryPoint

private const val BUNDLE_THROWABLE = "BUNDLE_THROWABLE"

/**
 * Activity for showing fatal crash info
 *
 * It shows the user an AlertDialog detailing the crash,
 *
 * and stays independent from the main launcher, so it displays properly even when the launcher itself breaks badly
 */
@AndroidEntryPoint
class FatalErrorActivity : AbstractAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras ?: return run { finish() }
        val throwable = extras.getSerializableSafely(BUNDLE_THROWABLE, Throwable::class.java) ?: return run { finish() }

        val message = getString(R.string.crash_launch_crash_message)
        val throwableStack = Log.getStackTraceString(throwable)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.crash_launch_crash_title))
            .setMessage(message + "\n\n" + throwableStack)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setNeutralButton(android.R.string.copy) { _, _ ->
                copyText(COPY_LABEL_THROWABLE_STACK, throwableStack, this)
                finish()
            }
            .setCancelable(false)
            .showThemed()
    }
}

/**
 * Shows the fatal error screen for the given throwable
 */
fun showFatalError(context: Context, throwable: Throwable) {
    val intent = Intent(context, FatalErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_THROWABLE, throwable)
    }
    context.startActivity(intent)
}