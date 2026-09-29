"""Generate radar_t0..t4.json from radar.json: antenna mast grows one segment per tier.
radar.json (tier 0) gets item overrides on predicate signalradar:tier."""
import json, copy
base = json.load(open("radar.json"))
els = base["elements"]
mast = next(e for e in els if e["name"] == "antenna")
tip = next(e for e in els if e["name"] == "antenna_tip")
rest = [e for e in els if e["name"] not in ("antenna", "antenna_tip")]
SEG = 1.75  # height of one mast segment
Y0 = 16.0

def model(tier):
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
    m = {k: v for k, v in base.items() if k != "elements"}
    m["elements"] = out
    return m

for t in range(5):
    m = model(t)
    if t == 0:
        m["overrides"] = [{"predicate": {"signalradar:tier": n / 4}, "model": f"signalradar:item/radar_t{n}"} for n in range(1, 5)]
        name = "radar.json"
    else:
        name = f"radar_t{t}.json"
    json.dump(m, open(name if t else "radar_item.json", "w"), indent=1)
    print(name, len(m["elements"]), "elements, antenna top", round(Y0 + (t + 1) * SEG + 1, 2))
