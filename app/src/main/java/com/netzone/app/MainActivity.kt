package com.netzone.app

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Handle result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceManager = PreferenceManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val isDarkModePref by produceState<Boolean?>(initialValue = null) {
                preferenceManager.isDarkMode.collect { value = it }
            }
            val coroutineScope = rememberCoroutineScope()
            val systemInDark = isSystemInDarkTheme()
            
            // If the preference is not loaded yet, use the system theme as a fallback, 
            // but ensure we don't flash light if the user is in a dark environment.
            val isDarkMode = isDarkModePref ?: systemInDark

            LaunchedEffect(isDarkMode) {
                if (isDarkModePref != null) {
                    ThemeTransitionController.attachIfPending(this@MainActivity)
                }
            }

            NetZoneTheme(isDark = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = if (isDarkModePref == null) Color(0xFF1A1C1E) else MaterialTheme.colorScheme.background
                ) {
                    if (isDarkModePref != null) {
                        MainScreen(
                            preferenceManager = preferenceManager,
                            isDarkMode = isDarkMode,
                            onToggleTheme = {
                                coroutineScope.launch {
                                    ThemeTransitionController.prepareTransition(this@MainActivity, !isDarkMode)
                                    preferenceManager.setDarkMode(!isDarkMode)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(preferenceManager: PreferenceManager, isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val repository = RuleRepository.getInstance(db.ruleDao())
    val coroutineScope = rememberCoroutineScope()
    
    val viewModel: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel(repository, context.packageManager, db.appMetadataDao(), preferenceManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val rules by viewModel.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showOnlyBlocked by viewModel.showOnlyBlocked.collectAsStateWithLifecycle()
    val showOnlySystem by viewModel.showOnlySystem.collectAsStateWithLifecycle()
    val isLockdown by preferenceManager.isLockdown.collectAsStateWithLifecycle(initialValue = false)
    val isVpnRunning by NetZoneVpnService.isRunning.collectAsStateWithLifecycle(initialValue = false)
    var isStarting by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVpnRunning) {
        isStarting = false
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            ContextCompat.startForegroundService(context, Intent(context, NetZoneVpnService::class.java))
        } else {
            isStarting = false
        }
    }

    var hasUsagePermission by remember { mutableStateOf(true) }
    var hasExactAlarmPermission by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            hasExactAlarmPermission = alarmManager.canScheduleExactAlarms()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Image(
                            //     painter = painterResource(R.drawable.netzone_launcher_foreground),
                            //     contentDescription = "NetZone icon",
                            //     modifier = Modifier.size(50.dp)
                            // )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "NZ",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, 
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            if (isVpnRunning) {
                                ContextCompat.startForegroundService(context, Intent(context, NetZoneVpnService::class.java).apply { 
                                    action = NetZoneVpnService.ACTION_STOP 
                                })
                            } else {
                                isStarting = true
                                val intent = VpnService.prepare(context)
                                if (intent != null) vpnLauncher.launch(intent)
                                else ContextCompat.startForegroundService(context, Intent(context, NetZoneVpnService::class.java))
                            }
                        }) {
                            Icon(
                                Icons.Default.PowerSettingsNew, 
                                contentDescription = when {
                                    isStarting -> "Starting VPN..."
                                    isVpnRunning -> "Stop VPN"
                                    else -> "Start VPN"
                                }, 
                                tint = when {
                                    isStarting -> Color(0xFFFFA500) // Orange
                                    isVpnRunning -> MaterialTheme.colorScheme.error 
                                    else -> Color(0xFF4CAF50)
                                },
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .width(220.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Lockdown traffic", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        coroutineScope.launch {
                                            preferenceManager.setLockdown(!isLockdown)
                                        }
                                        showMenu = false 
                                    },
                                    trailingIcon = { Checkbox(checked = isLockdown, onCheckedChange = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Show log", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showMenu = false 
                                        context.startActivity(Intent(context, LogActivity::class.java))
                                    },
                                    leadingIcon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showMenu = false 
                                        context.startActivity(Intent(context, SettingsActivity::class.java))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Features", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showMenu = false 
                                        context.startActivity(Intent(context, FeaturesActivity::class.java))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Legend", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showLegend = true
                                        showMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Support", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showMenu = false 
                                        context.startActivity(Intent(context, SupportActivity::class.java))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("About", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { 
                                        showMenu = false 
                                        context.startActivity(Intent(context, AboutActivity::class.java))
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                if (!hasUsagePermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Grant Usage Access for Daily Limits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (!hasExactAlarmPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Grant Exact Alarm Permission for Schedules", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChange("") },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = showOnlySystem,
                        onClick = { viewModel.toggleFilterSystem() },
                        label = { Text("System Apps") },
                        leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = showOnlyBlocked,
                        onClick = { viewModel.toggleFilterBlocked() },
                        label = { Text("Blocked Only") },
                        leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = filteredApps,
                key = { it.packageName },
                contentType = { "app" }
            ) { app ->
                val rule = rules.find { it.packageName == app.packageName } ?: Rule(app.packageName, app.name, app.uid)
                AppRuleItem(app, rule, onUpdate = { viewModel.updateRule(it) })
            }
        }
    }

    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            title = { Text("Legend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("WiFi Allowed")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("WiFi Blocked")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Mobile Allowed")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Mobile Blocked")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegend = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun AppRuleItem(app: AppMetadata, rule: Rule, onUpdate: (Rule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Fetch the actual application icon from the system
    val appIcon = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (expanded) 8.dp else 0.dp,
        shadowElevation = if (expanded) 4.dp else 0.dp,
        border = if (!expanded) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(app.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary) },
                leadingContent = {
                    if (appIcon != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(appIcon)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .placeholder(null)
                                .error(null)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).padding(2.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).padding(2.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onUpdate(rule.copy(wifiBlocked = !rule.wifiBlocked)) }) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "WiFi",
                                tint = if (rule.wifiBlocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { onUpdate(rule.copy(mobileBlocked = !rule.mobileBlocked)) }) {
                            Icon(
                                imageVector = Icons.Default.SignalCellularAlt,
                                contentDescription = "Mobile",
                                tint = if (rule.mobileBlocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Text("Blocking Controls", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Schedule Blocking", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = rule.isScheduleEnabled,
                            onCheckedChange = { onUpdate(rule.copy(isScheduleEnabled = it)) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    if (rule.isScheduleEnabled) {
                        // Day Picker
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("S", "M", "T", "W", "T", "F", "S")
                            days.forEachIndexed { index, day ->
                                val isSelected = (rule.daysToBlock and (1 shl index)) != 0
                                Surface(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
                                            val newMask = rule.daysToBlock xor (1 shl index)
                                            onUpdate(rule.copy(daysToBlock = newMask))
                                        },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(day, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(context, { _, h, m -> 
                                        onUpdate(rule.copy(startTimeMinutes = h * 60 + m))
                                    }, (rule.startTimeMinutes ?: 0) / 60, (rule.startTimeMinutes ?: 0) % 60, false).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text("From: ${formatTime(rule.startTimeMinutes ?: 0)}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(context, { _, h, m -> 
                                        onUpdate(rule.copy(endTimeMinutes = h * 60 + m))
                                    }, (rule.endTimeMinutes ?: 0) / 60, (rule.endTimeMinutes ?: 0) % 60, false).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text("To: ${formatTime(rule.endTimeMinutes ?: 0)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Daily Limit UI
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Daily Usage Limit", modifier = Modifier.weight(1f))
                    Text(if (rule.dailyLimitMinutes > 0) "${rule.dailyLimitMinutes / 60}h ${rule.dailyLimitMinutes % 60}m" else "None")
                }
                
                Slider(
                    value = rule.dailyLimitMinutes.toFloat(),
                    onValueChange = { onUpdate(rule.copy(dailyLimitMinutes = it.toInt())) },
                    valueRange = 0f..480f, // Up to 8 hours
                    steps = 15 // 30 min steps
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("UID: ${app.uid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
}

fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%02d:%02d %s".format(h12, m, ampm)
}
