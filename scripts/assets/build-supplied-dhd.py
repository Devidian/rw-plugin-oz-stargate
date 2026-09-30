#!/usr/bin/env python3
"""Convert the user-supplied BlendSwap DHD into bounded OBJ material groups.

Run with Blender 3.3.15 after extracting DHD.blend from Stargate DHD.zip:
  blender --background DHD.blend --python scripts/assets/build-supplied-dhd.py -- OUT_DIR
The original .blend and its license remain with the source archive; the exact license
notice is copied into OUT_DIR by the stdlib verification/copy step below.
"""
import bpy
import hashlib
import json
import math
import sys
from pathlib import Path
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
assert len(args) == 1
out = Path(args[0]);out.mkdir(parents=True, exist_ok=True)
assert bpy.app.version[:2] == (3, 3), bpy.app.version_string
assert hashlib.sha256(Path(bpy.data.filepath).read_bytes()).hexdigest() == 'a90ada3743ecb7cbbde078359dbb00f2e67be585f3c449583ddb432b7992ce9e'
objects = {o.name: o for o in bpy.data.objects if o.type == 'MESH' and o.name != 'Light planes'}
expected = {'DHD activation dome', 'DHD activation dome ring', 'DHD base', 'DHD buttons',
            'DHD flat top', 'DHD ring', 'DHD ring accents', 'DHD symbol house',
            'DHD symbols inner', 'DHD symbols outer'}
assert set(objects) == expected, set(objects) ^ expected
# Scale from a source 2.74m wide console to 1.45m, comparable to the accepted first model.
SCALE = .53
source_z_min = -2.597103
source_y_center = -3.550
# Blender Z-up to Rising World Y-up; original camera side (+Blender Y) faces game -Z.
def transform(world):
    return (round(world.x * SCALE, 6), round((world.z - source_z_min) * SCALE, 6),
            round(-(world.y - source_y_center) * SCALE, 6))

selection = {
    'supplied-dhd-body': ['DHD base', 'DHD flat top', 'DHD ring', 'DHD symbol house'],
    'supplied-dhd-accents': ['DHD ring accents', 'DHD activation dome ring'],
    'supplied-dhd-keys': ['DHD buttons'],
    'supplied-dhd-symbols': ['DHD symbols inner', 'DHD symbols outer'],
    'supplied-dhd-activate': ['DHD activation dome'],
}
# Ratio applies to original control cages. Mirror is required for the source's half-base;
# subdivision would add hundreds of thousands of mostly redundant client triangles.
# Flat key faces use deterministic planar dissolve; key collapse has nondeterministic ties.
ratios = {'DHD base': .35, 'DHD ring accents': .25,
          'DHD activation dome ring': .35, 'DHD symbol house': .65}
report = {'sourceArchiveSha256': '8d9f9a91305d9dd5a4245f12644c9c01c673cb6596ecc148b041fd4a303590aa',
          'sourceBlendSha256': 'a90ada3743ecb7cbbde078359dbb00f2e67be585f3c449583ddb432b7992ce9e',
          'blender': bpy.app.version_string, 'scale': SCALE,
          'sourceZMin': source_z_min, 'sourceYCenter': source_y_center,
          'modifiers': 'mirror on base; subdivision disabled; selected collapse ratios; planar key dissolve',
          'parts': {}}

def connected_parts(faces, points):
    parent = list(range(len(points)+1))
    def root(index):
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index
    for a,b,c in faces:
        parent[root(b)] = root(a)
        parent[root(c)] = root(a)
    parts = {}
    for face in faces:
        parts.setdefault(root(face[0]), []).append(face)
    result = []
    for part in parts.values():
        vertices = sorted(set(i for face in part for i in face))
        x = sum(points[i-1][0] for i in vertices)/len(vertices)
        z = sum(points[i-1][2] for i in vertices)/len(vertices)
        result.append((x,z,part))
    return result

def write_subset(name, faces, points):
    assert faces, name
    refs = sorted(set(i for face in faces for i in face))
    ids = {old:i+1 for i,old in enumerate(refs)}
    lines = ['# Derived from blenderjunky Stargate DHD (BlendSwap 75540 / 13419).',
             '# Source notice: supplied-dhd-LICENSE.html; Fan Art, noncommercial use only.',
             'o '+name]
    lines += ['v %.6f %.6f %.6f'%points[i-1] for i in refs]
    lines += ['f %d %d %d'%tuple(ids[i] for i in face) for face in faces]
    data = ('\n'.join(lines)+'\n').encode()
    (out/(name+'.obj')).write_bytes(data)
    return {'vertices':len(refs),'triangles':len(faces),'bytes':len(data),
            'sha256':hashlib.sha256(data).hexdigest()}

