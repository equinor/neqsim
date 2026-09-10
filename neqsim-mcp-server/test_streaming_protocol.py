"""Packaged-MCP qualification for the bounded streamSimulation contract.

The scenarios exercise real STDIO transport, discovery, fixed request limits,
fail-closed malformed and oversized work, a canonical NeqSim sweep, incremental
polling, cancellation, and in-process state reporting. They do not establish
numerical accuracy, uncertainty validity, durability, distributed execution,
hard process isolation, plant authority, certification, or engineering
approval.
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
                "clientInfo": {"name": "neqsim-streaming-contract-test", "version": "1.0"},
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
        response = self.request("tools/call", {
            "name": name,
            "arguments": arguments,
        })
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_stream(self, request):
        return self.call_tool("streamSimulation", {
            "streamJson": json.dumps(request),
        })

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
    require(result.get("status") == "success", "stream request failed", response)
    require(result.get("tool") == "streamSimulation", "tool identity drifted", response)
    require(result.get("validation", {}).get("valid") is True,
            "standard validation evidence drifted", response)
    require(result.get("qualityGate", {}).get("verdict") == "passed",
            "software gate did not pass", response)
    return result


def assert_error(response, expected_code):
    result = payload(response)
    require(result.get("status") == "error", "stream request did not fail closed", response)
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "stream error code drifted", result)
    return result


def test_bounded_discovery(client):
    tool = next((item for item in client.list_tools()
                 if item.get("name") == "streamSimulation"), None)
    require(tool is not None, "streamSimulation is missing from tools/list")
    description = tool.get("description", "")
    require("1000 sweep points" in description
            and "10000 dynamic steps" in description
            and "100 records" in description
            and "not durable or distributed" in description
            and "independent engineering review" in description,
            "streaming discovery boundary drifted", tool)


def test_list_reports_limits(client):
    result = assert_success(client.call_stream({"action": "listOperations"}))
    limits = result.get("requestLimits", {})
    require(limits.get("maxSweepPoints") == 1000
            and limits.get("maxDynamicSteps") == 10000
            and limits.get("maxMonteCarloIterations") == 1000
            and limits.get("maxResultsPerPoll") == 100
            and limits.get("maxProcessJsonBytes") == 262144,
            "fixed request limits drifted", result)
    require("executionPolicy" in result and isinstance(result.get("operations"), list),
            "execution policy or operation list missing", result)


def test_fail_closed_actions_and_work_sizes(client):
    assert_error(client.call_stream({"action": "erase"}), "UNKNOWN_ACTION")
    assert_error(client.call_stream({
        "action": "startSweep",
        "components": {"methane": 1.0},
        "points": 0,
    }), "INVALID_POINTS")
    assert_error(client.call_stream({
        "action": "startSweep",
        "components": {"methane": 1.0},
        "points": 1001,
    }), "INVALID_POINTS")
    assert_error(client.call_stream({
        "action": "startMonteCarlo",
        "components": {"methane": 1.0},
        "iterations": 1001,
    }), "INVALID_ITERATIONS")


def test_fail_closed_sweep_semantics(client):
    assert_error(client.call_stream({
        "action": "startSweep",
        "components": {"methane": 1.0},
        "sweepVariable": "enthalpy",
        "points": 2,
    }), "INVALID_SWEEP_VARIABLE")
    assert_error(client.call_stream({
        "action": "startSweep",
        "components": {"methane": 1.0},
        "sweepVariable": "temperature",
        "unit": "rankine",
        "points": 2,
    }), "INVALID_UNIT")
    assert_error(client.call_stream({
        "action": "startSweep",
        "components": {},
        "points": 2,
    }), "MISSING_COMPONENTS")


def test_fail_closed_dynamic_timing(client):
    assert_error(client.call_stream({"action": "startDynamic"}), "MISSING_PROCESS")
    assert_error(client.call_stream({
        "action": "startDynamic",
        "processJson": {},
        "totalTime": 1,
        "timeStep": 0,
    }), "INVALID_TIME_RANGE")
    assert_error(client.call_stream({
        "action": "startDynamic",
        "processJson": {},
        "totalTime": 10001,
        "timeStep": 1,
    }), "INVALID_STEP_COUNT")


def test_canonical_sweep_lifecycle(client):
    started = assert_success(client.call_stream({
        "action": "startParametricSweep",
        "components": {"methane": 1.0},
        "model": "SRK",
        "sweepVariable": "temperature",
        "from": 20.0,
        "to": 21.0,
        "points": 2,
        "unit": "C",
        "fixedPressure": 10.0,
        "fixedPressureUnit": "bara",
    }))
    require(started.get("operationStatus") == "started" and started.get("totalPoints") == 2,
            "bounded sweep did not start", started)
    operation_id = started.get("operationId")

    result = None
    for _ in range(200):
        result = assert_success(client.call_stream({
            "action": "pollResults",
            "operationId": operation_id,
            "lastIndex": 0,
        }))
        if result.get("operationStatus") == "completed":
            break
        time.sleep(0.05)

    require(result.get("operationStatus") == "completed"
            and result.get("completedSteps") == 2
            and result.get("totalResultCount") == 2,
            "canonical sweep did not complete", result)
    require(result.get("maxResultsPerPoll") == 100
            and result.get("hasMoreResults") is False
            and result.get("nextPollIndex") == 2,
            "poll continuation metadata drifted", result)
    points = result.get("newResults", [])
    require(len(points) == 2
            and all(point.get("pressure_bara") == 10.0 for point in points)
            and all(point.get("numberOfPhases", 0) >= 1 for point in points),
            "canonical NeqSim sweep evidence drifted", points)

    assert_error(client.call_stream({
        "action": "poll",
        "operationId": operation_id,
        "lastIndex": -1,
    }), "INVALID_CURSOR")


def test_unknown_cancel_is_non_disclosing(client):
    result = assert_error(client.call_stream({
        "action": "cancelOperation",
        "operationId": "sweep-not-owned-or-absent",
    }), "NOT_FOUND")
    require("operationId" not in result,
            "unknown cancellation disclosed state", result)



def test_inventory_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    require(result.get("status") == "success", "capabilities request failed", result)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("streamSimulation", {})
    require(inventory.get("inventoryVersion") == "1.34"
            and limitations.get("contractTestedToolCount") == 34
            and limitations.get("confirmedGapToolCount") == 17
            and limitations.get("contractPromotionCandidateCount") == 0,
            "streaming promotion accounting drifted", inventory)
    require(record.get("coverageStatus") == "CONTRACT_TESTED",
            "streamSimulation was not promoted atomically", record)
    require(record.get("benchmarkApplicability")
            == "NOT_APPLICABLE_NON_NUMERICAL_BOUNDED_STREAMING_SIMULATION",
            "streaming benchmark boundary drifted", record)
    require(record.get("contractEvidenceCount") == 7
            and "src/test/java/neqsim/mcp/runners/StreamingRunnerTest.java"
            in record.get("contractEvidenceSources", [])
            and "src/test/java/neqsim/mcp/runners/McpPrincipalScopingTest.java"
            in record.get("contractEvidenceSources", [])
            and "neqsim-mcp-server/test_streaming_protocol.py"
            in record.get("contractEvidenceSources", [])
            and "neqsim-mcp-server/docs/evidence/STREAMING_SIMULATION_CONTRACT.md"
            in record.get("contractEvidenceSources", []),
            "streaming evidence sources drifted", record)


def main():
    client = McpClient()
    tests = [
        ("bounded discovery", test_bounded_discovery),
        ("list reports limits", test_list_reports_limits),
        ("fail-closed actions and work sizes", test_fail_closed_actions_and_work_sizes),
        ("fail-closed sweep semantics", test_fail_closed_sweep_semantics),
        ("fail-closed dynamic timing", test_fail_closed_dynamic_timing),
        ("canonical sweep lifecycle", test_canonical_sweep_lifecycle),
        ("unknown cancel is non-disclosing", test_unknown_cancel_is_non_disclosing),
        ("inventory promoted", test_inventory_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} bounded-streaming scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
