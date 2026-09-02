"""Focused real-MCP qualification for post-calculation result validation.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO
and qualifies the existing advisory ``validateResults`` route. It verifies the
implemented engineering-rule software contract only. It does not execute a
simulation, generate independent physical evidence, certify conservation, or
grant facility, plant-control, design, or engineering-approval authority.
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
                        "name": "neqsim-validate-results-contract-test",
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

    def validate(self, results, context="general"):
        value = results if isinstance(results, str) else json.dumps(results)
        return self.call_tool(
            "validateResults", {"resultsJson": value, "context": context}
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
        "provenance",
        "validation",
        "qualityGate",
        "warnings",
        "errors",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def finding_codes(result):
    """Return stable machine-readable finding codes."""
    return {
        item.get("code")
        for item in result.get("findings", [])
        if isinstance(item, dict)
    }


def assert_report(response, context):
    """Verify the stable advisory response shape."""
    result = payload(response)
    require(result.get("status") == "success", "validation transport failed", response)
    require(result.get("validationContext") == context, "context was not preserved", result)
    require(isinstance(result.get("passed"), bool), "passed flag is absent", result)
    for field in ("totalFindings", "errors", "warnings", "infos"):
        require(isinstance(result.get(field), int), field + " count is absent", result)
    require(isinstance(result.get("verdict"), str), "verdict is absent", result)
    require(isinstance(result.get("findings"), list), "findings array is absent", result)
    for finding in result["findings"]:
        require(
            {"code", "severity", "message", "remediation"}.issubset(finding),
            "finding record is incomplete",
            finding,
        )
        require(
            finding["severity"] in {"ERROR", "WARNING", "INFO"},
            "finding severity drifted",
            finding,
        )
    return result


def test_clean_result_passes(client):
    result = assert_report(
        client.validate(
            {
                "converged": True,
                "temperature": 25.0,
                "pressure": 50.0,
                "massBalanceError": 0.0,
                "energyBalanceError": 0.0,
            },
            "process",
        ),
        "process",
    )
    require(result.get("passed") is True, "clean result was rejected", result)
    require(result.get("totalFindings") == 0, "clean result produced findings", result)
    require(result.get("verdict", "").startswith("PASS"), "pass verdict drifted", result)


def test_warning_remains_non_blocking(client):
    result = assert_report(
        client.validate({"converged": True, "polytropicEfficiency": 0.40}, "compressor"),
        "compressor",
    )
    require(result.get("passed") is True, "warning blocked the result", result)
    require(result.get("errors") == 0 and result.get("warnings") == 1, "warning counts drifted", result)
    require("LOW_EFFICIENCY" in finding_codes(result), "efficiency warning is absent", result)
    require(
        result.get("verdict", "").startswith("PASS_WITH_WARNINGS"),
        "warning verdict drifted",
        result,
    )


def test_blocking_findings_fail(client):
    result = assert_report(
        client.validate(
            {"converged": False, "pressure": -1.0, "massBalanceError": 0.02},
            "process",
        ),
        "process",
    )
    require(result.get("passed") is False, "blocking result was accepted", result)
    require(result.get("errors") == 3, "blocking error count drifted", result)
    require(
        {"NEGATIVE_PRESSURE", "MASS_BALANCE", "NOT_CONVERGED"}.issubset(
            finding_codes(result)
        ),
        "blocking findings are incomplete",
        result,
    )


def test_nested_fields_are_discovered(client):
    request = {
        "equipment": {
            "compressor": {"outletTemperature": 220.0, "compressionRatio": 5.0}
        }
    }
    first = assert_report(client.validate(request, "process"), "process")
    second = assert_report(client.validate(request, "process"), "process")
    require(first == second, "nested validation is not deterministic", {"first": first, "second": second})
    require(
        {"HIGH_DISCHARGE_TEMP", "HIGH_COMPRESSION_RATIO"}.issubset(
            finding_codes(first)
        ),
        "nested equipment findings are incomplete",
        first,
    )


def test_malformed_json_fails_closed(client):
    result = assert_report(client.validate("{bad json}", "general"), "general")
    require(result.get("passed") is False, "malformed JSON was accepted", result)
    require(result.get("errors") == 1, "parse error count drifted", result)
    require("PARSE_ERROR" in finding_codes(result), "parse finding is absent", result)


def test_phase0_inventory_remains_conservative(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 inventory", result)
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("validateResults", {})
    require(inventory.get("inventoryVersion") == "1.23", "inventory version drifted", inventory)
    require(
        limitations.get("contractTestedToolCount") == 21
        and limitations.get("confirmedGapToolCount") == 30
        and record.get("coverageStatus") == "CONFIRMED_GAP",
        "qualification prematurely promoted validateResults",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("clean result passes", test_clean_result_passes),
        ("warning remains non-blocking", test_warning_remains_non_blocking),
        ("blocking findings fail", test_blocking_findings_fail),
        ("nested fields are discovered", test_nested_fields_are_discovered),
        ("malformed JSON fails closed", test_malformed_json_fails_closed),
        ("Phase 0 inventory remains conservative", test_phase0_inventory_remains_conservative),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} validateResults contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
