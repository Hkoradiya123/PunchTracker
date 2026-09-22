package com.example.features.history.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entities.AttendanceSessionEntity
import com.example.core.model.PunchSource
import com.example.core.model.PunchType
import com.example.core.time.TimeUtils
import com.example.features.attendance.data.AttendanceRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    attendanceRepository: AttendanceRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val allSessions by attendanceRepository.getAllSessionsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // Group sessions by date
    val sessionsByDate = remember(allSessions) {
        allSessions.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    var editingSession by remember { mutableStateOf<AttendanceSessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<AttendanceSessionEntity?>(null) }
    var showAddEventDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEventDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_manual_punch_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add attendance event")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Attendance History",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Overview of recorded office days and sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (sessionsByDate.isEmpty()) {
                item {
                    EmptyHistoryCard()
                }
            } else {
                items(sessionsByDate.keys.toList(), key = { it }) { date ->
                    val daySessions = sessionsByDate[date] ?: emptyList()
                    val isExpanded = expandedDates[date] ?: false

                    DayHistoryCard(
                        date = date,
                        sessions = daySessions,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedDates[date] = !isExpanded
                        },
                        onEditSession = { session -> editingSession = session },
                        onDeleteSession = { session -> sessionToDelete = session }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Edit Session Dialog
    if (editingSession != null) {
        val session = editingSession!!
        EditSessionDialog(
            session = session,
            onDismiss = { editingSession = null },
            onConfirm = { punchIn, punchOut ->
                coroutineScope.launch {
                    attendanceRepository.editSession(
                        sessionId = session.id,
                        punchIn = punchIn,
                        punchOut = punchOut,
                        modifiedBy = "USER"
                    )
                    editingSession = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (sessionToDelete != null) {
        val session = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session?") },
            text = {
                Text("Are you sure you want to delete the session from ${TimeUtils.formatTime(session.punchInTime)} to ${TimeUtils.formatTime(session.punchOutTime)}? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            attendanceRepository.deleteSession(session.id)
                            sessionToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Missing Event Dialog
    if (showAddEventDialog) {
        AddMissingPunchDialog(
            onDismiss = { showAddEventDialog = false },
            onConfirm = { type, timestamp ->
                coroutineScope.launch {
                    attendanceRepository.addEvent(type, timestamp, PunchSource.MANUAL)
                    showAddEventDialog = false
                }
            }
        )
    }
}

@Composable
private fun DayHistoryCard(
    date: String,
    sessions: List<AttendanceSessionEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditSession: (AttendanceSessionEntity) -> Unit,
    onDeleteSession: (AttendanceSessionEntity) -> Unit
) {
    val dayStart = TimeUtils.getDayStartMillis(date)
    val dayEnd = TimeUtils.getDayEndMillis(date)

    var totalDuration = 0L
    for (s in sessions) {
        val end = s.punchOutTime ?: System.currentTimeMillis()
        totalDuration += TimeUtils.calculateOverlapDuration(s.punchInTime, end, dayStart, dayEnd)
    }

    val firstIn = sessions.minOfOrNull { it.punchInTime }
    val lastOut = sessions.mapNotNull { it.punchOutTime }.maxOfOrNull { it }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("history_day_card_$date"),
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
                    Text(
                        text = TimeUtils.formatDateHeader(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${TimeUtils.formatTime(firstIn)} → ${TimeUtils.formatTime(lastOut)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = TimeUtils.formatDuration(totalDuration),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand sessions",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "SESSIONS (${sessions.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    sessions.forEach { session ->
                        SessionItemRow(
                            session = session,
                            onEdit = { onEditSession(session) },
                            onDelete = { onDeleteSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    session: AttendanceSessionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val duration = if (session.punchOutTime != null) session.punchOutTime - session.punchInTime else 0L

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${TimeUtils.formatTime(session.punchInTime)} → ${TimeUtils.formatTime(session.punchOutTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeUtils.formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    if (session.modifiedBy != null) {
                        Text(
                            text = " • Edited by ${session.modifiedBy}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit session",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSessionDialog(
    session: AttendanceSessionEntity,
    onDismiss: () -> Unit,
    onConfirm: (punchIn: Long, punchOut: Long?) -> Unit
) {
    var inTimeStr by remember { mutableStateOf(TimeUtils.formatShortTime(session.punchInTime)) }
    var outTimeStr by remember { mutableStateOf(TimeUtils.formatShortTime(session.punchOutTime ?: System.currentTimeMillis())) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Session (${session.date})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = inTimeStr,
                    onValueChange = { inTimeStr = it },
                    label = { Text("Punch In Time (HH:mm 24h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = outTimeStr,
                    onValueChange = { outTimeStr = it },
                    label = { Text("Punch Out Time (HH:mm 24h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Audit: Edits are marked with modifiedBy=USER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val inDate = sdf.parse("${session.date} $inTimeStr")
                        val outDate = sdf.parse("${session.date} $outTimeStr")

                        if (inDate == null || outDate == null) {
                            errorMessage = "Invalid time format. Use HH:mm (e.g. 09:15)"
                            return@Button
                        }
                        if (outDate.time <= inDate.time) {
                            errorMessage = "Punch out time must be after punch in time"
                            return@Button
                        }
                        onConfirm(inDate.time, outDate.time)
                    } catch (e: Exception) {
                        errorMessage = "Error parsing times: ${e.message}"
                    }
                }
            ) {
                Text("Save Changes")
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
private fun AddMissingPunchDialog(
    onDismiss: () -> Unit,
    onConfirm: (PunchType, Long) -> Unit
) {
    var isPunchIn by remember { mutableStateOf(true) }
    var dateStr by remember { mutableStateOf(TimeUtils.todayDateString()) }
    var timeStr by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Missing Punch Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isPunchIn = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPunchIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isPunchIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Punch In")
                    }
                    Button(
                        onClick = { isPunchIn = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPunchIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isPunchIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Punch Out")
                    }
                }

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("Time (HH:mm 24h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val d = sdf.parse("$dateStr $timeStr")
                        if (d == null) {
                            errorMessage = "Invalid date/time"
                            return@Button
                        }
                        val type = if (isPunchIn) PunchType.PUNCH_IN else PunchType.PUNCH_OUT
                        onConfirm(type, d.time)
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    }
                }
            ) {
                Text("Add Event")
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
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No history recorded yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Punches will show up here by date as you log office hours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
