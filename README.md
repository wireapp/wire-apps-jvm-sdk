# Wire Applications JVM SDK

SDK for Wire third-party applications written in Kotlin, supporting JVM languages.
Import the SDK in your project to build your application and interact with the Wire backend and serve your users.

This will create a full-fledged client. It can send or receive
messages, place or receive calls, for example.

After completing the onboarding process, the Wire platform will provide an APP_TOKEN,
required to authenticate your application via the SDK.

Deploying the application and initializing the SDK will enable it to receive invites to Team and then reading/writing
messages to it.

## How to use it

### Wire dev

You can define the implementation of BackendClient to use, by changing it in the Modules.kt file.

* BackendClientDemo targets the Wire backend for development purposes, it uses the Client API for
  testing instead of the Application API
* BackendClientImpl is the real implementation of the SDK, targeting the Wire backend as an
  Application

For the demo setup, some default properties are there, you can override those values
by creating a `demo.properties` file in the classpath (e.g. `src/main/resources/demo.properties`).

## Requirements

* Java 17+
* Access to the file system to store cryptographic keys and data

## Import with

### Gradle

```kotlin
dependencies {
    implementation("com.wire:wire-apps-jvm-sdk:0.0.1")
}
```

### Maven
```xml
<dependency>
    <groupId>com.wire</groupId>
    <artifactId>wire-apps-jvm-sdk</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Build the project

```shell
./gradlew build 
```

## Troubleshooting

If you have started using the SDK targeting one Wire environment,
and later you want to switch to another, you may need to move/delete the `apps.db` directory
