package com.netzone.app

import com.netzone.app.ui.apps.buildAppRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListModelsTest {

    @Test
    fun search_matches_app_name_package_name_and_uid() {
        val apps = listOf(
            AppMetadata("com.alpha.browser", "Alpha Browser", 1001, false),
            AppMetadata("com.beta.mail", "Beta Mail", 2002, false),
            AppMetadata("org.sample.gamma", "Gamma Notes", 3003, false)
        )

        val byName = buildAppRows(
            apps = apps,
            rules = emptyMap(),
            searchQuery = "browser",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )
        val byPackage = buildAppRows(
            apps = apps,
            rules = emptyMap(),
            searchQuery = "beta.mail",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )
        val byUid = buildAppRows(
            apps = apps,
            rules = emptyMap(),
            searchQuery = "3003",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertEquals(listOf("com.alpha.browser"), byName.map { it.packageName })
        assertEquals(listOf("com.beta.mail"), byPackage.map { it.packageName })
        assertEquals(listOf("org.sample.gamma"), byUid.map { it.packageName })
    }

    @Test
    fun blocked_only_filter_keeps_only_rows_with_active_blocking() {
        val apps = listOf(
            AppMetadata("com.alpha", "Alpha", 10, false),
            AppMetadata("com.beta", "Beta", 11, false),
            AppMetadata("com.gamma", "Gamma", 12, false)
        )

        val rows = buildAppRows(
            apps = apps,
            rules = mapOf(
                "com.alpha" to Rule("com.alpha", "Alpha", 10),
                "com.beta" to Rule("com.beta", "Beta", 11, mobileBlocked = true)
            ),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = true,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertEquals(listOf("com.beta"), rows.map { it.packageName })
        assertTrue(rows.all { it.isBlocked })
    }

    @Test
    fun blocked_only_filter_does_not_treat_every_custom_rule_as_blocked() {
        val customOnlyRule = Rule("com.alpha", "Alpha", 10, dailyLimitMinutes = 30)
        val blockedRule = Rule("com.beta", "Beta", 11, wifiBlocked = true)
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.alpha", "Alpha", 10, false),
                AppMetadata("com.beta", "Beta", 11, false)
            ),
            rules = mapOf(
                "com.alpha" to customOnlyRule,
                "com.beta" to blockedRule
            ),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = true,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertEquals(listOf("com.beta"), rows.map { it.packageName })
        assertTrue(customOnlyRule.hasCustomRestriction())
        assertTrue(blockedRule.isBlocked())
    }

    @Test
    fun row_model_marks_daily_limit_rule_as_custom_without_marking_it_blocked() {
        val row = buildAppRows(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            rules = mapOf(
                "com.alpha" to Rule("com.alpha", "Alpha", 10, dailyLimitMinutes = 30)
            ),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        ).single()

        assertTrue(row.hasCustomRule)
        assertTrue(!row.isBlocked)
    }

    @Test
    fun row_model_marks_schedule_only_rule_as_custom_without_marking_it_blocked() {
        val row = buildAppRows(
            apps = listOf(AppMetadata("com.alpha", "Alpha", 10, false)),
            rules = mapOf(
                "com.alpha" to Rule("com.alpha", "Alpha", 10, isScheduleEnabled = true)
            ),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        ).single()

        assertTrue(row.hasCustomRule)
        assertTrue(!row.isBlocked)
    }

    @Test
    fun row_model_does_not_mark_disabled_or_dayless_rules_as_blocked() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.disabled", "Disabled", 10, false),
                AppMetadata("com.schedule", "Schedule", 11, false)
            ),
            rules = mapOf(
                "com.disabled" to Rule("com.disabled", "Disabled", 10, wifiBlocked = true, isEnabled = false),
                "com.schedule" to Rule(
                    "com.schedule",
                    "Schedule",
                    11,
                    isScheduleEnabled = true,
                    startTimeMinutes = 22 * 60,
                    endTimeMinutes = 2 * 60,
                    daysToBlock = 0
                )
            ),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertTrue(rows.all { !it.isBlocked })
        assertTrue(rows.all { it.hasCustomRule })
    }

    @Test
    fun include_system_apps_false_filters_system_rows() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.alpha", "Alpha", 10, false),
                AppMetadata("com.android.systemui", "System UI", 11, true)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = false,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertEquals(listOf("com.alpha"), rows.map { it.packageName })
    }

    @Test
    fun name_sort_orders_alphabetically_with_uid_tiebreaker() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.gamma", "Gamma", 30, false),
                AppMetadata("com.alpha.two", "Alpha", 20, false),
                AppMetadata("com.alpha.one", "Alpha", 10, false)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.NAME,
            recentPackages = emptyList()
        )

        assertEquals(
            listOf("com.alpha.one", "com.alpha.two", "com.gamma"),
            rows.map { it.packageName }
        )
    }

    @Test
    fun uid_sort_orders_by_uid_with_name_tiebreaker() {
        val rows = buildAppRows(
            apps = listOf(
                AppMetadata("com.gamma", "Gamma", 20, false),
                AppMetadata("com.alpha", "Alpha", 10, false),
                AppMetadata("com.beta", "Beta", 10, false)
            ),
            rules = emptyMap(),
            searchQuery = "",
            includeSystemApps = true,
            blockedOnly = false,
            sortMode = AppSortMode.UID,
            recentPackages = emptyList()
        )

        assertEquals(
            listOf("com.alpha", "com.beta", "com.gamma"),
            rows.map { it.packageName }
        )
    }
}
