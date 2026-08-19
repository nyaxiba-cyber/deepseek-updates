package com.deepseek.personal.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.deepseek.personal.BuildConfig
import com.deepseek.personal.data.ChatMessage
import com.deepseek.personal.data.Conversation
import com.deepseek.personal.data.DeepSeekClient
import com.deepseek.personal.data.HistoryStore
import com.deepseek.personal.data.LogCollector
import com.deepseek.personal.data.Memory
import com.deepseek.personal.data.ModelInfo
import com.deepseek.personal.data.PgyerUpdater
import com.deepseek.personal.data.SettingsStore
import com.deepseek.personal.data.StreamResult
import com.deepseek.personal.data.UpdateInfo
import com.deepseek.personal.data.UpdateManager
import com.deepseek.personal.ui.theme.AppTheme
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrashPending(
    val conversationId: Long,
    val title: String,
    val deleteAt: Long
)

class AppViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

    companion object {
        const val TRASH_TTL = 5 * 60 * 1000L
        const val DEFAULT_VISIBLE = 120
        const val VISIBLE_PAGE_SIZE = 120
    }

    private val history = HistoryStore(app)
    private val settings = SettingsStore(app)
    private val client = DeepSeekClient()
    private val updateManager = UpdateManager()
    private val pgyerUpdater = PgyerUpdater()
    private val idGen = AtomicLong(System.currentTimeMillis())
    private val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _currentConvId = MutableStateFlow<Long?>(null)
    val currentConvId: StateFlow<Long?> = _currentConvId

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _model = MutableStateFlow(ModelInfo.FLASH)
    val model: StateFlow<String> = _model

    private val _thinking = MutableStateFlow(true)
    val thinking: StateFlow<Boolean> = _thinking

    private val _reasoningEffort = MutableStateFlow("high")
    val reasoningEffort: StateFlow<String> = _reasoningEffort

    private val _memories = MutableStateFlow<List<Memory>>(emptyList())
    val memories: StateFlow<List<Memory>> = _memories

    private val _autoMemory = MutableStateFlow(true)
    val autoMemory: StateFlow<Boolean> = _autoMemory

    private val _memoryNotice = MutableStateFlow<String?>(null)
    val memoryNotice: StateFlow<String?> = _memoryNotice

    private val _retryNotice = MutableStateFlow<String?>(null)
    val retryNotice: StateFlow<String?> = _retryNotice

    private val _visibleCount = MutableStateFlow(DEFAULT_VISIBLE)
    val visibleCount: StateFlow<Int> = _visibleCount

    private val _theme = MutableStateFlow(AppTheme.DEEPSEEK)
    val theme: StateFlow<AppTheme> = _theme

    private val _webSearch = MutableStateFlow(false)
    val webSearch: StateFlow<Boolean> = _webSearch

    private val _webSearchStatus = MutableStateFlow<String?>(null)
    val webSearchStatus: StateFlow<String?> = _webSearchStatus

    private val _vibrateOnOutput = MutableStateFlow(true)
    val vibrateOnOutput: StateFlow<Boolean> = _vibrateOnOutput

    private val _highRefresh = MutableStateFlow(true)
    val highRefresh: StateFlow<Boolean> = _highRefresh

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode

    private val _trashPending = MutableStateFlow<TrashPending?>(null)
    val trashPending: StateFlow<TrashPending?> = _trashPending

    private val _trash = MutableStateFlow<List<Pair<Conversation, Long>>>(emptyList())
    val trash: StateFlow<List<Pair<Conversation, Long>>> = _trash

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage

    private var streamJob: Job? = null
    private var extractJob: Job? = null
    private var downloadJob: Job? = null
    private var currentTarget: File? = null
    private var lastVibrateAt = 0L

    private val pendingContent = ConcurrentLinkedQueue<String>()
    private val pendingReasoning = ConcurrentLinkedQueue<String>()
    private var flushJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            settings.migrateLegacyKeyIfNeeded()
        }
        viewModelScope.launch { settings.apiKey.collect { _apiKey.value = it } }
        viewModelScope.launch { settings.model.collect { _model.value = it } }
        viewModelScope.launch { settings.thinking.collect { _thinking.value = it } }
        viewModelScope.launch { settings.reasoningEffort.collect { _reasoningEffort.value = it } }
        viewModelScope.launch { settings.autoMemory.collect { _autoMemory.value = it } }
        viewModelScope.launch {
            settings.themeKey.collect { _theme.value = AppTheme.fromKey(it) }
        }
        viewModelScope.launch { settings.webSearch.collect { _webSearch.value = it } }
        viewModelScope.launch { settings.vibrateOnOutput.collect { _vibrateOnOutput.value = it } }
        viewModelScope.launch { settings.highRefresh.collect { _highRefresh.value = it } }
        viewModelScope.launch { settings.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch(Dispatchers.IO) {
            history.purgeExpiredTrash(System.currentTimeMillis(), TRASH_TTL)
            _conversations.value = history.listConversations()
            _trash.value = history.listTrash()
            _memories.value = history.listMemories()
        }
        // 进程被杀后恢复当前会话
        savedState.get<Long>("conv_id")?.let { restoredId ->
            _currentConvId.value = restoredId
            viewModelScope.launch(Dispatchers.IO) {
                _messages.value = history.loadMessages(restoredId)
            }
        }
        viewModelScope.launch {
            _currentConvId.collect { id ->
                if (id != null) savedState["conv_id"] = id
            }
        }
        // 每分钟自动彻底清空回收站中过期项
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                withContext(Dispatchers.IO) {
                    history.purgeExpiredTrash(System.currentTimeMillis(), TRASH_TTL)
                    _trash.value = history.listTrash()
                }
                _trashPending.value?.let { p ->
                    if (System.currentTimeMillis() >= p.deleteAt) {
                        _trashPending.value = null
                    }
                }
            }
        }
    }

    fun newConversation() {
        if (_isStreaming.value) return
        _visibleCount.value = DEFAULT_VISIBLE
        viewModelScope.launch(Dispatchers.IO) {
            val id = history.createConversation("新对话", _model.value, _thinking.value)
            _conversations.value = history.listConversations()
            _currentConvId.value = id
            _messages.value = emptyList()
        }
    }

    fun selectConversation(id: Long) {
        if (_isStreaming.value) stopStreaming()
        _visibleCount.value = DEFAULT_VISIBLE
        _currentConvId.value = id
        viewModelScope.launch(Dispatchers.IO) {
            _messages.value = history.loadMessages(id)
        }
    }

    /**
     * 右边缘左滑：切换到上一个会话（循环到最后）。
     */
    fun goToPreviousConversation() {
        val list = _conversations.value
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _currentConvId.value }
        val target = if (idx > 0) list[idx - 1] else list.last()
        if (target.id != _currentConvId.value) {
            selectConversation(target.id)
        }
    }

    fun deleteConversation(id: Long) {
        if (_isStreaming.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val title = _conversations.value.firstOrNull { it.id == id }?.title ?: "会话"
            history.deleteConversation(id)
            _conversations.value = history.listConversations()
            _trash.value = history.listTrash()
            _trashPending.value = TrashPending(
                conversationId = id,
                title = title,
                deleteAt = System.currentTimeMillis() + TRASH_TTL
            )
            if (_currentConvId.value == id) {
                _currentConvId.value = null
                _messages.value = emptyList()
                _visibleCount.value = DEFAULT_VISIBLE
            }
        }
    }

    fun restoreConversation(id: Long) {
        _visibleCount.value = DEFAULT_VISIBLE
        viewModelScope.launch(Dispatchers.IO) {
            history.restoreConversation(id)
            _conversations.value = history.listConversations()
            _trash.value = history.listTrash()
            if (_trashPending.value?.conversationId == id) {
                _trashPending.value = null
            }
            _currentConvId.value = id
            _messages.value = history.loadMessages(id)
        }
    }

    fun deleteTrashImmediately(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            history.deleteConversationHard(id)
            _trash.value = history.listTrash()
            if (_trashPending.value?.conversationId == id) {
                _trashPending.value = null
            }
        }
    }

    fun clearTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _trash.value.forEach { (conv, _) ->
                history.deleteConversationHard(conv.id)
            }
            _trash.value = emptyList()
            _trashPending.value = null
        }
    }

    /** 删除单条消息（同步从对话上下文移除）。 */
    fun deleteMessage(id: Long) {
        if (_isStreaming.value) return
        val convId = _currentConvId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            history.deleteMessage(id)
            history.touchConversation(convId)
            _messages.update { list -> list.filterNot { it.id == id } }
            _conversations.value = history.listConversations()
        }
    }

    fun clearMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            history.deleteAllMemories()
            _memories.value = history.listMemories()
        }
    }

    /** 长对话分页：向上滚动到顶部时加载更早的消息。 */
    fun loadMoreOlder() {
        val more = min(_visibleCount.value + VISIBLE_PAGE_SIZE, _messages.value.size)
        if (more != _visibleCount.value) _visibleCount.value = more
    }

    fun renameConversation(id: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            history.renameConversation(id, trimmed)
            _conversations.value = history.listConversations()
        }
    }

    fun clearAll() {
        streamJob?.cancel()
        _isStreaming.value = false
        _visibleCount.value = DEFAULT_VISIBLE
        viewModelScope.launch(Dispatchers.IO) {
            // 全部移入回收站（软删除），5 分钟后彻底清理
            _conversations.value.forEach { history.deleteConversation(it.id) }
            _conversations.value = emptyList()
            _trash.value = history.listTrash()
            _currentConvId.value = null
            _messages.value = emptyList()
            _trashPending.value = null
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { settings.setApiKey(key) }
    }

    fun updateModel(modelId: String) {
        viewModelScope.launch { settings.setModel(modelId) }
    }

    fun updateThinking(enabled: Boolean) {
        viewModelScope.launch { settings.setThinking(enabled) }
    }

    fun updateReasoningEffort(level: String) {
        viewModelScope.launch { settings.setReasoningEffort(level) }
    }

    fun updateAutoMemory(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoMemory(enabled) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setThemeKey(theme.key) }
    }

    fun setWebSearch(enabled: Boolean) {
        viewModelScope.launch { settings.setWebSearch(enabled) }
    }

    fun setVibrateOnOutput(enabled: Boolean) {
        viewModelScope.launch { settings.setVibrateOnOutput(enabled) }
    }

    fun setHighRefresh(enabled: Boolean) {
        viewModelScope.launch { settings.setHighRefresh(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun addMemory(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val exists = _memories.value.any { it.content == trimmed }
            if (exists) return@launch
            history.insertMemory(trimmed)
            _memories.value = history.listMemories()
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            history.deleteMemory(id)
            _memories.value = history.listMemories()
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return
        if (_apiKey.value.isBlank()) {
            val tipId = nextId()
            _messages.update {
                it + ChatMessage(
                    id = tipId,
                    role = "assistant",
                    error = "请先在侧栏「设置」中填写 API Key",
                    timestamp = System.currentTimeMillis()
                )
            }
            viewModelScope.launch {
                delay(5000)
                _messages.update { list -> list.filterNot { m -> m.id == tipId } }
            }
            return
        }

        viewModelScope.launch {
            var convId = _currentConvId.value
            val isFirstMessage: Boolean
            if (convId == null) {
                convId = withContext(Dispatchers.IO) {
                    history.createConversation(trimmed.take(20), _model.value, _thinking.value)
                }
                _currentConvId.value = convId
                isFirstMessage = true
            } else {
                isFirstMessage = _messages.value.isEmpty()
            }

            val userMsg = ChatMessage(id = nextId(), role = "user", content = trimmed)
            val asstMsg = ChatMessage(id = nextId(), role = "assistant", streaming = true)
            _messages.update { it + userMsg + asstMsg }
            withContext(Dispatchers.IO) {
                history.insertMessage(convId, userMsg)
                if (isFirstMessage) {
                    history.renameConversation(convId, trimmed.take(20))
                    _conversations.value = history.listConversations()
                }
            }
            _isStreaming.value = true

            streamJob = launch {
                val memoryText = _memories.value
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n") { "- ${it.content}" }
                val systemPrompt = memoryText?.let {
                    "以下是用户的长期记忆，回答时请自然运用这些信息：\n$it"
                }
                val historyMessages = _messages.value
                    .filter { !it.streaming && it.error == null }
                    .map { it.role to it.content }

                // 流式中断自动续写：带着已生成的内容重新请求，最多重试 2 次
                val maxRetries = 2
                var attempts = 0
                var error: String? = null
                while (true) {
                    if (attempts > 0) {
                        _retryNotice.value = "连接中断，正在自动续写…"
                        delay(600)
                    }
                    val partial = _messages.value
                        .firstOrNull { it.id == asstMsg.id }
                        ?.content.orEmpty()
                    val requestMessages = if (partial.isNotBlank()) {
                        historyMessages.filterNot { it.second.isBlank() } +
                            ("assistant" to partial)
                    } else {
                        historyMessages
                    }
                    val result = if (_webSearch.value) {
                        _webSearchStatus.value = if (attempts == 0) {
                            "正在联网搜索…"
                        } else {
                            "正在联网搜索…（续写）"
                        }
                        val r = withContext(Dispatchers.IO) {
                            client.streamResponses(
                                apiKey = _apiKey.value,
                                instructions = systemPrompt,
                                messages = requestMessages,
                                webSearch = true,
                                onReasoning = { enqueueDelta(asstMsg.id, it, reasoning = true) },
                                onContent = { enqueueDelta(asstMsg.id, it, reasoning = false) },
                                onSearchStatus = { status ->
                                    _webSearchStatus.value = status.ifBlank { null }
                                }
                            )
                        }
                        _webSearchStatus.value = null
                        r
                    } else {
                        withContext(Dispatchers.IO) {
                            client.streamChat(
                                DeepSeekClient.ChatRequest(
                                    apiKey = _apiKey.value,
                                    model = _model.value,
                                    thinking = _thinking.value,
                                    reasoningEffort = _reasoningEffort.value,
                                    messages = requestMessages,
                                    systemPrompt = systemPrompt
                                ),
                                onReasoning = { enqueueDelta(asstMsg.id, it, reasoning = true) },
                                onContent = { enqueueDelta(asstMsg.id, it, reasoning = false) }
                            )
                        }
                    }
                    when (result) {
                        is StreamResult.Done -> {
                            error = null
                            break
                        }
                        is StreamResult.Failed -> {
                            error = result.message
                            break
                        }
                        is StreamResult.Interrupted -> {
                            attempts++
                            if (attempts > maxRetries) {
                                error = "连接中断，自动续写 $maxRetries 次后仍失败，请重试"
                                break
                            }
                        }
                    }
                }
                _retryNotice.value = null

                // 等最后一波增量刷新到 UI
                while (pendingContent.isNotEmpty() || pendingReasoning.isNotEmpty()) {
                    delay(30)
                }
                _isStreaming.value = false

                val current = _messages.value.firstOrNull { it.id == asstMsg.id } ?: return@launch
                val finalMsg = current.copy(streaming = false, error = error)
                if (error != null) {
                    LogCollector.logError("API 请求失败: $error")
                }
                _messages.update { list -> list.map { if (it.id == asstMsg.id) finalMsg else it } }
                withContext(Dispatchers.IO) {
                    if (finalMsg.content.isNotBlank() || finalMsg.reasoning.isNotBlank()) {
                        history.insertMessage(convId, finalMsg)
                    }
                    history.touchConversation(convId)
                    _conversations.value = history.listConversations()
                }
                if (error == null) maybeExtractMemory()
            }
        }
    }

    /**
     * 对话完成后自动提取长期记忆（后台执行，不阻塞界面，成本极低）。
     */
    private fun maybeExtractMemory() {
        if (!_autoMemory.value) return
        if (extractJob?.isActive == true) return
        extractJob = viewModelScope.launch {
            val recent = _messages.value
                .filter { it.error == null && it.content.isNotBlank() }
                .takeLast(4)
                .map { it.role to it.content }
            if (recent.isEmpty()) return@launch

            val extracted = withContext(Dispatchers.IO) {
                client.extractMemories(_apiKey.value, recent)
            }
            val existing = _memories.value
            val toAdd = extracted
                .map { it.trim() }
                .filter { newOne ->
                    newOne.isNotEmpty() &&
                        existing.none { it.content.contains(newOne) || newOne.contains(it.content) }
                }
            if (toAdd.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                toAdd.forEach { history.insertMemory(it) }
                _memories.value = history.listMemories()
            }
            val first = toAdd.first()
            _memoryNotice.value =
                "已记住：$first" + if (toAdd.size > 1) "（共 ${toAdd.size} 条）" else ""
            viewModelScope.launch {
                delay(5000)
                _memoryNotice.value = null
            }
        }
    }

    fun stopStreaming() {
        if (!_isStreaming.value) return
        streamJob?.cancel()
        streamJob = null
        _isStreaming.value = false
        _retryNotice.value = null
        val convId = _currentConvId.value ?: return
        val streamingMsg = _messages.value.firstOrNull { it.streaming }
        if (streamingMsg == null) return
        val finalMsg = streamingMsg.copy(streaming = false)
        _messages.update { list -> list.map { if (it.id == streamingMsg.id) finalMsg else it } }
        viewModelScope.launch(Dispatchers.IO) {
            if (finalMsg.content.isNotBlank() || finalMsg.reasoning.isNotBlank()) {
                history.insertMessage(convId, finalMsg)
                history.touchConversation(convId)
                _conversations.value = history.listConversations()
            } else {
                _messages.update { list -> list.filterNot { it.id == streamingMsg.id } }
            }
        }
    }

    private fun nextId(): Long = idGen.incrementAndGet()

    fun checkForUpdate() {
        if (_checkingUpdate.value || _downloading.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            val result = pgyerUpdater.check(
                BuildConfig.PGYER_API_KEY,
                BuildConfig.PGYER_APP_KEY
            )
            _checkingUpdate.value = false
            result.onSuccess { info ->
                if (info.versionCode > BuildConfig.VERSION_CODE && info.downloadUrl.isNotBlank()) {
                    _updateInfo.value = info
                } else {
                    showUpdateMessage("当前已是最新版本（v${BuildConfig.VERSION_NAME}）")
                }
            }.onFailure {
                showUpdateMessage("检查更新失败：${it.message}")
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun startUpdate() {
        val info = _updateInfo.value ?: return
        if (_downloading.value) return
        val ctx = getApplication<Application>()
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            showUpdateMessage("需要允许「安装未知应用」，请在系统设置中开启")
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (_: Exception) {
                // 部分 ROM 无此设置入口，忽略
            }
            return
        }
        // 已下载过完整安装包（按版本号缓存）→ 直接进入安装，不重复下载
        val cached = File(ctx.cacheDir, "deepseek-update-${info.versionName}.apk")
        if (cached.exists() && cached.length() > 1_000_000) {
            _updateInfo.value = null
            installApk(ctx, cached)
            return
        }
        downloadJob = viewModelScope.launch {
            _downloading.value = true
            _downloadProgress.value = 0f
            val target = cached
            currentTarget = target
            val result = updateManager.download(info.downloadUrl, target) { p ->
                _downloadProgress.value = p
            }
            _downloading.value = false
            currentTarget = null
            result.onSuccess { file ->
                _updateInfo.value = null
                installApk(ctx, file)
            }.onFailure {
                target.delete()
                showUpdateMessage("下载失败：${it.message}")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloading.value = false
        _downloadProgress.value = 0f
        currentTarget?.delete()
        currentTarget = null
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            showUpdateMessage("无法打开安装界面：${e.message}")
        }
    }

    private fun showUpdateMessage(msg: String) {
        _updateMessage.value = msg
        viewModelScope.launch {
            delay(5000)
            _updateMessage.value = null
        }
    }

    /**
     * 流式增量先进入队列，由后台协程每 20ms 合并刷新一次，
     * 避免每个 token 都触发一次重组，让打字机效果更平滑。
     */
    private fun enqueueDelta(msgId: Long, delta: String, reasoning: Boolean) {
        (if (reasoning) pendingReasoning else pendingContent).add(delta)
        if (flushJob?.isActive != true) {
            flushJob = viewModelScope.launch {
                while (true) {
                    delay(20)
                    val content = drain(pendingContent)
                    val reason = drain(pendingReasoning)
                    if (content.isNotEmpty() || reason.isNotEmpty()) {
                        val c = content
                        val r = reason
                        _messages.update { list ->
                            list.map {
                                if (it.id == msgId) {
                                    it.copy(
                                        content = it.content + c,
                                        reasoning = it.reasoning + r
                                    )
                                } else it
                            }
                        }
                        if (c.isNotEmpty()) hapticTick()
                    }
                    if (pendingContent.isEmpty() && pendingReasoning.isEmpty()) break
                }
            }
        }
    }

    private fun drain(queue: ConcurrentLinkedQueue<String>): String {
        val sb = StringBuilder()
        while (true) {
            val s = queue.poll() ?: break
            sb.append(s)
        }
        return sb.toString()
    }

    /**
     * 流式输出震动反馈（ChatGPT 式打字机震动），约 90ms 一次短震。
     */
    private fun hapticTick() {
        if (!_vibrateOnOutput.value) return
        val now = System.currentTimeMillis()
        if (now - lastVibrateAt < 90) return
        lastVibrateAt = now
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        8,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(8)
            }
        } catch (_: Exception) {
        }
    }
}
