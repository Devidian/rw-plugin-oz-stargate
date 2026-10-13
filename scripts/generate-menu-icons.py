#!/usr/bin/env python3
"""Compose consistent Stargate menu icons from the reviewed motif assets."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets/icon-sources'
OUTPUT = ROOT / 'src/main/resources/assets/icons'


def magick(*args):
    subprocess.run(['magick', *map(str, args)], check=True)


def icon_source(name, path):
    magick(SOURCE / (name + '.png'), '-trim', '+repage', '-resize', '490x490',
           '-gravity', 'center', '-background', 'none', '-extent', '512x512', path)


def scaled(source, size, path):
    magick(source, '-resize', f'{size}x{size}', path)


def badge(color, sign, path):
    parts = [f'<circle cx="60" cy="60" r="53" fill="{color}" stroke="#fff0ce" stroke-width="7"/>']
    if sign == 'plus':
        parts += ['<rect x="50" y="27" width="20" height="66" rx="5" fill="white"/>',
                  '<rect x="27" y="50" width="66" height="20" rx="5" fill="white"/>']
    elif sign == 'delete':
        parts += ['<polygon points="35,27 60,49 85,27 93,35 71,60 93,85 85,93 60,71 35,93 27,85 49,60 27,35" fill="white"/>']
    elif sign == 'move':
        parts += ['<polygon points="60,22 47,42 54,42 54,54 42,54 42,47 22,60 42,73 42,66 54,66 54,78 47,78 60,98 73,78 66,78 66,66 78,66 78,73 98,60 78,47 78,54 66,54 66,42 73,42" fill="white"/>']
    elif sign == 'circle':
        parts += ['<circle cx="60" cy="60" r="31" fill="#153f53"/>',
                  '<circle cx="60" cy="60" r="20" fill="#65d8e9"/>']
    elif sign == 'milky':
        parts += ['<path d="M25 68 Q60 24 95 68 L83 84 Q60 54 37 84Z" fill="#96efff"/>']
    elif sign == 'pegasus':
        parts += ['<path d="M22 72 Q60 18 98 72 L86 81 Q60 46 34 81Z" fill="#adf2ff"/>',
                  '<path d="M35 87 Q60 62 85 87" stroke="#163650" stroke-width="5" fill="#78dfff"/>']
    elif sign == 'grace':
        parts += ['<polygon points="60,22 94,60 60,98 26,60" fill="#ffe099"/>',
                  '<polygon points="60,39 77,60 60,81 43,60" fill="#986328"/>']
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120">' + ''.join(parts) + '</svg>'
    path.with_suffix('.svg').write_text(svg)
    magick('-background', 'none', path.with_suffix('.svg'), path)


def compose(base, overlays, output):
    command = [str(base)]
    for image, x, y in overlays:
        command += [str(image), '-geometry', f'+{x}+{y}', '-compose', 'over', '-composite']
    magick(*command, output)


def main():
    with tempfile.TemporaryDirectory(prefix='oz-stargate-icons-') as temp:
        tmp = Path(temp)
        gate = tmp / 'gate.png'
        dhd = tmp / 'dhd.png'
        platform = tmp / 'platform.png'
        icon_source('gate', gate)
        icon_source('dhd', dhd)
        icon_source('platform', platform)
        small_gate = tmp / 'small-gate.png'
        small_dhd = tmp / 'small-dhd.png'
        platform_gate = tmp / 'platform-gate.png'
        scaled(gate, 365, small_gate)
        scaled(dhd, 230, small_dhd)
        scaled(gate, 300, platform_gate)
        badges = {}
        for key, color, sign in (
            ('plus', '#258b59', 'plus'), ('delete', '#c8343f', 'delete'),
            ('move', '#267caf', 'move'), ('circular', '#287ba8', 'circle'),
            ('milky', '#276eac', 'milky'), ('pegasus', '#2860ac', 'pegasus'),
            ('grace', '#bd8336', 'grace')):
            badges[key] = tmp / (key + '.png')
            badge(color, sign, badges[key])
        images = {
            'oz-stargate': (gate, []),
            'stargate-gate': (gate, [(badges['move'], 380, 380)]),
            'stargate-dhd': (dhd, [(badges['plus'], 380, 380)]),
            'stargate-gate-dhd': (tmp / 'blank.png', [(small_gate, 12, 0), (small_dhd, 245, 250), (badges['plus'], 380, 380)]),
            'stargate-delete': (tmp / 'blank.png', [(small_gate, 12, 0), (small_dhd, 245, 250), (badges['delete'], 380, 380)]),
        }
        magick('-size', '512x512', 'xc:#ff000000', tmp / 'blank.png')
        for name, badge_key in (
            ('stargate-platforms', None), ('stargate-platform-circular', 'circular'),
            ('stargate-platform-curved-milky-way', 'milky'),
            ('stargate-platform-curved-pegasus', 'pegasus'),
            ('stargate-platform-grace-of-god', 'grace')):
            images[name] = (tmp / 'blank.png', [(platform, 0, 190), (platform_gate, 106, 0)]
                            + ([(badges[badge_key], 380, 380)] if badge_key else []))
        for name, (base, overlays) in images.items():
            modern = OUTPUT / 'modern' / (name + '.png')
            classic = OUTPUT / 'classic' / (name + '.png')
            compose(base, overlays, modern)
            magick(modern, '-colors', '28', '-modulate', '108,72,100', classic)


if __name__ == '__main__':
    main()
