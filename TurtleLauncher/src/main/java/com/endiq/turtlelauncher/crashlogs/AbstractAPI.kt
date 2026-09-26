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

package com.endiq.turtlelauncher.crashlogs

import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.utils.network.safeBodyAsJson
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class AbstractAPI(
    val api: String,
    val root: String
) {
    /**
     * Uploads a log to the target server and generates a link
     * @return the generated link
     */
    @Throws(Exception::class)
    suspend fun onUpload(content: String): MCLogsResponse {
        return withContext(Dispatchers.IO) {
            val response = GLOBAL_CLIENT.post(urlString = api) {
                setBody(
                    body = FormDataContent(
                        formData = Parameters.build {
                            append("content", content)
                        }
                    )
                )
            }

            response.safeBodyAsJson<MCLogsResponse>()
        }
    }
}