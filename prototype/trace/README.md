# PROTOTYPE: tracing technique clips

Throwaway: it answers whether real lifters' motion, traced from video, can set a mannequin pose. The verdict is on #120, and the `trace` command it led to is in #123's scope.

- `report.md`: the findings, per exercise.
- `traced.html`: the prototype page with a Source switch (Authored, Traced, Traced at the clip's tempo) on the squat.
- `curves-*.png`: joint angles over the reps.
- `squat-authored-vs-traced.png`: the top and bottom of the rep, authored against traced.
- `out/`: the measured numbers (the trace, its summary, and the prototype's own angles).
- `bin/`: the scripts.
  - `track.py` runs MediaPipe Pose Landmarker. It needs `pose_landmarker_heavy.task` from Google's MediaPipe model page, next to it.
  - `analyze.py` measures the angles.
  - `retarget.py` and `build_traced.py` make the traced page.

No clips or video frames are here. The clips are named by their YouTube ids in `report.md`.
