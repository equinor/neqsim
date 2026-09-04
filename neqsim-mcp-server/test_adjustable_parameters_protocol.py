"""Focused real-MCP qualification for adjustable-parameter discovery.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies deterministic parameter discovery for the canonical
``compression-with-cooling`` process. It is bounded software-contract evidence:
it does not validate optimization, numerical fidelity, conservation, parameter
feasibility, live-plant authority, or engineering approval.
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
                        "name": "neqsim-adjustable-parameters-contract-test",
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
        "message",
        "tool",
        "action",
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
    """Return the canonical code from the standard nested error envelope."""
    if not isinstance(response, dict):
        return None
    if isinstance(response.get("code"), str):
        return response["code"]
    errors = response.get("errors")
    if isinstance(errors, list) and errors and isinstance(errors[0], dict):
        return errors[0].get("code")
    return None


def canonical_process(client):
    """Retrieve the catalog fixture through MCP instead of duplicating model JSON."""
    example = client.call_tool(
        "getExample", {"category": "process", "name": "compression-with-cooling"}
    )
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "compression example is not an object", example)
    require("process" in example, "compression example omitted process", example)
    return example


def assert_registry(result):
    """Validate stable parameter-address and unit-bearing metadata."""
    data = payload(result)
    require(data.get("status") == "success", "parameter discovery failed", result)
    parameters = data.get("parameters")
    require(isinstance(parameters, list) and parameters, "parameter registry is empty", data)
    require(data.get("count") == len(parameters), "parameter count drifted", data)
    required_keys = {
        "name",
        "address",
        "unit",
        "targetUnitName",
        "targetProperty",
        "source",
    }
    for parameter in parameters:
        require(isinstance(parameter, dict), "parameter record is not an object", parameter)
        require(required_keys.issubset(parameter), "parameter metadata is incomplete", parameter)
        require(bool(parameter.get("address")), "parameter address is empty", parameter)
        unit = parameter.get("unit")
        require(isinstance(unit, str), "parameter unit is not a string", parameter)
        if not unit:
            require(
                parameter.get("targetProperty") == "polytropicEfficiency",
                "empty unit is reserved for qualified dimensionless properties",
                parameter,
            )
        for bound_key in ("lowerBound", "upperBound"):
            if bound_key in parameter:
                bound = parameter[bound_key]
                require(
                    bound is None
                    or (
                        isinstance(bound, (int, float))
                        and not isinstance(bound, bool)
                    ),
                    "parameter bound is not numeric or null",
                    parameter,
                )
        require(
            parameter.get("source") in {"INPUT_VARIABLE", "ADJUSTER"},
            "parameter provenance is unsupported",
            parameter,
        )
    addresses = {parameter["address"] for parameter in parameters}
    require(
        {"1st Stage.outletPressure", "2nd Stage.outletPressure"}.issubset(addresses),
        "canonical compressor addresses drifted",
        parameters,
    )
    return data


def register_model(client, process):
    result = payload(
        client.manage_model(
            {
                "action": "register",
                "name": "phase0-adjustable-parameters",
                "version": "1.0.0",
                "processJson": process,
            }
        )
    )
    require(result.get("status") == "success", "model registration failed", result)
    model_id = result.get("modelId")
    require(isinstance(model_id, str) and model_id, "registration omitted modelId", result)
    return model_id


def test_direct_and_handle_equivalence(client):
    process = canonical_process(client)
    direct = assert_registry(
        client.call_tool("getAdjustableParameters", {"processJson": json.dumps(process)})
    )
    model_id = register_model(client, process)
    try:
        handled = assert_registry(
            client.call_tool("getAdjustableParameters", {"processJson": model_id})
        )
        require(
            handled.get("schemaVersion") == direct.get("schemaVersion")
            and handled.get("count") == direct.get("count")
            and handled.get("parameters") == direct.get("parameters"),
            "direct and model-handle registries differ",
            {"direct": direct, "handled": handled},
        )
    finally:
        deleted = payload(client.manage_model({"action": "delete", "modelId": model_id}))
        require(deleted.get("status") == "success", "model cleanup failed", deleted)


def test_invalid_request_fails_closed(client):
    result = client.call_tool("getAdjustableParameters", {"processJson": ""})
    require(payload(result).get("status") == "error", "blank request did not fail", result)
    require(error_code(result) == "INPUT_ERROR", "blank request error code drifted", result)


def test_phase0_contract_is_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    require(inventory.get("inventoryVersion") == "1.25", "inventory version drifted", inventory)
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("getAdjustableParameters", {})
    require(
        limitations.get("contractTestedToolCount") == 24
        and limitations.get("confirmedGapToolCount") == 27
        and record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_AUTOMATION_PARAMETER_DISCOVERY"
        and "neqsim-mcp-server/test_adjustable_parameters_protocol.py"
        in record.get("contractEvidenceSources", []),
        "adjustable-parameter promotion drifted",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("direct and handle equivalence", test_direct_and_handle_equivalence),
        ("invalid request fails closed", test_invalid_request_fails_closed),
        ("Phase 0 contract is promoted", test_phase0_contract_is_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} adjustable-parameter contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
