package com.netaccess.app

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

class UsageTracker(private val context: Context) {

    /**
     * Queries the UsageStatsManager for the total foreground time of all packages
     * since the start of the current day (midnight).
     *
     * @return Map of package name to total usage in minutes.
     */
    fun getAllTodayUsageMinutes(): Map<String, Int> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            
            aggregatedStats.mapValues { (_, stats) ->
                (stats.totalTimeInForeground / (1000 * 60)).toInt()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Queries the UsageStatsManager for the total foreground time of a given package
     * since the start of the current day (midnight).
     *
     * @param packageName The package name to query.
     * @return Total usage in minutes.
     */
    fun getTodayUsageMinutes(packageName: String): Int {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            val stats = aggregatedStats[packageName]
            
            stats?.let {
                (it.totalTimeInForeground / (1000 * 60)).toInt()
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
