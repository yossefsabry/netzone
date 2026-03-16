package com.netaccess.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceManager = PreferenceManager(this)

        setContent {
            val isDarkMode by preferenceManager.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(isDarkMode) {
                ThemeTransitionController.attachIfPending(this@MainActivity)
            }

            NetAccessTheme(isDark = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        isDarkMode = isDarkMode,
                        onToggleTheme = {
                            coroutineScope.launch {
                                ThemeTransitionController.prepareTransition(this@MainActivity)
                                preferenceManager.setDarkMode(!isDarkMode)
                            }
                        }
                    )
                }
            }
        }
    }
}

class MainViewModel(private val repository: RuleRepository, private val packageManager: PackageManager) : ViewModel() {
    val rules: Flow<List<Rule>> = repository.getAllRulesFlow()
    
    private val allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val searchQuery = MutableStateFlow("")
    val showOnlyBlocked = MutableStateFlow(false)
    val showOnlySystem = MutableStateFlow(true)

    // Reactive filtering pipeline
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        allApps,
        searchQuery,
        showOnlyBlocked,
        showOnlySystem,
        repository.rulesMap
    ) { apps, query, blockedOnly, systemOnly, rulesMap ->
        apps.filter { app ->
            val matchesSearch = query.isEmpty() || app.name.contains(query, ignoreCase = true) || 
                             app.packageName.contains(query, ignoreCase = true)
            val matchesSystem = systemOnly || !app.isSystem
            
            val rule = rulesMap[app.packageName]
            val isBlocked = rule != null && (rule.wifiBlocked || rule.mobileBlocked || rule.isScheduleEnabled)
            val matchesBlocked = !blockedOnly || isBlocked
            
            matchesSearch && matchesSystem && matchesBlocked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .sortedBy { packageManager.getApplicationLabel(it).toString() }
            
            allApps.value = apps.map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    uid = appInfo.uid,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun toggleFilterBlocked() {
        showOnlyBlocked.value = !showOnlyBlocked.value
    }

    fun toggleFilterSystem() {
        showOnlySystem.value = !showOnlySystem.value
    }

    fun updateRule(rule: Rule) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }
}

// Memory-optimized: Don't store the Drawable in the data class
data class AppInfo(
    val packageName: String,
    val name: String,
    val uid: Int,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(isDarkMode: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val repository = RuleRepository.getInstance(db.ruleDao())
    
    val viewModel: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, context.packageManager) as T
            }
        }
    )

    val rules by viewModel.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showOnlyBlocked by viewModel.showOnlyBlocked.collectAsStateWithLifecycle()
    val showOnlySystem by viewModel.showOnlySystem.collectAsStateWithLifecycle()
    
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            context.startService(Intent(context, NetAccessVpnService::class.java))
        }
    }

    var hasUsagePermission by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("NetAccess Firewall") },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, 
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(onClick = {
                            val intent = VpnService.prepare(context)
                            if (intent != null) vpnLauncher.launch(intent)
                            else context.startService(Intent(context, NetAccessVpnService::class.java))
                        }) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start VPN")
                        }
                    }
                )

                if (!hasUsagePermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Usage Access for Daily Limits", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showOnlySystem,
                        onClick = { viewModel.toggleFilterSystem() },
                        label = { Text("System Apps") }
                    )
                    FilterChip(
                        selected = showOnlyBlocked,
                        onClick = { viewModel.toggleFilterBlocked() },
                        label = { Text("Blocked Only") }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val rule = rules.find { it.packageName == app.packageName } ?: Rule(app.packageName, app.name, app.uid)
                AppRuleItem(app, rule, onUpdate = { viewModel.updateRule(it) })
            }
        }
    }
}

@Composable
fun AppRuleItem(app: AppInfo, rule: Rule, onUpdate: (Rule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(app.name, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(app.packageName, fontSize = 12.sp) },
            leadingContent = {
                // Use Coil for lazy icon loading from package name
                AsyncImage(
                    model = app.packageName,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    // Custom fetcher for package icons is automatically handled by Coil in most setups,
                    // but we can provide a fallback or placeholder here.
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onUpdate(rule.copy(wifiBlocked = !rule.wifiBlocked)) }) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "WiFi",
                            tint = if (rule.wifiBlocked) Color.Red else Color.Green
                        )
                    }
                    IconButton(onClick = { onUpdate(rule.copy(mobileBlocked = !rule.mobileBlocked)) }) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Mobile",
                            tint = if (rule.mobileBlocked) Color.Red else Color.Green
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text("Advanced Rules", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Schedule Blocking", modifier = Modifier.weight(1f))
                    Switch(
                        checked = rule.isScheduleEnabled,
                        onCheckedChange = { onUpdate(rule.copy(isScheduleEnabled = it)) }
                    )
                }

                if (rule.isScheduleEnabled) {
                    val context = LocalContext.current
                    
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
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(day, color = if (isSelected) Color.White else Color.Black, fontSize = 12.sp)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("From: ${formatTime(rule.startTimeMinutes ?: 0)}")
                        }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(context, { _, h, m -> 
                                    onUpdate(rule.copy(endTimeMinutes = h * 60 + m))
                                }, (rule.endTimeMinutes ?: 0) / 60, (rule.endTimeMinutes ?: 0) % 60, false).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("To: ${formatTime(rule.endTimeMinutes ?: 0)}")
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
                Text("UID: ${app.uid}", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Divider(color = Color.LightGray.copy(alpha = 0.5f))
    }
}

fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%02d:%02d %s".format(h12, m, ampm)
}

@Composable
private fun NetAccessTheme(isDark: Boolean = false, content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFD2A679),
        secondary = Color(0xFF8E5A3C),
        background = Color(0xFF2C1B12),
        surface = Color(0xFF2C1B12)
    )

    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF8E5A3C),
        secondary = Color(0xFFD2A679),
        background = Color(0xFFFFF2E4),
        surface = Color(0xFFFFF2E4)
    )

    val colors = if (isDark) darkColorScheme else lightColorScheme

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
