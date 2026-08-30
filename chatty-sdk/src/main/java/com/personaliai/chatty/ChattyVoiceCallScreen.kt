// NOTE: written without the ability to run Gradle, a real Android build, or
// a device/emulator in this environment. Every LiveKit Android SDK
// class/method signature referenced here (io.livekit:livekit-android:2.18.2)
// WAS verified against the real published AAR via `javap` — Room.connect,
// Room.disconnect, Room.localParticipant, Room.events (SharedFlow<RoomEvent>),
// Room.activeSpeakers, LocalParticipant.setMicrophoneEnabled,
// RoomEvent.TranscriptionReceived/ActiveSpeakersChanged/Disconnected/Connected,
// TranscriptionSegment(id/text/final), LiveKit.create — all confirmed to
// exist with these exact signatures. RoomEvent.TranscriptionReceived is
// itself annotated @io.livekit.android.annotations.Beta (confirmed via
// javap's RuntimeInvisibleAnnotations) — hence the file-level @OptIn below.
// What's NOT verified: that this file actually compiles as a whole — a real
// Gradle run against the real dependency (see commit history on this file)
// already caught and fixed three real errors here (wrong graphicsLayer
// import package, missing Flow.collect import, missing Beta opt-in) that
// static reasoning alone had missed. Build and test a real call on device
// before releasing regardless — that part still has zero verification.
@file:OptIn(io.livekit.android.annotations.Beta::class)

package com.personaliai.chatty

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private enum class ChattyCallStatus { CONNECTING, REQUESTING_MIC, CONNECTED, LISTENING, AGENT_SPEAKING, ERROR, ENDED }

private data class ChattyTranscriptEntry(val id: String, val fromVisitor: Boolean, val text: String, val final: Boolean)

/**
 * Full-screen voice-call UI — show this when [ChattyChatScreen]'s header
 * phone button is tapped and `theme.voiceEnabled` is true, e.g.:
 *
 * ```
 * var showCall by remember { mutableStateOf(false) }
 * if (showCall) {
 *     ChattyVoiceCallScreen(client = client, sessionId = sessionId,
 *         widgetStyle = theme?.widgetStyle, onClose = { showCall = false })
 * } else {
 *     ChattyChatScreen(state = state, onVoiceCallPress = { showCall = true }, ...)
 * }
 * ```
 *
 * Requires a separate Gradle dependency this SDK does NOT declare itself —
 * add `implementation("io.livekit:livekit-android:2.18.2")` (or newer) to
 * your app module, plus `RECORD_AUDIO` in your manifest, only if you render
 * this screen. Call `LiveKit.init(applicationContext)` once at app startup
 * (e.g. in your `Application.onCreate()`) before this screen is first shown.
 */
