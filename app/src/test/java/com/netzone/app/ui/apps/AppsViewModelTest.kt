package com.netzone.app.ui.apps

import com.netzone.app.AppDiscoveryState
import com.netzone.app.AppMetadata
import com.netzone.app.AppMetadataDao
import com.netzone.app.AppSortMode
import com.netzone.app.LogDao
import com.netzone.app.LogEntry
import com.netzone.app.MainViewModel
import com.netzone.app.Rule
import com.netzone.app.RuleDao
import com.netzone.app.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ui_state_uses_persisted_sort_mode_and_exposes_rows_in_that_order() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(
                AppMetadata("com.beta", "Beta", 2, false),
                AppMetadata("com.alpha", "Alpha", 1, false)
            ),
            sortMode = AppSortMode.UID
        )

        environment.emitApps()
        advanceUntilIdle()

        val state = environment.viewModel.uiState.value

        assertEquals(AppSortMode.UID, state.sortMode)
        assertEquals(listOf(1, 2), state.rows.take(2).map { it.uid })
    }

    @Test
    fun ui_state_maps_page_level_state_from_dependencies() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            sortMode = AppSortMode.NAME,
            isDarkMode = true,
            isVpnRunning = true,
            discoveryState = AppDiscoveryState(
                isSyncing = true,
                appCount = 1,
                lastSyncSucceeded = false,
                errorMessage = "Sync failed"
            )
        )

        environment.emitApps()
        advanceUntilIdle()

        val state = environment.viewModel.uiState.value

        assertTrue(state.isVpnRunning)
        assertTrue(state.isDarkMode)
        assertTrue(state.isSyncingApps)
        assertEquals("Sync failed", state.appSyncError)
        assertFalse(state.isInitialLoading)
        assertFalse(state.hasNoDiscoveredApps)
    }

    @Test
    fun syncing_with_no_usable_rows_shows_initial_loading_state() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = emptyList(),
            discoveryState = AppDiscoveryState(
                isSyncing = true,
                appCount = 0,
                lastSyncSucceeded = false,
                errorMessage = null
            )
        )

        environment.emitApps()
        advanceUntilIdle()

        assertEquals(AppsEmptyState.INITIAL_LOADING, environment.viewModel.uiState.value.emptyState)
    }

    @Test
    fun syncing_with_cached_apps_and_zero_visible_rows_stays_in_no_matches_state() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            discoveryState = AppDiscoveryState(
                isSyncing = true,
                appCount = 1,
                lastSyncSucceeded = true,
                errorMessage = null
            )
        )

        environment.emitApps()
        advanceUntilIdle()
        environment.viewModel.onSearchQueryChange("missing")
        advanceUntilIdle()

        assertEquals(AppsEmptyState.NO_MATCHES, environment.viewModel.uiState.value.emptyState)
    }

    @Test
    fun discovery_success_with_zero_discovered_apps_shows_no_discovered_apps_state() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = emptyList(),
            discoveryState = AppDiscoveryState(
                isSyncing = false,
                appCount = 0,
                lastSyncSucceeded = true,
                errorMessage = null
            )
        )

        environment.emitApps()
        advanceUntilIdle()

        assertEquals(AppsEmptyState.NO_DISCOVERED_APPS, environment.viewModel.uiState.value.emptyState)
    }

    @Test
    fun initial_empty_db_with_default_discovery_state_does_not_show_no_discovered_apps() = runTest(dispatcher) {
        val environment = TestEnvironment(apps = emptyList())

        environment.emitApps()
        advanceUntilIdle()

        assertNotEquals(AppsEmptyState.NO_DISCOVERED_APPS, environment.viewModel.uiState.value.emptyState)
    }

    @Test
    fun ui_state_distinguishes_discovery_failure_from_filtered_empty() = runTest(dispatcher) {
        val discoveryFailure = TestEnvironment(
            apps = emptyList(),
            discoveryState = AppDiscoveryState(
                isSyncing = false,
                appCount = 0,
                lastSyncSucceeded = false,
                errorMessage = "Sync failed"
            )
        )

        discoveryFailure.emitApps()
        advanceUntilIdle()

        assertEquals(AppsEmptyState.DISCOVERY_FAILURE, discoveryFailure.viewModel.uiState.value.emptyState)

        val filteredEmpty = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            discoveryState = AppDiscoveryState(
                isSyncing = false,
                appCount = 1,
                lastSyncSucceeded = true,
                errorMessage = null
            )
        )

        filteredEmpty.emitApps()
        advanceUntilIdle()
        filteredEmpty.viewModel.onSearchQueryChange("missing")
        advanceUntilIdle()

        assertEquals(AppsEmptyState.NO_MATCHES, filteredEmpty.viewModel.uiState.value.emptyState)
    }

    @Test
    fun sync_error_with_cached_apps_and_zero_result_search_shows_no_matches() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            discoveryState = AppDiscoveryState(
                isSyncing = false,
                appCount = 0,
                lastSyncSucceeded = false,
                errorMessage = "Sync failed"
            )
        )

        environment.emitApps()
        advanceUntilIdle()
        environment.viewModel.onSearchQueryChange("missing")
        advanceUntilIdle()

        assertEquals(AppsEmptyState.NO_MATCHES, environment.viewModel.uiState.value.emptyState)
    }

    @Test
    fun update_rule_failure_sets_action_error_message() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            updateRuleError = IllegalStateException("Update failed")
        )

        environment.emitApps()
        advanceUntilIdle()

        environment.viewModel.updateRule(Rule("com.alpha", "Alpha", 10, wifiBlocked = true))
        advanceUntilIdle()

        assertEquals("Update failed", environment.viewModel.uiState.value.actionErrorMessage)
    }

    @Test
    fun consumed_action_error_message_is_cleared() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            updateRuleError = IllegalStateException("Update failed")
        )

        environment.emitApps()
        advanceUntilIdle()

        environment.viewModel.updateRule(Rule("com.alpha", "Alpha", 10, wifiBlocked = true))
        advanceUntilIdle()
        environment.viewModel.consumeActionErrorMessage()
        advanceUntilIdle()

        assertEquals(null, environment.viewModel.uiState.value.actionErrorMessage)
    }

    @Test
    fun actions_delegate_to_dependencies_and_update_sort_mode() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(
                AppMetadata("com.beta", "Beta", 2, false),
                AppMetadata("com.alpha", "Alpha", 1, false)
            ),
            sortMode = AppSortMode.NAME,
            isDarkMode = false,
            isVpnRunning = false
        )

        environment.emitApps()
        advanceUntilIdle()

        environment.viewModel.setSortMode(AppSortMode.UID)
        environment.viewModel.toggleDarkMode()
        environment.viewModel.toggleVpn(true)
        environment.viewModel.refreshInstalledApps()
        advanceUntilIdle()

        val state = environment.viewModel.uiState.value
        assertEquals(AppSortMode.UID, state.sortMode)
        assertEquals(listOf(1, 2), state.rows.take(2).map { it.uid })
        assertTrue(state.isDarkMode)
        assertEquals(listOf(true), environment.toggleVpnCalls)
        assertEquals(1, environment.refreshCalls)
    }

    @Test
    fun toggles_use_owned_state_instead_of_stale_ui_state() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            isDarkMode = false,
            showSystemApps = true
        )

        environment.emitApps()
        advanceUntilIdle()

        environment.darkModeFlow.value = true
        environment.showSystemFlow.value = false

        environment.viewModel.toggleDarkMode()
        environment.viewModel.toggleFilterSystem()
        advanceUntilIdle()

        assertEquals(listOf(false), environment.setDarkModeCalls)
        assertEquals(listOf(true), environment.setManageSystemCalls)
    }

    @Test
    fun transformed_rule_updates_start_from_latest_repository_rule() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false))
        )

        environment.emitApps()
        advanceUntilIdle()
        environment.rulesDao.seedAuthoritativeRule(
            Rule("com.alpha", "Alpha", 10, wifiBlocked = true),
            publishToFlow = false
        )

        environment.viewModel.updateRule("com.alpha", "Alpha", 10) { current ->
            current.copy(mobileBlocked = true)
        }
        advanceUntilIdle()

        val updatedRule = environment.rulesDao.getRule("com.alpha")!!
        assertTrue(updatedRule.wifiBlocked)
        assertTrue(updatedRule.mobileBlocked)
    }

    @Test
    fun overlapping_rule_updates_for_same_app_preserve_both_changes() = runTest(dispatcher) {
        val environment = TestEnvironment(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false))
        )

        environment.emitApps()
        advanceUntilIdle()
        environment.rulesDao.seedAuthoritativeRule(Rule("com.alpha", "Alpha", 10), publishToFlow = true)
        environment.rulesDao.pauseGetRuleUntilTwoReads()

        val wifiUpdate = async {
            environment.viewModel.updateRule("com.alpha", "Alpha", 10) {
                it.copy(wifiBlocked = true)
            }
        }
        val mobileUpdate = async {
            environment.viewModel.updateRule("com.alpha", "Alpha", 10) {
                it.copy(mobileBlocked = true)
            }
        }

        advanceUntilIdle()
        environment.rulesDao.releasePausedReads()
        awaitAll(wifiUpdate, mobileUpdate)
        advanceUntilIdle()

        val updatedRule = environment.rulesDao.getRule("com.alpha")!!
        assertTrue(updatedRule.wifiBlocked)
        assertTrue(updatedRule.mobileBlocked)
    }

    @Test
    fun main_view_model_ignores_startup_sync_failures() = runTest(dispatcher) {
        val onboarding = MutableStateFlow(false)
        val viewModel = MainViewModel(
            hasCompletedOnboarding = onboarding,
            setHasCompletedOnboarding = { onboarding.value = it },
            syncInstalledApps = { throw IllegalStateException("boom") }
        )

        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals(false, viewModel.hasCompletedOnboarding.value)
    }

    private class TestEnvironment(
        apps: List<AppMetadata>,
        sortMode: AppSortMode = AppSortMode.SMART,
        isDarkMode: Boolean = false,
        isVpnRunning: Boolean = false,
        showSystemApps: Boolean = true,
        discoveryState: AppDiscoveryState = AppDiscoveryState(),
        updateRuleError: Throwable? = null,
        refreshError: Throwable? = null
    ) {
        private val initialApps = apps
        private val appFlow = MutableSharedFlow<List<AppMetadata>>(replay = 1)
        val rulesDao = FakeRuleDao()
        val repository = RuleRepository(rulesDao)
        private val sortModeFlow = MutableStateFlow(sortMode)
        val darkModeFlow = MutableStateFlow(isDarkMode)
        val showSystemFlow = MutableStateFlow(showSystemApps)
        private val recentPackagesFlow = MutableStateFlow<List<String>>(emptyList())
        private val vpnRunningFlow = MutableStateFlow(isVpnRunning)
        private val discoveryFlow = MutableStateFlow(discoveryState)
        val toggleVpnCalls = mutableListOf<Boolean>()
        val setDarkModeCalls = mutableListOf<Boolean>()
        val setManageSystemCalls = mutableListOf<Boolean>()
        var refreshCalls = 0

        fun emitApps() {
            appFlow.tryEmit(initialApps)
        }

        val viewModel = AppsViewModel(
            repository = repository,
            appMetadataDao = FakeAppMetadataDao(appFlow),
            appSortMode = sortModeFlow,
            setAppSortMode = { sortModeFlow.value = it },
            manageSystemApps = showSystemFlow,
            setManageSystemApps = {
                setManageSystemCalls += it
                showSystemFlow.value = it
            },
            isDarkMode = darkModeFlow,
            setDarkMode = {
                setDarkModeCalls += it
                darkModeFlow.value = it
            },
            recentPackages = recentPackagesFlow,
            vpnRunning = vpnRunningFlow,
            toggleVpnAction = {
                toggleVpnCalls += it
                vpnRunningFlow.value = it
            },
            appDiscoveryState = discoveryFlow,
            refreshInstalledAppsAction = {
                refreshCalls += 1
                refreshError?.let { throw it }
            },
            updateRuleAction = { rule ->
                updateRuleError?.let { throw it }
                repository.updateRule(rule)
            }
        )
    }

    private class FakeAppMetadataDao(
        private val apps: Flow<List<AppMetadata>>
    ) : AppMetadataDao {
        override fun getAllApps(): Flow<List<AppMetadata>> = apps

        override suspend fun getAllAppsList(): List<AppMetadata> = when (apps) {
            is StateFlow<List<AppMetadata>> -> apps.value
            else -> emptyList()
        }

        override suspend fun insertApps(apps: List<AppMetadata>) {
            error("Not needed in unit tests")
        }

        override suspend fun deleteAll() {
            error("Not needed in unit tests")
        }
    }

    private class FakeRuleDao : RuleDao {
        private val rules = MutableStateFlow<List<Rule>>(emptyList())
        private val authoritativeRules = linkedMapOf<String, Rule>()
        private var readBarrier: ReadBarrier? = null

        fun seedAuthoritativeRule(rule: Rule, publishToFlow: Boolean) {
            authoritativeRules[rule.packageName] = rule
            if (publishToFlow) {
                rules.value = rules.value.filterNot { it.packageName == rule.packageName } + rule
            }
        }

        fun pauseGetRuleUntilTwoReads() {
            readBarrier = ReadBarrier(expectedReads = 2)
        }

        fun releasePausedReads() {
            readBarrier?.release()
            readBarrier = null
        }

        override fun getAllRules(): Flow<List<Rule>> = rules

        override suspend fun getAllRulesList(): List<Rule> = rules.value

        override suspend fun insertRule(rule: Rule) {
            authoritativeRules[rule.packageName] = rule
            rules.value = rules.value.filterNot { it.packageName == rule.packageName } + rule
        }

        override suspend fun deleteRule(rule: Rule) {
            authoritativeRules.remove(rule.packageName)
            rules.value = rules.value.filterNot { it.packageName == rule.packageName }
        }

        override suspend fun getRule(packageName: String): Rule? {
            val snapshot = authoritativeRules[packageName]
            readBarrier?.awaitRead()
            return snapshot
        }

        private class ReadBarrier(
            private val expectedReads: Int
        ) {
            private var reads = 0
            private val releaseChannel = Channel<Unit>(capacity = expectedReads)

            suspend fun awaitRead() {
                reads += 1
                releaseChannel.receive()
            }

            fun release() {
                repeat(reads.coerceAtMost(expectedReads)) {
                    releaseChannel.trySend(Unit)
                }
            }
        }
    }
}
