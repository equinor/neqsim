"""Focused real-MCP qualification for the persisted-state lifecycle.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
with an isolated Java user home. It qualifies bounded local persistence for a
canonical ``simple-separation`` process. It is software-contract evidence only:
it does not establish numerical fidelity, conservation, distributed durability,
live-plant authority, or engineering approval.
"""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC client for packaged-server qualification."""

    def __init__(self):
        self.proc = None
        self.message_id = 0
        self.temporary_home = tempfile.TemporaryDirectory(
            prefix="neqsim-state-contract-"
        )

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
            [
                "java",
                "-Duser.home=" + self.temporary_home.name,
                "-jar",
                JAR,
            ],
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
                        "name": "neqsim-state-contract-test",
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

    def manage_session(self, request):
        return self.call_tool("manageSession", {"sessionJson": json.dumps(request)})

    def manage_state(self, request):
        return self.call_tool("manageState", {"persistJson": json.dumps(request)})

    def close(self):
        if self.proc is not None:
            if self.proc.stdin:
                self.proc.stdin.close()
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.terminate()
                self.proc.wait(timeout=10)
            self.proc = None
        self.temporary_home.cleanup()


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


def simple_process(client):
    """Retrieve the canonical catalog fixture instead of duplicating model JSON."""
    example = client.call_tool(
        "getExample", {"category": "process", "name": "simple-separation"}
    )
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "simple-separation example is not an object", example)
    require("process" in example, "simple-separation example omitted process", example)
    return example


def test_current_phase0_boundary(client):
    """Keep manageState unpromoted until this prerequisite evidence merges."""
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    require(inventory.get("inventoryVersion") == "1.20", "inventory version drifted", inventory)
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("manageState", {})
    require(
        limitations.get("contractTestedToolCount") == 18
        and limitations.get("confirmedGapToolCount") == 33
        and record.get("coverageStatus") == "CONFIRMED_GAP",
        "manageState was promoted before prerequisite evidence merged",
        limitations,
    )


def create_canonical_session(client):
    process = simple_process(client)
    result = payload(
        client.manage_session(
            {
                "action": "create",
                "name": "phase0-state-contract",
                "processJson": process,
            }
        )
    )
    require(result.get("status") == "success", "canonical session create failed", result)
    require(result.get("equipmentCount", 0) > 0, "canonical session has no equipment", result)
    return process, result.get("sessionId")


def configure_isolated_storage(client):
    storage = Path(client.temporary_home.name) / ".neqsim" / "state-contract"
    result = payload(
        client.manage_state({"action": "setStorageDir", "directory": str(storage)})
    )
    require(result.get("status") == "success", "storage configuration failed", result)
    require(Path(result.get("storageDir")) == storage, "storage path drifted", result)
    return storage


def save_two_versions_without_overwrite(client, session_id, process):
    request = {
        "action": "save",
        "sessionId": session_id,
        "name": "phase0-state",
        "version": "1.0.0",
        "description": "Canonical simple-separation lifecycle evidence",
        "processDefinition": process,
    }
    first = payload(client.manage_state(request))
    second = payload(client.manage_state(request))
    require(first.get("status") == "success", "first save failed", first)
    require(second.get("status") == "success", "second save failed", second)
    require(first.get("filename") != second.get("filename"), "save overwrote prior evidence", second)
    return first.get("filename"), second.get("filename")


def test_saved_envelope(storage, first_filename, process):
    envelope = json.loads((storage / first_filename).read_text(encoding="utf-8"))
    require(envelope.get("format") == "neqsim-saved-state", "saved format drifted", envelope)
    require(envelope.get("formatVersion") == "1.0.0", "format version drifted", envelope)
    require(envelope.get("processDefinition") == process, "process definition changed on save", envelope)
    require(bool(envelope.get("neqsimVersion")), "saved provenance omitted NeqSim version", envelope)


def list_compare_and_restore(client, first_filename, second_filename):
    listed = payload(client.manage_state({"action": "list"}))
    require(listed.get("status") == "success", "state list failed", listed)
    require(listed.get("count") == 2, "state list count drifted", listed)

    compared = payload(
        client.manage_state(
            {"action": "compare", "file1": first_filename, "file2": second_filename}
        )
    )
    require(compared.get("status") == "success", "state compare failed", compared)
    require(compared.get("processDefinitionsEqual") is True, "definitions differ", compared)
    require("statesEqual" in compared, "state equality diagnostic omitted", compared)

    loaded = payload(client.manage_state({"action": "load", "filename": first_filename}))
    require(loaded.get("status") == "success", "state load failed", loaded)
    restored_session_id = loaded.get("sessionId")
    require(bool(restored_session_id), "load omitted restored session identifier", loaded)
    restored = payload(
        client.manage_session({"action": "getState", "sessionId": restored_session_id})
    )
    require(restored.get("status") == "success", "restored session unavailable", restored)
    require(restored.get("equipmentCount", 0) > 0, "restored process lost equipment", restored)
    return restored_session_id


def test_export_and_fail_closed_paths(client, session_id):
    exported = payload(client.manage_state({"action": "export", "sessionId": session_id}))
    require(exported.get("status") == "success", "state export failed", exported)
    export_doc = exported.get("exportedSession", {})
    require(export_doc.get("format") == "neqsim-exported-session", "export format drifted", exported)

    traversal = payload(client.manage_state({"action": "load", "filename": "../escape.json"}))
    require(traversal.get("status") == "error", "path traversal was accepted", traversal)
    require(error_code(traversal) == "INVALID_PATH", "traversal error code drifted", traversal)

    unknown = payload(client.manage_state({"action": "not-a-state-action"}))
    require(unknown.get("status") == "error", "unknown persistence action was accepted", unknown)
    require(error_code(unknown) == "UNKNOWN_ACTION", "unknown-action code drifted", unknown)


def delete_saved_states(client, filenames):
    for filename in filenames:
        deleted = payload(client.manage_state({"action": "delete", "filename": filename}))
        require(deleted.get("status") == "success", "state delete failed", deleted)
    listed = payload(client.manage_state({"action": "list"}))
    require(listed.get("count") == 0, "deleted states remained visible", listed)


def close_session(client, session_id):
    if session_id:
        client.manage_session({"action": "close", "sessionId": session_id})


def main():
    client = McpClient()
    original_session_id = None
    restored_session_id = None
    try:
        client.start()
        test_current_phase0_boundary(client)
        process, original_session_id = create_canonical_session(client)
        storage = configure_isolated_storage(client)
        filenames = save_two_versions_without_overwrite(
            client, original_session_id, process
        )
        test_saved_envelope(storage, filenames[0], process)
        restored_session_id = list_compare_and_restore(client, *filenames)
        test_export_and_fail_closed_paths(client, original_session_id)
        delete_saved_states(client, filenames)
    finally:
        try:
            if client.proc is not None:
                close_session(client, restored_session_id)
                close_session(client, original_session_id)
        finally:
            client.close()
    print("manageState real-MCP qualification: 7/7 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
