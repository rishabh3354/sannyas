#!/usr/bin/env python3
"""
Renders the 1024x500 Play Console feature graphic.

Shows the launcher artwork itself, so the banner and the app icon beside it on
the listing are the same mark. Catalog figures are counted from discourses.json
rather than typed in — quoting a number the app does not actually ship is the
kind of thing store reviewers check.

Play crops this asset on some surfaces and overlays a play button on others, so
nothing load-bearing sits in the outer eighth or dead centre.

Drawn at 3x and downsampled, which keeps the type crisp and the disc's edge
smooth at the final size.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "branding/play-feature-graphic-1024x500.png"
W, H, S = 1024, 500, 3

FONT_BOLD = "/home/warlord/.local/share/fonts/NotoSans-Bold.ttf"
FONT_REG = "/home/warlord/.local/share/fonts/NotoSans-Regular.ttf"

# --- brand colours -----------------------------------------------------------
# Saffron, matching the launcher artwork. These were previously parsed out of
# ic_launcher_background.xml, but that vector no longer describes the icon —
# the launcher is illustrated artwork now — so the banner carries its own
# stops rather than tracking a drawable the app has stopped using.
STOPS_HEX = [(0.0, "E8920E"), (0.55, "B35A08"), (1.0, "7A3703")]
stops = [(o, tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))) for o, c in STOPS_HEX]

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

# --- the mark: the launcher artwork itself ------------------------------------
# This used to redraw the enso so the banner tracked the icon. The launcher is
# illustrated artwork now, so the banner shows that artwork instead — two
# different marks on one listing page reads as two different apps.
mark_d = 300 * S
cx, cy = 208 * S, 250 * S
half = mark_d // 2

# A soft shadow under the disc. The artwork's own background is a saffron close
# to the banner's, so without one the circle's edge dissolves and the mark
# looks like a stain rather than a badge.
shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
ImageDraw.Draw(shadow).ellipse([cx - half, cy - half, cx + half, cy + half],
                               fill=(54, 18, 0, 165))
shadow = shadow.filter(ImageFilter.GaussianBlur(13 * S))
img = Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB")
d = ImageDraw.Draw(img)

art = Image.open(ROOT / "branding/icon-source.png").convert("RGB")
art = art.resize((mark_d, mark_d), Image.LANCZOS)
disc = Image.new("L", (mark_d, mark_d), 0)
ImageDraw.Draw(disc).ellipse([0, 0, mark_d - 1, mark_d - 1], fill=255)
img.paste(art, (cx - half, cy - half), disc)

# --- type ---------------------------------------------------------------------
name = ImageFont.truetype(FONT_BOLD, 92 * S)
lede = ImageFont.truetype(FONT_REG, 34 * S)
stat = ImageFont.truetype(FONT_REG, 27 * S)

x = 384 * S
d.text((x, 170 * S), "Sannyas", font=name, fill="white", anchor="ls")
d.text((x, 224 * S), "Osho's recorded discourses", font=lede,
       fill=(255, 243, 224), anchor="ls")
d.text((x, 282 * S), f"{series} series  ·  {talks:,} talks  ·  {hours:,} hours",
       font=stat, fill=(255, 231, 196), anchor="ls")
d.text((x, 330 * S), "Stream or download  ·  No ads  ·  No account",
       font=stat, fill=(255, 231, 196), anchor="ls")

img.resize((W, H), Image.LANCZOS).save(OUT)
print(f"wrote {OUT.relative_to(ROOT)} — {series} series, {talks} talks, {hours} hours")
