@file:Suppress("DEPRECATION")

package com.netzone.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netzone.app.navigation.NetZoneNavHost
import com.netzone.app.navigation.Screen
import com.netzone.app.ui.components.NetZoneBottomBar
import com.netzone.app.ui.theme.NetZoneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val preferenceManager = viewModel.preferenceManager
            val startupState by viewModel.startupState.collectAsStateWithLifecycle()
            val isDarkModePref by preferenceManager.isDarkMode.collectAsStateWithLifecycle(initialValue = null)
            val systemInDark = isSystemInDarkTheme()
            val isDarkMode = isDarkModePref ?: systemInDark
            val coroutineScope = rememberCoroutineScope()
            var pendingAnimatedThemeTarget by rememberSaveable { mutableStateOf<Boolean?>(null) }
            var isThemeChangeInFlight by rememberSaveable { mutableStateOf(false) }
            val effectiveThemeIsDark = pendingAnimatedThemeTarget ?: isDarkMode
            val themeChangeCoordinator = remember(preferenceManager) {
                ThemeChangeCoordinator(
                    prepareTransition = { targetIsDark ->
                        ThemeTransitionController.prepareTransition(this@MainActivity, targetIsDark)
                    },
                    attachTransition = {
                        ThemeTransitionController.attachIfPending(this@MainActivity)
                    },
                    persistTheme = preferenceManager::setDarkMode
                )
            }

            val onThemeChangeRequested: (Boolean) -> Unit = { targetIsDark ->
                coroutineScope.launch {
                    val currentTarget = pendingAnimatedThemeTarget ?: isDarkMode
                    if (isThemeChangeInFlight || currentTarget == targetIsDark) {
                        return@launch
                    }

                    isThemeChangeInFlight = true
                    pendingAnimatedThemeTarget = targetIsDark
                    try {
                        val shouldAttach = themeChangeCoordinator.requestThemeChange(
                            currentIsDark = currentTarget,
                            targetIsDark = targetIsDark
                        )
                        if (!shouldAttach) {
                            pendingAnimatedThemeTarget = null
                            isThemeChangeInFlight = false
                        }
                    } catch (error: Exception) {
                        pendingAnimatedThemeTarget = null
                        isThemeChangeInFlight = false
                        throw error
                    }
                }
            }
            val onThemeToggleRequested: () -> Unit = {
                onThemeChangeRequested(!(pendingAnimatedThemeTarget ?: isDarkMode))
            }

            LaunchedEffect(Unit) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    withFrameNanos { }
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(isDarkMode, pendingAnimatedThemeTarget) {
                if (pendingAnimatedThemeTarget == isDarkMode) {
                    pendingAnimatedThemeTarget = null
                    isThemeChangeInFlight = false
                }
            }

            NetZoneTheme(darkTheme = effectiveThemeIsDark) {
                if (!startupState.isStartupReady) {
                    LoadingScreen()
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    val showBottomBar = currentDestination?.route in listOf(
                        Screen.Home.route,
                        Screen.Apps.route,
                        Screen.Logs.route,
                        Screen.Settings.route
                    )

                    Scaffold(
                        bottomBar = {
                            NetZoneBottomBar(
                                navController = navController,
                                visible = showBottomBar
                            )
                        }
                    ) { innerPadding ->
                        NetZoneNavHost(
                            navController = navController,
                            startDestination = startupState.startDestination ?: Screen.Onboarding.route,
                            onOnboardingComplete = { viewModel.completeOnboarding() },
                            onThemeChangeRequested = onThemeChangeRequested,
                            onThemeToggleRequested = onThemeToggleRequested,
                            currentThemeIsDark = effectiveThemeIsDark,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedNetZoneWordmark()
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Loading your firewall...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedNetZoneWordmark() {
    val word = "NetZone"
    val transition = rememberInfiniteTransition(label = "netzone_wordmark")

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        word.forEachIndexed { index, letter ->
            val alpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 850,
                        delayMillis = index * 90,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "letter_alpha_$index"
            )
            val offsetY by transition.animateFloat(
                initialValue = 10f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 850,
                        delayMillis = index * 90,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "letter_offset_$index"
            )

            Text(
                text = letter.toString(),
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .alpha(alpha),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp
                ),
                color = if (index < 3) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )
        }
    }
}
