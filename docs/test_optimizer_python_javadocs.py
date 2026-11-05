"""Protects the Python gateway examples embedded in optimizer Javadocs."""

import ast
import html
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = {
    REPO_ROOT
    / "src/main/java/neqsim/process/util/optimizer/ProductionOptimizationSpecLoader.java":
        ("Python Usage Example", "ProductionOptimizationSpecLoader"),
    REPO_ROOT
    / "src/main/java/neqsim/process/util/optimizer/CompressorOptimizationHelper.java":
        ("Usage Example (Python via JPype)", "CompressorOptimizationHelper"),
}


def _extract_python_example(source, heading):
    """Return one Python Javadoc block with comment and HTML wrappers removed."""
    heading_start = source.index(heading)
    pre_start = source.index("<pre", heading_start)
    code_start = source.index("\n", pre_start) + 1
    code_end = source.index("</pre>", code_start)
    wrapped_as_code = "{@code" in source[pre_start:code_start]

    lines = [
        re.sub(r"^\\s*\\* ?", "", line)
        for line in source[code_start:code_end].splitlines()
    ]
    if wrapped_as_code and lines and lines[-1].strip() == "}":
        lines.pop()
    return html.unescape("\n".join(lines).strip()) + "\n"


def test_optimizer_python_javadocs_use_supported_gateway_and_parse():
    """Compile both exact Python fragments and protect their public class paths."""
    for path, (heading, expected_class) in EXAMPLES.items():
        source = path.read_text(encoding="utf-8")
        example = _extract_python_example(source, heading)

        compile(example, str(path), "exec")
        tree = ast.parse(example)
        imports = [
            node
            for node in ast.walk(tree)
            if isinstance(node, ast.ImportFrom)
            and node.module == "neqsim"
            and any(alias.name == "jneqsim" for alias in node.names)
        ]

        assert len(imports) == 1
        assert "neqsim.neqsimpython" not in example
        assert "jneqsim.process.util.optimizer." + expected_class in example
        assert source.count("from neqsim import jneqsim") == 1
        assert "System.out" not in source
        assert "System.err" not in source
