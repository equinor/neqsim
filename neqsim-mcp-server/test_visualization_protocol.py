"""Focused packaged-MCP qualification for visualization generation.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies type dispatch, aliases, markup serialization, escaping, stable
media fields, fail-closed inputs, and transport. It does not establish browser
rendering, markup sandbox security, accessibility, numerical correctness,
complete process topology, plant authority, certification, or engineering
approval.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC client for packaged-server qualification."""

    def __init__(self):
        self.proc = None
        self.message_id = 0

    def next_id(self):
        self.message_id += 1
        return self.message_id

    def send(self, message):
        self.proc.stdin.write(json.dumps(message) + "\n")
        self.proc.stdin.flush()

    def receive(self):
        line = self.proc.stdout.readline()
        if not line:
            stderr = self.proc.stderr.read() if self.proc and self.proc.stderr else ""
            raise RuntimeError("MCP server closed stdout unexpectedly: " + stderr)
        return json.loads(line)

    def start(self):
        self.proc = subprocess.Popen(
            ["java", "-jar", JAR],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "neqsim-visualization-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require(
            "result" in response,
            "MCP initialize did not return a result",
            response,
        )
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_tool(self, name, arguments):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "tools/call",
                "params": {"name": name, "arguments": arguments},
            }
        )
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        text = content[0].get("text", "")
        try:
            return json.loads(text)
        except json.JSONDecodeError as error:
            raise AssertionError(
                f"MCP tool returned non-JSON content: {error}: {text}"
            )

    def visualize(self, request):
        return self.call_tool(
            "generateVisualization", {"vizJson": json.dumps(request)}
        )

    def close(self):
        if self.proc is None:
            return
        if self.proc.stdin:
            self.proc.stdin.close()
        try:
            self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            self.proc.wait(timeout=10)
        self.proc = None


def require(condition, message, detail=None):
    """Raise a compact assertion with JSON detail when a contract fails."""
    if condition:
        return
    suffix = ""
    if detail is not None:
        suffix = "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


