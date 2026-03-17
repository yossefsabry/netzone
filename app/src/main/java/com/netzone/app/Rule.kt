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
    val dailyLimitMinutes: Int = 0 // 0 = No limit
)
