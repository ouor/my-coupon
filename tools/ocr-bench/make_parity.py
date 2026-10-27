"""Write Kotlin parity fixtures: OCR lines + Python postprocess.process() output per image.

The Python reference is C:/Users/ouor/Codes/pp-ocr-v5/postprocess.py (read-only, baseline steps 1-6).
Fixtures go to .test/core-parity/ (git-ignored: they contain real coupon numbers).
usage: python make_parity.py <ocr dump.jsonl> [...]   ->  .test/core-parity/<dump name>.jsonl
"""
import json, sys
from pathlib import Path
sys.path.insert(0, r"C:\Users\ouor\Codes\pp-ocr-v5")
import postprocess as pp

OUT = Path(__file__).resolve().parents[2] / ".test" / "core-parity"
OUT.mkdir(parents=True, exist_ok=True)
for src in sys.argv[1:]:
    dst = OUT / Path(src).name
    with open(src, encoding="utf-8") as f, open(dst, "w", encoding="utf-8") as o:
        for r in map(json.loads, f):
            o.write(json.dumps({"file": r["file"], "lines": r["lines"], "expected": pp.process(r["lines"])}, ensure_ascii=False) + "\n")
    print("wrote", dst)
