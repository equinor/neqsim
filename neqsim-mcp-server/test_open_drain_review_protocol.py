"""Focused packaged-MCP qualification for the open-drain-review contract."""
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
                              "clientInfo": {"name": "neqsim-open-drain-contract-test",
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
        "category": "open-drain-review",
        "name": "norsok-s001-stid",
    })


def run_review(client, request):
    return client.call("runOpenDrainReview", {
        "openDrainReviewJson": json.dumps(request),
    })


def test_discovery_boundary(client):
    tool = next(item for item in client.list_tools()
                if item.get("name") == "runOpenDrainReview")
    description = tool.get("description", "")
    require("NORSOK S-001 Clause 9" in description
            and "normalized STID/P&ID evidence" in description
            and "tagreader/historian evidence" in description
            and "does not connect directly" in description,
            "open-drain discovery boundary drifted", tool)


def test_catalog_report(client):
    response = run_review(client, catalog_example(client))
    data = payload(response)
    require(response.get("status") == "success"
            and response.get("validation", {}).get("valid") is True
            and response.get("qualityGate", {}).get("verdict") == "passed",
            "open-drain response envelope failed", response)
    require(data.get("reviewType") == "open_drain_review"
            and data.get("overallVerdict") == "PASS"
            and data.get("itemCount") == 2
            and len(data.get("results", [])) == 2
            and "NORSOK S-001:2020+AC:2021 Clause 9"
            in data.get("standardsApplied", [])
            and response.get("provenance", {}).get("calculationType")
            == "open drain review",
            "open-drain report or provenance drifted", response)


def test_deterministic_replay(client):
    request = catalog_example(client)
    require(run_review(client, request) == run_review(client, request),
            "open-drain response is not deterministic")


def test_fail_closed_input(client):
    invalid = client.call("runOpenDrainReview", {"openDrainReviewJson": ""})
    invalid_data = payload(invalid)
    require((invalid.get("status") == "error" or invalid_data.get("status") == "error")
            and invalid.get("validation", {}).get("valid") is False,
            "empty open-drain input did not fail closed", invalid)


def test_inventory_promotion(client):
    capabilities = payload(client.call("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runOpenDrainReview", {})
    require(inventory.get("inventoryVersion") == "1.49"
            and limitations.get("contractTestedToolCount") == 49
            and limitations.get("confirmedGapToolCount") == 2
            and limitations.get("contractPromotionCandidateCount") == 0
            and record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("contractEvidenceCount") == 7
            and "neqsim-mcp-server/test_open_drain_review_protocol.py"
            in record.get("contractEvidenceSources", []),
            "open-drain inventory promotion drifted", limitations)


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
    print(f"\n{len(tests)}/{len(tests)} open-drain-review contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
