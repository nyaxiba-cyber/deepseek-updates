# DeepSeek Android（个人版）

## 项目定位

把 DeepSeek 官方 API 包装成安卓 App，界面尽量复刻 DeepSeek 官方 App 的对话体验，
**仅供自己使用**（不发布、不上架、不共享）。

背景：

- DeepSeek 官方手机版免费额度有限，体验受限，且没有付费版。
- 官方 API 平台（platform.deepseek.com）可以充值，按 token 计费。
- 本项目用自己的 API Key 直连官方 API，等于自建"付费版"。

## 核心功能

1. **对话**：和 DeepSeek 官方 App 一致的对话界面（消息气泡、流式输出、深度思考开关、联网搜索开关）。
2. **模型选择**：`deepseek-chat`（V3 对话）、`deepseek-reasoner`（R1 深度思考）。
3. **流式响应**：打字机效果，边生成边显示。
4. **历史记录**：本地保存对话历史（SQLite，仅存本机）。
5. **设置页**：填 API Key、选择模型、清空历史。

## 技术栈（推荐）

- **Kotlin + Jetpack Compose**（原生安卓，单 APK，体积小、启动快、UI 完全可控）
- Retrofit/OkHttp 调 DeepSeek API（`https://api.deepseek.com`）
- SQLite（Room）存历史

备选：Flutter（以后想上 iOS 再考虑）；WebView 套壳（开发最快但体验一般）。

## 环境要求

- JDK 17+（当前机器只有 Java 8，需要装）
- Android SDK（当前机器没有，需要装）
- 一台安卓手机（Android 8.0+ 即可）

## 目录结构

```
deepseek-android/
├── app/                  # 安卓应用主体
│   └── src/main/         # 代码、资源、Manifest
├── docs/                 # 设计文档、界面参考
└── README.md
```

## 里程碑

- [x] M1：搭建工程骨架，能编译出 APK（2026-08-11 构建成功）
- [x] M2：API 对接，能发消息并流式回复（已用真实 key 验证）
- [x] M3：对话界面（左侧会话列表 + 中间流式输出 + 空态欢迎页）
- [x] M4：历史记录（SQLite）、深度思考开关、模型切换、设置页
- [ ] M5：安装到手机实机验收

## 当前状态（2026-08-11）

- APK：`app/build/outputs/apk/debug/app-debug.apk`（约 17MB，Android 8.0+）
- 版本：v1.13（versionCode 14）
- 模型：`deepseek-v4-flash` / `deepseek-v4-pro`（2026 年 8 月官方最新阵容）
- 深度思考：**默认关闭（纯 Chat 模式）**，输入栏开关默认关；需要时可手动开
- API Key：**不内置在 APK**。首次打开后在设置页填写，保存到 App 本地配置文件
  （DataStore），仅存本机；更新安装时系统保留该文件，Key 不会丢失
- 流式动画：20ms 增量合并 + 平滑滚动跟随 + 思考过程实时显示
- 记忆：AI 会记住用户信息并在后续对话中自动运用（侧栏「记忆」管理）
- 检查更新：设置页一键检查/下载/安装新版本，更新地址内置（GitHub Releases，不展示给用户）
- 主题：7 款配色（DeepSeek 蓝 / ChatGPT 绿 / Claude 橙 / Gemini 蓝 /
  Perplexity 青 / Grok 黑白 / 石墨灰），跟随系统深浅色
- 语音输入：输入框左侧麦克风按钮，按住说话、松开结束，实时识别填入文本
- 联网搜索：**已正式支持**（Responses API + web_search 工具，服务端执行搜索，
  仅 V4 Flash 模型；固定 Chat 模式不思考）
- 反馈：侧栏「反馈」→ 填写问题 + 可选截图 → 自动打包（日志/设备信息/反馈文字/截图
  成 zip）→ 系统分享发送
- 更新弹窗：检查更新时弹窗列出「本次更新内容」，逐条展示
- 更新加速：更新清单走 GitHub raw（实时），APK 走 jsDelivr CDN（国内加速）
- 语音按钮自适应：设备不支持语音识别时自动隐藏麦克风按钮
- 深浅色模式：跟随系统 / 浅色 / 深色 手动切换
- 进程恢复：App 被系统杀掉后重启，回到原会话
- 流式：官方 okhttp-sse 库 + ChatGPT 式打字机动画 + Markdown 成熟库渲染
- 安全：API Key 用 Android Keystore AES-GCM 加密存储

