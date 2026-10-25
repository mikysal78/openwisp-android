package it.ninuxnnxx.openwisp

import it.ninuxnnxx.openwisp.data.ApiException
import it.ninuxnnxx.openwisp.data.OpenWispApi
import it.ninuxnnxx.openwisp.data.deviceIdFromTarget
import it.ninuxnnxx.openwisp.data.isUuid
import it.ninuxnnxx.openwisp.data.series
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OpenWispApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OpenWispApi(server.url("/"), "segreto")
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun parseServerAcceptsOnlyHttps() {
        assertEquals("https://openwisp.example.com/", OpenWispApi.parseServer("openwisp.example.com").toString())
        assertEquals("https://openwisp.example.com/", OpenWispApi.parseServer(" https://openwisp.example.com/ ").toString())
        assertEquals("https://example.com/openwisp/", OpenWispApi.parseServer("example.com/openwisp").toString())
        assertNull(OpenWispApi.parseServer("http://openwisp.example.com"))
        assertNull(OpenWispApi.parseServer("https://user:pass@openwisp.example.com"))
        assertNull(OpenWispApi.parseServer("https://openwisp.example.com/?a=b"))
        assertNull(OpenWispApi.parseServer(""))
    }

    @Test
    fun sendsBearerTokenAndFollowsPages() = runBlocking {
        val next = server.url("/api/v1/monitoring/device/?page=2&page_size=100")
        json("""{"count":2,"next":"$next","results":[{"id":"a","name":"uno","monitoring":{"status":"ok"}}]}""")
        json("""{"count":2,"next":null,"results":[{"id":"b","name":"due","extra":1}]}""")
        val devices = api.devices()
        assertEquals(listOf("uno", "due"), devices.map { it.name })
        assertEquals("ok", devices[0].monitoring?.status)
        val first = server.takeRequest()
        assertEquals("Bearer segreto", first.headers["Authorization"])
        assertEquals("/api/v1/monitoring/device/?page_size=100", first.target)
        assertEquals("/api/v1/monitoring/device/?page=2&page_size=100", server.takeRequest().target)
    }

    @Test
    fun ignoresNextLinksToOtherHosts() = runBlocking {
        json("""{"count":2,"next":"https://evil.example.com/steal/","results":[{"id":"a","name":"uno"}]}""")
        assertEquals(1, api.devices().size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun loginDoesNotSendToken() = runBlocking {
        json("""{"token":"nuovo"}""")
        val token = OpenWispApi(server.url("/"), null).obtainToken("admin", "pw")
        assertEquals("nuovo", token)
        val request = server.takeRequest()
        assertNull(request.headers["Authorization"])
        assertEquals("/api/v1/users/token/", request.target)
        assertTrue(request.body!!.utf8().contains("\"username\":\"admin\""))
    }

    @Test
    fun errorsCarryStatusAndDetail() = runBlocking {
        json("""{"non_field_errors":["Credenziali errate"]}""", 400)
        try {
            OpenWispApi(server.url("/"), null).obtainToken("admin", "x")
            fail()
        } catch (e: ApiException) {
            assertEquals(400, e.code)
            assertEquals("Credenziali errate", e.message)
        }
        json("""{"detail":"Token non valido."}""", 401)
        try {
            api.devices()
            fail()
        } catch (e: ApiException) {
            assertEquals(401, e.code)
            assertEquals("Token non valido.", e.message)
        }
    }

    @Test
    fun redirectsAreNotFollowed() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "https://evil.example.com/").build())
        try {
            api.devices()
            fail()
        } catch (e: ApiException) {
            assertEquals(302, e.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun pathTraversalIsRejected() = runBlocking {
        try {
            api.device("../../users/user", "1d")
            fail()
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rebootSendsNullInput() = runBlocking {
        json("""{"id":"c","type":"reboot","status":"in-progress"}""", 201)
        api.reboot("9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/controller/device/9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6/command/", request.target)
        assertEquals("""{"type":"reboot","input":null}""", request.body!!.utf8())
    }

    @Test
    fun optionalModulesReturnNullOn404() = runBlocking {
        json("""{"detail":"Not found."}""", 404)
        assertNull(api.luciLink("9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6"))
        json("""{"url":"https://x.luci.example.com/__openwisp_luci__/login/?token=t"}""")
        assertEquals(
            "https://x.luci.example.com/__openwisp_luci__/login/?token=t",
            api.luciLink("9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6"),
        )
    }

    @Test
    fun firmwareFindsNewerBuildsOfSameCategoryAndType() = runBlocking {
        val dev = "9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6"
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path.endsWith("/device/$dev/firmware/") -> """{"image":"img-old","installed":true}"""
                    path.endsWith("/build/") -> """{"count":3,"results":[
                        {"id":"b3","version":"v3","category":"other","created":"2026-10-24T00:00:00Z"},
                        {"id":"b2","version":"v2","category":"cat","created":"2026-10-23T00:00:00Z"},
                        {"id":"b1","version":"v1","category":"cat","created":"2026-10-20T00:00:00Z"}]}"""
                    path.endsWith("/build/b1/image/") -> """{"results":[{"id":"img-old","type":"sysupgrade","build":"b1"}]}"""
                    path.endsWith("/build/b2/image/") -> """{"results":[
                        {"id":"img-x","type":"factory","build":"b2"},{"id":"img-new","type":"sysupgrade","build":"b2"}]}"""
                    path.endsWith("/build/b3/image/") -> """{"results":[{"id":"img-3","type":"sysupgrade","build":"b3"}]}"""
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
            }
        }
        val status = api.firmware(dev)!!
        assertEquals("v1", status.installed?.version)
        assertEquals(listOf("img-new"), status.newer.map { it.second.id })
    }

    @Test
    fun sessionsAcceptPlainListAndPage() = runBlocking {
        json("""[]""")
        assertEquals(0, api.sessions(open = true).size)
        assertEquals("/api/v1/radius/sessions/?page_size=100&is_open=true", server.takeRequest().target)
        json("""[{"unique_id":"u1","username":"a"}]""")
        assertEquals("a", api.sessions(open = false).single().username)
        json("""{"count":1,"results":[{"unique_id":"u2"}]}""")
        assertEquals("u2", api.sessions(open = false).single().uniqueId)
    }

    @Test
    fun helpers() {
        assertTrue(isUuid("9c0bbdf5-fb7f-49a0-bbed-837a0bd3e2b6"))
        assertFalse(isUuid("../9c0bbdf5"))
        assertEquals(
            "9077e798-e245-4d4b-8892-5b92c97fe8f6",
            deviceIdFromTarget("https://o.example.com/admin/config/device/9077e798-e245-4d4b-8892-5b92c97fe8f6/change/#x"),
        )
        assertNull(deviceIdFromTarget("https://o.example.com/admin/"))
        val chart = OpenWispApi.json.decodeFromString(
            it.ninuxnnxx.openwisp.data.Chart.serializer(),
            """{"title":"RTT","traces":[["RTT_average",[0.5,null,1]],["bad"]]}""",
        )
        assertEquals(listOf("RTT_average" to listOf(0.5, null, 1.0)), chart.series())
    }
}
