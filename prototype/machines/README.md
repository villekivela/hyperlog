# PROTOTYPE: how detailed the machines are

Throwaway: it answers how much detail the generic equipment needs. The lat pulldown station is built in code at three levels, plus B in tones. The owner picked **B · tones** (2026-11-02), and #127 holds the decision with its colour table.

- `machines.html`: the page. B · tones is the default; A, B and C switch, and the rep loops.
- `levels.png`: A (today's boxes, 824 triangles), B (refined, 13,928) and C (detailed, 20,424), light and dark, top and bottom of the rep.
- `tones.png`: B in one grey beside B in tones.
- `bin/`: the scripts that build the page and render the shots.

## How gym.wesbos.com builds its machines (for reference)

Read from https://gym.wesbos.com/parts/titan-lat-tower-300 and the site's bundles on 2026-11-02.

- **Generated in code:** every part is built in the browser by a small builder over Manifold, the WASM solid-modelling library.
- **Rebuilt from published specs:** the lat tower is credited as "an independent Manifold reconstruction" from Titan Fitness's published dimensions, stack chart and product photos.
- **The GLB download** is exported on the spot by three.js's GLTFExporter: 25,460 triangles, flat colours by material, no textures.
- **What makes it read as a real machine** is colour and material by part (powder-coat, chrome rods, red pulleys, vinyl), more than triangle count.
- **No licence** is published, so none of his geometry, code or files are used or kept here.
