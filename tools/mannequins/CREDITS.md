# Credits

- `model/mannequin.glb` is the Mannequin from Quaternius's
  [Universal Animation Library](https://quaternius.com/packs/universalanimationlibrary.html)
  (Standard, free), released under
  [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). It is stripped to the mesh and
  skeleton of the `Mannequin` mesh of `UAL1_Standard.glb`, with no textures, materials or
  animations, by the `slim_gltf.py` script on the `prototype/exercise-figures` branch:

  ```sh
  python3 slim_gltf.py UAL1_Standard.glb Mannequin mannequin.glb
  ```

  CC0 asks for no credit; Hyperlog gives it anyway, here and in `docs/licences/Quaternius-UAL-CC0.txt`.
- [three.js](https://threejs.org) (MIT) loads, poses and exports the clips. It is a tool
  dependency only and doesn't ship in the app.
