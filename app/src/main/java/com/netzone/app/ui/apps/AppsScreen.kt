package com.netzone.app.ui.apps

import android.net.VpnService
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.netzone.app.AppSortMode
import com.netzone.app.Rule
import com.netzone.app.ui.components.EmptyState

@Composable
fun AppsScreen(
    onThemeToggleRequested: () -> Unit,
    currentThemeIsDark: Boolean,
    viewModel: AppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn(true)
        }
    }

    LaunchedEffect(uiState.actionErrorMessage) {
        uiState.actionErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeActionErrorMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppsActionRow(
                isVpnRunning = uiState.isVpnRunning,
                sortMode = uiState.sortMode,
                isDarkMode = currentThemeIsDark,
                onVpnClick = {
                    if (uiState.isVpnRunning) {
                        viewModel.toggleVpn(false)
                    } else {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            viewModel.toggleVpn(true)
                        }
                    }
                },
                onSortSelected = viewModel::setSortMode,
                onThemeClick = onThemeToggleRequested
            )

            if (uiState.appSyncError != null && uiState.emptyState != AppsEmptyState.DISCOVERY_FAILURE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Refresh failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = uiState.appSyncError ?: "App discovery failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = viewModel::refreshInstalledApps) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.showOnlySystem,
                    onClick = { viewModel.toggleFilterSystem() },
                    label = { Text("System Apps") },
                    leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = uiState.showOnlyBlocked,
                    onClick = { viewModel.toggleFilterBlocked() },
                    label = { Text("Blocked Only") },
                    leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Results count
            Text(
                "${uiState.rows.size} apps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // App List
            when (uiState.emptyState) {
                AppsEmptyState.INITIAL_LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                AppsEmptyState.DISCOVERY_FAILURE -> {
                    EmptyState(
                        icon = { Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp)) },
                        title = "Could not load apps",
                        description = uiState.appSyncError ?: "App discovery failed",
                        action = {
                            Button(onClick = viewModel::refreshInstalledApps) {
                                Text("Retry")
                            }
                        }
                    )
                }

                AppsEmptyState.NO_DISCOVERED_APPS -> {
                    EmptyState(
                        icon = { Icon(Icons.Default.Apps, null, modifier = Modifier.size(48.dp)) },
                        title = "No apps discovered",
                        description = "Refresh installed apps to populate this list.",
                        action = {
                            Button(onClick = viewModel::refreshInstalledApps) {
                                Text("Refresh")
                            }
                        }
                    )
                }

                AppsEmptyState.NO_MATCHES -> {
                    EmptyState(
                        icon = { Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp)) },
                        title = "No apps found",
                        description = "Try adjusting your search or filters"
                    )
                }

                null -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                items(
                    items = uiState.rows,
                    key = { it.packageName }
                ) { row ->
                    AppCard(
                        row = row,
                        onUpdate = { transform ->
                            viewModel.updateRule(
                                packageName = row.packageName,
                                appName = row.name,
                                uid = row.uid,
                                transform = transform
                            )
                        }
                    )
                }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun AppsActionRow(
    isVpnRunning: Boolean,
    sortMode: AppSortMode,
    isDarkMode: Boolean,
    onVpnClick: () -> Unit,
    onSortSelected: (AppSortMode) -> Unit,
    onThemeClick: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionRowButton(
            modifier = Modifier.weight(1f),
            title = "VPN",
            value = if (isVpnRunning) "On" else "Off",
            icon = Icons.Default.Shield,
            emphasized = isVpnRunning,
            onClick = onVpnClick
        )

        Box(modifier = Modifier.weight(1f)) {
            ActionRowButton(
                onClick = { sortMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                title = "Sort",
                value = sortMode.label,
                icon = Icons.Default.SwapVert
            )

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                AppSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            sortMenuExpanded = false
                            onSortSelected(mode)
                        }
                    )
                }
            }
        }

        ActionRowButton(
            onClick = onThemeClick,
            modifier = Modifier.weight(1f),
            title = "Theme",
            value = if (isDarkMode) "Dark" else "Light",
            icon = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
            emphasized = isDarkMode
        )
    }
}

