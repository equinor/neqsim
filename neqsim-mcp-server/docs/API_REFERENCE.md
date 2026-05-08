# NeqSim MCP Server — API Reference

Detailed parameter documentation for MCP tools and resources.
For the governance model, tier structure, and stability promises, see
[MCP_CONTRACT.md](../MCP_CONTRACT.md).

---

## `runFlash` — Thermodynamic Flash Calculation

Computes phase equilibrium for a fluid mixture. Returns per-phase densities,
viscosities, thermal conductivities, heat capacities, compressibility factors,
and compositions.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `components` | JSON string | Component-to-mole-fraction map, e.g. `{"methane": 0.85, "ethane": 0.15}` |
| `temperature` | number | Temperature value |
| `temperatureUnit` | string | `C`, `K`, or `F` |
| `pressure` | number | Pressure value |
| `pressureUnit` | string | `bara`, `barg`, `Pa`, `kPa`, `MPa`, `psi`, or `atm` |
| `eos` | string | Equation of state (see table below) |
| `flashType` | string | Flash algorithm (see table below) |

**Supported Equations of State:**

| EOS | Full Name | Best For |
|---|---|---|
| `SRK` | Soave-Redlich-Kwong | General hydrocarbon systems (default) |
| `PR` | Peng-Robinson | General purpose, slightly different liquid densities |
| `CPA` | CPA-SRK | Systems with water, methanol, MEG, or other associating fluids |
| `GERG2008` | GERG-2008 | High-accuracy natural gas (reference-quality) |
| `PCSAFT` | PC-SAFT | Polymers, associating fluids |
| `UMRPRU` | UMR-PRU with Mathias-Copeman | Advanced mixing rules |

**Supported Flash Types:**

| Flash Type | Description |
|---|---|
| `TP` | Temperature-Pressure flash (most common) |
| `PH` | Pressure-Enthalpy flash (requires `enthalpy` in input) |
| `PS` | Pressure-Entropy flash (requires `entropy` in input) |
| `TV` | Temperature-Volume flash (requires `volume` in input) |
| `dewPointT` | Dew point temperature at given pressure |
| `dewPointP` | Dew point pressure at given temperature |
| `bubblePointT` | Bubble point temperature at given pressure |
| `bubblePointP` | Bubble point pressure at given temperature |
| `hydrateTP` | Hydrate equilibrium temperature at given pressure |

**Example call (via MCP JSON-RPC):**

```json
{
  "method": "tools/call",
  "params": {
    "name": "runFlash",
    "arguments": {
      "components": "{\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}",
      "temperature": 25.0,
      "temperatureUnit": "C",
      "pressure": 50.0,
      "pressureUnit": "bara",
      "eos": "SRK",
      "flashType": "TP"
    }
  }
}
```

**Example response (abbreviated):**

```json
{
  "status": "success",
  "flash": {
    "model": "SRK",
    "flashType": "TP",
    "numberOfPhases": 1,
    "phases": ["gas"]
  },
  "fluid": {
    "properties": {
      "gas": {
        "density": { "value": 38.9, "unit": "kg/m3" },
        "compressibilityFactor": { "value": 0.907, "unit": "" },
        "viscosity": { "value": 1.17e-5, "unit": "Pa·s" },
        "thermalConductivity": { "value": 0.038, "unit": "W/(m·K)" },
        "Cp": { "value": 2350, "unit": "J/(kg·K)" }
      }
    },
    "composition": {
      "gas": {
        "methane": { "value": 0.85 },
        "ethane": { "value": 0.10 },
        "propane": { "value": 0.05 }
      }
    }
  }
}
```

---

## `runProcess` — Process Simulation

Builds and runs a flowsheet from a JSON definition. Supports single-area
`ProcessSystem` JSON (`fluid` + `process`) and multi-area `ProcessModel` JSON
with top-level `areas`. The stable core supports streams, separators,
compressors, coolers, heaters, valves, mixers, splitters, heat exchangers,
distillation columns, and pipelines; the builder also accepts additional
factory-backed equipment types where their constructor and stream wiring fit the
generic JSON pattern.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `processJson` | JSON string | Complete process definition (see format below) |

