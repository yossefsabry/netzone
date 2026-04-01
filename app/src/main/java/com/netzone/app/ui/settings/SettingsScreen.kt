@file:Suppress("DEPRECATION")

package com.netzone.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netzone.app.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = preferenceManager.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val manageSystemApps: StateFlow<Boolean> = preferenceManager.manageSystemApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockWhenScreenOff: StateFlow<Boolean> = preferenceManager.blockWhenScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val customDns: StateFlow<String> = preferenceManager.customDns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "8.8.8.8")

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setDarkMode(enabled) }
    }

    fun setManageSystemApps(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setManageSystemApps(enabled) }
    }

    fun setBlockWhenScreenOff(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setBlockWhenScreenOff(enabled) }
    }

    fun setCustomDns(dns: String) {
        viewModelScope.launch { preferenceManager.setCustomDns(dns) }
    }
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onThemeChangeRequested: (Boolean) -> Unit,
    currentThemeIsDark: Boolean,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val manageSystemApps by viewModel.manageSystemApps.collectAsStateWithLifecycle()
    val blockWhenScreenOff by viewModel.blockWhenScreenOff.collectAsStateWithLifecycle()
    val customDns by viewModel.customDns.collectAsStateWithLifecycle()

    var showDnsDialog by remember { mutableStateOf(false) }
    var dnsInput by remember { mutableStateOf(customDns) }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Appearance Section
        Text(
            "Appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.DarkMode,
            title = "Dark Mode",
            subtitle = "Use dark theme",
            trailing = {
                Switch(
                    checked = currentThemeIsDark,
                    onCheckedChange = onThemeChangeRequested
                )
            }
        )

        // App Management Section
        Text(
            "App Management",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.Settings,
            title = "Manage System Apps",
            subtitle = "Show system apps in app list",
            trailing = {
                Switch(
                    checked = manageSystemApps,
                    onCheckedChange = { viewModel.setManageSystemApps(it) }
                )
            }
        )

        SettingsItem(
            icon = Icons.Default.ScreenLockPortrait,
            title = "Block When Screen Off",
            subtitle = "Block traffic when screen is off",
            trailing = {
                Switch(
                    checked = blockWhenScreenOff,
                    onCheckedChange = { viewModel.setBlockWhenScreenOff(it) }
                )
            }
        )

        // Network Section
        Text(
            "Network",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.Dns,
            title = "Custom DNS",
            subtitle = customDns,
            onClick = { 
                dnsInput = customDns
                showDnsDialog = true 
            }
        )

        // About Section
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.Info,
            title = "Version",
            subtitle = "1.0.0",
            onClick = onNavigateToAbout
        )

        SettingsItem(
            icon = Icons.Default.Code,
            title = "Open Source Licenses",
            subtitle = "View third-party licenses",
            onClick = onNavigateToSupport
        )
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("Custom DNS Server") },
            text = {
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    label = { Text("DNS Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomDns(dnsInput)
                    showDnsDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}
