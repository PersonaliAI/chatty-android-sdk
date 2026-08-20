package com.personaliai.chatty

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val CHATTY_DEFAULT_BASE_URL = "https://api.chatty.personaliai.com"

data class ChattyTheme(
    val name: String? = null,
    val primaryColor: String? = null,
    val widgetStyle: String? = null,
    val logoUrl: String? = null,
    val welcomeMessage: String? = null,
    val sendButtonStyle: String? = null,
    val conversationStarters: List<String> = emptyList(),
    val teaserMessage: String? = null,
    val avatarIcon: String? = null,
    val avatarUrl: String? = null,
    val voiceEnabled: Boolean = false,
) {
    companion object {
        fun fromJson(json: JSONObject): ChattyTheme {
            val starters = mutableListOf<String>()
            json.optJSONArray("conversation_starters")?.let { arr ->
                for (i in 0 until arr.length()) starters.add(arr.getString(i))
            }
            return ChattyTheme(
                name = json.optString("name", null),
                primaryColor = json.optString("primary_color", null),
                widgetStyle = json.optString("widget_style", null),
                logoUrl = json.optString("logo_url", null),
                welcomeMessage = json.optString("welcome_message", null),
                sendButtonStyle = json.optString("send_button_style", null),
                conversationStarters = starters,
                teaserMessage = json.optString("teaser_message", null),
                avatarIcon = json.optString("avatar_icon", null),
                avatarUrl = json.optString("avatar_url", null),
                voiceEnabled = json.optBoolean("voice_enabled", false),
            )
        }
    }
}

data class ChattyChatResponse(
    val reply: String,
    val sessionId: String,
    val aiPaused: Boolean = false,
    val fileUrl: String? = null,
    val fileType: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): ChattyChatResponse = ChattyChatResponse(
            reply = json.optString("reply", ""),
            sessionId = json.optString("session_id", ""),
            aiPaused = json.optBoolean("ai_paused", false),
            fileUrl = json.optString("file_url", null),
            fileType = json.optString("file_type", null),
        )
    }
}

data class ChattyPollMessage(val content: String, val createdAt: String, val sender: String)

data class ChattyPollResponse(val messages: List<ChattyPollMessage>, val aiPaused: Boolean) {
    companion object {
        fun fromJson(json: JSONObject): ChattyPollResponse {
            val arr = json.optJSONArray("messages") ?: JSONArray()
            val msgs = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                ChattyPollMessage(m.optString("content"), m.optString("created_at"), m.optString("sender"))
            }
            return ChattyPollResponse(msgs, json.optBoolean("ai_paused", false))
        }
    }
}

// The backend can't cryptographically verify a native app's identity the way
// it verifies a browser's Referer for the web widget, so a bot with
// allowed_domains configured always rate-limits mobile SDK traffic at the
// stricter "unverified" tier (5 msgs/120s per bot+IP) rather than the normal
// tier (30 msgs/60s) — it does NOT reject on a mismatched `host`. The `host`
// field this client sends is advisory only; the backend doesn't trust it.
class ChattyRateLimitException : IOException("Chatty: rate limit exceeded — 30 msgs/60s normally, or 5 msgs/120s per bot+IP if allowed_domains is set (mobile traffic always gets this stricter tier)")
// Kept for forward-compatibility — the current backend never returns 403 for
// domain/origin reasons on these endpoints, but this stays wired up in case
// that changes.
class ChattyDomainNotAllowedException : IOException("Chatty: request rejected (403)")

/**
 * Thin HTTP client for the Chatty widget API (the `/api/widget/` routes). No auth header —
 * bot_id alone identifies the bot. bot_id is not a secret (it's extractable from any client);
 * allowed_domains is enforced via rate-limit tier, not a hard reject — see ChattyRateLimitException.
 */
class ChattyClient(
    private val botId: String,
    private val baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    private val host: String? = null,
) {
    private val client = OkHttpClient()

    suspend fun getTheme(): ChattyTheme {
        val url = "$baseUrl/api/widget/theme?bot_id=$botId&t=${System.currentTimeMillis()}"
        val json = execute(Request.Builder().url(url).get().build())
        return ChattyTheme.fromJson(json)
    }

    suspend fun sendMessage(sessionId: String, text: String, visitorTimezone: String = "UTC"): ChattyChatResponse {
        val body = JSONObject().apply {
            put("bot_id", botId)
            put("session_id", sessionId)
            put("text", text)
            put("visitor_timezone", visitorTimezone)
            host?.let { put("host", it) }
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url("$baseUrl/api/widget/chat").post(body).build()
        return ChattyChatResponse.fromJson(execute(req))
    }

    suspend fun sendMedia(
        sessionId: String,
        file: File,
        mimeType: String,
        text: String = "",
        visitorTimezone: String = "UTC",
    ): ChattyChatResponse {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("bot_id", botId)
            .addFormDataPart("session_id", sessionId)
            .addFormDataPart("text", text)
            .addFormDataPart("visitor_timezone", visitorTimezone)
            .apply { host?.let { addFormDataPart("host", it) } }
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
            .build()

        val req = Request.Builder().url("$baseUrl/api/widget/chat/media").post(multipart).build()
        return ChattyChatResponse.fromJson(execute(req))
    }

    suspend fun poll(sessionId: String, after: String): ChattyPollResponse {
        val url = "$baseUrl/api/widget/poll?bot_id=$botId&session_id=$sessionId&after=$after"
        return ChattyPollResponse.fromJson(execute(Request.Builder().url(url).get().build()))
    }

    /** Server-side speech-to-text for the mic button. Accepts wav/mp3/ogg/aac/aiff/flac
     * (not webm) up to 10MB. Returns the transcribed text, or "" if speech wasn't detected. */
    suspend fun transcribe(file: File, mimeType: String): String {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("bot_id", botId)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
            .build()
        val req = Request.Builder().url("$baseUrl/api/widget/transcribe").post(multipart).build()
        return execute(req).optString("text", "")
    }

    suspend fun sendMessageStream(
        sessionId: String,
        text: String,
        visitorTimezone: String = "UTC",
        onToken: (String) -> Unit
    ) {
        val body = JSONObject().apply {
            put("bot_id", botId)
            put("session_id", sessionId)
            put("text", text)
            put("visitor_timezone", visitorTimezone)
            host?.let { put("host", it) }
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder().url("$baseUrl/api/widget/chat/stream").post(body).build()
        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            when (response.code) {
                429 -> throw ChattyRateLimitException()
                403 -> throw ChattyDomainNotAllowedException()
                else -> throw IOException("Chatty stream request failed: ${response.code}")
            }
        }

        response.body?.source()?.let { source ->
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data.isEmpty()) continue
                    val json = JSONObject(data)
                    if (json.optBoolean("done", false)) {
                        break
                    }
                    val token = json.optString("token", "")
                    if (token.isNotEmpty()) {
                        onToken(token)
                    }
                }
            }
        }
    }

    private suspend fun execute(request: Request): JSONObject = suspendCoroutine { cont ->
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    when (response.code) {
                        429 -> return cont.resumeWithException(ChattyRateLimitException())
                        403 -> return cont.resumeWithException(ChattyDomainNotAllowedException())
                    }
                    if (!response.isSuccessful) {
                        return cont.resumeWithException(IOException("Chatty request failed: ${response.code}"))
                    }
                    val text = response.body?.string() ?: "{}"
                    cont.resume(JSONObject(text))
                }
            }
        })
    }
}
