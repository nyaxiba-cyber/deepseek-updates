package com.deepseek.personal.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 流式调用结果：区分正常完成、可重试的中断、失败。
 */
sealed class StreamResult {
    object Done : StreamResult()
    data class Interrupted(val reason: String = "连接中断") : StreamResult()
    data class Failed(val message: String) : StreamResult()
}

/**
 * DeepSeek 官方 API 流式客户端（chat/completions + SSE）
 */
class DeepSeekClient {

    data class ChatRequest(
        val apiKey: String,
        val model: String,
        val thinking: Boolean,
        val reasoningEffort: String,
        val messages: List<Pair<String, String>>,
        val systemPrompt: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 流式调用。成功返回 null，失败返回用户可读的错误信息。
     * onReasoning / onContent 在 IO 线程回调。
     */
    suspend fun streamChat(
        req: ChatRequest,
        onReasoning: (String) -> Unit,
        onContent: (String) -> Unit
    ): StreamResult = suspendCancellableCoroutine { cont ->
        val httpReq = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer ${req.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(buildBody(req).toRequestBody(jsonType))
            .build()

        val factory = EventSources.createFactory(client)
        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
        var completed = false
        val es = factory.newEventSource(httpReq, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (cont.isCancelled) return
                if (data == "[DONE]") {
                    completed = true
                    eventSource.cancel()
                    return
                }
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices") ?: return
                    if (choices.length() == 0) return
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return
                    val rc = delta.optString("reasoning_content", "")
                    val c = delta.optString("content", "")
                    if (rc.isNotEmpty()) onReasoning(rc)
                    if (c.isNotEmpty()) onContent(c)
                } catch (_: Exception) {
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!cont.isCancelled && resumed.compareAndSet(false, true)) {
                    cont.resume(
                        if (completed) StreamResult.Done
                        else StreamResult.Interrupted()
                    )
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                if (cont.isCancelled) return
                val err = if (response != null && !response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    parseError(body) ?: "请求失败（HTTP ${response.code}）"
                } else {
                    "网络错误：${t?.message ?: "连接失败"}"
                }
                if (resumed.compareAndSet(false, true)) cont.resume(StreamResult.Failed(err))
            }
        })
        cont.invokeOnCancellation { es.cancel() }
    }

