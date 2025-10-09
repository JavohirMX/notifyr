package com.javohirmx.notifyr.domain.ml

import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts numerical features from notifications for ML classification
 */
@Singleton
class NotificationFeatureExtractor @Inject constructor() {
    
    companion object {
        // Feature indices
        const val FEATURE_HOUR_OF_DAY = 0
        const val FEATURE_DAY_OF_WEEK = 1
        const val FEATURE_IS_WEEKEND = 2
        const val FEATURE_TITLE_LENGTH = 3
        const val FEATURE_TEXT_LENGTH = 4
        const val FEATURE_HAS_QUESTION_MARK = 5
        const val FEATURE_HAS_EXCLAMATION = 6
        const val FEATURE_HAS_CAPS_LOCK = 7
        const val FEATURE_WORD_COUNT = 8
        const val FEATURE_HAS_URGENT_KEYWORDS = 9
        const val FEATURE_IS_FINANCIAL_APP = 10
        const val FEATURE_IS_SOCIAL_APP = 11
        const val FEATURE_IS_MESSAGING_APP = 12
        const val FEATURE_IS_EMAIL_APP = 13
        const val FEATURE_MESSAGE_FREQUENCY = 14
        const val FEATURE_SENDER_FAMILIARITY = 15
        const val FEATURE_CONVERSATION_ACTIVE = 16
        const val FEATURE_TIME_SINCE_LAST = 17
        
        const val FEATURE_COUNT = 18
        
        // Keyword sets for feature detection
        private val URGENT_KEYWORDS = setOf(
            "urgent", "asap", "emergency", "important", "critical", "now",
            "immediately", "help", "alert", "warning", "action required",
            "deadline", "call me", "call back", "meeting", "verify", "confirm"
        )
        
        private val FINANCIAL_APPS = setOf(
            "com.chase.sig.android", "com.bankofamerica.digitalwallet",
            "com.wellsfargo.mobile.android", "com.paypal.android.p2pmobile",
            "com.venmo", "com.coinbase.android", "com.robinhood.android"
        )
        
        private val SOCIAL_APPS = setOf(
            "com.facebook.katana", "com.instagram.android", "com.twitter.android",
            "com.snapchat.android", "com.tiktok.android", "com.linkedin.android"
        )
        
        private val MESSAGING_APPS = setOf(
            "com.whatsapp", "com.telegram.messenger", "com.discord",
            "com.slack", "com.microsoft.teams", "com.google.android.apps.messaging"
        )
        
        private val EMAIL_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail"
        )
    }
    
    /**
     * Extract features from a notification
     */
    fun extractFeatures(
        notification: NotificationData,
        conversationHistory: List<NotificationData> = emptyList()
    ): FloatArray {
        val features = FloatArray(FEATURE_COUNT)
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = notification.timestamp
        }
        
        // Temporal features
        features[FEATURE_HOUR_OF_DAY] = calendar.get(Calendar.HOUR_OF_DAY) / 24f
        features[FEATURE_DAY_OF_WEEK] = calendar.get(Calendar.DAY_OF_WEEK) / 7f
        features[FEATURE_IS_WEEKEND] = if (isWeekend(calendar)) 1f else 0f
        
        // Text features
        val combinedText = "${notification.title} ${notification.text}".lowercase()
        features[FEATURE_TITLE_LENGTH] = minOf(notification.title.length / 100f, 1f)
        features[FEATURE_TEXT_LENGTH] = minOf(notification.text.length / 500f, 1f)
        features[FEATURE_HAS_QUESTION_MARK] = if (combinedText.contains("?")) 1f else 0f
        features[FEATURE_HAS_EXCLAMATION] = if (combinedText.contains("!")) 1f else 0f
        features[FEATURE_HAS_CAPS_LOCK] = calculateCapsLockRatio(combinedText)
        features[FEATURE_WORD_COUNT] = minOf(combinedText.split(" ").size / 50f, 1f)
        
        // Keyword features
        features[FEATURE_HAS_URGENT_KEYWORDS] = if (containsUrgentKeywords(combinedText)) 1f else 0f
        
        // App category features
        features[FEATURE_IS_FINANCIAL_APP] = if (FINANCIAL_APPS.contains(notification.packageName)) 1f else 0f
        features[FEATURE_IS_SOCIAL_APP] = if (SOCIAL_APPS.contains(notification.packageName)) 1f else 0f
        features[FEATURE_IS_MESSAGING_APP] = if (MESSAGING_APPS.contains(notification.packageName)) 1f else 0f
        features[FEATURE_IS_EMAIL_APP] = if (EMAIL_APPS.contains(notification.packageName)) 1f else 0f
        
        // Conversation features
        if (conversationHistory.isNotEmpty()) {
            val recentMessages = conversationHistory.filter { 
                it.timestamp > notification.timestamp - 3600000 // Last hour
            }
            
            features[FEATURE_MESSAGE_FREQUENCY] = minOf(recentMessages.size / 10f, 1f)
            features[FEATURE_SENDER_FAMILIARITY] = calculateSenderFamiliarity(
                notification.sender, conversationHistory
            )
            features[FEATURE_CONVERSATION_ACTIVE] = if (recentMessages.size >= 3) 1f else 0f
            
            val lastMessage = conversationHistory.maxByOrNull { it.timestamp }
            if (lastMessage != null) {
                val timeSince = (notification.timestamp - lastMessage.timestamp) / 3600000f // hours
                features[FEATURE_TIME_SINCE_LAST] = minOf(timeSince / 24f, 1f)
            }
        }
        
        return features
    }
    
    /**
     * Extract features with label for training
     */
    fun extractFeaturesWithLabel(
        notification: NotificationData,
        conversationHistory: List<NotificationData> = emptyList()
    ): Pair<FloatArray, Float> {
        val features = extractFeatures(notification, conversationHistory)
        val label = when (notification.importance) {
            NotificationImportance.URGENT -> 1f
            NotificationImportance.NORMAL -> 0.5f
            NotificationImportance.IGNORE -> 0f
        }
        return features to label
    }
    
    private fun isWeekend(calendar: Calendar): Boolean {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }
    
    private fun calculateCapsLockRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        val upperCount = letters.count { it.isUpperCase() }
        return upperCount.toFloat() / letters.length
    }
    
    private fun containsUrgentKeywords(text: String): Boolean {
        return URGENT_KEYWORDS.any { text.contains(it) }
    }
    
    private fun calculateSenderFamiliarity(
        sender: String?,
        history: List<NotificationData>
    ): Float {
        if (sender == null) return 0f
        val senderMessages = history.count { it.sender == sender }
        return minOf(senderMessages / 20f, 1f)
    }
    
    /**
     * Get feature names for debugging
     */
    fun getFeatureNames(): List<String> {
        return listOf(
            "hour_of_day",
            "day_of_week",
            "is_weekend",
            "title_length",
            "text_length",
            "has_question_mark",
            "has_exclamation",
            "has_caps_lock",
            "word_count",
            "has_urgent_keywords",
            "is_financial_app",
            "is_social_app",
            "is_messaging_app",
            "is_email_app",
            "message_frequency",
            "sender_familiarity",
            "conversation_active",
            "time_since_last"
        )
    }
}

