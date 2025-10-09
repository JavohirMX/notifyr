package com.javohirmx.notifyr.domain.ml

import android.content.Context
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages training data for ML model
 * Stores user feedback for batch training
 */
@Singleton
class MLTrainingDataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TRAINING_DATA_FILE = "ml_training_data.json"
        private const val MAX_TRAINING_SAMPLES = 1000 // Keep last 1000 samples
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    private val trainingDataFile: File
        get() = File(context.filesDir, TRAINING_DATA_FILE)
    
    /**
     * Add a training sample from user feedback
     */
    suspend fun addTrainingSample(
        notification: NotificationData,
        userImportance: NotificationImportance,
        conversationHistory: List<NotificationData> = emptyList()
    ) = withContext(Dispatchers.IO) {
        try {
            val samples = loadTrainingSamples().toMutableList()
            
            val newSample = TrainingSample(
                notification = notification,
                userImportance = userImportance,
                conversationHistory = conversationHistory.takeLast(10), // Keep last 10 messages
                timestamp = System.currentTimeMillis()
            )
            
            samples.add(newSample)
            
            // Keep only the most recent samples
            if (samples.size > MAX_TRAINING_SAMPLES) {
                samples.sortByDescending { it.timestamp }
                samples.subList(MAX_TRAINING_SAMPLES, samples.size).clear()
            }
            
            saveTrainingSamples(samples)
        } catch (e: Exception) {
            android.util.Log.e("MLTrainingData", "Error adding training sample", e)
        }
    }
    
    /**
     * Get all training samples
     */
    suspend fun getAllTrainingSamples(): List<TrainingSample> = withContext(Dispatchers.IO) {
        loadTrainingSamples()
    }
    
    /**
     * Get training samples for a specific app
     */
    suspend fun getTrainingSamplesForApp(packageName: String): List<TrainingSample> = 
        withContext(Dispatchers.IO) {
            loadTrainingSamples().filter { it.notification.packageName == packageName }
        }
    
    /**
     * Get recent training samples (last N days)
     */
    suspend fun getRecentTrainingSamples(daysAgo: Int = 7): List<TrainingSample> = 
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L)
            loadTrainingSamples().filter { it.timestamp >= cutoffTime }
        }
    
    /**
     * Clear all training data
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            if (trainingDataFile.exists()) {
                trainingDataFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("MLTrainingData", "Error clearing data", e)
        }
    }
    
    /**
     * Get statistics about training data
     */
    suspend fun getTrainingDataStats(): TrainingDataStats = withContext(Dispatchers.IO) {
        val samples = loadTrainingSamples()
        
        val urgentCount = samples.count { it.userImportance == NotificationImportance.URGENT }
        val normalCount = samples.count { it.userImportance == NotificationImportance.NORMAL }
        val ignoreCount = samples.count { it.userImportance == NotificationImportance.IGNORE }
        
        val appCounts = samples
            .groupBy { it.notification.packageName }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(5)
        
        val oldestSample = samples.minByOrNull { it.timestamp }
        val newestSample = samples.maxByOrNull { it.timestamp }
        
        TrainingDataStats(
            totalSamples = samples.size,
            urgentSamples = urgentCount,
            normalSamples = normalCount,
            ignoreSamples = ignoreCount,
            topApps = appCounts.map { it.key to it.value },
            oldestSampleTime = oldestSample?.timestamp,
            newestSampleTime = newestSample?.timestamp
        )
    }
    
    // Private helper functions
    
    private fun loadTrainingSamples(): List<TrainingSample> {
        return try {
            if (!trainingDataFile.exists()) {
                return emptyList()
            }
            
            val jsonString = trainingDataFile.readText()
            json.decodeFromString<List<TrainingSample>>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("MLTrainingData", "Error loading training samples", e)
            emptyList()
        }
    }
    
    private fun saveTrainingSamples(samples: List<TrainingSample>) {
        try {
            val jsonString = json.encodeToString(samples)
            trainingDataFile.writeText(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("MLTrainingData", "Error saving training samples", e)
        }
    }
}

@Serializable
data class TrainingSample(
    val notification: NotificationData,
    val userImportance: NotificationImportance,
    val conversationHistory: List<NotificationData>,
    val timestamp: Long
)

data class TrainingDataStats(
    val totalSamples: Int,
    val urgentSamples: Int,
    val normalSamples: Int,
    val ignoreSamples: Int,
    val topApps: List<Pair<String, Int>>,
    val oldestSampleTime: Long?,
    val newestSampleTime: Long?
)

