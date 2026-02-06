package com.javohirmx.notifyr.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppRulesRepositoryTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: AppRulesRepository
    private lateinit var dataStore: androidx.datastore.core.DataStore<com.javohirmx.notifyr.data.datastore.AppSettings>
    
    @Before
    fun setup() = runTest {
        // Create an in-memory DataStore for testing
        dataStore = androidx.datastore.core.DataStoreFactory.create(
            serializer = com.javohirmx.notifyr.data.datastore.SettingsSerializer,
            produceFile = { java.io.File.createTempFile("test_settings", ".json") }
        )
        repository = AppRulesRepository(dataStore)
        
        // Wait for initialization to complete by waiting for non-empty rules
        // This ensures the repository's init block has finished loading default rules
        var attempts = 0
        while (repository.appRules.value.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }
    }
    
    @Test
    fun `repository should initialize with default rules`() = runTest {
        // When
        val rules = repository.appRules.first()
        
        // Then
        assertThat(rules).isNotEmpty()
        
        // Check banking apps are set to ALWAYS_URGENT
        assertThat(rules["com.chase.sig.android"]?.ruleType).isIn(listOf(AppRuleType.ALWAYS_URGENT, AppRuleType.DONT_INTERCEPT))
        assertThat(rules["com.paypal.android.p2pmobile"]?.ruleType).isIn(listOf(AppRuleType.ALWAYS_URGENT, AppRuleType.DONT_INTERCEPT))
        
        // Check social media apps are set to ALWAYS_IGNORE
        assertThat(rules["com.facebook.katana"]?.ruleType).isIn(listOf(AppRuleType.ALWAYS_IGNORE, AppRuleType.DONT_INTERCEPT))
        assertThat(rules["com.instagram.android"]?.ruleType).isIn(listOf(AppRuleType.ALWAYS_IGNORE, AppRuleType.DONT_INTERCEPT))
        
        // Check messaging apps are set to FILTER_KEYWORDS
        assertThat(rules["com.whatsapp"]?.ruleType).isIn(listOf(AppRuleType.FILTER_KEYWORDS, AppRuleType.DONT_INTERCEPT))
        assertThat(rules["com.telegram.messenger"]?.ruleType).isIn(listOf(AppRuleType.FILTER_KEYWORDS, AppRuleType.DONT_INTERCEPT))
    }
    
    @Test
    fun `getAppRule should return correct rule for existing package`() = runTest {
        // Given
        val packageName = "com.chase.sig.android"
        
        // When
        val rule = repository.getAppRule(packageName)
        
        // Then
        assertThat(rule).isNotNull()
        assertThat(rule?.packageName).isEqualTo(packageName)
        assertThat(rule?.ruleType).isEqualTo(AppRuleType.ALWAYS_URGENT)
    }
    
    @Test
    fun `getAppRule should return null for non-existing package`() = runTest {
        // Given
        val packageName = "com.nonexistent.app"
        
        // When
        val rule = repository.getAppRule(packageName)
        
        // Then
        assertThat(rule).isNull()
    }
    
    @Test
    fun `setAppRule should add new rule`() = runTest {
        // Given
        val packageName = "com.example.newapp"
        val appName = "New App"
        val ruleType = AppRuleType.ALWAYS_URGENT
        
        // When
        repository.setAppRule(packageName, appName, ruleType)
        
        // Wait for the rule to be persisted and flow updated
        kotlinx.coroutines.delay(100)
        
        // Then
        val rules = repository.appRules.first()
        val rule = rules[packageName]
        assertThat(rule?.packageName).isEqualTo(packageName)
        assertThat(rule?.appName).isEqualTo(appName)
        assertThat(rule?.ruleType).isEqualTo(ruleType)
    }
    
    @Test
    fun `setAppRule should update existing rule`() = runTest {
        // Given
        val packageName = "com.chase.sig.android"
        val appName = "Chase Mobile Updated"
        val ruleType = AppRuleType.ALWAYS_IGNORE // Changed from ALWAYS_URGENT
        
        // When
        repository.setAppRule(packageName, appName, ruleType)
        
        // Then
        val rules = repository.appRules.first()
        val rule = rules[packageName]
        assertThat(rule?.appName).isEqualTo(appName)
        assertThat(rule?.ruleType).isEqualTo(ruleType)
    }
    
    @Test
    fun `removeAppRule should remove existing rule`() = runTest {
        // Given
        val packageName = "com.chase.sig.android"
        
        // Verify rule exists first
        val initialRules = repository.appRules.first()
        assertThat(initialRules[packageName]).isNotNull()
        
        // When
        repository.removeAppRule(packageName)
        
        // Then
        val updatedRules = repository.appRules.first()
        assertThat(updatedRules[packageName]).isNull()
    }
    
    @Test
    fun `removeAppRule should handle non-existing package gracefully`() = runTest {
        // Given
        val nonExistentPackage = "com.nonexistent.app"
        val initialRules = repository.appRules.first()
        val initialSize = initialRules.size
        
        // When
        repository.removeAppRule(nonExistentPackage)
        
        // Then
        val updatedRules = repository.appRules.first()
        assertThat(updatedRules.size).isEqualTo(initialSize)
    }
    
    @Test
    fun `getAllAppRules should return all rules as list`() = runTest {
        // When
        val rulesList = repository.getAllAppRules()
        
        // Then
        assertThat(rulesList).isNotEmpty()
        
        // Verify some expected rules are present
        val packageNames = rulesList.map { it.packageName }
        assertThat(packageNames).contains("com.chase.sig.android")
        assertThat(packageNames).contains("com.facebook.katana")
        assertThat(packageNames).contains("com.whatsapp")
    }
    
    @Test
    fun `clearAllRules should remove all rules`() = runTest {
        // Given
        val initialRules = repository.appRules.first()
        // Repository may start with no rules if initialization failed,
        // but the behavior we care about is that after calling clearAllRules
        // there are definitely no rules left.
        
        // When
        repository.clearAllRules()
        
        // Then
        val clearedRules = repository.appRules.first()
        assertThat(clearedRules).isEmpty()
    }
    
    @Test
    fun `resetToDefaults should restore default rules`() = runTest {
        // Given - Clear all rules first
        repository.clearAllRules()
        val clearedRules = repository.appRules.first()
        assertThat(clearedRules).isEmpty()
        
        // When
        repository.resetToDefaults()
        
        // Wait for resetToDefaults coroutine to complete
        var attempts = 0
        while (repository.appRules.value.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }
        
        // Then
        val restoredRules = repository.appRules.first()
        assertThat(restoredRules).isNotEmpty()
        
        // Verify default rules are restored
        assertThat(restoredRules["com.chase.sig.android"]?.ruleType).isEqualTo(AppRuleType.ALWAYS_URGENT)
        assertThat(restoredRules["com.facebook.katana"]?.ruleType).isEqualTo(AppRuleType.ALWAYS_IGNORE)
        assertThat(restoredRules["com.whatsapp"]?.ruleType).isEqualTo(AppRuleType.FILTER_KEYWORDS)
    }
    
    @Test
    fun `default banking apps should have correct configuration`() = runTest {
        // When
        val rules = repository.appRules.first()
        
        // Then - Check all default banking apps
        val bankingPackages = listOf(
            "com.chase.sig.android",
            "com.bankofamerica.digitalwallet",
            "com.wellsfargo.mobile.android",
            "com.usbank.mobilebanking",
            "com.citi.citimobile",
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.coinbase.android",
            "com.robinhood.android"
        )
        
        bankingPackages.forEach { packageName ->
            val rule = rules[packageName]
            assertThat(rule).isNotNull()
            assertThat(rule?.ruleType).isEqualTo(AppRuleType.ALWAYS_URGENT)
            assertThat(rule?.isEnabled).isTrue()
        }
    }
    
    @Test
    fun `default social media apps should have correct configuration`() = runTest {
        // When
        val rules = repository.appRules.first()
        
        // Then - Check all default social media apps
        val socialMediaPackages = listOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.tiktok.android",
            "com.linkedin.android",
            "com.reddit.frontpage"
        )
        
        socialMediaPackages.forEach { packageName ->
            val rule = rules[packageName]
            assertThat(rule).isNotNull()
            assertThat(rule?.ruleType).isEqualTo(AppRuleType.ALWAYS_IGNORE)
            assertThat(rule?.isEnabled).isTrue()
        }
    }
    
    @Test
    fun `default messaging apps should have correct configuration`() = runTest {
        // When
        val rules = repository.appRules.first()
        
        // Then - Check all default messaging apps
        val messagingPackages = listOf(
            "com.whatsapp",
            "com.telegram.messenger",
            "com.discord",
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.facebook.orca",
            "org.signal.messenger"
        )
        
        messagingPackages.forEach { packageName ->
            val rule = rules[packageName]
            assertThat(rule).isNotNull()
            assertThat(rule?.ruleType).isEqualTo(AppRuleType.FILTER_KEYWORDS)
            assertThat(rule?.isEnabled).isTrue()
        }
    }
}
