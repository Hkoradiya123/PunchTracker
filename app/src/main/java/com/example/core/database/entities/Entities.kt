package com.example.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "offices")
data class OfficeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "office_wifis",
    indices = [
        Index("officeId"),
        Index("enabled"),
        Index("ssid")
    ]
)
data class OfficeWifiEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val officeId: String,
    val name: String,
    val ssid: String,
    val bssid: String? = null,
    val matchBssid: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "punch_events",
    indices = [
        Index("timestamp"),
        Index("type", "timestamp")
    ]
)
data class PunchEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // PUNCH_IN, PUNCH_OUT
    val timestamp: Long,
    val wifiId: String? = null,
    val ssid: String? = null,
    val bssid: String? = null,
    val source: String, // WIFI_CONNECTED, WIFI_DISCONNECTED, MANUAL, SYSTEM_RECOVERY
    val modifiedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance_sessions",
    indices = [
        Index("date"),
        Index("status"),
        Index("punchInTime")
    ]
)
data class AttendanceSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val punchInTime: Long,
    val punchOutTime: Long? = null,
    val durationMillis: Long = 0L,
    val status: String, // ACTIVE, COMPLETED, OPEN, INVALID
    val source: String = "AUTO",
    val modifiedBy: String? = null,
    val modifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "work_schedules")
data class WorkScheduleEntity(
    @PrimaryKey val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val dayName: String,
    val startTime: String = "09:00",
    val endTime: String = "18:00",
    val isWorkDay: Boolean = true,
    val expectedHours: Double = 8.0
)

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val widgetId: Int,
    val dateMode: String = "TODAY", // TODAY, YESTERDAY, CUSTOM
    val customDate: String? = null,
    val showFirstIn: Boolean = true,
    val showLastOut: Boolean = true,
    val showTotalTime: Boolean = true,
    val showCurrentSession: Boolean = true,
    val showSessionCount: Boolean = false
)

@Entity(
    tableName = "logs",
    indices = [Index("timestamp")]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String, // INFO, DEBUG, WARN, ERROR
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
