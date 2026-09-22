package com.example.features.wifi.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.core.logging.AppLogger
import com.example.features.attendance.domain.AttendanceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiMonitor(
    private val context: Context,
    private val attendanceEngine: AttendanceEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid: StateFlow<String?> = _currentSsid.asStateFlow()

    private val _currentBssid = MutableStateFlow<String?>(null)
    val currentBssid: StateFlow<String?> = _currentBssid.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    readWifiInfo(caps)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                scope.launch {
                    readWifiInfo(networkCapabilities)
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    AppLogger.info("WifiMonitor", "Wi-Fi network connection lost")
                    _isWifiConnected.value = false
                    _currentSsid.value = null
                    _currentBssid.value = null
                    attendanceEngine.processWifiDisconnected()
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            // Initial check
            readCurrentWifi()
        } catch (e: Exception) {
            AppLogger.error("WifiMonitor", "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }

    fun readCurrentWifi() {
        scope.launch {
            try {
                val activeNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    readWifiInfo(caps)
                } else {
                    _isWifiConnected.value = false
                    _currentSsid.value = null
                    _currentBssid.value = null
                }
            } catch (e: Exception) {
                AppLogger.warn("WifiMonitor", "Failed reading current wifi: ${e.message}")
            }
        }
    }

    private fun readWifiInfo(caps: NetworkCapabilities?) {
        var ssid: String? = null
        var bssid: String? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                val transportInfo = caps.transportInfo
                if (transportInfo is WifiInfo) {
                    ssid = transportInfo.ssid
                    bssid = transportInfo.bssid
                }
            }

            if (ssid == null || ssid == "<unknown ssid>") {
                @Suppress("DEPRECATION")
                val connInfo = wifiManager?.connectionInfo
                if (connInfo != null) {
                    ssid = connInfo.ssid
                    bssid = connInfo.bssid
                }
            }
        } catch (e: Exception) {
            AppLogger.warn("WifiMonitor", "Exception retrieving wifi info: ${e.message}")
        }

        val cleanSsid = if (ssid != null && ssid != "<unknown ssid>") {
            ssid.removeSurrounding("\"")
        } else {
            // If location permission is not granted, fallback indicator
            "Connected WiFi"
        }

        _isWifiConnected.value = true
        _currentSsid.value = cleanSsid
        _currentBssid.value = bssid

        attendanceEngine.processWifiConnected(cleanSsid, bssid)
    }

    // --- Simulation methods for testing & debug screen ---

    fun simulateConnect(ssid: String, bssid: String? = null) {
        _isWifiConnected.value = true
        _currentSsid.value = ssid
        _currentBssid.value = bssid
        attendanceEngine.processWifiConnected(ssid, bssid)
    }

    fun simulateDisconnect() {
        _isWifiConnected.value = false
        _currentSsid.value = null
        _currentBssid.value = null
        attendanceEngine.processWifiDisconnected()
    }

    fun simulateQuickReconnect(ssid: String, reconnectAfterMs: Long = 3000L) {
        simulateDisconnect()
        scope.launch {
            delay(reconnectAfterMs)
            simulateConnect(ssid)
        }
    }
}
