package com.netzone.app.ui.home

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netzone.app.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isVpnRunning: Boolean = false,
    val isLockdown: Boolean = false,
    val blockedAppsCount: Int = 0,
    val totalBlockedRequests: Long = 0,
    val discoveredAppsCount: Int = 0,
    val isLoading: Boolean = true,
    val hasUsagePermission: Boolean = true,
    val hasExactAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isDarkMode: Boolean = false,
    val isSyncingApps: Boolean = false,
    val appSyncError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: RuleRepository,
    private val preferenceManager: PreferenceManager,
    private val logDao: LogDao,
    private val appMetadataDao: AppMetadataDao,
    private val appDiscoveryService: AppDiscoveryService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                combine(
                    NetZoneVpnService.isRunning,
                    preferenceManager.isLockdown,
                    repository.getAllRulesFlow(),
                    preferenceManager.isDarkMode
                ) { vpnRunning, lockdown, rules, isDarkMode ->
                    QuadState(vpnRunning, lockdown, rules, isDarkMode)
                },
                appMetadataDao.getAllApps(),
                appDiscoveryService.state
            ) { base, apps, discoveryState ->
                val blockedApps = base.rules.count { it.wifiBlocked || it.mobileBlocked }
                HomeUiState(
                    isVpnRunning = base.vpnRunning,
                    isLockdown = base.lockdown,
                    blockedAppsCount = blockedApps,
                    discoveredAppsCount = apps.size,
                    isLoading = false,
                    isDarkMode = base.isDarkMode,
                    isSyncingApps = discoveryState.isSyncing,
                    appSyncError = discoveryState.errorMessage
                )
            }.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isVpnRunning = state.isVpnRunning,
                    isLockdown = state.isLockdown,
                    blockedAppsCount = state.blockedAppsCount,
                    discoveredAppsCount = state.discoveredAppsCount,
                    isLoading = state.isLoading,
                    isDarkMode = state.isDarkMode,
                    isSyncingApps = state.isSyncingApps,
                    appSyncError = state.appSyncError
                )
            }
        }

        viewModelScope.launch {
            logDao.getRecentPackageNames(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                .collect { recent ->
                    _uiState.update { it.copy(totalBlockedRequests = recent.size.toLong()) }
                }
        }
    }

    private data class QuadState(
        val vpnRunning: Boolean,
        val lockdown: Boolean,
        val rules: List<Rule>,
        val isDarkMode: Boolean
    )

    fun toggleVpn(start: Boolean) {
        viewModelScope.launch {
            val intent = Intent(context, NetZoneVpnService::class.java)
            if (start) {
                context.startForegroundService(intent)
            } else {
                intent.action = NetZoneVpnService.ACTION_STOP
                context.startService(intent)
            }
        }
    }

    fun toggleLockdown() {
        viewModelScope.launch {
            preferenceManager.setLockdown(!_uiState.value.isLockdown)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            preferenceManager.setDarkMode(!_uiState.value.isDarkMode)
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val usageMode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            val hasUsage = usageMode == AppOpsManager.MODE_ALLOWED

            val hasAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else true

            val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true

            _uiState.update {
                it.copy(
                    hasUsagePermission = hasUsage,
                    hasExactAlarmPermission = hasAlarm,
                    hasNotificationPermission = hasNotifications
                )
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            appDiscoveryService.syncInstalledApps()
        }
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

}
