#!/usr/bin/env python3
import colorsys
import math
import random
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter


SIZES = {
    "mobile": (1080, 2400),
    "leanback": (1920, 1080),
}

PALETTES = [
    ("aurora_glass", ["#10243f", "#25c7b7", "#7d6df6", "#f4d7a3", "#f8fbff"]),
    ("sunset_prism", ["#251227", "#ff7b6e", "#f9c46b", "#7bdff2", "#f7edf6"]),
    ("mint_glacier", ["#eaf8f5", "#99ead0", "#6ab7ff", "#d8f4ff", "#ffffff"]),
    ("liquid_chrome", ["#12151d", "#707c92", "#d7e3f7", "#49d3ff", "#f2f6ff"]),
    ("neon_berry", ["#210826", "#ff3f8b", "#7e5cff", "#31e8ff", "#ffe2f3"]),
    ("champagne_mist", ["#fbf2df", "#ead2a5", "#d6ecff", "#b3c7d6", "#ffffff"]),
    ("deep_space", ["#07101f", "#142b4f", "#5f6dff", "#18d6b2", "#fff5c7"]),
    ("rose_veil", ["#fff1f3", "#f8aac0", "#c5ddff", "#fbe3cb", "#ffffff"]),
    ("emerald_aurora", ["#061b16", "#0a6b5c", "#2fe5aa", "#9af5ff", "#f0fff7"]),
    ("graphite_smoke", ["#101319", "#2a3140", "#7c8797", "#d9e1ea", "#f8fafc"]),
]


