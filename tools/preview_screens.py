"""Mock the v1.5 light theme (main screen) and the ring screen with the real palette values,
so the look can be checked without an emulator. Geometry is drawn by hand, text uses the same
sizes/colours as the Java code."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 360, 640
SCALE = 2
FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size * SCALE)


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def vgrad(size, top, bottom):
    img = Image.new("RGB", (1, size[1]))
    d = ImageDraw.Draw(img)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        d.point((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.resize(size)


def dgrad(size, c1, c2, c3):
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            t = (x / size[0] + y / size[1]) / 2
            if t < 0.5:
                u = t / 0.5
                col = tuple(int(c1[i] + (c2[i] - c1[i]) * u) for i in range(3))
            else:
                u = (t - 0.5) / 0.5
                col = tuple(int(c2[i] + (c3[i] - c2[i]) * u) for i in range(3))
            px[x, y] = col
    return img


def shadow(img, box, radius, blur=6, alpha=28):
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    x0, y0, x1, y1 = box
    d.rounded_rectangle([x0, y0 + 2, x1, y1 + 3], radius=radius, fill=(0, 0, 0, alpha))
    img.alpha_composite(lay.filter(ImageFilter.GaussianBlur(blur)))


def text(d, xy, s, size, color, bold=False, anchor="la", spacing=0):
    f = font(size, bold)
    if spacing == 0:
        d.text(xy, s, font=f, fill=color, anchor=anchor)
        return
    x = xy[0]
    for ch in s:
        d.text((x, xy[1]), ch, font=f, fill=color, anchor=anchor)
        x += d.textlength(ch, font=f) + spacing


def main_screen():
    img = Image.new("RGBA", (W * SCALE, H * SCALE))
    img.paste(vgrad((W * SCALE, H * SCALE), hexc("FFF4EC"), hexc("FBE5DA")))
    d = ImageDraw.Draw(img)
    S = SCALE
    pad = 18 * S

    text(d, (pad, 26 * S), "恶魔闹铃", 26, hexc("221B1A"), bold=True, spacing=S)
    d.rounded_rectangle([W * S - pad - 34 * S, 26 * S, W * S - pad, 58 * S], radius=12 * S,
                        fill=hexc("F7EFE9"), outline=hexc("EFDFD5"), width=S)
    d.text((W * S - pad - 17 * S, 42 * S), "⚙", font=font(16), fill=hexc("8C7C74"), anchor="mm")

    # banner
    by = 74 * S
    shadow(img, (pad, by, W * S - pad, by + 46 * S), 14 * S)
    d.rounded_rectangle([pad, by, W * S - pad, by + 46 * S], radius=14 * S,
                        fill=hexc("FFF4E3"), outline=hexc("F2D2A2"), width=S)
    text(d, (pad + 12 * S, by + 15 * S), "系统没给「精确闹钟」权限，闹钟可能晚几分钟", 11,
         hexc("8A5300"))
    text(d, (W * S - pad - 12 * S, by + 15 * S), "去开启", 11, hexc("8A5300"), bold=True, anchor="ra")

    # alarm cards
    def card(y, time_s, sub, next_s, on):
        top, bot = y, y + 96 * S
        shadow(img, (pad, top, W * S - pad, bot), 20 * S)
        d.rounded_rectangle([pad, top, W * S - pad, bot], radius=20 * S, fill=(255, 255, 255),
                            outline=hexc("EFDFD5"), width=S)
        text(d, (pad + 16 * S, top + 14 * S), time_s, 30, hexc("221B1A"), bold=True)
        text(d, (pad + 16 * S, top + 54 * S), sub, 11, hexc("8C7C74"))
        text(d, (pad + 16 * S, top + 72 * S), next_s, 11, hexc("2F7AE5"))
        # switch
        cx, cy = W * S - pad - 40 * S, top + 20 * S
        track = hexc("30D158") if on else hexc("E3D8D1")
        d.rounded_rectangle([cx, cy, cx + 40 * S, cy + 22 * S], radius=11 * S, fill=track)
        knob_x = cx + 20 * S if on else cx + 2 * S
        d.ellipse([knob_x, cy + 2 * S, knob_x + 18 * S, cy + 20 * S], fill=(255, 255, 255))

    card(132 * S, "07:00", "每天 · 算术 · ★★", "下次 明天 07:00 · 还有 9 小时 12 分", True)
    card(240 * S, "06:30", "工作日 · 算术+打字 · ★★★", "下次 周一 06:30", False)

    # add button
    ab_y = H * S - 96 * S
    d.rounded_rectangle([pad, ab_y, W * S - pad, ab_y + 50 * S], radius=18 * S, fill=hexc("EA5237"))
    text(d, (W * S // 2, ab_y + 25 * S), "＋  新建闹钟", 17, (255, 255, 255), bold=True, anchor="mm")
    text(d, (W * S // 2, H * S - 26 * S), "先试一次：10 秒后响", 11, hexc("8C7C74"), anchor="mm",
         spacing=S)
    return img


def ring_screen():
    img = Image.new("RGBA", (W * SCALE, H * SCALE))
    img.paste(dgrad((W * SCALE, H * SCALE), hexc("3D1017"), hexc("180608"), hexc("2E0A1E")))
    d = ImageDraw.Draw(img)
    S = SCALE
    pad = 20 * S

    text(d, (pad, 22 * S), "恶 魔 闹 铃", 13, hexc("FF9E8A"), spacing=2 * S)
    text(d, (W * S - pad, 20 * S), "07:00", 28, hexc("FDF3EF"), bold=True, anchor="ra")
    text(d, (pad, 62 * S), "第 1/2 关 · 算术", 20, hexc("FDF3EF"), bold=True, spacing=S)
    text(d, (pad, 96 * S), "07:00 的闹铃正在响 · 没有关闭按钮", 12, hexc("FDF3EF"))

    # white answer card
    top = 120 * S
    shadow(img, (pad, top, W * S - pad, top + 210 * S), 20 * S, blur=10, alpha=60)
    d.rounded_rectangle([pad, top, W * S - pad, top + 210 * S], radius=20 * S, fill=(255, 255, 255),
                        outline=hexc("EFDFD5"), width=S)
    text(d, (W * S // 2, top + 22 * S), "37 × 8 = ?", 30, hexc("221B1A"), bold=True, anchor="ma")
    d.rounded_rectangle([pad + 18 * S, top + 78 * S, W * S - pad - 18 * S, top + 128 * S],
                        radius=14 * S, fill=hexc("F7EFE9"), outline=hexc("EFDFD5"), width=S)
    text(d, (W * S // 2, top + 103 * S), "输入答案", 20, hexc("8C7C74"), anchor="mm")
    d.rounded_rectangle([pad + 18 * S, top + 146 * S, W * S - pad - 18 * S, top + 194 * S],
                        radius=18 * S, fill=hexc("EA5237"))
    text(d, (W * S // 2, top + 170 * S), "提交", 16, (255, 255, 255), bold=True, anchor="mm")

    text(d, (W * S // 2, top + 236 * S), "做完 2 关才会停", 11, hexc("FDF3EF"), anchor="mm")

    # pause button
    py = H * S - 70 * S
    glass = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glass)
    gd.rounded_rectangle([pad, py, W * S - pad, py + 44 * S], radius=18 * S,
                         fill=(255, 255, 255, 46), outline=(255, 255, 255, 90), width=S)
    img.alpha_composite(glass)
    text(d, (W * S // 2, py + 22 * S), "暂停 20 秒（还剩 1 次）", 15, hexc("FDF3EF"), bold=True,
         anchor="mm")
    return img


a = main_screen()
b = ring_screen()
gap = 26 * SCALE
sheet = Image.new("RGB", (a.width * 2 + gap * 3, a.height + gap * 2), hexc("EFEAE4"))
sheet.paste(a.convert("RGB"), (gap, gap))
sheet.paste(b.convert("RGB"), (gap * 2 + a.width, gap))
sheet.resize((sheet.width // SCALE, sheet.height // SCALE), Image.LANCZOS).save("screens-preview.png")
print("ok")
