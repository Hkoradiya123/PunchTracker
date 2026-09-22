package com.example.features.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.PunchTrackerApplication
import com.example.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppLogger.info("BootReceiver", "Device reboot/package update detected ($action). Starting state recovery.")
            val app = context.applicationContext as? PunchTrackerApplication ?: return

            CoroutineScope(Dispatchers.IO).launch {
                app.wifiMonitor.readCurrentWifi()
                val currentSsid = app.wifiMonitor.currentSsid.value
                val currentBssid = app.wifiMonitor.currentBssid.value
                app.attendanceEngine.reconcileState(currentSsid, currentBssid)
            }
        }
    }
}
