"""Protect the executable Python example in the MCP core README."""

import ast
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "src/main/java/neqsim/mcp/README.md"
JAVA_RUNTIME_TEST = (
    ROOT / "src/test/java/neqsim/mcp/runners/FlashRunnerTest.java"
)
EXAMPLE_MARKER = "<!-- mcp-readme-example: python-flash-runner -->"
PYTHON_EXAMPLE = re.compile(
    rf"{re.escape(EXAMPLE_MARKER)}\n```python\n(?P<source>.*?)\n```",
    re.DOTALL,
)
EXPECTED_REQUEST = {
    "model": "SRK",
    "temperature": {"value": 25.0, "unit": "C"},
    "pressure": {"value": 50.0, "unit": "bara"},
    "flashType": "TP",
    "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
    "mixingRule": "classic",
}


class McpReadmeDocumentationContractTest(unittest.TestCase):
    """Keep the public Python snippet literal, supported, and runtime-owned."""

    @classmethod
    def setUpClass(cls):
        cls.readme = README.read_text(encoding="utf-8")
        cls.java_runtime_test = JAVA_RUNTIME_TEST.read_text(encoding="utf-8")

    def python_source(self):
        """Return the one frozen Python example after checking its ownership marker."""
        self.assertEqual(self.readme.count(EXAMPLE_MARKER), 1)
        matches = list(PYTHON_EXAMPLE.finditer(self.readme))
        self.assertEqual(len(matches), 1)
        return matches[0].group("source")

    def test_literal_python_source_parses_and_keeps_the_complete_request(self):
        source = self.python_source()
        module = ast.parse(source, filename=str(README))
        request_assignments = [
            node
            for node in module.body
            if isinstance(node, ast.Assign)
            and any(
                isinstance(target, ast.Name) and target.id == "request"
                for target in node.targets
            )
        ]
        self.assertEqual(len(request_assignments), 1)
        self.assertEqual(ast.literal_eval(request_assignments[0].value), EXPECTED_REQUEST)

    def test_python_example_uses_the_supported_gateway_and_json_round_trip(self):
        source = self.python_source()
        for required in (
            "from neqsim import jneqsim",
            "FlashRunner = jneqsim.mcp.runners.FlashRunner",
            "FlashRunner.run(json.dumps(request))",
            "response = json.loads(str(",
            'assert response["status"] == "success"',
            'assert response["data"]["flash"]["numberOfPhases"] >= 1',
        ):
            self.assertIn(required, source)

        for unsupported in (
            "import jpype",
            "jpype.JClass",
            "JClass(",
            "jNeqSim",
            "...",
        ):
            self.assertNotIn(unsupported, source)

    def test_equivalent_java_runtime_owner_covers_the_canonical_response(self):
        owner = re.search(
            r"void testTPFlash_simpleGas\(\) \{(?P<body>.*?)(?=\n  @Test)",
            self.java_runtime_test,
            re.DOTALL,
        )
        self.assertIsNotNone(owner)
        body = owner.group("body")
        for required in (
            r'\"model\": \"SRK\"',
            r'\"temperature\": {\"value\": 25.0, \"unit\": \"C\"}',
            r'\"pressure\": {\"value\": 50.0, \"unit\": \"bara\"}',
            r'\"components\": {\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}',
            "FlashRunner.run(json)",
            'assertEquals("success", root.get("status").getAsString())',
            'JsonObject data = root.getAsJsonObject("data")',
            'data.getAsJsonObject("flash").get("numberOfPhases")',
        ):
            self.assertIn(required, body)


if __name__ == "__main__":
    unittest.main()
