// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** A Whisper-looking file: the ggml magic, a plausible vocabulary size, then [size] bytes of filler. */
fun fakeModelBytes(size: Int, seed: Int = 1): ByteArray {
    val bytes = ByteArray(size) { ((it * 31 + seed) and 0xff).toByte() }
    byteArrayOf(0x6c, 0x6d, 0x67, 0x67).copyInto(bytes, 0)
    // n_vocab = 51865, little endian
    bytes[4] = 0x99.toByte()
    bytes[5] = 0xca.toByte()
    bytes[6] = 0
    bytes[7] = 0
    return bytes
}

fun sha256Of(bytes: ByteArray): String =
    Checksums.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

fun specFor(bytes: ByteArray, id: String = "base", file: String = "ggml-$id-q5_1.bin") = ModelSpec(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    file = file,
    bytes = bytes.size.toLong(),
    sha256 = sha256Of(bytes),
    url = "https://example.test/$file",
    license = "MIT"
)

fun catalogOf(vararg specs: ModelSpec) =
    ModelCatalog(ModelCatalog.SCHEMA, CatalogOrigin("https://example.test", "abc"), specs.toList())

fun storeIn(dir: File, catalog: ModelCatalog) = ModelStore(dir, catalog)

/** A scripted server: serves [content] and honours ranges unless told not to. */
class FakeHttp(
    private val content: ByteArray,
    private val honourRange: Boolean = true,
    /** If set, the body breaks off with an IOException after this many bytes (relative to the body). */
    var failAfter: Int? = null,
    var status: Int? = null
) : HttpSource {
    val requestedRanges = mutableListOf<Long>()

    override fun open(url: String, rangeStart: Long): HttpReply {
        requestedRanges += rangeStart
        status?.let { return HttpReply(it, null, null) }
        val start = if (honourRange) rangeStart.toInt() else 0
        if (start > content.size) return HttpReply(416, null, null)
        val slice = content.copyOfRange(start, content.size)
        val body: InputStream = FailingStream(slice, failAfter)
        val partial = honourRange && rangeStart > 0
        return HttpReply(if (partial) 206 else 200, if (partial) rangeStart else null, body)
    }
}

class FailingStream(bytes: ByteArray, private val failAfter: Int?) : InputStream() {
    private val inner = ByteArrayInputStream(bytes)
    private var served = 0

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val limit = failAfter
        if (limit != null && served >= limit) throw IOException("connection reset")
        val max = if (limit != null) minOf(len, limit - served) else len
        val read = inner.read(b, off, max)
        if (read > 0) served += read
        return read
    }
}
