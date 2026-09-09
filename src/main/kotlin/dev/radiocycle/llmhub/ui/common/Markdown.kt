package dev.radiocycle.llmhub.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface Block {
    data class Paragraph(val lines: List<String>) : Block
    data class Code(val language: String, val code: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

/**
 * Purposefully small Markdown renderer: fenced code blocks, headings, list markers, bold, italic,
 * inline code and links.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> ParagraphBlock(block.lines, color)
                is Block.Code -> CodeBlock(block)
                is Block.Table -> TableBlock(block)
            }
        }
    }
}

@Composable
private fun ParagraphBlock(lines: List<String>, color: androidx.compose.ui.graphics.Color) {
    val annotated = remember(lines) { renderLines(lines) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

@Composable
private fun CodeBlock(block: Block.Code) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

private val TABLE_DIVIDER = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

private fun splitRow(line: String): List<String> = line
    .trim()
    .removePrefix("|")
    .removeSuffix("|")
    .split('|')
    .map { it.trim() }

@Composable
private fun TableBlock(block: Block.Table) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.horizontalScroll(rememberScrollState())
        ) {
            TableRow(block.header, header = true, outline = outline)
            block.rows.forEach { row -> TableRow(row, header = false, outline = outline) }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, outline: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .background(
                if (header) MaterialTheme.colorScheme.surfaceContainerHigh
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .drawBehind {
                drawLine(
                    color = outline,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
    ) {
        cells.forEach { cell ->
            Text(
                text = renderLines(listOf(cell)),
                style = if (header) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(min = 92.dp, max = 320.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = mutableListOf<String>()
    var inCode = false
    var language = ""
    val code = StringBuilder()
    val lines = text.lines()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += Block.Paragraph(paragraph.toList())
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val fence = line.trimStart().startsWith("```")
        when {
            fence && !inCode -> {
                flushParagraph()
                inCode = true
                language = line.trimStart().removePrefix("```").trim()
                code.setLength(0)
            }

            fence && inCode -> {
                inCode = false
                blocks += Block.Code(language, code.toString().trimEnd())
            }

            inCode -> code.appendLine(line)

            // A GFM pipe table: a header row followed by a --- divider.
            line.contains('|') && lines.getOrNull(index + 1)?.matches(TABLE_DIVIDER) == true -> {
                flushParagraph()
                val header = splitRow(line)
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                    rows += splitRow(lines[index]).let { cells ->
                        List(header.size) { cells.getOrElse(it) { "" } }
                    }
                    index++
                }
                blocks += Block.Table(header, rows)
                continue
            }

            else -> paragraph += line
        }
        index++
    }
    if (inCode) blocks += Block.Code(language, code.toString().trimEnd())
    flushParagraph()
    return blocks.filterNot { it is Block.Paragraph && it.lines.all(String::isBlank) }
}

private val INLINE = Regex(
    """(\*\*|__)(.+?)\1|(?<![\w*])([*_])(?!\s)(.+?)(?<!\s)\3(?![\w*])|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""",
    RegexOption.DOT_MATCHES_ALL,
)

private fun renderLines(lines: List<String>): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        val trimmed = raw.trimStart()
        val headingLevel = trimmed.takeWhile { it == '#' }.length
        when {
            headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (headingLevel) {
                            1 -> 21.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            else -> 16.sp
                        },
                    )
                )
                appendInline(trimmed.drop(headingLevel + 1))
                pop()
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                append(raw.takeWhile { it == ' ' })
                append("•  ")
                appendInline(trimmed.drop(2))
            }

            trimmed.startsWith("> ") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append("┃  ")
                appendInline(trimmed.drop(2))
                pop()
            }

            else -> appendInline(raw)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var cursor = 0
    INLINE.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val groups = match.groupValues
        when {
            groups[2].isNotEmpty() -> withSpan(SpanStyle(fontWeight = FontWeight.Bold), groups[2])
            groups[4].isNotEmpty() -> withSpan(SpanStyle(fontStyle = FontStyle.Italic), groups[4])
            groups[5].isNotEmpty() -> withSpan(
                SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                groups[5],
            )
            groups[6].isNotEmpty() -> {
                withSpan(SpanStyle(textDecoration = TextDecoration.Underline), groups[6])
                append(" (")
                append(groups[7])
                append(")")
            }
            else -> append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun AnnotatedString.Builder.withSpan(style: SpanStyle, text: String) {
    pushStyle(style)
    append(text)
    pop()
}
