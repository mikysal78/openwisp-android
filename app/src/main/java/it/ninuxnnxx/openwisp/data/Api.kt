package it.ninuxnnxx.openwisp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Risposta HTTP non 2xx: code e' lo stato, message il testo da mostrare. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Client delle API REST di OpenWISP (token di openwisp-users).
 *
 * Il token va solo verso [base]: niente redirect seguiti e i link di
 * paginazione ("next") vengono usati solo se puntano allo stesso server.
 */
class OpenWispApi(
    val base: HttpUrl,
    private val token: String?,
    private val client: OkHttpClient = defaultClient,
) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private val JSON_TYPE = "application/json".toMediaType()
        private const val PAGE_SIZE = 100

        /**
         * Indirizzo del server scritto dall'utente -> URL di base, solo https.
         * "openwisp.example.com" diventa "https://openwisp.example.com/".
         */
        fun parseServer(input: String): HttpUrl? {
            val text = input.trim().trimEnd('/')
            if (text.isEmpty() || text.contains(' ')) return null
            val withScheme = if ("://" in text) text else "https://$text"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            if (url.query != null || url.fragment != null) return null
            return url.newBuilder().addPathSegment("").build()
        }
    }

    private fun url(path: String, vararg query: Pair<String, String?>): HttpUrl {
        val builder = base.newBuilder()
        // base finisce con "/": i segmenti si aggiungono dopo quello vuoto
        builder.removePathSegment(base.pathSize - 1)
        "api/v1/$path".trim('/').split('/').forEach { segment ->
            // gli id arrivano anche da intent e notifiche: niente "..", "." o vuoti
            require(segment.isNotEmpty() && segment != "." && segment != "..") { "percorso non valido" }
            builder.addPathSegment(segment)
        }
        builder.addPathSegment("")
        query.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun sameServer(url: HttpUrl) =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port

    private suspend fun call(
        url: HttpUrl,
        method: String = "GET",
        body: JsonElement? = null,
        auth: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        check(sameServer(url)) { "URL fuori dal server configurato" }
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (auth) {
            builder.header("Authorization", "Bearer ${token ?: throw ApiException(401, "")}")
        }
        val requestBody = body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_TYPE) }
        builder.method(method, requestBody ?: if (method == "GET") null else ByteArray(0).toRequestBody(JSON_TYPE))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw ApiException(response.code, errorText(response.code, text))
            text
        }
    }

    private fun errorText(code: Int, body: String): String {
        val detail = runCatching {
            val element = json.parseToJsonElement(body)
            when {
                element is JsonObject && "detail" in element ->
                    element["detail"]!!.jsonPrimitive.content
                element is JsonObject ->
                    element.entries.joinToString("; ") { (k, v) ->
                        val msg = runCatching { v.jsonArray.joinToString(" ") { it.jsonPrimitive.content } }
                            .getOrElse { v.toString() }
                        if (k == "non_field_errors") msg else "$k: $msg"
                    }
                else -> null
            }
        }.getOrNull()
        return detail?.takeIf { it.isNotBlank() } ?: "HTTP $code"
    }

    private suspend fun <T> get(serializer: KSerializer<T>, path: String, vararg query: Pair<String, String?>): T =
        json.decodeFromString(serializer, call(url(path, *query)))

    /** Tutte le pagine di una lista, seguendo "next" solo sullo stesso server. */
    private suspend fun <T> all(serializer: KSerializer<T>, path: String, vararg query: Pair<String, String?>): List<T> {
        val pageSerializer = Page.serializer(serializer)
        val out = mutableListOf<T>()
        var next: HttpUrl? = url(path, *query, "page_size" to PAGE_SIZE.toString())
        var pages = 0
        while (next != null && pages++ < 50) {
            val page = json.decodeFromString(pageSerializer, call(next))
            out += page.results
            next = page.next?.toHttpUrlOrNull()?.takeIf { sameServer(it) }
        }
        return out
    }

    // --- accesso ---

    suspend fun obtainToken(username: String, password: String): String {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val text = call(url("users/token"), "POST", body, auth = false)
        return json.decodeFromString(TokenResponse.serializer(), text).token
    }

    // --- dispositivi ---

    suspend fun devices(): List<Device> = all(Device.serializer(), "monitoring/device")

    suspend fun device(id: String, time: String): DeviceDetail =
        get(DeviceDetail.serializer(), "monitoring/device/$id", "status" to "true", "time" to time)

    suspend fun connections(id: String): List<DeviceConnection> =
        all(DeviceConnection.serializer(), "controller/device/$id/connection")

    suspend fun commands(id: String): Page<Command> =
        get(Page.serializer(Command.serializer()), "controller/device/$id/command", "page_size" to "10")

    suspend fun command(deviceId: String, id: String): Command =
        get(Command.serializer(), "controller/device/$deviceId/command/$id")

    suspend fun reboot(id: String): Command = sendCommand(id, "reboot", JsonNull)

    suspend fun custom(id: String, command: String): Command =
        sendCommand(id, "custom", buildJsonObject { put("command", command) })

    private suspend fun sendCommand(id: String, type: String, input: JsonElement): Command {
        val body = buildJsonObject {
            put("type", type)
            put("input", input)
        }
        return json.decodeFromString(Command.serializer(), call(url("controller/device/$id/command"), "POST", body))
    }

    // --- firmware ---

    /**
     * Firmware installato e aggiornamenti possibili: build della stessa
     * categoria, piu' recenti, con un'immagine dello stesso tipo.
     */
    suspend fun firmware(deviceId: String): FirmwareStatus? = coroutineScope {
        val current = try {
            get(DeviceFirmware.serializer(), "firmware-upgrader/device/$deviceId/firmware")
        } catch (e: ApiException) {
            if (e.code == 404) return@coroutineScope null else throw e
        }
        val builds = all(Build.serializer(), "firmware-upgrader/build", "ordering" to "-created")
        val images = builds.map { build ->
            async { build to all(FirmwareImage.serializer(), "firmware-upgrader/build/${build.id}/image") }
        }.awaitAll()
        val image = images.firstNotNullOfOrNull { (_, list) -> list.firstOrNull { it.id == current.image } }
        val installed = builds.firstOrNull { it.id == image?.build }
        val newer = if (installed == null || image == null) emptyList() else images.mapNotNull { (build, list) ->
            if (build.category != installed.category || build.created <= installed.created) return@mapNotNull null
            list.firstOrNull { it.type == image.type }?.let { build to it }
        }
        FirmwareStatus(installed, image, newer)
    }

    suspend fun upgrade(deviceId: String, imageId: String) {
        call(url("firmware-upgrader/device/$deviceId/firmware"), "PUT", buildJsonObject { put("image", imageId) })
    }

    suspend fun upgradeOperations(deviceId: String): Page<UpgradeOperation> = get(
        Page.serializer(UpgradeOperation.serializer()),
        "firmware-upgrader/device/$deviceId/upgrade-operation",
        "page_size" to "3",
    )

    // --- notifiche ---

    suspend fun notifications(unreadOnly: Boolean = false, pageSize: Int = 50): Page<Notification> = get(
        Page.serializer(Notification.serializer()),
        "notifications/notification",
        "page_size" to pageSize.toString(),
        "unread" to if (unreadOnly) "true" else null,
    )

    suspend fun markRead(id: String) {
        call(url("notifications/notification/$id"), "PATCH")
    }

    suspend fun markAllRead() {
        call(url("notifications/notification/read"), "POST")
    }

    // --- utenti e RADIUS ---

    suspend fun users(): List<User> = all(User.serializer(), "users/user")

    suspend fun setUserActive(id: String, active: Boolean): User = json.decodeFromString(
        User.serializer(),
        call(url("users/user/$id"), "PATCH", buildJsonObject { put("is_active", active) }),
    )

    /**
     * Sessioni RADIUS, le piu' recenti per prime. Questa API risponde con una
     * lista semplice (paginazione nell'header Link), non con {"results": ...}.
     */
    suspend fun sessions(open: Boolean): List<RadiusSession> {
        val text = call(url("radius/sessions", "page_size" to "100", "is_open" to if (open) "true" else null))
        val element = json.parseToJsonElement(text)
        val list = if (element is JsonObject) element["results"] ?: return emptyList() else element
        return json.decodeFromJsonElement(ListSerializer(RadiusSession.serializer()), list)
    }

    // --- moduli facoltativi (openwisp-luci, openwisp-netconsole) ---

    /** Link monouso per LuCI; null se il server non ha openwisp-luci con l'API. */
    suspend fun luciLink(deviceId: String): String? = try {
        val text = call(url("luci/device/$deviceId/link"), "POST")
        json.decodeFromString(LinkResponse.serializer(), text).url
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }

    /** Ultime righe dei log netconsole; null se il server non ha l'API. */
    suspend fun netconsole(deviceId: String): List<NetconsoleLog>? = try {
        json.decodeFromString(
            ListSerializer(NetconsoleLog.serializer()),
            call(url("netconsole/device/$deviceId/logs")),
        )
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }
}

/** Le tracce di un grafico: nome e valori (null dove manca il dato). */
fun Chart.series(): List<Pair<String, List<Double?>>> = traces.mapNotNull { trace ->
    val name = trace.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
    val values = trace.getOrNull(1)?.let { el ->
        runCatching { el.jsonArray.map { it.jsonPrimitive.doubleOrNull } }.getOrNull()
    } ?: return@mapNotNull null
    name to values
}

/** Valori riassuntivi del grafico (es. media del periodo), per nome della traccia. */
fun Chart.summaryValues(): Map<String, Double?> =
    summary?.mapValues { (_, v) -> runCatching { v.jsonPrimitive.doubleOrNull }.getOrNull() } ?: emptyMap()

private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isUuid(text: String?): Boolean = text != null && UUID_RE.matches(text)

/** UUID del device dal target_url di una notifica (/admin/config/device/<uuid>/...). */
fun deviceIdFromTarget(url: String?): String? {
    if (url == null) return null
    return Regex("/admin/config/device/([0-9a-fA-F-]{36})/").find(url)?.groupValues?.get(1)
}
