"""Compare postprocess.py (steps 1-6) output on two OCR dumps: rows, barcode, expiry per image.
usage: python pp_compare.py ref.jsonl cand.jsonl"""
import json, sys
sys.path.insert(0, r"C:\Users\ouor\Codes\pp-ocr-v5")
import postprocess as pp

def load(p): return {r["file"]: r for r in map(json.loads, open(p, encoding="utf-8"))}
ref, cand = load(sys.argv[1]), load(sys.argv[2])
labels = {l["file"]: l for l in map(json.loads, open(r"C:\Users\ouor\Codes\mycoupon\.test\labels.jsonl", encoding="utf-8"))}
same = {"rows": 0, "barcode": 0, "expiry": 0}; n = 0; cn = 0; cs = {"rows": 0, "barcode": 0, "expiry": 0}
for f in ref:
    a, b = pp.process(ref[f]["lines"]), pp.process(cand[f]["lines"])
    n += 1; c = labels[f]["is_coupon"]; cn += c
    for k in same:
        eq = a[k] == b[k]; same[k] += eq; cs[k] += eq and c
        if not eq and k != "rows": print("  differs", labels[f]["id"], k)
print(f"all {n}: " + ", ".join(f"{k} {v}/{n}" for k, v in same.items()))
print(f"coupons {cn}: " + ", ".join(f"{k} {v}/{cn}" for k, v in cs.items()))
