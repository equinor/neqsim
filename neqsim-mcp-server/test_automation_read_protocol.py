"""Focused real-MCP qualification for read-only automation introspection contracts.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO and
checks listSimulationUnits, listUnitVariables, and getSimulationVariable against
the canonical simple-separation example. It qualifies software-contract and
transport behavior only; it does not validate the numerical accuracy of the
solved process or returned variable values.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"


class McpClient:
    """Minimal line-delimited JSON-RPC MCP client for packaged-server tests."""

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
                    "clientInfo": {"name": "neqsim-automation-read-test", "version": "1.0"},
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
    """Return canonical data while retaining standardized envelope state."""
    data = response.get("data") if isinstance(response, dict) else None
    if not isinstance(data, dict):
        return response
    merged = dict(data)
    for key in ("status", "message", "tool", "errors", "provenance", "validation", "qualityGate"):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def simple_process_json(client):
    """Retrieve the canonical catalog fixture through MCP instead of duplicating it here."""
    example = client.call_tool("getExample", {"category": "process", "name": "simple-separation"})
    if isinstance(example, dict) and isinstance(example.get("data"), dict):
        example = example["data"]
    require(isinstance(example, dict), "simple-separation example is not a JSON object", example)
    require("process" in example, "simple-separation example omitted process definition", example)
    return json.dumps(example)


def test_unit_inventory(client, process_json):
    result = client.call_tool("listSimulationUnits", {"processJson": process_json})
    data = payload(result)
    require(data.get("status") == "success", "listSimulationUnits failed", result)
    require(data.get("tool") == "listSimulationUnits", "unit inventory tool identity drifted", result)
    units = data.get("units", [])
    require(isinstance(units, list) and units, "unit inventory is empty", result)
    require(data.get("count") == len(units), "unit count does not match payload", result)
    for unit in units:
        require(unit.get("name"), "unit inventory contains blank name", result)
        require(unit.get("type"), "unit inventory contains blank type", result)


def test_variable_inventory(client, process_json):
    result = client.call_tool(
        "listUnitVariables", {"processJson": process_json, "unitName": "HP Sep"}
    )
    data = payload(result)
    require(data.get("status") == "success", "listUnitVariables failed", result)
    require(data.get("tool") == "listUnitVariables", "variable inventory tool identity drifted", result)
    require(data.get("unitName") == "HP Sep", "variable inventory lost requested unit", result)
    variables = data.get("variables", [])
    require(isinstance(variables, list) and variables, "variable inventory is empty", result)
    require(data.get("count") == len(variables), "variable count does not match payload", result)
    for variable in variables:
        require(variable.get("address"), "variable inventory contains blank address", result)
        require(variable.get("type"), "variable inventory contains blank type", result)
        require("writable" in variable, "variable metadata omitted writable flag", result)
        require("applicability" in variable, "variable metadata omitted applicability", result)


def test_variable_read(client, process_json):
    result = client.call_tool(
        "getSimulationVariable",
        {
            "processJson": process_json,
            "address": "HP Sep.gasOutStream.temperature",
            "unit": "C",
        },
    )
    data = payload(result)
    require(data.get("status") == "success", "getSimulationVariable failed", result)
    require(data.get("tool") == "getSimulationVariable", "variable read tool identity drifted", result)
    require(isinstance(data.get("provenance"), dict), "variable read omitted provenance", result)
    require(isinstance(data.get("validation"), dict), "variable read omitted validation state", result)
    require(isinstance(data.get("qualityGate"), dict), "variable read omitted quality gate", result)


def test_invalid_requests_fail_closed(client, process_json):
    cases = [
        ("listSimulationUnits", {"processJson": ""}),
        ("listUnitVariables", {"processJson": process_json, "unitName": ""}),
        (
            "getSimulationVariable",
            {"processJson": process_json, "address": "", "unit": "C"},
        ),
    ]
    for tool_name, arguments in cases:
        result = client.call_tool(tool_name, arguments)
        data = payload(result)
        require(data.get("status") == "error", f"{tool_name} invalid request did not fail closed", result)
        codes = []
        if data.get("code"):
            codes.append(data.get("code"))
        errors = data.get("errors", [])
        codes.extend(item.get("code") for item in errors if isinstance(item, dict))
        require("INPUT_ERROR" in codes, f"{tool_name} missing INPUT_ERROR diagnostic", result)


def main():
    client = McpClient()
    try:
        client.start()
        process_json = simple_process_json(client)
        test_unit_inventory(client, process_json)
        test_variable_inventory(client, process_json)
        test_variable_read(client, process_json)
        test_invalid_requests_fail_closed(client, process_json)
    finally:
        client.close()
    print("automation read real-MCP qualification: 4/4 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
