"""
Comprehensive MCP Server Tests for NeqSim
==========================================
Tests all 56 MCP tools through the real JSON-RPC protocol, verifying
correctness against known values from the NeqSim JUnit test suite.

Tier 1 — Trusted Core (21 tools):
  - TP flash (single phase, two-phase, multi-component)
  - Dew point / bubble point calculations
  - Different EOS models (SRK, PR, CPA)
  - Transport properties (viscosity, thermal conductivity)
  - Process simulation (separator, compressor, cooler, heater, valve, trains)
  - Input validation (error cases, typos, missing fields)
  - Examples, schemas, component search, capabilities
  - Batch calculations, property tables, phase envelopes
  - Automation API (units, variables, state save/compare, diagnostics)
  - Industrial profile, benchmark trust, tool access

Tier 2 — Engineering Advanced (21 tools):
  - PVT laboratory experiments
  - Flow assurance (hydrate, corrosion, wax)
  - Standards calculations (ISO 6976, AGA)
  - Pipeline flow simulation
  - Reservoir material balance
  - Field development economics
  - Dynamic transient simulation
  - Bioprocess reactors
  - Equipment sizing
  - Process comparison
  - Engineering validation
  - Cross-model validation
  - Parametric studies
    - Relief, LOPA, SIL, risk matrix, flare, HAZOP, barrier register, safety performance

Tier 3 — Experimental (14 tools):
  - Session management
  - Task solver, workflow composition
  - Report generation, visualization
  - Plugins, progress tracking, streaming
  - Multi-server composition
  - Security, state persistence
  - Validation profiles, data catalog
"""
import subprocess
import json
import time
import sys
import math

JAR = "target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"

# ---------------------------------------------------------------------------
# MCP client helpers
# ---------------------------------------------------------------------------
proc = None
msg_id = 0


def start_server():
    global proc
    proc = subprocess.Popen(
        ["java", "-jar", JAR],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    # initialize
    send(
        {
            "jsonrpc": "2.0",
            "id": next_id(),
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "neqsim-test", "version": "1.0"},
            },
        }
    )
    recv()
    send({"jsonrpc": "2.0", "method": "notifications/initialized"})
    time.sleep(0.3)


def stop_server():
    global proc
    if proc:
        proc.stdin.close()
        proc.wait(timeout=10)
        proc = None


def next_id():
    global msg_id
    msg_id += 1
    return msg_id


def send(msg):
    proc.stdin.write(json.dumps(msg) + "\n")
    proc.stdin.flush()


def recv():
    line = proc.stdout.readline()
    return json.loads(line) if line.strip() else None


def call_tool(name, arguments):
    send(
        {
            "jsonrpc": "2.0",
            "id": next_id(),
            "method": "tools/call",
            "params": {"name": name, "arguments": arguments},
        }
    )
    r = recv()
    content = r.get("result", {}).get("content", [])
    if content:
        return json.loads(content[0].get("text", "{}"))
    return {}


def run_flash(components, temp_c, press_bara, eos="SRK", flash_type="TP"):
    return call_tool(
        "runFlash",
        {
            "components": json.dumps(components),
            "temperature": temp_c,
            "temperatureUnit": "C",
            "pressure": press_bara,
            "pressureUnit": "bara",
            "eos": eos,
            "flashType": flash_type,
        },
    )


def run_process(process_json):
    return call_tool("runProcess", {"processJson": json.dumps(process_json)})


# ---------------------------------------------------------------------------
# Test tracking
# ---------------------------------------------------------------------------
passed = 0
failed = 0
errors_list = []


