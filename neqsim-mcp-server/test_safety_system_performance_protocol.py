"""Focused packaged-MCP qualification for the safety-system-performance contract."""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


def require(condition, message, detail=None):
    if condition:
        return
    suffix = "" if detail is None else "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


class McpClient:
    def __init__(self):
        self.proc = None
        self.message_id = 0

    def send(self, message):
        self.proc.stdin.write(json.dumps(message) + "\n")
        self.proc.stdin.flush()

    def receive(self):
        line = self.proc.stdout.readline()
        require(line, "MCP server closed stdout unexpectedly")
        return json.loads(line)

    def start(self):
        self.proc = subprocess.Popen(
            ["java", "-jar", JAR], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True,
        )
        self.message_id += 1
        self.send({"jsonrpc": "2.0", "id": self.message_id, "method": "initialize",
                   "params": {"protocolVersion": "2025-11-25", "capabilities": {},
                              "clientInfo": {"name": "neqsim-safety-performance-contract-test",
                                             "version": "1.0"}}})
        require("result" in self.receive(), "MCP initialize failed")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call(self, name, arguments):
        self.message_id += 1
        self.send({"jsonrpc": "2.0", "id": self.message_id, "method": "tools/call",
                   "params": {"name": name, "arguments": arguments}})
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP call returned no content", response)
        return json.loads(content[0].get("text", "{}"))

    def list_tools(self):
        self.message_id += 1
        self.send({"jsonrpc": "2.0", "id": self.message_id,
                   "method": "tools/list", "params": {}})
        return self.receive().get("result", {}).get("tools", [])

    def close(self):
        if self.proc is None:
            return
        self.proc.stdin.close()
        try:
            self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            self.proc.wait(timeout=10)


def payload(response):
    data = response.get("data") if isinstance(response, dict) else None
    return data if isinstance(data, dict) else response


def catalog_example(client):
    return client.call("getExample", {
        "category": "safety",
        "name": "safety-system-performance",
    })


def run_performance(client, request):
    return client.call("runSafetySystemPerformance", {
        "safetySystemJson": json.dumps(request),
    })


def test_discovery_boundary(client):
    tool = next(item for item in client.list_tools()
                if item.get("name") == "runSafetySystemPerformance")
    description = tool.get("description", "")
    require("active/passive safety-system performance" in description
            and "quantitative SIL/PFD" in description
            and "SafetySystemPerformanceReport" in description
            and "NORSOK S-001" in description,
            "safety-system discovery boundary drifted", tool)


def test_catalog_report(client):
    response = run_performance(client, catalog_example(client))
    data = payload(response)
    summary = data.get("summary", {})
    require(response.get("status") == "success" and data.get("status") == "success"
            and response.get("validation", {}).get("valid") is True
            and response.get("qualityGate", {}).get("verdict") == "passed",
            "safety-system response envelope failed", response)
    require(summary.get("overallVerdict") == "PASS_WITH_WARNINGS"
            and summary.get("assessmentCount", 0) > 0
            and "assessments" in data.get("performanceReport", {})
            and "NORSOK-S-001" in data.get("standardsTemplates", {})
            and "ISO-13702" in data.get("standardsTemplates", {})
            and "causeAndEffect" in data.get("stidExtractionTemplates", {}),
            "safety-system report or templates drifted", data)


def test_deterministic_replay(client):
    request = catalog_example(client)
    require(run_performance(client, request) == run_performance(client, request),
            "safety-system response is not deterministic")


def test_fail_closed_input(client):
    invalid = client.call("runSafetySystemPerformance", {"safetySystemJson": ""})
    invalid_data = payload(invalid)
    require((invalid.get("status") == "error" or invalid_data.get("status") == "error")
            and invalid.get("validation", {}).get("valid") is False,
            "empty safety-system input did not fail closed", invalid)


def test_inventory_promotion(client):
    capabilities = payload(client.call("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runSafetySystemPerformance", {})
    require(inventory.get("inventoryVersion") == "1.48"
            and limitations.get("contractTestedToolCount") == 48
            and limitations.get("confirmedGapToolCount") == 3
            and limitations.get("contractPromotionCandidateCount") == 0
            and record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("contractEvidenceCount") == 8
            and "neqsim-mcp-server/test_safety_system_performance_protocol.py"
            in record.get("contractEvidenceSources", []),
            "safety-system inventory promotion drifted", limitations)


def main():
    client = McpClient()
    tests = [
        ("discovery boundary", test_discovery_boundary),
        ("catalog report", test_catalog_report),
        ("deterministic replay", test_deterministic_replay),
        ("fail-closed input", test_fail_closed_input),
        ("inventory promotion", test_inventory_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} safety-system-performance contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
