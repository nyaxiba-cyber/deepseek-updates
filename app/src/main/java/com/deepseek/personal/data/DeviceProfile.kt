package com.deepseek.personal.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * 设备性能识别：检测最高刷新率、内存、SoC，给出性能分级，
 * 供 UI 决定是否启用高刷新率渲染。
 */
object DeviceProfile {

    data class Info(
        val maxRefreshRate: Float,
        val soc: String,
        val ramGB: Int,
        val level: String
    )

    fun detect(context: Context): Info {
        val display = try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
        } catch (_: Exception) {
            null
        }
        val maxRate = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val ramGB = (mem.totalMem / (1024 * 1024 * 1024)).toInt().coerceAtLeast(1)

        val level = when {
            maxRate >= 120f && ramGB >= 8 -> "旗舰级"
            maxRate >= 90f -> "高性能"
            ramGB >= 6 -> "中端"
            else -> "标准"
        }

        return Info(
            maxRefreshRate = maxRate,
            soc = Build.SOC_MODEL.ifBlank { Build.HARDWARE },
            ramGB = ramGB,
            level = level
        )
    }

    fun formatRefresh(rate: Float): String =
        if (rate % 1f == 0f) "${rate.toInt()}Hz" else "${rate}Hz"
}
