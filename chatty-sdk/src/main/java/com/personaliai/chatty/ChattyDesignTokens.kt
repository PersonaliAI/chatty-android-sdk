package com.personaliai.chatty

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The 10 widget designs' visual tokens, ported 1:1 from the web widget's
 * globals.css (`.style-*` rules) and widget.js's `LAUNCHER_STYLES` so the
 * native chat screen matches whatever design the bot owner picked in the
 * dashboard. Font pairing (each design uses a distinct Google Font on web)
 * is intentionally out of scope here — bundling 5 font families into this
 * SDK is a separate, larger piece of work; colors, radii, and structural
 * sizing are the part that carries most of a design's visual identity.
 */
data class ChattyDesignTokens(
    val containerBg: Color,
    val headerBg: Color,
    val headerText: Color,
    val botBubbleBg: Color,
    val botBubbleText: Color,
    val botBubbleRadius: Int,
    val userBubbleBg: Color,
    val userBubbleText: Color,
    val userBubbleRadius: Int,
    // widget.js's LAUNCHER_STYLES — NOT always the same as userBubbleBg (e.g.
    // dark-sleek's launcher is dark, not its teal accent; neubrutalism's
    // launcher is black, not its pink accent).
    val launcherBg: Color,
    val launcherShadow: Color,
)

