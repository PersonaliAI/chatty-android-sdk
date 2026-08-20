package com.personaliai.chatty

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ChattyRole { USER, ASSISTANT, AGENT }

data class ChattyMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChattyRole,
    val text: String,
    val createdAt: String = Instant.now().toString(),
    val fileUrl: String? = null,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("role", role.name)
        json.put("text", text)
        json.put("createdAt", createdAt)
        fileUrl?.let { json.put("fileUrl", it) }
        return json
    }
    companion object {
        fun fromJson(json: JSONObject): ChattyMessage {
            return ChattyMessage(
                id = json.optString("id", UUID.randomUUID().toString()),
                role = ChattyRole.valueOf(json.optString("role", ChattyRole.USER.name)),
                text = json.optString("text", ""),
                createdAt = json.optString("createdAt", Instant.now().toString()),
                fileUrl = json.optString("fileUrl", null).takeIf { it?.isNotEmpty() == true }
            )
        }
    }
}

private fun List<ChattyMessage>.toJsonArray(): JSONArray {
    val array = JSONArray()
    this.forEach { array.put(it.toJson()) }
    return array
}

private fun jsonArrayToChattyMessages(array: JSONArray): List<ChattyMessage> {
    val list = mutableListOf<ChattyMessage>()
    for (i in 0 until array.length()) {
        list.add(ChattyMessage.fromJson(array.getJSONObject(i)))
    }
    return list
}

data class ChattyUiState(
    val theme: ChattyTheme? = null,
    val ready: Boolean = false,
    val messages: List<ChattyMessage> = emptyList(),
    val sending: Boolean = false,
    val aiPaused: Boolean = false,
    val error: String? = null,
)

/**
 * Drives a full Chatty conversation: loads bot theme/config, manages the
 * persistent session id, sends messages, and polls for human-agent replies
 * every 4s — the native-SDK equivalent of widget.js's embed iframe lifecycle.
 */
