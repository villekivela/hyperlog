# Ask Claude without a key: tester pass (#74)

Run on the emulator (`hyperlog` AVD, headless, muted; the owner's phone holds their key, so it stayed
untouched), a fresh install, the starter program running, no Claude key, scripted coach off. Paste
success paths (a Change, a workout, Reply found) can't be driven there without a clipboard tool, so
they are covered by `AskClaudeAppTest`. The owner tries the real share sheet on the phone.

## Scenarios

| # | Scenario | Taps | Result |
| --- | --- | --- | --- |
| 1 | Elbow hurts on curls: Coach, Something hurts, finish the sentence, Ask Claude | 3 (+ typing) | The field opens focused with `Something hurts: ` and the keyboard up. The share sheet previews the prompt. |
| 2 | Back from the share sheet | 1 | Paste reply is the filled button, Ask Claude the text button. |
| 3 | Paste with nothing useful on the clipboard | 1 | "Not a reply Hyperlog can use / No Change, workout or program found…", no copy button. |
| 4 | Leave with Back | 1 | The tiles. The words are dropped. |

## Issues

- **Minor.** The prompt quotes the tile and then the prefilled sentence, so `Something hurts.` and
  `Something hurts: left elbow…` follow each other. It says the same twice; it costs Claude nothing and
  keeps the tile when the words are cleared (SPEC).
- **Minor.** The tiles' subtitle ("Claude asks what it needs") is the wizard's; without a key the app
  doesn't ask. Left, since the tiles are shared with the wizard.
- **Minor.** Back drops the words. A half-typed ask is lost on a mis-tap of Back.

## Works well

- Three taps from Today to the share sheet, with the sentence ready to finish.
- The filled button always names the next step.
- A bad paste says what is wrong in the same words as program import.

## Spec gaps

- The prompt carries no gym. A lifter at a hotel gym has to say so in the words.