@Composable
private fun ActionRowButton(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }
    val iconContainerColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = if (emphasized) 0.dp else 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val AppSortMode.label: String
    get() = when (this) {
        AppSortMode.SMART -> "Smart"
        AppSortMode.NAME -> "Name"
        AppSortMode.UID -> "UID"
    }

@Composable
private fun AppCard(
    row: AppListRow,
    onUpdate: ((Rule) -> Rule) -> Unit
) {
    var expanded by rememberSaveable(row.packageName) { mutableStateOf(false) }
    val context = LocalContext.current
    val rule = row.rule
    var dailyLimitDraft by remember(rule.packageName, rule.dailyLimitMinutes) {
        mutableFloatStateOf(rule.dailyLimitMinutes.toFloat())
    }
    val status = when {
        row.isBlocked -> AppRowStatus(
            label = "Blocked",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        row.hasCustomRule -> AppRowStatus(
            label = "Custom rule",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> AppRowStatus(
            label = "Allowed",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val appIcon: Drawable? = remember(row.packageName) {
        runCatching { context.packageManager.getApplicationIcon(row.packageName) }.getOrNull()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else if (row.hasCustomRule)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (appIcon != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(appIcon)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = status.containerColor
                        ) {
                            Text(
                                text = status.label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = status.contentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (row.isSystem) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "UID ${row.uid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        row.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppToggleButton(
                        icon = Icons.Default.Wifi,
                        contentDescription = "WiFi",
                        active = rule.wifiBlocked,
                        onClick = { onUpdate { it.copy(wifiBlocked = !it.wifiBlocked) } }
                    )
                    AppToggleButton(
                        icon = Icons.Default.SignalCellularAlt,
                        contentDescription = "Mobile",
                        active = rule.mobileBlocked,
                        onClick = { onUpdate { it.copy(mobileBlocked = !it.mobileBlocked) } }
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded content
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Schedule toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Schedule Blocking", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = rule.isScheduleEnabled,
                            onCheckedChange = { enabled -> onUpdate { it.copy(isScheduleEnabled = enabled) } }
                        )
                    }

                    if (rule.isScheduleEnabled) {
                        DayPicker(
                            selectedDays = rule.daysToBlock,
                            onDaysChange = { days -> onUpdate { it.copy(daysToBlock = days) } }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m -> onUpdate { it.copy(startTimeMinutes = h * 60 + m) } },
                                        (rule.startTimeMinutes ?: 0) / 60,
                                        (rule.startTimeMinutes ?: 0) % 60,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (rule.startTimeMinutes != null) {
                                        "From: ${formatTime(rule.startTimeMinutes)}"
                                    } else {
                                        "Set start"
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m -> onUpdate { it.copy(endTimeMinutes = h * 60 + m) } },
                                        (rule.endTimeMinutes ?: 0) / 60,
                                        (rule.endTimeMinutes ?: 0) % 60,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (rule.endTimeMinutes != null) {
                                        "To: ${formatTime(rule.endTimeMinutes)}"
                                    } else {
                                        "Set end"
                                    }
                                )
                            }
                        }
                    }

                    // Daily limit
                    Text("Daily Usage Limit", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = dailyLimitDraft,
                            onValueChange = { minutes -> dailyLimitDraft = minutes },
                            onValueChangeFinished = {
                                onUpdate { it.copy(dailyLimitMinutes = dailyLimitDraft.toInt()) }
                            },
                            valueRange = 0f..480f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (dailyLimitDraft > 0f)
                                "${dailyLimitDraft.toInt() / 60}h ${dailyLimitDraft.toInt() % 60}m"
                            else "None",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // UID info
                    Text(
                        "UID: ${row.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class AppRowStatus(
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

@Composable
private fun AppToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DayPicker(
    selectedDays: Int,
    onDaysChange: (Int) -> Unit
) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = (selectedDays and (1 shl index)) != 0
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                label = "day_bg"
            )

            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onDaysChange(selectedDays xor (1 shl index)) },
                shape = CircleShape,
                color = backgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        day,
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ampm)
}
