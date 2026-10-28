"""Code review 2 P-01: append ArgMax / ReduceMax to the PP-OCRv5 rec model.

The original [N, T, classes] softmax output stays the first output (the Python twin and the class-count check use it);
two outputs are added: `best` = ArgMax(axis 2) as int64 [N, T] (first maximum, as the Kotlin CTC loop picks it) and
`best_p` = ReduceMax(axis 2) as float [N, T]. The app asks only for these two, so the ~115 MB probabilities stay an ORT
intermediate instead of a Java-heap buffer. Weights and every other node are unchanged.

Usage (any Python with `onnx`, e.g. ocr-lab/.venv):
    python tools/rec_argmax.py <original rec.onnx> app/src/main/assets/ocr/rec.onnx
then update gradle/ocr-models.sha256 and docs/core/ocr-runtime.md.
"""
import sys

import onnx
from onnx import TensorProto, helper


def main(src: str, dst: str) -> None:
    m = onnx.load(src)
    g = m.graph
    assert [o.name for o in g.output] == ["fetch_name_0"], "already converted, or not the RapidOCR rec model"
    probs = g.output[0].name
    g.node.append(helper.make_node("ArgMax", [probs], ["best"], axis=2, keepdims=0, select_last_index=0))
    g.node.append(helper.make_node("ReduceMax", [probs], ["best_p"], axes=[2], keepdims=0))  # opset 14: axes is an attribute
    g.output.append(helper.make_tensor_value_info("best", TensorProto.INT64, ["N", "T"]))
    g.output.append(helper.make_tensor_value_info("best_p", TensorProto.FLOAT, ["N", "T"]))
    onnx.checker.check_model(m)
    onnx.save(m, dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
