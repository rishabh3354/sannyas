#!/usr/bin/env python3
"""
Builds the marketing-style Play Store screenshots.

Each panel is a coloured 9:16 card: a headline and one supporting line across
the top third, with the real app screenshot below, bleeding off the bottom
edge. The device image is never redrawn or mocked up — it is the actual
capture from branding/screenshots, so the listing cannot promise a screen the
app does not have.

Output is exactly 1080x1920 (9:16). The raw captures are 1080x2316, which is
taller than 9:16; Play accepts them, but composing into an exact ratio avoids
any letterboxing when Play scales the card.

    python3 branding/render_store_panels.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "branding/store-panels"
S = 2                            # supersample x2, downsample at the end

# One entry per slot Play asks for. The tablet canvases are bigger because
# 10-inch requires each side to be at least 1080px, and because the captures
# behind them are genuinely higher resolution — scaling a 1080-wide phone
# card up to fill a tablet slot would just look soft.
SETS = {
    "store-panels":        (1080, 1920, "screenshots"),
    "store-panels-7in":    (1080, 1920, "screenshots/tablet-7in"),
    "store-panels-10in":   (1440, 2560, "screenshots/tablet-10in"),
}

BOLD = "/home/warlord/.local/share/fonts/NotoSans-Bold.ttf"
REG = "/home/warlord/.local/share/fonts/NotoSans-Regular.ttf"

# Headline, supporting line, source capture, and the two gradient stops.
# Colours stay in the app's violet family and step around it, so the five
# cards read as one set rather than five unrelated adverts.
PANELS = [
    ("351 series.",      "Osho's discourses, in Hindi and English",  "01-home.png",   "#6D4DF6", "#3B1E9E"),
    ("Listen offline.",  "Download a talk or a whole series",        "02-series.png", "#1E7F8C", "#0C3E52"),
    ("Sleep timer.",     "Stops at the end of the talk",             "03-player.png", "#B24C7A", "#5E1F46"),
    ("Browse by theme.", "Zen, Buddha, Sufism, Tao and 50 more",     "04-browse.png", "#C2762B", "#6A3412"),
    ("No ads. No account.", "Nothing about you is collected",        "05-settings.png", "#4B3BA8", "#1C1340"),
]

# The tablet captures are a subset of the same journeys, so each reuses the
# colour of the phone card with the same headline — the three slots read as
# part of one set rather than a separate palette.
PANELS_7IN = [
    ("351 series.",      "Osho's discourses, in Hindi and English", "01-home.png",     "#6D4DF6", "#3B1E9E"),
    ("Browse by theme.", "Zen, Buddha, Sufism, Tao and 50 more",    "02-discover.png", "#C2762B", "#6A3412"),
    ("Sleep timer.",     "Stops at the end of the talk",            "03-player.png",   "#B24C7A", "#5E1F46"),
]

PANELS_10IN = [
    ("351 series.",     "Osho's discourses, in Hindi and English", "01-home.png",   "#6D4DF6", "#3B1E9E"),
    ("Listen offline.", "Download a talk or a whole series",       "02-series.png", "#1E7F8C", "#0C3E52"),
    ("Sleep timer.",    "Stops at the end of the talk",            "03-player.png", "#B24C7A", "#5E1F46"),
]


def gradient(size, top, bottom):
    """Vertical two-stop gradient. One column, then stretched — cheap and smooth."""
    t = tuple(int(top[i:i + 2], 16) for i in (1, 3, 5))
    b = tuple(int(bottom[i:i + 2], 16) for i in (1, 3, 5))
    col = Image.new("RGB", (1, size[1]))
    px = col.load()
    for y in range(size[1]):
        f = y / max(1, size[1] - 1)
        px[0, y] = tuple(round(t[c] + (b[c] - t[c]) * f) for c in range(3))
    return col.resize(size, Image.BILINEAR)


def rounded(img, radius):
    """Round the corners of the device capture so it reads as a screen, not a rectangle."""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                                           radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def wrap(draw, text, font, max_w):
    words, lines, line = text.split(), [], ""
    for w in words:
        trial = f"{line} {w}".strip()
        if draw.textlength(trial, font=font) <= max_w:
            line = trial
        else:
            if line:
                lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def build(headline, sub, shot_name, top, bottom, W, H, src_dir):
    cw, ch = W * S, H * S
    card = gradient((cw, ch), top, bottom)
    d = ImageDraw.Draw(card)

    # Type and spacing are authored for a 1080-wide card; k rescales them
    # so a 1440-wide tablet card keeps the same visual proportions rather
    # than the same pixel sizes.
    k = W / 1080
    margin = int(72 * S * k)
    f_head = ImageFont.truetype(BOLD, int(86 * S * k))
    f_sub = ImageFont.truetype(REG, int(40 * S * k))

    y = int(104 * S * k)
    for line in wrap(d, headline, f_head, cw - margin * 2):
        d.text((margin, y), line, font=f_head, fill="white")
        y += int(104 * S * k)
    y += int(14 * S * k)
    for line in wrap(d, sub, f_sub, cw - margin * 2):
        d.text((margin, y), line, font=f_sub, fill=(255, 255, 255, 220))
        y += int(54 * S * k)

    # The capture sits below the type and runs off the bottom edge, so the card
    # reads as a window onto the app rather than a framed thumbnail.
    shot = Image.open(ROOT / "branding" / src_dir / shot_name).convert("RGB")
    target_w = int(cw * 0.78)
    scale = target_w / shot.width
    shot = shot.resize((target_w, int(shot.height * scale)), Image.LANCZOS)
    shot = rounded(shot, int(34 * S * k))

    sx = (cw - target_w) // 2
    sy = y + int(60 * S * k)

    # Soft drop shadow: the capture is dark and would otherwise merge into the
    # darker end of the gradient.
    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle([sx, sy, sx + target_w, sy + shot.height],
                         radius=int(34 * S * k), fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(26 * S * k)))
    card = Image.alpha_composite(card.convert("RGBA"), shadow)
    card.paste(shot, (sx, sy), shot)

    return card.convert("RGB").resize((W, H), Image.LANCZOS)


def main():
    for out_name, panels in (
        ("store-panels", PANELS),
        ("store-panels-7in", PANELS_7IN),
        ("store-panels-10in", PANELS_10IN),
    ):
        W, H, src_dir = SETS[out_name]
        out = ROOT / "branding" / out_name
        out.mkdir(parents=True, exist_ok=True)
        print(f"{out_name}  ({W}x{H})")
        for i, (head, sub, shot, top, bottom) in enumerate(panels, start=1):
            img = build(head, sub, shot, top, bottom, W, H, src_dir)
            slug = head.split(".")[0].lower().replace(" ", "-")
            path = out / f"{i:02d}-{slug}.png"
            img.save(path, optimize=True)
            print(f"  {path.name:28} {img.size[0]}x{img.size[1]}  {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
