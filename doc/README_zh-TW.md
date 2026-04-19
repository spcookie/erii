<div align="center">

![Erii](https://socialify.git.ci/spcookie/Erii/image?custom_description=An+AI+group+chat+robot+with+emotions%2C+memory%2C+flow%2C+and+proactive+behavior&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2FErii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

[繁體中文](./README_zh-TW.md) | [English](../README.md) | [中文](./README_zh.md) | [한국어](./README_ko.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![JobRunr](https://img.shields.io/badge/JobRunr-6.2+-green?style=for-the-badge)](https://jobrunr.io/)

[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![Koin](https://img.shields.io/badge/Koin-3.5+-F3692A?style=for-the-badge)](https://insert-koin.io/)
[![Exposed](https://img.shields.io/badge/Exposed-ORM-F3692A?style=for-the-badge)](https://github.com/JetBrains/Exposed)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/Erii)

[🚀 功能特性](#-功能特性) • [🏁 快速開始](#-快速開始) • [📦 建置與執行](#-建置與執行) • [🛠️ 技術堆疊](#-技術堆疊)

</div>

---

## 📖 概述

Erii 是一個基於多維心理模型的 AI 群聊機器人系統。不同於傳統的問答機器人，Erii 擁有**情緒系統**、**長期記憶**、**對話心流**和
**主動行為**，能夠像真人一樣參與群聊對話，主動插話、破冰，並根據情緒狀態調整說話風格。

**✨ 核心特性：**

- 🎭 **情緒系統**：基於 PAD 模型，模擬真實情感反應
- 🧠 **長期記憶**：多維度記錄用戶畫像、偏好和群聊事實
- 🌊 **心流機制**：根據話題投入程度動態調整參與深度
- ⚡ **主動行為**：基於衝動值進行插話、破冰和日常互動

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

#### 🧬 進化系統 (Evolution)

- **詞彙學習** - 自動學習群聊中的新詞彙和梗
- **語義理解** - 記錄詞彙含義、使用場景和語氣
- **自然融入** - 在合適場景自然使用學習到的群聊天用語

#### 🎭 梗系統 (Meme)

- **梗提取** - 從群聊中自動提取流行語和梗
- **向量儲存** - 語義向量表示，支援相似度檢索
- **熱度追蹤** - 追蹤高頻詞彙的使用頻率和流行度

### 🔌 內建插件

| 插件                 | 類型               | 說明                              |
|:-------------------|:-----------------|:--------------------------------|
| **speech**         | AgentExtension   | 語音合成插件，使用 MiniMax TTS 將文字轉為語音發送 |
| **lolisuki**       | RouteExtension   | 二次元圖片插件，從 lolisuki.cn 取得並發送圖片   |
| **net-ease-music** | PassiveExtension | 網易雲音樂插件，搜尋音樂並發送音樂卡片             |
| **qa**             | RouteExtension   | AI 問答插件，透過網路搜尋直接回答問題            |
| **qq-face**        | PassiveExtension | QQ 表情插件，語義匹配發送合適的表情             |
| **reminder**       | AgentExtension   | 定時提醒插件，支援延遲訊息和定時任務              |
| **seeddream**      | RouteExtension   | AI 圖片生成插件，支援文生圖和圖生圖             |

### 💬 對話增強

- **上下文理解** - 引用歷史對話和長期記憶
- **多 Persona** - 支援多 Bot 角色設定
- **情緒共鳴** - 感知群體氛圍，調整參與方式

## 🏁 快速開始

### 📋 前置需求

在開始之前，確保已安裝以下環境：

- **JDK 17+**
- **Gradle 8.0+**
- **NapCat** - QQ 接入服務
- **LLM API Key** - Google Gemini / DeepSeek / MiniMax API

### ⚙️ 安裝與設定

1. **複製倉庫**
   ```bash
   git clone https://github.com/spcookie/Erii.git
   cd Erii
   ```

2. **設定環境變數**
   複製環境變數模板並編輯：
   ```bash
   cp .env.local.template .env.local
   nano .env.local
   ```
   主要設定項目：
    - `CHOICE_MODEL`: 選擇 LLM 提供商（GOOGLE / DEEP_SEEK / MINIMAX）
    - 對應的 API Key 設定
    - `NAPCAT_TOKEN`: NapCat 認證令牌
    - `ENABLE_GROUPS`: 啟用的群組列表

3. **設定機器人**
   複製設定檔模板並編輯：
   ```bash
   cp application.conf.template application.conf
   nano application.conf
   ```
   主要設定項目：
    - `llm.choice-model`: 選擇 LLM 提供商（GOOGLE / DEEP_SEEK / MINIMAX）
    - `onebot.bots`: QQ 機器人連線資訊
    - `groups.enable-groups`: 啟用的群組列表

4. **啟動開發伺服器**
   ```bash
   ./gradlew run
   ```

### 🐳 Docker 部署

**啟動 Erii 主服務**

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

## 🛠️ 技術堆疊

### 🖥️ 後端框架

- **語言:** Kotlin 1.9+
- **Web 框架:** Ktor 2.3+
- **建置工具:** Gradle 8.0+
- **資料庫:** H2 (嵌入式)
- **ORM:** Exposed
- **相依性注入:** Koin
- **工作調度:** JobRunr

### 🤖 機器人整合

- **機器人框架:** Mirai + Overflow
- **接入服務:** NapCat (WebSocket)
- **LLM:** Google Gemini API / OpenAI Compatible API

### 🔌 擴展架構

- **插件框架:** PF4J
- **SPI 介面:** erii-spi 模組定義擴展點

## 📦 建置與執行

| 命令                        | 說明                |
|:--------------------------|:------------------|
| `./gradlew compileKotlin` | 編譯 Kotlin 程式碼     |
| `./gradlew build`         | 建置整個專案            |
| `./gradlew run`           | 啟動開發伺服器           |
| `./gradlew buildFatJar`   | 建置包含所有相依性的可執行 JAR |
| `./gradlew buildImage`    | 建置 Docker 映像檔     |
| `./gradlew test`          | 執行所有測試            |

## 📂 專案結構

```
Erii/
├── erii-common/                 # 📦 公共模組
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 非同步/同步事件匯流排
│       ├── BotRole.kt           # Bot 角色定義
│       ├── ChatToolSet.kt       # 聊天工具集
│       └── ...
├── erii-core/                   # 🧠 核心模組
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 應用程式進入點
│       ├── config/              # 設定和 DI
│       │   ├── IOC.kt          # Koin 相依性注入設定
│       │   ├── LLMFactory.kt   # LLM 工廠
│       │   └── ...
│       ├── core/
│       │   ├── agent/          # AI Agent
│       │   │   └── BotAgent.kt  # 核心 AI Agent
│       │   ├── route/          # 路由系統
│       │   │   └── RoutingAgent.kt  # LLM 路由
│       │   ├── state/          # 核心 AI 狀態系統
│       │   │   ├── emotion/     # 情緒系統
│       │   │   ├── memory/      # 記憶系統
│       │   │   ├── flow/        # 心流系統
│       │   │   ├── volition/    # 主動行為系統
│       │   │   ├── evolution/   # 進化系統
│       │   │   └── meme/        # 梗系統
│       │   ├── bot/            # Bot 角色管理
│       │   │   └── BotRoleManager.kt
│       │   ├── message/        # 訊息處理
│       │   │   └── history/    # 歷史記錄服務
│       │   ├── plugin/         # 插件實作
│       │   ├── rule/           # 規則引擎
│       │   └── component/      # 元件
│       │       ├── browser/   # 瀏覽器（截圖/Markdown 抓取）
│       │       ├── embedding/  # 向量嵌入
│       │       ├── search/     # 搜尋服務
│       │       └── storage/    # 儲存（向量/物件）
│       └── server/             # HTTP API 路由
├── erii-spi/                    # 🔌 SPI 介面模組
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # Agent 擴展介面
│       ├── PluginDefinition.kt # 插件定義註解
│       └── processor/           # 註解處理器
├── erii-plugins/                # 🎨 插件模組
│   ├── speech/                  # MiniMax TTS 語音合成
│   ├── lolisuki/                # 二次元圖片取得
│   ├── net-ease-music/          # 網易雲音樂搜尋與卡片發送
│   ├── qa/                      # AI 網路搜尋問答
│   ├── qq-face/                 # QQ 表情語義匹配發送
│   ├── reminder/                # 定時提醒與延遲訊息
│   └── seeddream/               # AI 圖片生成
└── build.gradle.kts             # 根建置設定
```

## 🗺️ 架構設計

Erii 採用事件驅動架構，透過 `EventBus` 實作系統間解耦。

```
訊息接收 (NapCat/Mirai)
       │
       ▼
GroupMessageEventListener
       │
       ▼
HistoryService (儲存歷史)
       │
       ▼
RoutingAgent (LLM 意圖分類) / CmdRuleRegister (命令匹配)
       │
       ▼
RouteCallEvent (透過 EventBus.postAsync 分發)
       │
       ▼
BotAgent (消費事件，執行 AI Agent)
       │
       ▼
狀態更新 (情緒、記憶、心流、意志、進化、梗)
```

### 插件擴展點

| 擴展類型                 | 說明          | 匹配方式              |
|:---------------------|:------------|:------------------|
| **AgentExtension**   | 通用 Agent 擴展 | 組合使用              |
| **RouteExtension**   | LLM 路由擴展    | RoutingAgent 意圖分類 |
| **CmdExtension**     | 命令擴展        | `/xxx` 命令匹配       |
| **PassiveExtension** | 被動擴展        | 後台工作/事件監聽         |

## 📄 授權

本專案基於 [MIT](LICENSE) 授權條款開源。

## Repobeats

![Alt](https://repobeats.axiom.co/api/embed/341cfbaa0a0048c8c95abe32707d6760903d13e0.svg "Repobeats analytics image")

## Star History

<a href="https://star-history.com/?repos=spcookie%2FErii&type=date&logscale=&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=spcookie/Erii&type=date&theme=dark&logscale&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=spcookie/Erii&type=date&logscale&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=spcookie/Erii&type=date&logscale&legend=top-left" />
 </picture>
</a>
