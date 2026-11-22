package com.javohirmx.notifyr.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeOfDayTest {

    @Test
    fun `matches exactly at the start of the window`() {
        val time = TimeOfDay(9, 0)

        assertThat(time.isCurrentWithin(9 * 60)).isTrue()
    }

    @Test
    fun `matches inside the window`() {
        val time = TimeOfDay(9, 0)

        assertThat(time.isCurrentWithin(9 * 60 + 14)).isTrue()
    }

    @Test
    fun `does not match after the window closes`() {
        val time = TimeOfDay(9, 0)

        assertThat(time.isCurrentWithin(9 * 60 + 15)).isFalse()
    }

    @Test
    fun `does not match before the window starts`() {
        val time = TimeOfDay(9, 0)

        assertThat(time.isCurrentWithin(9 * 60 - 1)).isFalse()
    }

    @Test
    fun `window wraps past midnight`() {
        val time = TimeOfDay(23, 50)

        // 23:55 is inside the window.
        assertThat(time.isCurrentWithin(23 * 60 + 55)).isTrue()
        // 00:02 is also inside the same window.
        assertThat(time.isCurrentWithin(2)).isTrue()
        // 00:05 is the first minute after the 15-minute window.
        assertThat(time.isCurrentWithin(5)).isFalse()
    }

    @Test
    fun `minute offsets that overflow the hour are handled`() {
        val time = TimeOfDay(9, 50)

        assertThat(time.isCurrentWithin(10 * 60 + 4)).isTrue()
        assertThat(time.isCurrentWithin(10 * 60 + 5)).isFalse()
    }

    @Test
    fun `custom window size is respected`() {
        val time = TimeOfDay(12, 0)

        assertThat(time.isCurrentWithin(12 * 60 + 29, windowMinutes = 30)).isTrue()
        assertThat(time.isCurrentWithin(12 * 60 + 30, windowMinutes = 30)).isFalse()
    }
}
