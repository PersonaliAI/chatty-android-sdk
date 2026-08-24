package com.personaliai.chatty

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ChattyMessage.toJson/fromJson round-trips a full conversation into
 * SharedPreferences (see ChattyViewModel.persistMessages) — a bug here would
 * silently corrupt or drop a user's chat history across app restarts.
 */
@RunWith(RobolectricTestRunner::class)
class ChattyMessageTest {

    @Test
    fun `toJson then fromJson round-trips every field`() {
        val original = ChattyMessage(
            id = "msg-1",
            role = ChattyRole.AGENT,
            text = "Human agent here",
            createdAt = "2026-01-01T00:00:00Z",
            fileUrl = "https://example.com/photo.png",
        )

        val restored = ChattyMessage.fromJson(original.toJson())

        assertEquals(original.id, restored.id)
        assertEquals(original.role, restored.role)
        assertEquals(original.text, restored.text)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.fileUrl, restored.fileUrl)
    }

    @Test
    fun `toJson omits fileUrl key entirely when null`() {
        val msg = ChattyMessage(role = ChattyRole.USER, text = "hi", fileUrl = null)
        val json = msg.toJson()
        assertFalse(json.has("fileUrl"))
    }

    @Test
    fun `fromJson defaults role to USER for missing role field`() {
        val msg = ChattyMessage.fromJson(JSONObject().put("text", "hi"))
        assertEquals(ChattyRole.USER, msg.role)
    }

    @Test
    fun `fromJson generates a fresh id when missing`() {
        val msg = ChattyMessage.fromJson(JSONObject().put("text", "hi"))
        assertNotNull(msg.id)
        assertTrue(msg.id.isNotBlank())
    }

    @Test
    fun `fromJson treats an empty stored fileUrl as absent`() {
        val json = JSONObject().put("text", "hi").put("fileUrl", "")
        val msg = ChattyMessage.fromJson(json)
        assertNull(msg.fileUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromJson throws for an unrecognized role value`() {
        ChattyMessage.fromJson(JSONObject().put("role", "SUPERADMIN").put("text", "hi"))
    }

    @Test
    fun `all three roles round-trip through JSON`() {
        ChattyRole.values().forEach { role ->
            val msg = ChattyMessage(role = role, text = "x")
            val restored = ChattyMessage.fromJson(msg.toJson())
            assertEquals(role, restored.role)
        }
    }
}
