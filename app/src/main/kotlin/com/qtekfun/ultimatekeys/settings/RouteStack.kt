// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.settings

/**
 * The screens an activity has open, oldest first: the state-based navigation of the app screens.
 * It is immutable; every step returns the next stack. Opening a screen that is already in the
 * stack goes back to it instead of stacking it twice, so two screens that link to each other
 * cannot pile up.
 */
data class RouteStack<R>(val items: List<R>) {
    init {
        require(items.isNotEmpty()) { "A stack needs a root screen" }
    }

    constructor(root: R) : this(listOf(root))

    val current: R get() = items.last()

    /** Whether there is a screen below the current one to go back to. */
    val canGoBack: Boolean get() = items.size > 1

    fun open(route: R): RouteStack<R> {
        val at = items.indexOf(route)
        return if (at >= 0) RouteStack(items.take(at + 1)) else RouteStack(items + route)
    }

    /** The stack without its top screen; the root stays (leaving it is the activity's business). */
    fun back(): RouteStack<R> = if (canGoBack) RouteStack(items.dropLast(1)) else this

    companion object {
        /** A compact form for the saved instance state. */
        fun <R : Enum<R>> encode(stack: RouteStack<R>): String =
            stack.items.joinToString(",") { it.name }

        /** The stack [encode]d earlier; unknown names are dropped, and [fallback] is the root if none is left. */
        fun <R : Enum<R>> decode(text: String, all: List<R>, fallback: R): RouteStack<R> {
            val byName = all.associateBy { it.name }
            val items = text.split(',').mapNotNull { byName[it] }
            return if (items.isEmpty()) RouteStack(fallback) else RouteStack(items)
        }
    }
}
