// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** A [RouteStack] that survives rotation and process death (it is saved as the route names). */
@Composable
fun <R : Enum<R>> rememberRouteStack(
    root: R,
    all: List<R>,
    start: List<R> = listOf(root)
): MutableState<RouteStack<R>> {
    val saver = Saver<RouteStack<R>, String>(
        save = { RouteStack.encode(it) },
        restore = { RouteStack.decode(it, all, root) }
    )
    return rememberSaveable(stateSaver = saver) { mutableStateOf(RouteStack(start)) }
}
