package com.netzone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleTest {
    @Test
    fun testRuleDefaultValues() {
        val rule = Rule(packageName = "com.test.app", appName = "Test App", uid = 1234)
        assertEquals(0b1111111, rule.daysToBlock)
        assertEquals(0, rule.dailyLimitMinutes)
    }

    @Test
    fun testRuleCustomValues() {
        val rule = Rule(
            packageName = "com.test.app",
            appName = "Test App",
            uid = 1234,
            daysToBlock = 0b0101010,
            dailyLimitMinutes = 60
        )
        assertEquals(0b0101010, rule.daysToBlock)
        assertEquals(60, rule.dailyLimitMinutes)
    }

    @Test
    fun testGetNextTransitionTimeMillis() {
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentMillis = now.timeInMillis
        
        // Schedule: 12:00 to 14:00 daily
        val rule = Rule(
            packageName = "com.test.app",
            appName = "Test App",
            uid = 1234,
            isScheduleEnabled = true,
            startTimeMinutes = 12 * 60, // 12:00
            endTimeMinutes = 14 * 60,   // 14:00
            daysToBlock = 0b1111111     // Daily
        )
        
        val nextTime = rule.getNextTransitionTimeMillis(now)
        assert(nextTime != null)
        // From 10:00 to 12:00 is 2 hours = 120 minutes = 120 * 60 * 1000 ms
        assertEquals(currentMillis + 120L * 60 * 1000, nextTime!!)
    }

    @Test
    fun testGetNextTransitionTimeMillis_MidnightCrossing() {
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentMillis = now.timeInMillis
        
        // Schedule: 22:00 to 02:00 daily
        val rule = Rule(
            packageName = "com.test.app",
            appName = "Test App",
            uid = 1234,
            isScheduleEnabled = true,
            startTimeMinutes = 22 * 60, // 22:00
            endTimeMinutes = 2 * 60,    // 02:00
            daysToBlock = 0b1111111     // Daily
        )
        
        // At 00:30, the next transition is at 02:01 (end of block)
        // From 00:30 to 02:01 is 1 hour and 31 minutes = 91 minutes
        val nextTime = rule.getNextTransitionTimeMillis(now)
        assert(nextTime != null)
        assertEquals(currentMillis + 91L * 60 * 1000, nextTime!!)
    }
}
