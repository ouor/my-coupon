"""Layout-agnostic post-processing for PP-OCRv5 output (steps 1-6).

No issuer/brand templates or dictionaries: only confidence, character classes and
relative box geometry, so it should transfer to unseen coupon layouts.
usage: python postprocess.py out/mobile-gpu.jsonl ...   (scores against labels.jsonl)
"""
import datetime as dt
import json, re, sys
from pathlib import Path

MIN_SCORE = 0.6
ROW_OVERLAP = 0.5      # vertical overlap / smaller box height to share a row
WRAP_GAP = 0.5         # max gap between wrapped lines, in line heights
WRAP_HEIGHT = 0.8      # min height ratio of wrapped lines (same font size)
WRAP_WIDTH = 0.75      # first line must span this share of the widest line (a wrap means the line was full)
JOIN_GAP = 1.0         # boxes in one row closer than this many line heights are one text run
DATE_KEYWORDS = ("유효", "사용", "까지", "기간", "만료", "~")


def norm(s):  # comparison key: whitespace/punctuation-insensitive (step 4)
    return re.sub(r"[\W_]+", "", s).lower()


def box(line):
    xs = [p[0] for p in line["poly"]]
    ys = [p[1] for p in line["poly"]]
    return {"text": line["text"], "x0": min(xs), "x1": max(xs), "y0": min(ys), "y1": max(ys), "h": max(ys) - min(ys)}


# step 1: drop low-confidence and symbol-only detections (logos, product photos)
def is_noise(line):
    return line["score"] < MIN_SCORE or not re.search(r"[0-9A-Za-z가-힣]", line["text"])


# step 2: boxes overlapping vertically with a row's first box share that row, left to right
def rows_of(boxes):
    rows = []
    for b in sorted(boxes, key=lambda b: (b["y0"] + b["y1"]) / 2):
        if rows:
            a = rows[-1][0]
            ov = min(a["y1"], b["y1"]) - max(a["y0"], b["y0"])
            if ov / min(a["y1"] - a["y0"], b["y1"] - b["y0"]) >= ROW_OVERLAP:
                rows[-1].append(b)
                continue
        rows.append([b])
    return [join_runs(sorted(r, key=lambda b: b["x0"])) for r in rows]


def join_runs(row):  # the detector sometimes splits one text line into touching boxes
    out = [row[0]]
    for b in row[1:]:
        a = out[-1]
        if b["x0"] - a["x1"] < JOIN_GAP * min(a["h"], b["h"]):
            out[-1] = {**a, "text": f"{a['text']} {b['text']}", "x1": max(a["x1"], b["x1"]),
                       "y0": min(a["y0"], b["y0"]), "y1": max(a["y1"], b["y1"])}
        else:
            out.append(b)
    return out


def is_label(text):  # short Hangul-only cell, e.g. "유효기간", "교환처"; Latin cells are mostly logos
    return len(text) <= 8 and re.fullmatch(r"[가-힣 ]+", text) is not None


def row_text(row):
    if len(row) == 2 and is_label(row[0]["text"]):
        return f"{row[0]['text']}: {row[1]['text']}"
    return " | ".join(b["text"] for b in row)


# step 3: join a single-box row with the next when it looks like a wrap of the same text block
def merge_wraps(rows):
    widest = max((b["x1"] - b["x0"] for r in rows for b in r), default=1)
    out = []
    for r in rows:
        if out and len(r) == 1 and len(out[-1]) == 1:
            a, b = out[-1][0], r[0]
            # compare against the last physical line, so a wrap chain ends once a line is not full
            ha, hb = a["h"], b["h"]
            wa, wb = a.get("w", a["x1"] - a["x0"]), b["x1"] - b["x0"]
            if (b["y0"] - a["y1"] <= WRAP_GAP * min(ha, hb)
                    and min(ha, hb) / max(ha, hb) >= WRAP_HEIGHT
                    and wa >= WRAP_WIDTH * widest and wb <= wa
                    and not b["text"].startswith("-")):
                # shortcut: always joins with a space; whether the wrap split a word is unknowable without a lexicon
                out[-1] = [{**a, "text": f"{a['text']} {b['text']}", "x0": min(a["x0"], b["x0"]),
                            "x1": max(a["x1"], b["x1"]), "y1": b["y1"], "h": hb, "w": wb}]
                continue
        out.append(r)
    return out


# step 4 (display): only spacing that is unambiguous; comparisons use norm() instead
def tidy(s):
    s = re.sub(r"(?<!\d)\s*:\s*(?=\S)", ": ", s)  # not "1:1"
    s = re.sub(r"\]\s*(?=\S)", "] ", s)
    s = re.sub(r"(?<=[가-힣])(?=\d{4,})", " ", s)      # 주문번호0000000000
    return re.sub(r"\s*~\s*", " ~ ", s)


# step 5: barcode number = 12-20 digits in space/hyphen groups (a group gap is sometimes read as "."); a bare number box beats one glued to a label
NUM = re.compile(r"\d[\d\s.-]{10,}\d")


def barcode(boxes):
    cands = []
    for b in boxes:
        for m in NUM.finditer(b["text"]):
            digits = re.sub(r"\D", "", m.group())
            if 12 <= len(digits) <= 20:
                bare = not re.sub(r"[\d\s.-]", "", b["text"])
                cands.append((bare, len(digits), digits))
    return max(cands)[2] if cands else None


