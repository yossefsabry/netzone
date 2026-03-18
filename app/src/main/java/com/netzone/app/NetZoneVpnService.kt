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
    private var isScreenOn = true
    private var lastBlockedPackages: Set<String>? = null

    // Cached instances to avoid allocation churn
    private lateinit var repository: RuleRepository
    private lateinit var usageTracker: UsageTracker
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var logDao: LogDao

    // Drain job reads/discards packets from tunnel fd to prevent buffer overflow
    private var drainJob: Job? = null

    // Debounce reload to coalesce rapid rule/network changes
    private var reloadJob: Job? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null

    companion object {
        private const val TAG = "NetZoneVPN"
        const val ACTION_RELOAD = "com.netzone.app.RELOAD"
        const val ACTION_STOP = "com.netzone.app.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "netzone_vpn_channel"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _uploadSpeed = MutableStateFlow(0L)
        val uploadSpeed: StateFlow<Long> = _uploadSpeed.asStateFlow()

        private val totalBytesRead = java.util.concurrent.atomic.AtomicLong(0L)
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = RuleRepository.getInstance(db.ruleDao())
        usageTracker = UsageTracker(this)
        preferenceManager = PreferenceManager(this)
        logDao = db.logDao()

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
        observePreferences()
        observeScreenState()
        startSpeedMonitor()
    }

    private fun observeScreenState() {
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        screenReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isScreenOn = intent?.action == Intent.ACTION_SCREEN_ON
                debounceReload()
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startSpeedMonitor() {
        serviceScope.launch {
            var lastBytes = 0L
            while (isActive) {
                delay(1000)
                val currentBytes = totalBytesRead.get()
                _uploadSpeed.value = currentBytes - lastBytes
                lastBytes = currentBytes
            }
        }
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

    private fun observePreferences() {
        preferenceManager.isLockdown
            .onEach { debounceReload() }
            .launchIn(serviceScope)

        preferenceManager.customDns
            .onEach { debounceReload() }
            .launchIn(serviceScope)
    }

    private fun observeNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState(cm.getNetworkCapabilities(network))
                debounceReload()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkState(caps)
                debounceReload()
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
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
        val isLockdown = preferenceManager.isLockdown.first()
        val blockScreenOff = preferenceManager.blockWhenScreenOff.first()
        val now = Calendar.getInstance()
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val dayMask = 1 shl (currentDayOfWeek - 1)
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Reuse cached usageTracker instead of creating a new one every time
        val allUsage = usageTracker.getAllTodayUsageMinutes()
        val blockedPackages = mutableListOf<String>()
        val blockedAppDetails = mutableListOf<Rule>()

        for (rule in rules) {
            var shouldBlock = false

            if (isLockdown || (blockScreenOff && !isScreenOn)) {
                shouldBlock = true
            } else {
                if (!rule.isEnabled) continue

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
            }

            if (shouldBlock) {
                blockedPackages.add(rule.packageName)
                blockedAppDetails.add(rule)
            }
        }

        // Log blocked apps for the Access Log screen (only when they change)
        val blockedSet = blockedPackages.toSet()
        if (blockedSet != lastBlockedPackages && blockedSet.isNotEmpty()) {
            serviceScope.launch(Dispatchers.IO) {
                blockedAppDetails.forEach { rule ->
                    logDao.insert(LogEntry(
                        packageName = rule.packageName,
                        appName = rule.appName,
                        uid = rule.uid,
                        blocked = true
                    ))
                }
            }
        }

        withContext(Dispatchers.Main) {
            updateVpn(blockedPackages)
        }
    }

    private suspend fun updateVpn(blockedPackages: List<String>) {
        val blockedSet = blockedPackages.toSet().minus(packageName)
        val customDns = preferenceManager.customDns.first()

        if (vpnInterface != null && blockedSet == lastBlockedPackages) {
            return
        }

        // Delay closing old interface and cancelling drainJob for atomic handover
        val oldInterface = vpnInterface
        val oldDrainJob = drainJob

        try {
            val newInterface = withContext(Dispatchers.IO) {
                val builder = Builder()
                    .setSession("NetZone")
                    .addAddress("10.0.0.2", 32)
                    .addAddress("fd00:1::2", 128)
                    .setMtu(1500)

                // Apply custom DNS
                try {
                    if (customDns.contains(":")) {
                        builder.addDnsServer(customDns)
                    } else {
                        builder.addDnsServer(customDns)
                        // Add secondary common DNS if only one is provided
                        if (customDns == "8.8.8.8") builder.addDnsServer("8.8.4.4")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid DNS: $customDns", e)
                    builder.addDnsServer("8.8.8.8")
                }

                // Only add routes and allowed apps if we have apps to block.
                // If empty, the VPN interface is still established but no traffic is routed to it.
                // This maintains the VPN session and allows for a seamless handover when apps
                // are subsequently blocked/unblocked.
                if (blockedSet.isNotEmpty()) {
                    builder.addRoute("0.0.0.0", 0)
                    builder.addRoute("::", 0)

                    for (pkg in blockedSet) {
                        try {
                            builder.addAllowedApplication(pkg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to add allowed application $pkg", e)
                        }
                    }
                } else {
                    Log.i(TAG, "Establishing VPN with 0 blocked apps (transparent mode)")
                }

                // Add configure intent so tapping notification opens the app
                val configureIntent = Intent(this@NetZoneVpnService, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this@NetZoneVpnService, 0, configureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setConfigureIntent(pendingIntent)

                builder.establish()
            }

            if (newInterface != null) {
                // Handover: Update references and start new drain job
                vpnInterface = newInterface
                lastBlockedPackages = blockedSet
                
                // Start new drain job first
                drainJob = serviceScope.launch {
                    drainTunnel(newInterface)
                }

                // Clean up old resources after handover is complete
                oldDrainJob?.cancel()
                oldInterface?.close()

                val notificationText = if (blockedSet.isEmpty()) {
                    "Firewall active: 0 apps blocked"
                } else {
                    "Firewall active: ${blockedSet.size} apps blocked"
                }
                updateNotification(notificationText)
                Log.i(TAG, "VPN established (atomic), blocking ${blockedSet.size} apps")
            } else {
                Log.e(TAG, "VPN establish() returned null — user may not have granted permission")
                updateNotification("Firewall: waiting for VPN permission")
                // Keep the old one if it still exists
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            updateNotification("Firewall Error: ${e.message}")
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
                    
                    totalBytesRead.addAndGet(n.toLong())
                    
                    // Parse packet for logging
                    val parsed = PacketParser.parse(buffer, n)
                    if (parsed != null) {
                        logConnection(parsed)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel read error", e)
            }
        }
    }

    private fun logConnection(packet: ParsedPacket) {
        serviceScope.launch(Dispatchers.IO) {
            // Heuristic: log which app is likely sending to the sinkhole
            val rules = repository.rulesMap.value.values
            val possibleApp = rules.firstOrNull { it.wifiBlocked || it.mobileBlocked || it.isScheduleEnabled }
            
            logDao.insert(LogEntry(
                packageName = possibleApp?.packageName ?: "Unknown",
                appName = possibleApp?.appName ?: "Network Traffic",
                uid = possibleApp?.uid ?: 0,
                protocol = when(packet.protocol) {
                    6 -> "TCP"
                    17 -> "UDP"
                    1 -> "ICMP"
                    else -> "Prot:${packet.protocol}"
                },
                sourceAddress = packet.sourceAddress,
                sourcePort = packet.sourcePort,
                destinationAddress = packet.destinationAddress,
                destinationPort = packet.destinationPort,
                blocked = true
            ))
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
        
        // Unregister resources
        try {
            networkCallback?.let {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
        
        try {
            screenReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister screen receiver", e)
        }

        drainJob?.cancel()
        vpnInterface?.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
