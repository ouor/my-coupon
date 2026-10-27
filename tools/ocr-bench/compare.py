"""Line-level agreement between two OCR jsonl dumps (reference vs candidate).

Each reference line is matched to the candidate line with the highest box IoU (axis-aligned).
Reports: matched lines (IoU>=0.5), exact text match among all reference lines, mean IoU,
images whose full text sequence is identical, and the latency median of each file.
usage: python compare.py ref.jsonl cand.jsonl [--show N]
"""
import json, statistics, sys


def bbox(p):
    xs, ys = [q[0] for q in p], [q[1] for q in p]
    return min(xs), min(ys), max(xs), max(ys)


def iou(a, b):
    ax0, ay0, ax1, ay1 = bbox(a)
    bx0, by0, bx1, by1 = bbox(b)
    iw, ih = max(0, min(ax1, bx1) - max(ax0, bx0)), max(0, min(ay1, by1) - max(ay0, by0))
    inter = iw * ih
    u = (ax1 - ax0) * (ay1 - ay0) + (bx1 - bx0) * (by1 - by0) - inter
    return inter / u if u > 0 else 0.0


def load(p):
    return {r["file"]: r for r in map(json.loads, open(p, encoding="utf-8"))}


def main(ref_p, cand_p, show=10):
    ref, cand = load(ref_p), load(cand_p)
    files = [f for f in ref if f in cand]
    n_ref = n_match = n_text = n_img_same = 0
    ious, diffs = [], []
    for f in files:
        rl, cl = ref[f]["lines"], cand[f]["lines"]
        n_img_same += [l["text"] for l in rl] == [l["text"] for l in cl]
        for l in rl:
            n_ref += 1
            best = max(cl, key=lambda c: iou(l["poly"], c["poly"]), default=None)
            v = iou(l["poly"], best["poly"]) if best else 0
            if v >= 0.5:
                n_match += 1
                ious.append(v)
                if best["text"] == l["text"]:
                    n_text += 1
                else:
                    diffs.append((f[-12:], l["text"], best["text"]))
            else:
                diffs.append((f[-12:], l["text"], None))
    n_c = sum(len(cand[f]["lines"]) for f in files)
    print(f"images {len(files)}, ref lines {n_ref}, cand lines {n_c}")
    print(f"box matched (IoU>=0.5) {n_match}/{n_ref} = {n_match / max(n_ref, 1):.2%}, mean IoU {statistics.mean(ious) if ious else 0:.3f}")
    print(f"text exact {n_text}/{n_ref} = {n_text / max(n_ref, 1):.2%}")
    print(f"images with identical line sequence {n_img_same}/{len(files)}")
    for name, d in (("ref", ref), ("cand", cand)):
        ms = [d[f]["ms"] for f in files]
        print(f"{name} latency median {statistics.median(ms):.0f}ms")
    for d in diffs[:show]:
        print("  diff", d)


if __name__ == "__main__":
    a = sys.argv[1:]
    show = int(a[a.index("--show") + 1]) if "--show" in a else 10
    main(a[0], a[1], show)
