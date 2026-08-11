package com.deepseek.personal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题集合：参考国外主流 AI 产品配色。
 * 每个主题同时提供浅色 / 深色（跟随系统）。
 */
enum class AppTheme(
    val key: String,
    val label: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color
) {
    DEEPSEEK(
        "deepseek", "DeepSeek 蓝",
        Color(0xFF4D6BFE), Color(0xFF7B93FF),
        Color(0xFFFFFFFF), Color(0xFF141519)
    ),
    CHATGPT(
        "chatgpt", "ChatGPT 绿",
        Color(0xFF10A37F), Color(0xFF34D399),
        Color(0xFFFFFFFF), Color(0xFF0D0D0D)
    ),
    CLAUDE(
        "claude", "Claude 橙",
        Color(0xFFD97757), Color(0xFFE69A7A),
        Color(0xFFFAF9F5), Color(0xFF1C1917)
    ),
    GEMINI(
        "gemini", "Gemini 蓝",
        Color(0xFF1A73E8), Color(0xFF8AB4F8),
        Color(0xFFFFFFFF), Color(0xFF131314)
    ),
    PERPLEXITY(
        "perplexity", "Perplexity 青",
        Color(0xFF20B8CD), Color(0xFF54D1E2),
        Color(0xFFFCFCFC), Color(0xFF0E1111)
    ),
    GROK(
        "grok", "Grok 黑白",
        Color(0xFF111111), Color(0xFFFFFFFF),
        Color(0xFFFFFFFF), Color(0xFF000000)
    ),
    SLATE(
        "slate", "石墨灰",
        Color(0xFF4B5563), Color(0xFF9CA3AF),
        Color(0xFFF9FAFB), Color(0xFF17181C)
    );

    companion object {
        fun fromKey(key: String): AppTheme =
            entries.firstOrNull { it.key == key } ?: DEEPSEEK
    }
}

private fun lightScheme(t: AppTheme): ColorScheme {
    val primary = t.primaryLight
    val background = t.backgroundLight
    val onBackground = if (t == AppTheme.GROK) Color(0xFF111111) else Color(0xFF111318)
    val variant = if (t == AppTheme.GROK) Color(0xFFF1F1F1) else Color(0xFFF4F5F7)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.12f),
        onPrimaryContainer = primary,
        secondary = Color(0xFF6C7A9E),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = variant,
        onSurfaceVariant = if (t == AppTheme.GROK) Color(0xFF555555) else Color(0xFF5A5F6A),
        outline = if (t == AppTheme.GROK) Color(0xFFE4E4E4) else Color(0xFFE2E4E9),
        error = Color(0xFFD64545)
    )
}

private fun darkScheme(t: AppTheme): ColorScheme {
    val primary = t.primaryDark
    val background = t.backgroundDark
    val onBackground = if (t == AppTheme.GROK) Color(0xFFE5E5E5) else Color(0xFFE7E9EE)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (t == AppTheme.GROK) Color(0xFF111111) else Color(0xFF0D1026),
        primaryContainer = primary.copy(alpha = 0.18f),
        onPrimaryContainer = primary,
        secondary = Color(0xFFA6B0C8),
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = if (t == AppTheme.GROK) Color(0xFF1A1A1A) else Color(0xFF2A2B2F),
        onSurfaceVariant = if (t == AppTheme.GROK) Color(0xFF9E9E9E) else Color(0xFF9CA2AF),
        outline = if (t == AppTheme.GROK) Color(0xFF262626) else Color(0xFF33363D),
        error = Color(0xFFFF6B6B)
    )
}

@Composable
fun DeepSeekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: AppTheme = AppTheme.DEEPSEEK,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(theme) else lightScheme(theme),
        content = content
    )
}

@Composable
private fun isSystemInDarkTheme(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
