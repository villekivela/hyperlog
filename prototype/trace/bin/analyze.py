# Measure each traced clip and compare it with the prototype's poses.
# usage: analyze.py <trace dir>   (reads out/<ex>.npz, out/proto.json, clips/<ex>.mp4; writes out/, curves-*, overlay-*, out/summary.json)
import json
import sys
import cv2
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy.signal import savgol_filter

D = sys.argv[1]
proto = json.load(open(f"{D}/out/proto.json"))

# MediaPipe landmark indices, (left, right)
SH, EL, WR, HIP, KN, AN, HEEL, TOE = (11, 12), (13, 14), (15, 16), (23, 24), (25, 26), (27, 28), (29, 30), (31, 32)
NOSE = 0

# which way the prototype's d runs, and the signal that follows it (1 at d = 1)
DRIVE = {
    "squat": ("knee", +1), "bench": ("elbow", +1), "rdl": ("hip", +1), "pulldown": ("elbow", +1),
    "pressdown": ("elbow", -1), "legpress": ("knee", +1), "chestpress": ("elbow", +1),
}
# which point the wrist (or ankle) path is measured from, and in what length
PATH = {
    "squat": ("shoulder", "midfoot", "leg"), "rdl": ("wrist", "midfoot", "leg"), "bench": ("wrist", "shoulder", "arm"),
    "pulldown": ("wrist", "shoulder", "arm"), "pressdown": ("wrist", "shoulder", "arm"),
    "chestpress": ("wrist", "shoulder", "arm"), "legpress": ("ankle", "hip", "leg"),
}
NAMES = {"squat": "Barbell Back Squat", "bench": "Barbell Bench Press", "rdl": "Barbell RDL", "pulldown": "Lat Pulldown",
         "pressdown": "Triceps Pressdown", "legpress": "Leg Press", "chestpress": "Machine Chest Press"}
ANGLES = ["knee", "hip", "shin", "trunk", "shoulder", "elbow"]


def ang3(a, b, c):
    """Inner angle at b, degrees."""
    u, v = a - b, c - b
    cos = np.sum(u * v, -1) / (np.linalg.norm(u, axis=-1) * np.linalg.norm(v, axis=-1) + 1e-9)
    return np.degrees(np.arccos(np.clip(cos, -1, 1)))


def lean(lo, hi, up):
    """Angle of lo→hi from the up direction, degrees."""
    v = hi - lo
    cos = np.sum(v * up, -1) / (np.linalg.norm(v, axis=-1) + 1e-9)
    return np.degrees(np.arccos(np.clip(cos, -1, 1)))


def angles(p, up):
    """Joint angles from points: flexion for knee, hip and elbow (0 = straight), leans from vertical,
    shoulder = angle between the trunk (down) and the upper arm (0 = arm along the side)."""
    return {
        "knee": 180 - ang3(p["hip"], p["knee"], p["ankle"]),
        "hip": 180 - ang3(p["shoulder"], p["hip"], p["knee"]),
        "shin": lean(p["ankle"], p["knee"], up),
        "trunk": lean(p["hip"], p["shoulder"], up),
        "shoulder": ang3(p["hip"], p["shoulder"], p["elbow"]),
        "elbow": 180 - ang3(p["shoulder"], p["elbow"], p["wrist"]),
    }


