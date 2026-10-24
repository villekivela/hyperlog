# The coach calls Claude through the API, with the hand-off as fallback

ADR 0001 kept the app off the API because pay-per-token use was unwanted. The owner now has a
monthly API budget, and the paste step is the most annoying part of coaching. So with a **Claude
key** in Settings, Hyperlog calls the Messages API itself, as a **check-in**: a wizard that starts
from one tile and goes on with Claude's questions, one a screen, each with a few answers to tap.
Claude replies through tools, so a Change, a workout or a program comes back as data that the
existing validators read. A reply that doesn't validate goes back to Claude with its problems, at
most twice, before you see them.

Without a key the check-in still opens on the same tiles and hands off through the share sheet
(**Ask Claude**, then **Paste reply**), as ADR 0001 built it.

- **Opus 5.5 only.** A few check-ins a week and a program a few times a year cost little. A
  check-in sends about 30k tokens of context once (program, sessions, library) and reads it from
  the cache after that, so it costs tens of cents at $4 / $20 per million tokens in and out ($0.20
  cached); a program from a PDF costs about a dollar.
- **The key stays on the phone**, encrypted with Android Keystore, and is never in a backup or
  Auto Backup. The app's only hosts are `api.anthropic.com` (from one client) and the update
  check's GitHub. Training data goes to Anthropic only during a check-in.
- **Spend** is capped by the monthly limit in the Anthropic Console. The app adds up each reply's
  usage and shows the month's cost in Settings; it has no cap of its own.
- **Testable without a key.** The client sits behind one interface. Tests use a fake that plays
  scripted turns, debug builds have a scripted coach that runs the whole wizard offline, and one
  live test runs only when `ANTHROPIC_API_KEY` is set.

This replaces ADR 0001 and ADR 0003's "no model calls from the app". ADR 0003's words-first coach
with no built-in rules stands: the check-in's tiles are a first answer, not rules.

## Considered options

- **Keep only the hand-off**: free, but the paste loop stays, and Claude can't ask its questions
  as taps.
- **API only, no hand-off**: one path, but no coach at all without a key or after the budget runs
  out.
- **A free chat screen**: the shortest step from the hand-off, but the owner wants a wizard with a
  very simple screen.
- **A server holding the key**: keeps the key off the phone, but adds a server to a local-first,
  single-user app.
