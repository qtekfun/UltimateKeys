// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Checks that belong to the whole repository rather than to one module. */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("originalityCheck", OriginalityCheckTask::class.java) {
            group = "verification"
            description = "Fails when our files name other keyboard products."
            projectRoot.set(target.layout.projectDirectory)
        }
        target.tasks.register("translationCheck", TranslationCheckTask::class.java) {
            group = "verification"
            description = "Fails when a default string has no Spanish translation, or the reverse."
            projectRoot.set(target.layout.projectDirectory)
        }
    }
}
