<div align="center">

![erii](https://socialify.git.ci/spcookie/erii/image?custom_description=An+AI+group+chat+bot+with+emotions%2C+memory%2C+flow-state+cognition%2C+proactive+behavior%2C+task+reminders%2C+and+self-rule+management.&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2Ferii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

English | [中文](../README.md) | [繁體中文](./README_zh-TW.md) | [한국어](./README_ko.md)

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

[🚀 Features](#-features) • [🏁 Getting Started](#-getting-started) • [🛠️ Tech Stack](#-tech-stack) • [📦 Building & Running](#-building--running) • [📂 Project Structure](#-project-structure) • [🗺️ Architecture](#-architecture)

</div>

---

## 📖 Overview

erii is an AI group chat bot that feels like a real person. It has moods, memories, and a personality — its mood shifts with the conversation, and its speaking style follows. erii doesn't just wait to be called; it actively reads the room: jumping in when it has something to say, staying quiet when it doesn't, and starting conversations when the group goes silent. The longer it chats, the better it knows everyone — it remembers preferences, picks up inside jokes, and gradually becomes a natural part of the group. erii also supports scheduled reminders, automatic chat summaries, rule-based behavior constraints, and CLI tools for configuration and data management.

**✨ Core Features:**

- 🎭 **Has Emotions**: Mood shifts with the conversation — chatty when happy, quieter when not
- 🧠 **Has Memory**: Remembers what people like and what they've said; the longer you talk, the better it knows you
- 🌊 **Knows When to Talk**: Gauges engagement before jumping in — speaks when it matters, stays out when it doesn't
- ⚡ **Takes Initiative**: Doesn't just wait for commands; joins conversations it finds interesting, warms up quiet groups
- 📜 **Follows Rules**: Behavior governed by Markdown rule files, with different rules for different groups
- 📝 **Summarizes**: Auto-generates chat summaries so you can catch up on what you missed

## 🚀 Features

### 🧠 Core Systems

#### 🎭 Emotion System

- **PAD Three-Dimensional Emotion Model** — Pleasure, Arousal, Dominance
- **Dynamic Emotion Regulation** — Real-time emotional state adjustment based on conversation content
- **Emotional Expression** — Affects reply tone, emoji usage, reply delay
- **Long-term Mood** — Maintains baseline mood tone, influencing overall behavioral tendencies

#### 💾 Memory System

- **Multi-dimensional Memory** — User profiles, preference settings, factual knowledge, conversation summaries
- **Three-level Scope** — Global memory, group memory, member-group memory
- **Semantic Retrieval** — Intelligent memory recall based on vector similarity
- **LLM-driven** — AI automatically extracts and manages long-term memory

#### 🌊 Flow System

- **Engagement Quantification** — 0-100 flow value, three-stage state machine
- **Topic Matching** — Quickly enters flow state when encountering interesting topics
- **Focus Mechanism** — Locks onto conversation goals during high flow, filtering irrelevant distractions
- **Overheat Protection** — Simulates fatigue to prevent excessive output

#### ⚡ Volition System

- **Impulse Calculation** — Calculates desire to speak proactively based on emotion, flow, and keywords
- **Three Interjection Modes** — Interrupt, Icebreak, Routine
- **Social Awareness** — Detects serious topics and group emotions to avoid inappropriate comments
- **Fatigue Suppression** — Accumulates fatigue after proactive speaking to prevent spamming

#### 📜 Self-Rule Management

- **File System Storage** — Rules stored as Markdown files with hot-reload support
- **Multi-level Scope** — Global rules > Bot rules > Group rules
- **AI Controllable** — Agent can create/delete rules via ToolSet
- **Security Protection** — Path traversal protection and filename validation

#### ⏰ Cron Scheduling System

- **Time-wheel Scheduling** — Efficient in-memory time-wheel based task scheduling
- **Reminder Tasks** — Supports one-time and recurring reminders with Cron expressions
- **Trigger Tasks** — Supports routing-triggered and command-triggered modes
- **Natural Push** — AI generates natural reminder tone, avoiding robotic expression
- **Scope Management** — Bot/Group level task isolation

#### 📝 Summary System

- **Auto Summarization** — Periodically generates conversation summaries
- **LLM-driven** — AI extracts key information and topic threads
- **History Review** — Supports querying and managing historical summary records

#### 🧬 Evolution System

- **Vocabulary Learning** — Automatically learns new words and memes from group chats
- **Semantic Understanding** — Records word meanings, usage scenarios, and tones
- **Natural Integration** — Naturally uses learned group chat expressions in appropriate scenarios

#### 🎭 Meme System

- **Meme Extraction** — Automatically extracts trending phrases and memes from group chats
- **Vector Storage** — Semantic vector representation, supporting similarity retrieval
- **Popularity Tracking** — Tracks usage frequency and popularity of high-frequency words

### 🧩 Component Services

| Component | Description | Backend Options |
|:---|:---|:---|
| **LLM Chat** | AI conversation inference | Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter |
| **Vector Embedding** | Text & image semantic vectorization | ByteDance Embedding / SiliconFlow |
| **Web Search** | Real-time search callable by AI Agent | Exa / MiniMax Search |
| **Vision** | Image content understanding and description | MiniMax Vision |
| **Browser** | Webpage screenshot and Markdown scraping | Playwright |

### 🔌 Built-in Plugins

| Plugin | Type | Description |
|:---|:---|:---|
| **speech** | AgentExtension | Text-to-speech plugin using MiniMax TTS |
| **lolisuki** | RouteExtension | Anime image plugin from lolisuki.cn |
| **net-ease-music** | PassiveExtension | NetEase music plugin for music cards |
| **qq-face** | PassiveExtension | QQ emoji semantic matching plugin |
| **seeddream** | RouteExtension | AI image generation plugin (text-to-image & image-to-image) |
| **rollpig** | AgentExtension | Pig-collecting game plugin with collection mechanics |
| **animal** | AgentExtension | Virtual pet breeding plugin with roleplay and affection system |

### 💬 Conversation Enhancement

- **Context Understanding** — References historical conversations and long-term memory
- **Multiple Personas** — Supports multiple bot role configurations
- **Emotional Resonance** — Perceives group atmosphere, adjusts participation style
- **Multi-LLM Support** — 6 LLM providers, configurable by Lite/Flash/Pro tiers
- **Tool Calling** — AI Agent can actively use search, vision, browser, and other tools

## 🏁 Getting Started

### 📋 Prerequisites

- **Node.js 18+** (for npm install)
- **JDK 17+** (for source build)
- **NapCat** — QQ integration service
- **LLM API Key** — At least one LLM provider API key

### Method 1: npm Install (Recommended)

```bash
npm create @spcookie/erii
cd erii
erii setup     # Interactive configuration wizard
erii server    # Start the server
```

Or one-line install:

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.sh | bash

# Windows (PowerShell)
irm https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.ps1 | iex
```

`erii server` supports subcommands: `start` (default), `stop`, `status`, `restart`, `logs`.

### Method 2: Docker Deployment

**Start erii main service**

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

### 🖥️ CLI Configuration Tool

erii provides an interactive CLI tool built with Go + Bubble Tea:

```bash
go install ./erii-cli
```

| Command | Description |
|:---|:---|
| `erii config` | Interactive TUI config editor (env / app / plugin / souls / rules) |
| `erii config app get/set <key> [value]` | Read/write `application.conf` |
| `erii config env get/set <key> [value]` | Read/write `.env.local` |
| `erii config plugin get/set <plugin> <key> [value]` | Read/write plugin JSON config |
| `erii setup` | Setup wizard (LLM, tools, connections, groups) |
| `erii manage` | Data management TUI (facts, profiles, memes, vocabulary, summaries) |
| `erii stats` | Bot/group statistics TUI |
| `erii refresh` | Refresh backend configuration cache |
| `erii reload` | Reload plugin configuration and metadata |

The CLI auto-discovers server connection info via memory-mapped files (mmap + MsgPack), requiring no manual configuration.

## 🛠️ Tech Stack

### 🖥️ Backend Framework

- **Language:** Kotlin 2.2+
- **Web Framework:** Ktor 3.3+
- **Build Tool:** Gradle 9.2+
- **Database:** H2 (Embedded)
- **ORM:** Exposed + Migration
- **Dependency Injection:** Koin 4.1+
- **Task Scheduling:** JobRunr 8.3+
- **AI Framework:** Koog Agents 0.7+

### 🤖 Bot Integration

- **Bot Framework:** Mirai + Overflow
- **Integration Service:** NapCat (WebSocket)
- **LLM Providers:** Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter

### 🔌 Extension Architecture

- **Plugin Framework:** PF4J 3.15+
- **SPI Interface:** erii-spi module defines 4 extension point types
- **Plugin Build:** erii-plugin-gradle convention plugin

### 🖥️ CLI

- **Language:** Go 1.21+
- **TUI Framework:** Bubble Tea
- **Build:** Mage

### 📦 Distribution

- **Package Manager:** npm (monorepo workspaces)
- **Runtime:** Node.js CLI launcher
- **Git LFS:** Binary artifact versioning

## 📦 Building & Running

| Command | Description |
|:---|:---|
| `./gradlew compileKotlin` | Compile Kotlin code |
| `./gradlew build` | Build the entire project |
| `./gradlew run` | Start development server |
| `./gradlew buildFatJar` | Build fat JAR with all dependencies |
| `./gradlew buildImage` | Build Docker image |
| `./gradlew test` | Run all tests |
| `./gradlew :erii-core:test --tests "com.example.MyTest"` | Run a single test |
| `cd erii-plugins && ./gradlew buildAllPlugins` | Build all plugins |
| `cd erii-plugins && ./gradlew :<name>:build` | Build a single plugin |
| `cd erii-cli && go build .` | Build CLI tool |
| `cd erii-cli && mage All` | Cross-compile CLI (all platforms) |

## 📂 Project Structure

```
erii/
├── erii-common/                 # 📦 Common module
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # Async/sync event bus
│       ├── ChatToolSet.kt       # Chat toolset
│       └── data/                # Data models (History, Emotional, Resource)
├── erii-core/                   # 🧠 Core module
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor application entry
│       ├── config/              # Configuration and DI (IOC, LLMFactory, DatabaseConfig)
│       ├── core/
│       │   ├── agent/           # AI Agent (BotAgent, Prompts, Tools)
│       │   ├── route/           # Routing system (RoutingAgent, RouteCallEvent)
│       │   ├── state/           # Core AI state systems
│       │   │   ├── emotion/     # Emotion system (PAD model)
│       │   │   ├── memory/      # Memory system (multi-dimension vector retrieval)
│       │   │   ├── flow/        # Flow system (0-100 engagement)
│       │   │   ├── volition/    # Volition system (impulse/fatigue)
│       │   │   ├── summary/     # Summary system (scheduled conversation summaries)
│       │   │   ├── evolution/   # Evolution system (vocabulary learning)
│       │   │   └── meme/        # Meme system (trending phrase tracking)
│       │   ├── rule/            # Rule engine (Markdown file rules)
│       │   ├── cron/            # Cron scheduling (time-wheel + JobRunr)
│       │   ├── bot/             # Bot role management
│       │   ├── message/         # Message handling (history, resource)
│       │   ├── plugin/          # Plugin loading and registration
│       │   └── component/       # Component services
│       │       ├── llm/         # LLM clients (6 providers)
│       │       ├── embedding/   # Vector embedding (ByteDance/SiliconFlow)
│       │       ├── search/      # Search service (Exa/MiniMax)
│       │       ├── vision/      # Vision analysis (MiniMax Vision)
│       │       ├── browser/     # Browser (Playwright)
│       │       └── storage/     # Storage (Vector/Object)
│       ├── routing/             # HTTP API routes (status management/config)
│       └── cli/                 # IPC communication (mmap + MsgPack)
├── erii-spi/                    # 🔌 SPI interface module
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # 4 extension interfaces + PluginContext
│       ├── PluginDefinition.kt # Compile-time annotation
│       └── processor/           # Annotation processor
├── erii-plugin-gradle/          # 🔧 Plugin build tool
│   └── src/main/kotlin/uesugi/gradle/
│       └── EriiGradlePlugin.kt # Convention plugin (pluginZip, assemblePlugin)
├── erii-plugins/                # 🎨 Plugins (Git Submodule)
│   ├── speech/                  # MiniMax TTS speech synthesis
│   ├── lolisuki/                # Anime image fetching
│   ├── net-ease-music/          # NetEase music search & card sending
│   ├── qq-face/                 # QQ emoji semantic matching
│   ├── seeddream/               # AI image generation
│   ├── rollpig/                 # Pig-collecting game
│   └── animal/                  # Virtual pet breeding
├── erii-cli/                    # 🖥️ CLI tool (Go)
│   ├── cmd/                    # 6 commands (config, setup, manage, stats, refresh, reload)
│   └── internal/               # API client, IPC, TUI components
├── erii-distribution/           # 📦 Distribution (Git Submodule)
│   └── packages/               # npm monorepo packages (core, deps, browser, config, cli, runtime, plugins)
└── build.gradle.kts             # Root build configuration
```

## 🗺️ Architecture

erii uses an event-driven architecture with `EventBus` for system decoupling. The AI Agent is built on Koog's `GraphAIAgent` framework, supporting tool calls, event handling, and state management. The server exposes connection info to the CLI via mmap memory-mapped files, enabling zero-configuration inter-process communication.

```
NapCat/Mirai (WebSocket)
       │
       ▼
GroupMessageEventListener
       │
       ▼
HistoryService (persist) + ResourceService (image/resource dedup storage)
       │
       ▼
RoutingAgent (LLM intent classification) / CmdExtension (command matching)
       │
       ▼
EventBus.postAsync (RouteCallEvent / ProactiveSpeakEvent)
       │
       ▼
BotAgent (GraphAIAgent consumes events)
       │
       ├── Tool Calls (ChatToolSet: sendText, sendMeme, sendImage, ...)
       ├── Component Tools (WebSearch, ChatVision, BrowserScraper)
       └── State Updates (Emotion / Memory / Flow / Volition / Evolution / Meme / Summary)
       │
       ▼
Response → Group (via OneBot/Mirai)
```

### Plugin Extension Points

| Extension Type | Description | Matching Method |
|:---|:---|:---|
| **AgentExtension** | General Agent extension, always active | Combined usage |
| **RouteExtension** | LLM routing extension | RoutingAgent intent classification |
| **CmdExtension** | Command extension | `/xxx` command prefix matching with aliases |
| **PassiveExtension** | Passive extension | Background tasks / event listeners |

### Plugin Development

Plugins are built on the PF4J framework using the `uesugi.erii-plugin` Gradle convention plugin.

**1. Create a plugin project**

Create a new directory under `erii-plugins/` and register it in `settings.gradle.kts`:

```kotlin
// erii-plugins/settings.gradle.kts
include("my-plugin")
```

**2. Configure the build**

```kotlin
// erii-plugins/my-plugin/build.gradle.kts
plugins {
    id("uesugi.erii-plugin")
}

version = "0.0.1"
```

**3. Implement the plugin**

Use the `@PluginDefinition` annotation, extend `AgentPlugin`, and implement the corresponding extension interface:

```kotlin
@PluginDefinition(
    pluginId = "my-plugin",
    version = "0.0.1",
    description = "My Plugin"
)
class MyPlugin : AgentPlugin()

class MyExtension : AgentExtension<MyPlugin> {
    override fun onLoad(context: PluginContext) {
        // Access LLM, storage, scheduler, etc. via PluginContext
    }
}
```

**4. Build & Install**

```bash
cd erii-plugins && ./gradlew :my-plugin:build
```

The output zip is in `build/plugin/` — drop it into erii's `plugins/` directory to load.

## 📄 License

This project is open source under the [MIT](LICENSE) license.

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
