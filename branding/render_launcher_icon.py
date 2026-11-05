#!/usr/bin/env python3
"""
Builds every launcher icon asset from branding/icon-source.png.

Replaces the hand-written vector icon, which could not express illustrated
artwork. Emits three things:

  res/mipmap-*/ic_launcher_fg.png     adaptive foreground, one per density
  res/mipmap-*/ic_launcher.png        legacy icon for API 24-25
  res/mipmap-*/ic_launcher_round.png  legacy round icon for API 24-25
  branding/play-store-icon-512.png    the Play Console listing icon

The legacy files matter: minSdk is 24, and before this the only icon resource
was mipmap-anydpi-v26, which API 24-25 cannot resolve at all.

Adaptive geometry: the canvas is 108dp but a launcher may mask away everything
outside the central 72dp, and only the central 66dp is guaranteed. The artwork
is therefore scaled to SUBJECT_FRACTION of the canvas and centred, with the
remaining border filled from the art's own bottom-edge colour so the bleed is
seamless whatever shape the mask cuts.

    python3 branding/render_launcher_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "branding/icon-source.png"
RES = ROOT / "app/src/main/res"
STORE = ROOT / "branding/play-store-icon-512.png"

# Adaptive foreground is authored on a 108dp canvas; legacy icons are the
# finished square. Values are px per density bucket.
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# How much of the 108dp canvas the artwork occupies. 0.78 keeps the face and
# most of the beard inside the 72dp a round mask leaves visible, while still
# letting the shoulders bleed under the edge rather than floating in a border.
SUBJECT_FRACTION = 0.78

SUPER = 4  # render large, downsample once, so small densities stay crisp


def fill_colour(art):
    """Average the art's bottom edge — the warm saffron the robe sits on."""
    strip = art.crop((0, art.height - 6, art.width, art.height)).resize((1, 1), Image.BOX)
    return strip.getpixel((0, 0))


def compose(art, size):
    """Artwork centred on a filled canvas, ready for masking by the launcher."""
    big = size * SUPER
    canvas = Image.new("RGB", (big, big), fill_colour(art))
    inner = int(big * SUBJECT_FRACTION)
    scaled = art.resize((inner, inner), Image.LANCZOS)
    off = (big - inner) // 2
    canvas.paste(scaled, (off, off))
    return canvas.resize((size, size), Image.LANCZOS)


def circular(img):
    """Legacy round icon: same composition, clipped to a circle."""
    out = img.convert("RGBA")
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    out.putalpha(mask)
    return out


def main():
    art = Image.open(SRC).convert("RGB")

    for bucket, px in ADAPTIVE.items():
        d = RES / f"mipmap-{bucket}"
        d.mkdir(parents=True, exist_ok=True)
        compose(art, px).save(d / "ic_launcher_fg.png", optimize=True)

    for bucket, px in LEGACY.items():
        d = RES / f"mipmap-{bucket}"
        d.mkdir(parents=True, exist_ok=True)
        square = compose(art, px)
        square.save(d / "ic_launcher.png", optimize=True)
        circular(square).save(d / "ic_launcher_round.png", optimize=True)

    # Play's listing icon is shown unmasked and square, so it uses the full art
    # rather than the inset adaptive composition.
    art.resize((512, 512), Image.LANCZOS).save(STORE, optimize=True)

    print(f"  adaptive foregrounds : {', '.join(ADAPTIVE)}")
    print(f"  legacy + round icons : {', '.join(LEGACY)}  (needed for API 24-25)")
    print(f"  store icon           : {STORE.relative_to(ROOT)} 512x512")


if __name__ == "__main__":
    main()
