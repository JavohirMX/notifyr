package com.javohirmx.notifyr.domain.usecase

import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.model.DailyScreenTime
import com.javohirmx.notifyr.domain.model.ScreenTimeRange
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetScreenTimeUseCase @Inject constructor(
    private val screenTimeRepository: ScreenTimeRepository
) {
    
    suspend operator fun invoke(range: ScreenTimeRange): List<DailyScreenTime> {
        val startDate = range.getStartTimeMillis()
        val endDate = range.getEndTimeMillis()
        return screenTimeRepository.getDailyScreenTime(startDate, endDate)
    }
    
    suspend fun getDailyScreenTime(startDate: Long, endDate: Long): List<DailyScreenTime> {
        return screenTimeRepository.getDailyScreenTime(startDate, endDate)
    }
    
    suspend fun getTotalScreenTime(range: ScreenTimeRange): Long {
        val startDate = range.getStartTimeMillis()
        val endDate = range.getEndTimeMillis()
        return screenTimeRepository.getTotalScreenTime(startDate, endDate)
    }
}

