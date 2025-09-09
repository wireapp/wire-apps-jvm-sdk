# Understanding Apps

Apps — sometimes called *integrations*, *services*, or *third-party applications* — are modular components that extend the platform without being part of its core codebase.

## Key Characteristics
- **Independent from core code**  
  Apps are not bundled into the frontend or backend binaries. They live outside the main system.

- **Pluggable and flexible**  
  Apps can be added, updated, or removed without requiring a frontend/backend release.

- **User-driven installation**  
  Unlike system features that require admin deployment, apps can often be enabled (installed) directly by end users.

- **Open for third parties**  
  Apps are not limited to our company’s developers — external teams and third parties can build and operate their own apps.

## How They Work
Apps are separate pieces of software that communicate with the platform’s frontend and backend through a **common protocol** (such as APIs or events).  
They remain independent, but can seamlessly interact with the system and its users.  

An **App** is a way to extend and automate your team’s experience on Wire].
Apps can listen to messages, send responses, and integrate with external services.

## Why Apps?
- **Automation**: Reduce manual tasks and streamline workflows.
- **Integration**: Connect external systems (CRMs, support tools, monitoring).
- **Customization**: Tailor the platform to your team’s needs.

Apps are owned and managed at the **team level**, giving administrators control over:
- Which Apps can be installed
- What data Apps can access

## How to create an App
➡️ [Learn how to create an app](./creating-an-app.md)

## SDK Overview

We provide an official **Kotlin SDK** to help developers build apps quickly and safely.

### Why use the SDK
- Handles authentication for you
- Provides event handling
- Makes sending and receiving messages easier
- Strongly typed for Kotlin developers

### Future plans
We currently support **Kotlin**, and plan to expand to more languages (JavaScript, Python, etc.) so every developer can build apps in the language they know best.

For technical SDK usage and code examples, see the [Developer Documentation on GitHub](https://github.com/wireapp/wire-apps-jvm-sdk/blob/main/docs/APPLICATION.md).
