"""Validation rows without a relative tolerance or error (absolute criteria) must render as n/a, not crash the report."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import psr_report  # noqa: E402


def test_fmt_optional_handles_none_and_numbers():
    assert psr_report._fmt_optional(None, "%.0f") == "n/a"
    assert psr_report._fmt_optional(5, "%.0f") == "5"
    assert psr_report._fmt_optional(-1.44, "%+.1f") == "-1.4"
    assert psr_report._fmt_optional("abs 0.1 %", "%.0f") == "abs 0.1 %"
