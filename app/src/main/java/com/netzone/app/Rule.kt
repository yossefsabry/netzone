package com.netzone.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val packageName: String,
    val appName: String,
    val uid: Int,
    val wifiBlocked: Boolean = false,
    val mobileBlocked: Boolean = false,
    val roamingBlocked: Boolean = false,
    val screenOnBlocked: Boolean = false,
    val isEnabled: Boolean = true,
    // Schedule fields
    val startTimeMinutes: Int? = null,
    val endTimeMinutes: Int? = null,
    val isScheduleEnabled: Boolean = false,
    val daysToBlock: Int = 0b1111111, // Sun-Sat bitmask
    val dailyLimitMinutes: Int = 0, // 0 = No limit
    val blockedIPs: String = "",
    val blockedPorts: String = ""
) {
    fun getNextTransitionTimeMillis(now: java.util.Calendar = java.util.Calendar.getInstance()): Long? {
        if (!isScheduleEnabled || startTimeMinutes == null || endTimeMinutes == null) return null

        val currentMillis = now.timeInMillis
        var minTime = Long.MAX_VALUE

        // Check next 8 days to ensure we find the next transition
        for (dayOffset in 0..7) {
            val checkDay = (now.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayOfWeek = checkDay.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
            val dayMask = 1 shl (dayOfWeek - 1)

            if ((daysToBlock and dayMask) != 0) {
                // Potential transitions on this day: start of schedule and end of schedule
                // We add 1 to endTimeMinutes because it's inclusive in NetZoneVpnService
                val transitions = listOf(startTimeMinutes, (endTimeMinutes + 1) % 1440)

                for (tMinutes in transitions) {
                    val transitionTime = (checkDay.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.HOUR_OF_DAY, tMinutes / 60)
                        set(java.util.Calendar.MINUTE, tMinutes % 60)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }

                    // If tMinutes was 0 from (endTimeMinutes + 1) % 1440, it might refer to the start of the next day
                    if (tMinutes == 0 && (endTimeMinutes + 1) == 1440) {
                        transitionTime.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }

                    val time = transitionTime.timeInMillis
                    if (time > currentMillis && time < minTime) {
                        minTime = time
                    }
                }
            }
            
            // Also consider the start of the day (00:00) as a transition if the day selection changes
            val startOfDay = (checkDay.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val timeStart = startOfDay.timeInMillis
            if (timeStart > currentMillis && timeStart < minTime) {
                minTime = timeStart
            }
        }

        return if (minTime == Long.MAX_VALUE) null else minTime
    }
}
