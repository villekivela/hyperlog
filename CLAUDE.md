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
- **Coaching runs offline or through the user's Claude app.** The app makes no model calls (ADR
  0001). The network is only for the in-app update check (`SPEC.md`, Distribution).
