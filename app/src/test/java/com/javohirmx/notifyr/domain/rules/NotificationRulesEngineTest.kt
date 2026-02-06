package com.javohirmx.notifyr.domain.rules

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.data.repository.TemporaryAppStatusRepository
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class NotificationRulesEngineTest {
    
    private lateinit var appRulesRepository: AppRulesRepository
    private lateinit var keywordRulesRepository: KeywordRulesRepository
    private lateinit var temporaryAppStatusRepository: TemporaryAppStatusRepository
    private lateinit var rulesEngine: NotificationRulesEngine
    
    @Before
    fun setup() = runTest {
        // Create in-memory DataStores for testing
        val dataStore = androidx.datastore.core.DataStoreFactory.create(
            serializer = com.javohirmx.notifyr.data.datastore.SettingsSerializer,
            produceFile = { java.io.File.createTempFile("test_settings", ".json") }
        )
        
        // Use real repositories for testing since they have in-memory state
        appRulesRepository = AppRulesRepository(dataStore)
        keywordRulesRepository = KeywordRulesRepository(dataStore)
        temporaryAppStatusRepository = TemporaryAppStatusRepository(dataStore)
        
        // Wait for repositories to initialize
        var attempts = 0
        while (appRulesRepository.appRules.value.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }
        
        // Clear all rules to start with clean state
        appRulesRepository.clearAllRules()
        keywordRulesRepository.clearAllKeywords()
        
        rulesEngine = NotificationRulesEngine(appRulesRepository, keywordRulesRepository, temporaryAppStatusRepository)
    }
    
    @Test
    fun `classifyNotification should return notification with updated importance`() {
        // Given
        val notification = createTestNotification(
            packageName = "com.example.test",
            title = "Test notification",
            text = "This is a test"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.NORMAL)
    }
    
    @Test
    fun `banking app notifications should be classified as urgent`() {
        // Given
        val notification = createTestNotification(
            packageName = "com.chase.sig.android",
            title = "Account Alert",
            text = "Your account has been accessed"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `social media app notifications should be ignored by default`() {
        // Given
        val notification = createTestNotification(
            packageName = "com.facebook.katana",
            title = "New post",
            text = "Someone posted a photo"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.IGNORE)
    }
    
    @Test
    fun `urgent keywords should override default app behavior`() {
        // Given
        keywordRulesRepository.addKeywordRule("urgent", NotificationImportance.URGENT, false)
        keywordRulesRepository.addKeywordRule("emergency", NotificationImportance.URGENT, false)
        keywordRulesRepository.addKeywordRule("asap", NotificationImportance.URGENT, false)
        
        val notification = createTestNotification(
            packageName = "com.facebook.katana", // Social media app (normally ignored)
            title = "URGENT: Security Alert",
            text = "Please verify your account immediately"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `custom app rules should override default behavior`() {
        // Given
        appRulesRepository.setAppRule("com.facebook.katana", "Facebook", AppRuleType.ALWAYS_URGENT)
        
        val notification = createTestNotification(
            packageName = "com.facebook.katana",
            title = "New message",
            text = "You have a new message"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `app rule set to always ignore should be ignored`() {
        // Given
        appRulesRepository.setAppRule("com.chase.sig.android", "Chase Mobile", AppRuleType.ALWAYS_IGNORE)
        
        val notification = createTestNotification(
            packageName = "com.chase.sig.android", // Banking app (normally urgent)
            title = "Account Alert",
            text = "Your account has been accessed"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.IGNORE)
    }
    
    @Test
    fun `messaging app with filter keywords should use keyword evaluation`() {
        // Given
        appRulesRepository.setAppRule("com.whatsapp", "WhatsApp", AppRuleType.FILTER_KEYWORDS)
        keywordRulesRepository.addKeywordRule("meeting", NotificationImportance.URGENT, false)
        
        val notification = createTestNotification(
            packageName = "com.whatsapp",
            title = "John Doe",
            text = "Don't forget about our meeting tomorrow"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `keyword matching should be case insensitive`() {
        // Given
        keywordRulesRepository.addKeywordRule("urgent", NotificationImportance.URGENT, false)
        
        val notification = createTestNotification(
            packageName = "com.example.test",
            title = "URGENT Message",
            text = "This is an URGENT notification"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `regex keyword rules should work correctly`() {
        // Given
        keywordRulesRepository.addKeywordRule("\\d{3}-\\d{3}-\\d{4}", NotificationImportance.URGENT, true) // Phone number pattern
        
        val notification = createTestNotification(
            packageName = "com.example.test",
            title = "Call from 555-123-4567",
            text = "You have a missed call"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `call category notifications should be urgent by default`() {
        // Given
        val notification = createTestNotification(
            packageName = "com.android.dialer",
            title = "Missed call",
            text = "Missed call from John",
            category = "call"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `promotional category notifications should be ignored by default`() {
        // Given
        val notification = createTestNotification(
            packageName = "com.example.shopping",
            title = "50% Off Sale!",
            text = "Limited time offer",
            category = "promo"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.IGNORE)
    }
    
    @Test
    fun `disabled keyword rules should not be applied`() {
        // Given
        keywordRulesRepository.addKeywordRule("urgent", NotificationImportance.URGENT, false)
        keywordRulesRepository.toggleKeywordRule("urgent") // This will disable it
        
        val notification = createTestNotification(
            packageName = "com.example.test",
            title = "URGENT Message",
            text = "This is urgent"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        assertThat(result.importance).isEqualTo(NotificationImportance.NORMAL)
    }
    
    @Test
    fun `banking app should be urgent by default even without custom rules`() {
        // Given - No custom rules, should fall back to default behavior
        val notification = createTestNotification(
            packageName = "com.chase.sig.android", // Banking app
            title = "Account Alert",
            text = "Your account has been accessed"
        )
        
        // When
        val result = rulesEngine.classifyNotification(notification)
        
        // Then
        // Should fall back to default banking app behavior (urgent)
        assertThat(result.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    private fun createTestNotification(
        packageName: String,
        title: String,
        text: String,
        category: String? = null,
        appName: String = "Test App"
    ): NotificationData {
        return NotificationData(
            id = 0,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = category,
            importance = NotificationImportance.NORMAL, // Default, will be overridden by rules engine
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
    }
}
