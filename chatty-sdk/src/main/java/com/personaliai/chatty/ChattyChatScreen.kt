package com.personaliai.chatty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.AttachFile
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
 * indicator, and composer. Equivalent to the web widget's embed iframe content.
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
        val headerModifier = if (designId == "gradient-glow") {
            Modifier.fillMaxWidth().background(Brush.linearGradient(ChattyGradientGlowHeaderColors)).padding(16.dp, 14.dp)
        } else {
            Modifier.fillMaxWidth().background(t.headerBg).padding(16.dp, 14.dp)
        }
        Row(
            headerModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.theme?.logoUrl?.let {
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(8.dp))
            }
            Text(state.theme?.name ?: "Chat", color = t.headerText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages, key = { it.id }) { msg -> Bubble(msg, t) }
            if (state.sending) {
                item { TypingBubble(t, accent) }
            }
        }

        if (state.theme?.conversationStarters?.isNotEmpty() == true && state.messages.size <= 1) {
            FlowStarters(state.theme!!.conversationStarters, t.userBubbleBg) { viewModel.sendText(it) }
        }

        if (state.aiPaused) Banner("A human agent has taken over this conversation.", Color(0xFFFEF3C7))
        state.error?.let { Banner(it, Color(0xFFFEE2E2)) }

        Row(
            Modifier.fillMaxWidth().padding(10.dp, 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.padding(bottom = 4.dp).size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF3F4F6))
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach image", tint = Color(0xFF6B7280))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendText(input)
                        input = ""
                    }
                },
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(accent),
            ) {
                Text("↑", color = t.userBubbleText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Bubble(message: ChattyMessage, t: ChattyDesignTokens) {
    val isUser = message.role == ChattyRole.USER
    val radius = if (isUser) t.userBubbleRadius else t.botBubbleRadius
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(radius.dp))
                .background(if (isUser) t.userBubbleBg else t.botBubbleBg)
                .padding(14.dp, 10.dp),
        ) {
            Column {
                message.fileUrl?.let {
                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(160.dp, 120.dp).clip(RoundedCornerShape(10.dp)))
                }
                if (message.text.isNotEmpty()) {
                    if (isUser) {
                        Text(message.text, color = t.userBubbleText, fontSize = 14.sp)
                    } else {
                        MarkdownText(message.text, color = t.botBubbleText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(t: ChattyDesignTokens, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(Modifier.clip(RoundedCornerShape(t.botBubbleRadius.dp)).background(t.botBubbleBg).padding(14.dp, 10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
        }
    }
}

@Composable
private fun FlowStarters(starters: List<String>, accent: Color, onClick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp, 0.dp, 12.dp, 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starters.forEach { s ->
            OutlinedButton(
                onClick = { onClick(s) },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) {
                Text(s, fontSize = 12.5.sp, maxLines = 2)
            }
        }
    }
}



@Composable
private fun Banner(text: String, bg: Color) {
    Box(Modifier.fillMaxWidth().background(bg).padding(14.dp, 8.dp)) {
        Text(text, fontSize = 11.5.sp, color = Color(0xFF374151))
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF111827), fontSize: TextUnit = 14.sp) {
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
