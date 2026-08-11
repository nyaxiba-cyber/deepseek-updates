# -*- coding: utf-8 -*-
"""测试 Responses API 关闭思考模式的参数。"""
import json
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def load_key():
    with open(r"F:\Py项目\insurance_toolkit\api_config.json", "r", encoding="utf-8") as f:
        return json.load(f)["deepseek"]["key"]


def test(label, extra):
    body = {
        "model": "deepseek-v4-flash",
        "input": "搜索今天北京天气，一句话回答",
        "tools": [{"type": "web_search"}],
        **extra,
    }
    req = urllib.request.Request(
        "https://api.deepseek.com/responses",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {load_key()}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    has_reasoning = any(i.get("type") == "reasoning" for i in data.get("output", []))
    text = data.get("output_text", "")
    print(f"[{label}] reasoning={has_reasoning} text={text[:120]}")


def main():
    test("默认", {})
    test("effort=none", {"reasoning": {"effort": "none"}})
    test("effort=disabled", {"reasoning": {"effort": "disabled"}})
    test("thinking disabled", {"thinking": {"type": "disabled"}})


if __name__ == "__main__":
    main()
