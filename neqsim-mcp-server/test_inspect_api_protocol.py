"""Focused real-MCP qualification for the read-only inspectApi contract.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO and
checks a successful NeqSim API inspection, fail-closed rejection of a non-NeqSim
class, and the atomically promoted Phase 0 contract classification.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC MCP client for exact packaged-server tests."""

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
                    "clientInfo": {"name": "neqsim-inspect-api-test", "version": "1.0"},
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
            raise AssertionError(f"MCP tool returned non-JSON content: {error}: {text}")

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
    """Return domain data while retaining standardized envelope state."""
    data = response.get("data") if isinstance(response, dict) else None
    if not isinstance(data, dict):
        return response
    merged = dict(data)
    for key in ("status", "message", "tool"):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def test_successful_inspection(client):
    result = client.call_tool(
        "inspectApi", {"className": "ProcessModel", "memberFilter": "run"}
    )
    data = payload(result)
    require(data.get("status") == "success", "inspectApi success call failed", result)
    require(
        data.get("resolvedClass") == "neqsim.process.processmodel.ProcessModel",
        "inspectApi resolved an unexpected class",
        result,
    )
    require(data.get("contractBasis") == "runtime-reflection", "missing reflection contract basis", result)
    require(data.get("matchingMethodCount", 0) > 0, "member filter returned no methods", result)
    methods = data.get("methods", [])
    require(
        any(" run(" in item.get("signature", "") for item in methods),
        "member filter did not return a run method",
        result,
    )
    require(
        data.get("sourcePath") == "src/main/java/neqsim/process/processmodel/ProcessModel.java",
        "inspectApi source pointer drifted",
        result,
    )


def test_non_neqsim_target_fails_closed(client):
    result = client.call_tool(
        "inspectApi", {"className": "java.lang.Runtime", "memberFilter": "exec"}
    )
    data = payload(result)
    require(data.get("status") == "error", "non-NeqSim inspection was not rejected", result)
    require(data.get("requested") == "java.lang.Runtime", "rejection lost requested target", result)
    require(
        data.get("message") == "Unable to resolve NeqSim API target",
        "unexpected fail-closed diagnostic",
        result,
    )
    serialized = json.dumps(result)
    require("neqsim.*" in serialized, "rejection does not explain the accepted namespace", result)


def test_contract_classification_is_promoted_atomically(client):
    result = client.call_tool("getCapabilities", {})
    data = payload(result)
    inventory = data.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 evidence inventory", result)
    require(inventory.get("inventoryVersion") == "1.18", "unexpected evidence inventory version", result)
    limitations = inventory.get("knownLimitations", {})
    require(limitations.get("contractTestedToolCount") == 14, "contract-tested count drifted", result)
    require(limitations.get("confirmedGapToolCount") == 37, "confirmed-gap count drifted", result)
    records = limitations.get("coverageRecords", {})
    inspect_record = records.get("inspectApi", {})
    require(
        inspect_record.get("coverageStatus") == "CONTRACT_TESTED",
        "inspectApi coverage was not promoted with primary accounting",
        result,
    )
    require(
        inspect_record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_RUNTIME_API_INSPECTION",
        "inspectApi benchmark-applicability boundary drifted",
        result,
    )
    require(inspect_record.get("contractTrustAvailable") is True, "contract trust flag missing", result)
    require(inspect_record.get("contractEvidenceCount") == 5, "inspectApi evidence count drifted", result)
    require(
        "test_inspect_api_protocol.py" in json.dumps(inspect_record.get("contractEvidenceSources", [])),
        "inspectApi coverage does not cite focused real-MCP evidence",
        result,
    )
    require(
        limitations.get("contractPromotionCandidateCount") == 0
        and not limitations.get("contractPromotionCandidates", {}),
        "completed contract promotions remain queued as candidates",
        result,
    )


def main():
    client = McpClient()
    try:
        client.start()
        test_successful_inspection(client)
        test_non_neqsim_target_fails_closed(client)
        test_contract_classification_is_promoted_atomically(client)
    finally:
        client.close()
    print("inspectApi real-MCP qualification: 3/3 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
