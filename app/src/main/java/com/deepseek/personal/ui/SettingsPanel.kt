package com.deepseek.personal.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.deepseek.personal.BuildConfig
import com.deepseek.personal.data.DeviceProfile
import com.deepseek.personal.data.ModelInfo
import com.deepseek.personal.ui.theme.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class SettingsPage {
    HOME, API_KEY, MODEL, THEME, INTERACTION, UPDATE, MODEL_DETAIL, DEVICE
}

private data class MenuEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: SettingsPage
)

/**
 * 设置：右侧大面板（88% 宽，左侧露出遮罩，书签式效果），
 * 内部层级导航：L1 首页 → L2 子设置 → L3 详情，逐级返回。
 */
@Composable
fun SettingsPanel(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var dragProgress by remember { mutableStateOf(0f) }
        val setDragProgress = remember { { v: Float -> dragProgress = v } }

        Scrim(dragProgress = dragProgress, onDismiss = onDismiss)

        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) { it },
            exit = slideOutHorizontally(tween(240)) { it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) {
                // 状态栏避让：返回按钮不再顶进状态栏区域
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    SettingsNavigator(
                        vm = vm,
                        onClose = onDismiss,
                        onDragProgress = setDragProgress
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrim(
    dragProgress: Float,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(alpha = 0.45f * (1f - dragProgress.coerceIn(0f, 1f)))
            )
            .clickable { onDismiss() }
    )
}

