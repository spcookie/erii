<div align="center">

![Erii](https://socialify.git.ci/spcookie/Erii/image?custom_description=An+AI+group+chat+robot+with+emotions%2C+memory%2C+flow%2C+and+proactive+behavior&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2FErii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

[English](./README.md) | [中文](./doc/README_zh.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![Koin](https://img.shields.io/badge/Koin-3.5+-F3692A?style=for-the-badge)](https://insert-koin.io/)
[![Exposed](https://img.shields.io/badge/Exposed-ORM-F3692A?style=for-the-badge)](https://github.com/JetBrains/Exposed)
[![JobRunr](https://img.shields.io/badge/JobRunr-6.2+-green?style=for-the-badge)](https://jobrunr.io/)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/Erii)

[![Visitors](https://api.visitorbadge.io/api/visitors?path=https%3A%2F%2Fgithub.com%2Fspcookie%2FErii&labelColor=%23d9e3f0&countColor=%23263759)](https://visitorbadge.io/status?path=https%3A%2F%2Fgithub.com%2Fspcookie%2FErii)

[🚀 Features](#-features) • [🏁 Getting Started](#-getting-started) • [📦 Building & Running](#-building--running) • [🛠️ Tech Stack](#-tech-stack)

</div>

---

## 📖 Overview

Erii is an AI group chat bot system based on a multi-dimensional psychological model. Unlike traditional Q&A bots, Erii
has an **emotion system**, **long-term memory**, **conversation flow** and **proactive behaviors**, enabling it to
participate in group chats like a real person, interjecting, breaking the ice, and adjusting speaking style based on
emotional state.

**✨ Core Features:**

- 🎭 **Emotion System**: Based on PAD model, simulating real emotional responses
- 🧠 **Long-term Memory**: Multi-dimensional recording of user profiles, preferences, and group chat facts
- 🌊 **Flow Mechanism**: Dynamically adjusts participation depth based on topic engagement
- ⚡ **Proactive Behaviors**: Initiates conversations based on impulse values for ice-breaking and daily interactions

## 🚀 Features

### 🧠 Core Systems

#### 🎭 Emotion System (Emotion)

- **PAD Three-Dimensional Emotion Model** - Pleasure, Arousal, Dominance
- **Dynamic Emotion Regulation** - Real-time emotional state adjustment based on conversation content
- **Emotional Expression** - Affects reply tone, emoji usage, reply delay
- **Long-term Mood** - Maintains baseline mood tone, influencing overall behavioral tendencies

#### 💾 Memory System (Memory)

- **Multi-dimensional Memory** - User profiles, preference settings, factual knowledge, conversation summaries
- **Three-level Scope** - Global memory, group memory, member-group memory
- **Semantic Retrieval** - Intelligent memory recall based on vector similarity
- **LLM-driven** - AI automatically extracts and manages long-term memory

#### 🌊 Flow System (Flow)

- **Engagement Quantification** - 0-100 flow value, three-stage state machine
- **Topic Matching** - Quickly enters flow state when encountering interesting topics
- **Focus Mechanism** - Locks onto conversation goals during high flow, filtering irrelevant distractions
- **Overheat Protection** - Simulates fatigue to prevent excessive output

#### ⚡ Volition System (Volition)

- **Impulse Calculation** - Calculates desire to speak proactively based on emotion, flow, and keywords
- **Three Interjection Modes** - Interrupt, Icebreak, Routine
- **Social Awareness** - Detects serious topics and group emotions to avoid inappropriate comments
- **Fatigue Suppression** - Accumulates fatigue after proactive speaking to prevent spamming

#### 🧬 Evolution System (Evolution)

- **Vocabulary Learning** - Automatically learns new words and memes from group chats
- **Semantic Understanding** - Records word meanings, usage scenarios, and tones
- **Natural Integration** - Naturally uses learned group chat expressions in appropriate scenarios

#### 🎭 Meme System (Meme)

- **Meme Extraction** - Automatically extracts trending phrases and memes from group chats
- **Vector Storage** - Semantic vector representation, supporting similarity retrieval
- **Popularity Tracking** - Tracks usage frequency and popularity of high-frequency words

### 🔌 Built-in Plugins

| Plugin             | Type             | Description                             |
|:-------------------|:-----------------|:----------------------------------------|
| **speech**         | AgentExtension   | Text-to-speech plugin using MiniMax TTS |
| **lolisuki**       | RouteExtension   | Anime image plugin from lolisuki.cn     |
| **net-ease-music** | PassiveExtension | NetEase music plugin for music cards    |
| **qa**             | RouteExtension   | AI Q&A plugin with web search           |
| **qq-face**        | PassiveExtension | QQ emoji semantic matching plugin       |
| **reminder**       | AgentExtension   | Scheduled reminder plugin               |
| **seeddream**      | RouteExtension   | AI image generation plugin              |

### 💬 Conversation Enhancement

- **Context Understanding** - References historical conversations and long-term memory
- **Multiple Personas** - Supports multiple bot role configurations
- **Emotional Resonance** - Perceives group atmosphere, adjusts participation style

## 🏁 Getting Started

### 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 17+**
- **Gradle 8.0+**
- **NapCat** - QQ integration service
- **LLM API Key** - Google Gemini / DeepSeek / MiniMax API

### ⚙️ Installation & Configuration

1. **Clone the repository**
   ```bash
   git clone https://github.com/spcookie/Erii.git
   cd Erii
   ```

2. **Configure environment variables**
   Copy and edit the environment template:
   ```bash
   cp .env.local.template .env.local
   nano .env.local
   ```
   Main configuration items:
   - `CHOICE_MODEL`: Select LLM provider (GOOGLE / DEEP_SEEK / MINIMAX)
   - Corresponding API Key configuration
   - `NAPCAT_TOKEN`: NapCat authentication token
   - `ENABLE_GROUPS`: List of enabled groups

3. **Configure the bot**
   Copy and edit the configuration template:
   ```bash
   cp application.conf.template application.conf
   nano application.conf
   ```
   Main configuration items:
   - `llm.choice-model`: Select LLM provider (GOOGLE / DEEP_SEEK / MINIMAX)
   - `onebot.bots`: QQ bot connection information
   - `groups.enable-groups`: List of enabled groups

4. **Start the development server**
   ```bash
   ./gradlew run
   ```

### 🐳 Docker Deployment

**Start Erii main service**
```bash
docker-compose up -d
```

**Start NapCat QQ integration service**
```bash
cd docker/napcat-docker && docker-compose up -d
```

**Start Playwright browser service**

```bash
cd docker/playwright-docker && docker-compose up -d
```

## 🛠️ Tech Stack

### 🖥️ Backend Framework

- **Language:** Kotlin 1.9+
- **Web Framework:** Ktor 2.3+
- **Build Tool:** Gradle 8.0+
- **Database:** H2 (Embedded)
- **ORM:** Exposed
- **Dependency Injection:** Koin
- **Task Scheduling:** JobRunr

### 🤖 Bot Integration

- **Bot Framework:** Mirai + Overflow
- **Integration Service:** NapCat (WebSocket)
- **LLM:** Google Gemini API / OpenAI Compatible API

### 🔌 Extension Architecture

- **Plugin Framework:** PF4J
- **SPI Interface:** erii-spi module defines extension points

## 📦 Building & Running

| Command                   | Description                 |
|:--------------------------|:----------------------------|
| `./gradlew compileKotlin` | Compile Kotlin code         |
| `./gradlew build`         | Build the entire project    |
| `./gradlew run`           | Start development server    |
| `./gradlew buildFatJar`   | Build fat JAR with all deps |
| `./gradlew buildImage`    | Build Docker image          |
| `./gradlew test`          | Run all tests               |

## 📂 Project Structure

```
Erii/
├── erii-common/                 # 📦 Common module
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # Async/sync event bus
│       ├── BotRole.kt           # Bot role definitions
│       ├── ChatToolSet.kt       # Chat toolset
│       └── ...
├── erii-core/                   # 🧠 Core module
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor application entry
│       ├── config/              # Configuration and DI
│       │   ├── IOC.kt          # Koin DI configuration
│       │   ├── LLMFactory.kt   # LLM factory
│       │   └── ...
│       ├── core/
│       │   ├── agent/          # AI Agent
│       │   │   └── BotAgent.kt  # Core AI Agent
│       │   ├── route/          # Routing system
│       │   │   └── RoutingAgent.kt  # LLM routing
│       │   ├── state/          # Core AI state systems
│       │   │   ├── emotion/     # Emotion system
│       │   │   ├── memory/      # Memory system
│       │   │   ├── flow/        # Flow system
│       │   │   ├── volition/    # Volition system
│       │   │   ├── evolution/   # Evolution system
│       │   │   └── meme/        # Meme system
│       │   ├── bot/            # Bot role management
│       │   │   └── BotRoleManager.kt
│       │   ├── message/        # Message handling
│       │   │   └── history/    # History service
│       │   ├── plugin/         # Plugin implementations
│       │   ├── rule/           # Rule engine
│       │   └── component/      # Components
│       │       ├── browser/   # Browser (screenshot/markdown fetching)
│       │       ├── embedding/  # Vector embedding
│       │       ├── search/     # Search service
│       │       └── storage/    # Storage (vector/object)
│       └── server/             # HTTP API routes
├── erii-spi/                    # 🔌 SPI interface module
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # Agent extension interface
│       ├── PluginDefinition.kt # Plugin definition annotation
│       └── processor/           # Annotation processor
├── erii-plugins/                # 🎨 Plugin modules
│   ├── speech/                  # MiniMax TTS
│   ├── lolisuki/                # Anime images
│   ├── net-ease-music/          # NetEase music search & cards
│   ├── qa/                      # AI web search Q&A
│   ├── qq-face/                 # QQ emoji semantic matching
│   ├── reminder/                # Scheduled reminders & delayed messages
│   └── seeddream/               # AI image generation
└── build.gradle.kts             # Root build configuration
```

## 🗺️ Architecture

Erii uses an event-driven architecture with `EventBus` for system decoupling.

```
Message Received (NapCat/Mirai)
       │
       ▼
GroupMessageEventListener
       │
       ▼
HistoryService (Save History)
       │
       ▼
RoutingAgent (LLM Intent Classification) / CmdRuleRegister (Command Matching)
       │
       ▼
RouteCallEvent (Dispatched via EventBus.postAsync)
       │
       ▼
BotAgent (Consumes events, executes AI Agent)
       │
       ▼
State Updates (Emotion, Memory, Flow, Volition, Evolution, Meme)
```

### Plugin Extension Points

| Extension Type       | Description             | Matching Method                    |
|:---------------------|:------------------------|:-----------------------------------|
| **AgentExtension**   | General Agent extension | Combined usage                     |
| **RouteExtension**   | LLM routing extension   | RoutingAgent intent classification |
| **CmdExtension**     | Command extension       | `/xxx` command matching            |
| **PassiveExtension** | Passive extension       | Background tasks/event listeners   |

## 📄 License

This project is open source under the [MIT](LICENSE) license.

![Alt](https://repobeats.axiom.co/api/embed/341cfbaa0a0048c8c95abe32707d6760903d13e0.svg "Repobeats analytics image")
