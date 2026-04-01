package com.netzone.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Apps : Screen("apps")
    object Logs : Screen("logs")
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
    object About : Screen("about")
    object Support : Screen("support")
}
