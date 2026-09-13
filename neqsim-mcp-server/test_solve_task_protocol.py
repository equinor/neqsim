"""Packaged-MCP qualification for the bounded solveTask contract.

The scenarios exercise real STDIO transport, fixed-plan discovery, one
shared-fluid PVT calculation, explicit accounting, fail-closed inputs,
required-step stop behavior, and promoted Phase 0 inventory. They do not
establish general language planning, arbitrary execution, semantic result
chaining, numerical fidelity, convergence, plant authority, certification, or
engineering approval.
"""
import json
import subprocess
import sys
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
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "neqsim-task-solver-contract-test", "version": "1.0"},
            },
        })
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": method,
            "params": params,
        })
        return self.receive()

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

    def call_tool(self, name, arguments):
        response = self.request("tools/call", {"name": name, "arguments": arguments})
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        text = content[0].get("text", "")
        try:
            return json.loads(text)
        except json.JSONDecodeError as error:
            raise AssertionError(f"MCP tool returned non-JSON content: {error}: {text}")

    def call_task(self, request):
        return self.call_tool("solveTask", {"taskJson": json.dumps(request)})

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
    require(result.get("status") == "success", "task request failed", response)
    require(result.get("tool") == "solveTask", "tool identity drifted", response)
    require(result.get("validation", {}).get("valid") is True,
            "standard validation evidence drifted", response)
    require(result.get("qualityGate", {}).get("verdict") == "passed",
            "software gate did not pass", response)
    return result


def assert_error(response, expected_code):
    result = payload(response)
    require(result.get("status") == "error", "task did not fail closed", response)
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "task error code drifted", result)
    return result


def test_bounded_discovery(client):
    tool = next((item for item in client.list_tools()
                 if item.get("name") == "solveTask"), None)
    require(tool is not None, "solveTask is missing from tools/list")
    description = tool.get("description", "")
    require("deterministic fixed plan" in description
            and "unsupported task descriptions fail closed" in description
            and "independent engineering review" in description,
            "solveTask discovery boundary drifted", tool)


def test_shared_fluid_pvt(client):
    result = assert_success(client.call_task({
        "task": "Run a PVT saturation pressure analysis",
        "fluid": {
            "model": "PR",
            "components": {
                "methane": 0.70,
                "ethane": 0.10,
                "propane": 0.05,
                "n-heptane": 0.15,
            },
        },
        "parameters": {"experiment": "saturationPressure"},
        "validate": False,
    }))
    require(result.get("taskType") == "pvt"
            and result.get("totalSteps") == 1
            and result.get("completedSteps") == 1
            and result.get("success") is True,
            "PVT task accounting drifted", result)
    step = result.get("stepResults", [{}])[0]
    require(step.get("step") == "pvt_study"
            and step.get("runner") == "pvt"
            and step.get("success") is True
            and step.get("output", {}).get("status") == "success",
            "PVT task did not reach the canonical runner", step)
    combined = result.get("combinedData", {})
    require(combined.get("fluid", {}).get("components", {}).get("methane") == 0.70
            and "pvt_study_result" in combined,
            "shared fluid or PVT result evidence was not preserved", combined)


def test_missing_task(client):
    assert_error(client.call_task({"validate": False}), "MISSING_TASK")


def test_blank_and_malformed_task(client):
    assert_error(client.call_task({"task": "   ", "validate": False}), "MISSING_TASK")
    assert_error(client.call_tool("solveTask", {"taskJson": "{"}), "TASK_ERROR")


def test_unsupported_task(client):
    assert_error(client.call_task({
        "task": "Write a poem about offshore weather",
        "validate": False,
    }), "UNSUPPORTED_TASK")


def test_required_failure_stops(client):
    result = assert_error(client.call_task({
        "task": "Design two-stage compression",
        "parameters": {},
        "validate": False,
    }), "TASK_STEP_FAILED")
    require(result.get("taskType") == "compression"
            and result.get("totalSteps") == 3
            and result.get("completedSteps") == 1
            and result.get("success") is False,
            "required-step failure accounting drifted", result)
    require(result.get("errors", [{}])[0].get("causeCode") == "MISSING_COMPONENTS",
            "underlying runner diagnostic was not preserved", result)


def test_inventory_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    require(result.get("status") == "success", "capabilities request failed", result)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("solveTask", {})
    require(inventory.get("inventoryVersion") == "1.39"
            and limitations.get("contractTestedToolCount") == 39
            and limitations.get("confirmedGapToolCount") == 12
            and limitations.get("contractPromotionCandidateCount") == 0,
            "task-solver promotion accounting drifted", inventory)
    require(record.get("coverageStatus") == "CONTRACT_TESTED",
            "solveTask was not promoted atomically", record)
    require(record.get("benchmarkApplicability")
            == "NOT_APPLICABLE_NON_NUMERICAL_BOUNDED_TASK_ORCHESTRATION",
            "task-solver benchmark boundary drifted", record)
    require(record.get("contractEvidenceCount") == 6
            and "neqsim-mcp-server/test_solve_task_protocol.py" in record.get("contractEvidenceSources", [])
            and "neqsim-mcp-server/docs/evidence/TASK_SOLVER_CONTRACT.md" in record.get("contractEvidenceSources", []),
            "task-solver evidence sources drifted", record)


def main():
    client = McpClient()
    tests = [
        ("bounded discovery", test_bounded_discovery),
        ("shared-fluid PVT", test_shared_fluid_pvt),
        ("missing task", test_missing_task),
        ("blank and malformed task", test_blank_and_malformed_task),
        ("unsupported task", test_unsupported_task),
        ("required failure stops", test_required_failure_stops),
        ("inventory promoted", test_inventory_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} task-solver scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
