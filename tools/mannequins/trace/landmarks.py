# Finds the lifter in each frame of a clip's time range with MediaPipe Pose Landmarker, on the CPU,
# and writes the image landmarks as JSON for trace.mjs. Nothing of the clip is kept.
#
#   python3 landmarks.py <clip> <from s> <to s> <model.task> <out.json>
#
# Needs mediapipe (0.10.21 traced the prototype) and opencv-python, and the heavy model from
# MediaPipe's model page (pose_landmarker_heavy.task); see README, Tracing a clip.
import json
import sys

import cv2
import mediapipe as mp
from mediapipe.tasks.python import BaseOptions, vision

clip, start, end, model, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4], sys.argv[5]
options = vision.PoseLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=model, delegate=BaseOptions.Delegate.CPU),
    running_mode=vision.RunningMode.VIDEO,
    num_poses=1,
    min_pose_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)
video = cv2.VideoCapture(clip)
if not video.isOpened():
    sys.exit(f"can't open {clip}")
fps = video.get(cv2.CAP_PROP_FPS)
size = [int(video.get(cv2.CAP_PROP_FRAME_WIDTH)), int(video.get(cv2.CAP_PROP_FRAME_HEIGHT))]
# Frames are timed by their number: MediaPipe needs each later than the last.
first = round(start * fps)
video.set(cv2.CAP_PROP_POS_FRAMES, first)
frames = []
with vision.PoseLandmarker.create_from_options(options) as landmarker:
    while True:
        t = (first + len(frames)) / fps
        ok, frame = video.read()
        if not ok or t > end:
            break
        found = landmarker.detect_for_video(
            mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), int(t * 1000)
        )
        if found.pose_landmarks:
            pose = found.pose_landmarks[0]
            frames.append({
                "t": t,
                "image": [[p.x * size[0], p.y * size[1]] for p in pose],
                "visibility": [p.visibility for p in pose],
            })
        else:
            frames.append({"t": t, "image": None, "visibility": None})
with open(out, "w") as f:
    json.dump({"fps": fps, "size": size, "frames": frames}, f)
seen = sum(1 for f in frames if f["image"]) / max(1, len(frames))
print(f"{clip}: {len(frames)} frames at {fps:.2f} fps from {start} s to {end} s, the lifter found in {seen:.0%}", file=sys.stderr)
