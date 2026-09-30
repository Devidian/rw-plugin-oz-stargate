#!/usr/bin/env python3
"""Preserve the authored glyph faces as a contrasting child of the rotating ring.

Offline dependencies: numpy 2.5.3, mapbox-earcut 2.1.0 (same as gate builders).
The simplified ring stays intact; a 1mm front offset avoids coplanar flicker.
"""
import argparse
import hashlib
import json
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

import numpy as np
from triangulate_obj import read_obj

parser = argparse.ArgumentParser()
parser.add_argument('archive', type=Path)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
source_hash = hashlib.sha256(args.archive.read_bytes()).hexdigest()
assert source_hash == '3a976d4dfd234bc80024a11e3ade84710b4d917946decd9de6ec182bc7a5b641'
with ZipFile(args.archive) as archive:
    lines = archive.read('SG_MW_OBJ/SG_MW_InnerRing_Earth_Giza.obj').splitlines(keepends=True)
vertices = [line for line in lines if line.startswith(b'v ')]
faces = []
material = b''
for line in lines:
    if line.startswith(b'usemtl '):
        material = line.split()[1]
    elif line.startswith(b'f ') and material == b'Inner_Ring_Glyph_Face':
        faces.append(line)
assert len(faces) == 682
points, triangles, _ = read_obj(BytesIO(b''.join(vertices + faces)))
points *= .9
points[:, 1] += 3.073437 - 3.075  # Accepted body floor offset minus rotating ring pivot.
points[:, 2] -= .001  # Source front is -Z. Keep the original sculpted face relief.
points = np.round(points, 6)
assert len(triangles) == 2625
indices = sorted(set(triangles.ravel().tolist()))
mapping = {v: i + 1 for i, v in enumerate(indices)}
output = ['# CC0 David Gian-Cursio 2021; authored glyph faces, 1mm front offset.',
          'o ring-glyphs']
output += ['v %.6f %.6f %.6f' % tuple(points[i]) for i in indices]
output += ['f ' + ' '.join(str(mapping[int(i)]) for i in face) for face in triangles]
data = ('\n'.join(output) + '\n').encode()
root = Path(__file__).resolve().parents[2] / 'src/assets/models/milkyway'
report = {'archiveSha256': source_hash, 'material': 'Inner_Ring_Glyph_Face',
          'sourcePolygons': len(faces), 'triangles': len(triangles), 'vertices': len(indices),
          'frontOffset': .001, 'sha256': hashlib.sha256(data).hexdigest()}
for name, content in [('articulated-ring-glyphs.obj', data),
                      ('ring-glyph-report.json', (json.dumps(report, indent=2) + '\n').encode())]:
    path = root / name
    if args.check:
        assert path.read_bytes() == content, path
    else:
        path.write_bytes(content)
print(json.dumps(report, indent=2))
