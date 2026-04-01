package com.netzone.app

import com.netzone.app.navigation.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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
    fun startup_is_not_ready_until_minimum_loading_duration_finishes() = runTest(dispatcher) {
        val onboarding = MutableStateFlow(false)
        val viewModel = MainViewModel(
            hasCompletedOnboarding = onboarding,
            setHasCompletedOnboarding = { onboarding.value = it },
            syncInstalledApps = {},
            minimumLoadingMillis = 1_000L
        )

        runCurrent()

        assertEquals(Screen.Onboarding.route, viewModel.startupState.value.startDestination)
        assertFalse(viewModel.startupState.value.isStartupReady)
        assertTrue(viewModel.isLoading.value)

        advanceTimeBy(999)
        runCurrent()

        assertFalse(viewModel.startupState.value.isStartupReady)
        assertTrue(viewModel.isLoading.value)

        advanceTimeBy(1)
        runCurrent()

        assertTrue(viewModel.startupState.value.isStartupReady)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun startup_becomes_ready_while_app_discovery_is_still_running() = runTest(dispatcher) {
        val onboarding = MutableStateFlow(true)
        val syncStarted = CompletableDeferred<Unit>()
        val releaseSync = CompletableDeferred<Unit>()
        val viewModel = MainViewModel(
            hasCompletedOnboarding = onboarding,
            setHasCompletedOnboarding = { onboarding.value = it },
            syncInstalledApps = {
                syncStarted.complete(Unit)
                releaseSync.await()
            },
            minimumLoadingMillis = 1_000L
        )

        runCurrent()
        assertTrue(syncStarted.isCompleted)

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(Screen.Home.route, viewModel.startupState.value.startDestination)
        assertTrue(viewModel.startupState.value.isStartupReady)

        releaseSync.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun startup_falls_back_to_onboarding_when_onboarding_flow_fails() = runTest(dispatcher) {
        val viewModel = MainViewModel(
            hasCompletedOnboarding = flow { throw IllegalStateException("boom") },
            setHasCompletedOnboarding = {},
            syncInstalledApps = {},
            minimumLoadingMillis = 0L
        )

        advanceUntilIdle()

        assertEquals(false, viewModel.hasCompletedOnboarding.value)
        assertEquals(Screen.Onboarding.route, viewModel.startupState.value.startDestination)
        assertTrue(viewModel.startupState.value.isStartupReady)
    }

    @Test
    fun start_destination_is_latched_for_current_activity_lifetime() = runTest(dispatcher) {
        val onboarding = MutableStateFlow(false)
        val viewModel = MainViewModel(
            hasCompletedOnboarding = onboarding,
            setHasCompletedOnboarding = { onboarding.value = it },
            syncInstalledApps = {},
            minimumLoadingMillis = 0L
        )

        advanceUntilIdle()

        assertEquals(Screen.Onboarding.route, viewModel.startupState.value.startDestination)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertEquals(true, onboarding.value)
        assertEquals(Screen.Onboarding.route, viewModel.startupState.value.startDestination)
    }
}
