package com.example.core.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {

    fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun yesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    fun toDateString(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "--:--"
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatShortTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "--:--"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateHeader(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return dateStr
            val today = todayDateString()
            val yesterday = yesterdayDateString()
            when (dateStr) {
                today -> "Today (${SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)})"
                yesterday -> "Yesterday (${SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)})"
                else -> SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatDuration(durationMillis: Long, includeSeconds: Boolean = false): String {
        if (durationMillis <= 0) return if (includeSeconds) "00h 00m 00s" else "00h 00m"
        val totalSecs = durationMillis / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60

        return if (includeSeconds) {
            String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02dh %02dm", hours, minutes)
        }
    }

    fun formatMinutesToTime(minutesFromMidnight: Int?): String {
        if (minutesFromMidnight == null) return "--:--"
        val hours = minutesFromMidnight / 60
        val mins = minutesFromMidnight % 60
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, mins)
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    fun getDayStartMillis(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return 0L
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    fun getDayEndMillis(dateStr: String): Long {
        val start = getDayStartMillis(dateStr)
        return start + (24 * 60 * 60 * 1000L) - 1
    }

    /**
     * Calculates duration within a specific date segment, properly handling overnight sessions.
     */
    fun calculateOverlapDuration(
        sessionStart: Long,
        sessionEnd: Long,
        dayStart: Long,
        dayEnd: Long
    ): Long {
        val effectiveStart = maxOf(sessionStart, dayStart)
        val effectiveEnd = minOf(sessionEnd, dayEnd)
        return if (effectiveEnd > effectiveStart) effectiveEnd - effectiveStart else 0L
    }
}