class ChattyViewModel(
    application: Application,
    private val botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    private val hostKey: String = "app",
    private val pollIntervalMs: Long = 4000L,
    private val visitorTimezone: String = "UTC",
    private val onMessage: ((ChattyMessage) -> Unit)? = null,
) : AndroidViewModel(application) {

    private val client = ChattyClient(botId, baseUrl, host)
    private var sessionId: String? = null
    private var lastPollAt: String = Instant.now().toString()

    private val _state = MutableStateFlow(ChattyUiState())
    val state: StateFlow<ChattyUiState> = _state.asStateFlow()

    private fun persistMessages(messages: List<ChattyMessage>) {
        val toStore = messages.takeLast(100)
        val prefs = getApplication<Application>().getSharedPreferences("chatty_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("chatty_msgs_${botId}_${hostKey}", toStore.toJsonArray().toString()).apply()
    }

    init {
        val prefs = application.getSharedPreferences("chatty_prefs", Context.MODE_PRIVATE)
        val cachedMsgsString = prefs.getString("chatty_msgs_${botId}_${hostKey}", null)
        val cachedMsgs = if (cachedMsgsString != null) {
            try {
                jsonArrayToChattyMessages(JSONArray(cachedMsgsString))
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _state.update { it.copy(messages = cachedMsgs) }

        viewModelScope.launch {
            try {
                val theme = client.getTheme()
                sessionId = ChattySession.getOrCreateSessionId(application, botId, hostKey)
                val welcome = theme.welcomeMessage?.let {
                    listOf(ChattyMessage(id = "welcome", role = ChattyRole.ASSISTANT, text = it))
                } ?: emptyList()
                val currentMsgs = _state.value.messages
                val newMsgs = if (currentMsgs.isEmpty()) welcome else currentMsgs
                _state.update { it.copy(theme = theme, ready = true, messages = newMsgs) }
                if (currentMsgs.isEmpty() && welcome.isNotEmpty()) {
                    persistMessages(newMsgs)
                }
                startPolling()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to load Chatty bot") }
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(pollIntervalMs)
                val sid = sessionId ?: continue
                try {
                    val res = client.poll(sid, lastPollAt)
                    if (res.messages.isNotEmpty()) {
                        lastPollAt = res.messages.last().createdAt
                        val newMsgs = res.messages.map {
                            ChattyMessage(
                                role = if (it.sender == "agent") ChattyRole.AGENT else ChattyRole.ASSISTANT,
                                text = it.content,
                                createdAt = it.createdAt,
                            )
                        }
                        val updatedMsgs = _state.value.messages + newMsgs
                        persistMessages(updatedMsgs)
                        _state.update { it.copy(messages = updatedMsgs, aiPaused = res.aiPaused) }
                        newMsgs.forEach { onMessage?.invoke(it) }
                    } else {
                        _state.update { it.copy(aiPaused = res.aiPaused) }
                    }
                } catch (_: Exception) {
                    // silent — polling failures shouldn't surface as user-facing errors
                }
            }
        }
    }

    fun sendTextStream(text: String) {
        val trimmed = text.trim()
        val sid = sessionId ?: return
        if (trimmed.isEmpty()) return

        val userMsg = ChattyMessage(role = ChattyRole.USER, text = trimmed)
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg = ChattyMessage(id = assistantMsgId, role = ChattyRole.ASSISTANT, text = "")
        
        val newMsgs = _state.value.messages + userMsg + placeholderMsg
        _state.update { it.copy(messages = newMsgs, sending = true, error = null) }
        persistMessages(newMsgs)

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.sendMessageStream(sid, trimmed, visitorTimezone) { token ->
                        _state.update { st ->
                            val msgs = st.messages.map { 
                                if (it.id == assistantMsgId) it.copy(text = it.text + token) else it
                            }
                            persistMessages(msgs)
                            st.copy(messages = msgs)
                        }
                    }
                }
                val finishedMsg = _state.value.messages.find { it.id == assistantMsgId }
                if (finishedMsg != null) {
                    onMessage?.invoke(finishedMsg)
                }
            } catch (e: Exception) {
                _state.update { st -> 
                    val msgs = st.messages.filter { it.id != userMsg.id && it.id != assistantMsgId }
                    persistMessages(msgs)
                    st.copy(messages = msgs)
                }
                sendText(trimmed)
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        val sid = sessionId ?: return
        if (trimmed.isEmpty()) return

        val newMsgs = _state.value.messages + ChattyMessage(role = ChattyRole.USER, text = trimmed)
        _state.update { it.copy(messages = newMsgs, sending = true, error = null) }
        persistMessages(newMsgs)
        viewModelScope.launch {
            try {
                val res = client.sendMessage(sid, trimmed, visitorTimezone)
                _state.update { it.copy(aiPaused = res.aiPaused) }
                if (!res.aiPaused && res.reply.isNotEmpty()) {
                    val reply = ChattyMessage(role = ChattyRole.ASSISTANT, text = res.reply)
                    val updatedMsgs = _state.value.messages + reply
                    _state.update { it.copy(messages = updatedMsgs) }
                    persistMessages(updatedMsgs)
                    onMessage?.invoke(reply)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to send message") }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun sendImage(file: File, mimeType: String, caption: String = "") {
        val sid = sessionId ?: return
        val newMsgs = _state.value.messages + ChattyMessage(role = ChattyRole.USER, text = caption, fileUrl = file.absolutePath)
        _state.update {
            it.copy(
                messages = newMsgs,
                sending = true,
                error = null,
            )
        }
        persistMessages(newMsgs)
        viewModelScope.launch {
            try {
                val res = client.sendMedia(sid, file, mimeType, caption, visitorTimezone)
                _state.update { it.copy(aiPaused = res.aiPaused) }
                if (!res.aiPaused && res.reply.isNotEmpty()) {
                    val reply = ChattyMessage(role = ChattyRole.ASSISTANT, text = res.reply)
                    val updatedMsgs = _state.value.messages + reply
                    _state.update { it.copy(messages = updatedMsgs) }
                    persistMessages(updatedMsgs)
                    onMessage?.invoke(reply)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed to send image") }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }
}