    /**
     * Responses API 流式调用：支持 web_search 工具（仅 deepseek-v4-flash），
     * 固定关闭思考模式（reasoning.effort = none），保持 Chat 模式。
     */
    suspend fun streamResponses(
        apiKey: String,
        instructions: String?,
        messages: List<Pair<String, String>>,
        webSearch: Boolean,
        onReasoning: (String) -> Unit,
        onContent: (String) -> Unit,
        onSearchStatus: (String) -> Unit
    ): StreamResult = suspendCancellableCoroutine { cont ->
        val arr = JSONArray()
        for ((role, content) in messages) {
            if (content.isBlank()) continue
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val root = JSONObject()
            .put("model", ModelInfo.FLASH)
            .put("input", arr)
            .put("stream", true)
            .put("reasoning", JSONObject().put("effort", "none"))
        if (!instructions.isNullOrBlank()) {
            root.put("instructions", instructions)
        }
        if (webSearch) {
            root.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        }

        val httpReq = Request.Builder()
            .url("https://api.deepseek.com/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(root.toString().toRequestBody(jsonType))
            .build()

        val factory = EventSources.createFactory(client)
        val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
        var completed = false
        val es = factory.newEventSource(httpReq, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (cont.isCancelled) return
                try {
                    val json = JSONObject(data)
                    val evt = json.optString("type", "")
                    when (evt) {
                        "response.output_text.delta" -> {
                            json.optString("delta", "").takeIf { it.isNotEmpty() }?.let(onContent)
                        }
                        "response.reasoning_text.delta" -> {
                            json.optString("delta", "").takeIf { it.isNotEmpty() }?.let(onReasoning)
                        }
                        "response.web_search_call.in_progress",
                        "response.web_search_call.searching" -> onSearchStatus("正在联网搜索…")
                        "response.web_search_call.completed" -> onSearchStatus("")
                        "response.completed" -> {
                            completed = true
                            eventSource.cancel()
                        }
                        "response.failed" -> {
                            val err = json.optJSONObject("error")
                                ?.optString("message") ?: "生成失败"
                            if (resumed.compareAndSet(false, true)) {
                                cont.resume(StreamResult.Failed(err))
                            }
                            eventSource.cancel()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!cont.isCancelled && resumed.compareAndSet(false, true)) {
                    cont.resume(
                        if (completed) StreamResult.Done
                        else StreamResult.Interrupted()
                    )
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                if (cont.isCancelled) return
                val err = if (response != null && !response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    parseError(body) ?: "请求失败（HTTP ${response.code}）"
                } else {
                    "网络错误：${t?.message ?: "连接失败"}"
                }
                if (resumed.compareAndSet(false, true)) cont.resume(StreamResult.Failed(err))
            }
        })
        cont.invokeOnCancellation { es.cancel() }
    }

    private fun buildBody(req: ChatRequest): String {
        val arr = JSONArray()
        if (!req.systemPrompt.isNullOrBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", req.systemPrompt))
        }
        for ((role, content) in req.messages) {
            if (content.isBlank()) continue
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val root = JSONObject()
            .put("model", req.model)
            .put("messages", arr)
            .put("stream", true)
            .put("thinking", JSONObject().put("type", if (req.thinking) "enabled" else "disabled"))
        if (req.thinking) {
            root.put("reasoning_effort", req.reasoningEffort)
        }
        return root.toString()
    }

    /**
     * 非流式调用：从对话中提取值得长期记住的信息（始终用非思考模式，成本极低）。
     * 返回提取到的记忆文本列表；失败返回空列表。
     */
    suspend fun extractMemories(
        apiKey: String,
        messages: List<Pair<String, String>>
    ): List<String> = suspendCancellableCoroutine { cont ->
        val arr = JSONArray()
        arr.put(
            JSONObject().put(
                "role", "system"
            ).put(
                "content",
                "你是一个记忆提取器。阅读下面的对话，提取其中值得长期记住的" +
                    "用户个人信息或长期偏好（例如名字、职业、居住地、饮食习惯、" +
                    "常用语言、回答风格偏好等）。不要提取一次性请求内容。\n" +
                    "只输出 JSON，格式：{\"memories\": [\"...\", \"...\"]}。" +
                    "如果没有值得记住的信息，输出 {\"memories\": []}。"
            )
        )
        for ((role, content) in messages) {
            if (content.isBlank()) continue
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("model", ModelInfo.FLASH)
            .put("messages", arr)
            .put("stream", false)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("response_format", JSONObject().put("type", "json_object"))

        val httpReq = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        val call = client.newCall(httpReq)
        cont.invokeOnCancellation { call.cancel() }

        try {
            val resp = call.execute()
            if (!resp.isSuccessful) {
                resp.close()
                if (!cont.isCancelled) cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val text = resp.body?.string().orEmpty()
            resp.close()
            val content = try {
                JSONObject(text)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }
            val result = parseMemoryJson(content)
            if (!cont.isCancelled) cont.resume(result)
        } catch (e: Exception) {
            if (!cont.isCancelled) cont.resume(emptyList())
        }
    }

    private fun parseMemoryJson(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        return try {
            val obj = JSONObject(content.substring(start, end + 1))
            val arr = obj.optJSONArray("memories") ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { out += it }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseError(body: String): String? {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message") ?: body.take(300)
        } catch (_: Exception) {
            body.take(300)
        }
    }

    companion object {
        private val jsonType = "application/json; charset=utf-8".toMediaType()
    }
}
