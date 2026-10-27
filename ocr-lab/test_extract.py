"""Checks extract() on the synthetic cases and that tests/golden/ is current. usage: python test_extract.py"""
import json
from pathlib import Path

from cases import CASES
from extract import extract

GOLDEN = Path(__file__).resolve().parent / "tests" / "golden"

fails = 0
for name, inp, want in CASES:
    got = extract(inp["lines"], inp["barcodes"], inp["width"], inp["height"])
    for k, v in want.items():
        if got[k] != v:
            fails += 1
            print(f"FAIL {name}.{k}: want {v!r} got {got[k]!r}")
    g = GOLDEN / f"{name}.json"
    if not g.exists() or json.loads(g.read_text(encoding="utf-8"))["expected"] != json.loads(json.dumps(got, ensure_ascii=False)):
        fails += 1
        print(f"FAIL {name}: tests/golden out of date (run make_golden.py)")
print("ok" if not fails else f"{fails} failures")
raise SystemExit(1 if fails else 0)
