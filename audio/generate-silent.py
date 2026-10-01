#!/usr/bin/env python3
"""Generate packaged Vorbis silence from measured private reference durations.

Durations measured with ffprobe from local.res/stargate-audio-ref/audio-ref.md's
17 OGG files on 2026-10-01. The reference audio is never read by the build.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'src/audio/silent'
DURATIONS = {
    'dhd_1.ogg': 1.130975, 'dhd_2.ogg': 1.130975,
    'gate_roll_b.ogg': 3.181587, 'gate_roll_c.ogg': 3.089456,
    'chevron_out_1.ogg': 2.285578, 'gate_open.ogg': 3.372698,
    'open_loop.ogg': 5.315465, 'shutdown_b.ogg': 3.215873,
    'go_trough.ogg': 1.721315, 'dial_fail.ogg': 2.145034,
    **{f'chevron_{i}.ogg': 1.320975 for i in range(1, 8)},
}
OUTPUT.mkdir(parents=True, exist_ok=True)
for name, duration in DURATIONS.items():
    subprocess.run(['/usr/bin/ffmpeg', '-nostdin', '-v', 'error', '-y', '-f', 'lavfi',
        '-i', 'anullsrc=r=44100:cl=mono', '-t', f'{duration:.6f}',
        '-c:a', 'libvorbis', '-q:a', '3', str(OUTPUT / name)], check=True)
    print(f'{name}: {duration:.6f}s')
