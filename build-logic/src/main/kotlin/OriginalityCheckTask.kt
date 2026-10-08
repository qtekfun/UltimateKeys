// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** Fails when our files name other keyboard products; see [OriginalityScanner]. */
abstract class OriginalityCheckTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    // Always runs: it is cheap and its inputs (the whole tree) are not worth declaring.
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun check() {
        val hits = OriginalityScanner.scan(projectRoot.get().asFile)
        check(hits.isEmpty()) {
            "Other keyboard products must not be named (CLAUDE.md, Originality):\n" +
                hits.joinToString("\n") { "${it.path}:${it.line}: ${it.term}" }
        }
    }
}
