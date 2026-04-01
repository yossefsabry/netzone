package com.netzone.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netzone.app.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel private constructor(
    dependencies: Dependencies,
    preferenceManager: PreferenceManager? = null
) : ViewModel() {

    private val setHasCompletedOnboarding = dependencies.setHasCompletedOnboarding
    private val onboardingFlow = dependencies.hasCompletedOnboarding
    private val syncInstalledApps = dependencies.syncInstalledApps
    private val minimumLoadingMillis = dependencies.minimumLoadingMillis
    lateinit var preferenceManager: PreferenceManager
        private set

    data class StartupState(
        val startDestination: String? = null,
        val isStartupReady: Boolean = false
    )

    companion object {
        private const val MINIMUM_LOADING_MILLIS = 1_000L
    }

    init {
        if (preferenceManager != null) {
            this.preferenceManager = preferenceManager
        }
    }

    @Inject
    constructor(
        preferenceManager: PreferenceManager,
        appDiscoveryService: AppDiscoveryService
    ) : this(
        Dependencies(
            setHasCompletedOnboarding = preferenceManager::setHasCompletedOnboarding,
            hasCompletedOnboarding = preferenceManager.hasCompletedOnboarding,
            syncInstalledApps = appDiscoveryService::syncInstalledApps
        ),
        preferenceManager = preferenceManager
    )

    internal constructor(
        hasCompletedOnboarding: Flow<Boolean>,
        setHasCompletedOnboarding: suspend (Boolean) -> Unit,
        syncInstalledApps: suspend () -> Unit,
        minimumLoadingMillis: Long = MINIMUM_LOADING_MILLIS
    ) : this(
        Dependencies(
            setHasCompletedOnboarding = setHasCompletedOnboarding,
            hasCompletedOnboarding = hasCompletedOnboarding,
            syncInstalledApps = syncInstalledApps,
            minimumLoadingMillis = minimumLoadingMillis
        )
    )

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _startupState = MutableStateFlow(StartupState())
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    private var hasMinimumLoadingElapsed = false

    init {
        viewModelScope.launch {
            delay(minimumLoadingMillis)
            hasMinimumLoadingElapsed = true
            updateStartupState()
        }

        viewModelScope.launch {
            onboardingFlow
                .catch { emit(false) }
                .collect { value: Boolean ->
                    _hasCompletedOnboarding.value = value
                    updateStartupState(value)
                }
            }

        viewModelScope.launch {
            try {
                syncInstalledApps()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Discovery errors are already surfaced via AppDiscoveryState.
            }
        }
    }

    private fun updateStartupState(onboardingValue: Boolean? = _hasCompletedOnboarding.value) {
        val startDestination = _startupState.value.startDestination
            ?: onboardingValue?.let { hasCompleted ->
                if (hasCompleted) {
                    Screen.Home.route
                } else {
                    Screen.Onboarding.route
                }
            }
        val isStartupReady = startDestination != null && hasMinimumLoadingElapsed

        _startupState.value = StartupState(
            startDestination = startDestination,
            isStartupReady = isStartupReady
        )
        _isLoading.value = !isStartupReady
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            setHasCompletedOnboarding(true)
        }
    }

    private data class Dependencies(
        val setHasCompletedOnboarding: suspend (Boolean) -> Unit,
        val hasCompletedOnboarding: Flow<Boolean>,
        val syncInstalledApps: suspend () -> Unit,
        val minimumLoadingMillis: Long = MINIMUM_LOADING_MILLIS
    )
}
