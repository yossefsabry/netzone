package com.netzone.app.ui.apps

import com.netzone.app.AppMetadata
import com.netzone.app.AppSortMode
import com.netzone.app.Rule
import java.util.Locale

data class AppListRow(
    val packageName: String,
    val name: String,
    val uid: Int,
    val isSystem: Boolean,
    val isBlocked: Boolean,
    val hasCustomRule: Boolean,
    val isRecent: Boolean,
    val rule: Rule
)

fun buildAppRows(
    apps: List<AppMetadata>,
    rules: Map<String, Rule>,
    searchQuery: String,
    includeSystemApps: Boolean,
    blockedOnly: Boolean,
    sortMode: AppSortMode,
    recentPackages: List<String>
): List<AppListRow> {
    val normalizedQuery = searchQuery.trim()
    val recentOrder = linkedMapOf<String, Int>()
    recentPackages.forEachIndexed { index, packageName ->
        recentOrder.putIfAbsent(packageName, index)
    }

    return apps
        .asSequence()
        .map { app ->
            val rule = rules[app.packageName] ?: Rule(
                packageName = app.packageName,
                appName = app.name,
                uid = app.uid
            )
            val hasCustomRule = rule.hasCustomRestriction()
            val isBlocked = rule.isBlocked()

            AppListRow(
                packageName = app.packageName,
                name = app.name,
                uid = app.uid,
                isSystem = app.isSystem,
                isBlocked = isBlocked,
                hasCustomRule = hasCustomRule,
                isRecent = app.packageName in recentOrder,
                rule = rule
            )
        }
        .filter { row -> includeSystemApps || !row.isSystem }
        .filter { row -> !blockedOnly || row.isBlocked }
        .filter { row -> row.matchesSearch(normalizedQuery) }
        .sortedWith(sortComparator(sortMode, recentOrder))
        .toList()
}

private fun AppListRow.matchesSearch(query: String): Boolean {
    if (query.isEmpty()) return true

    return name.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true) ||
        uid.toString().contains(query)
}

private fun sortComparator(
    sortMode: AppSortMode,
    recentOrder: Map<String, Int>
): Comparator<AppListRow> {
    val smartFallback = compareBy<AppListRow>(
        { it.name.lowercase(Locale.ROOT) },
        { it.packageName }
    )
    val alphabetical = compareBy<AppListRow>(
        { it.name.lowercase(Locale.ROOT) },
        { it.uid },
        { it.packageName }
    )

    return when (sortMode) {
        AppSortMode.SMART -> compareByDescending<AppListRow> { it.hasCustomRule }
            .thenBy { recentOrder[it.packageName] ?: Int.MAX_VALUE }
            .then(smartFallback)
        AppSortMode.NAME -> alphabetical
        AppSortMode.UID -> compareBy<AppListRow>({ it.uid }, { it.name.lowercase(Locale.ROOT) }, { it.packageName })
    }
}
