"""Score extract() against .test/labels.jsonl (final extracted value == label, spec §2.2).

usage: python score.py out/<run>.jsonl [--no-decode] [--holdout] [--quiet] [--baseline]
  dev (default): totals, per-group scores and every miss (per image).
  --holdout:     holdout totals and per-group scores only; never prints per-image results (spec §2.2).
  --no-decode:   pretend ZXing failed on every image (OCR-only code path).
  --baseline:    score REPORT steps 1-6 (postprocess.py) instead of extract().
"""
import json, re, sys
from collections import defaultdict
from pathlib import Path

from extract import extract, norm

DATA = Path(__file__).resolve().parent.parent / ".test"
FIELDS = ["expiry", "code", "brand", "product", "type", "face"]
KEY = {"expiry": "expiryDate", "code": "code", "brand": "brand", "product": "productName", "type": "type", "face": "faceValue"}


def load_barcodes():
    out = {}
    for r in map(json.loads, (DATA / "zxing.jsonl").open(encoding="utf-8")):
        out[r["file"]] = [{"value": h["text"], "format": h["format"],
                           "box": [[p[0] / h.get("scale", 1), p[1] / h.get("scale", 1)] for p in h["box"]]}
                          for h in r["hits"]]
    return out


def correct(f, got, lab):
    if f == "expiry":
        return got["expiryDate"] == lab["expiry_date"]
    if f == "code":
        return got["code"] is not None and re.sub(r"[^0-9A-Za-z]", "", got["code"]) == lab["barcode_value"]
    if f == "brand":
        return got["brand"] is not None and norm(got["brand"]) == norm(lab["brand"])
    if f == "product":
        return got["productName"] is not None and norm(got["productName"]) == norm(lab["product_name"])
    if f == "type":
        return got["type"] == lab["coupon_type"].upper()
    if f == "face":
        return got["faceValue"] == lab["face_value"]


def evaluate(results):
    """results: [(label, extraction)] -> counters"""
    c = defaultdict(int)
    for lab, got in results:
        if not lab["is_coupon"]:
            c["neg"] += 1
            c["fp"] += got["isCoupon"]
            continue
        c["n"] += 1
        c["recall"] += got["isCoupon"]
        c["exp_review"] += "expiryDate" in got["needsReview"]
        c["pn_review"] += "productName" in got["needsReview"]
        c["issuer"] += norm(got["issuer"] or "") == norm(lab["issuer"] or "")
        for f in FIELDS:
            if f == "face" and lab["coupon_type"] != "amount":
                continue
            c[f + "_n"] += 1
            ok = correct(f, got, lab)
            c[f] += ok
            if not ok and KEY[f] not in got["needsReview"]:
                c[f + "_silent"] += 1
    return c


def fmt(c):
    n = c["n"]
    parts = [f"coupons {n}"]
    for f in FIELDS:
        if c[f + "_n"]:
            parts.append(f"{f} {c[f]}/{c[f + '_n']} (silent {c[f + '_silent']})")
    parts.append(f"recall {c['recall']}/{n}" if n else "")
    parts.append(f"expiry-review {c['exp_review']}/{n}" if n else "")
    parts.append(f"product-review {c['pn_review']}/{n}" if n else "")
    parts.append(f"issuer {c['issuer']}/{n}" if n else "")
    if c["neg"]:
        parts.append(f"non-coupon FP {c['fp']}/{c['neg']}")
    return " | ".join(p for p in parts if p)


BASELINE = "--baseline" in sys.argv


def baseline(lines):
    """REPORT steps 1-6 as shipped (postprocess.py): code + expiry only, no review flags, coupon = a code was found."""
    import postprocess
    pp = postprocess.process(lines)
    return {"isCoupon": pp["barcode"] is not None, "expiryDate": pp["expiry"], "code": pp["barcode"],
            "brand": None, "productName": None, "type": None, "faceValue": None, "issuer": None, "needsReview": {}}


def run(path, decode=True, split="dev"):
    labels = {l["file"]: l for l in map(json.loads, (DATA / "labels.jsonl").open(encoding="utf-8"))}
    bars = load_barcodes() if decode else {}
    out = []
    for r in map(json.loads, Path(path).open(encoding="utf-8")):
        lab = labels[r["file"]]
        if lab["split"] != split:
            continue
        if BASELINE:
            got = baseline(r["lines"])
        else:
            got = extract(r["lines"], bars.get(r["file"], []), r.get("w", 0), r.get("h", 0))
        out.append((lab, got))
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    split = "holdout" if "--holdout" in sys.argv else "dev"
    for path in args:
        res = run(path, "--no-decode" not in sys.argv, split)
        print(f"== {path} [{split}{', no-decode' if '--no-decode' in sys.argv else ''}]")
        print("  ALL   ", fmt(evaluate(res)))
        groups = defaultdict(list)
        for lab, got in res:
            groups[lab["group"]].append((lab, got))
        for g in sorted(groups, key=lambda g: (not groups[g][0][0]["is_coupon"], g)):
            print(f"  {g:20s}", fmt(evaluate(groups[g])))
        if split != "dev" or "--quiet" in sys.argv:
            continue
        for lab, got in res:  # dev only: per-image misses
            if not lab["is_coupon"]:
                if got["isCoupon"]:
                    print(f"   FP {lab['id']} {lab['group']}")
                continue
            if not got["isCoupon"]:
                print(f"   MISSED-COUPON {lab['id']}")
            for f in FIELDS:
                if f == "face" and lab["coupon_type"] != "amount":
                    continue
                if not correct(f, got, lab):
                    want = {"expiry": lab["expiry_date"], "code": "<code>", "brand": lab["brand"],
                            "product": lab["product_name"], "type": lab["coupon_type"], "face": lab["face_value"]}[f]
                    have = got[KEY[f]] if f != "code" else ("<other>" if got["code"] else None)
                    print(f"   miss {lab['id']} {f}: want {want!r} got {have!r} review={got['needsReview'].get(KEY[f])}")


if __name__ == "__main__":
    main()
