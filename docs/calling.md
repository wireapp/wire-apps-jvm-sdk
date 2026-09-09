# Calling integration

The SDK receives Wire calling signaling, joins existing conferences on request, and applies
incoming MLS updates. Other clients initiate calls and initialize their conferences.
Your app supplies AVS, handles its callbacks and SFT HTTP requests, and decides whether to
answer or leave a call. The SDK does not initialize AVS or manage audio/video devices.

## Messages and callbacks

Override `onCallingMessageReceived(WireMessage.Calling)` in `WireEventsHandlerDefault` or
`WireEventsHandlerSuspending`. Pass the content to your calling engine without interpreting
it as a chat message.

The message includes authenticated sender user/device IDs and the event timestamp.
`conversationId` identifies the transport conversation. `callConversationId` identifies
the associated call and falls back to the transport conversation for older messages.
Unknown signaling types and remote-mute content are forwarded unchanged.

Use the existing `onInCallReactionReceived` and `onInCallHandRaiseReceived` callbacks for
in-call reactions and hand raises. AVS-specific state callbacks, such as ringing, established,
and active speakers, belong to the app's engine adapter.

Send engine-generated signaling with:

```kotlin
manager.sendMessageSuspending(WireMessage.Calling.create(conversationId, content))
```

For Java:

```java
manager.sendMessage(WireMessage.Calling.create(conversationId, content));
```

Sending completes after backend acceptance. Failures propagate to the caller. A stale-epoch
retry re-encrypts the message after recovery. Calling messages do not inherit chat expiration.

## Conference membership and keys

Calling uses the single `conference` subconversation of a parent conversation. All methods below
are on `WireApplicationManager` and have a corresponding `Suspending` variant.

| Method | Behavior |
|---|---|
| `joinSubconversation(id)` | Join an existing conference and return its initial epoch snapshot |
| `leaveSubconversation(id)` | Leave this device's conference membership |

Feed the result of joining to AVS before starting media. A repeated join checks backend state
and reuses valid membership. An initial snapshot is returned by the join method; subsequent
changes arrive through `onSubconversationEpochChanged`.

Joining requires a conference already initialized by another client. An absent or epoch-zero
conference causes the join to fail. The SDK never creates it. The explicit join sends the MLS
external commit required to add this device; it does not start the call.

A snapshot contains the parent conversation ID, Base64 MLS group ID, epoch, member user/device
identities, and a 32-byte exported calling secret. `members` is a `Map<QualifiedId, List<String>>`
mapping each user to their device IDs. Epoch, members, and key are read in one
CoreCrypto transaction. The app converts these SDK values to whatever representation its AVS
binding expects.

The app owns each snapshot and must close it after use. `getSharedSecret()` returns a copy;
clear that copy when your engine has consumed it. Neither snapshots nor exported secrets are
written to SQLDelight.

```kotlin
manager.joinSubconversationSuspending(conversationId).use { info ->
    val secret = info.getSharedSecret()
    try {
        // Supply info.epoch, info.members and secret to your calling engine.
    } finally {
        secret.fill(0)
    }
}
```

Override these additional callbacks as needed:

- `onSubconversationEpochChanged(info)` for later key/member changes.
- `onSubconversationLeft(conversationId)` after local departure, removal, or parent invalidation.
- `onCallingError(conversationId, error)` for asynchronous MLS processing failures.

Calling and epoch callbacks are serialized per transport conversation and run outside crypto
transactions. They may call manager methods. Keep callbacks short; hand media processing to
your app's own execution context. The default epoch callback closes unused snapshots.

The SDK passes incoming proposals and commits to CoreCrypto, but never schedules or sends
pending-proposal commits and does not rotate conference keys. Other clients must commit
membership changes. Applied commits trigger epoch updates or, when this device is removed,
`onSubconversationLeft`. This callback describes this device's departure, not proof that every
participant's call has ended. Call-ending signaling is also forwarded to the app unchanged.
Future-epoch messages buffered by CoreCrypto are delivered when the corresponding commit arrives.

## Restart and cleanup

The SDK caches the conference group ID in memory. On demand, it recovers the ID from the
backend and checks both device membership and local CoreCrypto state. This needs no additional
database table. It never joins a call solely because it receives a conference event.

After an app restart, explicitly join if you want to resume participation and obtain the current
keys. Missing local crypto state requires an explicit join. The app remains responsible for
deciding whether to resume its media engine. Leaving a call does not leave the parent conversation.
Decryption errors do not trigger an automatic rejoin. The SDK reports them to the app, which
can explicitly join again if it wants to recover participation.
SDK shutdown stops background work but does not issue a remote leave; call the leave method
before shutdown when that is your intended behavior.

## Engine HTTP requests

`getCallingConfiguration()` fetches the backend's calling configuration JSON unchanged.
Call it when your engine requests configuration, then invoke your engine's config-update
function with the result.

The configuration includes the backend-provided SFT server information. Feed the full JSON
to AVS so new configuration fields remain available without an SDK update.

Your app handles AVS's SFT request callback directly. AVS supplies the URL, request payload,
and native callback context. The app performs the HTTP request and returns the response or
failure to AVS using that same context. The SDK does not need another callback or request model
for this exchange.

Use your app's HTTP client for SFT. Wire API credentials belong to the SDK's backend requests
and must not be forwarded to SFT. Native callback contexts and request correlation stay inside
your app.

## Compilable examples

- [Kotlin adapter](../sample/sample-kotlin/src/main/kotlin/com/wire/sdk/sample/CallingExample.kt)
- [Java adapter](../sample/sample-java/src/main/java/com/wire/sdk/sample/examples/callbacks/CallingExample.java)

Both examples take an app-owned engine interface. They demonstrate callback forwarding,
initial key delivery, signaling sends, and leaving without depending on AVS.
