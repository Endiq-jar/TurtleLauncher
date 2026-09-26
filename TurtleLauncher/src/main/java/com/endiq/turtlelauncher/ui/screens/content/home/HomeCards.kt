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

package com.endiq.turtlelauncher.ui.screens.content.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.endiq.cardgrid.model.CardLimits
import com.endiq.cardgrid.model.CardType
import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.components.BackgroundCard
import com.endiq.turtlelauncher.ui.screens.content.home.version.VersionCardContent

/** System cards (fixed), provided by the launcher itself and drawn outside the grid */
class SystemCard(val id: String, val content: @Composable () -> Unit)

/**
 * Home card registry
 */
object HomeCards {
    /** The version card's type id */
    const val VERSION_CARD_TYPE_ID = "version_card"

    private val versionCardType = CardType(
        typeId = VERSION_CARD_TYPE_ID,
        defaultSpan = IntOffset(10, 6),
        limits = CardLimits(
            minWidth = 10,
            minHeight = 4,
            maxHeight = 12
        ),
        content = { cardId ->
            VersionCardContent(cardId)
        }
    )

    /** Version card type */
    fun versionCardType(): CardType = versionCardType

    /** User card type registry */
    val userCardTypes: List<CardType> = listOf(versionCardType)

    /** System cards (fixed) */
    fun systemCards(): List<SystemCard> = buildList {
        if (BuildConfig.DEBUG) {
            add(debugWarningCard())
        }
    }

    /**
     * Debug-only undismissable warning, keeping test builds from posing as stable XD
     */
    private fun debugWarningCard() = SystemCard(id = "system_debug_warning") {
        BackgroundCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.generic_warning),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.launcher_version_debug_warning, BuildKeys.LAUNCHER_NAME),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    modifier = Modifier
                        .alpha(0.8f)
                        .align(Alignment.End),
                    text = stringResource(R.string.launcher_version_debug_warning_cant_close),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
