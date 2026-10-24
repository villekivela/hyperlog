@AGENTS.md

# Hyperlog

A native Android rewrite (Kotlin, Jetpack Compose) of a strength-training app: run a
periodized program, log sessions, coach around real life.

- **`SPEC.md` is the source of truth.** Build from it, and write every new product decision into it
  in the same change.
- **Read `GLOSSARY.md` before naming** anything in code, tickets or commits. Read `docs/adr/` before
  touching coaching or per-machine history.
- **Work comes from GitHub issues #1–#22**, a waterfall where each ticket is blocked by the one
  before it. Take the lowest open ticket, and close it with its acceptance criteria checked.
- **Building, the phone, the prototype and the legacy app:** read `docs/development.md` before the
  first build, adb command or legacy lookup.
- **UI work goes through the designer and tester agents** (`docs/design/designer-brief.md`, then
  `docs/design/tester-brief.md`). The owner judges whole passes, so bring them taste questions only.
- **The starter program** (low-volume high-intensity full body, a bench goal and a hand-pain
  coaching example) is in `docs/programs/full-body-3-day.md`.
- **Coaching is a check-in with Claude** (ADR 0005): through the API with the user's Claude key, or
  handed off to the Claude app without one. The network is only for `api.anthropic.com` and the
  in-app update check. Everything coach-related must be testable without a key: use the fake
  `CoachModel` or the scripted coach, never a real key in tests.
