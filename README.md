<div align="center">

# Erii

**一个具有情感、记忆、心流和主动行为的 AI 群聊机器人**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

[Features](#features) • [Tech Stack](#tech-stack) • [Getting Started](#getting-started) • [Building & Running](#building--running) • [Documentation](#documentation)

</div>

---

## Overview

Erii 是一个基于多维心理模型的 AI 群聊机器人系统。不同于传统的问答机器人，Erii 拥有**情绪系统**、**长期记忆**、**对话心流**和
**主动行为**，能够像真人一样参与群聊对话，主动插话、破冰，并根据情绪状态调整说话风格。

**核心特性：**

- 基于 PAD 模型的情绪系统，模拟真实情感反应
- 多维度长期记忆，记住用户画像、偏好和群聊事实
- 心流机制，根据话题投入程度动态调整参与深度
- 主动行为系统，基于冲动值进行插话、破冰和日常互动

## Tech Stack

<table>
<tr>
<td width="50%" valign="top">

### 后端框架

- **语言:** Kotlin 1.9+
- **Web 框架:** Ktor 2.3+
- **构建工具:** Gradle 8.0+
- **数据库:** H2
- **ORM:** Exposed
- **依赖注入:** Koin

</td>
<td width="50%" valign="top">

### QQ 机器人

- **机器人框架:** Mirai + Overflow
- **接入服务:** NapCat (WebSocket)
- **LLM:** Google Gemini API

</td>
</tr>
</table>

## Features

### 核心系统

<table>
<tr>
<td width="50%" valign="top">

#### 情绪系统 (Emotion)

- ✅ **PAD 三维情绪模型** - Pleasure(愉悦度)、Arousal(激活度)、Dominance(优势度)
- ✅ **动态情绪调节** - 根据对话内容实时调整情绪状态
- ✅ **情绪表达** - 影响回复语气、表情符号使用、回复延迟
- ✅ **长期心情** - 维护基础心情基调，影响整体行为倾向

</td>
<td width="50%" valign="top">

#### 记忆系统 (Memory)

- ✅ **多维度记忆** - 用户画像、偏好设置、事实知识、待办事项、对话摘要
- ✅ **三级作用域** - 全局记忆、群组记忆、成员-群组记忆
- ✅ **语义检索** - 基于向量相似度的智能记忆召回
- ✅ **LLM 驱动** - AI 自动提取和管理长期记忆

</td>
</tr>
<tr>
<td width="50%" valign="top">

#### 心流系统 (Flow)

- ✅ **投入度量化** - 0-100 心流值，三阶段状态机
- ✅ **话题匹配** - 遇到感兴趣话题快速进入心流状态
- ✅ **专注机制** - 高心流时锁定对话目标，过滤无关干扰
- ✅ **过热保护** - 模拟疲劳感，避免过度输出

</td>
<td width="50%" valign="top">

#### 主动行为系统 (Volition)

- ✅ **冲动值计算** - 基于情绪、心流、关键词计算主动发言欲望
- ✅ **三种插话模式** - 插话(Interrupt)、破冰(Icebreak)、日常(Routine)
- ✅ **识相机制** - 检测严肃话题、群体情绪，避免不合时宜发言
- ✅ **疲劳抑制** - 主动发言后积累疲劳值，防止刷屏

</td>
</tr>
<tr>
<td width="50%" valign="top">

#### 进化系统 (Evolution)

- ✅ **词汇学习** - 自动学习群聊中的新词汇和梗
- ✅ **语义理解** - 记录词汇含义、使用场景和语气
- ✅ **自然融入** - 在合适场景自然使用学习到的群聊用语
- ✅ **热词追踪** - 跟踪高频词汇的使用频率和流行度

</td>
<td width="50%" valign="top">

#### 历史系统 (History)

- ✅ **对话存储** - 完整保存群聊历史消息
- ✅ **上下文提供** - 为 AI 生成提供近期对话上下文
- ✅ **时间检索** - 按时间范围查询历史记录
- ✅ **用户追踪** - 记录用户发言历史和互动模式

</td>
</tr>
</table>

### 🔌 扩展功能

<table>
<tr>
<td width="50%" valign="top">

#### 插件系统

- ✅ **Steam 游戏监控** - 订阅 Steam 游戏更新推送
- ✅ **图片生成** - 集成 AI 图片生成服务
- ✅ **知识问答** - 技术问题委直接托理 AI 回答
- ✅ **自定义扩展** - 插件式架构，轻松添加新功能

</td>
<td width="50%" valign="top">

#### 对话增强

- ✅ **上下文理解** - 引用历史对话和长期记忆
- ✅ **多 Persona** - Erii(天真)、Eva(理性)、Nono(强势)
- ✅ **词汇学习** - 自动学习群聊梗和常用语
- ✅ **情绪共鸣** - 感知群体氛围，调整参与方式

</td>
</tr>
</table>

## Getting Started

### Prerequisites

在开始之前，确保已安装以下环境：

- **JDK 11+** - Java 开发工具包
- **Gradle 8.0+** - 构建工具
- **NapCat** - QQ 接入服务
- **Google Gemini API Key** - 大语言模型 API

### Installation

1. **克隆仓库**
   ```bash
   git clone <repository-url>
   cd Erii
   ```

2. **配置环境变量**

   创建 `.env.local` 文件并配置以下变量：
   ```properties
   # Google Gemini API 密钥（必填）
   GOOGLE_API_KEY=your_google_api_key_here
   
   # Steam API 密钥（可选，用于 Steam 游戏监控插件）
   STEAM_API_KEY=your_steam_api_key_here
   
   # HTTP 代理（可选，用于访问 Google API）
   HTTP_PROXY=http://127.0.0.1:7890
   
   # NapCat WebSocket 地址（必填）
   NAPCAT_WS=ws://127.0.0.1:3001
   
   # NapCat 访问令牌（可选，如果 NapCat 配置了验证）
   NAPCAT_TOKEN=your_napcat_token
   
   # 调试群组 ID（可选，开发模式下仅响应此群）
   DEBUG_GROUP_ID=
   
   # 启用的群组列表（必填，多个群组用逗号分隔）
   ENABLE_GROUPS=1053148332,474270623
   
   # 消息重定向映射（可选，格式：源群_:目标群,多个映射用逗号分隔）
   MESSAGE_REDIRECT_MAP=1053148332:474270623
   
   # Steam 游戏订阅配置（可选，格式：群ID,QQ号,SteamID;多个订阅用分号分隔）
   STEAM_SUBSCRIPTIONS=1053148332,2697951448,76561198415512702;1053148332,1,76561199087375065
   ```

3. **配置应用**

   编辑 `src/main/resources/application.yaml` 配置 Ktor 服务器：
   ```yaml
   ktor:
     application:
       modules:
         - uesugi.ApplicationKt.module
     deployment:
       port: 8080  # HTTP 服务端口
   ```

4. **构建项目**
   ```bash
   ./gradlew build
   ```

5. **启动应用**
   ```bash
   ./gradlew run
   ```

### 配置 NapCat

Erii 使用 NapCat 作为 QQ 接入层，请参考 [NapCat 官方文档](https://github.com/NapNeko/NapCatQQ) 进行配置：

1. 启动 NapCat 服务
2. 在 NapCat 配置中启用 **WebSocket 正向连接** (默认端口 3001)
3. 将 WebSocket 地址配置到 `.env.local` 的 `NAPCAT_WS` 变量中

## Building & Running

### Available Commands

| Command                                 | Description       |
|-----------------------------------------|-------------------|
| `./gradlew test`                        | 运行测试套件            |
| `./gradlew build`                       | 构建整个项目            |
| `./gradlew buildFatJar`                 | 构建包含所有依赖的可执行 JAR  |
| `./gradlew buildImage`                  | 构建 Docker 镜像      |
| `./gradlew publishImageToLocalRegistry` | 发布 Docker 镜像到本地仓库 |
| `./gradlew run`                         | 启动开发服务器           |
| `./gradlew runDocker`                   | 使用本地 Docker 镜像运行  |

### Successful Startup

成功启动后，您将看到：

```console
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
2024-12-04 14:32:45.695 [main] INFO  uesugi - H2 console started at http://localhost:8082
2026-01-17 22:51:04 INFO  [main @coroutine#1] org.jobrunr.dashboard.JobRunrDashboardWebServer JobRunr Dashboard using H2StorageProvider started at http://localhost:8000/dashboard
```

**管理控制台:**

- **Ktor Server**: `http://localhost:8080` - REST API 接口
- **H2 Database Console**: `http://localhost:8082` - 数据库管理界面
- **JobRunr Dashboard**: `http://localhost:8000/dashboard` - 后台任务监控面板

### Docker Deployment

使用 Docker 构建和运行：

```bash
# 构建 Docker 镜像
./gradlew buildImage

# 运行容器
./gradlew runDocker
```

或使用 docker-compose：

```bash
docker-compose up -d
```

## Project Structure

```
Erii/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── core/              # 核心系统模块
│   │   │   ├── plugins/           # 插件模块
│   │   │   ├── config/            # 配置和依赖注入
│   │   │   ├── server/            # Ktor 服务器配置
│   │   │   ├── routing/           # API 路由处理
│   │   │   ├── toolkit/           # 工具类
│   │   │   ├── Application.kt     # 应用入口
│   │   │   └── BotManage.kt       # 机器人管理
│   │   └── resources/
│   │       └── application.yaml   # Ktor 配置文件
│   └── test/                      # 测试文件
├── store/                         # H2 数据库文件目录
├── build.gradle.kts               # Gradle 构建配置
└── README.md                      # 本文件
```

## Documentation

### 系统设计文档

详细的系统设计文档位于 `doc/` 目录：

- **[emotion.md](doc/emotion.md)** - 情绪系统：PAD 三维模型、情绪计算引擎、表现层映射
- **[memory.md](doc/memory.md)** - 记忆系统：多维度记忆、作用域隔离、RAG 检索
- **[flow.md](doc/flow.md)** - 心流系统：投入度计算、状态分层、专注机制
- **[volition.md](doc/volition.md)** - 主动行为系统：冲动值计算、三大主动模式、抑制机制
- **[evolution.md](doc/evolution.md)** - 进化系统：词汇学习和群聊梗记忆
- **[meme.md](doc/meme.md)** - 梗文化：群聊用语识别和使用

### 技术参考

- [Kotlin 官方文档](https://kotlinlang.org/docs/home.html) - Kotlin 语言指南
- [Ktor 文档](https://ktor.io/docs/home.html) - Ktor 框架文档
- [Koog Framework](https://github.com/koog-ai/koog) - Koog AI Agent 框架
- [Mirai 文档](https://github.com/mamoe/mirai) - Mirai QQ 机器人框架
- [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki) - Exposed ORM 文档
- [Koin 文档](https://insert-koin.io/) - Koin 依赖注入文档
- [NapCat 文档](https://github.com/NapNeko/NapCatQQ) - NapCat QQ 接入服务

### 架构说明

Erii 采用事件驱动架构，通过 `EventBus` 实现系统间解耦。核心工作流程：

1. **消息接收** → Mirai Overflow 接收 QQ 消息
2. **历史记录** → HistoryService 保存到数据库
3. **事件触发** → 发布 ProactiveSpeakEvent
4. **Agent 处理** → BotAgent 加载上下文（情绪、记忆、心流、主动行为）
5. **LLM 生成** → 使用 Koog Agents + Gemini API 生成回复
6. **状态更新** → 更新情绪、心流、疲劳值等状态

## License

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件。

