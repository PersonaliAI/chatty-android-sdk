package com.personaliai.chatty

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

/**
 * Full Chatty chat screen: header (with voice/notification/clear-chat actions),
 * message list, conversation starters, typing indicator, and composer (emoji
 * picker, attach menu, mic recording). Equivalent to the web widget's embed
 * iframe content — sizing/spacing/structure below is ported 1:1 from
 * EmbedClient.tsx + globals.css + widget.js.
 */
@Composable
fun ChattyChatScreen(
    botId: String,
    baseUrl: String = CHATTY_DEFAULT_BASE_URL,
    host: String? = null,
    hostKey: String = "app",
    modifier: Modifier = Modifier,
    onMessage: ((ChattyMessage) -> Unit)? = null,
    /** Called when the header's voice-call button is tapped (only shown when the bot's
     * dashboard has voice enabled). This SDK doesn't bundle a voice-call implementation
     * (that's a separate LiveKit integration) — wire this up if your app has one. */
    onVoiceCallPress: (() -> Unit)? = null,
    /** Called when the header's notification-bell button is tapped, after the OS
     * notification-permission prompt (Android 13+) has been resolved either way. Native apps
     * still need their own push infrastructure (FCM/APNs, or a wrapper like OneSignal) to
     * actually *deliver* a notification when a reply arrives while the app is backgrounded —
     * this SDK only handles the local permission ask, not registration/delivery. */
    onNotificationBellPress: (() -> Unit)? = null,
    /** Renders a close (✕) button in the header when provided — pass this instead of drawing
     * your own close bar above [ChattyChatScreen] (e.g. in a dialog/sheet wrapper), so there's
     * one header, not two stacked ones. [ChattyLauncher] already does this for you. */
    onClose: (() -> Unit)? = null,
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

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val tempFile = File(context.cacheDir, "upload_image_${System.currentTimeMillis()}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.sendImage(tempFile, mimeType, "")
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            viewModel.sendImage(tempFile, "image/jpeg", "")
        }
    }

    fun startRecording() {
        val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recordingFile = file
            recordingSeconds = 0
            isRecording = true
        } catch (_: Exception) {
            // mic busy/unavailable — silently no-op rather than crash the composer
        }
    }

    fun stopRecordingAndTranscribe() {
        isRecording = false
        val recorder = mediaRecorder
        val file = recordingFile
        mediaRecorder = null
        recordingFile = null
        if (recorder == null || file == null) return
        try {
            recorder.stop()
        } catch (_: Exception) {
            // too-short recording etc — just skip transcription
        } finally {
            recorder.release()
        }
        if (recordingSeconds >= 1) {
            viewModel.transcribeVoiceNote(file, "audio/mp4") { text ->
                if (!text.isNullOrBlank()) input = if (input.isBlank()) text else "$input $text"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    // Android 13+ requires a runtime prompt for POST_NOTIFICATIONS; older versions grant it
    // at install time, so there's nothing to ask there (treated as always-granted below).
    fun hasNotificationPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // The bell icon needs to reflect grant state, so it has to be real Compose state — a plain
    // checkSelfPermission() call inside the click handler (the old code) never triggers a
    // recomposition, so the icon stayed on its initial glyph forever regardless of what the
    // user actually granted. Re-checked on ON_RESUME too, since granting/revoking via the
    // system dialog or the app's Settings page both resume this screen rather than recreating
    // it, and neither goes through notificationPermissionLauncher's callback.
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (granted) onNotificationBellPress?.invoke()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = hasNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun onBellPress() {
        if (notificationsGranted) {
            onNotificationBellPress?.invoke()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingSeconds++
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
            val logoBg = chattyLogoBgColor(state.theme?.widgetStyle) ?: t.headerText.copy(alpha = 0.08f)
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(logoBg),
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
            Column(Modifier.weight(1f)) {
                Text(
                    state.theme?.name ?: "Chatty Assistant",
                    color = t.headerText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChattyPulsingDot(color = Color(0xFF22C55E))
                    Spacer(Modifier.width(4.dp))
                    Text("Online · replies instantly", color = t.headerText.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
            // Header action buttons — voice call (if enabled), notification bell, clear chat, close.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.theme?.voiceEnabled == true) {
                    HeaderIconButton(Icons.Filled.Call, "Voice call", t.headerText) { onVoiceCallPress?.invoke() }
                }
                HeaderIconButton(
                    if (notificationsGranted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                    if (notificationsGranted) "Notifications" else "Enable notifications",
                    t.headerText,
                ) { onBellPress() }
                HeaderIconButton(Icons.Filled.RestartAlt, "Clear chat", t.headerText) { viewModel.clearChat() }
                if (onClose != null) {
                    HeaderIconButton(Icons.Filled.Close, "Close", t.headerText, onClose)
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
        // icon row (emoji + attach + mic + send) below, matching .chat-input-bar on web.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(t.containerBg)
                .border(1.dp, t.headerText.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(12.dp, 10.dp, 12.dp, 6.dp),
        ) {
            AnimatedVisibility(visible = showEmojiPicker, enter = fadeIn() + scaleIn(initialScale = 0.85f) + expandVertically(), exit = fadeOut() + scaleOut(targetScale = 0.85f) + shrinkVertically()) {
                Box(Modifier.padding(bottom = 8.dp)) {
                    EmojiPicker(onPick = { emoji -> input += emoji })
                }
            }
            AnimatedVisibility(visible = showAttachMenu, enter = fadeIn() + scaleIn(initialScale = 0.85f) + expandVertically(), exit = fadeOut() + scaleOut(targetScale = 0.85f) + shrinkVertically()) {
                Box(Modifier.padding(bottom = 8.dp)) {
                AttachMenu(
                    onCamera = { showAttachMenu = false; cameraLauncher.launch(null) },
                    onPhotoLibrary = { showAttachMenu = false; imagePicker.launch("image/*") },
                )
                }
            }

            if (isRecording) {
                RecordingIndicator(seconds = recordingSeconds, onStop = { stopRecordingAndTranscribe() })
            } else {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type a message…", fontSize = 13.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = t.botBubbleText),
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
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker; showAttachMenu = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showAttachMenu = !showAttachMenu; showEmojiPicker = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                stopRecordingAndTranscribe()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Record voice note",
                            tint = if (isRecording) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
                            showEmojiPicker = false
                            showAttachMenu = false
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
private fun HeaderIconButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

private val CHATTY_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "🙃", "😉", "😊", "😇",
    "🥰", "😍", "🤩", "😘", "😋", "😛", "🤪", "😜", "🤔", "🤨", "😐", "😑",
    "😶", "🙄", "😏", "😒", "😬", "🙁", "😢", "😭", "😤", "😡", "🥳", "😴",
    "🤗", "🤝", "👍", "👎", "👏", "🙌", "🙏", "💪", "👋", "✌️", "🤞", "❤️",
    "🔥", "✨", "🎉", "🎊", "⭐", "💯", "✅", "❌", "❓", "❗", "💬", "👀",
)

@Composable
private fun EmojiPicker(onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(CHATTY_EMOJIS) { emoji ->
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun AttachMenu(onCamera: () -> Unit, onPhotoLibrary: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AttachMenuOption(Icons.Filled.PhotoCamera, "Camera", onCamera, Modifier.weight(1f))
        AttachMenuOption(Icons.Filled.PhotoLibrary, "Photo Library", onPhotoLibrary, Modifier.weight(1f))
    }
}

@Composable
private fun AttachMenuOption(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF4B5563), modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF4B5563))
    }
}

@Composable
private fun RecordingIndicator(seconds: Int, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChattyPulsingDot(color = Color(0xFFEF4444), size = 8.dp)
            Spacer(Modifier.width(8.dp))
            val m = seconds / 60
            val s = seconds % 60
            Text("Recording… %d:%02d".format(m, s), fontSize = 13.sp, color = Color(0xFF6B7280))
        }
        TextButton(onClick = onStop) { Text("Stop", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
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
            Text("Send", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                Text(message.text, color = t.userBubbleText, fontSize = 13.sp)
                            } else {
                                MarkdownText(message.text, color = t.botBubbleText, fontSize = 13.sp)
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
                Text(s, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
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
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF111827), fontSize: TextUnit = 13.sp) {
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
