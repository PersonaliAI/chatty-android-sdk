package com.personaliai.chatty

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Exercises ChattyClient's real OkHttp request/response path against a local
 * MockWebServer instance instead of the live backend — covers URL/body
 * construction, success parsing, and the 429/403/5xx/malformed-JSON error
 * paths that the app is expected to distinguish between.
 */
@RunWith(RobolectricTestRunner::class)
class ChattyClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ChattyClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ChattyClient(botId = "bot-42", baseUrl = server.url("/").toString().trimEnd('/'), host = "example.com")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getTheme sends GET with bot_id query param and parses response`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"name":"Acme","welcome_message":"hi"}"""))

        val theme = client.getTheme()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/widget/theme?bot_id=bot-42"))
        assertEquals("Acme", theme.name)
        assertEquals("hi", theme.welcomeMessage)
    }

    @Test
    fun `sendMessage posts JSON body with bot_id session_id text and host`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"reply":"hello back","session_id":"s1"}"""))

        val res = client.sendMessage("s1", "hi there", "Asia/Colombo")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/widget/chat", recorded.path)
        val sentBody = org.json.JSONObject(recorded.body.readUtf8())
        assertEquals("bot-42", sentBody.getString("bot_id"))
        assertEquals("s1", sentBody.getString("session_id"))
        assertEquals("hi there", sentBody.getString("text"))
        assertEquals("Asia/Colombo", sentBody.getString("visitor_timezone"))
        assertEquals("example.com", sentBody.getString("host"))
        assertEquals("hello back", res.reply)
    }

    @Test
    fun `sendMessage omits host field when host is null`() = runBlocking {
        val noHostClient = ChattyClient(botId = "bot-42", baseUrl = server.url("/").toString().trimEnd('/'), host = null)
        server.enqueue(MockResponse().setBody("""{"reply":"ok","session_id":"s1"}"""))

        noHostClient.sendMessage("s1", "hi", "UTC")

        val recorded = server.takeRequest()
        val sentBody = org.json.JSONObject(recorded.body.readUtf8())
        assertTrue(!sentBody.has("host"))
    }

    @Test
    fun `poll builds URL with bot_id session_id and after params`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"messages":[],"ai_paused":false}"""))

        client.poll("s1", "2026-01-01T00:00:00Z")

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("bot_id=bot-42"))
        assertTrue(recorded.path!!.contains("session_id=s1"))
        assertTrue(recorded.path!!.contains("after=2026-01-01T00:00:00Z"))
    }

    @Test
    fun `429 response throws ChattyRateLimitException`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val ex = try {
            runBlocking { client.sendMessage("s1", "hi") }
            null
        } catch (e: ChattyRateLimitException) {
            e
        }
        assertTrue(ex is ChattyRateLimitException)
    }

    @Test
    fun `403 response throws ChattyDomainNotAllowedException`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val ex = try {
            runBlocking { client.sendMessage("s1", "hi") }
            null
        } catch (e: ChattyDomainNotAllowedException) {
            e
        }
        assertTrue(ex is ChattyDomainNotAllowedException)
    }

    @Test
    fun `500 response throws IOException with status code in message`() {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            runBlocking { client.sendMessage("s1", "hi") }
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `malformed JSON body surfaces as a JSONException`() {
        server.enqueue(MockResponse().setBody("not json"))

        try {
            runBlocking { client.getTheme() }
            fail("expected JSONException")
        } catch (e: JSONException) {
            // expected — execute() does not swallow parse failures
        }
    }

    @Test
    fun `empty body defaults to empty JSON object`() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        val theme = client.getTheme()

        assertEquals(null, theme.name)
    }

    @Test
    fun `sendMessageStream emits tokens until done marker`() = runBlocking {
        val sse = buildString {
            append("data: {\"token\":\"Hel\"}\n")
            append("data: {\"token\":\"lo\"}\n")
            append("data: {\"done\":true}\n")
            append("data: {\"token\":\"never seen\"}\n")
        }
        server.enqueue(MockResponse().setBody(sse))

        val tokens = mutableListOf<String>()
        client.sendMessageStream("s1", "hi") { tokens.add(it) }

        assertEquals(listOf("Hel", "lo"), tokens)
    }

    @Test
    fun `sendMessageStream 429 throws ChattyRateLimitException before reading body`() {
        server.enqueue(MockResponse().setResponseCode(429))

        try {
            runBlocking { client.sendMessageStream("s1", "hi") { } }
            fail("expected ChattyRateLimitException")
        } catch (e: ChattyRateLimitException) {
            // expected
        }
    }
}
