#!/usr/bin/env python3
"""
Renders the Play Store listing icon from the shipped launcher drawables.

Reads the real vectors in app/src/main/res/drawable so the store asset can
never drift from what the launcher shows. Rasterises at 2048 and downsamples
to 512 with a proper filter — rendering straight at 512, or worse rendering at
the vector's declared 108 and scaling up, gives visibly soft edges.
"""
import re
import subprocess
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"
OUT = ROOT / "branding/play-store-icon-512.png"
SUPERSAMPLE = 2048
VIEWPORT = 108

fg = (RES / "ic_launcher_foreground.xml").read_text()
bg = (RES / "ic_launcher_background.xml").read_text()

stroke = re.search(r'android:strokeWidth="([\d.]+)"', fg).group(1)
paths = re.findall(r'android:pathData="([^"]+)"', fg)
stops = re.findall(r'android:offset="([\d.]+)"\s+android:color="#FF([0-9A-Fa-f]{6})"', bg)
if not paths or not stops:
    sys.exit("could not read the launcher vectors")

svg_stops = "\n".join(f'    <stop offset="{o}" stop-color="#{c}"/>' for o, c in stops)
svg_paths = "\n".join(
    f'  <path d="{d}" stroke="#FFFFFF" stroke-width="{stroke}" '
    'stroke-linecap="round" stroke-linejoin="round" fill="none"/>' for d in paths
)

svg = ROOT / "branding/.icon.svg"
svg.write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{SUPERSAMPLE}" height="{SUPERSAMPLE}" '
    f'viewBox="0 0 {VIEWPORT} {VIEWPORT}">\n'
    f'  <defs><linearGradient id="bg" x1="0" y1="0" x2="{VIEWPORT}" y2="{VIEWPORT}" '
    f'gradientUnits="userSpaceOnUse">\n{svg_stops}\n  </linearGradient></defs>\n'
    f'  <rect width="{VIEWPORT}" height="{VIEWPORT}" fill="url(#bg)"/>\n{svg_paths}\n</svg>\n'
)

big = ROOT / "branding/.icon-big.png"
subprocess.run(["convert", "-background", "none", str(svg), str(big)], check=True)
Image.open(big).convert("RGB").resize((512, 512), Image.LANCZOS).save(OUT)
svg.unlink(); big.unlink()
print(f"wrote {OUT.relative_to(ROOT)} from {len(paths)} paths, stroke {stroke}")
