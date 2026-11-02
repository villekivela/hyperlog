"""Writes the side-by-side page: python3 -I report.py <out dir> <exercises.json> <starter program.json>.

Reads <out>/before/*.run.json and <out>/after/*.run.json (from run.sh) and prints one
self-contained HTML page to stdout. Each reply is scored by checks.py against the default targets.
"""
import html
import json
import sys
from pathlib import Path

import checks

SCENARIOS = [
    ("new-program", "New program", "Three training days of 45 to 60 minutes, at Harbour."),
    ("new-program-chest", "New program, chest focus", "The same, and bring up the chest."),
    ("hurts-elbow", "Something hurts", "Left elbow, on pressing."),
    ("workout-45", "A 45-minute workout", "Build a workout for today, 45 minutes at Harbour."),
]
TARGET_ORDER = ["Proposal", "Sets per muscle", "Frequency", "Reps in reserve", "Failure", "Rep ranges", "Time", "Focus", "Swaps the painful lift", "Keeps the rest of the session"]
RULES = {
    "Sets per muscle": "8–14 work sets a week for each muscle trained as a main mover (primary 1, secondary 0.5)",
    "Frequency": "each muscle group on 2–3 days a week",
    "Reps in reserve": "last set 0–1 RIR, earlier sets 1–2",
    "Failure": "0 RIR only on isolation exercises and machine, cable and Smith compounds",
    "Rep ranges": "compounds 5–10 reps, isolation 8–15",
    "Time": "within the minutes asked (45 s a set + rest, 2 min warm-up per exercise)",
    "Focus": "the asked group is written as the focus; its muscles get 14–21 sets a week, every other muscle 4–8",
    "Swaps the painful lift": "at least one pressing slot is swapped or left out",
    "Keeps the rest of the session": "only slots that load the elbow (presses, triceps, curls) are changed",
    "Proposal": "a program, workout or Change",
}

CSS = """
:root{--bg:#fafaf7;--fg:#1c1c1a;--mute:#6b6b66;--line:#dcdcd4;--card:#fff;--met:#1d7a46;--miss:#b3261e;--na:#6b6b66;--metbg:#e3f3ea;--missbg:#fbe5e3;--nabg:#ececE6}
@media (prefers-color-scheme:dark){:root{--bg:#161614;--fg:#ecece6;--mute:#a0a09a;--line:#34342f;--card:#1e1e1b;--met:#6fd09a;--miss:#ff9a92;--na:#a0a09a;--metbg:#173326;--missbg:#3b1d1a;--nabg:#262622}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,sans-serif}
main{max-width:1180px;margin:0 auto;padding:24px 16px 64px}h1{font-size:26px;margin:0 0 4px}h2{font-size:20px;margin:40px 0 4px}h3{font-size:15px;margin:0 0 8px}
.sub{color:var(--mute);margin:0 0 16px}.cols{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:820px){.cols{grid-template-columns:1fr}}
.col{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px;min-width:0}.tag{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:var(--mute)}
.chip{display:inline-block;font-size:12px;font-weight:600;border-radius:99px;padding:1px 9px;margin-right:6px}.met{color:var(--met);background:var(--metbg)}.missed{color:var(--miss);background:var(--missbg)}.na{color:var(--na);background:var(--nabg)}
table{border-collapse:collapse;width:100%;font-size:13px}td,th{border-top:1px solid var(--line);padding:4px 6px;text-align:left;vertical-align:top}th{font-weight:600;color:var(--mute)}
.checks td:first-child{white-space:nowrap}.detail{color:var(--mute);font-size:12.5px}.rule{color:var(--mute);font-size:12px}
.qa{font-size:13px;margin:0 0 10px;padding-left:18px}.words{white-space:pre-wrap;font-size:13.5px}.note{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px;margin:12px 0}
details{margin-top:8px}summary{cursor:pointer;color:var(--mute);font-size:13px}code{font-size:12.5px}
"""


def esc(x):
    return html.escape(str(x))


def load(path):
    p = Path(path)
    return json.loads(p.read_text()) if p.exists() else None


def reps(line):
    return str(line["reps"])


def set_text(sets):
    return ", ".join(f"{s.get('count', 1)}×{reps(s)}" + (f" @{s['rir']}" if s.get("rir") is not None else "") + (f" +{s['technique']['kind']}" if s.get("technique") else "") for s in sets)


