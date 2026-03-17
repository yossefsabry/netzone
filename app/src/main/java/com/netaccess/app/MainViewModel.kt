package com.netaccess.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: RuleRepository,
    private val packageManager: PackageManager,
    private val appMetadataDao: AppMetadataDao
) : ViewModel() {
    val rules: Flow<List<Rule>> = repository.getAllRulesFlow()
    
    val searchQuery = MutableStateFlow("")
    val showOnlyBlocked = MutableStateFlow(false)
    val showOnlySystem = MutableStateFlow(true)

    // Reactive filtering pipeline
    val filteredApps: StateFlow<List<AppMetadata>> = combine(
        appMetadataDao.getAllApps(),
        searchQuery,
        showOnlyBlocked,
        showOnlySystem,
        repository.rulesMap
    ) { apps, query, blockedOnly, systemOnly, rulesMap ->
        apps.filter { app ->
            val matchesSearch = query.isEmpty() || app.name.contains(query, ignoreCase = true) || 
                             app.packageName.contains(query, ignoreCase = true)
            val matchesSystem = systemOnly || !app.isSystem
            
            val rule = rulesMap[app.packageName]
            val isBlocked = rule != null && (rule.wifiBlocked || rule.mobileBlocked || rule.isScheduleEnabled)
            val matchesBlocked = !blockedOnly || isBlocked
            
            matchesSearch && matchesSystem && matchesBlocked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        syncAppsInBackground()
    }

    private fun syncAppsInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { appInfo ->
                    AppMetadata(
                        packageName = appInfo.packageName,
                        name = packageManager.getApplicationLabel(appInfo).toString(),
                        uid = appInfo.uid,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.name }
            
            val currentApps = appMetadataDao.getAllApps().first()
            
            val hasChanges = installedApps.size != currentApps.size || 
                installedApps.zip(currentApps).any { (new, old) -> 
                    new.packageName != old.packageName || 
                    new.name != old.name || 
                    new.uid != old.uid || 
                    new.isSystem != old.isSystem 
                }

            if (hasChanges) {
                appMetadataDao.deleteAll()
                appMetadataDao.insertApps(installedApps)
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun toggleFilterBlocked() {
        showOnlyBlocked.value = !showOnlyBlocked.value
    }

    fun toggleFilterSystem() {
        showOnlySystem.value = !showOnlySystem.value
    }

    fun updateRule(rule: Rule) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }
}
