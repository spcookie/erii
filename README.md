<div align="center">

# 🌟 EriiX

**一个具有情感、记忆、心流和主动行为的 AI 群聊机器人**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/EriiX)

[🚀 Features](#-features) • [🛠️ Tech Stack](#-tech-stack) • [🏁 Getting Started](#-getting-started) • [📦 Building & Running](#-building--running)

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

## 🚀 Features

### 🧠 核心系统

#### 🎭 情绪系统 (Emotion)
- **PAD 三维情绪模型** - Pleasure(愉悦度)、Arousal(激活度)、Dominance(优势度)
- **动态情绪调节** - 根据对话内容实时调整情绪状态
- **情绪表达** - 影响回复语气、表情符号使用、回复延迟
- **长期心情** - 维护基础心情基调，影响整体行为倾向

#### 💾 记忆系统 (Memory)
- **多维度记忆** - 用户画像、偏好设置、事实知识、待办事项、对话摘要
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
- **热词追踪** - 跟踪高频词汇的使用频率和流行度

#### 📚 历史系统 (History)
- **对话存储** - 完整保存群聊历史消息
- **上下文提供** - 为 AI 生成提供近期对话上下文
- **时间检索** - 按时间范围查询历史记录
- **用户追踪** - 记录用户发言历史和互动模式

#### 💬 对话增强
- **上下文理解** - 引用历史对话和长期记忆
- **多 Persona** - Erii(天真)、Eva(理性)、Nono(强势)
- **情绪共鸣** - 感知群体氛围，调整参与方式

### 🔌 扩展功能

- **🎮 Steam 游戏监控** - 订阅 Steam 游戏更新推送
- **🎨 图片生成** - 集成 AI 图片生成服务
- **❓ 知识问答** - 技术问题直接委托 AI 回答
- **🧩 自定义扩展** - 插件式架构，轻松添加新功能

## 🏁 Getting Started

### 📋 前置要求
在开始之前，确保已安装以下环境：

- **JDK 11+**
- **Gradle 8.0+**
- **NapCat** - QQ 接入服务
- **Google Gemini API Key** - 大语言模型 API

### ⚙️ 安装与配置

1. **克隆仓库**
   ```bash
   git clone https://github.com/spcookie/EriiX.git
   cd EriiX
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
     #DEBUG_GROUP_ID=
     
     # 启用的群组列表（必填，多个群组用逗号分隔）
     ENABLE_GROUPS=1053148332,474270623
     
     # 消息重定向映射（可选，格式：源群_:目标群,多个映射用逗号分隔）
     MESSAGE_REDIRECT_MAP=1053148332:474270623
     
     # Steam 游戏订阅配置（可选，格式：群ID,QQ号,SteamID;多个订阅用分号分隔）
     STEAM_SUBSCRIPTIONS=1053148332,2697951448,76561198415512702;1053148332,1,76561199087375065
   ```

3. **构建并运行**
   ```bash
   ./gradlew run
   ```

### 💬 配置 NapCat

EriiX 使用 NapCat 作为 QQ 接入层，请参考 [NapCat 官方文档](https://github.com/NapNeko/NapNeko/NapCatQQ) 进行配置。

## 📦 Building & Running

| 命令 | 说明 |
| :--- | :--- |
| `./gradlew build` | 构建整个项目 |
| `./gradlew run` | 启动开发服务器 |
| `./gradlew buildFatJar` | 构建包含所有依赖的可执行 JAR |
| `./gradlew buildImage` | 构建 Docker 镜像 |

## 📂 Project Structure

```bash
EriiX/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── core/              # 🧠 核心系统模块 (情绪、记忆、心流等)
│   │   │   ├── plugins/           # 🔌 插件模块 (Steam, Image, etc.)
│   │   │   ├── config/            # ⚙️ 配置和依赖注入
│   │   │   ├── server/            # 🌐 Ktor 服务器配置
│   │   │   └── Application.kt     # 🚀 应用入口
│   │   └── resources/             # 📄 资源文件
└── build.gradle.kts               # 🔨 构建配置
```

## 🗺️ Architecture

EriiX 采用事件驱动架构，通过 `EventBus` 实现系统间解耦。

1. **消息接收** 📥 -> 2. **历史记录** 📝 -> 3. **事件触发** 🔔 -> 4. **Agent 处理** 🤖 -> 5. **LLM 生成** 🧠 -> 6. **状态更新
   ** 🔄

## 📄 License

本项目基于 [MIT](LICENSE) 许可证开源。

![Alt](https://repobeats.axiom.co/api/embed/12134436f49b0440db57c5d06c901307da82bdce.svg "Repobeats analytics image")
