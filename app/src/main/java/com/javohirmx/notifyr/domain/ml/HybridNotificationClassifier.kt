package com.javohirmx.notifyr.domain.ml

import android.util.Log
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.rules.EnhancedNotificationRulesEngine
import com.javohirmx.notifyr.domain.rules.NotificationRulesEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid classifier that combines:
 * 1. Traditional rule-based classification
 * 2. ML-based prediction
 * 3. User feedback learning
 */
@Singleton
class HybridNotificationClassifier @Inject constructor(
    private val rulesEngine: NotificationRulesEngine,
    private val enhancedRulesEngine: EnhancedNotificationRulesEngine,
    private val mlClassifier: SmartNotificationClassifier,
    private val notificationRepository: NotificationRepository
) {
    
    companion object {
        private const val TAG = "HybridClassifier"
        private const val ML_CONFIDENCE_THRESHOLD = 0.75f
    }
    
    private var mlEnabled = true // Can be toggled in settings
    
    /**
     * Classify notification using hybrid approach
     */
    suspend fun classify(notification: NotificationData): NotificationData {
        // 1. Apply traditional rules first
        val rulesClassified = rulesEngine.classifyNotification(notification)
        val enhancedClassified = enhancedRulesEngine.classifyNotificationWithTags(rulesClassified)
        
        // 2. If ML is enabled and model is trained, get ML prediction
        if (mlEnabled && mlClassifier.getModelStats().isModelTrained) {
            try {
                // Get conversation history for better context
                val conversationHistory = if (notification.conversationId != null) {
                    notificationRepository
                        .getNotificationsByPackage(notification.packageName)
                        .first()
                        .filter { it.conversationId == notification.conversationId }
                        .sortedByDescending { it.timestamp }
                        .take(20)
                } else {
                    emptyList()
                }
                
                val (mlImportance, confidence) = mlClassifier.classify(
                    enhancedClassified,
                    conversationHistory
                )
                
                // 3. Decide which classification to use
                val finalImportance = decideClassification(
                    rulesImportance = enhancedClassified.importance,
                    mlImportance = mlImportance,
                    mlConfidence = confidence
                )
                
                Log.d(TAG, "Hybrid: Rules=${ enhancedClassified.importance}, ML=$mlImportance ($confidence), Final=$finalImportance")
                
                return enhancedClassified.copy(importance = finalImportance)
            } catch (e: Exception) {
                Log.e(TAG, "ML classification failed, using rules", e)
                return enhancedClassified
            }
        }
        
        // 4. If ML disabled or not trained, use rules only
        return enhancedClassified
    }
    
    /**
     * Learn from user correction
     * Called when user manually changes importance
     */
    suspend fun learnFromUserCorrection(
        notification: NotificationData,
        userImportance: NotificationImportance
    ) {
        if (!mlEnabled) return
        
        try {
            // Get conversation history
            val conversationHistory = if (notification.conversationId != null) {
                notificationRepository
                    .getNotificationsByPackage(notification.packageName)
                    .first()
                    .filter { it.conversationId == notification.conversationId }
                    .sortedByDescending { it.timestamp }
                    .take(20)
            } else {
                emptyList()
            }
            
            // Let ML model learn from this
            mlClassifier.learnFromFeedback(
                notification,
                userImportance,
                conversationHistory
            )
            
            Log.d(TAG, "Learned from user: ${notification.appName} -> $userImportance")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to learn from user feedback", e)
        }
    }
    
    /**
     * Trigger batch training
     */
    suspend fun trainModel(epochs: Int = 5) {
        if (!mlEnabled) return
        
        try {
            mlClassifier.batchTrain(epochs)
        } catch (e: Exception) {
            Log.e(TAG, "Batch training failed", e)
        }
    }
    
    /**
     * Get ML model stats
     */
    fun getMLStats(): MLModelStats {
        return mlClassifier.getModelStats()
    }
    
    /**
     * Enable or disable ML
     */
    fun setMLEnabled(enabled: Boolean) {
        mlEnabled = enabled
        Log.d(TAG, "ML ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if ML is enabled
     */
    fun isMLEnabled(): Boolean = mlEnabled
    
    /**
     * Reset ML model
     */
    suspend fun resetMLModel() {
        mlClassifier.resetModel()
    }
    
    // Private helper
    
    private fun decideClassification(
        rulesImportance: NotificationImportance,
        mlImportance: NotificationImportance,
        mlConfidence: Float
    ): NotificationImportance {
        
        // Strategy: Use ML when confident, otherwise trust rules
        return when {
            // If ML is very confident (>75%), use ML prediction
            mlConfidence >= ML_CONFIDENCE_THRESHOLD -> mlImportance
            
            // If both agree, definitely use that
            rulesImportance == mlImportance -> rulesImportance
            
            // If rules say URGENT and ML says less, trust rules (safety)
            rulesImportance == NotificationImportance.URGENT -> rulesImportance
            
            // If ML says URGENT with moderate confidence, trust ML
            mlImportance == NotificationImportance.URGENT && mlConfidence >= 0.6f -> mlImportance
            
            // Otherwise, trust rules
            else -> rulesImportance
        }
    }
}

