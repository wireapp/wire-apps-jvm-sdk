# SDK Examples

This directory contains example code demonstrating how to use the Wire SDK in different scenarios.

The examples are divided into two categories:

```
examples/
  callbacks/
  standalone/
```

---

## callbacks/

Examples in this [directory](https://github.com/wireapp/wire-apps-jvm-sdk/tree/main/sample/sample-java/src/main/java/com/wire/sdk/sample/examples/callbacks) demonstrate how to **handle SDK events via callbacks**.

Each class extends: `WireEventsHandlerDefault` and overrides the callback methods relevant to the specific example.

### Running a callback example

To test a callback example:

1. Open the class:

```
com.wire.sdk.sample.Main
```

2. Replace the default handler:

```java
new GreetConversationOnAppAddedExample()
```

with the example class you want to run.

3. Run the `Main` class.

The SDK will start and your example class will receive and handle the relevant events.

---

## standalone/

Examples in this [directory](https://github.com/wireapp/wire-apps-jvm-sdk/tree/main/sample/sample-java/src/main/java/com/wire/sdk/sample/examples/callbacks) are **self-contained runnable programs**.

Each example includes its own `main()` method and demonstrates a complete workflow using the SDK.

### Running a standalone example

1. Ensure the required **environment variables** are set correctly.
2. Run the example class directly from its `main()` method.

Example:

```
CreateGroupConversationExample.main()
```

These examples typically perform a single operation.

---

## Purpose of the examples

The examples are intended to help developers:

- understand how to integrate the SDK into their applications
- learn how to react to SDK events
- quickly test SDK functionality
- explore common usage patterns
