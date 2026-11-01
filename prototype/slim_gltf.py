# Keep one skinned mesh (POSITION, NORMAL, JOINTS_0, WEIGHTS_0) and its skeleton; write a GLB.
# Every primitive of the mesh is kept, its index in extras.part. An optional fourth argument
# smooths the first primitive's bind-pose surface that many Taubin passes.
import json, struct, sys, array, math
src, mesh_name, out = sys.argv[1], sys.argv[2], sys.argv[3]
passes = int(sys.argv[4]) if len(sys.argv) > 4 else 0
if src.endswith('.glb'):
    raw = open(src, 'rb').read(); jl = struct.unpack('<I', raw[12:16])[0]
    j = json.loads(raw[20:20 + jl]); binb = raw[20 + jl + 8:]
else:
    j = json.load(open(src)); binb = open(src.rsplit('.', 1)[0] + '.bin', 'rb').read()
acc, bvs = j['accessors'], j['bufferViews']
new_acc, new_bv, blob = [], [], bytearray()
def take(i):
    a = dict(acc[i]); bv = bvs[a['bufferView']]
    start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    comp = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}[a['componentType']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[a['type']]
    stride = bv.get('byteStride', comp * n); size = comp * n
    data = b''.join(binb[start + k * stride:start + k * stride + size] for k in range(a['count']))
    while len(blob) % 4: blob.append(0)
    new_bv.append({'buffer': 0, 'byteOffset': len(blob), 'byteLength': len(data)})
    blob.extend(data)
    a['bufferView'] = len(new_bv) - 1; a.pop('byteOffset', None)
    new_acc.append(a); return len(new_acc) - 1
mi = next(k for k, m in enumerate(j['meshes']) if m['name'] == mesh_name)
prims = []
for k, prim in enumerate(j['meshes'][mi]['primitives']):
    attrs = {a: take(v) for a, v in prim['attributes'].items() if a in ('POSITION', 'NORMAL', 'JOINTS_0', 'WEIGHTS_0')}
    prims.append({'attributes': attrs, 'indices': take(prim['indices']), 'extras': {'part': k}})
mesh = {'name': 'body', 'primitives': prims}
attrs = prims[0]['attributes']

def view(i, code):
    a = new_acc[i]; bv = new_bv[a['bufferView']]
    return memoryview(blob)[bv['byteOffset']:bv['byteOffset'] + bv['byteLength']].cast(code)

if passes:
    pos, nrm = view(attrs['POSITION'], 'f'), view(attrs['NORMAL'], 'f')
    ia = new_acc[prims[0]['indices']]
    tri = view(prims[0]['indices'], {5123: 'H', 5125: 'I'}[ia['componentType']])
    n = len(pos) // 3
    # weld the copies UV seams split, so the surface smooths as one piece
    key = {}; weld = [key.setdefault((round(pos[3*v], 5), round(pos[3*v+1], 5), round(pos[3*v+2], 5)), len(key)) for v in range(n)]
    m = len(key); P = [[0.0] * 3 for _ in range(m)]
    for v in range(n): P[weld[v]] = [pos[3*v], pos[3*v+1], pos[3*v+2]]
    nb = [set() for _ in range(m)]
    for t in range(0, len(tri), 3):
        a, b, c = weld[tri[t]], weld[tri[t+1]], weld[tri[t+2]]
        nb[a] |= {b, c}; nb[b] |= {a, c}; nb[c] |= {a, b}
    def step(f):
        Q = []
        for i in range(m):
            if not nb[i]: Q.append(P[i]); continue
            k = len(nb[i]); avg = [sum(P[j][d] for j in nb[i]) / k for d in range(3)]
            Q.append([P[i][d] + f * (avg[d] - P[i][d]) for d in range(3)])
        return Q
    for _ in range(passes): P = step(0.5); P = step(-0.53)
    N = [[0.0] * 3 for _ in range(m)]
    for t in range(0, len(tri), 3):
        a, b, c = weld[tri[t]], weld[tri[t+1]], weld[tri[t+2]]
        u = [P[b][d] - P[a][d] for d in range(3)]; w = [P[c][d] - P[a][d] for d in range(3)]
        fn = [u[1]*w[2] - u[2]*w[1], u[2]*w[0] - u[0]*w[2], u[0]*w[1] - u[1]*w[0]]
        for i in (a, b, c): N[i] = [N[i][d] + fn[d] for d in range(3)]
    for v in range(n):
        i = weld[v]; l = math.sqrt(sum(x * x for x in N[i])) or 1
        for d in range(3): pos[3*v+d] = P[i][d]; nrm[3*v+d] = N[i][d] / l
    pa = new_acc[attrs['POSITION']]
    pa['min'] = [min(pos[d::3]) for d in range(3)]; pa['max'] = [max(pos[d::3]) for d in range(3)]
    del pos, nrm, tri
skin = dict(j['skins'][0]); skin['inverseBindMatrices'] = take(skin['inverseBindMatrices'])
nodes = []
for n in j['nodes']:
    n = dict(n)
    if 'mesh' in n:
        if n['mesh'] == mi: n['mesh'] = 0
        else: n.pop('mesh'); n.pop('skin', None)
    nodes.append(n)
g = {'asset': {'version': '2.0', 'generator': 'slim_gltf.py from Quaternius Universal Base Characters (CC0)'},
     'scene': 0, 'scenes': j['scenes'], 'nodes': nodes, 'meshes': [mesh], 'skins': [skin],
     'accessors': new_acc, 'bufferViews': new_bv, 'buffers': [{'byteLength': len(blob)}]}
js = json.dumps(g, separators=(',', ':')).encode()
js += b' ' * (-len(js) % 4); blob += b'\0' * (-len(blob) % 4)
with open(out, 'wb') as f:
    f.write(struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob)))
    f.write(struct.pack('<II', len(js), 0x4E4F534A)); f.write(js)
    f.write(struct.pack('<II', len(blob), 0x004E4942)); f.write(blob)
print(out, 12 + 16 + len(js) + len(blob), 'bytes')
