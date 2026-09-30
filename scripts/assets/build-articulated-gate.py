#!/usr/bin/env python3
"""Losslessly split the accepted CC0 preview into native animation meshes (stdlib)."""
from decimal import Decimal
import hashlib
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2] / 'src/assets/models/milkyway'
source = root / 'milkyway-preview.obj'
assert hashlib.sha256(source.read_bytes()).hexdigest() == '54702a7922cbcb84079f353d3b79108511f8f2f6827e1f04f94320e56a4298b3'
vertices = []
parts = {}
for line in source.read_text().splitlines():
    if line.startswith('o '):
        name = line[2:]
        parts[name] = []
    elif line.startswith('v '):
        vertices.append(tuple(Decimal(v) for v in line.split()[1:]))
    elif line.startswith('f '):
        parts[name].append(tuple(int(i) - 1 for i in line.split()[1:]))
meshes = [('body', ['SG_MW_Main_0'], Decimal(0)),
          ('ring', ['SG_MW_InnerRing_Earth_Giza_0'], Decimal('3.075'))]
meshes += [(f'chevron-base-{i}', [f'SG_MW_Chevron_{i}'], Decimal(0)) for i in range(9)]
meshes += [(f'chevron-block-{i}', [f'SG_MW_ChevronBlock_{i}'], Decimal(0)) for i in range(9)]
count = 0
for filename, names, pivot in meshes:
    faces = [f for n in names for f in parts[n]]
    indices = sorted({i for f in faces for i in f})
    mapping = {old: new + 1 for new, old in enumerate(indices)}
    lines = ['# CC0 David Gian-Cursio 2021; lossless split of accepted optimized preview.', 'o ' + filename]
    for i in indices:
        x, y, z = vertices[i]
        lines.append(f'v {x:.6f} {y-pivot:.6f} {z:.6f}')
    lines += ['f ' + ' '.join(str(mapping[i]) for i in f) for f in faces]
    data = '\n'.join(lines) + '\n'
    target = root / f'articulated-{filename}.obj'
    if '--check' in sys.argv:
        assert target.read_text() == data, f'Stale or changed mesh: {target}'
    else:
        target.write_text(data)
    # Prove each remapped face reconstructs precisely the accepted source geometry.
    transformed = [tuple(Decimal(v) for v in l.split()[1:]) for l in lines if l.startswith('v ')]
    for face in faces:
        for i in face:
            x, y, z = transformed[mapping[i]-1]
            assert (x, y+pivot, z) == vertices[i]
    count += len(faces)
assert count == sum(map(len, parts.values())) == 156395
print(f'Articulated meshes verified: {len(meshes)} assets, {count} original triangles; rest geometry preserved exactly')
