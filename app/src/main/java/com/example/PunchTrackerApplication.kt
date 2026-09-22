package com.example

import android.app.Application
import com.example.core.database.AppDatabase
import com.example.core.logging.AppLogger
import com.example.features.attendance.data.AttendanceRepository
import com.example.features.attendance.data.AttendanceRepositoryImpl
import com.example.features.attendance.domain.AttendanceEngine
import com.example.features.widget.WidgetManager
import com.example.features.wifi.data.WifiRepository
import com.example.features.wifi.data.WifiRepositoryImpl
import com.example.features.wifi.domain.WifiMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PunchTrackerApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val attendanceRepository: AttendanceRepository by lazy { AttendanceRepositoryImpl(database) }
    val wifiRepository: WifiRepository by lazy { WifiRepositoryImpl(database) }
    val attendanceEngine: AttendanceEngine by lazy {
        AttendanceEngine(this, attendanceRepository, wifiRepository)
    }
    val wifiMonitor: WifiMonitor by lazy {
        WifiMonitor(this, attendanceEngine)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(database)
        AppLogger.info("Application", "PunchTrackerApplication initialized")

        applicationScope.launch {
            wifiRepository.ensureDefaultOffice()
            wifiMonitor.startMonitoring()

            // Startup state recovery
            val ssid = wifiMonitor.currentSsid.value
            val bssid = wifiMonitor.currentBssid.value
            attendanceEngine.reconcileState(ssid, bssid)
            WidgetManager.updateWidgets(this@PunchTrackerApplication)
        }
    }
}
