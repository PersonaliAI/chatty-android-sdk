package com.personaliai.chatty

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

/**
 * Full Chatty chat screen: header, message list, conversation starters, typing
 * indicator, and composer. Equivalent to the web widget's embed iframe content —
 * sizing/spacing/structure below is ported 1:1 from EmbedClient.tsx + globals.css.
 */
@Composable
fun ChattyChatScreen(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    hostKey: String = "app",
    modifier: Modifier = Modifier,
    onMessage: ((ChattyMessage) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ChattyViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ChattyViewModel(
                    context.applicationContext as android.app.Application,
                    botId, baseUrl, host, hostKey, onMessage = onMessage,
                ) as T
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val designId = chattyNormalizeWidgetStyle(state.theme?.widgetStyle)
    val t = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
    // Every design's .send-btn background matches its .user-bubble background
    // on web — reuse that as the "accent" for the send button and spinners.
    val accent = t.userBubbleBg
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val tempFile = java.io.File(context.cacheDir, "upload_image.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.sendImage(tempFile, mimeType, "")
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    if (!state.ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent)
        }
        return
    }

    Column(modifier.fillMaxSize().background(t.containerBg)) {
        // Header: px-4 pt-3 pb-2 on web -> 16dp horizontal, 12dp top, 8dp bottom.
        val headerModifier = if (designId == "gradient-glow") {
            Modifier.fillMaxWidth().background(Brush.linearGradient(ChattyGradientGlowHeaderColors)).padding(16.dp, 12.dp, 16.dp, 8.dp)
        } else {
            Modifier.fillMaxWidth().background(t.headerBg).padding(16.dp, 12.dp, 16.dp, 8.dp)
        }
        Row(headerModifier, verticalAlignment = Alignment.CenterVertically) {
            // 44dp avatar circle (web: size-11), logo image at 34dp if set, else an icon at 24dp.
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(t.headerText.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                val logoUrl = state.theme?.logoUrl
                if (!logoUrl.isNullOrEmpty()) {
                    AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape))
                } else {
                    Icon(
                        chattyAvatarIconVector(state.theme?.avatarIcon),
                        contentDescription = null,
                        tint = t.headerText,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    state.theme?.name ?: "Chatty Assistant",
                    color = t.headerText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChattyPulsingDot(color = Color(0xFF22C55E))
                    Spacer(Modifier.width(4.dp))
                    Text("Online · replies instantly", color = t.headerText.copy(alpha = 0.7f), fontSize = 9.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // p-4 space-y-4 on web -> 16dp padding, 16dp gap between rows.
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                Bubble(msg, t, state.theme?.avatarIcon, state.theme?.avatarUrl)
            }
            if (state.sending) {
                item { TypingBubble(t, state.theme?.avatarIcon, state.theme?.avatarUrl) }
            }
        }

        if (state.theme?.conversationStarters?.isNotEmpty() == true && state.messages.size <= 1) {
            FlowStarters(state.theme!!.conversationStarters, t.userBubbleBg) { viewModel.sendText(it) }
        }

        if (state.aiPaused) Banner("A human agent has taken over this conversation.", Color(0xFFFEF3C7))
        state.error?.let { Banner(it, Color(0xFFFEE2E2)) }

        // Composer: bordered rounded-2xl bar with the text field on top and an
        // icon row (attach + send) below, matching .chat-input-bar on web.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(t.containerBg)
                .border(1.dp, t.headerText.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(12.dp, 10.dp, 12.dp, 6.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type a message…", fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = t.botBubbleText),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach image", tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                }
                ChattySendButton(
                    style = state.theme?.sendButtonStyle,
                    accent = accent,
                    textColor = t.userBubbleText,
                    enabled = input.isNotBlank(),
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.sendText(input)
                            input = ""
                        }
                    },
                )
            }
        }
    }
}

private fun chattyAvatarIconVector(avatarIcon: String?): ImageVector = when (avatarIcon) {
    "headset" -> Icons.Filled.SupportAgent
    "sparkles" -> Icons.Filled.AutoAwesome
    "message" -> Icons.Filled.ChatBubble
    "user" -> Icons.Filled.Person
    else -> Icons.Filled.SmartToy // "logo" (no logoUrl), "bot", "custom" (no avatarUrl), or null
}

