# -*- coding: utf-8 -*-
"""反馈分析工具：解析反馈 zip（日志+截图+单句反馈），
GLM 识别图片，DeepSeek 生成可行性方案，输出 Markdown 报告。

用法：
    python feedback_analyzer.py <反馈.zip> [更多zip...]
    python feedback_analyzer.py --dir D:\\feedback_folder
"""
import argparse
import base64
import json
import os
import re
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from datetime import datetime

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API_CONFIG = r"F:\Py项目\insurance_toolkit\api_config.json"
REPORT_DIR = os.path.join(ROOT, "reports")
DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"


def load_keys():
    with open(API_CONFIG, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    return cfg["bigmodel"], cfg["deepseek"]["key"]


def http_json(url, body, key, timeout=120):
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def extract_zip(zip_path, dest):
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(dest)


def read_text(path, limit=6000):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read(limit)
    except Exception:
        return ""


def analyze_logs(text):
    """提取日志中的错误线索。"""
    lines = text.splitlines()
    errors = []
    pattern = re.compile(
        r"(ERROR|Exception|FATAL|Caused by|HTTP \d{3}|SocketTimeout|Connect(ion)? timed out|"
        r"Unresolved|NetworkError|TimeoutException|OutOfMemory|ANR|IllegalState)"
    )
    for i, line in enumerate(lines):
        if pattern.search(line):
            ctx = lines[max(0, i - 1):i + 3]
            errors.append("\n".join(ctx))
    return errors[:30]


def describe_image(bigmodel_cfg, img_path):
    """GLM 视觉识别截图内容。"""
    with open(img_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    ext = os.path.splitext(img_path)[1].lower().lstrip(".") or "jpg"
    mime = {"png": "image/png", "webp": "image/webp", "gif": "image/gif"}.get(ext, "image/jpeg")
    body = {
        "model": "glm-4v-plus",
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{mime};base64,{b64}"},
                    },
                    {
                        "type": "text",
                        "text": (
                            "这是一张软件使用中的截图。请描述：1) 界面显示了什么内容；"
                            "2) 是否有错误提示、异常或空白区域；3) 如果有报错文字，原文照抄。"
                        ),
                    },
                ],
            }
        ],
    }
    try:
        data = http_json(
            bigmodel_cfg["base_url"].rstrip("/") + "/chat/completions",
            body,
            bigmodel_cfg["key"],
        )
        return data["choices"][0]["message"]["content"]
    except Exception as e:
        return f"[GLM 识别失败: {e}]"


def deepseek_plan(api_key, summary):
    body = {
        "model": "deepseek-v4-flash",
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是安卓 App 的反馈分析助手。根据用户反馈、日志线索和截图描述，"
                    "给出问题归类、根因推断和可执行的改进方案。要求："
                    "1) 每个方案写明改动点、涉及模块、工作量评估（小/中/大）、风险；"
                    "2) 给出优先级；3) 输出 Markdown，结构清晰；"
                    "4) 不确定的根因要标注「需进一步验证」；"
                    "5) 结尾列出「待拍板」清单。"
                ),
            },
            {"role": "user", "content": summary},
        ],
        "thinking": {"type": "disabled"},
    }
    try:
        data = http_json(DEEPSEEK_URL, body, api_key, timeout=180)
        return data["choices"][0]["message"]["content"]
    except Exception as e:
        return f"[方案生成失败: {e}]"


def analyze_zip(zip_path, bigmodel_cfg, api_key, report):
    name = os.path.basename(zip_path)
    report.append(f"\n---\n\n## 反馈包：{name}\n")
    tmp = tempfile.mkdtemp(prefix="feedback_")
    try:
        extract_zip(zip_path, tmp)
        feedback = read_text(os.path.join(tmp, "feedback.txt"))
        system_info = read_text(os.path.join(tmp, "system_info.txt"))

        log_dir = os.path.join(tmp, "logs")
        log_text = ""
        if os.path.isdir(log_dir):
            for f in sorted(os.listdir(log_dir)):
                log_text += f"### {f}\n" + read_text(os.path.join(log_dir, f), 8000) + "\n"

        img_dir = os.path.join(tmp, "images")
        img_desc = []
        if os.path.isdir(img_dir):
            for f in sorted(os.listdir(img_dir)):
                img_path = os.path.join(img_dir, f)
                print(f"  [GLM] 识别 {f} ...")
                img_desc.append(f"- {f}: {describe_image(bigmodel_cfg, img_path)}")

        report.append("### 用户反馈\n")
        report.append(feedback.strip() or "（无文字反馈）")
        report.append("\n### 设备信息\n")
        report.append(system_info.strip() or "（无）")
        report.append("\n### 日志错误线索\n")
        log_errors = analyze_logs(log_text)
        if log_errors:
            for e in log_errors[:10]:
                report.append(f"```\n{e}\n```\n")
        else:
            report.append("未发现明显错误关键字。\n")
        report.append("\n### 截图识别\n")
        report.append("\n".join(img_desc) if img_desc else "（无截图）")
        report.append("\n")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser(description="反馈包分析工具")
    parser.add_argument("zips", nargs="*", help="反馈 zip 文件路径")
    parser.add_argument("--dir", help="包含多个反馈 zip 的目录")
    args = parser.parse_args()

    zip_files = list(args.zips)
    if args.dir:
        zip_files += [
            os.path.join(args.dir, f)
            for f in os.listdir(args.dir)
            if f.lower().endswith(".zip")
        ]
    if not zip_files:
        print("请提供反馈 zip 路径，或用 --dir 指定目录")
        sys.exit(1)

    bigmodel_cfg, api_key = load_keys()
    os.makedirs(REPORT_DIR, exist_ok=True)
    report = [
        "# 反馈分析报告\n",
        f"生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n",
    ]

    for z in zip_files:
        if not os.path.exists(z):
            print(f"跳过不存在的文件: {z}")
            continue
        print(f"== 分析 {os.path.basename(z)} ==")
        analyze_zip(z, bigmodel_cfg, api_key, report)

    print("== DeepSeek 生成可行性方案 ==")
    plan = deepseek_plan(api_key, "\n".join(report))
    report.append("\n---\n\n# AI 可行性方案\n")
    report.append(plan)

    out = os.path.join(
        REPORT_DIR,
        f"report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.md",
    )
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(report))
    print(f"\n报告已生成: {out}")


if __name__ == "__main__":
    main()
