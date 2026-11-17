package com.javohirmx.notifyr.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationIdsTest {

    @Test
    fun `urgent ids never collide with the digest id`() {
        // The first rows are the dangerous ones: a base of 1001 made row 1 land exactly
        // on the digest's fixed ID of 1002.
        assertThat(NotificationIds.urgent(1)).isNotEqualTo(NotificationIds.DIGEST)
        assertThat(NotificationIds.urgent(2)).isNotEqualTo(NotificationIds.DIGEST)
        assertThat(NotificationIds.urgent(1)).isGreaterThan(NotificationIds.DIGEST)
    }

    @Test
    fun `urgent ids are unique per row`() {
        val ids = (1L..1_000L).map(NotificationIds::urgent)
        assertThat(ids.toSet()).hasSize(ids.size)
    }
}
