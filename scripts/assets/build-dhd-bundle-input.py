#!/usr/bin/env python3
"""Stage the full-source DHD as LOD0 and accepted reduced OBJ as LOD1."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('source_archive', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
generated = args.output / 'LOD0'
reduced = args.output / 'LOD1'
generated.mkdir(parents=True, exist_ok=True)
reduced.mkdir(parents=True, exist_ok=True)
import subprocess
subprocess.run(['python', str(Path(__file__).with_name('build-original-dhd-lod0.py')),
                str(args.source_archive), str(generated)], check=True)
report = json.loads((generated / 'original-dhd-build-report.json').read_text())
accepted = json.loads((root / 'src/assets/models/dhd/supplied-dhd-build-report.json').read_text())
entries = []
for level in range(2):
    source = generated if level == 0 else root / 'src/assets/models/dhd'
    prefix = 'original-dhd-' if level == 0 else 'supplied-dhd-'
    folder = generated if level == 0 else reduced
    selected = ['body', 'accents', 'keys-unlit', 'symbols-unlit', 'activate']
    selected += [f'keys-{i}' for i in range(7)] + [f'symbols-{i}' for i in range(7)]
    part_entries = []
    for part in selected:
        filename = prefix + part + '.obj'
        src = source / filename
        dst = folder / filename
        if level == 1:
            shutil.copyfile(src, dst)
        triangles = sum(line.startswith(b'f ') for line in dst.open('rb'))
        part_entries.append({'part': part, 'file': filename, 'triangles': triangles,
                             'sha256': hashlib.sha256(dst.read_bytes()).hexdigest()})
    entries.append({'level': level, 'entries': part_entries,
                    'triangles': sum(part['triangles'] for part in part_entries)})
if entries[0]['triangles'] != report['totalTriangles'] or entries[1]['triangles'] != accepted['totalTriangles']:
    raise ValueError('DHD LOD triangle mismatch')
manifest = {'lods': entries, 'sourceBlendSha256': report['sourceBlendSha256']}
(args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('DHD LODs:', [(lod['level'], lod['triangles']) for lod in entries])
