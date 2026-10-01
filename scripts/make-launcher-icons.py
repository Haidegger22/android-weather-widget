#!/usr/bin/env python3
"""Нарезка картинки-иконки под Android: обычные значки всех плотностей + адаптивная иконка.

Что делает:
  1) обрезает белые поля и мягкую тень вокруг плитки;
  2) делает значки ic_launcher.png для mdpi..xxxhdpi (48..192 px) с прозрачным фоном;
  3) делает передний план адаптивной иконки (Android 8+) и цвет фона по самой плитке;
  4) собирает превью: как значок будет выглядеть на светлом и тёмном фоне.

Запуск:
    python3 scripts/make-launcher-icons.py icons/icon_a.png app/res
"""
import os
import sys
from PIL import Image, ImageDraw

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ICON_DP = 48          # размер обычного значка
FOREGROUND_DP = 108   # холст адаптивной иконки


def crop_to_tile(im):
    """Обрезка по самой плитке: мягкая тень вокруг отсекается по яркости."""
    rgb = im.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    step = max(1, w // 400)
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(0, h, step):
        for x in range(0, w, step):
            r, g, b = px[x, y]
            lum = (r * 299 + g * 587 + b * 114) // 1000
            if lum < 190:
                minx, miny = min(minx, x), min(miny, y)
                maxx, maxy = max(maxx, x), max(maxy, y)
    return rgb.crop((minx, miny, maxx + 1, maxy + 1)) if maxx > 0 else rgb


def tile_color(im, inset_ratio=0.22):
    """Цвет плитки — берём по её углам (внутри скругления)."""
    w, h = im.size
    d = max(4, int(w * inset_ratio))
    total = [0, 0, 0]
    for x, y in ((d, d), (w - d, d), (d, h - d), (w - d, h - d)):
        p = im.getpixel((x, y))
        total[0] += p[0]
        total[1] += p[1]
        total[2] += p[2]
    return tuple(v // 4 for v in total)


def main():
    if len(sys.argv) < 3:
        sys.exit("Использование: make-launcher-icons.py <картинка.png> <папка res>")
    src_path, res = sys.argv[1], sys.argv[2]

    art = crop_to_tile(Image.open(src_path))
    # убираем остатки тени по краю
    cut = int(min(art.size) * 0.05)
    art = art.crop((cut, cut, art.width - cut, art.height - cut))

    bg = tile_color(art)
    radius = max(3, int(min(art.size) * 0.19))
    side = max(art.size)
    mask = Image.new("L", art.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, art.width - 1, art.height - 1], radius=radius, fill=255)
    tile = Image.new("RGB", (side, side), bg)
    tile.paste(art, ((side - art.width) // 2, (side - art.height) // 2), mask)
    print(f"  плитка {art.size}, цвет #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}, скругление {radius}px")

    for name, k in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)

        px = int(ICON_DP * k)
        small = tile.resize((px, px), Image.LANCZOS)
        alpha = Image.new("L", (px, px), 0)
        ImageDraw.Draw(alpha).rounded_rectangle([0, 0, px - 1, px - 1],
                                                radius=max(2, int(px * 0.19)), fill=255)
        icon = small.convert("RGBA")
        icon.putalpha(alpha)
        icon.save(os.path.join(folder, "ic_launcher.png"))

        canvas = int(FOREGROUND_DP * k)
        tile.resize((canvas, canvas), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_foreground.png"))

    anydpi = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n'
                 '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                 '    <background android:drawable="@color/ic_launcher_background" />\n'
                 '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
                 '</adaptive-icon>\n')

    values = os.path.join(res, "values")
    os.makedirs(values, exist_ok=True)
    with open(os.path.join(values, "ic_launcher_background.xml"), "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                 f'    <color name="ic_launcher_background">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>\n'
                 '</resources>\n')

    # превью: светлый фон, тёмный фон, мелкие размеры
    preview = Image.new("RGB", (760, 260), (250, 250, 252))
    for x0, back in ((10, (238, 240, 244)), (390, (28, 32, 42))):
        preview.paste(Image.new("RGB", (360, 220), back), (x0, 20))
        preview.paste(tile.resize((150, 150), Image.LANCZOS), (x0 + 20, 55))
        for i, size in enumerate((72, 48, 32)):
            preview.paste(tile.resize((size, size), Image.LANCZOS), (x0 + 190 + i * 60, 70))
    preview.save(os.path.join(os.path.dirname(res), "icon-preview.png"))
    print("  готово: значки в res/mipmap-*, превью — icon-preview.png")


if __name__ == "__main__":
    main()
