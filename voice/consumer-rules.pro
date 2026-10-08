# SPDX-FileCopyrightText: 2026 UltimateKeys contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# The JNI functions in libukwhisper.so are bound by name to this class.
-keepclasseswithmembers class com.qtekfun.ultimatekeys.voice.NativeWhisper {
    native <methods>;
}
