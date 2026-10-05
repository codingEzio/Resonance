"""Compile/review the owned SVG. Run with bundled Python + Pillow.

python review.py --compile   # adopt vectors, then validate and render
python review.py             # validate adopted vectors and render evidence
The contact sheet previews Android masks; it is not device acceptance.
"""
from pathlib import Path
import argparse
import math
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

OWNER = Path(__file__).resolve().parent
RES = OWNER.parent / "res"
PROJECT = OWNER.parents[3]
OUT = PROJECT / "Local/evidence/icons"
ANDROID = "{http://schemas.android.com/apk/res/android}"
path = ET.parse(OWNER / "resonance.svg").find("{http://www.w3.org/2000/svg}path")
data = path.attrib["d"]
contours = [[tuple(map(float, pair)) for pair in re.findall(r"[ML]([0-9]+),([0-9]+)", chunk)] for chunk in data.split("Z") if chunk.strip()]
assert max(math.hypot(x - 54, y - 54) for x, y in contours[0]) <= 33, "Outside adaptive safe circle"
parser = argparse.ArgumentParser()
parser.add_argument("--compile", action="store_true")
args = parser.parse_args()
for name, color in [("ic_resonance", "@color/icon_symbol"), ("ic_resonance_foreground", "@color/icon_symbol"), ("ic_resonance_monochrome", "#FFFFFFFF")]:
    target = RES / f"drawable/{name}.xml"
    if args.compile:
        target.write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n    <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{data}" />\n</vector>\n')
    adopted = ET.parse(target).find("path")
    assert adopted.attrib[ANDROID + "pathData"] == data, f"SVG drift: {target}"
    assert adopted.attrib[ANDROID + "fillType"] == "evenOdd"
    assert adopted.attrib[ANDROID + "fillColor"] == color
for version in (26, 33):
    adaptive = ET.parse(RES / f"mipmap-anydpi-v{version}/ic_resonance.xml")
    assert adaptive.find("foreground").attrib[ANDROID + "drawable"] == "@drawable/ic_resonance_foreground"
    if version == 33:
        assert adaptive.find("monochrome").attrib[ANDROID + "drawable"] == "@drawable/ic_resonance_monochrome"

def render(size, ink, field, mask):
    scale = size * 4 / 108
    alpha = Image.new("L", (size * 4, size * 4))
    draw = ImageDraw.Draw(alpha)
    for index, contour in enumerate(contours):
        draw.polygon([(x * scale, y * scale) for x, y in contour], fill=255 if index == 0 else 0)
    if field is None:
        result = Image.new("RGBA", alpha.size, ink)
        result.putalpha(alpha)
        return result.resize((size, size), Image.Resampling.LANCZOS)
    result = Image.new("RGB", alpha.size, field)
    result.paste(ink, mask=alpha)
    enclosure = Image.new("L", alpha.size, 0)
    draw = ImageDraw.Draw(enclosure)
    if mask == "circle":
        draw.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    elif mask == "rounded":
        draw.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size, fill=255)
    else:
        draw.rectangle((0, 0, size * 4, size * 4), fill=255)
    result.putalpha(enclosure)
    return result.resize((size, size), Image.Resampling.LANCZOS)

OUT.mkdir(parents=True, exist_ok=True)
sheet = Image.new("RGB", (1000, 1016), "#D2D0CC")
d = ImageDraw.Draw(sheet)
d.text((24, 15), "RESONANCE / pixel play disc / Android adaptive + monochrome", fill="#171717")
modes = [("light", "#171717", "#F3F1EB"), ("dark", "#F3F1EB", "#171717"), ("themed light", "#233728", "#D8E8D1"), ("themed dark", "#D8E8D1", "#233728")]
for row, (label, ink, field) in enumerate(modes):
    y = 48 + row * 188
    d.text((24, y), label, fill="#171717")
    for col, mask in enumerate(("square", "rounded", "circle")):
        x = 24 + col * 168
        sheet.paste(render(108, ink, field, mask), (x, y + 22), render(108, ink, field, mask))
        d.text((x, y + 136), mask + " / 108px", fill="#171717")
    for col, background in enumerate(("#FFFFFF", "#242424", "#898989", "#A4B9BB")):
        x = 534 + col * 112
        d.rectangle((x, y + 22, x + 100, y + 172), fill=background)
        # True 1x and 2x delivery-size previews; no nearest-neighbor enlargement.
        for size, offset in ((16, 30), (32, 64), (64, 108)):
            preview = render(size, ink, field, "circle")
            sheet.paste(preview, (x + (100 - size) // 2, y + offset), preview)
        d.text((x, y + 174), "16 / 32 / 64", fill="#171717")
d.text((24, 834), "Transparent semantic mark / notification alpha / 16 and 32px at 1x and 2x", fill="#171717")
for col, (background, ink) in enumerate((("#FFFFFF", "#171717"), ("#242424", "#F3F1EB"), ("#898989", "#171717"), ("#A4B9BB", "#171717"))):
    x = 24 + col * 240
    d.rectangle((x, 861, x + 220, 985), fill=background)
    for size, offset in ((16, 12), (32, 49), (64, 112)):
        preview = render(size, ink, None, "none")
        sheet.paste(preview, (x + offset, 890), preview)
sheet.save(OUT / "contact-sheet.png")
(OUT / "review.txt").write_text("PASS: SVG/vector parity; evenOdd transparency; separate monochrome; adaptive API26/33 consumers.\nPASS: all silhouette vertices within centered radius33 safe circle.\nPreview: light/dark/themed light/themed dark, square/rounded/circle, 16/32/64px, four surroundings.\nDevice launcher, themed launcher and notification remain unverified here.\n")
print((OUT / "review.txt").read_text())
print(OUT / "contact-sheet.png")
