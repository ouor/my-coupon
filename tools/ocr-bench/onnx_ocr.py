"""PP-OCRv5 (mobile det + korean mobile rec) on ONNX Runtime, mirroring PaddleX 3.x OCR pipeline.

Pre/post-processing follows paddlex/inference (OCR.yaml: limit_side_len=64, limit_type=min,
max_side_limit=4000, thresh=0.3, box_thresh=0.6, unclip_ratio=1.5, rec batch 6 sorted by aspect).
The app's Kotlin engine (ocr-engine module) implements the same steps; see docs/core/ocr-runtime.md.

usage: python onnx_ocr.py <out.jsonl> [--limit N] [--threads T] [--max-side S]
Writes the same jsonl shape as pp-ocr-v5/run_ocr.py (file, ms, lines[text, score, poly]).
"""
import argparse, json, math, time
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
import pyclipper

HERE = Path(__file__).parent
DATA = HERE.parents[1] / ".test"


class Ocr:
    def __init__(self, threads=4, max_side=4000):
        so = ort.SessionOptions()
        so.intra_op_num_threads = threads
        self.det = ort.InferenceSession(str(HERE / "models/PP-OCRv5_mobile_det.onnx"), so, providers=["CPUExecutionProvider"])
        self.rec = ort.InferenceSession(str(HERE / "models/korean_PP-OCRv5_mobile_rec.onnx"), so, providers=["CPUExecutionProvider"])
        chars = (HERE / "models/korean_dict.txt").read_text(encoding="utf-8").split("\n")[:-1]
        self.chars = ["blank"] + chars + [" "]
        self.max_side = max_side

    # ---- detection ----
    def det_resize(self, img):
        h, w = img.shape[:2]
        ratio = 64.0 / min(h, w) if min(h, w) < 64 else 1.0
        rh, rw = int(h * ratio), int(w * ratio)
        if max(rh, rw) > self.max_side:
            r = float(self.max_side) / max(rh, rw)
            rh, rw = int(rh * r), int(rw * r)
        rh = max(int(round(rh / 32) * 32), 32)
        rw = max(int(round(rw / 32) * 32), 32)
        if (rh, rw) == (h, w):
            return img
        return cv2.resize(img, (rw, rh))

    def detect(self, img):
        h, w = img.shape[:2]
        x = self.det_resize(img).astype(np.float32)
        x = (x / 255.0 - np.array([0.485, 0.456, 0.406], np.float32)) / np.array([0.229, 0.224, 0.225], np.float32)
        x = x.transpose(2, 0, 1)[None].astype(np.float32)
        pred = self.det.run(None, {"x": x})[0][0, 0]
        return self.db_post(pred, w, h)

    def db_post(self, pred, dw, dh):
        bitmap = (pred > 0.3).astype(np.uint8)
        hh, ww = bitmap.shape
        contours, _ = cv2.findContours(bitmap * 255, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        boxes = []
        for c in contours[:1000]:
            pts, sside = mini_box(c)
            if sside < 3:
                continue
            pts = np.array(pts)
            if score_fast(pred, pts) < 0.6:
                continue
            exp = unclip(pts, 1.5).reshape(-1, 1, 2)
            box, sside = mini_box(exp)
            if sside < 5:
                continue
            box = np.array(box)
            for i in range(4):
                box[i, 0] = max(0, min(round(box[i, 0] * dw / ww), dw))
                box[i, 1] = max(0, min(round(box[i, 1] * dh / hh), dh))
            boxes.append(box.astype(np.int16))
        boxes = sorted(boxes, key=lambda b: (b[0][1], b[0][0]))
        for i in range(len(boxes) - 1):
            for j in range(i, -1, -1):
                if abs(boxes[j + 1][0][1] - boxes[j][0][1]) < 10 and boxes[j + 1][0][0] < boxes[j][0][0]:
                    boxes[j], boxes[j + 1] = boxes[j + 1], boxes[j]
                else:
                    break
        return boxes

    # ---- recognition ----
    def crop(self, img, poly):
        box, _ = mini_box(np.array(poly).astype(np.int32))
        pts = np.array(box, np.float32)
        cw = int(max(np.linalg.norm(pts[0] - pts[1]), np.linalg.norm(pts[2] - pts[3])))
        ch = int(max(np.linalg.norm(pts[0] - pts[3]), np.linalg.norm(pts[1] - pts[2])))
        M = cv2.getPerspectiveTransform(pts, np.float32([[0, 0], [cw, 0], [cw, ch], [0, ch]]))
        out = cv2.warpPerspective(img, M, (cw, ch), borderMode=cv2.BORDER_REPLICATE, flags=cv2.INTER_CUBIC)
        if out.shape[0] * 1.0 / out.shape[1] >= 1.5:
            out = np.rot90(out)
        return out

    def rec_norm(self, img):
        h, w = img.shape[:2]
        imgW = int(48 * max(320 / 48, w / h))
        if imgW > 3200:
            r, rw, imgW = cv2.resize(img, (3200, 48)), 3200, 3200
        else:
            rw = min(imgW, int(math.ceil(48 * w / h)))
            r = cv2.resize(img, (rw, 48))
        r = (r.astype(np.float32).transpose(2, 0, 1) / 255 - 0.5) / 0.5
        out = np.zeros((3, 48, imgW), np.float32)
        out[:, :, :rw] = r
        return out

    def recognize(self, crops):
        order = sorted(range(len(crops)), key=lambda i: crops[i].shape[1] / float(crops[i].shape[0]))
        res = [None] * len(crops)
        for s in range(0, len(order), 6):
            idx = order[s:s + 6]
            ims = [self.rec_norm(crops[i]) for i in idx]
            mw = max(x.shape[2] for x in ims)
            batch = np.stack([np.pad(x, ((0, 0), (0, 0), (0, mw - x.shape[2]))) for x in ims]).astype(np.float32)
            pred = self.rec.run(None, {"x": batch})[0]
            for k, i in enumerate(idx):
                res[i] = ctc(pred[k], self.chars)
        return res

    def __call__(self, img):
        polys = self.detect(img)
        crops, kept = [], []
        for p in polys:
            c = self.crop(img, p)
            if c.size > 0:
                crops.append(c)
                kept.append(p)
        recs = self.recognize(crops) if crops else []
        return [{"text": t, "score": round(float(s), 3), "poly": p.tolist()} for p, (t, s) in zip(kept, recs)]


def ctc(p, chars):
    idx = p.argmax(-1)
    prob = p.max(-1)
    sel = np.ones(len(idx), bool)
    sel[1:] = idx[1:] != idx[:-1]
    sel &= idx != 0
    conf = prob[sel]
    return "".join(chars[i] for i in idx[sel]), (float(np.mean(conf)) if len(conf) else 0.0)


def mini_box(contour):
    rect = cv2.minAreaRect(contour)
    pts = sorted(list(cv2.boxPoints(rect)), key=lambda x: x[0])
    a, d = (0, 1) if pts[1][1] > pts[0][1] else (1, 0)
    b, c = (2, 3) if pts[3][1] > pts[2][1] else (3, 2)
    return [pts[a], pts[b], pts[c], pts[d]], min(rect[1])


def score_fast(bitmap, box):
    h, w = bitmap.shape[:2]
    box = box.copy()
    xmin = max(0, min(math.floor(box[:, 0].min()), w - 1))
    xmax = max(0, min(math.ceil(box[:, 0].max()), w - 1))
    ymin = max(0, min(math.floor(box[:, 1].min()), h - 1))
    ymax = max(0, min(math.ceil(box[:, 1].max()), h - 1))
    mask = np.zeros((ymax - ymin + 1, xmax - xmin + 1), np.uint8)
    box[:, 0] -= xmin
    box[:, 1] -= ymin
    cv2.fillPoly(mask, box.reshape(1, -1, 2).astype(np.int32), 1)
    return cv2.mean(bitmap[ymin:ymax + 1, xmin:xmax + 1], mask)[0]


def unclip(box, ratio):
    dist = cv2.contourArea(box) * ratio / cv2.arcLength(box, True)
    off = pyclipper.PyclipperOffset()
    off.AddPath(box, pyclipper.JT_ROUND, pyclipper.ET_CLOSEDPOLYGON)
    return np.array(off.Execute(dist))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--max-side", type=int, default=4000)
    a = ap.parse_args()
    ocr = Ocr(a.threads, a.max_side)
    files = [json.loads(l)["file"] for l in (DATA / "labels.jsonl").open(encoding="utf-8")]
    if a.limit:
        files = files[:a.limit]
    read = lambda f: cv2.imdecode(np.fromfile(str(DATA / f), np.uint8), cv2.IMREAD_COLOR)
    ocr(read(files[0]))  # warm-up
    with open(a.out, "w", encoding="utf-8") as f:
        for name in files:
            img = read(name)
            t = time.perf_counter()
            lines = ocr(img)
            ms = (time.perf_counter() - t) * 1000
            f.write(json.dumps({"file": name, "ms": round(ms, 1), "lines": lines}, ensure_ascii=False) + "\n")
            print(f"{ms:7.0f}ms {len(lines):3d} lines")
