package com.personaliai.chatty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * chattyNormalizeWidgetStyle/chattyLogoBgColor parse the `widgetStyle` string
 * the theme API returns (`"{styleId}:{logoBgColor}:{launcherShape}"`), mapping
 * across 3 generations of historical style ids (see chattyLegacyStyleMap).
 * Runs under Robolectric because chattyLogoBgColor eventually calls into
 * android.graphics.Color.parseColor.
 */
@RunWith(RobolectricTestRunner::class)
class ChattyDesignTokensTest {

    @Test
    fun `normalizeWidgetStyle returns minimal for null or empty input`() {
        assertEquals("minimal", chattyNormalizeWidgetStyle(null))
        assertEquals("minimal", chattyNormalizeWidgetStyle(""))
    }

    @Test
    fun `normalizeWidgetStyle passes through a current design id unchanged`() {
        assertEquals("dark-sleek", chattyNormalizeWidgetStyle("dark-sleek"))
        assertEquals("neubrutalism", chattyNormalizeWidgetStyle("neubrutalism:#111:square"))
    }

    @Test
    fun `normalizeWidgetStyle maps every legacy style id to a current design`() {
        val legacyToExpected = mapOf(
            "liquid" to "glassmorphism",
            "neumorphism" to "corporate",
            "claymorphism" to "playful",
            "bento" to "minimal",
            "brutalism" to "neubrutalism",
            "retro" to "dark-sleek",
            "aurora" to "gradient-glow",
            "minimalist" to "minimal",
            "elevated" to "corporate",
            "frosted" to "glassmorphism",
            "bold" to "gradient-glow",
            "contrast" to "dark-sleek",
        )
        legacyToExpected.forEach { (legacy, expected) ->
            assertEquals("legacy id '$legacy'", expected, chattyNormalizeWidgetStyle(legacy))
        }
    }

    @Test
    fun `normalizeWidgetStyle only reads the segment before the first colon`() {
        assertEquals("corporate", chattyNormalizeWidgetStyle("neumorphism:#fefefe:rounded"))
    }

    @Test
    fun `normalizeWidgetStyle falls back to minimal for an unknown id`() {
        assertEquals("minimal", chattyNormalizeWidgetStyle("totally-made-up-style"))
    }

    @Test
    fun `every design token key is reachable via normalizeWidgetStyle`() {
        chattyDesignTokens.keys.forEach { key ->
            assertEquals(key, chattyNormalizeWidgetStyle(key))
        }
    }

    @Test
    fun `logoBgColor parses the 2nd colon segment as a hex color`() {
        val color = chattyLogoBgColor("minimal:#ff0000:bubble")
        assertEquals(1f, color!!.red, 0.001f)
        assertEquals(0f, color.green, 0.001f)
        assertEquals(0f, color.blue, 0.001f)
    }

    @Test
    fun `logoBgColor tolerates a missing leading hash`() {
        val color = chattyLogoBgColor("minimal:00ff00:bubble")
        assertEquals(0f, color!!.red, 0.001f)
        assertEquals(1f, color.green, 0.001f)
    }

    @Test
    fun `logoBgColor returns null when the segment is absent or blank`() {
        assertNull(chattyLogoBgColor(null))
        assertNull(chattyLogoBgColor("minimal"))
        assertNull(chattyLogoBgColor("minimal:"))
        assertNull(chattyLogoBgColor("minimal: "))
    }

    @Test
    fun `logoBgColor returns null for an invalid hex value instead of throwing`() {
        assertNull(chattyLogoBgColor("minimal:not-a-color:bubble"))
    }

    @Test
    fun `bubbleShape squares off the corner nearest the avatar`() {
        val density = androidx.compose.ui.unit.Density(1f)
        val size = androidx.compose.ui.geometry.Size(100f, 100f)

        // User bubbles square the top-end corner; bot bubbles square the top-start corner.
        val userShape = chattyBubbleShape(14, isUser = true)
        assertEquals(0f, userShape.topEnd.toPx(size, density), 0.001f)
        assertTrue(userShape.topStart.toPx(size, density) > 0f)

        val botShape = chattyBubbleShape(14, isUser = false)
        assertEquals(0f, botShape.topStart.toPx(size, density), 0.001f)
        assertTrue(botShape.topEnd.toPx(size, density) > 0f)
    }
}
