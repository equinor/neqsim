"""Packaged-MCP qualification for bounded operational-study orchestration.

The scenarios exercise the real STDIO transport, discovery, schema and advisory
boundaries, deterministic controller screening, canonical local-model scenario
execution, request admission, fail-closed errors, standard response evidence,
and unchanged Phase 0 accounting. They do not establish causal diagnosis,
controller or safety adequacy, model fidelity, plant authority, or approval.
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
        "provenance",
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
                        "name": "neqsim-operational-study-contract-test",
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

    def call_tool(self, name, arguments):
        response = self.request(
            "tools/call",
            {"name": name, "arguments": arguments},
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_operational(self, request):
        argument = request if isinstance(request, str) else json.dumps(request)
        return self.call_tool(
            "runOperationalStudy",
            {"operationalJson": argument},
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


def assert_success(response):
    result = payload(response)
    require(result.get("status") == "success", "operational-study request failed", response)
    require(result.get("tool") == "runOperationalStudy", "tool identity drifted", response)
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
        result.get("plantWritePerformed") is False,
        "no-plant-write boundary missing",
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
    require(code == expected_code, "operational-study error code drifted", result)
    require(result.get("screeningOnly") is True, "error boundary missing", result)
    require(result.get("plantWritePerformed") is False, "error write boundary missing", result)


def process_definition():
    return {
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 70.0,
            "mixingRule": "classic",
            "components": {"methane": 0.90, "ethane": 0.10},
        },
        "process": [
            {
                "type": "Stream",
                "name": "feed",
                "properties": {"flowRate": [10000.0, "kg/hr"]},
            },
            {"type": "Separator", "name": "Separator", "inlet": "feed"},
            {
                "type": "valve",
                "name": "Outlet Valve",
                "inlet": "Separator.gasOut",
                "properties": {"outletPressure": 50.0},
            },
        ],
    }


def test_discovery_boundary(client):
    tool = next(
        (item for item in client.list_tools() if item.get("name") == "runOperationalStudy"),
        None,
    )
    require(tool is not None, "runOperationalStudy missing from tools/list")
    description = tool.get("description", "")
    require(
        "1048576 UTF-8 bytes" in description
        and "local simulation copy" in description
        and "does not write to plant systems" in description
        and "does not establish causality" in description
        and "qualified engineering review" in description,
        "discovery boundary drifted",
        tool,
    )


def test_schema_and_advisory_boundary(client):
    result = assert_success(client.call_operational({"action": "getSchema"}))
    require(result.get("maxRequestBytes") == 1048576, "request bound drifted", result)
    require(
        "runScenario" in result.get("actions", [])
        and "evaluateControllerResponse" in result.get("actions", [])
        and "evaluateOperatingEnvelope" in result.get("actions", []),
        "operational action catalog drifted",
        result,
    )
    require(
        "do not write to plant systems" in result.get("advisoryBoundary", ""),
        "advisory boundary drifted",
        result,
    )


def test_controller_screen_is_deterministic(client):
    request = {
        "action": "evaluateControllerResponse",
        "controllerName": "LC-001",
        "setPoint": 1.0,
        "timeSeconds": [0.0, 10.0, 20.0, 30.0, 40.0, 50.0],
        "processValue": [0.0, 0.55, 0.82, 0.95, 0.99, 1.0],
        "controllerOutput": [40.0, 55.0, 58.0, 53.0, 50.0, 50.0],
        "outputMin": 0.0,
        "outputMax": 100.0,
        "settlingTolerance": 0.05,
    }
    first = assert_success(client.call_operational(request))
    second = assert_success(client.call_operational(request))
    require(
        first.get("controllerTuning", {}).get("recommendation")
        == "ACCEPTABLE_SCREENING_RESULT",
        "controller recommendation drifted",
        first,
    )
    for result in (first, second):
        for key in ("validation", "qualityGate", "provenance"):
            result.pop(key, None)
    require(first == second, "controller screen is not deterministic", {"first": first, "second": second})


def test_canonical_local_scenario(client):
    request = {
        "action": "runScenario",
        "scenarioName": "partly close outlet",
        "processJson": process_definition(),
        "actions": [
            {
                "type": "SET_VALVE_OPENING",
                "target": "Outlet Valve",
                "value": 25.0,
            },
            {"type": "RUN_STEADY_STATE"},
        ],
    }
    result = assert_success(client.call_operational(request))
    require(result.get("successful") is True, "scenario did not complete", result)
    after = result.get("scenarioResult", {}).get("afterValues", {})
    require(
        after.get("Outlet Valve.percentValveOpening") == 25.0,
        "canonical valve action drifted",
        result,
    )
    require("processReport" in result, "canonical process report missing", result)


def test_fail_closed_inputs(client):
    assert_error(client.call_operational("{not-json"), "JSON_PARSE_ERROR")
    assert_error(client.call_operational({"action": "notAnAction"}), "UNKNOWN_ACTION")
    oversized = {"action": "getSchema", "padding": "x" * 1048577}
    assert_error(client.call_operational(oversized), "REQUEST_TOO_LARGE")


def test_inventory_remains_qualification_only(client):
    capabilities = payload(client.call_tool("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runOperationalStudy", {})
    require(
        inventory.get("inventoryVersion") == "1.40"
        and limitations.get("contractTestedToolCount") == 40
        and limitations.get("confirmedGapToolCount") == 11
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
            test_schema_and_advisory_boundary,
            test_controller_screen_is_deterministic,
            test_canonical_local_scenario,
            test_fail_closed_inputs,
            test_inventory_remains_qualification_only,
        ]
        for scenario in scenarios:
            scenario(client)
    finally:
        client.close()
    print("PASS: bounded operational-study packaged-MCP qualification (6 scenarios)")


if __name__ == "__main__":
    main()