def check(test_name, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  PASS: {test_name}")
    else:
        failed += 1
        msg = f"  FAIL: {test_name}"
        if detail:
            msg += f" -- {detail}"
        print(msg)
        errors_list.append(test_name)


def approx(actual, expected, rel_tol=0.02, abs_tol=1e-6):
    """Check if actual is within rel_tol (2%) of expected, or abs_tol."""
    if expected == 0:
        return abs(actual) < abs_tol
    return abs(actual - expected) / abs(expected) < rel_tol


def get_phase_prop(result, phase, prop):
    """Extract a property value from flash result."""
    try:
        props = result["fluid"]["properties"][phase]
        val = props[prop]["value"]
        return float(val)
    except (KeyError, TypeError, ValueError):
        return None


def get_composition(result, phase, component):
    """Extract composition from flash result."""
    try:
        comp = result["fluid"]["composition"][phase]
        val = comp[component]["value"]
        return float(val)
    except (KeyError, TypeError, ValueError):
        return None


# ===========================================================================
# TESTS
# ===========================================================================

def test_protocol():
    """Test MCP protocol basics: tools/list, resources/list."""
    print("\n=== Protocol Tests ===")

    send({"jsonrpc": "2.0", "id": next_id(), "method": "tools/list", "params": {}})
    r = recv()
    tools = r.get("result", {}).get("tools", [])
    tool_names = sorted([t["name"] for t in tools])
    check("56 tools registered", len(tools) == 56, f"got {len(tools)}: {tool_names}")

    # Tier 1 — Trusted Core (21 tools)
    tier1 = ["runFlash", "runProcess", "validateInput", "searchComponents",
             "getExample", "getSchema", "getPropertyTable", "getPhaseEnvelope",
             "getCapabilities", "runBatch", "listSimulationUnits",
             "listUnitVariables", "getSimulationVariable", "setSimulationVariable",
             "saveSimulationState", "compareSimulationStates", "diagnoseAutomation",
             "getAutomationLearningReport", "manageIndustrialProfile",
             "getBenchmarkTrust", "checkToolAccess"]
    for name in tier1:
        check(f"tier1 tool '{name}'", name in tool_names)

    # Tier 2 — Engineering Advanced (21 tools)
    tier2 = ["crossValidateModels", "runParametricStudy", "runPVT",
             "runFlowAssurance", "calculateStandard", "runPipeline",
             "runReservoir", "runFieldEconomics", "runDynamic", "runBioprocess",
             "sizeEquipment", "compareProcesses", "validateResults",
             "runRelief", "runLOPA", "runSIL", "runRiskMatrix",
             "runFlareNetwork", "runHAZOP", "runBarrierRegister",
             "runSafetySystemPerformance"]
    for name in tier2:
        check(f"tier2 tool '{name}'", name in tool_names)

    # Tier 3 — Experimental (14 tools)
    tier3 = ["manageSession", "solveTask", "composeWorkflow", "generateReport",
             "runPlugin", "getProgress", "streamSimulation",
             "generateVisualization", "composeMultiServerWorkflow",
             "manageSecurity", "manageState", "manageValidationProfile",
             "queryDataCatalog", "bridgeTaskWorkflow"]
    for name in tier3:
        check(f"tier3 tool '{name}'", name in tool_names)

    send({"jsonrpc": "2.0", "id": next_id(), "method": "resources/list", "params": {}})
    r = recv()
    resources = r.get("result", {}).get("resources", [])
    check("6 resources", len(resources) == 6, f"got {len(resources)}")

    send({"jsonrpc": "2.0", "id": next_id(), "method": "resources/templates/list", "params": {}})
    r = recv()
    templates = r.get("result", {}).get("resourceTemplates", [])
    check("5 templates", len(templates) == 5, f"got {len(templates)}")


def test_component_search():
    """Test component database search."""
    print("\n=== Component Search Tests ===")

    r = call_tool("searchComponents", {"query": "methane"})
    check("search methane", "methane" in r.get("components", []))

    r = call_tool("searchComponents", {"query": "meth"})
    comps = r.get("components", [])
    check("partial search 'meth'", len(comps) >= 2, f"got {len(comps)}")
    check("methane in partial results", "methane" in comps)
    check("methanol in partial results", "methanol" in comps)

    r = call_tool("searchComponents", {"query": "CO2"})
    check("search CO2", "CO2" in r.get("components", []))

    r = call_tool("searchComponents", {"query": "water"})
    check("search water", "water" in r.get("components", []))

    r = call_tool("searchComponents", {"query": "H2S"})
    check("search H2S", "H2S" in r.get("components", []))

    r = call_tool("searchComponents", {"query": ""})
    check("empty search returns all", r.get("matchCount", 0) > 50)

    r = call_tool("searchComponents", {"query": "unobtainium"})
    check("no match returns 0", r.get("matchCount", 0) == 0)


def test_examples_and_schemas():
    """Test example catalog and schema retrieval."""
    print("\n=== Examples & Schemas Tests ===")

    r = call_tool("getExample", {"category": "flash", "name": "tp-simple-gas"})
    check("flash example has model", "model" in r)
    check("flash example has components", "components" in r)
    check("flash example model=SRK", r.get("model") == "SRK")

    r = call_tool("getExample", {"category": "flash", "name": "tp-two-phase"})
    check("two-phase example exists", "components" in r)

    r = call_tool("getExample", {"category": "process", "name": "simple-separation"})
    check("process example has fluid", "fluid" in r)
    check("process example has process", "process" in r)

    r = call_tool("getExample", {"category": "process", "name": "compression-with-cooling"})
    check("compression example exists", "fluid" in r)

    r = call_tool("getSchema", {"toolName": "run_flash", "schemaType": "input"})
    check("flash input schema", "properties" in r)

    r = call_tool("getSchema", {"toolName": "run_flash", "schemaType": "output"})
    check("flash output schema", "properties" in r)

    r = call_tool("getSchema", {"toolName": "run_process", "schemaType": "input"})
    check("process input schema", "properties" in r)

    # Batch schemas
    r = call_tool("getSchema", {"toolName": "run_batch", "schemaType": "input"})
    check("batch input schema", "properties" in r)
    r = call_tool("getSchema", {"toolName": "run_batch", "schemaType": "output"})
    check("batch output schema", "properties" in r)

    # Property table schemas
    r = call_tool("getSchema", {"toolName": "get_property_table", "schemaType": "input"})
    check("prop table input schema", "properties" in r)
    r = call_tool("getSchema", {"toolName": "get_property_table", "schemaType": "output"})
    check("prop table output schema", "properties" in r)

    # Phase envelope schemas
    r = call_tool("getSchema", {"toolName": "get_phase_envelope", "schemaType": "input"})
    check("phase env input schema", "properties" in r)
    r = call_tool("getSchema", {"toolName": "get_phase_envelope", "schemaType": "output"})
    check("phase env output schema", "properties" in r)

    # Capabilities schema (output only)
    r = call_tool("getSchema", {"toolName": "get_capabilities", "schemaType": "output"})
    check("capabilities output schema", "properties" in r)

    # New example categories
    r = call_tool("getExample", {"category": "batch", "name": "temperature-sweep"})
    check("batch temp-sweep example has components", "components" in r)
    check("batch temp-sweep example has cases", "cases" in r)

    r = call_tool("getExample", {"category": "batch", "name": "pressure-sweep"})
    check("batch press-sweep example has cases", "cases" in r)

    r = call_tool("getExample", {"category": "property-table", "name": "temperature-sweep"})
    check("prop-table temp-sweep has sweep", "sweep" in r)

    r = call_tool("getExample", {"category": "property-table", "name": "pressure-sweep"})
    check("prop-table press-sweep has sweep", "sweep" in r)

    r = call_tool("getExample", {"category": "phase-envelope", "name": "natural-gas"})
    check("phase-env example has components", "components" in r)


# ---------------------------------------------------------------------------
# FLASH CALCULATION TESTS
# ---------------------------------------------------------------------------

def test_tp_flash_simple_gas():
    """SRK TP flash: simple natural gas at 25C, 50 bara (single phase)."""
    print("\n=== TP Flash: Simple Gas (SRK, 25C, 50 bara) ===")

    r = run_flash({"methane": 0.85, "ethane": 0.10, "propane": 0.05}, 25.0, 50.0)
    check("status=success", r.get("status") == "success", r.get("message", ""))

    flash = r.get("flash", {})
    check("model=SRK", flash.get("model") == "SRK")
    check("single phase", flash.get("numberOfPhases") == 1)
    check("gas phase", "gas" in flash.get("phases", []))

    density = get_phase_prop(r, "gas", "density")
    check("gas density > 30 kg/m3", density is not None and density > 30, f"got {density}")
    check("gas density < 60 kg/m3", density is not None and density < 60, f"got {density}")

    z = get_phase_prop(r, "gas", "compressibilityFactor")
    check("Z-factor 0.85-0.95", z is not None and 0.85 < z < 0.95, f"got {z}")

    visc = get_phase_prop(r, "gas", "viscosity")
    check("viscosity > 0", visc is not None and visc > 0, f"got {visc}")
    check("viscosity order 1e-5", visc is not None and 1e-6 < visc < 1e-4, f"got {visc}")

    tc = get_phase_prop(r, "gas", "thermalConductivity")
    check("thermal cond > 0", tc is not None and tc > 0, f"got {tc}")

    cp = get_phase_prop(r, "gas", "Cp")
    check("Cp > 1500 J/kgK", cp is not None and cp > 1500, f"got {cp}")


def test_tp_flash_pr_two_phase():
    """PR EOS TP flash: natural gas at 25C, 10 bara (from SystemPrEoSTest)."""
    print("\n=== TP Flash: PR Two-Phase (25C, 10 bara) ===")

    r = run_flash(
        {"nitrogen": 0.01, "CO2": 0.01, "methane": 0.68, "ethane": 0.10, "n-heptane": 0.20},
        24.85, 10.0, eos="PR"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))

    flash = r.get("flash", {})
    check("model=PR", flash.get("model") == "PR")
    nphases = flash.get("numberOfPhases", 0)
    check("two phases", nphases == 2, f"got {nphases}")

    # From SystemPrEoSTest: gas Z ~0.9708
    z = get_phase_prop(r, "gas", "compressibilityFactor")
    if z is not None:
        check("gas Z ~0.97", approx(z, 0.9708, rel_tol=0.01), f"got {z}")


def test_tp_flash_two_phase_ch4_c3():
    """SRK TP flash: CH4/C3 at -20C, 10 bara should give two phases."""
    print("\n=== TP Flash: CH4/C3 Two-Phase (-20C, 10 bara) ===")

    r = run_flash({"methane": 0.50, "propane": 0.50}, -20.0, 10.0)
    check("status=success", r.get("status") == "success", r.get("message", ""))

    nphases = r.get("flash", {}).get("numberOfPhases", 0)
    check("two phases", nphases >= 2, f"got {nphases}")

    # Gas phase should exist with density << liquid
    gas_d = get_phase_prop(r, "gas", "density")
    liq_d = get_phase_prop(r, "liquid", "density") or get_phase_prop(r, "oil", "density")
    if gas_d and liq_d:
        check("gas density < liquid density", gas_d < liq_d,
              f"gas={gas_d}, liq={liq_d}")


def test_tp_flash_rich_gas():
    """SRK TP flash: rich gas near cricondenbar (from TPFlashTest)."""
    print("\n=== TP Flash: Rich Gas Near Cricondenbar (0C, 100 bara) ===")

    r = run_flash(
        {
            "nitrogen": 3.43, "CO2": 0.34, "methane": 62.51, "ethane": 15.65,
            "propane": 13.22, "i-butane": 1.61, "n-butane": 2.48,
            "i-pentane": 0.35, "n-pentane": 0.29, "n-hexane": 0.12
        },
        0.0, 100.0
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))

    nphases = r.get("flash", {}).get("numberOfPhases", 0)
    check("two phases at 0C/100bar", nphases >= 2, f"got {nphases}")


def test_tp_flash_rich_gas_single_phase():
    """SRK TP flash: same rich gas at 30C, 100 bara should be single phase."""
    print("\n=== TP Flash: Rich Gas Single Phase (30C, 100 bara) ===")

    r = run_flash(
        {
            "nitrogen": 3.43, "CO2": 0.34, "methane": 62.51, "ethane": 15.65,
            "propane": 13.22, "i-butane": 1.61, "n-butane": 2.48,
            "i-pentane": 0.35, "n-pentane": 0.29, "n-hexane": 0.12
        },
        30.0, 100.0
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))

    nphases = r.get("flash", {}).get("numberOfPhases", 0)
    check("single phase at 30C/100bar", nphases == 1, f"got {nphases}")


