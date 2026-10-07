// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails if a merged manifest declares network permissions (the `full` flavor must stay air-gapped). */
abstract class VerifyNoInternetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifests: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbidden = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        val files = manifests.files
        check(files.isNotEmpty()) { "No merged manifests to verify" }
        val offenders = files.flatMap { file ->
            val text = file.readText()
            forbidden.filter { text.contains(it) }.map { "${file.path}: $it" }
        }
        check(offenders.isEmpty()) {
            "The full flavor must not declare network permissions:\n" + offenders.joinToString("\n")
        }
    }
}
