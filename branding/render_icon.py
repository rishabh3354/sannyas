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
from PIL import Image, ImageDraw

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
master = Image.open(big).convert("RGB")
master.resize((512, 512), Image.LANCZOS).save(OUT)

# Legacy launcher icons. minSdk is 24, but the only icon resource used to be
# mipmap-anydpi-v26, which API 24-25 cannot resolve — those devices had no
# launcher icon at all. The adaptive XML still serves API 26+; these cover the
# two releases below it.
MIPMAP = ROOT / "app/src/main/res"
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for bucket, px in LEGACY.items():
    d = MIPMAP / f"mipmap-{bucket}"
    d.mkdir(parents=True, exist_ok=True)
    square = master.resize((px, px), Image.LANCZOS)
    square.save(d / "ic_launcher.png", optimize=True)
    # Round variant for launchers that ask for one by name.
    rounded = square.convert("RGBA")
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
    rounded.putalpha(mask)
    rounded.save(d / "ic_launcher_round.png", optimize=True)

svg.unlink(); big.unlink()
print(f"wrote {OUT.relative_to(ROOT)} from {len(paths)} paths, stroke {stroke}")
print(f"wrote legacy icons for API 24-25: {', '.join(LEGACY)}")
