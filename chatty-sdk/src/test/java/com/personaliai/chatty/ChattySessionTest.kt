package com.personaliai.chatty

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ChattySession persists a per-bot/per-hostKey visitor id in SharedPreferences
 * so the same device is recognized across app restarts. Runs under Robolectric
 * for a real Context + SharedPreferences implementation.
 */
@RunWith(RobolectricTestRunner::class)
class ChattySessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `getOrCreateSessionId creates and persists a new id`() {
        val id = ChattySession.getOrCreateSessionId(context, "bot-1")
        assertTrue(id.startsWith("v-"))

        val prefs = context.getSharedPreferences("chatty_sdk_prefs", android.content.Context.MODE_PRIVATE)
        assertEquals(id, prefs.getString("chatty_sid_bot-1_app", null))
    }

    @Test
    fun `getOrCreateSessionId returns the same id on repeated calls`() {
        val first = ChattySession.getOrCreateSessionId(context, "bot-1")
        val second = ChattySession.getOrCreateSessionId(context, "bot-1")
        assertEquals(first, second)
    }

    @Test
    fun `different botId or hostKey get independent session ids`() {
        val botA = ChattySession.getOrCreateSessionId(context, "bot-a")
        val botB = ChattySession.getOrCreateSessionId(context, "bot-b")
        val hostKeyed = ChattySession.getOrCreateSessionId(context, "bot-a", hostKey = "widget")

        assertNotEquals(botA, botB)
        assertNotEquals(botA, hostKeyed)
    }

    @Test
    fun `newSession overwrites the previously stored id`() {
        val original = ChattySession.getOrCreateSessionId(context, "bot-1")
        val fresh = ChattySession.newSession(context, "bot-1")

        assertNotEquals(original, fresh)
        assertEquals(fresh, ChattySession.getOrCreateSessionId(context, "bot-1"))
    }

    @Test
    fun `session ids are unique UUID-derived strings without dashes`() {
        val id = ChattySession.newSession(context, "bot-1")
        assertTrue(id.matches(Regex("^v-[0-9a-f]{32}$")))
    }
}
