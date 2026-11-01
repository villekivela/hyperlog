# Credits

`figure.glb` is the Superhero Male body from Quaternius's
[Universal Base Characters](https://quaternius.com/packs/universalbasecharacters.html) (Standard,
free), licensed CC0 1.0. `slim_gltf.py` stripped it to the body mesh and skeleton:

```sh
python3 slim_gltf.py "Superhero_Male_FullBody.gltf" "Sphere.005_Retopology.004" figure.glb
python3 slim_gltf.py "Superhero_Male_FullBody.gltf" "Sphere.005_Retopology.004" figure-smooth.glb 100
```

`figure-smooth.glb` is the same body after 100 Taubin smoothing passes, which iron out the muscles
and the face.
