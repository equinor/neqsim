"""Focused packaged-MCP qualification for composed workflows.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies one real shared-fluid calculation, explicit accounting,
stop-on-first-failure behavior, invalid input handling, unchanged inventory,
standard envelopes, and real transport. It does not establish arbitrary
execution, semantic compatibility between steps, transactionality,
persistence, numerical fidelity, convergence, conservation, facility
completeness, plant authority, certification, or engineering approval.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC client."""

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
                        "name": "neqsim-compose-workflow-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require("result" in response, "MCP initialize did not return a result", response)
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

    def call_workflow(self, request):
        return self.call_tool(
            "composeWorkflow", {"workflowJson": json.dumps(request)}
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


def assert_standard_success(response):
    """Require a successful standard composeWorkflow envelope."""
    result = payload(response)
    require(result.get("status") == "success", "workflow request failed", response)
    require(result.get("tool") == "composeWorkflow", "tool identity drifted", response)
    require(
        result.get("validation", {}).get("valid") is True,
        "workflow response lacks valid standard evidence",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "workflow response did not pass its software gate",
        response,
    )
    return result


def assert_error(response, expected_code):
    """Require invalid workflow input to fail closed."""
    result = payload(response)
    require(result.get("status") == "error", "workflow did not fail closed", response)
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "workflow error code drifted", result)
    return result


def test_shared_fluid_flash(client):
    result = assert_standard_success(
        client.call_workflow(
            {
                "workflow": "bounded-flash",
                "fluid": {
                    "model": "SRK",
                    "components": {"methane": 1.0},
                },
                "steps": [
                    {
                        "runner": "flash",
                        "name": "feed_flash",
                        "input": {
                            "temperature": {"value": 25.0, "unit": "C"},
                            "pressure": {"value": 50.0, "unit": "bara"},
                        },
                    }
                ],
            }
        )
    )
    require(result.get("workflow") == "bounded-flash", "workflow identity drifted", result)
    require(
        result.get("totalSteps") == 1
        and result.get("completedSteps") == 1
        and result.get("success") is True,
        "workflow accounting drifted",
        result,
    )
    step = result.get("steps", [{}])[0]
    require(
        step.get("step") == "feed_flash"
        and step.get("runner") == "flash"
        and step.get("success") is True,
        "flash step accounting drifted",
        step,
    )
    require(
        step.get("output", {}).get("status") == "success",
        "shared fluid did not reach the flash runner",
        step,
    )
    combined = result.get("combinedData", {})
    require(
        combined.get("fluid", {}).get("components", {}).get("methane") == 1.0
        and "feed_flash_result" in combined,
        "shared fluid or result evidence was not preserved",
        combined,
    )


def test_unknown_runner_stops(client):
    result = assert_standard_success(
        client.call_workflow(
            {
                "workflow": "fail-closed",
                "steps": [
                    {"runner": "arbitrary", "name": "blocked", "input": {}},
                    {"runner": "flash", "name": "skipped", "input": {}},
                ],
            }
        )
    )
    require(
        result.get("totalSteps") == 2
        and result.get("completedSteps") == 1
        and result.get("success") is False,
        "workflow did not stop on the first failed step",
        result,
    )
    steps = result.get("steps", [])
    require(len(steps) == 1 and steps[0].get("success") is False, "failed step drifted", result)
    output = steps[0].get("output", {})
    errors = output.get("errors", [])
    require(
        output.get("status") == "error"
        and errors
        and errors[0].get("code") == "UNKNOWN_RUNNER",
        "unknown runner diagnostic drifted",
        output,
    )
    require(
        "skipped_result" not in result.get("combinedData", {}),
        "a step after the failure was executed",
        result,
    )


def test_missing_steps_fails_closed(client):
    assert_error(
        client.call_workflow({"workflow": "missing-steps"}),
        "MISSING_STEPS",
    )


def test_malformed_step_fails_closed(client):
    assert_error(
        client.call_workflow(
            {
                "workflow": "malformed-step",
                "steps": [
                    {
                        "runner": "flash",
                        "name": "malformed",
                        "input": "not-an-object",
                    }
                ],
            }
        ),
        "WORKFLOW_ERROR",
    )


def test_inventory_remains_candidate(client):
    result = payload(client.call_tool("getCapabilities", {}))
    require(result.get("status") == "success", "capabilities request failed", result)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("composeWorkflow", {})
    require(
        inventory.get("inventoryVersion") == "1.31"
        and limitations.get("contractTestedToolCount") == 31
        and limitations.get("confirmedGapToolCount") == 20,
        "qualification changed inventory accounting",
        inventory,
    )
    require(
        record.get("coverageStatus") == "CONFIRMED_GAP",
        "composeWorkflow was promoted before evidence merged",
        record,
    )
    require(
        limitations.get("contractPromotionCandidateCount") == 0,
        "qualification queued a promotion inside inventory",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("shared-fluid flash", test_shared_fluid_flash),
        ("unknown runner stops", test_unknown_runner_stops),
        ("missing steps fail closed", test_missing_steps_fails_closed),
        ("malformed step fails closed", test_malformed_step_fails_closed),
        ("inventory remains candidate", test_inventory_remains_candidate),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} composed-workflow scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
