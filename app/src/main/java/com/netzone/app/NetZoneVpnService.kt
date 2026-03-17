package com.netzone.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.FileInputStream
import java.util.*

class NetZoneVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    private var isWiFi = false
    private var isMobile = false
    private var lastBlockedPackages: Set<String>? = null

    // Cached instances to avoid allocation churn
    private lateinit var repository: RuleRepository
    private lateinit var usageTracker: UsageTracker

    // Drain job reads/discards packets from tunnel fd to prevent buffer overflow
    private var drainJob: Job? = null

    // Debounce reload to coalesce rapid rule/network changes
    private var reloadJob: Job? = null

    companion object {
        private const val TAG = "NetZoneVPN"
        const val ACTION_RELOAD = "com.netzone.app.RELOAD"
        const val ACTION_STOP = "com.netzone.app.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "netzone_vpn_channel"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = RuleRepository.getInstance(db.ruleDao())
        usageTracker = UsageTracker(this)

        createNotificationChannel()

        val notification = createNotification("Starting firewall...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // Mark as running only after foreground is established
        _isRunning.value = true

        observeNetwork()
        observeRules()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        debounceReload()
        return START_STICKY
    }

    private fun stopVpn() {
        drainJob?.cancel()
        drainJob = null
        vpnInterface?.close()
        vpnInterface = null
        lastBlockedPackages = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        _isRunning.value = false
        stopSelf()
    }

    private fun observeRules() {
        repository.rulesMap
            .onEach { debounceReload() }
            .launchIn(serviceScope)
    }

    private fun observeNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState(cm.getNetworkCapabilities(network))
                debounceReload()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkState(caps)
                debounceReload()
            }
        })
    }

    /**
     * Debounces reloadRules() calls to avoid rapid VPN rebuilds.
     * Multiple triggers within 500ms are coalesced into a single reload.
     */
    private fun debounceReload() {
        reloadJob?.cancel()
        reloadJob = serviceScope.launch {
            delay(500)
            reloadRules()
        }
    }

    private fun updateNetworkState(caps: NetworkCapabilities?) {
        isWiFi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        isConnected = isWiFi || isMobile
    }

    private suspend fun reloadRules() {
        val rules = repository.rulesMap.value.values
        val now = Calendar.getInstance()
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val dayMask = 1 shl (currentDayOfWeek - 1)
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Reuse cached usageTracker instead of creating a new one every time
        val allUsage = usageTracker.getAllTodayUsageMinutes()
        val blockedPackages = mutableListOf<String>()

        for (rule in rules) {
            if (!rule.isEnabled) continue

            var shouldBlock = false

            // Per-network blocking
            if (isWiFi && rule.wifiBlocked) shouldBlock = true
            if (isMobile && rule.mobileBlocked) shouldBlock = true

            // Schedule blocking
            if (!shouldBlock && rule.isScheduleEnabled && rule.startTimeMinutes != null && rule.endTimeMinutes != null) {
                val isDaySelected = (rule.daysToBlock and dayMask) != 0
                if (isDaySelected && isCurrentTimeInRange(currentMinutes, rule.startTimeMinutes, rule.endTimeMinutes)) {
                    shouldBlock = true
                }
            }

            // Daily usage limit
            if (!shouldBlock && rule.dailyLimitMinutes > 0) {
                val usage = allUsage[rule.packageName] ?: 0
                if (usage >= rule.dailyLimitMinutes) {
                    shouldBlock = true
                }
            }

            if (shouldBlock) {
                blockedPackages.add(rule.packageName)
            }
        }

        withContext(Dispatchers.Main) {
            updateVpn(blockedPackages)
        }
    }

    private fun updateVpn(blockedPackages: List<String>) {
        val blockedSet = blockedPackages.toSet()
        if (vpnInterface != null && blockedSet == lastBlockedPackages) {
            return
        }

        // Stop existing drain and close old interface
        drainJob?.cancel()
        drainJob = null
        vpnInterface?.close()
        vpnInterface = null
        lastBlockedPackages = blockedSet

        if (blockedSet.isEmpty()) {
            updateNotification("Firewall active: 0 apps blocked")
            return
        }

        try {
            val builder = Builder()
                .setSession("NetZone")
                .addAddress("10.0.0.2", 32)
                .addAddress("fd00:1::2", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setMtu(1500)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addDnsServer("2001:4860:4860::8888")
                .addDnsServer("2001:4860:4860::8844")

            // CRITICAL: Exclude ourselves to prevent recursive VPN routing
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude self from VPN: ${e.message}")
            }

            for (pkg in blockedSet) {
                try {
                    builder.addAllowedApplication(pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "App $pkg not found, skipping")
                }
            }

            // Add configure intent so tapping notification opens the app
            val configureIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setConfigureIntent(pendingIntent)

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                // Start draining packets from the tunnel to prevent buffer overflow.
                // Without this, Android kills the VPN service when the fd buffer fills.
                drainJob = serviceScope.launch {
                    drainTunnel(vpnInterface!!)
                }
                updateNotification("Firewall active: ${blockedSet.size} apps blocked")
                Log.i(TAG, "VPN established, blocking ${blockedSet.size} apps")
            } else {
                Log.e(TAG, "VPN establish() returned null — user may not have granted permission")
                updateNotification("Firewall: waiting for VPN permission")
                lastBlockedPackages = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            updateNotification("Firewall Error: ${e.message}")
            lastBlockedPackages = null
        }
    }

    /**
     * Continuously reads and discards packets from the VPN tunnel.
     * This is essential — without a reader, the tunnel fd buffer fills up and
     * Android kills the VPN service. This is the same sinkhole approach NetGuard uses
     * in non-filter mode (their native jni_run does the equivalent in C).
     */
    private suspend fun drainTunnel(pfd: ParcelFileDescriptor) {
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(32767)
            try {
                val inputStream = FileInputStream(pfd.fileDescriptor)
                while (isActive) {
                    val n = inputStream.read(buffer)
                    if (n <= 0) break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel read error", e)
            }
        }
    }

    private fun isCurrentTimeInRange(now: Int, start: Int, end: Int): Boolean {
        return if (start <= end) now in start..end else now >= start || now <= end
    }

    /**
     * Called by Android when the user revokes VPN permission.
     * Gracefully tears down the VPN without crashing.
     */
    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by user")
        drainJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        lastBlockedPackages = null
        serviceScope.cancel()
        stopSelf()
        super.onRevoke()
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NetZone Firewall")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Firewall Status", NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        _isRunning.value = false
        drainJob?.cancel()
        vpnInterface?.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
