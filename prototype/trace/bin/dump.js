// injected before the last requestAnimationFrame: once the mannequin loads, pose every exercise
// at d = 0, 0.1 … 1 and write the joints' world positions into <pre id="dump">
const DUMP_BONES = ['pelvis', 'spine_03', 'neck_01', 'Head', 'thigh_l', 'calf_l', 'foot_l', 'ball_l', 'upperarm_l', 'lowerarm_l', 'hand_l', 'middle_01_l', 'thigh_r', 'calf_r', 'foot_r', 'upperarm_r', 'lowerarm_r', 'hand_r'];
(function waitDump() {
  if (!rigs.mannequin || rig !== rigs.mannequin) return setTimeout(waitDump, 50);
  const out = { L: { ...L }, timing: {}, poses: {} };
  for (const ex of Object.keys(POSES)) {
    out.timing[ex] = EXERCISES[ex].timing;
    out.poses[ex] = [];
    for (let i = 0; i <= 10; i++) {
      const P = POSES[ex](i / 10);
      poseRig(P);
      rig.root.updateMatrixWorld(true);
      const j = {};
      for (const n of DUMP_BONES) if (rig.bones[n]) j[n] = wpos(rig.bones[n]).toArray().map(v => +v.toFixed(4));
      if (P.bar) j.bar = P.bar.toArray().map(v => +v.toFixed(4));
      out.poses[ex].push(j);
    }
  }
  const pre = document.createElement('pre'); pre.id = 'dump'; pre.textContent = JSON.stringify(out); document.body.append(pre);
})();