@Composable
private fun ChattyPulsingDot(color: Color, size: androidx.compose.ui.unit.Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "chattyPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chattyPulseAlpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
private fun BotAvatar(avatarIcon: String?, avatarUrl: String?, t: ChattyDesignTokens) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(t.botBubbleBg),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarIcon == "custom" && !avatarUrl.isNullOrEmpty()) {
            AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
        } else {
            Icon(chattyAvatarIconVector(avatarIcon), contentDescription = null, tint = t.botBubbleText, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ChattySendButton(style: String?, accent: Color, textColor: Color, enabled: Boolean, onClick: () -> Unit) {
    when (style) {
        "square" -> IconButton(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = textColor, modifier = Modifier.size(16.dp))
        }
        "label" -> Button(
            onClick = onClick, enabled = enabled,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = textColor),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Send", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        "arrowUp" -> ChattyRoundSendButton(Icons.Filled.ArrowUpward, accent, textColor, enabled, onClick)
        "arrowRight" -> ChattyRoundSendButton(Icons.Filled.ArrowForward, accent, textColor, enabled, onClick)
        else -> ChattyRoundSendButton(Icons.Filled.Send, accent, textColor, enabled, onClick) // "plane" default
    }
}

@Composable
private fun ChattyRoundSendButton(icon: ImageVector, accent: Color, textColor: Color, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
    ) {
        Icon(icon, contentDescription = "Send", tint = textColor, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Bubble(message: ChattyMessage, t: ChattyDesignTokens, avatarIcon: String?, avatarUrl: String?) {
    val isUser = message.role == ChattyRole.USER
    val radius = if (isUser) t.userBubbleRadius else t.botBubbleRadius
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // max-w-[88%] on web.
        val maxRowWidth = maxWidth * 0.88f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
            Row(
                Modifier.widthIn(max = maxRowWidth),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!isUser) BotAvatar(avatarIcon, avatarUrl, t)
                Box(
                    Modifier
                        .clip(chattyBubbleShape(radius, isUser))
                        .background(if (isUser) t.userBubbleBg else t.botBubbleBg)
                        .padding(10.dp), // p-2.5 on web
                ) {
                    Column {
                        message.fileUrl?.let {
                            AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(10.dp)))
                        }
                        if (message.text.isNotEmpty()) {
                            if (isUser) {
                                Text(message.text, color = t.userBubbleText, fontSize = 12.sp)
                            } else {
                                MarkdownText(message.text, color = t.botBubbleText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(t: ChattyDesignTokens, avatarIcon: String?, avatarUrl: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            BotAvatar(avatarIcon, avatarUrl, t)
            Box(Modifier.clip(chattyBubbleShape(t.botBubbleRadius, false)).background(t.botBubbleBg).padding(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        val transition = rememberInfiniteTransition(label = "chattyTyping$i")
                        val offset by transition.animateFloat(
                            initialValue = 0f, targetValue = -4f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = i * 150),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "chattyTypingOffset$i",
                        )
                        Box(
                            Modifier
                                .size(6.dp)
                                .offset(y = offset.dp)
                                .clip(CircleShape)
                                .background(t.botBubbleText.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowStarters(starters: List<String>, accent: Color, onClick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starters.forEach { s ->
            OutlinedButton(
                onClick = { onClick(s) },
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) {
                Text(s, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Banner(text: String, bg: Color) {
    Box(Modifier.fillMaxWidth().background(bg).padding(16.dp, 8.dp)) {
        Text(text, fontSize = 11.sp, color = Color(0xFF374151))
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF111827), fontSize: TextUnit = 12.sp) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            var currentLine = line
            if (currentLine.startsWith("### ")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize * 1.2f, color = color)) {
                    appendInlineMarkdown(currentLine.substring(4), color)
                }
            } else if (currentLine.startsWith("- ")) {
                append("• ")
                appendInlineMarkdown(currentLine.substring(2), color)
            } else {
                appendInlineMarkdown(currentLine, color)
            }
            if (index < lines.size - 1) append("\n")
        }
    }
    ClickableText(
        text = annotatedString,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}

fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(text: String, defaultColor: Color) {
    val pattern = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|`(.*?)`|\\[(.*?)\\]\\((.*?)\\)")
    var currentIndex = 0
    pattern.findAll(text).forEach { matchResult ->
        val startIndex = matchResult.range.first
        val endIndex = matchResult.range.last + 1

        if (startIndex > currentIndex) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(text.substring(currentIndex, startIndex))
            }
        }

        when {
            matchResult.groups[1] != null -> { // **bold**
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                    append(matchResult.groups[1]!!.value)
                }
            }
            matchResult.groups[2] != null -> { // *italic*
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                    append(matchResult.groups[2]!!.value)
                }
            }
            matchResult.groups[3] != null -> { // `code`
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFE5E7EB), color = defaultColor)) {
                    append(matchResult.groups[3]!!.value)
                }
            }
            matchResult.groups[4] != null -> { // [text](url)
                val linkText = matchResult.groups[4]!!.value
                val url = matchResult.groups[5]!!.value
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
                pop()
            }
        }
        currentIndex = endIndex
    }
    if (currentIndex < text.length) {
        withStyle(SpanStyle(color = defaultColor)) {
            append(text.substring(currentIndex))
        }
    }
}
