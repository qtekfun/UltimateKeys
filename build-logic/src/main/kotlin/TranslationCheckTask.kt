// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** Fails when a default string has no Spanish translation (or the reverse); see [TranslationScanner]. */
abstract class TranslationCheckTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun check() {
        val problems = TranslationScanner.scan(projectRoot.get().asFile)
        check(problems.isEmpty()) {
            "String resources and their translations disagree (values vs values-es):\n" +
                problems.joinToString("\n") { "${it.module} [${it.language}] ${it.name}: ${it.message}" }
        }
    }
}
