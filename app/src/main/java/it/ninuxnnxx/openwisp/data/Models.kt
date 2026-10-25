package it.ninuxnnxx.openwisp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Solo i campi che l'app usa: il resto delle risposte viene ignorato
// (ignoreUnknownKeys), cosi' versioni diverse di OpenWISP non la rompono.

@Serializable
data class Page<T>(
    val count: Int = 0,
    val next: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class StatusField(val status: String? = null)

@Serializable
data class ConfigField(
    val status: String? = null,
    @SerialName("error_reason") val errorReason: String? = null,
)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val organization: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("last_ip") val lastIp: String? = null,
    @SerialName("management_ip") val managementIp: String? = null,
    val model: String? = null,
    val os: String? = null,
    val system: String? = null,
    val notes: String? = null,
    val config: ConfigField? = null,
    val monitoring: StatusField? = null,
    val modified: String? = null,
)

/** monitoring/device/<id>/?status=true: device, ultimi dati inviati e grafici. */
@Serializable
data class DeviceDetail(
    val id: String,
    val name: String,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("last_ip") val lastIp: String? = null,
    @SerialName("management_ip") val managementIp: String? = null,
    val model: String? = null,
    val os: String? = null,
    val system: String? = null,
    val notes: String? = null,
    val config: ConfigField? = null,
    val monitoring: StatusField? = null,
    val charts: List<Chart> = emptyList(),
    val x: List<String> = emptyList(),
    val data: MonitoringData? = null,
)

@Serializable
data class Chart(
    val title: String = "",
    val type: String = "",
    val unit: String? = null,
    val description: String? = null,
    // [["nome", [valori...]], ...]: si legge con Chart.series()
    val traces: List<List<JsonElement>> = emptyList(),
    val summary: JsonObject? = null,
    val colors: List<String>? = null,
)

@Serializable
data class MonitoringData(
    val general: General? = null,
    val resources: Resources? = null,
    val interfaces: List<NetInterface> = emptyList(),
)

@Serializable
data class General(
    val hostname: String? = null,
    val uptime: Long? = null,
    @SerialName("local_time") val localTime: Long? = null,
)

@Serializable
data class Resources(
    val memory: Memory? = null,
    val load: List<Double> = emptyList(),
    val cpus: Int? = null,
    val disk: List<Disk> = emptyList(),
)

@Serializable
data class Memory(
    val total: Long = 0,
    val available: Long? = null,
    val free: Long = 0,
)

@Serializable
data class Disk(
    @SerialName("mount_point") val mountPoint: String = "",
    @SerialName("used_percent") val usedPercent: Int = 0,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
)

@Serializable
data class NetInterface(
    val name: String,
    val type: String? = null,
    val up: Boolean = false,
    val wireless: Wireless? = null,
)

@Serializable
data class Wireless(
    val ssid: String? = null,
    val mode: String? = null,
    val channel: Int? = null,
    val frequency: Int? = null,
    @SerialName("tx_power") val txPower: Int? = null,
    val htmode: String? = null,
    val clients: List<WifiClient> = emptyList(),
)

@Serializable
data class WifiClient(
    val mac: String,
    val signal: Int? = null,
    val vendor: String? = null,
)

@Serializable
data class DeviceConnection(
    val id: String,
    val enabled: Boolean = false,
    @SerialName("is_working") val isWorking: Boolean? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
)

@Serializable
data class Command(
    val id: String,
    val type: String,
    val status: String,
    val input: JsonElement? = null,
    val output: String? = null,
    val created: String? = null,
)

@Serializable
data class Notification(
    val id: String,
    val message: String = "",
    val description: String? = null,
    val unread: Boolean = false,
    @SerialName("target_url") val targetUrl: String? = null,
    @SerialName("email_subject") val emailSubject: String? = null,
    val timestamp: String = "",
    val level: String? = null,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
)

@Serializable
data class RadiusSession(
    @SerialName("unique_id") val uniqueId: String,
    val username: String? = null,
    @SerialName("framed_ip_address") val framedIp: String? = null,
    @SerialName("nas_ip_address") val nasIp: String? = null,
    @SerialName("calling_station_id") val callingStationId: String? = null,
    @SerialName("called_station_id") val calledStationId: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("stop_time") val stopTime: String? = null,
    @SerialName("session_time") val sessionTime: Long? = null,
    @SerialName("input_octets") val inputOctets: Long? = null,
    @SerialName("output_octets") val outputOctets: Long? = null,
    @SerialName("terminate_cause") val terminateCause: String? = null,
)

@Serializable
data class DeviceFirmware(
    val image: String? = null,
    val installed: Boolean = false,
)

@Serializable
data class Build(
    val id: String,
    val version: String,
    val category: String,
    val created: String = "",
    val changelog: String? = null,
)

@Serializable
data class FirmwareImage(
    val id: String,
    val type: String,
    val build: String,
)

@Serializable
data class UpgradeOperation(
    val id: String,
    val image: String? = null,
    val status: String,
    val log: String? = null,
    val modified: String? = null,
)

/** Firmware installato e build piu' recenti con un'immagine dello stesso tipo. */
data class FirmwareStatus(
    val installed: Build?,
    val image: FirmwareImage?,
    val newer: List<Pair<Build, FirmwareImage>>,
)

@Serializable
data class LinkResponse(val url: String)

/**
 * Moduli presenti sul server: un'installazione OpenWISP puo' non avere
 * monitoring, firmware-upgrader o radius, e quasi mai openwisp-luci e
 * openwisp-netconsole. L'app nasconde quello che manca.
 */
data class Features(
    val monitoring: Boolean = true,
    val firmware: Boolean = true,
    val radius: Boolean = true,
    val luci: Boolean = true,
    val netconsole: Boolean = true,
) {
    companion object {
        val ALL = Features()
    }
}

@Serializable
data class NetconsoleLog(
    val key: String = "",
    val lines: List<String> = emptyList(),
)
