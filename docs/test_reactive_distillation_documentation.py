"""Contracts for the reactive-distillation Java and Python guide examples."""

import ast
from pathlib import Path
import re
import unittest


GUIDE = Path(__file__).resolve().parent / "process/reactive_distillation.md"
PYTHON_SECTION = re.compile(
    r"^### Python \(Jupyter\)\s*$.*?^```python\s*$\n(.*?)^```\s*$",
    re.MULTILINE | re.DOTALL,
)
JAVA_MARKER = "<!-- reactive-distillation-quick-start: complete -->"


class ReactiveDistillationDocumentationContractTest(unittest.TestCase):
    """Protect the bounded reactive-distillation documentation surface."""

    def source(self):
        """Return the current guide source."""
        return GUIDE.read_text(encoding="utf-8")

    def python_example(self):
        """Return the unique companion Python program."""
        matches = PYTHON_SECTION.findall(self.source())
        self.assertEqual(1, len(matches))
        ast.parse(matches[0])
        return matches[0]

    def test_page_uses_front_matter_title_without_duplicate_h1(self):
        """Keep the title in Jekyll front matter only."""
        source = self.source()
        self.assertTrue(source.startswith("---\n"))
        self.assertNotRegex(source, r"(?m)^# Reactive Distillation in NeqSim\s*$")

    def test_only_complete_java_program_remains(self):
        """Reject a return of unverified Java fragments or console output."""
        source = self.source()
        self.assertEqual(1, source.count(JAVA_MARKER))
        self.assertEqual(1, len(re.findall(r"(?m)^```java\s*$", source)))
        self.assertNotIn("System.out", source)
        self.assertNotIn("System.err", source)

    def test_python_example_is_silent_complete_and_self_validating(self):
        """Require initialization, execution, and bounded result checks."""
        source = self.python_example()
        required_in_order = (
            "import math",
            "from neqsim import jneqsim",
            'fluid = SystemSrkEos(273.15 + 250.0, 10.0)',
            'feed = Stream("WGS feed", fluid)',
            'feed.setPressure(10.0, "bara")',
            "feed.run()",
            'column = DistillationColumn("WGS Column", 3, True, False)',
            "column.setReactive(True)",
            "column.addFeedStream(feed, 2)",
            "column.run()",
            "h2_gas = column.getGasOutStream()",
            "assert math.isfinite(h2_gas)",
            "assert 0.10 < h2_gas < 1.0",
        )
        positions = [source.index(token) for token in required_in_order]
        self.assertEqual(sorted(positions), positions)

        self.assertNotIn("print(", source)
        self.assertNotIn("System.out", source)
        self.assertNotIn("jpype", source)
        self.assertNotIn("JClass", source)


if __name__ == "__main__":
    unittest.main()
