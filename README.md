# Wire™

[![Wire logo](https://github.com/wireapp/wire/blob/master/assets/header-small.png?raw=true)](https://wire.com/jobs/)

This repository is part of the source code of Wire. You can find more information at [wire.com](https://wire.com) or by contacting opensource@wire.com.

You can find the published source code at [github.com/wireapp/wire](https://github.com/wireapp/wire), and the apk of the latest release at [https://wire.com/en/download/](https://wire.com/en/download/).

For licensing information, see the attached LICENSE file and the list of third-party licenses at [wire.com/legal/licenses/](https://wire.com/legal/licenses/).

If you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and cause that application to connect to our servers for any purposes, we refer to that resulting application as an “Open Source App”.  All Open Source Apps are subject to, and may only be used and/or commercialized in accordance with, the Terms of Use applicable to the Wire Application, which can be found at https://wire.com/legal/#terms.  Additionally, if you choose to build an Open Source App, certain restrictions apply, as follows:

a. You agree not to change the way the Open Source App connects and interacts with our servers; b. You agree not to weaken any of the security features of the Open Source App; c. You agree not to use our servers to store data for purposes other than the intended and original functionality of the Open Source App; d. You acknowledge that you are solely responsible for any and all updates to your Open Source App.

For clarity, if you compile the open source software that we make available from time to time to develop your own mobile, desktop or web application, and do not cause that application to connect to our servers for any purposes, then that application will not be deemed an Open Source App and the foregoing will not apply to that application.

No license is granted to the Wire trademark and its associated logos, all of which will continue to be owned exclusively by Wire Swiss GmbH. Any use of the Wire trademark and/or its associated logos is expressly prohibited without the express prior written consent of Wire Swiss GmbH.

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
    implementation("com.wire.integrations:wire-apps-jvm-sdk:0.0.1-SNAPSHOT")
}
```

### Maven
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

If you have started using the SDK targeting one Wire environment,
and later you want to switch to another, you may need to move/delete the `apps.db` directory
