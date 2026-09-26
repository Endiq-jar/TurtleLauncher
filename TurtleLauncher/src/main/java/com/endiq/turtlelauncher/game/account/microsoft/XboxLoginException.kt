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

package com.endiq.turtlelauncher.game.account.microsoft

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.BANNED
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.BLOCKED_REGION
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.NOT_ACCEPTED_SERVICE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.REACHED_PLAYTIME_LIMIT
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.REQUIRES_PROOF_OF_AGE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.RESTRICTED
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNDERAGE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNREGISTERED
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText

/**
 * Various exceptions from Xbox login
 */
class XboxLoginException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {
        /**
         * The account is banned
         */
        BANNED,

        /**
         * The account is restricted
         */
        RESTRICTED,

        /**
         * The Xbox profile hasn't been created
         */
        UNREGISTERED,

        /**
         * Terms of service not accepted
         */
        NOT_ACCEPTED_SERVICE,

        /**
         * Login is banned in this region
         */
        BLOCKED_REGION,

        /**
         * Age proof not provided
         */
        REQUIRES_PROOF_OF_AGE,

        /**
         * Play-time limit reached
         */
        REACHED_PLAYTIME_LIMIT,

        /**
         * The account holder is a minor
         */
        UNDERAGE
    }
}

fun XboxLoginException.toLocal(): AndroidStringText {
    return androidText(
        when(status) {
            BANNED -> R.string.account_logging_xbox_banned
            RESTRICTED -> R.string.account_logging_xbox_restricted
            UNREGISTERED -> R.string.account_logging_xbox_unregistered
            NOT_ACCEPTED_SERVICE -> R.string.account_logging_xbox_not_accepted_service
            BLOCKED_REGION -> R.string.account_logging_xbox_blocked_region
            REQUIRES_PROOF_OF_AGE -> R.string.account_logging_xbox_requires_proof_of_age
            REACHED_PLAYTIME_LIMIT -> R.string.account_logging_xbox_reached_playtime_limit
            UNDERAGE -> R.string.account_logging_xbox_underage
        }
    )
}