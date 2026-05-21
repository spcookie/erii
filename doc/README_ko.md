<div align="center">

![erii](https://socialify.git.ci/spcookie/erii/image?custom_description=%EA%B0%90%EC%A0%95%2C+%EA%B8%B0%EC%96%B5%2C+%ED%9D%90%EB%A6%84+%EA%B8%B0%EB%B0%98+%EC%82%AC%EA%B3%A0%2C+%EB%8A%A5%EB%8F%99%EC%A0%81+%ED%96%89%EB%8F%99%2C+%EC%9E%91%EC%97%85+%EC%95%8C%EB%A6%BC+%EB%B0%8F+%EC%9E%90%EA%B8%B0+%EA%B7%9C%EC%B9%99+%EA%B4%80%EB%A6%AC%EB%A5%BC+%EA%B0%96%EC%B6%98+AI+%EA%B7%B8%EB%A3%B9+%EC%B1%84%ED%8C%85+%EB%B4%87.&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2Ferii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

한국어 | [English](./README_en.md) | [中文](../README.md) | [繁體中文](./README_zh-TW.md)

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

[🚀 기능](#-기능) • [🏁 시작하기](#-시작하기) • [🛠️ 기술 스택](#-기술-스택) • [📦 빌드 및 실행](#-빌드-및-실행) • [📂 프로젝트 구조](#-프로젝트-구조) • [🗺️ 아키텍처](#-아키텍처)

</div>

---

## 📖 개요

erii는 실제 사람처럼 그룹 채팅에 참여하는 AI 봇입니다. 감정, 기억, 성격을 가지고 있어 대화에 따라 기분이 변하고 말투도 달라집니다. erii는 호출을 기다리기만 하지 않고, 분위기를 능동적으로 파악합니다: 할 말이 있을 때 끼어들고, 조용히 있어야 할 때는 머물며, 그룹이 조용해지면 스스로 대화를 시작합니다. 채팅이 길어질수록 사람들의 취향을 기억하고, 그룹 특유의 농담을 익히며, 점차 그룹의 자연스러운 일부가 됩니다. 또한 erii는 예약 알림, 자동 대화 요약, 규칙 파일을 통한 행동 제약, CLI 도구를 통한 설정 및 데이터 관리를 지원합니다.

**✨ 핵심 기능:**

- 🎭 **감정이 있어요**: 대화에 따라 기분이 변합니다 — 기쁠 땐 수다스럽고, 가라앉을 땐 조용해집니다
- 🧠 **기억해요**: 사람들의 취향과 했던 말을 기억합니다; 오래 대화할수록 더 잘 알게 됩니다
- 🌊 **눈치가 있어요**: 끼어들기 전에 분위기를 살핍니다 — 필요할 때 말하고, 아닐 땐 조용히 있습니다
- ⚡ **먼저 다가가요**: 명령만 기다리지 않고, 관심 있는 주제에 스스로 참여하며, 조용한 그룹에 활기를 불어넣습니다
- 📜 **규칙을 따라요**: Markdown 규칙 파일로 행동을 관리하며, 그룹마다 다른 규칙을 적용할 수 있습니다
- 📝 **요약해줘요**: 대화 요약을 자동 생성하여 놓친 내용을 쉽게 따라잡을 수 있습니다

## 🚀 기능

### 🧠 핵심 시스템

#### 🎭 감정 시스템 (Emotion)

- **PAD 3차원 감정 모델** - 쾌감(Pleasure), 각성(Arousal), 우세(Dominance)
- **동적 감정 조절** - 대화 내용에 기반한 실시간 감정 상태 조정
- **감정 표현** - 답장 어조, 이모지 사용, 답장 지연에 영향
- **장기 기분** - 기본 기분 톤 유지, 전체 행동 경향에 영향

#### 💾 기억 시스템 (Memory)

- **다차원 기억** - 사용자 프로필, 선호도 설정, 사실 지식, 대화 요약
- **3단계 범위** - 전역 기억, 그룹 기억, 구성원-그룹 기억
- **의미 검색** - 벡터 유사도 기반의 지능형 기억 호출
- **LLM 구동** - AI가 자동으로 장기 기억을 추출하고 관리

#### 🌊 흐름 시스템 (Flow)

- **참여도 정량화** - 0-100 흐름 값, 3단계 상태 머신
- **주제 매칭** - 관심 있는 주제를 만나면 빠르게 흐름 상태 진입
- **집중 메커니즘** - 높은 흐름 시 대화 목표에 집중, 관련 없는 방해 필터링
- **과열 보호** - 피로감 시뮬레이션, 과도한 출력 방지

#### ⚡ 의지 시스템 (Volition)

- **충동 계산** - 감정, 흐름, 키워드 기반 능동적 발언 욕구 계산
- **3가지 끼어들기 모드** - 끼어들기(Interrupt), 분위기 깨기(Icebreak), 일상(Routine)
- **상황 파악** - 진지한 주제와 그룹 감정 감지, 부적절한 발언 방지
- **피로 억제** - 능동적 발언 후 피로 축적, 스팸 방지

#### 📜 자기 규칙 관리 (Rule Management)

- **파일 시스템 저장** - 규칙을 Markdown 파일로 저장, 핫 리로드 지원
- **다단계 범위** - 전역 규칙 > 봇 규칙 > 그룹 규칙
- **AI 제어 가능** - Agent가 ToolSet을 통해 규칙 생성/삭제 가능
- **보안 보호** - 경로 탐색 보호 및 파일명 검증

#### ⏰ 크론 스케줄링 시스템 (Cron)

- **시간 휠 스케줄링** - 메모리 시간 휠 기반의 효율적인 작업 스케줄링
- **알림 작업** - 일회성 및 반복 알림 지원, Cron 표현식으로 주기 정의
- **트리거 작업** - 라우팅 트리거 및 명령어 트리거 모드 지원
- **자연스러운 푸시** - AI가 자연스러운 알림 어조 생성, 기계적 표현 방지
- **범위 관리** - Bot/Group 수준 작업 격리

#### 📝 요약 시스템 (Summary)

- **자동 요약** - 그룹 채팅 대화 요약을 주기적으로 생성
- **LLM 구동** - AI가 핵심 정보와 주제 흐름을 추출
- **기록 검토** - 과거 요약 기록 조회 및 관리 지원

#### 🧬 진화 시스템 (Evolution)

- **어휘 학습** - 그룹 채팅에서 새로운 단어와 밈 자동 학습
- **의미 이해** - 단어 의미, 사용 상황, 어조 기록
- **자연스러운 통합** - 적절한 상황에서 학습된 그룹 채팅 표현 자연스럽게 사용

#### 🎭 밈 시스템 (Meme)

- **밈 추출** - 그룹 채팅에서 유행어와 밈 자동 추출
- **벡터 저장** - 의미 벡터 표현, 유사도 검색 지원
- **인기 추적** - 고빈도 단어의 사용 빈도와 인기도 추적

### 🧩 컴포넌트 서비스

| 컴포넌트 | 설명 | 백엔드 옵션 |
|:---|:---|:---|
| **LLM 채팅** | AI 대화 추론 | Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter |
| **벡터 임베딩** | 텍스트 및 이미지 의미 벡터화 | ByteDance Embedding / SiliconFlow |
| **웹 검색** | AI Agent가 호출 가능한 실시간 검색 | Exa / MiniMax Search |
| **비전** | 이미지 내용 이해 및 설명 | MiniMax Vision |
| **브라우저** | 웹페이지 스크린샷 및 Markdown 스크래핑 | Playwright |

### 🔌 내장 플러그인

| 플러그인 | 유형 | 설명 |
|:---|:---|:---|
| **speech** | AgentExtension | MiniMax TTS 음성 합성 플러그인 |
| **lolisuki** | RouteExtension | 애니메이션 이미지 플러그인 (lolisuki.cn) |
| **net-ease-music** | PassiveExtension | NetEase 음악 플러그인, 음악 카드 전송 |
| **qq-face** | PassiveExtension | QQ 이모지 의미 매칭 플러그인 |
| **seeddream** | RouteExtension | AI 이미지 생성 플러그인 (텍스트→이미지, 이미지→이미지) |
| **rollpig** | AgentExtension | 돼지 수집 게임 플러그인 |
| **animal** | AgentExtension | 가상 반려동물 육성 플러그인, 역할극과 호감도 시스템 |

### 💬 대화 향상

- **문맥 이해** - 과거 대화와 장기 기억 참조
- **다중 페르소나** - 다중 봇 역할 구성 지원
- **감정 공명** - 그룹 분위기 인식, 참여 방식 조정
- **다중 LLM 지원** - 6개 LLM 제공자, Lite/Flash/Pro 등급별 구성 가능
- **도구 호출** - AI Agent가 검색, 비전, 브라우저 등 도구를 능동적으로 사용

## 🏁 시작하기

### 📋 전제 조건

- **Node.js 18+** (npm 설치 방식)
- **JDK 17+** (소스 빌드 방식)
- **NapCat** — QQ 연동 서비스
- **LLM API Key** — 최소 하나의 LLM 제공자 API 키

### 방법 1: npm 설치 (권장)

```bash
npm create @spcookie/erii
cd erii
erii setup     # 대화형 설정 마법사
erii server    # 서버 시작
```

또는 원라인 설치:

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.sh | bash

# Windows (PowerShell)
irm https://raw.githubusercontent.com/spcookie/erii/main/erii-distribution/scripts/install.ps1 | iex
```

`erii server`는 하위 명령어를 지원합니다: `start` (기본값), `stop`, `status`, `restart`, `logs`.

### 방법 2: Docker 배포

**erii 메인 서비스 시작**

```bash
docker-compose up -d
```

**NapCat QQ 연동 서비스 시작**

```bash
cd docker/napcat-docker && docker-compose up -d
```

**Playwright 브라우저 서비스 시작**

```bash
cd docker/playwright-docker && docker-compose up -d
```

### 🖥️ CLI 구성 도구

erii는 Go + Bubble Tea로 구축된 대화형 CLI 도구를 제공합니다:

```bash
go install ./erii-cli
```

| 명령어 | 설명 |
|:---|:---|
| `erii config` | 대화형 TUI 설정 편집기 (env / app / plugin / souls / rules) |
| `erii config app get/set <key> [value]` | `application.conf` 읽기/쓰기 |
| `erii config env get/set <key> [value]` | `.env.local` 읽기/쓰기 |
| `erii config plugin get/set <plugin> <key> [value]` | 플러그인 JSON 설정 읽기/쓰기 |
| `erii setup` | 설정 마법사 (LLM, 도구, 연결, 그룹) |
| `erii manage` | 데이터 관리 TUI (facts, 프로필, memes, 어휘, 요약) |
| `erii stats` | 봇/그룹 통계 TUI |
| `erii refresh` | 백엔드 설정 캐시 새로고침 |
| `erii reload` | 플러그인 설정 및 메타데이터 다시 로드 |

CLI는 메모리 매핑 파일(mmap + MsgPack)을 통해 서버 연결 정보를 자동으로 발견하므로 수동 설정이 필요하지 않습니다.

## 🛠️ 기술 스택

### 🖥️ 백엔드 프레임워크

- **언어:** Kotlin 2.2+
- **웹 프레임워크:** Ktor 3.3+
- **빌드 도구:** Gradle 9.2+
- **데이터베이스:** H2 (임베디드)
- **ORM:** Exposed + Migration
- **의존성 주입:** Koin 4.1+
- **작업 스케줄링:** JobRunr 8.3+
- **AI 프레임워크:** Koog Agents 0.7+

### 🤖 봇 통합

- **봇 프레임워크:** Mirai + Overflow
- **연동 서비스:** NapCat (WebSocket)
- **LLM 제공자:** Google Gemini / DeepSeek / MiniMax / OpenAI / Anthropic / OpenRouter

### 🔌 확장 아키텍처

- **플러그인 프레임워크:** PF4J 3.15+
- **SPI 인터페이스:** erii-spi 모듈이 4가지 확장점 유형 정의
- **플러그인 빌드:** erii-plugin-gradle 컨벤션 플러그인

### 🖥️ CLI

- **언어:** Go 1.21+
- **TUI 프레임워크:** Bubble Tea
- **빌드:** Mage

### 📦 배포

- **패키지 관리자:** npm (모노레포 워크스페이스)
- **런타임:** Node.js CLI 런처
- **Git LFS:** 바이너리 아티팩트 버전 관리

## 📦 빌드 및 실행

| 명령어 | 설명 |
|:---|:---|
| `./gradlew compileKotlin` | Kotlin 코드 컴파일 |
| `./gradlew build` | 전체 프로젝트 빌드 |
| `./gradlew run` | 개발 서버 시작 |
| `./gradlew buildFatJar` | 모든 의존성을 포함한 Fat JAR 빌드 |
| `./gradlew buildImage` | Docker 이미지 빌드 |
| `./gradlew test` | 모든 테스트 실행 |
| `./gradlew :erii-core:test --tests "com.example.MyTest"` | 단일 테스트 실행 |
| `cd erii-plugins && ./gradlew buildAllPlugins` | 모든 플러그인 빌드 |
| `cd erii-plugins && ./gradlew :<name>:build` | 단일 플러그인 빌드 |
| `cd erii-cli && go build .` | CLI 도구 빌드 |
| `cd erii-cli && mage All` | CLI 크로스 컴파일 (모든 플랫폼) |

## 📂 프로젝트 구조

```
erii/
├── erii-common/                 # 📦 공통 모듈
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 비동기/동기 이벤트 버스
│       ├── ChatToolSet.kt       # 채팅 도구 세트
│       └── data/                # 데이터 모델 (History, Emotional, Resource)
├── erii-core/                   # 🧠 코어 모듈
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 애플리케이션 진입점
│       ├── config/              # 구성 및 DI (IOC, LLMFactory, DatabaseConfig)
│       ├── core/
│       │   ├── agent/           # AI Agent (BotAgent, Prompts, Tools)
│       │   ├── route/           # 라우팅 시스템 (RoutingAgent, RouteCallEvent)
│       │   ├── state/           # 핵심 AI 상태 시스템
│       │   │   ├── emotion/     # 감정 시스템 (PAD 모델)
│       │   │   ├── memory/      # 기억 시스템 (다차원 벡터 검색)
│       │   │   ├── flow/        # 흐름 시스템 (0-100 참여도)
│       │   │   ├── volition/    # 의지 시스템 (충동/피로)
│       │   │   ├── summary/     # 요약 시스템 (예약 대화 요약)
│       │   │   ├── evolution/   # 진화 시스템 (어휘 학습)
│       │   │   └── meme/        # 밈 시스템 (유행어 추적)
│       │   ├── rule/            # 규칙 엔진 (Markdown 파일 규칙)
│       │   ├── cron/            # 크론 스케줄링 (시간 휠 + JobRunr)
│       │   ├── bot/             # 봇 역할 관리
│       │   ├── message/         # 메시지 처리 (history, resource)
│       │   ├── plugin/          # 플러그인 로딩 및 등록
│       │   └── component/       # 컴포넌트 서비스
│       │       ├── llm/         # LLM 클라이언트 (6개 제공자)
│       │       ├── embedding/   # 벡터 임베딩 (ByteDance/SiliconFlow)
│       │       ├── search/      # 검색 서비스 (Exa/MiniMax)
│       │       ├── vision/      # 비전 분석 (MiniMax Vision)
│       │       ├── browser/     # 브라우저 (Playwright)
│       │       └── storage/     # 저장소 (Vector/Object)
│       ├── routing/             # HTTP API 라우트 (상태 관리/설정)
│       └── cli/                 # IPC 통신 (mmap + MsgPack)
├── erii-spi/                    # 🔌 SPI 인터페이스 모듈
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # 4가지 확장 인터페이스 + PluginContext
│       ├── PluginDefinition.kt # 컴파일 타임 어노테이션
│       └── processor/           # 어노테이션 프로세서
├── erii-plugin-gradle/          # 🔧 플러그인 빌드 도구
│   └── src/main/kotlin/uesugi/gradle/
│       └── EriiGradlePlugin.kt # 컨벤션 플러그인 (pluginZip, assemblePlugin)
├── erii-plugins/                # 🎨 플러그인 (Git Submodule)
│   ├── speech/                  # MiniMax TTS 음성 합성
│   ├── lolisuki/                # 애니메이션 이미지 가져오기
│   ├── net-ease-music/          # NetEase 음악 검색 및 카드 전송
│   ├── qq-face/                 # QQ 이모지 의미 매칭
│   ├── seeddream/               # AI 이미지 생성
│   ├── rollpig/                 # 돼지 수집 게임
│   └── animal/                  # 가상 반려동물 육성
├── erii-cli/                    # 🖥️ CLI 도구 (Go)
│   ├── cmd/                    # 6개 명령어 (config, setup, manage, stats, refresh, reload)
│   └── internal/               # API 클라이언트, IPC, TUI 컴포넌트
├── erii-distribution/           # 📦 배포 (Git Submodule)
│   └── packages/               # npm 모노레포 패키지 (core, deps, browser, config, cli, runtime, plugins)
└── build.gradle.kts             # 루트 빌드 구성
```

## 🗺️ 아키텍처

erii는 `EventBus`를 통해 시스템 간 결합을 분리하는 이벤트 중심 아키텍처를 채택했습니다. AI Agent는 Koog의 `GraphAIAgent` 프레임워크를 기반으로 하며, 도구 호출, 이벤트 처리, 상태 관리를 지원합니다. 서버는 mmap 메모리 매핑 파일을 통해 CLI에 연결 정보를 노출하여 제로 구성 프로세스 간 통신을 구현합니다.

```
NapCat/Mirai (WebSocket)
       │
       ▼
GroupMessageEventListener (메시지 수신)
       │
       ▼
HistoryService (기록 저장) + ResourceService (이미지/리소스 중복 제거 저장)
       │
       ▼
RoutingAgent (LLM 의도 분류) / CmdExtension (명령어 매칭)
       │
       ▼
EventBus.postAsync (RouteCallEvent / ProactiveSpeakEvent)
       │
       ▼
BotAgent (GraphAIAgent가 이벤트 소비)
       │
       ├── Tool Calls (ChatToolSet: sendText, sendMeme, sendImage, ...)
       ├── Component Tools (WebSearch, ChatVision, BrowserScraper)
       └── State Updates (Emotion / Memory / Flow / Volition / Evolution / Meme / Summary)
       │
       ▼
Response → Group (OneBot/Mirai를 통해)
```

### 플러그인 확장점

| 확장 유형 | 설명 | 매칭 방식 |
|:---|:---|:---|
| **AgentExtension** | 범용 Agent 확장, 항상 활성 | 조합 사용 |
| **RouteExtension** | LLM 라우팅 확장 | RoutingAgent 의도 분류 |
| **CmdExtension** | 명령어 확장 | `/xxx` 명령어 접두사 매칭, 별칭 지원 |
| **PassiveExtension** | 수동 확장 | 백그라운드 작업 / 이벤트 리스너 |

### 플러그인 개발

플러그인은 PF4J 프레임워크를 기반으로 하며 `uesugi.erii-plugin` Gradle 컨벤션 플러그인을 사용하여 빌드합니다.

**1. 플러그인 프로젝트 생성**

`erii-plugins/` 아래에 새 디렉토리를 만들고 `settings.gradle.kts`에 등록합니다:

```kotlin
// erii-plugins/settings.gradle.kts
include("my-plugin")
```

**2. 빌드 구성**

```kotlin
// erii-plugins/my-plugin/build.gradle.kts
plugins {
    id("uesugi.erii-plugin")
}

version = "0.0.1"
```

**3. 플러그인 구현**

`@PluginDefinition` 어노테이션을 사용하고, `AgentPlugin`을 상속하며, 해당 확장 인터페이스를 구현합니다:

```kotlin
@PluginDefinition(
    pluginId = "my-plugin",
    version = "0.0.1",
    description = "내 플러그인"
)
class MyPlugin : AgentPlugin()

class MyExtension : AgentExtension<MyPlugin> {
    override fun onLoad(context: PluginContext) {
        // PluginContext를 통해 LLM, 저장소, 스케줄러 등에 접근
    }
}
```

**4. 빌드 및 설치**

```bash
cd erii-plugins && ./gradlew :my-plugin:build
```

결과물 zip 파일은 `build/plugin/`에 생성되며, erii의 `plugins/` 디렉토리에 넣으면 로드됩니다.

## 📄 라이선스

이 프로젝트는 [MIT](LICENSE) 라이선스로 오픈소스입니다.

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
