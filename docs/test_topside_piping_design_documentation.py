"""Keep the topside-piping Python examples on the supported gateway."""

import ast
from pathlib import Path
import re
import unittest


PAGE = Path(__file__).resolve().parent / "process" / "topside_piping_design.md"
PYTHON_FENCE = re.compile(r"```python\n(.*?)```", re.DOTALL)


class TopsidePipingPythonExampleContractTest(unittest.TestCase):
    """Check the committed Python snippets before documentation publication."""

    @classmethod
    def setUpClass(cls):
        cls.page = PAGE.read_text(encoding="utf-8")
        cls.examples = PYTHON_FENCE.findall(cls.page)

    def test_python_examples_parse(self):
        self.assertEqual(2, len(self.examples))
        for index, source in enumerate(self.examples):
            with self.subTest(example=index):
                ast.parse(source)

    def test_python_examples_use_supported_gateway(self):
        self.assertNotIn("jNeqSim", self.page)
        for index, source in enumerate(self.examples):
            with self.subTest(example=index):
                self.assertIn("from neqsim import jneqsim", source)
                self.assertIn("jneqsim.process.", source)

    def test_examples_keep_topside_api_paths(self):
        complete_workflow, velocity_analysis = self.examples
        self.assertIn(
            "jneqsim.process.equipment.pipeline.TopsidePiping",
            complete_workflow,
        )
        self.assertIn(
            "jneqsim.process.mechanicaldesign.pipeline."
            "TopsidePipingMechanicalDesignCalculator",
            velocity_analysis,
        )


if __name__ == "__main__":
    unittest.main()
