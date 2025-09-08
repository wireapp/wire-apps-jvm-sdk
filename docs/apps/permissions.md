# App Permissions

Permissions define what your app is allowed to do inside the platform.

### Permission Types:
- **ALL**: Can do everything a normal User can.
    - Reserved for legal hold, archival bots, etc. are permanently online and active. 
- **READ/WRITE**: Utility Apps.
    - Users interact with these Apps in limited ways, on demand using some command like /poll (in contrast to type 1). Think polls, summary bots, Jira integrations, confluence integrations.
- **READ/WRITE**: Utility Apps.
    - Apps that notify you about things happening elsewhere and that need a very limited security scope. GitHub notifications, pager duty, jenkins bot, birthday bot. They do not receive messages.

### Example Scenarios
- A **standup bot** might only need `WRITE`.
- A **reporting app** might need `READ/WRITE`.