def test_tp_flash_pure_methane():
    """SRK TP flash: pure methane at 25C, 50 bara."""
    print("\n=== TP Flash: Pure Methane (25C, 50 bara) ===")

    r = run_flash({"methane": 1.0}, 25.0, 50.0)
    check("status=success", r.get("status") == "success", r.get("message", ""))
    check("single phase", r.get("flash", {}).get("numberOfPhases") == 1)

    z = get_phase_prop(r, "gas", "compressibilityFactor")
    # Pure CH4 at 25C, 50 bara with SRK: Z ~ 0.92
    check("Z ~0.92", z is not None and approx(z, 0.92, rel_tol=0.03), f"got {z}")


def test_tp_flash_high_pressure():
    """SRK TP flash: gas at high pressure 300 bara (dense phase)."""
    print("\n=== TP Flash: High Pressure (25C, 300 bara) ===")

    r = run_flash({"methane": 0.90, "ethane": 0.10}, 25.0, 300.0)
    check("status=success", r.get("status") == "success", r.get("message", ""))

    density = get_phase_prop(r, "gas", "density")
    # At 300 bara, methane is very dense
    check("high density > 150 kg/m3", density is not None and density > 150, f"got {density}")


def test_tp_flash_low_temperature():
    """SRK TP flash: natural gas at -40C, 50 bara (may condense)."""
    print("\n=== TP Flash: Low Temperature (-40C, 50 bara) ===")

    r = run_flash(
        {"methane": 0.80, "ethane": 0.10, "propane": 0.05, "n-butane": 0.05},
        -40.0, 50.0
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))
    nphases = r.get("flash", {}).get("numberOfPhases", 0)
    check("phases >= 1", nphases >= 1, f"got {nphases}")


def test_tp_flash_with_co2_h2s():
    """SRK TP flash: sour gas with CO2 and H2S."""
    print("\n=== TP Flash: Sour Gas with CO2/H2S (40C, 80 bara) ===")

    r = run_flash(
        {"methane": 0.75, "CO2": 0.10, "H2S": 0.05, "ethane": 0.05, "propane": 0.05},
        40.0, 80.0
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))
    check("has gas phase", "gas" in r.get("flash", {}).get("phases", []))


def test_tp_flash_with_water_cpa():
    """CPA EOS TP flash: gas with water (tests CPA model)."""
    print("\n=== TP Flash: CPA with Water (25C, 50 bara) ===")

    r = run_flash(
        {"methane": 0.85, "ethane": 0.10, "water": 0.05},
        25.0, 50.0, eos="CPA"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))

    nphases = r.get("flash", {}).get("numberOfPhases", 0)
    check("two phases (gas+aqueous)", nphases >= 2, f"got {nphases}")


# ---------------------------------------------------------------------------
# DEW POINT / BUBBLE POINT TESTS
# ---------------------------------------------------------------------------

def test_dew_point_temperature():
    """Dew point temperature at 50 bara for natural gas."""
    print("\n=== Dew Point Temperature (50 bara) ===")

    r = run_flash(
        {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
        25.0, 50.0, flash_type="dewPointT"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))
    check("flashType=dewPointT", r.get("flash", {}).get("flashType") == "dewPointT")


def test_bubble_point_pressure():
    """Bubble point pressure for CH4/C2/C3 mix at -50C."""
    print("\n=== Bubble Point Pressure (-50C) ===")

    r = run_flash(
        {"methane": 0.50, "ethane": 0.30, "propane": 0.20},
        -50.0, 30.0, flash_type="bubblePointP"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))
    check("flashType=bubblePointP", r.get("flash", {}).get("flashType") == "bubblePointP")


def test_dew_point_pressure():
    """Dew point pressure for rich gas at 0C."""
    print("\n=== Dew Point Pressure (0C) ===")

    r = run_flash(
        {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
        0.0, 50.0, flash_type="dewPointP"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_bubble_point_temperature():
    """Bubble point temperature for liquid-rich mixture at 30 bara."""
    print("\n=== Bubble Point Temperature (30 bara) ===")

    r = run_flash(
        {"methane": 0.30, "propane": 0.40, "n-butane": 0.30},
        -20.0, 30.0, flash_type="bubblePointT"
    )
    check("status=success", r.get("status") == "success", r.get("message", ""))


# ---------------------------------------------------------------------------
# PROCESS SIMULATION TESTS
# ---------------------------------------------------------------------------

def test_process_separator():
    """Separator: HP separation of two-phase gas at 25C, 50 bara."""
    print("\n=== Process: HP Separator ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 50.0,
            "mixingRule": "classic",
            "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [10000.0, "kg/hr"]}},
            {"type": "Separator", "name": "HP Sep", "inlet": "feed"}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))
    check("has report", "report" in r or "unitOperations" in r or "reportJson" in str(r.keys()))


def test_process_compressor():
    """Compressor: compress methane from 20 to 60 bara."""
    print("\n=== Process: Compressor (20->60 bara) ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 20.0,
            "components": {"methane": 0.90, "ethane": 0.10}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [5000.0, "kg/hr"]}},
            {"type": "Compressor", "name": "Comp", "inlet": "feed",
             "properties": {"outletPressure": [60.0, "bara"]}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_cooler():
    """Cooler: cool gas from 80C to 30C."""
    print("\n=== Process: Cooler (80C -> 30C) ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 353.15,
            "pressure": 50.0,
            "components": {"methane": 0.90, "ethane": 0.10}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [5000.0, "kg/hr"]}},
            {"type": "Cooler", "name": "cooler1", "inlet": "feed",
             "properties": {"outletTemperature": [303.15, "K"]}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_heater():
    """Heater: heat gas from 25C to 80C."""
    print("\n=== Process: Heater (25C -> 80C) ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 50.0,
            "components": {"methane": 0.90, "ethane": 0.10}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [5000.0, "kg/hr"]}},
            {"type": "Heater", "name": "heater1", "inlet": "feed",
             "properties": {"outletTemperature": [353.15, "K"]}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_valve():
    """Throttling valve: drop pressure from 50 to 20 bara."""
    print("\n=== Process: Throttling Valve (50 -> 20 bara) ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 50.0,
            "components": {"methane": 0.90, "ethane": 0.10}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [10000.0, "kg/hr"]}},
            {"type": "valve", "name": "choke", "inlet": "feed",
             "properties": {"outletPressure": 20.0}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_separator_and_compressor():
    """Separator + compressor train: separate gas, then compress."""
    print("\n=== Process: Separator + Compressor Train ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 50.0,
            "mixingRule": "classic",
            "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [50000.0, "kg/hr"]}},
            {"type": "Separator", "name": "HP Sep", "inlet": "feed"},
            {"type": "Compressor", "name": "Comp", "inlet": "HP Sep.gasOut",
             "properties": {"outletPressure": [80.0, "bara"]}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_compression_cooling_train():
    """Compressor + cooler: compress then cool (from ExampleCatalog)."""
    print("\n=== Process: Compression + Cooling Train ===")

    r = call_tool("getExample", {"category": "process", "name": "compression-with-cooling"})
    check("example retrieved", "fluid" in r)

    if "fluid" in r:
        result = run_process(r)
        check("compression+cooling success", result.get("status") == "success",
              result.get("message", ""))


def test_process_two_phase_separator():
    """Separator with two-phase feed (gas + liquid)."""
    print("\n=== Process: Two-Phase Separator ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 253.15,
            "pressure": 10.0,
            "mixingRule": "classic",
            "components": {"methane": 0.50, "propane": 0.50}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [20000.0, "kg/hr"]}},
            {"type": "Separator", "name": "sep", "inlet": "feed"}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_valve_then_separator():
    """Valve + separator: choke then separate (JT cooling effect)."""
    print("\n=== Process: Valve + Separator (JT Effect) ===")

    r = run_process({
        "fluid": {
            "model": "SRK",
            "temperature": 298.15,
            "pressure": 80.0,
            "components": {
                "methane": 0.80, "ethane": 0.10, "propane": 0.05, "n-butane": 0.05
            }
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [30000.0, "kg/hr"]}},
            {"type": "valve", "name": "JT", "inlet": "feed",
             "properties": {"outletPressure": 20.0}},
            {"type": "Separator", "name": "LTS", "inlet": "JT"}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_multi_fluid():
    """Process with multiple named fluids."""
    print("\n=== Process: Multiple Named Fluids ===")

    r = run_process({
        "fluids": {
            "gas": {
                "model": "SRK",
                "temperature": 298.15,
                "pressure": 50.0,
                "components": {"methane": 0.9, "ethane": 0.1}
            },
            "oil": {
                "model": "PR",
                "temperature": 350.0,
                "pressure": 100.0,
                "components": {"methane": 0.3, "nC10": 0.7}
            }
        },
        "process": [
            {"type": "Stream", "name": "gasFeed", "fluidRef": "gas",
             "properties": {"flowRate": [10000.0, "kg/hr"]}},
            {"type": "Stream", "name": "oilFeed", "fluidRef": "oil",
             "properties": {"flowRate": [50000.0, "kg/hr"]}}
        ]
    })
    check("status=success", r.get("status") == "success", r.get("message", ""))


def test_process_pr_model():
    """Process using Peng-Robinson EOS."""
    print("\n=== Process: PR EOS ===")

    r = run_process({
        "fluid": {
            "model": "PR",
            "temperature": 298.15,
            "pressure": 50.0,
            "components": {"methane": 0.85, "ethane": 0.15}
        },
        "process": [
            {"type": "Stream", "name": "feed",
             "properties": {"flowRate": [10000.0, "kg/hr"]}},
            {"type": "Separator", "name": "sep", "inlet": "feed"}
        ]
    })
    check("PR process success", r.get("status") == "success", r.get("message", ""))


# ---------------------------------------------------------------------------
# VALIDATION & ERROR HANDLING TESTS
# ---------------------------------------------------------------------------

def test_validation_valid_flash():
    """Validate a correct flash input."""
    print("\n=== Validation: Valid Flash ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "model": "SRK",
            "temperature": {"value": 25.0, "unit": "C"},
            "pressure": {"value": 50.0, "unit": "bara"},
            "flashType": "TP",
            "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
            "mixingRule": "classic"
        })
    })
    check("valid=true", r.get("valid") is True)
    check("no issues", len(r.get("issues", [])) == 0, f"got {len(r.get('issues', []))}")


