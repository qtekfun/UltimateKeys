// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimatekeys.ime.panels.PanelKind
import com.qtekfun.ultimatekeys.ime.panels.PanelPreview
import com.qtekfun.ultimatekeys.ime.panels.PanelPreviewContent
import com.qtekfun.ultimatekeys.ime.surface.KeyboardPreview
import com.qtekfun.ultimatekeys.ime.surface.PreviewContent
import com.qtekfun.ultimatekeys.ime.voice.VoicePanelColors
import com.qtekfun.ultimatekeys.ime.voice.VoicePanelContent
import com.qtekfun.ultimatekeys.style.Presets
import com.qtekfun.ultimatekeys.voice.DictationState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store screenshots: phone-sized scenes (a caption, a text field and the real keyboard or panel
 * drawn by the same renderer as the app) in English and Spanish. Unlike the goldens they are not
 * compared with anything: they are written to `build/store/<locale>/<n>_<scene>.png`, and
 * `./gradlew :screenshots:updateStoreScreenshots` copies them into `fastlane/metadata/android`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class StoreScreenshotTest(private val locale: Locale, private val scene: Scene) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun store() {
        compose.setContent { Frame(locale, scene) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("store.dir"), locale.dir).apply { mkdirs() }
        File(dir, "${scene.number}_${scene.name.lowercase()}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    enum class Locale(val dir: String, val layout: String) {
        EN("en-US", "en_qwerty"),
        ES("es-ES", "es_qwerty")
    }

    enum class Scene(
        val number: Int,
        val presetId: String,
        val dark: Boolean,
        val title: Map<Locale, String>,
        val text: Map<Locale, String>,
        val suggestions: Map<Locale, List<String>> = emptyMap(),
        val hint: Map<Locale, String> = emptyMap()
    ) {
        TYPING(
            1,
            "ultimate",
            false,
            mapOf(
                Locale.EN to "Type in two languages, no switching",
                Locale.ES to "Escribe en dos idiomas sin cambiar"
            ),
            mapOf(
                Locale.EN to "See you at the cafe, nos vemos a las",
                Locale.ES to "Nos vemos en la cafetería, see you at"
            ),
            mapOf(
                Locale.EN to listOf("seven", "7:30", "eight"),
                Locale.ES to listOf("seven", "the", "six")
            )
        ),
        STYLES(
            2,
            "soft",
            false,
            mapOf(
                Locale.EN to "Make it look like you",
                Locale.ES to "Haz que se vea como tú"
            ),
            emptyMap()
        ),
        DARK(
            3,
            "midnight",
            true,
            mapOf(
                Locale.EN to "Light and dark, in every style",
                Locale.ES to "Claro y oscuro, en cada estilo"
            ),
            mapOf(
                Locale.EN to "Good night, sleep well",
                Locale.ES to "Buenas noches, que descanses"
            ),
            mapOf(
                Locale.EN to listOf("well", "tight", "now"),
                Locale.ES to listOf("bien", "mucho", "ahora")
            )
        ),
        EMOJI(
            4,
            "soft",
            false,
            mapOf(
                Locale.EN to "Emoji with offline search",
                Locale.ES to "Emoji con búsqueda sin conexión"
            ),
            mapOf(Locale.EN to "Thanks a lot", Locale.ES to "Mil gracias")
        ),
        CLIPBOARD(
            5,
            "outline",
            true,
            mapOf(
                Locale.EN to "Clipboard history you control",
                Locale.ES to "Historial del portapapeles bajo tu control"
            ),
            mapOf(Locale.EN to "Address: ", Locale.ES to "Dirección: ")
        ),
        VOICE(
            6,
            "ultimate",
            false,
            mapOf(
                Locale.EN to "Dictate without a connection",
                Locale.ES to "Dicta sin conexión"
            ),
            mapOf(
                Locale.EN to "Remind me to call Ana tomorrow at nine",
                Locale.ES to "Recuérdame llamar a Ana mañana a las nueve"
            )
        ),
        PRIVATE(
            7,
            "midnight",
            true,
            mapOf(
                Locale.EN to "Private mode: nothing is learned",
                Locale.ES to "Modo privado: no se aprende nada"
            ),
            mapOf(Locale.EN to "Search privately", Locale.ES to "Buscar en privado"),
            mapOf(
                Locale.EN to listOf("privately", "private", "privacy"),
                Locale.ES to listOf("privado", "privacidad", "privada")
            )
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> =
            Locale.entries.flatMap { l -> Scene.entries.map { arrayOf<Any>(l, it) } }
    }
}

@Composable
private fun Frame(locale: StoreScreenshotTest.Locale, scene: StoreScreenshotTest.Scene) {
    val dark = scene.dark
    val page = if (dark) Color(0xFF14131C) else Color(0xFFF3F2F7)
    val ink = if (dark) Color(0xFFEDEAF7) else Color(0xFF1C1B22)
    val card = if (dark) Color(0xFF211F2D) else Color.White
    val line = if (dark) Color(0xFF3A3750) else Color(0xFFD5D2E2)
    Column(Modifier.fillMaxSize().background(page)) {
        Spacer(Modifier.height(36.dp))
        BasicText(
            scene.title.getValue(locale),
            Modifier.padding(horizontal = 24.dp),
            style = TextStyle(ink, 24.sp, FontWeight.Bold)
        )
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.weight(1f))
        if (scene != StoreScreenshotTest.Scene.STYLES) {
            Conversation(locale, scene, ink, card, line)
        }
        Bottom(locale, scene)
    }
}

