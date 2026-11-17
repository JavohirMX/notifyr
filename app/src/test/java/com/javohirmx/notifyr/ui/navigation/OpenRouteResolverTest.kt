package com.javohirmx.notifyr.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenRouteResolverTest {

    @Test
    fun `resolves known top-level tabs`() {
        assertThat(OpenRouteResolver.resolve(Screen.Dashboard.route))
            .isEqualTo(Screen.Dashboard.route)
        assertThat(OpenRouteResolver.resolve(Screen.History.route))
            .isEqualTo(Screen.History.route)
        assertThat(OpenRouteResolver.resolve(Screen.Settings.route))
            .isEqualTo(Screen.Settings.route)
        assertThat(OpenRouteResolver.resolve(Screen.ScreenTime.route))
            .isEqualTo(Screen.ScreenTime.route)
        assertThat(OpenRouteResolver.resolve(Screen.Insights.route))
            .isEqualTo(Screen.Insights.route)
    }

    @Test
    fun `maps the legacy digest extra to the real digest route`() {
        assertThat(OpenRouteResolver.resolve("digest"))
            .isEqualTo(Screen.DigestView.route)
        assertThat(OpenRouteResolver.resolve(Screen.DigestView.route))
            .isEqualTo(Screen.DigestView.route)
    }

    @Test
    fun `ignores unknown or missing extras`() {
        assertThat(OpenRouteResolver.resolve(null)).isNull()
        assertThat(OpenRouteResolver.resolve("")).isNull()
        assertThat(OpenRouteResolver.resolve("not_a_screen")).isNull()
    }
}
