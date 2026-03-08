package com.javohirmx.notifyr.ui.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryScrollTopBehaviorTest {

    @Test
    fun `shows button when scrolled beyond index threshold`() {
        val shouldShow = shouldShowScrollToTopButton(
            firstVisibleItemIndex = 5,
            firstVisibleItemScrollOffset = 0,
            indexThreshold = 3,
            offsetThresholdPx = 200
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun `shows button when at threshold index and offset exceeds threshold`() {
        val shouldShow = shouldShowScrollToTopButton(
            firstVisibleItemIndex = 3,
            firstVisibleItemScrollOffset = 250,
            indexThreshold = 3,
            offsetThresholdPx = 200
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun `hides button when below threshold`() {
        val shouldShow = shouldShowScrollToTopButton(
            firstVisibleItemIndex = 2,
            firstVisibleItemScrollOffset = 199,
            indexThreshold = 3,
            offsetThresholdPx = 200
        )

        assertThat(shouldShow).isFalse()
    }
}
