package com.netzone.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnServiceLauncherTest {

    @Test
    fun shouldStartVpnAsForeground_returnsFalse_whenStartIsFalse() {
        assertFalse(shouldStartVpnAsForeground(start = false, sdkInt = 34))
    }

    @Test
    fun shouldStartVpnAsForeground_returnsFalse_belowApi26() {
        assertFalse(shouldStartVpnAsForeground(start = true, sdkInt = 25))
    }

    @Test
    fun shouldStartVpnAsForeground_returnsTrue_onApi26AndAbove() {
        assertTrue(shouldStartVpnAsForeground(start = true, sdkInt = 26))
        assertTrue(shouldStartVpnAsForeground(start = true, sdkInt = 34))
    }
}
