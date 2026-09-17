"""Regression tests for key_results label and unit parsing.

Covers two defects seen on a real task report:
  * ``.title()`` mangled acronyms, so ``GFC_isometric_corpus_size_LL_documents``
    rendered as "Gfc Isometric Corpus Size Ll Documents";
  * common engineering unit suffixes (ppm, l/h, degC, days, cost) were not
    recognised, so the Unit column was blank and the unit stayed stuck in the
    parameter name.
"""
import importlib.util
import sys
import unittest
from pathlib import Path

CANONICAL = (
    Path(__file__).resolve().parent
    / "task_template" / "step3_report" / "generate_report.py"
)


def _load_generate_report():
    """Import the canonical generate_report module, or return None if unavailable."""
    if not CANONICAL.is_file():
        return None
    spec = importlib.util.spec_from_file_location("canonical_generate_report", CANONICAL)
    mod = importlib.util.module_from_spec(spec)
    saved_argv = sys.argv
    sys.argv = ["generate_report.py"]
    try:
        spec.loader.exec_module(mod)
    except SystemExit:
        return None
    finally:
        sys.argv = saved_argv
    return mod


GR = _load_generate_report()


@unittest.skipIf(GR is None, "python-docx not installed")
class KeyLabelTest(unittest.TestCase):
    def test_acronyms_are_preserved(self):
        label, _unit = GR._parse_key_name("GFC_isometric_corpus_size_LL_documents")
        self.assertEqual(label, "GFC Isometric Corpus Size LL Documents")

    def test_mixed_alphanumeric_tag_preserved(self):
        label, unit = GR._parse_key_name("pump_discharge_pressure_42PF36A_barg")
        self.assertEqual(label, "Pump Discharge Pressure 42PF36A")
        self.assertEqual(unit, "barg")

    def test_ordinary_words_still_title_cased(self):
        label, _unit = GR._parse_key_name("lines_with_no_isometric_anywhere")
        self.assertEqual(label, "Lines With No Isometric Anywhere")

    def test_single_uppercase_letter_is_not_treated_as_acronym(self):
        label, unit = GR._parse_key_name("H2S_upstream_stream_A_ppm")
        self.assertEqual(label, "H2S Upstream Stream A")
        self.assertEqual(unit, "ppm")


@unittest.skipIf(GR is None, "python-docx not installed")
class UnitSuffixTest(unittest.TestCase):
    def test_new_suffixes(self):
        cases = {
            "scavenger_injection_flow_head_A_l_per_h": "l/h",
            "scavenger_tank_temperature_degC": "\u00b0C",
            "H2S_GFC_export_ppm": "ppm",
            "historian_window_days": "days",
            "deferred_production_cost_MNOK": "MNOK",
        }
        for key, expected in cases.items():
            _label, unit = GR._parse_key_name(key)
            self.assertEqual(unit, expected, "wrong unit for {}".format(key))

    def test_existing_suffixes_still_work(self):
        for key, expected in (("outlet_temperature_C", "\u00b0C"),
                              ("pressure_drop_bar", "bar"),
                              ("efficiency_pct", "%"),
                              ("flow_kg_hr", "kg/hr")):
            _label, unit = GR._parse_key_name(key)
            self.assertEqual(unit, expected, "wrong unit for {}".format(key))

    def test_key_without_unit(self):
        label, unit = GR._parse_key_name("lines_requested")
        self.assertEqual((label, unit), ("Lines Requested", ""))


if __name__ == "__main__":
    unittest.main()
