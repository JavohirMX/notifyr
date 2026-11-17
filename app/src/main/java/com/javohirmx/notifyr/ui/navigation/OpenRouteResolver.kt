package com.javohirmx.notifyr.ui.navigation

/**
 * Resolves the `open_tab` extra attached to a notification tap into a navigation route.
 *
 * Kept as a pure function so the mapping (including the legacy "digest" alias) can be
 * unit-tested without an Activity.
 */
object OpenRouteResolver {

    const val EXTRA_OPEN_TAB = "open_tab"

    /** Legacy alias used by earlier digest notifications. */
    private const val DIGEST_ALIAS = "digest"

    fun resolve(openTab: String?): String? = when (openTab) {
        null -> null
        DIGEST_ALIAS -> Screen.DigestView.route
        Screen.Dashboard.route,
        Screen.History.route,
        Screen.Settings.route,
        Screen.ScreenTime.route,
        Screen.Insights.route,
        Screen.DigestView.route -> openTab
        else -> null
    }
}
