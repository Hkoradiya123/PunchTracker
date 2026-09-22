package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.database.daos.AppSettingsDao
import com.example.core.database.daos.AttendanceSessionDao
import com.example.core.database.daos.LogDao
import com.example.core.database.daos.OfficeDao
import com.example.core.database.daos.OfficeWifiDao
import com.example.core.database.daos.PunchEventDao
import com.example.core.database.daos.WidgetConfigDao
import com.example.core.database.daos.WorkScheduleDao
import com.example.core.database.entities.AppSettingsEntity
import com.example.core.database.entities.AttendanceSessionEntity
import com.example.core.database.entities.LogEntryEntity
import com.example.core.database.entities.OfficeEntity
import com.example.core.database.entities.OfficeWifiEntity
import com.example.core.database.entities.PunchEventEntity
import com.example.core.database.entities.WidgetConfigEntity
import com.example.core.database.entities.WorkScheduleEntity

@Database(
    entities = [
        OfficeEntity::class,
        OfficeWifiEntity::class,
        PunchEventEntity::class,
        AttendanceSessionEntity::class,
        AppSettingsEntity::class,
        WorkScheduleEntity::class,
        WidgetConfigEntity::class,
        LogEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun officeDao(): OfficeDao
    abstract fun officeWifiDao(): OfficeWifiDao
    abstract fun punchEventDao(): PunchEventDao
    abstract fun attendanceSessionDao(): AttendanceSessionDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun workScheduleDao(): WorkScheduleDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "punchtracker_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