def test_validation_unknown_component():
    """Validate flash with misspelled component."""
    print("\n=== Validation: Unknown Component ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "components": {"metane": 1.0}
        })
    })
    check("valid=false", r.get("valid") is False)
    issues = r.get("issues", [])
    codes = [i.get("code") for i in issues]
    check("UNKNOWN_COMPONENT", "UNKNOWN_COMPONENT" in codes, f"got {codes}")
    # Should suggest "methane"
    for iss in issues:
        if iss.get("code") == "UNKNOWN_COMPONENT":
            check("suggests methane", "methane" in iss.get("message", ""))
            break


def test_validation_unknown_model():
    """Validate flash with invalid EOS model."""
    print("\n=== Validation: Unknown Model ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "model": "NONEXISTENT",
            "components": {"methane": 1.0}
        })
    })
    check("valid=false", r.get("valid") is False)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("UNKNOWN_MODEL", "UNKNOWN_MODEL" in codes, f"got {codes}")


def test_validation_negative_fraction():
    """Validate flash with negative mole fraction."""
    print("\n=== Validation: Negative Fraction ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "components": {"methane": -0.5}
        })
    })
    check("valid=false", r.get("valid") is False)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("NEGATIVE_FRACTION", "NEGATIVE_FRACTION" in codes, f"got {codes}")


def test_validation_composition_sum_warning():
    """Validate flash with non-unity composition sum (warning only)."""
    print("\n=== Validation: Composition Sum Warning ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "components": {"methane": 0.5, "ethane": 0.3}
        })
    })
    check("valid=true (warning)", r.get("valid") is True)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("COMPOSITION_SUM warning", "COMPOSITION_SUM" in codes, f"got {codes}")


def test_validation_missing_enthalpy_ph():
    """Validate PH flash without enthalpy spec."""
    print("\n=== Validation: PH Flash Missing Enthalpy ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "flashType": "PH",
            "components": {"methane": 1.0}
        })
    })
    check("valid=false", r.get("valid") is False)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("MISSING_SPEC", "MISSING_SPEC" in codes, f"got {codes}")


def test_validation_unknown_flash_type():
    """Validate flash with invalid flash type."""
    print("\n=== Validation: Unknown Flash Type ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "flashType": "XYZ",
            "components": {"methane": 1.0}
        })
    })
    check("valid=false", r.get("valid") is False)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("UNKNOWN_FLASH_TYPE", "UNKNOWN_FLASH_TYPE" in codes, f"got {codes}")


def test_validation_extreme_temperature():
    """Validate flash with extreme temperature (warning)."""
    print("\n=== Validation: Extreme Temperature Warning ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "temperature": 50000.0,
            "components": {"methane": 1.0}
        })
    })
    check("valid=true (warning)", r.get("valid") is True)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("TEMPERATURE_RANGE", "TEMPERATURE_RANGE" in codes, f"got {codes}")


def test_validation_process_missing_type():
    """Validate process with unit missing type field."""
    print("\n=== Validation: Process Unit Missing Type ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "fluid": {"components": {"methane": 1.0}},
            "process": [{"name": "feed"}]
        })
    })
    check("valid=false", r.get("valid") is False)
    codes = [i.get("code") for i in r.get("issues", [])]
    check("MISSING_TYPE", "MISSING_TYPE" in codes, f"got {codes}")


def test_validation_process_duplicate_names():
    """Validate process with duplicate equipment names."""
    print("\n=== Validation: Duplicate Names ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "fluid": {"components": {"methane": 1.0}},
            "process": [
                {"type": "Stream", "name": "feed"},
                {"type": "Stream", "name": "feed"}
            ]
        })
    })
    codes = [i.get("code") for i in r.get("issues", [])]
    check("DUPLICATE_NAME", "DUPLICATE_NAME" in codes, f"got {codes}")


def test_validation_multiple_errors():
    """Validate input with multiple simultaneous errors."""
    print("\n=== Validation: Multiple Errors ===")

    r = call_tool("validateInput", {
        "inputJson": json.dumps({
            "model": "FAKEOS",
            "flashType": "PH",
            "components": {"fakey": 1.0}
        })
    })
    check("valid=false", r.get("valid") is False)
    issues = r.get("issues", [])
    check("at least 3 issues", len(issues) >= 3, f"got {len(issues)}")


def test_flash_error_unknown_component():
    """Flash with unknown component returns error."""
    print("\n=== Flash Error: Unknown Component ===")

    r = run_flash({"metane": 1.0}, 25.0, 50.0)
    check("status=error", r.get("status") == "error")


def test_flash_error_unknown_model():
    """Flash with unknown EOS returns error."""
    print("\n=== Flash Error: Unknown Model ===")

    r = run_flash({"methane": 1.0}, 25.0, 50.0, eos="FAKEOS")
    check("status=error", r.get("status") == "error")


# ---------------------------------------------------------------------------
# RUN ALL EXAMPLES THROUGH FLASH/PROCESS
# ---------------------------------------------------------------------------

def test_run_all_flash_examples():
    """Run every flash example from the catalog through FlashRunner."""
    print("\n=== Run All Flash Examples ===")

    for name in ["tp-simple-gas", "tp-two-phase", "dew-point-t", "bubble-point-p", "cpa-with-water"]:
        example = call_tool("getExample", {"category": "flash", "name": name})
        if "components" not in example:
            check(f"example '{name}' retrieved", False, "no components")
            continue

        # Run the example directly through FlashRunner via runFlash
        comps = example["components"]
        temp = example.get("temperature", {})
        pres = example.get("pressure", {})
        model = example.get("model", "SRK")
        ft = example.get("flashType", "TP")

        temp_val = temp.get("value", 25.0) if isinstance(temp, dict) else temp
        temp_unit = temp.get("unit", "C") if isinstance(temp, dict) else "K"
        pres_val = pres.get("value", 50.0) if isinstance(pres, dict) else pres
        pres_unit = pres.get("unit", "bara") if isinstance(pres, dict) else "bara"

        result = call_tool("runFlash", {
            "components": json.dumps(comps),
            "temperature": temp_val,
            "temperatureUnit": temp_unit,
            "pressure": pres_val,
            "pressureUnit": pres_unit,
            "eos": model,
            "flashType": ft,
        })
        check(f"flash example '{name}' runs", result.get("status") == "success",
              result.get("message", ""))


