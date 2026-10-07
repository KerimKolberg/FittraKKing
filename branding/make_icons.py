"""
Builds the Android launcher icon from branding/logo.jpg.

The logo is drawn on a flat navy background. That navy becomes the icon's background layer, and the
logo is cut out of it (alpha from its color distance to the navy) for the foreground layer, so any
launcher shape (circle, squircle, rounded square) and themed icons work.

Run from the repository root:  python3 branding/make_icons.py   (needs Pillow: pip install pillow)
"""
import math
import os

from PIL import Image

LOGO = "branding/logo.jpg"
# The phone and the watch use the same icon.
RES_FOLDERS = ["app/src/main/res", "wear/src/main/res"]
BACKGROUND = (16, 31, 50)  # #101F32, the logo's own background
# Pixels this close to the background are fully transparent; this far away fully opaque.
TRANSPARENT_BELOW, OPAQUE_ABOVE = 30.0, 120.0
# The adaptive icon is 108 dp; the smallest mask is a 72 dp circle and the safe zone is 66 dp. The logo
# stays well inside it (52 dp across), because some launchers (Samsung One UI) shrink and crop icons further.
ICON_DP, SAFE_RADIUS_DP = 108, 26
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def cut_out(image):
    """Returns (colored, white) RGBA versions of the logo with the background removed."""
    rgb = image.convert("RGB")
    width, height = rgb.size
    colored = Image.new("RGBA", rgb.size)
    white = Image.new("RGBA", rgb.size)
    src, dst, mono = rgb.load(), colored.load(), white.load()
    for y in range(height):
        for x in range(width):
            pixel = src[x, y]
            distance = math.dist(pixel, BACKGROUND)
            alpha = min(1.0, max(0.0, (distance - TRANSPARENT_BELOW) / (OPAQUE_ABOVE - TRANSPARENT_BELOW)))
            if alpha == 0:
                continue
            # Undo the blend with the background so edges keep the logo's own color.
            color = tuple(
                max(0, min(255, round((c - (1 - alpha) * b) / alpha))) for c, b in zip(pixel, BACKGROUND)
            )
            dst[x, y] = color + (round(alpha * 255),)
            mono[x, y] = (255, 255, 255, round(alpha * 255))
    return colored, white


def logo_center_and_radius(cutout):
    alpha = cutout.getchannel("A").load()
    width, height = cutout.size
    points = [(x, y) for y in range(height) for x in range(width) if alpha[x, y] > 128]
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    return cx, cy, max(math.hypot(x - cx, y - cy) for x, y in points)


def layer(cutout, cx, cy, radius, size_px):
    """Places the logo centered on a transparent square canvas of the icon's full size."""
    px_per_dp_source = radius / SAFE_RADIUS_DP
    canvas_source = round(ICON_DP * px_per_dp_source)
    canvas = Image.new("RGBA", (canvas_source, canvas_source))
    canvas.paste(cutout, (round(canvas_source / 2 - cx), round(canvas_source / 2 - cy)))
    return canvas.resize((size_px, size_px), Image.LANCZOS)


def main():
    logo = Image.open(LOGO)
    colored, white = cut_out(logo)
    cx, cy, radius = logo_center_and_radius(colored)
    for res in RES_FOLDERS:
        for name, scale in DENSITIES.items():
            folder = os.path.join(res, f"mipmap-{name}")
            os.makedirs(folder, exist_ok=True)
            size = round(ICON_DP * scale)
            layer(colored, cx, cy, radius, size).save(os.path.join(folder, "ic_launcher_foreground.png"), optimize=True)
            layer(white, cx, cy, radius, size).save(os.path.join(folder, "ic_launcher_monochrome.png"), optimize=True)
    # The 512 px icon Google Play asks for when publishing.
    logo.convert("RGB").resize((512, 512), Image.LANCZOS).save("app/src/main/ic_launcher-playstore.png", optimize=True)
    print(f"Icons written (logo center {cx:.0f},{cy:.0f}, radius {radius:.0f}px)")


if __name__ == "__main__":
    main()
