# Run MediaPipe Pose Landmarker (heavy) over a clip and save every frame's landmarks.
# usage: track.py <clip.mp4> <out.npz>
import sys
import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import BaseOptions, vision

clip, out = sys.argv[1], sys.argv[2]
model = __file__.rsplit("/", 1)[0] + "/pose_landmarker_heavy.task"
opts = vision.PoseLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=model),
    running_mode=vision.RunningMode.VIDEO,
    num_poses=1,
    min_pose_detection_confidence=0.5,
    min_tracking_confidence=0.5,
)
cap = cv2.VideoCapture(clip)
fps = cap.get(cv2.CAP_PROP_FPS)
w, h = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
img2d, world, vis, ts = [], [], [], []
with vision.PoseLandmarker.create_from_options(opts) as lm:
    i = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        t = int(i * 1000 / fps)
        res = lm.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), t)
        if res.pose_landmarks:
            p = res.pose_landmarks[0]
            q = res.pose_world_landmarks[0]
            img2d.append([[l.x * w, l.y * h] for l in p])
            world.append([[l.x, l.y, l.z] for l in q])
            vis.append([l.visibility for l in p])
        else:
            img2d.append(np.full((33, 2), np.nan))
            world.append(np.full((33, 3), np.nan))
            vis.append(np.zeros(33))
        ts.append(i / fps)
        i += 1
np.savez(out, img=np.array(img2d, float), world=np.array(world, float), vis=np.array(vis, float), t=np.array(ts), fps=fps, size=(w, h))
found = np.isfinite(np.array(img2d)[:, 0, 0]).mean()
print(f"{clip}: {i} frames at {fps:.2f} fps, pose found in {found:.0%}")
