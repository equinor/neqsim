"""Catch nonexistent Java classes in hand-maintained Java and Python documentation.

This checks names, not overloads or numerical validity. Runnable Java examples
are exercised by the documentation JUnit tests. Generated notebook pages must
be repaired in their source notebooks and are outside this source-only check.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
FENCE = re.compile(r"^```(?:java|python)\s*\n(.*?)^```\s*$", re.MULTILINE | re.DOTALL)
REFERENCE = re.compile(r"\b(?:jneqsim|neqsim)(?:\.[A-Za-z_$][\w$]*)+")
DECLARATION = re.compile(r"\b(?:class|interface|enum)\s+([A-Z][\w$]*)")
WILDCARD = re.compile(r"(?m)^\s*import\s+(neqsim[\w.]*)\.\*\s*;")


def snippets(page):
    """Yield code and its first line, excluding generated notebook projections."""
    text = page.read_text(encoding="utf-8")
    if "This is an auto-generated Markdown version of the Jupyter notebook" in text:
        return
    declared = set(DECLARATION.findall(text))
    for match in FENCE.finditer(text):
        yield declared, match.group(1), text.count("\n", 0, match.start(1)) + 1


def class_prefix(reference):
    """Return the outer class, excluding nested classes and method suffixes."""
    parts = reference.replace("jneqsim.", "neqsim.", 1).split("$", 1)[0].split(".")
    for index, part in enumerate(parts[1:], 1):
        if part[0].isupper():
            return ".".join(parts[:index + 1])
    return None


class DocumentedJavaClassesTest(unittest.TestCase):
    """Validate explicit class references against production and example source."""

    def test_explicit_class_references_exist(self):
        available = {
            ".".join(path.relative_to(source_root).with_suffix("").parts)
            for source_root in (ROOT / "src/main/java", ROOT / "src/test/java")
            for path in source_root.rglob("*.java")
        }
        failures = []
        references_checked = 0
        for page in sorted(DOCS.rglob("*.md")):
            for declared, code, first_line in snippets(page):
                # Extension tutorials define their own classes within the page.
                for match in REFERENCE.finditer(code):
                    references_checked += 1
                    name = class_prefix(match.group())
                    if name is None or name in available or name.rsplit(".", 1)[-1] in declared:
                        continue
                    line = first_line + code.count("\n", 0, match.start())
                    failures.append(f"{page.relative_to(ROOT)}:{line}: {name}")
        self.assertGreater(references_checked, 1000, "The corpus scan must not pass vacuously")
        self.assertFalse(failures, "Nonexistent documented Java classes:\n" + "\n".join(failures))

    def test_wildcard_import_packages_exist(self):
        failures = []
        for page in sorted(DOCS.rglob("*.md")):
            for _text, code, first_line in snippets(page):
                for match in WILDCARD.finditer(code):
                    package = Path(*match.group(1).split("."))
                    if any((ROOT / source / package).is_dir()
                           or (ROOT / source / package).with_suffix(".java").is_file()
                           for source in ("src/main/java", "src/test/java")):
                        continue
                    line = first_line + code.count("\n", 0, match.start())
                    failures.append(f"{page.relative_to(ROOT)}:{line}: {match.group(1)}.*")
        self.assertFalse(failures, "Nonexistent documented packages:\n" + "\n".join(failures))

    def test_class_prefix_handles_nested_types_and_calls(self):
        self.assertEqual(
            "neqsim.process.equipment.failure.ReliabilityDataSource",
            class_prefix("jneqsim.process.equipment.failure.ReliabilityDataSource.ReliabilityData"),
        )
        self.assertEqual(
            "neqsim.thermo.system.SystemSrkEos",
            class_prefix("neqsim.thermo.system.SystemSrkEos.class"),
        )
        self.assertIsNone(class_prefix("neqsim.process.equipment"))
        self.assertEqual(
            "neqsim.process.equipment.pipeline.TwoFluidPipe",
            class_prefix("neqsim.process.equipment.pipeline.TwoFluidPipe$SlugTrackingMode"),
        )


if __name__ == "__main__":
    unittest.main()
