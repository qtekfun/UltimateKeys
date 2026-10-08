// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.qtekfun.ultimatekeys.ui.UkActionRow
import com.qtekfun.ultimatekeys.ui.UkChevron
import com.qtekfun.ultimatekeys.ui.UkRow
import com.qtekfun.ultimatekeys.ui.UkScreen
import com.qtekfun.ultimatekeys.ui.UkSpacing
import com.qtekfun.ultimatekeys.ui.UkTheme
import com.qtekfun.ultimatekeys.ui.group
import com.qtekfun.ultimatekeys.ui.note
import com.qtekfun.ultimatekeys.ui.setUkContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists every open-source component the app contains, with its licence and the full licence text. */
class LicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUkContent { LicensesScreen(onClose = ::finish) }
    }
}

private fun loadCatalog(context: Context): LicenseCatalog =
    LicenseCatalog.parse(readAsset(context, LicenseCatalog.ASSET))

private fun readAsset(context: Context, path: String): String =
    context.assets.open(path).bufferedReader().use { it.readText() }

@Composable
private fun LicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { loadCatalog(context) }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val component = catalog.components.firstOrNull { it.name == shown }
    BackHandler(enabled = component != null) { shown = null }
    if (component == null) {
        ComponentList(catalog, onOpen = { shown = it.name }, onClose = onClose)
    } else {
        LicenseText(catalog, component, onBack = { shown = null })
    }
}

@Composable
private fun ComponentList(
    catalog: LicenseCatalog,
    onOpen: (LicenseComponent) -> Unit,
    onClose: () -> Unit
) {
    val back = stringResource(R.string.nav_back)
    val intro = stringResource(R.string.licenses_intro)
    val viewLabel = stringResource(R.string.licenses_view_text)
    val titles = catalog.grouped().associate { (category, _) ->
        category to categoryTitle(category)
    }
    UkScreen(
        title = stringResource(R.string.licenses_title),
        onBack = onClose,
        backText = back,
        backDescription = back
    ) {
        note(intro, key = "intro")
        catalog.grouped().forEach { (category, components) ->
            group(key = "category-$category", header = titles[category]) {
                components.forEach { component ->
                    row {
                        val licenseName =
                            catalog.license(component.license)?.name ?: component.license
                        UkRow(
                            title = component.name,
                            subtitle = "${component.use}\n$licenseName\n${component.copyright}",
                            onClick = { onOpen(component) },
                            onClickLabel = viewLabel,
                            trailing = { UkChevron() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseText(catalog: LicenseCatalog, component: LicenseComponent, onBack: () -> Unit) {
    val context = LocalContext.current
    val asset = catalog.assetFor(component)
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            asset?.let { runCatching { readAsset(context, it) }.getOrNull() }.orEmpty()
        }
    }
    val back = stringResource(R.string.nav_back)
    val paragraphs = text?.split("\n\n").orEmpty()
    val loading = stringResource(R.string.licenses_loading)
    UkScreen(title = component.name, onBack = onBack, backText = back, backDescription = back) {
        note(component.copyright, key = "copyright")
        group(key = "website") {
            row {
                UkActionRow(
                    stringResource(R.string.licenses_open_website),
                    onClick = { openLink(context, component.url) }
                )
            }
        }
        if (text == null) note(loading, key = "loading")
        // The text is a few hundred paragraphs: lazy items keep a long licence cheap to show.
        items(paragraphs.size, key = { "paragraph-$it" }) { index ->
            Text(
                paragraphs[index],
                modifier = Modifier.padding(horizontal = UkSpacing.md * 2, vertical = UkSpacing.xs),
                color = UkTheme.colors.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun categoryTitle(category: String): String = stringResource(
    when (category) {
        "app" -> R.string.licenses_cat_app
        "code" -> R.string.licenses_cat_code
        "data" -> R.string.licenses_cat_data
        "fonts" -> R.string.licenses_cat_fonts
        "icons" -> R.string.licenses_cat_icons
        "libraries" -> R.string.licenses_cat_libraries
        else -> R.string.licenses_cat_build
    }
)

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.licenses_no_browser, Toast.LENGTH_SHORT).show()
    }
}
