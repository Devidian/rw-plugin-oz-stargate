#!/usr/bin/env python3
"""Build matching modern/classic SVG icons for the platform radial actions."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/icons"
ET.register_namespace("", "http://www.w3.org/2000/svg")


def base_gate(style):
    root = ET.parse(ROOT / style / "stargate-gate.svg").getroot()
    return ET.tostring(root[0], encoding="unicode")


def platform_art(kind, style):
    dark = "#182d40"
    stone = "#a9c0cc" if style == "modern" else "#eadfbd"
    face = "#668596" if style == "modern" else "#a49b78"
    trim = "#f7c56e" if style == "modern" else "#f0d989"
    blue = "#41bce9" if style == "modern" else "#95d6e4"
    if kind == "platforms":
        return f'''<path d="M56 423 Q242 351 428 423 L399 463 Q242 510 85 463Z" fill="{face}" stroke="{dark}" stroke-width="12"/>
<path d="M62 416 Q242 350 422 416 Q242 478 62 416Z" fill="{stone}" stroke="{dark}" stroke-width="10"/>
<path d="M107 447 Q242 490 377 447" fill="none" stroke="{trim}" stroke-width="12"/>
<g fill="{blue}" stroke="{dark}" stroke-width="5"><circle cx="140" cy="423" r="12"/><circle cx="208" cy="444" r="12"/><circle cx="276" cy="444" r="12"/><circle cx="344" cy="423" r="12"/></g>'''
    if kind == "circular":
        return f'''<path d="M54 401 Q242 340 430 401 L430 438 Q242 512 54 438Z" fill="{face}" stroke="{dark}" stroke-width="12"/>
<ellipse cx="242" cy="394" rx="187" ry="53" fill="{stone}" stroke="{dark}" stroke-width="11"/>
<ellipse cx="242" cy="396" rx="129" ry="33" fill="none" stroke="{trim}" stroke-width="11"/>
<path d="M94 443 Q242 491 390 443" fill="none" stroke="{trim}" stroke-width="9"/>'''
    if kind == "curved_milky_way":
        return f'''<path d="M52 446 Q242 339 432 446 L400 474 Q242 514 84 474Z" fill="{face}" stroke="{dark}" stroke-width="12"/>
<path d="M84 433 Q242 349 400 433 L375 450 Q242 484 109 450Z" fill="{stone}" stroke="{dark}" stroke-width="9"/>
<path d="M62 434 Q91 363 151 360 M422 434 Q393 363 333 360" fill="none" stroke="{dark}" stroke-width="27" stroke-linecap="round"/>
<path d="M62 434 Q91 363 151 360 M422 434 Q393 363 333 360" fill="none" stroke="{trim}" stroke-width="12" stroke-linecap="round"/>
<path d="M120 461 Q242 495 364 461" fill="none" stroke="{trim}" stroke-width="9"/>'''
    if kind == "curved_pegasus":
        return f'''<path d="M52 446 Q242 339 432 446 L400 474 Q242 514 84 474Z" fill="{face}" stroke="{dark}" stroke-width="12"/>
<path d="M84 433 Q242 349 400 433 L375 450 Q242 484 109 450Z" fill="{stone}" stroke="{dark}" stroke-width="9"/>
<path d="M57 428 Q68 377 118 351 L142 362 Q92 391 81 438Z M427 428 Q416 377 366 351 L342 362 Q392 391 403 438Z" fill="{blue}" stroke="{dark}" stroke-width="8"/>
<path d="M117 463 Q242 497 367 463" fill="none" stroke="{blue}" stroke-width="11"/>
<g fill="{trim}" stroke="{dark}" stroke-width="5"><circle cx="112" cy="382" r="12"/><circle cx="372" cy="382" r="12"/></g>'''
    if kind == "grace_of_god":
        return f'''<path d="M108 437 L157 374 L327 374 L376 437 L344 477 L140 477Z" fill="{face}" stroke="{dark}" stroke-width="12" stroke-linejoin="round"/>
<path d="M142 423 L177 386 L307 386 L342 423 L314 447 L170 447Z" fill="{stone}" stroke="{dark}" stroke-width="9" stroke-linejoin="round"/>
<path d="M163 451 L322 451 M181 469 L303 469" stroke="{trim}" stroke-width="10" stroke-linecap="round"/>
<path d="M108 437 L157 374 L179 388 L142 439Z M376 437 L327 374 L305 388 L342 439Z" fill="{trim}" stroke="{dark}" stroke-width="6"/>'''
    raise ValueError(kind)


def main():
    for style in ("modern", "classic"):
        gate = base_gate(style)
        for kind in ("platforms", "circular", "curved_milky_way", "curved_pegasus", "grace_of_god"):
            name = "stargate-platforms" if kind == "platforms" else "stargate-platform-" + kind.replace("_", "-")
            svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'
                   + platform_art(kind, style)
                   + f'<g transform="translate(73 90) scale(.70)">{gate}</g></svg>')
            ET.fromstring(svg)
            (ROOT / style / (name + ".svg")).write_text(svg + "\n", encoding="utf-8")
            print(style, name)


if __name__ == "__main__":
    main()
