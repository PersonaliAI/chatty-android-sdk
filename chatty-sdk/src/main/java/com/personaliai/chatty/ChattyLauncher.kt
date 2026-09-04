package com.personaliai.chatty

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage

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
    /** Forwarded to [ChattyEmbedScreen]'s onRequestNotificationPermission — see its doc for details. */
    onRequestNotificationPermission: ((botName: String?) -> Unit)? = null,
    /** Forwarded to [ChattyEmbedScreen]'s onMicPermissionNeeded — see its doc for details. */
    onMicPermissionNeeded: (() -> Unit)? = null,
    /** Forwarded to [ChattyEmbedScreen]'s onLocationPermissionNeeded — see its doc for details. */
    onLocationPermissionNeeded: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var unread by remember { mutableStateOf(0) }
    var designId by remember { mutableStateOf("minimal") }
    var rawWidgetStyle by remember { mutableStateOf<String?>(null) }
    var colorScheme by remember { mutableStateOf<ChattyColorScheme?>(null) }
    // Same avatar_icon/avatar_url/logo_url the header avatar (in ChattyChatScreen,
    // and EmbedClient.tsx's own avatarInner()) renders — so the launcher shows the
    // bot's actual configured branding instead of an unrelated fixed glyph.
    var avatarIcon by remember { mutableStateOf<String?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var logoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(botId) {
        try {
            val theme = ChattyClient(botId, baseUrl, host).getTheme()
            designId = chattyNormalizeWidgetStyle(theme.widgetStyle)
            rawWidgetStyle = theme.widgetStyle
            colorScheme = theme.colorScheme
            avatarIcon = theme.avatarIcon
            avatarUrl = theme.avatarUrl
            logoUrl = theme.logoUrl
        } catch (_: Exception) {
            // keep the fallback design — a failed theme fetch shouldn't block the button from rendering
        }
    }
    val tokens = chattyDesignTokens[designId] ?: chattyDesignTokens.getValue("minimal")
    val launcherOverride = colorScheme?.launcher
    val resolvedColor = color ?: chattyParseColor(launcherOverride?.bg) ?: tokens.launcherBg
    // A color_scheme override always wins with a solid color — same as web,
    // where buildColorSchemeCss's launcher rule would also beat the gradient.
    val isGradient = color == null && launcherOverride?.bg == null && designId == "gradient-glow"
    val launcherShape = chattyLauncherShape(rawWidgetStyle, position)
    // web's launcher icon is a crisp vector line icon (Lucide's MessageCircle by
    // default, or whatever avatarIconType the bot owner picked), not a raw
    // platform emoji glyph — a Unicode 💬 renders as a flat colorful glyph that
    // varies by OEM emoji font and has no "outline" character at all.
    val iconTint = chattyParseColor(launcherOverride?.text) ?: Color.White
    // Same precedence as EmbedClient.tsx's own avatarInner(): a real uploaded
    // custom avatar wins, then a known vector-icon type, then the bot's fallback
    // logo image — a plain glyph is the last resort, not the default, so the
    // launcher visually matches the widget's actual branding instead of showing
    // an unrelated icon next to it.
    val knownVectorIconTypes = setOf("headset", "sparkles", "message", "user")
    val launcherImageUrl = when {
        avatarIcon == "custom" && !avatarUrl.isNullOrEmpty() -> avatarUrl
        avatarIcon in knownVectorIconTypes -> null
        !logoUrl.isNullOrEmpty() -> logoUrl
        else -> null
    }
    val launcherIcon = chattyAvatarIconVector(avatarIcon)

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
                if (launcherImageUrl != null) {
                    AsyncImage(
                        model = launcherImageUrl,
                        contentDescription = "Open chat",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(launcherIcon, contentDescription = "Open chat", tint = iconTint, modifier = Modifier.size(26.dp))
                }
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

    // Once opened, the panel (and its WebView) stays composed for the rest of
    // this launcher's lifetime — only its visibility toggles from then on.
    // A `Dialog` (this used previously) fully unmounts on dismiss, destroying
    // the WebView and forcing a genuine fresh page load + lost scroll
    // position on every reopen; the real web widget's own iframe never does
    // this, it's just shown/hidden via CSS. Matching that here means reopening
    // is instant with no reload, same as web.
    var hasOpenedOnce by remember { mutableStateOf(false) }
    if (open) hasOpenedOnce = true

    if (hasOpenedOnce) {
        if (open) BackHandler { open = false }
        Box(
            Modifier
                .zIndex(1f)
                // Zero-size instead of removing from composition — keeps the
                // WebView (and its JS state/scroll position/session) alive
                // while closed, instead of tearing it down. background() must
                // be applied AFTER (i.e. nested inside) the size constraint —
                // otherwise it paints using the fillMaxSize() bounds
                // established earlier in the chain regardless of the later
                // size(0.dp), which is why this used to paint solid color
                // over the whole screen even while "closed".
                .then(
                    if (open) Modifier.fillMaxSize().background(tokens.containerBg)
                    else Modifier.size(0.dp)
                ),
        ) {
            ChattyEmbedScreen(
                botId = botId,
                modifier = Modifier.fillMaxSize(),
                onMessage = { if (!open) unread++ },
                onClose = { open = false },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onMicPermissionNeeded = onMicPermissionNeeded,
                onLocationPermissionNeeded = onLocationPermissionNeeded,
                visible = open,
            )
        }
    }
}

enum class ChattyPosition(val alignment: Alignment) {
    BOTTOM_START(Alignment.BottomStart),
    BOTTOM_END(Alignment.BottomEnd),
}
