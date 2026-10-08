// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qtekfun.ultimatekeys.core.KeyboardSettings
import com.qtekfun.ultimatekeys.core.SettingsRepository
import com.qtekfun.ultimatekeys.core.settingsRepository
import com.qtekfun.ultimatekeys.models.ModelsActivity
import com.qtekfun.ultimatekeys.settings.AboutScreen
import com.qtekfun.ultimatekeys.settings.ClipboardScreen
import com.qtekfun.ultimatekeys.settings.FeedbackScreen
import com.qtekfun.ultimatekeys.settings.GesturesScreen
import com.qtekfun.ultimatekeys.settings.HomeScreen
import com.qtekfun.ultimatekeys.settings.HomeTarget
import com.qtekfun.ultimatekeys.settings.PrivacyScreen
import com.qtekfun.ultimatekeys.settings.SettingsRoute
import com.qtekfun.ultimatekeys.settings.SetupScreen
import com.qtekfun.ultimatekeys.settings.SuggestionsScreen
import com.qtekfun.ultimatekeys.settings.TypingScreen
import com.qtekfun.ultimatekeys.settings.rememberRouteStack
import com.qtekfun.ultimatekeys.styles.StylesActivity
import com.qtekfun.ultimatekeys.ui.setUkContent
import kotlinx.coroutines.launch

/**
 * The app's home: a list of settings groups, each opening a sub-screen of this activity (a small
 * back stack, see [com.qtekfun.ultimatekeys.settings.RouteStack]) or, for the style gallery, the
 * dictation settings, the user dictionary and the licenses, an activity of its own.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = settingsRepository()
        setUkContent { SettingsHost(repository) }
    }
}

@Composable
private fun SettingsHost(repository: SettingsRepository) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ImeStatus.read(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = ImeStatus.read(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val settings by repository.settings.collectAsState(initial = KeyboardSettings())
    val scope = rememberCoroutineScope()
    val update: ((KeyboardSettings) -> KeyboardSettings) -> Unit = { transform ->
        scope.launch { repository.update(transform) }
    }
    var stack by rememberRouteStack(SettingsRoute.Home, SettingsRoute.entries)
    BackHandler(enabled = stack.canGoBack) { stack = stack.back() }
    val back = { stack = stack.back() }

    when (stack.current) {
        SettingsRoute.Home -> HomeScreen(
            settings,
            status,
            remember(context) {
                AppVersion.read(context)
            },
            onOpen = { target ->
                target.route?.let { stack = stack.open(it) } ?: context.openExternal(target)
            }
        )

        SettingsRoute.Setup -> SetupScreen(
            status = status,
            onEnable = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            onSelect = {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            },
            onBack = back
        )

        SettingsRoute.Typing -> TypingScreen(settings, update, back)

        SettingsRoute.Suggestions -> SuggestionsScreen(
            settings,
            update,
            onOpenDictionary = { context.open(UserDictionaryActivity::class.java) },
            onBack = back
        )

        SettingsRoute.Gestures -> GesturesScreen(settings, update, back)

        SettingsRoute.Feedback -> FeedbackScreen(settings, update, back)

        SettingsRoute.Clipboard -> ClipboardScreen(settings, update, back)

        SettingsRoute.Privacy -> PrivacyScreen(settings, update, back)

        SettingsRoute.About -> {
            val version = remember(context) { AppVersion.read(context) }
            AboutScreen(
                version,
                onOpenLicenses = { context.open(LicensesActivity::class.java) },
                onBack = back
            )
        }
    }
}

private fun Context.open(activity: Class<*>) = startActivity(Intent(this, activity))

/** The rows of the home list that are separate activities. */
private fun Context.openExternal(target: HomeTarget) {
    when (target) {
        HomeTarget.Appearance -> open(StylesActivity::class.java)

        HomeTarget.Dictation -> startActivity(
            Intent(this, ModelsActivity::class.java).putExtra(ModelsActivity.EXTRA_SETTINGS, true)
        )

        else -> Unit
    }
}
