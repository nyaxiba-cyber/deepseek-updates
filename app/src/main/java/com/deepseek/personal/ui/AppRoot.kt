package com.deepseek.personal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.deepseek.personal.ui.theme.AppTheme
import com.deepseek.personal.ui.theme.DeepSeekTheme
import kotlinx.coroutines.delay

@Composable
fun AppRoot(vm: AppViewModel) {
    val theme by vm.theme.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    DeepSeekTheme(theme = theme, darkTheme = darkTheme) {
        AppRootInner(vm)
    }
}

@Composable
private fun AppRootInner(vm: AppViewModel) {
    val conversations by vm.conversations.collectAsState()
    val currentId by vm.currentConvId.collectAsState()
    val messages by vm.messages.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val model by vm.model.collectAsState()
    val thinking by vm.thinking.collectAsState()
    val memoryNotice by vm.memoryNotice.collectAsState()
    val webSearch by vm.webSearch.collectAsState()
    val webSearchStatus by vm.webSearchStatus.collectAsState()
    val retryNotice by vm.retryNotice.collectAsState()
    val visibleCount by vm.visibleCount.collectAsState()
    val trashPending by vm.trashPending.collectAsState()
    val trash by vm.trash.collectAsState()

    var sidebarVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var trashNoticeVisible by remember { mutableStateOf(false) }

    // 主对话页手势激活条件：没有任何覆盖层（侧栏/设置/记忆/反馈/回收站）打开。
    // 激活时把左右边缘 40dp 排除出系统返回手势，让「左缘右滑开侧栏/右缘左滑上一会话」生效。
    val chatGesturesActive =
        !sidebarVisible && !showSettings && !showMemory && !showFeedback && !showTrash

    val currentTitle = conversations.firstOrNull { it.id == currentId }?.title ?: "新对话"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp

        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Sidebar(
                    conversations = conversations,
                    currentId = currentId,
                    model = model,
                    onSelect = { vm.selectConversation(it) },
                    onNew = { vm.newConversation() },
                    onDelete = { vm.deleteConversation(it) },
                    onMemory = { showMemory = true },
                    onFeedback = { showFeedback = true },
                    onTrash = { showTrash = true },
                    onSettings = { showSettings = true },
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                )
                VerticalDivider()
            }

            ChatScreen(
                vm = vm,
                messages = messages,
                isStreaming = isStreaming,
                title = currentTitle,
                model = model,
                thinking = thinking,
                webSearch = webSearch,
                apiKeyBlank = apiKey.isBlank(),
                memoryNotice = memoryNotice,
                webSearchStatus = webSearchStatus,
                retryNotice = retryNotice,
                visibleCount = visibleCount,
                showMenu = !wide,
                gesturesActive = chatGesturesActive,
                onMenuClick = { sidebarVisible = true },
                modifier = Modifier.weight(1f)
            )
        }

        if (!wide) {
            AnimatedVisibility(
                visible = sidebarVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { sidebarVisible = false }
                )
            }

            AnimatedVisibility(
                visible = sidebarVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) { -it } + fadeIn(tween(280)),
                exit = slideOutHorizontally(
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) { -it } + fadeOut(tween(220)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(1f)
            ) {
                Sidebar(
                    conversations = conversations,
                    currentId = currentId,
                    model = model,
                    onSelect = {
                        vm.selectConversation(it)
                        sidebarVisible = false
                    },
                    onNew = {
                        vm.newConversation()
                        sidebarVisible = false
                    },
                    onDelete = { vm.deleteConversation(it) },
                    onMemory = {
                        showMemory = true
                        sidebarVisible = false
                    },
                    onFeedback = {
                        showFeedback = true
                        sidebarVisible = false
                    },
                    onTrash = {
                        showTrash = true
                        sidebarVisible = false
                    },
                    onSettings = {
                        showSettings = true
                        sidebarVisible = false
                    },
                    onClose = { sidebarVisible = false },
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                )
            }
        }

        // 删除会话后的轻量提示：右上角非阻塞浮层，不弹窗、不倒计时、可继续操作
        LaunchedEffect(trashPending) {
            if (trashPending != null) {
                trashNoticeVisible = true
                delay(3500)
                trashNoticeVisible = false
            }
        }
        AnimatedVisibility(
            visible = trashNoticeVisible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 68.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    "会话已移入回收站，5 分钟后自动彻底删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showSettings) {
        SettingsPanel(
            vm = vm,
            onDismiss = { showSettings = false }
        )
    }

    if (showMemory) {
        MemorySheet(
            vm = vm,
            onDismiss = { showMemory = false }
        )
    }

    if (showFeedback) {
        FeedbackSheet(
            onDismiss = { showFeedback = false }
        )
    }

    if (showTrash) {
        TrashSheet(
            trash = trash,
            onRestore = { vm.restoreConversation(it) },
            onDeleteNow = { vm.deleteTrashImmediately(it) },
            onClearAll = { vm.clearTrash() },
            onDismiss = { showTrash = false }
        )
    }

}