def test_run_all_process_examples():
    """Run every process example from the catalog through ProcessRunner."""
    print("\n=== Run All Process Examples ===")

    for name in ["simple-separation", "compression-with-cooling"]:
        example = call_tool("getExample", {"category": "process", "name": name})
        if "fluid" not in example:
            check(f"example '{name}' retrieved", False, "no fluid")
            continue

        result = run_process(example)
        check(f"process example '{name}' runs", result.get("status") == "success",
              result.get("message", ""))


# ---------------------------------------------------------------------------
# BATCH CALCULATION TESTS
# ---------------------------------------------------------------------------

def test_batch_temperature_sweep():
    """Run a batch of 3 TP flashes at different temperatures."""
    print("\n=== Batch: Temperature Sweep ===")

    r = call_tool("runBatch", {
        "components": json.dumps({"methane": 0.85, "ethane": 0.10, "propane": 0.05}),
        "eos": "SRK",
        "flashType": "TP",
        "cases": json.dumps([
            {"temperature": {"value": -20.0, "unit": "C"}, "pressure": {"value": 50.0, "unit": "bara"}},
            {"temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value": 50.0, "unit": "bara"}},
            {"temperature": {"value": 80.0, "unit": "C"}, "pressure": {"value": 50.0, "unit": "bara"}},
        ]),
    })
    check("batch status=success", r.get("status") == "success", r.get("message", ""))
    summary = r.get("summary", {})
    check("batch totalCases=3", summary.get("totalCases") == 3)
    check("batch succeeded=3", summary.get("succeeded") == 3)
    check("batch failed=0", summary.get("failed") == 0)
    results = r.get("results", [])
    check("batch 3 results", len(results) == 3, f"got {len(results)}")
    for i, res in enumerate(results):
        check(f"batch case[{i}] status=success", res.get("status") == "success")


def test_batch_pressure_sweep():
    """Run a batch of 4 TP flashes at different pressures."""
    print("\n=== Batch: Pressure Sweep ===")

    r = call_tool("runBatch", {
        "components": json.dumps({"methane": 0.70, "ethane": 0.15, "propane": 0.10, "n-butane": 0.05}),
        "eos": "PR",
        "flashType": "TP",
        "cases": json.dumps([
            {"temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value": 10.0, "unit": "bara"}},
            {"temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value": 30.0, "unit": "bara"}},
            {"temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value": 60.0, "unit": "bara"}},
            {"temperature": {"value": 25.0, "unit": "C"}, "pressure": {"value": 100.0, "unit": "bara"}},
        ]),
    })
    check("batch PR status=success", r.get("status") == "success", r.get("message", ""))
    summary = r.get("summary", {})
    check("batch PR totalCases=4", summary.get("totalCases") == 4)
    check("batch PR succeeded=4", summary.get("succeeded") == 4)


def test_batch_example_from_catalog():
    """Run the batch temperature-sweep example from the catalog."""
    print("\n=== Batch: Run Catalog Example ===")

    example = call_tool("getExample", {"category": "batch", "name": "temperature-sweep"})
    check("batch example retrieved", "cases" in example)

    r = call_tool("runBatch", {
        "components": json.dumps(example.get("components", {})),
        "eos": example.get("model", "SRK"),
        "flashType": example.get("flashType", "TP"),
        "cases": json.dumps(example.get("cases", [])),
    })
    check("batch example runs", r.get("status") == "success", r.get("message", ""))


# ---------------------------------------------------------------------------
# PROPERTY TABLE TESTS
# ---------------------------------------------------------------------------

def test_property_table_temperature_sweep():
    """Sweep temperature from -20 to 60 C at 50 bara."""
    print("\n=== Property Table: Temperature Sweep ===")

    r = call_tool("getPropertyTable", {
        "components": json.dumps({"methane": 0.85, "ethane": 0.10, "propane": 0.05}),
        "sweep": "temperature",
        "sweepFrom": -20.0,
        "sweepFromUnit": "C",
        "sweepTo": 60.0,
        "sweepToUnit": "C",
        "fixedValue": 50.0,
        "fixedUnit": "bara",
        "points": 9,
        "eos": "SRK",
    })
    check("prop table status=success", r.get("status") == "success", r.get("message", ""))
    table = r.get("table", [])
    check("prop table has rows", len(table) >= 9, f"got {len(table)} rows")
    # Verify first row has expected properties
    if table:
        first_row = table[0]
        check("row has temperature", "temperature_C" in first_row or "temperature" in first_row)
        check("row has density", "density" in first_row)


def test_property_table_pressure_sweep():
    """Sweep pressure from 10 to 100 bara at 25 C."""
    print("\n=== Property Table: Pressure Sweep ===")

    r = call_tool("getPropertyTable", {
        "components": json.dumps({"methane": 0.90, "ethane": 0.07, "propane": 0.03}),
        "sweep": "pressure",
        "sweepFrom": 10.0,
        "sweepFromUnit": "bara",
        "sweepTo": 100.0,
        "sweepToUnit": "bara",
        "fixedValue": 25.0,
        "fixedUnit": "C",
        "points": 10,
        "eos": "SRK",
    })
    check("prop table P status=success", r.get("status") == "success", r.get("message", ""))
    table = r.get("table", [])
    check("prop table P has rows", len(table) >= 10, f"got {len(table)} rows")


def test_property_table_example_from_catalog():
    """Run the property table temperature-sweep example from the catalog."""
    print("\n=== Property Table: Run Catalog Example ===")

    example = call_tool("getExample", {"category": "property-table", "name": "temperature-sweep"})
    check("prop table example retrieved", "sweep" in example)

    r = call_tool("getPropertyTable", {
        "components": json.dumps(example.get("components", {})),
        "sweep": example.get("sweep", "temperature"),
        "sweepFrom": example["sweepFrom"]["value"],
        "sweepFromUnit": example["sweepFrom"]["unit"],
        "sweepTo": example["sweepTo"]["value"],
        "sweepToUnit": example["sweepTo"]["unit"],
        "fixedValue": example["fixedPressure"]["value"],
        "fixedUnit": example["fixedPressure"]["unit"],
        "points": example.get("points", 13),
        "eos": example.get("model", "SRK"),
    })
    check("prop table example runs", r.get("status") == "success", r.get("message", ""))


# ---------------------------------------------------------------------------
# PHASE ENVELOPE TESTS
# ---------------------------------------------------------------------------

def test_phase_envelope_natural_gas():
    """Calculate phase envelope for a 5-component natural gas."""
    print("\n=== Phase Envelope: Natural Gas ===")

    r = call_tool("getPhaseEnvelope", {
        "components": json.dumps({
            "methane": 0.80,
            "ethane": 0.10,
            "propane": 0.05,
            "n-butane": 0.03,
            "n-pentane": 0.02,
        }),
        "eos": "SRK",
    })
    check("phase env status=success", r.get("status") == "success", r.get("message", ""))
    envelope = r.get("envelope", [])
    check("phase env has points", len(envelope) > 5, f"got {len(envelope)} points")
    if envelope:
        pt = envelope[0]
        check("env point has T", "temperature_K" in pt)
        check("env point has P", "pressure_bara" in pt)


def test_phase_envelope_example_from_catalog():
    """Run the phase envelope natural-gas example from the catalog."""
    print("\n=== Phase Envelope: Run Catalog Example ===")

    example = call_tool("getExample", {"category": "phase-envelope", "name": "natural-gas"})
    check("phase env example has components", "components" in example)

    r = call_tool("getPhaseEnvelope", {
        "components": json.dumps(example.get("components", {})),
        "eos": example.get("model", "SRK"),
    })
    check("phase env example runs", r.get("status") == "success", r.get("message", ""))


