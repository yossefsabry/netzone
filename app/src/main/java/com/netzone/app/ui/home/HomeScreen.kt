@file:Suppress("DEPRECATION")

package com.netzone.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.netzone.app.NetZoneVpnService
import android.net.VpnService

@Composable
fun HomeScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onThemeToggleRequested: () -> Unit,
    currentThemeIsDark: Boolean,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleVpn(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "NetZone",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Firewall Control",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onThemeToggleRequested) {
                    Icon(
                        if (currentThemeIsDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme"
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        // VPN Status Card
        VpnStatusCard(
            isRunning = uiState.isVpnRunning,
            onToggle = {
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
            }
        )

        // Permission Warnings
        if (
            !uiState.hasUsagePermission ||
            !uiState.hasExactAlarmPermission ||
            !uiState.hasNotificationPermission ||
            uiState.discoveredAppsCount == 0 ||
            uiState.appSyncError != null
        ) {
            SetupActionCards(
                uiState = uiState,
                onOpenUsageAccess = { viewModel.openUsageAccessSettings() },
                onOpenExactAlarm = { viewModel.openExactAlarmSettings() },
                onOpenNotifications = { viewModel.openNotificationSettings() },
                onRefreshApps = { viewModel.refreshInstalledApps() }
            )
        }

        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Block,
                title = "Blocked",
                value = "${uiState.blockedAppsCount}",
                subtitle = "Apps"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                title = "Requests",
                value = "${uiState.totalBlockedRequests}",
                subtitle = "Blocked today"
            )
        }

        // Quick Actions
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        QuickActionRow(
            isLockdown = uiState.isLockdown,
            onToggleLockdown = { viewModel.toggleLockdown() },
            onNavigateToApps = onNavigateToApps,
            onNavigateToLogs = onNavigateToLogs
        )

        // Active Rules Preview
        Text(
            "Getting Started",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        GettingStartedCard(onNavigateToApps = onNavigateToApps)
    }
}

@Composable
private fun VpnStatusCard(
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isRunning) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        animationSpec = tween(300),
        label = "vpn_bg"
    )
    
    val statusColor by animateColorAsState(
        targetValue = if (isRunning) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.error,
        label = "vpn_status"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val scale by animateFloatAsState(
                    targetValue = if (isRunning) 1.1f else 1f,
                    label = "icon_scale"
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Shield else Icons.Default.ShieldMoon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "VPN Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isRunning) "Active & Protecting" else "Inactive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
            FilledIconButton(
                onClick = onToggle,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = statusColor,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Stop" else "Start"
                )
            }
        }
    }
}

@Composable
private fun SetupActionCards(
    uiState: HomeUiState,
    onOpenUsageAccess: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRefreshApps: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!uiState.hasUsagePermission) {
            SetupActionCard(
                title = "Usage Access Required",
                description = "Needed for daily limits and usage-based blocking.",
                buttonText = "Grant Access",
                onClick = onOpenUsageAccess
            )
        }

        if (!uiState.hasExactAlarmPermission) {
            SetupActionCard(
                title = "Exact Alarm Required",
                description = "Needed for scheduled blocking to run on time.",
                buttonText = "Open Setting",
                onClick = onOpenExactAlarm
            )
        }

        if (!uiState.hasNotificationPermission) {
            SetupActionCard(
                title = "Notifications Disabled",
                description = "Allow notifications so VPN status and alerts remain visible.",
                buttonText = "Enable",
                onClick = onOpenNotifications
            )
        }

        if (uiState.discoveredAppsCount == 0 || uiState.appSyncError != null) {
            SetupActionCard(
                title = if (uiState.isSyncingApps) "Loading Installed Apps" else "Apps Not Loaded",
                description = uiState.appSyncError ?: if (uiState.isSyncingApps) {
                    "Scanning installed apps and preparing your app list."
                } else {
                    "Refresh the installed app list so you can manage firewall rules."
                },
                buttonText = if (uiState.isSyncingApps) "Refreshing..." else "Refresh Apps",
                onClick = onRefreshApps,
                enabled = !uiState.isSyncingApps
            )
        }
    }
}

@Composable
private fun SetupActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onClick, enabled = enabled) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    isLockdown: Boolean,
    onToggleLockdown: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionChip(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Default.Lock,
            label = if (isLockdown) "Unlock" else "Lockdown",
            isActive = isLockdown,
            onClick = onToggleLockdown
        )
        ActionChip(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Default.Apps,
            label = "Manage Apps",
            onClick = onNavigateToApps
        )
        ActionChip(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Filled.List,
            label = "View Logs",
            onClick = onNavigateToLogs
        )
    }
}

@Composable
private fun ActionChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        label = "chip_bg"
    )

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GettingStartedCard(onNavigateToApps: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "How it works",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "NetZone creates a local VPN to filter network traffic. " +
                "Apps you block will have their internet access restricted. " +
                "Start the VPN to begin protecting your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onNavigateToApps) {
                Text("Manage Apps")
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
