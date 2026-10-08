// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import com.qtekfun.ultimatekeys.voicemodels.HttpReply
import com.qtekfun.ultimatekeys.voicemodels.HttpSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * The only network code in the app: a ranged GET over `HttpURLConnection`, https only. Redirects
 * are followed here (not by the platform) so the range request and the https rule apply to every
 * hop. [allowInsecure] exists for tests against a local server.
 */
class HttpUrlConnectionSource(
    private val connectTimeoutMs: Int = TIMEOUT_MS,
    private val readTimeoutMs: Int = TIMEOUT_MS,
    private val allowInsecure: Boolean = false
) : HttpSource {
    override fun open(url: String, rangeStart: Long): HttpReply {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = connect(target, rangeStart)
            val status = connection.responseCode
            if (status in REDIRECTS) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                target = resolve(target, location)
            } else {
                val body = if (status in SUCCESS) connection.inputStream else null
                return HttpReply(
                    status,
                    parseRangeStart(connection.getHeaderField("Content-Range")),
                    body
                ) {
                    connection.disconnect()
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun connect(target: String, rangeStart: Long): HttpURLConnection {
        val url = URL(target)
        if (url.protocol != "https" && !(allowInsecure && url.protocol == "http")) {
            throw IOException("Refusing a non-https address")
        }
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        // A compressed body would not match the byte offsets of a range.
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (rangeStart > 0) connection.setRequestProperty("Range", "bytes=$rangeStart-")
        return connection
    }

    companion object {
        private const val TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val SUCCESS = 200..299
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-\d+/(\d+|\*)""")

        /** The first byte of a `Content-Range: bytes 100-999/1000` header; null when absent or odd. */
        fun parseRangeStart(header: String?): Long? = header?.let {
            CONTENT_RANGE.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull()
        }

        /** Resolves a `Location` header against the address it came from. */
        fun resolve(base: String, location: String?): String {
            if (location.isNullOrBlank()) throw IOException("Redirect without a location")
            return try {
                URI(base).resolve(location).toString()
            } catch (e: URISyntaxException) {
                throw IOException("Bad redirect address", e)
            } catch (e: IllegalArgumentException) {
                // URI.resolve parses the location itself and reports a bad one this way.
                throw IOException("Bad redirect address", e)
            }
        }
    }
}
