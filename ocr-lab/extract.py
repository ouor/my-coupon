"""Coupon field extraction from PP-OCRv5 lines + decoded barcodes (the reference implementation).

Pure function, standard library only (re, datetime), no model code: this is what ocr-core ports to Kotlin.
    extract(lines, barcodes, width, height) -> dict      (format: docs/ocr/porting-spec.md)
lines:    [{"text": str, "score": float, "poly": [[x, y] x4]}]   PP-OCRv5 rec_texts/rec_scores/rec_polys
barcodes: [{"value": str, "format": str, "box": [[x, y] ...]}]    ZXing results in image pixels
No issuer templates, brand dictionaries or absolute coordinates: only confidence, character classes,
relative geometry (in line heights / image width), generic Korean coupon vocabulary and the barcode box.
"""
import datetime as dt
import re

VERSION = "1"

MIN_SCORE = 0.6        # step 1: lines below this are noise
ROW_OVERLAP = 0.5      # step 2: vertical overlap / smaller box height to share a row
JOIN_GAP = 1.0         # step 2: boxes in a row closer than this many line heights are one text run
WRAP_GAP = 0.5         # step 3: max gap between wrapped lines, in line heights
WRAP_HEIGHT = 0.8      # step 3: min height ratio of wrapped lines
WRAP_HEIGHT_BRACKET = 0.6  # step 3: same, when line 1 leaves a bracket open and line 2 closes it
WRAP_WIDTH = 0.75      # step 3: the first line must span this share of the widest line
REVIEW_SCORE = 0.9     # a field read from a run below this OCR confidence is flagged LOW_CONFIDENCE

# generic coupon vocabulary (not issuer/brand specific)
NOTES_HEADER = re.compile(r"(유의|주의|안내|참고|이용)\s*(사항|안내|방법)|이용\s*안내|사용\s*방법|꼭\s*확인")
BULLET = re.compile(r"^\s*[-·•*※ㆍ]")
LABEL_TAIL = re.compile(r"(기간|기한|처|명|번호|일|일시|금액|수량|매장|장소|종류|가격|상품|브랜드)$")
DATE_KEYWORDS = ("유효", "사용", "까지", "기간", "만료", "~")
NOTE_DATE_KEYWORDS = ("유효", "사용", "만료")
NOT_EXPIRY = re.compile(r"발급|발행|주문|결제|구매|승인|거래|발송|등록")
NOT_CODE = re.compile(r"주문|승인|사업자|전화|TEL|tel|Tel|카드|가맹|고객센터|문의|계좌|운송장")
BRAND_LABEL = re.compile(r"^(교환처|사용처|브랜드명?|매장명?|사용\s*매장|사용\s*브랜드|가맹점명?)$")
AMOUNT_TYPE = re.compile(r"\d\s*원\s*권|만\s*원\s*권|천\s*원\s*권|금액권|사용\s*가능\s*금액|잔액|충전\s*금액")
VALUE_LABEL = re.compile(r"(사용\s*가능\s*)?금액|잔액|권종|액면")
TAGS = re.compile(r"^(EVENT|event|Event|NEW|New|HOT|BEST|이벤트|증정|특가|한정|신규|단독|기획|선물)$")
COUPON_WORDS = re.compile(r"교환권|금액권|상품권|쿠폰|기프티|선물하기|교환처|사용처|유효\s*기간|사용\s*기간|까지\s*사용|사용\s*가능")


# ---------- geometry ----------

def bbox(poly):
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    return [min(xs), min(ys), max(xs), max(ys)]


def union(boxes):
    return [min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes)]


def run(text, box, score, h=None):
    return {"text": text, "x0": box[0], "y0": box[1], "x1": box[2], "y1": box[3],
            "h": h if h is not None else box[3] - box[1], "score": score}


def rbox(r):
    return [r["x0"], r["y0"], r["x1"], r["y1"]]


def hangul(s):
    return len(re.findall(r"[가-힣]", s))


