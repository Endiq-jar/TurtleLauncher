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

package com.endiq.turtlelauncher.game.download.jvm_server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exclusive-process list matching regression:
 * :filemanager is persistently bound by the main UI with BIND_AUTO_CREATE and is always recreated by the system after being killed,
 * so if it ever entered the "must be gone" list, the pre-install wait would never be satisfied (the root cause of the stuck Forge installation).
 */
class JvmExclusiveProcessTest {

    private val main = "com.endiq.turtlelauncher.debug"

    @Test
    fun `jvm and game processes are exclusive`() {
        assertTrue(isJvmExclusiveProcess("$main:jvm", main))
        assertTrue(isJvmExclusiveProcess("$main:game", main))
    }

    @Test
    fun `filemanager and other sub processes are not exclusive`() {
        assertFalse(isJvmExclusiveProcess("$main:filemanager", main))
        assertFalse(isJvmExclusiveProcess(main, main))
        assertFalse(isJvmExclusiveProcess("$main:other", main))
    }

    @Test
    fun `foreign packages never match`() {
        //Exact match on main process name + suffix; startsWith prefix matching is not allowed
        assertFalse(isJvmExclusiveProcess("com.other.app:jvm", main))
        assertFalse(isJvmExclusiveProcess("${main}evil:jvm", main))
    }
}
