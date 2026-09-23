package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AttendanceState
import com.example.features.dashboard.presentation.DashboardScreen
import com.example.features.debug.presentation.DebugScreen
import com.example.features.history.presentation.HistoryScreen
import com.example.features.settings.presentation.SettingsScreen
import com.example.features.statistics.presentation.StatisticsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusDisconnecting
import com.example.ui.theme.StatusHome
import com.example.ui.theme.StatusInside
import com.example.ui.theme.StatusOutside

sealed class Screen(val title: String, val icon: ImageVector, val tag: String) {
    data object Dashboard : Screen("Dashboard", Icons.Default.Dashboard, "nav_dashboard")
    data object History : Screen("History", Icons.Default.History, "nav_history")
    data object Statistics : Screen("Statistics", Icons.Default.BarChart, "nav_statistics")
    data object Settings : Screen("Settings", Icons.Default.Settings, "nav_settings")
    data object Debug : Screen("Debug", Icons.Default.BugReport, "nav_debug")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PunchTrackerApplication

        setContent {
            MyApplicationTheme {
                PunchTrackerApp(app)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunchTrackerApp(app: PunchTrackerApplication) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val screens = listOf(
        Screen.Dashboard,
        Screen.History,
        Screen.Statistics,
        Screen.Settings,
        Screen.Debug
    )

    val attendanceState by app.attendanceRepository.getCurrentStateFlow()
        .collectAsStateWithLifecycle(initialValue = AttendanceState.OUTSIDE_OFFICE)
    val isInside = attendanceState == AttendanceState.INSIDE_OFFICE

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PunchTracker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val (chipColor, chipText) = when (attendanceState) {
                        AttendanceState.INSIDE_OFFICE -> Pair(StatusInside, "IN OFFICE")
                        AttendanceState.AT_HOME -> Pair(StatusHome, "AT HOME")
                        AttendanceState.DISCONNECTING -> Pair(StatusDisconnecting, "DISCONNECTING")
                        else -> Pair(StatusOutside, "OUTSIDE")
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(chipColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = chipColor,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                screens.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag(screen.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> DashboardScreen(
                    attendanceRepository = app.attendanceRepository,
                    attendanceEngine = app.attendanceEngine,
                    wifiMonitor = app.wifiMonitor
                )
                1 -> HistoryScreen(
                    attendanceRepository = app.attendanceRepository
                )
                2 -> StatisticsScreen(
                    attendanceRepository = app.attendanceRepository
                )
                3 -> SettingsScreen(
                    wifiRepository = app.wifiRepository,
                    wifiMonitor = app.wifiMonitor
                )
                4 -> DebugScreen(
                    attendanceRepository = app.attendanceRepository,
                    attendanceEngine = app.attendanceEngine,
                    wifiRepository = app.wifiRepository,
                    wifiMonitor = app.wifiMonitor,
                    database = app.database
                )
            }
        }
    }
}
