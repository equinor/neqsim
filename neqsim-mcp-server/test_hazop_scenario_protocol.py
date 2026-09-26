"""Focused packaged-MCP qualification for the simulation-backed HAZOP scenario contract."""
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
                              "clientInfo": {"name": "neqsim-hazop-scenario-contract-test",
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


def definition(node_tag="2nd Stage"):
    return {
        "nodeTag": node_tag,
        "guideWord": "MORE",
        "parameter": "TEMPERATURE",
        "limits": {
            "maxDischargeTemperatureC": 150.0,
            "maxDischargeTemperatureByUnit": {"2nd Stage": 170.0},
        },
        "process": {
            "fluid": {
                "model": "SRK",
                "temperature": 298.15,
                "pressure": 10.0,
                "mixingRule": "classic",
                "components": {"methane": 0.90, "ethane": 0.07, "propane": 0.03},
            },
            "process": [
                {"type": "Stream", "name": "feed",
                 "properties": {"flowRate": [5000.0, "kg/hr"]}},
                {"type": "Compressor", "name": "2nd Stage", "inlet": "feed",
                 "properties": {"outletPressure": [80.0, "bara"]}},
            ],
        },
    }


def run_scenario(client, request):
    return client.call("runHazopScenario", {"scenarioJson": json.dumps(request)})


def test_discovery_boundary(client):
    tool = next(item for item in client.list_tools() if item.get("name") == "runHazopScenario")
    description = tool.get("description", "")
    require("single HAZOP deviation" in description and "ProcessSystem" in description
            and "computed value" in description and "design limit" in description
            and "governing standard" in description and "auditable basis" in description,
            "HAZOP scenario discovery boundary drifted", tool)


def test_quantified_finding(client):
    response = run_scenario(client, definition())
    data = payload(response)
    findings = data.get("findings", [])
    require(response.get("status") == "success" and data.get("status") == "ok"
            and data.get("schemaVersion") == "1.0" and data.get("matchCount", 0) >= 1
            and findings and findings[0].get("guideWord") == "MORE"
            and findings[0].get("parameter") == "TEMPERATURE"
            and findings[0].get("standardReference")
            and findings[0].get("limitBasis"),
            "quantified HAZOP scenario contract failed", response)


def test_deterministic_replay(client):
    request = definition()
    require(run_scenario(client, request) == run_scenario(client, request),
            "HAZOP scenario response is not deterministic")


def test_no_match_and_fail_closed_input(client):
    no_match = payload(run_scenario(client, definition("does-not-exist")))
    require(no_match.get("status") == "ok" and no_match.get("matchCount") == 0
            and no_match.get("note"), "HAZOP no-match evidence was hidden", no_match)
    invalid = client.call("runHazopScenario", {"scenarioJson": ""})
    invalid_data = payload(invalid)
    require(invalid.get("status") == "error" or invalid_data.get("status") == "error",
            "empty HAZOP scenario input did not fail closed", invalid)


def test_inventory_promotion(client):
    capabilities = payload(client.call("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runHazopScenario", {})
    require(inventory.get("inventoryVersion") == "1.47"
            and limitations.get("contractTestedToolCount") == 47
            and limitations.get("confirmedGapToolCount") == 4
            and limitations.get("contractPromotionCandidateCount") == 0
            and record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("contractEvidenceCount") == 7
            and "neqsim-mcp-server/test_hazop_scenario_protocol.py"
            in record.get("contractEvidenceSources", []),
            "HAZOP scenario inventory promotion drifted", limitations)


def main():
    client = McpClient()
    tests = [
        ("discovery boundary", test_discovery_boundary),
        ("quantified finding", test_quantified_finding),
        ("deterministic replay", test_deterministic_replay),
        ("no-match and fail-closed input", test_no_match_and_fail_closed_input),
        ("inventory promotion", test_inventory_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} HAZOP-scenario contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
