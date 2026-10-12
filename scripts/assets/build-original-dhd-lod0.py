#!/usr/bin/env python3
"""Export the supplied Blender 2.71 DHD control mesh without decimation.

This reads mesh datablocks directly so the build does not depend on a particular
Blender installation. The authored base Mirror is applied; Subsurf is omitted.
"""
import argparse
import hashlib
import json
import re
import struct
from pathlib import Path
from zipfile import ZipFile

ARCHIVE_SHA = '8d9f9a91305d9dd5a4245f12644c9c01c673cb6596ecc148b041fd4a303590aa'
BLEND_SHA = 'a90ada3743ecb7cbbde078359dbb00f2e67be585f3c449583ddb432b7992ce9e'
SOURCE_Z_MIN = -2.597103
SOURCE_Y_CENTER = -3.550
SCALE = .53


class Blend:
    def __init__(self, data):
        if data[:12] != b'BLENDER-v271':
            raise ValueError('Expected the supplied 64-bit little-endian Blender 2.71 file')
        self.data = data
        self.blocks = []
        offset = 12
        while offset + 24 <= len(data):
            code, size, address, sdna, count = struct.unpack_from('<4sIQII', data, offset)
            self.blocks.append((code, size, address, sdna, count, offset + 24))
            offset += 24 + size
            if code == b'ENDB':
                break
        self.by_address = {b[2]: b for b in self.blocks}
        self._read_dna()

    def _read_dna(self):
        data = self.data
        block = next(b for b in self.blocks if b[0] == b'DNA1')
        offset = block[5]
        assert data[offset:offset + 4] == b'SDNA'
        offset += 4
        def strings(label):
            nonlocal offset
            assert data[offset:offset + 4] == label
            offset += 4
            count = struct.unpack_from('<I', data, offset)[0]
            offset += 4
            result = []
            for _ in range(count):
                end = data.index(0, offset)
                result.append(data[offset:end].decode('latin1'))
                offset = end + 1
            offset = (offset + 3) & ~3
            return result
        names = strings(b'NAME')
        types = strings(b'TYPE')
        assert data[offset:offset + 4] == b'TLEN'
        offset += 4
        lengths = struct.unpack_from('<' + 'H' * len(types), data, offset)
        offset = (offset + 2 * len(types) + 3) & ~3
        assert data[offset:offset + 4] == b'STRC'
        offset += 4
        count = struct.unpack_from('<I', data, offset)[0]
        offset += 4
        self.structs = []
        for _ in range(count):
            type_index, field_count = struct.unpack_from('<HH', data, offset)
            offset += 4
            fields = []
            for _ in range(field_count):
                field_type, field_name = struct.unpack_from('<HH', data, offset)
                offset += 4
                fields.append((types[field_type], names[field_name], lengths[field_type]))
            self.structs.append((types[type_index], lengths[type_index], fields))

    def layout(self, type_name):
        index = next(i for i, (name, _, _) in enumerate(self.structs) if name == type_name)
        name, size, fields = self.structs[index]
        position = 0
        offsets = {}
        for field_type, declaration, length in fields:
            dimensions = [int(n) for n in re.findall(r'\[(\d+)\]', declaration)]
            pointer = '*' in declaration.split('[')[0]
            element_size = 8 if pointer else length
            alignment = 8 if pointer else min(element_size, 8)
            position = (position + alignment - 1) // alignment * alignment
            offsets[declaration] = position
            for dimension in dimensions:
                element_size *= dimension
            position += element_size
        if position != size:
            raise ValueError(f'Invalid {name} DNA layout: {position} != {size}')
        return index, offsets

    def objects(self):
        data = self.data
        object_index, object_layout = self.layout('Object')
        mesh_index, mesh_layout = self.layout('Mesh')
        result = {}
        for block in self.blocks:
            if block[3] != object_index:
                continue
            start = block[5]
            name = data[start + 32:start + 98].split(b'\0')[0].decode('latin1')
            address = struct.unpack_from('<Q', data, start + object_layout['*data'])[0]
            mesh = self.by_address.get(address)
            if mesh is None or mesh[3] != mesh_index:
                continue
            matrix = struct.unpack_from('<16f', data, start + object_layout['obmat[4][4]'])
            mesh_start = mesh[5]
            count = struct.unpack_from('<i', data, mesh_start + mesh_layout['totvert'])[0]
            vertex_address = struct.unpack_from('<Q', data, mesh_start + mesh_layout['*mvert'])[0]
            vertex_block = self.by_address[vertex_address]
            vertex_size = self.layout('MVert')[1]['co[3]']
            # Blender 2.71 MVert is 20 bytes. DNA validates its definition.
            assert self.structs[self.layout('MVert')[0]][1] == 20 and vertex_size == 0
            vertices = [struct.unpack_from('<3f', data, vertex_block[5] + i * 20)
                        for i in range(count)]
            loop_count = struct.unpack_from('<i', data, mesh_start + mesh_layout['totloop'])[0]
            loop_address = struct.unpack_from('<Q', data, mesh_start + mesh_layout['*mloop'])[0]
            loop_block = self.by_address[loop_address]
            loops = [struct.unpack_from('<I', data, loop_block[5] + i * 8)[0]
                     for i in range(loop_count)]
            poly_count = struct.unpack_from('<i', data, mesh_start + mesh_layout['totpoly'])[0]
            poly_address = struct.unpack_from('<Q', data, mesh_start + mesh_layout['*mpoly'])[0]
            poly_block = self.by_address[poly_address]
            faces = []
            for i in range(poly_count):
                first, length = struct.unpack_from('<ii', data, poly_block[5] + i * 12)
                corners = loops[first:first + length]
                faces += [(corners[0], corners[j], corners[j + 1])
                          for j in range(1, length - 1)]
            result[name.removeprefix('OB')] = (vertices, faces, matrix)
        return result


