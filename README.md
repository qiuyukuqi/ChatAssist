# 📱 ChatAssist - AI智能聊天助手

> 一个Android应用，可以在任意聊天软件中智能辅助回复

## ✨ 功能特性

- 🔔 **智能监听** - 自动监听微信、QQ、钉钉、Slack等主流聊天App
- 🤖 **AI生成回复** - 基于上下文智能生成3个回复建议
- 🎨 **多种风格** - 支持普通、随意、礼貌、简短、幽默等风格
- 📋 **一键复制** - 点击建议即可复制，粘贴发送
- 🔧 **可配置** - 支持MiniMax、OpenAI、Claude等多种AI服务商

## 🚀 快速开始

### 方式一：直接下载APK

APK文件位于项目根目录：`ChatAssist-debug.apk`

### 方式二：从源码构建

#### 使用 GitHub Actions（推荐）

1. Fork 这个仓库
2. 进入 **Actions** 标签页
3. 点击 **Build Android APK** workflow
4. 点击 **Run workflow**
5. 等待构建完成
6. 在 Artifacts 中下载 APK

#### 本地构建

```bash
cd android
./gradlew assembleDebug
```

APK输出位置：`android/app/build/outputs/apk/debug/app-debug.apk`

## 🔧 开发

### 项目结构

```
ChatAssist/
├── android/                    # Android客户端
│   ├── app/src/main/
│   │   ├── java/com/lisa/chatassist/
│   │   │   ├── ChatAssistApp.kt           # 应用入口
│   │   │   ├── MainActivity.kt            # 主界面
│   │   │   ├── services/
│   │   │   │   └── NotificationService.kt  # 通知监听核心
│   │   │   ├── ai/
│   │   │   │   └── AiService.kt           # AI推理服务
│   │   │   ├── ui/
│   │   │   │   ├── overlay/
│   │   │   │   │   └── ChatOverlayService.kt  # 悬浮窗
│   │   │   │   └── settings/
│   │   │   │       └── SettingsActivity.kt    # 设置页
│   │   │   └── data/
│   │   │       ├── ChatSession.kt          # 数据模型
│   │   │       └── AppPreferences.kt       # 偏好设置
│   │   ├── res/                           # 布局/资源
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── server/                     # 可选：本地AI API服务
│   ├── index.js
│   └── package.json
├── .github/
│   └── workflows/
│       └── build.yml           # GitHub Actions 构建配置
└── README.md
```

### 添加新的聊天App

在 `NotificationService.kt` 的 `CHAT_APP_PACKAGES` 中添加包名：

```kotlin
val CHAT_APP_PACKAGES = setOf(
    "com.tencent.mm",           // 微信
    "com.tencent.mobileqq",      // QQ
    // 添加更多...
)
```

## 🔒 隐私说明

应用不会收集或上传您的聊天内容，仅用于本地生成回复建议。

## 📄 License

MIT License

---

Made with ❤️ by Lisa