# step 6: expiry = earliest end date among rows that read like a validity period
FULL = re.compile(r"(20\d\d)\s*[./년-]\s*(\d{1,2})\s*[./월-]\s*(\d{1,2})")
NOYEAR = re.compile(r"(?<![\d.:])(\d{1,2})\s*[/월]\s*(\d{1,2})\s*일?\s*까지")


def mkdate(y, m, d):
    try:
        return dt.date(int(y), int(m), int(d))
    except ValueError:
        return None


def expiry(texts):
    full = [[d for d in (mkdate(*g) for g in FULL.findall(t)) if d] for t in texts]
    years = [d.year for ds in full for d in ds]
    cands, fallback = [], []
    for t, ds in zip(texts, full):
        if "발급" in t:
            continue
        if years:  # "8/31까지" carries no year; borrow the latest one printed on the coupon
            ds = ds + [d for d in (mkdate(max(years), m, d) for m, d in NOYEAR.findall(t)) if d]
        if not ds:
            continue
        end = max(ds)  # a range's end
        (cands if any(k in t for k in DATE_KEYWORDS) else fallback).append(end)
    if cands:
        return min(cands)  # conflicting periods: the earlier one is the safe deadline
    return max(fallback) if fallback else None


def process(lines):
    kept = [l for l in lines if not is_noise(l)]
    boxes = [box(l) for l in kept]
    rows = merge_wraps(rows_of(boxes))
    texts = [tidy(row_text(r)) for r in rows]
    exp = expiry(texts)
    return {"rows": texts, "dropped": [l["text"] for l in lines if is_noise(l)],
            "barcode": barcode(boxes), "expiry": exp.isoformat() if exp else None}


if __name__ == "__main__":
    DATA = Path(r"C:\Users\ouor\Codes\mycoupon\.test")
    labels = {l["file"]: l for l in map(json.loads, (DATA / "labels.jsonl").open(encoding="utf-8"))}
    zxing = {r["file"]: {h["text"] for h in r["hits"]} for r in map(json.loads, (DATA / "zxing.jsonl").open(encoding="utf-8"))}

    def within(gt, units):  # GT field intact inside one unit (line or row)
        return any(norm(gt) in norm(u) for u in units)

    for path in sys.argv[1:]:
        c = dict.fromkeys(["n", "pn_raw", "pn_pp", "pn_disp_raw", "pn_disp_pp", "ev_raw", "ev_pp",
                           "exp", "bc", "bc_zx", "zx_n", "neg", "neg_bc", "neg_exp"], 0)
        misses, dropped = [], []
        for r in map(json.loads, Path(path).open(encoding="utf-8")):
            lab, pp = labels[r["file"]], process(r["lines"])
            raw = [l["text"] for l in r["lines"]]
            if not lab["is_coupon"]:
                c["neg"] += 1
                c["neg_bc"] += pp["barcode"] is not None
                c["neg_exp"] += pp["expiry"] is not None
                continue
            c["n"] += 1
            dropped += pp["dropped"]
            c["pn_raw"] += within(lab["product_name"], raw)
            c["pn_pp"] += within(lab["product_name"], pp["rows"])
            c["pn_disp_raw"] += any(lab["product_name"] in t for t in raw)
            c["pn_disp_pp"] += any(lab["product_name"] in t for t in pp["rows"])
            c["ev_raw"] += within(lab["expiry_evidence"], raw)
            c["ev_pp"] += within(lab["expiry_evidence"], pp["rows"])
            c["exp"] += pp["expiry"] == lab["expiry_date"]
            c["bc"] += pp["barcode"] == lab["barcode_value"]
            if zxing.get(r["file"]):
                c["zx_n"] += 1
                c["bc_zx"] += pp["barcode"] in zxing[r["file"]]
            for f, got in (("expiry", pp["expiry"]), ("barcode", pp["barcode"])):
                want = lab["expiry_date" if f == "expiry" else "barcode_value"]
                if got != want:
                    misses.append((lab["id"], f, want, got))
            if not within(lab["product_name"], pp["rows"]):
                misses.append((lab["id"], "product_name", lab["product_name"], pp["rows"]))
        n = c["n"]
        print(f"\n== {path}  ({n} coupons)")
        print(f"  product_name intact in one unit   raw {c['pn_raw']}/{n} -> pp {c['pn_pp']}/{n}")
        print(f"  product_name exact incl. spaces   raw {c['pn_disp_raw']}/{n} -> pp {c['pn_disp_pp']}/{n}")
        print(f"  expiry_evidence intact in one unit raw {c['ev_raw']}/{n} -> pp {c['ev_pp']}/{n}")
        print(f"  expiry_date extracted (single pick) {c['exp']}/{n}")
        print(f"  barcode_value extracted (single pick) {c['bc']}/{n}, agrees with zxing {c['bc_zx']}/{c['zx_n']}")
        print(f"  non-coupon ({c['neg']}): barcode-like number {c['neg_bc']}, date {c['neg_exp']}")
        print(f"  dropped as noise on coupons: {len(dropped)} -> {sorted(set(dropped))}")
        for m in misses:
            print("   miss", *m)
