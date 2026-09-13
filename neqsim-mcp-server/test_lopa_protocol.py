"""Packaged-MCP qualification for bounded caller-supplied LOPA screening.

The scenarios exercise the real STDIO transport, canonical NeqSim LOPA
calculation, deterministic ordering, request/layer/text bounds, stable
fail-closed errors, and the explicit engineering-review boundary. They do not
identify hazards, verify IPL independence or SIL, determine risk acceptance,
establish standards compliance, authorize plant action, or replace qualified
process-safety review.
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
                        "name": "neqsim-lopa-screening-contract-test",
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

    def call_lopa(self, request):
        response = self.request(
            "tools/call",
            {
                "name": "runLOPA",
                "arguments": {"lopaJson": json.dumps(request)},
            },
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP LOPA call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_raw_lopa(self, request_text):
        response = self.request(
            "tools/call",
            {"name": "runLOPA", "arguments": {"lopaJson": request_text}},
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP LOPA call returned no content", response)
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
    require(result.get("status") == "success", "LOPA request failed", response)
    require(result.get("tool") == "runLOPA", "tool identity drifted", response)
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
        == "Caller-supplied LOPA screening; project-specific verification required",
        "standard descriptor drifted",
        result,
    )
    require(
        result.get("inputBasis") == "CALLER_SUPPLIED_FREQUENCIES_AND_LAYER_PFDS",
        "input basis drifted",
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
    require(code == expected_code, "LOPA error code drifted", result)
    require(result.get("screeningOnly") is True, "error boundary missing", result)
    require(
        result.get("standardConformanceClaimed") is False,
        "error standards boundary missing",
        result,
    )
    return result


def valid_request():
    return {
        "scenario": "Synthetic separator overpressure",
        "initiatingEventFrequency_per_year": 0.1,
        "targetFrequency_per_year": 1.1e-4,
        "layers": [
            {"name": "Caller-supplied BPCS claim", "pfd": 0.1},
            {"name": "Caller-supplied relief claim", "pfd": 0.01},
        ],
    }


def test_discovery_boundary(client):
    tool = next((item for item in client.list_tools() if item.get("name") == "runLOPA"), None)
    require(tool is not None, "runLOPA is missing from tools/list")
    description = tool.get("description", "")
    require(
        "16384 UTF-8 bytes" in description
        and "100 layers" in description
        and "does not identify hazards" in description
        and "qualified process-safety review" in description,
        "LOPA discovery boundary drifted",
        tool,
    )


def test_canonical_target_met_calculation(client):
    result = assert_success(client.call_lopa(valid_request()))
    lopa = result.get("lopa", {})
    gap = result.get("gapAnalysis", {})
    require(abs(lopa.get("initiatingEventFrequency", 0) - 0.1) < 1e-15, "initiating frequency drifted", lopa)
    require(abs(lopa.get("mitigatedFrequency", 0) - 0.0001) < 1e-15, "canonical product drifted", lopa)
    require(abs(lopa.get("totalRRF", 0) - 1000.0) < 1e-12, "canonical RRF drifted", lopa)
    require(gap.get("targetMet") is True, "target classification drifted", gap)
    require(
        [layer.get("name") for layer in lopa.get("protectionLayers", [])]
        == ["Caller-supplied BPCS claim", "Caller-supplied relief claim"],
        "caller layer order drifted",
        lopa,
    )


def test_canonical_gap_and_indicative_sil(client):
    request = valid_request()
    request["initiatingEventFrequency_per_year"] = 0.5
    request["targetFrequency_per_year"] = 1.0e-5
    request["layers"] = [{"name": "Caller-supplied BPCS claim", "pfd": 0.1}]
    gap = assert_success(client.call_lopa(request)).get("gapAnalysis", {})
    require(gap.get("targetMet") is False, "gap classification drifted", gap)
    require(gap.get("requiredAdditionalRRF") == 5000.0, "required RRF drifted", gap)
    require(gap.get("requiredAdditionalSIL") == 3, "canonical SIL band drifted", gap)
    require(gap.get("requiredAdditionalSILIsIndicative") is True, "indicative SIL label missing", gap)
    require(gap.get("requiredAdditionalPFD") == 0.0002, "required PFD drifted", gap)


def test_fail_closed_request_and_frequency_inputs(client):
    assert_error(client.call_raw_lopa("[]"), "INVALID_INPUT")
    malformed = assert_error(client.call_raw_lopa("{not-json"), "INVALID_INPUT")
    require("line" not in malformed.get("message", ""), "parser detail leaked", malformed)
    request = valid_request()
    del request["targetFrequency_per_year"]
    assert_error(client.call_lopa(request), "INVALID_INPUT")
    for field, value in (
        ("initiatingEventFrequency_per_year", 0),
        ("initiatingEventFrequency_per_year", -0.1),
        ("targetFrequency_per_year", 0),
        ("targetFrequency_per_year", "rare"),
    ):
        request = valid_request()
        request[field] = value
        assert_error(client.call_lopa(request), "INVALID_INPUT")
    assert_error(
        client.call_raw_lopa(
            '{"initiatingEventFrequency_per_year":NaN,"targetFrequency_per_year":1e-5,'
            '"layers":[{"name":"Layer","pfd":0.1}]}'
        ),
        "INVALID_INPUT",
    )


def test_fail_closed_layer_contract(client):
    invalid_layers = (
        [],
        ["not-an-object"],
        [{"pfd": 0.1}],
        [{"name": " ", "pfd": 0.1}],
        [{"name": "Layer", "pfd": 0}],
        [{"name": "Layer", "pfd": 1.01}],
        [{"name": "Layer", "pfd": "low"}],
    )
    for layers in invalid_layers:
        request = valid_request()
        request["layers"] = layers
        expected = "INVALID_INPUT" if layers == [] else "INVALID_LAYER"
        assert_error(client.call_lopa(request), expected)


def test_collection_text_and_request_bounds(client):
    request = valid_request()
    request["layers"] = [{"name": "L" + str(i), "pfd": 1.0} for i in range(101)]
    assert_error(client.call_lopa(request), "TOO_MANY_LAYERS")
    request = valid_request()
    request["scenario"] = "s" * 257
    assert_error(client.call_lopa(request), "INVALID_INPUT")
    request = valid_request()
    request["layers"] = [{"name": "n" * 257, "pfd": 0.1}]
    assert_error(client.call_lopa(request), "INVALID_LAYER")
    request["layers"] = [{"name": "x" * 16400, "pfd": 0.1}]
    assert_error(client.call_lopa(request), "REQUEST_TOO_LARGE")


def test_deterministic_screening_and_advisory_evidence(client):
    first = assert_success(client.call_lopa(valid_request()))
    second = assert_success(client.call_lopa(valid_request()))
    for result in (first, second):
        for key in ("validation", "qualityGate", "provenance"):
            result.pop(key, None)
    require(first == second, "LOPA response is not deterministic", {"first": first, "second": second})
    require(len(first.get("assumptions", [])) == 3, "assumption evidence drifted", first)
    boundary = first.get("advisoryBoundary", "")
    require(
        "claimed IPL independence" in boundary
        and "verify SIL" in boundary
        and "authorize plant action" in boundary,
        "advisory boundary drifted",
        first,
    )


def test_inventory_is_promoted(client):
    response = client.request("tools/call", {"name": "getCapabilities", "arguments": {}})
    content = response.get("result", {}).get("content", [])
    require(content, "getCapabilities returned no content", response)
    result = payload(json.loads(content[0].get("text", "")))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runLOPA", {})
    require(
        inventory.get("inventoryVersion") == "1.39"
        and limitations.get("contractTestedToolCount") == 39
        and limitations.get("confirmedGapToolCount") == 12
        and limitations.get("contractPromotionCandidateCount") == 0,
        "LOPA promotion did not update inventory accounting",
        inventory,
    )
    sources = record.get("contractEvidenceSources", [])
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_BOUNDED_LOPA_SCREENING_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 7
        and "src/main/java/neqsim/process/safety/risk/sis/SafetyInstrumentedFunction.java" in sources
        and "src/test/java/neqsim/mcp/runners/LOPARunnerTest.java" in sources
        and "neqsim-mcp-server/test_lopa_protocol.py" in sources
        and "neqsim-mcp-server/docs/evidence/LOPA_SCREENING_CONTRACT.md" in sources
        and "does not identify hazards" in record.get("evidenceBoundary", "")
        and "qualified process-safety review" in record.get("evidenceBoundary", ""),
        "runLOPA contract evidence drifted",
        record,
    )


def main():
    client = McpClient()
    client.start()
    try:
        scenarios = [
            test_discovery_boundary,
            test_canonical_target_met_calculation,
            test_canonical_gap_and_indicative_sil,
            test_fail_closed_request_and_frequency_inputs,
            test_fail_closed_layer_contract,
            test_collection_text_and_request_bounds,
            test_deterministic_screening_and_advisory_evidence,
            test_inventory_is_promoted,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded LOPA packaged-MCP contract (8 scenarios)")


if __name__ == "__main__":
    main()