def norm(s):  # comparison key: whitespace/punctuation-insensitive
    return re.sub(r"[\W_]+", "", s).lower()


# ---------- steps 1-3: noise, rows, wraps ----------

def is_noise(line, bars):
    if line["score"] < MIN_SCORE or not re.search(r"[0-9A-Za-z가-힣]", line["text"]):
        return True
    # bar area of a decoded barcode: anything but a long digit string read off the bars is noise
    x0, y0, x1, y1 = bbox(line["poly"])
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return any(b[0] <= cx <= b[2] and b[1] <= cy <= b[3] for b in bars) and not re.search(r"\d{8,}", line["text"].replace(" ", ""))


def rows_of(runs):
    rows = []
    for b in sorted(runs, key=lambda b: (b["y0"] + b["y1"]) / 2):
        if rows:
            a = rows[-1][0]
            ov = min(a["y1"], b["y1"]) - max(a["y0"], b["y0"])
            if ov / max(1e-6, min(a["y1"] - a["y0"], b["y1"] - b["y0"])) >= ROW_OVERLAP:
                rows[-1].append(b)
                continue
        rows.append([b])
    return [join_runs(sorted(r, key=lambda b: b["x0"])) for r in rows]


def join_runs(row):  # the detector sometimes splits one text line into touching boxes
    out = [row[0]]
    for b in row[1:]:
        a = out[-1]
        if b["x0"] - a["x1"] < JOIN_GAP * min(a["h"], b["h"]):
            out[-1] = {**run(f"{a['text']} {b['text']}", union([rbox(a), rbox(b)]), min(a["score"], b["score"]),
                             max(a["h"], b["h"]))}
        else:
            out.append(b)
    return out


def merge_wraps(rows):
    widest = max((b["x1"] - b["x0"] for r in rows for b in r), default=1)
    out = []
    for r in rows:
        if out and len(r) == 1 and len(out[-1]) == 1:
            a, b = out[-1][0], r[0]
            ha, hb = a.get("lh", a["h"]), b["h"]  # compare against the last physical line
            wa, wb = a.get("lw", a["x1"] - a["x0"]), b["x1"] - b["x0"]
            # an unclosed bracket closed on the next line is a wrap even when box heights differ (")" pads the box)
            bracket = a["text"].count("(") > a["text"].count(")") and b["text"].count(")") > b["text"].count("(")
            if (b["y0"] - a["y1"] <= WRAP_GAP * min(ha, hb)
                    and min(ha, hb) / max(ha, hb) >= (WRAP_HEIGHT_BRACKET if bracket else WRAP_HEIGHT)
                    and wa >= WRAP_WIDTH * widest and wb <= wa
                    and not BULLET.match(b["text"])
                    and not any(FULL.search(t) or NUM.search(t) for t in (a["text"], b["text"]))):  # a date or code line is its own field
                glue = "" if b["text"].startswith("+") or a["text"].endswith(("+", "(", "/")) else " "
                m = run(a["text"] + glue + b["text"], union([rbox(a), rbox(b)]), min(a["score"], b["score"]), a["h"])
                m.update(lh=hb, lw=wb)
                out[-1] = [m]
                continue
        out.append(r)
    return out


# ---------- step 4: display spacing, step 7: cells / sections ----------

def tidy(s):
    s = re.sub(r"(?<!\d)\s*:\s*(?=\S)", ": ", s)  # not "1:1"
    s = re.sub(r"\]\s*(?=\S)", "] ", s)
    s = re.sub(r"(?<=[가-힣])(?=\d{4,})", " ", s)
    return re.sub(r"\s*~\s*", " ~ ", s).strip()


def is_label(text):  # short Hangul form label such as 유효기간, 교환처, 교환수량
    t = text.strip().rstrip(":").strip()
    return 2 <= len(t) <= 8 and re.fullmatch(r"[가-힣 ]+", t) is not None and LABEL_TAIL.search(t) is not None


