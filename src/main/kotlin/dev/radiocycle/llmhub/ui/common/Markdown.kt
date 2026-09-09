package dev.radiocycle.llmhub.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.commonmark.ext.autolink.AutolinkExtension
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
import org.commonmark.node.CustomBlock
import org.commonmark.node.Document
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
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.awt.Desktop
import java.net.URI

private val commonmarkParser: Parser by lazy {
    val extensions = listOf(
        TablesExtension.create(),
        AutolinkExtension.create(),
        StrikethroughExtension.create(),
    )
    Parser.builder().extensions(extensions).build()
}

fun openWebUrl(url: String) {
    val clean = url.trim()
    if (clean.isBlank()) return
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(clean))
            return
        }
    } catch (_: Throwable) {}
    try {
        ProcessBuilder("xdg-open", clean).start()
    } catch (_: Throwable) {}
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val document = remember(text) {
        try {
            commonmarkParser.parse(text)
        } catch (_: Throwable) {
            null
        }
    }

    if (document == null) {
        Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.bodyLarge)
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val linkColor = Color(0xFF64B5F6) // Clear, accessible accent blue for links

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var child = document.firstChild
        while (child != null) {
            RenderBlockNode(child, colorScheme, linkColor, color)
            child = child.next
        }
    }
}

@Composable
private fun RenderBlockNode(
    node: Node,
    colorScheme: ColorScheme,
    linkColor: Color,
    textColor: Color,
) {
    when (node) {
        is Heading -> {
            val annotated = remember(node) { buildInlineString(node, colorScheme, linkColor) }
            val (fontSize, fontWeight) = when (node.level) {
                1 -> 22.sp to FontWeight.Bold
                2 -> 19.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.SemiBold
                else -> 15.sp to FontWeight.SemiBold
            }
            Text(
                text = annotated,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = textColor,
                lineHeight = (fontSize.value * 1.3f).sp,
                modifier = Modifier.padding(top = if (node.level <= 2) 8.dp else 4.dp, bottom = 2.dp),
            )
        }

        is Paragraph -> {
            val annotated = remember(node) { buildInlineString(node, colorScheme, linkColor) }
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 23.sp,
                color = textColor,
            )
        }

        is BulletList -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                "•",
                                color = colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                            val annotated = remember(item) { buildInlineString(item, colorScheme, linkColor) }
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 23.sp,
                                color = textColor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item = item.next
                }
            }
        }

        is OrderedList -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                var item = node.firstChild
                var index = node.startNumber
                while (item != null) {
                    if (item is ListItem) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                "$index.",
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                            val annotated = remember(item) { buildInlineString(item, colorScheme, linkColor) }
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 23.sp,
                                color = textColor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        index++
                    }
                    item = item.next
                }
            }
        }

        is BlockQuote -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(Modifier.padding(vertical = 6.dp, horizontal = 10.dp)) {
                    Box(
                        Modifier
                            .width(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    val annotated = remember(node) { buildInlineString(node, colorScheme, linkColor) }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp,
                    )
                }
            }
        }

        is FencedCodeBlock -> {
            DesktopCodeBlock(node.info.orEmpty().trim(), node.literal.orEmpty())
        }

        is IndentedCodeBlock -> {
            DesktopCodeBlock("", node.literal.orEmpty())
        }

        is ThematicBreak -> {
            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        is TableBlock -> {
            DesktopTableBlock(node, colorScheme, linkColor)
        }

        is CustomBlock -> {
            if (node is TableBlock) {
                DesktopTableBlock(node, colorScheme, linkColor)
            }
        }
    }
}

