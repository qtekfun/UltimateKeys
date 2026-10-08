// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpUrlConnectionSourceTest {
    private val content = ByteArray(10_000) { (it % 251).toByte() }
    private lateinit var server: HttpServer
    private val seenRanges = mutableListOf<String?>()
    private val seenEncodings = mutableListOf<String?>()
    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val source = HttpUrlConnectionSource(allowInsecure = true)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/file") { exchange ->
            seenRanges += exchange.requestHeaders.getFirst("Range")
            seenEncodings += exchange.requestHeaders.getFirst("Accept-Encoding")
            serve(exchange)
        }
        server.createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", "/file")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/loop") { exchange ->
            exchange.responseHeaders.add("Location", "/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/nowhere") { exchange ->
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun serve(exchange: HttpExchange) {
        val range = exchange.requestHeaders.getFirst("Range")
        if (range == null) {
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
            return
        }
        val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
        if (start >= content.size) {
            exchange.sendResponseHeaders(416, -1)
            exchange.close()
            return
        }
        exchange.responseHeaders.add(
            "Content-Range",
            "bytes $start-${content.size - 1}/${content.size}"
        )
        exchange.sendResponseHeaders(206, (content.size - start).toLong())
        exchange.responseBody.use { it.write(content, start, content.size - start) }
    }

    @Test
    fun `a plain get returns the whole body`() {
        source.open("$base/file", 0).use { reply ->
            assertEquals(200, reply.status)
            assertNull(reply.rangeStart)
            assertArrayEquals(content, reply.body!!.readBytes())
        }
        assertEquals(listOf<String?>(null), seenRanges)
        assertEquals(listOf<String?>("identity"), seenEncodings)
    }

    @Test
    fun `a ranged get resumes at the requested byte`() {
        source.open("$base/file", 4_000).use { reply ->
            assertEquals(206, reply.status)
            assertEquals(4_000L, reply.rangeStart)
            assertArrayEquals(content.copyOfRange(4_000, content.size), reply.body!!.readBytes())
        }
        assertEquals(listOf<String?>("bytes=4000-"), seenRanges)
    }

    @Test
    fun `a range past the end is reported and has no body`() {
        source.open("$base/file", 50_000).use { reply ->
            assertEquals(416, reply.status)
            assertNull(reply.body)
        }
        source.open("$base/missing", 0).use { assertEquals(404, it.status) }
    }

    @Test
    fun `redirects are followed with the range kept`() {
        source.open("$base/moved", 100).use { reply ->
            assertEquals(206, reply.status)
            assertEquals(100L, reply.rangeStart)
        }
        assertEquals(listOf<String?>("bytes=100-"), seenRanges)
    }

    @Test
    fun `endless or empty redirects fail`() {
        assertThrows<IOException> { source.open("$base/loop", 0) }
        assertThrows<IOException> { source.open("$base/nowhere", 0) }
    }

    @Test
    fun `plain http is refused unless the test switch is on`() {
        assertThrows<IOException> { HttpUrlConnectionSource().open("$base/file", 0) }
    }

    @Test
    fun `an unreachable server is an io error`() {
        val port = server.address.port
        server.stop(0)
        assertThrows<IOException> { source.open("http://127.0.0.1:$port/file", 0) }
    }

    @Test
    fun `content range and location parsing`() {
        assertEquals(100L, HttpUrlConnectionSource.parseRangeStart("bytes 100-999/1000"))
        assertEquals(0L, HttpUrlConnectionSource.parseRangeStart(" bytes 0-9/*"))
        assertNull(HttpUrlConnectionSource.parseRangeStart("bytes */1000"))
        assertNull(HttpUrlConnectionSource.parseRangeStart("garbage"))
        assertNull(HttpUrlConnectionSource.parseRangeStart(null))
        assertEquals(
            "https://cdn.test/a/b",
            HttpUrlConnectionSource.resolve("https://x.test/f", "https://cdn.test/a/b")
        )
        assertEquals("https://x.test/g", HttpUrlConnectionSource.resolve("https://x.test/f", "/g"))
        assertThrows<IOException> { HttpUrlConnectionSource.resolve("https://x.test/f", null) }
        assertThrows<IOException> { HttpUrlConnectionSource.resolve("https://x.test/f", " ") }
        assertThrows<IOException> {
            HttpUrlConnectionSource.resolve("https://x.test/f", "http://bad host/")
        }
    }
}