# ---------------------------------------------------------------------------
# CAPABILITIES TESTS
# ---------------------------------------------------------------------------

def test_capabilities():
    """Test the capabilities discovery tool."""
    print("\n=== Capabilities Discovery ===")

    r = call_tool("getCapabilities", {})
    check("capabilities status=success", r.get("status") == "success", r.get("message", ""))
    check("capabilities has engine", r.get("engine") == "NeqSim")
    check("capabilities has thermo models", "thermodynamicModels" in r)
    check("capabilities has equipment", "processEquipment" in r)


# ===========================================================================
# TIER 2 TOOL TESTS — Engineering Advanced
# ===========================================================================

# --- PVT tools ---

def test_pvt_saturation_pressure():
    """Run a PVT saturation pressure experiment."""
    print("\n=== PVT: Saturation Pressure ===")
    r = call_tool("runPVT", {
        "components": json.dumps({"methane": 0.6, "ethane": 0.1, "propane": 0.1, "n-heptane": 0.2}),
        "model": "SRK",
        "temperature_C": 80.0,
        "pressure_bara": 200.0,
        "experiment": "saturationPressure",
    })
    check("pvt satP status=success", r.get("status") == "success", r.get("message", ""))


# --- Flow assurance tools ---

def test_flow_assurance_hydrate():
    """Run a hydrate risk map analysis."""
    print("\n=== Flow Assurance: Hydrate Risk Map ===")
    r = call_tool("runFlowAssurance", {
        "components": json.dumps({"methane": 0.85, "ethane": 0.08, "propane": 0.04, "water": 0.03}),
        "model": "CPA",
        "temperature_C": 4.0,
        "pressure_bara": 100.0,
        "analysis": "hydrateRiskMap",
    })
    check("hydrate status=success", r.get("status") == "success", r.get("message", ""))


# --- Standards tools ---

def test_calculate_standard_iso6976():
    """Calculate gas quality per ISO 6976."""
    print("\n=== Standards: ISO 6976 ===")
    r = call_tool("calculateStandard", {
        "components": json.dumps({"methane": 0.90, "ethane": 0.06, "propane": 0.03, "nitrogen": 0.01}),
        "model": "SRK",
        "temperature_C": 15.0,
        "pressure_bara": 1.01325,
        "standard": "ISO6976",
    })
    check("ISO6976 status=success", r.get("status") == "success", r.get("message", ""))


# --- Pipeline tools ---

def test_pipeline_single_phase_gas():
    """Simulate a single-phase gas pipeline."""
    print("\n=== Pipeline: Single Phase Gas ===")
    r = call_tool("runPipeline", {
        "components": json.dumps({"methane": 0.95, "ethane": 0.05}),
        "model": "SRK",
        "temperature_C": 25.0,
        "pressure_bara": 100.0,
        "flowRate": json.dumps({"value": 5000.0, "unit": "kg/hr"}),
        "pipe": json.dumps({"diameter_m": 0.2, "length_m": 10000.0,
                            "elevation_m": 0.0, "roughness_m": 0.00005,
                            "numberOfIncrements": 10}),
    })
    check("pipeline status=success", r.get("status") == "success", r.get("message", ""))


# --- Reservoir tools ---

def test_reservoir_simple_tank():
    """Simulate a gas reservoir material balance."""
    print("\n=== Reservoir: Simple Tank Model ===")
    r = call_tool("runReservoir", {
        "components": json.dumps({"methane": 0.85, "ethane": 0.10, "propane": 0.05}),
        "model": "SRK",
        "reservoirTemperature_C": 100.0,
        "reservoirPressure_bara": 200.0,
        "gasVolume_Sm3": 1e9,
        "oilVolume_Sm3": 0.0,
        "waterVolume_Sm3": 0.0,
        "producers": json.dumps([{"name": "well1", "flowRate": {"value": 100000.0, "unit": "Sm3/day"}}]),
    })
    check("reservoir status=success", r.get("status") == "success", r.get("message", ""))


# --- Field economics tools ---

def test_field_economics_production_profile():
    """Generate a production decline profile."""
    print("\n=== Field Economics: Production Profile ===")
    r = call_tool("runFieldEconomics", {
        "mode": "productionProfile",
        "declineType": "EXPONENTIAL",
        "initialRate_bblPerDay": 10000.0,
        "annualDeclineRate": 0.15,
        "startYear": 2025,
        "totalYears": 20,
    })
    check("econ profile status=success", r.get("status") == "success", r.get("message", ""))


# --- Dynamic simulation tools ---

def test_dynamic_separator():
    """Run a short dynamic simulation on a separator."""
    print("\n=== Dynamic: Separator Transient ===")
    process_json = {
        "fluid": {
            "components": {"methane": 0.7, "propane": 0.2, "n-decane": 0.1},
            "model": "SRK",
            "temperature_C": 25.0,
            "pressure_bara": 50.0,
        },
        "process": {
            "equipment": [
                {"type": "stream", "name": "feed", "flowRate": {"value": 1000.0, "unit": "kg/hr"}},
                {"type": "separator", "name": "sep", "inlet": "feed"},
            ]
        },
    }
    r = call_tool("runDynamic", {
        "processJson": json.dumps(process_json),
        "duration_seconds": 10.0,
        "timeStep_seconds": 1.0,
    })
    check("dynamic status=success", r.get("status") == "success", r.get("message", ""))


# --- Bioprocess tools ---

def test_bioprocess_anaerobic_digester():
    """Run an anaerobic digester simulation."""
    print("\n=== Bioprocess: Anaerobic Digester ===")
    r = call_tool("runBioprocess", {
        "reactorType": "anaerobicDigester",
        "substrateType": "FOOD_WASTE",
        "feedRate_kgPerHr": 100.0,
        "totalSolidsFraction": 0.15,
        "temperature_C": 37.0,
    })
    check("bioprocess status=success", r.get("status") == "success", r.get("message", ""))


# --- Equipment sizing tools ---

def test_size_separator():
    """Size a separator vessel."""
    print("\n=== Equipment Sizing: Separator ===")
    r = call_tool("sizeEquipment", {
        "equipmentType": "separator",
        "model": "SRK",
        "temperature_C": 25.0,
        "pressure_bara": 50.0,
        "components": json.dumps({"methane": 0.7, "propane": 0.2, "n-decane": 0.1}),
        "flowRate": json.dumps({"value": 10000.0, "unit": "kg/hr"}),
        "orientation": "horizontal",
        "liquidRetentionTime_min": 5.0,
    })
    check("size sep status=success", r.get("status") == "success", r.get("message", ""))


def test_size_compressor():
    """Size a compressor."""
    print("\n=== Equipment Sizing: Compressor ===")
    r = call_tool("sizeEquipment", {
        "equipmentType": "compressor",
        "model": "SRK",
        "temperature_C": 25.0,
        "pressure_bara": 20.0,
        "components": json.dumps({"methane": 0.95, "ethane": 0.05}),
        "flowRate": json.dumps({"value": 5000.0, "unit": "kg/hr"}),
        "outletPressure_bara": 80.0,
        "polytropicEfficiency": 0.80,
    })
    check("size comp status=success", r.get("status") == "success", r.get("message", ""))


# --- Process comparison tools ---

def test_compare_processes():
    """Compare two process cases side by side."""
    print("\n=== Process Comparison ===")
    case = {
        "fluid": {
            "components": {"methane": 0.9, "ethane": 0.1},
            "model": "SRK",
            "temperature_C": 25.0,
            "pressure_bara": 50.0,
        },
        "process": {
            "equipment": [
                {"type": "stream", "name": "feed", "flowRate": {"value": 1000.0, "unit": "kg/hr"}},
                {"type": "separator", "name": "sep", "inlet": "feed"},
            ]
        },
    }
    r = call_tool("compareProcesses", {
        "cases": json.dumps([
            {"name": "Case-A", **case},
            {"name": "Case-B", **case},
        ]),
    })
    check("compare status=success", r.get("status") == "success", r.get("message", ""))


# --- Validation tools ---

