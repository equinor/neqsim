"""Tests for the table-layout lint of validate_task_results."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from validate_task_results import _check_table_layout  # noqa: E402


def test_multi_number_cell_is_flagged():
    res = {"tables": [{"title": "t", "headers": ["a", "b"], "rows": [["x", "12.3 / 4.5"], ["y", "7"]]}]}
    out = _check_table_layout(res)
    assert len(out) == 1 and "one value per cell" in out[0]


def test_wide_table_is_flagged():
    res = {"tables": [{"title": "wide", "headers": [str(i) for i in range(12)], "rows": []}]}
    assert any("12 columns" in w for w in _check_table_layout(res))


def test_clean_table_passes():
    res = {"tables": [{"title": "ok", "headers": ["Day", "MAE (bar)"], "rows": [["19", "14.5"], ["18", "-3.2"]]}]}
    assert _check_table_layout(res) == []