key_centres = []
for name, object_names in selection.items():
    lines = ['# Derived from blenderjunky Stargate DHD (BlendSwap 75540 / 13419).',
             '# Source notice: supplied-dhd-LICENSE.html; Fan Art, noncommercial use only.', 'o ' + name]
    all_points = []
    faces = []
    by_object = {}
    for object_name in object_names:
        o = objects[object_name]
        for mod in o.modifiers:
            if mod.type == 'SUBSURF': mod.show_viewport = False
            if mod.type == 'MIRROR': mod.show_viewport = True
        if object_name in ratios:
            mod = o.modifiers.new('OZ_runtime_decimate', 'DECIMATE')
            mod.ratio = ratios[object_name]
            mod.use_collapse_triangulate = True
        elif object_name == 'DHD buttons':
            mod = o.modifiers.new('OZ_flat_key_faces', 'DECIMATE')
            mod.decimate_type = 'DISSOLVE'
            mod.angle_limit = math.radians(1)
        evaluated = o.evaluated_get(bpy.context.evaluated_depsgraph_get())
        mesh = evaluated.to_mesh(preserve_all_data_layers=False,
                                 depsgraph=bpy.context.evaluated_depsgraph_get())
        mesh.calc_loop_triangles()
        matrix = o.matrix_world
        points = [transform(matrix @ v.co) for v in mesh.vertices]
        offset = len(all_points)
        all_points.extend(points)
        accepted = 0
        for t in mesh.loop_triangles:
            ids = tuple(int(i) for i in t.vertices)
            a,b,c = (points[i] for i in ids)
            u = [b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
            cross=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            if sum(x*x for x in cross) < 1e-16:continue
            faces.append(tuple(offset+i+1 for i in ids))
            accepted += 1
        by_object[object_name] = {'vertices': len(points), 'triangles': accepted,
                                  'decimation': ratios.get(object_name, 1)}
        evaluated.to_mesh_clear()
    # Only referenced vertices are written to avoid importer trouble with unused points.
    referenced = sorted(set(i for face in faces for i in face))
    remap = {old:i+1 for i,old in enumerate(referenced)}
    lines += ['v %.6f %.6f %.6f'%all_points[i-1] for i in referenced]
    if name in ('supplied-dhd-body', 'supplied-dhd-accents'):
        # Dominant-axis projection retains the source geometry and gives the stone
        # finish a stable tile on horizontal and vertical surfaces.
        uvmap = {};uvs = [];output_faces = []
        for face in faces:
            a,b,c = (all_points[i-1] for i in face)
            u = [b[i]-a[i] for i in range(3)];v = [c[i]-a[i] for i in range(3)]
            normal = (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            axis = max(range(3), key=lambda i:abs(normal[i]))
            sign = 1 if normal[axis] >= 0 else -1
            indices = []
            for index in face:
                key = (index, axis, sign)
                if key not in uvmap:
                    x,y,z = all_points[index-1]
                    pair = ((z*sign,y) if axis == 0 else (x,z*sign) if axis == 1 else (x*sign,y))
                    uvs.append(tuple(.5+n/1.8 for n in pair))
                    uvmap[key] = len(uvs)
                indices.append('%d/%d'%(remap[index],uvmap[key]))
            output_faces.append('f '+' '.join(indices))
        lines += ['vt %.7f %.7f'%uv for uv in uvs]
        lines += output_faces
    else:
        lines += ['f %d %d %d'%tuple(remap[i] for i in face) for face in faces]
    data=('\n'.join(lines)+'\n').encode()
    (out/(name+'.obj')).write_bytes(data)
    report['parts'][name]={'vertices':len(referenced),'triangles':len(faces),'bytes':len(data),
                           'sha256':hashlib.sha256(data).hexdigest(),'objects':by_object}
    if name == 'supplied-dhd-keys':
        components = connected_parts(faces, all_points)
        assert len(components) == 38, len(components)
        # Select seven whole buttons in a stable scattered order. The remaining
        # buttons stay in one unlit mesh; no face is drawn twice.
        shuffled = sorted(components, key=lambda c: hashlib.sha256(
                ('%.5f:%.5f'%(c[0],c[1])).encode()).digest())
        ranked = [shuffled.pop(0)]
        while len(ranked) < 7:
            chosen = max(shuffled, key=lambda c: min((c[0]-s[0])**2+(c[1]-s[1])**2 for s in ranked))
            ranked.append(chosen)
            shuffled.remove(chosen)
        ranked += shuffled
        key_centres = [(x,z) for x,z,_ in ranked]
        report['keyGroups'] = {}
        for group in range(7):
            group_name = 'supplied-dhd-keys-%d'%group
            report['keyGroups'][group_name] = write_subset(group_name,ranked[group][2],all_points)
            vertices = sorted(set(i for face in ranked[group][2] for i in face))
            report['keyGroups'][group_name]['center'] = [round(ranked[group][0],6),
                    round(sum(all_points[i-1][1] for i in vertices)/len(vertices),6),
                    round(ranked[group][1],6)]
        unlit = [face for _,_,part in ranked[7:] for face in part]
        report['keyGroups']['supplied-dhd-keys-unlit'] = write_subset(
                'supplied-dhd-keys-unlit',unlit,all_points)
        assert sum(g['triangles'] for g in report['keyGroups'].values()) == len(faces)
    if name == 'supplied-dhd-symbols':
        assert len(key_centres) == 38
        circuits = [[] for _ in range(8)]
        for x,z,part in connected_parts(faces,all_points):
            nearest = min(range(38), key=lambda i: (x-key_centres[i][0])**2 + (z-key_centres[i][1])**2)
            circuits[nearest if nearest < 7 else 7].extend(part)
        report['symbolGroups'] = {}
        for group in range(8):
            suffix = str(group) if group < 7 else 'unlit'
            group_name = 'supplied-dhd-symbols-'+suffix
            report['symbolGroups'][group_name] = write_subset(group_name,circuits[group],all_points)
        assert sum(g['triangles'] for g in report['symbolGroups'].values()) == len(faces)
report['totalTriangles']=sum(p['triangles'] for p in report['parts'].values())
assert report['totalTriangles'] < 50000,report['totalTriangles']
(out/'supplied-dhd-build-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
