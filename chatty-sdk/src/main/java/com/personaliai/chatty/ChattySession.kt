package com.personaliai.chatty

import android.content.Context
import java.util.UUID

/**
 * Mirrors the web widget's session id scheme (`chatty_sid_{botId}_{hostKey}` in
 * localStorage) so the same visitor is recognized whether they arrive via web
 * or the native app, when `hostKey` is shared deliberately.
 */
object ChattySession {
    private const val PREFS = "chatty_sdk_prefs"

    fun getOrCreateSessionId(context: Context, botId: String, hostKey: String = "app"): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "chatty_sid_${botId}_$hostKey"
        prefs.getString(key, null)?.let { return it }
        return newSession(context, botId, hostKey)
    }

    /** Overwrites the stored session id with a fresh one — used by the "clear chat" action. */
    fun newSession(context: Context, botId: String, hostKey: String = "app"): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "chatty_sid_${botId}_$hostKey"
        val sid = "v-${UUID.randomUUID().toString().replace("-", "")}"
        prefs.edit().putString(key, sid).apply()
        return sid
    }
}
