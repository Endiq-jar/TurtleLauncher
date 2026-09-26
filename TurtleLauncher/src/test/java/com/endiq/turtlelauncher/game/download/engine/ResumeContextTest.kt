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

package com.endiq.turtlelauncher.game.download.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/** Per-field validation semantics of Range resume eligibility */
class ResumeContextTest {

    private val url = URL("https://example.test/file.bin")

    private fun response(
        code: Int = 200,
        path: String = "/file.bin",
        headers: Map<String, String> = emptyMap()
    ): ResponseInfo = ResponseInfo(
        code = code,
        url = URL("https://example.test$path"),
        headers = headers.mapValues { listOf(it.value) }
    )

    private fun established(
        headers: Map<String, String> = mapOf(
            "ETag" to "\"a\"", "accept-ranges" to "bytes", "Content-Length" to "10"
        )
    ): ResumeContext = ResumeContext.of(response(headers = headers))!!

    @Test
    fun `of requires 200 with ranges identity length and a validator`() {
        assertNull(ResumeContext.of(response(code = 206)))
        assertNull(ResumeContext.of(response(headers = mapOf("ETag" to "\"a\""))))
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf(
                        "ETag" to "\"a\"", "accept-ranges" to "bytes",
                        "Content-Length" to "10", "Content-Encoding" to "gzip"
                    )
                )
            )
        )
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("ETag" to "\"a\"", "accept-ranges" to "bytes", "Content-Length" to "abc")
                )
            )
        )
        //Weak ETag and no Last-Modified: no usable validator
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("ETag" to "W/\"a\"", "accept-ranges" to "bytes", "Content-Length" to "10")
                )
            )
        )
        assertNull(
            ResumeContext.of(
                response(
                    headers = mapOf("accept-ranges" to "bytes", "Content-Length" to "10")
                )
            )
        )
        //Strong ETag + Last-Modified + all conditions: establishment succeeds
        val established = ResumeContext.of(
            response(
                headers = mapOf(
                    "ETag" to "\"a\"", "accept-ranges" to "bytes",
                    "Content-Length" to "10", "Last-Modified" to "Tue, 15 Nov 2099 12:00:00 GMT"
                )
            )
        )
        assertTrue(established != null)
    }

    @Test
    fun `weak etag falls back to last-modified validator`() {
        val context = established(
            headers = mapOf(
                "ETag" to "W/\"a\"", "accept-ranges" to "bytes",
                "Content-Length" to "10", "Last-Modified" to "Tue, 15 Nov 2099 12:00:00 GMT"
            )
        )
        assertEquals("Tue, 15 Nov 2099 12:00:00 GMT", context.ifRange())
    }

    @Test
    fun `canResume validates code encoding length etag and content range`() {
        val context = established()
        context.addBytes(4)

        //Non-206 rejected
        assertFalse(
            context.canResume(200, response(headers = partialHeaders(6)))
        )
        //Non-identity encoding rejected
        assertFalse(
            context.canResume(
                206, response(code = 206, headers = partialHeaders(6) + ("Content-Encoding" to "gzip"))
            )
        )
        //Mismatched total length rejected
        assertFalse(
            context.canResume(206, response(code = 206, headers = partialHeaders(100)))
        )
        //Inconsistent ETag rejected
        assertFalse(
            context.canResume(
                206, response(code = 206, headers = partialHeaders(6) + ("ETag" to "\"b\""))
            )
        )
        //Content-Range start ≠ received bytes rejected
        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 5-9/10"
                    )
                )
            )
        )
        //Content-Range end mismatching response length rejected
        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-7/10"
                    )
                )
            )
        )
        //Full match allowed
        assertTrue(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "ETag" to "\"a\"", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
    }

    @Test
    fun `canResume with last-modified validator requires same url`() {
        val lastModified = "Tue, 15 Nov 2099 12:00:00 GMT"
        val context = established(
            headers = mapOf(
                "Last-Modified" to lastModified, "accept-ranges" to "bytes", "Content-Length" to "10"
            )
        )
        context.addBytes(4)

        //Same URL + same Last-Modified: allowed
        assertTrue(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "Last-Modified" to lastModified, "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
        //Different URL: rejected
        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    path = "/elsewhere.bin",
                    headers = mapOf(
                        "Last-Modified" to lastModified, "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
        //Different Last-Modified: rejected
        assertFalse(
            context.canResume(
                206,
                response(
                    code = 206,
                    headers = mapOf(
                        "Last-Modified" to "Wed, 16 Nov 2099 12:00:00 GMT", "Content-Length" to "6",
                        "Content-Range" to "bytes 4-9/10"
                    )
                )
            )
        )
    }

    @Test
    fun `hasPartialContent is exclusive of both empty and complete`() {
        val context = established()
        assertFalse(context.hasPartialContent())
        context.addBytes(4)
        assertTrue(context.hasPartialContent())
        context.addBytes(6)
        assertFalse(context.hasPartialContent())
    }

    private fun partialHeaders(remaining: Long): Map<String, String> = mapOf(
        "ETag" to "\"a\"", "Content-Length" to remaining.toString(),
        "Content-Range" to "bytes 4-${4 + remaining - 1}/10"
    )
}
