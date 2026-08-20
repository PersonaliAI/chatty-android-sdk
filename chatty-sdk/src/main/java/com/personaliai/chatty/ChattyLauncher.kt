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
) {
    var open by remember { mutableStateOf(false) }
    var unread by remember { mutableStateOf(0) }
    var designId by remember { mutableStateOf("minimal") }

    LaunchedEffect(botId) {
        try {
            val theme = ChattyClient(botId, baseUrl, host).getTheme()
            designId = chattyNormalizeWidgetStyle(theme.widgetStyle)
        } catch (_: Exception) {
            // keep the fallback design — a failed theme fetch shouldn't block the button from rendering
        }
    }
    val tokens = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
    val resolvedColor = color ?: tokens.launcherBg
    val isGradient = color == null && designId == "gradient-glow"

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(position.alignment).padding(20.dp)) {
            Box(
                Modifier
                    .size(60.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = tokens.launcherShadow, spotColor = tokens.launcherShadow)
                    .clip(CircleShape)
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
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "✕",
                            modifier = Modifier.padding(8.dp).clickable { open = false },
                            color = Color(0xFF6B7280),
                            fontSize = 18.sp,
                        )
                    }
                    ChattyChatScreen(
                        botId = botId,
                        baseUrl = baseUrl,
                        host = host,
                        modifier = Modifier.weight(1f),
                        onMessage = { if (!open) unread++ },
                    )
                }
            }
        }
    }
}

enum class ChattyPosition(val alignment: Alignment) {
    BOTTOM_START(Alignment.BottomStart),
    BOTTOM_END(Alignment.BottomEnd),
}
