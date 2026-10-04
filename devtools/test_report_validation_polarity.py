"""Regression tests for inverted (negatively phrased) validation keys.

The enterprise skills require agents to record ``credentials_disclosed: false``
in results.json. Before this fix the report generator read every ``False`` in
the ``validation`` block as a failed check, so a correct security attestation
was rendered FAIL, listed under "Validation checks requiring attention" in the
executive summary, and flipped safety readiness to DESIGN-GRADE BLOCKED.

Importing the canonical module requires python-docx; these tests skip cleanly
when it is absent.
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
class NegativeValidationKeyTest(unittest.TestCase):
    def test_credentials_disclosed_false_is_not_a_failure(self):
        results = {"validation": {"credentials_disclosed": False}}
        self.assertEqual(GR._validation_failures(results), [])

    def test_credentials_disclosed_true_is_a_failure(self):
        results = {"validation": {"credentials_disclosed": True}}
        self.assertEqual(GR._validation_failures(results), ["Credentials Disclosed"])

    def test_positive_key_keeps_normal_polarity(self):
        self.assertEqual(
            GR._validation_failures({"validation": {"mass_balance_closed": False}}),
            ["Mass Balance Closed"])
        self.assertEqual(
            GR._validation_failures({"validation": {"mass_balance_closed": True}}), [])

    def test_other_negative_markers(self):
        for key in ("design_pressure_exceeded", "spec_violated", "solver_failed",
                    "tags_fabricated", "run_blocked"):
            self.assertEqual(GR._validation_failures({"validation": {key: False}}), [],
                             "{} = False should pass".format(key))
            self.assertTrue(GR._validation_failures({"validation": {key: True}}),
                            "{} = True should fail".format(key))

    def test_marker_matches_whole_word_only(self):
        # "errors" as a whole token is negative; a word merely containing a
        # marker substring must keep normal polarity.
        self.assertEqual(
            GR._validation_failures({"validation": {"terrorism_check": False}}),
            ["Terrorism Check"])

    def test_self_negating_key_flips_back_to_positive(self):
        # "no_..._inferred" already negates the negative marker, so True passes.
        for key in ("no_document_number_or_tag_inferred_or_constructed",
                    "no_data_fabricated", "never_exceeded", "zero_errors"):
            self.assertEqual(GR._validation_failures({"validation": {key: True}}), [],
                             "{} = True should pass".format(key))
            self.assertTrue(GR._validation_failures({"validation": {key: False}}),
                            "{} = False should fail".format(key))

    def test_non_boolean_values_are_never_failures(self):
        results = {"validation": {"mass_balance_error_pct": 0.0, "note": "ok"}}
        self.assertEqual(GR._validation_failures(results), [])

    def test_html_table_renders_pass_for_negative_key(self):
        html = GR.format_validation_html({"validation": {"credentials_disclosed": False}})
        self.assertIn("PASS", html)
        self.assertNotIn("FAIL", html)

    def test_text_table_renders_pass_for_negative_key(self):
        text = GR.format_validation_table({"validation": {"credentials_disclosed": False}})
        self.assertIn("Credentials Disclosed: PASS", text)

    def test_validation_labels_preserve_acronyms(self):
        text = GR.format_validation_table(
            {"validation": {"DCS_values_reconciled_against_historian": True,
                            "PID_routing_matches_SAP_links": True}})
        self.assertIn("DCS Values Reconciled Against Historian", text)
        self.assertIn("PID Routing Matches SAP Links", text)


if __name__ == "__main__":
    unittest.main()