def segment(s, t, lo=0.15, hi=0.85):
    """Split a 0..1 signal into reps: [(top-hold start, leave top, reach bottom, leave bottom, back at top)] in seconds,
    plus frame indices. A rep runs from leaving the top band to coming back into it."""
    state, marks, reps = None, {}, []
    for i, v in enumerate(s):
        if state is None:
            state = "top" if v < lo else ("bottom" if v > hi else None)
            if state == "top":
                marks = {"top_in": i}
            continue
        if state == "top" and v >= lo:
            marks["top_out"] = i; state = "down"
        elif state == "down" and v > hi:
            marks["bot_in"] = i; state = "bottom"
        elif state == "down" and v < lo:
            state = "top"  # a false start
        elif state == "bottom" and v <= hi:
            marks["bot_out"] = i; state = "up"
        elif state == "up" and v < lo:
            marks["back"] = i
            if all(k in marks for k in ("top_out", "bot_in", "bot_out")):
                reps.append(dict(marks))
            marks = {"top_in": i}; state = "top"
        elif state == "up" and v > hi:
            state = "bottom"
    out = []
    for k, r in enumerate(reps):
        nxt = reps[k + 1]["top_out"] if k + 1 < len(reps) else None
        out.append({
            **r,
            "top_hold_before": (t[r["top_out"]] - t[r["top_in"]]) if k > 0 else None,
            "down": t[r["bot_in"]] - t[r["top_out"]], "bottom_hold": t[r["bot_out"]] - t[r["bot_in"]],
            "up": t[r["back"]] - t[r["bot_out"]], "top_hold_after": (t[nxt] - t[r["back"]]) if nxt is not None else None,
        })
    return out


def proto_points(j, side="l"):
    q = lambda n: np.array(j[n], float)
    mid = (q("foot_" + side) + q("ball_" + side)) / 2  # ankle to ball, the prototype's foot
    return {"shoulder": q("upperarm_" + side), "elbow": q("lowerarm_" + side), "wrist": q("hand_" + side),
            "hip": q("thigh_" + side), "knee": q("calf_" + side), "ankle": q("foot_" + side), "midfoot": mid,
            "toe": q("ball_" + side)}


def proto_phase(timing, d_curve_t):
    """The prototype's phase(t) over one loop, as in script.part."""
    h, down, hb, up = timing
    ease = lambda x: x * x * (3 - 2 * x)
    total = h + down + hb + up
    out = []
    for m in d_curve_t % total:
        if m < h: out.append(0.0)
        elif m < h + down: out.append(ease((m - h) / down))
        elif m < h + down + hb: out.append(1.0)
        else: out.append(1 - ease((m - h - down - hb) / up))
    return np.array(out)


def proto_measure(ex):
    poses = proto["poses"][ex]
    up = np.array([0, 1, 0.0])
    ds = np.linspace(0, 1, len(poses))
    curves = {a: [] for a in ANGLES}
    paths = []
    for j in poses:
        p = proto_points(j)
        # the side view: drop x (the figure faces +z)
        p2 = {k: np.array([0, v[1], v[2]]) for k, v in p.items()}
        a = angles(p, up)
        a2 = angles(p2, up)
        for k in ANGLES:
            curves[k].append(float(a2[k] if k in ("shin", "trunk") else a[k]))
        what, ref, unit = PATH[ex]
        pt = p[what]
        fwd = np.sign(p["shoulder"][2] - p["hip"][2]) if ex == "bench" else 1.0
        L = proto["L"]
        length = (L["thigh"] + L["shin"]) if unit == "leg" else (L["up"] + L["fore"])
        paths.append([fwd * (pt[2] - p[ref][2]) / length, (pt[1] - p[ref][1]) / length])
    # tempo, segmented the same way as a clip
    tt = np.arange(0, 3 * sum(proto["timing"][ex]), 0.01)
    reps = segment(proto_phase(proto["timing"][ex], tt), tt)
    tempo = {k: float(np.median([r[k] for r in reps if r[k] is not None])) for k in ("top_hold_before", "down", "bottom_hold", "up")}
    return ds, curves, np.array(paths), tempo


def nearest_side(world):
    """The side nearer the camera has the smaller MediaPipe world z."""
    zl = np.nanmean(world[:, [SH[0], HIP[0], KN[0], EL[0]], 2])
    zr = np.nanmean(world[:, [SH[1], HIP[1], KN[1], EL[1]], 2])
    return 0 if zl < zr else 1


def fill(x):
    """Interpolate frames where the pose was lost."""
    x = x.copy(); flat = x.reshape(len(x), -1); idx = np.arange(len(x))
    for c in range(flat.shape[1]):
        ok = np.isfinite(flat[:, c])
        if ok.any() and not ok.all():
            flat[:, c] = np.interp(idx, idx[ok], flat[ok, c])
    return flat.reshape(x.shape)


