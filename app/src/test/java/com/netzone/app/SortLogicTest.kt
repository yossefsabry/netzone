package com.netzone.app

import com.netzone.app.ui.apps.buildAppRows
import org.junit.Assert.assertEquals
import org.junit.Test

class SortLogicTest {

    @Test
    fun smart_sort_prioritizes_custom_rules_then_recent_then_name() {
        val apps = listOf(
            AppMetadata("com.alpha", "Alpha", 10, false),
            AppMetadata("com.beta", "Beta", 11, false),
            AppMetadata("com.gamma", "Gamma", 12, false),
            AppMetadata("com.delta", "Delta", 13, false)
        )

        val rulesMap = mapOf(
            "com.gamma" to Rule("com.gamma", "Gamma", 12, wifiBlocked = true),
            "com.delta" to Rule("com.delta", "Delta", 13, isScheduleEnabled = true)
        )

        val rows = buildAppRows(
            apps = apps,
            rules = rulesMap,
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = listOf("com.beta", "com.gamma")
        )

        assertEquals(
            listOf("com.gamma", "com.delta", "com.beta", "com.alpha"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun smart_sort_treats_non_network_restrictions_as_custom() {
        val apps = listOf(
            AppMetadata("com.alpha", "Alpha", 10, false),
            AppMetadata("com.beta", "Beta", 11, false),
            AppMetadata("com.gamma", "Gamma", 12, false)
        )

        val rulesMap = mapOf(
            "com.beta" to Rule("com.beta", "Beta", 11, dailyLimitMinutes = 15),
            "com.gamma" to Rule("com.gamma", "Gamma", 12, isScheduleEnabled = true)
        )

        val rows = buildAppRows(
            apps = apps,
            rules = rulesMap,
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = emptyList()
        )

        assertEquals(
            listOf("com.beta", "com.gamma", "com.alpha"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun smart_sort_preserves_recent_packages_order() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.alpha", "Alpha", 10, false),
                AppMetadata("com.beta", "Beta", 11, false),
                AppMetadata("com.gamma", "Gamma", 12, false)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = listOf("com.gamma", "com.alpha")
        )

        assertEquals(
            listOf("com.gamma", "com.alpha", "com.beta"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun smart_sort_uses_first_occurrence_when_recent_packages_contains_duplicates() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.alpha", "Alpha", 10, false),
                AppMetadata("com.beta", "Beta", 11, false),
                AppMetadata("com.gamma", "Gamma", 12, false)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = listOf("com.alpha", "com.gamma", "com.alpha")
        )

        assertEquals(
            listOf("com.alpha", "com.gamma", "com.beta"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun smart_sort_uses_package_name_as_final_tiebreaker_for_same_name() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.zulu.alpha", "Alpha", 20, false),
                AppMetadata("com.alpha.alpha", "Alpha", 10, false),
                AppMetadata("com.beta.beta", "Beta", 30, false)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = emptyList()
        )

        assertEquals(
            listOf("com.alpha.alpha", "com.zulu.alpha", "com.beta.beta"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun smart_sort_returns_empty_list_for_empty_input() {
        val rows = buildAppRows(
            apps = emptyList(),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.SMART,
            recentPackages = emptyList()
        )

        assertEquals(emptyList<String>(), rows.map { it.packageName })
    }
}
