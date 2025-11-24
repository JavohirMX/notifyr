package com.javohirmx.notifyr.domain.ml

import android.content.Context
import android.util.Log
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * ML-based notification classifier using a simple neural network
 * Learns from user feedback to improve over time
 */
@Singleton
class SmartNotificationClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureExtractor: NotificationFeatureExtractor,
    private val trainingDataManager: MLTrainingDataManager
) {
    
    companion object {
        private const val TAG = "MLClassifier"
        private const val LEARNING_RATE = 0.01f
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val BATCH_TRAINING_THRESHOLD = 50 // Trigger batch training after 50 new samples
    }
    
    // Simple neural network weights (18 input features -> 1 output)
    private var weights = FloatArray(NotificationFeatureExtractor.FEATURE_COUNT) { 
        (Math.random().toFloat() - 0.5f) * 0.1f 
    }
    private var bias = 0f
    
    // Model metadata
    private var totalTrainingSamples = 0
    private var lastTrainingTime = 0L
    private var modelAccuracy = 0f
    private var samplesSinceLastBatchTraining = 0 // Track samples since last batch training
    
    init {
        loadModelWeights()
    }
    
    /**
     * Classify notification using ML model
     * Returns: Pair(predicted importance, confidence score)
     */
    suspend fun classify(
        notification: NotificationData,
        conversationHistory: List<NotificationData> = emptyList()
    ): Pair<NotificationImportance, Float> = withContext(Dispatchers.Default) {
        
        // Extract features
        val features = featureExtractor.extractFeatures(notification, conversationHistory)
        
        // Forward pass (simple linear model with sigmoid)
        val logit = computeLogit(features)
        val probability = sigmoid(logit)
        
        // Convert probability to importance level
        val importance = when {
            probability >= 0.75f -> NotificationImportance.URGENT
            probability >= 0.35f -> NotificationImportance.NORMAL
            else -> NotificationImportance.IGNORE
        }
        
        // Calculate confidence (how sure we are)
        val confidence = when {
            probability >= 0.75f -> (probability - 0.75f) / 0.25f
            probability <= 0.35f -> (0.35f - probability) / 0.35f
            else -> 1f - ((probability - 0.35f) / 0.4f) * 0.7f
        }
        
        Log.d(TAG, "ML Classification: $importance (${(probability * 100).toInt()}% urgent, ${(confidence * 100).toInt()}% confident)")
        
        importance to confidence
    }
    
    /**
     * Learn from user feedback
     * Called when user manually changes notification importance
     */
    suspend fun learnFromFeedback(
        notification: NotificationData,
        userImportance: NotificationImportance,
        conversationHistory: List<NotificationData> = emptyList()
    ) = withContext(Dispatchers.Default) {
        
        // Extract features
        val features = featureExtractor.extractFeatures(notification, conversationHistory)
        
        // Convert user importance to target value
        val target = when (userImportance) {
            NotificationImportance.URGENT -> 1f
            NotificationImportance.NORMAL -> 0.5f
            NotificationImportance.IGNORE -> 0f
        }
        
        // Perform one gradient descent step
        val prediction = sigmoid(computeLogit(features))
        val error = target - prediction
        
        // Update weights
        for (i in features.indices) {
            weights[i] += LEARNING_RATE * error * features[i]
        }
        bias += LEARNING_RATE * error
        
        // Save training sample for later batch training
        trainingDataManager.addTrainingSample(notification, userImportance, conversationHistory)
        
        totalTrainingSamples++
        samplesSinceLastBatchTraining++
        
        // Periodically save weights
        if (totalTrainingSamples % 10 == 0) {
            saveModelWeights()
        }
        
        // Check if we should trigger automatic batch training
        if (samplesSinceLastBatchTraining >= BATCH_TRAINING_THRESHOLD) {
            Log.d(TAG, "Threshold reached ($samplesSinceLastBatchTraining samples), triggering automatic batch training")
            samplesSinceLastBatchTraining = 0
            batchTrain(epochs = 5)
        }
        
        Log.d(TAG, "Learned from feedback: ${notification.appName} -> $userImportance (error: ${(error * 100).toInt()}%)")
    }
    
    /**
     * Batch train on collected data
     * Should be called periodically (e.g., daily) or when enough samples are collected
     */
    suspend fun batchTrain(epochs: Int = 5) = withContext(Dispatchers.Default) {
        val trainingSamples = trainingDataManager.getAllTrainingSamples()
        
        if (trainingSamples.isEmpty()) {
            Log.d(TAG, "No training samples available")
            return@withContext
        }
        
        Log.d(TAG, "Starting batch training with ${trainingSamples.size} samples, $epochs epochs")
        
        var totalLoss = 0f
        var correctPredictions = 0
        
        repeat(epochs) { epoch ->
            // Shuffle samples
            val shuffled = trainingSamples.shuffled()
            var epochLoss = 0f
            
            for (sample in shuffled) {
                val features = featureExtractor.extractFeatures(
                    sample.notification,
                    sample.conversationHistory
                )
                
                val target = when (sample.userImportance) {
                    NotificationImportance.URGENT -> 1f
                    NotificationImportance.NORMAL -> 0.5f
                    NotificationImportance.IGNORE -> 0f
                }
                
                val prediction = sigmoid(computeLogit(features))
                val error = target - prediction
                
                // Update weights
                for (i in features.indices) {
                    weights[i] += LEARNING_RATE * error * features[i]
                }
                bias += LEARNING_RATE * error
                
                epochLoss += error * error
                
                // Check accuracy
                val predictedClass = when {
                    prediction >= 0.75f -> NotificationImportance.URGENT
                    prediction >= 0.35f -> NotificationImportance.NORMAL
                    else -> NotificationImportance.IGNORE
                }
                if (predictedClass == sample.userImportance) {
                    correctPredictions++
                }
            }
            
            totalLoss += epochLoss / shuffled.size
            Log.d(TAG, "Epoch ${epoch + 1}/$epochs - Loss: ${epochLoss / shuffled.size}")
        }
        
        modelAccuracy = correctPredictions.toFloat() / (trainingSamples.size * epochs)
        lastTrainingTime = System.currentTimeMillis()
        samplesSinceLastBatchTraining = 0 // Reset counter after batch training
        
        saveModelWeights()
        
        Log.d(TAG, "Batch training complete. Accuracy: ${(modelAccuracy * 100).toInt()}%")
    }
    
    /**
     * Get model statistics
     */
    fun getModelStats(): MLModelStats {
        return MLModelStats(
            totalTrainingSamples = totalTrainingSamples,
            lastTrainingTime = lastTrainingTime,
            modelAccuracy = modelAccuracy,
            isModelTrained = totalTrainingSamples > 0
        )
    }
    
    /**
     * Reset model to initial state
     */
    suspend fun resetModel() = withContext(Dispatchers.IO) {
        weights = FloatArray(NotificationFeatureExtractor.FEATURE_COUNT) { 
            (Math.random().toFloat() - 0.5f) * 0.1f 
        }
        bias = 0f
        totalTrainingSamples = 0
        lastTrainingTime = 0L
        modelAccuracy = 0f
        samplesSinceLastBatchTraining = 0
        saveModelWeights()
        trainingDataManager.clearAllData()
    }
    
    // Private helper functions
    
    private fun computeLogit(features: FloatArray): Float {
        var sum = bias
        for (i in features.indices) {
            sum += features[i] * weights[i]
        }
        return sum
    }
    
    private fun sigmoid(x: Float): Float {
        return 1f / (1f + exp(-x.toDouble()).toFloat())
    }
    
    private fun loadModelWeights() {
        try {
            val prefs = context.getSharedPreferences("ml_model", Context.MODE_PRIVATE)
            
            // Load weights
            for (i in weights.indices) {
                weights[i] = prefs.getFloat("weight_$i", weights[i])
            }
            bias = prefs.getFloat("bias", bias)
            
            // Load metadata
            totalTrainingSamples = prefs.getInt("total_samples", 0)
            lastTrainingTime = prefs.getLong("last_training", 0L)
            modelAccuracy = prefs.getFloat("accuracy", 0f)
            samplesSinceLastBatchTraining = prefs.getInt("samples_since_batch", 0)
            
            Log.d(TAG, "Loaded ML model with $totalTrainingSamples training samples")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model weights", e)
        }
    }
    
    private fun saveModelWeights() {
        try {
            val prefs = context.getSharedPreferences("ml_model", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Save weights
            for (i in weights.indices) {
                editor.putFloat("weight_$i", weights[i])
            }
            editor.putFloat("bias", bias)
            
            // Save metadata
            editor.putInt("total_samples", totalTrainingSamples)
            editor.putLong("last_training", lastTrainingTime)
            editor.putFloat("accuracy", modelAccuracy)
            editor.putInt("samples_since_batch", samplesSinceLastBatchTraining)
            
            editor.apply()
            
            Log.d(TAG, "Saved ML model weights")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving model weights", e)
        }
    }
}

data class MLModelStats(
    val totalTrainingSamples: Int,
    val lastTrainingTime: Long,
    val modelAccuracy: Float,
    val isModelTrained: Boolean
)