## 记忆功能（2026-08-11 新增）

- **自动记忆**：每次回答完成后，后台用 flash 非思考模式提取对话中值得长期记住的
  信息（名字、职业、偏好等），提取成功会在聊天底部提示「已记住：…」
- **手动管理**：侧栏 →「记忆」可查看、添加、删除记忆，可关闭自动记忆
- **注入方式**：发消息时把全部记忆作为 system 提示注入，AI 回答时自然运用

## 反馈与自动分析（v1.5 新增）

### App 端（收集）

侧栏 →「反馈」：填问题、加截图 → 打包 zip（含日志 `logs/`、设备信息、反馈文字、
截图）→ 选择微信/网盘/邮箱等发送给开发者。

App 自动记录：未捕获异常（全局捕获）、API 请求失败等日志，保留最近 7 天。

### 电脑端（分析）

```powershell
$env:PYTHONIOENCODING='utf-8'
python F:\Py项目\deepseek-android\tools\feedback_analyzer.py D:\收到\反馈.zip
python F:\Py项目\deepseek-android\tools\feedback_analyzer.py --dir D:\反馈目录
```

流程：解压 zip → 提取日志错误线索 → GLM 识别截图 → DeepSeek 生成可行性方案
（问题归类 / 根因推断 / 改动点 / 工作量 / 风险 / 优先级 / 待拍板清单），
输出 Markdown 报告到 `reports/`。API Key 复用保险项目配置，不新增密钥。

测试反馈包生成：`python tools\make_test_feedback.py`

## 构建方法

```powershell
$env:JAVA_HOME = "F:\Android\jdk-17.0.20+8"
$env:GRADLE_USER_HOME = "F:\Android\.gradle"
cd F:\Py项目\deepseek-android
.\gradlew.bat assembleDebug
```

## 安装到手机

1. 把 `app-debug.apk` 传到手机（数据线 / 微信 / 网盘均可）
2. 手机点击 APK 安装，允许「安装未知来源应用」
3. 打开 App 直接对话；侧栏 → 设置可改 API Key / 模型 / 思考力度

## 自动更新（v1.1 新增）

App 设置页 →「更新」→ 检查更新。v1.2 起默认更新源指向
GitHub Releases：`https://github.com/nyaxiba-cyber/deepseek-updates/releases/latest/download/version.json`

### 电脑端发布（一键上传 GitHub）

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& F:\Py项目\deepseek-android\scripts\publish_github.ps1 -VersionCode 4 -VersionName "1.3" -Notes "更新说明"
```

脚本会：构建 APK → 生成 version.json → 用 gh CLI 上传 APK + version.json
到 GitHub Releases 并创建 v{版本号} Release（幂等，可重跑）。

局域网发布脚本（备用，无需 GitHub）：`scripts\publish.ps1 -StartServer`

### GitHub Actions 自动发布（v1.13 新增）

源码已托管到 `nyaxiba-cyber/deepseek-updates` 仓库（公开）。两种触发方式：

1. **打 tag**：`git tag v1.14 && git push origin v1.14` → 云端自动构建、创建
   Release、更新 CDN 文件
2. **手动触发**：`gh workflow run "Build & Release" --repo nyaxiba-cyber/deepseek-updates -f notes="更新说明"`

云端构建完成后，手机点「检查更新」即可从 jsDelivr CDN 下载安装。

### 手机端更新

1. 手机和电脑连同一个 WiFi
2. v1.2+ 默认更新地址已是 GitHub，直接点「检查」即可
3. 发现新版本 → 下载并安装
4. 首次安装需要允许「安装未知来源应用」（系统会引导）

### 手机网络说明

GitHub Releases 的下载在国内网络时快时慢；如果手机下载慢或失败，
可在设置里把更新地址改成局域网地址，或告知后加 CDN 加速。

## 注意事项

- APK 已不含任何 API Key，可以放心分发；Key 只存在于你手机的 App 私有目录
- 从旧版升级时：如果旧版是你手动填过 Key 的，升级后 Key 自动保留；
  如果旧版一直用的内置 Key，升级后需要重新填一次（仅此一次）
- 联网搜索走 Responses API（仅 deepseek-v4-flash；选了 Pro 也会自动切 Flash 执行搜索）
- 对话历史存本机 SQLite，清除操作不可恢复
- 个人自用项目，未经官方授权勿上架分发
