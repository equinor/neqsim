"""Unit tests for the documentation notebook-link audit."""

import unittest
from pathlib import Path
import tempfile
from unittest import mock

from devtools import check_documentation_search as audit


class DocumentationNotebookLinkAuditTest(unittest.TestCase):
    def test_relative_markdown_notebook_link_is_rejected(self) -> None:
        errors = audit.relative_notebook_link_errors(
            audit.ROOT / "docs" / "example.md",
            "[Example](../examples/example.ipynb)",
        )

        self.assertEqual(len(errors), 1)
        self.assertIn("relative notebook link ../examples/example.ipynb", errors[0])

    def test_markdown_code_examples_are_ignored(self) -> None:
        markdown = """`[Inline](../examples/inline.ipynb)`

```markdown
[Fenced](../examples/fenced.ipynb)
```
"""

        self.assertEqual(
            audit.relative_notebook_link_errors(
                audit.ROOT / "docs" / "example.md",
                markdown,
            ),
            [],
        )

    def test_relative_html_notebook_link_is_rejected(self) -> None:
        errors = audit.relative_notebook_link_errors(
            audit.ROOT / "docs" / "example.html",
            '<a href = "../examples/example.ipynb">Example</a>',
            strip_markdown_code=False,
        )

        self.assertEqual(len(errors), 1)
        self.assertIn("relative notebook link ../examples/example.ipynb", errors[0])

    def test_absolute_notebook_links_are_allowed(self) -> None:
        links = """
[GitHub](https://github.com/equinor/neqsim/blob/master/examples/example.ipynb)
<a href="//colab.research.google.com/example.ipynb">Colab</a>
"""

        self.assertEqual(
            audit.relative_notebook_link_errors(
                Path(audit.ROOT / "docs" / "example.md"),
                links,
            ),
            [],
        )


class DocumentationPageLinkAuditTest(unittest.TestCase):
    def test_include_scan_reuses_the_audited_source_text(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            docs = Path(temporary_directory)
            source = docs / "source.md"
            target = docs / "included.md"
            audited_text = "{% include_relative included.md %}\n"
            source.write_text(audited_text, encoding="utf-8")
            target.write_text("included\n", encoding="utf-8")
            source.write_bytes(b"\xaa\x00")

            self.assertEqual(
                audit.included_markdown_sources([source], {source: audited_text}),
                [target.resolve()],
            )

    def test_include_scan_does_not_reread_a_source_that_failed_decoding(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source = Path(temporary_directory) / "invalid.md"
            source.write_bytes(b"\xaa\x00")

            self.assertEqual(audit.included_markdown_sources([source], {}), [])

    def test_generated_and_dependency_directories_are_not_document_sources(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            docs = Path(temporary_directory)
            (docs / "guide.md").write_text("---\ntitle: Guide\n---\ntext\n", encoding="utf-8")
            for directory in ("vendor", "_site", ".jekyll-cache"):
                generated = docs / directory / "nested"
                generated.mkdir(parents=True)
                (generated / "not-source.md").write_bytes(b"\xaa\x00")
                (generated / "not-source.html").write_bytes(b"\xaa\x00")

            with mock.patch.object(audit, "DOCS", docs):
                self.assertEqual(audit.markdown_files(), [docs / "guide.md"])
                self.assertEqual(audit.content_html_files(), [])

    def test_included_markdown_requires_extensionless_links(self) -> None:
        errors = audit.included_markdown_suffix_errors(
            audit.DOCS / "process" / "equipment" / "README.md",
            "[Separators](separators.md) and [external](https://example.com/guide.md)",
        )

        self.assertEqual(len(errors), 1)
        self.assertIn("retains a .md suffix", errors[0])

    def test_existing_extensionless_document_link_is_allowed(self) -> None:
        errors = audit.internal_document_link_errors(
            audit.DOCS / "process" / "equipment" / "README.md",
            "[Separators](separators)",
        )

        self.assertEqual(errors, [])

    def test_missing_internal_document_link_is_rejected(self) -> None:
        errors = audit.internal_document_link_errors(
            audit.DOCS / "process" / "equipment" / "README.md",
            "[Missing](equipment-page-that-does-not-exist)",
        )

        self.assertEqual(len(errors), 1)
        self.assertIn("target does not exist", errors[0])

    def test_repository_source_link_outside_docs_is_not_treated_as_a_page(self) -> None:
        errors = audit.internal_document_link_errors(
            audit.DOCS / "development" / "example.md",
            "[Agent](../../.github/agents/documentation.agent.md)",
        )

        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
