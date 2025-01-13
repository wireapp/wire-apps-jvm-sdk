# Wire Applications JVM SDK

SDK for Wire third-party applications written in Kotlin, supporting JVM languages.
Import the SDK in your project to build your application and interact with the Wire backend and serve your users.

This will create a full-fledged client. It can send or receive
messages, place or receive calls, for example.

After completing the onboarding process, the Wire platform will provide an Application ID and an API_TOKEN.
These credentials are required to authenticate your application via the SDK.

Deploying the application and initializing the SDK will enable it to receive invites to Team and then reading/writing
messages to it.

## How to use it

TODO - Add examples

## Requirements

* Java 17+
* Access to the file system to store cryptographic keys and data

## Import with

### Gradle

```kotlin
dependencies {
    implementation("com.wire.integrations:wire-apps-jvm-sdk:0.0.1-SNAPSHOT")
}
```

### Maven

Add the following to your `build.gradle` file:

```xml

<dependency>
    <groupId>com.wire.integrations</groupId>
    <artifactId>wire-apps-jvm-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Build the project

```shell
./gradlew build 
```

## Troubleshooting

No issues known.
