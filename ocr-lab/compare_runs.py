"""Line-level agreement between two OCR runs (e.g. Paddle vs ONNX Runtime, or PC vs app dump).

usage: python compare_runs.py out/a.jsonl out/b.jsonl
Each line of run A is matched to the run-B line with the highest box IoU (>= 0.5).
Prints totals only (no text), so it is safe for holdout images too.
"""
import json, sys


def bbox(p):
    xs, ys = [q[0] for q in p], [q[1] for q in p]
    return min(xs), min(ys), max(xs), max(ys)


def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    u = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / u if u > 0 else 0


def main(pa, pb):
    A = {r["file"]: r for r in map(json.loads, open(pa, encoding="utf-8"))}
    B = {r["file"]: r for r in map(json.loads, open(pb, encoding="utf-8"))}
    n = matched = same = imgs_same = 0
    ious = []
    for f, ra in A.items():
        lb = [(bbox(l["poly"]), l["text"]) for l in B[f]["lines"]]
        all_same = len(ra["lines"]) == len(lb)
        for l in ra["lines"]:
            n += 1
            ba = bbox(l["poly"])
            best = max(((iou(ba, b), t) for b, t in lb), default=(0, None))
            if best[0] >= 0.5:
                matched += 1
                ious.append(best[0])
                same += best[1] == l["text"]
                all_same &= best[1] == l["text"]
            else:
                all_same = False
        imgs_same += all_same
    print(f"lines {n}: box matched {matched} ({matched / n:.1%}), mean IoU {sum(ious) / len(ious):.3f}, "
          f"same text {same} ({same / n:.1%}); images with identical text {imgs_same}/{len(A)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
