"""Packaged-MCP qualification for bounded barrier-register screening.

The scenarios exercise the real STDIO transport, catalog discovery, deterministic
traceability and exclusion accounting, admission bounds, standard response
evidence, and the explicit process-safety review boundary. They do not identify
hazards, validate source evidence or barrier independence/effectiveness, establish
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
    for key in ("status", "tool", "code", "errors", "message", "validation", "qualityGate", "provenance"):
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
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "neqsim-barrier-register-contract-test", "version": "1.0"},
            },
        })
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send({"jsonrpc": "2.0", "id": self.next_id(), "method": method, "params": params})
        return self.receive()

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

    def call_tool(self, name, arguments):
        response = self.request("tools/call", {"name": name, "arguments": arguments})
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_barriers(self, request):
        return self.call_tool("runBarrierRegister", {"barrierJson": json.dumps(request)})

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
    require(result.get("status") == "success", "barrier register request failed", response)
    require(result.get("tool") == "runBarrierRegister", "tool identity drifted", response)
    require(result.get("validation", {}).get("valid") is True, "standard validation evidence drifted", response)
    require(result.get("qualityGate", {}).get("verdict") == "passed", "software gate did not pass", response)
    require(result.get("screeningOnly") is True, "screening boundary missing", result)
    require(result.get("standardConformanceClaimed") is False, "standards boundary missing", result)
    return result


def assert_error(response, expected_code):
    result = payload(response)
    require(result.get("status") == "error", "request did not fail closed", response)
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "barrier-register error code drifted", result)
    require(result.get("screeningOnly") is True, "error boundary missing", result)
    require(result.get("standardConformanceClaimed") is False, "error standards boundary missing", result)


def valid_register():
    return {
        "register": {
            "registerId": "BR-001",
            "name": "Synthetic separator barrier register",
            "evidence": [{
                "evidenceId": "EV-1",
                "documentId": "SRS-1",
                "sourceReference": "Synthetic SRS section 1",
                "excerpt": "Synthetic evidence only.",
                "confidence": 0.9,
            }],
            "performanceStandards": [{
                "id": "PS-1",
                "title": "Synthetic shutdown standard",
                "targetPfd": 0.01,
                "acceptanceCriteria": ["Qualified review required"],
                "evidenceRefs": ["EV-1"],
            }],
            "barriers": [
                {
                    "id": "B-1",
                    "name": "Synthetic shutdown",
                    "type": "PREVENTION",
                    "status": "AVAILABLE",
                    "pfd": 0.01,
                    "performanceStandardId": "PS-1",
                    "equipmentTags": ["V-1"],
                    "hazardIds": ["H-1"],
                    "evidenceRefs": ["EV-1"],
                },
                {
                    "id": "B-2",
                    "name": "Impaired alarm",
                    "type": "PREVENTION",
                    "status": "IMPAIRED",
                    "pfd": 0.1,
                    "equipmentTags": ["V-1"],
                    "hazardIds": ["H-1"],
                },
            ],
            "safetyCriticalElements": [{
                "id": "SCE-1",
                "tag": "V-1",
                "name": "Synthetic protected vessel",
                "barrierRefs": ["B-1"],
                "equipmentTags": ["V-1"],
                "evidenceRefs": ["EV-1"],
            }],
        }
    }


def test_discovery_boundary(client):
    tool = next((item for item in client.list_tools() if item.get("name") == "runBarrierRegister"), None)
    require(tool is not None, "runBarrierRegister missing from tools/list")
    description = tool.get("description", "")
    require(
        "65536 UTF-8 bytes" in description
        and "100 items" in description
        and "does not identify hazards" in description
        and "qualified process-safety review" in description,
        "discovery boundary drifted",
        tool,
    )


def test_catalog_schema_and_example(client):
    schema = payload(client.call_tool("getSchema", {"toolName": "run_barrier_register", "schemaType": "input"}))
    require(schema.get("type") == "object", "barrier schema drifted", schema)
    example = payload(client.call_tool("getExample", {"category": "safety", "name": "barrier-register"}))
    assert_success(client.call_barriers(example))


def test_traceable_and_impaired_accounting(client):
    result = assert_success(client.call_barriers(valid_register()))
    summary = result.get("summary", {})
    require(summary.get("barrierCount") == 2 and summary.get("impairedBarrierCount") == 1, "summary drifted", result)
    require(len(result.get("lopaHandoff", {}).get("layers", [])) == 1, "qualified barrier was not handed off", result)
    require(len(result.get("lopaHandoff", {}).get("excludedBarriers", [])) == 1, "impaired exclusion missing", result)
    require(result.get("equipmentBarrierMap", {}).get("V-1", [])[0].get("id") == "B-1", "source order drifted", result)


def test_unqualified_barrier_is_not_credited(client):
    request = valid_register()
    request["register"]["barriers"][0].pop("evidenceRefs")
    result = payload(client.call_barriers(request))
    require(result.get("status") == "success", "advisory validation should preserve findings", result)
    require(len(result.get("lopaHandoff", {}).get("layers", [])) == 0, "untraceable barrier was credited", result)
    require(result.get("validation", {}).get("valid") is False, "missing evidence was not reported", result)


def test_fail_closed_types_and_numbers(client):
    for request in (
        [],
        {"register": "not-an-object"},
        {"barriers": "not-an-array"},
        {"barriers": [1]},
        {"barriers": [{"pfd": "NaN"}]},
        {"evidenceRefs": [1]},
    ):
        assert_error(client.call_barriers(request), "INVALID_INPUT")


def test_collection_text_and_request_bounds(client):
    assert_error(client.call_barriers({"barriers": [{"id": "B"}] * 101}), "INVALID_INPUT")
    assert_error(client.call_barriers({"name": "x" * 4097}), "INVALID_INPUT")
    assert_error(client.call_barriers({"padding": "x" * 65536}), "REQUEST_TOO_LARGE")


def test_deterministic_and_inventory_boundary(client):
    first = assert_success(client.call_barriers(valid_register()))
    second = assert_success(client.call_barriers(valid_register()))
    for result in (first, second):
        for key in ("validation", "qualityGate", "provenance"):
            result.pop(key, None)
    require(first == second, "barrier response is not deterministic", {"first": first, "second": second})

    capabilities = payload(client.call_tool("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runBarrierRegister", {})
    require(
        inventory.get("inventoryVersion") == "1.39"
        and limitations.get("contractTestedToolCount") == 39
        and limitations.get("confirmedGapToolCount") == 12
        and record.get("coverageStatus") == "CONFIRMED_GAP",
        "qualification changed inventory before merged promotion",
        inventory,
    )


def main():
    client = McpClient()
    client.start()
    try:
        scenarios = [
            test_discovery_boundary,
            test_catalog_schema_and_example,
            test_traceable_and_impaired_accounting,
            test_unqualified_barrier_is_not_credited,
            test_fail_closed_types_and_numbers,
            test_collection_text_and_request_bounds,
            test_deterministic_and_inventory_boundary,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded barrier-register packaged-MCP qualification (7 scenarios)")


if __name__ == "__main__":
    main()
