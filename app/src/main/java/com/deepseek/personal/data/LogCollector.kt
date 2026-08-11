package com.deepseek.personal.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量日志收集：记录到 App 私有目录 logs/，用于反馈打包。
 */
object LogCollector {

    private var logDir: File? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (logDir != null) return
        logDir = File(context.filesDir, "logs").apply { mkdirs() }
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logError("未捕获异常", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun logInfo(message: String) = write("INFO", message, null)

    fun logError(message: String, throwable: Throwable? = null) =
        write("ERROR", message, throwable)

    fun logFiles(): List<File> =
        logDir?.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun write(level: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return
        val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val file = File(dir, "app_$day.log")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
            .append(time).append(" [").append(level).append("] ").append(message)
        if (throwable != null) {
            sb.append('\n').append(Log.getStackTraceString(throwable))
        }
        sb.append('\n')
        try {
            file.appendText(sb.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
