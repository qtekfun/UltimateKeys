// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Feeds the system clipboard into the [ClipboardHistory]. Only plain text is read, and the
 * sensitive flag is read before anything else touches the content. Android glue, covered by the
 * emulator tests; the rules themselves live in [ClipboardHistory].
 */
class SystemClipboardWatcher(
    context: Context,
    private val history: ClipboardHistory,
    private val scope: CoroutineScope
) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onChanged() }

    fun start() = manager.addPrimaryClipChangedListener(listener)

    fun stop() = manager.removePrimaryClipChangedListener(listener)

    private fun onChanged() {
        val clip = manager.primaryClip ?: return
        val sensitive = ClipSensitivity.isSensitive(
            clip.description.extras?.getBoolean(ClipSensitivity.EXTRA_IS_SENSITIVE)
        )
        val text = if (sensitive) null else clip.firstText()
        scope.launch { history.onClip(text, sensitive) }
    }

    private fun ClipData.firstText(): CharSequence? = if (itemCount > 0) getItemAt(0).text else null
}
