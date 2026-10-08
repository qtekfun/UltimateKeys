// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** The rules the flavors' merged manifests must follow (SPEC section 8). Pure, so it is unit tested. */
object ManifestRules {
    const val INTERNET = "android.permission.INTERNET"
    const val NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"

    /** Network permissions a `full` manifest must not declare, found in [manifest]. */
    fun forbiddenForFull(manifest: String): List<String> =
        listOf(INTERNET, NETWORK_STATE).filter { declares(manifest, it) }

    /** True when [manifest] declares the INTERNET permission (the `lite` flavor needs it for the downloader). */
    fun declaresInternet(manifest: String): Boolean = declares(manifest, INTERNET)

    // A `uses-permission` element naming the permission; mentions in comments do not count.
    private fun declares(manifest: String, permission: String): Boolean {
        val withoutComments = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        return Regex("""<uses-permission(?:-sdk-23)?\s[^>]*android:name\s*=\s*"${Regex.escape(permission)}"""")
            .containsMatchIn(withoutComments)
    }
}

/** Fails if a merged manifest declares network permissions (the `full` flavor must stay air-gapped). */
abstract class VerifyNoInternetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifests: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val files = manifests.files
        check(files.isNotEmpty()) { "No merged manifests to verify" }
        val offenders = files.flatMap { file ->
            ManifestRules.forbiddenForFull(file.readText()).map { "${file.path}: $it" }
        }
        check(offenders.isEmpty()) {
            "The full flavor must not declare network permissions:\n" + offenders.joinToString("\n")
        }
    }
}

/** Fails if a `lite` merged manifest does not declare INTERNET: the model downloader cannot work without it. */
abstract class VerifyHasInternetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifests: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val files = manifests.files
        check(files.isNotEmpty()) { "No merged manifests to verify" }
        val missing = files.filterNot { ManifestRules.declaresInternet(it.readText()) }
        check(missing.isEmpty()) {
            "The lite flavor must declare ${ManifestRules.INTERNET} for the model downloader:\n" +
                missing.joinToString("\n") { it.path }
        }
    }
}
