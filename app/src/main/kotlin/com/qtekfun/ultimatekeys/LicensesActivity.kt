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
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists every open-source component the app contains, with its licence and the full licence text. */
class LicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { LicensesScreen(onClose = ::finish) }
            }
        }
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
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        OutlinedButton(onClick = { if (component != null) shown = null else onClose() }) {
            Text(stringResource(R.string.style_back))
        }
        if (component == null) {
            ComponentList(catalog, onOpen = { shown = it.name })
        } else {
            LicenseText(catalog, component)
        }
    }
}

@Composable
private fun ComponentList(catalog: LicenseCatalog, onOpen: (LicenseComponent) -> Unit) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Heading(
                stringResource(R.string.licenses_title),
                MaterialTheme.typography.headlineSmall,
                Modifier.padding(top = 12.dp)
            )
        }
        item { Text(stringResource(R.string.licenses_intro)) }
        catalog.grouped().forEach { (category, components) ->
            item(key = "category-$category") {
                Heading(categoryTitle(category), Modifier.padding(top = 8.dp))
            }
            items(components, key = { it.name }) { component ->
                ComponentCard(catalog, component, onOpen = { onOpen(component) }) {
                    openLink(context, component.url)
                }
            }
        }
    }
}

@Composable
private fun ComponentCard(
    catalog: LicenseCatalog,
    component: LicenseComponent,
    onOpen: () -> Unit,
    onLink: () -> Unit
) {
    val licenseName = catalog.license(component.license)?.name ?: component.license
    val viewLabel = stringResource(R.string.licenses_view_text)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            // The whole text block is one button: it reads as "name, use, licence" and opens the text.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .clickable(onClickLabel = viewLabel, role = Role.Button, onClick = onOpen)
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(component.name, style = MaterialTheme.typography.titleSmall)
                Text(component.use, style = MaterialTheme.typography.bodyMedium)
                Text(licenseName, style = MaterialTheme.typography.bodySmall)
                Text(component.copyright, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onLink) {
                Text(stringResource(R.string.licenses_open_website))
            }
        }
    }
}

@Composable
private fun LicenseText(catalog: LicenseCatalog, component: LicenseComponent) {
    val context = LocalContext.current
    val asset = catalog.assetFor(component)
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            asset?.let { runCatching { readAsset(context, it) }.getOrNull() }.orEmpty()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Heading(
            component.name,
            MaterialTheme.typography.headlineSmall,
            Modifier.padding(top = 12.dp)
        )
        Text(component.copyright, Modifier.padding(vertical = 4.dp))
        Text(
            text ?: stringResource(R.string.licenses_loading),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
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
