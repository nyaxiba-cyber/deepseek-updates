# -*- coding: utf-8 -*-
"""验证 DeepSeek Responses API 的 web_search 联网搜索能力。"""
import json
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def load_key():
    with open(r"F:\Py项目\insurance_toolkit\api_config.json", "r", encoding="utf-8") as f:
        cfg = json.load(f)
    return cfg["deepseek"]["key"]


def call(endpoint, key, stream=False):
    body = {
        "model": "deepseek-v4-flash",
        "input": "帮我搜索一下今天北京天气怎么样",
        "tools": [{"type": "web_search"}],
        "stream": stream,
    }
    req = urllib.request.Request(
        endpoint,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        return resp.read().decode("utf-8")


def main():
    key = load_key()
    for endpoint in ("https://api.deepseek.com/responses", "https://api.deepseek.com/v1/responses"):
        print(f"===== {endpoint} =====")
        try:
            raw = call(endpoint, key)
            data = json.loads(raw)
            print("status:", data.get("status"))
            for item in data.get("output", []):
                t = item.get("type")
                print("output item:", t)
                if t == "web_search_call":
                    print("  search query:", item.get("search_query") or item.get("input"))
                    print("  status:", item.get("status"))
                if t == "message":
                    for c in item.get("content", []):
                        print("  content:", c.get("text", "")[:300])
            print("usage:", data.get("usage"))
            print("OK: Responses API 可用")
            break
        except Exception as exc:
            print("失败:", exc)


if __name__ == "__main__":
    main()