def hex_to_rgb(value):
    value = value.strip().lstrip("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def clamp(value, low, high):
    return max(low, min(high, value))


def jitter_color(rgb, rng, hue_shift=0.035, sat_scale=0.18, val_scale=0.14):
    r, g, b = [channel / 255 for channel in rgb]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    h = (h + rng.uniform(-hue_shift, hue_shift)) % 1.0
    s = clamp(s * rng.uniform(1 - sat_scale, 1 + sat_scale), 0.0, 1.0)
    v = clamp(v * rng.uniform(1 - val_scale, 1 + val_scale), 0.0, 1.0)
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def gradient(size, colors, rng):
    width, height = size
    scale = 3
    small = (max(2, width // scale), max(2, height // scale))
    img = Image.new("RGB", small)
    pix = img.load()
    stops = [hex_to_rgb(color) for color in colors]
    angle = rng.uniform(0, math.pi * 2)
    cx = rng.uniform(-0.2, 0.2)
    cy = rng.uniform(-0.2, 0.2)
    for y in range(small[1]):
        ny = y / max(1, small[1] - 1) - 0.5
        for x in range(small[0]):
            nx = x / max(1, small[0] - 1) - 0.5
            linear = (math.cos(angle) * nx + math.sin(angle) * ny + 0.72) / 1.44
            radial = math.hypot(nx - cx, ny - cy)
            wave = math.sin((nx * rng.uniform(2.5, 5.5) + ny * rng.uniform(2.0, 4.5)) * math.pi)
            t = clamp(linear * 0.62 + (1 - radial) * 0.28 + wave * 0.06 + 0.08, 0, 1)
            pos = t * (len(stops) - 1)
            index = min(len(stops) - 2, int(pos))
            pix[x, y] = mix(stops[index], stops[index + 1], pos - index)
    return img.resize(size, Image.Resampling.BICUBIC).filter(ImageFilter.GaussianBlur(max(10, width // 95)))


def add_soft_light(img, palette, rng):
    width, height = img.size
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    colors = [hex_to_rgb(color) for color in palette]
    for _ in range(rng.randint(10, 18)):
        radius = rng.randint(width // 7, width // 2)
        x = rng.randint(-radius // 2, width + radius // 2)
        y = rng.randint(-radius // 2, height + radius // 2)
        color = jitter_color(rng.choice(colors), rng)
        alpha = rng.randint(28, 86)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(width // rng.randint(12, 20)))
    return Image.alpha_composite(img.convert("RGBA"), layer)


def add_crystal_facets(img, palette, rng):
    width, height = img.size
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    colors = [hex_to_rgb(color) for color in palette]
    for _ in range(rng.randint(9, 16)):
        cx = rng.randint(-width // 10, width + width // 10)
        cy = rng.randint(-height // 10, height + height // 10)
        radius = rng.randint(width // 7, width // 3)
        sides = rng.randint(3, 6)
        angle = rng.random() * math.tau
        points = []
        for side in range(sides):
            a = angle + side * math.tau / sides + rng.uniform(-0.12, 0.12)
            r = radius * rng.uniform(0.55, 1.1)
            points.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        fill = (*jitter_color(rng.choice(colors), rng), rng.randint(14, 42))
        outline = (255, 255, 255, rng.randint(18, 55))
        draw.polygon(points, fill=fill)
        draw.line(points + [points[0]], fill=outline, width=max(1, width // 380))
    return Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(0.6)))


def add_glass_ribbons(img, palette, rng):
    width, height = img.size
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    colors = [hex_to_rgb(color) for color in palette]
    for _ in range(rng.randint(5, 9)):
        base_y = rng.randint(-height // 6, height + height // 6)
        amp = rng.randint(height // 18, height // 7)
        phase = rng.random() * math.tau
        stroke = rng.randint(width // 42, width // 19)
        color = jitter_color(rng.choice(colors), rng)
        points = []
        for x in range(-width // 8, width + width // 8, max(18, width // 55)):
            y = base_y + math.sin(x / width * math.tau * rng.uniform(0.8, 1.8) + phase) * amp
            y += math.sin(x / width * math.tau * rng.uniform(1.8, 3.4) + phase * 0.7) * amp * 0.32
            points.append((x, y))
        draw.line(points, fill=(*color, rng.randint(20, 58)), width=stroke, joint="curve")
        highlight = [(x, y - stroke * 0.32) for x, y in points]
        draw.line(highlight, fill=(255, 255, 255, rng.randint(12, 34)), width=max(1, stroke // 5), joint="curve")
    return Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(1.2)))


def add_grain_and_vignette(img, rng):
    width, height = img.size
    alpha = rng.randint(10, 18)
    grain = Image.effect_noise(img.size, rng.uniform(8, 15)).convert("L")
    grain = ImageEnhance.Contrast(grain).enhance(0.45)
    grain_layer = Image.new("RGBA", img.size, (255, 255, 255, 0))
    grain_layer.putalpha(grain.point(lambda value: int((value / 255) * alpha)))
    img = Image.alpha_composite(img, grain_layer)

    mask = Image.new("L", img.size, 0)
    pix = mask.load()
    cx = width * 0.5
    cy = height * 0.48
    max_dist = math.hypot(cx, cy)
    for y in range(height):
        for x in range(width):
            dist = math.hypot(x - cx, y - cy) / max_dist
            pix[x, y] = int(clamp((dist - 0.34) / 0.66, 0, 1) * 110)
    shade = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shade.putalpha(mask.filter(ImageFilter.GaussianBlur(width // 30)))
    return Image.alpha_composite(img, shade)


def generate_wallpaper(size, rng):
    name, palette = rng.choice(PALETTES)
    colors = list(palette)
    rng.shuffle(colors)
    img = gradient(size, colors, rng).convert("RGBA")
    img = add_soft_light(img, colors, rng)
    if rng.random() < 0.88:
        img = add_glass_ribbons(img, colors, rng)
    if rng.random() < 0.80:
        img = add_crystal_facets(img, colors, rng)
    img = add_grain_and_vignette(img, rng)
    img = ImageEnhance.Color(img.convert("RGB")).enhance(rng.uniform(0.92, 1.12))
    img = ImageEnhance.Contrast(img).enhance(rng.uniform(0.96, 1.08))
    return name, img


def save_webp(img, path, quality):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=quality, method=6)


def main(count, out_dir, seed, quality, target, prefix):
    seed = seed if seed is not None else time.time_ns()
    master = random.Random(seed)
    targets = SIZES.keys() if target == "both" else [target]
    print(f"seed={seed}")
    print(f"out={out_dir}")
    for index in range(1, count + 1):
        item_seed = master.randrange(1 << 63)
        for target in targets:
            rng = random.Random(item_seed)
            style, img = generate_wallpaper(SIZES[target], rng)
            file = out_dir / target / f"{prefix}_{index:02d}_{style}.webp"
            save_webp(img, file, clamp(quality, 1, 100))
            print(file)


if __name__ == "__main__":
    COUNT = 8
    OUT_DIR = Path.cwd() / "tmp"
    SEED = None
    QUALITY = 72
    TARGET = "both"
    PREFIX = "wallpaper_random"

    main(COUNT, OUT_DIR, SEED, QUALITY, TARGET, PREFIX)
