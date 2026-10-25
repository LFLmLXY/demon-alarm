"""Approximate ic_launcher_bg.xml + ic_launcher_fg.xml so the icon can be eyeballed without a
device. Geometry only, no Android renderer involved.

Run from anywhere: `python tools/preview_icon.py` -> preview-icon-1.5.png
"""
import math
import os

from PIL import Image, ImageDraw

S = 4
N = 108 * S
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "preview-icon-1.5.png")


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def canvas():
    img = Image.new("RGB", (N, N))
    px = img.load()
    a, b = (0xFF, 0xC9, 0x78), (0xDD, 0x3F, 0x3A)
    cx, cy, rad = 26 * S, 18 * S, 70 * S
    for y in range(N):
        for x in range(N):
            t = min(1.0, (x / N) * 0.55 + (y / N) * 0.45)
            r, g, bl = lerp(a, b, t)
            d = math.hypot(x - cx, y - cy) / rad
            if d < 1:
                w = 0x40 * (1 - d) / 255
                r, g, bl = (int(c + (255 - c) * w) for c in (r, g, bl))
            px[x, y] = (r, g, bl)
    return img


def clock(d):
    white, coral, soft = (255, 255, 255), (0xE0, 0x45, 0x3C), (0xFF, 0xD8, 0xCE)
    for bx in (37, 71):
        d.ellipse([(bx - 7) * S, (32 - 7) * S, (bx + 7) * S, (32 + 7) * S], fill=white)
    for (x1, y1), (x2, y2) in (((43, 78), (36, 88)), ((65, 78), (72, 88))):
        d.line([x1 * S, y1 * S, x2 * S, y2 * S], fill=white, width=int(7 * S))
        r = 3.5 * S
        for px_, py_ in ((x1, y1), (x2, y2)):
            d.ellipse([px_ * S - r, py_ * S - r, px_ * S + r, py_ * S + r], fill=white)
    d.ellipse([(54 - 23) * S, (57 - 23) * S, (54 + 23) * S, (57 + 23) * S], fill=white)
    d.arc([(54 - 18) * S, (57 - 18) * S, (54 + 18) * S, (57 + 18) * S], 0, 360, fill=soft,
          width=int(2.5 * S))
    for x2, y2 in ((54, 44), (65, 63)):
        d.line([54 * S, 57 * S, x2 * S, y2 * S], fill=coral, width=int(4.5 * S))
        r = 2.25 * S
        for px_, py_ in ((54, 57), (x2, y2)):
            d.ellipse([px_ * S - r, py_ * S - r, px_ * S + r, py_ * S + r], fill=coral)
    r = 3.5 * S
    d.ellipse([54 * S - r, 57 * S - r, 54 * S + r, 57 * S + r], fill=coral)


def mask(size, circle):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if circle:
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.23), fill=255)
    return m


base = canvas()
clock(ImageDraw.Draw(base))

pad = 40
sheet = Image.new("RGB", (N * 2 + pad * 3, N + pad * 2), (245, 241, 236))
for i, circle in enumerate((False, True)):
    layer = base.copy()
    layer.putalpha(mask(N, circle))
    sheet.paste(layer, (pad + i * (N + pad), pad), layer)

sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS).save(OUT)
print("saved", os.path.normpath(OUT))