def day_table(day, names):
    rows = "".join(
        f"<tr><td>{esc(names.get(slot['exercise'], slot['exercise']))}</td><td>{esc(set_text(slot['sets']))}</td></tr>"
        for group in day["groups"] for slot in group["slots"]
    )
    return f"<table><tr><th>{esc(day.get('name', 'Workout'))}</th><th>sets × reps @ RIR</th></tr>{rows}</table>"


def proposal_html(run, names):
    found = checks.proposal(run)
    if found is None:
        text = next((r["text"] for r in reversed(run["replies"]) if r["text"]), "")
        return f"<h3>Claude ended in words</h3><div class='words'>{esc(text)}</div>"
    kind, data = found
    if kind == "propose_program":
        p = data["program"]
        out = f"<h3>{esc(p.get('title', 'Program'))}</h3><div class='detail'>focus: {esc(', '.join(p.get('focus') or []) or 'none')}"
        if p.get("targets"):
            out += f" · targets: {esc(json.dumps(p['targets']))}"
        out += f" · {len(p['blocks'])} blocks</div>"
        out += f"<div class='detail'>First block, {esc(p['blocks'][0]['name'])}:</div>"
        return out + "".join(day_table(d, names) for d in p["blocks"][0]["days"])
    if kind == "propose_workout":
        return f"<h3>{esc(data.get('name', 'Workout'))}</h3><div class='detail'>{esc(data.get('summary', ''))}</div>" + day_table(data, names)
    ops = "".join(f"<li><code>{esc(op['op'])}</code> {esc(op.get('slot', ''))} {('→ ' + esc(names.get(op['exercise'], op['exercise']))) if 'exercise' in op else ''} <span class='detail'>{esc(op.get('reason', ''))}</span></li>" for op in data.get("operations", []))
    return f"<h3>Change: {esc(data.get('summary', ''))}</h3><div class='detail'>scope: {esc(json.dumps(data.get('scope')))}</div><ul>{ops}</ul>"


def column(side, run, names, library, plan):
    if run is None:
        return f"<div class='col'><div class='tag'>{side}</div><p>No run.</p></div>"
    qa = "".join(f"<li>{esc(a['question'])} <b>{esc(a['picked'])}</b></li>" for a in run["asked"])
    status = f"<div class='detail'>ended: {esc(run['ended'])}" + (f" · problems: {esc('; '.join(run['problems']))}" if run.get("problems") else "") + (f" · failure: {esc(run['failure'])}" if run.get("failure") else "") + "</div>"
    out = f"<div class='col'><div class='tag'>{side}</div>{status}<details><summary>{len(run['asked'])} questions asked</summary><ol class='qa'>{qa}</ol></details>{proposal_html(run, names)}"
    out += "<table class='checks'><tr><th>Target</th><th>Result</th></tr>"
    for r in sorted(checks.score(run, library, plan), key=lambda r: TARGET_ORDER.index(r["target"])):
        out += f"<tr><td>{esc(r['target'])}</td><td><span class='chip {r['verdict']}'>{r['verdict']}</span><span class='detail'>{esc(r['detail'])}</span></td></tr>"
    return out + "</table></div>"


def main():
    out_dir, library_path, plan_path = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    raw = json.loads(Path(library_path).read_text())["exercises"]
    library = {e["id"]: e for e in raw}
    names = {e["id"]: e["name"] for e in raw}
    plan = json.loads(Path(plan_path).read_text())
    body = ""
    for key, title, line in SCENARIOS:
        before, after = load(out_dir / "before" / f"{key}.run.json"), load(out_dir / "after" / f"{key}.run.json")
        body += f"<h2>{esc(title)}</h2><p class='sub'>{esc(line)}</p><div class='cols'>{column('Before: main at 2a431ef, no training style', before, names, library, plan)}{column('After: main with the training style', after, names, library, plan)}</div>"
    print(f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Training style replies</title><style>{CSS}</style></head><body><main>
<h1>Check-in replies, before and after the training style</h1>
<p class="sub">Four scenarios against the Claude API (claude-opus-5-5, the app's own client and check-in code), once with the context before the training style and once with it.</p>
<div class="note"><b>How to read this.</b> Each side is one sample of one run: a reply varies from run to run, so read a miss as a hint, not a verdict. Both sides are scored against the <i>default</i> training targets, which the "before" context never told Claude. Counts use the first block's base prescription (week overrides are ignored). Compound or isolation comes from the exercise's movement pattern, and the time is an estimate. The fixed data is the starter program with six logged sessions at Harbour, a full commercial gym, and Itis, a dumbbell-only hotel gym; they stand in for the owner's own gyms, which live in the phone's database.</div>
{body}
</main></body></html>""")


if __name__ == "__main__":
    main()
