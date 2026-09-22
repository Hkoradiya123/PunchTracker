package com.example.features.debug.presentation

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.AppDatabase
import com.example.core.database.entities.AttendanceSessionEntity
import com.example.core.database.entities.LogEntryEntity
import com.example.core.database.entities.PunchEventEntity
import com.example.core.logging.AppLogger
import com.example.core.model.AttendanceState
import com.example.core.model.PunchSource
import com.example.core.model.PunchType
import com.example.core.model.SessionStatus
import com.example.core.time.TimeUtils
import com.example.features.attendance.data.AttendanceRepository
import com.example.features.attendance.domain.AttendanceEngine
import com.example.features.widget.WidgetManager
import com.example.features.wifi.data.WifiRepository
import com.example.features.wifi.domain.WifiMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen(
    attendanceRepository: AttendanceRepository,
    attendanceEngine: AttendanceEngine,
    wifiRepository: WifiRepository,
    wifiMonitor: WifiMonitor,
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val attendanceState by attendanceRepository.getCurrentStateFlow().collectAsStateWithLifecycle(initialValue = AttendanceState.OUTSIDE_OFFICE)
    val manualOverride by attendanceRepository.getManualOverrideFlow().collectAsStateWithLifecycle(initialValue = false)
    val disconnectCountdown by attendanceEngine.disconnectCountdownSeconds.collectAsStateWithLifecycle()
    val currentSsid by wifiMonitor.currentSsid.collectAsStateWithLifecycle()
    val currentBssid by wifiMonitor.currentBssid.collectAsStateWithLifecycle()
    val enabledNetworks by wifiRepository.getEnabledNetworksFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentLogs by database.logDao().getRecentLogsFlow(limit = 100).collectAsStateWithLifecycle(initialValue = emptyList())

    val isMatchedOfficeWifi = enabledNetworks.any {
        it.ssid.equals(currentSsid, ignoreCase = true) || (it.matchBssid && it.bssid == currentBssid)
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
                    text = "Diagnostics & Testing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Live telemetry, network simulations, and event log inspector",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("diagnostics_telemetry_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REAL-TIME STATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticRow("Attendance State", attendanceState.name)
                    DiagnosticRow("Manual Override Active", manualOverride.toString())
                    DiagnosticRow("Current Wi-Fi SSID", currentSsid ?: "None (Disconnected)")
                    DiagnosticRow("Current BSSID", currentBssid ?: "None")
                    DiagnosticRow("Matches Office Wi-Fi", if (isMatchedOfficeWifi) "YES" else "NO")
                    DiagnosticRow(
                        "Disconnect Grace Timer",
                        if (disconnectCountdown != null) "${disconnectCountdown}s remaining" else "Inactive"
                    )
                }
            }
        }

        // Simulation & Testing Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("diagnostics_simulation_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TEST SIMULATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Simulate Wi-Fi transitions to test the state machine without leaving your desk:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 1: Connect & Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val targetSsid = enabledNetworks.firstOrNull()?.ssid ?: "CompanyWiFi"
                                wifiMonitor.simulateConnect(targetSsid)
                            },
                            modifier = Modifier.weight(1f).testTag("sim_connect_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Connect Office", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                val secondSsid = enabledNetworks.getOrNull(1)?.ssid ?: "CompanyWiFi-5G"
                                wifiMonitor.simulateConnect(secondSsid)
                            },
                            modifier = Modifier.weight(1f).testTag("sim_roam_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Switch AP", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Disconnect & Flap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { wifiMonitor.simulateDisconnect() },
                            modifier = Modifier.weight(1f).testTag("sim_disconnect_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val targetSsid = enabledNetworks.firstOrNull()?.ssid ?: "CompanyWiFi"
                                wifiMonitor.simulateQuickReconnect(targetSsid, reconnectAfterMs = 3000L)
                            },
                            modifier = Modifier.weight(1f).testTag("sim_flapping_wifi_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Test Flapping", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 3: Seed Sample Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    seed7DaysDemoData(database)
                                    WidgetManager.updateWidgets(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f).testTag("seed_demo_data_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Seed 7-Day Demo Data", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Event Logs Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STRUCTURED LOGS (${recentLogs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            database.logDao().clearLogs()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear logs",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Event Logs List
        items(recentLogs, key = { it.id }) { log ->
            LogEntryRow(log = log)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LogEntryRow(log: LogEntryEntity) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (log.level) {
                    "ERROR" -> MaterialTheme.colorScheme.errorContainer
                    "WARN" -> MaterialTheme.colorScheme.tertiaryContainer
                    "INFO" -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            ) {
                Text(
                    text = log.level,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = TimeUtils.formatTime(log.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Seeds sample attendance data for the last 7 days so users can see rich charts and history.
 */
private suspend fun seed7DaysDemoData(database: AppDatabase) {
    val sessionDao = database.attendanceSessionDao()
    val eventDao = database.punchEventDao()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val nowCal = Calendar.getInstance()

    for (i in 1..6) {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
        val dateStr = sdf.format(c.time)

        // Session 1: Morning (09:15 - 12:45)
        val in1Cal = Calendar.getInstance().apply {
            time = c.time
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
        }
        val out1Cal = Calendar.getInstance().apply {
            time = c.time
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
        }

        // Session 2: Afternoon (13:30 - 18:15)
        val in2Cal = Calendar.getInstance().apply {
            time = c.time
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val out2Cal = Calendar.getInstance().apply {
            time = c.time
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
        }

        val s1 = AttendanceSessionEntity(
            date = dateStr,
            punchInTime = in1Cal.timeInMillis,
            punchOutTime = out1Cal.timeInMillis,
            durationMillis = out1Cal.timeInMillis - in1Cal.timeInMillis,
            status = SessionStatus.COMPLETED.name,
            source = "AUTO"
        )
        val s2 = AttendanceSessionEntity(
            date = dateStr,
            punchInTime = in2Cal.timeInMillis,
            punchOutTime = out2Cal.timeInMillis,
            durationMillis = out2Cal.timeInMillis - in2Cal.timeInMillis,
            status = SessionStatus.COMPLETED.name,
            source = "AUTO"
        )

        sessionDao.insertSession(s1)
        sessionDao.insertSession(s2)

        eventDao.insertEvent(
            PunchEventEntity(
                type = PunchType.PUNCH_IN.name,
                timestamp = in1Cal.timeInMillis,
                ssid = "CompanyWiFi",
                source = PunchSource.WIFI_CONNECTED.name
            )
        )
        eventDao.insertEvent(
            PunchEventEntity(
                type = PunchType.PUNCH_OUT.name,
                timestamp = out1Cal.timeInMillis,
                ssid = "CompanyWiFi",
                source = PunchSource.WIFI_DISCONNECTED.name
            )
        )
        eventDao.insertEvent(
            PunchEventEntity(
                type = PunchType.PUNCH_IN.name,
                timestamp = in2Cal.timeInMillis,
                ssid = "CompanyWiFi",
                source = PunchSource.WIFI_CONNECTED.name
            )
        )
        eventDao.insertEvent(
            PunchEventEntity(
                type = PunchType.PUNCH_OUT.name,
                timestamp = out2Cal.timeInMillis,
                ssid = "CompanyWiFi",
                source = PunchSource.WIFI_DISCONNECTED.name
            )
        )
    }

    AppLogger.info("Diagnostics", "Successfully seeded 7 days of sample attendance data")
}
