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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.endiq.cardgrid.model.CardRect
import com.endiq.cardgrid.state.CardGridState
import com.endiq.cardgrid.state.CardSeed
import com.endiq.cardgrid.state.GridCard
import com.endiq.cardgrid.ui.CardGrid
import com.endiq.cardgrid.ui.CardGridAutoScroll
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.screens.content.elements.backgroundGlass
import com.endiq.turtlelauncher.ui.screens.content.home.version.VersionCardManager
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.onCardColor

/**
 * Home grid: system-card column plus the card-grid container,
 * handling layout seeding, persistence, and version record sync.
 */
@Composable
fun HomeGrid(
    state: CardGridState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Seed the persisted user-card layout
    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load()
        state.seed(
            types = HomeCards.userCardTypes,
            seeds = snapshot?.cards?.map { entry ->
                CardSeed(
                    id = entry.id,
                    typeId = entry.type,
                    layout = CardRect(
                        id = entry.id,
                        x = entry.x,
                        y = entry.y,
                        width = entry.width,
                        height = entry.height
                    )
                )
            } ?: emptyList(),
            storedColumns = snapshot?.columns ?: 0
        )
    }

    // Persist after layout settles
    LaunchedEffect(Unit) {
        state.onLayoutCommitted = {
            HomeGridStore.save(
                cards = state.cards,
                columns = state.geometry.columns
            )
        }
    }

    // Card-removed callback: sync version card records
    LaunchedEffect(Unit) {
        state.onCardRemoved = { cardId ->
            VersionCardManager.removeCard(cardId)
        }
    }

    // Sync with version card records: re-add when a record exists but the grid lacks the card
    LaunchedEffect(Unit) {
        VersionCardManager.cards.collect { states ->
            states.forEach { cardState ->
                if (state.cards.none { it.id == cardState.record.cardId }) {
                    state.addCard(HomeCards.versionCardType(), cardState.record.cardId)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                state.onViewportPositioned(
                    topPx = coordinates.positionInRoot().y,
                    heightPx = coordinates.size.height.toFloat()
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val systemCards = HomeCards.systemCards()
            if (!systemCards.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    systemCards.forEach { systemCard ->
                        key(systemCard.id) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp)) {
                                systemCard.content()
                            }
                        }
                    }
                }
            }

            CardGrid(
                state = state,
                containerColor = cardColor(),
                contentColor = onCardColor(),
                cardBackground = { base ->
                    base.backgroundGlass(
                        blur = AllSettings.backgroundBlur.state,
                        color = cardColor(),
                        enabled = true
                    )
                },
                adjustingBar = { modifier, card ->
                    CardToolbar(
                        modifier = modifier,
                        state = state,
                        card = card
                    )
                },
            )
        }
        CardGridAutoScroll(
            state = state,
            scrollState = scrollState
        )
    }
}


@Composable
private fun CardToolbar(
    state: CardGridState,
    card: GridCard,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(all = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(34.dp),
                onClick = { state.removeCard(card.id) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_filled),
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = stringResource(R.string.generic_delete)
                )
            }
            IconButton(
                modifier = Modifier.size(34.dp),
                onClick = { state.exitAdjusting() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.generic_done)
                )
            }
        }
    }
}