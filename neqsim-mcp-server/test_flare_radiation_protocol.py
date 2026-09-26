"""Focused packaged-MCP qualification for bounded flare-radiation screening."""
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
                              "clientInfo": {"name": "neqsim-flare-contract-test", "version": "1.0"}}})
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


def flare(client, definition):
    return client.call("runFlareNetwork", {"flareJson": json.dumps(definition)})


def test_discovery_boundary(client):
    client.message_id += 1
    client.send({"jsonrpc": "2.0", "id": client.message_id, "method": "tools/list", "params": {}})
    tools = client.receive().get("result", {}).get("tools", [])
    tool = next(item for item in tools if item.get("name") == "runFlareNetwork")
    description = tool.get("description", "")
    require("16384 UTF-8 bytes" in description and "200 positive finite distances" in description
            and "screening only" in description and "standards conformance" in description
            and "qualified engineering review" in description,
            "flare discovery boundary drifted", tool)


def test_canonical_profile(client):
    result = flare(client, {"heatDuty_MW": 50.0, "flameHeight_m": 40.0,
                            "radiantFraction": 0.2, "distances_m": [20.0, 50.0, 100.0]})
    data = payload(result)
    require(result.get("status") == "success" and len(data.get("radiationProfile", [])) == 3
            and len(data.get("safeDistanceContour", [])) == 4
            and data.get("screeningOnly") is True
            and data.get("standardConformanceClaimed") is False
            and data.get("engineeringReviewRequired") is True,
            "canonical flare profile failed", result)


def test_deterministic_replay(client):
    definition = {"heatDuty_W": 20000000.0, "distances_m": [20.0, 50.0, 100.0]}
    require(flare(client, definition) == flare(client, definition),
            "flare response is not deterministic")


def test_fail_closed_bounds(client):
    cases = [
        ({}, "INVALID_HEAT_DUTY"),
        ({"heatDuty_W": 0}, "INVALID_HEAT_DUTY"),
        ({"heatDuty_W": 1, "radiantFraction": 2}, "INVALID_RADIANT_FRACTION"),
        ({"heatDuty_W": 1, "distances_m": []}, "INVALID_DISTANCES"),
        ({"heatDuty_W": 1, "distances_m": [-1]}, "INVALID_DISTANCE"),
    ]
    for definition, code in cases:
        response = flare(client, definition)
        require(response.get("status") == "error" and payload(response).get("errorCode") == code,
                "flare bound did not fail closed", response)
    oversized = client.call("runFlareNetwork", {"flareJson": json.dumps(
        {"heatDuty_W": 1, "padding": "x" * 17000})})
    require(payload(oversized).get("errorCode") == "REQUEST_TOO_LARGE",
            "oversized flare request was admitted", oversized)


def test_inventory_promotion(client):
    capabilities = payload(client.call("getCapabilities", {}))
    inventory = capabilities.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get("runFlareNetwork", {})
    require(inventory.get("inventoryVersion") == "1.47"
            and limitations.get("contractTestedToolCount") == 47
            and limitations.get("confirmedGapToolCount") == 4
            and limitations.get("contractPromotionCandidateCount") == 0
            and record.get("coverageStatus") == "CONTRACT_TESTED"
            and record.get("contractEvidenceCount") == 7
            and "neqsim-mcp-server/test_flare_radiation_protocol.py"
            in record.get("contractEvidenceSources", []),
            "flare inventory promotion drifted", limitations)


def main():
    client = McpClient()
    tests = [
        ("discovery boundary", test_discovery_boundary),
        ("canonical profile", test_canonical_profile),
        ("deterministic replay", test_deterministic_replay),
        ("fail-closed bounds", test_fail_closed_bounds),
        ("inventory promotion", test_inventory_promotion),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} flare-radiation contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
