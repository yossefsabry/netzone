package com.netzone.app.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netzone.app.AppDiscoveryService
import com.netzone.app.AppDiscoveryState
import com.netzone.app.AppMetadataDao
import com.netzone.app.AppSortMode
import com.netzone.app.LogDao
import com.netzone.app.NetZoneVpnService
import com.netzone.app.PreferenceManager
import com.netzone.app.Rule
import com.netzone.app.RuleRepository
import com.netzone.app.startVpnServiceCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppsEmptyState {
    INITIAL_LOADING,
    DISCOVERY_FAILURE,
    NO_DISCOVERED_APPS,
    NO_MATCHES
}

data class AppsUiState(
    val rows: List<AppListRow> = emptyList(),
    val sortMode: AppSortMode = AppSortMode.SMART,
    val isVpnRunning: Boolean = false,
    val isDarkMode: Boolean = false,
    val isSyncingApps: Boolean = false,
    val appSyncError: String? = null,
    val isInitialLoading: Boolean = true,
    val hasNoDiscoveredApps: Boolean = false,
    val emptyState: AppsEmptyState? = AppsEmptyState.INITIAL_LOADING,
    val actionErrorMessage: String? = null,
    val searchQuery: String = "",
    val showOnlyBlocked: Boolean = false,
    val showOnlySystem: Boolean = true
)