def payload(response):
    """Return canonical data while retaining standardized envelope fields."""
    data = response.get("data") if isinstance(response, dict) else None
    if not isinstance(data, dict):
        return response
    merged = dict(data)
    for key in (
        "status",
        "tool",
        "code",
        "errors",
        "provenance",
        "validation",
        "qualityGate",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def assert_success(response, viz_type, mime_type, content_field):
    """Validate the standard envelope and canonical visualization media fields."""
    result = payload(response)
    require(result.get("status") == "success", "visualization failed", response)
    require(
        result.get("tool") == "generateVisualization",
        "standard tool identity drifted",
        response,
    )
    require(
        result.get("visualizationType") == viz_type,
        "canonical visualization type drifted",
        result,
    )
    require(result.get("mimeType") == mime_type, "MIME type drifted", result)
    require(
        isinstance(result.get(content_field), str) and result.get(content_field),
        "visualization content missing",
        result,
    )
    require(
        result.get("validation", {}).get("valid") is True,
        "visualization lacks a valid standard envelope",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "visualization did not pass the software gate",
        response,
    )
    return result


def assert_error(response):
    """Require malformed or unsupported input to fail closed."""
    result = payload(response)
    require(result.get("status") == "error", "input did not fail closed", response)


def test_bar_chart_and_xml_escaping(client):
    result = assert_success(
        client.visualize(
            {
                "type": "barChart",
                "title": "<unsafe>&",
                "labels": ["<feed>", "product"],
                "values": [1.0, 2.0],
            }
        ),
        "barChart",
        "image/svg+xml",
        "svg",
    )
    svg = result["svg"]
    require("&lt;unsafe&gt;&amp;" in svg, "chart title was not XML escaped", result)
    require("&lt;feed&gt;" in svg, "chart label was not XML escaped", result)
    require("<unsafe>" not in svg, "raw chart markup leaked", result)


def test_flowsheet_documented_alias(client):
    result = assert_success(
        client.visualize(
            {
                "type": "flowsheetDiagram",
                "title": "Separation",
                "equipment": [
                    {"name": "Feed", "type": "Stream"},
                    {"name": "HP Sep", "type": "Separator"},
                ],
            }
        ),
        "flowsheet",
        "text/x-mermaid",
        "mermaid",
    )
    require(
        "Feed --> HP_Sep" in result["mermaid"],
        "flowsheet topology drifted",
        result,
    )


def test_styled_table_alias_and_html_escaping(client):
    result = assert_success(
        client.visualize(
            {
                "type": "styledTable",
                "caption": "<summary>&",
                "headers": ["<property>", "value"],
                "rows": [["<pressure>", "50 bara"]],
            }
        ),
        "propertyTable",
        "text/html",
        "html",
    )
    html = result["html"]
    require("&lt;summary&gt;&amp;" in html, "table caption was not escaped", result)
    require("&lt;property&gt;" in html, "table header was not escaped", result)
    require("&lt;pressure&gt;" in html, "table cell was not escaped", result)


def test_readme_table_alias(client):
    assert_success(
        client.visualize(
            {
                "type": "table",
                "headers": ["property", "value"],
                "rows": [["pressure", "50 bara"]],
            }
        ),
        "propertyTable",
        "text/html",
        "html",
    )


def test_additional_chart_contracts(client):
    pie = client.visualize(
        {"type": "pieChart", "categories": ["gas", "liquid"], "values": [2, 1]}
    )
    line = client.visualize(
        {"type": "lineChart", "xValues": [0, 1], "yValues": [1, 2]}
    )
    assert_success(pie, "pieChart", "image/svg+xml", "svg")
    assert_success(line, "lineChart", "image/svg+xml", "svg")


def test_invalid_arrays_fail_closed(client):
    assert_error(
        client.visualize(
            {"type": "barChart", "labels": ["A", "B"], "values": [1]}
        )
    )
    assert_error(
        client.visualize(
            {"type": "pieChart", "categories": ["A"], "values": [1, 2]}
        )
    )
    assert_error(
        client.visualize(
            {"type": "lineChart", "xValues": [0, 1], "yValues": [1]}
        )
    )


def test_missing_unknown_and_malformed_inputs_fail_closed(client):
    assert_error(client.visualize({}))
    assert_error(client.visualize({"type": "unknown"}))
    malformed = client.call_tool("generateVisualization", {"vizJson": "{"})
    assert_error(malformed)


def test_inventory_records_atomic_promotion(client):
    response = payload(client.call_tool("getCapabilities", {}))
    inventory = response.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get(
        "generateVisualization", {}
    )
    require(
        inventory.get("inventoryVersion") == "1.39",
        "inventory version drifted",
        inventory,
    )
    require(
        limitations.get("contractTestedToolCount") == 39
        and limitations.get("confirmedGapToolCount") == 12,
        "promotion accounting drifted",
        limitations,
    )
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED",
        "visualization contract was not promoted",
        record,
    )
    require(
        record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_VISUALIZATION_GENERATION"
        and "neqsim-mcp-server/test_visualization_protocol.py"
        in record.get("contractEvidenceSources", [])
        and "VISUALIZATION_CONTRACT.md"
        in " ".join(record.get("contractEvidenceSources", []))
        and limitations.get("contractPromotionCandidateCount") == 0,
        "visualization evidence boundary drifted",
        record,
    )


def main():
    client = McpClient()
    tests = [
        ("bar chart and XML escaping", test_bar_chart_and_xml_escaping),
        ("documented flowsheet alias", test_flowsheet_documented_alias),
        (
            "styled table alias and HTML escaping",
            test_styled_table_alias_and_html_escaping,
        ),
        ("README table alias", test_readme_table_alias),
        ("additional chart contracts", test_additional_chart_contracts),
        ("invalid arrays fail closed", test_invalid_arrays_fail_closed),
        (
            "missing, unknown, and malformed inputs fail closed",
            test_missing_unknown_and_malformed_inputs_fail_closed,
        ),
        ("inventory records atomic promotion", test_inventory_records_atomic_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} visualization contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
