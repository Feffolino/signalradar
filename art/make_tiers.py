"""Build the shipped radar item models from art/radar.json (Blockbench export, tier 0 geometry).

Outputs (written straight into the mod resources, see art/README.md):
  models/item/radar.json             builtin/entity item model: display transforms + particle only. The item is drawn
                                     by the BEWLR (RadarItemRenderer), which picks the body model by tier.
  models/item/radar_body_t0..t4.json geometry per tier (antenna mast grows one segment per tier); loaded as standalone
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
mast = next(e for e in els if e["name"] == "antenna")
tip = next(e for e in els if e["name"] == "antenna_tip")
rest = [e for e in els if e["name"] not in ("antenna", "antenna_tip")]
SEG = 1.75  # height of one mast segment
Y0 = 16.0


def body(tier):
    out = list(rest)
    y = Y0
    for i in range(tier + 1):
        s = copy.deepcopy(mast)
        s["name"] = f"antenna_{i}"
        w = 0.4 - 0.05 * i  # segments get thinner going up
        cx, cz = 12.75, 7.75
        s["from"] = [cx - w, y, cz - w]
        s["to"] = [cx + w, y + SEG, cz + w]
        out.append(s)
        if i < tier:  # joint ring between segments
            r = copy.deepcopy(mast)
            r["name"] = f"antenna_joint_{i}"
            r["from"] = [cx - w - 0.15, y + SEG - 0.25, cz - w - 0.15]
            r["to"] = [cx + w + 0.15, y + SEG + 0.25, cz + w + 0.15]
            out.append(r)
        y += SEG
    t = copy.deepcopy(tip)
    t["from"] = [12.15, y, 7.15]
    t["to"] = [13.35, y + 1, 8.35]
    out.append(t)
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
    print(f"radar_body_t{t}.json", len(m["elements"]), "elements, antenna top", round(Y0 + (t + 1) * SEG + 1, 2))
