package com.example.features.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.core.database.AppDatabase
import com.example.core.model.AttendanceState
import com.example.core.time.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetManager {

    fun updateWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext) ?: return
        val componentName = ComponentName(context.applicationContext, PunchTrackerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val settingsDao = db.appSettingsDao()
                val sessionDao = db.attendanceSessionDao()
                val widgetConfigDao = db.widgetConfigDao()

                val currentStateStr = settingsDao.getValue("attendance_state") ?: AttendanceState.OUTSIDE_OFFICE.name
                val isInside = currentStateStr == AttendanceState.INSIDE_OFFICE.name

                val todayDate = TimeUtils.todayDateString()
                val todayStart = TimeUtils.getDayStartMillis(todayDate)
                val todayEnd = TimeUtils.getDayEndMillis(todayDate)
                val sessions = sessionDao.getSessionsForDate(todayDate)
                val activeSession = sessionDao.getActiveSession()

                val firstIn = sessions.minOfOrNull { it.punchInTime } ?: activeSession?.punchInTime
                val lastOut = if (isInside) null else sessions.mapNotNull { it.punchOutTime }.maxOfOrNull { it }

                var totalDuration = 0L
                for (s in sessions) {
                    if (s.punchOutTime != null) {
                        totalDuration += TimeUtils.calculateOverlapDuration(s.punchInTime, s.punchOutTime, todayStart, todayEnd)
                    }
                }
                if (isInside && activeSession != null) {
                    totalDuration += TimeUtils.calculateOverlapDuration(activeSession.punchInTime, System.currentTimeMillis(), todayStart, todayEnd)
                }

                val currentSessionDuration = if (isInside && activeSession != null) {
                    System.currentTimeMillis() - activeSession.punchInTime
                } else null

                for (widgetId in appWidgetIds) {
                    val config = widgetConfigDao.getConfig(widgetId)
                    val views = RemoteViews(context.packageName, R.layout.widget_punch_tracker)

                    // Set status
                    val statusText = when (currentStateStr) {
                        AttendanceState.INSIDE_OFFICE.name -> "IN OFFICE"
                        AttendanceState.AT_HOME.name -> "AT HOME"
                        AttendanceState.DISCONNECTING.name -> "DISCONNECTING"
                        else -> "OUTSIDE OFFICE"
                    }
                    val statusColor = when (currentStateStr) {
                        AttendanceState.INSIDE_OFFICE.name -> 0xFF38BDF8.toInt()
                        AttendanceState.AT_HOME.name -> 0xFF818CF8.toInt()
                        AttendanceState.DISCONNECTING.name -> 0xFFF59E0B.toInt()
                        else -> 0xFF94A3B8.toInt()
                    }

                    views.setTextViewText(
                        R.id.widget_status_text,
                        statusText
                    )
                    views.setTextColor(
                        R.id.widget_status_text,
                        statusColor
                    )

                    // First in & Last out
                    views.setTextViewText(R.id.widget_text_first_in, TimeUtils.formatTime(firstIn))
                    views.setTextViewText(R.id.widget_text_last_out, TimeUtils.formatTime(lastOut))

                    // Total time & Current session
                    views.setTextViewText(R.id.widget_text_total, TimeUtils.formatDuration(totalDuration))
                    views.setTextViewText(
                        R.id.widget_text_current_session,
                        if (currentSessionDuration != null) TimeUtils.formatDuration(currentSessionDuration) else "--:--"
                    )

                    // Apply config visibility if configured
                    if (config != null) {
                        views.setViewVisibility(R.id.widget_box_first_in, if (config.showFirstIn) View.VISIBLE else View.GONE)
                        views.setViewVisibility(R.id.widget_box_last_out, if (config.showLastOut) View.VISIBLE else View.GONE)
                        views.setViewVisibility(R.id.widget_box_total, if (config.showTotalTime) View.VISIBLE else View.GONE)
                        views.setViewVisibility(R.id.widget_box_current_session, if (config.showCurrentSession) View.VISIBLE else View.GONE)
                    }

                    // Tap to open app
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        widgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                // Ignore widget update exceptions
            }
        }
    }
}
