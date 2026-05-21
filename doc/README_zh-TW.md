<div align="center">

![erii](https://socialify.git.ci/spcookie/erii/image?custom_description=%E4%B8%80%E5%80%8B%E5%85%B7%E5%82%99%E6%83%85%E6%84%9F%E3%80%81%E8%A8%98%E6%86%B6%E3%80%81%E5%BF%83%E6%B5%81%E3%80%81%E4%B8%BB%E5%8B%95%E8%A1%8C%E7%82%BA%EF%BC%8C%E4%BB%A5%E5%8F%8A%E4%BA%8B%E9%A0%85%E6%8F%90%E9%86%92%E8%88%87%E8%87%AA%E6%88%91%E8%A6%8F%E5%89%87%E7%AE%A1%E7%90%86%E7%9A%84+AI+%E7%BE%A4%E8%81%8A%E6%A9%9F%E5%99%A8%E4%BA%BA%E3%80%82&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2Ferii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

繁體中文 | [English](./README_en.md) | [中文](../README.md) | [한국어](./README_ko.md)

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

[🚀 功能特性](#-功能特性) • [🏁 快速開始](#-快速開始) • [📦 建置與執行](#-建置與執行) • [🛠️ 技術堆疊](#-技術堆疊)

</div>

---

## 📖 概述

erii 是一個具有情感、記憶、心流和主動行為的 AI 群聊機器人。基於多維度心理模型，erii 能夠像真人一樣參與群聊對話——主動插話、破冰，根據情緒狀態調整說話風格，在長期互動中學習群聊用語、建立用戶畫像、追蹤流行梗。同時支援自我規則管理和定時摘要生成，透過 CLI 管理工具進行設定和資料管理。

**✨ 核心特性：**

- 🎭 **情緒系統**：基於 PAD 模型，模擬真實情感反應
- 🧠 **長期記憶**：多維度記錄用戶畫像、偏好和群聊事實
- 🌊 **心流機制**：根據話題投入程度動態調整參與深度
- ⚡ **主動行為**：基於衝動值進行插話、破冰和日常互動
- 📜 **自我規則管理**：檔案系統規則儲存，多級作用域控制
- 📝 **摘要生成**：自動生成對話摘要，支援定時和手動觸發

## 🚀 功能特性

### 🧠 核心系統

#### 🎭 情緒系統 (Emotion)

- **PAD 三維情緒模型** - Pleasure(愉悅度)、Arousal(激活度)、Dominance(優勢度)
- **動態情緒調節** - 根據對話內容即時調整情緒狀態
- **情緒表達** - 影響回覆語氣、表情符號使用、回覆延遲
- **長期心情** - 維護基礎心情基調，影響整體行為傾向

#### 💾 記憶系統 (Memory)

- **多維度記憶** - 用戶畫像、偏好設定、事實知識、對話摘要
- **三級作用域** - 全域記憶、群組記憶、成員-群組記憶
- **語義檢索** - 基於向量相似度的智慧記憶召回
- **LLM 驅動** - AI 自動提取和管理長期記憶

#### 🌊 心流系統 (Flow)

- **投入度量化** - 0-100 心流值，三階段狀態機
- **話題匹配** - 遇到感興趣話題快速進入心流狀態
- **專注機制** - 高心流時鎖定對話目標，過濾無關干擾
- **過熱保護** - 模擬疲勞感，避免過度輸出

#### ⚡ 主動行為系統 (Volition)

- **衝動值計算** - 基於情緒、心流、關鍵詞計算主動發言欲望
- **三種插話模式** - 插話(Interrupt)、破冰(Icebreak)、日常(Routine)
- **識相機制** - 檢測嚴肅話題、群體情緒，避免不合時宜發言
- **疲勞抑制** - 主動發言後累積疲勞值，防止刷屏

#### 📜 自我規則管理 (Rule Management)

- **檔案系統儲存** - 規則以 Markdown 檔案儲存，支援熱重載
- **多級作用域** - 全域規則 > Bot 規則 > 群組規則
- **AI 可控** - Agent 可透過 ToolSet 建立/刪除規則
- **安全防護** - 路徑遍歷保護和檔案名驗證

#### ⏰ 定時任務系統 (Cron)

- **時間輪調度** - 基於記憶體時間輪的高效任務調度
- **提醒任務** - 支援單次和重複提醒，Cron 表達式定義週期
- **觸發任務** - 支援路由觸發和命令觸發兩種模式
- **自然推送** - AI 生成自然的提醒語氣，避免系統化表達
- **作用域管理** - Bot/Group 級別任務隔離

#### 📝 摘要系統 (Summary)

- **自動摘要** - 定時生成群聊對話摘要
- **LLM 驅動** - AI 提取關鍵資訊和話題脈絡
- **歷史回溯** - 支援查詢和管理歷史摘要記錄

#### 🧬 進化系統 (Evolution)

- **詞彙學習** - 自動學習群聊中的新詞彙和梗
- **語義理解** - 記錄詞彙含義、使用場景和語氣
- **自然融入** - 在合適場景自然使用學習到的群聊天用語

#### 🎭 梗系統 (Meme)

- **梗提取** - 從群聊中自動提取流行語和梗
- **向量儲存** - 語義向量表示，支援相似度檢索
- **熱度追蹤** - 追蹤高頻詞彙的使用頻率和流行度

### 🧩 組件服務

| 組件 | 說明 | 可選後端 |
|:---|:---|:---|
| **LLM 對話** | AI 對話推理 | Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter |
| **向量嵌入** | 文字和圖像的語義向量化 | ByteDance Embedding / SiliconFlow |
| **網路搜尋** | AI Agent 可調用的即時搜尋 | Exa / MiniMax Search |
| **視覺分析** | 圖片內容理解和描述 | MiniMax Vision |
| **瀏覽器** | 網頁截圖和 Markdown 抓取 | Playwright |

### 🔌 內建插件

| 插件 | 類型 | 說明 |
|:---|:---|:---|
| **speech** | AgentExtension | 語音合成插件，使用 MiniMax TTS 將文字轉為語音發送 |
| **lolisuki** | RouteExtension | 二次元圖片插件，從 lolisuki.cn 取得並發送圖片 |
| **net-ease-music** | PassiveExtension | 網易雲音樂插件，搜尋音樂並發送音樂卡片 |
| **qq-face** | PassiveExtension | QQ 表情插件，語義匹配發送合適的表情 |
| **seeddream** | RouteExtension | AI 圖片生成插件，支援文生圖和圖生圖 |
| **rollpig** | AgentExtension | 抽小豬遊戲插件，收集養成類互動遊戲 |
| **animal** | AgentExtension | 虛擬寵物養成插件，角色扮演與好感度系統 |

### 💬 對話增強

- **上下文理解** - 引用歷史對話和長期記憶
- **多 Persona** - 支援多 Bot 角色設定
- **情緒共鳴** - 感知群體氛圍，調整參與方式
- **多 LLM 支援** - 6 種 LLM 提供商，可按 Lite/Flash/Pro 分檔配置
- **工具調用** - AI Agent 可主動使用搜尋、視覺、瀏覽器等工具

## 🏁 快速開始

### 📋 前置需求

- **Node.js 18+**（npm 安裝方式）
- **JDK 17+**（原始碼構建方式）
- **NapCat** - QQ 接入服務
- **LLM API Key** - 至少一個 LLM 提供商的 API Key

### 方式一：npm 安裝（推薦）

```bash
npm create @spcookie/erii
cd erii
erii setup     # 互動式設定精靈
erii server    # 啟動服務
```

或一鍵安裝：

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.sh | bash

# Windows (PowerShell)
irm https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.ps1 | iex
```

`erii server` 支援子命令：`start`（預設）、`stop`、`status`、`restart`、`logs`。

### 方式二：Docker 部署

**啟動 erii 主服務**

```bash
docker-compose up -d
```

**啟動 NapCat QQ 接入服務**

```bash
cd docker/napcat-docker && docker-compose up -d
```

**啟動 Playwright 瀏覽器服務**

```bash
cd docker/playwright-docker && docker-compose up -d
```

### 🖥️ CLI 設定工具

erii 提供互動式 CLI 工具，基於 Go + Bubble Tea 構建：

```bash
go install ./erii-cli
```

| 命令 | 說明 |
|:---|:---|
| `erii config` | 互動式 TUI 設定編輯器（env / app / plugin / souls / rules） |
| `erii config app get/set <key> [value]` | 讀寫 `application.conf` |
| `erii config env get/set <key> [value]` | 讀寫 `.env.local` |
| `erii config plugin get/set <plugin> <key> [value]` | 讀寫插件 JSON 設定 |
| `erii setup` | 設定精靈（LLM、工具、連線、群組） |
| `erii manage` | 資料管理 TUI（facts、畫像、memes、詞彙、摘要） |
| `erii stats` | 機器人/群組狀態統計 TUI |
| `erii refresh` | 刷新後端設定快取 |
| `erii reload` | 重新載入插件設定和元資料 |

CLI 透過記憶體映射檔案（mmap + MsgPack）自動發現伺服器端連線資訊，無需手動設定。

## 🛠️ 技術堆疊

### 🖥️ 後端框架

- **語言:** Kotlin 2.2+
- **Web 框架:** Ktor 3.3+
- **建置工具:** Gradle 9.2+
- **資料庫:** H2 (嵌入式)
- **ORM:** Exposed + Migration
- **相依性注入:** Koin 4.1+
- **工作調度:** JobRunr 8.3+
- **AI 框架:** Koog Agents 0.7+

### 🤖 機器人整合

- **機器人框架:** Mirai + Overflow
- **接入服務:** NapCat (WebSocket)
- **LLM 提供商:** Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter

### 🔌 擴展架構

- **插件框架:** PF4J 3.15+
- **SPI 介面:** erii-spi 模組定義 4 種擴展點
- **插件構建:** erii-plugin-gradle 約定插件

### 🖥️ CLI

- **語言:** Go 1.21+
- **TUI 框架:** Bubble Tea
- **構建:** Mage

### 📦 分發

- **套件管理:** npm (monorepo workspaces)
- **執行時:** Node.js CLI 啟動器
- **Git LFS:** 二進制構件版本管理

## 📦 建置與執行

| 命令 | 說明 |
|:---|:---|
| `./gradlew compileKotlin` | 編譯 Kotlin 程式碼 |
| `./gradlew build` | 建置整個專案 |
| `./gradlew run` | 啟動開發伺服器 |
| `./gradlew buildFatJar` | 建置包含所有相依性的可執行 JAR |
| `./gradlew buildImage` | 建置 Docker 映像檔 |
| `./gradlew test` | 執行所有測試 |
| `./gradlew :erii-core:test --tests "com.example.MyTest"` | 執行單個測試 |
| `cd erii-plugins && ./gradlew buildAllPlugins` | 建置全部插件 |
| `cd erii-plugins && ./gradlew :<name>:build` | 建置單個插件 |
| `cd erii-cli && go build .` | 建置 CLI 工具 |
| `cd erii-cli && mage All` | 交叉編譯 CLI (所有平台) |

## 📂 專案結構

```
erii/
├── erii-common/                 # 📦 公共模組
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 非同步/同步事件匯流排
│       ├── ChatToolSet.kt       # 聊天工具集
│       └── data/                # 資料模型 (History, Emotional, Resource)
├── erii-core/                   # 🧠 核心模組
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 應用程式進入點
│       ├── config/              # 設定和 DI (IOC, LLMFactory, DatabaseConfig)
│       ├── core/
│       │   ├── agent/           # AI Agent (BotAgent, Prompts, Tools)
│       │   ├── route/           # 路由系統 (RoutingAgent, RouteCallEvent)
│       │   ├── state/           # 核心 AI 狀態系統
│       │   │   ├── emotion/     # 情緒系統 (PAD 模型)
│       │   │   ├── memory/      # 記憶系統 (多維度向量檢索)
│       │   │   ├── flow/        # 心流系統 (0-100 投入度)
│       │   │   ├── volition/    # 主動行為系統 (衝動值/疲勞)
│       │   │   ├── summary/     # 摘要系統 (定時對話摘要)
│       │   │   ├── evolution/   # 進化系統 (詞彙學習)
│       │   │   └── meme/        # 梗系統 (流行語追蹤)
│       │   ├── rule/            # 規則引擎 (Markdown 檔案規則)
│       │   ├── cron/            # 定時任務 (時間輪 + JobRunr)
│       │   ├── bot/             # Bot 角色管理
│       │   ├── message/         # 訊息處理 (history, resource)
│       │   ├── plugin/          # 插件載入和註冊
│       │   └── component/       # 組件服務
│       │       ├── llm/         # LLM 客戶端 (6 個提供商)
│       │       ├── embedding/   # 向量嵌入 (ByteDance/SiliconFlow)
│       │       ├── search/      # 搜尋服務 (Exa/MiniMax)
│       │       ├── vision/      # 視覺分析 (MiniMax Vision)
│       │       ├── browser/     # 瀏覽器 (Playwright)
│       │       └── storage/     # 儲存 (Vector/Object)
│       ├── routing/             # HTTP API 路由 (狀態管理/設定)
│       └── cli/                 # IPC 通訊 (mmap + MsgPack)
├── erii-spi/                    # 🔌 SPI 介面模組
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # 4 種擴展介面 + PluginContext
│       ├── PluginDefinition.kt # 編譯時註解
│       └── processor/           # 註解處理器
├── erii-plugin-gradle/          # 🔧 插件構建工具
│   └── src/main/kotlin/uesugi/gradle/
│       └── EriiGradlePlugin.kt # 約定插件 (pluginZip, assemblePlugin)
├── erii-plugins/                # 🎨 插件 (Git Submodule)
│   ├── speech/                  # MiniMax TTS 語音合成
│   ├── lolisuki/                # 二次元圖片取得
│   ├── net-ease-music/          # 網易雲音樂搜尋與卡片發送
│   ├── qq-face/                 # QQ 表情語義匹配發送
│   ├── seeddream/               # AI 圖片生成
│   ├── rollpig/                 # 抽小豬收集遊戲
│   └── animal/                  # 虛擬寵物養成
├── erii-cli/                    # 🖥️ CLI 工具 (Go)
│   ├── cmd/                    # 6 個命令 (config, setup, manage, stats, refresh, reload)
│   └── internal/               # API 客戶端, IPC, TUI 組件
├── erii-distribution/           # 📦 分發 (Git Submodule)
│   └── packages/               # npm monorepo 套件 (core, deps, browser, config, cli, runtime, plugins)
└── build.gradle.kts             # 根建置設定
```

## 🗺️ 架構設計

erii 採用事件驅動架構，透過 `EventBus` 實現系統間解耦。AI Agent 基於 Koog 的 `GraphAIAgent` 框架，支援工具調用、事件處理和狀態管理。伺服器透過 mmap 記憶體映射檔案向 CLI 暴露連線資訊，實現零設定進程間通訊。

```
NapCat/Mirai (WebSocket)
       │
       ▼
GroupMessageEventListener (訊息監聽)
       │
       ▼
HistoryService (儲存歷史) + ResourceService (圖片/資源去重儲存)
       │
       ▼
RoutingAgent (LLM 意圖分類) / CmdExtension (命令匹配)
       │
       ▼
EventBus.postAsync (RouteCallEvent / ProactiveSpeakEvent)
       │
       ▼
BotAgent (GraphAIAgent 消費事件)
       │
       ├── Tool Calls (ChatToolSet: sendText, sendMeme, sendImage, ...)
       ├── Component Tools (WebSearch, ChatVision, BrowserScraper)
       └── State Updates (Emotion / Memory / Flow / Volition / Evolution / Meme / Summary)
       │
       ▼
Response → Group (透過 OneBot/Mirai)
```

### 插件擴展點

| 擴展類型 | 說明 | 匹配方式 |
|:---|:---|:---|
| **AgentExtension** | 通用 Agent 擴展，始終參與 | 組合使用 |
| **RouteExtension** | LLM 路由擴展 | RoutingAgent 意圖分類 |
| **CmdExtension** | 命令擴展 | `/xxx` 命令前綴匹配，支援別名 |
| **PassiveExtension** | 被動擴展 | 後台工作/事件監聽 |

### 插件開發

插件基於 PF4J 框架，使用 `uesugi.erii-plugin` Gradle 約定插件構建。

**1. 建立插件專案**

在 `erii-plugins/` 下建立新目錄，並在 `settings.gradle.kts` 中註冊：

```kotlin
// erii-plugins/settings.gradle.kts
include("my-plugin")
```

**2. 設定構建**

```kotlin
// erii-plugins/my-plugin/build.gradle.kts
plugins {
    id("uesugi.erii-plugin")
}

version = "0.0.1"
```

**3. 實作插件**

使用 `@PluginDefinition` 註解，繼承 `AgentPlugin`，實作對應的擴展介面：

```kotlin
@PluginDefinition(
    pluginId = "my-plugin",
    version = "0.0.1",
    description = "我的插件"
)
class MyPlugin : AgentPlugin()

class MyExtension : AgentExtension<MyPlugin> {
    override fun onLoad(context: PluginContext) {
        // 透過 PluginContext 存取 LLM、儲存、排程器等能力
    }
}
```

**4. 構建與安裝**

```bash
cd erii-plugins && ./gradlew :my-plugin:build
```

產出 zip 包位於 `build/plugin/`，放入 erii 的 `plugins/` 目錄即可載入。

## 📄 授權

本專案基於 [MIT](LICENSE) 授權條款開源。

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
