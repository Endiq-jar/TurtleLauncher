/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface

/**
 * Freedreno (Adreno) tối ưu hóa:
 * - Bật glthread để tách việc gọi GL khỏi thread render.
 * - Bỏ kiểm tra lỗi GL (MESA_NO_ERROR) để giảm overhead CPU.
 * - Tăng cache shader để giảm stutter khi load lại.
 * - Tắt vsync ở tầng Mesa để tránh bị khóa FPS.
 *
 * Lưu ý: nếu GPU không hỗ trợ GL 4.6, hạ MESA_GL_VERSION_OVERRIDE xuống "4.3" / "430".
 */
object OptimizedFreedrenoRenderer : RendererInterface {
    override fun getRendererId(): String = "gallium_freedreno_opt"

    override fun getUniqueIdentifier(): String = "b3f1c9e2-7a4d-4e8b-9c61-2d5f0a8e7b13"

    override fun getRendererName(): String = "Freedreno Optimized (Adreno)"

    private val env: Map<String, String> = mapOf(
        "GALLIUM_DRIVER" to "freedreno",
        "MESA_GL_VERSION_OVERRIDE" to "4.6",
        "MESA_GLSL_VERSION_OVERRIDE" to "460",
        "mesa_glthread" to "true",
        "MESA_NO_ERROR" to "1",
        "MESA_SHADER_CACHE_MAX_SIZE" to "512M",
        "vblank_mode" to "0",
        "allow_glsl_extension_directive_midshader" to "true"
    )

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { env }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}
