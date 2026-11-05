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
SRC = ROOT / "branding/screenshots"
OUT = ROOT / "branding/store-panels"
W, H, S = 1080, 1920, 2          # supersample x2, downsample at the end

BOLD = "/home/warlord/.local/share/fonts/NotoSans-Bold.ttf"
REG = "/home/warlord/.local/share/fonts/NotoSans-Regular.ttf"

# Headline, supporting line, source capture, and the two gradient stops.
# Colours are a graded saffron family matching the launcher artwork, running
# gold -> orange -> terracotta -> rust, so the five cards read as one set
# while still being told apart in the store's filmstrip.
PANELS = [
    ("351 series.",         "Osho's discourses, in Hindi and English", "01-home.png",     "#E8920E", "#8A4A02"),
    ("Listen offline.",     "Download a talk or a whole series",       "02-series.png",   "#D96A14", "#73300A"),
    ("Sleep timer.",        "Stops at the end of the talk",            "03-player.png",   "#C44A22", "#5E1C0E"),
    ("Browse by theme.",    "Zen, Buddha, Sufism, Tao and 50 more",    "04-browse.png",   "#B8701A", "#5C3208"),
    ("No ads. No account.", "Nothing about you is collected",          "05-settings.png", "#9E4418", "#47180A"),
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


def build(headline, sub, shot_name, top, bottom):
    cw, ch = W * S, H * S
    card = gradient((cw, ch), top, bottom)
    d = ImageDraw.Draw(card)

    margin = 72 * S
    f_head = ImageFont.truetype(BOLD, 86 * S)
    f_sub = ImageFont.truetype(REG, 40 * S)

    y = 104 * S
    for line in wrap(d, headline, f_head, cw - margin * 2):
        d.text((margin, y), line, font=f_head, fill="white")
        y += 104 * S
    y += 14 * S
    for line in wrap(d, sub, f_sub, cw - margin * 2):
        d.text((margin, y), line, font=f_sub, fill=(255, 255, 255, 220))
        y += 54 * S

    # The capture sits below the type and runs off the bottom edge, so the card
    # reads as a window onto the app rather than a framed thumbnail.
    shot = Image.open(SRC / shot_name).convert("RGB")
    target_w = int(cw * 0.78)
    scale = target_w / shot.width
    shot = shot.resize((target_w, int(shot.height * scale)), Image.LANCZOS)
    shot = rounded(shot, 34 * S)

    sx = (cw - target_w) // 2
    sy = y + 60 * S

    # Soft drop shadow: the capture is dark and would otherwise merge into the
    # darker end of the gradient.
    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle([sx, sy, sx + target_w, sy + shot.height],
                         radius=34 * S, fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(26 * S))
    card = Image.alpha_composite(card.convert("RGBA"), shadow)
    card.paste(shot, (sx, sy), shot)

    return card.convert("RGB").resize((W, H), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (head, sub, shot, top, bottom) in enumerate(PANELS, start=1):
        img = build(head, sub, shot, top, bottom)
        path = OUT / f"{i:02d}-{head.split('.')[0].lower().replace(' ', '-')}.png"
        img.save(path, optimize=True)
        print(f"  {path.name:28} {img.size[0]}x{img.size[1]}  {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
