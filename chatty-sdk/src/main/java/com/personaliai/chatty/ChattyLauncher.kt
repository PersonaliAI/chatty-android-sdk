package com.personaliai.chatty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Floating launcher button + full-screen dialog chat panel — the native-SDK
 * equivalent of widget.js's launcher button + iframe panel. The button color
 * follows the selected design's own accent (same as web's LAUNCHER_STYLES)
 * unless [color] is explicitly passed, which always wins.
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
    var designAccent by remember { mutableStateOf(Color(0xFFF97316)) }

    LaunchedEffect(botId) {
        try {
            val theme = ChattyClient(botId, baseUrl, host).getTheme()
            val designId = chattyNormalizeWidgetStyle(theme.widgetStyle)
            designAccent = (chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")).userBubbleBg
        } catch (_: Exception) {
            // keep the fallback accent — a failed theme fetch shouldn't block the button from rendering
        }
    }
    val resolvedColor = color ?: designAccent

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(position.alignment).padding(20.dp)) {
            FloatingActionButton(
                onClick = { open = true; unread = 0 },
                containerColor = resolvedColor,
                shape = CircleShape,
            ) {
                Text("💬", fontSize = 22.sp)
            }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (unread > 9) "9+" else unread.toString(), color = Color.White, fontSize = 10.sp)
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
