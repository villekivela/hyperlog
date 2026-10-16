"""Builds the theme-proposal contact sheets and overview: sheets.py <renders> <out dir>."""
import subprocess, sys, os

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
names = {
    "cobalt": ("Cobalt", "seed #1F4FD8 · Archivo SemiExpanded"),
    "lime": ("Lime", "seed #C6F21C · Barlow Condensed"),
    "plum": ("Plum", "seed #8B2FA8 · Bricolage Grotesque"),
}
screens = [("1-today", "Today"), ("2-session", "Session, mid drop set"), ("3-coach", "Coach"), ("4-progress", "History, Progress")]
font = "/usr/share/fonts/noto/NotoSans-Regular.ttf"


def montage(files, labels, tile, title, dest, width=300):
    args = ["magick", "montage"]
    for f, l in zip(files, labels):
        args += ["-label", l, f]
    args += ["-tile", tile, "-geometry", f"{width}x+12+12", "-font", font, "-pointsize", "14",
             "-background", "#FFFFFF", "-title", title, "-depth", "8", dest]
    subprocess.run(args, check=True)


for key, (name, sub) in names.items():
    files, labels = [], []
    for mode in ["light", "dark"]:
        for s, label in screens:
            files.append(f"{src}/{key}-{mode}-{s}.png")
            labels.append(f"{label} · {mode}")
    montage(files, labels, "4x2", f"{name}  ({sub})", f"{out}/{key}.png")

files, labels = [], []
for mode in ["light", "dark"]:
    for key, (name, _) in names.items():
        files.append(f"{src}/{key}-{mode}-1-today.png")
        labels.append(f"{name} · {mode}")
montage(files, labels, "3x2", "Theme proposals: Today", f"{out}/overview.png")
