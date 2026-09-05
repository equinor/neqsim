"""Focused real-MCP qualification for reusable process model handles.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO and
qualifies the existing manageModel lifecycle plus handle reuse through canonical
runProcess and automation-read routes. It is software-contract and transport
evidence only: it does not validate process numerical accuracy, convergence
quality, facility completeness, persistence across server restarts, external
authorization, or plant authority.
"""
import copy
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC MCP client for packaged-server tests."""

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
                        "name": "neqsim-model-registry-test",
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
        return self.call_tool(
            "manageModel", {"modelJson": json.dumps(request)}
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
    """Raise a compact assertion with JSON detail when a protocol contract fails."""
    if condition:
        return
    suffix = ""
    if detail is not None:
        suffix = "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


def payload(response):
    """Return canonical data while retaining standardized envelope state."""
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


def simple_process(client):
    """Retrieve the canonical catalog fixture through MCP instead of duplicating it."""
    example = client.call_tool(
        "getExample", {"category": "process", "name": "simple-separation"}
    )
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "simple-separation example is not an object", example)
    require("process" in example, "simple-separation example omitted process", example)
    return example


def register(client, process_definition):
    result = client.manage_model(
        {
            "action": "register",
            "name": "phase0-model-registry",
            "version": "1.0.0",
            "processJson": process_definition,
        }
    )
    data = payload(result)
    require(data.get("status") == "success", "manageModel register failed", result)
    model_id = data.get("modelId")
    require(
        isinstance(model_id, str) and model_id.startswith("model_"),
        "registration did not issue a model handle",
        result,
    )
    require(data.get("revision") == 1, "initial model revision drifted", result)
    require(data.get("name") == "phase0-model-registry", "model name drifted", result)
    require(data.get("version") == "1.0.0", "model version drifted", result)
    require(bool(data.get("usage")), "registration omitted handle-usage guidance", result)
    return model_id


def test_registration_is_idempotent(client, process_definition):
    model_id = register(client, process_definition)
    repeated = payload(
        client.manage_model(
            {
                "action": "register",
                "name": "phase0-model-registry",
                "version": "1.0.0",
                "processJson": process_definition,
            }
        )
    )
    require(repeated.get("status") == "success", "repeat registration failed", repeated)
    require(
        repeated.get("modelId") == model_id,
        "identical content did not retain the same handle",
        repeated,
    )
    require(repeated.get("revision") == 1, "idempotent registration changed revision", repeated)
    return model_id


def test_get_list_and_inspect(client, model_id):
    fetched = payload(client.manage_model({"action": "get", "modelId": model_id}))
    require(fetched.get("status") == "success", "manageModel get failed", fetched)
    require(fetched.get("modelId") == model_id, "get lost model identity", fetched)
    definition = fetched.get("definition")
    require(isinstance(definition, dict), "get omitted stored definition", fetched)
    require("process" in definition, "stored definition lost process content", fetched)

    listed = payload(client.manage_model({"action": "list"}))
    require(listed.get("status") == "success", "manageModel list failed", listed)
    models = listed.get("models", [])
    require(isinstance(models, list), "model list is not an array", listed)
    require(
        any(item.get("modelId") == model_id for item in models if isinstance(item, dict)),
        "registered model is absent from caller-visible list",
        listed,
    )
    require(listed.get("count") == len(models), "model count does not match list", listed)

    inspected = payload(client.manage_model({"action": "inspect", "modelId": model_id}))
    require(inspected.get("status") == "success", "manageModel inspect failed", inspected)
    require(inspected.get("modelId") == model_id, "inspect lost model identity", inspected)
    equipment = inspected.get("equipment", [])
    require(
        isinstance(equipment, list) and len(equipment) >= 2,
        "inspect did not expose expected process structure",
        inspected,
    )
    require(
        inspected.get("equipmentCount") == len(equipment),
        "inspect equipment count drifted",
        inspected,
    )


def test_handle_drives_canonical_process_and_read_routes(client, model_id):
    process_result = payload(client.call_tool("runProcess", {"processJson": model_id}))
    require(process_result.get("status") == "success", "runProcess(handle) failed", process_result)
    require(
        isinstance(process_result.get("provenance"), dict),
        "runProcess(handle) omitted provenance",
        process_result,
    )
    require(
        isinstance(process_result.get("validation"), dict),
        "runProcess(handle) omitted validation",
        process_result,
    )

    units_result = payload(
        client.call_tool("listSimulationUnits", {"processJson": model_id})
    )
    require(
        units_result.get("status") == "success",
        "listSimulationUnits(handle) failed",
        units_result,
    )
    units = units_result.get("units", [])
    require(isinstance(units, list) and units, "handle-backed unit inventory is empty", units_result)


def test_revision_is_stable_and_visible(client, model_id, process_definition):
    revised = copy.deepcopy(process_definition)
    revised["phase0QualificationRevision"] = 2

    result = payload(
        client.manage_model(
            {
                "action": "revise",
                "modelId": model_id,
                "version": "1.0.1",
                "processJson": revised,
            }
        )
    )
    require(result.get("status") == "success", "manageModel revise failed", result)
    require(result.get("modelId") == model_id, "revision changed stable handle", result)
    require(result.get("revision") == 2, "revision counter did not increment", result)
    require(result.get("version") == "1.0.1", "revision version was not retained", result)

    fetched = payload(client.manage_model({"action": "get", "modelId": model_id}))
    require(fetched.get("revision") == 2, "get did not expose revised version", fetched)
    require(fetched.get("definition") == revised, "revised definition was not retained exactly", fetched)


def error_code(response):
    """Collect the stable root/domain error code without assuming one envelope layout."""
    data = payload(response)
    if isinstance(data, dict) and data.get("code"):
        return data.get("code")
    for item in data.get("errors", []) if isinstance(data, dict) else []:
        if isinstance(item, dict) and item.get("code"):
            return item.get("code")
    return None


def test_invalid_requests_fail_closed(client):
    invalid_definition = client.manage_model(
        {"action": "register", "processJson": {"fluid": {"components": {"methane": 1.0}}}}
    )
    require(
        payload(invalid_definition).get("status") == "error",
        "invalid process definition was accepted",
        invalid_definition,
    )
    require(
        error_code(invalid_definition) == "INVALID_DEFINITION",
        "invalid-definition error code drifted",
        invalid_definition,
    )

    unknown_action = client.manage_model({"action": "not-a-phase0-action"})
    require(
        payload(unknown_action).get("status") == "error",
        "unknown model action was accepted",
        unknown_action,
    )
    require(
        error_code(unknown_action) == "UNKNOWN_ACTION",
        "unknown-action error code drifted",
        unknown_action,
    )

    unknown_model = client.manage_model(
        {"action": "get", "modelId": "model_deadbeefdeadbeef"}
    )
    require(
        payload(unknown_model).get("status") == "error",
        "unknown model handle was accepted",
        unknown_model,
    )
    require(
        error_code(unknown_model) == "UNKNOWN_MODEL",
        "unknown-model error code drifted",
        unknown_model,
    )


def test_delete_invalidates_handle(client, model_id):
    deleted = payload(client.manage_model({"action": "delete", "modelId": model_id}))
    require(deleted.get("status") == "success", "manageModel delete failed", deleted)
    require(deleted.get("modelId") == model_id, "delete lost model identity", deleted)
    require(deleted.get("deleted") is True, "delete did not confirm removal", deleted)

    missing = client.manage_model({"action": "get", "modelId": model_id})
    require(
        payload(missing).get("status") == "error",
        "deleted model remained retrievable",
        missing,
    )
    require(error_code(missing) == "UNKNOWN_MODEL", "deleted handle error drifted", missing)

    process_result = payload(client.call_tool("runProcess", {"processJson": model_id}))
    require(
        process_result.get("status") == "error",
        "deleted handle still executed through runProcess",
        process_result,
    )


def test_phase0_classification_is_promoted_atomically(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 evidence inventory", result)
    require(inventory.get("inventoryVersion") == "1.26", "unexpected evidence inventory version", result)
    limitations = inventory.get("knownLimitations", {})
    require(
        limitations.get("contractTestedToolCount") == 25
        and limitations.get("confirmedGapToolCount") == 26,
        "manageModel promotion did not move trust accounting atomically",
        limitations,
    )
    record = limitations.get("coverageRecords", {}).get("manageModel", {})
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED",
        "manageModel was not promoted with its qualification evidence",
        record,
    )
    require(
        record.get("contractTrustAvailable") is True,
        "manageModel promotion omitted contract-trust marker",
        record,
    )
    require(
        record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_MODEL_REGISTRY_LIFECYCLE",
        "manageModel benchmark-applicability boundary drifted",
        record,
    )
    evidence = json.dumps(record.get("contractEvidenceSources", []))
    require(record.get("contractEvidenceCount") == 5, "manageModel evidence count drifted", record)
    require("ModelRegistryTest.java" in evidence, "manageModel omits Java regression evidence", record)
    require("test_model_registry_protocol.py" in evidence, "manageModel omits focused protocol evidence", record)
    require("test_mcp_server.py" in evidence, "manageModel omits primary protocol accounting", record)
    boundary = record.get("evidenceBoundary", "")
    require("server restarts" in boundary, "manageModel persistence limitation was lost", record)
    require("numerical model accuracy" in boundary, "manageModel numerical limitation was lost", record)


def main():
    client = McpClient()
    model_id = None
    try:
        client.start()
        process_definition = simple_process(client)
        model_id = test_registration_is_idempotent(client, process_definition)
        test_get_list_and_inspect(client, model_id)
        test_handle_drives_canonical_process_and_read_routes(client, model_id)
        test_revision_is_stable_and_visible(client, model_id, process_definition)
        test_invalid_requests_fail_closed(client)
        test_phase0_classification_is_promoted_atomically(client)
        test_delete_invalidates_handle(client, model_id)
        model_id = None
    finally:
        try:
            if client.proc is not None and model_id is not None:
                client.manage_model({"action": "delete", "modelId": model_id})
        finally:
            client.close()
    print("manageModel real-MCP qualification: 7/7 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
