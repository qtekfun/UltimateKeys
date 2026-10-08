// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.voicemodels

import android.content.Context

/** The catalog packaged as the `models.json` asset of this module. */
fun Context.modelCatalog(): ModelCatalog = ModelCatalogHolder.get(this)

/** The model directory of this app (`filesDir/models`); one store per process. */
fun Context.modelStore(): ModelStore = ModelCatalogHolder.store(this)

private object ModelCatalogHolder {
    @Volatile
    private var catalog: ModelCatalog? = null

    @Volatile
    private var store: ModelStore? = null

    fun get(context: Context): ModelCatalog = catalog ?: synchronized(this) {
        catalog ?: context.applicationContext.assets.open("models.json").use {
            ModelCatalog.parse(it.readBytes().decodeToString())
        }.also { catalog = it }
    }

    fun store(context: Context): ModelStore = store ?: synchronized(this) {
        store ?: ModelStore(context.applicationContext.filesDir, get(context)).also { store = it }
    }
}
