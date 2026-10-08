"""Tests for devtools/task_template/step3_report/report_kit.py."""
import importlib.util
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

KIT = Path(__file__).resolve().parent / "task_template" / "step3_report" / "report_kit.py"
spec = importlib.util.spec_from_file_location("report_kit", KIT)
kit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kit)

M = "{http://schemas.openxmlformats.org/officeDocument/2006/math}"


class FigurePlanTest(unittest.TestCase):
    def test_discussed_figures_move_next_to_their_discussion(self):
        paths = ["/f/a.png", "/f/b.png", "/f/c.png"]
        plan = kit.FigurePlan(paths, [{"figure": "c.png"}, {"figure": "a.png"}])
        self.assertEqual(plan.results_figures, ["/f/b.png"])
        self.assertEqual(plan.order, ["/f/b.png", "/f/c.png", "/f/a.png"])
        self.assertEqual(plan.number_of("c.png"), 2)
        self.assertEqual(plan.number_of("/x/a.png"), 3)

    def test_figure_shown_once_when_two_entries_use_it(self):
        plan = kit.FigurePlan(["/f/a.png"], [{"figure": "a.png"}, {"figure": "a.png"}])
        self.assertEqual(plan.discussion_figure, {0: "/f/a.png"})
        self.assertEqual(plan.discussion_ref, {1: "/f/a.png"})
        self.assertEqual(plan.order, ["/f/a.png"])

    def test_without_discussion_all_figures_stay_in_results(self):
        plan = kit.FigurePlan(["/f/a.png", "/f/b.png"], None)
        self.assertEqual(plan.results_figures, ["/f/a.png", "/f/b.png"])

    def test_token_resolves_with_or_without_extension(self):
        plan = kit.FigurePlan(["/f/fig01_pressure.png"], None)
        self.assertEqual(plan.resolve("fig01_pressure"), "/f/fig01_pressure.png")
        self.assertEqual(plan.resolve("FIG01_pressure.PNG"), "/f/fig01_pressure.png")
        self.assertIsNone(plan.resolve("missing"))


class OmmlTest(unittest.TestCase):
    def convert(self, latex):
        xml = kit.latex_to_omml(latex)
        self.assertIsNotNone(xml, latex)
        return ET.fromstring(xml)

    def test_fraction_with_subscripts(self):
        root = self.convert(r"\dot{m} = \frac{Q}{\Delta h_{fg}}")
        self.assertEqual(len(root.findall(".//" + M + "f")), 1)
        self.assertEqual(len(root.findall(".//" + M + "sSub")), 1)
        self.assertEqual(len(root.findall(".//" + M + "acc")), 1)

    def test_superscript_sqrt_and_greek(self):
        root = self.convert(r"\rho v^2 = \sqrt{2 \Delta P}")
        texts = "".join(t.text for t in root.iter(M + "t"))
        self.assertIn("\u03c1", texts)
        self.assertEqual(len(root.findall(".//" + M + "sSup")), 1)
        self.assertEqual(len(root.findall(".//" + M + "rad")), 1)

    def test_big_operator_and_delimiters(self):
        root = self.convert(r"\sum_{i=1}^{n} \left( x_i - \bar{x} \right)^2")
        self.assertEqual(len(root.findall(".//" + M + "nary")), 1)
        self.assertEqual(len(root.findall(".//" + M + "d")), 1)

    def test_text_is_upright(self):
        root = self.convert(r"\text{Re} = \frac{\rho v D}{\mu}")
        self.assertTrue(any(s.get(M + "val") == "p" for s in root.iter(M + "sty")))

    def test_unsupported_input_returns_none(self):
        self.assertIsNone(kit.latex_to_omml(r"\begin{cases} a & b \end{cases}"))
        self.assertIsNone(kit.latex_to_omml(r"\unknowncommand{x}"))
        self.assertIsNone(kit.latex_to_omml(r"\frac{a}{b"))
        self.assertIsNone(kit.latex_to_omml(""))

    def test_special_characters_are_escaped(self):
        root = self.convert(r"a < b")
        self.assertIn("<", "".join(t.text for t in root.iter(M + "t")))


class NumberTraceTest(unittest.TestCase):
    def test_flags_only_numbers_missing_from_results(self):
        results = {
            "key_results": {"outlet_T_C": 25.3, "drop_bar": 3.2},
            "conclusions": "Outlet is 25.3 C and the drop is 3.2 bar, with 7.77 unexplained.",
        }
        quoted, unmatched = kit.untraced_numbers(results)
        self.assertEqual(sorted(quoted), [3.2, 7.77, 25.3])
        self.assertEqual(unmatched, [7.77])

    def test_unit_scaling_is_accepted(self):
        results = {"key_results": {"duty_MW": 4.5, "frac": 0.123},
                   "conclusions": "Duty is 4500 kW and 12.3 % of feed."}
        self.assertEqual(kit.untraced_numbers(results)[1], [])

    def test_years_tags_and_small_counts_ignored(self):
        text = "In 2026 the 4th stage, tag LT-20-0105 and RS2026 had 3 drains."
        self.assertEqual(kit.numbers_in_text(text), [])

    def test_thousands_separator(self):
        self.assertEqual(kit.numbers_in_text("about 1 600 Sm3/d"), [1600.0])

    def test_narrative_fields_do_not_vouch_for_themselves(self):
        results = {"conclusions": "Flow is 98.76.", "executive_summary": "Flow is 98.76."}
        self.assertEqual(kit.untraced_numbers(results)[1], [98.76, 98.76])


class AssetTest(unittest.TestCase):
    def test_katex_is_bundled_for_offline_use(self):
        assets = kit.katex_assets()
        self.assertIsNotNone(assets)
        css, js, auto = assets
        self.assertIn("KaTeX_Main", css)
        self.assertIn("data:font/woff2;base64", css)
        self.assertNotIn("http", css.split("src:")[1][:40])
        self.assertIn("renderMathInElement", auto)

    def test_alt_text_cleaning(self):
        self.assertEqual(kit.plain_alt_text("**Bold** $x$ {fig:a}  text"), "Bold x text")
        long = "word " * 100
        self.assertTrue(kit.plain_alt_text(long, 50).endswith("..."))


if __name__ == "__main__":
    unittest.main()
