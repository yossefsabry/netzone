package com.netaccess.app

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
}
