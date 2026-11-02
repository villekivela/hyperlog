"""usage: merge.py <clip.glb> <dir with its external buffers> <out.glb>

Folds a clip's external buffers (mannequin.bin) into its own BIN chunk, so the page can parse
it from bytes without fetching anything.
"""
import json
import struct
import sys

clip, folder, out = sys.argv[1:4]
data = open(clip, "rb").read()
json_len = struct.unpack("<I", data[12:16])[0]
gltf = json.loads(data[20:20 + json_len])
offset = 20 + json_len
bin_chunk = b""
if offset < len(data):
    bin_len = struct.unpack("<I", data[offset:offset + 4])[0]
    bin_chunk = data[offset + 8:offset + 8 + bin_len]

blob = bytearray()
starts = []
for buf in gltf["buffers"]:
    while len(blob) % 4:
        blob.append(0)
    starts.append(len(blob))
    blob += open(f"{folder}/{buf['uri']}", "rb").read() if "uri" in buf else bin_chunk[: buf["byteLength"]]
for view in gltf["bufferViews"]:
    view["byteOffset"] = view.get("byteOffset", 0) + starts[view["buffer"]]
    view["buffer"] = 0
gltf["buffers"] = [{"byteLength": len(blob)}]
while len(blob) % 4:
    blob.append(0)

text = json.dumps(gltf, separators=(",", ":")).encode()
text += b" " * (-len(text) % 4)
total = 12 + 8 + len(text) + 8 + len(blob)
with open(out, "wb") as f:
    f.write(struct.pack("<III", 0x46546C67, 2, total))
    f.write(struct.pack("<II", len(text), 0x4E4F534A) + text)
    f.write(struct.pack("<II", len(blob), 0x004E4942) + blob)
