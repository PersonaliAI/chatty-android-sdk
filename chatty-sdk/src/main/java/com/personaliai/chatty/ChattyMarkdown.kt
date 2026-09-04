package com.personaliai.chatty

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Real CommonMark + GFM (tables, strikethrough) rendering for chat replies, plus LaTeX
 * equation support ($inline$ / $$block$$) — matches web's react-markdown + remark-gfm +
 * remark-math/rehype-katex feature set (minus syntax highlighting, which web's own
 * CodeBlock doesn't do either — see chatty/packages/chatty-react/src/ChatWidgetCore.tsx).
 * Replaces the previous hand-rolled regex parser, which only understood
 * bold/italic/inline-code/links and one heading level.
 */

private val chattyMarkdownParser: Parser by lazy {
    Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
        .build()
}

private data class ChattyMathSpan(val latex: String, val isBlock: Boolean)

// Alnum-only placeholders (no markdown-special characters) so LaTeX's own backslashes/
// underscores/asterisks/dollar signs never get misread as markdown syntax by the parser
// below — the same reason remark-math is its own pre-parse pass on web rather than a
// generic inline token.
private const val MATH_PLACEHOLDER_PREFIX = "ChattyMathSpanZ"
private val BLOCK_MATH_REGEX = Regex("\\$\\$([\\s\\S]+?)\\$\\$")
private val INLINE_MATH_REGEX = Regex("(?<!\\$)\\$(?!\\$)([^$\\n]+?)(?<!\\$)\\$(?!\\$)")
private val MATH_PLACEHOLDER_REGEX = Regex("${MATH_PLACEHOLDER_PREFIX}(\\d+)Z")

private fun extractMath(raw: String): Pair<String, List<ChattyMathSpan>> {
    val spans = mutableListOf<ChattyMathSpan>()
    var text = BLOCK_MATH_REGEX.replace(raw) { m ->
        val idx = spans.size
        spans.add(ChattyMathSpan(m.groupValues[1].trim(), isBlock = true))
        "$MATH_PLACEHOLDER_PREFIX${idx}Z"
    }
    text = INLINE_MATH_REGEX.replace(text) { m ->
        val idx = spans.size
        spans.add(ChattyMathSpan(m.groupValues[1].trim(), isBlock = false))
        "$MATH_PLACEHOLDER_PREFIX${idx}Z"
    }
    return text to spans
}

private fun Node.childList(): List<Node> {
    val list = mutableListOf<Node>()
    var child = firstChild
    while (child != null) {
        list.add(child)
        child = child.next
    }
    return list
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF111827), fontSize: TextUnit = 13.sp) {
    val (withPlaceholders, mathSpans) = remember(text) { extractMath(text) }
    val document = remember(withPlaceholders) { chattyMarkdownParser.parse(withPlaceholders) }
    Column(modifier) {
        document.childList().forEach { block ->
            ChattyMarkdownBlock(block, mathSpans, color, fontSize)
        }
    }
}

@Composable
private fun ChattyMarkdownBlock(node: Node, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit) {
    when (node) {
        is Paragraph -> {
            val single = node.firstChild
            // A paragraph that's ONLY a block-math placeholder (the common case for a lone
            // $$...$$ on its own line) renders as the actual equation view, not inline text.
            val soleMathSpan = (single as? MdText)?.literal
                ?.let { MATH_PLACEHOLDER_REGEX.matchEntire(it.trim()) }
                ?.groupValues?.get(1)?.toIntOrNull()
                ?.let { mathSpans.getOrNull(it) }
                ?.takeIf { it.isBlock && single === node.lastChild }
            if (soleMathSpan != null) {
                ChattyLatexView(soleMathSpan.latex, color, fontSize, Modifier.padding(vertical = 4.dp))
            } else {
                ChattyInlineMarkdown(node, mathSpans, color, fontSize)
            }
        }
        is Heading -> {
            val scale = when (node.level) { 1 -> 1.4f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            ChattyInlineMarkdown(node, mathSpans, color, fontSize * scale, bold = true)
        }
        is BulletList -> Column(Modifier.padding(vertical = 2.dp)) {
            node.childList().filterIsInstance<ListItem>().forEach { item ->
                ChattyListItemRow("•", item, mathSpans, color, fontSize)
            }
        }
        is OrderedList -> Column(Modifier.padding(vertical = 2.dp)) {
            var n = node.startNumber
            node.childList().filterIsInstance<ListItem>().forEach { item ->
                ChattyListItemRow("${n++}.", item, mathSpans, color, fontSize)
            }
        }
        is BlockQuote -> Row(Modifier.padding(vertical = 2.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(color.copy(alpha = 0.25f)))
            Spacer(Modifier.width(8.dp))
            Column {
                node.childList().forEach { ChattyMarkdownBlock(it, mathSpans, color.copy(alpha = 0.75f), fontSize) }
            }
        }
        is FencedCodeBlock -> ChattyCodeBlock(node.info?.trim().orEmpty(), node.literal.trimEnd('\n'), fontSize)
        is IndentedCodeBlock -> ChattyCodeBlock("", node.literal.trimEnd('\n'), fontSize)
        is TableBlock -> ChattyMarkdownTable(node, mathSpans, color, fontSize)
        is ThematicBreak -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(color.copy(alpha = 0.15f)))
        else -> node.childList().forEach { ChattyMarkdownBlock(it, mathSpans, color, fontSize) }
    }
}

