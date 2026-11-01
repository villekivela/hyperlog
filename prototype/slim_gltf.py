# Keep one skinned mesh (POSITION, NORMAL, JOINTS_0, WEIGHTS_0) and its skeleton; write a GLB.
import json, struct, sys
src, mesh_name, out = sys.argv[1], sys.argv[2], sys.argv[3]
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
prim = j['meshes'][mi]['primitives'][0]
attrs = {k: take(v) for k, v in prim['attributes'].items() if k in ('POSITION', 'NORMAL', 'JOINTS_0', 'WEIGHTS_0')}
mesh = {'name': 'body', 'primitives': [{'attributes': attrs, 'indices': take(prim['indices'])}]}
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
