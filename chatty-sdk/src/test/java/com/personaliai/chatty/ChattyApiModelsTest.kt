package com.personaliai.chatty

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the plain-data JSON (de)serialization in ChattyApi.kt:
 * ChattyTheme, ChattyChatResponse, ChattyPollResponse, and the two
 * IOException subclasses used to signal specific HTTP failure modes.
 *
 * Runs under Robolectric because org.json.* classes are stubbed to throw
 * "not mocked" under the plain android.jar unit-test classpath.
 */
@RunWith(RobolectricTestRunner::class)
class ChattyApiModelsTest {

    // ---- ChattyTheme -------------------------------------------------

    @Test
    fun `theme fromJson parses all fields`() {
        val json = JSONObject().apply {
            put("name", "Acme Support")
            put("primary_color", "#ff0000")
            put("widget_style", "minimal:#fff:bubble")
            put("logo_url", "https://example.com/logo.png")
            put("welcome_message", "Hi there!")
            put("send_button_style", "arrow")
            put("conversation_starters", JSONArray(listOf("Pricing?", "Support")))
            put("teaser_message", "Need help?")
            put("avatar_icon", "robot")
            put("avatar_url", "https://example.com/avatar.png")
            put("voice_enabled", true)
        }

        val theme = ChattyTheme.fromJson(json)

        assertEquals("Acme Support", theme.name)
        assertEquals("#ff0000", theme.primaryColor)
        assertEquals("minimal:#fff:bubble", theme.widgetStyle)
        assertEquals("https://example.com/logo.png", theme.logoUrl)
        assertEquals("Hi there!", theme.welcomeMessage)
        assertEquals("arrow", theme.sendButtonStyle)
        assertEquals(listOf("Pricing?", "Support"), theme.conversationStarters)
        assertEquals("Need help?", theme.teaserMessage)
        assertEquals("robot", theme.avatarIcon)
        assertEquals("https://example.com/avatar.png", theme.avatarUrl)
        assertTrue(theme.voiceEnabled)
    }

    @Test
    fun `theme fromJson defaults missing fields to null-safe values`() {
        val theme = ChattyTheme.fromJson(JSONObject())

        assertNull(theme.name)
        assertNull(theme.primaryColor)
        assertNull(theme.widgetStyle)
        assertNull(theme.logoUrl)
        assertNull(theme.welcomeMessage)
        assertTrue(theme.conversationStarters.isEmpty())
        assertFalse(theme.voiceEnabled)
    }

    @Test
    fun `theme fromJson tolerates empty conversation_starters array`() {
        val json = JSONObject().put("conversation_starters", JSONArray())
        val theme = ChattyTheme.fromJson(json)
        assertTrue(theme.conversationStarters.isEmpty())
    }

    // ---- ChattyChatResponse -------------------------------------------

    @Test
    fun `chatResponse fromJson parses all fields`() {
        val json = JSONObject().apply {
            put("reply", "Sure, I can help.")
            put("session_id", "sess-123")
            put("ai_paused", true)
            put("file_url", "https://example.com/file.pdf")
            put("file_type", "application/pdf")
        }

        val res = ChattyChatResponse.fromJson(json)

        assertEquals("Sure, I can help.", res.reply)
        assertEquals("sess-123", res.sessionId)
        assertTrue(res.aiPaused)
        assertEquals("https://example.com/file.pdf", res.fileUrl)
        assertEquals("application/pdf", res.fileType)
    }

    @Test
    fun `chatResponse fromJson defaults reply and sessionId to empty string`() {
        val res = ChattyChatResponse.fromJson(JSONObject())
        assertEquals("", res.reply)
        assertEquals("", res.sessionId)
        assertFalse(res.aiPaused)
        assertNull(res.fileUrl)
        assertNull(res.fileType)
    }

    // ---- ChattyPollResponse --------------------------------------------

    @Test
    fun `pollResponse fromJson parses messages in order`() {
        val messages = JSONArray().apply {
            put(JSONObject().put("content", "hello").put("created_at", "2026-01-01T00:00:00Z").put("sender", "bot"))
            put(JSONObject().put("content", "human here").put("created_at", "2026-01-01T00:01:00Z").put("sender", "agent"))
        }
        val json = JSONObject().put("messages", messages).put("ai_paused", true)

        val res = ChattyPollResponse.fromJson(json)

        assertEquals(2, res.messages.size)
        assertEquals("hello", res.messages[0].content)
        assertEquals("bot", res.messages[0].sender)
        assertEquals("agent", res.messages[1].sender)
        assertTrue(res.aiPaused)
    }

    @Test
    fun `pollResponse fromJson handles missing messages array`() {
        val res = ChattyPollResponse.fromJson(JSONObject())
        assertTrue(res.messages.isEmpty())
        assertFalse(res.aiPaused)
    }

    // ---- Exceptions -----------------------------------------------------

    @Test
    fun `rate limit exception carries a descriptive message`() {
        val ex = ChattyRateLimitException()
        assertTrue(ex.message!!.contains("rate limit"))
    }

    @Test
    fun `domain not allowed exception mentions 403`() {
        val ex = ChattyDomainNotAllowedException()
        assertTrue(ex.message!!.contains("403"))
    }
}
