"""Focused packaged-MCP qualification for security-management boundaries.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
with a synthetic administrator token. It qualifies the existing
``manageSecurity`` software contract without claiming transport security,
external identity assurance, persistent secret storage, penetration resistance,
certification, regulatory compliance, or plant-access authority.
"""
import json
import subprocess
import sys
import time

ADMIN_TOKEN = "neqsim-protocol-test-admin"
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
            ["java", "-Dneqsim.mcp.adminToken=" + ADMIN_TOKEN, "-jar", JAR],
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
                        "name": "neqsim-security-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        response = self.receive()
        require("result" in response, "MCP initialize did not return a result", response)
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_tool(self, name, argument_name, value):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "tools/call",
                "params": {
                    "name": name,
                    "arguments": {argument_name: value},
                },
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

    def call_no_arg_tool(self, name):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "tools/call",
                "params": {"name": name, "arguments": {}},
            }
        )
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def security(self, request):
        value = request if isinstance(request, str) else json.dumps(request)
        return self.call_tool("manageSecurity", "securityJson", value)

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


def first_error_code(response):
    """Return the first structured application error code."""
    errors = payload(response).get("errors", [])
    require(errors and isinstance(errors[0], dict), "structured error is absent", response)
    return errors[0].get("code")


def test_default_disabled_status_and_envelope(client):
    response = client.security({"action": "getStatus"})
    result = payload(response)
    require(result.get("status") == "success", "security status failed", result)
    require(result.get("securityEnabled") is False, "security must default disabled", result)
    require(result.get("apiKeyCount") == 0, "fresh process unexpectedly has API keys", result)
    require(isinstance(result.get("auditLogSize"), int), "audit-log count is absent", result)
    require(isinstance(result.get("totalRequests"), int), "request count is absent", result)
    require(isinstance(result.get("activeRateLimits"), int), "rate-limit count is absent", result)
    require(response.get("tool") == "manageSecurity", "tool envelope drifted", response)
    require(
        response.get("validation", {}).get("valid") is True,
        "standard MCP validation is absent",
        response,
    )


def test_disabled_mode_authentication(client):
    result = payload(client.security({"action": "authenticate"}))
    require(result.get("status") == "success", "disabled-mode authentication failed", result)
    require(result.get("authenticated") is True, "desktop caller was not admitted", result)
    require(result.get("securityEnabled") is False, "disabled mode drifted", result)
    require(
        "all requests are allowed" in result.get("message", ""),
        "disabled-mode boundary is absent",
        result,
    )


def test_administrator_gate_and_bootstrap_recovery(client):
    enabled = payload(
        client.security(
            {"action": "setConfig", "enabled": True, "adminToken": ADMIN_TOKEN}
        )
    )
    require(enabled.get("status") == "success", "enabling security failed", enabled)
    require(enabled.get("securityEnabled") is True, "security did not enable", enabled)

    denied = client.security(
        {"action": "createApiKey", "userId": "synthetic-engineer"}
    )
    require(payload(denied).get("status") == "error", "unprivileged key minting succeeded", denied)
    require(
        first_error_code(denied) == "ADMIN_REQUIRED",
        "administrator denial code drifted",
        denied,
    )

    status = payload(client.security({"action": "getStatus"}))
    require(status.get("status") == "success", "bootstrap status became unreachable", status)
    require(status.get("securityEnabled") is True, "enabled status drifted", status)

    disabled = payload(
        client.security(
            {"action": "setConfig", "enabled": False, "adminToken": ADMIN_TOKEN}
        )
    )
    require(disabled.get("status") == "success", "administrator recovery failed", disabled)
    require(disabled.get("securityEnabled") is False, "security did not disable", disabled)
    require(
        ADMIN_TOKEN not in json.dumps(enabled)
        and ADMIN_TOKEN not in json.dumps(denied)
        and ADMIN_TOKEN not in json.dumps(disabled),
        "administrator token was echoed",
    )


def test_fail_closed_requests(client):
    cases = (
        ("{bad json}", "SECURITY_ERROR"),
        ("[]", "SECURITY_ERROR"),
        ({"action": "unknown"}, "UNKNOWN_ACTION"),
        ({"action": "createApiKey"}, "MISSING_USER"),
    )
    for request, expected_code in cases:
        response = client.security(request)
        result = payload(response)
        require(result.get("status") == "error", "invalid security request was accepted", result)
        require(
            first_error_code(response) == expected_code,
            "security error code drifted",
            response,
        )


def test_process_local_operational_views(client):
    audit = payload(client.security({"action": "getAuditLog", "limit": 5}))
    require(audit.get("status") == "success", "audit-log query failed", audit)
    require(isinstance(audit.get("entries"), list), "audit entries are absent", audit)
    require(isinstance(audit.get("totalEntries"), int), "audit total is absent", audit)
    require(isinstance(audit.get("returnedCount"), int), "audit return count is absent", audit)
    require(audit.get("returnedCount") <= 5, "audit limit was ignored", audit)

    limits = payload(client.security({"action": "getRateLimits"}))
    require(limits.get("status") == "success", "rate-limit query failed", limits)
    require(limits.get("securityEnabled") is False, "security state drifted", limits)
    require(limits.get("defaultRateLimit") == 60, "default rate limit drifted", limits)
    require(isinstance(limits.get("users"), list), "rate-limit users are absent", limits)


def test_phase0_inventory_remains_unpromoted(client):
    result = payload(client.call_no_arg_tool("getCapabilities"))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    limitations = inventory.get("knownLimitations", {})
    records = limitations.get("coverageRecords", {})
    record = records.get("manageSecurity", {})
    require(inventory.get("inventoryVersion") == "1.25", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 24
        and limitations.get("confirmedGapToolCount") == 27,
        "qualification changed inventory accounting",
        limitations,
    )
    require(
        record.get("coverageStatus") == "CONFIRMED_GAP",
        "qualification prematurely promoted manageSecurity",
        record,
    )


def main():
    client = McpClient()
    tests = [
        ("default-disabled status and envelope", test_default_disabled_status_and_envelope),
        ("disabled-mode authentication", test_disabled_mode_authentication),
        ("administrator gate and bootstrap recovery", test_administrator_gate_and_bootstrap_recovery),
        ("fail-closed requests", test_fail_closed_requests),
        ("process-local operational views", test_process_local_operational_views),
        ("Phase 0 classification remains unpromoted", test_phase0_inventory_remains_unpromoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} security contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
