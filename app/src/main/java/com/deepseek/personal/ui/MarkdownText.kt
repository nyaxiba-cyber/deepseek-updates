package com.deepseek.personal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Markdown 渲染：纯文本直接走 Text；带格式文本走轻量自绘渲染
 * （加粗 / 行内代码 / 列表 / 标题 / 引用 / 代码块），解析结果按文本 LRU 缓存，
 * 滚动回到历史消息时不再重新解析，避免卡顿。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    simple: Boolean = false
) {
    val plain = simple || !containsMarkdown(text)
    if (plain) {
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = modifier
        )
        return
    }
    val annotated = remember(text) { LightMarkdownCache.get(text) }
    androidx.compose.material3.Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
    )
}

/** 轻量 Markdown 解析结果缓存（LRU，最多 200 条，防止内存无限增长）。 */
object LightMarkdownCache {
    private val cache = object : LinkedHashMap<String, AnnotatedString>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, AnnotatedString>?
        ): Boolean = size > 200
    }

    fun get(text: String): AnnotatedString =
        cache.getOrPut(text) { buildLightMarkdown(text) }
}

private fun buildLightMarkdown(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var inCodeBlock = false
    text.split("\n").forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.startsWith("```") -> {
                inCodeBlock = !inCodeBlock
                builder.append("\n")
            }
            inCodeBlock -> {
                builder.append(line)
                builder.append("\n")
            }
            line.startsWith("#") && (line.length == 1 || line[1] == ' ') -> {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                    append(line.trimStart('#').trim())
                }
                builder.append("\n")
            }
            line.startsWith("> ") -> {
                builder.withStyle(
                    SpanStyle(color = Color(0xFF888888), fontStyle = FontStyle.Italic)
                ) {
                    append(line.removePrefix("> ").trim())
                }
                builder.append("\n")
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                builder.append("•  ")
                appendInline(builder, line.substring(2).trim())
                builder.append("\n")
            }
            Regex("^\\d+\\.\\s+").containsMatchIn(line) -> {
                val idx = line.indexOf(". ")
                builder.append(line.substring(0, idx + 1) + "  ")
                appendInline(builder, line.substring(idx + 2).trim())
                builder.append("\n")
            }
            else -> {
                appendInline(builder, line)
                builder.append("\n")
            }
        }
    }
    return builder.toAnnotatedString()
}

/** 行内解析：**加粗** 与 `行内代码`。 */
private fun appendInline(builder: AnnotatedString.Builder, line: String) {
    var i = 0
    while (i < line.length) {
        val boldStart = line.indexOf("**", i)
        val codeStart = line.indexOf('`', i)
        if (boldStart < 0 && codeStart < 0) {
            builder.append(line.substring(i))
            break
        }
        val next = when {
            boldStart < 0 -> codeStart
            codeStart < 0 -> boldStart
            else -> minOf(boldStart, codeStart)
        }
        if (next > i) builder.append(line.substring(i, next))
        if (next == boldStart) {
            val end = line.indexOf("**", boldStart + 2)
            if (end > 0) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(line.substring(boldStart + 2, end))
                }
                i = end + 2
            } else {
                builder.append(line.substring(boldStart))
                i = line.length
            }
        } else {
            val end = line.indexOf('`', codeStart + 1)
            if (end > 0) {
                builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(line.substring(codeStart + 1, end))
                }
                i = end + 1
            } else {
                builder.append(line.substring(codeStart))
                i = line.length
            }
        }
    }
}

/** 粗判文本是否包含 Markdown 语法（命中才走轻量富文本渲染）。 */
private fun containsMarkdown(text: String): Boolean {
    if (text.length < 2) return false
    if (text.contains("```")) return true
    val lines = text.lines()
    for (line in lines) {
        val t = line.trimStart()
        if (t.startsWith("#") && (t.length == 1 || t[1] == ' ')) return true
        if (t.startsWith("> ") || t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
            return true
        }
        if (Regex("^\\d+\\.\\s+").containsMatchIn(t)) return true
    }
    if (text.contains("**") || text.contains('`')) return true
    return false
}
