#!/usr/bin/env python3
"""Bakes each mannequin clip's start and end pose into a small 2D table the app can draw in a list.

The clips are read, never changed. Each clip's joints are posed at the first and last key of its
`lowering` animation and projected through the clip's own camera, then fitted to a 0-1000 box:

    python3 -I tools/exercise-poses/bake.py

Run it after the clips change and commit app/src/main/assets/exercise-poses.json. An exercise
missing from the table draws its designed fallback, so a late bake costs nothing but the picture.
"""
import json
import math
import os
import struct
import sys

CLIPS = os.path.join("app", "src", "main", "assets", "mannequins")
OUT = os.path.join("app", "src", "main", "assets", "exercise-poses.json")
SEED = os.path.join("app", "src", "main", "assets", "exercises.json")
JOINTS = [
    "pelvis", "spine_03", "neck_01", "Head",
    "upperarm_l", "lowerarm_l", "hand_l", "upperarm_r", "lowerarm_r", "hand_r",
    "thigh_l", "calf_l", "foot_l", "thigh_r", "calf_r", "foot_r",
]
COMPONENTS = {5126: ("f", 4)}
WIDTHS = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}


def load(path):
    """The glTF JSON of a .glb, and its binary chunk."""
    data = open(path, "rb").read()
    length = struct.unpack("<I", data[12:16])[0]
    return json.loads(data[20:20 + length]), data[20 + length + 8:]


def accessor(gltf, chunk, index):
    """An accessor's values, as a list of tuples."""
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    width = WIDTHS[acc["type"]]
    code, size = COMPONENTS[acc["componentType"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride", size * width)
    return [struct.unpack_from("<" + code * width, chunk, start + i * stride) for i in range(acc["count"])]


def matrix(translation, rotation, scale):
    """A column-major-free 4x4 as rows, from translation, a quaternion and scale."""
    x, y, z, w = rotation
    sx, sy, sz = scale
    return [
        [(1 - 2 * (y * y + z * z)) * sx, (2 * (x * y - z * w)) * sy, (2 * (x * z + y * w)) * sz, translation[0]],
        [(2 * (x * y + z * w)) * sx, (1 - 2 * (x * x + z * z)) * sy, (2 * (y * z - x * w)) * sz, translation[1]],
        [(2 * (x * z - y * w)) * sx, (2 * (y * z + x * w)) * sy, (1 - 2 * (x * x + y * y)) * sz, translation[2]],
        [0, 0, 0, 1],
    ]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def local(node):
    if "matrix" in node:
        m = node["matrix"]
        return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
    return matrix(node.get("translation", (0, 0, 0)), node.get("rotation", (0, 0, 0, 1)), node.get("scale", (1, 1, 1)))


def world_positions(gltf, overrides):
    """Every node's world position, with [overrides] of {node: {path: value}} applied."""
    parents = {}
    for i, node in enumerate(gltf["nodes"]):
        for child in node.get("children", []):
            parents[child] = i
    cache = {}

    def world(i):
        if i not in cache:
            node = dict(gltf["nodes"][i])
            node.update(overrides.get(i, {}))
            node.pop("matrix", None) if overrides.get(i) else None
            own = local(node)
            cache[i] = multiply(world(parents[i]), own) if i in parents else own
        return cache[i]

    return {node.get("name"): tuple(world(i)[r][3] for r in range(3)) for i, node in enumerate(gltf["nodes"])}


def key(gltf, chunk, which):
    """The animation's channels at its first (0) or last (-1) key."""
    overrides = {}
    for animation in gltf.get("animations", []):
        for channel in animation["channels"]:
            sampler = animation["samplers"][channel["sampler"]]
            value = accessor(gltf, chunk, sampler["output"])[which]
            target = channel["target"]
            overrides.setdefault(target["node"], {})[target["path"]] = value
    return overrides


def project(point, camera):
    """A point on the camera's screen, x to the right and y up, with its depth towards the camera."""
    azimuth = math.radians(camera["azimuth"])
    elevation = math.radians(camera["elevation"])
    forward = (math.sin(azimuth) * math.cos(elevation), math.sin(elevation), math.cos(azimuth) * math.cos(elevation))
    right = (math.cos(azimuth), 0.0, -math.sin(azimuth))
    up = (
        forward[1] * right[2] - forward[2] * right[1],
        forward[2] * right[0] - forward[0] * right[2],
        forward[0] * right[1] - forward[1] * right[0],
    )
    dot = lambda a, b: sum(p * q for p, q in zip(a, b))
    return dot(point, right), -dot(point, up) if up[1] < 0 else dot(point, up), dot(point, forward)


def pose(path, camera):
    """The start and end poses of the clip at [path] on a 0-1000 box, and which side is further away."""
    gltf, chunk = load(path)
    frames = []
    for which in (0, -1):
        positions = world_positions(gltf, key(gltf, chunk, which))
        frames.append([project(positions[j], camera) for j in JOINTS])
    xs = [p[0] for f in frames for p in f]
    ys = [p[1] for f in frames for p in f]
    scale = 1000 / max(max(xs) - min(xs), max(ys) - min(ys), 1e-6)
    pad_x = (1000 - (max(xs) - min(xs)) * scale) / 2
    pad_y = (1000 - (max(ys) - min(ys)) * scale) / 2
    fitted = [
        [v for p in f for v in (round((p[0] - min(xs)) * scale + pad_x), round((max(ys) - p[1]) * scale + pad_y))]
        for f in frames
    ]
    depth = lambda side: sum(frames[0][JOINTS.index(j)][2] for j in JOINTS if j.endswith("_" + side))
    return {"start": fitted[0], "end": fitted[1], "far": "l" if depth("l") < depth("r") else "r"}


def clip_of(entry):
    """The file and camera an index entry shows by default."""
    if "clip" in entry:
        return entry["clip"], entry["camera"]
    if "grips" in entry:
        return entry["grips"][0]["clip"], entry["grips"][0]["camera"]
    bench = entry["bench"]
    typical = bench["clips"][str(bench["typical"])]
    return typical["clip"], typical["camera"]


def main():
    index = json.load(open(os.path.join(CLIPS, "mannequins.json")))
    seed = json.load(open(SEED))
    patterns = {e["id"]: e["pattern"] for e in (seed["exercises"] if isinstance(seed, dict) else seed)}
    poses = {}
    for exercise, entry in sorted(index["clips"].items()):
        clip, camera = clip_of(entry)
        poses[exercise] = {
            **pose(os.path.join(CLIPS, clip), camera),
            "equipment": entry["equipment"],
            "pattern": patterns.get(exercise, ""),
        }
    with open(OUT, "w") as out:
        json.dump({"joints": JOINTS, "poses": poses}, out, separators=(",", ":"), sort_keys=True)
        out.write("\n")
    print(f"{len(poses)} poses, {os.path.getsize(OUT)} bytes", file=sys.stderr)


if __name__ == "__main__":
    main()
