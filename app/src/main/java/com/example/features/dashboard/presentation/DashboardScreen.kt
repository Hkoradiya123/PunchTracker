package com.example.features.dashboard.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entities.PunchEventEntity
import com.example.core.model.AttendanceState
import com.example.core.model.DailyAttendanceSummary
import com.example.core.model.PunchSource
import com.example.core.model.PunchType
import com.example.core.time.TimeUtils
import com.example.features.attendance.data.AttendanceRepository
import com.example.features.attendance.domain.AttendanceEngine
import com.example.features.wifi.domain.WifiMonitor
import com.example.ui.theme.StatusDisconnecting
import com.example.ui.theme.StatusInside
import com.example.ui.theme.StatusOutside
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun DashboardScreen(
    attendanceRepository: AttendanceRepository,
    attendanceEngine: AttendanceEngine,
    wifiMonitor: WifiMonitor,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val attendanceState by attendanceRepository.getCurrentStateFlow().collectAsStateWithLifecycle(initialValue = AttendanceState.OUTSIDE_OFFICE)
    val todaySummary by attendanceRepository.getTodaySummaryFlow().collectAsStateWithLifecycle(
        initialValue = DailyAttendanceSummary(
            date = TimeUtils.todayDateString(),
            firstPunchIn = null,
            lastPunchOut = null,
            totalOfficeDurationMillis = 0L,
            sessionCount = 0,
            isCurrentlyInside = false,
            activeSessionStartTime = null
        )
    )
    val todayEvents by attendanceRepository.getEventsForDateFlow(TimeUtils.todayDateString())
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val disconnectCountdown by attendanceEngine.disconnectCountdownSeconds.collectAsStateWithLifecycle()
    val currentSsid by wifiMonitor.currentSsid.collectAsStateWithLifecycle()
    val manualOverride by attendanceRepository.getManualOverrideFlow().collectAsStateWithLifecycle(initialValue = false)

    var showPunchConfirmDialog by remember { mutableStateOf<PunchType?>(null) }

    // Live ticking timer for current session
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attendanceState) {
        while (attendanceState == AttendanceState.INSIDE_OFFICE) {
            delay(1000L)
            currentMillis = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HeaderSection()
        }

        item {
            OfficeStatusCard(
                attendanceState = attendanceState,
                isInside = todaySummary.isCurrentlyInside,
                firstIn = todaySummary.firstPunchIn,
                lastOut = todaySummary.lastPunchOut,
                currentSsid = currentSsid,
                disconnectCountdown = disconnectCountdown,
                manualOverride = manualOverride
            )
        }

        // Active Session Live Timer
        if (todaySummary.isCurrentlyInside && todaySummary.activeSessionStartTime != null) {
            item {
                ActiveSessionCard(
                    startTime = todaySummary.activeSessionStartTime!!,
                    now = currentMillis
                )
            }
        }

        // Manual Punch Action Buttons
        item {
            ManualPunchControls(
                isCurrentlyInside = todaySummary.isCurrentlyInside,
                onPunchInClick = { showPunchConfirmDialog = PunchType.PUNCH_IN },
                onPunchOutClick = { showPunchConfirmDialog = PunchType.PUNCH_OUT }
            )
        }

        // Today's Summary Metrics
        item {
            TodaySummaryMetricsCard(summary = todaySummary)
        }

        // Timeline Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY'S TIMELINE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${todayEvents.size} events",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (todayEvents.isEmpty()) {
            item {
                EmptyTimelineCard()
            }
        } else {
            items(todayEvents, key = { it.id }) { event ->
                TimelineEventItem(event = event)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation dialog for manual punch
    if (showPunchConfirmDialog != null) {
        val punchType = showPunchConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showPunchConfirmDialog = null },
            icon = {
                Icon(
                    imageVector = if (punchType == PunchType.PUNCH_IN) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = if (punchType == PunchType.PUNCH_IN) StatusInside else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = if (punchType == PunchType.PUNCH_IN) "Manual Punch In" else "Manual Punch Out"
                )
            },
            text = {
                Text(
                    text = if (punchType == PunchType.PUNCH_IN) {
                        "Are you sure you want to manually punch IN? This records attendance with MANUAL source."
                    } else {
                        "Are you sure you want to manually punch OUT? If you remain on office Wi-Fi, automatic punch-in will be suppressed until your next network transition."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (punchType == PunchType.PUNCH_IN) {
                                attendanceRepository.punchIn(source = PunchSource.MANUAL)
                            } else {
                                attendanceRepository.punchOut(source = PunchSource.MANUAL)
                            }
                            showPunchConfirmDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (punchType == PunchType.PUNCH_IN) StatusInside else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_punch_dialog_button")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPunchConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeaderSection() {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = TimeUtils.formatDateHeader(TimeUtils.todayDateString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfficeStatusCard(
    attendanceState: AttendanceState,
    isInside: Boolean,
    firstIn: Long?,
    lastOut: Long?,
    currentSsid: String?,
    disconnectCountdown: Int?,
    manualOverride: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("office_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInside) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OFFICE STATUS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isInside) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                if (currentSsid != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentSsid,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isInside) StatusInside else StatusOutside).copy(
                                alpha = if (isInside) pulseAlpha else 1f
                            )
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isInside) "IN OFFICE" else "OUTSIDE OFFICE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isInside) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isInside) {
                    "Since ${TimeUtils.formatTime(firstIn)}"
                } else {
                    if (lastOut != null) "Last left at ${TimeUtils.formatTime(lastOut)}" else "Not clocked in today"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isInside) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Flapping / Grace period alert
            AnimatedVisibility(visible = disconnectCountdown != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = StatusDisconnecting.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = StatusDisconnecting,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi disconnected. Grace period: ${disconnectCountdown}s before punch out",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = StatusDisconnecting
                        )
                    }
                }
            }

            // Manual override indicator
            if (manualOverride && !isInside) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manual punch out override active. Auto-punch suppressed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    startTime: Long,
    now: Long
) {
    val durationMillis = maxOf(0L, now - startTime)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CURRENT SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${TimeUtils.formatTime(startTime)} → Now",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = TimeUtils.formatDuration(durationMillis, includeSeconds = true),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ManualPunchControls(
    isCurrentlyInside: Boolean,
    onPunchInClick: () -> Unit,
    onPunchOutClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPunchInClick,
            enabled = !isCurrentlyInside,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("dashboard_punch_in_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusInside)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Punch In", fontWeight = FontWeight.Bold)
        }

        FilledTonalButton(
            onClick = onPunchOutClick,
            enabled = isCurrentlyInside,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("dashboard_punch_out_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Punch Out", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TodaySummaryMetricsCard(summary: DailyAttendanceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("today_summary_metrics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TODAY'S SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "First Punch In",
                    value = TimeUtils.formatTime(summary.firstPunchIn),
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Last Punch Out",
                    value = if (summary.isCurrentlyInside) "—" else TimeUtils.formatTime(summary.lastPunchOut),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Office Time",
                    value = TimeUtils.formatDuration(summary.totalOfficeDurationMillis),
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "Sessions",
                    value = summary.sessionCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TimelineEventItem(event: PunchEventEntity) {
    val isPunchIn = event.type == PunchType.PUNCH_IN.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Styled timeline node
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isPunchIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPunchIn) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = if (isPunchIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Event info card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isPunchIn) "Punch In" else "Punch Out",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (event.ssid != null) {
                        Text(
                            text = event.ssid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TimeUtils.formatTime(event.timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = event.source.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No punch events recorded today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Connect to office Wi-Fi or punch in manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