@HiltViewModel
class AppsViewModel private constructor(
    dependencies: Dependencies
) : ViewModel() {

    private val dependencies = dependencies
    private val repository = dependencies.repository
    private val setAppSortMode = dependencies.setAppSortMode
    private val setManageSystemApps = dependencies.setManageSystemApps
    private val setDarkMode = dependencies.setDarkMode
    private val toggleVpnAction = dependencies.toggleVpnAction
    private val refreshInstalledAppsAction = dependencies.refreshInstalledAppsAction
    private val updateRuleAction = dependencies.updateRuleAction

    @Inject
    constructor(
        repository: RuleRepository,
        appMetadataDao: AppMetadataDao,
        preferenceManager: PreferenceManager,
        logDao: LogDao,
        appDiscoveryService: AppDiscoveryService,
        @ApplicationContext context: Context
    ) : this(
        Dependencies(
            repository = repository,
            appMetadataDao = appMetadataDao,
            appSortMode = preferenceManager.appSortMode,
            setAppSortMode = preferenceManager::setAppSortMode,
            manageSystemApps = preferenceManager.manageSystemApps,
            setManageSystemApps = preferenceManager::setManageSystemApps,
            isDarkMode = preferenceManager.isDarkMode,
            setDarkMode = preferenceManager::setDarkMode,
            recentPackages = logDao.getRecentPackageNames(System.currentTimeMillis() - 24 * 60 * 60 * 1000),
            vpnRunning = NetZoneVpnService.isRunning,
            toggleVpnAction = { start -> context.startVpnServiceCompat(start) },
            appDiscoveryState = appDiscoveryService.state,
            refreshInstalledAppsAction = appDiscoveryService::syncInstalledApps,
            updateRuleAction = repository::updateRule
        )
    )

    internal constructor(
        repository: RuleRepository,
        appMetadataDao: AppMetadataDao,
        appSortMode: Flow<AppSortMode>,
        setAppSortMode: suspend (AppSortMode) -> Unit,
        manageSystemApps: Flow<Boolean>,
        setManageSystemApps: suspend (Boolean) -> Unit,
        isDarkMode: Flow<Boolean>,
        setDarkMode: suspend (Boolean) -> Unit,
        recentPackages: Flow<List<String>>,
        vpnRunning: StateFlow<Boolean>,
        toggleVpnAction: suspend (Boolean) -> Unit,
        appDiscoveryState: StateFlow<AppDiscoveryState>,
        refreshInstalledAppsAction: suspend () -> Unit,
        updateRuleAction: suspend (Rule) -> Unit
    ) : this(
        Dependencies(
            repository = repository,
            appMetadataDao = appMetadataDao,
            appSortMode = appSortMode,
            setAppSortMode = setAppSortMode,
            manageSystemApps = manageSystemApps,
            setManageSystemApps = setManageSystemApps,
            isDarkMode = isDarkMode,
            setDarkMode = setDarkMode,
            recentPackages = recentPackages,
            vpnRunning = vpnRunning,
            toggleVpnAction = toggleVpnAction,
            appDiscoveryState = appDiscoveryState,
            refreshInstalledAppsAction = refreshInstalledAppsAction,
            updateRuleAction = updateRuleAction
        )
    )

    private val searchQuery = MutableStateFlow("")
    private val showOnlyBlocked = MutableStateFlow(false)
    private val actionErrorMessage = MutableStateFlow<String?>(null)
    private val appsFlow = dependencies.appMetadataDao.getAllApps()
    private val manageSystemAppsState = dependencies.manageSystemApps.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        (dependencies.manageSystemApps as? StateFlow<Boolean>)?.value ?: true
    )
    private val darkModeState = dependencies.isDarkMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        (dependencies.isDarkMode as? StateFlow<Boolean>)?.value ?: false
    )

    private val filterState = combine(
        searchQuery,
        manageSystemAppsState,
        showOnlyBlocked,
        dependencies.appSortMode
    ) { query, includeSystemApps, blockedOnly, sortMode ->
        AppFilterState(
            searchQuery = query,
            showOnlySystem = includeSystemApps,
            showOnlyBlocked = blockedOnly,
            sortMode = sortMode
        )
    }

    private val listState = combine(
        appsFlow,
        repository.rulesMap,
        filterState,
        dependencies.recentPackages
    ) { apps, rules, filters, recents ->
        AppListState(
            sourceAppCount = apps.size,
            rows = buildAppRows(
                apps = apps,
                rules = rules,
                searchQuery = filters.searchQuery,
                includeSystemApps = filters.showOnlySystem,
                blockedOnly = filters.showOnlyBlocked,
                sortMode = filters.sortMode,
                recentPackages = recents
            ),
            sortMode = filters.sortMode,
            searchQuery = filters.searchQuery,
            showOnlyBlocked = filters.showOnlyBlocked,
            showOnlySystem = filters.showOnlySystem
        )
    }

    private val pageState = combine(
        dependencies.vpnRunning,
        darkModeState,
        dependencies.appDiscoveryState
    ) { isVpnRunning, isDarkModeEnabled, discovery ->
        AppPageState(
            isVpnRunning = isVpnRunning,
            isDarkMode = isDarkModeEnabled,
            isSyncingApps = discovery.isSyncing,
            appSyncError = discovery.errorMessage,
            hasCompletedDiscovery = discovery.lastSyncSucceeded || discovery.errorMessage != null,
            hasNoDiscoveredApps = discovery.lastSyncSucceeded && discovery.appCount == 0 && !discovery.isSyncing
        )
    }

    private val _uiState = MutableStateFlow(buildInitialUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                listState,
                pageState,
                actionErrorMessage
        ) { list, page, actionError ->
                AppsUiState(
                    rows = list.rows,
                    sortMode = list.sortMode,
                    isVpnRunning = page.isVpnRunning,
                    isDarkMode = page.isDarkMode,
                    isSyncingApps = page.isSyncingApps,
                    appSyncError = page.appSyncError,
                    isInitialLoading = list.sourceAppCount == 0 && !page.hasCompletedDiscovery,
                    hasNoDiscoveredApps = page.hasNoDiscoveredApps,
                    emptyState = resolveEmptyState(
                        isInitialLoading = list.sourceAppCount == 0 && !page.hasCompletedDiscovery,
                        appSyncError = page.appSyncError,
                        isSyncingApps = page.isSyncingApps,
                        hasNoDiscoveredApps = page.hasNoDiscoveredApps,
                        sourceAppCount = list.sourceAppCount,
                        rows = list.rows
                    ),
                    actionErrorMessage = actionError,
                    searchQuery = list.searchQuery,
                    showOnlyBlocked = list.showOnlyBlocked,
                    showOnlySystem = list.showOnlySystem
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun toggleFilterBlocked() {
        showOnlyBlocked.update { !it }
    }

    fun toggleFilterSystem() {
        runAction {
            setManageSystemApps(!manageSystemAppsState.value)
        }
    }

    fun setSortMode(sortMode: AppSortMode) {
        runAction {
            setAppSortMode(sortMode)
        }
    }

    fun toggleDarkMode() {
        runAction {
            setDarkMode(!darkModeState.value)
        }
    }

    fun toggleVpn(start: Boolean) {
        runAction {
            toggleVpnAction(start)
        }
    }

    fun refreshInstalledApps() {
        runAction {
            refreshInstalledAppsAction()
        }
    }

    fun consumeActionErrorMessage() {
        actionErrorMessage.value = null
    }

    fun updateRule(rule: Rule) {
        runAction {
            updateRuleAction(rule)
        }
    }

    fun updateRule(
        packageName: String,
        appName: String,
        uid: Int,
        transform: (Rule) -> Rule
    ) {
        runAction {
            repository.updateRule(
                packageName = packageName,
                appName = appName,
                uid = uid,
                transform = transform
            )
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            actionErrorMessage.value = null
            runCatching { action() }
                .onFailure { error ->
                    actionErrorMessage.value = error.message ?: "Unable to complete action"
                }
        }
    }

    private fun buildInitialUiState(): AppsUiState {
        val apps = (appsFlow as? StateFlow<List<com.netzone.app.AppMetadata>>)?.value
        val rules = repository.rulesMap.value
        val sortMode = (dependencies.appSortMode as? StateFlow<AppSortMode>)?.value ?: AppSortMode.SMART
        val recentPackages = (dependencies.recentPackages as? StateFlow<List<String>>)?.value.orEmpty()
        val showSystem = (dependencies.manageSystemApps as? StateFlow<Boolean>)?.value ?: true
        val isDarkMode = (dependencies.isDarkMode as? StateFlow<Boolean>)?.value ?: false
        val discovery = dependencies.appDiscoveryState.value
        val rows = apps?.let {
            buildAppRows(
                apps = it,
                rules = rules,
                searchQuery = searchQuery.value,
                includeSystemApps = showSystem,
                blockedOnly = showOnlyBlocked.value,
                sortMode = sortMode,
                recentPackages = recentPackages
            )
        }.orEmpty()

        return AppsUiState(
            rows = rows,
            sortMode = sortMode,
            isVpnRunning = dependencies.vpnRunning.value,
            isDarkMode = isDarkMode,
            isSyncingApps = discovery.isSyncing,
            appSyncError = discovery.errorMessage,
            isInitialLoading = (apps == null || apps.isEmpty()) && !discovery.lastSyncSucceeded && discovery.errorMessage == null,
            hasNoDiscoveredApps = discovery.lastSyncSucceeded && discovery.appCount == 0 && !discovery.isSyncing,
            emptyState = resolveEmptyState(
                isInitialLoading = (apps == null || apps.isEmpty()) && !discovery.lastSyncSucceeded && discovery.errorMessage == null,
                appSyncError = discovery.errorMessage,
                isSyncingApps = discovery.isSyncing,
                hasNoDiscoveredApps = discovery.lastSyncSucceeded && discovery.appCount == 0 && !discovery.isSyncing,
                sourceAppCount = apps?.size ?: 0,
                rows = rows
            ),
            actionErrorMessage = null,
            searchQuery = searchQuery.value,
            showOnlyBlocked = showOnlyBlocked.value,
            showOnlySystem = showSystem
        )
    }

    private data class AppListState(
        val sourceAppCount: Int,
        val rows: List<AppListRow>,
        val sortMode: AppSortMode,
        val searchQuery: String,
        val showOnlyBlocked: Boolean,
        val showOnlySystem: Boolean
    )

    private data class AppFilterState(
        val searchQuery: String,
        val showOnlySystem: Boolean,
        val showOnlyBlocked: Boolean,
        val sortMode: AppSortMode
    )

    private data class AppPageState(
        val isVpnRunning: Boolean,
        val isDarkMode: Boolean,
        val isSyncingApps: Boolean,
        val appSyncError: String?,
        val hasCompletedDiscovery: Boolean,
        val hasNoDiscoveredApps: Boolean
    )

    private data class Dependencies(
        val repository: RuleRepository,
        val appMetadataDao: AppMetadataDao,
        val appSortMode: Flow<AppSortMode>,
        val setAppSortMode: suspend (AppSortMode) -> Unit,
        val manageSystemApps: Flow<Boolean>,
        val setManageSystemApps: suspend (Boolean) -> Unit,
        val isDarkMode: Flow<Boolean>,
        val setDarkMode: suspend (Boolean) -> Unit,
        val recentPackages: Flow<List<String>>,
        val vpnRunning: StateFlow<Boolean>,
        val toggleVpnAction: suspend (Boolean) -> Unit,
        val appDiscoveryState: StateFlow<AppDiscoveryState>,
        val refreshInstalledAppsAction: suspend () -> Unit,
        val updateRuleAction: suspend (Rule) -> Unit
    )

    private fun resolveEmptyState(
        isInitialLoading: Boolean,
        appSyncError: String?,
        isSyncingApps: Boolean,
        hasNoDiscoveredApps: Boolean,
        sourceAppCount: Int,
        rows: List<AppListRow>
    ): AppsEmptyState? = when {
        isInitialLoading || (isSyncingApps && sourceAppCount == 0 && rows.isEmpty()) -> AppsEmptyState.INITIAL_LOADING
        appSyncError != null && sourceAppCount == 0 -> AppsEmptyState.DISCOVERY_FAILURE
        hasNoDiscoveredApps -> AppsEmptyState.NO_DISCOVERED_APPS
        rows.isEmpty() -> AppsEmptyState.NO_MATCHES
        else -> null
    }
}
