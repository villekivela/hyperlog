# Credits

`figure.glb` is the Superhero Male body from Quaternius's
[Universal Base Characters](https://quaternius.com/packs/universalbasecharacters.html) (Standard,
free), licensed CC0 1.0. `slim_gltf.py` stripped it to the body mesh and skeleton:

```sh
python3 slim_gltf.py "Superhero_Male_FullBody.gltf" "Sphere.005_Retopology.004" figure.glb
python3 slim_gltf.py "Superhero_Male_FullBody.gltf" "Sphere.005_Retopology.004" figure-smooth.glb 100
```

`figure-lowpoly.glb` is `figure.glb` cut to 722 triangles with gltfpack 1.3.0, which keeps the
skin weights:

```sh
gltfpack -i figure.glb -o figure-lowpoly.glb -si 0.06 -sp -sa -noq -kn
```
