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
    CompositionLocalProvider(LocalContentColor provides color) {
        BasicRichText(
            modifier = modifier,
            style = RichTextStyle()
        ) {
            Markdown(text)
        }
    }
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
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullText, streaming) {
        if (!streaming) {
            shown = fullText.length
            return@LaunchedEffect
        }
        while (shown < fullText.length) {
            val gap = fullText.length - shown
            shown = if (gap > 80) fullText.length else minOf(shown + 2, fullText.length)
            delay(28)
        }
    }

    androidx.compose.material3.Text(
        fullText.take(shown) + if (streaming) "▍" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
    )
}
