package com.example.features.wifi.data

import com.example.core.database.AppDatabase
import com.example.core.database.entities.AppSettingsEntity
import com.example.core.database.entities.OfficeEntity
import com.example.core.database.entities.OfficeWifiEntity
import com.example.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface WifiRepository {
    fun getAllOfficesFlow(): Flow<List<OfficeEntity>>
    fun getAllNetworksFlow(): Flow<List<OfficeWifiEntity>>
    fun getOfficeNetworksFlow(): Flow<List<OfficeWifiEntity>>
    fun getHomeNetworksFlow(): Flow<List<OfficeWifiEntity>>
    fun getEnabledNetworksFlow(): Flow<List<OfficeWifiEntity>>
    fun getGracePeriodSecondsFlow(): Flow<Int>

    suspend fun getEnabledNetworks(): List<OfficeWifiEntity>
    suspend fun addOffice(name: String, address: String = ""): OfficeEntity
    suspend fun addNetwork(
        officeId: String,
        name: String,
        ssid: String,
        bssid: String? = null,
        matchBssid: Boolean = false,
        networkType: String = "OFFICE"
    )
    suspend fun addHomeNetwork(
        name: String,
        ssid: String,
        bssid: String? = null,
        matchBssid: Boolean = false
    )
    suspend fun updateNetwork(network: OfficeWifiEntity)
    suspend fun deleteNetwork(id: String)
    suspend fun getGracePeriodSeconds(): Int
    suspend fun setGracePeriodSeconds(seconds: Int)
    suspend fun ensureDefaultOffice(): OfficeEntity
}

class WifiRepositoryImpl(
    private val database: AppDatabase
) : WifiRepository {

    private val officeDao = database.officeDao()
    private val wifiDao = database.officeWifiDao()
    private val settingsDao = database.appSettingsDao()

    override fun getAllOfficesFlow(): Flow<List<OfficeEntity>> =
        officeDao.getAllOffices().flowOn(Dispatchers.IO)

    override fun getAllNetworksFlow(): Flow<List<OfficeWifiEntity>> =
        wifiDao.getAllWifis().flowOn(Dispatchers.IO)

    override fun getOfficeNetworksFlow(): Flow<List<OfficeWifiEntity>> =
        wifiDao.getWifisByTypeFlow("OFFICE").flowOn(Dispatchers.IO)

    override fun getHomeNetworksFlow(): Flow<List<OfficeWifiEntity>> =
        wifiDao.getWifisByTypeFlow("HOME").flowOn(Dispatchers.IO)

    override fun getEnabledNetworksFlow(): Flow<List<OfficeWifiEntity>> =
        wifiDao.getEnabledWifisFlow().flowOn(Dispatchers.IO)

    override fun getGracePeriodSecondsFlow(): Flow<Int> {
        return settingsDao.getValueFlow("grace_period_seconds").map {
            it?.toIntOrNull() ?: 30
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getEnabledNetworks(): List<OfficeWifiEntity> =
        withContext(Dispatchers.IO) {
            wifiDao.getEnabledWifis()
        }

    override suspend fun addOffice(name: String, address: String): OfficeEntity =
        withContext(Dispatchers.IO) {
            val office = OfficeEntity(name = name, address = address)
            officeDao.insertOffice(office)
            AppLogger.info("WifiRepo", "Added office: $name")
            office
        }

    override suspend fun addNetwork(
        officeId: String,
        name: String,
        ssid: String,
        bssid: String?,
        matchBssid: Boolean,
        networkType: String
    ) = withContext(Dispatchers.IO) {
        val cleanSsid = ssid.removeSurrounding("\"")
        val network = OfficeWifiEntity(
            officeId = officeId,
            name = name,
            ssid = cleanSsid,
            bssid = if (matchBssid) bssid else null,
            matchBssid = matchBssid,
            networkType = networkType,
            enabled = true
        )
        wifiDao.insertWifi(network)
        AppLogger.info("WifiRepo", "Added $networkType Wi-Fi: $name (SSID: $cleanSsid, matchBssid=$matchBssid)")
    }

    override suspend fun addHomeNetwork(
        name: String,
        ssid: String,
        bssid: String?,
        matchBssid: Boolean
    ) = withContext(Dispatchers.IO) {
        val office = ensureDefaultOffice()
        addNetwork(
            officeId = office.id,
            name = name,
            ssid = ssid,
            bssid = bssid,
            matchBssid = matchBssid,
            networkType = "HOME"
        )
    }

    override suspend fun updateNetwork(network: OfficeWifiEntity) =
        withContext(Dispatchers.IO) {
            wifiDao.updateWifi(network)
            AppLogger.info("WifiRepo", "Updated Wi-Fi: ${network.name}")
        }

    override suspend fun deleteNetwork(id: String) =
        withContext(Dispatchers.IO) {
            wifiDao.deleteWifiById(id)
            AppLogger.info("WifiRepo", "Deleted Wi-Fi: $id")
        }

    override suspend fun getGracePeriodSeconds(): Int =
        withContext(Dispatchers.IO) {
            settingsDao.getValue("grace_period_seconds")?.toIntOrNull() ?: 30
        }

    override suspend fun setGracePeriodSeconds(seconds: Int) =
        withContext(Dispatchers.IO) {
            settingsDao.setValue(AppSettingsEntity("grace_period_seconds", seconds.toString()))
            AppLogger.info("WifiRepo", "Updated grace period to ${seconds}s")
        }

    override suspend fun ensureDefaultOffice(): OfficeEntity =
        withContext(Dispatchers.IO) {
            var active = officeDao.getActiveOffice()
            if (active == null) {
                active = OfficeEntity(name = "Office HQ", address = "Main Campus")
                officeDao.insertOffice(active)

                // Add sample configured networks so user can test right away
                val wifi1 = OfficeWifiEntity(
                    officeId = active.id,
                    name = "Office Main",
                    ssid = "CompanyWiFi",
                    networkType = "OFFICE",
                    enabled = true
                )
                val wifi2 = OfficeWifiEntity(
                    officeId = active.id,
                    name = "Office 5G",
                    ssid = "CompanyWiFi-5G",
                    networkType = "OFFICE",
                    enabled = true
                )
                val homeWifi = OfficeWifiEntity(
                    officeId = active.id,
                    name = "My Home Wi-Fi",
                    ssid = "HomeWiFi",
                    networkType = "HOME",
                    enabled = true
                )
                wifiDao.insertWifi(wifi1)
                wifiDao.insertWifi(wifi2)
                wifiDao.insertWifi(homeWifi)
                AppLogger.info("WifiRepo", "Initialized default Office HQ, Office Wi-Fi, and Home Wi-Fi networks")
            }
            active
        }
}
