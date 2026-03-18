package com.netzone.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VpnScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "com.netzone.app.UPDATE_VPN") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    reloadVpn(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "VpnScheduler"

        suspend fun reloadVpn(context: Context) {
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

        private suspend fun scheduleNextAlarm(context: Context) {
            val db = AppDatabase.getDatabase(context)
            val repository = RuleRepository.getInstance(db.ruleDao())
            val allRules = repository.getAllRules()

            var minNextTime = Long.MAX_VALUE
            for (rule in allRules) {
                val nextTime = rule.getNextTransitionTimeMillis()
                if (nextTime != null && nextTime < minNextTime) {
                    minNextTime = nextTime
                }
            }

            val now = System.currentTimeMillis()
            val finalNextTime = if (minNextTime == Long.MAX_VALUE) {
                now + 3600_000L // Fallback to 1-hour keep-alive
            } else {
                minNextTime.coerceAtLeast(now + 15_000L) // Min 15s in the future
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, VpnScheduler::class.java).apply {
                action = "com.netzone.app.UPDATE_VPN"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    finalNextTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled next alarm at $finalNextTime (in ${(finalNextTime - now) / 1000}s)")
            } catch (e: SecurityException) {
                Log.e(TAG, "Exact alarm permission not granted, falling back to non-exact", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    finalNextTime,
                    pendingIntent
                )
            }
        }
    }
}
