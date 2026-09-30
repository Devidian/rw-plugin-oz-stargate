#!/usr/bin/env python3
"""Original parametric radial DHD console. No third-party source geometry."""
import argparse
import hashlib
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2] / 'src/assets/models/dhd'
root.mkdir(parents=True, exist_ok=True)

class Mesh:
    def __init__(self, name):
        self.name, self.vertices, self.faces = name, [], []

    def face(self, *points):
        for i in range(1, len(points) - 1):
            triangle = (points[0], points[i], points[i + 1])
            a, b, c = triangle
            ab = [b[j] - a[j] for j in range(3)]
            ac = [c[j] - a[j] for j in range(3)]
            cross = (ab[1] * ac[2] - ab[2] * ac[1],
                     ab[2] * ac[0] - ab[0] * ac[2],
                     ab[0] * ac[1] - ab[1] * ac[0])
            if sum(n * n for n in cross) < 1e-16:
                continue
            ids = []
            for point in triangle:
                assert all(math.isfinite(n) for n in point)
                self.vertices.append(tuple(round(n, 6) for n in point))
                ids.append(len(self.vertices))
            self.faces.append(tuple(ids))

    def ring(self, inner, outer, y_inner, y_outer, segments=96):
        for i in range(segments):
            a, b = 2 * math.pi * i / segments, 2 * math.pi * (i + 1) / segments
            self.face(rad(inner, y_inner, a), rad(inner, y_inner, b), rad(outer, y_outer, b), rad(outer, y_outer, a))

    def wall(self, radius_bottom, radius_top, bottom, top, segments=96):
        for i in range(segments):
            a, b = 2 * math.pi * i / segments, 2 * math.pi * (i + 1) / segments
            self.face(rad(radius_bottom, bottom, a), rad(radius_top, top, a),
                      rad(radius_top, top, b), rad(radius_bottom, bottom, b))

    def sector(self, inner, outer, y, start, end):
        self.face(rad(inner, y, start), rad(inner, y, end), rad(outer, y, end), rad(outer, y, start))

    def obj(self):
        lines = ['# Original OZ Stargate parametric DHD console, 2026', 'o ' + self.name]
        lines += ['v %.6f %.6f %.6f' % p for p in self.vertices]
        lines += ['f %d %d %d' % f for f in self.faces]
        return ('\n'.join(lines) + '\n').encode()

def rad(r, y, a):
    return (r * math.cos(a), y, r * math.sin(a))

# Author in local metres. Runtime doubles coordinates to game units.
body = Mesh('dhd-body')
body.ring(0, .34, .015, .015)
body.wall(.34, .24, .015, .77)
body.ring(.24, .38, .77, .83)
body.wall(.38, .72, .83, 1.12)
body.ring(.31, .72, 1.12, 1.12)
body.ring(.31, .75, 1.09, 1.09)
body.wall(.75, .75, 1.09, 1.12)
body.ring(.295, .31, 1.125, 1.125)
body.ring(.095, .29, 1.115, 1.115)
body.wall(.095, .095, 1.11, 1.14)
for i in range(8):
    a = 2 * math.pi * i / 8
    body.sector(.26, .55, .90, a - .035, a + .035)

keys = Mesh('dhd-keys')
key_count = 36
for i in range(key_count):
    start = 2 * math.pi * (i + .12) / key_count
    end = 2 * math.pi * (i + .88) / key_count
    # 36 separate raised trapezoids on the sloping keypad.
    keys.sector(.35, .54, 1.146, start, end)
    keys.sector(.55, .69, 1.146, start, end)
    # A small high-contrast wedge on each outer key serves as an engraved marker.

markers = Mesh('dhd-key-markers')
for i in range(key_count):
    angle = 2 * math.pi * (i + .5) / key_count
    width = .014 if i % 3 else .022
    markers.sector(.59, .642, 1.149, angle - width, angle + width)
    if i % 4 == 0:
        markers.sector(.40, .48, 1.149, angle - .014, angle + .014)

button = Mesh('dhd-activate')
button.ring(0, .09, 1.156, 1.156)
button.wall(.09, .11, 1.14, 1.156)

report = {'source': 'Original parametric geometry; no third-party model imported', 'parts': {}}
for mesh in (body, keys, markers, button):
    data = mesh.obj(); path = root / (mesh.name + '.obj')
    if args.check:
        assert path.read_bytes() == data, path
    else:
        path.write_bytes(data)
    report['parts'][mesh.name] = {'vertices': len(mesh.vertices), 'triangles': len(mesh.faces),
                                  'sha256': hashlib.sha256(data).hexdigest()}
assert sum(p['triangles'] for p in report['parts'].values()) < 5000
content = (json.dumps(report, indent=2) + '\n').encode()
path = root / 'build-report.json'
if args.check: assert path.read_bytes() == content
else: path.write_bytes(content)
print(json.dumps(report, indent=2))
