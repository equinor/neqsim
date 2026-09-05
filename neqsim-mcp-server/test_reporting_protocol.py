"""Focused real-MCP qualification for reporting and task-workflow handoff.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies the existing ``generateReport`` and ``bridgeTaskWorkflow``
software contracts. It uses synthetic data and does not execute a simulation,
persist a report, render Word/HTML, validate physical fidelity, or grant plant,
design, control, certification, or engineering-approval authority.
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
                        "name": "neqsim-reporting-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require("result" in response, "MCP initialize did not return a result", response)
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_tool(self, name, argument_name, value):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "tools/call",
                "params": {
                    "name": name,
                    "arguments": {argument_name: value},
                },
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

    def call_no_arg_tool(self, name):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "tools/call",
                "params": {"name": name, "arguments": {}},
            }
        )
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def report(self, request):
        value = request if isinstance(request, str) else json.dumps(request)
        return self.call_tool("generateReport", "reportJson", value)

    def bridge(self, request):
        value = request if isinstance(request, str) else json.dumps(request)
        return self.call_tool("bridgeTaskWorkflow", "bridgeJson", value)

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
        "provenance",
        "validation",
        "qualityGate",
        "warnings",
        "errors",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def test_structured_report(client):
    response = client.report(
        {
            "reportType": "custom",
            "title": "Compression review",
            "author": "Packaged MCP contract",
            "includeValidation": False,
            "data": {
                "temperature_C": 25.0,
                "pressure_bar": 50.0,
                "curve": [1.0, 2.0, 3.0],
                "conclusions": "Synthetic evidence only.",
            },
        }
    )
    report = response.get("data", response)
    result = payload(response)
    require(result.get("status") == "success", "report transport failed", result)
    require(result.get("title") == "Compression review", "report title drifted", result)
    require(result.get("author") == "Packaged MCP contract", "report author drifted", result)
    require(result.get("reportType") == "custom", "report type drifted", result)
    require(isinstance(result.get("generatedAt"), str), "generation timestamp is absent", result)
    require("# Compression review" in result.get("markdown", ""), "Markdown title is absent", result)
    require("Synthetic evidence only." in result.get("markdown", ""), "conclusion is absent", result)
    require(len(result.get("tables", [])) == 1, "structured table is absent", result)
    require(len(result.get("chartData", [])) == 1, "chart-ready array is absent", result)
    require(result["chartData"][0].get("name") == "curve", "chart source drifted", result)
    require("validation" not in report, "disabled report validation was emitted", report)
    require(
        response.get("validation", {}).get("valid") is True,
        "standard MCP envelope validation is absent",
        response,
    )
    require(
        result.get("summary")
        == {"numericFields": 2, "objectFields": 0, "arrayFields": 1, "totalFields": 4},
        "summary counts drifted",
        result,
    )
    rows = result["tables"][0].get("rows", [])
    require(rows[0] == ["temperature_C", 25.0, "C"], "temperature row drifted", rows)
    require(rows[1] == ["pressure_bar", 50.0, "bara"], "pressure row drifted", rows)


def test_report_switches_and_fail_closed_input(client):
    response = client.report(
        {"includeChartData": False, "includeValidation": False, "data": {}}
    )
    report = response.get("data", response)
    result = payload(response)
    require(result.get("status") == "success", "minimal report failed", result)
    require("chartData" not in report, "disabled chart data was emitted", report)
    require("validation" not in report, "disabled report validation was emitted", report)
    require(isinstance(result.get("tables"), list), "tables field is absent", result)

    for malformed in ("{bad json}", "[]"):
        error = payload(client.report(malformed))
        require(error.get("status") == "error", "malformed report input was accepted", error)
        errors = error.get("errors", [])
        require(errors and errors[0].get("code") == "REPORT_ERROR", "report error code drifted", error)


def test_bridge_results_json(client):
    result = payload(
        client.bridge(
            {
                "action": "toResultsJson",
                "sourceRunner": "runFlash",
                "approach": "Synthetic SRK handoff",
                "conclusions": "Contract shape only",
                "toolOutput": {
                    "status": "success",
                    "fluid": {
                        "conditions": {
                            "temperature_K": 300.0,
                            "pressure_bara": 42.0,
                        },
                        "properties": {
                            "density_kgm3": 10.5,
                            "molarMass_kgmol": 0.020,
                        },
                    },
                    "flash": {"numberOfPhases": 2},
                },
            }
        )
    )
    require(result.get("status") == "success", "bridge transport failed", result)
    handoff = result.get("resultsJson", {})
    key_results = handoff.get("key_results", {})
    require(abs(key_results.get("temperature_C", 0.0) - 26.85) < 1.0e-10, "temperature conversion drifted", handoff)
    require(key_results.get("pressure_bar") == 42.0, "pressure handoff drifted", handoff)
    require(key_results.get("density_kgm3") == 10.5, "density handoff drifted", handoff)
    require(key_results.get("number_of_phases") == 2, "phase count drifted", handoff)
    require(handoff.get("validation", {}).get("acceptance_criteria_met") is True, "validation handoff drifted", handoff)
    require(handoff.get("approach") == "Synthetic SRK handoff", "approach drifted", handoff)
    require(handoff.get("conclusions") == "Contract shape only", "conclusions drifted", handoff)
    require(handoff.get("_meta", {}).get("tool") == "runFlash", "source metadata drifted", handoff)
    for field, expected_type in (
        ("figure_captions", dict),
        ("figure_discussion", list),
        ("equations", list),
        ("tables", list),
        ("references", list),
    ):
        require(isinstance(handoff.get(field), expected_type), field + " shape drifted", handoff)


def test_bridge_schema(client):
    result = payload(client.bridge({"action": "getSchema"}))
    require(result.get("status") == "success", "bridge schema failed", result)
    fields = result.get("fields", {})
    expected = {
        "key_results",
        "validation",
        "approach",
        "conclusions",
        "figure_captions",
        "figure_discussion",
        "equations",
        "tables",
        "references",
        "uncertainty",
        "risk_evaluation",
        "benchmark_validation",
    }
    require(expected.issubset(fields), "bridge schema is incomplete", result)
    require(result.get("schema") == fields, "schema alias drifted", result)


def test_bridge_fail_closed_input(client):
    for request in (
        "{bad json}",
        {"action": "unknown"},
        {"action": "toResultsJson"},
    ):
        result = payload(client.bridge(request))
        require(result.get("status") == "error", "invalid bridge request was accepted", result)
        require(isinstance(result.get("message"), str), "bridge error message is absent", result)


def test_phase0_inventory_promotes_reporting_contracts(client):
    result = payload(client.call_no_arg_tool("getCapabilities"))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    limitations = inventory.get("knownLimitations", {})
    records = limitations.get("coverageRecords", {})
    require(inventory.get("inventoryVersion") == "1.26", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 25
        and limitations.get("confirmedGapToolCount") == 26
        and limitations.get("contractPromotionCandidateCount") == 0,
        "reporting promotion accounting drifted",
        limitations,
    )
    expected = {
        "generateReport": "NOT_APPLICABLE_NON_NUMERICAL_REPORT_GENERATION",
        "bridgeTaskWorkflow": "NOT_APPLICABLE_NON_NUMERICAL_TASK_WORKFLOW_HANDOFF",
    }
    for tool, applicability in expected.items():
        record = records.get(tool, {})
        require(
            record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("benchmarkApplicability") == applicability
            and "neqsim-mcp-server/test_reporting_protocol.py"
            in record.get("contractEvidenceSources", [])
            and record.get("contractEvidenceCount")
            == len(record.get("contractEvidenceSources", [])),
            tool + " promotion evidence drifted",
            record,
        )
    require(
        "report completeness" in records["generateReport"].get("evidenceBoundary", ""),
        "report-generation stop boundary drifted",
        records["generateReport"],
    )
    require(
        "does not execute or recompute a simulation"
        in records["bridgeTaskWorkflow"].get("evidenceBoundary", ""),
        "workflow-handoff stop boundary drifted",
        records["bridgeTaskWorkflow"],
    )


def main():
    client = McpClient()
    tests = [
        ("structured report", test_structured_report),
        ("report switches and fail-closed input", test_report_switches_and_fail_closed_input),
        ("results.json bridge", test_bridge_results_json),
        ("bridge schema", test_bridge_schema),
        ("bridge fail-closed input", test_bridge_fail_closed_input),
        ("Phase 0 reporting promotion", test_phase0_inventory_promotes_reporting_contracts),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} reporting contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
