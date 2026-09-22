package com.example.features.attendance.domain

import android.content.Context
import com.example.core.database.entities.OfficeWifiEntity
import com.example.core.logging.AppLogger
import com.example.core.model.AttendanceState
import com.example.core.model.PunchSource
import com.example.features.attendance.data.AttendanceRepository
import com.example.features.widget.WidgetManager
import com.example.features.wifi.data.WifiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceEngine(
    private val context: Context,
    private val attendanceRepository: AttendanceRepository,
    private val wifiRepository: WifiRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingDisconnectJob: Job? = null

    private val _disconnectCountdownSeconds = MutableStateFlow<Int?>(null)
    val disconnectCountdownSeconds: StateFlow<Int?> = _disconnectCountdownSeconds.asStateFlow()

    private var lastObservedSsid: String? = null
    private var lastObservedBssid: String? = null

    /**
     * Processes a Wi-Fi connection event idempotently.
     */
    fun processWifiConnected(rawSsid: String?, rawBssid: String?) {
        scope.launch {
            val ssid = rawSsid?.removeSurrounding("\"")
            val bssid = rawBssid
            AppLogger.info("AttendanceEngine", "WiFi connected: SSID=$ssid BSSID=$bssid")

            // Check if there is a pending disconnect grace timer
            if (pendingDisconnectJob?.isActive == true) {
                pendingDisconnectJob?.cancel()
                _disconnectCountdownSeconds.value = null
                AppLogger.info("AttendanceEngine", "WiFi reconnected before grace period expired. action=cancel_punch_out")
            }

            // Find matching configured office network
            val configuredWifis = wifiRepository.getEnabledNetworks()
            val matchedWifi = matchOfficeWifi(ssid, bssid, configuredWifis)

            if (matchedWifi == null) {
                AppLogger.debug("AttendanceEngine", "Connected WiFi '$ssid' does not match any configured office network")
                // Reset manual override if phone disconnected from the old office wifi
                if (lastObservedSsid != null && lastObservedSsid != ssid) {
                    attendanceRepository.setManualOverride(false)
                }
                lastObservedSsid = ssid
                lastObservedBssid = bssid
                return@launch
            }

            AppLogger.info("AttendanceEngine", "Office WiFi detected: ${matchedWifi.name} (id=${matchedWifi.id})")

            val currentState = attendanceRepository.getCurrentState()
            val manualOverride = attendanceRepository.getManualOverride()

            // Check for manual override rule (Case 9 & 10)
            if (manualOverride) {
                if (lastObservedSsid == ssid && (matchedWifi.matchBssid.not() || lastObservedBssid == bssid)) {
                    AppLogger.info(
                        "AttendanceEngine",
                        "Manual override active for unchanged network '$ssid'. Suppressing auto punch-in."
                    )
                    return@launch
                } else {
                    // Transitioned to a different office Wi-Fi network! Clear manual override.
                    AppLogger.info(
                        "AttendanceEngine",
                        "Transitioned to different network '${matchedWifi.name}'. Clearing manual override."
                    )
                    attendanceRepository.setManualOverride(false)
                }
            }

            lastObservedSsid = ssid
            lastObservedBssid = bssid

            if (currentState == AttendanceState.INSIDE_OFFICE) {
                AppLogger.debug(
                    "AttendanceEngine",
                    "Already INSIDE_OFFICE. Phone switched or affirmed office network without punching."
                )
                return@launch
            }

            // Perform automatic Punch In
            val success = attendanceRepository.punchIn(
                timestamp = System.currentTimeMillis(),
                source = PunchSource.WIFI_CONNECTED,
                wifiId = matchedWifi.id,
                ssid = matchedWifi.ssid,
                bssid = bssid
            )
            if (success) {
                WidgetManager.updateWidgets(context)
            }
        }
    }

    /**
     * Processes a Wi-Fi disconnect event with grace period & flapping protection.
     */
    fun processWifiDisconnected() {
        scope.launch {
            val currentState = attendanceRepository.getCurrentState()
            if (currentState != AttendanceState.INSIDE_OFFICE) {
                AppLogger.debug("AttendanceEngine", "WiFi disconnected, but user is already OUTSIDE_OFFICE.")
                attendanceRepository.setManualOverride(false)
                lastObservedSsid = null
                lastObservedBssid = null
                return@launch
            }

            // Cancel any previous pending job
            pendingDisconnectJob?.cancel()

            val gracePeriodSeconds = wifiRepository.getGracePeriodSeconds()
            AppLogger.debug(
                "AttendanceEngine",
                "WiFi disconnect detected. Initiating grace period countdown of ${gracePeriodSeconds}s"
            )

            pendingDisconnectJob = scope.launch {
                for (sec in gracePeriodSeconds downTo 1) {
                    _disconnectCountdownSeconds.value = sec
                    delay(1000L)
                }
                _disconnectCountdownSeconds.value = null

                // Grace period expired! Check whether ANY office Wi-Fi is currently connected
                val stillConnected = isAnyOfficeWifiConnected()
                if (stillConnected) {
                    AppLogger.info(
                        "AttendanceEngine",
                        "Grace period expired, but phone is currently connected to an office WiFi. action=cancel_punch_out"
                    )
                    return@launch
                }

                // Confirm punch out
                AppLogger.info(
                    "AttendanceEngine",
                    "Grace period expired with no office WiFi connected. Creating automatic Punch Out."
                )
                val success = attendanceRepository.punchOut(
                    timestamp = System.currentTimeMillis(),
                    source = PunchSource.WIFI_DISCONNECTED
                )
                if (success) {
                    attendanceRepository.setManualOverride(false)
                    lastObservedSsid = null
                    lastObservedBssid = null
                    WidgetManager.updateWidgets(context)
                }
            }
        }
    }

    /**
     * Reconciles attendance state on app launch or system boot.
     */
    suspend fun reconcileState(currentSsid: String?, currentBssid: String?) = withContext(Dispatchers.IO) {
        val configured = wifiRepository.getEnabledNetworks()
        val matched = matchOfficeWifi(currentSsid?.removeSurrounding("\""), currentBssid, configured)
        val currentState = attendanceRepository.getCurrentState()

        AppLogger.info(
            "AttendanceEngine",
            "Reconciliation: currentState=$currentState, matchedWifi=${matched?.name}"
        )

        if (matched != null) {
            lastObservedSsid = matched.ssid
            lastObservedBssid = currentBssid
            if (currentState != AttendanceState.INSIDE_OFFICE) {
                val manualOverride = attendanceRepository.getManualOverride()
                if (!manualOverride) {
                    AppLogger.info("AttendanceEngine", "Reconciliation: Auto punching in to ${matched.name}")
                    attendanceRepository.punchIn(
                        timestamp = System.currentTimeMillis(),
                        source = PunchSource.SYSTEM_RECOVERY,
                        wifiId = matched.id,
                        ssid = matched.ssid,
                        bssid = currentBssid
                    )
                    WidgetManager.updateWidgets(context)
                }
            }
        } else {
            // Not connected to office wifi
            if (currentState == AttendanceState.INSIDE_OFFICE) {
                AppLogger.info(
                    "AttendanceEngine",
                    "Reconciliation: Outside office but state was INSIDE_OFFICE. Punching out via SYSTEM_RECOVERY."
                )
                attendanceRepository.punchOut(
                    timestamp = System.currentTimeMillis(),
                    source = PunchSource.SYSTEM_RECOVERY
                )
                attendanceRepository.setManualOverride(false)
                WidgetManager.updateWidgets(context)
            }
        }
    }

    private suspend fun isAnyOfficeWifiConnected(): Boolean {
        // Will be checked against current network info or current observed network
        return lastObservedSsid != null && matchOfficeWifi(
            lastObservedSsid,
            lastObservedBssid,
            wifiRepository.getEnabledNetworks()
        ) != null
    }

    private fun matchOfficeWifi(
        ssid: String?,
        bssid: String?,
        configured: List<OfficeWifiEntity>
    ): OfficeWifiEntity? {
        if (ssid == null && bssid == null) return null
        for (wifi in configured) {
            if (!wifi.enabled) continue
            if (wifi.matchBssid && wifi.bssid != null) {
                if (wifi.bssid.equals(bssid, ignoreCase = true)) {
                    return wifi
                }
            } else {
                if (wifi.ssid.equals(ssid, ignoreCase = true)) {
                    return wifi
                }
            }
        }
        return null
    }
}
