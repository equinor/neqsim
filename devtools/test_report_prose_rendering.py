"""Regression tests for prose rendering in the canonical report generator.

Covers three defects found on a real task report:
  * hard-wrapped markdown prose was broken with <br> mid-sentence, because a
    single newline was rendered as a line break instead of a space;
  * inline `code` spans (how tag and document numbers are written in a task
    spec) were rendered as literal backticks;
  * the Operating Envelope markdown table was flattened into a paragraph of
    pipe characters in the Problem Description, duplicating the properly
    rendered table in Scope and Standards.
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

WRAPPED = ("Gullfaks C is preparing a modification on the H2S scavenger\n"
           "chemical injection system. Four injection line tags could not be\n"
           "found when searching STID.")

NUMBERED = ('1. `0.75"-CC-42656-CS11`\n'
            '2. `12mm-CC-42174-TS41A` (to mixer SI-5032)\n'
            '3. `12mm-CC-42164-TS35A`')

BULLETS = "- first bullet\n- second bullet\n- third bullet"

SPEC_WITH_TABLE = (
    "## Objective\n\nEstablish whether the drawings exist.\n\n"
    "## Operating Envelope\n\n"
    "| Parameter | Min | Max | Unit |\n"
    "|---|---|---|---|\n"
    "| Pressure | 50 | 55 | barg |\n")

SPEC_WITH_PROSE_ENVELOPE = (
    "## Objective\n\nEstablish whether the drawings exist.\n\n"
    "## Operating Envelope\n\nAmbient to 55 barg, 20 to 25 degC.\n")


@unittest.skipIf(GR is None, "python-docx not installed")
class ProseRenderingTest(unittest.TestCase):
    def test_wrapped_prose_is_rejoined_with_spaces(self):
        html = GR._prose_to_html(WRAPPED)
        self.assertNotIn("<br>", html)
        self.assertIn("H2S scavenger chemical injection system", html)
        self.assertIn("could not be found when searching", html)

    def test_numbered_list_keeps_its_line_breaks(self):
        html = GR._prose_to_html(NUMBERED)
        self.assertEqual(html.count("<br>"), 2)

    def test_bullet_list_keeps_its_line_breaks(self):
        self.assertEqual(GR._prose_to_html(BULLETS).count("<br>"), 2)

    def test_inline_code_becomes_code_element(self):
        html = GR._prose_to_html(NUMBERED)
        self.assertIn('<code>0.75"-CC-42656-CS11</code>', html)
        self.assertNotIn("`", html)

    def test_single_line_paragraph_unchanged(self):
        self.assertEqual(GR._prose_to_html("One short line."), "<p>One short line.</p>")

    def test_word_paragraphs_rejoin_prose_and_split_lists(self):
        prose = GR._word_paragraphs(WRAPPED)
        self.assertEqual(len(prose), 1)
        self.assertNotIn("\n", prose[0])
        self.assertEqual(len(GR._word_paragraphs(NUMBERED)), 3)

    def test_envelope_table_not_flattened_into_problem_description(self):
        text = GR.auto_problem_description({}, SPEC_WITH_TABLE)
        self.assertIn("Establish whether the drawings exist", text)
        self.assertNotIn("|", text)

    def test_prose_envelope_still_included(self):
        text = GR.auto_problem_description({}, SPEC_WITH_PROSE_ENVELOPE)
        self.assertIn("Operating envelope: Ambient to 55 barg", text)


if __name__ == "__main__":
    unittest.main()
