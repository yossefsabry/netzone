package com.netzone.app

class ThemeChangeCoordinator(
    private val prepareTransition: (Boolean) -> Unit,
    private val attachTransition: () -> Unit,
    private val persistTheme: suspend (Boolean) -> Unit
) {
    suspend fun requestThemeChange(currentIsDark: Boolean, targetIsDark: Boolean): Boolean {
        if (currentIsDark == targetIsDark) {
            return false
        }

        prepareTransition(targetIsDark)
        attachTransition()
        persistTheme(targetIsDark)
        return true
    }
}
