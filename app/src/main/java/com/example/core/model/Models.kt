package com.example.core.model

enum class AttendanceState {
    OUTSIDE_OFFICE,
    INSIDE_OFFICE,
    AT_HOME,
    UNKNOWN,
    CONNECTING,
    DISCONNECTING
}

enum class PunchType {
    PUNCH_IN,
    PUNCH_OUT
}

enum class PunchSource {
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    MANUAL,
    SYSTEM_RECOVERY
}

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    OPEN,
    INVALID
}

enum class WifiMatchMode {
    SSID,
    BSSID
}

data class DailyAttendanceSummary(
    val date: String, // YYYY-MM-DD
    val firstPunchIn: Long?,
    val lastPunchOut: Long?,
    val totalOfficeDurationMillis: Long,
    val sessionCount: Int,
    val isCurrentlyInside: Boolean,
    val activeSessionStartTime: Long?
)

data class WeeklyStats(
    val totalDurationMillis: Long,
    val averageDailyMillis: Long,
    val daysPresent: Int,
    val avgFirstInTimeMinutes: Int?, // minutes from midnight
    val avgLastOutTimeMinutes: Int?, // minutes from midnight
    val dailyHours: List<DayDuration>
)

data class DayDuration(
    val date: String,
    val dayName: String,
    val durationMillis: Long,
    val isPresent: Boolean
)
