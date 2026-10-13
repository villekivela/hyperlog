# Coaching hands off to the Claude app instead of calling a model

Free-text coaching runs in the user's Claude app on their own subscription. Hyperlog sends the
situation and the plan through the Android share sheet, and the user pastes back a Change for
review. Hyperlog makes no model calls. Anthropic's terms keep subscription credentials inside
Anthropic's own apps, and pay-per-token API usage is unwanted. Offline rules cover the
common situations without any model.

## Considered options

- **Claude API from the app**: smooth, but billed per token, and needs a key on the device or a
  server.
- **Subscription token in the app**: not allowed by the terms.
- **Custom MCP connector**: no paste step, but it needs a public HTTPS server holding training data,
  against local-first. Revisit if syncing is ever wanted for its own sake.
