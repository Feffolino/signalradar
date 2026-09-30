"""Build the shipped radar item models from art/radar.json (Blockbench export, tier 0 geometry).

Outputs (written straight into the mod resources, see art/README.md):
  models/item/radar.json             builtin/entity item model: display transforms + particle only. The item is drawn
                                     by the BEWLR (RadarItemRenderer), which picks the body model by tier.
  models/item/radar_body_t0..t4.json geometry per tier (antenna more articulated per tier, see antenna()); loaded as standalone
                                     models (ModelEvent.RegisterAdditional).
Run from anywhere:  python art/make_tiers.py
"""
import copy
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "signalradar", "models", "item")

base = json.load(open(os.path.join(HERE, "radar.json")))
els = base["elements"]
BY_NAME = {e["name"]: e for e in els}
MAST, TIP, BASE, BRASS = BY_NAME["antenna"], BY_NAME["antenna_tip"], BY_NAME["antenna_base"], BY_NAME["dial"]
ANTENNA_PARTS = ("antenna", "antenna_tip")

# Geometry fixes applied on top of the Blockbench export (kept here so a re-export doesn't bring bugs back).
# bezel_bottom used to span x 1.5..14.5 and shared the front plane z=10.75 with the corner bumpers -> z-fighting.
FIXUPS = {
    "bezel_bottom": {"from": [2.5, 3.5, 10], "to": [13.5, 5, 10.75]},
    "bumper_bl": {"to": [2.5, 4.5, 10.95]},
    "bumper_br": {"to": [15.5, 4.5, 10.95]},
}

CX, CZ = 12.75, 7.75   # main mast axis (centre of antenna_base)
SX, SZ = 3.25, 7.75    # secondary antenna axis (left side of the hood), tiers 3-4


def box(name, src, frm, to, rotation=None):
    e = copy.deepcopy(src)
    e["name"] = name
    e["from"] = [round(v, 4) for v in frm]
    e["to"] = [round(v, 4) for v in to]
    e.pop("rotation", None)
    if rotation:
        e["rotation"] = rotation
    return e


def mast(name, x, z, y0, y1, w, src=None):
    return box(name, src or MAST, [x - w, y0, z - w], [x + w, y1, z + w])


def crossbar(name, y, half, t=0.15):
    return box(name, MAST, [CX - half, y, CZ - t], [CX + half, y + 2 * t, CZ + t])


def tip(name, x, z, y, r=0.45):
    return box(name, TIP, [x - r, y, z - r], [x + r, y + 2 * r, z + r])


def antenna(tier):
    """Antenna gets more articulated per tier:
    t0 short whip | t1 + brass loading coil, longer whip | t2 telescopic mast + crossbar |
    t3 + Yagi crossbars (3) + secondary whip on the left | t4 + third mast section, 4 crossbars, dish on the left."""
    out = []
    if tier == 0:
        out.append(mast("antenna_0", CX, CZ, 16, 19, 0.4))
        out.append(tip("antenna_tip", CX, CZ, 19, 0.4))
        return out
    out.append(mast("antenna_coil", CX, CZ, 16, 17.2, 0.6, BRASS))
    if tier == 1:
        out.append(mast("antenna_0", CX, CZ, 17.2, 21, 0.35))
        out.append(tip("antenna_tip", CX, CZ, 21, 0.4))
        return out
    top = 23.4 if tier == 2 else 23.4 if tier == 3 else 25.2
    out.append(mast("antenna_0", CX, CZ, 17.2, 20.2, 0.4))
    out.append(mast("antenna_joint_0", CX, CZ, 20.0, 20.4, 0.55, BASE))
    out.append(mast("antenna_1", CX, CZ, 20.2, 23.4 if tier < 4 else 22.6, 0.3))
    if tier == 4:
        out.append(mast("antenna_joint_1", CX, CZ, 22.4, 22.8, 0.42, BASE))
        out.append(mast("antenna_2", CX, CZ, 22.6, 25.2, 0.22))
    bars = {2: [(22.2, 2.0)],
            3: [(20.9, 2.5), (21.9, 2.0), (22.9, 1.5)],
            4: [(20.9, 2.75), (21.8, 2.25), (23.3, 1.75), (24.3, 1.25)]}[tier]
    for i, (y, half) in enumerate(bars):
        out.append(crossbar(f"antenna_bar_{i}", y, half))
    out.append(tip("antenna_tip", CX, CZ, top, 0.45))
    if tier >= 3:  # secondary antenna on the left of the hood
        out.append(box("aux_base", BASE, [SX - 0.5, 15, SZ - 0.5], [SX + 0.5, 15.8, SZ + 0.5]))
        if tier == 3:
            out.append(mast("aux_whip", SX, SZ, 15.8, 19.5, 0.25))
            out.append(tip("aux_tip", SX, SZ, 19.5, 0.3))
        else:  # small dish tilted up/back with a feed horn
            out.append(mast("aux_stem", SX, SZ, 15.8, 17.6, 0.25))
            out.append(box("aux_dish", BASE, [SX - 1.6, 17.6, SZ - 1.6], [SX + 1.6, 17.9, SZ + 1.6],
                           {"angle": -22.5, "axis": "x", "origin": [SX, 17.75, SZ]}))
            out.append(mast("aux_feed", SX, SZ, 17.9, 19.1, 0.12))
            out.append(tip("aux_tip", SX, SZ, 19.1, 0.25))
    return out


def body(tier):
    out = []
    for e in els:
        if e["name"] in ANTENNA_PARTS:
            continue
        e = copy.deepcopy(e)
        e.update(copy.deepcopy(FIXUPS.get(e["name"], {})))
        out.append(e)
    out += antenna(tier)
    # Geometry only: display transforms live on the item model (the BEWLR draws this in model space).
    m = {k: v for k, v in base.items() if k not in ("elements", "display", "overrides")}
    m["elements"] = out
    return m


# Hand poses. Tune here (the Blockbench display tab is overridden for the hand entries).
# FP_LIFT_Y: extra first-person translation y (display units, 1/16 block) so the device sits higher in both hands.
FP_LIFT_Y = 2


def hand_display(display):
    """Vanilla ItemTransform.apply(leftHand, ...) already mirrors the left hand (negates translation x and rotation
    y/z), so the left entries must carry the SAME numbers as the right ones; writing them pre-mirrored mirrors twice
    and turns the screen away from the camera."""
    d = copy.deepcopy(display)
    fp = d["firstperson_righthand"]
    fp["translation"][1] += FP_LIFT_Y
    d["firstperson_lefthand"] = copy.deepcopy(fp)
    d["thirdperson_lefthand"] = copy.deepcopy(d["thirdperson_righthand"])
    return d


def item_model():
    return {
        "parent": "builtin/entity",
        "textures": {"particle": base["textures"].get("particle", base["textures"]["0"])},
        "display": hand_display(base["display"]),
    }


def write(name, m):
    with open(os.path.join(OUT, name), "w", newline="\n") as f:
        json.dump(m, f, indent=1)
        f.write("\n")


write("radar.json", item_model())
print("radar.json (builtin/entity)")
for t in range(5):
    m = body(t)
    write(f"radar_body_t{t}.json", m)
    top = max(e["to"][1] for e in m["elements"])
    print(f"radar_body_t{t}.json", len(m["elements"]), "elements, top y", top)
