# Erii Distribution Workflow

Erii 项目分发脚本，自动化构建、复制和版本 bump 流程。

## 快速开始

```bash
cd .workflow
npm install
```

## 使用

```bash
# 完整流程（构建 + 交互式版本选择）
npx tsx src/index.ts

# 自动模式（git 变更检测 → 自动 patch bump，无 TUI）
npx tsx src/index.ts --auto

# 仅构建阶段
npx tsx src/index.ts build

# 仅版本阶段（交互式 TUI）
npx tsx src/index.ts version

# 预览模式（不实际修改文件）
npx tsx src/index.ts --auto --dry-run

# 跳过部分构建步骤
npx tsx src/index.ts --skip-cli --skip-plugins

# 详细输出
npx tsx src/index.ts --verbose
```

## 工作流程

### Phase 1: 构建与复制

| 步骤           | 内容                                                                        |
|--------------|---------------------------------------------------------------------------|
| erii-cli     | `go run mage` 构建 → 复制 .conf/conf/opts/build 到分发目录                         |
| erii-core    | `gradlew :erii-core:installDist` → 复制 jar → `split.mjs` 分片 → 保留 8 个核心 jar |
| erii-plugins | `gradlew :erii-plugins:assembleAllPlugins` → 复制插件 zip                     |

### Phase 2: 版本 Bump

4 个版本组，每组可选策略：

| 策略   | 效果                                     |
|------|----------------------------------------|
| 自动侦测 | `git status` 有变更 → bump patch；无变更 → 跳过 |
| 补丁版本 | x.x.+1                                 |
| 次版本  | x.+1.x                                 |
| 主版本  | +1.x.x                                 |
| 跳过   | 不修改                                    |

| 组                 | 包含包                       | 设置方式                     |
|-------------------|---------------------------|--------------------------|
| [1] deps + cli    | deps-\*, erii-cli-\*      | set-version.mjs          |
| [2] config + core | erii-config, erii-core    | 直接编辑 package.json        |
| [3] plugins       | 各 erii-plugins/&lt;id&gt; | 直接编辑 package.json（可逐个配置） |
| [4] meta          | erii, create-erii         | 直接编辑 package.json        |

版本 bump 完成后自动执行 `sync-versions.mjs` 同步 erii 聚合包的依赖版本。

## CLI 选项

| 选项               | 说明                 |
|------------------|--------------------|
| `--auto`         | 自动侦测版本（无 TUI）      |
| `--dry-run`      | 预览模式，不写入文件         |
| `--verbose`      | 详细输出               |
| `--skip-cli`     | 跳过 erii-cli 构建     |
| `--skip-core`    | 跳过 erii-core 构建    |
| `--skip-plugins` | 跳过 erii-plugins 构建 |

## 目录结构

```
src/
├── index.ts              # 入口
├── cli.ts                # Commander 程序定义
├── config.ts             # 路径常量、jar 匹配规则
├── types.ts              # 类型定义
├── lib/
│   ├── shell.ts          # spawnSync 封装
│   ├── fs.ts             # 文件操作
│   └── semver.ts         # semver 辅助
├── build/
│   ├── index.ts          # Build 编排器
│   ├── step-cli.ts       # erii-cli 构建
│   ├── step-core.ts      # erii-core 构建 + jar 分发
│   └── step-plugins.ts   # erii-plugins 构建
└── version/
    ├── index.ts          # Version 编排器
    ├── detector.ts       # git 变更检测
    ├── tui.ts            # 交互式版本选择 UI
    ├── bumper.ts         # 版本 bump 执行
    └── sync.ts           # 依赖同步
```
