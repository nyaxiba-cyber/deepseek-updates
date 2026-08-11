# -*- coding: utf-8 -*-
"""上传 APK 到蒲公英（API 2.0 app/upload）。

用法：python publish_pgyer.py <apk路径> <更新说明txt路径> <keys.json路径>
keys.json 格式：{"api_key": "...", "app_key": "..."}（app_key 可选，不传则上传到默认应用）
"""
import json
import os
import sys

import requests

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def main() -> int:
    if len(sys.argv) < 4:
        print("用法：python publish_pgyer.py <apk路径> <说明txt路径> <keys.json路径>")
        return 1
    apk_path = sys.argv[1]
    notes_path = sys.argv[2]
    keys_path = sys.argv[3]

    with open(keys_path, "r", encoding="utf-8") as f:
        keys = json.load(f)
    api_key = keys.get("api_key", "").strip()
    app_key = keys.get("app_key", "").strip()
    if not api_key:
        print("错误：keys.json 里缺少 api_key")
        return 1

    with open(notes_path, "r", encoding="utf-8") as f:
        notes = f.read().strip()

    data = {"_api_key": api_key}
    if app_key:
        data["appKey"] = app_key
    if notes:
        data["buildUpdateDescription"] = notes

    with open(apk_path, "rb") as f:
        files = {
            "file": (
                os.path.basename(apk_path),
                f,
                "application/vnd.android.package-archive",
            )
        }
        resp = requests.post(
            "https://www.pgyer.com/apiv2/app/upload",
            data=data,
            files=files,
            timeout=600,
        )

    print("HTTP", resp.status_code)
    print(resp.text)
    try:
        body = resp.json()
    except Exception:
        return 1
    if body.get("code") == 0:
        d = body.get("data", {})
        print("上传成功：", d.get("appKey"), d.get("buildShortcutUrl"))
        return 0
    print("上传失败：", body.get("message"))
    return 1


if __name__ == "__main__":
    sys.exit(main())
