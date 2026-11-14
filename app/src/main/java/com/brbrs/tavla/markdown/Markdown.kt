package com.brbrs.tavla.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Covers what people actually write in a Deck card: headings, bold/italic, inline code, links,
 * bullet/numbered lists, blockquotes and a horizontal rule. Checklist lines ("- [ ] ...") are
 * expected to already be stripped out by the caller (see [com.brbrs.tavla.util.descriptionWithoutChecklist]),
 * since those render as Tavla's own interactive checklist instead.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(text) { parseBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                is Block.Heading -> Text(
                    inlineAnnotated(block.text, color, linkColor),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = color,
                )
                is Block.Paragraph -> Text(inlineAnnotated(block.text, color, linkColor), style = style, color = color)
                is Block.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inlineAnnotated(block.text, color, linkColor),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        color = color.copy(alpha = 0.85f),
                    )
                }
                is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                if (block.ordered) "${index + 1}." else "•",
                                style = style,
                                color = color.copy(alpha = 0.7f),
                                modifier = Modifier.width(if (block.ordered) 24.dp else 16.dp),
                            )
                            Text(inlineAnnotated(item, color, linkColor), style = style, color = color)
                        }
                    }
                }
            }
        }
    }
}

private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Paragraph(val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class ListBlock(val items: List<String>, val ordered: Boolean) : Block()
    object Rule : Block()
}

private val headingRegex = Regex("""^(#{1,3})\s+(.*)$""")
private val unorderedRegex = Regex("""^[-*+]\s+(.*)$""")
private val orderedRegex = Regex("""^\d+[.)]\s+(.*)$""")
private val quoteRegex = Regex("""^>\s?(.*)$""")
private val ruleRegex = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

private fun parseBlocks(text: String): List<Block> {
    val lines = text.lines()
    val blocks = mutableListOf<Block>()
    var i = 0
    val paragraphBuffer = StringBuilder()
    fun flushParagraph() {
        if (paragraphBuffer.isNotBlank()) blocks += Block.Paragraph(paragraphBuffer.toString().trim())
        paragraphBuffer.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> flushParagraph()
            ruleRegex.matches(line.trim()) -> { flushParagraph(); blocks += Block.Rule }
            headingRegex.matches(line) -> {
                flushParagraph()
                val m = headingRegex.find(line)!!
                blocks += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }
            quoteRegex.matches(line) -> {
                flushParagraph()
                val quoteLines = mutableListOf(quoteRegex.find(line)!!.groupValues[1])
                while (i + 1 < lines.size && quoteRegex.matches(lines[i + 1])) {
                    i++; quoteLines += quoteRegex.find(lines[i])!!.groupValues[1]
                }
                blocks += Block.Quote(quoteLines.joinToString(" ").trim())
            }
            unorderedRegex.matches(line) || orderedRegex.matches(line) -> {
                flushParagraph()
                val ordered = orderedRegex.matches(line)
                val regex = if (ordered) orderedRegex else unorderedRegex
                val items = mutableListOf(regex.find(line)!!.groupValues[1])
                while (i + 1 < lines.size && regex.matches(lines[i + 1])) {
                    i++; items += regex.find(lines[i])!!.groupValues[1]
                }
                blocks += Block.ListBlock(items, ordered)
            }
            else -> { if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' '); paragraphBuffer.append(line.trim()) }
        }
        i++
    }
    flushParagraph()
    return blocks
}

// Bold (**x** / __x__), italic (*x* / _x_), inline code (`x`), and [text](url) links.
private val inlineTokenRegex = Regex(
    """(\*\*.+?\*\*|__.+?__|`.+?`|\[[^]]+]\([^)]+\)|\*[^*]+?\*|_[^_]+?_)""",
)

private fun inlineAnnotated(raw: String, color: Color, linkColor: Color) = buildAnnotatedString {
    var lastEnd = 0
    for (match in inlineTokenRegex.findAll(raw)) {
        if (match.range.first > lastEnd) append(raw.substring(lastEnd, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.removeSurrounding("**")) }
            token.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.removeSurrounding("__")) }
            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = color.copy(alpha = 0.12f)),
            ) { append(token.removeSurrounding("`")) }
            token.startsWith("[") -> {
                val linkMatch = Regex("""\[([^]]+)]\(([^)]+)\)""").find(token)
                if (linkMatch != null) {
                    val (label, url) = linkMatch.destructured
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)))) {
                        append(label)
                    }
                } else append(token)
            }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.removeSurrounding("*")) }
            token.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.removeSurrounding("_")) }
            else -> append(token)
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < raw.length) append(raw.substring(lastEnd))
}