**Process JSON format:**

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.85,
      "ethane": 0.10,
      "propane": 0.05
    }
  },
  "process": [
    {
      "type": "Stream",
      "name": "feed",
      "properties": { "flowRate": [50000.0, "kg/hr"] }
    },
    {
      "type": "Separator",
      "name": "HP Sep",
      "inlet": "feed"
    },
    {
      "type": "Compressor",
      "name": "Comp",
      "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": [80.0, "bara"] }
    }
  ]
}
```

**Equipment types:** `Stream`, `Separator`, `Compressor`, `Cooler`, `Heater`,
`Valve`, `Mixer`, `Splitter`, `HeatExchanger`, `DistillationColumn`, `Pipe`

**Outlet port selectors** (for connecting equipment):

| Port | Description |
|---|---|
| `<name>.gasOut` | Gas outlet from separator |
| `<name>.liquidOut` | Liquid outlet from separator |
| `<name>.oilOut` | Oil outlet from three-phase separator |
| `<name>.waterOut` | Water outlet from three-phase separator |

**Multiple fluids** — use `"fluids"` (plural) with named references:

```json
{
  "fluids": {
    "gas": { "model": "SRK", "temperature": 298.15, "pressure": 50.0, "components": {"methane": 0.9} },
    "oil": { "model": "PR", "temperature": 350.0, "pressure": 100.0, "components": {"nC10": 1.0} }
  },
  "process": [
    { "type": "Stream", "name": "gasFeed", "fluidRef": "gas", "properties": {"flowRate": [10000.0, "kg/hr"]} },
    { "type": "Stream", "name": "oilFeed", "fluidRef": "oil", "properties": {"flowRate": [50000.0, "kg/hr"]} }
  ]
}
```

**Multi-area process models** — use top-level `"areas"` with one standard
process JSON object per area:

```json
{
  "areas": {
    "separation": {
      "fluid": { "model": "SRK", "temperature": 298.15, "pressure": 50.0,
        "components": { "methane": 0.9, "ethane": 0.1 } },
      "process": [
        { "type": "Stream", "name": "feed", "properties": { "flowRate": [10000.0, "kg/hr"] } },
        { "type": "Separator", "name": "Sep", "inlet": "feed" }
      ]
    },
    "compression": {
      "fluid": { "model": "SRK", "temperature": 298.15, "pressure": 50.0,
        "components": { "methane": 0.9, "ethane": 0.1 } },
      "process": [
        { "type": "Stream", "name": "compFeed", "properties": { "flowRate": [10000.0, "kg/hr"] } },
        { "type": "Compressor", "name": "Comp", "inlet": "compFeed",
          "properties": { "outletPressure": [80.0, "bara"] } }
      ]
    }
  }
}
```

**Extended factory-backed equipment:** `Pump`, `Expander`, `Tank`,
`ComponentSplitter`, `Recycle`, `Adjuster`, `GasScrubber`,
`ThreePhaseSeparator`, `SimpleReservoir`, `Flare`, `FlareStack`, `FuelCell`,
`Electrolyzer`, `CO2Electrolyzer`, `WindTurbine`, `WindFarm`,
`BatteryStorage`, `SolarPanel`, `OffshoreEnergySystem`,
`AmmoniaSynthesisReactor`, and `SubseaPowerCable` are recognized by validation
and routed through `EquipmentFactory`. Equipment that needs non-generic
construction or custom multi-port semantics may still require a dedicated MCP
runner or builder extension.

---

## `runHAZOP` — Simulation-backed HAZOP Study

Generates a first-pass IEC 61882 HAZOP worksheet from a NeqSim process
definition, optional STID/P&ID-extracted nodes, selected failure modes, and an
optional barrier register. The tool runs the baseline process, generates
equipment-failure scenarios, executes those scenarios against copied process
models, and returns HAZOP rows with simulation evidence.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `hazopJson` | JSON string | HAZOP study input with `processDefinition`, optional `nodes`, `failureModes`, `barrierRegister`, and `runSimulations` |

**Input highlights:**

- `processDefinition` uses the same JSON format as `runProcess`.
- `nodes[]` may include `nodeId`, `designIntent`, `equipment`, `safeguards`, and `evidenceRefs`.
- `failureModes[]` may include `COOLING_LOSS`, `VALVE_STUCK_CLOSED`, `COMPRESSOR_TRIP`, `PUMP_TRIP`, or `BLOCKED_OUTLET`.
- `barrierRegister` is passed to `runBarrierRegister` and returned as a handoff block.

**Output highlights:**

- `hazopRows` with node, guideword, parameter, cause, consequence, safeguards, recommendation, and evidence references.
- `scenarioResults` with per-scenario status, execution time, and captured KPI values.
- `qualityGates` showing human review, document evidence, and generated-row checks.
- `reportMarkdown` for engineering report generation.

Use `getExample` with category `safety` and name `hazop-study` for a complete
template. Use `getSchema` with tool name `run_hazop` for JSON Schema.

---

## `validateInput` — Pre-flight Validation

Validates a flash or process JSON **before running it**. Catches common mistakes
and returns actionable fix suggestions.

**Checks performed:**
- Component names exist in the database (suggests corrections for typos)
- Temperature and pressure are in physically reasonable ranges
- EOS model is recognized
- Flash type is valid, and required specs are present (e.g. enthalpy for PH flash)
- Composition sums are reasonable
- Process equipment types are recognized
- Duplicate equipment names detected

**Example response (with errors):**

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "error",
      "code": "UNKNOWN_COMPONENT",
      "message": "'metane' is not a known component. Did you mean 'methane'?"
    },
    {
      "severity": "error",
      "code": "UNKNOWN_MODEL",
      "message": "'FAKEOS' is not a supported model. Valid: SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU"
    }
  ]
}
```

