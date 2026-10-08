// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModelDownloaderTest {
    @TempDir
    lateinit var root: File

    private val bytes = fakeModelBytes(1_000_000)
    private val spec = specFor(bytes)
    private val store by lazy { storeIn(root, catalogOf(spec)) }

    private fun downloader(http: HttpSource, free: Long = Long.MAX_VALUE) =
        ModelDownloader(store, http) { free }

    private fun failure(outcome: DownloadOutcome) = outcome as DownloadOutcome.Failure

    @Test
    fun `a clean download goes through downloading and verifying to completed`() = runTest {
        val states = mutableListOf<DownloadState>()
        val outcome = downloader(FakeHttp(bytes)).download(spec) { states += it }
        assertEquals(DownloadOutcome.Success, outcome)
        assertTrue(store.isInstalled(spec))
        assertFalse(store.partialFile(spec).exists())
        assertEquals(DownloadState.Downloading(0, spec.bytes), states.first())
        assertTrue(states.any { it is DownloadState.Downloading && it.bytes > 0 })
        assertEquals(listOf(DownloadState.Verifying, DownloadState.Completed), states.takeLast(2))
        assertEquals(0.5f, DownloadState.Downloading(50, 100).fraction)
        assertEquals(0f, DownloadState.Downloading(5, 0).fraction)
    }

    @Test
    fun `an installed model is not downloaded again`() = runTest {
        store.install(spec, bytes.inputStream())
        val http = FakeHttp(bytes)
        assertEquals(DownloadOutcome.Success, downloader(http).download(spec))
        assertTrue(http.requestedRanges.isEmpty())
    }

    @Test
    fun `a dropped connection keeps the partial file and the next run resumes with a range`() =
        runTest {
            val http = FakeHttp(bytes, failAfter = 300_000)
            val first = failure(downloader(http).download(spec))
            assertEquals(DownloadFailure.NETWORK, first.reason)
            assertTrue(first.retryable)
            assertEquals(300_000L, store.partialFile(spec).length())
            assertFalse(store.isInstalled(spec))

            http.failAfter = null
            assertEquals(DownloadOutcome.Success, downloader(http).download(spec))
            assertEquals(listOf(0L, 300_000L), http.requestedRanges)
            assertTrue(store.isInstalled(spec))
        }

    @Test
    fun `a server that ignores the range restarts the file from zero`() = runTest {
        store.partialFile(spec).writeBytes(bytes.copyOf(1234))
        val http = FakeHttp(bytes, honourRange = false)
        assertEquals(DownloadOutcome.Success, downloader(http).download(spec))
        assertEquals(listOf(1234L), http.requestedRanges)
        assertTrue(store.isInstalled(spec))
    }

    @Test
    fun `a partial file that is already complete is only verified`() = runTest {
        store.partialFile(spec).writeBytes(bytes)
        val http = FakeHttp(bytes)
        assertEquals(DownloadOutcome.Success, downloader(http).download(spec))
        assertTrue(http.requestedRanges.isEmpty())
    }

    @Test
    fun `a partial file larger than the model is thrown away`() = runTest {
        store.partialFile(spec).writeBytes(ByteArray(bytes.size + 5))
        val http = FakeHttp(bytes)
        assertEquals(DownloadOutcome.Success, downloader(http).download(spec))
        assertEquals(listOf(0L), http.requestedRanges)
    }

    @Test
    fun `a corrupt download fails the checksum and is deleted`() = runTest {
        val corrupt = bytes.copyOf().also { it[500] = 1 }
        val result = failure(downloader(FakeHttp(corrupt)).download(spec))
        assertEquals(DownloadFailure.CHECKSUM, result.reason)
        assertFalse(result.retryable)
        assertFalse(store.partialFile(spec).exists())
        assertFalse(store.isInstalled(spec))
    }

    @Test
    fun `a corrupt partial file is caught after resuming`() = runTest {
        store.partialFile(spec).writeBytes(ByteArray(1000) { 9 })
        val result = failure(downloader(FakeHttp(bytes)).download(spec))
        assertEquals(DownloadFailure.CHECKSUM, result.reason)
        assertFalse(store.partialFile(spec).exists())
    }

    @Test
    fun `more bytes than the model has is a checksum failure`() = runTest {
        val long = bytes + ByteArray(10)
        val result = failure(downloader(FakeHttp(long)).download(spec))
        assertEquals(DownloadFailure.CHECKSUM, result.reason)
        assertFalse(store.partialFile(spec).exists())
    }

    @Test
    fun `a body that ends early is a retryable network failure`() = runTest {
        val short = FakeHttp(bytes.copyOf(bytes.size - 10))
        val result = failure(downloader(short).download(spec))
        assertEquals(DownloadFailure.NETWORK, result.reason)
        assertTrue(result.retryable)
        assertEquals((bytes.size - 10).toLong(), store.partialFile(spec).length())
    }

    @Test
    fun `http statuses map to retryable or final failures`() = runTest {
        suspend fun run(status: Int) =
            failure(downloader(FakeHttp(bytes, status = status)).download(spec))
        listOf(500, 503, 408, 429).forEach {
            assertEquals(DownloadFailure.SERVER_BUSY, run(it).reason)
            assertTrue(run(it).retryable)
        }
        listOf(401, 403, 404, 302).forEach {
            assertEquals(DownloadFailure.SERVER_REFUSED, run(it).reason)
            assertFalse(run(it).retryable)
        }
    }

    @Test
    fun `range not satisfiable restarts once then gives up`() = runTest {
        store.partialFile(spec).writeBytes(bytes.copyOf(100))
        val always416 = FakeHttp(bytes, status = 416)
        val result = failure(downloader(always416).download(spec))
        assertEquals(DownloadFailure.SERVER_REFUSED, result.reason)
        assertEquals(2, always416.requestedRanges.size)
    }

    @Test
    fun `a partial answer that starts elsewhere restarts from zero`() = runTest {
        store.partialFile(spec).writeBytes(bytes.copyOf(100))
        var calls = 0
        val odd = HttpSource { _, start ->
            calls++
            if (start > 0) {
                HttpReply(206, 7L, bytes.inputStream())
            } else {
                HttpReply(200, null, bytes.inputStream())
            }
        }
        assertEquals(DownloadOutcome.Success, downloader(odd).download(spec))
        assertEquals(2, calls)
    }

    @Test
    fun `an unreachable network is retryable`() = runTest {
        val down = HttpSource { _, _ -> throw IOException("no route") }
        val result = failure(downloader(down).download(spec))
        assertEquals(DownloadFailure.NETWORK, result.reason)
        assertTrue(result.retryable)
    }

    @Test
    fun `not enough space fails before any request`() = runTest {
        val http = FakeHttp(bytes)
        val result = failure(downloader(http, free = 1000).download(spec))
        assertEquals(DownloadFailure.NO_SPACE, result.reason)
        assertFalse(result.retryable)
        assertTrue(http.requestedRanges.isEmpty())
    }

    @Test
    fun `a failing disk is a storage failure`() = runTest {
        // A staging directory that cannot be written to makes creating the partial file fail.
        val staging = store.partialFile(spec).parentFile!!
        assertTrue(staging.setWritable(false))
        try {
            val result = failure(downloader(FakeHttp(bytes)).download(spec))
            assertEquals(DownloadFailure.STORAGE, result.reason)
        } finally {
            staging.setWritable(true)
        }
    }

    @Test
    fun `cancelling stops the transfer and keeps the partial file`() = runTest {
        val slow = object : HttpSource {
            override fun open(url: String, rangeStart: Long) = HttpReply(
                200,
                null,
                object : InputStream() {
                    override fun read(): Int = throw UnsupportedOperationException()

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        b.fill(1, off, off + len)
                        return len
                    }
                }
            )
        }
        lateinit var job: Job
        job = launch {
            downloader(slow).download(spec) {
                if (it is DownloadState.Downloading &&
                    it.bytes > 0
                ) {
                    job.cancel()
                }
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        val kept = store.partialFile(spec).length()
        assertTrue(kept > 0 && kept < spec.bytes)
        assertFalse(store.isInstalled(spec))
    }

    @Test
    fun `the reply closes its body and its connection`() {
        var closed = 0
        val reply = HttpReply(200, null, bytes.inputStream(), onClose = { closed++ })
        reply.close()
        assertEquals(1, closed)
        HttpReply(404, null, null).close()
    }
}
