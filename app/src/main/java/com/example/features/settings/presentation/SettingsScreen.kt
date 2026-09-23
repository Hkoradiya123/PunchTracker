package com.example.features.settings.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entities.OfficeWifiEntity
import com.example.features.widget.WidgetManager
import com.example.features.wifi.data.WifiRepository
import com.example.features.wifi.domain.WifiMonitor
import com.example.ui.theme.StatusHome
import com.example.ui.theme.StatusHomeContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    wifiRepository: WifiRepository,
    wifiMonitor: WifiMonitor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val networks by wifiRepository.getAllNetworksFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val offices by wifiRepository.getAllOfficesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val gracePeriod by wifiRepository.getGracePeriodSecondsFlow().collectAsStateWithLifecycle(initialValue = 30)
    val currentSsid by wifiMonitor.currentSsid.collectAsStateWithLifecycle()
    val currentBssid by wifiMonitor.currentBssid.collectAsStateWithLifecycle()

    val officeNetworks = networks.filter { it.networkType == "OFFICE" }
    val homeNetworks = networks.filter { it.networkType == "HOME" }

    var showAddNetworkDialog by remember { mutableStateOf(false) }
    var networkTypeForDialog by remember { mutableStateOf("OFFICE") }
    var prefilledSsid by remember { mutableStateOf("") }
    var prefilledBssid by remember { mutableStateOf("") }

    var showGracePeriodDialog by remember { mutableStateOf(false) }
    var networkToDelete by remember { mutableStateOf<OfficeWifiEntity?>(null) }

    // Permission launcher
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        wifiMonitor.readCurrentWifi()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "Settings & Wi-Fi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Configure office networks, flapping timers, and permissions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Permissions Status Card
        item {
            PermissionsCard(
                hasPermission = hasLocationPermission,
                onRequestPermissions = {
                    permissionLauncher.launch(permissionsToRequest)
                }
            )
        }

        // Office Networks Header and Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("office_networks_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OFFICE WI-FI NETWORKS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp
                                )
                            }
                            val officeName = offices.firstOrNull()?.name ?: "Office HQ"
                            Text(
                                text = "Group: $officeName (${officeNetworks.size} configured)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                prefilledSsid = currentSsid ?: ""
                                prefilledBssid = currentBssid ?: ""
                                networkTypeForDialog = "OFFICE"
                                showAddNetworkDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("add_current_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Current", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                prefilledSsid = ""
                                prefilledBssid = ""
                                networkTypeForDialog = "OFFICE"
                                showAddNetworkDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("add_custom_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Manual", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // List of configured office networks
        if (officeNetworks.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "No office Wi-Fi networks configured yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(officeNetworks, key = { it.id }) { network ->
                NetworkItemCard(
                    network = network,
                    onToggleEnabled = { enabled ->
                        coroutineScope.launch {
                            wifiRepository.updateNetwork(network.copy(enabled = enabled))
                        }
                    },
                    onDelete = {
                        networkToDelete = network
                    }
                )
            }
        }

        // Home Networks Header and Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("home_networks_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = StatusHome,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HOME WI-FI NETWORKS (AT HOME)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusHome,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Home Locations (${homeNetworks.size} configured)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "When connected, PunchTracker recognizes you are at home and switches status to AT HOME.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                prefilledSsid = currentSsid ?: ""
                                prefilledBssid = currentBssid ?: ""
                                networkTypeForDialog = "HOME"
                                showAddNetworkDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("add_current_home_wifi_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusHome),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Current as Home", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                prefilledSsid = ""
                                prefilledBssid = ""
                                networkTypeForDialog = "HOME"
                                showAddNetworkDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("add_custom_home_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Manual Home", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // List of configured home networks
        if (homeNetworks.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = StatusHomeContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "No home Wi-Fi networks configured yet. Add your home network above to show when you are at home.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4338CA)
                    )
                }
            }
        } else {
            items(homeNetworks, key = { it.id }) { network ->
                NetworkItemCard(
                    network = network,
                    onToggleEnabled = { enabled ->
                        coroutineScope.launch {
                            wifiRepository.updateNetwork(network.copy(enabled = enabled))
                        }
                    },
                    onDelete = {
                        networkToDelete = network
                    }
                )
            }
        }

        // Flapping & Grace Period Setting Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("grace_period_setting_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disconnect Grace Period",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Waits before punching out to protect against Wi-Fi flapping",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(onClick = { showGracePeriodDialog = true }) {
                        Text("${gracePeriod}s")
                    }
                }
            }
        }

        // Widget refresh card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Home-Screen Widget",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sync and update Android home-screen widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            WidgetManager.updateWidgets(context)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Add Network Dialog
    if (showAddNetworkDialog) {
        val activeOfficeId = offices.firstOrNull()?.id ?: ""
        AddNetworkDialog(
            defaultSsid = prefilledSsid,
            defaultBssid = prefilledBssid,
            initialNetworkType = networkTypeForDialog,
            onDismiss = { showAddNetworkDialog = false },
            onConfirm = { name, ssid, bssid, matchBssid, type ->
                coroutineScope.launch {
                    wifiRepository.addNetwork(
                        officeId = activeOfficeId,
                        name = name,
                        ssid = ssid,
                        bssid = bssid,
                        matchBssid = matchBssid,
                        networkType = type
                    )
                    showAddNetworkDialog = false
                }
            }
        )
    }

    // Grace Period Selection Dialog
    if (showGracePeriodDialog) {
        GracePeriodDialog(
            currentSeconds = gracePeriod,
            onDismiss = { showGracePeriodDialog = false },
            onSelect = { seconds ->
                coroutineScope.launch {
                    wifiRepository.setGracePeriodSeconds(seconds)
                    showGracePeriodDialog = false
                }
            }
        )
    }

    // Delete Network Confirmation Dialog
    if (networkToDelete != null) {
        val net = networkToDelete!!
        AlertDialog(
            onDismissRequest = { networkToDelete = null },
            title = { Text("Delete Wi-Fi Network?") },
            text = { Text("Are you sure you want to remove '${net.name}' (${net.ssid})?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            wifiRepository.deleteNetwork(net.id)
                            networkToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { networkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionsCard(
    hasPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermission) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.Check else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasPermission) "Wi-Fi Permissions Active" else "Permissions Needed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasPermission) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasPermission) {
                        "Background Wi-Fi detection is enabled"
                    } else {
                        "PunchTracker needs Wi-Fi access to detect office arrival and departure"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasPermission) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (!hasPermission) {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Grant")
                }
            }
        }
    }
}

