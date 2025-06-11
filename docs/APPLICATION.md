# Getting started creating a Wire Application with Wire JVM SDK

This guide will help you create and understand how to develop and deploy a Wire Application written
in a JVM language (Java, Kotlin, Scala). The SDK you will be using will simplify encryption/decryption, and http client calls to the Wire backend, leaving you to care about your business logic.

Note that the SDK takes care of security in transit and partially for securing cryptographic data stored by the SDK in the filesystem. However, as you will have access to decrypted messages and identifiers of conversations and teams, it is up to you to secure them.

## Prerequisites

- Java 17 or higher
- Kotlin 2.x.x if you are using Kotlin
- An application registered with Wire (to obtain an API token)
- File system access for storing cryptographic keys and data

## Adding the SDK to Your Project

### Gradle

```kotlin
dependencies {
    implementation("com.wire.integrations:wire-apps-jvm-sdk:{version}")
}
```

### Maven

```xml
<dependency>
    <groupId>com.wire.integrations</groupId>
    <artifactId>wire-apps-jvm-sdk</artifactId>
    <version>{version}</version>
</dependency>
```

## Initializing the SDK

The SDK needs to be initialized with your application's credentials, the backend host, a password for the cryptographic material and your event handler implementation:

| Parameter                     | Description                                                                                                                                                                                |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `applicationId` + `apiToken`  | Retrieved from the backend during onboarding, when you register a new Application                                                                                                          |
| `apiHost`                     | The target backend, should be production for normal operations: https://prod-nginz-https.wire.com                                                                                          |
| `cryptographyStoragePassword` | The password you choose to let the SDK encrypt the cryptographic material at rest. It is recommended to be generated randomly and stored in a secure place. It must be 32 characters long. |
| `wireEventsHandler`           | Your implementation (extending the `WireEventsHandler` abstract class)                                                                                                                     |

Initializing an instance of WireAppSdk is enough to get access to local stored teams and conversations and to send messages or similar actions.

However, to establish a long-lasting connection with the backend and receive all the events targeted to you Application, you need to call the `startListening()` method.
The `startListening()` method keeps a background thread running until explicitly stopped or the application terminates.

## Managing Teams and Conversations

The SDK provides access to teams and conversations through the `WireApplicationManager`, available after initializing the `WireAppSDK`

```kotlin
val applicationManager = wireAppSdk.getTeamManager()

// Get all teams the application has been invited to
val teams = applicationManager.getStoredTeams()
teams.forEach { teamId ->
    println("Team: $teamId")
}

// Get all conversations the application has access to
val conversations = applicationManager.getStoredConversations()
conversations.forEach { conversation ->
    println("Conversation: ${conversation.id} in team: ${conversation.teamId}")
}

// Get application data
val appData = applicationManager.getApplicationData()
println("Application name: ${appData.name}")
```

## Handling Events

The SDK uses the `WireEventsHandler` to notify your application about events and messages. Override the methods that you need in this class to handle them however you want. The http connection, deserialization, authentication and decrypting are performed by the Application, so you will receive the event as a `WireMessage`

## Complete Example

Here's a complete example showing how to initialize the SDK and handle received events:

```kotlin
fun main() {
    val wireAppSdk = WireAppSdk(
        applicationId = "9c40bb37-6904-11ef-8008-be4b58ff1d17",
        apiToken = "your-api-token",
        apiHost = "https://your-wire-backend.example.com",
        cryptographyStoragePassword = "secure-password",
        object : WireEventsHandler() {
            override fun onMessage(wireMessage: WireMessage) {
                println("Message received: $wireMessage")
                
                // Add your message handling logic here, like storing the message,
                //   sending back another message, or triggering some workflow
            }
        }
    )
    
    // Start the SDK
    wireAppSdk.startListening()
}
```
For simplicity the subclassing of WireEventsHandler is done inline as an anonymous class, but you can create a separate class for it,
especially if you handle events in a complex way:
```kotlin
class MyWireEventsHandler : WireEventsHandler() {
    private val logger = LoggerFactory.getLogger(MyWireEventsHandler::class.java)

    override fun onMessage(wireMessage: WireMessage.Text) {
        logger.info("Message received: $wireMessage")
    }
}
```

**NOTE**: Your application can simply call `startListening()` and a new thread is created and will keep the Application running and receiving events. To stop it, just close the Application (Cmd+d) or call `stopListening()`

## Sending Messages

You can make your application send messages to specific conversations.
It can be sent via WireApplicationManager, for example you might want to send a message when an external event happens or in a scheduled fashion.
Otherwise, you can make the Application react to events it receives, and send back a message immediately.

There are two ways to send messages on the SDK:

