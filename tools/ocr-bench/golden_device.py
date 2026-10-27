"""Compares the on-device Extract run (BenchActivity golden mode) with the goldens' expected output.
Prints only counts, golden indices and differing key names (real goldens hold real coupon numbers).
usage: python golden_device.py <golden-out.jsonl> <golden dir> [<golden dir> ...]
"""
import json, sys
from pathlib import Path


def norm(v):
    if isinstance(v, bool) or v is None or isinstance(v, str):
        return v
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, list):
        return [norm(x) for x in v]
    return {k: norm(x) for k, x in v.items()}


got = {r["file"]: r["got"] for r in map(json.loads, open(sys.argv[1], encoding="utf-8"))}
want = {}
for d in sys.argv[2:]:
    for p in sorted(Path(d).glob("*.json")):
        want[p.name] = json.loads(p.read_text(encoding="utf-8"))["expected"]
names = sorted(want)
bad = 0
for i, n in enumerate(names):
    if n not in got:
        print(f"#{i} missing"); bad += 1; continue
    keys = [k for k in set(want[n]) | set(got[n]) if norm(want[n].get(k)) != norm(got[n].get(k))]
    if keys:
        bad += 1
        print(f"#{i} differs: {sorted(keys)}")
print(f"device extract identical {len(names) - bad}/{len(names)}")
