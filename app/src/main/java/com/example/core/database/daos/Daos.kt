package com.example.core.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entities.AppSettingsEntity
import com.example.core.database.entities.AttendanceSessionEntity
import com.example.core.database.entities.LogEntryEntity
import com.example.core.database.entities.OfficeEntity
import com.example.core.database.entities.OfficeWifiEntity
import com.example.core.database.entities.PunchEventEntity
import com.example.core.database.entities.WidgetConfigEntity
import com.example.core.database.entities.WorkScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficeDao {
    @Query("SELECT * FROM offices ORDER BY createdAt ASC")
    fun getAllOffices(): Flow<List<OfficeEntity>>

    @Query("SELECT * FROM offices WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveOffice(): OfficeEntity?

    @Query("SELECT * FROM offices WHERE id = :id LIMIT 1")
    suspend fun getOfficeById(id: String): OfficeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffice(office: OfficeEntity)

    @Update
    suspend fun updateOffice(office: OfficeEntity)

    @Delete
    suspend fun deleteOffice(office: OfficeEntity)
}

@Dao
interface OfficeWifiDao {
    @Query("SELECT * FROM office_wifis ORDER BY name ASC")
    fun getAllWifis(): Flow<List<OfficeWifiEntity>>

    @Query("SELECT * FROM office_wifis WHERE enabled = 1")
    fun getEnabledWifisFlow(): Flow<List<OfficeWifiEntity>>

    @Query("SELECT * FROM office_wifis WHERE enabled = 1")
    suspend fun getEnabledWifis(): List<OfficeWifiEntity>

    @Query("SELECT * FROM office_wifis WHERE officeId = :officeId")
    suspend fun getWifisForOffice(officeId: String): List<OfficeWifiEntity>

    @Query("SELECT * FROM office_wifis WHERE id = :id LIMIT 1")
    suspend fun getWifiById(id: String): OfficeWifiEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWifi(wifi: OfficeWifiEntity)

    @Update
    suspend fun updateWifi(wifi: OfficeWifiEntity)

    @Delete
    suspend fun deleteWifi(wifi: OfficeWifiEntity)

    @Query("DELETE FROM office_wifis WHERE id = :id")
    suspend fun deleteWifiById(id: String)
}

@Dao
interface PunchEventDao {
    @Query("SELECT * FROM punch_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<PunchEventEntity>>

    @Query("SELECT * FROM punch_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getEventsBetweenFlow(startTime: Long, endTime: Long): Flow<List<PunchEventEntity>>

    @Query("SELECT * FROM punch_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getEventsBetween(startTime: Long, endTime: Long): List<PunchEventEntity>

    @Query("SELECT * FROM punch_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEvent(): PunchEventEntity?

    @Query("SELECT * FROM punch_events ORDER BY timestamp DESC LIMIT 1")
    fun getLatestEventFlow(): Flow<PunchEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PunchEventEntity)

    @Update
    suspend fun updateEvent(event: PunchEventEntity)

    @Query("DELETE FROM punch_events WHERE id = :id")
    suspend fun deleteEventById(id: String)
}

@Dao
interface AttendanceSessionDao {
    @Query("SELECT * FROM attendance_sessions ORDER BY punchInTime DESC")
    fun getAllSessionsFlow(): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): AttendanceSessionEntity?

    @Query("SELECT * FROM attendance_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveSessionFlow(): Flow<AttendanceSessionEntity?>

    @Query("SELECT * FROM attendance_sessions WHERE date = :date ORDER BY punchInTime ASC")
    fun getSessionsForDateFlow(date: String): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE date = :date ORDER BY punchInTime ASC")
    suspend fun getSessionsForDate(date: String): List<AttendanceSessionEntity>

    @Query("SELECT * FROM attendance_sessions WHERE punchInTime >= :startTime AND punchInTime <= :endTime ORDER BY punchInTime ASC")
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<AttendanceSessionEntity>

    @Query("SELECT * FROM attendance_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): AttendanceSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSessionEntity)

    @Update
    suspend fun updateSession(session: AttendanceSessionEntity)

    @Query("DELETE FROM attendance_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getValueFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(setting: AppSettingsEntity)

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettingsEntity>
}

@Dao
interface WorkScheduleDao {
    @Query("SELECT * FROM work_schedules ORDER BY dayOfWeek ASC")
    fun getAllSchedulesFlow(): Flow<List<WorkScheduleEntity>>

    @Query("SELECT * FROM work_schedules ORDER BY dayOfWeek ASC")
    suspend fun getAllSchedules(): List<WorkScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedules(schedules: List<WorkScheduleEntity>)
}

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs WHERE widgetId = :widgetId LIMIT 1")
    suspend fun getConfig(widgetId: Int): WidgetConfigEntity?

    @Query("SELECT * FROM widget_configs")
    suspend fun getAllConfigs(): List<WidgetConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: WidgetConfigEntity)

    @Query("DELETE FROM widget_configs WHERE widgetId = :widgetId")
    suspend fun deleteConfig(widgetId: Int)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 200): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntryEntity)

    @Query("DELETE FROM logs")
    suspend fun clearLogs()
}
