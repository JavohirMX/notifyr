package com.javohirmx.notifyr.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationImportanceTest {
    
    @Test
    fun `fromValue should return correct importance for valid values`() {
        // Test all valid values
        assertThat(NotificationImportance.fromValue(0)).isEqualTo(NotificationImportance.IGNORE)
        assertThat(NotificationImportance.fromValue(1)).isEqualTo(NotificationImportance.NORMAL)
        assertThat(NotificationImportance.fromValue(2)).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `fromValue should return NORMAL for invalid values`() {
        // Test invalid values
        assertThat(NotificationImportance.fromValue(-1)).isEqualTo(NotificationImportance.NORMAL)
        assertThat(NotificationImportance.fromValue(3)).isEqualTo(NotificationImportance.NORMAL)
        assertThat(NotificationImportance.fromValue(999)).isEqualTo(NotificationImportance.NORMAL)
    }
    
    @Test
    fun `value property should return correct integer values`() {
        assertThat(NotificationImportance.IGNORE.value).isEqualTo(0)
        assertThat(NotificationImportance.NORMAL.value).isEqualTo(1)
        assertThat(NotificationImportance.URGENT.value).isEqualTo(2)
    }
    
    @Test
    fun `importance levels should have correct ordering`() {
        // URGENT should be highest priority (highest value)
        assertThat(NotificationImportance.URGENT.value).isGreaterThan(NotificationImportance.NORMAL.value)
        assertThat(NotificationImportance.NORMAL.value).isGreaterThan(NotificationImportance.IGNORE.value)
    }
    
    @Test
    fun `enum values should be consistent`() {
        // Verify all enum values exist
        val values = NotificationImportance.values()
        assertThat(values).hasLength(3)
        assertThat(values).asList().containsExactly(
            NotificationImportance.IGNORE,
            NotificationImportance.NORMAL,
            NotificationImportance.URGENT
        )
    }
    
    @Test
    fun `toString should return readable names`() {
        assertThat(NotificationImportance.IGNORE.toString()).isEqualTo("IGNORE")
        assertThat(NotificationImportance.NORMAL.toString()).isEqualTo("NORMAL")
        assertThat(NotificationImportance.URGENT.toString()).isEqualTo("URGENT")
    }
}
