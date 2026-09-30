#!/usr/bin/env python3
"""Bake a 600ms procedural alpha dissolve over the supplied video using FFmpeg."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

DEST = Path(__file__).resolve().parents[2] / 'src/assets/models/milkyway'
parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
# Fixed spatial noise makes coherent wisps erode inward, rather than random flicker.
x, y = '((X-W/2)/(W/2))', '((Y-H/2)/(H/2))'
r = f'hypot({x},{y})'
noise = f'(.10*sin(13*{x}+4*sin(7*{y}))+.07*sin(19*{y}-5*{x})+.04*sin(37*{x}+23*{y}))'
white = '(.8*sin(PI*clip(T/.6,0,1)))'
alpha = f'255*clip((1.45-1.9*T/.6-{r}+{noise})/.22,0,1)'
channels = ':'.join(f"{c}='{c}(X,Y)*(1-{white})+255*{white}'" for c in 'rgb')
filters = f"crop=2120:2120:860:20,fps=20,trim=end_frame=12,scale=384:384:flags=lanczos,format=rgba,geq={channels}:a='{alpha}',format=rgba,tile=4x3"
with tempfile.TemporaryDirectory(prefix='stargate-dissolve-') as temp:
    out = Path(temp) / 'puddle-dissolve.png'
    subprocess.run(['/usr/bin/ffmpeg', '-v', 'error', '-i', str(args.source), '-vf', filters,
                    '-frames:v', '1', str(out)], check=True)
    report = {'source': args.source.name, 'sourceSha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
              'frames': 12, 'fps': 20, 'durationMs': 600, 'peakWhiteMs': 300, 'peakWhiteBlend': .8,
              'tilePixels': 384, 'columns': 4, 'rows': 3, 'filter': filters,
              'sha256': hashlib.sha256(out.read_bytes()).hexdigest()}
    metadata = Path(temp) / 'puddle-dissolve-report.json'
    metadata.write_text(json.dumps(report, indent=2) + '\n')
    for file in (out, metadata):
        if args.check:
            assert (DEST / file.name).read_bytes() == file.read_bytes(), file.name
        else:
            (DEST / file.name).write_bytes(file.read_bytes())
    print(json.dumps(report, indent=2))
