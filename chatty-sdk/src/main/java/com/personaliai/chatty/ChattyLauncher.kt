package com.personaliai.chatty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Floating launcher button + full-screen dialog chat panel — the native-SDK
 * equivalent of widget.js's launcher button + iframe panel. 60dp (widget.js:
 * 60x60px), button color/shadow follow the selected design's own
 * LAUNCHER_STYLES entry unless [color] is explicitly passed, which always wins.
 */
@Composable
fun ChattyLauncher(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    position: ChattyPosition = ChattyPosition.BOTTOM_END,
    color: Color? = null,
    /** Forwarded to [ChattyChatScreen]'s onVoiceCallPress — see its doc for details. */
    onVoiceCallPress: (() -> Unit)? = null,
    /** Forwarded to [ChattyChatScreen]'s onNotificationBellPress — see its doc for details. */
    onNotificationBellPress: (() -> Unit)? = null,
    /** Forwarded to [ChattyChatScreen]'s enableVoiceNotes — see its doc for details. */
    enableVoiceNotes: Boolean = true,
    /** Forwarded to [ChattyChatScreen]'s enableNotificationBell — see its doc for details. */
    enableNotificationBell: Boolean = true,
    /** Forwarded to [ChattyChatScreen]'s enableLocationSharing — see its doc for details. */
    enableLocationSharing: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var unread by remember { mutableStateOf(0) }
    var designId by remember { mutableStateOf("minimal") }
    var rawWidgetStyle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(botId) {
        try {
            val theme = ChattyClient(botId, baseUrl, host).getTheme()
            designId = chattyNormalizeWidgetStyle(theme.widgetStyle)
            rawWidgetStyle = theme.widgetStyle
        } catch (_: Exception) {
            // keep the fallback design — a failed theme fetch shouldn't block the button from rendering
        }
    }
    val tokens = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
    val resolvedColor = color ?: tokens.launcherBg
    val isGradient = color == null && designId == "gradient-glow"
    val launcherShape = chattyLauncherShape(rawWidgetStyle, position)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(position.alignment).padding(20.dp)) {
            Box(
                Modifier
                    .size(60.dp)
                    .shadow(elevation = 10.dp, shape = launcherShape, ambientColor = tokens.launcherShadow, spotColor = tokens.launcherShadow)
                    .clip(launcherShape)
                    .then(
                        if (isGradient) Modifier.background(Brush.linearGradient(ChattyGradientGlowHeaderColors))
                        else Modifier.background(resolvedColor)
                    )
                    .clickable { open = true; unread = 0 },
                contentAlignment = Alignment.Center,
            ) {
                Text("💬", fontSize = 24.sp)
            }
            if (unread > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (unread > 9) "9+" else unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // Close lives in ChattyChatScreen's own header (onClose) — no separate close bar
            // drawn here, which used to stack a second, redundant header above it.
            // statusBarsPadding keeps the header clear of the status bar on edge-to-edge hosts;
            // a no-op otherwise.
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                ChattyChatScreen(
                    botId = botId,
                    baseUrl = baseUrl,
                    host = host,
                    modifier = Modifier.fillMaxSize(),
                    onMessage = { if (!open) unread++ },
                    onClose = { open = false },
                    onVoiceCallPress = onVoiceCallPress,
                    onNotificationBellPress = onNotificationBellPress,
                    enableVoiceNotes = enableVoiceNotes,
                    enableNotificationBell = enableNotificationBell,
                    enableLocationSharing = enableLocationSharing,
                )
            }
        }
    }
}

enum class ChattyPosition(val alignment: Alignment) {
    BOTTOM_START(Alignment.BottomStart),
    BOTTOM_END(Alignment.BottomEnd),
}