private fun c(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

// gradient-glow's header/launcher is a linear-gradient(#a855f7, #ec4899) on
// web; Brush.linearGradient is used directly at call sites for that one
// design instead of trying to encode a gradient into a single Color.
val ChattyGradientGlowHeaderColors = listOf(Color(0xFFA855F7), Color(0xFFEC4899))

val chattyDesignTokens: Map<String, ChattyDesignTokens> = mapOf(
    "minimal" to ChattyDesignTokens(
        containerBg = c("#f3f2ee"), headerBg = c("#ffffff"), headerText = c("#1c1a15"),
        botBubbleBg = c("#f3f2ee"), botBubbleText = c("#1c1a15"), botBubbleRadius = 14,
        userBubbleBg = c("#1c1a15"), userBubbleText = c("#ffffff"), userBubbleRadius = 14,
        launcherBg = c("#1c1a15"), launcherShadow = Color(0x2E000000),
    ),
    "playful" to ChattyDesignTokens(
        containerBg = c("#fffaf5"), headerBg = c("#ff8a5c"), headerText = c("#ffffff"),
        botBubbleBg = c("#ffe6d9"), botBubbleText = c("#7a3f24"), botBubbleRadius = 20,
        userBubbleBg = c("#ff8a5c"), userBubbleText = c("#ffffff"), userBubbleRadius = 20,
        launcherBg = c("#ff8a5c"), launcherShadow = Color(0x73FF8A5C),
    ),
    "corporate" to ChattyDesignTokens(
        containerBg = c("#eef1f6"), headerBg = c("#1c2e4a"), headerText = c("#ffffff"),
        botBubbleBg = c("#eef1f6"), botBubbleText = c("#1c2e4a"), botBubbleRadius = 8,
        userBubbleBg = c("#1c2e4a"), userBubbleText = c("#ffffff"), userBubbleRadius = 8,
        launcherBg = c("#1c2e4a"), launcherShadow = Color(0x4C1C2E4A),
    ),
    "dark-sleek" to ChattyDesignTokens(
        containerBg = c("#111114"), headerBg = c("#14141a"), headerText = c("#e4e4e8"),
        botBubbleBg = c("#1c1c22"), botBubbleText = c("#e4e4e8"), botBubbleRadius = 12,
        userBubbleBg = c("#00e5c7"), userBubbleText = c("#05201c"), userBubbleRadius = 12,
        // Launcher is dark (matches header), not the teal accent bubble color.
        launcherBg = c("#14141a"), launcherShadow = Color(0x5900E5C7),
    ),
    // headerBg/launcherBg here are placeholders — see ChattyGradientGlowHeaderColors above.
    "gradient-glow" to ChattyDesignTokens(
        containerBg = c("#ffffff"), headerBg = c("#a855f7"), headerText = c("#ffffff"),
        botBubbleBg = c("#f6effc"), botBubbleText = c("#4a2467"), botBubbleRadius = 16,
        userBubbleBg = c("#a855f7"), userBubbleText = c("#ffffff"), userBubbleRadius = 16,
        launcherBg = c("#a855f7"), launcherShadow = Color(0x66A855F7),
    ),
    "glassmorphism" to ChattyDesignTokens(
        containerBg = c("#8f6ff0"), headerBg = Color(0x14FFFFFF), headerText = c("#ffffff"),
        botBubbleBg = Color(0x2EFFFFFF), botBubbleText = c("#ffffff"), botBubbleRadius = 14,
        userBubbleBg = Color(0xE6FFFFFF), userBubbleText = c("#39396b"), userBubbleRadius = 14,
        // Launcher is translucent white (rgba(255,255,255,.25)), lighter than the near-opaque user bubble.
        launcherBg = Color(0x40FFFFFF), launcherShadow = Color(0x33000000),
    ),
    "ecommerce" to ChattyDesignTokens(
        containerBg = c("#fdf9f2"), headerBg = c("#0f9d8c"), headerText = c("#ffffff"),
        botBubbleBg = c("#f3efe6"), botBubbleText = c("#3a3226"), botBubbleRadius = 14,
        userBubbleBg = c("#0f9d8c"), userBubbleText = c("#ffffff"), userBubbleRadius = 14,
        launcherBg = c("#0f9d8c"), launcherShadow = Color(0x590F9D8C),
    ),
    "healthcare-calm" to ChattyDesignTokens(
        containerBg = c("#fbfcf9"), headerBg = c("#eaf1e9"), headerText = c("#2f4235"),
        botBubbleBg = c("#eaf1e9"), botBubbleText = c("#2f4235"), botBubbleRadius = 14,
        userBubbleBg = c("#6f9c7d"), userBubbleText = c("#ffffff"), userBubbleRadius = 14,
        launcherBg = c("#6f9c7d"), launcherShadow = Color(0x596F9C7D),
    ),
    "neubrutalism" to ChattyDesignTokens(
        containerBg = c("#ffffff"), headerBg = c("#ffde59"), headerText = c("#111111"),
        botBubbleBg = c("#f2f2f2"), botBubbleText = c("#111111"), botBubbleRadius = 4,
        userBubbleBg = c("#ff3d67"), userBubbleText = c("#ffffff"), userBubbleRadius = 4,
        // Launcher is black with a hard offset shadow, not the pink accent bubble color.
        launcherBg = c("#111111"), launcherShadow = c("#111111"),
    ),
    "luxury-editorial" to ChattyDesignTokens(
        containerBg = c("#fbf9f5"), headerBg = c("#161412"), headerText = c("#f5efe3"),
        botBubbleBg = c("#f1ebdf"), botBubbleText = c("#2a251d"), botBubbleRadius = 2,
        userBubbleBg = c("#161412"), userBubbleText = c("#f5efe3"), userBubbleRadius = 2,
        launcherBg = c("#161412"), launcherShadow = Color(0x4D000000),
    ),
)

/** Mirrors widget-style.ts's LEGACY_STYLE_MAP — every historical widget_style ID
 * across all 3 preset generations this project has shipped, mapped onto one
 * of the 10 current design keys above. */
private val chattyLegacyStyleMap: Map<String, String> = mapOf(
    "liquid" to "glassmorphism", "neumorphism" to "corporate", "claymorphism" to "playful",
    "bento" to "minimal", "brutalism" to "neubrutalism", "retro" to "dark-sleek", "aurora" to "gradient-glow",
    "minimalist" to "minimal", "elevated" to "corporate", "frosted" to "glassmorphism",
    "bold" to "gradient-glow", "contrast" to "dark-sleek",
)

/** `widgetStyle` from the theme API is `"{styleId}:{logoBgColor}:{launcherShape}"`. */
fun chattyNormalizeWidgetStyle(raw: String?): String {
    if (raw.isNullOrEmpty()) return "minimal"
    val id = raw.substringBefore(":")
    if (chattyDesignTokens.containsKey(id)) return id
    return chattyLegacyStyleMap[id] ?: "minimal"
}

/**
 * Message bubble shape with the corner nearest the avatar squared off,
 * matching web's `.rounded-tl-none` (bot) / `.rounded-tr-none` (user) — the
 * "speech tail" corner treatment used by every design.
 */
fun chattyBubbleShape(radiusDp: Int, isUser: Boolean): RoundedCornerShape = if (isUser) {
    RoundedCornerShape(topStart = radiusDp.dp, topEnd = 0.dp, bottomEnd = radiusDp.dp, bottomStart = radiusDp.dp)
} else {
    RoundedCornerShape(topStart = 0.dp, topEnd = radiusDp.dp, bottomEnd = radiusDp.dp, bottomStart = radiusDp.dp)
}
