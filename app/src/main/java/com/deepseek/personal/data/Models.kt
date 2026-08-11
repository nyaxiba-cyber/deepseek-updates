package com.deepseek.personal.data

data class ChatMessage(
    val id: Long,
    val role: String,          // "user" / "assistant"
    val content: String = "",
    val reasoning: String = "", // 思考模式下的推理过程
    val timestamp: Long = System.currentTimeMillis(),
    val streaming: Boolean = false,
    val error: String? = null
)

data class Conversation(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String,
    val thinking: Boolean
)

data class Memory(
    val id: Long,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

object ModelInfo {
    const val FLASH = "deepseek-v4-flash"
    const val PRO = "deepseek-v4-pro"

    val all = listOf(
        ModelOption(FLASH, "DeepSeek V4 Flash", "快速 · 便宜", "$0.14 输入 · $0.29 输出 / 百万 Token"),
        ModelOption(PRO, "DeepSeek V4 Pro", "最强 · 慢一些", "$0.44 输入 · $0.87 输出 / 百万 Token")
    )
}

data class ModelOption(
    val id: String,
    val name: String,
    val desc: String,
    val price: String
)
