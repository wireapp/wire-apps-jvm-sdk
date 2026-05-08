# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Whenever you write code for this repository, consider if the changes require new tests to be written, and if so, include them in your response.

## Project Overview

Wire Apps JVM SDK - An SDK for building Wire third-party applications in Kotlin/JVM languages. The SDK handles encryption/decryption (MLS/Proteus), HTTP client calls to the Wire backend, and WebSocket connections for real-time events.

## Build Commands

```bash
# Build the project (includes shadowJar)
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.wire.sdk.WireAppSdkTest"

# Run a single test method
./gradlew test --tests "com.wire.sdk.WireAppSdkTest.koinModulesLoadCorrectly"

# Lint with ktlint
./gradlew ktlintCheck

# Format code with ktlint
./gradlew ktlintFormat

# Run detekt static analysis
./gradlew detekt

# Publish to local Maven (skip signing for local dev)
./gradlew publishToMavenLocal -PskipSigning=true

# Run sample Kotlin application
./gradlew :sample-kotlin:run
```

## Project Structure

- `lib/` - Main SDK library module
- `sample/sample-kotlin/` - Kotlin sample application
- `sample/sample-java/` - Java sample application
- `config/detekt/` - Detekt configuration

## Architecture

### Core Components

**WireAppSdk** (`lib/src/main/kotlin/com/wire/sdk/WireAppSdk.kt`)
- Main entry point. Initialize with applicationId, apiToken, apiHost, cryptographyStoragePassword (must be 32 chars), and a WireEventsHandler implementation.
- `startListening()` starts WebSocket connection in background thread
- `stopListening()` shuts down the connection
- `getApplicationManager()` returns WireApplicationManager for API operations

**WireApplicationManager** (`lib/src/main/kotlin/com/wire/sdk/service/WireApplicationManager.kt`)
- Primary interface for SDK consumers to interact with Wire backend
- Provides both blocking methods (for Java) and suspending methods (for Kotlin)
- Handles: sending messages, downloading/uploading assets, creating conversations, managing teams

**WireEventsHandler** (`lib/src/main/kotlin/com/wire/sdk/WireEventsHandler.kt`)
- Sealed class - extend `WireEventsHandlerDefault` (blocking) or `WireEventsHandlerSuspending` (coroutines)
- Override event methods like `onTextMessageReceived`, `onAssetSuspending` to handle incoming events
- Access `manager` property within handlers to send responses

**BackendClientHttp** (`lib/src/main/kotlin/com/wire/sdk/client/BackendClientHttp.kt`)
- Ktor http client connecting to the backend with Rest and WebSocket

### Dependency Injection

Uses Koin with `IsolatedKoinContext` for isolated context management. Module configuration in `lib/src/main/kotlin/com/wire/sdk/config/Modules.kt`.

### Cryptography

- `CryptoClient` interface with `CoreCryptoClient` implementation
- Handles MLS (Messaging Layer Security) and Proteus protocols
- Uses Wire's `core-crypto-jvm` library

### Persistence

- SQLDelight for local SQLite storage
- Schema files in `lib/src/main/sqldelight/com/wire/sdk/`
- Tables: App, Conversation, ConversationMember, Team

### Message Serialization

- Protobuf for message encoding (`lib/src/main/proto/messages.proto`)
- Serializers/deserializers in `lib/src/main/kotlin/com/wire/sdk/model/protobuf/`

## Testing

Tests use:
- JUnit 5 (JUnitPlatform)
- WireMock for HTTP mocking
- MockK for Kotlin mocking
- kotlinx-coroutines-test for coroutine testing

Environment variables are auto-configured for tests in `lib/build.gradle.kts`.

## Code Style

- ktlint for formatting (excludes generated code)
- detekt for static analysis (config in `config/detekt/detekt.yml`)
- Max line length: 100 characters
- Wildcard imports disallowed (except `java.util.*`)
