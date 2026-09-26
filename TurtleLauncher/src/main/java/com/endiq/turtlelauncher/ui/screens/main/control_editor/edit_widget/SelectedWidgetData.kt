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

package com.endiq.turtlelauncher.ui.screens.main.control_editor.edit_widget

import com.endiq.layer_controller.observable.ObservableControlLayer
import com.endiq.layer_controller.observable.ObservableWidget

/**
 * The component being edited in the edit dialog
 * @param data the component to edit
 * @param layer the widget layer the component belongs to
 */
data class SelectedWidgetData(
    val data: ObservableWidget,
    val layer: ObservableControlLayer
)