@Composable
private fun ColumnScope.Conversation(
    locale: StoreScreenshotTest.Locale,
    scene: StoreScreenshotTest.Scene,
    ink: Color,
    card: Color,
    line: Color
) {
    val chat = if (locale == StoreScreenshotTest.Locale.EN) {
        listOf("Are we still on for tonight?", "Yes! What time works?")
    } else {
        listOf("¿Seguimos con el plan de esta noche?", "¡Sí! ¿A qué hora te viene bien?")
    }
    chat.forEachIndexed { i, message ->
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                .align(if (i == 0) Alignment.Start else Alignment.End)
                .background(if (i == 0) card else line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) { BasicText(message, style = TextStyle(ink, 16.sp)) }
    }
    Spacer(Modifier.height(12.dp))
    val text = scene.text.getValue(locale)
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .background(card, RoundedCornerShape(16.dp))
            .border(1.dp, line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        BasicText("$text|", style = TextStyle(ink, 18.sp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Bottom(locale: StoreScreenshotTest.Locale, scene: StoreScreenshotTest.Scene) {
    val dark = scene.dark
    val style = checkNotNull(Presets.byId(scene.presetId))
    val content = PreviewContent(
        layoutId = locale.layout,
        suggestions = scene.suggestions[locale] ?: listOf("hello", "hi", "hey"),
        private = scene == StoreScreenshotTest.Scene.PRIVATE
    )
    when (scene) {
        StoreScreenshotTest.Scene.STYLES -> Gallery(locale)

        StoreScreenshotTest.Scene.EMOJI ->
            PanelPreview(style, dark, PanelPreviewContent(PanelKind.EMOJI))

        StoreScreenshotTest.Scene.CLIPBOARD ->
            PanelPreview(style, dark, PanelPreviewContent(PanelKind.CLIPBOARD))

        StoreScreenshotTest.Scene.VOICE -> Box(Modifier.fillMaxWidth().height(270.dp)) {
            VoicePanelContent(
                state = DictationState.Listening(0.8f, true),
                action = null,
                isPrivate = false,
                colors = VoicePanelColors.resolve(style, dark),
                cornerDp = style.panels.cornerRadiusDp,
                level = 0.8f,
                phase = 0.25f,
                onStop = {},
                onCancel = {},
                onAction = {}
            )
        }

        else -> KeyboardPreview(style, dark, content = content)
    }
}

/** Three different looks stacked, so one image shows how far the style engine goes. */
@Composable
private fun Gallery(locale: StoreScreenshotTest.Locale) {
    val looks = listOf("soft" to false, "outline" to true, "paper" to false)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        looks.forEach { (id, dark) ->
            KeyboardPreview(
                checkNotNull(Presets.byId(id)),
                dark,
                content = PreviewContent(layoutId = locale.layout, heightPercent = 62)
            )
        }
    }
}
