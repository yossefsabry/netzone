package com.netzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

class VpnScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "com.netzone.app.UPDATE_VPN") {
            reloadVpn(context)
        }
    }

    companion object {
        private const val TAG = "VpnScheduler"

        fun reloadVpn(context: Context) {
            // Avoid infinite loop when called from service's onStartCommand
            if (context !is NetZoneVpnService) {
                val vpnIntent = Intent(context, NetZoneVpnService::class.java)
                ContextCompat.startForegroundService(context, vpnIntent)
            }
            
            scheduleNextAlarm(context)
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, VpnScheduler::class.java).apply {
                action = "com.netzone.app.UPDATE_VPN"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        private fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, VpnScheduler::class.java).apply {
                action = "com.netzone.app.UPDATE_VPN"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Reduce frequency to 15 minutes as per Task 1 fixes
            val nextTime = SystemClock.elapsedRealtime() + 15 * 60 * 1000
            
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Exact alarm permission not granted, falling back to non-exact", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            }
        }
    }
}
