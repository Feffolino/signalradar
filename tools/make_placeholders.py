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

# ---- phase 4: addon item icons (16x16) and the addon GUI background (176x133 in a 256x256 sheet) ----
ACCENT = {  # addon -> accent colour
    "container": (224, 160, 64),
    "ore": (176, 176, 176),
    "biosign": (76, 217, 100),
    "structure": (64, 192, 255),
    "motion": (255, 48, 48),
}
for name, col in ACCENT.items():
    out = TEX / f"addon_{name}.png"
    if out.exists():
        print("keep", out.name)
        continue
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 2, 12, 13], fill=(44, 48, 52, 255), outline=(20, 22, 24, 255))
    d.rectangle([5, 4, 10, 9], fill=(11, 26, 14, 255))
    d.rectangle([6, 5, 9, 8], fill=col + (255,))
    for x in (5, 7, 9):
        d.line([x, 11, x, 12], fill=(176, 141, 79, 255))
    img.save(out)
    print("made", out.name)

# ---- phase 7: generic icon of custom (KubeJS) addons without their own model: body + a white light tinted by the
# addon colour (tint index 1) ----
for name, light in (("addon_custom", False), ("addon_custom_light", True)):
    out = TEX / f"{name}.png"
    if out.exists():
        print("keep", out.name)
        continue
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if light:
        d.rectangle([6, 5, 9, 8], fill=(255, 255, 255, 255))
    else:
        d.rectangle([3, 2, 12, 13], fill=(44, 48, 52, 255), outline=(20, 22, 24, 255))
        d.rectangle([5, 4, 10, 9], fill=(11, 26, 14, 255))
        for x in (5, 7, 9):
            d.line([x, 11, x, 12], fill=(176, 141, 79, 255))
    img.save(out)
    print("made", out.name)

GUI = Path(__file__).resolve().parent.parent / "src/main/resources/assets/signalradar/textures/gui"
GUI.mkdir(parents=True, exist_ok=True)
gui_out = GUI / "addon_slots.png"
if gui_out.exists():
    print("keep", gui_out.name)
else:
    sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    d.rectangle([0, 0, 175, 132], fill=(198, 198, 198, 255), outline=(20, 20, 20, 255))
    def slot(x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=(139, 139, 139, 255), outline=(55, 55, 55, 255))
    for i in range(5):
        slot(43 + 18 * i, 19)
    for r in range(3):
        for c in range(9):
            slot(7 + 18 * c, 50 + 18 * r)
    for c in range(9):
        slot(7 + 18 * c, 108)
    sheet.save(gui_out)
    print("made", gui_out.name)
