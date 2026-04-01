package com.netzone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun isBlocked_only_covers_active_blocking_restrictions() {
        val customOnly = Rule(
            packageName = "com.custom.only",
            appName = "Custom Only",
            uid = 1001,
            dailyLimitMinutes = 30,
            blockedPorts = "443"
        )
        val blocked = Rule(
            packageName = "com.blocked",
            appName = "Blocked",
            uid = 1002,
            mobileBlocked = true
        )

        assertFalse(customOnly.isBlocked())
        assertTrue(blocked.isBlocked())
    }

    @Test
    fun hasCustomRestriction_includes_non_blocking_customizations() {
        val rule = Rule(
            packageName = "com.custom.only",
            appName = "Custom Only",
            uid = 1001,
            dailyLimitMinutes = 30
        )

        assertTrue(rule.hasCustomRestriction())
    }

    @Test
    fun schedule_without_times_is_custom_but_not_active_blocking() {
        val rule = Rule(
            packageName = "com.schedule",
            appName = "Schedule",
            uid = 1003,
            isScheduleEnabled = true
        )

        assertFalse(rule.isBlocked())
        assertTrue(rule.hasCustomRestriction())
    }

    @Test
    fun disabled_rule_is_not_treated_as_blocked() {
        val rule = Rule(
            packageName = "com.disabled",
            appName = "Disabled",
            uid = 1004,
            wifiBlocked = true,
            isEnabled = false
        )

        assertFalse(rule.isBlocked())
        assertTrue(rule.hasCustomRestriction())
    }

    @Test
    fun schedule_with_no_selected_days_is_not_treated_as_blocked() {
        val rule = Rule(
            packageName = "com.schedule.days",
            appName = "Schedule Days",
            uid = 1005,
            isScheduleEnabled = true,
            startTimeMinutes = 22 * 60,
            endTimeMinutes = 2 * 60,
            daysToBlock = 0
        )

        assertFalse(rule.isBlocked())
        assertTrue(rule.hasCustomRestriction())
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

    @Test
    fun testGetNextTransitionTimeMillis_MidnightCrossingSingleWeekday() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.APRIL, 4) // Saturday
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentMillis = now.timeInMillis

        val fridayMask = 1 shl (java.util.Calendar.FRIDAY - 1)
        val rule = Rule(
            packageName = "com.test.app",
            appName = "Test App",
            uid = 1234,
            isScheduleEnabled = true,
            startTimeMinutes = 22 * 60,
            endTimeMinutes = 2 * 60,
            daysToBlock = fridayMask
        )

        val nextTime = rule.getNextTransitionTimeMillis(now)
        assert(nextTime != null)
        assertEquals(currentMillis + 91L * 60 * 1000, nextTime!!)
    }

    @Test
    fun testGetNextTransitionTimeMillis_DoesNotReturnUnrelatedMidnight() {
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.APRIL, 6) // Monday
            set(java.util.Calendar.HOUR_OF_DAY, 15)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentMillis = now.timeInMillis

        val mondayMask = 1 shl (java.util.Calendar.MONDAY - 1)
        val rule = Rule(
            packageName = "com.test.app",
            appName = "Test App",
            uid = 1234,
            isScheduleEnabled = true,
            startTimeMinutes = 12 * 60,
            endTimeMinutes = 14 * 60,
            daysToBlock = mondayMask
        )

        val nextTime = rule.getNextTransitionTimeMillis(now)
        assert(nextTime != null)
        assertEquals(currentMillis + (6L * 24 * 60 + 21L * 60) * 60 * 1000, nextTime!!)
    }
}
