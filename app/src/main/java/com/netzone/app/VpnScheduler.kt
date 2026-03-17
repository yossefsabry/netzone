package com.netzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VpnScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "com.netzone.app.UPDATE_VPN") {
            reloadVpn(context)
        }
    }

    companion object {
        fun reloadVpn(context: Context) {
            val vpnIntent = Intent(context, NetZoneVpnService::class.java)
            context.startService(vpnIntent)
            
            scheduleNextAlarm(context)
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

            val nextTime = System.currentTimeMillis() + 60 * 1000
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
        }
    }
}