### Standalone messages
For when you to send a message. It can be achieved when you have the conversation ID it needs to be sending a message.
```kotlin
val applicationManager = wireAppSdk.getApplicationManager()
applicationManager.sendMessageSuspending(
    message = WireMessage // All WireMessage event types are supported through this method
)
```
> **_Java:_**  Use `applicationManager.sendMessage`

For when you upload and send an asset (file) to a conversation.

Nots: When sending an asset message, you need to provide the file, name and mime type, while we take
care of the encryption and metadata based on file mime type.
```kotlin
// Get local File
val resourcePath = javaClass.classLoader.getResource("my-file.png")?.path
    ?: throw IllegalStateException("Test resource 'my-file.png' not found")
val asset = File(resourcePath)

// Get File data in ByteArray
val originalData = asset.readBytes()

// Send File with necessary parameters
applicationManager.sendAssetSuspending(
    conversationId = wireMessage.conversationId,
    asset = AssetResource(originalData),
    name = asset.name,
    mimeType = "image/png",
    retention = AssetRetention.ETERNAL
)
```
> **_Java:_**  Use `applicationManager.sendAsset`


### Reacting to events
For when the SDK received an event and you want to react/respond to this event by sending a message.
This is done inside the method override of `WireEventsHandler` using a local `manager`.

#### Text
```kotlin
override suspend fun onMessageSuspending(wireMessage: WireMessage.Text) {
    println("Message received: $wireMessage")
    
    // Add your message handling logic here, like storing the message,
    //   sending back another message, or triggering some workflow

    manager.sendMessageSuspending(
        message = WireMessage // All WireMessage event types are supported through this method
    )
}
```
> **_Java:_**  Use `override fun onMessage(wireMessage: WireMessage.Text) { .. }`

> **_Other Event Types:_** Other event types can be listened through `on[EventType]Suspending` or `on[EventType]`

#### Asset

Receiving an asset event does not mean receiving the asset itself. The asset is a separate entity that needs to be downloaded.
In the event there will be a `remoteData` field that contains the information needed to download the asset.
For simplicity, you can simply forward the `AssetRemoteData` to downloadAssetSuspending, which will
actually return the asset in byte array format.

```kotlin
override suspend fun onAssetSuspending(wireMessage: WireMessage.Asset) {
    println("Asset received: $wireMessage")
    
    // Add your asset handling logic here, like downloading the asset,
    // sending a message or triggering some workflow
    
    // To download the asset you can:
    wireMessage.remoteData?.let { remoteData ->
        val asset = manager.downloadAssetSuspending(remoteData)
        val fileName = wireMessage.name ?: "unknown-${UUID.randomUUID()}"
        val outputDir = File("build/downloaded_assets").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)
        outputFile.writeBytes(asset.value)
        println("Downloaded asset with size: ${asset.value.size} bytes, saved to: ${outputFile.absolutePath}")
    }
}
```
> **_Java:_** Use `override fun onAsset(wireMessage: WireMessage.Asset) { .. }`

## Deploy example

After building your Application leveraging the SDK, you need to find a place to let it run. At its core, the SDK is working as a client for the Wire Backend, with some storage for crypto data and for conversations (local `SQLite` database). This means that generally it needs only to be able to reach the public internet, specifically the Wire backend host you chose.

You can take the artifacts built from your Application and run it in any server, on-premise or in the cloud, or Dockerize the Application and do the same. Note that you want 1 instance of the Application running and it can run indefinitely.

For example, giving that the app does not need HTTPS, DNS, CDN, simpler deployment processes are available, for example: [Heroku - Java](https://devcenter.heroku.com/articles/getting-started-with-java)

## Building Locally
In case you need to build the SDK locally, you can skip the signing option by running:
```bash
./gradlew publishToMavenLocal -PskipSigning=true
```
> **_Keep in mind_** to include `repositories { mavenCentral() }` in your `build.gradle.kts` file.

## Troubleshooting

- Enable DEBUG logging on the SDK if you are developing an Application and want to test it in a safe environment. Set the log level to DEBUG in your logging framework for the package `com.wire.integrations.jvm` (e.g. for Logback `<logger name="com.wire.integrations.jvm" level="DEBUG" />`).
- If you switch between different Wire environments, you may need to delete the `apps.db` directory to avoid conflicts
- For connection issues, verify your API token, host URL and if your deployed app has access to the public network (firewalls, docker ports, etc.)
- When running into cryptography issues, ensure your storage password is consistent between app restarts
- The SDK is designed to be thread-safe. The `startListening()` and `stopListening()` methods are synchronized to prevent concurrent modifications to the SDK state. However at this moment, only using a single Wire Application instance has been tested.

## Additional Resources

- Check the [SDK README](../README.md) for more information
- For any issue, requests or improvements, let us know by contacting us or creating a new issue on GitHub
