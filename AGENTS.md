# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin/JVM 17 Gradle monorepo. `erii-core` contains the Ktor runtime, state services, routing, templates, and
most tests. `erii-common` holds shared data models and configuration contracts. Public plugin APIs live in
`erii-spi/erii-spi-core` and `erii-spi/erii-spi-annotation`; Gradle plugin packaging is in `erii-plugin-gradle`.
Adjacent components include the Go CLI (`erii-cli`), distribution tooling (`erii-distribution`), built-in plugins
(`erii-plugins`), Docker assets, and documentation under `doc` and `erii-docs`.

Use standard source layouts: `src/main/kotlin`, `src/main/resources`, and `src/test/kotlin`. Keep changes within the
owning module and avoid coupling public SPI code to `erii-core` internals.

## Build, Test, and Development Commands

On Windows, run commands from the repository root:

```powershell
.\gradlew.bat build                    # compile all included Gradle modules and run tests
.\gradlew.bat :erii-core:test          # run core unit and Ktor integration tests
.\gradlew.bat :erii-core:run           # start the Ktor application from the repository root
.\gradlew.bat :erii-core:test --tests "uesugi.core.state.*"  # targeted tests
```

For the CLI, use `cd erii-cli; go test ./...`. Use module-specific tasks during iteration, then run the broader
affected-module test suite before submitting.

## Coding Style & Naming Conventions

Kotlin uses the official style (`kotlin.code.style=official`) with four-space indentation. Use `PascalCase` for types,
`camelCase` for functions and properties, and lowercase dot-separated packages under `uesugi`. Prefer immutable
transformations, explicit module boundaries, and existing repository helpers over duplicate utilities. No
repository-wide lint task is configured, so format with the Kotlin IDE formatter and rely on compilation/tests for
enforcement.

## Testing Guidelines

Tests use `kotlin.test`/JUnit and Ktor's test host. Name files `*Test.kt`; descriptive backtick test names are common.
Add focused regression tests beside the affected package. Avoid live network dependencies and isolate databases with
in-memory H2 where practical.

## Commit & Pull Request Guidelines

Follow the established Conventional Commit pattern, such as `fix(config): ...`, `feat(agent): ...`, or
`refactor(history): ...`. Keep commits scoped; reserve `Sync module` for actual synchronization work. Pull requests
should explain behavior changes, list affected modules and verification commands, link relevant issues, and include
screenshots for TUI or web UI changes.

## Security & Configuration

Never commit API keys, tokens, local database files, generated logs, or machine-specific configuration. Document new
configuration keys and provide safe defaults where possible.
