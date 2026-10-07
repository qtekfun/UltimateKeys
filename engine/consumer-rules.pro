# SPDX-FileCopyrightText: 2026 UltimateKeys contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# The native engine registers its methods on this class by name (RegisterNatives).
-keepclasseswithmembers class com.qtekfun.ultimatekeys.engine.NativeEngine {
    native <methods>;
}