---

## `searchComponents` — Component Database Search

Searches the NeqSim component database by name (partial matching, case-insensitive).

**Examples:**
- `query: "methane"` → `["methane"]`
- `query: "meth"` → `["methane", "methanol", "dimethylether", ...]`
- `query: ""` → all 100+ components

---

## `getExample` — Example Templates

Returns ready-to-use JSON examples. The LLM reads these to learn the format,
then modifies them based on the user's requirements.

**Available examples:**

| Category | Name | Description |
|---|---|---|
| `flash` | `tp-simple-gas` | TP flash of a simple natural gas |
| `flash` | `tp-two-phase` | TP flash producing gas + liquid phases |
| `flash` | `dew-point-t` | Dew point temperature calculation |
| `flash` | `bubble-point-p` | Bubble point pressure calculation |
| `flash` | `cpa-with-water` | CPA EOS flash with water (associating fluid) |
| `process` | `simple-separation` | Stream → Separator |
| `process` | `compression-with-cooling` | Stream → Compressor → Cooler |
| `validation` | `error-flash` | A deliberately invalid flash input |
| `safety` | `hazop-study` | Simulation-backed HAZOP from process scenarios and document evidence |
| `safety` | `barrier-register` | Evidence-linked PSF/SCE barrier register |

---

## `getSchema` — JSON Schemas

Returns JSON Schema (Draft 2020-12) definitions for tool inputs and outputs.

**Available schemas:**

| Tool Name | Types | Description |
|---|---|---|
| `run_flash` | `input`, `output` | Flash calculation JSON format |
| `run_process` | `input`, `output` | Process simulation JSON format |
| `run_hazop` | `input`, `output` | Simulation-backed HAZOP study JSON format |
| `run_barrier_register` | `input`, `output` | Barrier register JSON format |
| `validate_input` | `input`, `output` | Validator JSON format |
| `search_components` | `input`, `output` | Component search JSON format |

---

## Browsable MCP Resources (11 Endpoints)

### Catalog Resources (Static)

| URI | Description |
|---|---|
| `neqsim://example-catalog` | Full catalog of all examples with descriptions |
| `neqsim://schema-catalog` | Full catalog of all JSON schemas |
| `neqsim://components` | Component families: hydrocarbons, acid gases, glycols, olefins, etc. |
| `neqsim://standards` | Design standards catalog: ASME, API, DNV, ISO, NORSOK |
| `neqsim://models` | Equation of state models with usage recommendations |
| `neqsim://data-tables` | All queryable tables in thermodynamic and design databases |

### Template Resources (Parameterized)

| URI Pattern | Description |
|---|---|
| `neqsim://examples/{category}/{name}` | Specific example by category and name |
| `neqsim://schemas/{tool}/{type}` | Specific schema by tool name and type |
| `neqsim://components/{name}` | Full properties for a component (Tc, Pc, omega, MW, etc.) |
| `neqsim://standards/{code}` | Parameters for a specific design standard |
| `neqsim://materials/{type}` | Material grades by type: pipe, plate, casing, etc. |
