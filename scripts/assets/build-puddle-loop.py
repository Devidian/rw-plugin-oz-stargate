#!/usr/bin/env python3
"""Extract the user-supplied reference video into bounded, immutable native texture atlases.
Requires system FFmpeg. Source media stays outside the repository; no license is inferred.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'src/assets/models/milkyway'
NAMES = ['puddle-loop-0.png', 'puddle-loop-1.png', 'puddle-surge.png']

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('--ffmpeg', default='/usr/bin/ffmpeg')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='stargate-puddle-') as temp:
        output = Path(temp)
        # The video contains a centred 2160px disk on a 3840x2160 black canvas.
        # A small inset prevents the black boundary leaking through the circular mesh.
        crop = 'crop=2120:2120:860:20'
        for index in range(2):
            filters = f'{crop},fps=12,trim=start_frame={index*70}:end_frame={(index+1)*70},scale=384:384:flags=lanczos,tile=7x10'
            subprocess.run([args.ffmpeg, '-v', 'error', '-i', str(args.source), '-vf', filters,
                            '-frames:v', '1', str(output / NAMES[index])], check=True)
        subprocess.run([args.ffmpeg, '-v', 'error', '-i', str(args.source), '-vf',
                        f'{crop},scale=512:512:flags=lanczos', '-frames:v', '1',
                        str(output / NAMES[2])], check=True)
        report = {'source': args.source.name, 'sourceSha256': sha(args.source),
                  'provenance': 'User-provided reference; redistribution license not supplied.',
                  'frames': 140, 'fps': 12, 'seconds': 140 / 12, 'tilePixels': 384,
                  'columns': 7, 'rows': 10, 'atlasCount': 2,
                  'crop': [860, 20, 2120, 2120],
                  'sha256': {name: sha(output / name) for name in NAMES}}
        (output / 'puddle-loop-report.json').write_text(json.dumps(report, indent=2) + '\n')
        for name in NAMES + ['puddle-loop-report.json']:
            if args.check:
                assert (DEST / name).read_bytes() == (output / name).read_bytes(), name
            else:
                (DEST / name).write_bytes((output / name).read_bytes())
        print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
