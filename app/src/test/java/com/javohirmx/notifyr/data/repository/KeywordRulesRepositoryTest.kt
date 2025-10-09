package com.javohirmx.notifyr.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.KeywordRule
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KeywordRulesRepositoryTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: KeywordRulesRepository
    
    @Before
    fun setup() {
        repository = KeywordRulesRepository()
    }
    
    @Test
    fun `repository should initialize with default urgent keywords`() = runTest {
        // When
        val rules = repository.keywordRules.first()
        
        // Then
        assertThat(rules).isNotEmpty()
        
        // Check some default urgent keywords
        val urgentKeywords = rules.filter { it.importance == NotificationImportance.URGENT }
        val keywordTexts = urgentKeywords.map { it.keyword }
        
        assertThat(keywordTexts).contains("urgent")
        assertThat(keywordTexts).contains("emergency")
        assertThat(keywordTexts).contains("asap")
        assertThat(keywordTexts).contains("important")
        assertThat(keywordTexts).contains("critical")
    }
    
    @Test
    fun `repository should initialize with default ignore keywords`() = runTest {
        // When
        val rules = repository.keywordRules.first()
        
        // Then
        val ignoreKeywords = rules.filter { it.importance == NotificationImportance.IGNORE }
        val keywordTexts = ignoreKeywords.map { it.keyword }
        
        assertThat(keywordTexts).contains("unsubscribe")
        assertThat(keywordTexts).contains("promotion")
        assertThat(keywordTexts).contains("sale")
        assertThat(keywordTexts).contains("discount")
        assertThat(keywordTexts).contains("spam")
    }
    
    @Test
    fun `getAllKeywords should return rule for existing keyword`() = runTest {
        // Given
        val keyword = "urgent"
        
        // When
        val rules = repository.getAllKeywords()
        val rule = rules.find { it.keyword == keyword }
        
        // Then
        assertThat(rule).isNotNull()
        assertThat(rule?.keyword).isEqualTo(keyword)
        assertThat(rule?.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `getAllKeywords should not contain non-existing keyword`() = runTest {
        // Given
        val keyword = "nonexistent"
        
        // When
        val rules = repository.getAllKeywords()
        val rule = rules.find { it.keyword == keyword }
        
        // Then
        assertThat(rule).isNull()
    }
    
    @Test
    fun `addKeywordRule should add new rule`() = runTest {
        // Given
        val keyword = "newkeyword"
        val importance = NotificationImportance.URGENT
        val isRegex = false
        
        // When
        repository.addKeywordRule(keyword, importance, isRegex)
        
        // Then
        val rules = repository.keywordRules.first()
        val addedRule = rules.find { it.keyword == keyword }
        assertThat(addedRule?.keyword).isEqualTo(keyword)
        assertThat(addedRule?.importance).isEqualTo(importance)
        assertThat(addedRule?.isRegex).isEqualTo(isRegex)
    }
    
    @Test
    fun `updateKeywordRule should update existing rule`() = runTest {
        // Given
        val originalKeyword = "urgent"
        val newKeyword = "very_urgent"
        val newImportance = NotificationImportance.IGNORE
        val newIsRegex = true
        
        // When
        repository.updateKeywordRule(originalKeyword, newKeyword, newImportance, newIsRegex)
        
        // Then
        val rules = repository.keywordRules.first()
        val rule = rules.find { it.keyword == newKeyword }
        assertThat(rule?.keyword).isEqualTo(newKeyword)
        assertThat(rule?.importance).isEqualTo(newImportance)
        assertThat(rule?.isRegex).isEqualTo(newIsRegex)
    }
    
    @Test
    fun `removeKeywordRule should remove existing rule`() = runTest {
        // Given
        val keyword = "urgent"
        
        // Verify rule exists first
        val initialRules = repository.keywordRules.first()
        val initialRule = initialRules.find { it.keyword == keyword }
        assertThat(initialRule).isNotNull()
        
        // When
        repository.removeKeywordRule(keyword)
        
        // Then
        val updatedRules = repository.keywordRules.first()
        val removedRule = updatedRules.find { it.keyword == keyword }
        assertThat(removedRule).isNull()
    }
    
    @Test
    fun `removeKeywordRule should handle non-existing keyword gracefully`() = runTest {
        // Given
        val nonExistentKeyword = "nonexistent"
        val initialRules = repository.keywordRules.first()
        val initialSize = initialRules.size
        
        // When
        repository.removeKeywordRule(nonExistentKeyword)
        
        // Then
        val updatedRules = repository.keywordRules.first()
        assertThat(updatedRules.size).isEqualTo(initialSize)
    }
    
    @Test
    fun `getKeywordsByImportance should return filtered keywords`() = runTest {
        // When
        val urgentKeywords = repository.getKeywordsByImportance(NotificationImportance.URGENT)
        val ignoreKeywords = repository.getKeywordsByImportance(NotificationImportance.IGNORE)
        
        // Then
        assertThat(urgentKeywords).isNotEmpty()
        assertThat(ignoreKeywords).isNotEmpty()
        
        // Verify all returned keywords have correct importance
        urgentKeywords.forEach { rule ->
            assertThat(rule.importance).isEqualTo(NotificationImportance.URGENT)
        }
        
        ignoreKeywords.forEach { rule ->
            assertThat(rule.importance).isEqualTo(NotificationImportance.IGNORE)
        }
    }
    
    @Test
    fun `getKeywordsByImportance should return only enabled keywords of specific importance`() = runTest {
        // Given - Add a disabled keyword
        repository.addKeywordRule("disabled", NotificationImportance.URGENT, false)
        repository.toggleKeywordRule("disabled") // This will disable it
        
        // When
        val enabledUrgentKeywords = repository.getKeywordsByImportance(NotificationImportance.URGENT)
        
        // Then
        enabledUrgentKeywords.forEach { rule ->
            assertThat(rule.isEnabled).isTrue()
            assertThat(rule.importance).isEqualTo(NotificationImportance.URGENT)
        }
        
        // Verify disabled keyword is not included
        val disabledKeywordFound = enabledUrgentKeywords.any { it.keyword == "disabled" }
        assertThat(disabledKeywordFound).isFalse()
    }
    
    @Test
    fun `clearAllKeywords should remove all keywords`() = runTest {
        // Given
        val initialRules = repository.keywordRules.first()
        assertThat(initialRules).isNotEmpty()
        
        // When
        repository.clearAllKeywords()
        
        // Then
        val clearedRules = repository.keywordRules.first()
        assertThat(clearedRules).isEmpty()
    }
    
    @Test
    fun `resetToDefaults should restore default keywords`() = runTest {
        // Given - Clear all keywords first
        repository.clearAllKeywords()
        val clearedRules = repository.keywordRules.first()
        assertThat(clearedRules).isEmpty()
        
        // When
        repository.resetToDefaults()
        
        // Then
        val restoredRules = repository.keywordRules.first()
        assertThat(restoredRules).isNotEmpty()
        
        // Verify default keywords are restored
        val keywordTexts = restoredRules.map { it.keyword }
        assertThat(keywordTexts).contains("urgent")
        assertThat(keywordTexts).contains("emergency")
        assertThat(keywordTexts).contains("unsubscribe")
        assertThat(keywordTexts).contains("promotion")
    }
    
    @Test
    fun `multiple addKeywordRule calls should add all keywords`() = runTest {
        // Given
        val newKeywords = listOf(
            Triple("keyword1", NotificationImportance.URGENT, false),
            Triple("keyword2", NotificationImportance.NORMAL, false),
            Triple("keyword3", NotificationImportance.IGNORE, true)
        )
        
        // When
        newKeywords.forEach { (keyword, importance, isRegex) ->
            repository.addKeywordRule(keyword, importance, isRegex)
        }
        
        // Then
        val rules = repository.keywordRules.first()
        newKeywords.forEach { (keyword, importance, isRegex) ->
            val foundRule = rules.find { it.keyword == keyword }
            assertThat(foundRule?.keyword).isEqualTo(keyword)
            assertThat(foundRule?.importance).isEqualTo(importance)
            assertThat(foundRule?.isRegex).isEqualTo(isRegex)
        }
    }
    
    @Test
    fun `importKeywords should replace existing keywords`() = runTest {
        // Given
        val importedKeywords = listOf(
            KeywordRule("imported1", NotificationImportance.URGENT, true, false),
            KeywordRule("imported2", NotificationImportance.IGNORE, true, false)
        )
        
        // When
        repository.importKeywords(importedKeywords)
        
        // Then
        val rules = repository.keywordRules.first()
        assertThat(rules).hasSize(2)
        assertThat(rules).containsExactlyElementsIn(importedKeywords)
    }
    
    @Test
    fun `exportKeywords should return all current keywords`() = runTest {
        // When
        val exportedKeywords = repository.exportKeywords()
        val currentKeywords = repository.keywordRules.first()
        
        // Then
        assertThat(exportedKeywords).containsExactlyElementsIn(currentKeywords)
    }
    
    @Test
    fun `default keywords should have correct properties`() = runTest {
        // When
        val rules = repository.keywordRules.first()
        
        // Then - All default keywords should be enabled and non-regex
        rules.forEach { rule ->
            assertThat(rule.isEnabled).isTrue()
            assertThat(rule.isRegex).isFalse()
            assertThat(rule.importance).isIn(listOf(
                NotificationImportance.URGENT,
                NotificationImportance.IGNORE
            ))
        }
    }
    
    @Test
    fun `regex keyword rules should work correctly`() = runTest {
        // Given
        val keyword = "\\d{3}-\\d{3}-\\d{4}" // Phone number pattern
        val importance = NotificationImportance.URGENT
        val isRegex = true
        
        // When
        repository.addKeywordRule(keyword, importance, isRegex)
        
        // Then
        val rules = repository.keywordRules.first()
        val addedRule = rules.find { it.keyword == keyword }
        assertThat(addedRule?.isRegex).isTrue()
        assertThat(addedRule?.importance).isEqualTo(NotificationImportance.URGENT)
    }
}
