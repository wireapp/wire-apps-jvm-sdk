# Wire™

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp/wire](https://github.com/wireapp/wire), and the apk of the latest release at [https://wire.com/en/download/](https://wire.com/en/download/).

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

If you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and cause that application to connect to our servers for any purposes, we refer to that resulting application as an “Open Source App”.  All Open Source Apps are subject to, and may only be used and/or commercialized in accordance with, the Terms of Use applicable to the Wire Application, which can be found at https://wire.com/legal/#terms.  Additionally, if you choose to build an Open Source App, certain restrictions apply, as follows:

a. You agree not to change the way the Open Source App connects and interacts with our servers;

b. You agree not to weaken any of the security features of the Open Source App;

c. You agree not to use our servers to store data for purposes other than the intended and original functionality of the Open Source App;

d. You acknowledge that you are solely responsible for any and all updates to your Open Source App.

For clarity, if you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and do not cause that application to connect to our servers for any purposes, then that application will not be deemed an Open Source App and the foregoing will not apply to that application.

No license is granted to the Wire trademark and its associated logos, all of which will continue to be owned exclusively by Wire Swiss GmbH. Any use of the Wire trademark and/or its associated logos is expressly prohibited without the express prior written consent of Wire Swiss GmbH.

# Wire Applications JVM SDK

SDK for Wire third-party applications written in Kotlin, supporting JVM languages.
Import the SDK in your project to build your application and interact with the Wire backend and serve your users.

This will create a full-fledged client, connected with E2EE to other users and apps. It will listen to events, send and receive messages, and more.

The SDK will handle all the complex logic of cryptography and storage, so you can focus on building your application.

## How to use it

After completing the onboarding process, the Wire platform will provide an *apiToken*,
required to authenticate your application via the SDK.

For information about usage and onboarding refer to [Sdk Documentation](https://dev.wire.com).

## Requirements

* Java 17+
* Access to the file system to store cryptographic keys and data

## Import with
The latest release is avaliable at [Maven Central](https://central.sonatype.com/artifact/com.wire/wire-apps-jvm-sdk).

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

## Calling

Apps can use their own AVS integration with the SDK's signaling, conference MLS membership,
epoch callbacks, and calling HTTP operations. See [Calling integration](docs/calling.md)
for the API and Java/Kotlin examples.

## Typing indicator

Use the application manager to show that your app is preparing a response. Kotlin callers use `sendTypingIndicatorSuspending`; Java callers use `sendTypingIndicator`:

```kotlin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

val manager = sdk.getApplicationManager()
manager.sendTypingIndicatorSuspending(conversationId, TypingStatus.STARTED)
try {
    // Prepare and send the response.
} finally {
    withContext(NonCancellable) {
        runCatching {
            manager.sendTypingIndicatorSuspending(conversationId, TypingStatus.STOPPED)
        }.onFailure { logger.warn("Could not clear typing indicator", it) }
    }
}
```

```java
WireApplicationManager manager = sdk.getApplicationManager();
manager.sendTypingIndicator(conversationId, TypingStatus.STARTED);
try {
    // Prepare and send the response.
} finally {
    manager.sendTypingIndicator(conversationId, TypingStatus.STOPPED);
}
```

Import `TypingStatus` from `com.wire.sdk.model.http.conversation`. The Kotlin example assumes an application logger named `logger`. Each call sends one event without automatic refresh or retries, including on network failures. A lost `STOPPED` event clears when receiving clients time out the indicator. For longer operations, resend `STARTED` periodically (for example, every 8 seconds as a conservative interval). Stop any refresh and wait for an in-flight refresh to finish before sending `STOPPED`. The app must be a member of the conversation.

## Build the project

```shell
./gradlew build 
```

## Troubleshooting

If you have started using the SDK targeting one Wire environment or you have switched Api Token (apiToken),
and later you want to switch to another, you may need to move/delete the `storage` directory to have a clean start.
