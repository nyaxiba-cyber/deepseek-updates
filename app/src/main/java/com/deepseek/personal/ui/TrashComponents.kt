package com.deepseek.personal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.personal.data.Conversation

/**
 * 删除后的倒计时浮层：居中圆环倒计时（>1 分钟显示分钟数，≤1 分钟显示秒数），
 * 可撤销恢复或关闭。
 */
@Composable
fun TrashCountdownOverlay(
    pending: TrashPending,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((pending.deleteAt - now) / 1000).toInt().coerceAtLeast(0)
    val expired = remaining <= 0

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
        exit = fadeOut(tween(180)) + scaleOut(animationSpec = tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp)
                ) {
                    Text(
                        if (expired) "回收站会话已彻底删除" else "已移入回收站",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    TrashCountdownCircle(remaining, total = (AppViewModel.TRASH_TTL / 1000).toInt())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pending.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (expired) "已无恢复机会" else "5 分钟后自动彻底删除，可随时撤销",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        TextButton(
                            onClick = onRestore,
                            enabled = !expired
                        ) {
                            Text("撤销删除", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("知道了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashCountdownCircle(
    remainingSeconds: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = (remainingSeconds.toFloat() / total).coerceIn(0f, 1f)
    val display = if (remainingSeconds > 60) {
        "${remainingSeconds / 60}"
    } else {
        "$remainingSeconds"
    }
    val subLabel = if (remainingSeconds > 60) "分钟" else "秒"
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                display,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 剩余时间文本：>1 分钟显示分钟数，≤1 分钟显示秒数。 */
fun trashRemainingText(deleteAt: Long): String {
    val remaining = ((deleteAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    return if (remaining > 60) "剩余 ${remaining / 60} 分钟" else "剩余 $remaining 秒"
}

/**
 * 回收站面板：列出已删除会话（软删除），可恢复或立即彻底删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashSheet(
    trash: List<Pair<Conversation, Long>>,
    onRestore: (Long) -> Unit,
    onDeleteNow: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "回收站",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "删除的会话暂存于此，5 分钟后自动彻底清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (trash.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "回收站是空的",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    trash.forEach { (conv, deletedAt) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conv.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    trashRemainingText(deletedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { onRestore(conv.id) }) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = "恢复",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteNow(conv.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "立即彻底删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "清空回收站",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
