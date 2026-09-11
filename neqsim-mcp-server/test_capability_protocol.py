"""Focused packaged-MCP qualification for bounded runtime capabilities.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies discovery routing, exact static invocation, policy rejection,
invalid input handling, inventory promotion, standard envelopes, and real transport. It does not
establish scientific validity, arbitrary classpath completeness, sandboxing,
resource or tenant isolation, external IAM, transport security, plant
authority, certification, or engineering approval.
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
                        "name": "neqsim-runtime-capability-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require("result" in response, "MCP initialize did not return a result", response)
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_capability(self, request):
        return self.call_tool(
            "runCapability", {"capabilityJson": json.dumps(request)}
        )

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
        "message",
        "provenance",
        "validation",
        "qualityGate",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def assert_success(response):
    """Require a successful standard runCapability envelope."""
    result = payload(response)
    require(result.get("status") == "success", "capability request failed", response)
    require(
        result.get("tool") == "runCapability", "tool identity drifted", response
    )
    require(
        result.get("validation", {}).get("valid") is True,
        "capability response lacks valid standard evidence",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "capability response did not pass its software gate",
        response,
    )
    return result


def assert_error(response, expected_code):
    """Require invalid or unsafe capability input to fail closed."""
    result = payload(response)
    require(
        result.get("status") == "error",
        "capability request did not fail closed",
        response,
    )
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "capability error code drifted", result)
    return result


def test_static_search_route(client):
    result = assert_success(
        client.call_capability(
            {"action": "search", "query": "sulfur vapour pressure", "limit": 25}
        )
    )
    matches = result.get("matches", [])
    target = [
        match
        for match in matches
        if match.get("classSimpleName") == "SulfurThermodynamics"
        and match.get("methodName") == "calculateVapourPressureBar"
    ]
    require(target, "static sulfur capability was not discovered", result)
    require(
        target[0].get("executionMode") == "static-json"
        and target[0].get("route") == "runCapability action=invoke",
        "static capability routing drifted",
        target[0],
    )
    require(
        target[0].get("sourcePath", "").startswith("src/main/java/neqsim/"),
        "source metadata drifted",
        target[0],
    )


def test_stateful_search_route(client):
    result = assert_success(
        client.call_capability(
            {"action": "search", "query": "sulfur solubility", "limit": 50}
        )
    )
    matches = result.get("matches", [])
    target = [
        match
        for match in matches
        if match.get("classSimpleName") == "SulfurDepositionAnalyser"
    ]
    require(target, "stateful sulfur capability was not discovered", result)
    require(
        any(match.get("executionMode") == "process-json" for match in target),
        "stateful capability did not retain the process route",
        target,
    )


def test_exact_static_invocation(client):
    result = assert_success(
        client.call_capability(
            {
                "action": "invoke",
                "className": "neqsim.thermo.util.sulfur.SulfurThermodynamics",
                "methodName": "calculateVapourPressureBar",
                "parameterTypes": ["double"],
                "arguments": [717.76],
            }
        )
    )
    require(result.get("executionMode") == "static-json", "execution mode drifted", result)
    require(
        abs(result.get("result") - 1.01325) < 1.0e-10,
        "static calculation serialization drifted",
        result,
    )
    require(
        result.get("provenance", {}).get("safetyPolicy") == "bounded-static-json-v1",
        "bounded invocation provenance drifted",
        result,
    )


def test_external_class_fails_closed(client):
    assert_error(
        client.call_capability(
            {
                "action": "invoke",
                "className": "java.lang.Runtime",
                "methodName": "getRuntime",
                "arguments": [],
            }
        ),
        "CLASS_NOT_ALLOWED",
    )


def test_instance_method_fails_closed(client):
    assert_error(
        client.call_capability(
            {
                "action": "invoke",
                "className": (
                    "neqsim.process.equipment.reactor.SulfurDepositionAnalyser"
                ),
                "methodName": "getSulfurSolubilityMgSm3",
                "arguments": [],
            }
        ),
        "METHOD_NOT_EXECUTABLE",
    )


def test_unknown_action_fails_closed(client):
    assert_error(client.call_capability({"action": "install"}), "UNKNOWN_ACTION")


def test_malformed_input_fails_closed(client):
    assert_error(
        client.call_tool("runCapability", {"capabilityJson": "{"}),
        "INPUT_ERROR",
    )


def test_inventory_promotion(client):
    response = client.call_tool("getCapabilities", {})
    result = payload(response)
    require(result.get("status") == "success", "capability request failed", response)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runCapability", {})
    require(inventory.get("inventoryVersion") == "1.36", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 36
        and limitations.get("confirmedGapToolCount") == 15,
        "runtime-capability promotion accounting drifted",
        limitations,
    )
    require(
        limitations.get("contractPromotionCandidateCount") == 0,
        "promotion candidate remained queued",
        limitations,
    )
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED",
        "runCapability was not promoted",
        record,
    )
    require(
        record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_BOUNDED_RUNTIME_CAPABILITY_EXECUTION",
        "runtime-capability applicability drifted",
        record,
    )
    require(
        "neqsim-mcp-server/test_capability_protocol.py"
        in record.get("contractEvidenceSources", [])
        and "scientific validity" in record.get("evidenceBoundary", "")
        and "operating-system or process sandbox"
        in record.get("evidenceBoundary", ""),
        "runtime-capability evidence or boundary drifted",
        record,
    )


def main():
    client = McpClient()
    tests = [
        ("static search route", test_static_search_route),
        ("stateful search route", test_stateful_search_route),
        ("exact static invocation", test_exact_static_invocation),
        ("external class fails closed", test_external_class_fails_closed),
        ("instance method fails closed", test_instance_method_fails_closed),
        ("unknown action fails closed", test_unknown_action_fails_closed),
        ("malformed input fails closed", test_malformed_input_fails_closed),
        ("inventory promotion", test_inventory_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} runtime-capability scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
