package com.personaliai.chatty

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.text.style.TextOverflow
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
    /** Called when the header's notification-bell button is tapped (only shown once
     * POST_NOTIFICATIONS is already granted — see [enableNotificationBell]). Native apps
     * still need their own push infrastructure (FCM/APNs, or a wrapper like OneSignal) to
     * actually *deliver* a notification when a reply arrives while the app is backgrounded —
     * this SDK only reflects the permission state, not registration/delivery. */
    onNotificationBellPress: (() -> Unit)? = null,
    /** Renders a close (✕) button in the header when provided — pass this instead of drawing
     * your own close bar above [ChattyChatScreen] (e.g. in a dialog/sheet wrapper), so there's
     * one header, not two stacked ones. [ChattyLauncher] already does this for you. */
    onClose: (() -> Unit)? = null,
    /** Gates the composer's mic button on RECORD_AUDIO (a dangerous permission) already being
     * granted — the SDK never requests it itself. Your app owns asking for it (its own
     * `rememberLauncherForActivityResult(RequestPermission())` elsewhere, however/whenever you
     * choose); the button appears automatically once granted (re-checked on resume) and stays
     * hidden otherwise, so there's no silently-failing button and no surprise system dialog
     * triggered from inside the SDK. Set false to hide the button regardless of permission
     * state. The manifest still declares RECORD_AUDIO (so it's available for your own request);
     * to drop it from your app's own permission list entirely, add
     * `<uses-permission android:name="android.permission.RECORD_AUDIO" tools:node="remove" />`
     * to your app's AndroidManifest.xml. See the SDK README's Permissions section. */
    enableVoiceNotes: Boolean = true,
    /** Gates the header's notification-bell button on POST_NOTIFICATIONS (Android 13+) already
     * being granted — the SDK never requests it itself; see [enableVoiceNotes] for the same
     * request-ownership contract. Pre-13 devices grant this at install time, so the button
     * always shows there. Set false to hide the button regardless of permission state. See the
     * SDK README's Permissions section. */
    enableNotificationBell: Boolean = true,
    /** Gates the attach menu's "Location" option on ACCESS_COARSE_LOCATION already being
     * granted — the SDK never requests it itself; see [enableVoiceNotes] for the same
     * request-ownership contract. Once granted, tapping it matches the web widget's behavior:
     * drops a Google Maps link for the current fix into the composer text (not a special
     * message type, and not sent automatically — the user still taps send). Set false to hide
     * the option regardless of permission state. See the SDK README's Permissions section. */
    enableLocationSharing: Boolean = true,
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
    val cs = state.theme?.colorScheme
    // The dashboard's per-section color overrides (color_scheme) win over the
    // design preset's own colors wherever a section's bg is set — mirrors
    // buildColorSchemeCss's !important rules on web exactly. Merging into `t`
    // here means every downstream read of `t.headerBg`/`t.botBubbleBg`/etc.
    // (including Bubble/TypingBubble, which never see color_scheme directly)
    // picks up the override for free.
    val t = run {
        val base = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
        base.copy(
            headerBg = chattyParseColor(cs?.header?.bg) ?: base.headerBg,
            headerText = chattyParseColor(cs?.header?.text) ?: base.headerText,
            botBubbleBg = chattyParseColor(cs?.botBubble?.bg) ?: base.botBubbleBg,
            botBubbleText = chattyParseColor(cs?.botBubble?.text) ?: base.botBubbleText,
            userBubbleBg = chattyParseColor(cs?.userBubble?.bg) ?: base.userBubbleBg,
            userBubbleText = chattyParseColor(cs?.userBubble?.text) ?: base.userBubbleText,
        )
    }
    // Every design's .send-btn background matches its .user-bubble background
    // on web — reuse that as the "accent" for the send button and spinners,
    // unless color_scheme.sendBtn overrides it independently (below).
    val accent = t.userBubbleBg
    val sendBtnBg = chattyParseColor(cs?.sendBtn?.bg) ?: accent
    val sendBtnText = chattyParseColor(cs?.sendBtn?.text) ?: t.userBubbleText
    val inputBarBg = chattyParseColor(cs?.inputBar?.bg) ?: t.containerBg
    val inputBarText = chattyParseColor(cs?.inputBar?.text) ?: t.botBubbleText
    val inputBarIconTint = chattyParseColor(cs?.inputBar?.icon)
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
    // OpenDocument (SAF) rather than GetContent: needs a mime-type *array* to
    // offer more than one file type in the system picker, and — unlike
    // GetContent — grants a persistable read URI without requiring any
    // storage/media runtime permission.
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val tempFile = File(context.cacheDir, "upload_doc_${System.currentTimeMillis()}.tmp")
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

    // Dangerous-permission policy: this SDK never triggers a system permission
    // dialog itself — it only reflects whatever the host app has already
    // granted (or not). Each gated button/option below simply doesn't render
    // when its permission is missing, rather than showing something that
    // would either silently no-op or have to request it internally. The host
    // app owns if/when/how to actually ask (its own
    // rememberLauncherForActivityResult elsewhere) — see enableVoiceNotes/
    // enableNotificationBell/enableLocationSharing's doc comments and the
    // README's Permissions section.
    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    // Android 13+ requires a runtime prompt for POST_NOTIFICATIONS; older versions grant it
    // at install time, so there's nothing to ask there (treated as always-granted below).
    fun hasNotificationPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Each gated button needs to reflect grant state as real Compose state — a plain
    // checkSelfPermission() call at render time wouldn't trigger a recomposition when the host
    // app requests the permission itself elsewhere, so the button would stay hidden (or shown)
    // forever regardless of what actually got granted afterward. Re-checked on ON_RESUME, since
    // granting via the system dialog or the app's Settings page both resume this screen.
    var micPermissionGranted by remember { mutableStateOf(hasMicPermission()) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission()) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micPermissionGranted = hasMicPermission()
                notificationsGranted = hasNotificationPermission()
                locationPermissionGranted = hasLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun onBellPress() {
        onNotificationBellPress?.invoke()
    }

    // Matches the web widget exactly: drops a Google Maps link into the
    // composer text rather than sending a special "location message" type —
    // the user still has to tap send. android.location's LocationManager
    // (framework API) rather than Play Services' FusedLocationProviderClient
    // — this SDK stays free of a Play Services dependency for one feature.
    fun insertLocationLink(location: android.location.Location) {
        val link = "https://www.google.com/maps?q=${location.latitude},${location.longitude}"
        input = if (input.isBlank()) "📍 My location: $link" else "$input 📍 $link"
    }
    fun fetchAndShareLocation() {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val provider = when {
            locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true -> android.location.LocationManager.GPS_PROVIDER
            locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true -> android.location.LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (locationManager == null || provider == null) return
        try {
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null) {
                insertLocationLink(last)
            } else {
                locationManager.requestSingleUpdate(provider, object : android.location.LocationListener {
                    override fun onLocationChanged(loc: android.location.Location) = insertLocationLink(loc)
                }, android.os.Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            // permission revoked between the button rendering and this call — no-op
        }
    }
    fun onLocationPress() {
        showAttachMenu = false
        fetchAndShareLocation()
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

    // statusBarsPadding keeps the header clear of the status bar on edge-to-edge
    // hosts (a no-op otherwise) — applied here rather than by each caller so a
    // direct full-screen embed and ChattyLauncher's Dialog panel both get it
    // right automatically instead of depending on the caller to remember it.
    Column(modifier.fillMaxSize().background(t.containerBg).statusBarsPadding()) {
        // Header: px-4 pt-3 pb-2 on web -> 16dp horizontal, 12dp top, 8dp bottom.
        // A color_scheme.header override wins even over gradient-glow's own
        // gradient — matches buildColorSchemeCss's !important solid-color rule.
        val headerModifier = if (designId == "gradient-glow" && cs?.header?.bg == null) {
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
                // Only rendered once POST_NOTIFICATIONS is already granted — see
                // enableNotificationBell's doc comment; the SDK never requests it itself.
                if (enableNotificationBell && notificationsGranted) {
                    HeaderIconButton(Icons.Filled.Notifications, "Notifications", t.headerText) { onBellPress() }
                }
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
                Bubble(msg, t, state.theme?.avatarIcon, state.theme?.avatarUrl, state.theme?.showSenderTag == true)
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
                .background(inputBarBg)
                .border(1.dp, t.headerText.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(12.dp, 10.dp, 12.dp, 6.dp),
        ) {
            AnimatedVisibility(visible = showEmojiPicker, enter = fadeIn() + scaleIn(initialScale = 0.85f) + expandVertically(), exit = fadeOut() + scaleOut(targetScale = 0.85f) + shrinkVertically()) {
                Box(Modifier.padding(bottom = 8.dp)) {
                    EmojiPicker(borderColor = t.headerText.copy(alpha = 0.12f), onPick = { emoji -> input += emoji })
                }
            }
            AnimatedVisibility(visible = showAttachMenu, enter = fadeIn() + scaleIn(initialScale = 0.85f) + expandVertically(), exit = fadeOut() + scaleOut(targetScale = 0.85f) + shrinkVertically()) {
                Box(Modifier.padding(bottom = 8.dp)) {
                AttachMenu(
                    onCamera = { showAttachMenu = false; cameraLauncher.launch(null) },
                    onPhotoLibrary = { showAttachMenu = false; imagePicker.launch("image/*") },
                    onDocuments = {
                        showAttachMenu = false
                        documentPicker.launch(arrayOf(
                            "application/pdf", "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain",
                        ))
                    },
                    // Only offered once ACCESS_COARSE_LOCATION is already granted — see
                    // enableLocationSharing's doc comment; the SDK never requests it itself.
                    onLocation = if (enableLocationSharing && locationPermissionGranted) { { onLocationPress() } } else null,
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
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = inputBarText),
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
                        Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = inputBarIconTint ?: Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showAttachMenu = !showAttachMenu; showEmojiPicker = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = inputBarIconTint ?: Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                    }
                    // Only rendered once RECORD_AUDIO is already granted — see
                    // enableVoiceNotes's doc comment; the SDK never requests it itself.
                    if (enableVoiceNotes && micPermissionGranted) {
                        IconButton(
                            onClick = { if (isRecording) stopRecordingAndTranscribe() else startRecording() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) "Stop recording" else "Record voice note",
                                tint = if (isRecording) Color(0xFFEF4444) else (inputBarIconTint ?: Color(0xFF9CA3AF)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                ChattySendButton(
                    style = state.theme?.sendButtonStyle,
                    accent = sendBtnBg,
                    textColor = sendBtnText,
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

        // "Powered by Chatty" — matches EmbedClient.tsx's own footer exactly:
        // hidden when the bot owner has white-labeled (paid-plan hide_branding).
        if (state.theme?.hideBranding != true) {
            Text(
                "Powered by Chatty",
                color = t.headerText.copy(alpha = 0.45f),
                fontSize = 9.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

internal fun chattyAvatarIconVector(avatarIcon: String?): ImageVector = when (avatarIcon) {
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

// Full-Unicode emoji picker (search, categories, skin tones, recently used) —
// Google's own androidx.emoji2 EmojiPickerView, not a hand-picked list.
// Entirely local/bundled data (no network), so there's nothing to lazily
// load or show a spinner for — AndroidView inflates it synchronously and it
// paints in the same frame the panel opens.
@Composable
private fun EmojiPicker(borderColor: Color = Color(0xFFE5E7EB), onPick: (String) -> Unit) {
    // Background stays white regardless of theme — EmojiPickerView is a system
    // Android View with its own baked-in light styling, not themeable from
    // Compose — but the border/shadow tint follows the active theme (same as
    // the composer's own border) so the panel doesn't look like a foreign
    // element dropped onto a differently-colored design.
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
    ) {
        // EmojiPickerView has no content-padding attribute of its own (only
        // emojiGridColumns/emojiGridRows) — without this padding its tab row and
        // emoji grid touch the rounded border directly, with no breathing room
        // at all between the system view's content and the Compose panel around it.
        AndroidView(
            factory = { context ->
                val density = context.resources.displayMetrics.density
                fun px(dp: Int) = (dp * density).toInt()
                androidx.emoji2.emojipicker.EmojiPickerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setPadding(px(8), px(8), px(8), px(4))
                    setOnEmojiPickedListener { onPick(it.emoji) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AttachMenu(
    onCamera: () -> Unit,
    onPhotoLibrary: () -> Unit,
    onDocuments: () -> Unit,
    onLocation: (() -> Unit)?,
) {
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
        AttachMenuOption(Icons.Filled.Description, "Documents", onDocuments, Modifier.weight(1f))
        if (onLocation != null) {
            AttachMenuOption(Icons.Filled.LocationOn, "Location", onLocation, Modifier.weight(1f))
        }
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
private fun Bubble(message: ChattyMessage, t: ChattyDesignTokens, avatarIcon: String?, avatarUrl: String?, showSenderTag: Boolean = false) {
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
                Column {
                    // Matches EmbedClient.tsx's own tiny uppercase sender label exactly:
                    // 9sp, semibold, tracked-out, shown only for assistant/agent messages.
                    if (!isUser && showSenderTag) {
                        Text(
                            if (message.role == ChattyRole.AGENT) "HUMAN AGENT" else "AI",
                            color = t.botBubbleText.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                        )
                    }
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

// MarkdownText itself now lives in ChattyMarkdown.kt (real CommonMark/GFM
// parsing + LaTeX rendering, replacing the old hand-rolled regex parser that
// used to live here) — same package, so no import needed at Bubble's call site.
