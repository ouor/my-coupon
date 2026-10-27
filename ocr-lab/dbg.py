"""Print rows and fields for dev images: python dbg.py out/<run>.jsonl c24,c13 (dev only; holdout is refused)."""
import json, sys
from extract import extract
from score import DATA, load_barcodes
L = {l["file"]: l for l in map(json.loads, (DATA / "labels.jsonl").open(encoding="utf-8"))}
B = load_barcodes() if "--no-decode" not in sys.argv else {}
ids = sys.argv[2].split(",")
for r in map(json.loads, open(sys.argv[1], encoding="utf-8")):
    lab = L[r["file"]]
    if lab["split"] != "dev" or lab["id"] not in ids:
        continue
    g = extract(r["lines"], B.get(r["file"], []), r.get("w", 0), r.get("h", 0))
    print("==", lab["id"], lab["group"])
    for row in g["rows"]:
        print("   ", "N" if row["notes"] else " ", [round(v) for v in row["box"]], row["text"])
    print("   ", {k: v for k, v in g.items() if k not in ("rows", "notes", "code", "fieldBoxes")})
