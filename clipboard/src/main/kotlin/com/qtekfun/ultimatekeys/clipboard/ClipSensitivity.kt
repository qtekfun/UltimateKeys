// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

/**
 * Android 13+ lets an app flag a clip as sensitive (a password manager, for example) with the
 * `ClipDescription.EXTRA_IS_SENSITIVE` extra. Those clips are never stored (SPEC section 9).
 */
object ClipSensitivity {
    /** The value of `ClipDescription.EXTRA_IS_SENSITIVE`; written out so older SDKs can use it. */
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /** Whether a clip with the [flag] extra value (null when absent) must not be stored. */
    fun isSensitive(flag: Boolean?): Boolean = flag == true
}
