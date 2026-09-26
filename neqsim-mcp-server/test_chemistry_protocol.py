"""Focused real-MCP qualification for canonical chemistry dispatch.

The harness executes the packaged server over STDIO. It qualifies deterministic
routing to existing NeqSim chemistry models, response envelopes, and explicit
failure behavior only; it does not claim model accuracy, applicability, safety,
standards compliance, or plant authority.
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
                "clientInfo": {"name": "neqsim-chemistry-contract-test", "version": "1.0"},
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


def chemistry(client, definition):
    return client.call_tool("runChemistry", {"chemistryJson": json.dumps(definition)})


def require_success(client, definition, label):
    result = chemistry(client, definition)
    require(result.get("status") == "success", label + " route failed", result)
    require(isinstance(payload(result), dict), label + " result data is missing", result)


def test_eight_canonical_routes(client):
    cases = [
        ("electrolyteScale", {
            "analysis": "electrolyteScale", "temperature_C": 60.0, "pH": 7.5,
            "pCO2_bar": 1.0, "ca_mgL": 600.0, "hco3_mgL": 300.0,
        }),
        ("multiMineralScale", {
            "analysis": "multiMineralScale", "temperature_C": 60.0,
            "pressure_bara": 50.0, "ba_mgL": 100.0, "so4_mgL": 500.0,
            "na_mgL": 20000.0, "cl_mgL": 30000.0,
        }),
        ("mechanisticCorrosion", {
            "analysis": "mechanisticCorrosion", "temperature_C": 60.0,
            "pressure_bara": 80.0, "co2_mol": 0.05, "velocity_ms": 2.0,
            "diameter_m": 0.15, "dose_mgL": 50.0,
        }),
        ("langmuirInhibitor", {
            "analysis": "langmuirInhibitor", "temperature_C": 60.0,
            "dose_mgL": 50.0, "targetEfficiency": 0.5,
        }),
        ("packedBedScavenger", {
            "analysis": "packedBedScavenger", "diameter_m": 0.5,
            "height_m": 2.0, "k_per_s": 8.0, "cInlet_molm3": 1.0,
            "flow_m3s": 0.005, "nCells": 20, "nTimeSteps": 50,
            "simTime_s": 864000.0,
        }),
        ("electrolyteScaleEquilibrium", {
            "analysis": "electrolyteScaleEquilibrium", "model": "pitzer",
            "dataset": "phreeqc-ca-mg-cl-so4", "temperature_K": 298.15,
            "pressure_bara": 1.01325, "mineral": "CaSO4_A",
            "components": {"water": 55.508, "Na+": 1.0, "Ca++": 0.2,
                           "Mg++": 0.0, "Cl-": 1.0, "SO4--": 0.2},
        }),
        ("electrolyteMultiScaleEquilibrium", {
            "analysis": "electrolyteMultiScaleEquilibrium", "model": "pitzer",
            "dataset": "phreeqc-catalog", "temperature_K": 298.15,
            "pressure_bara": 1.01325, "minerals": ["CaSO4_A", "CaSO4_G"],
            "components": {"water": 55.508, "Na+": 1.0, "Ca++": 0.2,
                           "Mg++": 0.15, "Cl-": 1.3, "SO4--": 0.2},
        }),
        ("pitzerQualification", {
            "analysis": "pitzerQualification", "temperature_K": 298.15,
            "pressure_bara": 1.01325, "dataset": "phreeqc-na-k-cl",
            "validationTarget": "AQUEOUS_ACTIVITY_COEFFICIENTS",
            "components": {"water": 55.508, "Na+": 0.5,
                           "K+": 0.5, "Cl-": 1.0},
        }),
    ]
    for label, definition in cases:
        require_success(client, definition, label)


def test_blank_input_fails_closed(client):
    result = client.call_tool("runChemistry", {"chemistryJson": ""})
    require(result.get("status") == "error", "blank input did not fail closed", result)


def test_malformed_input_fails_closed(client):
    result = client.call_tool("runChemistry", {"chemistryJson": "{"})
    require(result.get("status") == "error", "malformed input did not fail closed", result)


def test_unknown_analysis_fails_closed(client):
    result = chemistry(client, {"analysis": "alchemy"})
    require(result.get("status") == "error", "unknown analysis did not fail closed", result)


def test_phase0_contract_is_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runChemistry", {})
    require(
        inventory.get("inventoryVersion") == "1.46"
        and limitations.get("contractTestedToolCount") == 46
        and limitations.get("confirmedGapToolCount") == 5
        and limitations.get("contractPromotionCandidateCount") == 0
        and record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_CANONICAL_CHEMISTRY_DISPATCH_AND_TRANSPORT_SOFTWARE_CONTRACT"
        and record.get("contractEvidenceCount") == 7
        and "neqsim-mcp-server/test_chemistry_protocol.py"
        in record.get("contractEvidenceSources", []),
        "chemistry promotion drifted",
        limitations,
    )


def main():
    client = McpClient()
    tests = [
        ("eight canonical chemistry routes", test_eight_canonical_routes),
        ("blank input fails closed", test_blank_input_fails_closed),
        ("malformed input fails closed", test_malformed_input_fails_closed),
        ("unknown analysis fails closed", test_unknown_analysis_fails_closed),
        ("Phase 0 contract is promoted", test_phase0_contract_is_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} chemistry contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
