"""Builds the typeface sheets for Cobalt: typesheets.py <renders> <out dir>."""
import subprocess, sys

src, out = sys.argv[1], sys.argv[2]
font = "/usr/share/fonts/noto/NotoSans-Regular.ttf"
faces = {
    "archivo": "Archivo SemiExpanded, SemiBold 600 / Bold 700 (baseline)",
    "barlow": "Barlow Condensed, SemiBold 600 / Bold 700",
    "outfit": "Outfit, SemiBold 600 / Bold 700",
    "spacegrotesk": "Space Grotesk, SemiBold 600 / Bold 700",
    "azeret": "Azeret Mono, SemiBold 600 / Bold 700",
    "unbounded": "Unbounded, SemiBold 600 / Bold 700",
}
short = {"archivo": "Archivo SemiExpanded", "barlow": "Barlow Condensed", "outfit": "Outfit",
         "spacegrotesk": "Space Grotesk", "azeret": "Azeret Mono", "unbounded": "Unbounded"}


def montage(files, labels, tile, title, dest, width=300):
    args = ["magick", "montage"]
    for f, l in zip(files, labels):
        args += ["-label", l, f]
    args += ["-tile", tile, "-geometry", f"{width}x+12+12", "-font", font, "-pointsize", "14",
             "-background", "#FFFFFF", "-title", title, "-depth", "8", dest]
    subprocess.run(args, check=True)


for key, title in faces.items():
    files, labels = [], []
    for mode in ["light", "dark"]:
        for s, label in [("1-today", "Today"), ("5-numbers", "Session, typing 100")]:
            files.append(f"{src}/cobalt-{key}-{mode}-{s}.png")
            labels.append(f"{label} · {mode}")
    montage(files, labels, "4x1", f"{title}  ·  Cobalt", f"{out}/type-{key}.png")

# The overview shows the set table, cropped from the session render, large enough to judge the figures.
crops = []
for k in faces:
    crop = f"{src}/crop-{k}.png"
    subprocess.run(["magick", f"{src}/cobalt-{k}-dark-5-numbers.png", "-crop", "1236x1250+0+0", "+repage", crop], check=True)
    crops.append(crop)
montage(crops, [short[k] for k in faces], "3x2", "Display typefaces on Cobalt: the set table while typing 100, dark",
        f"{out}/type-overview.png", width=440)
