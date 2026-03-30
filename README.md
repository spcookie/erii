<div align="center">

# 🌟 EriiX

**一个具有情感、记忆、心流和主动行为的 AI 群聊机器人**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/EriiX)

[🚀 Features](#-features) • [🛠️ Tech Stack](#-tech-stack) • [🏁 Getting Started](#-getting-started) • [📦 Building \& Running](#-building--running)

</div>

---

## 📖 Overview

EriiX 是一个基于多维心理模型的 AI 群聊机器人系统。不同于传统的问答机器人，EriiX 拥有**情绪系统**、**长期记忆**、**对话心流**
和**主动行为**，能够像真人一样参与群聊对话，主动插话、破冰，并根据情绪状态调整说话风格。

**✨ 核心特性：**

- 🎭 **情绪系统**：基于 PAD 模型，模拟真实情感反应
- 🧠 **长期记忆**：多维度记录用户画像、偏好和群聊事实
- 🌊 **心流机制**：根据话题投入程度动态调整参与深度
- ⚡ **主动行为**：基于冲动值进行插话、破冰和日常互动

## 🛠️ Tech Stack

### 🖥️ 后端框架

- **语言:** Kotlin 1.9+
- **Web 框架:** Ktor 2.3+
- **构建工具:** Gradle 8.0+
- **数据库:** H2 (Embedded)
- **ORM:** Exposed
- **依赖注入:** Koin
- **任务调度:** JobRunr

### 🤖 机器人集成

- **机器人框架:** Mirai + Overflow
- **接入服务:** NapCat (WebSocket)
- **LLM:** Google Gemini API / OpenAI Compatible API

### 🔌 扩展架构

- **插件框架:** PF4J
- **SPI 接口:** erii-spi 模块定义扩展点

## 🚀 Features

### 🧠 核心系统

#### 🎭 情绪系统 (Emotion)

- **PAD 三维情绪模型** - Pleasure(愉悦度)、Arousal(激活度)、Dominance(优势度)
- **动态情绪调节** - 根据对话内容实时调整情绪状态
- **情绪表达** - 影响回复语气、表情符号使用、回复延迟
- **长期心情** - 维护基础心情基调，影响整体行为倾向

#### 💾 记忆系统 (Memory)

- **多维度记忆** - 用户画像、偏好设置、事实知识、对话摘要
- **三级作用域** - 全局记忆、群组记忆、成员-群组记忆
- **语义检索** - 基于向量相似度的智能记忆召回
- **LLM 驱动** - AI 自动提取和管理长期记忆

#### 🌊 心流系统 (Flow)

- **投入度量化** - 0-100 心流值，三阶段状态机
- **话题匹配** - 遇到感兴趣话题快速进入心流状态
- **专注机制** - 高心流时锁定对话目标，过滤无关干扰
- **过热保护** - 模拟疲劳感，避免过度输出

#### ⚡ 主动行为系统 (Volition)

- **冲动值计算** - 基于情绪、心流、关键词计算主动发言欲望
- **三种插话模式** - 插话(Interrupt)、破冰(Icebreak)、日常(Routine)
- **识相机制** - 检测严肃话题、群体情绪，避免不合时宜发言
- **疲劳抑制** - 主动发言后积累疲劳值，防止刷屏

#### 🧬 进化系统 (Evolution)

- **词汇学习** - 自动学习群聊中的新词汇和梗
- **语义理解** - 记录词汇含义、使用场景和语气
- **自然融入** - 在合适场景自然使用学习到的群聊用语

#### 🎭 梗系统 (Meme)

- **梗提取** - 从群聊中自动提取流行语和梗
- **向量存储** - 语义向量表示，支持相似度检索
- **热度追踪** - 跟踪高频词汇的使用频率和流行度

### 🔌 内置插件

| 插件                 | 类型               | 说明                                |
|:-------------------|:-----------------|:----------------------------------|
| **speech**         | AgentExtension   | 语音合成插件，使用 MiniMax TTS 将文字转为语音发送   |
| **lolisuki**       | RouteExtension   | R18 二次元图片插件，从 lolisuki.cn 获取并发送涩图 |
| **net-ease-music** | PassiveExtension | 网易云音乐插件，搜索音乐并发送音乐卡片               |
| **qa**             | RouteExtension   | AI 问答插件，通过网络搜索直接回答问题              |
| **qq-face**        | PassiveExtension | QQ 表情插件，语义匹配发送合适的表情               |
| **reminder**       | AgentExtension   | 定时提醒插件，支持延迟消息和定时任务                |
| **seeddream**      | RouteExtension   | AI 图片生成插件，支持文生图和图生图               |

### 💬 对话增强

- **上下文理解** - 引用历史对话和长期记忆
- **多 Persona** - 支持多 Bot 角色配置
- **情绪共鸣** - 感知群体氛围，调整参与方式

## 🏁 Getting Started

### 📋 前置要求

在开始之前，确保已安装以下环境：

- **JDK 11+**
- **Gradle 8.0+**
- **NapCat** - QQ 接入服务
- **LLM API Key** - Google Gemini 或 DeepSeek API

### ⚙️ 安装与配置

1. **克隆仓库**
   ```bash
   git clone https://github.com/spcookie/EriiX.git
   cd EriiX
   ```

2. **配置环境变量**
   创建 `.env.local` 文件并配置以下变量：
   ```properties
   # LLM API 配置
   GOOGLE_API_KEY=your_google_api_key_here
   # 或使用 DeepSeek
   DEEP_SEEK_API_KEY=your_deep_seek_api_key_here
   CHOICE_MODEL=GOOGLE  # 或 DEEP_SEEK

   # NapCat WebSocket 地址
   NAPCAT_WS=ws://127.0.0.1:3001
   NAPCAT_TOKEN=your_napcat_token

   # 启用的群组列表
   ENABLE_GROUPS=xxx,xxx

   # Steam API（可选）
   STEAM_API_KEY=your_steam_api_key_here

   # HTTP 代理（可选）
   HTTP_PROXY=http://127.0.0.1:7890

   # 消息重定向（可选）
   MESSAGE_REDIRECT_MAP=sourceGroup:targetGroup

   # Steam 订阅（可选）
   STEAM_SUBSCRIPTIONS=groupId,qqNum,steamId;...
   ```

3. **启动开发服务器**
   ```bash
   ./gradlew run
   ```

### 🐳 Docker 部署

```bash
docker-compose up -d
```

这将构建并启动 Erii 容器。如需完整开发环境，请分别进入以下目录启动：

```bash
cd docker/napcat-docker && docker-compose up -d  # NapCat QQ 接入服务
cd docker/playwright-docker && docker-compose up -d  # Playwright 浏览器服务
cd docker/searxng-docker && docker-compose up -d  # SearXNG 搜索服务
```

## 📦 Building & Running

| 命令                        | 说明               |
|:--------------------------|:-----------------|
| `./gradlew compileKotlin` | 编译 Kotlin 代码     |
| `./gradlew build`         | 构建整个项目           |
| `./gradlew run`           | 启动开发服务器          |
| `./gradlew buildFatJar`   | 构建包含所有依赖的可执行 JAR |
| `./gradlew buildImage`    | 构建 Docker 镜像     |
| `./gradlew test`          | 运行所有测试           |

## 📂 Project Structure

```
EriiX/
├── erii-common/                 # 📦 公共模块
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 异步/同步事件总线
│       ├── BotRole.kt           # Bot 角色定义
│       ├── ChatToolSet.kt       # 聊天工具集
│       └── ...
├── erii-core/                   # 🧠 核心模块
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 应用入口
│       ├── config/              # 配置和 DI
│       │   └── IOC.kt          # Koin 依赖注入配置
│       ├── core/
│       │   ├── GroupMessageEventListener.kt  # 消息事件监听
│       │   ├── BotRoleManager.kt             # Bot 角色管理
│       │   ├── agent/           # AI Agent
│       │   │   └── BotAgent.kt  # 核心 AI Agent
│       │   ├── route/           # 路由系统
│       │   │   └── RoutingAgent.kt  # LLM 路由
│       │   └── state/           # 核心 AI 状态系统
│       │       ├── emotion/     # 情绪系统
│       │       ├── memory/      # 记忆系统
│       │       ├── flow/        # 心流系统
│       │       ├── volition/    # 主动行为系统
│       │       ├── evolution/   # 进化系统
│       │       └── meme/        # 梗系统
│       └── routing/             # HTTP API 路由
├── erii-spi/                    # 🔌 SPI 接口模块
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # 插件扩展接口
│       ├── PluginDefinition.kt # 插件定义注解
│       └── processor/           # 注解处理器
├── erii-plugins/                # 🎨 插件模块
│   ├── speech/                  # MiniMax TTS 语音合成
│   ├── lolisuki/                # R18 二次元涩图获取
│   ├── net-ease-music/          # 网易云音乐搜索与卡片发送
│   ├── qa/                      # AI 网络搜索问答
│   ├── qq-face/                 # QQ 表情语义匹配发送
│   ├── reminder/                # 定时提醒与延迟消息
│   └── seeddream/               # 豆包文生图/图生图
└── build.gradle.kts             # 根构建配置
```

## 🗺️ Architecture

EriiX 采用事件驱动架构，通过 `EventBus` 实现系统间解耦。

```
消息接收 (NapCat/Mirai)
       │
       ▼
GroupMessageEventListener
       │
       ▼
HistoryService (保存历史)
       │
       ▼
RoutingAgent (LLM 意图分类) / CmdRuleRegister (命令匹配)
       │
       ▼
RouteCallEvent (通过 EventBus.postAsync 分发)
       │
       ▼
BotAgent (消费事件，执行 AI Agent)
       │
       ▼
状态更新 (Emotion, Memory, Flow, Volition, Evolution, Meme)
```

### 插件扩展点

| 扩展类型                 | 说明          | 匹配方式              |
|:---------------------|:------------|:------------------|
| **AgentExtension**   | 通用 Agent 扩展 | 组合使用              |
| **RouteExtension**   | LLM 路由扩展    | RoutingAgent 意图分类 |
| **CmdExtension**     | 命令扩展        | `/xxx` 命令匹配       |
| **PassiveExtension** | 被动扩展        | 后台任务/事件监听         |

## 📄 License

本项目基于 [MIT](LICENSE) 许可证开源。

![Alt](https://repobeats.axiom.co/api/embed/12134436f49b0440db57c5d06c901307da82bdce.svg "Repobeats analytics image")
