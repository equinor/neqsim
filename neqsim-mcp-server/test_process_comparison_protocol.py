"""Packaged-MCP qualification for bounded canonical process comparison.

Exercises real STDIO transport, canonical ProcessRunner delegation, deterministic
order, admission limits, partial-failure visibility, schema/example discovery,
standard response evidence, and atomic Phase 0 accounting. It does not establish
compatible case bases, numerical accuracy, convergence for arbitrary inputs,
optimization quality, facility fidelity, plant authority, or approval.
"""

import json
import subprocess
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


def require(condition, message, detail=None):
    if condition:
        return
    suffix = "" if detail is None else "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


def payload(response):
    data = response.get("data") if isinstance(response, dict) else None
    if not isinstance(data, dict):
        return response
    merged = dict(data)
    for key in ("status", "tool", "errors", "message", "validation", "qualityGate"):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


class McpClient:
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
                        "name": "neqsim-process-comparison-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send({"jsonrpc": "2.0", "id": self.next_id(), "method": method, "params": params})
        return self.receive()

    def call_tool(self, name, arguments):
        response = self.request("tools/call", {"name": name, "arguments": arguments})
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_compare(self, request):
        return self.call_tool(
            "compareProcesses", {"comparisonJson": json.dumps(request)}
        )

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

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


def valid_case(name, pressure):
    return {
        "name": name,
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": pressure,
            "mixingRule": "classic",
            "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
        },
        "process": [
            {
                "type": "Stream",
                "name": "feed",
                "properties": {"flowRate": [10000.0, "kg/hr"]},
            },
            {"type": "Separator", "name": "HP Sep", "inlet": "feed"},
        ],
    }


def assert_success(response):
    result = payload(response)
    require(result.get("status") == "success", "comparison request failed", response)
    require(result.get("tool") == "compareProcesses", "tool identity drifted", response)
    require(result.get("validation", {}).get("valid") is True, "validation drifted", response)
    require(result.get("qualityGate", {}).get("verdict") == "passed", "gate failed", response)
    return result


def assert_error(response):
    result = payload(response)
    require(result.get("status") == "error", "request did not fail closed", response)
    require(isinstance(result.get("message"), str), "error message missing", result)


def test_discovery_and_schema(client):
    tool = next(
        (item for item in client.list_tools() if item.get("name") == "compareProcesses"),
        None,
    )
    require(tool is not None, "compareProcesses missing")
    description = tool.get("description", "")
    require(
        "two to 32" in description
        and "1 MiB UTF-8" in description
        and "canonical ProcessRunner" in description
        and "partial failures remain visible" in description,
        "discovery boundary drifted",
        tool,
    )
    schema = payload(
        client.call_tool(
            "getSchema", {"toolName": "compare_processes", "schemaType": "input"}
        )
    )
    cases = schema.get("properties", {}).get("cases", {})
    item = cases.get("items", {})
    name = item.get("properties", {}).get("name", {})
    require(
        cases.get("minItems") == 2
        and cases.get("maxItems") == 32
        and name.get("maxLength") == 256
        and item.get("required") == ["fluid", "process"],
        "schema bounds drifted",
        schema,
    )


def test_catalog_example(client):
    example = payload(
        client.call_tool("getExample", {"category": "comparison", "name": "two-cases"})
    )
    require(
        isinstance(example.get("cases"), list) and len(example.get("cases")) == 2,
        "comparison example drifted",
        example,
    )
    result = assert_success(client.call_compare(example))
    require(result.get("complete") is True, "catalog comparison incomplete", result)


def test_complete_canonical_comparison(client):
    result = assert_success(
        client.call_compare(
            {"cases": [valid_case("Low", 30.0), valid_case("High", 80.0)]}
        )
    )
    require(
        result.get("complete") is True
        and result.get("caseCount") == 2
        and result.get("successfulCaseCount") == 2
        and result.get("failedCaseCount") == 0,
        "complete accounting drifted",
        result,
    )
    require(result.get("caseNames") == ["Low", "High"], "case order drifted", result)
    require(
        all(
            item.get("converged") is True
            and item.get("result", {}).get("status") == "success"
            and item.get("result", {}).get("tool") == "runProcess"
            for item in result.get("cases", [])
        ),
        "canonical case result missing",
        result,
    )


def test_repeated_order_is_stable(client):
    request = {"cases": [valid_case("A", 30.0), valid_case("B", 80.0)]}
    for result in (
        assert_success(client.call_compare(request)),
        assert_success(client.call_compare(request)),
    ):
        require(result.get("caseNames") == ["A", "B"], "name order drifted", result)
        require(
            [item.get("name") for item in result.get("cases", [])] == ["A", "B"],
            "result order drifted",
            result,
        )


def test_partial_failure_is_visible(client):
    failing = valid_case("Invalid", 80.0)
    failing["process"][0]["type"] = "NotARealUnit"
    result = assert_success(
        client.call_compare({"cases": [valid_case("Valid", 30.0), failing]})
    )
    failed = result.get("cases", [None, None])[1]
    require(
        result.get("complete") is False
        and result.get("successfulCaseCount") == 1
        and result.get("failedCaseCount") == 1
        and len(result.get("errors", [])) == 1
        and failed.get("converged") is False
        and failed.get("result", {}).get("status") == "error"
        and isinstance(failed.get("error"), str),
        "partial failure accounting drifted",
        result,
    )


def test_fail_closed_bounds(client):
    for request in (
        {},
        {"cases": [valid_case("Only", 30.0)]},
        {"cases": ["A", "B"]},
        {"cases": [{}, {}]},
        {"cases": [valid_case("duplicate", 30.0), valid_case("duplicate", 80.0)]},
        {"cases": [{} for _ in range(33)]},
    ):
        assert_error(client.call_compare(request))
    assert_error(client.call_tool("compareProcesses", {"comparisonJson": "[]"}))
    long_name = valid_case("n" * 257, 30.0)
    assert_error(client.call_compare({"cases": [long_name, valid_case("B", 80.0)]}))
    assert_error(
        client.call_compare({"padding": "x" * 1048576, "cases": []})
    )


def test_inventory_promotion(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("compareProcesses", {})
    sources = record.get("contractEvidenceSources", [])
    require(
        inventory.get("inventoryVersion") == "1.39"
        and limitations.get("contractTestedToolCount") == 39
        and limitations.get("confirmedGapToolCount") == 12
        and limitations.get("contractPromotionCandidateCount") == 0,
        "inventory accounting drifted",
        inventory,
    )
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_BOUNDED_CANONICAL_PROCESS_COMPARISON_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 7
        and "src/main/java/neqsim/mcp/runners/ProcessRunner.java" in sources
        and "src/test/java/neqsim/mcp/runners/ProcessComparisonRunnerTest.java" in sources
        and "neqsim-mcp-server/test_process_comparison_protocol.py" in sources
        and "PROCESS_COMPARISON_CONTRACT.md" in " ".join(sources)
        and "partial-result visibility" in record.get("evidenceBoundary", "")
        and "case comparability" in record.get("evidenceBoundary", ""),
        "comparison evidence record drifted",
        record,
    )


def main():
    client = McpClient()
    client.start()
    try:
        scenarios = [
            test_discovery_and_schema,
            test_catalog_example,
            test_complete_canonical_comparison,
            test_repeated_order_is_stable,
            test_partial_failure_is_visible,
            test_fail_closed_bounds,
            test_inventory_promotion,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded process-comparison packaged-MCP contract (7 scenarios)")


if __name__ == "__main__":
    main()
