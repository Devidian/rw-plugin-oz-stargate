#!/usr/bin/env python3
"""Build an optional Milky Way sized surge mesh from the user-supplied LWO3 demo.

The LightWave patch mesh is triangulated and subdivided offline. Runtime timing
and texture remain owned by WormholePose and the existing surge atlas.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

import numpy as np
import pymeshlab

SOURCE_NAME = 'KawooshMorph_20211124A.lwo'
TARGET_RADIUS = 2.31


def read_lwo(path):
    raw = path.read_bytes()
    if raw[:4] != b'FORM' or raw[8:12] != b'LWO3':
        raise ValueError('Expected the supplied LightWave LWO3 demo')
    offset = 12
    points = []
    polygons = []
    morph_names = []
    while offset + 8 <= len(raw):
        tag = raw[offset:offset + 4]
        length = int.from_bytes(raw[offset + 4:offset + 8], 'big')
        chunk = raw[offset + 8:offset + 8 + length]
        if tag == b'PNTS':
            points = list(struct.iter_unpack('>fff', chunk))
        elif tag == b'POLS':
            if chunk[:4] != b'PTCH':
                raise ValueError('Expected patch polygons')
            index = 4
            while index < len(chunk):
                count = struct.unpack_from('>H', chunk, index)[0] & 1023
                index += 2
                polygon = []
                for _ in range(count):
                    if chunk[index] == 255:
                        polygon.append(int.from_bytes(chunk[index + 1:index + 4], 'big'))
                        index += 4
                    else:
                        polygon.append(int.from_bytes(chunk[index:index + 2], 'big'))
                        index += 2
                polygons.append(polygon)
        elif tag == b'VMAP' and chunk[:4] == b'MORF':
            end = chunk.index(0, 6)
            morph_names.append(chunk[6:end].decode('utf-8'))
        offset += 8 + length + (length & 1)
    if len(points) != 133 or len(polygons) != 132 or set(morph_names) != {'Kawoosh_Start', 'Kawoosh_End'}:
        raise ValueError('Unexpected Kawoosh mesh or morph map count')
    faces = []
    for polygon in polygons:
        if len(polygon) not in (3, 4) or max(polygon) >= len(points):
            raise ValueError('Unexpected Kawoosh patch')
        faces.extend((polygon[0], polygon[i], polygon[i + 1]) for i in range(1, len(polygon) - 1))
    if len(faces) != 252:
        raise ValueError('Unexpected Kawoosh source triangle count')
    return np.array(points, dtype=np.float64), np.array(faces, dtype=np.int32), raw


def build(source, output):
    points, faces, raw = read_lwo(source)
    if not 2.50 < np.linalg.norm(points[:, :2], axis=1).max() < 2.51 or not 4.19 < points[:, 2].max() < 4.20:
        raise ValueError('Unexpected LightWave source dimensions')
    meshes = pymeshlab.MeshSet()
    meshes.add_mesh(pymeshlab.Mesh(vertex_matrix=points, face_matrix=faces))
    meshes.meshing_surface_subdivision_loop(iterations=2)
    mesh = meshes.current_mesh()
    vertices = mesh.vertex_matrix()
    triangles = mesh.face_matrix()
    source_radius = np.linalg.norm(vertices[:, :2], axis=1).max()
    source_depth = vertices[:, 2].max()
    if source_radius < 2.3 or source_depth < 4:
        raise ValueError('Unexpected subdivided Kawoosh dimensions')
    # LightWave's positive Z Kawoosh becomes the plugin's forward (-Z) surge.
    # Normalize depth because WormholePose supplies the animated 0..3.78m extent.
    factor = TARGET_RADIUS / source_radius
    vertices[:, 0] *= factor
    vertices[:, 1] *= factor
    vertices[:, 2] /= -source_depth
    # Reflection across Z reverses winding.
    triangles = triangles[:, [0, 2, 1]]
    surface = np.cross(vertices[triangles[:, 1]] - vertices[triangles[:, 0]],
                       vertices[triangles[:, 2]] - vertices[triangles[:, 0]])
    if np.any(np.linalg.norm(surface, axis=1) < 1e-12):
        raise ValueError('Degenerate subdivided Kawoosh triangle')
    uv = vertices[:, :2] / (2 * TARGET_RADIUS) + .5
    lines = ['# User-supplied LightWave movie Kawoosh, remapped to Milky Way surge bounds.',
             '# Source stays outside plugin; comparison candidate only.',
             'o wormhole-surge-lightwave']
    lines += ['v %.7f %.7f %.7f' % tuple(vertex) for vertex in vertices]
    lines += ['vt %.7f %.7f' % tuple(coord) for coord in uv]
    lines += ['f ' + ' '.join(f'{int(i)+1}/{int(i)+1}' for i in triangle) for triangle in triangles]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text('\n'.join(lines) + '\n')
    report = {'source': SOURCE_NAME, 'sourceSha256': hashlib.sha256(raw).hexdigest(),
              'sourceVertices': len(points), 'sourceTriangles': len(faces),
              'subdividedVertices': len(vertices), 'subdividedTriangles': len(triangles),
              'targetRadius': TARGET_RADIUS, 'normalizedDepth': 1.0,
              'outputSha256': hashlib.sha256(output.read_bytes()).hexdigest()}
    output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    build(args.source, args.output)
