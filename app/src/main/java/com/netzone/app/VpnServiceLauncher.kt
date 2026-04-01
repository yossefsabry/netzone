package com.netzone.app

import android.content.Context
import android.content.Intent
import android.os.Build

internal fun shouldStartVpnAsForeground(start: Boolean, sdkInt: Int): Boolean {
    return start && sdkInt >= Build.VERSION_CODES.O
}

fun Context.startVpnServiceCompat(start: Boolean) {
    val intent = Intent(this, NetZoneVpnService::class.java)
    if (start) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        return
    }
    intent.action = NetZoneVpnService.ACTION_STOP
    startService(intent)
}
