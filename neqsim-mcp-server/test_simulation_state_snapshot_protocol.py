"""Focused packaged-MCP qualification for simulation-state snapshots.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies bounded saveSimulationState and compareSimulationStates behavior
against the canonical simple-separation process. It does not establish replay,
numeric-diff completeness, persistence, convergence, conservation, plant
authority, or engineering approval.
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
                        "name": "neqsim-state-snapshot-contract-test",
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

    def manage_model(self, request):
        return self.call_tool("manageModel", {"modelJson": json.dumps(request)})

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
        "provenance",
        "validation",
        "qualityGate",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def error_code(response):
    """Return a stable error code from the standard response."""
    result = payload(response)
    if isinstance(result.get("code"), str):
        return result["code"]
    errors = result.get("errors")
    if isinstance(errors, list) and errors and isinstance(errors[0], dict):
        return errors[0].get("code")
    return None


def canonical_process(client):
    """Retrieve the catalog process through MCP instead of duplicating model JSON."""
    example = client.call_tool(
        "getExample", {"category": "process", "name": "simple-separation"}
    )
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "simple-separation example is not an object", example)
    require("process" in example, "simple-separation example omitted process", example)
    return example


def save_snapshot(client, process_or_handle, version="1.0"):
    """Save one bounded snapshot with explicit lifecycle metadata."""
    return client.call_tool(
        "saveSimulationState",
        {
            "processJson": process_or_handle,
            "stateName": "phase0-snapshot",
            "stateVersion": version,
        },
    )


def assert_snapshot(response, version="1.0"):
    """Validate standard envelope and bounded canonical snapshot content."""
    result = payload(response)
    require(result.get("status") == "success", "state snapshot failed", response)
    require(result.get("tool") == "saveSimulationState", "snapshot tool drifted", response)
    require(result.get("stateName") == "phase0-snapshot", "snapshot name drifted", result)
    require(result.get("stateVersion") == version, "snapshot version drifted", result)
    state = result.get("state")
    require(isinstance(state, dict), "snapshot state is not an object", result)
    require(state.get("schemaVersion") == "1.1", "snapshot schema drifted", state)
    require(state.get("name") == "phase0-snapshot", "state name drifted", state)
    require(state.get("version") == version, "state version drifted", state)
    require(bool(state.get("equipmentStates")), "snapshot omitted equipment state", state)
    require(bool(state.get("streamStates")), "snapshot omitted stream state", state)
    require(
        result.get("validation", {}).get("valid") is True,
        "snapshot lacks a valid standard envelope",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "snapshot did not pass the software gate",
        response,
    )
    return state


def test_inline_snapshot(client):
    process = canonical_process(client)
    assert_snapshot(save_snapshot(client, json.dumps(process)))


def test_model_handle_equivalence(client):
    process = canonical_process(client)
    direct = assert_snapshot(save_snapshot(client, json.dumps(process)))
    registered = payload(
        client.manage_model(
            {
                "action": "register",
                "name": "phase0-state-snapshot",
                "version": "1.0",
                "processJson": process,
            }
        )
    )
    require(registered.get("status") == "success", "model registration failed", registered)
    model_id = registered.get("modelId")
    require(isinstance(model_id, str) and model_id, "registration omitted modelId", registered)
    try:
        handled = assert_snapshot(save_snapshot(client, model_id))
        require(
            len(handled.get("equipmentStates", []))
            == len(direct.get("equipmentStates", [])),
            "model-handle equipment snapshot drifted",
            handled,
        )
        require(
            sorted(handled.get("streamStates", {}))
            == sorted(direct.get("streamStates", {})),
            "model-handle stream snapshot drifted",
            handled,
        )
    finally:
        deleted = payload(client.manage_model({"action": "delete", "modelId": model_id}))
        require(deleted.get("status") == "success", "model cleanup failed", deleted)


def test_identical_comparison(client):
    process = canonical_process(client)
    state = assert_snapshot(save_snapshot(client, json.dumps(process)))
    result = payload(
        client.call_tool(
            "compareSimulationStates",
            {"stateJson1": json.dumps(state), "stateJson2": json.dumps(state)},
        )
    )
    require(result.get("status") == "success", "identity comparison failed", result)
    require(result.get("hasChanges") is False, "identical snapshots reported changes", result)


def test_version_comparison(client):
    process = canonical_process(client)
    state = assert_snapshot(save_snapshot(client, json.dumps(process)))
    revised = json.loads(json.dumps(state))
    revised["version"] = "1.1"
    result = payload(
        client.call_tool(
            "compareSimulationStates",
            {"stateJson1": json.dumps(state), "stateJson2": json.dumps(revised)},
        )
    )
    require(result.get("status") == "success", "version comparison failed", result)
    require(result.get("hasChanges") is True, "version change was not reported", result)
    modified = result.get("diff", {}).get("modifiedParameters", {})
    require(modified.get("version") == "1.0 -> 1.1", "version diff drifted", result)
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "version comparison did not pass the software gate",
        result,
    )


def test_missing_inputs_fail_closed(client):
    missing_process = client.call_tool(
        "saveSimulationState",
        {"processJson": "", "stateName": "snapshot", "stateVersion": "1.0"},
    )
    missing_first = client.call_tool(
        "compareSimulationStates", {"stateJson1": "", "stateJson2": "{}"}
    )
    missing_second = client.call_tool(
        "compareSimulationStates", {"stateJson1": "{}", "stateJson2": ""}
    )
    for label, response in (
        ("blank process", missing_process),
        ("blank first state", missing_first),
        ("blank second state", missing_second),
    ):
        require(payload(response).get("status") == "error", label + " was accepted", response)
        require(error_code(response) == "INPUT_ERROR", label + " code drifted", response)


def test_phase0_inventory_is_promoted_atomically(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    limitations = inventory.get("knownLimitations", {})
    records = limitations.get("coverageRecords", {})
    require(inventory.get("inventoryVersion") == "1.31", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 31
        and limitations.get("confirmedGapToolCount") == 20
        and limitations.get("contractPromotionCandidateCount") == 0,
        "promotion inventory accounting drifted",
        limitations,
    )
    expected = {
        "saveSimulationState":
            "NOT_APPLICABLE_NON_NUMERICAL_CANONICAL_PROCESS_STATE_SNAPSHOT",
        "compareSimulationStates":
            "NOT_APPLICABLE_NON_NUMERICAL_PROCESS_STATE_SNAPSHOT_COMPARISON",
    }
    for tool_name, applicability in expected.items():
        record = records.get(tool_name, {})
        require(
            record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("benchmarkApplicability") == applicability,
            tool_name + " promotion is incomplete",
            record,
        )
        require(
            "neqsim-mcp-server/test_simulation_state_snapshot_protocol.py"
            in record.get("contractEvidenceSources", [])
            and "plant or control authority" in record.get("evidenceBoundary", ""),
            tool_name + " evidence boundary drifted",
            record,
        )


def main():
    client = McpClient()
    tests = [
        ("inline canonical snapshot", test_inline_snapshot),
        ("model-handle snapshot equivalence", test_model_handle_equivalence),
        ("identical snapshot comparison", test_identical_comparison),
        ("metadata-version comparison", test_version_comparison),
        ("missing snapshot inputs fail closed", test_missing_inputs_fail_closed),
        ("Phase 0 classification is promoted atomically", test_phase0_inventory_is_promoted_atomically),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} state-snapshot contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
