package com.netzone.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.netzone.app.AboutScreen
import com.netzone.app.SupportScreen
import com.netzone.app.ui.apps.AppsScreen
import com.netzone.app.ui.home.HomeScreen
import com.netzone.app.ui.logs.LogsScreen
import com.netzone.app.ui.onboarding.OnboardingScreen
import com.netzone.app.ui.settings.SettingsScreen

@Composable
fun NetZoneNavHost(
    navController: NavHostController,
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    onThemeChangeRequested: (Boolean) -> Unit,
    onThemeToggleRequested: () -> Unit,
    currentThemeIsDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToApps = { navController.navigate(Screen.Apps.route) },
                onNavigateToLogs = { navController.navigate(Screen.Logs.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onThemeToggleRequested = onThemeToggleRequested,
                currentThemeIsDark = currentThemeIsDark
            )
        }
        composable(Screen.Apps.route) {
            AppsScreen(
                onThemeToggleRequested = onThemeToggleRequested,
                currentThemeIsDark = currentThemeIsDark
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToSupport = { navController.navigate(Screen.Support.route) },
                onThemeChangeRequested = onThemeChangeRequested,
                currentThemeIsDark = currentThemeIsDark
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onGitHubClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/yossefsabry/netzone")))
                }
            )
        }
        composable(Screen.Support.route) {
            SupportScreen(
                onBack = { navController.popBackStack() },
                onContactClick = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:support@netzone.app")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "NetZone Support Request")
                    })
                },
                onWebClick = { url ->
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
