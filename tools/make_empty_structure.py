"""Write data/signalradar/structure/gametest_empty.nbt: a 1x1x1 air structure (game test template).
Plain gzip + big-endian NBT, no third-party libraries. DataVersion 3955 = Minecraft 1.21.1."""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/signalradar/structure/gametest_empty.nbt"

def s(x):
    b = x.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def tag(t, name, payload):
    return bytes([t]) + s(name) + payload

def i32(v):
    return struct.pack(">i", v)

def lst(t, items):
    return bytes([t]) + struct.pack(">i", len(items)) + b"".join(items)

def comp(entries):
    return b"".join(entries) + b"\x00"

root = tag(10, "", comp([
    tag(3, "DataVersion", i32(3955)),
    tag(9, "size", lst(3, [i32(1)] * 3)),
    tag(9, "palette", lst(10, [comp([tag(8, "Name", s("minecraft:air"))])])),
    tag(9, "blocks", lst(10, [comp([tag(9, "pos", lst(3, [i32(0)] * 3)), tag(3, "state", i32(0))])])),
    tag(9, "entities", lst(10, [])),
]))
OUT.parent.mkdir(parents=True, exist_ok=True)
with gzip.open(OUT, "wb") as f:
    f.write(root)
print("wrote", OUT, OUT.stat().st_size, "bytes")
