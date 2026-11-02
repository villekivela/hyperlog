# Build trace/traced.html: the prototype page plus a Traced squat, from head.part, script.part and out/squat-trace.json.
# The originals are read, never written.
import json
import sys

S = sys.argv[1]  # the scratchpad
T = f"{S}/trace"
head = open(f"{S}/head.part").read()
script = open(f"{S}/script.part").read()
trace = json.load(open(f"{T}/out/squat-trace.json"))


def swap(text, old, new, count=1):
    assert text.count(old) == count, (old[:60], text.count(old))
    return text.replace(old, new)


# ---- page ----
head = swap(head, "<title>Exercise Figures</title>", "<title>Traced Squat</title>")
head = swap(head, "/* PROTOTYPE, throwaway. Question: how could a low-detail 3D figure look in Hyperlog's exercise sheet?",
            "/* PROTOTYPE, throwaway. Traced copy: can the mannequin take its motion from a technique clip?\n"
            "   Built by trace/bin/build_traced.py from head.part and script.part.\n"
            "   Original question: how could a low-detail 3D figure look in Hyperlog's exercise sheet?")
head = swap(head, '<p class="proto-note"><b>Prototype.</b> ',
            '<p class="proto-note"><b>Prototype: traced squat.</b> Motion source switches the Barbell Back Squat between the '
            'authored pose and one rep traced from its technique clip with MediaPipe (shin, thigh and trunk angles, '
            'smoothed), retargeted onto the mannequin with the feet, hands and bar-over-mid-foot kept by IK. '
            'Other exercises stay authored. ')
head = swap(head, '''      <button class="seg" id="body-detailed" data-body="detailed">Superhero</button>
      <button class="seg" id="body-mannequiny" data-body="mannequiny">Mannequiny</button>
''', "")
head = swap(head, '''    <div class="row" role="group" aria-label="Muscles">''',
            '''    <div class="row" role="group" aria-label="Motion source"><span>Source</span>
      <button class="seg" id="src-authored" data-src="authored">Authored</button>
      <button class="seg" id="src-traced" data-src="traced">Traced</button>
      <button class="seg" id="src-clip" data-src="clip">Traced, clip tempo</button>
    </div>
    <p class="note" id="trace-info" style="font-size:12px;color:var(--on-surface-variant)"></p>
    <div class="row" role="group" aria-label="Muscles">''')

# ---- script ----
script = swap(script, "const FIGURES = { mannequin: 'figure-mannequin.glb', detailed: 'figure.glb', mannequiny: 'figure-mannequiny.glb' };",
              "const FIGURES = { mannequin: 'figure-mannequin.glb' };")
TRACE_JS = f"""
// ---------- the traced squat: one rep of {trace['clip']} (frames {trace['frames'][0]}–{trace['frames'][1]}) ----------
// a: shin lean forward, b: thigh from vertical (hip behind the knee), c: trunk lean (hip→shoulder line), degrees,
// sampled evenly in time over the lowering half, so the clip's speed profile is in the samples
const TRACE = {json.dumps({'a': trace['samples']['a'], 'b': trace['samples']['b'], 'c': trace['samples']['c']})};
const TRACE_TIMING = [{trace['clip_timing'][0]}, {trace['clip_timing'][1]}, 0.05, {trace['clip_timing'][3]}];
const traceAt = (n, d) => {{ const s = TRACE[n], x = Math.max(0, Math.min(1, d)) * (s.length - 1), i = Math.min(s.length - 2, Math.floor(x)); return lerp(s[i], s[i + 1], x - i); }};
let shOff = null; // the hip→shoulder line leans this far forward of the spine when the spine is upright
function shoulderOffset() {{
  if (shOff !== null || !rig.ready) return shOff || 0;
  poseRig(Object.assign(poseSquat(0), {{ u: V(0, 1, 0) }}));
  const mid = (a, b) => wpos(rig.bones[a]).add(wpos(rig.bones[b])).multiplyScalar(0.5);
  const v = mid('upperarm_l', 'upperarm_r').sub(mid('thigh_l', 'thigh_r'));
  shOff = Math.atan2(v.z, v.y);
  return shOff;
}}
// the trace sets the joint angles; the whole body then turns about the ankle (knee and hip angles unchanged)
// until the bar is back over mid-foot, the contact the mannequin's own proportions would otherwise lose
function poseSquatTraced(d) {{
  const a0 = rad(traceAt('a', d)), b0 = rad(traceAt('b', d)), c0 = rad(traceAt('c', d)) - shoulderOffset(), k = 0.995;
  const build = dl => {{
    const a = a0 + dl, b = b0 - dl, c = c0 + dl;
    const kz = L.shin * k * Math.sin(a), ky = L.ankleY + L.shin * k * Math.cos(a);
    const hipC = V(0, ky + L.thigh * k * Math.cos(b), kz - L.thigh * k * Math.sin(b));
    const u = trunkUp(c), f = fwdOf(u);
    return {{ hipC, u, f, bar: hipC.clone().addScaledVector(u, L.trunk + 0.06).addScaledVector(f, -0.07) }};
  }};
  let lo = rad(-30), hi = rad(30);
  for (let i = 0; i < 32; i++) {{ const m = (lo + hi) / 2; if (build(m).bar.z > 0.04) hi = m; else lo = m; }}
  const dl = (lo + hi) / 2, {{ hipC, u, f, bar }} = build(dl);
  return {{
    hipC, u, bar, headDir: u.clone().add(V(0, 0.5, 0.1)).normalize(),
    ankles: [V(-0.17, L.ankleY, 0), V(0.17, L.ankleY, 0)], toeOut: 18, kneePole: s => V(s * 0.45, 0, 1).normalize(),
    kit: ['barbell'], grip: 0.4, armPole: s => u.clone().multiplyScalar(-1).addScaledVector(f, -0.8).addScaledVector(X, s * 0.3).normalize(),
    correction: dl * 180 / Math.PI, traced: {{ a: traceAt('a', d), b: traceAt('b', d), c: traceAt('c', d) }},
  }};
}}
"""
script = swap(script, "function poseBench(d) {", TRACE_JS + "function poseBench(d) {")
script = swap(script, "const state = { vi: 0, ex: 'squat', body: 'mannequin', motion: reduced ? 'still' : 'loop', muscles: true, az: 62, el: 8, t0: performance.now(), variant: STYLE.line };",
              """const state = { vi: 0, ex: 'squat', body: 'mannequin', motion: reduced ? 'still' : 'loop', muscles: true, az: 62, el: 8, t0: performance.now(), variant: STYLE.line, src: 'traced', stillD: 1 };
// ?src=authored|traced|clip&d=0..1&az=&el=&muscles=off for headless renders: d fixes the phase
{ const q = new URLSearchParams(location.search);
  if (q.has('src')) state.src = q.get('src');
  if (q.has('d')) { state.motion = 'still'; state.stillD = +q.get('d'); }
  if (q.has('az')) state.az = +q.get('az');
  if (q.has('el')) state.el = +q.get('el');
  if (q.get('muscles') === 'off') state.muscles = false;
  if (q.has('shot')) document.querySelector('.proto-note').style.display = 'none'; }""")