def cells_of(row):
    """Pair `label | value` cells; other cells stay standalone (keeps two-column rows apart)."""
    cells, i = [], 0
    while i < len(row):
        a = row[i]
        if i + 1 < len(row) and is_label(a["text"]):
            b = row[i + 1]
            cells.append({"label": a["text"].strip().rstrip(":").strip(), "value": b["text"].strip(),
                          "text": f"{a['text'].strip().rstrip(':').strip()}: {b['text'].strip()}",
                          "box": union([rbox(a), rbox(b)]), "vbox": rbox(b), "score": min(a["score"], b["score"]),
                          "h": b["h"]})
            i += 2
            continue
        lab = re.match(r"^\s*([가-힣 ]{2,8}?)\s*:\s*(.+)$", a["text"])  # "유효기간: 2026..." inside one box
        if lab and is_label(lab.group(1)):
            cells.append({"label": lab.group(1).strip(), "value": lab.group(2).strip(), "text": a["text"],
                          "box": rbox(a), "vbox": rbox(a), "score": a["score"], "h": a["h"]})
        else:
            cells.append({"label": None, "value": a["text"].strip(), "text": a["text"], "box": rbox(a),
                          "vbox": rbox(a), "score": a["score"], "h": a["h"]})
        i += 1
    return cells


def build_rows(lines, bars):
    runs = [run(l["text"], bbox(l["poly"]), l["score"]) for l in lines if not is_noise(l, bars)]
    rows, notes = [], False
    for r in merge_wraps(rows_of(runs)):
        cells = cells_of(r)
        text = tidy(" | ".join(c["text"] for c in cells))
        header = len(norm(text)) <= 12 and NOTES_HEADER.search(text) is not None
        notes = notes or header
        rows.append({"text": text, "cells": cells, "box": union([c["box"] for c in cells]),
                     "score": min(c["score"] for c in cells), "notes": notes or BULLET.match(text) is not None,
                     "header": header})
    return rows


# ---------- step 5: code ----------

NUM = re.compile(r"[0-9A-Za-z]?\d[\d\s.\-]{10,}\d")


def ocr_codes(rows):
    out = []
    for r in rows:
        if NOT_CODE.search(r["text"]):
            continue
        for c in r["cells"]:
            for m in NUM.finditer(c["value"]):
                digits = re.sub(r"\D", "", m.group())
                if 12 <= len(digits) <= 24:
                    bare = not re.sub(r"[\d\s.\-]", "", c["value"])
                    out.append({"value": digits, "bare": bare, "box": c["vbox"], "score": c["score"]})
    return out


def pick_code(rows, barcodes):
    """Decoded barcode first; the printed number nearest the barcode is the cross-check."""
    review, cands = [], ocr_codes(rows)
    dec = sorted(barcodes, key=lambda b: -(b["box"][2] - b["box"][0]) * max(1, b["box"][3] - b["box"][1]))
    if dec:
        known = {c["value"] for c in cands}
        d = next((b for b in dec if re.sub(r"[^0-9A-Za-z]", "", b["value"]) in known), dec[0])
        value = re.sub(r"[^0-9A-Za-z]", "", d["value"])
        near = None
        if cands:
            cy = (d["box"][1] + d["box"][3]) / 2
            near = min(cands, key=lambda c: abs((c["box"][1] + c["box"][3]) / 2 - cy))
        if near and near["value"] != value:
            review.append("CODE_MISMATCH")
        return value, d["format"], "DECODED", d["box"], review
    if not cands:
        return None, None, None, None, ["NOT_FOUND"]
    best = max(cands, key=lambda c: (c["bare"], len(c["value"])))
    return best["value"], "NONE", "OCR", best["box"], ["CODE_NOT_DECODED"]


# ---------- step 6: expiry ----------

FULL = re.compile(r"(20\d\d)\s*[./년-]\s*(\d{1,2})\s*[./월-]\s*(\d{1,2})")
NOYEAR = re.compile(r"(?<![\d.:/])(\d{1,2})\s*[/월.]\s*(\d{1,2})\s*일?\s*까지")


