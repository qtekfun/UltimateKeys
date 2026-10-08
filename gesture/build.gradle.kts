// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("uk.android.library")
}

android {
    namespace = "com.qtekfun.ultimatekeys.gesture"
}

// Pure Kotlin on purpose: no Android, no other module. The keyboard surface feeds it key rectangles and
// touch points, the dictionaries feed it words, and everything is unit-testable on the JVM.
