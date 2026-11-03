"""Protect the independent unit-handling recipe in the Python quickstart."""

import ast
from pathlib import Path
import re
import unittest


PAGE = Path(__file__).parent / "quickstart/python-quickstart.md"


def units_recipe():
    """Return the Python fence under Working with Units."""
    section = PAGE.read_text(encoding="utf-8").split("## Working with Units\n", 1)[1]
    return re.search(r"```python\n(.*?)\n```", section, re.DOTALL).group(1)


class PythonQuickstartUnitsTest(unittest.TestCase):
    """Check syntax, standalone setup, lifecycle order and documented units."""

    def test_page_uses_current_published_runtime_requirements(self):
        text = PAGE.read_text(encoding="utf-8")
        self.assertNotIn("\n# Python Quickstart\n", text)
        self.assertNotIn("Java 8+", text)
        self.assertIn("requires Python 3.10+", text)
        self.assertIn("requires Java 17 or newer", text)
        self.assertIn("conda install -c conda-forge neqsim", text)
        self.assertIn("Conda package installs OpenJDK automatically", text)
        self.assertIn(
            "https://github.com/equinor/neqsim-python#install",
            text,
        )

    def test_recipe_has_no_undefined_top_level_names(self):
        code = units_recipe()
        tree = ast.parse(code)
        defined = {"print"}
        for statement in tree.body:
            loaded = {node.id for node in ast.walk(statement)
                      if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load)}
            self.assertEqual(set(), loaded - defined)
            if isinstance(statement, (ast.Import, ast.ImportFrom)):
                defined.update(alias.asname or alias.name.split(".")[0]
                               for alias in statement.names)
            defined.update(node.id for node in ast.walk(statement)
                           if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store))
        self.assertIn("stream", defined)
        self.assertIn("solved_fluid", defined)

    def test_properties_follow_current_stream_state(self):
        code = units_recipe()
        ordered = ['fluid.setMixingRule("classic")',
                   'stream.setFlowRate(1000.0, "kg/hr")',
                   'stream.setTemperature(30.0, "C")',
                   'stream.setPressure(50.0, "bara")',
                   "stream.run()", "solved_fluid = stream.getFluid()",
                   "solved_fluid.initProperties()",
                   'solved_fluid.getDensity("kg/m3")',
                   'solved_fluid.getViscosity("cP")',
                   'solved_fluid.getEnthalpy("kJ/kg")']
        positions = [code.index(fragment) for fragment in ordered]
        self.assertEqual(sorted(positions), positions)

    def test_recipe_checks_units_and_valid_properties(self):
        code = units_recipe()
        for expression in ["math.isclose(temp_kelvin, 303.15",
                           "math.isclose(temp_celsius, 30.0",
                           "math.isclose(pressure, 50.0",
                           "math.isfinite(density) and density > 0.0",
                           "math.isfinite(viscosity) and viscosity > 0.0",
                           "math.isfinite(enthalpy)"]:
            self.assertIn("assert " + expression, code)


if __name__ == "__main__":
    unittest.main()
