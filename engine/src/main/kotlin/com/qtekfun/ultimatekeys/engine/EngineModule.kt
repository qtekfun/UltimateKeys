// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.engine

/** Kotlin API, JNI and the vendored AOSP suggestion engine. */
object EngineModule {
    fun label(prefix: String): String = prefix + "engine"
}
