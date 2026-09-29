"""Placeholder 16x16 module textures. Never overwrites an existing file (textures are drawn by hand)."""
from pathlib import Path
from PIL import Image, ImageDraw

TEX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/signalradar/textures/item"
TEX.mkdir(parents=True, exist_ok=True)
for n in range(1, 5):
    out = TEX / f"radar_module_{n}.png"
    if out.exists():
        print("keep", out.name)
        continue
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 3, 13, 12], fill=(58, 63, 68, 255), outline=(30, 33, 36, 255))
    d.rectangle([4, 5, 11, 10], fill=(11, 26, 14, 255))
    for i in range(n):  # one green pip per tier
        d.point((5 + i * 2, 8), fill=(57, 255, 90, 255))
    for x in (4, 7, 10):  # connector pins
        d.line([x, 13, x, 14], fill=(176, 141, 79, 255))
    img.save(out)
    print("made", out.name)
