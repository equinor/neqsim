"""Packaged-MCP qualification for bounded multi-server composition metadata.

The scenarios exercise the real STDIO transport, deterministic server and
workflow discovery, metadata-only planning, bounded custom metadata lifecycle,
connection/credential rejection, fail-closed inputs, normal access enforcement,
and standard response evidence. They do not connect to or execute an external
server, validate suggested engineering calculations, establish external IAM or
transport security, persist state, isolate tenants, grant plant authority, or
replace accountable engineering review.
"""
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


def payload(response):
    data = response.get("data") if isinstance(response, dict) else None
    if not isinstance(data, dict):
        return response
    merged = dict(data)
    for key in (
        "status",
        "tool",
        "code",
        "errors",
        "message",
        "validation",
        "qualityGate",
    ):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


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
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "neqsim-composition-contract-test",
                        "version": "1.0",
                    },
                },
            }
        )
        require("result" in self.receive(), "MCP initialize did not return a result")
        self.send({"jsonrpc": "2.0", "method": "notifications/initialized"})
        time.sleep(0.2)

    def request(self, method, params):
        self.send(
            {
                "jsonrpc": "2.0",
                "id": self.next_id(),
                "method": method,
                "params": params,
            }
        )
        return self.receive()

    def list_tools(self):
        return self.request("tools/list", {}).get("result", {}).get("tools", [])

    def call_tool(self, name, arguments):
        response = self.request(
            "tools/call",
            {"name": name, "arguments": arguments},
        )
        content = response.get("result", {}).get("content", [])
        require(content, "MCP tool call returned no content", response)
        return json.loads(content[0].get("text", ""))

    def call_composition(self, request):
        return self.call_tool(
            "composeMultiServerWorkflow",
            {"compositionJson": json.dumps(request)},
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


def assert_success(response):
    result = payload(response)
    require(result.get("status") == "success", "composition request failed", response)
    require(
        result.get("tool") == "composeMultiServerWorkflow",
        "tool identity drifted",
        response,
    )
    require(
        result.get("validation", {}).get("valid") is True,
        "standard validation evidence drifted",
        response,
    )
    require(
        result.get("qualityGate", {}).get("verdict") == "passed",
        "software gate did not pass",
        response,
    )
    return result


def assert_error(response, expected_code):
    result = payload(response)
    require(
        result.get("status") == "error",
        "composition request did not fail closed",
        response,
    )
    errors = result.get("errors", [])
    code = result.get("code")
    if code is None and errors:
        code = errors[0].get("code")
    require(code == expected_code, "composition error code drifted", result)
    return result


def test_bounded_discovery(client):
    tool = next(
        (
            item
            for item in client.list_tools()
            if item.get("name") == "composeMultiServerWorkflow"
        ),
        None,
    )
    require(tool is not None, "composeMultiServerWorkflow is missing from tools/list")
    description = tool.get("description", "")
    require(
        "metadata only" in description
        and "does not connect" in description
        and "16384 bytes" in description
        and "independent engineering review" in description,
        "composition discovery boundary drifted",
        tool,
    )


def test_server_catalog_is_deterministic_metadata(client):
    first = assert_success(client.call_composition({"action": "listServers"}))
    second = assert_success(client.call_composition({"action": "listServers"}))
    require(first.get("count") == 5, "default server count drifted", first)
    require(
        first.get("metadataOnly") is True
        and first.get("executionPerformed") is False,
        "server catalog execution boundary drifted",
        first,
    )
    require(first.get("servers") == second.get("servers"), "server order drifted", first)
    names = [server.get("name") for server in first.get("servers", [])]
    require(names == sorted(names), "server names are not sorted", names)
    require(
        "plant-historian" in names
        and "endpoint" not in json.dumps(first)
        and "credentials" not in json.dumps(first),
        "server metadata boundary drifted",
        first,
    )


def test_workflow_and_plan_are_host_executed_metadata(client):
    catalog = assert_success(client.call_composition({"action": "listWorkflows"}))
    require(catalog.get("count") == 4, "workflow catalog count drifted", catalog)

    workflow = assert_success(
        client.call_composition(
            {"action": "getWorkflow", "workflowId": "safety-study"}
        )
    )
    require(
        workflow.get("id") == "safety-study"
        and len(workflow.get("steps", [])) == 4,
        "workflow detail drifted",
        workflow,
    )

    plan = assert_success(
        client.call_composition(
            {
                "action": "planComposition",
                "task": "Compare plant measurements and project cost",
            }
        )
    )
    steps = plan.get("suggestedSteps", [])
    require(
        plan.get("metadataOnly") is True
        and plan.get("executionPerformed") is False
        and plan.get("hostExecutionRequired") is True,
        "plan execution boundary drifted",
        plan,
    )
    require(
        [step.get("order") for step in steps] == list(range(1, len(steps) + 1)),
        "plan step order drifted",
        steps,
    )


def test_custom_metadata_lifecycle(client):
    name = "phase0-packaged-composition"
    registered = assert_success(
        client.call_composition(
            {
                "action": "registerServer",
                "name": name,
                "description": "Synthetic packaged-test metadata",
                "domain": "test",
                "tools": ["inspect", "inspect"],
                "dataFormats": ["JSON", "JSON"],
            }
        )
    )
    require(
        registered.get("metadataOnly") is True
        and registered.get("executionPerformed") is False,
        "registration boundary drifted",
        registered,
    )

    catalog = assert_success(client.call_composition({"action": "listServers"}))
    custom = next(
        (server for server in catalog.get("servers", []) if server.get("name") == name),
        None,
    )
    require(custom is not None, "custom metadata was not listed", catalog)
    require(
        custom.get("tools") == ["inspect"]
        and custom.get("dataFormats") == ["JSON"],
        "custom metadata was not deduplicated",
        custom,
    )

    removed = assert_success(
        client.call_composition({"action": "removeServer", "name": name})
    )
    require(removed.get("removed") is True, "custom metadata was not removed", removed)


def test_connection_and_built_in_mutations_fail_closed(client):
    assert_error(
        client.call_composition(
            {
                "action": "registerServer",
                "name": "forbidden-endpoint",
                "endpoint": "https://example.invalid/mcp",
            }
        ),
        "UNSUPPORTED_CONNECTION_DATA",
    )
    assert_error(
        client.call_composition(
            {
                "action": "registerServer",
                "name": "forbidden-token",
                "token": "do-not-store",
            }
        ),
        "UNSUPPORTED_CONNECTION_DATA",
    )
    assert_error(
        client.call_composition(
            {"action": "registerServer", "name": "plant-historian"}
        ),
        "PROTECTED_SERVER",
    )
    assert_error(
        client.call_composition(
            {"action": "removeServer", "name": "plant-historian"}
        ),
        "PROTECTED_SERVER",
    )


def test_invalid_requests_fail_closed(client):
    assert_error(client.call_composition({"action": "execute"}), "UNKNOWN_ACTION")
    assert_error(
        client.call_composition({"action": "planComposition", "task": "   "}),
        "INVALID_INPUT",
    )
    assert_error(
        client.call_composition(
            {"action": "planComposition", "task": "x" * 4097}
        ),
        "INVALID_INPUT",
    )
    assert_error(
        client.call_tool(
            "composeMultiServerWorkflow",
            {"compositionJson": "{"},
        ),
        "INVALID_INPUT",
    )


def test_inventory_is_promoted(client):
    result = payload(client.call_tool("getCapabilities", {}))
    require(result.get("status") == "success", "capabilities request failed", result)
    inventory = result.get("phase0EvidenceInventory", {})
    limitations = inventory.get("knownLimitations", {})
    record = limitations.get("coverageRecords", {}).get(
        "composeMultiServerWorkflow", {}
    )
    sources = record.get("contractEvidenceSources", [])
    require(
        inventory.get("inventoryVersion") == "1.36"
        and limitations.get("contractTestedToolCount") == 36
        and limitations.get("confirmedGapToolCount") == 15
        and limitations.get("contractPromotionCandidateCount") == 0,
        "composition promotion did not update inventory accounting",
        inventory,
    )
    require(
        record.get("coverageStatus") == "CONTRACT_TESTED"
        and record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_BOUNDED_MULTI_SERVER_COMPOSITION_METADATA"
        and record.get("contractEvidenceCount") == 6
        and "src/test/java/neqsim/mcp/runners/CompositionRunnerTest.java" in sources
        and "neqsim-mcp-server/test_composition_protocol.py" in sources
        and "neqsim-mcp-server/docs/evidence/MULTI_SERVER_COMPOSITION_CONTRACT.md"
        in sources
        and "does not establish external server connection"
        in record.get("evidenceBoundary", "")
        and "accountable engineering approval"
        in record.get("evidenceBoundary", ""),
        "composition contract evidence is incomplete",
        record,
    )



def main():
    client = McpClient()
    tests = [
        ("bounded discovery", test_bounded_discovery),
        ("deterministic metadata server catalog", test_server_catalog_is_deterministic_metadata),
        ("host-executed workflow metadata", test_workflow_and_plan_are_host_executed_metadata),
        ("custom metadata lifecycle", test_custom_metadata_lifecycle),
        ("connection and built-in mutations fail closed", test_connection_and_built_in_mutations_fail_closed),
        ("invalid requests fail closed", test_invalid_requests_fail_closed),
        ("inventory promoted", test_inventory_is_promoted),
    ]
    try:
        client.start()
        for label, test in tests:
            test(client)
            print("PASS:", label)
    finally:
        client.close()
    print(f"\n{len(tests)}/{len(tests)} composition contract scenarios passed.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
