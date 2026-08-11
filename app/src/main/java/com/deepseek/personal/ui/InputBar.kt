package com.deepseek.personal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MessageInputBar(
    apiKeyBlank: Boolean,
    isStreaming: Boolean,
    thinking: Boolean,
    webSearch: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val speechAvailable = remember {
        try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    // ===== 语音输入 =====
    val speechRecognizer = remember {
        try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
    }
    var recognizing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val currentOnVoiceResult by rememberUpdatedState { result: String ->
        text = if (text.isBlank()) result else text + result
    }

    fun setRecognizing(v: Boolean) { recognizing = v }
    fun setPartial(v: String) { partialText = v }
    fun setVoiceError(v: String?) { voiceError = v }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { setRecognizing(true) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { setRecognizing(false) }
            override fun onError(error: Int) {
                setRecognizing(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请重试"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要录音权限"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务忙，请稍后"
                    SpeechRecognizer.ERROR_CLIENT -> "当前设备不支持语音输入"
                    else -> "语音识别失败"
                }
                setVoiceError(msg)
            }
            override fun onResults(results: Bundle?) {
                val r = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!r.isNullOrBlank()) currentOnVoiceResult(r)
                setPartial("")
                setRecognizing(false)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val r = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                setPartial(r ?: "")
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startListening() {
        setPartial("")
        setVoiceError(null)
        speechRecognizer?.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            setVoiceError("语音启动失败")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
    }

    val micModifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    if (speechRecognizer == null) {
                        setVoiceError("当前设备不支持语音输入")
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            startListening()
                            tryAwaitRelease()
                            stopListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            )
        }

    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            kotlinx.coroutines.delay(2500)
            voiceError = null
        }
    }
    // ===== 语音输入结束 =====

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding()
    ) {
        if (apiKeyBlank) {
            Text(
                "未设置 API Key，请在侧栏「设置」中填写",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // 语音识别状态条
        if (recognizing || partialText.isNotBlank() || voiceError != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            voiceError != null -> voiceError.orEmpty()
                            partialText.isNotBlank() -> partialText
                            else -> "正在聆听…（松开结束）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (voiceError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleChip(
                selected = thinking,
                label = "深度思考",
                icon = {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onToggle = { onThinkingChange(!thinking) }
            )
            ToggleChip(
                selected = webSearch,
                label = "联网搜索",
                icon = {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onToggle = { onWebSearchChange(!webSearch) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (speechAvailable) {
                // 语音按钮（仅设备支持语音识别时显示）
                Box(
                    modifier = micModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "按住说话",
                        tint = if (recognizing) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                "给 DeepSeek 发送消息",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isStreaming) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                    .clickable(enabled = text.isNotBlank() || isStreaming) {
                        if (isStreaming) {
                            onStop()
                        } else {
                            onSend(text)
                            text = ""
                            focusManager.clearFocus()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isStreaming) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "停止生成",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (text.isNotBlank()) Color.White
                        else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onToggle: () -> Unit,
    enabled: Boolean = true
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (enabled) 1f else 0.55f
        )
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(modifier = Modifier.size(16.dp)) {
                icon()
            }
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}
