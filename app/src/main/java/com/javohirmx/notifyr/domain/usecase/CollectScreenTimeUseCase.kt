package com.javohirmx.notifyr.domain.usecase

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.javohirmx.notifyr.data.database.ScreenTimeEntity
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectScreenTimeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenTimeRepository: ScreenTimeRepository
) {
    
    /**
     * Collect usage stats and store them in the database
     * Collects data for the last 24 hours
     */
    suspend operator fun invoke(): Boolean {
        if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
            return false
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.HOUR, -24) // Get last 24 hours
        val startTime = calendar.timeInMillis
        
        // Query usage stats
        val usageStatsMap = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            now
        )?.associateBy { it.packageName } ?: return false
        
        val packageManager = context.packageManager
        val screenTimeEntities = mutableListOf<ScreenTimeEntity>()
        
        // Process usage stats and group by hour
        usageStatsMap.values.forEach { usageStat ->
            val packageName = usageStat.packageName
            
            // Skip system packages
            if (isSystemPackage(packageName)) {
                return@forEach
            }
            
            // Get app name
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
            
            // Calculate time spent in each hour
            val lastTimeUsed = usageStat.lastTimeUsed
            val totalTimeInForeground = usageStat.totalTimeInForeground
            
            if (totalTimeInForeground > 0 && lastTimeUsed >= startTime) {
                // Distribute time across hours
                val hourEntities = distributeTimeAcrossHours(
                    packageName = packageName,
                    appName = appName,
                    startTime = lastTimeUsed - totalTimeInForeground,
                    endTime = lastTimeUsed,
                    totalTime = totalTimeInForeground
                )
                screenTimeEntities.addAll(hourEntities)
            }
        }
        
        // Store in database
        if (screenTimeEntities.isNotEmpty()) {
            screenTimeRepository.insertScreenTimeList(screenTimeEntities)
        }
        
        return true
    }
    
    /**
     * Distribute usage time across hours
     */
    private fun distributeTimeAcrossHours(
        packageName: String,
        appName: String,
        startTime: Long,
        endTime: Long,
        totalTime: Long
    ): List<ScreenTimeEntity> {
        val entities = mutableListOf<ScreenTimeEntity>()
        val calendar = Calendar.getInstance()
        
        var currentTime = startTime
        var remainingTime = totalTime
        
        while (currentTime < endTime && remainingTime > 0) {
            calendar.timeInMillis = currentTime
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Get end of current hour
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            val hourEnd = calendar.timeInMillis
            
            // Calculate time spent in this hour
            val timeInThisHour = minOf(
                hourEnd - currentTime,
                remainingTime,
                endTime - currentTime
            )
            
            if (timeInThisHour > 0) {
                // Get day start timestamp
                calendar.timeInMillis = currentTime
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                
                entities.add(
                    ScreenTimeEntity(
                        packageName = packageName,
                        appName = appName,
                        date = dayStart,
                        hour = hour,
                        durationMs = timeInThisHour
                    )
                )
            }
            
            currentTime = hourEnd
            remainingTime -= timeInThisHour
        }
        
        return entities
    }
    
    /**
     * Check if a package is a system package
     */
    private fun isSystemPackage(packageName: String): Boolean {
        val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.qualcomm",
            context.packageName // Exclude our own app
        )
        
        return systemPackages.any { packageName.startsWith(it) }
    }
}

