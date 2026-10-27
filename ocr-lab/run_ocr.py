"""Run PP-OCRv5 (Paddle) over the .test set and dump one jsonl per run.

usage: python run_ocr.py <tag> [--det mobile|server] [--device gpu|cpu] [--onnx]
                         [--limit-type min|max] [--limit-side N] [--thresh F] [--box-thresh F] [--unclip F]
default = the reference config (mobile det + korean mobile rec, PaddleOCR defaults: limit min 64).
Output: out/<tag>.jsonl, one image per line: {file, w, h, ms, lines:[{text, score, poly}]}.
OCR text includes real coupon numbers, so out/ is git-ignored.
"""
import argparse, json, time
from pathlib import Path

import cv2
from paddleocr import PaddleOCR

DATA = Path(__file__).resolve().parent.parent / ".test"
DET = {"mobile": "PP-OCRv5_mobile_det", "server": "PP-OCRv5_server_det"}

ap = argparse.ArgumentParser()
ap.add_argument("tag")
ap.add_argument("--det", default="mobile")
ap.add_argument("--device", default="gpu")
ap.add_argument("--limit-type", default=None)
ap.add_argument("--limit-side", type=int, default=None)
ap.add_argument("--thresh", type=float, default=None)
ap.add_argument("--box-thresh", type=float, default=None)
ap.add_argument("--unclip", type=float, default=None)
ap.add_argument("--onnx", action="store_true", help="ONNX Runtime engine on models/onnx/* (paddle2onnx of the same models)")
a = ap.parse_args()

MODELS = Path(__file__).resolve().parent / "models" / "onnx"
extra = dict(engine="onnxruntime", text_detection_model_dir=str(MODELS / DET[a.det]),
             text_recognition_model_dir=str(MODELS / "korean_PP-OCRv5_mobile_rec")) if a.onnx else {}
ocr = PaddleOCR(
    **extra,
    text_detection_model_name=DET[a.det],
    text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
    use_doc_orientation_classify=False,
    use_doc_unwarping=False,
    use_textline_orientation=False,
    text_det_limit_type=a.limit_type,
    text_det_limit_side_len=a.limit_side,
    text_det_thresh=a.thresh,
    text_det_box_thresh=a.box_thresh,
    text_det_unclip_ratio=a.unclip,
    device=a.device,
)

files = [json.loads(l)["file"] for l in (DATA / "labels.jsonl").open(encoding="utf-8")]
ocr.predict(str(DATA / files[0]))  # warm-up, excluded from timing

out = Path(__file__).resolve().parent / "out" / f"{a.tag}.jsonl"
out.parent.mkdir(exist_ok=True)
with out.open("w", encoding="utf-8") as f:
    for name in files:
        img = cv2.imdecode(__import__("numpy").fromfile(str(DATA / name), dtype="uint8"), cv2.IMREAD_COLOR)
        t = time.perf_counter()
        r = ocr.predict(img)[0]
        ms = (time.perf_counter() - t) * 1000
        lines = [{"text": s, "score": round(float(c), 3), "poly": p.tolist()}
                 for s, c, p in zip(r["rec_texts"], r["rec_scores"], r["rec_polys"])]
        f.write(json.dumps({"file": name, "w": img.shape[1], "h": img.shape[0], "ms": round(ms, 1), "lines": lines},
                           ensure_ascii=False) + "\n")
print(f"wrote {out}")
