# Pull one clean squat rep out of the trace as sagittal angles for the mannequin.
# usage: retarget.py <trace dir>   (reads out/squat.npz; writes out/squat-trace.json, out/squat-cleanup.png)
import json
import sys
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy.signal import savgol_filter

D = sys.argv[1]
z = np.load(f"{D}/out/squat.npz")
img, world, t, fps = z["img"], z["world"], z["t"], float(z["fps"])
k = 0  # the left side is nearer the camera (analyze.py)
SH, HIP, KN, AN, HEEL, TOE = 11, 23, 25, 27, 29, 31
im = img.copy(); im[..., 1] *= -1  # y up
fwd = np.sign(np.nanmedian(im[:, TOE, 0] - im[:, HEEL, 0]))  # +1 when the lifter faces +x
im[..., 0] *= fwd  # now +x is forward for the lifter

def sg(x, win_s=0.25, order=2):
    w = max(5, int(round(win_s * fps)) | 1)
    return savgol_filter(x, w, order, axis=0, mode="interp")

def lean_fwd(lo, hi):
    """Signed angle of lo→hi from straight up, positive when hi is forward of lo."""
    v = hi - lo
    return np.degrees(np.arctan2(v[..., 0], v[..., 1]))

raw = {"a": lean_fwd(im[:, AN], im[:, KN]),         # shin: knee forward of the ankle
       "b": -lean_fwd(im[:, KN], im[:, HIP]),       # thigh: hip behind the knee
       "c": lean_fwd(im[:, HIP], im[:, SH])}        # trunk: shoulders forward of the hips
ims = sg(im)
smo = {"a": lean_fwd(ims[:, AN], ims[:, KN]), "b": -lean_fwd(ims[:, KN], ims[:, HIP]), "c": lean_fwd(ims[:, HIP], ims[:, SH])}

# the reps: knee flexion = a + b; take the middle one, from the stillest frame at the top to the deepest frame
flex = smo["a"] + smo["b"]
lo, hi = np.percentile(flex, 3), np.percentile(flex, 97)
s = (flex - lo) / (hi - lo)
downs = [i for i in range(1, len(s)) if s[i - 1] < 0.15 <= s[i] and s[i:i + int(1.5 * fps)].max() > 0.85]  # full-depth reps only, not the walkout
mid = downs[len(downs) // 2]
start = mid
while start > 0 and s[start - 1] < s[start] and s[start] > 0.005:
    start -= 1
bottom = mid
while bottom + 1 < len(s) and s[bottom + 1] >= s[bottom]:
    bottom += 1
# the lifting half, for the clip's own tempo: from the deepest frame back to the stillest frame at the top
back = bottom
while back + 1 < len(s) and (s[back + 1] < s[back] or s[back] > 0.3):
    back += 1
top_before = start
while top_before > 0 and s[top_before - 1] < 0.08:
    top_before -= 1
# time near the bottom: within 5% of the deepest point
near = np.where(s[start:back + 1] > s[bottom] - 0.05)[0]
clip_timing = [round(float(t[start] - t[top_before]), 2), round(float(t[bottom] - t[start]), 2),
               round(float((near[-1] - near[0]) / fps), 2), round(float(t[back] - t[bottom]), 2)]
# the leg length in pixels, to put the slide in metres for an 0.9 m leg
leg = np.nanmedian(np.linalg.norm(im[:, HIP] - im[:, KN], axis=-1) + np.linalg.norm(im[:, KN] - im[:, AN], axis=-1))
m_per_px = 0.9 / leg
rep = slice(start, bottom + 1)
foot_raw = im[rep, [AN, HEEL, TOE]]
foot_slide = float(np.max(np.linalg.norm(foot_raw - np.median(foot_raw, axis=0), axis=-1)) * m_per_px)
foot_slide_smooth = float(np.max(np.linalg.norm(ims[rep][:, [AN, HEEL, TOE]] - np.median(ims[rep][:, [AN, HEEL, TOE]], axis=0), axis=-1)) * m_per_px)
knee_dev = float(np.std(np.linalg.norm(im[rep, KN] - ims[rep, KN], axis=-1)) * m_per_px)
jitter = {n: float(np.std(raw[n][rep] - smo[n][rep])) for n in "abc"}
jitter_all = {n: float(np.std(raw[n] - smo[n])) for n in "abc"}
# the frame-to-frame step in the smoothed signal against the raw one: what the eye sees as shake
step = {n: [float(np.percentile(np.abs(np.diff(raw[n][rep])), 95)), float(np.percentile(np.abs(np.diff(smo[n][rep])), 95))] for n in "abc"}

N = 31
tt = t[rep] - t[start]
u = np.linspace(0, tt[-1], N)
samples = {n: np.interp(u, tt, smo[n][rep]).round(2).tolist() for n in "abc"}
out = {
    "clip": "https://youtu.be/UGwmUJkTIx8", "frames": [int(start), int(bottom)], "fps": fps,
    "duration": float(tt[-1]), "samples": samples, "clip_timing": clip_timing,
    "diagnostics": {"jitter_deg": jitter, "jitter_deg_whole_clip": jitter_all, "step95_raw_vs_smoothed": step,
                    "foot_slide_m_raw": foot_slide, "foot_slide_m_smoothed": foot_slide_smooth, "knee_jitter_m": knee_dev},
}
json.dump(out, open(f"{D}/out/squat-trace.json", "w"), indent=1)
print(json.dumps({k: v for k, v in out.items() if k != "samples"}, indent=1))
print("a", samples["a"][0], "→", samples["a"][-1], "| b", samples["b"][0], "→", samples["b"][-1], "| c", samples["c"][0], "→", samples["c"][-1])

fig, ax = plt.subplots(1, 1, figsize=(9, 4))
tr = t[rep] - t[start]
for n, lab in zip("abc", ("shin lean a", "thigh from vertical b", "trunk lean c")):
    ax.plot(tr, raw[n][rep], lw=0.8, alpha=0.6, label=f"{lab}, raw")
    ax.plot(tr, smo[n][rep], lw=2, label=f"{lab}, Savitzky–Golay 0.25 s")
ax.set_xlabel("s from the top"); ax.set_ylabel("degrees"); ax.grid(alpha=0.3); ax.legend(fontsize=7, ncol=2)
ax.set_title("Squat, the traced lowering half: raw vs smoothed")
fig.tight_layout(); fig.savefig(f"{D}/out/squat-cleanup.png", dpi=90)
