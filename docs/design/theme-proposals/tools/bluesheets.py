"""Builds the blue-variation sheets: bluesheets.py <renders> <out dir>."""
import subprocess, sys

src, out = sys.argv[1], sys.argv[2]
font = "/usr/share/fonts/noto/NotoSans-Regular.ttf"
blues = {
    "cobalt": "Cobalt #1F4FD8 (current)",
    "navy": "Navy #16236E",
    "indigo": "Indigo #4338CA",
    "electric": "Electric #0066FF",
    "cerulean": "Cerulean #0284C7",
}


def montage(files, labels, tile, title, dest, width=300):
    args = ["magick", "montage"]
    for f, l in zip(files, labels):
        args += ["-label", l, f]
    args += ["-tile", tile, "-geometry", f"{width}x+12+12", "-font", font, "-pointsize", "14",
             "-background", "#FFFFFF", "-title", title, "-depth", "8", dest]
    subprocess.run(args, check=True)


for key, title in blues.items():
    files, labels = [], []
    for mode in ["light", "dark"]:
        for s, label in [("1-today", "Today"), ("2-session", "Session, mid drop set")]:
            files.append(f"{src}/{key}-{mode}-{s}.png")
            labels.append(f"{label} · {mode}")
    montage(files, labels, "4x1", f"{title}  ·  Archivo SemiExpanded", f"{out}/blue-{key}.png")

files, labels = [], []
for mode in ["light", "dark"]:
    for key, title in blues.items():
        files.append(f"{src}/{key}-{mode}-1-today.png")
        labels.append(f"{title.split(' (')[0]} · {mode}")
montage(files, labels, "5x2", "Cobalt and four blue variations: Today", f"{out}/blue-overview.png")
