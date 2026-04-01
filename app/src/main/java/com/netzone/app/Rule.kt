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
    fun isBlocked(now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        return (isEnabled && (wifiBlocked ||
            mobileBlocked)) ||
            isScheduleBlockingNow(now)
    }

    fun isScheduleBlockingNow(now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!isEnabled || !isScheduleEnabled || daysToBlock == 0) return false

        val start = startTimeMinutes ?: return false
        val end = endTimeMinutes ?: return false
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val dayMask = 1 shl (now.get(java.util.Calendar.DAY_OF_WEEK) - 1)

        return if (start <= end) {
            (daysToBlock and dayMask) != 0 && currentMinutes in start..end
        } else {
            val previousDay = (now.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            val previousDayMask = 1 shl (previousDay.get(java.util.Calendar.DAY_OF_WEEK) - 1)

            ((daysToBlock and dayMask) != 0 && currentMinutes >= start) ||
                ((daysToBlock and previousDayMask) != 0 && currentMinutes <= end)
        }
    }

    fun hasCustomRestriction(): Boolean {
        return wifiBlocked ||
            mobileBlocked ||
            isScheduleEnabled ||
            dailyLimitMinutes > 0
    }

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
            val previousDay = (checkDay.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            val previousDayMask = 1 shl (previousDay.get(java.util.Calendar.DAY_OF_WEEK) - 1)
            val crossesMidnight = endTimeMinutes < startTimeMinutes

            if ((daysToBlock and dayMask) != 0) {
                val transitions = mutableListOf(startTimeMinutes)

                if (!crossesMidnight) {
                    // We add 1 to endTimeMinutes because it's inclusive in NetZoneVpnService.
                    transitions += (endTimeMinutes + 1) % 1440
                }

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

            if (crossesMidnight && (daysToBlock and previousDayMask) != 0) {
                val overnightEnd = (endTimeMinutes + 1) % 1440
                val transitionTime = (checkDay.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, overnightEnd / 60)
                    set(java.util.Calendar.MINUTE, overnightEnd % 60)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val time = transitionTime.timeInMillis
                if (time > currentMillis && time < minTime) {
                    minTime = time
                }
            }
        }

        return if (minTime == Long.MAX_VALUE) null else minTime
    }
}
