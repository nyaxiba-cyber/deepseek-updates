package com.deepseek.personal.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.personal.data.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    onDelete: (() -> Unit)? = null
) {
    val isUser = msg.role == "user"
    val isDark = isSystemInDarkTheme()
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            MessageActions(msg, onDelete)
            Spacer(Modifier.padding(end = 2.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            // 注意：这里不做 animateContentSize / 进入动画包装——
            // 尺寸动画包裹 LazyColumn item 会导致滚动时布局抖动（富文本首帧测量+高度插值）。
            if (msg.reasoning.isNotBlank()) {
                ReasoningCard(msg.reasoning, msg.streaming)
                Spacer(Modifier.height(8.dp))
            }

            val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface

            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = bubbleColor,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(AnnotatedString(msg.content))
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
            ) {
                BoxContent(
                    msg = msg,
                    textColor = textColor,
                    isDark = isDark
                )
            }
        }
        if (isUser) {
            Spacer(Modifier.padding(start = 2.dp))
            MessageActions(msg, onDelete)
        }
    }
}

@Composable
private fun MessageActions(
    msg: ChatMessage,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { menu = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "消息操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false }
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    clipboard.setText(AnnotatedString(msg.content))
                    menu = false
                }
            )
            DropdownMenuItem(
                text = { Text("分享此消息") },
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, msg.content)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "分享消息")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    menu = false
                }
            )
            if (onDelete != null && !msg.streaming) {
                DropdownMenuItem(
                    text = { Text("删除此条") },
                    onClick = {
                        onDelete()
                        menu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BoxContent(
    msg: ChatMessage,
    textColor: Color,
    isDark: Boolean
) {
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (msg.content.isNotBlank()) {
            // 不做 SelectionContainer：它会在滚动时为每条消息维护选区状态，非常吃性能。
            // 复制走每条消息右上角「⋮」菜单里的「复制」。
            // 流式内容直接渲染（无逐字打字机动画），配合滚动跟随形成自然输出效果。
            MarkdownText(
                text = msg.content,
                color = textColor,
                isDark = isDark
            )
        }
        if (msg.error != null) {
            Text(
                "⚠ ${msg.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (msg.content.isNotBlank()) 6.dp else 0.dp)
            )
        }
        if (msg.content.isBlank() && msg.error == null) {
            Text(
                "…",
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReasoningCard(reasoning: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded || streaming) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.padding(end = 4.dp))
                Text(
                    if (streaming) "深度思考中…" else "深度思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            AnimatedVisibility(visible = expanded || streaming) {
                MarkdownText(
                    text = reasoning + if (streaming) "▍" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    isDark = isSystemInDarkTheme(),
                    simple = streaming,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