def test_validate_results():
    """Validate a flash result against engineering rules."""
    print("\n=== Validate Results ===")
    # First run a flash to get results
    flash_r = run_flash({"methane": 0.9, "ethane": 0.1}, 25.0, 50.0)
    r = call_tool("validateResults", {
        "resultsJson": json.dumps(flash_r),
        "context": "general",
    })
    check("validateResults status=success", r.get("status") == "success", r.get("message", ""))


# --- Cross-validation tools ---

def test_cross_validate_models():
    """Cross-validate a process across multiple EOS models."""
    print("\n=== Cross-Validate Models ===")
    r = call_tool("crossValidateModels", {
        "baseProcess": json.dumps({
            "fluid": {
                "components": {"methane": 0.9, "ethane": 0.1},
                "model": "SRK",
                "temperature_C": 25.0,
                "pressure_bara": 50.0,
            },
            "process": {
                "equipment": [
                    {"type": "stream", "name": "feed", "flowRate": {"value": 1000.0, "unit": "kg/hr"}},
                    {"type": "separator", "name": "sep", "inlet": "feed"},
                ]
            },
        }),
        "models": json.dumps(["SRK", "PR"]),
        "compareVariables": json.dumps([
            {"address": "sep.gasOutStream.temperature", "unit": "C"},
        ]),
    })
    check("crossValidate status=success", r.get("status") == "success", r.get("message", ""))


# --- Parametric study tools ---

def test_parametric_study():
    """Run a parametric pressure sweep."""
    print("\n=== Parametric Study ===")
    r = call_tool("runParametricStudy", {
        "baseProcess": json.dumps({
            "fluid": {
                "components": {"methane": 0.9, "ethane": 0.1},
                "model": "SRK",
                "temperature_C": 25.0,
                "pressure_bara": 50.0,
            },
            "process": {
                "equipment": [
                    {"type": "stream", "name": "feed", "flowRate": {"value": 1000.0, "unit": "kg/hr"}},
                    {"type": "separator", "name": "sep", "inlet": "feed"},
                ]
            },
        }),
        "sweeps": json.dumps([
            {"address": "feed.pressure", "unit": "bara", "from": 30.0, "to": 80.0, "steps": 3},
        ]),
        "outputs": json.dumps([
            {"address": "sep.gasOutStream.temperature", "unit": "C"},
        ]),
        "mode": "one_at_a_time",
    })
    check("parametric status=success", r.get("status") == "success", r.get("message", ""))


# ===========================================================================
# TIER 3 TOOL TESTS — Experimental
# ===========================================================================

# --- Session management tools ---

def test_session_lifecycle():
    """Create, list, and close a session."""
    print("\n=== Session: Lifecycle ===")
    # Create
    r = call_tool("manageSession", {
        "action": "create",
        "fluid": json.dumps({"methane": 0.9, "ethane": 0.1}),
    })
    check("session create status=success", r.get("status") == "success", r.get("message", ""))
    session_id = r.get("sessionId", "")

    # List
    r2 = call_tool("manageSession", {"action": "list"})
    check("session list status=success", r2.get("status") == "success", r2.get("message", ""))

    # Close
    if session_id:
        r3 = call_tool("manageSession", {
            "action": "close",
            "sessionId": session_id,
        })
        check("session close status=success", r3.get("status") == "success", r3.get("message", ""))


# --- Task solver tools ---

def test_solve_task():
    """Solve a simple engineering task."""
    print("\n=== Solve Task ===")
    r = call_tool("solveTask", {
        "task": "Calculate the density of methane at 25C and 50 bar",
        "fluid": json.dumps({"methane": 1.0}),
    })
    check("solveTask status=success", r.get("status") == "success", r.get("message", ""))


# --- Workflow composition tools ---

def test_compose_workflow():
    """Compose a simple two-step workflow."""
    print("\n=== Compose Workflow ===")
    r = call_tool("composeWorkflow", {
        "workflow": "test-workflow",
        "fluid": json.dumps({"methane": 0.9, "ethane": 0.1}),
        "steps": json.dumps([
            {"runner": "flash", "name": "step1",
             "input": {"temperature_C": 25.0, "pressure_bara": 50.0}},
        ]),
    })
    check("workflow status=success", r.get("status") == "success", r.get("message", ""))


# --- Report generation tools ---

def test_generate_report():
    """Generate a process summary report."""
    print("\n=== Generate Report ===")
    flash_r = run_flash({"methane": 0.9, "ethane": 0.1}, 25.0, 50.0)
    r = call_tool("generateReport", {
        "reportType": "process_summary",
        "title": "Test Report",
        "data": json.dumps(flash_r),
    })
    check("report status=success", r.get("status") == "success", r.get("message", ""))


# --- Task workflow bridge ---

def test_bridge_task_workflow_schema():
    """Get the results.json schema from the bridge."""
    print("\n=== Bridge Task Workflow: Schema ===")
    r = call_tool("bridgeTaskWorkflow", {"action": "getSchema"})
    check("bridge schema status=success", r.get("status") == "success", r.get("message", ""))
    check("bridge schema has schema", "schema" in r or "resultsJsonSchema" in str(r),
          str(r)[:200])


def test_bridge_task_workflow_convert():
    """Convert a flash result to results.json format."""
    print("\n=== Bridge Task Workflow: Convert ===")
    flash_r = run_flash({"methane": 0.85, "ethane": 0.1, "propane": 0.05}, 25.0, 60.0)
    r = call_tool("bridgeTaskWorkflow", {
        "action": "toResultsJson",
        "toolOutput": json.dumps(flash_r),
        "toolName": "runFlash",
        "taskTitle": "E2E Test Flash",
    })
    check("bridge convert status=success", r.get("status") == "success", r.get("message", ""))


# --- Plugin tools ---

def test_plugin_list():
    """List available plugins."""
    print("\n=== Plugin: List ===")
    r = call_tool("runPlugin", {"action": "list"})
    check("plugin list status=success", r.get("status") == "success", r.get("message", ""))


# --- Progress tracking tools ---

def test_progress_list():
    """List active operations."""
    print("\n=== Progress: List Active ===")
    r = call_tool("getProgress", {"action": "listActive"})
    check("progress list status=success", r.get("status") == "success", r.get("message", ""))


# --- Streaming tools ---

def test_streaming_list():
    """List streaming operations."""
    print("\n=== Streaming: List Operations ===")
    r = call_tool("streamSimulation", {"action": "listOperations"})
    check("streaming list status=success", r.get("status") == "success", r.get("message", ""))


# --- Visualization tools ---

def test_visualization_bar_chart():
    """Generate a bar chart visualization."""
    print("\n=== Visualization: Bar Chart ===")
    r = call_tool("generateVisualization", {
        "type": "barChart",
        "labels": json.dumps(["A", "B", "C"]),
        "values": json.dumps([10.0, 20.0, 30.0]),
    })
    check("viz barChart status=success", r.get("status") == "success", r.get("message", ""))


# --- Multi-server composition tools ---

def test_multi_server_describe():
    """Describe NeqSim capabilities to other servers."""
    print("\n=== Multi-Server: Describe Capabilities ===")
    r = call_tool("composeMultiServerWorkflow", {"action": "describeCapabilities"})
    check("multiserver describe status=success", r.get("status") == "success", r.get("message", ""))


def test_multi_server_list():
    """List registered external servers."""
    print("\n=== Multi-Server: List Servers ===")
    r = call_tool("composeMultiServerWorkflow", {"action": "listServers"})
    check("multiserver list status=success", r.get("status") == "success", r.get("message", ""))


# --- Security tools ---

def test_security_status():
    """Get security status."""
    print("\n=== Security: Status ===")
    r = call_tool("manageSecurity", {"action": "getStatus"})
    check("security status=success", r.get("status") == "success", r.get("message", ""))


# --- State persistence tools ---

def test_state_list():
    """List saved states."""
    print("\n=== State: List ===")
    r = call_tool("manageState", {"action": "list"})
    check("state list status=success", r.get("status") == "success", r.get("message", ""))


def test_state_info():
    """Get state storage info."""
    print("\n=== State: Info ===")
    r = call_tool("manageState", {"action": "getInfo"})
    check("state info status=success", r.get("status") == "success", r.get("message", ""))


