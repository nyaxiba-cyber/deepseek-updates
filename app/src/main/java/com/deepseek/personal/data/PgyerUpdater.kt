package com.deepseek.personal.data

import com.deepseek.personal.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 蒲公英 API 2.0 检测更新：POST https://api.pgyer.com/apiv2/app/check
 * 返回最新版本信息（含 APK 下载直链 downloadURL），供设置页「检查更新」使用。
 */
class PgyerUpdater {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun check(apiKey: String, appKey: String): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("_api_key", apiKey)
                    .add("appKey", appKey)
                    .add("buildVersion", BuildConfig.VERSION_NAME)
                    .build()
                val req = Request.Builder()
                    .url("https://api.pgyer.com/apiv2/app/check")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.optInt("code", -1) != 0) {
                        return@withContext Result.failure(
                            Exception(json.optString("message", "检查更新失败"))
                        )
                    }
                    val data = json.optJSONObject("data")
                        ?: return@withContext Result.failure(Exception("响应数据为空"))
                    Result.success(
                        UpdateInfo(
                            versionCode = data.optInt("buildVersionNo", 0),
                            versionName = data.optString("buildVersion", ""),
                            notes = data.optString("buildUpdateDescription", ""),
                            downloadUrl = data.optString("downloadURL", "")
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
