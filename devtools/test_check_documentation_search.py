"""Unit tests for the documentation notebook-link audit."""

import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
