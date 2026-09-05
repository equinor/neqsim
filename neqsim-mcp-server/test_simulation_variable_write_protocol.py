"""Focused packaged-MCP qualification for simulation-variable mutation.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies the existing setSimulationVariable software contract against the
canonical simple-separation process. It does not validate process accuracy,
convergence adequacy, conservation, optimization, plant authority, or design
approval.
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
                        "name": "neqsim-variable-write-contract-test",
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


def register_model(client, process):
    """Register the same canonical definition for handle-routing qualification."""
    result = payload(
        client.manage_model(
            {
                "action": "register",
                "name": "phase0-variable-write",
                "version": "1.0.0",
                "processJson": process,
            }
        )
    )
    require(result.get("status") == "success", "model registration failed", result)
    model_id = result.get("modelId")
    require(isinstance(model_id, str) and model_id, "registration omitted modelId", result)
    return model_id


def write_temperature(client, process_or_handle):
    """Apply one bounded write in explicit Celsius units."""
    return client.call_tool(
        "setSimulationVariable",
        {
            "processJson": process_or_handle,
            "address": "feed.temperature",
            "value": 35.0,
            "unit": "C",
        },
    )


def assert_successful_write(response):
    """Validate the write, rerun handoff, units, and standard envelope."""
    result = payload(response)
    require(result.get("status") == "success", "variable write failed", response)
    require(result.get("tool") == "setSimulationVariable", "tool envelope drifted", response)
    require(result.get("address") == "feed.temperature", "write address drifted", result)
    require(result.get("value") == 35.0, "written value drifted", result)
    require(result.get("unit") == "C", "requested Celsius unit drifted", result)
    require(isinstance(result.get("simulationReport"), dict), "rerun report is absent", result)
    require(
        result.get("validation", {}).get("valid") is True,
        "successful write lacks valid envelope",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "successful write did not pass the software gate",
        response,
    )
    return result


def test_inline_write(client):
    process = canonical_process(client)
    assert_successful_write(write_temperature(client, json.dumps(process)))


def test_model_handle_equivalence(client):
    process = canonical_process(client)
    direct = assert_successful_write(write_temperature(client, json.dumps(process)))
    model_id = register_model(client, process)
    try:
        handled = assert_successful_write(write_temperature(client, model_id))
        for key in ("status", "tool", "address", "value", "unit"):
            require(handled.get(key) == direct.get(key), "handle write drifted", handled)
    finally:
        deleted = payload(client.manage_model({"action": "delete", "modelId": model_id}))
        require(deleted.get("status") == "success", "model cleanup failed", deleted)


def test_physical_bound_rejection(client):
    process = canonical_process(client)
    response = client.call_tool(
        "setSimulationVariable",
        {
            "processJson": json.dumps(process),
            "address": "feed.temperature",
            "value": -300.0,
            "unit": "C",
        },
    )
    result = payload(response)
    require(result.get("status") == "error", "invalid temperature was accepted", response)
    require("simulationReport" not in result, "rejected write claimed a rerun report", response)
    require(
        result.get("qualityGate", {}).get("verdict") == "failed",
        "invalid temperature did not fail the software gate",
        response,
    )


def test_output_address_rejection(client):
    process = canonical_process(client)
    response = client.call_tool(
        "setSimulationVariable",
        {
            "processJson": json.dumps(process),
            "address": "HP Sep.gasOutStream.temperature",
            "value": 35.0,
            "unit": "C",
        },
    )
    result = payload(response)
    require(result.get("status") != "success", "read-only output was reported written", response)
    require("simulationReport" not in result, "read-only rejection claimed a rerun report", response)
    require(
        result.get("qualityGate", {}).get("verdict") != "passed",
        "read-only output passed the software gate",
        response,
    )


def test_missing_input_fails_closed(client):
    response = client.call_tool(
        "setSimulationVariable",
        {
            "processJson": "",
            "address": "feed.temperature",
            "value": 35.0,
            "unit": "C",
        },
    )
    require(payload(response).get("status") == "error", "blank process was accepted", response)
    require(error_code(response) == "INPUT_ERROR", "blank-process code drifted", response)


def test_phase0_inventory_remains_unpromoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("setSimulationVariable", {})
    require(inventory.get("inventoryVersion") == "1.26", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 25
        and limitations.get("confirmedGapToolCount") == 26,
        "qualification changed inventory accounting",
        limitations,
    )
    require(
        record.get("coverageStatus") == "CONFIRMED_GAP",
        "qualification prematurely promoted setSimulationVariable",
        record,
    )


def main():
    client = McpClient()
    tests = [
        ("inline canonical write", test_inline_write),
        ("model-handle write equivalence", test_model_handle_equivalence),
        ("physical-bound rejection", test_physical_bound_rejection),
        ("read-only output rejection", test_output_address_rejection),
        ("missing input fails closed", test_missing_input_fails_closed),
        ("Phase 0 classification remains unpromoted", test_phase0_inventory_remains_unpromoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} variable-write contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
