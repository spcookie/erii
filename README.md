<div align="center">

![erii](https://socialify.git.ci/spcookie/erii/image?custom_description=%E4%B8%80%E4%B8%AA%E5%85%B7%E6%9C%89%E6%83%85%E6%84%9F%E3%80%81%E8%AE%B0%E5%BF%86%E3%80%81%E5%BF%83%E6%B5%81%E3%80%81%E4%B8%BB%E5%8A%A8%E8%A1%8C%E4%B8%BA%EF%BC%8C%E4%BA%8B%E9%A1%B9%E6%8F%90%E9%86%92%E5%92%8C%E8%87%AA%E6%88%91%E8%A7%84%E5%88%99%E7%AE%A1%E7%90%86%E7%9A%84+AI+%E7%BE%A4%E8%81%8A%E6%9C%BA%E5%99%A8%E4%BA%BA%E3%80%82&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2Ferii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

中文 | [English](./doc/README_en.md) | [繁體中文](./doc/README_zh-TW.md) | [한국어](./doc/README_ko.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Go](https://img.shields.io/badge/Go-1.21+-00ADD8?style=for-the-badge&logo=go&logoColor=white)](https://go.dev/)
[![Node.js](https://img.shields.io/badge/Node.js-18+-339933?style=for-the-badge&logo=node.js&logoColor=white)](https://nodejs.org/)
<br/>
[![Ktor](https://img.shields.io/badge/Ktor-3.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Koog_Agents](https://img.shields.io/badge/Koog_Agents-0.7+-FF6B6B?style=for-the-badge)](https://github.com/spcookie/koog)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![JobRunr](https://img.shields.io/badge/JobRunr-8.3+-green?style=for-the-badge)](https://jobrunr.io/)
<br/>
[![H2](https://img.shields.io/badge/H2-2.3+-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![Exposed](https://img.shields.io/badge/Exposed-1.1+-F3692A?style=for-the-badge)](https://github.com/JetBrains/Exposed)
[![Koin](https://img.shields.io/badge/Koin-4.1+-F3692A?style=for-the-badge)](https://insert-koin.io/)
[![PF4J](https://img.shields.io/badge/PF4J-3.15+-blue?style=for-the-badge)](https://pf4j.org/)
<br/>
[![Bubble_Tea](https://img.shields.io/badge/Bubble_Tea-TUI-2C3E50?style=for-the-badge&logo=bubble-tea&logoColor=white)](https://github.com/charmbracelet/bubbletea)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/erii)
[![Visitors](https://api.visitorbadge.io/api/visitors?path=https%3A%2F%2Fgithub.com%2Fspcookie%2Ferii&labelColor=%23d9e3f0&countColor=%23263759&style=flat)](https://visitorbadge.io/status?path=https%3A%2F%2Fgithub.com%2Fspcookie%2Ferii)

[🚀 功能特性](#-功能特性) • [🏁 快速开始](#-快速开始) • [📦 构建与运行](#-构建与运行) • [🛠️ 技术栈](#-技术栈)

</div>

---

## 📖 概述

erii 是一个具有情感、记忆、心流和主动行为的 AI 群聊机器人。基于多维度心理模型，erii 能够像真人一样参与群聊对话——主动插话、破冰，根据情绪状态调整说话风格，在长期互动中学习群聊用语、建立用户画像、跟踪流行梗。同时支持自我规则管理和定时摘要生成，通过 CLI 管理工具进行配置和数据管理。

**✨ 核心特性：**

- 🎭 **情绪系统**：基于 PAD 模型，模拟真实情感反应
- 🧠 **长期记忆**：多维度记录用户画像、偏好和群聊事实
- 🌊 **心流机制**：根据话题投入程度动态调整参与深度
- ⚡ **主动行为**：基于冲动值进行插话、破冰和日常互动
- 📜 **自我规则管理**：文件系统规则存储，多级作用域控制
- 📝 **摘要生成**：自动生成对话摘要，支持定时和手动触发

## 🚀 功能特性

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

#### 📜 自我规则管理 (Rule Management)

- **文件系统存储** - 规则以 Markdown 文件存储，支持热重载
- **多级作用域** - 全局规则 > Bot 规则 > 群组规则
- **AI 可控** - Agent 可通过 ToolSet 创建/删除规则
- **安全防护** - 路径遍历保护和文件名校验

#### ⏰ 定时任务系统 (Cron)

- **时间轮调度** - 基于内存时间轮的高效任务调度
- **提醒任务** - 支持单次和重复提醒，Cron 表达式定义周期
- **触发任务** - 支持路由触发和命令触发两种模式
- **自然推送** - AI 生成自然的提醒语气，避免系统化表达
- **作用域管理** - Bot/Group 级别任务隔离

#### 📝 摘要系统 (Summary)

- **自动摘要** - 定时生成群聊对话摘要
- **LLM 驱动** - AI 提取关键信息和话题脉络
- **历史回溯** - 支持查询和管理历史摘要记录

#### 🧬 进化系统 (Evolution)

- **词汇学习** - 自动学习群聊中的新词汇和梗
- **语义理解** - 记录词汇含义、使用场景和语气
- **自然融入** - 在合适场景自然使用学习到的群聊用语

#### 🎭 梗系统 (Meme)

- **梗提取** - 从群聊中自动提取流行语和梗
- **向量存储** - 语义向量表示，支持相似度检索
- **热度追踪** - 跟踪高频词汇的使用频率和流行度

### 🧩 组件服务

| 组件 | 说明 | 可选后端 |
|:---|:---|:---|
| **LLM 对话** | AI 对话推理 | Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter |
| **向量嵌入** | 文本和图像的语义向量化 | ByteDance Embedding / SiliconFlow |
| **网络搜索** | AI Agent 可调用的实时搜索 | Exa / MiniMax Search |
| **视觉分析** | 图片内容理解和描述 | MiniMax Vision |
| **浏览器** | 网页截图和 Markdown 抓取 | Playwright |

### 🔌 内置插件

| 插件 | 类型 | 说明 |
|:---|:---|:---|
| **speech** | AgentExtension | 语音合成插件，使用 MiniMax TTS 将文字转为语音发送 |
| **lolisuki** | RouteExtension | 二次元图片插件，从 lolisuki.cn 获取并发送图片 |
| **net-ease-music** | PassiveExtension | 网易云音乐插件，搜索音乐并发送音乐卡片 |
| **qq-face** | PassiveExtension | QQ 表情插件，语义匹配发送合适的表情 |
| **seeddream** | RouteExtension | AI 图片生成插件，支持文生图和图生图 |
| **rollpig** | AgentExtension | 抽小猪游戏插件，收集养成类互动游戏 |
| **animal** | AgentExtension | 虚拟宠物养成插件，角色扮演与好感度系统 |

### 💬 对话增强

- **上下文理解** - 引用历史对话和长期记忆
- **多 Persona** - 支持多 Bot 角色配置
- **情绪共鸣** - 感知群体氛围，调整参与方式
- **多 LLM 支持** - 6 种 LLM 提供商，可按 Lite/Flash/Pro 分档配置
- **工具调用** - AI Agent 可主动使用搜索、视觉、浏览器等工具

## 🏁 快速开始

### 📋 前置要求

- **Node.js 18+**（npm 安装方式）
- **JDK 17+**（源码构建方式）
- **NapCat** - QQ 接入服务
- **LLM API Key** - 至少一个 LLM 提供商的 API Key

### 方式一：npm 安装（推荐）

```bash
npm create @spcookie/erii
cd erii
erii setup     # 交互式配置向导
erii server    # 启动服务
```

或一键安装：

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.sh | bash

# Windows (PowerShell)
irm https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.ps1 | iex
```

`erii server` 支持子命令：`start`（默认）、`stop`、`status`、`restart`、`logs`。

### 方式二：Docker 部署

**启动 erii 主服务**

```bash
docker-compose up -d
```

**启动 NapCat QQ 接入服务**

```bash
cd docker/napcat-docker && docker-compose up -d
```

**启动 Playwright 浏览器服务**

```bash
cd docker/playwright-docker && docker-compose up -d
```

### 🖥️ CLI 配置工具

erii 提供交互式 CLI 工具，基于 Go + Bubble Tea 构建：

```bash
go install ./erii-cli
```

| 命令 | 说明 |
|:---|:---|
| `erii config` | 交互式 TUI 配置编辑器（env / app / plugin / souls / rules） |
| `erii config app get/set <key> [value]` | 读写 `application.conf` |
| `erii config env get/set <key> [value]` | 读写 `.env.local` |
| `erii config plugin get/set <plugin> <key> [value]` | 读写插件 JSON 配置 |
| `erii setup` | 设置向导（LLM、工具、连接、群组） |
| `erii manage` | 数据管理 TUI（facts、画像、memes、词汇、摘要） |
| `erii stats` | 机器人/群组状态统计 TUI |
| `erii refresh` | 刷新后端配置缓存 |
| `erii reload` | 重新加载插件配置和元数据 |

CLI 通过内存映射文件（mmap + MsgPack）自动发现服务端连接信息，无需手动配置。

## 🛠️ 技术栈

### 🖥️ 后端框架

- **语言:** Kotlin 2.2+
- **Web 框架:** Ktor 3.3+
- **构建工具:** Gradle 9.2+
- **数据库:** H2 (嵌入式)
- **ORM:** Exposed + Migration
- **依赖注入:** Koin 4.1+
- **任务调度:** JobRunr 8.3+
- **AI 框架:** Koog Agents 0.7+

### 🤖 机器人集成

- **机器人框架:** Mirai + Overflow
- **接入服务:** NapCat (WebSocket)
- **LLM 提供商:** Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter

### 🔌 扩展架构

- **插件框架:** PF4J 3.15+
- **SPI 接口:** erii-spi 模块定义 4 种扩展点
- **插件构建:** erii-plugin-gradle 约定插件

### 🖥️ CLI

- **语言:** Go 1.21+
- **TUI 框架:** Bubble Tea
- **构建:** Mage

### 📦 分发

- **包管理:** npm (monorepo workspaces)
- **运行时:** Node.js CLI 启动器
- **Git LFS:** 二进制构件版本管理

## 📦 构建与运行

| 命令 | 说明 |
|:---|:---|
| `./gradlew compileKotlin` | 编译 Kotlin 代码 |
| `./gradlew build` | 构建整个项目 |
| `./gradlew run` | 启动开发服务器 |
| `./gradlew buildFatJar` | 构建包含所有依赖的可执行 JAR |
| `./gradlew buildImage` | 构建 Docker 镜像 |
| `./gradlew test` | 运行所有测试 |
| `./gradlew :erii-core:test --tests "com.example.MyTest"` | 运行单个测试 |
| `cd erii-plugins && ./gradlew buildAllPlugins` | 构建全部插件 |
| `cd erii-plugins && ./gradlew :<name>:build` | 构建单个插件 |
| `cd erii-cli && go build .` | 构建 CLI 工具 |
| `cd erii-cli && mage All` | 交叉编译 CLI (所有平台) |

## 📂 项目结构

```
erii/
├── erii-common/                 # 📦 公共模块
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 异步/同步事件总线
│       ├── ChatToolSet.kt       # 聊天工具集
│       └── data/                # 数据模型 (History, Emotional, Resource)
├── erii-core/                   # 🧠 核心模块
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 应用入口
│       ├── config/              # 配置和 DI (IOC, LLMFactory, DatabaseConfig)
│       ├── core/
│       │   ├── agent/           # AI Agent (BotAgent, Prompts, Tools)
│       │   ├── route/           # 路由系统 (RoutingAgent, RouteCallEvent)
│       │   ├── state/           # 核心 AI 状态系统
│       │   │   ├── emotion/     # 情绪系统 (PAD 模型)
│       │   │   ├── memory/      # 记忆系统 (多维度向量检索)
│       │   │   ├── flow/        # 心流系统 (0-100 投入度)
│       │   │   ├── volition/    # 主动行为系统 (冲动值/疲劳)
│       │   │   ├── summary/     # 摘要系统 (定时对话摘要)
│       │   │   ├── evolution/   # 进化系统 (词汇学习)
│       │   │   └── meme/        # 梗系统 (流行语追踪)
│       │   ├── rule/            # 规则引擎 (Markdown 文件规则)
│       │   ├── cron/            # 定时任务 (时间轮 + JobRunr)
│       │   ├── bot/             # Bot 角色管理
│       │   ├── message/         # 消息处理 (history, resource)
│       │   ├── plugin/          # 插件加载和注册
│       │   └── component/       # 组件服务
│       │       ├── llm/         # LLM 客户端 (6 个提供商)
│       │       ├── embedding/   # 向量嵌入 (ByteDance/SiliconFlow)
│       │       ├── search/      # 搜索服务 (Exa/MiniMax)
│       │       ├── vision/      # 视觉分析 (MiniMax Vision)
│       │       ├── browser/     # 浏览器 (Playwright)
│       │       └── storage/     # 存储 (Vector/Object)
│       ├── routing/             # HTTP API 路由 (状态管理/配置)
│       └── cli/                 # IPC 通信 (mmap + MsgPack)
├── erii-spi/                    # 🔌 SPI 接口模块
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # 4 种扩展接口 + PluginContext
│       ├── PluginDefinition.kt # 编译时注解
│       └── processor/           # 注解处理器
├── erii-plugin-gradle/          # 🔧 插件构建工具
│   └── src/main/kotlin/uesugi/gradle/
│       └── EriiGradlePlugin.kt # 约定插件 (pluginZip, assemblePlugin)
├── erii-plugins/                # 🎨 插件 (Git Submodule)
│   ├── speech/                  # MiniMax TTS 语音合成
│   ├── lolisuki/                # 二次元图片获取
│   ├── net-ease-music/          # 网易云音乐搜索与卡片发送
│   ├── qq-face/                 # QQ 表情语义匹配发送
│   ├── seeddream/               # 豆包文生图/图生图
│   ├── rollpig/                 # 抽小猪收集游戏
│   └── animal/                  # 虚拟宠物养成
├── erii-cli/                    # 🖥️ CLI 工具 (Go)
│   ├── cmd/                    # 6 个命令 (config, setup, manage, stats, refresh, reload)
│   └── internal/               # API 客户端, IPC, TUI 组件
├── erii-distribution/           # 📦 分发 (Git Submodule)
│   └── packages/               # npm monorepo 包 (core, deps, browser, config, cli, runtime, plugins)
└── build.gradle.kts             # 根构建配置
```

## 🗺️ 架构设计

erii 采用事件驱动架构，通过 `EventBus` 实现系统间解耦。AI Agent 基于 Koog 的 `GraphAIAgent` 框架，支持工具调用、事件处理和状态管理。服务器通过 mmap 内存映射文件向 CLI 暴露连接信息，实现零配置进程间通信。

```
NapCat/Mirai (WebSocket)
       │
       ▼
GroupMessageEventListener (消息监听)
       │
       ▼
HistoryService (保存历史) + ResourceService (图片/资源去重存储)
       │
       ▼
RoutingAgent (LLM 意图分类) / CmdExtension (命令匹配)
       │
       ▼
EventBus.postAsync (RouteCallEvent / ProactiveSpeakEvent)
       │
       ▼
BotAgent (GraphAIAgent 消费事件)
       │
       ├── Tool Calls (ChatToolSet: sendText, sendMeme, sendImage, ...)
       ├── Component Tools (WebSearch, ChatVision, BrowserScraper)
       └── State Updates (Emotion / Memory / Flow / Volition / Evolution / Meme / Summary)
       │
       ▼
Response → Group (通过 OneBot/Mirai)
```

### 插件扩展点

| 扩展类型 | 说明 | 匹配方式 |
|:---|:---|:---|
| **AgentExtension** | 通用 Agent 扩展，始终参与 | 组合使用 |
| **RouteExtension** | LLM 路由扩展 | RoutingAgent 意图分类 |
| **CmdExtension** | 命令扩展 | `/xxx` 命令前缀匹配，支持别名 |
| **PassiveExtension** | 被动扩展 | 后台任务/事件监听 |

### 插件开发

插件基于 PF4J 框架，使用 `uesugi.erii-plugin` Gradle 约定插件构建。

**1. 创建插件项目**

在 `erii-plugins/` 下创建新目录，并在 `settings.gradle.kts` 中注册：

```kotlin
// erii-plugins/settings.gradle.kts
include("my-plugin")
```

**2. 配置构建**

```kotlin
// erii-plugins/my-plugin/build.gradle.kts
plugins {
    id("uesugi.erii-plugin")
}

version = "0.0.1"
```

**3. 实现插件**

使用 `@PluginDefinition` 注解，继承 `AgentPlugin`，实现对应的扩展接口：

```kotlin
@PluginDefinition(
    pluginId = "my-plugin",
    version = "0.0.1",
    description = "我的插件"
)
class MyPlugin : AgentPlugin()

class MyExtension : AgentExtension<MyPlugin> {
    override fun onLoad(context: PluginContext) {
        // 通过 PluginContext 访问 LLM、存储、调度器等能力
    }
}
```

**4. 构建与安装**

```bash
cd erii-plugins && ./gradlew :my-plugin:build
```

产出 zip 包位于 `build/plugin/`，放入 erii 的 `plugins/` 目录即可加载。

## 📄 许可证

本项目基于 [MIT](LICENSE) 许可证开源。

## Repobeats

![Alt](https://repobeats.axiom.co/api/embed/341cfbaa0a0048c8c95abe32707d6760903d13e0.svg "Repobeats analytics image")

## Star History

<a href="https://www.star-history.com/?repos=spcookie%2Ferii&type=date&logscale=&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=spcookie/erii&type=date&theme=dark&logscale&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=spcookie/erii&type=date&logscale&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=spcookie/erii&type=date&logscale&legend=top-left" />
 </picture>
</a>