@Composable
private fun DesktopCodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = (if (language.isNotBlank()) language else "code").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code.trimEnd()))
                        copied = true
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = code.trimEnd(),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun DesktopTableBlock(table: TableBlock, colorScheme: ColorScheme, linkColor: Color) {
    val headers = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()

    var section = table.firstChild
    while (section != null) {
        when (section) {
            is TableHead -> {
                var row = section.firstChild
                while (row != null) {
                    if (row is TableRow) {
                        var cell = row.firstChild
                        while (cell != null) {
                            if (cell is TableCell) {
                                headers += extractPlainText(cell)
                            }
                            cell = cell.next
                        }
                    }
                    row = row.next
                }
            }
            is TableBody -> {
                var row = section.firstChild
                while (row != null) {
                    if (row is TableRow) {
                        val rowCells = mutableListOf<String>()
                        var cell = row.firstChild
                        while (cell != null) {
                            if (cell is TableCell) {
                                rowCells += extractPlainText(cell)
                            }
                            cell = cell.next
                        }
                        if (rowCells.isNotEmpty()) rows += rowCells
                    }
                    row = row.next
                }
            }
        }
        section = section.next
    }

    if (headers.isEmpty() && rows.isEmpty()) return

    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    val colWidths = remember(headers, rows) {
        List(colCount) { colIndex ->
            val headerLen = headers.getOrNull(colIndex)?.length ?: 0
            val maxRowLen = rows.maxOfOrNull { it.getOrNull(colIndex)?.length ?: 0 } ?: 0
            val maxLen = maxOf(headerLen, maxRowLen)
            (maxLen * 8.5f + 36).coerceIn(100f, 380f).dp
        }
    }

    Surface(
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                if (headers.isNotEmpty()) {
                    Row(
                        Modifier.background(colorScheme.surfaceContainerHigh)
                    ) {
                        headers.forEachIndexed { colIndex, cellText ->
                            Box(
                                modifier = Modifier
                                    .width(colWidths.getOrElse(colIndex) { 120.dp })
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colorScheme.outlineVariant, thickness = 1.dp)
                }

                rows.forEachIndexed { rowIndex, rowCells ->
                    Row(
                        Modifier.background(
                            if (rowIndex % 2 == 1) colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)
                            else Color.Transparent
                        )
                    ) {
                        List(colCount) { i -> rowCells.getOrElse(i) { "" } }.forEachIndexed { colIndex, cellText ->
                            Box(
                                modifier = Modifier
                                    .width(colWidths.getOrElse(colIndex) { 120.dp })
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (rowIndex < rows.lastIndex) {
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.8.dp)
                    }
                }
            }
        }
    }
}

private fun extractPlainText(node: Node): String {
    val sb = StringBuilder()
    fun walk(n: Node) {
        when (n) {
            is org.commonmark.node.Text -> sb.append(n.literal)
            is Code -> sb.append(n.literal)
            is SoftLineBreak -> sb.append(" ")
            is HardLineBreak -> sb.append("\n")
            else -> {
                var c = n.firstChild
                while (c != null) {
                    walk(c)
                    c = c.next
                }
            }
        }
    }
    walk(node)
    return sb.toString().trim()
}

private fun buildInlineString(
    root: Node,
    colorScheme: ColorScheme,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    fun appendInline(node: Node) {
        when (node) {
            is org.commonmark.node.Text -> append(node.literal)

            is StrongEmphasis -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                var c = node.firstChild
                while (c != null) {
                    appendInline(c)
                    c = c.next
                }
                pop()
            }

            is Emphasis -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                var c = node.firstChild
                while (c != null) {
                    appendInline(c)
                    c = c.next
                }
                pop()
            }

            is Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                var c = node.firstChild
                while (c != null) {
                    appendInline(c)
                    c = c.next
                }
                pop()
            }

            is Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        background = colorScheme.surfaceContainerHighest,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                )
                append(" ${node.literal} ")
                pop()
            }

            is Link -> {
                val url = node.destination.orEmpty()
                val link = LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        hoveredStyle = SpanStyle(
                            color = linkColor.copy(alpha = 0.8f),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = {
                        openWebUrl(url)
                    },
                )
                pushLink(link)
                var c = node.firstChild
                while (c != null) {
                    appendInline(c)
                    c = c.next
                }
                pop()
            }

            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")

            else -> {
                var c = node.firstChild
                while (c != null) {
                    appendInline(c)
                    c = c.next
                }
            }
        }
    }

    var c = root.firstChild
    while (c != null) {
        appendInline(c)
        c = c.next
    }
}
