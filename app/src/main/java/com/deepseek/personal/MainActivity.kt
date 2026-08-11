package com.deepseek.personal

import android.os.Bundle
import android.os.Build
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepseek.personal.ui.AppRoot
import com.deepseek.personal.ui.AppViewModel
import com.deepseek.personal.ui.theme.DeepSeekTheme
import com.deepseek.personal.data.LogCollector
import com.deepseek.personal.data.SettingsStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogCollector.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            DeepSeekTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm)
            }
        }
        // 根据设置自适应高刷新率
        val settings = SettingsStore(applicationContext)
        lifecycleScope.launch {
            settings.highRefresh.collect { enable ->
                enableHighRefreshRate(enable)
            }
        }
    }

    /**
     * 识别设备最高刷新率：性能强的设备（≥90Hz）启用高刷渲染，
     * 关闭时恢复系统默认，让 Compose 动画与流式渲染更流畅。
     */
    private fun enableHighRefreshRate(enabled: Boolean) {
        val display = display ?: return
        val bestMode = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        if (!enabled || bestMode.refreshRate < 90f) {
            val lp = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (lp.preferredDisplayModeId != 0) {
                    lp.preferredDisplayModeId = 0
                    window.attributes = lp
                }
            } else {
                @Suppress("DEPRECATION")
                if (lp.preferredRefreshRate != 0f) {
                    lp.preferredRefreshRate = 0f
                    window.attributes = lp
                }
            }
            return
        }
        val lp = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (lp.preferredDisplayModeId != bestMode.modeId) {
                lp.preferredDisplayModeId = bestMode.modeId
                window.attributes = lp
            }
        } else {
            @Suppress("DEPRECATION")
            lp.preferredRefreshRate = bestMode.refreshRate
            window.attributes = lp
        }
    }
}