def mkdate(y, m, d):
    try:
        return dt.date(int(y), int(m), int(d))
    except ValueError:
        return None


def pick_expiry(rows):
    full = [[d for d in (mkdate(*g) for g in FULL.findall(r["text"])) if d] for r in rows]
    alld = [d for ds in full for d in ds]
    years = [d.year for d in alld]
    keyed, fallback, review = [], [], []
    inferred = set()
    for r, ds in zip(rows, full):
        if NOT_EXPIRY.search(r["text"]):
            continue
        if r["notes"] and not any(k in r["text"] for k in NOTE_DATE_KEYWORDS):
            continue  # notes mention dates for events etc.; only validity sentences count
        ds = list(ds)
        if years:  # "8/31까지" has no year: borrow the latest printed year, next year if that lands before every date
            for m, d in NOYEAR.findall(r["text"]):
                x = mkdate(max(years), m, d)
                if x and x < min(alld):
                    x = mkdate(max(years) + 1, m, d)
                if x:
                    ds.append(x)
                    inferred.add(x)
        if not ds:
            continue
        end = max(ds)  # a range's end
        if any(k in r["text"] for k in DATE_KEYWORDS):
            keyed.append((end, r))
        elif not r["notes"]:
            fallback.append((end, r))
    if keyed:
        dates = sorted({d for d, _ in keyed})
        value, src = min(keyed, key=lambda x: x[0])  # conflicting deadlines: the earlier is the safe one
        if len(dates) > 1:
            review.append("MULTIPLE_CANDIDATES")
    elif fallback:
        dates = sorted({d for d, _ in fallback})
        value, src = max(fallback, key=lambda x: x[0])
        review.append("NO_KEYWORD")
        if len(dates) > 1:
            review.append("MULTIPLE_CANDIDATES")
    else:
        return None, [], None, ["NOT_FOUND"]
    if value in inferred:
        review.append("YEAR_INFERRED")
    if src["score"] < REVIEW_SCORE:
        review.append("LOW_CONFIDENCE")
    return value, dates, src["box"], review


# ---------- brand, product, type, amount, issuer ----------

def clean_brand(s):
    s = re.sub(r"\s*\([^)]*\)\s*$", "", s.strip())             # 노브랜드버거(금액권) -> 노브랜드버거
    s = re.sub(r"\s*(전\s*)?(매장|지점)\s*(,.*)?$", "", s)       # 메가MGC커피 매장, 앱 -> 메가MGC커피
    s = re.sub(r"\s*선물하기$", "", s)
    return s.strip(" :|,")


def brand_from_labels(rows):
    for r in rows:
        if r["notes"]:
            continue
        for c in r["cells"]:
            if c["label"] and BRAND_LABEL.match(c["label"]):
                b = clean_brand(c["value"])
                if b:
                    return b, c["vbox"], c["score"]
    return None


AMOUNT = re.compile(r"(\d{1,3}(?:,\d{3})+|\d+)\s*(만|천)?\s*원(?!\s*(이상|이하|미만|초과|할인|당))")


def amounts(s):
    out = []
    for m in AMOUNT.finditer(s):
        n = int(m.group(1).replace(",", ""))
        n *= {"만": 10000, "천": 1000}.get(m.group(2), 1)
        if n > 0:
            out.append(n)
    return out


def is_product_text(t):
    v = t.strip()
    if hangul(v) < 2 or FULL.search(v) or NUM.search(v):
        return False
    if re.fullmatch(r"[\d,\s]*(만|천)?\s*원", v):  # an amount on its own
        return False
    if re.search(r"선물하기$|님이\s*보낸", v) or NOTES_HEADER.search(v):
        return False
    return COUPON_WORDS.fullmatch(norm(v)) is None  # a bare "교환권" is not a title