def transform(vertex, matrix):
    x, y, z = vertex
    world_x = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]
    world_y = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]
    world_z = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]
    return (round(world_x * SCALE, 6), round((world_z - SOURCE_Z_MIN) * SCALE, 6),
            round(-(world_y - SOURCE_Y_CENTER) * SCALE, 6))


def connected(faces, points):
    parent = list(range(len(points)))
    def root(index):
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index
    for a, b, c in faces:
        parent[root(b)] = root(a)
        parent[root(c)] = root(a)
    groups = {}
    for face in faces:
        groups.setdefault(root(face[0]), []).append(face)
    return list(groups.values())


def centre(faces, points):
    indices = {i for face in faces for i in face}
    return tuple(sum(points[i][axis] for i in indices) / len(indices) for axis in range(3))


def nondegenerate(face, points):
    a, b, c = (points[i] for i in face)
    u = tuple(b[i] - a[i] for i in range(3))
    v = tuple(c[i] - a[i] for i in range(3))
    cross = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2],
             u[0] * v[1] - u[1] * v[0])
    return sum(value * value for value in cross) >= 1e-16


def write_obj(path, points, faces, texture=False):
    referenced = sorted({i for face in faces for i in face})
    remap = {old: index + 1 for index, old in enumerate(referenced)}
    lines = ['# Unreduced DHD LOD0 from user-supplied DHD.blend; Subsurf omitted.',
             '# See supplied-dhd-SOURCE-NOTICE.txt and supplied-dhd-LICENSE.html.',
             'o ' + path.stem]
    lines += ['v %.6f %.6f %.6f' % points[i] for i in referenced]
    if texture:
        uvmap = {}
        uvs = []
        output_faces = []
        for face in faces:
            a, b, c = (points[i] for i in face)
            u = tuple(b[i] - a[i] for i in range(3))
            v = tuple(c[i] - a[i] for i in range(3))
            normal = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2],
                      u[0] * v[1] - u[1] * v[0])
            axis = max(range(3), key=lambda i: abs(normal[i]))
            sign = 1 if normal[axis] >= 0 else -1
            entries = []
            for index in face:
                key = (index, axis, sign)
                if key not in uvmap:
                    x, y, z = points[index]
                    pair = (z * sign, y) if axis == 0 else (x, z * sign) if axis == 1 else (x * sign, y)
                    uvs.append(tuple(.5 + value / 1.8 for value in pair))
                    uvmap[key] = len(uvs)
                entries.append(f'{remap[index]}/{uvmap[key]}')
            output_faces.append('f ' + ' '.join(entries))
        lines += ['vt %.7f %.7f' % uv for uv in uvs]
        lines += output_faces
    else:
        lines += ['f %d %d %d' % tuple(remap[i] for i in face) for face in faces]
    content = ('\n'.join(lines) + '\n').encode()
    path.write_bytes(content)
    return {'triangles': len(faces), 'vertices': len(referenced), 'bytes': len(content),
            'sha256': hashlib.sha256(content).hexdigest()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('archive', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if hashlib.sha256(args.archive.read_bytes()).hexdigest() != ARCHIVE_SHA:
        raise ValueError('Unexpected DHD source archive')
    with ZipFile(args.archive) as source:
        blend = source.read('DHD.blend')
    if hashlib.sha256(blend).hexdigest() != BLEND_SHA:
        raise ValueError('Unexpected DHD.blend source')
    objects = Blend(blend).objects()
    wanted = {
        'body': ('DHD base', 'DHD flat top', 'DHD ring', 'DHD symbol house'),
        'accents': ('DHD ring accents', 'DHD activation dome ring'),
        'keys': ('DHD buttons',),
        'symbols': ('DHD symbols inner', 'DHD symbols outer'),
        'activate': ('DHD activation dome',),
    }
    if set(objects) != {n for group in wanted.values() for n in group} | {'Light planes'}:
        raise ValueError('Unexpected DHD source object list')
    args.output.mkdir(parents=True, exist_ok=True)
    report = {'sourceArchiveSha256': ARCHIVE_SHA, 'sourceBlendSha256': BLEND_SHA,
              'scale': SCALE, 'mirror': 'DHD base local X; no subdivision or decimation',
              'parts': {}, 'groups': {}}
    assembled = {}
    for part, names in wanted.items():
        points = []
        faces = []
        for name in names:
            vertices, triangles, matrix = objects[name]
            copies = [(vertices, triangles)]
            if name == 'DHD base':
                copies.append(([( -x, y, z) for x, y, z in vertices],
                               [(a, c, b) for a, b, c in triangles]))
            for copy_vertices, copy_triangles in copies:
                offset = len(points)
                points.extend(transform(vertex, matrix) for vertex in copy_vertices)
                faces.extend(tuple(offset + i for i in face) for face in copy_triangles)
        faces = [face for face in faces if nondegenerate(face, points)]
        assembled[part] = (points, faces)
        report['parts'][part] = write_obj(args.output / f'original-dhd-{part}.obj',
                                         points, faces, part in ('body', 'accents'))
    # Component centres from the accepted reduction identify the seven circuits.
    accepted = json.loads((Path(__file__).resolve().parents[2] /
                          'src/assets/models/dhd/supplied-dhd-build-report.json').read_text())
    reference = [accepted['keyGroups'][f'supplied-dhd-keys-{i}']['center'] for i in range(7)]
    key_points, key_faces = assembled['keys']
    components = connected(key_faces, key_points)
    if len(components) != 38:
        raise ValueError(f'Expected 38 authored key components, found {len(components)}')
    candidates = [(centre(faces, key_points), faces) for faces in components]
    selected = []
    for target in reference:
        item = min(candidates, key=lambda item: sum((item[0][axis] - target[axis]) ** 2
                                                    for axis in range(3)))
        selected.append(item)
        candidates.remove(item)
    for part in ('keys', 'symbols'):
        points, faces = assembled[part]
        if part == 'keys':
            groups = [item[1] for item in selected]
            groups.append([face for _, component in candidates for face in component])
        else:
            groups = [[] for _ in range(8)]
            for component in connected(faces, points):
                location = centre(component, points)
                closest = min(range(7), key=lambda i: sum(
                    (location[axis] - selected[i][0][axis]) ** 2 for axis in range(3)))
                # Symbols beyond the selected circuits are assigned to the unlit mesh.
                all_keys = [item[0] for item in selected + candidates]
                closest_key = min(range(38), key=lambda i: sum(
                    (location[axis] - all_keys[i][axis]) ** 2 for axis in range(3)))
                groups[closest if closest_key < 7 else 7].extend(component)
        if sum(map(len, groups)) != len(faces):
            raise ValueError(f'Lost {part} faces while partitioning')
        for i, group in enumerate(groups):
            suffix = str(i) if i < 7 else 'unlit'
            name = f'original-dhd-{part}-{suffix}'
            report['groups'][name] = write_obj(args.output / f'{name}.obj', points, group)
    report['totalTriangles'] = sum(part['triangles'] for part in report['parts'].values())
    report['sourceTrianglesBeforeDegenerateFilter'] = 122136
    if report['totalTriangles'] != 122129:
        raise ValueError(f'Unexpected source triangle count: {report["totalTriangles"]}')
    (args.output / 'original-dhd-build-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Original DHD LOD0:', report['totalTriangles'], 'triangles')


if __name__ == '__main__':
    main()
