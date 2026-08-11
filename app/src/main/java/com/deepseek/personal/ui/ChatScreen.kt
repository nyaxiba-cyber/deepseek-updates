package com.deepseek.personal.ui

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.personal.data.ChatMessage
import com.deepseek.personal.data.ModelInfo
import kotlinx.coroutines.launch

// 手势阈值常量（统一调手感）
private val EdgeSwipeDp = 30.dp
private const val EdgeSwipeMinDx = 120f
private const val TopPullMinDy = 120f
private const val BottomPullMinDy = 150f
private const val BottomAreaRatio = 0.72f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    title: String,
    model: String,
    thinking: Boolean,
    webSearch: Boolean,
    apiKeyBlank: Boolean,
    memoryNotice: String?,
    webSearchStatus: String?,
    retryNotice: String?,
    visibleCount: Int,
    showMenu: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var moreMenu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val edgePx = with(LocalDensity.current) { EdgeSwipeDp.toPx() }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    var dx = 0f
                    do {
                        val event = awaitPointerEvent()
                        dx += event.changes.firstOrNull()?.positionChange()?.x ?: 0f
                    } while (event.changes.any { it.pressed })
                    when {
                        dx > EdgeSwipeMinDx && startX < edgePx -> onMenuClick()
                        dx < -EdgeSwipeMinDx && startX > size.width - edgePx ->
                            vm.goToPreviousConversation()
                    }
                }
            }
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        ModelInfo.all.firstOrNull { it.id == model }?.name ?: model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                if (showMenu) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = moreMenu,
                        onDismissRequest = { moreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享会话") },
                            onClick = {
                                shareConversation(context, title, messages)
                                moreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                renameText = title
                                renameDialog = true
                                moreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除会话") },
                            onClick = {
                                vm.currentConvId.value?.let { vm.deleteConversation(it) }
                                moreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("新建对话") },
                            onClick = {
                                vm.newConversation()
                                moreMenu = false
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (messages.isEmpty() && !isStreaming) {
            EmptyChat(
                onExample = { vm.sendMessage(it) },
                modifier = Modifier.weight(1f)
            )
        } else {
            MessageList(
                messages = messages,
                isStreaming = isStreaming,
                onNewConversation = { vm.newConversation() },
                visibleCount = visibleCount,
                onLoadMoreOlder = { vm.loadMoreOlder() },
                onDeleteMessage = { vm.deleteMessage(it) },
                modifier = Modifier.weight(1f)
            )
        }

        MessageInputBar(
            apiKeyBlank = apiKeyBlank,
            isStreaming = isStreaming,
            thinking = thinking,
            webSearch = webSearch,
            onThinkingChange = { vm.updateThinking(it) },
            onWebSearchChange = { vm.setWebSearch(it) },
            onSend = { vm.sendMessage(it) },
            onStop = { vm.stopStreaming() },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = memoryNotice != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    memoryNotice ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = webSearchStatus != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    webSearchStatus.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = retryNotice != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    retryNotice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        vm.currentConvId.value?.let {
                            vm.renameConversation(it, renameText.trim())
                        }
                    }
                    renameDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("取消") }
            }
        )
    }

}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    onNewConversation: () -> Unit,
    visibleCount: Int,
    onLoadMoreOlder: () -> Unit,
    onDeleteMessage: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }
    var userScrolling by remember { mutableStateOf(false) }
    var lastStreamProgress by remember { mutableIntStateOf(0) }
    val lastId = messages.lastOrNull()?.id
    val visibleMessages = if (visibleCount >= messages.size) {
        messages
    } else {
        messages.takeLast(visibleCount)
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 用户上翻历史时暂停自动跟随；松手且回到底部附近后恢复跟随
    LaunchedEffect(Unit) {
        // 通过 snapshotFlow 监控是否已贴近底部；用户拖动中不恢复跟随
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }.collect { atBottom ->
            if (!userScrolling) autoFollow = atBottom
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startY = down.position.y
                    var dy = 0f
                    userScrolling = true
                    autoFollow = false
                    do {
                        val event = awaitPointerEvent()
                        dy += event.changes.firstOrNull()?.positionChange()?.y ?: 0f
                    } while (event.changes.any { it.pressed })
                    userScrolling = false
                    val atTop = !listState.canScrollBackward
                    val atBottom = !listState.canScrollForward
                    val startInBottomArea = startY > size.height * BottomAreaRatio
                    when {
                        // 消息列表底部下拉（手势从屏幕下方区域开始）：新建对话
                        atBottom && startInBottomArea && dy > BottomPullMinDy -> {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onNewConversation()
                        }
                        // 消息列表顶部下拉（已上翻历史时）：平滑滚回最新消息（Telegram 式跳转）
                        atTop && !atBottom && dy > TopPullMinDy -> {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            autoFollow = true
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(lastIndex)
                                }
                            }
                        }
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            visibleMessages,
            key = { it.id },
            contentType = { it.role }
        ) { msg ->
            val isLastStreaming = msg.streaming && msg.id == messages.lastOrNull()?.id
            MessageBubble(
                msg = msg,
                onProgress = if (isLastStreaming) { p -> lastStreamProgress = p } else null,
                onDelete = { onDeleteMessage(msg.id) }
            )
        }
    }

    // 长对话分页：滚到顶部且还有更早消息时，加载上一批
    LaunchedEffect(listState, visibleCount, messages.size) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 &&
                !listState.canScrollBackward &&
                visibleCount < messages.size
        }.collect { needMore ->
            if (needMore) onLoadMoreOlder()
        }
    }

    // 平滑跟随流式输出
    LaunchedEffect(lastId) {
        if (autoFollow && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    // 打字机进度驱动滚动：窗口跟随"已显示的字"平滑下移，不跳变
    LaunchedEffect(lastStreamProgress, isStreaming) {
        if (autoFollow && isStreaming && visibleMessages.isNotEmpty()) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            if (lastVisible == null || lastVisible.index < visibleMessages.lastIndex) {
                listState.scrollToItem(visibleMessages.lastIndex)
            }
        }
    }
}

private fun shareConversation(
    context: Context,
    title: String,
    messages: List<ChatMessage>
) {
    val sb = StringBuilder()
    sb.appendLine("【$title】")
    messages.forEach { msg ->
        if (msg.content.isNotBlank()) {
            val who = if (msg.role == "user") "我" else "DeepSeek"
            sb.appendLine("$who：${msg.content}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(
        Intent.createChooser(intent, "分享对话")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun EmptyChat(
    onExample: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val examples = listOf(
        "用简单的话解释量子计算",
        "帮我写一份本周工作总结",
        "推荐一部值得看的科幻电影",
        "把这段中文翻译成英文"
    )
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "D",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "你好，我是 DeepSeek",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "有什么可以帮你的吗？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        examples.forEach { example ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .padding(vertical = 5.dp)
                    .clickable { onExample(example) }
            ) {
                Text(
                    example,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