def pick_product(rows, limit_y, brand):
    cands = []
    for i, r in enumerate(rows):
        if r["notes"] or r["box"][1] >= limit_y:
            continue
        for c in r["cells"]:
            if c["label"] or not is_product_text(c["value"]):
                continue
            if brand and norm(c["value"]) == norm(brand):
                continue
            cands.append((c["h"], -c["box"][1], c, i))
    if not cands:
        return None
    _, _, c, i = max(cands, key=lambda x: (x[0], x[1]))
    # a big brand title right above a title that repeats it ("버거킹" / "(버거킹)치즈와퍼…"): the longer line is the product
    for _, _, d, j in sorted(cands, key=lambda x: x[3]):
        if i < j <= i + 2 and norm(c["value"]) in norm(d["value"]) and len(norm(d["value"])) > len(norm(c["value"])):
            return d, j
    return c, i


def repeated_fix(text, rows):
    """Cross-correct a stray character inside brackets using a phrase printed elsewhere on the same image:
    "(메가MGC커피I)" -> "(메가MGC커피)" when "메가MGC커피" is its own line."""
    others = {norm(c["value"]): c["value"].strip() for r in rows for c in r["cells"] if len(norm(c["value"])) >= 3}

    def fix(m):
        inner = m.group(2)
        if norm(inner) in others:
            return m.group(0)
        for cut in (inner[:-1], inner[1:]):
            if len(norm(cut)) >= 3 and norm(cut) in others and cut.strip() == others[norm(cut)]:
                return m.group(1) + cut + m.group(3)
        return m.group(0)

    return re.sub(r"([(\[])([^()\[\]]{3,})([)\]])", fix, text)


def brand_near_product(rows, pi, prod):
    """Short line right above the product title (a brand title), or the bracket prefix of the title."""
    if pi > 0:
        r = rows[pi - 1]
        p = prod["box"]
        gap = p[1] - r["box"][3]
        if (not r["notes"] and len(r["cells"]) == 1 and r["cells"][0]["label"] is None
                and gap <= 1.5 * prod["h"] and len(norm(r["text"])) <= 15
                and not FULL.search(r["text"]) and not NUM.search(r["text"]) and hangul(r["text"]) + len(re.findall(r"[A-Za-z]", r["text"])) >= 2
                and not re.search(r"선물하기$", r["text"])):
            c = r["cells"][0]
            return clean_brand(c["value"]), c["vbox"], c["score"], norm(c["value"]) in norm(prod["value"])
    m = re.match(r"^\s*[\[(]([^\])]{1,15})[\])]", prod["value"])
    if m and not TAGS.match(m.group(1).strip()):
        return m.group(1).strip(), prod["vbox"], prod["score"], True
    return None


def pick_type(rows, product):
    body = [r for r in rows if not r["notes"]]
    amount = any(AMOUNT_TYPE.search(r["text"]) for r in body)
    if not amount:
        return "EXCHANGE", None, None, []
    vals, box = [], None
    if product:
        m = re.search(r"(\d{1,3}(?:,\d{3})+|\d+)\s*(만|천)?\s*원\s*권", product["value"])
        if m:
            vals.append(int(m.group(1).replace(",", "")) * {"만": 10000, "천": 1000}.get(m.group(2), 1))
            box = product["vbox"]
    for r in body:
        for c in r["cells"]:
            if c["label"] and VALUE_LABEL.search(c["label"]) and amounts(c["value"]):
                vals.append(amounts(c["value"])[0])
                box = box or c["vbox"]
    if not vals:  # an amount-only cell on the card, e.g. a big "30,000원"
        for r in body:
            for c in r["cells"]:
                if re.fullmatch(r"\s*(\d{1,3}(?:,\d{3})+|\d+)\s*(만|천)?\s*원\s*", c["value"]):
                    vals.append(amounts(c["value"])[0])
                    box = box or c["vbox"]
    if not vals:
        return "AMOUNT", None, None, ["NOT_FOUND"]
    review = ["MULTIPLE_CANDIDATES"] if len(set(vals)) > 1 else []
    return "AMOUNT", vals[0], box, review


