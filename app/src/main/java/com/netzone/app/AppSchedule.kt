package com.netzone.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_schedules")
data class AppSchedule(
    @PrimaryKey val packageName: String,
    val appName: String,
    val startTimeMinutes: Int, // Minutes from midnight (e.g., 5 PM = 17 * 60 = 1020)
    val endTimeMinutes: Int,   // Minutes from midnight (e.g., 10 PM = 22 * 60 = 1320)
    val isEnabled: Boolean = true
)

@Dao
interface AppScheduleDao {
    @Query("SELECT * FROM app_schedules")
    fun getAllSchedules(): Flow<List<AppSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: AppSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: AppSchedule)
    
    @Query("DELETE FROM app_schedules WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