@Composable
private fun ChattyListItemRow(marker: String, item: ListItem, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit) {
    Row {
        Text(marker, color = color, fontSize = fontSize, modifier = Modifier.padding(end = 6.dp))
        Column(Modifier.padding(bottom = 2.dp)) {
            item.childList().forEach { ChattyMarkdownBlock(it, mathSpans, color, fontSize) }
        }
    }
}

@Composable
private fun ChattyMarkdownTable(table: TableBlock, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit) {
    val borderColor = color.copy(alpha = 0.2f)
    Column(Modifier.padding(vertical = 4.dp).border(1.dp, borderColor, RoundedCornerShape(6.dp))) {
        table.childList().forEach { section ->
            when (section) {
                is TableHead -> section.childList().filterIsInstance<TableRow>().forEach { row ->
                    ChattyTableRow(row, mathSpans, color, fontSize, bold = true, borderColor = borderColor)
                }
                is TableBody -> section.childList().filterIsInstance<TableRow>().forEach { row ->
                    ChattyTableRow(row, mathSpans, color, fontSize, bold = false, borderColor = borderColor)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ChattyTableRow(row: TableRow, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit, bold: Boolean, borderColor: Color) {
    Row(Modifier.fillMaxWidth()) {
        row.childList().filterIsInstance<TableCell>().forEach { cell ->
            Box(Modifier.weight(1f).border(0.5.dp, borderColor).padding(6.dp)) {
                val text = buildInlineAnnotatedString(cell, mathSpans, color, fontSize, bold)
                ClickableTextWithLinks(text, Modifier.fillMaxWidth(), fontSize, color, mathSpans)
            }
        }
    }
}

@Composable
private fun ChattyCodeBlock(lang: String, code: String, fontSize: TextUnit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0x14000000)).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(lang.ifEmpty { "code" }, color = Color(0xFF6B7280), fontSize = fontSize * 0.85f, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
            Text(code, color = Color(0xFF111827), fontSize = fontSize * 0.9f, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Paragraph/Heading-level inline content — bold/italic/code/strikethrough/links/math,
 * flowing as real text (unlike block math, which renders as its own element). */
@Composable
private fun ChattyInlineMarkdown(node: Node, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit, bold: Boolean = false) {
    val annotated = buildInlineAnnotatedString(node, mathSpans, color, fontSize, bold)
    ClickableTextWithLinks(annotated, Modifier.fillMaxWidth(), fontSize, color, mathSpans)
}

private fun buildInlineAnnotatedString(node: Node, mathSpans: List<ChattyMathSpan>, color: Color, fontSize: TextUnit, bold: Boolean): AnnotatedString =
    buildAnnotatedString {
        if (bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                appendInlineNodes(node.childList(), mathSpans, color)
            }
        } else {
            appendInlineNodes(node.childList(), mathSpans, color)
        }
    }

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineNodes(nodes: List<Node>, mathSpans: List<ChattyMathSpan>, color: Color) {
    nodes.forEach { n ->
        when (n) {
            is MdText -> appendTextWithMath(n.literal, mathSpans, color)
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) { appendInlineNodes(n.childList(), mathSpans, color) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { appendInlineNodes(n.childList(), mathSpans, color) }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = color)) { appendInlineNodes(n.childList(), mathSpans, color) }
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = color.copy(alpha = 0.1f), color = color)) { append(n.literal) }
            is Link -> {
                // LinkAnnotation.Url with no listener opens via the ambient UriHandler
                // automatically — no manual click-offset/tag lookup needed (that was
                // ClickableText's older pattern; this SDK's Compose Foundation version
                // dropped inlineContent support from ClickableText, so both links and
                // math now go through material3 Text's own inlineContent/LinkAnnotation
                // support instead — see ChattyAnnotatedText below).
                withLink(LinkAnnotation.Url(n.destination ?: "")) {
                    withStyle(SpanStyle(color = Color(0xFF2563EB), textDecoration = TextDecoration.Underline)) {
                        appendInlineNodes(n.childList(), mathSpans, color)
                    }
                }
            }
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            else -> appendInlineNodes(n.childList(), mathSpans, color)
        }
    }
}

/** Splits a Text node's literal around any embedded math placeholders, inserting an
 * inline-content slot (rendered via ClickableTextWithLinks's inlineContent map) for each. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendTextWithMath(literal: String, mathSpans: List<ChattyMathSpan>, color: Color) {
    var last = 0
    MATH_PLACEHOLDER_REGEX.findAll(literal).forEach { m ->
        if (m.range.first > last) withStyle(SpanStyle(color = color)) { append(literal.substring(last, m.range.first)) }
        val idx = m.groupValues[1].toIntOrNull()
        val span = idx?.let { mathSpans.getOrNull(it) }
        if (span != null) {
            appendInlineContent(id = "math_$idx", alternateText = span.latex)
        } else {
            append(m.value) // shouldn't happen, but never silently drop text
        }
        last = m.range.last + 1
    }
    if (last < literal.length) withStyle(SpanStyle(color = color)) { append(literal.substring(last)) }
}

/** Renders inline markdown text — links (via [LinkAnnotation], which opens through the
 * ambient UriHandler on its own) and inline math (via [InlineTextContent]) both ride on
 * material3's own Text now; ClickableText's Compose Foundation version here dropped
 * inlineContent support, and its own doc comment already points at this replacement. */
@Composable
private fun ClickableTextWithLinks(text: AnnotatedString, modifier: Modifier, fontSize: TextUnit, color: Color, mathSpans: List<ChattyMathSpan>) {
    val density = LocalDensity.current
    val inlineContent = remember(text, mathSpans) {
        mathSpans.indices.associate { idx ->
            val span = mathSpans[idx]
            val fontSizePx = with(density) { fontSize.toPx() }
            val (w, h) = measureLatex(span.latex, fontSizePx, color.toArgb())
            val widthSp = with(density) { w.toDp().value.sp }
            val heightSp = with(density) { h.toDp().value.sp }
            "math_$idx" to InlineTextContent(
                Placeholder(widthSp, heightSp, PlaceholderVerticalAlign.TextCenter)
            ) {
                ChattyLatexView(span.latex, color, fontSize)
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        inlineContent = inlineContent,
    )
}

/** Builds the drawable just to read its intrinsic size (used for the Placeholder), cheap
 * enough to redo inside the actual render (ChattyLatexView) — JLatexMath layout is
 * microseconds-scale for the short expressions a chat reply realistically contains. */
private fun measureLatex(latex: String, textSizePx: Float, colorInt: Int): Pair<Int, Int> = try {
    val d = JLatexMathDrawable.builder(latex).textSize(textSizePx.coerceAtLeast(1f)).color(colorInt).build()
    d.intrinsicWidth.coerceAtLeast(1) to d.intrinsicHeight.coerceAtLeast(1)
} catch (_: Exception) {
    (textSizePx * latex.length * 0.6f).toInt().coerceAtLeast(1) to textSizePx.toInt().coerceAtLeast(1)
}

@Composable
private fun ChattyLatexView(latex: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val colorInt = color.toArgb()
    val fontSizePx = with(density) { fontSize.toPx() }
    val drawable: Drawable? = remember(latex, colorInt, fontSizePx) {
        try {
            JLatexMathDrawable.builder(latex).textSize(fontSizePx.coerceAtLeast(1f)).color(colorInt).build()
        } catch (_: Exception) {
            null // malformed LaTeX from the model — fall back to raw text rather than crash
        }
    }
    if (drawable == null) {
        Text("$$$latex$$", color = color, fontSize = fontSize, fontFamily = FontFamily.Monospace, modifier = modifier)
        return
    }
    val widthDp = with(density) { drawable.intrinsicWidth.toDp() }
    val heightDp = with(density) { drawable.intrinsicHeight.toDp() }
    Canvas(modifier.size(widthDp, heightDp)) {
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
}
