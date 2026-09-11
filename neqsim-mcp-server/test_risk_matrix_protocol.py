"""Packaged-MCP qualification for bounded risk-matrix screening.

The scenarios exercise the real STDIO transport, deterministic scoring, caller
input-basis evidence, request/event/text bounds, fail-closed malformed inputs,
and the explicit standards and engineering-review boundary. They do not
identify hazards, validate safeguards or acceptance criteria, establish
standards compliance, authorize plant action, or replace qualified review.
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
    for key in (
        "status",
        "tool",
        "code",
        "errors",
        "message",
        "validation",
        "qualityGate",
    ):
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
                        "name": "neqsim-risk-matrix-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": method,
                "params": params,
            }
        )
        return self.receive()

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

    def call_risk_matrix(self, request):
        response = self.request(
            "tools/call",
            {
                "name": "runRiskMatrix",
                "arguments": {"riskJson": json.dumps(request)},
            },
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP risk-matrix call returned no content", response)
        return json.loads(content[0].get("text", ""))

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


def assert_success(response):
    result = payload(response)
    require(result.get("status") == "success", "risk-matrix request failed", response)
    require(result.get("tool") == "runRiskMatrix", "tool identity drifted", response)
    require(
        result.get("validation", {}).get("valid") is True,
        "standard validation evidence drifted",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "software gate did not pass",
        response,
    )
    require(result.get("screeningOnly") is True, "screening boundary missing", result)
    require(
        result.get("standardConformanceClaimed") is False,
        "standards boundary missing",
        result,
    )
    require(
        result.get("standard")
        == "Generic 5x5 screening; project-specific verification required",
        "compatibility-safe standard descriptor drifted",
        result,
    )
    return result


def assert_error(response, expected_code):
    result = payload(response)
    require(result.get("status") == "error", "request did not fail closed", response)
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "risk-matrix error code drifted", result)
    require(result.get("screeningOnly") is True, "error boundary missing", result)
    return result


def test_discovery_boundary(client):
    tool = next(
        (item for item in client.list_tools() if item.get("name") == "runRiskMatrix"),
        None,
    )
    require(tool is not None, "runRiskMatrix is missing from tools/list")
    description = tool.get("description", "")
    require(
        "16384 UTF-8 bytes" in description
        and "100 events" in description
        and "does not identify hazards" in description
        and "qualified review" in description,
        "risk-matrix discovery boundary drifted",
        tool,
    )


def test_explicit_levels(client):
    result = assert_success(
        client.call_risk_matrix(
            {
                "events": [
                    {
                        "name": "Synthetic event",
                        "probabilityLevel": 4,
                        "consequenceLevel": 5,
                        "mitigation": "Review the hypothetical safeguard.",
                    }
                ]
            }
        )
    )
    event = result.get("events", [])[0]
    require(event.get("riskScore") == 20, "explicit score drifted", event)
    require(event.get("riskLevel") == "Critical", "explicit level drifted", event)
    require(event.get("inputBasis") == "CALLER_SUPPLIED_LEVELS", "input basis drifted", event)


def test_frequency_boundaries(client):
    result = assert_success(
        client.call_risk_matrix(
            {
                "events": [
                    {"failuresPerYear": 0, "productionLossPercent": 0},
                    {"failuresPerYear": 0.5, "productionLossPercent": 20},
                    {"failuresPerYear": 2, "productionLossPercent": 100},
                ]
            }
        )
    )
    events = result.get("events", [])
    require([item.get("name") for item in events] == ["Event 1", "Event 2", "Event 3"], "default names drifted", events)
    require([item.get("probabilityLevel") for item in events] == [1, 3, 5], "frequency thresholds drifted", events)
    require([item.get("consequenceLevel") for item in events] == [1, 3, 5], "loss thresholds drifted", events)
    require(
        all(item.get("inputBasis") == "CALLER_SUPPLIED_FREQUENCY_AND_PRODUCTION_LOSS" for item in events),
        "measured input basis drifted",
        events,
    )


def test_fail_closed_modes_and_ranges(client):
    assert_error(client.call_risk_matrix({"events": [{}]}), "INVALID_EVENT")
    assert_error(
        client.call_risk_matrix(
            {
                "events": [
                    {
                        "probabilityLevel": 2,
                        "consequenceLevel": 3,
                        "failuresPerYear": 0.1,
                        "productionLossPercent": 5,
                    }
                ]
            }
        ),
        "INVALID_EVENT",
    )
    assert_error(
        client.call_risk_matrix(
            {"events": [{"probabilityLevel": 1.5, "consequenceLevel": 3}]}
        ),
        "INVALID_EVENT",
    )
    assert_error(
        client.call_risk_matrix(
            {"events": [{"failuresPerYear": -0.1, "productionLossPercent": 5}]}
        ),
        "INVALID_EVENT",
    )
    assert_error(
        client.call_risk_matrix(
            {"events": [{"failuresPerYear": 0.1, "productionLossPercent": 101}]}
        ),
        "INVALID_EVENT",
    )


def test_collection_and_text_bounds(client):
    assert_error(client.call_risk_matrix({"events": []}), "INVALID_INPUT")
    event = {"probabilityLevel": 1, "consequenceLevel": 1}
    assert_error(client.call_risk_matrix({"events": [event] * 101}), "TOO_MANY_EVENTS")
    assert_error(
        client.call_risk_matrix(
            {"events": [{"name": "n" * 257, **event}]}
        ),
        "INVALID_EVENT",
    )
    assert_error(
        client.call_risk_matrix(
            {"events": [{"mitigation": "m" * 2049, **event}]}
        ),
        "INVALID_EVENT",
    )
    assert_error(
        client.call_risk_matrix(
            {"events": [{"name": "x" * 16400, **event}]}
        ),
        "REQUEST_TOO_LARGE",
    )


def test_deterministic_screening(client):
    request = {
        "events": [
            {
                "name": "Repeatable synthetic event",
                "probabilityLevel": 3,
                "consequenceLevel": 4,
            }
        ]
    }
    first = assert_success(client.call_risk_matrix(request))
    second = assert_success(client.call_risk_matrix(request))
    for result in (first, second):
        for key in ("validation", "qualityGate", "provenance"):
            result.pop(key, None)
    require(first == second, "risk-matrix response is not deterministic", {"first": first, "second": second})
    require(
        "caller supplies" in first.get("advisoryBoundary", ""),
        "advisory boundary drifted",
        first,
    )


def test_inventory_is_promoted(client):
    response = client.request("tools/call", {"name": "getCapabilities", "arguments": {}})
    content = response.get("result", {}).get("content", [])
    require(content, "getCapabilities returned no content", response)
    result = payload(json.loads(content[0].get("text", "")))
    require(result.get("status") == "success", "capabilities request failed", result)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runRiskMatrix", {})
    sources = record.get("contractEvidenceSources", [])
    require(
        inventory.get("inventoryVersion") == "1.37"
        and limitations.get("contractTestedToolCount") == 37
        and limitations.get("confirmedGapToolCount") == 14
        and limitations.get("contractPromotionCandidateCount") == 0,
        "risk-matrix promotion did not update inventory accounting",
        inventory,
    )
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_BOUNDED_GENERIC_RISK_SCREENING_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 7
        and "src/main/java/neqsim/process/safety/risk/RiskMatrix.java" in sources
        and "src/test/java/neqsim/mcp/runners/RiskMatrixRunnerTest.java" in sources
        and "neqsim-mcp-server/test_risk_matrix_protocol.py" in sources
        and "neqsim-mcp-server/docs/evidence/RISK_MATRIX_SCREENING_CONTRACT.md" in sources
        and "does not identify hazards" in record.get("evidenceBoundary", "")
        and "qualified safety-engineering review" in record.get("evidenceBoundary", ""),
        "risk-matrix contract evidence is incomplete",
        record,
    )


def main():
    client = McpClient()
    client.start()
    try:
        scenarios = [
            test_discovery_boundary,
            test_explicit_levels,
            test_frequency_boundaries,
            test_fail_closed_modes_and_ranges,
            test_collection_and_text_bounds,
            test_deterministic_screening,
            test_inventory_is_promoted,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded risk-matrix packaged-MCP contract (7 scenarios)")


if __name__ == "__main__":
    main()