@Composable
private fun SettingsNavigator(
    vm: AppViewModel,
    onClose: () -> Unit,
    onDragProgress: (Float) -> Unit
) {
    val stack = remember { mutableStateListOf(SettingsPage.HOME) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxDrag = with(density) { (maxWidth * 0.92f).toPx() }

        stack.forEachIndexed { index, page ->
            val isTop = index == stack.lastIndex
            var pageDrag by remember(page) { mutableStateOf(0f) }
            val currentDrag by rememberUpdatedState(pageDrag)

            AnimatedVisibility(
                visible = isTop,
                enter = slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) { it },
                exit = fadeOut(tween(160)),
                modifier = Modifier.fillMaxSize()
            ) {
                val pageContent: @Composable () -> Unit = {
                    when (page) {
                        SettingsPage.HOME -> HomePage(
                            onPush = { stack.add(it) },
                            onClose = onClose,
                            vm = vm
                        )
                        SettingsPage.API_KEY -> ApiKeyPage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        SettingsPage.MODEL -> ModelPage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onDetail = { stack.add(SettingsPage.MODEL_DETAIL) }
                        )
                        SettingsPage.THEME -> ThemePage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        SettingsPage.INTERACTION -> InteractionPage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        SettingsPage.UPDATE -> UpdatePage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        SettingsPage.MODEL_DETAIL -> ModelDetailPage(
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        SettingsPage.DEVICE -> DevicePage(
                            vm = vm,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                    }
                }

                if (isTop) {
                    // 顶层页面：整卡跟手右滑返回（首页滑出 = 关闭面板）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(pageDrag.roundToInt(), 0) }
                            .pointerInput(page) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        pageDrag = (currentDrag + dragAmount)
                                            .coerceIn(0f, maxDrag)
                                        onDragProgress(pageDrag / maxDrag)
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            if (currentDrag > maxDrag * 0.28f) {
                                                animate(
                                                    currentDrag,
                                                    maxDrag,
                                                    animationSpec = tween(
                                                        220,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                ) { v, _ ->
                                                    pageDrag = v
                                                    onDragProgress(v / maxDrag)
                                                }
                                                onDragProgress(0f)
                                                if (stack.size > 1) {
                                                    stack.removeAt(stack.lastIndex)
                                                } else {
                                                    onClose()
                                                }
                                            } else {
                                                animate(
                                                    currentDrag,
                                                    0f,
                                                    animationSpec = tween(
                                                        260,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                ) { v, _ ->
                                                    pageDrag = v
                                                    onDragProgress(v / maxDrag)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        pageContent()
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        pageContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    onBack: (() -> Unit)?,
    onClose: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
private fun HomePage(
    vm: AppViewModel,
    onPush: (SettingsPage) -> Unit,
    onClose: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("设置", onBack = null, onClose = onClose)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            val entries = remember {
                listOf(
                    MenuEntry("API Key", "填写 DeepSeek 密钥", Icons.Filled.VpnKey, SettingsPage.API_KEY),
                    MenuEntry("模型", "V4 Flash / V4 Pro", Icons.Filled.SmartToy, SettingsPage.MODEL),
                    MenuEntry("主题", "7 款配色方案", Icons.Filled.Palette, SettingsPage.THEME),
                    MenuEntry("交互", "思考、震动等", Icons.Filled.Tune, SettingsPage.INTERACTION),
                    MenuEntry("设备与性能", "刷新率、性能识别", Icons.Filled.Bolt, SettingsPage.DEVICE),
                    MenuEntry("更新", "检查并安装新版本", Icons.Filled.SystemUpdate, SettingsPage.UPDATE)
                )
            }
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPush(entry.page) }
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirmClear = true }
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "清除所有对话历史",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("确认清除所有对话？") },
            text = { Text("全部会话将移入回收站，5 分钟后自动彻底删除；期间可在回收站恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmClear = false
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DevicePage(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { DeviceProfile.detect(context) }
    val highRefresh by vm.highRefresh.collectAsState()
    val currentRate = remember {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay?.refreshRate ?: 60f
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("设备与性能", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("性能等级", style = MaterialTheme.typography.titleSmall)
                    Text(
                        info.level,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            InfoRow("设备", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            InfoRow("处理器", info.soc)
            InfoRow("内存", "${info.ramGB} GB")
            InfoRow("最高刷新率", DeviceProfile.formatRefresh(info.maxRefreshRate))
            InfoRow("当前实际刷新率", DeviceProfile.formatRefresh(currentRate))
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("高刷新率渲染", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (info.maxRefreshRate >= 90f && currentRate >= info.maxRefreshRate - 1f)
                            "已生效：当前 ${DeviceProfile.formatRefresh(currentRate)}"
                        else if (info.maxRefreshRate >= 90f)
                            "开关已打开，但系统当前仍为 ${DeviceProfile.formatRefresh(currentRate)}"
                        else "当前设备最高 ${DeviceProfile.formatRefresh(info.maxRefreshRate)}，无需额外设置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = highRefresh,
                    onCheckedChange = { vm.setHighRefresh(it) },
                    enabled = info.maxRefreshRate >= 90f
                )
            }
            Spacer(Modifier.height(12.dp))
            if (info.maxRefreshRate >= 90f && currentRate < info.maxRefreshRate - 1f) {
                Text(
                    "系统未放行高刷新率：请到手机「设置 → 显示与亮度 → 屏幕刷新率」把本应用设为最高刷新率（iQOO 还可到「设置 → 显示与亮度 → 高刷新率应用」加入本应用），返回后重开本页确认。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                "App 会自动识别手机性能：旗舰/高刷设备启用最高刷新率渲染，让对话流式动画更流畅；标准设备保持系统默认以省电。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ApiKeyPage(vm: AppViewModel, onBack: () -> Unit) {
    val apiKey by vm.apiKey.collectAsState()
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showKey by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("API Key", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "DeepSeek API Key",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text("sk-...") },
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "仅保存在本机，不会上传；更新安装不会丢失",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.updateApiKey(keyInput.trim()); onBack() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存并返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelPage(
    vm: AppViewModel,
    onBack: () -> Unit,
    onDetail: () -> Unit
) {
    val model by vm.model.collectAsState()

    Column(Modifier.fillMaxSize()) {
        PageHeader("模型", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            ModelInfo.all.forEach { opt ->
                val selected = opt.id == model
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.updateModel(opt.id) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { vm.updateModel(opt.id) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                opt.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Normal
                            )
                            Text(
                                if (opt.id == ModelInfo.PRO)
                                    "${opt.desc} · ${opt.price} · 联网搜索自动用 Flash"
                                else "${opt.desc} · ${opt.price}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDetail,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("查看模型详情 →")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelDetailPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("模型详情", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("DeepSeek V4 Flash", fontWeight = FontWeight.Bold)
            Text(
                "快速、便宜，支持联网搜索（Responses API）、1M 上下文。" +
                    "输入 \$0.14/百万 token，输出 \$0.28/百万 token。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("DeepSeek V4 Pro", fontWeight = FontWeight.Bold)
            Text(
                "最强推理，1.6T 总参 / 49B 激活，适合复杂任务。" +
                    "输入 \$0.435/百万 token，输出 \$0.87/百万 token。" +
                    "暂不支持联网搜索（官方未开放）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemePage(vm: AppViewModel, onBack: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val themeMode by vm.themeMode.collectAsState()

    Column(Modifier.fillMaxSize()) {
        PageHeader("主题", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppTheme.entries.forEach { t ->
                    val selected = t == theme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.setTheme(t) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(t.primaryLight)
                                .then(
                                    if (selected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "每个主题自动适配系统深色/浅色模式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("外观模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = themeMode == value,
                        onClick = { vm.setThemeMode(value) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InteractionPage(vm: AppViewModel, onBack: () -> Unit) {
    val thinking by vm.thinking.collectAsState()
    val effort by vm.reasoningEffort.collectAsState()
    val vibrate by vm.vibrateOnOutput.collectAsState()

    Column(Modifier.fillMaxSize()) {
        PageHeader("交互", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("深度思考", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "生成答案前先推理（默认关闭）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = thinking,
                    onCheckedChange = { vm.updateThinking(it) }
                )
            }
            if (thinking) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "思考力度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("low" to "低", "high" to "高", "max" to "极致").forEach { (value, label) ->
                        FilterChip(
                            selected = effort == value,
                            onClick = { vm.updateReasoningEffort(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("打字震动", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "AI 输出文字时轻微震动反馈",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = vibrate,
                    onCheckedChange = { vm.setVibrateOnOutput(it) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UpdatePage(vm: AppViewModel, onBack: () -> Unit) {
    val checkingUpdate by vm.checkingUpdate.collectAsState()
    val updateInfo by vm.updateInfo.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val updateMessage by vm.updateMessage.collectAsState()

    Column(Modifier.fillMaxSize()) {
        PageHeader("更新", onBack)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("检查更新", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when {
                            checkingUpdate -> "正在检查…"
                            updateInfo != null -> "发现新版本 v${updateInfo?.versionName}"
                            else -> "当前版本 v${BuildConfig.VERSION_NAME}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (checkingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Button(
                        onClick = { vm.checkForUpdate() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("检查")
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                updateMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { if (!downloading) vm.dismissUpdate() },
            title = { Text("发现新版本 v${updateInfo?.versionName}") },
            text = {
                Column {
                    val notes = updateInfo?.notes.orEmpty()
                    if (notes.isBlank()) {
                        Text("有新版本可用")
                    } else {
                        Text(
                            "本次更新内容：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        notes.split("\n").filter { it.isNotBlank() }.forEach { line ->
                            Text(
                                "• $line",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "下载中 ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (downloading) {
                    TextButton(onClick = { vm.cancelDownload() }) {
                        Text(
                            "取消下载",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    TextButton(onClick = { vm.startUpdate() }) {
                        Text("下载并安装")
                    }
                }
            },
            dismissButton = {
                if (!downloading) {
                    TextButton(onClick = { vm.dismissUpdate() }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
