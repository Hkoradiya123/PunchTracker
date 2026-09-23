package com.example.features.attendance.data

import com.example.core.database.AppDatabase
import com.example.core.database.entities.AppSettingsEntity
import com.example.core.database.entities.AttendanceSessionEntity
import com.example.core.database.entities.PunchEventEntity
import com.example.core.logging.AppLogger
import com.example.core.model.AttendanceState
import com.example.core.model.DailyAttendanceSummary
import com.example.core.model.DayDuration
import com.example.core.model.PunchSource
import com.example.core.model.PunchType
import com.example.core.model.SessionStatus
import com.example.core.model.WeeklyStats
import com.example.core.time.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

interface AttendanceRepository {
    fun getCurrentStateFlow(): Flow<AttendanceState>
    fun getManualOverrideFlow(): Flow<Boolean>
    fun getActiveSessionFlow(): Flow<AttendanceSessionEntity?>
    fun getTodaySummaryFlow(): Flow<DailyAttendanceSummary>
    fun getDailySummaryFlow(date: String): Flow<DailyAttendanceSummary>
    fun getSessionsForDateFlow(date: String): Flow<List<AttendanceSessionEntity>>
    fun getEventsForDateFlow(date: String): Flow<List<PunchEventEntity>>
    fun getAllSessionsFlow(): Flow<List<AttendanceSessionEntity>>
    fun getWeeklyStatsFlow(): Flow<WeeklyStats>

    suspend fun getCurrentState(): AttendanceState
    suspend fun setAttendanceState(state: AttendanceState)
    suspend fun getManualOverride(): Boolean
    suspend fun setManualOverride(override: Boolean)
    suspend fun punchIn(
        timestamp: Long = System.currentTimeMillis(),
        source: PunchSource,
        wifiId: String? = null,
        ssid: String? = null,
        bssid: String? = null
    ): Boolean

    suspend fun punchOut(
        timestamp: Long = System.currentTimeMillis(),
        source: PunchSource
    ): Boolean

    suspend fun editSession(
        sessionId: String,
        punchIn: Long,
        punchOut: Long?,
        modifiedBy: String = "USER"
    )

    suspend fun deleteSession(sessionId: String)
    suspend fun addEvent(
        type: PunchType,
        timestamp: Long,
        source: PunchSource = PunchSource.MANUAL
    )
    suspend fun deleteEvent(eventId: String)
}

