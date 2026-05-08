# AGENTS.md

## Purpose

This repository contains the Wire Apps JVM SDK: a Kotlin/JVM SDK for third-party Wire applications. The main deliverable is the `:lib` module, published as `com.wire:wire-apps-jvm-sdk`. The repository also includes Java and Kotlin sample applications that exercise the SDK from consumer projects.

This guide is for contributors and coding agents working in this repository. Keep changes narrow, preserve existing module boundaries, and prefer the established build, test, and style tooling over ad hoc workflows.

## Repository Layout

- `lib/`: main SDK module and the only published artifact.
- `sample/sample-kotlin/`: Kotlin sample app using `:lib`.
- `sample/sample-java/`: Java sample app using `:lib`.
- `buildSrc/`: shared build logic and version constants used by Gradle scripts.
- `config/detekt/`: Detekt configuration and baseline.
- `storage/`: local runtime state used when running the SDK or samples. Treat as local data, not source.

## Setup

### Requirements

- Java 17
- Gradle wrapper (`./gradlew`)
- Network access if you need to resolve dependencies

### First commands

```sh
./gradlew build
./gradlew ktlintCheck
./gradlew detekt
```

### Useful module-scoped commands

```sh
./gradlew :lib:test
./gradlew :sample-kotlin:run
./gradlew :sample-java:build
```

### Local runtime notes

- The SDK creates and uses a repository-root `storage/` directory for SQLite state and cryptographic material.
- If you switch backend environments or `applicationId`, a stale `storage/` directory can break local runs. Clean or move it when debugging environment-specific issues.

## Architecture

### Module roles

- `:lib` is the product. Keep public API decisions centered here.
- `:sample-kotlin` and `:sample-java` are consumer examples, not alternate implementations.

### Main entrypoints

- `com.wire.sdk.WireAppSdk`: primary SDK facade for consumers.
- `com.wire.sdk.service.WireApplicationManager`: higher-level API for teams, conversations, assets, and messages.
- `com.wire.sdk.WireEventsHandler*`: event callback abstractions for incoming Wire events.

### Internal layering

The `lib` module is structured in clear layers:

- `config/`: Koin dependency graph, HTTP client setup, database initialization.
- `client/`: backend HTTP/WebSocket API adapters built on Ktor.
- `crypto/`: MLS and crypto integration via `core-crypto-jvm`.
- `service/`: orchestration logic, event routing, conversation coordination.
- `persistence/`: SQLite-backed storage abstractions and implementations.
- `model/`: public and internal data models, including protobuf-backed message types.
- `utils/`: serialization, crypto helpers, and small shared utilities.
- `exception/`: WireException hierarchy and transport/backend error types.
- `logging/`: SLF4J/logback wrappers and shared logging utilities.

### State and data flow

- HTTP and WebSocket transport are provided by Ktor.
- Dependency injection is isolated through Koin.
- Persistent state is stored in SQLite via SQLDelight.
- Protocol buffers live under `lib/src/main/proto`.
- SQLDelight schema and migrations live under `lib/src/main/sqldelight`.

When adding features, keep transport code in `client/`, orchestration in `service/`, persistence in `persistence/`, and avoid pushing behavior into model classes unless it is truly model-local.

## Build and Tooling Conventions

### Gradle

- The build uses Kotlin DSL (`*.gradle.kts`).
- Java toolchains are pinned to 17.
- `:lib` also builds a shaded jar via `shadowJar`.
- Publishing is configured through `com.vanniktech.maven.publish`.

### Static analysis

- Formatting is enforced with Ktlint.
- Static analysis is enforced with Detekt using `config/detekt/detekt.yml`.
- CI runs `ktlintCheck`, `detekt`, and `build` on pull requests.

### Generated sources and schemas

- Do not hand-edit generated output under `build/` or generated directories.
- Protobuf definitions belong in `lib/src/main/proto`.
- SQLDelight schema changes belong in `lib/src/main/sqldelight`.
- Database migrations are checked into source control under `lib/src/main/sqldelight/migrations`.

If you change persistence models, verify both the SQLDelight schema and migration story, not just compilation.

## Code Conventions

### Formatting

The repository-wide defaults from `.editorconfig` are authoritative:

- UTF-8, LF endings, final newline
- 4-space indentation
- max line length: 100
- YAML files use 2 spaces

Kotlin-specific conventions already in use:

- No trailing commas
- Import ordering is not using Ktlint defaults
- Some wrapping rules are intentionally relaxed

Run the Gradle lint tasks instead of guessing style rules.

### Kotlin style

- Prefer existing naming and package structure over introducing new patterns.
- Use named arguments where existing code already does so, especially on longer constructor/service calls.
- Public SDK-facing types and methods generally include KDoc; preserve that standard when expanding the public API.
- Java interoperability matters. Many APIs intentionally provide both blocking and `suspend` variants. Do not remove one side of that pairing without a strong reason.

### Licensing and headers

Source files in this repository use the Wire GPL header. Preserve existing headers when editing files, and add the same style of header to new source files where appropriate.

### Logging and errors

- Use SLF4J/logback conventions already present in the codebase.
- Map backend and transport failures through the existing `WireException` types rather than inventing parallel error models.

## Testing Expectations

- Unit and integration-style tests live under `lib/src/test/kotlin`.
- Existing tests use JUnit 5, MockK, Kotlin test, coroutine test utilities, and WireMock.
- PR CI runs the full build, so keep test additions deterministic and local.

Before finishing a change, run the narrowest useful commands first, then the broader checks if the change crosses boundaries:

```sh
./gradlew :lib:test
./gradlew ktlintCheck detekt
./gradlew build
```

## Contribution Guidelines

### Scope control

- Prefer targeted changes over large refactors.
- Avoid renaming packages or moving layers unless the task requires it.
- Keep sample changes aligned with the SDK API; do not let examples drift from the current public surface.

### When changing public API

- Update both Kotlin and Java usage expectations.
- Check whether samples should be updated.
- Maintain binary coordinates and publishing assumptions in `lib/build.gradle.kts`.

### When changing persistence or protocol behavior

- Review storage implications in `storage/`.
- Review SQLDelight migrations and schema versioning.
- Review protobuf compatibility and downstream serialization effects.
- Add or update tests that exercise the changed path.

### When changing build or release logic

- Check `.github/workflows/pull-request.yml` and `.github/workflows/release.yml`.
- Preserve Java 17 compatibility unless there is an explicit repository-wide decision to change it.

## Practical Guardrails For Agents

- Read existing code in the affected package before editing.
- Do not overwrite user changes in a dirty worktree.
- Avoid editing `storage/`, `build/`, or other generated/runtime artifacts unless the task is explicitly about them.
- Prefer root-cause fixes in `:lib` over patching samples around SDK behavior.
- Verify with Gradle tasks relevant to the files you changed.

## Quick Checklist

Before opening or finalizing a change:

- Code is in the right layer and module.
- Formatting and static analysis pass.
- Tests cover the changed behavior.
- Migrations or generated-source inputs are updated if needed.
- Samples or docs are updated if the public API changed.
- AGENTS.md should be updated whenever the public API, module layout, or CI pipeline changes.
