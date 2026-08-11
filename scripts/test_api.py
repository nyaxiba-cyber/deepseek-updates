# -*- coding: utf-8 -*-
"""验证 DeepSeek API 流式接口（模型名、thinking 参数、SSE 解析）。"""
import json
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def load_key():
    with open(r"F:\Py项目\deepseek-android\local.properties", "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("DEEPSEEK_API_KEY="):
                return line.split("=", 1)[1]
    raise SystemExit("未找到 API Key")


def main():
    key = load_key()
    body = {
        "model": "deepseek-v4-flash",
        "messages": [{"role": "user", "content": "用一句话介绍你自己"}],
        "stream": True,
        "thinking": {"type": "enabled"},
        "reasoning_effort": "low",
    }
    req = urllib.request.Request(
        "https://api.deepseek.com/chat/completions",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
        method="POST",
    )
    content_parts = []
    reasoning_parts = []
    done = False
    with urllib.request.urlopen(req, timeout=60) as resp:
        print(f"HTTP {resp.status}")
        for raw in resp:
            line = raw.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                done = True
                break
            try:
                obj = json.loads(data)
                delta = obj["choices"][0].get("delta", {})
                rc = delta.get("reasoning_content", "")
                c = delta.get("content", "")
                if rc:
                    reasoning_parts.append(rc)
                if c:
                    content_parts.append(c)
            except Exception as exc:
                print(f"解析行失败: {exc}")
    print(f"[DONE]={done}")
    print(f"思考内容长度: {len(''.join(reasoning_parts))}")
    print(f"回答内容长度: {len(''.join(content_parts))}")
    print("回答预览:", "".join(content_parts)[:200])


if __name__ == "__main__":
    main()
