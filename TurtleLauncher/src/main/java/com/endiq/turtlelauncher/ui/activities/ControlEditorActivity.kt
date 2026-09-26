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
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.endiq.layer_controller.layout.ControlLayout
import com.endiq.layer_controller.layout.loadLayoutFromFile
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.base.BaseAppCompatActivity
import com.endiq.turtlelauncher.ui.screens.content.elements.Background
import com.endiq.turtlelauncher.ui.screens.main.control_editor.ControlEditor
import com.endiq.turtlelauncher.ui.theme.TurtleLauncherTheme
import com.endiq.turtlelauncher.ui.theme.backgroundColor
import com.endiq.turtlelauncher.ui.theme.onBackgroundColor
import com.endiq.turtlelauncher.viewmodel.BackgroundViewModel
import com.endiq.turtlelauncher.viewmodel.EditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private const val BUNDLE_CONTROL = "BUNDLE_CONTROL"

@AndroidEntryPoint
class ControlEditorActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = AllSettings.gameFullScreen.getValue()

    override fun getTaskDescriptionTitle(): String = getString(R.string.control_manage_info_edit)

    /** The editor */
    private val editorViewModel: EditorViewModel by viewModels()

    /**
     * Launcher background content management ViewModel
     */
    private val backgroundViewModel: BackgroundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** The control layout's absolute path */
        val controlPath: String = intent.extras?.getString(BUNDLE_CONTROL) ?: return runFinish()
        /** The control layout file */
        val controlFile: File = File(controlPath).takeIf { it.isFile && it.exists() } ?: return runFinish()
        /** The control layout */
        val layout: ControlLayout = runCatching {
            loadLayoutFromFile(controlFile)
        }.getOrNull() ?: return runFinish()

        //Initialize the control layout
        editorViewModel.initLayout(layout)

        //Bind the back key: exiting directly would make the control layout lose all changes
        //Prompt the user to save and exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                editorViewModel.onBackPressed(context = this@ControlEditorActivity) {
                    this@ControlEditorActivity.finish()
                }
            }
        })

        setContent {
            TurtleLauncherTheme(
                backgroundViewModel = backgroundViewModel
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Background(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = backgroundViewModel,
                            allowVideo = false
                        )

                        ControlEditor(
                            viewModel = editorViewModel,
                            targetFile = controlFile,
                            exit = {
                                //Exit after the control layout has been saved
                                finish()
                            },
                            menuExit = {
                                //A menu-requested direct exit opens a confirmation dialog
                                editorViewModel.showExitEditorDialog(
                                    context = this@ControlEditorActivity,
                                    onExit = {
                                        this@ControlEditorActivity.finish()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens the control layout editor
 */
fun startEditorActivity(context: Context, file: File) {
    val intent = Intent(context, ControlEditorActivity::class.java).apply {
        putExtra(BUNDLE_CONTROL, file.absolutePath)
    }
    context.startActivity(intent)
}