@Composable
fun ChattyVoiceCallScreen(
    client: ChattyClient,
    sessionId: String,
    widgetStyle: String?,
    visitorTimezone: String = "UTC",
    onClose: () -> Unit,
) {
    val designId = chattyNormalizeWidgetStyle(widgetStyle)
    val t = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(ChattyCallStatus.CONNECTING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    val transcript = remember { mutableStateListOf<ChattyTranscriptEntry>() }
    var room by remember { mutableStateOf<Room?>(null) }
    val listState = rememberLazyListState()

    // Connect once when this screen first appears.
    LaunchedEffect(Unit) {
        try {
            val tok = client.getVoiceToken(sessionId, visitorTimezone)
            val r = LiveKit.create(context.applicationContext)
            room = r

            launch {
                // Room.events returns EventListenable<RoomEvent>, a wrapper
                // — not a Flow itself (confirmed via javap: getEvents()'s
                // declared return type). The actual SharedFlow is one level
                // deeper, at EventListenable.events.
                r.events.events.collect { event ->
                    when (event) {
                        is RoomEvent.Disconnected -> {
                            if (status != ChattyCallStatus.ERROR) status = ChattyCallStatus.ENDED
                        }
                        is RoomEvent.TranscriptionReceived -> {
                            val speakerIsVisitor = event.participant == null || event.participant?.identity == r.localParticipant.identity
                            for (seg in event.transcriptionSegments) {
                                val idx = transcript.indexOfFirst { it.id == seg.id }
                                val entry = ChattyTranscriptEntry(seg.id, speakerIsVisitor, seg.text, seg.final)
                                if (idx >= 0) transcript[idx] = entry else transcript.add(entry)
                            }
                        }
                        is RoomEvent.ActiveSpeakersChanged -> {
                            val localIdentity = r.localParticipant.identity
                            var remoteLevel = 0f
                            var localSpeaking = false
                            for (p in event.speakers) {
                                if (p.identity == localIdentity) localSpeaking = true
                                else remoteLevel = maxOf(remoteLevel, p.audioLevel)
                            }
                            status = when {
                                status == ChattyCallStatus.CONNECTING || status == ChattyCallStatus.REQUESTING_MIC ||
                                    status == ChattyCallStatus.ERROR || status == ChattyCallStatus.ENDED -> status
                                remoteLevel > 0.01f -> ChattyCallStatus.AGENT_SPEAKING
                                localSpeaking -> ChattyCallStatus.LISTENING
                                else -> ChattyCallStatus.CONNECTED
                            }
                        }
                        else -> {}
                    }
                }
            }

            r.connect(tok.livekitUrl, tok.token)
            status = ChattyCallStatus.REQUESTING_MIC
            try {
                r.localParticipant.setMicrophoneEnabled(true)
            } catch (micErr: Exception) {
                errorMessage = "Microphone access is required for voice calls. Please allow microphone access and try again."
                status = ChattyCallStatus.ERROR
                r.disconnect()
                return@LaunchedEffect
            }
            status = ChattyCallStatus.CONNECTED
        } catch (e: Exception) {
            errorMessage = e.message ?: "Couldn't start the call, please try again."
            status = ChattyCallStatus.ERROR
        }
    }

    // Tears the room down when this screen leaves composition (back press,
    // navigating away) — not just on the explicit hangup button.
    DisposableEffect(Unit) {
        onDispose {
            room?.let {
                scope.launch { runCatching { it.localParticipant.setMicrophoneEnabled(false) } }
                it.disconnect()
            }
        }
    }

    LaunchedEffect(status) {
        if (status == ChattyCallStatus.CONNECTING || status == ChattyCallStatus.REQUESTING_MIC || status == ChattyCallStatus.ERROR || status == ChattyCallStatus.ENDED) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            duration++
        }
    }

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.size - 1)
    }

    fun hangup() {
        room?.let {
            scope.launch { runCatching { it.localParticipant.setMicrophoneEnabled(false) } }
            it.disconnect()
        }
        room = null
        onClose()
    }

    fun fmt(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

    Box(modifier = Modifier.fillMaxSize().background(t.containerBg)) {
        when (status) {
            ChattyCallStatus.ERROR -> ChattyCallEndState(
                icon = Icons.Filled.Warning, iconBg = Color(0xFFFEE2E2), iconTint = Color(0xFFEF4444),
                title = errorMessage ?: "Something went wrong", subtitle = null, t = t, buttonLabel = "Close", onButtonClick = onClose,
            )
            ChattyCallStatus.ENDED -> ChattyCallEndState(
                icon = Icons.Filled.CallEnd, iconBg = t.userBubbleBg.copy(alpha = 0.12f), iconTint = t.userBubbleBg,
                title = "Call ended", subtitle = fmt(duration), t = t, buttonLabel = "Back to chat", onButtonClick = onClose,
            )
            else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val statusLabel = when (status) {
                    ChattyCallStatus.CONNECTING -> "Connecting…"
                    ChattyCallStatus.REQUESTING_MIC -> "Please allow microphone access…"
                    ChattyCallStatus.CONNECTED -> fmt(duration)
                    ChattyCallStatus.LISTENING -> "Listening…"
                    ChattyCallStatus.AGENT_SPEAKING -> "Speaking…"
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    ChattyCallOrb(active = status == ChattyCallStatus.AGENT_SPEAKING, color = t.userBubbleBg)
                    Spacer(modifier = Modifier.width(12.dp))
                    if (status == ChattyCallStatus.CONNECTING || status == ChattyCallStatus.REQUESTING_MIC) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = t.headerText.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(statusLabel, color = t.headerText.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (transcript.isEmpty()) {
                        Text(
                            "Say something — your conversation will appear here.",
                            color = t.headerText.copy(alpha = 0.4f), fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(transcript, key = { it.id }) { entry ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (entry.fromVisitor) Arrangement.End else Arrangement.Start) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (entry.fromVisitor) t.userBubbleBg else t.botBubbleBg,
                                                RoundedCornerShape(
                                                    topStart = 16.dp, topEnd = 16.dp,
                                                    bottomStart = if (entry.fromVisitor) 16.dp else 4.dp,
                                                    bottomEnd = if (entry.fromVisitor) 4.dp else 16.dp,
                                                ),
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(entry.text.ifBlank { "…" }, color = if (entry.fromVisitor) t.userBubbleText else t.botBubbleText, fontSize = 13.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val r = room ?: return@IconButton
                            val next = !muted
                            scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(!next) } }
                            muted = next
                        },
                        enabled = status != ChattyCallStatus.CONNECTING && status != ChattyCallStatus.REQUESTING_MIC,
                        modifier = Modifier.size(52.dp).clip(CircleShape).border(1.dp, t.headerText.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = if (muted) "Unmute" else "Mute", tint = t.headerText)
                    }
                    IconButton(
                        onClick = { hangup() },
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFEF4444)),
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChattyCallOrb(active: Boolean, color: Color) {
    val infinite = rememberInfiniteTransition(label = "chatty-call-orb")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.15f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 320 else 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chatty-call-orb-scale",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
    }
}

@Composable
private fun ChattyCallEndState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String?,
    t: ChattyDesignTokens,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = t.headerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = t.headerText.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onButtonClick, colors = ButtonDefaults.buttonColors(containerColor = t.userBubbleBg)) {
            Text(buttonLabel, color = t.userBubbleText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
