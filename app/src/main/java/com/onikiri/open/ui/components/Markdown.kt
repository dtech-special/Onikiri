package com.onikiri.open.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val depth: Int) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val HTML_TAG = Regex("</?[A-Za-z][^>]*>")
private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
private val LINKED_IMAGE = Regex("\\[!\\[[^\\]]*]\\([^)]*\\)]\\([^)]*\\)")
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
private val QUOTE = Regex("^>\\s?(.*)$")
private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val INLINE = Regex(
    "\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)|(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])"
)

private fun cleanLine(line: String): String =
    line.replace(LINKED_IMAGE, "").replace(IMAGE, "").replace(HTML_TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

/** Small, dependency-free Markdown block parser; HTML and images are stripped. */
fun parseMarkdownBlocks(source: String): List<MdBlock> {
    val blocks = ArrayList<MdBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks.add(MdBlock.Paragraph(text))
        paragraph.clear()
    }

    for (rawLine in source.replace(HTML_COMMENT, "").replace("\r", "").lines()) {
        if (rawLine.trimStart().startsWith("```")) {
            if (inCode) {
                blocks.add(MdBlock.Code(code.toString().trimEnd()))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(rawLine).append('\n')
            continue
        }
        val line = cleanLine(rawLine)
        if (line.isBlank()) {
            flushParagraph()
            continue
        }
        val heading = HEADING.matchEntire(line.trim())
        val bullet = BULLET.matchEntire(line)
        val numbered = NUMBERED.matchEntire(line)
        val quote = QUOTE.matchEntire(line.trim())
        when {
            heading != null -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
            }
            RULE.matches(line) -> {
                flushParagraph()
                blocks.add(MdBlock.Rule)
            }
            bullet != null -> {
                flushParagraph()
                blocks.add(MdBlock.Bullet(bullet.groupValues[2].trim(), bullet.groupValues[1].length / 2))
            }
            numbered != null -> {
                flushParagraph()
                blocks.add(MdBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2].trim()))
            }
            quote != null -> {
                flushParagraph()
                blocks.add(MdBlock.Quote(quote.groupValues[1].trim()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks.add(MdBlock.Code(code.toString().trimEnd()))
    flushParagraph()
    return blocks
}

fun inlineMarkdown(text: String, linkColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (match in INLINE.findAll(text)) {
            append(text.substring(last, match.range.first))
            when {
                match.groups[1] != null ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
                match.groups[2] != null ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(match.groupValues[2])
                    }
                match.groups[3] != null -> {
                    val url = match.groupValues[4]
                    val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(style))) { append(match.groupValues[3]) }
                    } else {
                        withStyle(style) { append(match.groupValues[3]) }
                    }
                }
                else ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[5]) }
            }
            last = match.range.last + 1
        }
        append(text.substring(last))
    }

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown).take(250) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val body = MaterialTheme.typography.bodyMedium

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, linkColor, codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is MdBlock.Paragraph -> Text(inlineMarkdown(block.text, linkColor, codeBackground), style = body)
                is MdBlock.Bullet -> Row(Modifier.padding(start = (block.depth * 12).dp)) {
                    Text("•", style = body, modifier = Modifier.width(16.dp))
                    Text(inlineMarkdown(block.text, linkColor, codeBackground), style = body)
                }
                is MdBlock.Numbered -> Row {
                    Text("${block.number}.", style = body, modifier = Modifier.width(24.dp))
                    Text(inlineMarkdown(block.text, linkColor, codeBackground), style = body)
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                is MdBlock.Code -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = codeBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(PaddingValues(10.dp))
                    )
                }
                MdBlock.Rule -> HorizontalDivider()
            }
        }
    }
}
