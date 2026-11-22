"""The process-study report reads as an engineering document: no file names, tool names or code words in generated text."""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import psr_report  # noqa: E402
import psr_validate  # noqa: E402

FILE_NAME = re.compile(r"\b[\w.-]+\.(?:py|jsonl|json|csv|xlsx|ipynb|ps1)\b", re.I)
TOOL_NAME = re.compile(r"\bpython\b|\bpytest\b|\bjupyter\b|\bcopilot\b", re.I)


def test_check_detail_has_no_python_list_reprs():
    detail = psr_validate._check("X", "d", "WARN", "2 stream/case pairs: ['S022', 'S030']; first (S1, A)")["detail"]
    assert "[" not in detail and "'" not in detail
    assert "S022; S030" in detail
    assert psr_validate._check("X", "d", "PASS", "none")["detail"] == "none"


def test_tolerance_labels_cover_every_default_tolerance():
    import psr_balances

    assert set(psr_balances.DEFAULT_TOLERANCES) <= set(psr_report.TOLERANCE_LABELS)
    for label in psr_report.TOLERANCE_LABELS.values():
        assert "_" not in label and "=" not in label


def test_generated_wording_has_no_file_or_tool_names():
    source = open(psr_report.__file__, encoding="utf-8").read()
    for phrase in ("machine-readable exports (", "validation_report.json.", "data/connectivity_audit.csv.", '"Python library"'):
        assert phrase not in source
    for text in ("the connectivity audit table of the data package", "The full validation record is delivered with the data package.",
                 "tables of the data package delivered with this report"):
        assert text in source
    for label in psr_report.TOLERANCE_LABELS.values():
        assert not FILE_NAME.search(label) and not TOOL_NAME.search(label)
