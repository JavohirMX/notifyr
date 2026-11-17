package com.javohirmx.notifyr.domain.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeywordMatcherTest {

    @Test
    fun `does not match keyword inside a longer word`() {
        assertThat(KeywordMatcher.matches("I know the answer", "now")).isFalse()
        assertThat(KeywordMatcher.matches("wholesale prices", "sale")).isFalse()
        assertThat(KeywordMatcher.matches("that was helpful", "help")).isFalse()
        assertThat(KeywordMatcher.matches("reorder the item", "order")).isFalse()
    }

    @Test
    fun `matches whole words regardless of case`() {
        assertThat(KeywordMatcher.matches("Please reply NOW", "now")).isTrue()
        assertThat(KeywordMatcher.matches("Urgent: server down", "urgent")).isTrue()
        assertThat(KeywordMatcher.matches("Big SALE today", "sale")).isTrue()
    }

    @Test
    fun `matches simple plurals`() {
        assertThat(KeywordMatcher.matches("Weekend sales start today", "sale")).isTrue()
        assertThat(KeywordMatcher.matches("Special offers inside", "offer")).isTrue()
        assertThat(KeywordMatcher.matches("Two meetings scheduled", "meeting")).isTrue()
    }

    @Test
    fun `matches multi-word phrases`() {
        assertThat(KeywordMatcher.matches("Please call me back", "call me")).isTrue()
        assertThat(KeywordMatcher.matches("Action required: approve invoice", "action required")).isTrue()
        assertThat(KeywordMatcher.matches("Please call me back", "action required")).isFalse()
    }

    @Test
    fun `treats punctuation-only keywords as literal presence checks`() {
        assertThat(KeywordMatcher.matches("Can you confirm?", "?")).isTrue()
        assertThat(KeywordMatcher.matches("No question here", "?")).isFalse()
    }

    @Test
    fun `escapes regex metacharacters in literal keywords`() {
        assertThat(KeywordMatcher.matches("We use c++ internally", "c++")).isTrue()
        assertThat(KeywordMatcher.matches("cccccccc", "c++")).isFalse()
        assertThat(KeywordMatcher.matches("cost is $5", "$5")).isTrue()
    }

    @Test
    fun `empty keyword never matches`() {
        assertThat(KeywordMatcher.matches("anything", "")).isFalse()
        assertThat(KeywordMatcher.matches("anything", "   ")).isFalse()
    }
}