class AttendanceRepositoryImpl(
    private val database: AppDatabase
) : AttendanceRepository {

    private val sessionDao = database.attendanceSessionDao()
    private val eventDao = database.punchEventDao()
    private val settingsDao = database.appSettingsDao()

    override fun getCurrentStateFlow(): Flow<AttendanceState> {
        return settingsDao.getValueFlow("attendance_state").map { value ->
            when (value) {
                AttendanceState.INSIDE_OFFICE.name -> AttendanceState.INSIDE_OFFICE
                AttendanceState.AT_HOME.name -> AttendanceState.AT_HOME
                AttendanceState.CONNECTING.name -> AttendanceState.CONNECTING
                AttendanceState.DISCONNECTING.name -> AttendanceState.DISCONNECTING
                AttendanceState.UNKNOWN.name -> AttendanceState.UNKNOWN
                else -> AttendanceState.OUTSIDE_OFFICE
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getManualOverrideFlow(): Flow<Boolean> {
        return settingsDao.getValueFlow("manual_override").map {
            it?.toBoolean() ?: false
        }.flowOn(Dispatchers.IO)
    }

    override fun getActiveSessionFlow(): Flow<AttendanceSessionEntity?> {
        return sessionDao.getActiveSessionFlow().flowOn(Dispatchers.IO)
    }

    override suspend fun getCurrentState(): AttendanceState {
        val value = settingsDao.getValue("attendance_state")
        return when (value) {
            AttendanceState.INSIDE_OFFICE.name -> AttendanceState.INSIDE_OFFICE
            AttendanceState.AT_HOME.name -> AttendanceState.AT_HOME
            AttendanceState.CONNECTING.name -> AttendanceState.CONNECTING
            AttendanceState.DISCONNECTING.name -> AttendanceState.DISCONNECTING
            AttendanceState.UNKNOWN.name -> AttendanceState.UNKNOWN
            else -> AttendanceState.OUTSIDE_OFFICE
        }
    }

    override suspend fun setAttendanceState(state: AttendanceState) = withContext(Dispatchers.IO) {
        settingsDao.setValue(AppSettingsEntity("attendance_state", state.name))
        AppLogger.info("AttendanceRepo", "Attendance state set to: ${state.name}")
    }

    override suspend fun getManualOverride(): Boolean {
        return settingsDao.getValue("manual_override")?.toBoolean() ?: false
    }

    override suspend fun setManualOverride(override: Boolean) {
        settingsDao.setValue(AppSettingsEntity("manual_override", override.toString()))
    }

    override suspend fun punchIn(
        timestamp: Long,
        source: PunchSource,
        wifiId: String?,
        ssid: String?,
        bssid: String?
    ): Boolean = withContext(Dispatchers.IO) {
        // Idempotency check:
        val currentState = getCurrentState()
        val latestEvent = eventDao.getLatestEvent()
        if (currentState == AttendanceState.INSIDE_OFFICE) {
            AppLogger.debug("AttendanceRepo", "Ignoring punchIn: Already INSIDE_OFFICE")
            return@withContext false
        }
        if (latestEvent?.type == PunchType.PUNCH_IN.name && (timestamp - latestEvent.timestamp) < 5000) {
            AppLogger.debug("AttendanceRepo", "Ignoring duplicate punchIn within 5s")
            return@withContext false
        }

        val dateStr = TimeUtils.toDateString(timestamp)

        // Atomic Transaction:
        // 1. Insert punch event
        val event = PunchEventEntity(
            type = PunchType.PUNCH_IN.name,
            timestamp = timestamp,
            wifiId = wifiId,
            ssid = ssid,
            bssid = bssid,
            source = source.name,
            createdAt = System.currentTimeMillis()
        )
        eventDao.insertEvent(event)

        // 2. Close any orphaned active session first (defensive)
        val active = sessionDao.getActiveSession()
        if (active != null) {
            val duration = timestamp - active.punchInTime
            sessionDao.updateSession(
                active.copy(
                    punchOutTime = timestamp,
                    durationMillis = if (duration > 0) duration else 0,
                    status = SessionStatus.COMPLETED.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 3. Create new active session
        val newSession = AttendanceSessionEntity(
            date = dateStr,
            punchInTime = timestamp,
            punchOutTime = null,
            durationMillis = 0L,
            status = SessionStatus.ACTIVE.name,
            source = if (source == PunchSource.MANUAL) "MANUAL" else "AUTO"
        )
        sessionDao.insertSession(newSession)

        // 4. Update persisted attendance state
        settingsDao.setValue(AppSettingsEntity("attendance_state", AttendanceState.INSIDE_OFFICE.name))
        settingsDao.setValue(AppSettingsEntity("active_session_id", newSession.id))
        settingsDao.setValue(AppSettingsEntity("last_punch_in_time", timestamp.toString()))
        settingsDao.setValue(AppSettingsEntity("last_connected_ssid", ssid ?: ""))

        // If this was a manual punch in while disconnected from wifi, set manual override
        if (source == PunchSource.MANUAL) {
            settingsDao.setValue(AppSettingsEntity("manual_override", "false"))
        }

        AppLogger.info(
            "AttendanceRepo",
            "Punch In created timestamp=${Date(timestamp)} source=$source ssid=$ssid"
        )
        true
    }

    override suspend fun punchOut(
        timestamp: Long,
        source: PunchSource
    ): Boolean = withContext(Dispatchers.IO) {
        val currentState = getCurrentState()
        if (currentState == AttendanceState.OUTSIDE_OFFICE) {
            AppLogger.debug("AttendanceRepo", "Ignoring punchOut: Already OUTSIDE_OFFICE")
            return@withContext false
        }

        // Atomic Transaction:
        // 1. Insert punch event
        val event = PunchEventEntity(
            type = PunchType.PUNCH_OUT.name,
            timestamp = timestamp,
            source = source.name,
            createdAt = System.currentTimeMillis()
        )
        eventDao.insertEvent(event)

        // 2. Complete active session
        val active = sessionDao.getActiveSession()
        if (active != null) {
            val duration = timestamp - active.punchInTime
            sessionDao.updateSession(
                active.copy(
                    punchOutTime = timestamp,
                    durationMillis = if (duration > 0) duration else 0,
                    status = SessionStatus.COMPLETED.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 3. Update persisted state
        settingsDao.setValue(AppSettingsEntity("attendance_state", AttendanceState.OUTSIDE_OFFICE.name))
        settingsDao.setValue(AppSettingsEntity("active_session_id", ""))
        settingsDao.setValue(AppSettingsEntity("last_punch_out_time", timestamp.toString()))

        if (source == PunchSource.MANUAL) {
            // Case 9 in requirements: User manually punches out while still connected to office Wi-Fi.
            // Maintain manualOverrideUntilWifiTransition = true
            settingsDao.setValue(AppSettingsEntity("manual_override", "true"))
            AppLogger.info("AttendanceRepo", "Manual punch out: Set manualOverrideUntilWifiTransition=true")
        }

        AppLogger.info(
            "AttendanceRepo",
            "Punch Out created timestamp=${Date(timestamp)} source=$source"
        )
        true
    }

    override fun getTodaySummaryFlow(): Flow<DailyAttendanceSummary> {
        val today = TimeUtils.todayDateString()
        return getDailySummaryFlow(today)
    }

    override fun getDailySummaryFlow(date: String): Flow<DailyAttendanceSummary> {
        val dayStart = TimeUtils.getDayStartMillis(date)
        val dayEnd = TimeUtils.getDayEndMillis(date)

        return combine(
            sessionDao.getSessionsForDateFlow(date),
            getCurrentStateFlow(),
            getActiveSessionFlow()
        ) { sessions, state, activeSession ->
            val isToday = (date == TimeUtils.todayDateString())
            val isInside = (state == AttendanceState.INSIDE_OFFICE)

            val firstIn = sessions.minOfOrNull { it.punchInTime }
                ?: (if (isToday && isInside && activeSession != null) activeSession.punchInTime else null)

            val completedSessions = sessions.filter { it.punchOutTime != null }
            val lastOut = if (isInside && isToday) null else completedSessions.maxOfOrNull { it.punchOutTime!! }

            var totalDuration = 0L
            for (s in sessions) {
                if (s.punchOutTime != null) {
                    totalDuration += TimeUtils.calculateOverlapDuration(
                        s.punchInTime,
                        s.punchOutTime,
                        dayStart,
                        dayEnd
                    )
                }
            }

            if (isToday && isInside && activeSession != null) {
                val now = System.currentTimeMillis()
                totalDuration += TimeUtils.calculateOverlapDuration(
                    activeSession.punchInTime,
                    now,
                    dayStart,
                    dayEnd
                )
            }

            val sessionCount = sessions.size + (if (isToday && isInside && activeSession != null && !sessions.any { it.id == activeSession.id }) 1 else 0)

            DailyAttendanceSummary(
                date = date,
                firstPunchIn = firstIn,
                lastPunchOut = lastOut,
                totalOfficeDurationMillis = totalDuration,
                sessionCount = sessionCount,
                isCurrentlyInside = isInside && isToday,
                activeSessionStartTime = if (isInside && isToday) activeSession?.punchInTime else null
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun getSessionsForDateFlow(date: String): Flow<List<AttendanceSessionEntity>> {
        return sessionDao.getSessionsForDateFlow(date).flowOn(Dispatchers.IO)
    }

    override fun getEventsForDateFlow(date: String): Flow<List<PunchEventEntity>> {
        val start = TimeUtils.getDayStartMillis(date)
        val end = TimeUtils.getDayEndMillis(date)
        return eventDao.getEventsBetweenFlow(start, end).flowOn(Dispatchers.IO)
    }

    override fun getAllSessionsFlow(): Flow<List<AttendanceSessionEntity>> {
        return sessionDao.getAllSessionsFlow().flowOn(Dispatchers.IO)
    }

    override fun getWeeklyStatsFlow(): Flow<WeeklyStats> {
        return sessionDao.getAllSessionsFlow().map { allSessions ->
            val cal = Calendar.getInstance()
            // Go back 6 days + today = 7 days
            val dailyList = mutableListOf<DayDuration>()
            var totalDuration = 0L
            var daysPresent = 0
            val firstInMinutesList = mutableListOf<Int>()
            val lastOutMinutesList = mutableListOf<Int>()

            val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (i in 6 downTo 0) {
                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                val dateStr = sdfDate.format(c.time)
                val dayName = sdfDayName.format(c.time)
                val dayStart = TimeUtils.getDayStartMillis(dateStr)
                val dayEnd = TimeUtils.getDayEndMillis(dateStr)

                val daySessions = allSessions.filter { it.date == dateStr }
                var dayDur = 0L
                for (s in daySessions) {
                    val end = s.punchOutTime ?: System.currentTimeMillis()
                    dayDur += TimeUtils.calculateOverlapDuration(s.punchInTime, end, dayStart, dayEnd)
                }

                if (daySessions.isNotEmpty() && dayDur > 0) {
                    daysPresent++
                    totalDuration += dayDur

                    val firstIn = daySessions.minOfOrNull { it.punchInTime }
                    if (firstIn != null) {
                        val cIn = Calendar.getInstance().apply { timeInMillis = firstIn }
                        firstInMinutesList.add(cIn.get(Calendar.HOUR_OF_DAY) * 60 + cIn.get(Calendar.MINUTE))
                    }
                    val lastOut = daySessions.mapNotNull { it.punchOutTime }.maxOfOrNull { it }
                    if (lastOut != null) {
                        val cOut = Calendar.getInstance().apply { timeInMillis = lastOut }
                        lastOutMinutesList.add(cOut.get(Calendar.HOUR_OF_DAY) * 60 + cOut.get(Calendar.MINUTE))
                    }
                }

                dailyList.add(
                    DayDuration(
                        date = dateStr,
                        dayName = dayName,
                        durationMillis = dayDur,
                        isPresent = dayDur > 0
                    )
                )
            }

            val avgDaily = if (daysPresent > 0) totalDuration / daysPresent else 0L
            val avgIn = if (firstInMinutesList.isNotEmpty()) firstInMinutesList.average().toInt() else null
            val avgOut = if (lastOutMinutesList.isNotEmpty()) lastOutMinutesList.average().toInt() else null

            WeeklyStats(
                totalDurationMillis = totalDuration,
                averageDailyMillis = avgDaily,
                daysPresent = daysPresent,
                avgFirstInTimeMinutes = avgIn,
                avgLastOutTimeMinutes = avgOut,
                dailyHours = dailyList
            )
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun editSession(
        sessionId: String,
        punchIn: Long,
        punchOut: Long?,
        modifiedBy: String
    ) = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId) ?: return@withContext
        val duration = if (punchOut != null && punchOut > punchIn) punchOut - punchIn else 0L
        val status = if (punchOut == null) SessionStatus.ACTIVE.name else SessionStatus.COMPLETED.name

        sessionDao.updateSession(
            session.copy(
                punchInTime = punchIn,
                punchOutTime = punchOut,
                durationMillis = duration,
                status = status,
                modifiedBy = modifiedBy,
                modifiedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        AppLogger.info("AttendanceRepo", "Edited session $sessionId by $modifiedBy")
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(sessionId)
        AppLogger.info("AttendanceRepo", "Deleted session $sessionId")
    }

    override suspend fun addEvent(
        type: PunchType,
        timestamp: Long,
        source: PunchSource
    ) = withContext(Dispatchers.IO) {
        val event = PunchEventEntity(
            type = type.name,
            timestamp = timestamp,
            source = source.name,
            modifiedBy = "USER"
        )
        eventDao.insertEvent(event)
        AppLogger.info("AttendanceRepo", "Added manual event ${type.name} at ${Date(timestamp)}")
    }

    override suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        eventDao.deleteEventById(eventId)
        AppLogger.info("AttendanceRepo", "Deleted event $eventId")
    }
}
