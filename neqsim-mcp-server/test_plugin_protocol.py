"""Focused packaged-MCP qualification for process-local plugin execution.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies empty-catalog discovery, default-list behavior, absent-plugin
diagnostics, invalid input handling, standard envelopes, and real transport.
It does not establish plugin provenance, installation, signing, sandboxing,
resource or tenant isolation, persistence, external IAM, transport security,
scientific validity, plant authority, certification, or engineering approval.
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
                        "name": "neqsim-plugin-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require("result" in response, "MCP initialize did not return a result", response)
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_plugin(self, request):
        return self.call_tool("runPlugin", {"pluginJson": json.dumps(request)})

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
        "message",
        "provenance",
        "validation",
        "qualityGate",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def assert_success(response):
    """Require a successful standard runPlugin envelope."""
    result = payload(response)
    require(result.get("status") == "success", "plugin request failed", response)
    require(result.get("tool") == "runPlugin", "tool identity drifted", response)
    require(
        result.get("validation", {}).get("valid") is True,
        "plugin response lacks valid standard evidence",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "plugin response did not pass its software gate",
        response,
    )
    return result


def assert_error(response):
    """Require invalid or unavailable plugin input to fail closed."""
    result = payload(response)
    require(result.get("status") == "error", "plugin request did not fail closed", response)
    return result


def test_empty_catalog_discovery(client):
    result = assert_success(client.call_plugin({"action": "list"}))
    require(result.get("count") == 0, "fresh server plugin count drifted", result)
    require(result.get("plugins") == [], "fresh server plugin catalog is not empty", result)


def test_missing_action_defaults_to_list(client):
    result = assert_success(client.call_plugin({}))
    require(result.get("count") == 0, "default list count drifted", result)
    require(result.get("plugins") == [], "default list catalog drifted", result)


def test_absent_plugin_returns_structured_diagnostic(client):
    result = assert_error(
        client.call_plugin(
            {"action": "run", "pluginName": "missing", "input": {"value": 1}}
        )
    )
    errors = result.get("errors", [])
    require(errors, "missing plugin returned no diagnostic", result)
    require(errors[0].get("code") == "PLUGIN_NOT_FOUND", "error code drifted", result)
    require("missing" in errors[0].get("message", ""), "plugin name missing", result)
    require(
        "none registered" in errors[0].get("remediation", ""),
        "empty-catalog remediation drifted",
        result,
    )


def test_empty_plugin_name_fails_closed(client):
    result = assert_error(client.call_plugin({"action": "run", "input": {}}))
    errors = result.get("errors", [])
    require(
        errors and errors[0].get("code") == "PLUGIN_NOT_FOUND",
        "empty plugin name diagnostic drifted",
        result,
    )


def test_unknown_action_fails_closed(client):
    result = assert_error(client.call_plugin({"action": "install"}))
    require(
        "Unknown plugin action" in result.get("message", ""),
        "unknown-action remediation drifted",
        result,
    )


def test_malformed_input_fails_closed(client):
    result = assert_error(client.call_tool("runPlugin", {"pluginJson": "{"}))
    require(
        "Plugin operation failed" in result.get("message", ""),
        "malformed-input diagnostic drifted",
        result,
    )


def test_inventory_promotion(client):
    response = client.call_tool("getCapabilities", {})
    result = payload(response)
    require(result.get("status") == "success", "capability request failed", response)
    require(result.get("tool") == "getCapabilities", "capability tool identity drifted", response)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    plugin = limitations.get("coverageRecords", {}).get("runPlugin", {})
    require(inventory.get("inventoryVersion") == "1.30", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 30
        and limitations.get("confirmedGapToolCount") == 21,
        "plugin promotion accounting drifted",
        limitations,
    )
    require(
        limitations.get("contractPromotionCandidateCount") == 0,
        "promotion candidate remained queued",
        limitations,
    )
    require(plugin.get("coverageStatus") == "CONTRACT_TESTED", "runPlugin was not promoted", plugin)
    require(
        plugin.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_PROCESS_LOCAL_PLUGIN_EXECUTION",
        "plugin applicability drifted",
        plugin,
    )
    require(
        "neqsim-mcp-server/test_plugin_protocol.py"
        in plugin.get("contractEvidenceSources", [])
        and "plugin provenance" in plugin.get("evidenceBoundary", ""),
        "plugin evidence or boundary drifted",
        plugin,
    )


def main():
    client = McpClient()
    tests = [
        ("empty catalog discovery", test_empty_catalog_discovery),
        ("missing action defaults to list", test_missing_action_defaults_to_list),
        (
            "absent plugin returns structured diagnostic",
            test_absent_plugin_returns_structured_diagnostic,
        ),
        ("empty plugin name fails closed", test_empty_plugin_name_fails_closed),
        ("unknown action fails closed", test_unknown_action_fails_closed),
        ("malformed input fails closed", test_malformed_input_fails_closed),
        ("inventory promotion", test_inventory_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} plugin contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
