"""Write golden files for the Kotlin port: {"input": {lines, barcodes, width, height}, "expected": extract(...)}.

usage: python make_golden.py [out/<run>.jsonl]
  always:      tests/golden/<case>.json   synthetic cases (committed)
  with a run:  .test/golden/<id>.json     all 82 real images (real numbers: .test/ is git-ignored, spec §8.3-4)
"""
import json, sys
from pathlib import Path

from cases import CASES
from extract import extract
from score import DATA, load_barcodes


def write(path, inp):
    out = {"input": inp, "expected": extract(inp["lines"], inp["barcodes"], inp["width"], inp["height"])}
    path.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")


here = Path(__file__).resolve().parent / "tests" / "golden"
here.mkdir(parents=True, exist_ok=True)
for name, inp, _ in CASES:
    write(here / f"{name}.json", inp)
print(f"{len(CASES)} synthetic cases -> {here}")

if len(sys.argv) > 1:
    labels = {l["file"]: l for l in map(json.loads, (DATA / "labels.jsonl").open(encoding="utf-8"))}
    bars = load_barcodes()
    dst = DATA / "golden"
    dst.mkdir(exist_ok=True)
    n = 0
    for r in map(json.loads, open(sys.argv[1], encoding="utf-8")):
        lines = [{"text": l["text"], "score": l["score"], "poly": l["poly"]} for l in r["lines"]]
        write(dst / f"{labels[r['file']]['id']}.json",
              {"lines": lines, "barcodes": bars.get(r["file"], []), "width": r["w"], "height": r["h"]})
        n += 1
    (dst / "SOURCE.txt").write_text(f"OCR run: {Path(sys.argv[1]).name}\nextract VERSION from extract.py\n", encoding="utf-8")
    print(f"{n} images -> {dst}")
