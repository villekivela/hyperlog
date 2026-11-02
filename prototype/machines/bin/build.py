"""usage: build.py — inlines the merged clip into machines.src.html as machines.html."""
import base64
import pathlib

here = pathlib.Path(__file__).resolve().parent.parent
clip = base64.b64encode((here / "lat_pulldown.glb").read_bytes()).decode()
page = (here / "machines.src.html").read_text().replace("__CLIP__", clip)
(here / "machines.html").write_text(page)
print(len(page))
