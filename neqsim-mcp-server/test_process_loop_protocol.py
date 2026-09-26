"""Focused real-MCP qualification for canonical process-loop orchestration.

The harness executes the packaged server over STDIO. It qualifies ordered
build-once/sweep-many delegation and explicit failure evidence only; it does not
claim optimization quality, numerical validity, safe limits, or plant authority.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


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
                "clientInfo": {"name": "neqsim-process-loop-contract-test", "version": "1.0"},
            },
        })
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_tool(self, name, arguments):
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "tools/call",
            "params": {"name": name, "arguments": arguments},
        })
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", "{}"))

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
    if condition:
        return
    suffix = "" if detail is None else "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


def payload(response):
    data = response.get("data") if isinstance(response, dict) else None
    return data if isinstance(data, dict) else response


def canonical_process(client):
    response = client.call_tool(
        "getExample", {"category": "process", "name": "compression-with-cooling"}
    )
    process = payload(response)
    require(isinstance(process, dict) and "process" in process,
            "canonical compression example is unavailable", response)
    return process


def run_loop(client, process, trials, readbacks=None):
    return client.call_tool(
        "runProcessLoop",
        {
            "processJson": json.dumps(process) if isinstance(process, dict) else process,
            "trials": json.dumps(trials) if not isinstance(trials, str) else trials,
            "readbacks": json.dumps(readbacks or []),
            "setpointUnit": "bara",
            "readbackUnit": "kW",
        },
    )


def test_ordered_canonical_sweep(client):
    result = run_loop(
        client,
        canonical_process(client),
        [
            {"1st Stage.outletPressure": 80.0, "2nd Stage.outletPressure": 120.0},
            {"1st Stage.outletPressure": 82.0, "2nd Stage.outletPressure": 125.0},
        ],
        ["1st Stage.power"],
    )
    data = payload(result)
    require(result.get("status") == "success", "process loop failed", result)
    require(data.get("trialCount") == 2, "trial count drifted", data)
    require(data.get("feasibleCount") in (0, 1, 2), "feasible count is invalid", data)
    trials = data.get("trials")
    require(isinstance(trials, list) and len(trials) == 2, "trial results drifted", data)
    require([trial.get("trialIndex") for trial in trials] == [0, 1],
            "trial order drifted", trials)
    require(all("feasible" in trial for trial in trials),
            "trial feasibility evidence is missing", trials)


def test_rejected_setpoint_isolated(client):
    result = run_loop(
        client,
        canonical_process(client),
        [
            {"1st Stage.outletPressure": 80.0},
            {"NoSuchUnit.outletPressure": 120.0},
        ],
    )
    data = payload(result)
    require(result.get("status") == "success", "mixed sweep crashed", result)
    trials = data.get("trials", [])
    require(len(trials) == 2, "mixed sweep lost a trial", data)
    require("setpointsRejected" in trials[1],
            "bad address lacked explicit rejected-setpoint evidence", trials[1])


def test_malformed_trials_fail_closed(client):
    result = run_loop(client, canonical_process(client), "[")
    require(result.get("status") == "error", "malformed trials did not fail closed", result)


def test_blank_process_fails_closed(client):
    result = run_loop(client, "", [{"1st Stage.outletPressure": 80.0}])
    require(result.get("status") == "error", "blank process did not fail closed", result)


def test_phase0_contract_is_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runProcessLoop", {})
    require(
        inventory.get("inventoryVersion") == "1.46"
        and limitations.get("contractTestedToolCount") == 46
        and limitations.get("confirmedGapToolCount") == 5
        and limitations.get("contractPromotionCandidateCount") == 0
        and record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_BOUNDED_CANONICAL_PROCESS_LOOP_ORCHESTRATION_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 7
        and "neqsim-mcp-server/test_process_loop_protocol.py"
        in record.get("contractEvidenceSources", []),
        "process-loop promotion drifted",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("ordered canonical sweep", test_ordered_canonical_sweep),
        ("rejected setpoint isolation", test_rejected_setpoint_isolated),
        ("malformed trials fail closed", test_malformed_trials_fail_closed),
        ("blank process fails closed", test_blank_process_fails_closed),
        ("Phase 0 contract is promoted", test_phase0_contract_is_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} process-loop contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
