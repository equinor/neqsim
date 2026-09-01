"""Protect clean-run JPype aliases in generated example notebooks."""

import json
from pathlib import Path
import re
import unittest


EXAMPLES = Path(__file__).resolve().parent / "examples"
ALIASES = ("JClass", "JArray", "JDouble", "JInt", "JString")


def defined_aliases(source):
    """Return JPype aliases explicitly imported or assigned by one cell."""
    defined = set()
    for alias in ALIASES:
        assignment = re.compile(
            r"(?m)^\s*" + re.escape(alias) + r"\s*="
        )
        imported = re.compile(
            r"(?m)^\s*from\s+jpype(?:\.types)?\s+import\s+[^#\n]*\b"
            + re.escape(alias)
            + r"\b"
        )
        if assignment.search(source) or imported.search(source):
            defined.add(alias)
    return defined


def bare_aliases_used(source):
    """Return bare JPype constructor aliases called by one cell."""
    used = set()
    for alias in ALIASES:
        call = re.compile(
            r"(^|[^.\w])" + re.escape(alias) + r"\s*\(",
            re.MULTILINE,
        )
        if call.search(source):
            used.add(alias)
    return used


class NotebookPythonGatewayContractTest(unittest.TestCase):
    """Keep committed notebook sources executable in clean sequential order."""

    def test_notebooks_define_bare_jpype_aliases_before_use(self):
        notebooks = sorted(EXAMPLES.glob("*.ipynb"))
        self.assertEqual(30, len(notebooks))

        for notebook_path in notebooks:
            notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
            defined = set()
            for cell_index, cell in enumerate(notebook.get("cells", [])):
                if cell.get("cell_type") != "code":
                    continue
                source = "".join(cell.get("source", []))
                local_definitions = defined_aliases(source)
                unresolved = (
                    bare_aliases_used(source)
                    - defined
                    - local_definitions
                )
                with self.subTest(
                    notebook=notebook_path.name,
                    cell=cell_index,
                ):
                    self.assertFalse(
                        unresolved,
                        msg=(
                            "Undefined JPype aliases: "
                            + ", ".join(sorted(unresolved))
                        ),
                    )
                defined.update(local_definitions)

    def test_generated_pages_keep_repaired_gateway_calls(self):
        graph_page = (
            EXAMPLES / "GraphBasedProcessSimulation.md"
        ).read_text(encoding="utf-8")
        tvp_page = (EXAMPLES / "TVP_RVP_Study.md").read_text(
            encoding="utf-8"
        )

        self.assertIn("import jpype", graph_page)
        self.assertIn(
            "jpype.JArray(jpype.JDouble)([0.6, 0.4])",
            graph_page,
        )
        self.assertIn(
            "PseudoComponentCombiner = jpype.JClass(",
            tvp_page,
        )
        self.assertIn(
            "SystemInterface = jpype.JClass(",
            tvp_page,
        )
        self.assertNotIn(
            "splitter.setSplitFactors(JArray(JDouble)",
            graph_page,
        )
        self.assertNotIn(
            "PseudoComponentCombiner = JClass(",
            tvp_page,
        )
        self.assertNotIn("SystemInterface = JClass(", tvp_page)


if __name__ == "__main__":
    unittest.main()
