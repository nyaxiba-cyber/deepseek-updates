package com.deepseek.personal.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把反馈文字 + 截图 + 日志 + 设备信息打包成 zip，并可分享。
 */
object FeedbackPackager {

    fun createZip(
        context: Context,
        feedbackText: String,
        imageUris: List<Uri>
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFile = File(context.cacheDir, "feedback_$stamp.zip")

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            // 1. 设备与版本信息
            zos.putNextEntry(ZipEntry("system_info.txt"))
            zos.write(buildSystemInfo(context).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. 反馈内容
            zos.putNextEntry(ZipEntry("feedback.txt"))
            zos.write(buildFeedbackText(feedbackText).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. 日志文件
            LogCollector.logFiles().forEach { log ->
                zos.putNextEntry(ZipEntry("logs/${log.name}"))
                log.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            // 4. 截图
            imageUris.forEachIndexed { index, uri ->
                val ext = imageExtension(context, uri)
                zos.putNextEntry(ZipEntry("images/img_${index + 1}.$ext"))
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.copyTo(zos) }
                } catch (_: Exception) {
                }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    fun share(context: Context, zipFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "发送反馈包")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun buildSystemInfo(context: Context): String {
        val app = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("App 版本: v${app.versionName} (${app.versionCode})")
            appendLine("Android 版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        }
    }

    private fun buildFeedbackText(feedback: String): String =
        "反馈内容：\n${feedback.trim()}\n"

    private fun imageExtension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
    }
}
