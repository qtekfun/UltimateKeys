// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.Context
import android.content.pm.PackageManager

/** The installed app's version, as shown on the home screen and useful in bug reports. */
data class AppVersion(val name: String, val code: Long) {
    companion object {
        const val UNKNOWN = "?"

        /** Builds the value from what the package manager reports, tolerating missing data. */
        fun of(versionName: String?, versionCode: Long): AppVersion = AppVersion(
            versionName?.takeIf {
                it.isNotBlank()
            } ?: UNKNOWN,
            versionCode.coerceAtLeast(0L)
        )

        fun read(context: Context): AppVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            of(info.versionName, info.longVersionCode)
        } catch (_: PackageManager.NameNotFoundException) {
            of(null, 0L)
        }
    }
}
