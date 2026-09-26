"""Focused real-MCP qualification for canonical utility-design screening.

The harness executes the packaged server over STDIO. It qualifies deterministic
routing to existing NeqSim utility models and explicit failure evidence only; it
does not claim detailed design, accuracy, standards compliance, or plant authority.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
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
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "neqsim-utility-design-contract-test", "version": "1.0"},
            },
        })
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def call_tool(self, name, arguments):
        self.send({
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "tools/call",
            "params": {"name": name, "arguments": arguments},
        })
        response = self.receive()
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", "{}"))

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
    if condition:
        return
    suffix = "" if detail is None else "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)


def payload(response):
    data = response.get("data") if isinstance(response, dict) else None
    return data if isinstance(data, dict) else response


def design(client, definition):
    return client.call_tool("designUtilities", {"utilityJson": json.dumps(definition)})


def require_success(client, definition, label):
    result = design(client, definition)
    require(result.get("status") == "success", label + " route failed", result)
    require(isinstance(payload(result), dict), label + " result data is missing", result)


def test_five_canonical_utility_routes(client):
    cases = [
        ("boiler", {"utilityType": "boiler", "duties": [{"name": "Reboiler", "dutyKW": 5000.0}]}),
        ("deaerator", {"utilityType": "deaerator", "feedwaterFlowKgh": 12000.0,
                       "feedwaterInletTempC": 85.0, "operatingPressureBara": 1.2}),
        ("refrigeration", {"utilityType": "refrigeration", "dutyKW": 3000.0,
                           "evaporatorTempC": -35.0, "condenserTempC": 35.0}),
        ("nitrogen", {"utilityType": "nitrogen", "nitrogenDemandNm3h": 500.0,
                      "purityPercent": 99.5, "generationMethod": "MEMBRANE"}),
        ("steamNetwork", {"utilityType": "steamNetwork",
                          "levels": [{"name": "HP", "pressureBara": 42.0,
                                      "saturationTempC": 253.0},
                                     {"name": "LP", "pressureBara": 4.5,
                                      "saturationTempC": 148.0}],
                          "demands": [{"level": "HP", "demandKgh": 8000.0},
                                      {"level": "LP", "demandKgh": 5000.0}],
                          "localGeneration": [{"level": "LP", "generationKgh": 2000.0}]}),
    ]
    for label, definition in cases:
        require_success(client, definition, label)


def test_blank_input_fails_closed(client):
    result = client.call_tool("designUtilities", {"utilityJson": ""})
    require(result.get("status") == "error", "blank input did not fail closed", result)


def test_malformed_input_fails_closed(client):
    result = client.call_tool("designUtilities", {"utilityJson": "{"})
    require(result.get("status") == "error", "malformed input did not fail closed", result)


def test_unknown_type_fails_closed(client):
    result = design(client, {"utilityType": "fusion"})
    require(result.get("status") == "error", "unknown utility type did not fail closed", result)


def test_phase0_contract_is_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("designUtilities", {})
    require(
        inventory.get("inventoryVersion") == "1.46"
        and limitations.get("contractTestedToolCount") == 46
        and limitations.get("confirmedGapToolCount") == 5
        and limitations.get("contractPromotionCandidateCount") == 0
        and record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_CANONICAL_UTILITY_DESIGN_SCREENING_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 6
        and "neqsim-mcp-server/test_utility_design_protocol.py"
        in record.get("contractEvidenceSources", []),
        "utility-design promotion drifted",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("five canonical utility routes", test_five_canonical_utility_routes),
        ("blank input fails closed", test_blank_input_fails_closed),
        ("malformed input fails closed", test_malformed_input_fails_closed),
        ("unknown type fails closed", test_unknown_type_fails_closed),
        ("Phase 0 contract is promoted", test_phase0_contract_is_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} utility-design contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
