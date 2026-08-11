# -*- coding: utf-8 -*-
"""生成测试反馈 zip（模拟用户反馈包）。"""
import shutil
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

OUT = r"F:\Py项目\deepseek-android\reports\test_feedback.zip"
SCREENSHOT = r"F:\Py项目\手机截屏的图片\Screenshot_2026_0806_001549.jpg"

feedback = "发送消息后一直没有回复，界面好像卡住了，等很久也没反应。\n"
system_info = "App 版本: v1.5 (6)\nAndroid 版本: 13 (API 33)\n设备: Xiaomi 23127PN0CC\n"
log = (
    "2026-08-11 20:40:01 [INFO] 应用启动\n"
    "2026-08-11 20:41:22 [ERROR] API 请求失败: 网络错误: Connect timed out\n"
    "2026-08-11 20:41:22 java.net.SocketTimeoutException: timeout\n"
    "\tat okhttp3.internal.http2.Http2Stream$StreamTimeout.newTimeoutException(Http2Stream.kt)\n"
    "\tat okhttp3.internal.connection.RealCall.timeout(RealCall.kt)\n"
    "2026-08-11 20:42:01 [ERROR] 未捕获异常\n"
    "java.lang.IllegalStateException: Cannot access database on the main thread\n"
    "\tat com.deepseek.personal.data.HistoryStore.createConversation(HistoryStore.kt:56)\n"
)

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("feedback.txt", feedback)
    zf.writestr("system_info.txt", system_info)
    zf.writestr("logs/app_20260811.log", log)
    zf.write(SCREENSHOT, "images/img_1.jpg")
print(f"测试反馈包已生成: {OUT}")
