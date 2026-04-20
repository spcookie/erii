<div align="center">

![Erii](https://socialify.git.ci/spcookie/Erii/image?custom_description=%EA%B0%90%EC%A0%95%2C+%EA%B8%B0%EC%96%B5%2C+%ED%9D%90%EB%A6%84+%EA%B8%B0%EB%B0%98+%EC%82%AC%EA%B3%A0%2C+%EB%8A%A5%EB%8F%99%EC%A0%81+%ED%96%89%EB%8F%99%2C+%EC%9E%91%EC%97%85+%EC%95%8C%EB%A6%BC+%EB%B0%8F+%EC%9E%90%EA%B8%B0+%EA%B7%9C%EC%B9%99+%EA%B4%80%EB%A6%AC%EB%A5%BC+%EA%B0%96%EC%B6%98+AI+%EA%B7%B8%EB%A3%B9+%EC%B1%84%ED%8C%85+%EB%B4%87.&custom_language=Kotlin&description=1&font=Source+Code+Pro&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fspcookie%2FErii%2F626e8833ae51f8ead0b2f41be237c4c2fb761577%2Fdoc%2Fassets%2FLOGO.svg&name=1&owner=1&pattern=Circuit+Board&pulls=1&stargazers=1&theme=Auto)

한국어 | [English](../README.md) | [中文](./README_zh.md) | [繁體中文](./README_zh-TW.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3+-087CFA?style=for-the-badge&logo=ktor&logoColor=white)](https://ktor.io/)
[![Mirai](https://img.shields.io/badge/Mirai-2.16+-00C4B3?style=for-the-badge)](https://github.com/mamoe/mirai)
[![JobRunr](https://img.shields.io/badge/JobRunr-6.2+-green?style=for-the-badge)](https://jobrunr.io/)

[![H2](https://img.shields.io/badge/H2-Database-0066CC?style=for-the-badge)](https://www.h2database.com/)
[![Koin](https://img.shields.io/badge/Koin-3.5+-F3692A?style=for-the-badge)](https://insert-koin.io/)
[![Exposed](https://img.shields.io/badge/Exposed-ORM-F3692A?style=for-the-badge)](https://github.com/JetBrains/Exposed)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/spcookie/Erii)

[🚀 기능](#-기능) • [🏁 시작하기](#-시작하기) • [📦 빌드 및 실행](#-빌드-및-실행) • [🛠️ 기술 스택](#-기술-스택)

</div>

---

## 📖 개요

Erii는 다차원 심리 모델에 기반한 AI 그룹 채팅 봇 시스템입니다. 전통적인 질문 답변 봇과 달리, Erii는 **감정 시스템**, **장기 기억**, **대화 흐름** 및 **주동적 행동**을 갖추고 있어,
진정한 사람처럼 그룹 채팅에 참여하고, 말을 끼워넣고, 얼어붙은 분위기를 깨뜨리며, 감정 상태에 따라 말투를 조정합니다.

**✨ 핵심 기능:**

- 🎭 **감정 시스템**: PAD 모델 기반, 실제 감정 반응 시뮬레이션
- 🧠 **장기 기억**: 사용자 프로필, 선호도, 그룹 채팅 사실의 다차원 기록
- 🌊 **흐름 메커니즘**: 주제 참여도에 따라 참여 깊이 동적 조정
- ⚡ **주동적 행동**: 충동 값 기반 말 끼워넣기, 얼음 깨기, 일상 상호작용

## 🚀 기능

### 🧠 핵심 시스템

#### 🎭 감정 시스템 (Emotion)

- **PAD 3차원 감정 모델** - 쾌활성(Pleasure), 각성도(Arousal), 지배성(Dominance)
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

- **충동 계산** - 감정, 흐름, 키워드 기반 주동적 발언 욕구 계산
- **3가지 끼워넣기 모드** - 끼워넣기(Interrupt), 얼음 깨기(Icebreak), 일상(Routine)
- **상황 파악** - 진지한 주제와 그룹 감정 감지, 부적절한 발언 방지
- **피로 억제** - 주동적 발언 후 피로 축적, 스팸 방지

#### 📜 자기 규칙 관리 (Rule Management)

- **파일 시스템 저장** - 규칙을 Markdown 파일로 저장, 핫 리로드 지원
- **다단계 범위** - 전역 규칙 > 봇 규칙 > 그룹 규칙
- **AI 제어 가능** - Agent가 ToolSet을 통해 규칙 생성/삭제 가능
- **보안 보호** - 경로 탐색 보호 및 파일명 검증

#### 📋 작업 알림 기능 (Reminder)

- **예약 알림** - 일회성 및 반복 알림 작업 지원
- **자연스러운推送** - AI가 자연스러운 알림 어조 생성, 기계적 표현 방지
- **반복 작업** - Cron 표현식을 지원하는 반복 알림
- **범위 관리** - Bot/Group 수준 격리

#### 🧬 진화 시스템 (Evolution)

- **어휘 학습** - 그룹 채팅에서 새로운 단어와 밈 자동 학습
- **의미 이해** - 단어 의미, 사용 상황, 어조 기록
- **자연스러운 통합** - 적절한 상황에서 학습된 그룹 채팅 표현 자연스럽게 사용

#### 🎭 밈 시스템 (Meme)

- **밈 추출** - 그룹 채팅에서 유행어와 밈 자동 추출
- **벡터 저장** - 의미 벡터 표현, 유사도 검색 지원
- **인기 추적** - 고빈도 단어의 사용 빈도와 인기도 추적

### 🔌 내장 플러그인

| 플러그인               | 유형               | 설명                            |
|:-------------------|:-----------------|:------------------------------|
| **speech**         | AgentExtension   | MiniMax TTS 음성 합성 플러그인        |
| **lolisuki**       | RouteExtension   | 二次원 이미지 플러그인 (lolisuki.cn)    |
| **net-ease-music** | PassiveExtension | 网易云音乐 플러그인, 음악 카드 전송          |
| **qa**             | RouteExtension   | AI Q&A 플러그인, 웹 검색으로 답변        |
| **qq-face**        | PassiveExtension | QQ 이모지 의미 매칭 플러그인             |
| **seeddream**      | RouteExtension   | AI 이미지 생성 플러그인                |
| **animal**         | AgentExtension   | 가상 반려동물 양육 플러그인, 역할극과 호감도 시스템 |

### 💬 대화 향상

- **문맥 이해** - 과거 대화와 장기 기억 참조
- **다중 페르소나** - 다중 봇 역할 구성 지원
- **감정 공명** - 그룹 분위기 인식, 참여 방식 조정

## 🏁 시작하기

### 📋 전제 조건

시작하기 전에 다음이 설치되어 있는지 확인하세요:

- **JDK 17+**
- **Gradle 8.0+**
- **NapCat** - QQ 연동 서비스
- **LLM API Key** - Google Gemini / DeepSeek / MiniMax API

### ⚙️ 설치 및 구성

1. **저장소 복제**
   ```bash
   git clone https://github.com/spcookie/Erii.git
   cd Erii
   ```

2. **환경 변수 구성**
   환경 변수 템플릿을 복사하여 편집:
   ```bash
   cp .env.local.template .env.local
   nano .env.local
   ```
   주요 구성 항목:
    - `CHOICE_MODEL`: LLM 제공자 선택 (GOOGLE / DEEP_SEEK / MINIMAX)
    - 해당 API Key 구성
    - `NAPCAT_TOKEN`: NapCat 인증 토큰
    - `ENABLE_GROUPS`: 활성화할 그룹 목록

3. **봇 구성**
   구성 파일 템플릿을 복사하여 편집:
   ```bash
   cp application.conf.template application.conf
   nano application.conf
   ```
   주요 구성 항목:
    - `llm.choice-model`: LLM 제공자 선택 (GOOGLE / DEEP_SEEK / MINIMAX)
    - `onebot.bots`: QQ 봇 연결 정보
    - `groups.enable-groups`: 활성화할 그룹 목록

4. **개발 서버 시작**
   ```bash
   ./gradlew run
   ```

### 🐳 Docker 배포

**Erii 메인 서비스 시작**

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

## 🛠️ 기술 스택

### 🖥️ 백엔드 프레임워크

- **언어:** Kotlin 1.9+
- **웹 프레임워크:** Ktor 2.3+
- **빌드 도구:** Gradle 8.0+
- **데이터베이스:** H2 (임베디드)
- **ORM:** Exposed
- **의존성 주입:** Koin
- **작업 스케줄링:** JobRunr

### 🤖 봇 통합

- **봇 프레임워크:** Mirai + Overflow
- **연동 서비스:** NapCat (WebSocket)
- **LLM:** Google Gemini API / OpenAI Compatible API

### 🔌 확장 아키텍처

- **플러그인 프레임워크:** PF4J
- **SPI 인터페이스:** erii-spi 모듈이 확장점 정의

## 📦 빌드 및 실행

| 명령                        | 설명                     |
|:--------------------------|:-----------------------|
| `./gradlew compileKotlin` | Kotlin 코드 컴파일          |
| `./gradlew build`         | 전체 프로젝트 빌드             |
| `./gradlew run`           | 개발 서버 시작               |
| `./gradlew buildFatJar`   | 모든 종속성을 포함한 Fat JAR 빌드 |
| `./gradlew buildImage`    | Docker 이미지 빌드          |
| `./gradlew test`          | 모든 테스트 실행              |

## 📂 프로젝트 구조

```
Erii/
├── erii-common/                 # 📦 공통 모듈
│   └── src/main/kotlin/uesugi/common/
│       ├── EventBus.kt          # 비동기/동기 이벤트 버스
│       ├── BotRole.kt           # 봇 역할 정의
│       ├── ChatToolSet.kt       # 채팅 도구 세트
│       └── ...
├── erii-core/                   # 🧠 코어 모듈
│   └── src/main/kotlin/uesugi/
│       ├── Application.kt       # Ktor 애플리케이션 진입점
│       ├── config/              # 구성 및 DI
│       │   ├── IOC.kt          # Koin DI 구성
│       │   ├── LLMFactory.kt   # LLM 팩토리
│       │   └── ...
│       ├── core/
│       │   ├── agent/          # AI Agent
│       │   │   └── BotAgent.kt  # 핵심 AI Agent
│       │   ├── route/          # 라우팅 시스템
│       │   │   └── RoutingAgent.kt  # LLM 라우팅
│       │   ├── state/          # 핵심 AI 상태 시스템
│       │   │   ├── emotion/     # 감정 시스템
│       │   │   ├── memory/      # 기억 시스템
│       │   │   ├── flow/        # 흐름 시스템
│       │   │   ├── volition/    # 의지 시스템
│       │   │   ├── evolution/   # 진화 시스템
│       │   │   └── meme/        # 밈 시스템
│       │   ├── bot/            # 봇 역할 관리
│       │   │   └── BotRoleManager.kt
│       │   ├── message/        # 메시지 처리
│       │   │   └── history/    # 히스토리 서비스
│       │   ├── plugin/         # 플러그인 구현
│       │   ├── rule/           # 규칙 엔진
│       │   └── component/      # 컴포넌트
│       │       ├── browser/   # 브라우저 (스크린샷/Markdown 가져오기)
│       │       ├── embedding/  # 벡터 임베딩
│       │       ├── search/     # 검색 서비스
│       │       └── storage/    # 저장소 (벡터/객체)
│       └── server/             # HTTP API 라우트
├── erii-spi/                    # 🔌 SPI 인터페이스 모듈
│   └── src/main/kotlin/uesugi/spi/
│       ├── AgentExtension.kt   # Agent 확장 인터페이스
│       ├── PluginDefinition.kt # 플러그인 정의 어노테이션
│       └── processor/           # 어노테이션 프로세서
├── erii-plugins/                # 🎨 플러그인 모듈
│   ├── speech/                  # MiniMax TTS 음성 합성
│   ├── lolisuki/                #二次원 이미지
│   ├── net-ease-music/          #网易云音乐 검색 및 카드 전송
│   ├── qa/                      # AI 웹 검색 Q&A
│   ├── qq-face/                 # QQ 이모지 의미 매칭
│   ├── reminder/                # 예약 알림 및 지연 메시지
│   └── seeddream/               # AI 이미지 생성
└── build.gradle.kts             # 루트 빌드 구성
```

## 🗺️ 아키텍처

Erii는 내부 `EventBus`를 사용하여 이벤트 중심 아키텍처를 채택하고 시스템 간 결합을 분리합니다.

```
메시지 수신 (NapCat/Mirai)
       │
       ▼
GroupMessageEventListener
       │
       ▼
HistoryService (히스토리 저장)
       │
       ▼
RoutingAgent (LLM 의도 분류) / CmdRuleRegister (명령어 매칭)
       │
       ▼
RouteCallEvent (EventBus.postAsync를 통해 배포)
       │
       ▼
BotAgent (이벤트 소비, AI Agent 실행)
       │
       ▼
상태 업데이트 (감정, 기억, 흐름, 의지, 알림, 규칙, 진화, 밈)
```

### 플러그인 확장점

| 확장 유형                | 설명          | 매칭 방식              |
|:---------------------|:------------|:-------------------|
| **AgentExtension**   | 범용 Agent 확장 | 조합 사용              |
| **RouteExtension**   | LLM 라우팅 확장  | RoutingAgent 의도 분류 |
| **CmdExtension**     | 명령어 확장      | `/xxx` 명령어 매칭      |
| **PassiveExtension** | 수동 확장       | 后台 작업/이벤트 리스너      |

## 📄 라이선스

이 프로젝트는 [MIT](LICENSE) 라이선스로 오픈소스입니다.

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