@Composable
private fun NetworkItemCard(
    network: OfficeWifiEntity,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val isHome = network.networkType == "HOME"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHome) StatusHomeContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isHome) Icons.Default.Home else Icons.Default.Business,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isHome) StatusHome else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = network.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SSID: ${network.ssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (network.matchBssid && network.bssid != null) {
                    Text(
                        text = "BSSID: ${network.bssid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isHome) StatusHome.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isHome) "HOME NETWORK" else "OFFICE NETWORK",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHome) StatusHome else MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (network.matchBssid) "BSSID Match" else "SSID Match",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = network.enabled,
                    onCheckedChange = onToggleEnabled
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Wi-Fi",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddNetworkDialog(
    defaultSsid: String,
    defaultBssid: String,
    initialNetworkType: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, ssid: String, bssid: String?, matchBssid: Boolean, networkType: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialNetworkType) }
    var name by remember {
        mutableStateOf(
            if (defaultSsid.isNotEmpty()) defaultSsid else if (initialNetworkType == "HOME") "Home Wi-Fi" else "Office Main"
        )
    }
    var ssid by remember { mutableStateOf(defaultSsid) }
    var bssid by remember { mutableStateOf(defaultBssid) }
    var matchBssid by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (selectedType == "HOME") "Add Home Wi-Fi" else "Add Office Wi-Fi")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Network Type Selector
                Text(
                    text = "NETWORK TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            selectedType = "OFFICE"
                            if (name == "Home Wi-Fi") name = "Office Main"
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedType == "OFFICE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedType == "OFFICE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Office",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selectedType == "OFFICE") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedType == "OFFICE") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            selectedType = "HOME"
                            if (name == "Office Main") name = "Home Wi-Fi"
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedType == "HOME") StatusHomeContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedType == "HOME") StatusHome else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selectedType == "HOME") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedType == "HOME") Color(0xFF3730A3) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = if (selectedType == "HOME") {
                        "When connected to Home Wi-Fi, app shows AT HOME status and pauses office punch-in."
                    } else {
                        "When connected to Office Wi-Fi, app triggers automatic Punch In and tracks work duration."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Network Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("SSID (Wi-Fi Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bssid,
                    onValueChange = { bssid = it },
                    label = { Text("BSSID (Optional MAC Address)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = matchBssid,
                        onCheckedChange = { matchBssid = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Match by specific BSSID for precision",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ssid.isBlank()) {
                        error = "SSID is required"
                        return@Button
                    }
                    onConfirm(name.ifBlank { ssid }, ssid.trim(), bssid.ifBlank { null }, matchBssid, selectedType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == "HOME") StatusHome else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (selectedType == "HOME") "Add Home Network" else "Add Office Network")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GracePeriodDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        10 to "10 seconds",
        30 to "30 seconds (Default)",
        60 to "60 seconds (1 minute)",
        120 to "2 minutes",
        300 to "5 minutes"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi Flapping Grace Period") },
        text = {
            Column {
                Text(
                    text = "If Wi-Fi disconnects temporarily, PunchTracker waits this long before registering a Punch Out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                options.forEach { (sec, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentSeconds == sec),
                            onClick = { onSelect(sec) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
