package com.deepseek.personal.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String
)

/**
 * 检查更新 + 下载 APK。更新源是 version.json + APK 的静态目录。
 */
class UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        url: String,
        target: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(url).build()
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (!cont.isCancelled) cont.resume(Result.failure(Exception("HTTP ${resp.code}")))
                        return@suspendCancellableCoroutine
                    }
                    val body = resp.body
                    if (body == null) {
                        if (!cont.isCancelled) cont.resume(Result.failure(Exception("响应为空")))
                        return@suspendCancellableCoroutine
                    }
                    val total = body.contentLength()
                    val input = body.byteStream()
                    target.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        var done = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(done.toFloat() / total)
                            if (cont.isCancelled) break
                        }
                    }
                    if (!cont.isCancelled) cont.resume(Result.success(target))
                }
            } catch (e: Exception) {
                if (!cont.isCancelled) cont.resume(Result.failure(e))
            }
        }
    }
}
