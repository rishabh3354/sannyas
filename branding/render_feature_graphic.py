#!/usr/bin/env python3
"""
Renders the 1024x500 Play Console feature graphic.

Reads the gradient stops out of the real launcher background vector and redraws
the launcher mark, so the store banner cannot drift from the icon the way a
hand-made asset would. Catalog figures are counted from discourses.json for the
same reason — quoting a number the app does not actually ship is the kind of
thing store reviewers check.

Play crops this asset on some surfaces and overlays a play button on others, so
nothing load-bearing sits in the outer eighth or dead centre.

Drawn at 3x and downsampled: Pillow has no round line caps, so the enso's ends
are stamped as circles, and that seam only disappears with supersampling.
"""
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "branding/play-feature-graphic-1024x500.png"
W, H, S = 1024, 500, 3

FONT_BOLD = "/home/warlord/.local/share/fonts/NotoSans-Bold.ttf"
FONT_REG = "/home/warlord/.local/share/fonts/NotoSans-Regular.ttf"

# --- brand colours, read from the shipped launcher background -----------------
bg_vector = (ROOT / "app/src/main/res/drawable/ic_launcher_background.xml").read_text()
stops = re.findall(r'android:offset="([\d.]+)"\s+android:color="#FF([0-9A-Fa-f]{6})"', bg_vector)
if not stops:
    raise SystemExit("could not read the launcher gradient")
stops = [(float(o), tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))) for o, c in stops]

# --- catalog figures, counted rather than remembered --------------------------
catalog = json.loads((ROOT / "app/src/main/assets/discourses.json").read_text())
series = len(catalog["discourses"])
talks = sum(len(d.get("tracks", [])) for d in catalog["discourses"])


def _secs(t):
    p = [int(x) for x in t.split(":")] if t else []
    return p[0] * 3600 + p[1] * 60 + p[2] if len(p) == 3 else 0


hours = round(sum(_secs(t.get("duration", "")) for d in catalog["discourses"]
                  for t in d.get("tracks", [])) / 3600)

img = Image.new("RGB", (W * S, H * S))
d = ImageDraw.Draw(img)

# Diagonal gradient, interpolated across the same stops the icon uses. Projecting
# each pixel onto the diagonal keeps the sweep identical in direction to the
# icon's 0,0 -> 108,108 linear gradient.
diag = (W * S) + (H * S)
for i in range(diag):
    t = i / diag
    for j in range(len(stops) - 1):
        o0, c0 = stops[j]
        o1, c1 = stops[j + 1]
        if o0 <= t <= o1:
            f = (t - o0) / (o1 - o0)
            col = tuple(round(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
            break
    else:
        col = stops[-1][1]
    d.line([(i, 0), (0, i)], fill=col, width=2)

# --- the mark: open enso + play triangle, mirroring ic_launcher_foreground ----
cx, cy, r, sw = 232 * S, 250 * S, 96 * S, 18 * S
# Gap spans 40deg..80deg, matching the vector. Pillow measures clockwise from
# 3 o'clock with y growing downward, so the vector's angles negate.
d.arc([cx - r, cy - r, cx + r, cy + r], start=-40, end=-80 + 360, fill="white", width=sw)
# Pillow strokes an arc *inward* from the bounding box, so the drawn band runs
# from r-sw to r and its centreline sits at r-sw/2. Stamping the round caps at r
# instead leaves them hanging off the end with a notch on the inner edge.
cap_r = r - sw / 2
for ang in (-40, -80):
    import math
    ex = cx + cap_r * math.cos(math.radians(ang))
    ey = cy + cap_r * math.sin(math.radians(ang))
    d.ellipse([ex - sw / 2, ey - sw / 2, ex + sw / 2, ey + sw / 2], fill="white")

tri = [(cx - 34 * S, cy - 44 * S), (cx + 52 * S, cy), (cx - 34 * S, cy + 44 * S)]
d.polygon(tri, fill="white")
# The vector softens these corners with a stroke line join. Stamping circles on
# the vertices does not reproduce that — a circle centred on a sharp point juts
# out past both edges and reads as a knob. Stroking the perimeter with a round
# joint is what the vector actually does.
d.line(tri + [tri[0]], fill="white", width=10 * S, joint="curve")

# --- type ---------------------------------------------------------------------
name = ImageFont.truetype(FONT_BOLD, 92 * S)
lede = ImageFont.truetype(FONT_REG, 34 * S)
stat = ImageFont.truetype(FONT_REG, 27 * S)

x = 384 * S
d.text((x, 170 * S), "Sannyas", font=name, fill="white", anchor="ls")
d.text((x, 224 * S), "Osho's recorded discourses", font=lede,
       fill=(226, 220, 255), anchor="ls")
d.text((x, 282 * S), f"{series} series  ·  {talks:,} talks  ·  {hours:,} hours",
       font=stat, fill=(198, 188, 240), anchor="ls")
d.text((x, 330 * S), "Stream or download  ·  No ads  ·  No account",
       font=stat, fill=(198, 188, 240), anchor="ls")

img.resize((W, H), Image.LANCZOS).save(OUT)
print(f"wrote {OUT.relative_to(ROOT)} — {series} series, {talks} talks, {hours} hours")