def smooth(x, fps, win_s=0.25):
    w = max(5, int(round(win_s * fps)) | 1)
    return savgol_filter(fill(x), w, 2, axis=0, mode="interp")


def measure(ex):
    z = np.load(f"{D}/out/{ex}.npz")
    img, world, vis, t, fps = z["img"], z["world"], z["vis"], z["t"], float(z["fps"])
    side = nearest_side(world)
    k = side
    # image coordinates with y up
    im = img.copy(); im[..., 1] *= -1
    ims, wos = smooth(im, fps), smooth(world, fps)
    wos_up = wos.copy(); wos_up[..., 1] *= -1  # MediaPipe world y points down
    pick = lambda arr, idx: arr[:, idx[k]]
    def points(arr):
        p = {"shoulder": pick(arr, SH), "elbow": pick(arr, EL), "wrist": pick(arr, WR), "hip": pick(arr, HIP),
             "knee": pick(arr, KN), "ankle": pick(arr, AN), "heel": pick(arr, HEEL), "toe": pick(arr, TOE)}
        p["midfoot"] = (p["heel"] + p["toe"]) / 2
        return p
    P2, P3 = points(ims), points(wos_up)
    P2raw = points(im)
    up2 = np.array([0, 1.0])
    a2 = angles(P2, up2)
    a3 = angles(P3, np.array([0, 1.0, 0]))
    a2raw = angles(P2raw, up2)
    # joint flexions from 3D world landmarks (less view-dependent), leans from the image (needs a side view)
    A = {n: (a2[n] if n in ("shin", "trunk") else a3[n]) for n in ANGLES}
    jitter = {n: float(np.nanstd(a2raw[n] - a2[n])) for n in ANGLES}
    disagree = {n: float(np.nanmedian(np.abs(a2[n] - a3[n]))) for n in ("knee", "hip", "elbow", "shoulder")}
    # view: shoulder width over trunk length in the image; ~0 side on, ~0.6+ facing the camera
    shw = np.linalg.norm(ims[:, SH[0]] - ims[:, SH[1]], axis=-1)
    trunk = np.linalg.norm(P2["shoulder"] - P2["hip"], axis=-1)
    sideness = float(np.nanmedian(shw / trunk))
    used = [SH[k], EL[k], WR[k], HIP[k], KN[k], AN[k]]
    visibility = float(np.nanmean(vis[:, used]))

    name, sign = DRIVE[ex]
    sig = sign * A[name]
    lo, hi = np.nanpercentile(sig, 3), np.nanpercentile(sig, 97)
    s = np.clip((sig - lo) / (hi - lo), -0.2, 1.2)
    reps = segment(s, t)

    # the wrist (or ankle) path, forward of the reference point, in limb lengths
    what, ref, unit = PATH[ex]
    if ex == "bench":
        fwd = np.sign(np.nanmedian(P2["shoulder"][:, 0] - P2["hip"][:, 0]))  # toward the head
    elif ex == "legpress":
        fwd = np.sign(np.nanmedian(P2["knee"][:, 0] - P2["hip"][:, 0]))
    else:
        fwd = np.sign(np.nanmedian(P2["toe"][:, 0] - P2["heel"][:, 0]))
    seg = lambda a, b: np.nanmedian(np.linalg.norm(P2[a] - P2[b], axis=-1))
    length = (seg("hip", "knee") + seg("knee", "ankle")) if unit == "leg" else (seg("shoulder", "elbow") + seg("elbow", "wrist"))
    pt = P2[what]
    path = np.stack([fwd * (pt[:, 0] - P2[ref][:, 0]) / length, (pt[:, 1] - P2[ref][:, 1]) / length], -1)

    top_frames = [i for r in reps for i in range(r["top_in"], r["top_out"])]
    bot_frames = [i for r in reps for i in range(r["bot_in"], r["bot_out"] + 1)]
    per_rep = lambda frames, n: float(np.nanmedian(A[n][frames])) if frames else float("nan")
    real = {
        "side": "left" if side == 0 else "right", "sideness": sideness, "visibility": visibility,
        "reps": len(reps), "jitter": jitter, "disagree_2d_3d": disagree,
        "top": {n: per_rep(top_frames, n) for n in ANGLES},
        "top2d": {n: float(np.nanmedian(a2[n][top_frames])) if top_frames else None for n in ANGLES},
        "bottom2d": {n: float(np.nanmedian(a2[n][bot_frames])) if bot_frames else None for n in ANGLES}, "bottom": {n: per_rep(bot_frames, n) for n in ANGLES},
        "tempo": {kk: float(np.median([r[kk] for r in reps if r[kk] is not None])) if any(r[kk] is not None for r in reps) else None
                  for kk in ("top_hold_before", "down", "bottom_hold", "up")},
        "tempo_reps": [{kk: (round(r[kk], 2) if r[kk] is not None else None) for kk in ("down", "bottom_hold", "up", "top_hold_after")} for r in reps],
        "path_top": path[top_frames].mean(0).tolist() if top_frames else None,
        "path_bottom": path[bot_frames].mean(0).tolist() if bot_frames else None,
        "path_fwd_range": [float(np.nanmin(path[top_frames + bot_frames, 0])), float(np.nanmax(path[top_frames + bot_frames, 0]))] if reps else None,
    }
    ds, pc, ppath, ptempo = proto_measure(ex)
    pro = {"top": {n: pc[n][0] for n in ANGLES}, "bottom": {n: pc[n][-1] for n in ANGLES}, "tempo": ptempo,
           "path_top": ppath[0].tolist(), "path_bottom": ppath[-1].tolist()}

    # ---- curves ----
    fig, axs = plt.subplots(3, 1, figsize=(11, 11), gridspec_kw={"height_ratios": [3, 2.2, 2.2]})
    ax = axs[0]
    for n in ANGLES:
        ax.plot(t, A[n], label=n, lw=1.3)
    for r in reps:
        ax.axvspan(t[r["top_out"]], t[r["bot_in"]], color="tab:blue", alpha=0.07)
        ax.axvspan(t[r["bot_in"]], t[r["bot_out"]], color="tab:red", alpha=0.12)
        ax.axvspan(t[r["bot_out"]], t[r["back"]], color="tab:green", alpha=0.07)
    ax.set_title(f"{NAMES[ex]}: traced angles ({real['side']} side; blue lowering-half d 0→1, red at d=1, green back)")
    ax.set_xlabel("s"); ax.set_ylabel("degrees"); ax.legend(ncol=6, fontsize=8, loc="upper right"); ax.grid(alpha=0.3)
    # one rep on a 0..1 progress axis (d 0→1 half), each rep vs the prototype
    ax = axs[1]
    colors = dict(zip(ANGLES, plt.rcParams["axes.prop_cycle"].by_key()["color"]))
    for n in ANGLES:
        for r in reps:
            idx = np.arange(r["top_out"], r["bot_in"] + 1)
            if len(idx) < 3: continue
            ax.plot(np.linspace(0, 1, len(idx)), A[n][idx], color=colors[n], alpha=0.25, lw=1)
        ax.plot(ds, pc[n], color=colors[n], lw=2.4, ls="--", label=f"{n} (prototype, dashed)")
    ax.set_title("d 0→1 half of each rep (thin, traced) vs prototype (dashed), on progress through the half")
    ax.set_xlabel("progress 0→1"); ax.set_ylabel("degrees"); ax.legend(ncol=3, fontsize=7); ax.grid(alpha=0.3)
    ax = axs[2]
    for r in reps:
        idx = np.arange(r["top_in"], r["back"] + 1)
        ax.plot(path[idx, 0], path[idx, 1], color="tab:gray", alpha=0.5, lw=1)
    ax.plot(ppath[:, 0], ppath[:, 1], "k--", lw=2.4, label="prototype")
    ax.set_aspect("equal"); ax.grid(alpha=0.3); ax.legend(fontsize=8)
    ax.set_title(f"{what} path from the {ref}, in {unit} lengths (x forward, y up)")
    fig.tight_layout(); fig.savefig(f"{D}/curves-{ex}.png", dpi=90); plt.close(fig)

    # ---- overlay: the median rep's top and bottom frames with the skeleton ----
    if reps:
        mid = reps[len(reps) // 2]
        frames = [(mid["top_in"] + mid["top_out"]) // 2, (mid["bot_in"] + mid["bot_out"]) // 2]
        cap = cv2.VideoCapture(f"{D}/clips/{ex}.mp4")
        tiles = []
        bones = [(SH, EL), (EL, WR), (SH, HIP), (HIP, KN), (KN, AN), (AN, HEEL), (HEEL, TOE), (AN, TOE)]
        for fi, label in zip(frames, ("top (d=0)", "d=1")):
            cap.set(cv2.CAP_PROP_POS_FRAMES, fi)
            ok, fr = cap.read()
            for a, b in bones:
                for kk, col in ((1 - k, (160, 160, 160)), (k, (0, 200, 255))):
                    pa, pb = img[fi, a[kk]], img[fi, b[kk]]
                    cv2.line(fr, tuple(int(v) for v in pa), tuple(int(v) for v in pb), col, 3, cv2.LINE_AA)
            for j in (SH[k], EL[k], WR[k], HIP[k], KN[k], AN[k]):
                cv2.circle(fr, tuple(int(v) for v in img[fi, j]), 6, (0, 80, 255), -1)
            xs, ys = img[fi, :, 0], img[fi, :, 1]
            pad = 60
            x0, x1 = int(max(0, np.nanmin(xs) - pad)), int(min(fr.shape[1], np.nanmax(xs) + pad))
            y0, y1 = int(max(0, np.nanmin(ys) - pad)), int(min(fr.shape[0], np.nanmax(ys) + pad))
            tile = fr[y0:y1, x0:x1]
            tile = cv2.resize(tile, (int(tile.shape[1] * 520 / tile.shape[0]), 520))
            a_txt = f"{label}  knee {A['knee'][fi]:.0f}  hip {A['hip'][fi]:.0f}  trunk {A['trunk'][fi]:.0f}  elbow {A['elbow'][fi]:.0f}"
            cv2.rectangle(tile, (0, 0), (tile.shape[1], 30), (255, 255, 255), -1)
            cv2.putText(tile, a_txt, (8, 21), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 0, 0), 1, cv2.LINE_AA)
            tiles.append(tile)
        cv2.imwrite(f"{D}/overlay-{ex}.png", np.hstack(tiles))
    return {"real": real, "proto": pro}


summary = {}
for ex in NAMES:
    summary[ex] = measure(ex)
    r, p = summary[ex]["real"], summary[ex]["proto"]
    print(f"\n== {ex}: side {r['side']}, sideness {r['sideness']:.2f}, vis {r['visibility']:.2f}, reps {r['reps']}")
    for n in ANGLES:
        print(f"  {n:9s} top real {r['top'][n]:6.1f} proto {p['top'][n]:6.1f} | d=1 real {r['bottom'][n]:6.1f} proto {p['bottom'][n]:6.1f} | jitter {r['jitter'][n]:.1f} | 2D {r['top2d'][n]:6.1f} {r['bottom2d'][n]:6.1f}")
    print("  tempo real ", {kk: (round(v, 2) if v is not None else None) for kk, v in r["tempo"].items()})
    print("  tempo proto", {kk: round(v, 2) for kk, v in p["tempo"].items()})
    print("  2D vs 3D disagreement", {kk: round(v, 1) for kk, v in r["disagree_2d_3d"].items()})
    print("  path top real", np.round(r["path_top"], 2) if r["path_top"] else None, "proto", np.round(p["path_top"], 2),
          "| d=1 real", np.round(r["path_bottom"], 2) if r["path_bottom"] else None, "proto", np.round(p["path_bottom"], 2))
    print("  reps:", r["tempo_reps"])
json.dump(summary, open(f"{D}/out/summary.json", "w"), indent=1)
