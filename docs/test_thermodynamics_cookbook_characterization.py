"""Protect the three independent heavy-fraction cookbook recipes."""

import ast
from pathlib import Path
import re
import unittest


PAGE = Path(__file__).parent / "cookbook/thermodynamics-recipes.md"


def recipe(heading):
    """Extract the first Python fence following an exact heading."""
    source = PAGE.read_text(encoding="utf-8")
    section = source.split(heading + "\n", 1)[1]
    return re.search(r"```python\n(.*?)\n```", section, re.DOTALL).group(1)


class ThermodynamicsCookbookCharacterizationTest(unittest.TestCase):
    """Check Python syntax, independent inputs, and initialization order."""

    def test_oil_initializes_mixing_after_characterization(self):
        code = recipe("### Create Oil with a Characterized Plus Fraction")
        ast.parse(code)
        self.assertLess(code.index("characterisePlusFraction()"),
                        code.index('setMixingRule("classic")'))
        self.assertLess(code.index('setMixingRule("classic")'),
                        code.index("TPflash()"))
        self.assertIn("setNumberOfPseudoComponents(12)", code)
        self.assertIn('addPlusFraction("C7", 32.5, 0.220, 0.82)', code)
        self.assertIn("assert fluid.getNumberOfComponents() == 20", code)
        self.assertIn("fluid.initProperties()", code)

    def test_closures_use_independent_fluids(self):
        code = recipe("#### Choosing the closure correlation")
        tree = ast.parse(code)
        assigned = {node.targets[0].id for node in tree.body
                    if isinstance(node, ast.Assign)
                    and isinstance(node.targets[0], ast.Name)}
        self.assertTrue({"default_fluid", "rd1980_fluid"} <= assigned)
        self.assertEqual(2, code.count("SystemPrEos(333.15, 20.0)"))
        self.assertIn('default_fluid.addTBPfraction_Sg_Tb("C10", 1.0, 0.734, 447.3)', code)
        self.assertIn('"C10", 1.0, 0.734, 447.3, TbpClosure.RIAZI_DAUBERT_1980', code)
        self.assertNotRegex(code, r"(?m)^fluid\.")

    def test_dha_recipe_defines_its_own_state(self):
        code = recipe("#### Defining a cut from a detailed hydrocarbon analysis")
        ast.parse(code)
        self.assertIn("from neqsim import jneqsim", code)
        self.assertIn("fluid = jneqsim.thermo.system.SystemPrEos(333.15, 20.0)", code)
        self.assertIn('fluid.addTBPfraction_Tb_Pna("cut1", 1.0, 450.0, 0.60, 0.25, 0.15)', code)
        self.assertIn("assert fluid.getNumberOfComponents() == 1", code)
        self.assertIn('fluid.setMixingRule("classic")', code)


if __name__ == "__main__":
    unittest.main()
