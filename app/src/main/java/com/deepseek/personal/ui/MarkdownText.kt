package com.deepseek.personal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextStyle
import kotlinx.coroutines.delay

/**
 * Markdown 渲染：使用成熟库 compose-richtext（CommonMark 标准，
 * 支持表格、代码块、标题、列表、引用等）。
 * 流式输出期间由 [TypewriterText] 承担纯文本打字机呈现。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    simple: Boolean = false
) {
    if (simple) {
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = modifier
        )
        return
    }
    // 纯文本快速通道：绝大多数对话是普通文本，直接走 Text 渲染，
    // 避免滚动进入视口时每条消息都做一遍 Markdown 解析（卡顿主因之一）。
    val usePlain = remember(text) { !containsMarkdown(text) }
    if (usePlain) {
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = modifier
        )
        return
    }
    CompositionLocalProvider(LocalContentColor provides color) {
        BasicRichText(
            modifier = modifier,
            style = RichTextStyle()
        ) {
            Markdown(text)
        }
    }
}

/** 粗判文本是否包含 Markdown 语法（命中才走富文本解析）。 */
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
    if (text.contains("|") && text.contains("---")) return true
    if (text.contains("[") && text.contains("](")) return true
    return false
}

/**
 * ChatGPT 式打字机呈现：流式输出时按匀速逐字浮现，
 * 落后过多时自动追赶，结束后一次性显示完整内容。
 */
@Composable
fun TypewriterText(
    fullText: String,
    streaming: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onProgress: ((Int) -> Unit)? = null
) {
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullText, streaming) {
        if (!streaming) {
            if (shown != fullText.length) {
                shown = fullText.length
                onProgress?.invoke(shown)
            }
            return@LaunchedEffect
        }
        // 帧驱动：每帧按节奏显示字符，与 AI 输出速度匹配且平滑不跳变
        var lastFrame = 0L
        var frameCount = 0
        while (shown < fullText.length) {
            withFrameNanos { frameTime ->
                if (lastFrame != 0L) {
                    val gap = fullText.length - shown
                    val step = when {
                        gap > 60 -> 6
                        gap > 20 -> 4
                        else -> 2
                    }
                    shown = minOf(shown + step, fullText.length)
                    // 回调节流：每 2 帧上报一次，减少滚动逻辑的重复计算
                    frameCount++
                    if (frameCount % 2 == 0) onProgress?.invoke(shown)
                }
                lastFrame = frameTime
            }
        }
    }

    androidx.compose.material3.Text(
        fullText.take(shown) + if (streaming) "▍" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
    )
}
