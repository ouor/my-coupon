"""O-08: synthetic long scroll captures from dev coupons, whole-image det (app: long side 1280) vs vertical tiles.

usage: python long_capture.py            (Paddle GPU; writes out/long/*.png and out/long-<mode>.jsonl, prints scores)
Each synthetic image keeps one dev coupon at its original scale inside a tall canvas (aspect ~5.5, like a
1080x6000 capture). Fillers are dev non-coupon photos, so no holdout image is used.
Tiling: tiles of height 2.2 x width with 10% overlap, each run with the same det limit (max side 1280), boxes
shifted back to canvas coordinates, duplicates in the overlap (IoU >= 0.5, or one box inside the other) keep the
higher-confidence one. Rules (extract.py) are unchanged.
"""
import json, time
from pathlib import Path

import cv2
import numpy as np
from paddleocr import PaddleOCR

from extract import extract
from score import DATA, load_barcodes, correct

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "long"
COUPONS = ["c04", "c08", "c13", "c16", "c21", "c24", "c35", "c41", "c46", "c48"]  # every dev coupon group/brand
FILLERS = ["n03", "n05", "n10", "n20", "n02", "n04"]  # dev photo-screen / photo-handwriting
ASPECT, TILE_ASPECT, OVERLAP = 5.5, 2.2, 0.10


def read(name):
    return cv2.imdecode(np.fromfile(str(DATA / name), dtype="uint8"), cv2.IMREAD_COLOR)


def fit_width(img, w):
    return cv2.resize(img, (w, round(img.shape[0] * w / img.shape[1])), interpolation=cv2.INTER_AREA)


def build(labels):
    """-> list of (name, canvas, coupon label, y offset of the coupon)"""
    by_id = {l["id"]: l for l in labels.values()}
    fillers = [read(by_id[i]["file"]) for i in FILLERS]
    out = []
    for k, cid in enumerate(COUPONS):
        lab = by_id[cid]
        img = read(lab["file"])
        h, w = img.shape[:2]
        H = int(w * ASPECT)
        for mode in ("top", "mid", "filler"):
            canvas = np.full((H, w, 3), 255, np.uint8)
            y = 0 if mode != "mid" else (H - h) // 2
            canvas[y:y + h] = img
            if mode == "filler":  # unrelated text-heavy screenshots below the coupon
                yy, j = h, k
                while yy < H:
                    f = fit_width(fillers[j % len(fillers)], w)
                    f = f[: H - yy]
                    canvas[yy:yy + len(f)] = f
                    yy += len(f)
                    j += 1
            out.append((f"{cid}-{mode}", canvas, lab, y))
    return out


def lines_of(r, dy=0):
    return [{"text": s, "score": round(float(c), 3), "poly": [[float(p[0]), float(p[1]) + dy] for p in poly]}
            for s, c, poly in zip(r["rec_texts"], r["rec_scores"], r["rec_polys"])]


def iou_or_inside(a, b):
    ax0, ay0, ax1, ay1 = a
    bx0, by0, bx1, by1 = b
    iw, ih = max(0, min(ax1, bx1) - max(ax0, bx0)), max(0, min(ay1, by1) - max(ay0, by0))
    inter = iw * ih
    if not inter:
        return False
    sa, sb = (ax1 - ax0) * (ay1 - ay0), (bx1 - bx0) * (by1 - by0)
    return inter / (sa + sb - inter) >= 0.5 or inter / min(sa, sb) >= 0.9


def bb(l):
    xs, ys = [p[0] for p in l["poly"]], [p[1] for p in l["poly"]]
    return min(xs), min(ys), max(xs), max(ys)


def tiled(ocr, img):
    h, w = img.shape[:2]
    th = int(w * TILE_ASPECT)
    if h <= th:
        return lines_of(ocr.predict(img)[0])
    step = int(th * (1 - OVERLAP))
    keep = []
    y = 0
    while True:
        y = min(y, h - th)
        for l in lines_of(ocr.predict(img[y:y + th])[0], y):
            dup = next((k for k in keep if iou_or_inside(bb(k), bb(l))), None)
            if dup is None:
                keep.append(l)
            elif l["score"] > dup["score"]:
                keep[keep.index(dup)] = l
        if y + th >= h:
            return keep
        y += step


def main():
    labels = {l["file"]: l for l in map(json.loads, (DATA / "labels.jsonl").open(encoding="utf-8"))}
    assert all(labels[f]["split"] == "dev" for f in labels if labels[f]["id"] in COUPONS + FILLERS)
    bars = load_barcodes()
    OUT.mkdir(parents=True, exist_ok=True)
    ocr = PaddleOCR(text_detection_model_name="PP-OCRv5_mobile_det", text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
                    use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False,
                    text_det_limit_type="max", text_det_limit_side_len=1280, device="gpu")
    imgs = build(labels)
    runs = {"whole1280": lambda im: lines_of(ocr.predict(im)[0]), "tiles1280": lambda im: tiled(ocr, im)}
    for mode, fn in runs.items():
        fn(imgs[0][1])  # warm-up
        with (HERE / "out" / f"long-{mode}.jsonl").open("w", encoding="utf-8") as f:
            for name, im, lab, y in imgs:
                cv2.imencode(".png", im)[1].tofile(str(OUT / f"{name}.png"))
                t = time.perf_counter()
                lines = fn(im)
                ms = (time.perf_counter() - t) * 1000
                f.write(json.dumps({"name": name, "file": lab["file"], "dy": y, "w": im.shape[1], "h": im.shape[0],
                                    "ms": round(ms, 1), "lines": lines}, ensure_ascii=False) + "\n")
    for mode in runs:
        for decode in (True, False):
            score(mode, labels, bars if decode else {})


def score(mode, labels, bars):
    fields = ["expiry", "code", "brand", "product", "type"]
    ok = {f: 0 for f in fields}
    silent = {f: 0 for f in fields}
    n = rec = 0
    ms, bad = [], []
    for r in map(json.loads, (HERE / "out" / f"long-{mode}.jsonl").open(encoding="utf-8")):
        lab = labels[r["file"]]
        bc = [{**b, "box": [[p[0], p[1] + r["dy"]] for p in b["box"]]} for b in bars.get(r["file"], [])]
        got = extract(r["lines"], bc, r["w"], r["h"])
        n += 1
        rec += got["isCoupon"]
        ms.append(r["ms"])
        for f in fields:
            c = correct(f, got, lab)
            ok[f] += c
            key = {"expiry": "expiryDate", "code": "code", "brand": "brand", "product": "productName", "type": "type"}[f]
            if not c and key not in got["needsReview"]:
                silent[f] += 1
                bad.append(f"{r['name']}:{f}")
    print(f"{mode:10s} decode={'yes' if bars else 'no ':3s} n={n} coupon {rec}/{n} | "
          + " | ".join(f"{f} {ok[f]}/{n} (silent {silent[f]})" for f in fields)
          + f" | median {np.median(ms):.0f}ms")
    if bad:
        print("   silent:", " ".join(bad))


if __name__ == "__main__":
    main()
