package com.netzone.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppDiscoveryState(
    val isSyncing: Boolean = false,
    val appCount: Int = 0,
    val lastSyncSucceeded: Boolean = false,
    val errorMessage: String? = null
)

@Singleton
class AppDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appMetadataDao: AppMetadataDao
) {
    private val _state = MutableStateFlow(AppDiscoveryState())
    val state: StateFlow<AppDiscoveryState> = _state.asStateFlow()

    @Suppress("DEPRECATION")
    suspend fun syncInstalledApps() {
        _state.value = _state.value.copy(isSyncing = true, errorMessage = null)

        runCatching {
            withContext(Dispatchers.IO) {
                val apps = context.packageManager
                    .getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { appInfo ->
                        AppMetadata(
                            packageName = appInfo.packageName,
                            name = context.packageManager.getApplicationLabel(appInfo).toString(),
                            uid = appInfo.uid,
                            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                appMetadataDao.replaceAll(apps)
                apps.size
            }
        }.onSuccess { count ->
            _state.value = AppDiscoveryState(
                isSyncing = false,
                appCount = count,
                lastSyncSucceeded = true,
                errorMessage = null
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                isSyncing = false,
                lastSyncSucceeded = false,
                errorMessage = error.message ?: "App discovery failed"
            )
        }
    }
}
