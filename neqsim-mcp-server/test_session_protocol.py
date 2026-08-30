"""Focused real-MCP qualification for the stateful session lifecycle.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO and
qualifies bounded lifecycle behavior for ``manageSession`` using a canonical
NeqSim process definition retrieved from the MCP example catalog. It is
software-contract and transport evidence only: it does not establish numerical
accuracy, convergence quality, persistence across server restart, multi-process
coherence, live-plant authority, or engineering approval.
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
                    "clientInfo": {"name": "neqsim-session-contract-test", "version": "1.0"},
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
    code = response.get("code")
    if isinstance(code, str):
        return code
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
    """Freeze the atomic promoted accounting without overstating maturity."""
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    require(inventory.get("inventoryVersion") == "1.20", "unexpected inventory version", inventory)
    limitations = inventory.get("knownLimitations", {})
    require(
        limitations.get("contractTestedToolCount") == 18
        and limitations.get("confirmedGapToolCount") == 33,
        "promoted trust accounting drifted",
        limitations,
    )
    record = limitations.get("coverageRecords", {}).get("manageSession", {})
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_SESSION_LIFECYCLE"
        and any(
            source.endswith("SessionRunnerContractTest.java")
            for source in record.get("contractEvidenceSources", [])
        )
        and any(
            source.endswith("test_session_protocol.py")
            for source in record.get("contractEvidenceSources", [])
        ),
        "manageSession promotion evidence drifted",
        record,
    )


def test_create_from_canonical_process(client):
    process = simple_process(client)
    result = payload(
        client.manage_session(
            {
                "action": "create",
                "name": "phase0-session-contract",
                "processJson": process,
            }
        )
    )
    require(result.get("status") == "success", "manageSession create failed", result)
    session_id = result.get("sessionId")
    require(
        isinstance(session_id, str) and session_id,
        "session create omitted stable session identifier",
        result,
    )
    require(result.get("name") == "phase0-session-contract", "session name drifted", result)
    require(result.get("ownerId") == "anonymous", "STDIO owner boundary drifted", result)
    require(
        isinstance(result.get("equipmentCount"), int) and result.get("equipmentCount") > 0,
        "canonical process did not populate session equipment",
        result,
    )
    return session_id


def test_list_and_state(client, session_id):
    listed = payload(client.manage_session({"action": "list"}))
    require(listed.get("status") == "success", "manageSession list failed", listed)
    sessions = listed.get("sessions", [])
    require(isinstance(sessions, list), "session list is not an array", listed)
    require(
        any(item.get("sessionId") == session_id for item in sessions if isinstance(item, dict)),
        "created session is absent from caller-visible list",
        listed,
    )

    state = payload(
        client.manage_session({"action": "getState", "sessionId": session_id})
    )
    require(state.get("status") == "success", "manageSession getState failed", state)
    require(state.get("sessionId") == session_id, "session state lost identity", state)
    require(state.get("ownerId") == "anonymous", "session state lost owner", state)
    require(state.get("hasRun") is True, "process-backed session did not retain solved state", state)
    require(
        isinstance(state.get("equipmentCount"), int) and state.get("equipmentCount") > 0,
        "session state lost canonical equipment",
        state,
    )


def test_invalid_action_fails_closed(client):
    invalid = payload(client.manage_session({"action": "not-a-session-action"}))
    require(invalid.get("status") == "error", "unknown session action was accepted", invalid)
    require(error_code(invalid) == "UNKNOWN_ACTION", "unknown-action code drifted", invalid)


def test_close_invalidates_session(client, session_id):
    closed = payload(
        client.manage_session({"action": "close", "sessionId": session_id})
    )
    require(closed.get("status") == "success", "manageSession close failed", closed)

    stale = payload(
        client.manage_session({"action": "getState", "sessionId": session_id})
    )
    require(stale.get("status") == "error", "closed session remained retrievable", stale)
    require(error_code(stale) == "SESSION_NOT_FOUND", "closed-session code drifted", stale)


def main():
    client = McpClient()
    session_id = None
    try:
        client.start()
        test_current_phase0_boundary(client)
        session_id = test_create_from_canonical_process(client)
        test_list_and_state(client, session_id)
        test_invalid_action_fails_closed(client)
        test_close_invalidates_session(client, session_id)
        session_id = None
    finally:
        try:
            if client.proc is not None and session_id is not None:
                client.manage_session({"action": "close", "sessionId": session_id})
        finally:
            client.close()
    print("manageSession real-MCP qualification: 5/5 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
