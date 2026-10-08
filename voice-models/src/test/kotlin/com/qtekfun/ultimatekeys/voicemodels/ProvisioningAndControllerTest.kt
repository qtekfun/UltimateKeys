// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.qtekfun.ultimatekeys.voicemodels

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ProvisioningAndControllerTest {
    @TempDir
    lateinit var root: File

    private val baseBytes = fakeModelBytes(4096)
    private val smallBytes = fakeModelBytes(8192, seed = 5)
    private val base = specFor(baseBytes, "base")
    private val small = specFor(smallBytes, "small")
    private val catalog = catalogOf(base, small)
    private val store by lazy { storeIn(root, catalog) }

    // --- bundled install ---

    @Test
    fun `the bundled model is copied once and a deleted one stays deleted`() {
        val opened = mutableListOf<String>()
        val installer = BundledModelInstaller(store) { path ->
            opened += path
            ByteArrayInputStream(baseBytes)
        }
        assertTrue(installer.installOnce(base, "models/base.ggml"))
        assertTrue(store.isInstalled(base))
        assertFalse(installer.installOnce(base, "models/base.ggml"))
        store.installed().forEach(store::delete)
        assertFalse(installer.installOnce(base, "models/base.ggml"))
        assertFalse(store.isInstalled(base))
        assertEquals(1, opened.size)

        var progress = 0L
        installer.install(base, "models/base.ggml") { progress = it }
        assertTrue(store.isInstalled(base))
        assertEquals(baseBytes.size.toLong(), progress)
    }

    @Test
    fun `a corrupt bundle installs nothing and leaves no marker`() {
        val installer = BundledModelInstaller(store) { ByteArrayInputStream(baseBytes.copyOf(100)) }
        try {
            installer.installOnce(base, "x")
        } catch (_: ChecksumMismatchException) {
            // expected
        }
        assertFalse(store.isInstalled(base))
        assertFalse(File(store.directory, ".bundled-base").exists())
    }

    @Test
    fun `an installed model is not copied from the bundle`() {
        store.install(base, ByteArrayInputStream(baseBytes))
        val installer = BundledModelInstaller(store) { error("must not open") }
        assertFalse(installer.installOnce(base, "x"))
    }

    // --- selected model ---

    @Test
    fun `the selected model wins and the first installed is the fallback`() {
        var wanted = "small"
        val source = SelectedModelSource(store) { wanted }
        assertNull(source.currentModel())
        store.install(base, ByteArrayInputStream(baseBytes))
        assertEquals(store.fileFor(base), source.currentModel())
        store.install(small, ByteArrayInputStream(smallBytes))
        assertEquals(store.fileFor(small), source.currentModel())
        wanted = "base"
        assertEquals(store.fileFor(base), source.currentModel())
        wanted = "gone"
        assertEquals(store.fileFor(base), source.currentModel())
    }

    // --- listing ---

    @Test
    fun `listing shows catalog models, imported ones and the active choice`() {
        store.install(base, ByteArrayInputStream(baseBytes))
        File(store.directory, "mine.bin").writeBytes(ByteArray(3))
        val provisioner = FakeProvisioner(canProvide = setOf("small"))
        val downloads = mapOf("small" to DownloadState.Queued, "base" to DownloadState.Queued)
        val rows = ModelListing.rows(catalog, store.installed(), downloads, "mine", provisioner)
        assertEquals(listOf("base", "small", "mine"), rows.map { it.id })
        val (b, s, m) = rows
        assertTrue(b.installed && !b.active && b.download == DownloadState.Idle && !b.canProvide)
        assertTrue(!s.installed && s.canProvide && s.download == DownloadState.Queued)
        assertEquals(small.bytes, s.bytes)
        assertTrue(m.installed && m.active && m.spec == null && m.name == "mine.bin")
        val fallback = ModelListing.rows(
            catalog,
            store.installed(),
            emptyMap(),
            "small",
            provisioner
        )
        assertTrue(fallback.first { it.id == "base" }.active)
        assertTrue(
            ModelListing.rows(catalog, emptyList(), emptyMap(), "", provisioner).none {
                it.active
            }
        )
    }

    // --- controller ---

    /** Runs everything due now, including work of `backgroundScope` that `advanceUntilIdle` skips. */
    private fun TestScope.settle() = repeat(5) { runCurrent() }

    private class FakeProvisioner(
        val canProvide: Set<String> = emptySet(),
        override val usesNetwork: Boolean = true
    ) : ModelProvisioner {
        val board = ProgressBoard()
        val started = mutableListOf<Pair<String, Boolean>>()
        val cancelled = mutableListOf<String>()
        override val states: Flow<Map<String, DownloadState>> = board.states

        override fun canProvide(spec: ModelSpec) = spec.id in canProvide

        override fun start(spec: ModelSpec, wifiOnly: Boolean) {
            started += spec.id to wifiOnly
        }

        override fun cancel(spec: ModelSpec) {
            cancelled += spec.id
        }
    }

    private class Harness(
        val controller: ModelsController,
        val provisioner: FakeProvisioner,
        val selected: MutableStateFlow<String>,
        val wifi: MutableStateFlow<Boolean>
    )

    private fun TestScope.harness(
        provisioner: FakeProvisioner = FakeProvisioner(setOf("base", "small"))
    ): Harness {
        val selected = MutableStateFlow("")
        val wifi = MutableStateFlow(true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = ModelsController(
            scope = backgroundScope,
            catalog = catalog,
            store = store,
            importer = ModelImporter(store, catalog, minBytes = 1000),
            provisioner = provisioner,
            selectedId = selected,
            wifiOnly = wifi,
            setSelected = { selected.value = it },
            io = dispatcher
        )
        return Harness(controller, provisioner, selected, wifi)
    }

    @Test
    fun `the controller lists, selects, deletes and starts downloads`() = runTest {
        store.install(base, ByteArrayInputStream(baseBytes))
        val h = harness()
        settle()
        var state = h.controller.state.value
        assertEquals(listOf("base", "small"), state.rows.map { it.id })
        assertTrue(state.usesNetwork && state.wifiOnly)
        assertTrue(state.rows[0].installed && state.rows[0].active)

        h.controller.download("small", wifiOnly = false)
        h.controller.download("nope", wifiOnly = true)
        h.controller.download("base", wifiOnly = true) // supported by the fake, listed as installed
        assertEquals(listOf("small" to false, "base" to true), h.provisioner.started)
        h.provisioner.board.set("small", DownloadState.Downloading(10, 100))
        settle()
        state = h.controller.state.value
        assertEquals(DownloadState.Downloading(10, 100), state.rows[1].download)

        h.controller.cancel("small")
        h.controller.cancel("nope")
        assertEquals(listOf("small"), h.provisioner.cancelled)

        h.wifi.value = false
        h.controller.select("small")
        settle()
        assertEquals("small", h.selected.value)
        assertFalse(h.controller.state.value.wifiOnly)

        h.controller.delete("base")
        h.controller.delete("missing")
        settle()
        assertFalse(store.isInstalled(base))
        assertFalse(h.controller.state.value.rows[0].installed)
    }

    @Test
    fun `downloads are refused when the flavor cannot provide the model`() = runTest {
        val h = harness(FakeProvisioner(canProvide = emptySet(), usesNetwork = false))
        h.controller.download("base", true)
        assertTrue(h.provisioner.started.isEmpty())
        settle()
        assertFalse(h.controller.state.value.usesNetwork)
        assertEquals(DownloadState.Idle, h.controller.state.value.rows[0].download)
    }

    @Test
    fun `importing a catalog model installs it without asking`() = runTest {
        val h = harness()
        h.controller.import { ByteArrayInputStream(baseBytes) }
        settle()
        assertEquals(
            ImportUiState.Imported("base", wasRecognized = true),
            h.controller.state.value.import
        )
        assertTrue(store.isInstalled(base))
        h.controller.dismissImport()
        settle()
        assertEquals(ImportUiState.Idle, h.controller.state.value.import)
    }

    @Test
    fun `an unknown model needs a confirmation and can be cancelled`() = runTest {
        val h = harness()
        val other = fakeModelBytes(5000, seed = 11)
        h.controller.import { ByteArrayInputStream(other) }
        settle()
        val ask = h.controller.state.value.import as ImportUiState.ConfirmUnknown
        assertEquals(sha256Of(other), ask.sha256)
        assertEquals(5000L, ask.bytes)
        assertTrue(store.installed().isEmpty())

        h.controller.dismissImport()
        settle()
        assertTrue(store.installed().isEmpty())
        assertEquals(ImportUiState.Idle, h.controller.state.value.import)

        h.controller.import { ByteArrayInputStream(other) }
        settle()
        h.controller.confirmImport()
        settle()
        val done = h.controller.state.value.import as ImportUiState.Imported
        assertFalse(done.wasRecognized)
        assertEquals(1, store.installed().size)
        assertEquals(done.modelId, store.installed().single().id)
        h.controller.confirmImport() // nothing pending any more
        settle()
    }

    @Test
    fun `a second pick replaces a pending unknown file`() = runTest {
        val h = harness()
        h.controller.import { ByteArrayInputStream(fakeModelBytes(5000, seed = 1)) }
        settle()
        h.controller.import { ByteArrayInputStream(fakeModelBytes(6000, seed = 2)) }
        settle()
        assertEquals(6000L, (h.controller.state.value.import as ImportUiState.ConfirmUnknown).bytes)
    }

    @Test
    fun `bad files and unreadable picks report why`() = runTest {
        val h = harness()
        h.controller.import { ByteArrayInputStream(ByteArray(5000) { 3 }) }
        settle()
        assertEquals(
            ImportUiState.Rejected(ImportRejection.NOT_GGML),
            h.controller.state.value.import
        )

        h.controller.import { null }
        settle()
        assertEquals(ImportUiState.ReadFailed, h.controller.state.value.import)

        h.controller.import {
            object : InputStream() {
                override fun read(): Int = throw IOException("gone")
            }
        }
        settle()
        assertEquals(ImportUiState.ReadFailed, h.controller.state.value.import)
        assertTrue(store.installed().isEmpty())
    }

    @Test
    fun `the no-op provisioner offers nothing`() {
        assertFalse(NoProvisioner.canProvide(base))
        NoProvisioner.start(base, true)
        NoProvisioner.cancel(base)
        assertFalse(NoProvisioner.usesNetwork)
        val board = ProgressBoard()
        board.set("a", DownloadState.Queued)
        assertEquals(mapOf("a" to DownloadState.Queued), board.states.value)
        board.clear("a")
        assertTrue(board.states.value.isEmpty())
    }
}
