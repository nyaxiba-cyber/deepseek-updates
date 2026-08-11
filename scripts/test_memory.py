# -*- coding: utf-8 -*-
"""验证记忆提取：非思考模式 + JSON 输出，从对话中提取长期记忆。"""
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
        "stream": False,
        "thinking": {"type": "disabled"},
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是一个记忆提取器。阅读下面的对话，提取其中值得长期记住的"
                    "用户个人信息或长期偏好（例如名字、职业、居住地、饮食习惯、"
                    "常用语言、回答风格偏好等）。不要提取一次性请求内容。\n"
                    "只输出 JSON，格式：{\"memories\": [\"...\", \"...\"]}。"
                    "如果没有值得记住的信息，输出 {\"memories\": []}。"
                ),
            },
            {"role": "user", "content": "你好，我叫张三，我在上海做保险经纪人。"},
            {"role": "assistant", "content": "你好张三！很高兴认识你。有什么可以帮你的吗？"},
            {"role": "user", "content": "以后回答尽量简洁一些，不要长篇大论。"},
        ],
    }
    req = urllib.request.Request(
        "https://api.deepseek.com/chat/completions",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    content = data["choices"][0]["message"]["content"]
    print("模型原始输出:", content)
    try:
        parsed = json.loads(content)
        print("提取到的记忆:")
        for m in parsed.get("memories", []):
            print(" -", m)
    except Exception as exc:
        print("JSON 解析失败:", exc)


if __name__ == "__main__":
    main()
