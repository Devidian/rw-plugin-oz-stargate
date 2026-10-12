#!/usr/bin/env python3
"""Build four gate LODs from the accepted body and Unity-extracted FBX moving parts."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import shutil
from collections import Counter

import numpy as np
import pymeshlab

SOURCE_SHA256 = '277b775a102f19d17a5a8383b065a04d5472e43dd456371667ef5230cabb9e13'
BODY_SHA256 = '7b8636c2348cabafebf56c5f64707d02977334f8ca6f0b1d0866e7735e7de0c3'
SOURCE_TRIANGLES = {'body': 4292991, 'ring': 74633, 'chevron-v': 2796, 'chevron-block': 3216}
# The body includes nine rear V housings and strips already supplied by the
# separately animated Chevron parts. Exclude only those LOD0 duplicates to
# avoid overlapping stone and light faces on the rear Chevrons.
LOD0_BODY_EXCLUDE = frozenset({'Chevron', 'Chevron Light'})

TARGETS = (
    {'body': 220000, 'ring': 74633, 'chevron-v': 2796, 'chevron-block': 3216},
    {'body': 120000, 'ring': 74633, 'chevron-v': 2796, 'chevron-block': 3216},
    {'body': 45000, 'ring': 8000, 'chevron-v': 400, 'chevron-block': 400},
    {'body': 15000, 'ring': 2500, 'chevron-v': 140, 'chevron-block': 140},
)


def read_source(path):
    with path.open('rb') as stream:
        if stream.read(4) != b'1MGS':
            raise ValueError(f'Invalid SG mesh header: {path}')
        count, groups = struct.unpack('<II', stream.read(8))
        vertices = np.fromfile(stream, dtype='<f4', count=count * 5).reshape(-1, 5)
        if len(vertices) != count or not np.isfinite(vertices).all():
            raise ValueError(f'Invalid vertices: {path}')
        result = []
        for _ in range(groups):
            length = read_7bit_int(stream)
            material = stream.read(length).decode('utf-8')
            indices = struct.unpack('<I', stream.read(4))[0]
            faces = np.fromfile(stream, dtype='<i4', count=indices).reshape(-1, 3)
            if np.any(faces < 0) or np.any(faces >= count):
                raise ValueError(f'Invalid indices: {path} / {material}')
            result.append((material, faces))
        if stream.read(1):
            raise ValueError(f'Unexpected data at end of {path}')
    return vertices, result


def read_7bit_int(stream):
    value = 0
    for shift in range(0, 35, 7):
        byte = stream.read(1)
        if not byte:
            raise EOFError('Truncated material name')
        value |= (byte[0] & 127) << shift
        if byte[0] < 128:
            return value
    raise ValueError('Material name too long')


def allocate(counts, budget):
    """Favor small authored material surfaces while meeting a whole-part budget."""
    weights = np.power(counts, .85)
    targets = np.minimum(counts, np.maximum(8, np.rint(budget * weights / weights.sum()).astype(int)))
    while targets.sum() > budget:
        eligible = np.flatnonzero(targets > 8)
        if not len(eligible):
            break
        index = eligible[np.argmax(targets[eligible])]
        targets[index] -= min(int(targets.sum() - budget), max(1, int(targets[index] // 10)))
    return targets


def simplify(points, faces, target, preserve_boundary):
    if len(faces) <= target:
        return points, faces
    meshes = pymeshlab.MeshSet()
    meshes.add_mesh(pymeshlab.Mesh(vertex_matrix=points, face_matrix=faces))
    meshes.meshing_remove_duplicate_vertices()
    meshes.meshing_remove_duplicate_faces()
    meshes.meshing_remove_null_faces()
    meshes.meshing_decimation_quadric_edge_collapse(
        targetfacenum=int(target), preserveboundary=preserve_boundary,
        preservenormal=True, optimalplacement=False, planarquadric=True, qualitythr=.3)
    result = meshes.current_mesh()
    return result.vertex_matrix(), result.face_matrix()


def write_obj(path, points, faces, offset_y, scale=.9):
    # Source FBX root already carries its authored 0.9 scale. Apply that once;
    # the existing plugin parent supplies two Rising World units per metre.
    points = points * scale
    points[:, 1] += offset_y
    with path.open('w') as out:
        out.write('# Derived from David Gian-Cursio SG_MilkyWay.fbx (CC0)\n')
        for x, y, z in points:
            out.write(f'v {x:.6f} {y:.6f} {z:.6f}\n')
        # Dominant-axis 3.6m box projection follows the accepted gate material.
        uv = {}
        uv_lines = []
        face_lines = []
        for face in faces:
            p = points[face]
            normal = np.cross(p[1] - p[0], p[2] - p[0])
            axis = int(np.argmax(np.abs(normal)))
            sign = 1 if normal[axis] >= 0 else -1
            refs = []
            for index in face:
                index = int(index)
                key = index, axis, sign
                if key not in uv:
                    x, y, z = points[index]
                    y -= offset_y
                    u, v = ((z * sign, y) if axis == 0 else
                            (x, z * sign) if axis == 1 else (x * sign, y))
                    uv[key] = len(uv_lines) + 1
                    uv_lines.append(f'vt {.5 + u / 3.6:.7f} {.5 + v / 3.6:.7f}\n')
                refs.append(f'{index + 1}/{uv[key]}')
            face_lines.append('f ' + ' '.join(refs) + '\n')
        out.writelines(uv_lines)
        out.writelines(face_lines)


def remove_rounded_degenerate(points, faces, offset_y):
    rounded = np.round(points * .9, 6)
    rounded[:, 1] = np.round(rounded[:, 1] + offset_y, 6)
    triangles = rounded[faces]
    twice_area = np.linalg.norm(np.cross(triangles[:, 1] - triangles[:, 0],
                                        triangles[:, 2] - triangles[:, 0]), axis=1)
    return faces[twice_area > 1e-12]


def read_body(path):
    vertices, faces = [], []
    with path.open() as stream:
        for line in stream:
            if line.startswith('v '):
                vertices.append([float(x) for x in line.split()[1:4]])
            elif line.startswith('f '):
                faces.append([int(x.split('/')[0]) - 1 for x in line.split()[1:4]])
    return np.asarray(vertices, dtype=np.float64), np.asarray(faces, dtype=np.int32)


def role(part, material):
    if part == 'ring':
        return ('Inner Ring Glyph Face' if 'Glyph Face' in material else
                'Inner Ring Grooves' if 'Groove' in material or 'Recess' in material else
                'Inner Ring Main')
    if part == 'chevron-v':
        return 'Chevron Light' if 'Light' in material else 'Chevron A'
    return 'Chevron Block Light' if 'Light' in material else 'Chevron Block Arm'


def material_order(part):
    return {'ring': ('Inner Ring Glyph Face', 'Inner Ring Main', 'Inner Ring Grooves'),
            'chevron-v': ('Chevron Light', 'Chevron A'),
            'chevron-block': ('Chevron Block Light', 'Chevron Block Arm')}[part]


def build_part(vertices, groups, part, output, report):
    source_faces = np.concatenate([faces for _, faces in groups]).astype(np.int32)
    source_roles = np.concatenate([[role(part, name)] * len(faces) for name, faces in groups])
    # Keep the authored coordinates fixed: a simplified face can then inherit a
    # material from its original vertices without splitting geometry at seams.
    votes = {}
    for face, name in zip(source_faces, source_roles):
        for index in face:
            key = tuple(np.round(vertices[index, :3], 6))
            votes.setdefault(key, Counter())[name] += 1
    labels = {key: counts.most_common(1)[0][0] for key, counts in votes.items()}
    # Quadric reduction can place a vertex between source coordinates even with
    # optimalplacement disabled. Resolve those few vertices locally by distance.
    cell_size = .1
    cells = {}
    for key in labels:
        cell = tuple(int(np.floor(x / cell_size)) for x in key)
        cells.setdefault(cell, []).append(key)
    def nearest_role(point):
        origin = tuple(int(np.floor(x / cell_size)) for x in point)
        for radius in range(1, 8):
            candidates = []
            for x in range(origin[0] - radius, origin[0] + radius + 1):
                for y in range(origin[1] - radius, origin[1] + radius + 1):
                    for z in range(origin[2] - radius, origin[2] + radius + 1):
                        candidates.extend(cells.get((x, y, z), ()))
            if candidates:
                closest = min(candidates, key=lambda p: sum((a - b) ** 2 for a, b in zip(p, point)))
                return labels[closest]
        raise ValueError(f'No source vertex near {part}: {point}')
    for level, target in enumerate(TARGETS):
        if level <= 1:
            points, faces = vertices[:, :3].astype(np.float64), source_faces
            face_roles = source_roles
        else:
            points, faces = simplify(vertices[:, :3].astype(np.float64), source_faces,
                                     target[part], False)
            point_roles = []
            for point in points:
                key = tuple(np.round(point, 6))
                point_roles.append(labels[key] if key in labels else nearest_role(point))
            face_roles = np.asarray([Counter(point_roles[i] for i in face).most_common(1)[0][0]
                                     for face in faces])
        for index, name in enumerate(material_order(part)):
            selected = faces[face_roles == name]
            selected = remove_rounded_degenerate(points, selected, 0)
            if not len(selected):
                raise ValueError(f'Lost {part} {name} in LOD{level}')
            filename = f'{part}-{index:02d}.obj'
            write_obj(output / f'LOD{level}' / filename, points.copy(), selected, 0)
            report['lods'][level]['entries'].append({
                'part': part, 'file': filename, 'material': name,
                'triangles': len(selected), 'sourceTriangles': sum(
                    len(group_faces) for material, group_faces in groups if role(part, material) == name)})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('fbx', type=Path)
    parser.add_argument('extracted', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--accepted-body', type=Path, default=Path(__file__).resolve().parents[2] /
                        'src/assets/models/milkyway/textured-articulated-body.obj')
    args = parser.parse_args()
    digest = hashlib.sha256(args.fbx.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise ValueError('Unexpected SG_MilkyWay.fbx SHA256: ' + digest)
    body_digest = hashlib.sha256(args.accepted_body.read_bytes()).hexdigest()
    if body_digest != BODY_SHA256:
        raise ValueError('Unexpected accepted body SHA256: ' + body_digest)
    args.output.mkdir(parents=True, exist_ok=True)
    report = {'sourceSha256': digest, 'acceptedBodySha256': body_digest,
              'targets': TARGETS, 'lods': [{'level': level, 'entries': []} for level in range(4)]}
    body_points, body_faces = read_body(args.accepted_body)
    source_body, source_groups = read_source(args.extracted / 'body.sgmesh')
    source_body_total = sum(len(faces) for _, faces in source_groups)
    if source_body_total != SOURCE_TRIANGLES['body']:
        raise ValueError('Unexpected source body triangle count')
    if not LOD0_BODY_EXCLUDE.issubset({name for name, _ in source_groups}):
        raise ValueError('Missing LOD0 exclusion group in source FBX')
    source_body_faces = np.concatenate([faces for name, faces in source_groups
                                        if name not in LOD0_BODY_EXCLUDE]).astype(np.int32)
    report['lod0ExcludedBodyGroups'] = sorted(LOD0_BODY_EXCLUDE)
    report['lod0SourceBodyTrianglesAfterExclusion'] = len(source_body_faces)
    for level, target in enumerate(TARGETS):
        folder = args.output / f'LOD{level}'
        folder.mkdir(exist_ok=True)
        path = folder / 'body-00.obj'
        if level == 0:
            points, faces = simplify(source_body[:, :3].astype(np.float64), source_body_faces,
                                     target['body'], True)
            faces = remove_rounded_degenerate(points, faces, 3.075)
            centres = points[faces].mean(axis=1) * .9
            radii = np.linalg.norm(centres[:, :2], axis=1)
            if radii.min() < 2.25:
                raise ValueError(f'LOD0 body intrudes into the passage: '
                                 f'{int((radii < 2.25).sum())} centroids, min radius {radii.min():.4f}')
            write_obj(path, points, faces, 3.075)
            count = len(faces)
        elif level == 1:
            shutil.copyfile(args.accepted_body, path)
            count = len(body_faces)
        else:
            points, faces = simplify(body_points, body_faces, target['body'], False)
            faces = faces[np.linalg.norm(np.cross(points[faces[:, 1]] - points[faces[:, 0]],
                                                  points[faces[:, 2]] - points[faces[:, 0]]), axis=1) > 1e-12]
            write_obj(path, points, faces, 0, scale=1)
            count = len(faces)
        report['lods'][level]['entries'].append({
            'part': 'body', 'file': path.name, 'material': 'Stone',
            'triangles': count, 'sourceTriangles': len(source_body_faces) if level == 0 else len(body_faces)})
    for part in ('ring', 'chevron-v', 'chevron-block'):
        vertices, groups = read_source(args.extracted / f'{part}.sgmesh')
        count = sum(len(faces) for _, faces in groups)
        if count != SOURCE_TRIANGLES[part]:
            raise ValueError(f'Unexpected source triangles for {part}: {count}')
        build_part(vertices, groups, part, args.output, report)
    for level, lod in enumerate(report['lods']):
        entries = lod['entries']
        total = sum(e['triangles'] for e in entries if e['part'] in ('body', 'ring'))
        total += 9 * sum(e['triangles'] for e in entries if e['part'].startswith('chevron-'))
        total += 9 * sum(e['triangles'] for e in entries if e['part'] == 'chevron-v')
        if total > (375000, 275000, 65000, 22000)[level] * 1.1:
            raise ValueError(f'LOD{level} over triangle budget: {total}')
        lod['trianglesPerGate'] = total
    (args.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
