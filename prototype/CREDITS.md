# Credits

The figures are stripped to mesh and skeleton by `slim_gltf.py`, which drops textures, materials and
animations and keeps every part of the named mesh.

- `figure-mannequin.glb`: the Mannequin from Quaternius's
  [Universal Animation Library](https://quaternius.com/packs/universalanimationlibrary.html)
  (Standard, free), CC0 1.0.

  ```sh
  python3 slim_gltf.py UAL1_Standard.glb Mannequin figure-mannequin.glb
  ```

- `figure.glb`: the Superhero Male from Quaternius's
  [Universal Base Characters](https://quaternius.com/packs/universalbasecharacters.html)
  (Standard, free), CC0 1.0.

  ```sh
  python3 slim_gltf.py Superhero_Male_FullBody.gltf Sphere.005_Retopology.004 figure.glb
  ```

- `figure-mannequiny.glb`: Mannequiny 0.3.0 from
  [godot-3d-mannequin](https://github.com/gdquest-demos/godot-3d-mannequin) by GDQuest, Luciano
  Muñoz and contributors, CC BY 4.0. Shipping it needs this credit in the app.

  ```sh
  python3 slim_gltf.py mannequiny-0.3.0.glb bony_body.002 figure-mannequiny.glb
  ```
