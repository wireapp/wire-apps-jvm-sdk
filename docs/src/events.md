## Wire Events

When implementing the events handler `WireEventsHandler` your App will be notified when an event is received from the Websocket connection.

Below is a table of existing events that can be received and what are their purpose:

| Event                      | Description                                                                                                     |
|----------------------------|-----------------------------------------------------------------------------------------------------------------|
| onMessage                  | A Text message was received.                                                                                    |
| onConversationJoin         | The App has been added to a conversation.                                                                       |
| onConversationDelete       | A conversation that the App was part of, was deleted.                                                           |
| onAsset                    | An Asset (file) was received.                                                                                   |
| onComposite                | A Composite message was received. Composite messages are a combination of text and buttons in a single message. |
| onButtonAction             | A button action (button press/click) was received. From a composite message.                                    |
| onButtonActionConfirmation | Sent from the SDK that it received the button action.                                                           |
| onKnock                    | Also known as `ping` (to call for attention in a conversation) was received.                                    |
| onLocation                 | A message containing details (longituds, latitude) of a location.                                               |
| onDeletedMessage           | When a message is deleted.                                                                                      |
| onReceiptConfirmation      | When your message was `DELIVERED` or another user has read your message.                                        |
| onTextEdited               | An existing message was edited.                                                                                 |
| onCompositeEdited          | An existing Composite message was edited.                                                                       |
| onReaction                 | When an user has reacted to a message.                                                                          |
| onInCallEmoji              | Received an emoji while in a call.                                                                              |
| onInCallHandRaise          | Received the Hand Raise emoji while in a call.                                                                  |
| onMemberJoin               | When one or more users joined a conversation the App is part of.                                                |
| onMemberLeave              | When one or more users have left a conversation the App is part of.                                             |