def pick_issuer(rows, brand):
    for r in rows:
        if r["notes"]:
            continue
        m = re.fullmatch(r"\s*(\S.{0,14}?)\s*선물하기\s*", r["text"])
        if m and not (brand and norm(m.group(1)) == norm(brand)):
            return r["text"].strip(), r["box"]
    return None, None


# ---------- top level ----------

def extract(lines, barcodes, width, height):
    bars = [bbox(b["box"]) for b in barcodes]
    barcodes = [{**b, "box": bx} for b, bx in zip(barcodes, bars)]
    rows = build_rows(lines, bars)
    review, boxes = {}, {}

    code, code_format, code_source, code_box, rv = pick_code(rows, barcodes)
    if rv:
        review["code"] = rv
    if code_box:
        boxes["code"] = code_box

    expiry, exp_cands, exp_box, rv = pick_expiry(rows)
    if rv:
        review["expiryDate"] = rv
    if exp_box:
        boxes["expiryDate"] = exp_box

    # the product title sits above the barcode; without one, above the printed code
    limit_y = min((b[1] for b in bars), default=None)
    if limit_y is None:
        limit_y = code_box[1] if code_box else height + 1
    labelled = brand_from_labels(rows)
    pp = pick_product(rows, limit_y, labelled[0] if labelled else None)
    product = brand = None
    if pp:
        prod, pi = pp
        product = repeated_fix(prod["value"], rows)
        boxes["productName"] = prod["vbox"]
        if prod["score"] < REVIEW_SCORE:
            review["productName"] = ["LOW_CONFIDENCE"]
        near = brand_near_product(rows, pi, prod)
        if labelled:
            brand, boxes["brand"], bscore = labelled
        elif near:
            brand, boxes["brand"], bscore, _ = near
        if brand and bscore < REVIEW_SCORE:
            review["brand"] = ["LOW_CONFIDENCE"]
        # the title line may carry a stray prefix of the brand line; keep the label as printed
    else:
        review["productName"] = ["NOT_FOUND"]
        if labelled:
            brand, boxes["brand"], _ = labelled
    if not brand:
        review["brand"] = ["NOT_FOUND"]

    ctype, face, face_box, rv = pick_type(rows, {"value": product, "vbox": boxes.get("productName")} if product else None)
    if rv:
        review["faceValue"] = rv
    if face_box:
        boxes["faceValue"] = face_box

    issuer, ibox = pick_issuer(rows, brand)
    if ibox:
        boxes["issuer"] = ibox
    sender = None
    for r in rows:
        m = re.search(r"([^\s:]{1,10})\s*님이\s*보낸", r["text"])
        if m:
            sender = m.group(1)
            boxes["sender"] = r["box"]
            review["sender"] = ["ALWAYS_CHECK"]
            break
    notes = "\n".join(r["text"] for r in rows if r["notes"]) or None

    # coupon or not: a scannable code + a validity date + coupon vocabulary
    words = any(COUPON_WORDS.search(r["text"]) for r in rows)
    keyed_expiry = expiry is not None and "NO_KEYWORD" not in review.get("expiryDate", [])
    is_coupon = code is not None and keyed_expiry and words

    return {
        "version": VERSION,
        "isCoupon": is_coupon,
        "brand": brand or None,
        "productName": product,
        "type": ctype,
        "faceValue": face,
        "code": code,
        "codeFormat": code_format,
        "codeSource": code_source,
        "expiryDate": expiry.isoformat() if expiry else None,
        "expiryCandidates": [d.isoformat() for d in exp_cands],
        "issuer": issuer,
        "sender": sender,
        "notes": notes,
        "needsReview": review,
        "fieldBoxes": boxes,
        "rows": [{"text": r["text"], "box": r["box"], "notes": r["notes"]} for r in rows],
    }
