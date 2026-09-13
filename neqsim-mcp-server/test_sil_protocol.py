"""Packaged-MCP qualification for bounded caller-supplied SIF PFD screening.

The scenarios exercise the real STDIO transport, canonical NeqSim SIL-band
calculation, deterministic ordering, request/component/text bounds, stable
fail-closed errors, and the explicit independent-assessment boundary. They do
not select or approve SIL, establish standards conformance, authorize plant
action, or replace functional-safety assessment and accountable approval.
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
    for key in ("status", "tool", "code", "errors", "message", "validation", "qualityGate"):
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
                    "clientInfo": {"name": "neqsim-sil-screening-contract-test", "version": "1.0"},
                },
            }
        )
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send({"jsonrpc": "2.0", "id": self.next_id(), "method": method, "params": params})
        return self.receive()

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

    def call_sil(self, request):
        return self.call_raw_sil(json.dumps(request))

    def call_raw_sil(self, request_text):
        response = self.request(
            "tools/call", {"name": "runSIL", "arguments": {"silJson": request_text}}
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP SIL call returned no content", response)
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
    require(result.get("status") == "success", "SIL request failed", response)
    require(result.get("tool") == "runSIL", "tool identity drifted", response)
    require(result.get("validation", {}).get("valid") is True, "validation evidence drifted", response)
    require(result.get("qualityGate", {}).get("verdict") == "passed", "quality gate failed", response)
    require(result.get("screeningOnly") is True, "screening boundary missing", result)
    require(result.get("standardConformanceClaimed") is False, "standards boundary missing", result)
    require(
        result.get("standard")
        == "Caller-supplied SIF PFD screening; independent functional-safety verification required",
        "standard descriptor drifted",
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
    require(code == expected_code, "SIL error code drifted", result)
    require(result.get("screeningOnly") is True, "error boundary missing", result)
    require(result.get("standardConformanceClaimed") is False, "error standards boundary missing", result)
    return result


def valid_request():
    return {
        "name": "Synthetic high-pressure shutdown",
        "description": "Caller-defined SIF screening case",
        "claimedSIL": 2,
        "architecture": "1oo1",
        "proofTestInterval_hours": 8760,
        "components": [
            {"name": "PT", "type": "sensor", "pfd": 0.001},
            {"name": "Logic", "type": "logic", "pfd": 0.0005},
            {"name": "Valve", "type": "finalElement", "pfd": 0.005},
        ],
    }


def test_discovery_boundary(client):
    tool = next((item for item in client.list_tools() if item.get("name") == "runSIL"), None)
    require(tool is not None, "runSIL is missing from tools/list")
    description = tool.get("description", "")
    require(
        "16384 UTF-8 bytes" in description
        and "100 components" in description
        and "does not select or approve SIL" in description
        and "independent functional-safety assessment" in description,
        "SIL discovery boundary drifted",
        tool,
    )


def test_canonical_direct_pfd_band(client):
    result = assert_success(client.call_sil({"name": "SIF-direct", "claimedSIL": 2, "pfdAvg": 0.005}))
    screening = result.get("screening", {})
    require(result.get("inputBasis") == "CALLER_SUPPLIED_DIRECT_PFD_AVG", "input basis drifted", result)
    require(screening.get("achievedSILBand") == 2, "canonical SIL band drifted", screening)
    require(screening.get("claimedBandMetByPfd") is True, "claimed-band comparison drifted", screening)
    require(screening.get("silBandIsIndicative") is True, "indicative label missing", screening)
    require(screening.get("architectureSuitabilityVerified") is False, "architecture claim drifted", screening)


def test_canonical_component_calculation_and_order(client):
    first = assert_success(client.call_sil(valid_request()))
    second = assert_success(client.call_sil(valid_request()))
    require(first.get("screening", {}).get("pfdAvg") == 0.0065, "component sum drifted", first)
    require(
        [component.get("name") for component in first.get("components", [])]
        == ["PT", "Logic", "Valve"],
        "component order drifted",
        first,
    )
    for result in (first, second):
        for key in ("validation", "qualityGate", "provenance"):
            result.pop(key, None)
    require(first == second, "SIL response is not deterministic", {"first": first, "second": second})


def test_explicit_lifecycle_and_advisory_boundary(client):
    result = assert_success(client.call_sil({"pfdAvg": 0.01}))
    boundary = result.get("advisoryBoundary", "")
    require(len(result.get("assumptions", [])) == 3, "assumption evidence drifted", result)
    require(
        "does not select or approve SIL" in boundary
        and "proof-test effectiveness" in boundary
        and "independent functional-safety assessment" in boundary
        and "does not demonstrate conformance" in result.get("standardContext", ""),
        "advisory boundary drifted",
        result,
    )


def test_fail_closed_request_and_top_level_inputs(client):
    assert_error(client.call_raw_sil("[]"), "INVALID_INPUT")
    malformed = assert_error(client.call_raw_sil("{not-json"), "INVALID_INPUT")
    require("line" not in malformed.get("message", ""), "parser detail leaked", malformed)
    assert_error(client.call_sil({}), "INVALID_INPUT")
    assert_error(client.call_sil({"pfdAvg": 0.01, "components": []}), "INVALID_INPUT")
    for request in (
        {"pfdAvg": 0},
        {"pfdAvg": 1.01},
        {"pfdAvg": "low"},
        {"pfdAvg": 0.01, "claimedSIL": 2.5},
        {"pfdAvg": 0.01, "architecture": "2oo2"},
        {"pfdAvg": 0.01, "proofTestInterval_hours": 87601},
    ):
        assert_error(client.call_sil(request), "INVALID_INPUT")


def test_fail_closed_component_contract(client):
    invalid_components = (
        [],
        ["component"],
        [{"type": "sensor", "pfd": 0.01}],
        [{"name": "PT", "type": "other", "pfd": 0.01}],
        [{"name": "PT", "type": "sensor"}],
        [{"name": "PT", "type": "sensor", "pfd": 0.01, "lambdaDU_per_hr": 1e-7}],
        [{"name": "PT", "type": "sensor", "pfd": 0}],
        [{"name": "PT", "type": "sensor", "lambdaDU_per_hr": 1.01}],
    )
    for components in invalid_components:
        expected = "INVALID_INPUT" if components == [] else "INVALID_COMPONENT"
        assert_error(client.call_sil({"components": components}), expected)


def test_collection_text_request_and_calculation_bounds(client):
    request = valid_request()
    request["components"] = [
        {"name": "C" + str(index), "type": "sensor", "pfd": 0.001}
        for index in range(101)
    ]
    assert_error(client.call_sil(request), "TOO_MANY_COMPONENTS")
    request = valid_request()
    request["components"][0]["name"] = "n" * 257
    assert_error(client.call_sil(request), "INVALID_COMPONENT")
    request = valid_request()
    request["name"] = "x" * 16400
    assert_error(client.call_sil(request), "REQUEST_TOO_LARGE")
    request = valid_request()
    request["components"] = [
        {"name": "A", "type": "sensor", "pfd": 0.6},
        {"name": "B", "type": "logic", "pfd": 0.5},
    ]
    assert_error(client.call_sil(request), "CALCULATION_OUT_OF_RANGE")


def test_inventory_promotes_contract_atomically(client):
    response = client.request("tools/call", {"name": "getCapabilities", "arguments": {}})
    content = response.get("result", {}).get("content", [])
    require(content, "getCapabilities returned no content", response)
    result = payload(json.loads(content[0].get("text", "")))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runSIL", {})
    require(
        inventory.get("inventoryVersion") == "1.39"
        and limitations.get("contractTestedToolCount") == 39
        and limitations.get("confirmedGapToolCount") == 12
        and record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_BOUNDED_SIF_PFD_SCREENING_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 8
        and "src/main/java/neqsim/process/safety/risk/sis/SafetyInstrumentedFunction.java"
        in record.get("contractEvidenceSources", [])
        and "src/test/java/neqsim/mcp/runners/SILRunnerTest.java"
        in record.get("contractEvidenceSources", [])
        and "neqsim-mcp-server/test_sil_protocol.py"
        in record.get("contractEvidenceSources", [])
        and "neqsim-mcp-server/docs/evidence/SIL_SCREENING_CONTRACT.md"
        in record.get("contractEvidenceSources", [])
        and "does not establish SRS completeness" in record.get("evidenceBoundary", "")
        and "independent functional-safety assessment" in record.get("evidenceBoundary", ""),
        "SIL promotion did not move inventory and evidence atomically",
        inventory,
    )


def main():
    client = McpClient()
    client.start()
    try:
        scenarios = [
            test_discovery_boundary,
            test_canonical_direct_pfd_band,
            test_canonical_component_calculation_and_order,
            test_explicit_lifecycle_and_advisory_boundary,
            test_fail_closed_request_and_top_level_inputs,
            test_fail_closed_component_contract,
            test_collection_text_request_and_calculation_bounds,
            test_inventory_promotes_contract_atomically,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded SIL packaged-MCP contract (8 scenarios)")


if __name__ == "__main__":
    main()
