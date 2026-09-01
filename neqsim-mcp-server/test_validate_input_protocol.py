"""Focused real-MCP qualification for pre-flight input validation.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies the existing advisory ``validateInput`` route. It verifies input
classification and issue reporting only: no simulation is executed, and the
contract does not establish convergence, conservation, numerical fidelity,
facility authority, or engineering approval.
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
                        "name": "neqsim-validate-input-contract-test",
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

    def validate(self, definition):
        value = definition if isinstance(definition, str) else json.dumps(definition)
        return self.call_tool("validateInput", {"inputJson": value})

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
        "provenance",
        "validation",
        "qualityGate",
        "warnings",
        "errors",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def issue_by_code(result, code):
    """Return one issue record by stable code."""
    for issue in result.get("issues", []):
        if isinstance(issue, dict) and issue.get("code") == code:
            return issue
    return None


def assert_validation(result):
    """Verify the stable advisory response shape."""
    data = payload(result)
    require(data.get("status") == "success", "validation transport failed", result)
    require(isinstance(data.get("valid"), bool), "valid flag is absent", data)
    require(isinstance(data.get("issues"), list), "issues array is absent", data)
    for issue in data["issues"]:
        require(isinstance(issue, dict), "issue is not an object", issue)
        require(
            {"severity", "code", "message", "remediation"}.issubset(issue),
            "issue record is incomplete",
            issue,
        )
        require(
            issue["severity"] in {"error", "warning"},
            "issue severity is unsupported",
            issue,
        )
    return data


def canonical_process(client):
    """Retrieve the canonical process fixture through MCP."""
    example = client.call_tool(
        "getExample", {"category": "process", "name": "simple-separation"}
    )
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "simple-separation example is not an object", example)
    require("process" in example, "simple-separation example omitted process", example)
    return example


def test_process_and_handle_equivalence(client):
    process = canonical_process(client)
    direct = assert_validation(client.validate(process))
    require(direct.get("valid") is True, "canonical process was rejected", direct)

    registered = payload(
        client.manage_model(
            {
                "action": "register",
                "name": "phase0-validate-input",
                "version": "1.0.0",
                "processJson": process,
            }
        )
    )
    require(registered.get("status") == "success", "model registration failed", registered)
    model_id = registered.get("modelId")
    require(isinstance(model_id, str) and model_id, "registration omitted modelId", registered)
    try:
        handled = assert_validation(client.validate(model_id))
        require(
            handled.get("valid") == direct.get("valid")
            and handled.get("issues") == direct.get("issues"),
            "direct and model-handle validation differ",
            {"direct": direct, "handled": handled},
        )
    finally:
        deleted = payload(client.manage_model({"action": "delete", "modelId": model_id}))
        require(deleted.get("status") == "success", "model cleanup failed", deleted)


def test_flash_with_explicit_units(client):
    flash = {
        "model": "SRK",
        "flashType": "TP",
        "temperature": {"value": 25.0, "unit": "C"},
        "pressure": {"value": 50.0, "unit": "bara"},
        "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
    }
    result = assert_validation(client.validate(flash))
    require(result.get("valid") is True, "valid flash was rejected", result)
    require(not result.get("issues"), "valid flash returned issues", result)


def test_malformed_input_fails_closed(client):
    result = assert_validation(client.validate("{bad json}"))
    issue = issue_by_code(result, "JSON_PARSE_ERROR")
    require(result.get("valid") is False, "malformed JSON was accepted", result)
    require(issue is not None and issue.get("severity") == "error", "parse error drifted", result)


def test_component_typo_has_remediation(client):
    result = assert_validation(client.validate({"components": {"metane": 1.0}}))
    issue = issue_by_code(result, "UNKNOWN_COMPONENT")
    require(result.get("valid") is False, "unknown component was accepted", result)
    require(issue is not None, "unknown-component issue is absent", result)
    require("methane" in issue.get("message", ""), "component suggestion drifted", issue)
    require(bool(issue.get("remediation")), "component remediation is empty", issue)


def test_warning_does_not_invalidate(client):
    result = assert_validation(
        client.validate({"components": {"methane": 0.5, "ethane": 0.3}})
    )
    issue = issue_by_code(result, "COMPOSITION_SUM")
    require(result.get("valid") is True, "warning incorrectly invalidated input", result)
    require(issue is not None and issue.get("severity") == "warning", "warning drifted", result)


def test_phase0_gap_is_preserved(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    require(inventory.get("inventoryVersion") == "1.22", "inventory version drifted", inventory)
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("validateInput", {})
    require(
        limitations.get("contractTestedToolCount") == 20
        and limitations.get("confirmedGapToolCount") == 31
        and limitations.get("contractPromotionCandidateCount") == 0
        and record.get("coverageStatus") == "CONFIRMED_GAP",
        "validateInput was promoted before prerequisite evidence merged",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("process and handle equivalence", test_process_and_handle_equivalence),
        ("flash with explicit units", test_flash_with_explicit_units),
        ("malformed input fails closed", test_malformed_input_fails_closed),
        ("component typo has remediation", test_component_typo_has_remediation),
        ("warning does not invalidate", test_warning_does_not_invalidate),
        ("Phase 0 gap is preserved", test_phase0_gap_is_preserved),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} validateInput contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
