"""Focused real-MCP qualification for manageValidationProfile.

This dependency-free harness starts the packaged NeqSim MCP server over STDIO and
checks built-in profile discovery, bounded validation metadata, an isolated custom-profile lifecycle,
fail-closed error behavior, and the machine-readable CONTRACT_TESTED state. It qualifies
software-contract behavior only; it is not scientific, regulatory, authorization, or plant-control validation.
"""
import json
import subprocess
import sys
import time

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"
PROFILE_NAME = "phase0-protocol-profile"


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
                    "clientInfo": {"name": "neqsim-validation-profile-test", "version": "1.0"},
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

    def validation_profile(self, request):
        return self.call_tool(
            "manageValidationProfile", {"profileJson": json.dumps(request)}
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
    for key in ("status", "message", "tool", "errors"):
        if key in response:
            merged.setdefault(key, response[key])
    return merged


def error_codes(response):
    """Collect runner or standardized-envelope error codes without assuming nesting."""
    data = payload(response)
    errors = data.get("errors", []) if isinstance(data, dict) else []
    return [item.get("code") for item in errors if isinstance(item, dict)]


def test_builtin_profile_discovery(client):
    result = client.validation_profile({"action": "listProfiles"})
    data = payload(result)
    require(data.get("status") == "success", "listProfiles failed", result)
    profiles = data.get("profiles", [])
    names = {item.get("name") for item in profiles if isinstance(item, dict)}
    require({"ncs", "ukcs", "gom", "brazil", "generic"}.issubset(names),
            "built-in validation profiles drifted", result)
    require(data.get("totalCount", 0) >= 5, "profile count omitted built-ins", result)

    ncs = payload(client.validation_profile({"action": "getProfile", "profileName": "ncs"}))
    require(ncs.get("status") == "success", "getProfile(ncs) failed", ncs)
    require(ncs.get("name") == "ncs", "getProfile lost requested name", ncs)
    require(ncs.get("type") == "built-in", "ncs should remain built-in", ncs)
    profile = ncs.get("profile", {})
    require(isinstance(profile.get("standards"), list), "ncs standards missing", ncs)


def test_validation_metadata_is_preserved(client):
    result = client.validation_profile(
        {"action": "validateWithProfile", "profileName": "generic"}
    )
    data = payload(result)
    require("verdict" in data, "validator verdict was omitted", result)
    require(
        data.get("validationProfile") == "generic",
        "validation profile provenance drifted",
        result,
    )
    require(isinstance(data.get("applicableStandards"), list), "standards metadata missing", result)
    require(isinstance(data.get("requiredDesignFactors"), dict), "design-factor metadata missing", result)


def test_custom_profile_lifecycle(client):
    client.validation_profile({"action": "deleteProfile", "profileName": PROFILE_NAME})
    created = payload(
        client.validation_profile(
            {
                "action": "createProfile",
                "profileName": PROFILE_NAME,
                "basedOn": "generic",
                "description": "Phase 0 packaged protocol fixture",
            }
        )
    )
    require(created.get("status") == "success", "createProfile failed", created)
    require(created.get("profileName") == PROFILE_NAME, "created profile identity drifted", created)

    activated = payload(
        client.validation_profile({"action": "setActiveProfile", "profileName": PROFILE_NAME})
    )
    require(activated.get("status") == "success", "setActiveProfile failed", activated)
    require(activated.get("activeProfile") == PROFILE_NAME, "active profile did not change", activated)

    active = payload(client.validation_profile({"action": "getActiveProfile"}))
    require(active.get("status") == "success", "getActiveProfile failed", active)
    require(active.get("activeProfile") == PROFILE_NAME, "active profile identity was not retained", active)
    require(active.get("type") == "custom", "custom profile type was not retained", active)
    require(
        active.get("profile", {}).get("description") == "Phase 0 packaged protocol fixture",
        "custom profile payload drifted",
        active,
    )

    standards = payload(
        client.validation_profile(
            {"action": "getStandardsForEquipment", "equipmentType": "separator"}
        )
    )
    require(standards.get("status") == "success", "equipment standards lookup failed", standards)
    require(standards.get("equipmentType") == "separator", "equipment identity drifted", standards)
    require(isinstance(standards.get("standards"), list), "equipment standards missing", standards)

    deleted = payload(
        client.validation_profile({"action": "deleteProfile", "profileName": PROFILE_NAME})
    )
    require(deleted.get("status") == "success", "deleteProfile failed", deleted)
    require(deleted.get("deleted") is True, "custom profile was not deleted", deleted)
    require(deleted.get("activeProfile") == "generic", "deleting active custom profile did not recover to generic", deleted)


def test_mutation_errors_fail_closed(client):
    reserved = client.validation_profile({"action": "deleteProfile", "profileName": "generic"})
    require(payload(reserved).get("status") == "error", "built-in profile deletion was not rejected", reserved)
    require("CANNOT_DELETE" in error_codes(reserved), "built-in deletion error code drifted", reserved)

    missing = client.validation_profile(
        {"action": "setActiveProfile", "profileName": "does-not-exist-phase0"}
    )
    require(payload(missing).get("status") == "error", "unknown profile activation was not rejected", missing)
    require("PROFILE_NOT_FOUND" in error_codes(missing), "unknown-profile error code drifted", missing)

    unknown = client.validation_profile({"action": "unsupported-phase0-action"})
    require(payload(unknown).get("status") == "error", "unknown action was not rejected", unknown)
    require("UNKNOWN_ACTION" in error_codes(unknown), "unknown-action error code drifted", unknown)


def test_contract_classification_is_promoted_atomically(client):
    result = client.call_tool("getCapabilities", {})
    data = payload(result)
    inventory = data.get("phase0EvidenceInventory")
    require(isinstance(inventory, dict), "capabilities omitted Phase 0 evidence inventory", result)
    require(inventory.get("inventoryVersion") == "1.22", "unexpected evidence inventory version", result)
    limitations = inventory.get("knownLimitations", {})
    require(limitations.get("contractTestedToolCount") == 20, "contract-tested count did not promote", result)
    require(limitations.get("confirmedGapToolCount") == 31, "confirmed-gap count did not promote", result)
    records = limitations.get("coverageRecords", {})
    profile_record = records.get("manageValidationProfile", {})
    require(
        profile_record.get("coverageStatus") == "CONTRACT_TESTED",
        "manageValidationProfile was not promoted atomically",
        result,
    )
    require(profile_record.get("contractTrustAvailable") is True, "contract trust flag missing", result)
    require(
        profile_record.get("benchmarkApplicability")
        == "NOT_APPLICABLE_NON_NUMERICAL_VALIDATION_PROFILE_GOVERNANCE",
        "validation-profile applicability boundary drifted",
        result,
    )
    evidence = json.dumps(profile_record.get("contractEvidenceSources", []))
    require(profile_record.get("contractEvidenceCount") == 6, "contract evidence count drifted", result)
    require("ValidationProfileRunnerTest.java" in evidence, "coverage omits Java regression evidence", result)
    require("test_validation_profile_protocol.py" in evidence, "coverage omits packaged MCP evidence", result)
    require(
        limitations.get("contractPromotionCandidateCount") == 0
        and not limitations.get("contractPromotionCandidates", {}),
        "completed validation-profile promotion remains queued as a candidate",
        result,
    )


def main():
    client = McpClient()
    try:
        client.start()
        test_builtin_profile_discovery(client)
        test_validation_metadata_is_preserved(client)
        test_custom_profile_lifecycle(client)
        test_mutation_errors_fail_closed(client)
        test_contract_classification_is_promoted_atomically(client)
    finally:
        try:
            if client.proc is not None:
                client.validation_profile({"action": "deleteProfile", "profileName": PROFILE_NAME})
                client.validation_profile({"action": "setActiveProfile", "profileName": "generic"})
        finally:
            client.close()
    print("manageValidationProfile real-MCP qualification: 5/5 scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