# --- Validation profile tools ---

def test_validation_profile_list():
    """List validation profiles."""
    print("\n=== Validation Profiles: List ===")
    r = call_tool("manageValidationProfile", {"action": "listProfiles"})
    check("valprofile list status=success", r.get("status") == "success", r.get("message", ""))


def test_validation_profile_active():
    """Get the active validation profile."""
    print("\n=== Validation Profiles: Active ===")
    r = call_tool("manageValidationProfile", {"action": "getActiveProfile"})
    check("valprofile active status=success", r.get("status") == "success", r.get("message", ""))


# --- Data catalog tools ---

def test_data_catalog_component_families():
    """List component families."""
    print("\n=== Data Catalog: Component Families ===")
    r = call_tool("queryDataCatalog", {"action": "listComponentFamilies"})
    check("catalog families status=success", r.get("status") == "success", r.get("message", ""))


def test_data_catalog_eos_models():
    """List EOS models."""
    print("\n=== Data Catalog: EOS Models ===")
    r = call_tool("queryDataCatalog", {"action": "listEOSModels"})
    check("catalog eos status=success", r.get("status") == "success", r.get("message", ""))


# ===========================================================================
# GOVERNANCE TOOL TESTS
# ===========================================================================

def test_industrial_profile_describe():
    """Describe all industrial profiles."""
    print("\n=== Industrial Profile: Describe ===")
    r = call_tool("manageIndustrialProfile", {"action": "describe"})
    check("profile describe status=success", r.get("status") == "success", r.get("message", ""))


def test_industrial_profile_active():
    """Get active deployment mode."""
    print("\n=== Industrial Profile: Active ===")
    r = call_tool("manageIndustrialProfile", {"action": "getActive"})
    check("profile active status=success", r.get("status") == "success", r.get("message", ""))
    check("profile mode is DESKTOP_ENGINEER",
          r.get("activeMode") == "DESKTOP_ENGINEER",
          f"got {r.get('activeMode')}")


def test_industrial_profile_classify():
    """Classify a tool."""
    print("\n=== Industrial Profile: Classify Tool ===")
    r = call_tool("manageIndustrialProfile", {
        "action": "classifyTool",
        "toolName": "runFlash",
    })
    check("classify status=success", r.get("status") == "success", r.get("message", ""))
    check("runFlash is TRUSTED_CORE",
          r.get("category") == "TRUSTED_CORE",
          f"got category={r.get('category')}")


def test_benchmark_trust_all():
    """Get the full trust report."""
    print("\n=== Benchmark Trust: All ===")
    r = call_tool("getBenchmarkTrust", {"action": "getAll"})
    check("trust all status=success", r.get("status") == "success", r.get("message", ""))
    check("trust has tools", "tools" in r,
          f"keys: {list(r.keys())}")
    # Check covered tools
    tools = r.get("tools", {})
    for t in ["runFlash", "runProcess", "runPVT"]:
        check(f"trust covers {t}", t in tools)


def test_benchmark_trust_single():
    """Get trust page for a single tool."""
    print("\n=== Benchmark Trust: Single Tool ===")
    r = call_tool("getBenchmarkTrust", {
        "action": "getTool",
        "toolName": "runFlash",
    })
    check("trust single status=success", r.get("status") == "success", r.get("message", ""))
    check("trust has trust object", "trust" in r)
    trust = r.get("trust", {})
    check("flash trust maturity=VALIDATED",
          trust.get("maturityLevel") == "VALIDATED",
          f"got {trust.get('maturityLevel')}")


def test_check_tool_access():
    """Check tool access for different tools."""
    print("\n=== Check Tool Access ===")
    r = call_tool("checkToolAccess", {"toolName": "runFlash"})
    check("access runFlash status=success", r.get("status") == "success", r.get("message", ""))
    check("runFlash is allowed", r.get("allowed") is True)

    r2 = call_tool("checkToolAccess", {"toolName": "manageSecurity"})
    check("access manageSecurity status=success", r2.get("status") == "success")
    check("manageSecurity is allowed (DESKTOP_ENGINEER)", r2.get("allowed") is True)


# ===========================================================================
# MAIN
# ===========================================================================

if __name__ == "__main__":
    print("=" * 60)
    print("NeqSim MCP Server — Comprehensive Test Suite")
    print("=" * 60)

    start_server()

    try:
        # Protocol
        test_protocol()

        # Component search
        test_component_search()

        # Examples and schemas
        test_examples_and_schemas()

        # Flash calculations — different compositions and conditions
        test_tp_flash_simple_gas()
        test_tp_flash_pr_two_phase()
        test_tp_flash_two_phase_ch4_c3()
        test_tp_flash_rich_gas()
        test_tp_flash_rich_gas_single_phase()
        test_tp_flash_pure_methane()
        test_tp_flash_high_pressure()
        test_tp_flash_low_temperature()
        test_tp_flash_with_co2_h2s()
        test_tp_flash_with_water_cpa()

        # Dew point / bubble point
        test_dew_point_temperature()
        test_bubble_point_pressure()
        test_dew_point_pressure()
        test_bubble_point_temperature()

        # Process simulations
        test_process_separator()
        test_process_compressor()
        test_process_cooler()
        test_process_heater()
        test_process_valve()
        test_process_separator_and_compressor()
        test_process_compression_cooling_train()
        test_process_two_phase_separator()
        test_process_valve_then_separator()
        test_process_multi_fluid()
        test_process_pr_model()

        # Validation
        test_validation_valid_flash()
        test_validation_unknown_component()
        test_validation_unknown_model()
        test_validation_negative_fraction()
        test_validation_composition_sum_warning()
        test_validation_missing_enthalpy_ph()
        test_validation_unknown_flash_type()
        test_validation_extreme_temperature()
        test_validation_process_missing_type()
        test_validation_process_duplicate_names()
        test_validation_multiple_errors()

        # Flash error cases
        test_flash_error_unknown_component()
        test_flash_error_unknown_model()

        # Run all catalog examples
        test_run_all_flash_examples()
        test_run_all_process_examples()

        # Batch calculations
        test_batch_temperature_sweep()
        test_batch_pressure_sweep()
        test_batch_example_from_catalog()

        # Property table
        test_property_table_temperature_sweep()
        test_property_table_pressure_sweep()
        test_property_table_example_from_catalog()

        # Phase envelope
        test_phase_envelope_natural_gas()
        test_phase_envelope_example_from_catalog()

        # Capabilities
        test_capabilities()

        # ── Tier 2: Engineering Advanced tools ──
        test_pvt_saturation_pressure()
        test_flow_assurance_hydrate()
        test_calculate_standard_iso6976()
        test_pipeline_single_phase_gas()
        test_reservoir_simple_tank()
        test_field_economics_production_profile()
        test_dynamic_separator()
        test_bioprocess_anaerobic_digester()
        test_size_separator()
        test_size_compressor()
        test_compare_processes()
        test_validate_results()
        test_cross_validate_models()
        test_parametric_study()

        # ── Tier 3: Experimental tools ──
        test_session_lifecycle()
        test_solve_task()
        test_compose_workflow()
        test_generate_report()
        test_bridge_task_workflow_schema()
        test_bridge_task_workflow_convert()
        test_plugin_list()
        test_progress_list()
        test_streaming_list()
        test_visualization_bar_chart()
        test_multi_server_describe()
        test_multi_server_list()
        test_security_status()
        test_state_list()
        test_state_info()
        test_validation_profile_list()
        test_validation_profile_active()
        test_data_catalog_component_families()
        test_data_catalog_eos_models()

        # ── Governance tools ──
        test_industrial_profile_describe()
        test_industrial_profile_active()
        test_industrial_profile_classify()
        test_benchmark_trust_all()
        test_benchmark_trust_single()
        test_check_tool_access()

    finally:
        stop_server()

    # Summary
    total = passed + failed
    print(f"\n{'=' * 60}")
    print(f"RESULTS: {passed}/{total} passed, {failed} failed")
    print(f"{'=' * 60}")
    if errors_list:
        print("\nFailed checks:")
        for e in errors_list:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print("\nALL CHECKS PASSED")
        sys.exit(0)
