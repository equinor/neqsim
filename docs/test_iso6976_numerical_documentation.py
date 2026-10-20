"""Numerical provenance contracts for the bounded ISO 6976 worked example."""

import csv
import math
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/standards/iso6976_calorific_values.md"


class Iso6976NumericalDocumentationTest(unittest.TestCase):
    """Recalculate the documented four-component fixture from repository data."""

    @classmethod
    def setUpClass(cls):
        cls.doc = GUIDE.read_text(encoding="utf-8")
        cls.example = cls.doc.split("## Worked Numerical Example\n", 1)[1].split(
            "\n---\n", 1
        )[0]
        with (ROOT / "src/main/resources/data/ISO6976constants.csv").open(
            encoding="utf-8", newline=""
        ) as stream:
            cls.constants = {row["ComponentName"]: row for row in csv.DictReader(stream)}

    def rows(self):
        pattern = (
            r"^\| (methane|ethane|nitrogen|CO2) \| "
            r"([0-9.]+) \| ([0-9.]+) \| ([0-9.]+) \| ([0-9.]+) \| ([0-9.]+) \|$"
        )
        rows = re.findall(pattern, self.example, re.MULTILINE)
        self.assertEqual(4, len(rows))
        self.assertEqual(4, len({row[0] for row in rows}))
        return [(row[0], *map(float, row[1:])) for row in rows]

    def test_normalization_and_component_data(self):
        rows = self.rows()
        total = sum(row[1] for row in rows)
        self.assertAlmostEqual(0.983163, total, places=12)
        self.assertIn("not already normalized mole fractions", self.example)
        for name, amount, fraction, mass, heat, factor in rows:
            with self.subTest(component=name):
                self.assertAlmostEqual(amount / total, fraction, delta=5e-10)
                data = self.constants[name]
                self.assertEqual(float(data["MolarMass"]), mass)
                self.assertEqual(float(data["Hsupmolar15"]), heat)
                self.assertEqual(float(data["srtb15"]), factor)

    def test_complete_numerical_chain(self):
        rows = self.rows()
        total = sum(row[1] for row in rows)
        mass = sum(row[1] * row[3] for row in rows) / total
        heat = sum(row[1] * row[4] for row in rows) / total
        factor = sum(row[1] * row[5] for row in rows) / total
        z = 1.0 - factor**2
        molar_density = 101325.0 / (8.314510 * 288.15 * z)
        air_constants = (ROOT / "src/main/java/neqsim/thermo/"
                         "ThermodynamicConstantsInterface.java").read_text(encoding="utf-8")
        self.assertRegex(air_constants, r"double molarMassAir = 28\.96546;")
        self.assertIn("28.96546", self.example)
        density_ratio = mass / 28.96546 * 0.99958 / z
        expected = {
            "Molar mass": mass,
            "Ideal molar GCV": heat,
            "Summation factor": factor,
            "Compression factor": z,
            "Real molar density": molar_density,
            "Real volumetric GCV": heat * molar_density,
            "Real relative density": density_ratio,
            "Superior Wobbe index": heat * molar_density / math.sqrt(density_ratio),
            "Real gas density": mass / 1000.0 * molar_density,
        }
        for label, value in expected.items():
            with self.subTest(result=label):
                match = re.search(
                    r"^\| " + re.escape(label) + r" \| ([0-9.]+) \|",
                    self.example,
                    re.MULTILINE,
                )
                self.assertIsNotNone(match, label)
                self.assertAlmostEqual(value, float(match[1]), delta=5e-6)

    def test_fixture_provenance_is_not_conflated(self):
        for required in (
            "ISO6976constants.csv",
            "15/15",
            "1.01325 bara",
            "Iso6976NumericalDocumentationTest",
            "eight-component",
            "not independent laboratory validation",
        ):
            self.assertIn(required, self.example)
        for stale in ("38959", "50107", "17.478", "870.93", "match the NeqSim test suite"):
            self.assertNotIn(stale, self.example)
        java = (ROOT / "src/test/java/neqsim/standards/gasquality/"
                "Iso6976NumericalDocumentationTest.java").read_text(encoding="utf-8")
        for method in ("normalizedFourComponentWorkedExample", "amountScaleDoesNotChangeQuality"):
            self.assertIn(method, java)

    def test_reference_boundaries_are_visible_at_both_entry_points(self):
        landing = (ROOT / "docs/standards/README.md").read_text(encoding="utf-8")
        for text in (self.doc, landing):
            for phrase in ("0.999601", "15.55", "real relative density", "Wobbe"):
                self.assertIn(phrase, text)
            for stale in ("2016-edition limitation", "Known implementation limitation",
                          "implementation defect; see warning below",
                          "invalid current implementation"):
                self.assertNotIn(stale, text)
        self.assertIn("three volume-temperature tables", self.doc)
        self.assertIn("four volume-temperature tables", self.doc)
        self.assertNotIn("This is evaluated at five reference temperatures", self.doc)

    def test_changed_equations_and_existing_navigation(self):
        self.assertNotIn(r"\[", self.example)
        self.assertNotIn(r"\(", self.example)
        segments = self.example.split("$$")
        self.assertEqual(1, len(segments) % 2)
        for equation in segments[1::2]:
            self.assertEqual(equation.strip(), equation)
            self.assertTrue(equation)
        for target in re.findall(r"\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)", self.example):
            self.assertTrue((GUIDE.parent / target).resolve().is_file(), target)


if __name__ == "__main__":
    unittest.main()