script = swap(script, "  if (state.motion === 'still') return 1;", "  if (state.motion === 'still') return state.stillD;")
script = swap(script, """  const [h, down, hb, up] = ex.timing, total = h + down + hb + up, m = t % total;
  if (m < h) return 0;
  if (m < h + down) return ease((m - h) / down);
  if (m < h + down + hb) return 1;
  return 1 - ease((m - h - down - hb) / up);""",
              """  // a traced rep already holds the clip's speed, so it plays linearly; the authored one eases
  const traced = state.ex === 'squat' && state.src !== 'authored', sh = traced ? (x => x) : ease;
  const [h, down, hb, up] = traced && state.src === 'clip' ? TRACE_TIMING : ex.timing, total = h + down + hb + up, m = t % total;
  if (m < h) return 0;
  if (m < h + down) return sh((m - h) / down);
  if (m < h + down + hb) return 1;
  return 1 - sh((m - h - down - hb) / up);""")
script = swap(script, "  const P = POSES[state.ex](phase(now));",
              """  const P = state.ex === 'squat' && state.src !== 'authored' ? poseSquatTraced(phase(now)) : POSES[state.ex](phase(now));
  const info = document.getElementById('trace-info');
  info.textContent = P.traced
    ? `Traced from ${'""" + trace['clip'] + """'} · shin ${P.traced.a.toFixed(0)}°, thigh ${P.traced.b.toFixed(0)}°, trunk ${P.traced.c.toFixed(0)}° · turned ${P.correction >= 0 ? '+' : ''}${P.correction.toFixed(1)}° about the ankle to keep the bar over mid-foot`
    : (state.ex === 'squat' ? 'Authored pose.' : 'Only the squat is traced; this exercise is authored.');""")
script = swap(script, "  document.querySelectorAll('[data-mo]').forEach(b => b.setAttribute('aria-pressed', b.dataset.mo === state.motion));",
              """  document.querySelectorAll('[data-mo]').forEach(b => b.setAttribute('aria-pressed', b.dataset.mo === state.motion));
  document.querySelectorAll('[data-src]').forEach(b => b.setAttribute('aria-pressed', b.dataset.src === state.src));""")
script = swap(script, "document.querySelectorAll('[data-mo]').forEach(b => b.addEventListener('click', () => { state.motion = b.dataset.mo; state.t0 = performance.now(); syncUi(); }));",
              """document.querySelectorAll('[data-mo]').forEach(b => b.addEventListener('click', () => { state.motion = b.dataset.mo; state.t0 = performance.now(); syncUi(); }));
document.querySelectorAll('[data-src]').forEach(b => b.addEventListener('click', () => { state.src = b.dataset.src; state.t0 = performance.now(); syncUi(); }));""")
open(f"{T}/traced.src.html", "w").write(head + script)

# the self-contained copy: the mannequin inlined, so the page opens from anywhere
import base64
glb = base64.b64encode(open(f"{S}/figure-mannequin.glb", "rb").read()).decode()
inline = (head + script).replace("const FIGURES = { mannequin: 'figure-mannequin.glb' };",
                                 f"const FIGURES = {{ mannequin: 'data:model/gltf-binary;base64,{glb}' }};")
open(f"{T}/traced.html", "w").write(inline)
print("ok", len(inline) // 1024, "KB")
