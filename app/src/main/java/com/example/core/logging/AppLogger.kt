package com.example.core.logging

import android.util.Log
import com.example.core.database.AppDatabase
import com.example.core.database.entities.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLogger {
    private const val TAG_PREFIX = "PunchTracker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: AppDatabase? = null

    fun init(db: AppDatabase) {
        database = db
    }

    fun info(tag: String, message: String) {
        Log.i("$TAG_PREFIX:$tag", message)
        persist("INFO", tag, message)
    }

    fun debug(tag: String, message: String) {
        Log.d("$TAG_PREFIX:$tag", message)
        persist("DEBUG", tag, message)
    }

    fun warn(tag: String, message: String) {
        Log.w("$TAG_PREFIX:$tag", message)
        persist("WARN", tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG_PREFIX:$tag", message, throwable)
        val fullMsg = if (throwable != null) "$message\n${throwable.message}" else message
        persist("ERROR", tag, fullMsg)
    }

    private fun persist(level: String, tag: String, message: String) {
        val db = database ?: return
        scope.launch {
            try {
                db.logDao().insertLog(
                    LogEntryEntity(
                        level = level,
                        tag = tag,
                        message = message,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
