package com.example.scenic_avatar_guide_app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 数据模型 ────────────────────────────────────────────────

private enum class ColAlign { LEFT, CENTER, RIGHT }

private sealed class Segment {
    data class Markdown(val content: String) : Segment()
    data class Table(
        val headers: List<String>,
        val alignments: List<ColAlign>,
        val rows: List<List<String>>
    ) : Segment()
}

// ── 解析 ─────────────────────────────────────────────────────

private fun parseCells(line: String): List<String> {
    val trimmed = line.trim()
    val inner = if (trimmed.startsWith('|')) trimmed.drop(1) else trimmed
    val withoutTrailing = if (inner.trimEnd().endsWith('|')) inner.trimEnd().dropLast(1) else inner
    return withoutTrailing.split('|').map { it.trim() }
}

private fun plainCellText(text: String): String {
    return text
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""__(.+?)__"""), "$1")
        .replace(Regex("""`(.+?)`"""), "$1")
        .replace("*", "")
        .replace("_", "")
}

private fun displayWidth(text: String): Int {
    return plainCellText(text).sumOf { char ->
        when {
            char.code <= 0x7F -> 1
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN -> 2
            else -> 2
        }.toInt()
    }
}

private fun isSeparatorRow(line: String): Boolean {
    val cleaned = line.filter { it !in "|-: " }
    return cleaned.isEmpty() && line.contains('-')
}

private fun parseAlignments(separatorLine: String, count: Int): List<ColAlign> {
    val cells = parseCells(separatorLine)
    return (0 until count).map { i ->
        val cell = cells.getOrElse(i) { "---" }.trim()
        when {
            cell.startsWith(':') && cell.endsWith(':') -> ColAlign.CENTER
            cell.endsWith(':') -> ColAlign.RIGHT
            else -> ColAlign.LEFT
        }
    }
}

private fun parseSegments(content: String): List<Segment> {
    val lines = content.lines()
    val segments = mutableListOf<Segment>()
    val pendingLines = mutableListOf<String>()
    var i = 0

    fun flushText() {
        val text = pendingLines.joinToString("\n").trim()
        if (text.isNotEmpty()) segments.add(Segment.Markdown(text))
        pendingLines.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val nextLine = lines.getOrNull(i + 1) ?: ""
        if (line.contains('|') && isSeparatorRow(nextLine)) {
            flushText()
            val headers = parseCells(line)
            val alignments = parseAlignments(nextLine, headers.size)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].contains('|')) {
                rows.add(parseCells(lines[i]))
                i++
            }
            if (headers.isNotEmpty()) segments.add(Segment.Table(headers, alignments, rows))
            continue
        }
        pendingLines.add(line)
        i++
    }
    flushText()
    return segments
}

// ── 表格渲染 ─────────────────────────────────────────────────

private val MIN_COL_WIDTH = 72.dp
private val MAX_COL_WIDTH = 180.dp
private val TABLE_SHAPE = RoundedCornerShape(10.dp)

private fun estimateColumnWidth(cells: List<String>): Dp {
    val maxWidth = cells.maxOfOrNull(::displayWidth) ?: 0
    val width = 28.dp + (maxWidth * 7).dp
    return width.coerceIn(MIN_COL_WIDTH, MAX_COL_WIDTH)
}

private fun Modifier.tableCellLines(
    color: Color,
    drawRight: Boolean,
    drawBottom: Boolean
): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.dp.toPx()
    if (drawRight) {
        drawLine(
            color = color,
            start = Offset(size.width - stroke / 2f, 0f),
            end = Offset(size.width - stroke / 2f, size.height),
            strokeWidth = stroke
        )
    }
    if (drawBottom) {
        drawLine(
            color = color,
            start = Offset(0f, size.height - stroke / 2f),
            end = Offset(size.width, size.height - stroke / 2f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun TableCellText(
    content: String,
    textColor: Color,
    textAlign: TextAlign,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val annotated = remember(content, fontWeight) {
        buildAnnotatedString {
            var index = 0
            while (index < content.length) {
                when {
                    content.startsWith("**", index) -> {
                        val end = content.indexOf("**", startIndex = index + 2)
                        if (end > index + 2) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(content.substring(index + 2, end))
                            pop()
                            index = end + 2
                        } else {
                            append(content[index])
                            index++
                        }
                    }
                    content.startsWith("__", index) -> {
                        val end = content.indexOf("__", startIndex = index + 2)
                        if (end > index + 2) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(content.substring(index + 2, end))
                            pop()
                            index = end + 2
                        } else {
                            append(content[index])
                            index++
                        }
                    }
                    content[index] == '`' -> {
                        val end = content.indexOf('`', startIndex = index + 1)
                        if (end > index + 1) {
                            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                            append(content.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append(content[index])
                            index++
                        }
                    }
                    content[index] == '*' || content[index] == '_' -> {
                        val marker = content[index]
                        val end = content.indexOf(marker, startIndex = index + 1)
                        if (end > index + 1) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(content.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append(content[index])
                            index++
                        }
                    }
                    else -> {
                        append(content[index])
                        index++
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = fontWeight,
            color = textColor,
            textAlign = textAlign
        )
    )
}

@Composable
private fun TableView(
    headers: List<String>,
    alignments: List<ColAlign>,
    rows: List<List<String>>,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val colCount = headers.size
    val columnWidths = remember(headers, rows) {
        (0 until colCount).map { col ->
            estimateColumnWidth(
                buildList {
                    add(headers.getOrElse(col) { "" })
                    rows.forEach { row -> add(row.getOrElse(col) { "" }) }
                }
            )
        }
    }
    val gridColor = textColor.copy(alpha = 0.18f)
    val outerBorderColor = textColor.copy(alpha = 0.24f)
    val headerBackground = textColor.copy(alpha = 0.09f)
    val oddRowBackground = textColor.copy(alpha = 0.025f)

    Box(
        modifier = modifier
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState())
            .clip(TABLE_SHAPE)
            .background(textColor.copy(alpha = 0.012f))
            .border(1.dp, outerBorderColor, TABLE_SHAPE)
    ) {
        Column {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                headers.forEachIndexed { col, header ->
                    val align = alignments.getOrElse(col) { ColAlign.LEFT }
                    Box(
                        modifier = Modifier
                            .width(columnWidths[col])
                            .fillMaxHeight()
                            .heightIn(min = 38.dp)
                            .background(headerBackground)
                            .tableCellLines(
                                color = gridColor,
                                drawRight = col < colCount - 1,
                                drawBottom = rows.isNotEmpty()
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = align.boxAlignment()
                    ) {
                        TableCellText(
                            content = header,
                            textColor = textColor,
                            textAlign = align.textAlign(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            rows.forEachIndexed { rowIndex, row ->
                val rowBackground = if (rowIndex % 2 == 1) oddRowBackground else Color.Transparent
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    (0 until colCount).forEach { col ->
                        val cell = row.getOrElse(col) { "" }
                        val align = alignments.getOrElse(col) { ColAlign.LEFT }
                        Box(
                            modifier = Modifier
                                .width(columnWidths[col])
                                .fillMaxHeight()
                                .heightIn(min = 36.dp)
                                .background(rowBackground)
                                .tableCellLines(
                                    color = gridColor,
                                    drawRight = col < colCount - 1,
                                    drawBottom = rowIndex < rows.lastIndex
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = align.boxAlignment()
                        ) {
                            TableCellText(
                                content = cell,
                                textColor = textColor,
                                textAlign = align.textAlign(),
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ColAlign.textAlign() = when (this) {
    ColAlign.CENTER -> TextAlign.Center
    ColAlign.RIGHT -> TextAlign.End
    ColAlign.LEFT -> TextAlign.Start
}

private fun ColAlign.boxAlignment() = when (this) {
    ColAlign.CENTER -> Alignment.Center
    ColAlign.RIGHT -> Alignment.CenterEnd
    ColAlign.LEFT -> Alignment.CenterStart
}

// ── 公开入口 ─────────────────────────────────────────────────

@Composable
fun MarkdownWithTable(
    content: String,
    textColor: Color,
    linkColor: Color,
    codeBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseSegments(content) }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Markdown -> MarkdownBubbleText(
                    content = segment.content,
                    textColor = textColor,
                    linkColor = linkColor,
                    codeBackgroundColor = codeBackgroundColor
                )
                is Segment.Table -> TableView(
                    headers = segment.headers,
                    alignments = segment.alignments,
                    rows = segment.rows,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
