---
name: neqsim-process-extraction
description: "Extracts process simulation data from unstructured sources (text, tables, PFDs, data sheets, STID/E3D line lists) and converts it to NeqSim JSON builder format or PipingRouteBuilder route models. USE WHEN: a user provides a process description, PFD, operating data, line-list table, or design document and wants a running NeqSim simulation. Covers equipment mapping, stream wiring, route hydraulics, unit conversion, composition normalization, and confidence scoring."
last_verified: "2026-07-04"
---

# NeqSim Process Extraction Skill

Convert unstructured engineering information into the canonical NeqSim JSON format
accepted by `ProcessSystem.fromJson()` and `ProcessSystem.fromJsonAndRun()`.

## Core Principle

> **Extract structured data into a constrained JSON schema. Do NOT write NeqSim Java/Python code.**
>
> The JSON schema is finite and well-defined. `ProcessSystem.fromJson()` handles all
> NeqSim API calls deterministically. Errors come back as structured, actionable messages.
>
> **P&ID operational workflow:** When the source is a P&ID and the user asks
> about a valve action, active train, isolation boundary, bypass, drain, vent,
> or control-loop behavior, load `neqsim-pid-process-operations`. Extract both
> the steady-state topology and the model delta needed to simulate the action.
>
> **Exception for route hydraulics:** When the source is a STID/E3D/P&ID/stress-isometric
> line-list table with serial pipe segments, use
> `neqsim.process.equipment.pipeline.routing.PipingRouteBuilder` rather than the generic
> JSON process builder. The route builder preserves line-list segment metadata, K-value
> minor losses, elevations, and explicit connection topology.
>
> **Architecture decision (MANDATORY):** Before assembling JSON, classify the process
> complexity. Small/medium processes (≤ ~15 units, single recycle loop) use a single
> `ProcessSystem`. Large processes (multiple plant areas, cross-area recycles, different
> fluids) must be split into multiple `ProcessSystem` objects composed inside a
> `ProcessModule`, or use pre-built `ProcessModuleBaseClass` implementations.
> See **Section 16** for the decision guide.

---

## 1. Target JSON Schema

Every extraction must produce JSON matching this format:

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 323.15,
    "pressure": 65.0,
    "mixingRule": "classic",
    "multiPhaseCheck": false,
    "components": {
      "methane": 0.80,
      "ethane": 0.08,
      "propane": 0.05,
      "CO2": 0.03,
      "n-butane": 0.02,
      "nitrogen": 0.01,
      "n-pentane": 0.005,
      "n-hexane": 0.005
    }
  },
  "process": [
    {"type": "Stream", "name": "well stream", "properties": {"flowRate": [75000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "inlet separator", "inlet": "well stream"},
    {"type": "Compressor", "name": "export compressor", "inlet": "inlet separator.gasOut",
     "properties": {"outletPressure": 120.0, "isentropicEfficiency": 0.78}},
    {"type": "ThrottlingValve", "name": "letdown valve", "inlet": "inlet separator.oilOut",
     "properties": {"outletPressure": 15.0}}
  ],
  "autoRun": true
}
```

### Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `fluid.model` | string | Yes | EOS model: `SRK`, `PR`, `CPA`, `GERG2008`, `PCSAFT`, `UMRPRU` |
| `fluid.temperature` | number | Yes | Temperature in **Kelvin** |
| `fluid.pressure` | number | Yes | Pressure in **bara** |
| `fluid.mixingRule` | string | Yes | Usually `"classic"` for SRK/PR, `"CLASSIC_TX_CPA"` for CPA |
| `fluid.multiPhaseCheck` | boolean | No | Set `true` for water+HC or 3-phase systems |
| `fluid.components` | object | Yes | Component name → mole fraction (must sum to ~1.0) |
| `process[].type` | string | Yes | Equipment type from the Equipment Type Table below |
| `process[].name` | string | Yes | Unique equipment tag / display name |
| `process[].inlet` | string | Conditional | Stream reference (dot-notation). Required for all except 1st Stream |
| `process[].properties` | object | No | Equipment-specific settings (see Properties Reference) |
| `autoRun` | boolean | No | Set `true` to auto-run after building |

### Multiple Fluids (Named)

For processes with different feed compositions, use the `fluids` map:

```json
{
  "fluids": {
    "gas_feed": { "model": "SRK", "temperature": 323.15, "pressure": 80.0, "mixingRule": "classic", "components": {"methane": 0.90, "ethane": 0.05, "propane": 0.03, "n-butane": 0.02} },
    "water_feed": { "model": "CPA", "temperature": 293.15, "pressure": 80.0, "mixingRule": "CLASSIC_TX_CPA", "components": {"water": 0.999, "MEG": 0.001} }
  },
  "process": [
    {"type": "Stream", "name": "gas inlet", "fluidRef": "gas_feed", "properties": {"flowRate": [50000.0, "kg/hr"]}},
    {"type": "Stream", "name": "water inlet", "fluidRef": "water_feed", "properties": {"flowRate": [5000.0, "kg/hr"]}}
  ]
}
```

---

## 2. Equipment Type Mapping

Map natural language equipment names to NeqSim JSON `type` values.
Use the **longest matching keyword** to avoid false matches.

### Separation

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| separator, 2-phase separator, two-phase separator, flash drum, flash vessel, KO drum, knock-out drum, knockout drum, scrubber, inlet scrubber, suction scrubber, slug catcher, production scrubber, gas scrubber | `Separator` |
| 3-phase separator, three-phase separator, production separator, test separator, oil-water-gas separator, 3-phase test separator | `ThreePhaseSeparator` |

### Compression & Expansion

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| compressor, gas compressor, export compressor, recompressor, booster compressor, LP compressor, HP compressor, 1st stage compressor, 2nd stage compressor, 3rd stage compressor, centrifugal compressor, reciprocating compressor | `Compressor` |
| expander, turbo-expander, turboexpander, power recovery turbine | `Expander` |

### Heat Transfer

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| cooler, gas cooler, aftercooler, after-cooler, intercooler, air cooler, fin fan cooler, air-fin cooler, trim cooler, export cooler, overhead condenser | `Cooler` |
| heater, pre-heater, preheater, line heater, electric heater, fired heater, reboiler, trim heater | `Heater` |
| heat exchanger, shell and tube, shell-and-tube, plate heat exchanger, plate-fin exchanger, FWHE, gas-gas exchanger, cross-exchanger, economizer | `HeatExchanger` |

### Valves

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| valve, throttling valve, choke valve, choke, JT valve, Joule-Thomson valve, letdown valve, control valve, pressure control valve, PCV, backpressure valve, production choke, wellhead choke | `ThrottlingValve` |

### Pumps

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| pump, centrifugal pump, export pump, booster pump, injection pump, feed pump, charge pump, transfer pump, multiphase pump | `Pump` |

### Piping Routes

| Natural Language Synonyms | NeqSim target |
|---------------------------|---------------|
| line list, line-list, route table, STID route, E3D route, stress isometric, pipe run list, serial piping route, compressor suction route, compressor discharge route | `PipingRouteBuilder` |

`PipingRouteBuilder` is not a JSON equipment type. It is a Java/Python-accessible
builder for serial route hydraulics. Use it when the input table has from/to
nodes, pipe lengths, sizes, elevations, fittings, valves, and K values. Extract
the route rows first, then build the route model and export `route.toJson()` for
traceability.

For P&ID valve-action studies, classify each valve before mapping it to NeqSim:
control valves become `ThrottlingValve` equipment, isolation and shutdown valves
become scenario switches or boundary states, check valves become directed route
constraints, and BDV/PSV/vent valves become relief or blowdown paths.

### Mixing & Splitting

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| mixer, mixing tee, junction, merge, combine, commingling manifold | `Mixer` |
| splitter, tee, flow divider, bypass tee | `Splitter` |
| manifold, production manifold, subsea manifold | `Manifold` |

### Streams

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| stream, feed, inlet, well stream, feed gas, feed stream, input, source | `Stream` |

### Other Equipment

| Natural Language Synonyms | NeqSim `type` |
|---------------------------|---------------|
| tank, storage tank, atmospheric tank, settling tank, buffer tank | `Tank` |
| flare, flare stack, flare header, HP flare, LP flare | `Flare` |
| recycle, recirculation | `Recycle` |
| ejector, jet pump, steam ejector, gas ejector | `Ejector` |
| TEG absorber, glycol contactor, TEG contactor, dehydration absorber | `SimpleTEGAbsorber` |
| reservoir, simple reservoir | `SimpleReservoir` |
| electrolyzer, water electrolyzer, PEM electrolyzer | `Electrolyzer` |
| CO2 electrolyzer | `CO2Electrolyzer` |
| fuel cell | `FuelCell` |
| wind turbine | `WindTurbine` |
| solar panel, PV panel | `SolarPanel` |
| battery storage, battery, BESS | `BatteryStorage` |
| ammonia reactor, Haber-Bosch reactor, ammonia synthesis | `AmmoniaSynthesisReactor` |
| distillation column, fractionation column, distillation tower, deethanizer, demethanizer, depropanizer, debutanizer, stripper column, stabilizer column | `DistillationColumn` |
| pipe, pipe segment, pipeline, flowline, adiabatic pipe | `AdiabaticPipe` |
| stream saturator, saturator, water saturator | `StreamSaturatorUtil` |

---

## 3. Stream Wiring (Dot-Notation)

Equipment is connected via dot-notation references in the `inlet` field.

### Port Reference Table

| Upstream Equipment Type | Port Syntax | Resolves To |
|-------------------------|-------------|-------------|
| `Stream` | `"feed"` (name only, no port) | The stream directly |
| `Separator` | `"HP Sep.gasOut"` | Gas outlet stream |
| `Separator` | `"HP Sep.liquidOut"` | Liquid outlet stream |
| `ThreePhaseSeparator` | `"Inlet Sep.gasOut"` | Gas outlet |
| `ThreePhaseSeparator` | `"Inlet Sep.oilOut"` | Oil outlet |
| `ThreePhaseSeparator` | `"Inlet Sep.waterOut"` | Water outlet |
| `ThreePhaseSeparator` | `"Inlet Sep.liquidOut"` | Oil outlet (alias) |
| `Compressor` | `"Comp.outlet"` | Outlet stream |
| `Cooler` | `"Cooler.outlet"` | Outlet stream |
| `Heater` | `"Heater.outlet"` | Outlet stream |
| `ThrottlingValve` | `"Valve.outlet"` | Outlet stream |
| `Pump` | `"Pump.outlet"` | Outlet stream |
| `Expander` | `"Expander.outlet"` | Outlet stream |
| `Mixer` | `"Mixer.outlet"` | Outlet stream |
| `Splitter` | `"Splitter.outlet"` | Outlet stream (first split) |
| `Splitter` | `"Splitter.split0"` | Split port 0 |
| `Splitter` | `"Splitter.split1"` | Split port 1 |
| `Splitter` | `"Splitter.splitN"` | Split port N (zero-indexed) |
| `HeatExchanger` | `"HX.outlet"` | Outlet stream |
| `DistillationColumn` | `"Column.gasOut"` | Gas (overhead) outlet |
| `DistillationColumn` | `"Column.liquidOut"` | Liquid (bottoms) outlet |
| `Tank` | `"Tank.outlet"` | Outlet stream |

### Wiring Rules

1. **First equipment MUST be a `Stream`** — it gets the fluid from the `fluid` section
2. **Every subsequent equipment MUST have an `inlet` reference** pointing to a previously defined equipment
3. **For separators, specify the port** — `gasOut`, `liquidOut`, `oilOut`, `waterOut`
4. **For single-outlet equipment** — use `"name.outlet"` or just `"name"` (default resolves to outlet)
5. **Do NOT create circular references** — the JSON builder does not support recycle loops directly (add `Recycle` equipment for convergence)
6. **Branching is supported** — multiple equipment can reference different ports of the same separator
7. **Mixer multi-inlet: use `"inlets"` (plural)** — Mixers require `"inlets": ["stream1", "stream2"]` (array). Do NOT use `"inlet"` with an array — it will fail with "Array must have size 1"
8. **Separator name without port returns null** — `resolveStreamReference("HP Sep")` returns `null`. Always use `"HP Sep.gasOut"` or `"HP Sep.liquidOut"`

### Branching Example

```json
{"type": "ThreePhaseSeparator", "name": "inlet sep", "inlet": "feed"},
{"type": "Compressor", "name": "gas comp", "inlet": "inlet sep.gasOut", ...},
{"type": "ThrottlingValve", "name": "oil valve", "inlet": "inlet sep.oilOut", ...},
{"type": "Pump", "name": "water pump", "inlet": "inlet sep.waterOut", ...}
```

### Mixer Multi-Inlet Example

```json
{"type": "Stream", "name": "gas 1", "properties": {"flowRate": [10000.0, "kg/hr"]}},
{"type": "Stream", "name": "gas 2", "properties": {"flowRate": [5000.0, "kg/hr"]}},
{"type": "Mixer", "name": "gas mixer", "inlets": ["gas 1", "gas 2"]},
{"type": "Cooler", "name": "mixed cooler", "inlet": "gas mixer", "properties": {"outletTemperature": [25.0, "C"]}}
```

> **CRITICAL**: Use `"inlets"` (plural key, with array value) for Mixer/multi-inlet equipment. Using `"inlet"` with an array value will fail.

---

## 4. Equipment Properties Reference

### Stream

| Property | Type | Unit | Example |
|----------|------|------|---------|
| `flowRate` | `[number, "unit"]` | kg/hr, MSm3/day, Am3/hr | `[75000.0, "kg/hr"]` |
| `temperature` | number | Kelvin | `353.15` (= 80°C) |
| `pressure` | number | bara | `65.0` |

### Compressor

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `outletPressure` | number (bara) | — | Discharge pressure |
| `isentropicEfficiency` | number (0-1) | 0.75 | Isentropic efficiency |
| `polytropicEfficiency` | number (0-1) | — | Polytropic efficiency (alternative) |
| `usePolytropicCalc` | boolean | false | Use polytropic head calculation |

### Cooler / Heater

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `outTemperature` | number (K) | — | Outlet temperature in Kelvin |
| `outletTemperature` | `[number, "unit"]` | — | Outlet temperature with unit (e.g., `[25.0, "C"]`) |

**Property Unit Arrays:** Equipment properties can be specified with units using the `[value, "unit"]` array format. This applies to any property that accepts a unit string, such as `outletTemperature`, `flowRate`, etc. The JSON builder uses Java reflection to find matching setter methods.

### ThrottlingValve

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `outletPressure` | number (bara) | — | Downstream pressure |

### Pump

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `outletPressure` | number (bara) | — | Discharge pressure |
| `isentropicEfficiency` | number (0-1) | 0.75 | Isentropic efficiency |

### Separator / ThreePhaseSeparator

No required properties. Operates at inlet conditions.

### Splitter

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `splitNumber` | integer | — | Number of outlet streams |
| `splitFactors` | `[number, ...]` | — | Split factors per outlet (e.g., `[0.5, 0.5]`) |

### DistillationColumn

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `numberOfTrays` | integer | 10 | Number of theoretical trays |
| `hasReboiler` | boolean | true | Whether column has a reboiler |
| `hasCondenser` | boolean | true | Whether column has a condenser |

### HeatExchanger (Multi-Inlet)

`HeatExchanger` supports two inlets (hot and cold side):

```json
{"type": "HeatExchanger", "name": "gas-gas HX",
 "inlets": ["hot stream", "cold stream"]}
```

The first inlet becomes the feed stream; the second is set via `setFeedStream(1, stream)`.

### AdiabaticPipe

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `length` | number (m) | — | Pipe length in meters |
| `diameter` | number (m) | — | Pipe inner diameter in meters |

### Route-Level Piping Line Lists

When the source has a line-list or stress-isometric table, extract these fields
before constructing the route:

| Extracted field | Required | Notes |
|-----------------|----------|-------|
| `segment_id` | Yes | Line number, row id, or generated `S1`, `S2` |
| `from_node`, `to_node` | Yes | Equipment tag, nozzle, tee, manifold, or route node |
| `length`, `length_unit` | Yes | Straight pipe length, not equivalent length |
| `internal_diameter`, `diameter_unit` | Yes | Convert NPS/schedule to internal diameter first |
| `wall_thickness`, `wall_thickness_unit` | No | Store if schedule or stress iso gives it |
| `elevation_change`, `elevation_unit` | No | Positive uphill, negative downhill |
| `roughness`, `roughness_unit` | No | Use default roughness when only piping class is known |
| `minor_losses` | No | Fittings/valves as `{type, k_value}` rows |
| `source_ref` | Yes | Drawing/page/row reference for traceability |

Route extraction workflow:

1. Sort rows in hydraulic flow order from upstream to downstream.
2. Convert NPS/schedule to internal diameter before calling `addSegment(...)`.
3. Convert every valve, bend, tee, reducer, strainer, and entry/exit loss to K.
4. For a route-only study, build the route with `PipingRouteBuilder.build(feedStream)`
  and run the returned `ProcessSystem`.
5. For a full plant model, call `route.addToProcessSystem(process, inletStream)`
  and pass the returned outlet stream to the downstream equipment. Use the overload
  with source-equipment metadata when the inlet is an upstream equipment outlet stream.
6. Save `route.toJson()` and pressure-drop results in the task folder.

Reference guide: `docs/process/piping_route_builder.md`.

---

## 5. Component Name Mapping

Map common aliases to NeqSim database names. The NeqSim name is case-sensitive.

### Hydrocarbon Components

| Common Aliases | NeqSim Name |
|----------------|-------------|
| C1, CH4, methane | `methane` |
| C2, C2H6, ethane | `ethane` |
| C3, C3H8, propane | `propane` |
| iC4, i-C4, isobutane | `i-butane` |
| nC4, n-C4, butane, normal-butane | `n-butane` |
| iC5, i-C5, isopentane | `i-pentane` |
| nC5, n-C5, pentane, normal-pentane | `n-pentane` |
| nC6, n-C6, hexane | `n-hexane` |
| nC7, n-C7, heptane | `n-heptane` |
| nC8, n-C8, octane | `n-octane` |
| nC9, n-C9, nonane | `n-nonane` |
| nC10, n-C10, decane | `nC10` |
| nC11 through nC24 | `nC11` through `nC24` |

### Non-Hydrocarbon Components

| Common Aliases | NeqSim Name |
|----------------|-------------|
| CO2, carbon dioxide | `CO2` |
| H2S, hydrogen sulfide, hydrogen sulphide | `H2S` |
| N2, nitrogen | `nitrogen` |
| H2, hydrogen | `hydrogen` |
| O2, oxygen | `oxygen` |
| Ar, argon | `argon` |
| He, helium | `helium` |
| H2O, water | `water` |
| Hg, mercury | `mercury` |
| COS, carbonyl sulfide | `COS` |
| SO2, sulfur dioxide | `SO2` |

### Chemical Additives

| Common Aliases | NeqSim Name |
|----------------|-------------|
| MEG, monoethylene glycol, ethylene glycol | `MEG` |
| DEG, diethylene glycol | `DEG` |
| TEG, triethylene glycol | `TEG` |
| MeOH, methanol | `methanol` |
| EtOH, ethanol | `ethanol` |
| MDEA, methyldiethanolamine | `MDEA` |

### Aromatics

| Common Aliases | NeqSim Name |
|----------------|-------------|
| benzene, C6H6 | `benzene` |
| toluene, C7H8, methylbenzene | `toluene` |
| cyclohexane, c-C6, cy-C6 | `c-hexane` |
| cyclopentane, c-C5, cy-C5 | `c-C5` |

---

## 6. Unit Conversion Rules

All NeqSim JSON values must be in standard units. Convert before inserting into JSON.

### Temperature

| Input Unit | To Kelvin | Formula |
|------------|-----------|---------|
| °C, degC, Celsius | K | `T_K = T_C + 273.15` |
| °F, degF, Fahrenheit | K | `T_K = (T_F - 32) × 5/9 + 273.15` |
| K, Kelvin | K | Identity |
| °R, Rankine | K | `T_K = T_R × 5/9` |

### Pressure

| Input Unit | To bara | Formula |
|------------|---------|---------|
| barg, bar gauge | bara | `P_bara = P_barg + 1.01325` |
| bara, bar absolute | bara | Identity |
| psia, psi absolute | bara | `P_bara = P_psia × 0.0689476` |
| psig, psi gauge | bara | `P_bara = (P_psig + 14.696) × 0.0689476` |
| kPa, kilopascal | bara | `P_bara = P_kPa / 100.0` |
| MPa, megapascal | bara | `P_bara = P_MPa × 10.0` |
| atm, atmosphere | bara | `P_bara = P_atm × 1.01325` |

### Flow Rate

Flow rate in JSON uses the `[value, "unit"]` array format. Supported unit strings:

| Unit String | Description |
|-------------|-------------|
| `"kg/hr"` | Kilograms per hour (mass flow) |
| `"kg/min"` | Kilograms per minute |
| `"kg/sec"` | Kilograms per second |
| `"m3/hr"` | Cubic meters per hour (volume flow) |
| `"Am3/hr"` | Actual cubic meters per hour |
| `"Sm3/hr"` | Standard cubic meters per hour |
| `"MSm3/day"` | Million standard cubic meters per day |
| `"idSm3/day"` | Ideal standard cubic meters per day |
| `"mole/sec"` | Moles per second |
| `"mole/hr"` | Moles per hour |

### Composition: Weight% to Mole Fraction Conversion

NeqSim uses **mole fractions** (summing to 1.0) in the JSON `components` field. If the source provides weight% (wt%), mass fractions, or ppm-by-weight, convert as follows:

**Formula:**

For each component $i$ with weight fraction $w_i$ and molar mass $M_i$:

$$x_i = \frac{w_i / M_i}{\sum_j (w_j / M_j)}$$

**Common Molar Masses (g/mol):**

| Component | NeqSim Name | $M$ (g/mol) |
|-----------|-------------|-------------|
| Methane | `methane` | 16.04 |
| Ethane | `ethane` | 30.07 |
| Propane | `propane` | 44.10 |
| n-Butane | `n-butane` | 58.12 |
| i-Butane | `i-butane` | 58.12 |
| n-Pentane | `n-pentane` | 72.15 |
| n-Hexane | `n-hexane` | 86.18 |
| CO2 | `CO2` | 44.01 |
| H2S | `H2S` | 34.08 |
| Nitrogen | `nitrogen` | 28.01 |
| Water | `water` | 18.02 |
| MEG | `MEG` | 62.07 |
| TEG | `TEG` | 150.17 |
| MDEA | `MDEA` | 119.16 |

**Worked Example:**

Input: 70 wt% methane, 20 wt% ethane, 10 wt% propane

| Component | $w_i$ | $M_i$ | $w_i / M_i$ | $x_i$ (mole frac) |
|-----------|--------|--------|-------------|-------------------|
| methane | 0.70 | 16.04 | 0.04364 | 0.8370 |
| ethane | 0.20 | 30.07 | 0.00665 | 0.1275 |
| propane | 0.10 | 44.10 | 0.00227 | 0.0355 |
| **Total** | 1.00 | | 0.05216 | **1.0000** |

Result JSON: `{"methane": 0.837, "ethane": 0.128, "propane": 0.035}`

**ppm-by-weight:** Convert ppm_w to weight fraction first: $w_i = \text{ppm}_w \times 10^{-6}$

**Volume% (gas at standard conditions):** Volume% ≈ mole% for ideal gas behavior. Use directly as mole fractions.

---

## 7. EOS Model Selection

Choose the thermodynamic model based on the fluid system:

| Fluid System | Recommended Model | Mixing Rule |
|-------------|-------------------|-------------|
| Dry gas, lean gas, simple hydrocarbons | `SRK` | `"classic"` |
| Oil systems, general hydrocarbons | `PR` | `"classic"` |
| Water + hydrocarbons, MEG/methanol, polar | `CPA` | `"CLASSIC_TX_CPA"` |
| Fiscal metering, custody transfer | `GERG2008` | (none needed) |
| Polymer/associating fluids | `PCSAFT` | `"classic"` |

### Decision Rules

1. **If water or glycol is present** → use `CPA` with mixing rule `"CLASSIC_TX_CPA"` and set `multiPhaseCheck: true`
2. **If accuracy for gas density/Z-factor is critical** → use `GERG2008`
3. **If heavy oil (C20+)** → use `PR` or `SRK` with `"classic"` mixing rule
4. **Default / unknown** → use `SRK` with `"classic"` mixing rule

---

## 8. Extraction Workflow

Follow this step-by-step process for every extraction:

### Step 1: Identify the Source Type

- **Text description** — paragraph or bullet list describing a process
- **Table / spreadsheet** — heat & mass balance, operating data, well test
- **PFD / sketch** — process flow diagram (described or as image)
- **Data sheet** — equipment data sheet with design conditions
- **Mixed** — combination of above

### Step 2: Extract Fluid Composition

1. Look for: mole fractions, mol%, weight%, component tables
2. Map component names to NeqSim names using the Component Name Mapping table
3. Normalize to mole fractions summing to 1.0
4. If weight% given, note it as an assumption (NeqSim uses mole fractions)
5. If no composition given, flag as missing and use a placeholder

### Step 3: Extract Equipment List

1. Scan for equipment keywords using the Equipment Type Mapping table
2. Match the **longest keyword first** (e.g., "three-phase separator" before "separator")
3. Assign unique names/tags (use P&ID tags if provided, or generate descriptive names)
4. Record the NeqSim `type` for each

### Step 4: Extract Stream Connectivity

1. Look for phrases indicating flow direction: "enters", "goes to", "feeds", "is routed to", "flows to", "passes through"
2. Identify which phase exits which equipment: "gas from the separator", "oil from the 3-phase separator", "compressed gas"
3. Build dot-notation references: `"equipment_name.port"`
4. Verify no orphan streams (every equipment except feed has an inlet)

### Step 5: Extract Operating Conditions

1. Pressures: look for `bara`, `barg`, `bar`, `psi`, `MPa`, `kPa`, `atm`
2. Temperatures: look for `°C`, `°F`, `K`, `degC`, `degF`
3. Flow rates: look for `kg/hr`, `t/h`, `MMSCFD`, `MSm3/d`, `Am3/hr`
4. Convert all to NeqSim standard units (K, bara)
5. Assign to the correct equipment property

### Step 6: Assemble JSON

1. Build the `fluid` section with model, T, P, mixing rule, and components
2. Build the `process` array in topological order (upstream before downstream)
3. First element MUST be a `Stream` with the feed fluid
4. Wire all equipment with `inlet` references
5. Set `autoRun: true`

### Step 7: Validate and Report

1. Check composition sums to ~1.0 (within 0.01)
2. Check all stream references point to existing equipment
3. Check no circular references
4. Compute confidence score (see Confidence Scoring below)
5. List all assumptions made
6. List all missing information detected

---

## 9. Confidence Scoring

Score the extraction confidence on a 0.0–1.0 scale:

| Criterion | Points |
|-----------|--------|
| Fluid composition explicitly provided | +0.25 |
| Feed temperature specified | +0.10 |
| Feed pressure specified | +0.10 |
| Feed flow rate specified | +0.10 |
| All equipment have explicit operating conditions | +0.15 |
| Stream topology clearly described | +0.15 |
| Equipment tags/names from source (not generated) | +0.05 |
| EOS model specified or inferable from context | +0.05 |
| No conflicting information in source | +0.05 |

### Confidence Bands

| Score | Label | Recommendation |
|-------|-------|----------------|
| 0.80–1.00 | **High** | Run directly, review results |
| 0.60–0.79 | **Medium** | Run but flag assumptions for user review |
| 0.40–0.59 | **Low** | More information needed — show what's missing |
| 0.00–0.39 | **Very Low** | Cannot produce reliable simulation — ask user |

---

## 10. Assumption Defaults

When information is not specified, use these engineering defaults and **always track them**:

| Parameter | Default Value | Assumption Text |
|-----------|---------------|-----------------|
| EOS model | SRK | "Default SRK EOS (not specified in source)" |
| Mixing rule | classic | "Classic mixing rule assumed" |
| Feed temperature | 288.15 K (15°C) | "Standard temperature assumed (15°C)" |
| Feed pressure | 1.01325 bara | "Atmospheric pressure assumed" |
| Feed flow rate | 50000 kg/hr | "Default flow rate 50000 kg/hr assumed" |
| Compressor efficiency | 0.75 (isentropic) | "Default isentropic efficiency 0.75 assumed" |
| Cooler outlet temp | 308.15 K (35°C) | "Default cooler outlet 35°C assumed" |
| Composition (no data) | 90% CH4, 5% C2, 3% C3, 2% nC4 | "Placeholder lean gas composition used" |

---

## 11. Process Templates

When the extracted topology matches a known pattern, use a template for better reliability.

### Template: Gas Dew Point Control

**Pattern:** cooler → separator → compressor

```json
{
  "fluid": { "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$", "mixingRule": "classic", "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "Cooler", "name": "dew point cooler", "inlet": "feed", "properties": {"outTemperature": "$COOLER_T_K$"}},
    {"type": "Separator", "name": "cold separator", "inlet": "dew point cooler.outlet"},
    {"type": "Compressor", "name": "export compressor", "inlet": "cold separator.gasOut", "properties": {"outletPressure": "$EXPORT_P$", "isentropicEfficiency": 0.78}}
  ],
  "autoRun": true
}
```

### Template: Two-Stage HP/LP Separation

**Pattern:** 3-phase sep → gas compression + oil letdown → LP sep → recompression

```json
{
  "fluid": { "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$HP_P$", "mixingRule": "classic", "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "well stream", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "HP separator", "inlet": "well stream"},
    {"type": "Cooler", "name": "gas cooler", "inlet": "HP separator.gasOut", "properties": {"outTemperature": 308.15}},
    {"type": "Compressor", "name": "export compressor", "inlet": "gas cooler.outlet", "properties": {"outletPressure": "$EXPORT_P$", "isentropicEfficiency": 0.78}},
    {"type": "ThrottlingValve", "name": "HP-LP valve", "inlet": "HP separator.oilOut", "properties": {"outletPressure": "$LP_P$"}},
    {"type": "Separator", "name": "LP separator", "inlet": "HP-LP valve.outlet"},
    {"type": "Compressor", "name": "LP recompressor", "inlet": "LP separator.gasOut", "properties": {"outletPressure": "$HP_P$", "isentropicEfficiency": 0.75}}
  ],
  "autoRun": true
}
```

### Template: Multi-Stage Compression with Intercooling

**Pattern:** compressor → cooler → scrubber → compressor → cooler → scrubber → ... (N stages)

Build dynamically with equal pressure ratio per stage:

- `ratio_per_stage = (P_out / P_in) ^ (1/N)`
- `stage_P[i] = P_in × ratio_per_stage^i`
- Each stage: `Compressor` → `Cooler` (to intercooler temp) → `Separator` (scrub condensate)
- Last stage: `Compressor` only (no aftercooler/scrubber, unless specified)

### Template: Gas Cooling and JT Expansion

**Pattern:** cooler → separator → JT valve → cold separator

```json
{
  "fluid": { "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$", "mixingRule": "classic", "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "Cooler", "name": "pre-cooler", "inlet": "feed", "properties": {"outTemperature": "$PRECOOL_T_K$"}},
    {"type": "Separator", "name": "inlet scrubber", "inlet": "pre-cooler.outlet"},
    {"type": "ThrottlingValve", "name": "JT valve", "inlet": "inlet scrubber.gasOut", "properties": {"outletPressure": "$JT_P$"}},
    {"type": "Separator", "name": "cold separator", "inlet": "JT valve.outlet"}
  ],
  "autoRun": true
}
```

### Template: Simple Oil Stabilization

**Pattern:** 3-phase sep → valve → flash drum → valve → atmospheric flash

```json
{
  "fluid": { "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$HP_P$", "mixingRule": "classic", "multiPhaseCheck": true, "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "well fluid", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "production separator", "inlet": "well fluid"},
    {"type": "ThrottlingValve", "name": "1st stage valve", "inlet": "production separator.oilOut", "properties": {"outletPressure": "$STAGE2_P$"}},
    {"type": "Separator", "name": "2nd stage separator", "inlet": "1st stage valve.outlet"},
    {"type": "ThrottlingValve", "name": "2nd stage valve", "inlet": "2nd stage separator.liquidOut", "properties": {"outletPressure": "$STAGE3_P$"}},
    {"type": "Separator", "name": "stabilizer", "inlet": "2nd stage valve.outlet"}
  ],
  "autoRun": true
}
```

### Template: Subsea Tieback (Well → Pipeline → Platform)

**Pattern:** well stream → choke → pipeline → separator

```json
{
  "fluid": { "model": "SRK", "temperature": "$WELLHEAD_T_K$", "pressure": "$WELLHEAD_P$", "mixingRule": "classic", "multiPhaseCheck": true, "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "well stream", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "ThrottlingValve", "name": "production choke", "inlet": "well stream", "properties": {"outletPressure": "$CHOKE_P$"}},
    {"type": "Heater", "name": "pipeline heat loss", "inlet": "production choke.outlet", "properties": {"outTemperature": "$ARRIVAL_T_K$"}},
    {"type": "ThreePhaseSeparator", "name": "inlet separator", "inlet": "pipeline heat loss.outlet"}
  ],
  "autoRun": true
}
```

### Template: TEG Dehydration

**Pattern:** wet gas → TEG absorber ← lean TEG; dry gas out, rich TEG out

**Note:** `SimpleTEGAbsorber` requires two input streams added via `addGasInStream()` and `addSolventInStream()`. The JSON builder currently wires via the `inlet` field, so for TEG dehydration, build the lean TEG stream as a separate feed with a TEG+water fluid.

```json
{
  "fluids": {
    "wet_gas": {
      "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$",
      "mixingRule": "classic",
      "components": "$GAS_COMPOSITION_WITH_WATER$"
    },
    "lean_teg": {
      "model": "CPA", "temperature": "$TEG_T_K$", "pressure": "$FEED_P$",
      "mixingRule": "CLASSIC_TX_CPA",
      "components": {"TEG": 0.99, "water": 0.01}
    }
  },
  "process": [
    {"type": "Stream", "name": "wet gas feed", "fluidRef": "wet_gas", "properties": {"flowRate": ["$GAS_FLOW$", "kg/hr"]}},
    {"type": "Stream", "name": "lean TEG", "fluidRef": "lean_teg", "properties": {"flowRate": ["$TEG_FLOW$", "kg/hr"]}},
    {"type": "SimpleTEGAbsorber", "name": "TEG absorber", "inlet": "wet gas feed",
      "properties": {"numberOfStages": "$STAGES$", "stageEfficiency": 0.5}},
    {"type": "Heater", "name": "TEG reboiler sim", "inlet": "TEG absorber.liquidOut",
      "properties": {"outTemperature": "$REBOILER_T_K$"}}
  ],
  "autoRun": true
}
```

**Typical defaults:** 3–5 stages, stage efficiency 0.5, lean TEG flow 5–10× water to remove, reboiler at ~200°C (473 K). TEG purity: 99–99.5 wt%.

### Template: NGL Recovery (Turbo-Expander + Demethanizer)

**Pattern:** gas inlet → cooler → expander → demethanizer column; overhead = sales gas, bottoms = NGL

```json
{
  "fluid": { "model": "SRK", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$", "mixingRule": "classic", "components": "$COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "inlet gas", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "Cooler", "name": "gas chiller", "inlet": "inlet gas", "properties": {"outTemperature": "$CHILLER_T_K$"}},
    {"type": "Separator", "name": "cold separator", "inlet": "gas chiller.outlet"},
    {"type": "Expander", "name": "turbo-expander", "inlet": "cold separator.gasOut", "properties": {"outletPressure": "$EXPANDER_P$", "isentropicEfficiency": 0.85}},
    {"type": "ThrottlingValve", "name": "liquid JT valve", "inlet": "cold separator.liquidOut", "properties": {"outletPressure": "$EXPANDER_P$"}},
    {"type": "Mixer", "name": "column feed mixer", "inlets": ["turbo-expander.outlet", "liquid JT valve.outlet"]},
    {"type": "Separator", "name": "demethanizer sim", "inlet": "column feed mixer.outlet"},
    {"type": "Compressor", "name": "residue compressor", "inlet": "demethanizer sim.gasOut", "properties": {"outletPressure": "$SALES_P$", "isentropicEfficiency": 0.78}}
  ],
  "autoRun": true
}
```

**Note:** For a rigorous demethanizer, replace the Separator with a `Column` (DistillationColumn). The simplified version uses a cold separator as a proxy. Typical expander outlet: 15–25 bara, efficiency 0.82–0.88, chiller to –30°C to –40°C.

### Template: Acid Gas Removal (Amine Sweetening)

**Pattern:** sour gas → amine absorber ← lean amine; sweet gas out, rich amine to regenerator

**Note:** Uses `SimpleTEGAbsorber` which works for generic absorption. For amine-specific thermodynamics, the CPA EOS with MDEA is recommended.

```json
{
  "fluids": {
    "sour_gas": {
      "model": "CPA", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$",
      "mixingRule": "CLASSIC_TX_CPA",
      "components": "$SOUR_GAS_COMPOSITION$"
    },
    "lean_amine": {
      "model": "CPA", "temperature": "$AMINE_T_K$", "pressure": "$FEED_P$",
      "mixingRule": "CLASSIC_TX_CPA",
      "components": {"MDEA": 0.40, "water": 0.60}
    }
  },
  "process": [
    {"type": "Stream", "name": "sour gas feed", "fluidRef": "sour_gas", "properties": {"flowRate": ["$GAS_FLOW$", "kg/hr"]}},
    {"type": "Stream", "name": "lean amine", "fluidRef": "lean_amine", "properties": {"flowRate": ["$AMINE_FLOW$", "kg/hr"]}},
    {"type": "SimpleTEGAbsorber", "name": "amine absorber", "inlet": "sour gas feed",
      "properties": {"numberOfStages": "$STAGES$", "stageEfficiency": 0.5}},
    {"type": "Heater", "name": "amine regenerator sim", "inlet": "amine absorber.liquidOut",
      "properties": {"outTemperature": "$REGEN_T_K$"}}
  ],
  "autoRun": true
}
```

**Typical defaults:** 10–20 stages, MDEA 40–50 wt%, amine circulation rate 50–100 L/kg acid gas, regenerator at 120–130°C. Use CPA EOS with mixing rule "CLASSIC_TX_CPA" for polar systems.

### Template: Produced Water Treatment (Degassing)

**Pattern:** produced water → heater → 3-phase separator → water stripper or flash drum

```json
{
  "fluid": { "model": "CPA", "temperature": "$FEED_T_K$", "pressure": "$FEED_P$", "mixingRule": "CLASSIC_TX_CPA", "multiPhaseCheck": true,
    "components": "$WATER_OIL_GAS_COMPOSITION$" },
  "process": [
    {"type": "Stream", "name": "produced water", "properties": {"flowRate": ["$FLOW$", "kg/hr"]}},
    {"type": "Heater", "name": "water heater", "inlet": "produced water", "properties": {"outTemperature": "$HEATER_T_K$"}},
    {"type": "ThreePhaseSeparator", "name": "water degasser", "inlet": "water heater.outlet"},
    {"type": "ThrottlingValve", "name": "flash valve", "inlet": "water degasser.waterOut", "properties": {"outletPressure": "$FLASH_P$"}},
    {"type": "Separator", "name": "atmospheric flash", "inlet": "flash valve.outlet"}
  ],
  "autoRun": true
}
```

**Typical defaults:** Produced water at 60–80°C, degassing at 1–3 bara. Use CPA EOS with mixing rule "CLASSIC_TX_CPA" when water is a major component. Composition: primarily water (>95 mol%) with dissolved methane, CO2, and trace hydrocarbons.

---

## 12. Worked Examples

### Example 1: Simple Text Description

**Input:**
> "Feed gas at 80 bara and 40°C enters a cooler to 15°C. The cooled stream goes to a separator. Gas from the separator is compressed to 120 bara."

**Extraction:**

| Step | Extracted |
|------|-----------|
| Composition | NOT PROVIDED → flag as missing, use placeholder |
| Feed T | 40°C → `313.15` K |
| Feed P | 80 bara → `80.0` |
| Cooler | Target 15°C → `outTemperature: 288.15` |
| Separator | After cooler, takes gas port |
| Compressor | 120 bara → `outletPressure: 120.0` |

**Output JSON:**
```json
{
  "fluid": {
    "model": "SRK", "temperature": 313.15, "pressure": 80.0,
    "mixingRule": "classic",
    "components": {"methane": 0.90, "ethane": 0.05, "propane": 0.03, "n-butane": 0.02}
  },
  "process": [
    {"type": "Stream", "name": "feed gas", "properties": {"flowRate": [50000.0, "kg/hr"]}},
    {"type": "Cooler", "name": "gas cooler", "inlet": "feed gas", "properties": {"outTemperature": 288.15}},
    {"type": "Separator", "name": "scrubber", "inlet": "gas cooler.outlet"},
    {"type": "Compressor", "name": "export compressor", "inlet": "scrubber.gasOut", "properties": {"outletPressure": 120.0, "isentropicEfficiency": 0.75}}
  ],
  "autoRun": true
}
```

**Report:**
- Confidence: 0.50 (temperature ✓, pressure ✓, topology ✓, but no composition, no flow rate)
- Assumptions: Placeholder composition (90% CH4), default flow rate 50000 kg/hr, default compressor efficiency 0.75, SRK EOS
- Missing: Feed composition, feed flow rate

---

### Example 2: Process with Composition

**Input:**
> "The well stream arrives at 65 bara and 80°C with 75000 kg/hr. Composition: 80% methane, 8% ethane, 5% propane, 3% CO2, 2% n-butane, 1% N2, 0.5% n-pentane, 0.5% n-hexane. It enters a 3-phase separator. Gas goes to a compressor at 120 bara. Oil goes through a letdown valve to 15 bara."

**Output JSON:**
```json
{
  "fluid": {
    "model": "SRK", "temperature": 353.15, "pressure": 65.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.80, "ethane": 0.08, "propane": 0.05,
      "CO2": 0.03, "n-butane": 0.02, "nitrogen": 0.01,
      "n-pentane": 0.005, "n-hexane": 0.005
    }
  },
  "process": [
    {"type": "Stream", "name": "well stream", "properties": {"flowRate": [75000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "inlet separator", "inlet": "well stream"},
    {"type": "Compressor", "name": "gas compressor", "inlet": "inlet separator.gasOut", "properties": {"outletPressure": 120.0, "isentropicEfficiency": 0.75}},
    {"type": "ThrottlingValve", "name": "letdown valve", "inlet": "inlet separator.oilOut", "properties": {"outletPressure": 15.0}}
  ],
  "autoRun": true
}
```

**Report:**
- Confidence: 0.85
- Assumptions: SRK EOS (not specified), default compressor efficiency 0.75
- Missing: Compressor efficiency, separator operating temperature

---

### Example 3: From a Heat & Mass Balance Table

**Input Table:**

| Stream | Phase | T (°C) | P (barg) | Flow (t/h) | CH4 mol% | C2H6 mol% | C3H8 mol% | CO2 mol% |
|--------|-------|---------|----------|------------|-----------|------------|------------|----------|
| Feed | V+L | 60 | 48 | 120 | 82.5 | 7.2 | 4.1 | 2.3 |

**Equipment List:** HP separator → export compressor (95 barg) → aftercooler (35°C) → scrubber

**Extraction:**
- T = 60°C → 333.15 K; P = 48 barg → 49.01325 bara
- Flow = 120 t/h → 120000 kg/hr
- Composition: map aliases, normalize remainder to nC4
- Export P = 95 barg → 96.01325 bara

**Output JSON:**
```json
{
  "fluid": {
    "model": "SRK", "temperature": 333.15, "pressure": 49.01325,
    "mixingRule": "classic",
    "components": {
      "methane": 0.825, "ethane": 0.072, "propane": 0.041,
      "CO2": 0.023, "n-butane": 0.039
    }
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [120000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "HP separator", "inlet": "feed"},
    {"type": "Compressor", "name": "export compressor", "inlet": "HP separator.gasOut", "properties": {"outletPressure": 96.01325, "isentropicEfficiency": 0.75}},
    {"type": "Cooler", "name": "aftercooler", "inlet": "export compressor.outlet", "properties": {"outTemperature": 308.15}},
    {"type": "Separator", "name": "export scrubber", "inlet": "aftercooler.outlet"}
  ],
  "autoRun": true
}
```

**Report:**
- Confidence: 0.90 (composition ✓, conditions ✓, flow ✓, equipment clear)
- Assumptions: SRK EOS, default compressor efficiency, remaining 3.9 mol% assigned to n-butane
- Missing: Compressor efficiency, individual heavy component split

---

### Example 4: From Equipment Tag List (ProcessPilot style)

**Input:**

```
Separators: 20VA001 (HP, 50 barg, 60°C), 20VB001 (LP, 14 barg)
Compressors: 23KA600 (discharge 60 barg, eff 0.78)
Valves: 20XV001 (outlet 15 barg)
Topology: feed→20VA001, 20VA001.gasOut→23KA600, 20VA001.oilOut→20XV001, 20XV001.outlet→20VB001
Feed: 100000 kg/hr, 60°C, 50 barg
```

**Output JSON:**
```json
{
  "fluid": {
    "model": "SRK", "temperature": 333.15, "pressure": 51.01325,
    "mixingRule": "classic",
    "components": {"methane": 0.90, "ethane": 0.05, "propane": 0.03, "n-butane": 0.02}
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [100000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "20VA001", "inlet": "feed"},
    {"type": "Compressor", "name": "23KA600", "inlet": "20VA001.gasOut", "properties": {"outletPressure": 61.01325, "isentropicEfficiency": 0.78}},
    {"type": "ThrottlingValve", "name": "20XV001", "inlet": "20VA001.oilOut", "properties": {"outletPressure": 16.01325}},
    {"type": "Separator", "name": "20VB001", "inlet": "20XV001.outlet"}
  ],
  "autoRun": true
}
```

---

### Example 5: Multi-Stage Compression (from a design report)

**Input:**
> "Compress LP gas from 5 bara to 150 bara in 3 stages with intercooling to 30°C. Gas composition: 88% methane, 6% ethane, 3% propane, 2% CO2, 1% nitrogen. Feed is 25000 kg/hr at 25°C."

**Extraction:**
- 3 stages, ratio = (150/5)^(1/3) = 3.107
- Stage 1: 5 → 15.5 bara
- Stage 2: 15.5 → 48.2 bara
- Stage 3: 48.2 → 150 bara

**Output JSON:**
```json
{
  "fluid": {
    "model": "SRK", "temperature": 298.15, "pressure": 5.0,
    "mixingRule": "classic",
    "components": {"methane": 0.88, "ethane": 0.06, "propane": 0.03, "CO2": 0.02, "nitrogen": 0.01}
  },
  "process": [
    {"type": "Stream", "name": "feed gas", "properties": {"flowRate": [25000.0, "kg/hr"]}},
    {"type": "Compressor", "name": "compressor stage 1", "inlet": "feed gas", "properties": {"outletPressure": 15.5, "isentropicEfficiency": 0.78}},
    {"type": "Cooler", "name": "aftercooler 1", "inlet": "compressor stage 1.outlet", "properties": {"outTemperature": 303.15}},
    {"type": "Separator", "name": "scrubber 1", "inlet": "aftercooler 1.outlet"},
    {"type": "Compressor", "name": "compressor stage 2", "inlet": "scrubber 1.gasOut", "properties": {"outletPressure": 48.2, "isentropicEfficiency": 0.78}},
    {"type": "Cooler", "name": "aftercooler 2", "inlet": "compressor stage 2.outlet", "properties": {"outTemperature": 303.15}},
    {"type": "Separator", "name": "scrubber 2", "inlet": "aftercooler 2.outlet"},
    {"type": "Compressor", "name": "compressor stage 3", "inlet": "scrubber 2.gasOut", "properties": {"outletPressure": 150.0, "isentropicEfficiency": 0.78}}
  ],
  "autoRun": true
}
```

---

## 13. Output Format

Every extraction MUST produce three things:

### 1. The NeqSim JSON

The complete JSON object ready for `ProcessSystem.fromJsonAndRun()`.

### 2. Extraction Report

Present as a structured summary:

```
EXTRACTION REPORT
─────────────────
Source type:  [text / table / PFD / data sheet / mixed]
Confidence:  [0.XX] — [High / Medium / Low / Very Low]
Equipment:   [N] units extracted
Streams:     [N] connections wired

Assumptions Used:
  - [assumption 1]
  - [assumption 2]
  ...

Missing Information:
  - [missing item 1]
  - [missing item 2]
  ...

Warnings:
  - [warning 1]
  ...
```

### 3. Simulation Results (after running)

```
SIMULATION RESULTS
──────────────────
Status: [SUCCESS / ERROR]

Equipment        T (°C)    P (bara)   Flow (kg/hr)   Notes
─────────       ──────    ────────   ────────────   ─────
Feed              80.0      65.0      75000          —
HP Separator      80.0      65.0      —              Gas + Oil split
Gas Compressor   145.2     120.0      62000          Power: 2450 kW
Oil Valve         55.3      15.0      13000          —
```

---

## 14. Error Handling

If `ProcessSystem.fromJsonAndRun()` returns errors, interpret them:

| Error Code | Meaning | Likely Fix |
|------------|---------|------------|
| `JSON_PARSE_ERROR` | Malformed JSON | Check JSON syntax (missing comma, unmatched brace) |
| `MISSING_PROCESS` | No `process` array | Add the process equipment array |
| `MISSING_TYPE` | Equipment has no `type` | Add `"type": "..."` to the unit definition |
| `STREAM_NOT_FOUND` | Inlet reference points to nonexistent equipment | Check equipment order and name spelling |
| `FLUID_NOT_FOUND` | `fluidRef` points to undefined fluid | Define the fluid in the `fluids` section |
| `NO_FLUID` | Stream has no fluid | Define a `fluid` section or add `fluidRef` |
| `UNKNOWN_MODEL` | Unrecognized EOS model | Use: SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU |
| `UNIT_ERROR` | Equipment creation failed | Check property values and types |
| `SIMULATION_ERROR` | Runtime failure during `process.run()` | Check pressure/temperature ranges, compositions |

### Tolerant Error Handling

The JSON builder uses **tolerant** error handling for stream wiring: when a stream
reference cannot be resolved (e.g., the upstream unit was skipped), the equipment
is removed from the process rather than failing the entire build. These show up as
warnings (not errors) in `SimulationResult`. Similarly, if `process.run()` throws
an exception, it is caught and returned as a warning — the process is still returned
in its partially-run state.

This means `result.isSuccess()` can be `true` even when `result.hasWarnings()` is
also `true`. Always check warnings to identify any equipment that was skipped:

```python
result = ProcessSystem.fromJson(json_str)
if result.hasWarnings():
    for w in result.getWarnings():
        print(f"WARNING [{w.getCode()}]: {w.getMessage()}")
```

---

## 15. Operating Data Bridge Patterns

Patterns for converting operating data from external sources (Excel, CSV, historians) into NeqSim JSON.

### From Excel / CSV Tabular Data

When operating data arrives as a table (common in FEED reports, well tests, plant data):

**Step 1 — Identify columns:**

| Column Type | Maps To |
|-------------|---------|
| Stream name / tag | `"name"` in process array |
| Temperature (with unit) | `"outTemperature"` (convert to K) |
| Pressure (with unit) | `"outletPressure"` / fluid `"pressure"` |
| Flow rate (with unit) | `"flowRate"` property |
| Composition columns (CH4 mol%, C2H6 mol%, ...) | `"components"` in fluid |
| Equipment tag (e.g., 20VA001) | `"name"` |
| Equipment type (separator, compressor, ...) | `"type"` (via equipment mapping) |

**Step 2 — Build JSON from rows:**

```
Excel Row:                          NeqSim JSON Unit:
┌─────────────────────────────┐    ┌──────────────────────────────────┐
│ Tag: 23KA601                │ →  │ "name": "23KA601"                │
│ Type: Centrifugal compressor│ →  │ "type": "Compressor"             │
│ Suction P: 48 barg          │ →  │ (inlet stream pressure)          │
│ Discharge P: 95 barg        │ →  │ "outletPressure": 96.01325       │
│ Efficiency: 78%             │ →  │ "isentropicEfficiency": 0.78     │
│ Inlet: from 20VA001 gas     │ →  │ "inlet": "20VA001.gasOut"        │
└─────────────────────────────┘    └──────────────────────────────────┘
```

**Step 3 — Handle multi-row compositions:**

If composition is spread across rows (one row per component):

```
Component    mol%
methane      82.5
ethane        7.2
propane       4.1
CO2           2.3
remainder     3.9  ← assign to n-butane or split to nC4/nC5
```

Convert to: `{"methane": 0.825, "ethane": 0.072, "propane": 0.041, "CO2": 0.023, "n-butane": 0.039}`

### From Plant Historian Data (PI / IP.21)

When operating data comes from realtime historians:

**Tag Mapping Pattern:**

```
Historian Tag            → NeqSim Parameter
─────────────           ─────────────────
PT-20001.PV (bara)      → feed stream pressure
TT-20001.PV (°C)        → feed stream temperature (+273.15)
FT-20001.PV (kg/hr)     → feed stream flowRate
AT-20001-CH4.PV (mol%)  → fluid component "methane" (/100)
AT-20001-C2H6.PV (mol%) → fluid component "ethane" (/100)
PT-23001.PV (bara)      → compressor outletPressure
```

**JSON with Historian Placeholder Tags:**

Use `"$TAG:tagname$"` placeholders that a data bridge fills at runtime:

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": "$TAG:TT-20001.PV+273.15$",
    "pressure": "$TAG:PT-20001.PV$",
    "mixingRule": "classic",
    "components": {
      "methane": "$TAG:AT-20001-CH4.PV/100$",
      "ethane": "$TAG:AT-20001-C2H6.PV/100$"
    }
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": ["$TAG:FT-20001.PV$", "kg/hr"]}},
    {"type": "Compressor", "name": "23KA001", "inlet": "feed",
      "properties": {"outletPressure": "$TAG:PT-23001.PV$"}}
  ]
}
```

**Python Data Bridge Example (pandas):**

```python
import pandas as pd
import json

# Load operating data from Excel
df = pd.read_excel("operating_data.xlsx", sheet_name="Well Test")

# Build fluid composition from columns
composition = {}
component_map = {"CH4": "methane", "C2H6": "ethane", "C3H8": "propane",
                 "CO2": "CO2", "N2": "nitrogen", "H2S": "H2S"}
for col, neqsim_name in component_map.items():
    if col in df.columns:
        val = float(df[col].iloc[0])
        if val > 1.0:  # Likely mol% not fraction
            val /= 100.0
        composition[neqsim_name] = round(val, 6)

# Normalize
total = sum(composition.values())
composition = {k: round(v / total, 6) for k, v in composition.items()}

# Build JSON
neqsim_json = {
    "fluid": {
        "model": "SRK",
        "temperature": float(df["Temperature_C"].iloc[0]) + 273.15,
        "pressure": float(df["Pressure_bara"].iloc[0]),
        "mixingRule": "classic",
        "components": composition
    },
    "process": [
        {"type": "Stream", "name": "feed",
         "properties": {"flowRate": [float(df["FlowRate_kghr"].iloc[0]), "kg/hr"]}}
        # ... add equipment from equipment sheet
    ],
    "autoRun": True
}
```

### Handling Common Data Issues

| Issue | Detection | Fix |
|-------|-----------|-----|
| Composition sums to ~100 not ~1 | Sum > 1.5 | Divide all values by 100 |
| Composition sums to < 0.95 | Sum < 0.95 | Assign remainder to heaviest component, flag assumption |
| Pressure in barg not bara | Values look low (< 1 for HP systems) | Add 1.01325 to convert |
| Temperature in °C not K | Values < 200 for process | Add 273.15 |
| Missing flow units | No unit column | Default to kg/hr, flag assumption |
| ppm trace components | Values > 100 in composition column | Convert: mol_frac = ppm × 1e-6 |

---

## 16. ProcessSystem vs ProcessModule Architecture

NeqSim has **three levels** of process model organization. The extraction agent MUST
choose the right level before assembling JSON or Python code.

### Decision Guide

| Process Complexity | Units | Recycles | Architecture | JSON Support |
|--------------------|-------|----------|--------------|-------------|
| Simple / linear | 1–8 | 0–1 | Single `ProcessSystem` | YES — use `fromJsonAndRun()` |
| Medium | 5–15 | 0–1 | Single `ProcessSystem` | YES — use `fromJsonAndRun()` |
| Large / multi-area | 10–50+ | 0–3+ | `ProcessModule` composing multiple `ProcessSystem`s | NO — must use Python/Java code |
| Reusable subsystem | any | internal | `ProcessModuleBaseClass` subclass | NO — must use Python/Java code |

**Decision rules (apply in order):**

1. **Can the entire process be expressed as a single linear/branching chain?** → Single `ProcessSystem` via JSON.
2. **Does the process have ≤ 1 recycle loop with ≤ 15 total units?** → Single `ProcessSystem` via JSON (recycles supported via `Recycle` equipment type).
3. **Does the process have distinct plant areas** (e.g., separation train + compression + dehydration + export) **with different fluids or cross-area recycles?** → Split into multiple `ProcessSystem` objects inside a `ProcessModule`.
4. **Does a pre-built module exist** (separation, TEG dehydration, CO2 removal, adsorption)? → Use the corresponding `ProcessModuleBaseClass` subclass.
5. **Does the process require > 50 units or nested recycles across areas?** → Use nested `ProcessModule` containing other `ProcessModule`s.

### Architecture A: Single ProcessSystem (JSON-compatible)

**When to use:** Most extraction scenarios. The JSON builder creates a single
`ProcessSystem` with equipment wired via stream references.

```
┌──────────────────────────────────────────┐
│           ProcessSystem                  │
│                                          │
│  Stream → Cooler → Separator → Compressor│
│                      │                   │
│                      └→ Valve → LP Sep   │
└──────────────────────────────────────────┘
```

**Limitations of single ProcessSystem:**
- All equipment shares one feed fluid definition
- One convergence loop (adjusters + recycles solved together)
- No sub-module encapsulation
- Gets unwieldy above ~15-20 units

### Architecture B: ProcessModuleBaseClass (Reusable Black-Box Modules)

**When to use:** The source describes a **standard process subsystem** that matches
one of NeqSim's pre-built modules. Use the module directly rather than rebuilding
from individual equipment.

**Available pre-built modules:**

| Module Class | Purpose | Input Streams | Output Streams |
|-------------|---------|---------------|----------------|
| `SeparationTrainModule` | Multi-stage HP/MP/LP separation | `"feed stream"` | `"gas exit stream"`, `"oil exit stream"` |
| `SeparationTrainModuleSimple` | Simplified 2-stage separation | `"feed stream"` | `"gas exit stream"`, `"oil exit stream"` |
| `GlycolDehydrationlModule` | TEG dehydration (absorber + stripper + regen) | `"gasStreamToAbsorber"`, `"strippingGas"` | `"gasStreamFromAbsorber"`, `"liquidFromStripper"` |
| `CO2RemovalModule` | CO2 absorption/stripping | `"streamToAbsorber"` | `"streamFromAbsorber"` |
| `AdsorptionDehydrationlModule` | Adsorption dehydration with multiple beds | `"gasStreamToAdsorber"` | `"gasStreamFromAdsorber"` |
| `DPCUModule` | Dew Point Control Unit (expander + column) | `"feed stream"` | `"gas exit stream"`, `"oil exit stream"` |
| `PropaneCoolingModule` | Propane refrigeration cycle | `"refrigerant"` | `"refrigerant"` |
| `MEGReclaimerModule` | MEG reclamation | `"streamToReclaimer"` | `"streamToWaterRemoval"` |
| `MixerGasProcessingModule` | Gas processing with glycol injection | `"feed stream"`, `"glycol feed stream"` | `"gas exit stream"`, `"oil exit stream"` |
| `WellFluidModule` | Well fluid characterization & separation | `"feed stream"` | single outlet |

**Pattern — module as equipment in ProcessSystem:**

```python
# A module IS a ProcessEquipmentInterface — add it to a ProcessSystem
from neqsim import jneqsim

SepModule = jneqsim.process.processmodel.processmodules.SeparationTrainModule

sep_module = SepModule("separation train")
sep_module.addInputStream("feed stream", feed_stream)
sep_module.setSpecification("pressure1", 65.0)  # HP sep pressure
sep_module.setSpecification("pressure2", 25.0)  # MP sep pressure
sep_module.setSpecification("pressure3", 5.0)   # LP sep pressure

process = jneqsim.process.processmodel.ProcessSystem()
process.add(feed_stream)
process.add(sep_module)  # Module treated as 1 equipment
process.run()

# Get output streams from module
gas_out = sep_module.getOutputStream("gas exit stream")
oil_out = sep_module.getOutputStream("oil exit stream")
```

**Key API:**
- `addInputStream(String name, StreamInterface stream)` — wire input by port name
- `getOutputStream(String name)` — get output by port name
- `getOperations()` → `ProcessSystem` — access the internal process
- `getUnit(String name)` — access individual equipment inside the module
- `initializeModule()` — called automatically when added to a ProcessSystem

### Architecture C: ProcessModule (Multi-System Composition)

**When to use:** The source describes a **large facility** with multiple distinct
process areas that need separate convergence or different thermodynamic models.

```
┌─────────────────────────────────────────────────────────┐
│                    ProcessModule                        │
│                                                         │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────┐ │
│  │ ProcessSystem │──►│ ProcessSystem │──►│ProcessSystem│ │
│  │  Separation   │   │  Compression │   │ Dehydration │ │
│  └──────────────┘   └──────────────┘   └─────────────┘ │
│         │                                    │          │
│         └──────── recycle stream ─────────────┘          │
└─────────────────────────────────────────────────────────┘
```

**Pattern — composing multiple ProcessSystems:**

```python
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProcessModule = jneqsim.process.processmodel.ProcessModule

# Build each plant area as a separate ProcessSystem
sep_system = ProcessSystem()
sep_system.add(feed_stream)
sep_system.add(hp_separator)
sep_system.add(lp_separator)

comp_system = ProcessSystem()
comp_system.add(gas_from_sep)      # shared stream object links the systems
comp_system.add(compressor_1)
comp_system.add(aftercooler_1)
comp_system.add(compressor_2)

dehyd_system = ProcessSystem()
dehyd_system.add(compressed_gas)   # shared stream object
dehyd_system.add(teg_absorber)
dehyd_system.add(dry_gas_stream)

# Compose into a ProcessModule
plant = ProcessModule("Gas Processing Plant")
plant.add(sep_system)
plant.add(comp_system)
plant.add(dehyd_system)
plant.run()  # Runs sub-systems in order; handles cross-system recycles

# Access any equipment across all sub-systems
comp = plant.getUnit("compressor 1")
print(f"Power: {comp.getPower('kW'):.0f} kW")
```

**Key API for ProcessModule:**

| Method | Description |
|--------|-------------|
| `add(ProcessSystem)` | Add a sub-system |
| `add(ProcessModule)` | Nest another module |
| `run()` | Run all sub-systems (handles cross-system recycles) |
| `getUnit(String name)` | Find equipment across all sub-systems |
| `getAllProcessSystems()` | Recursively list all ProcessSystems |
| `getSubSystemCount()` | Number of sub-systems + nested modules |
| `hasRecycleLoops()` | Detect cross-system recycles |
| `validateStructure()` | Check for errors (empty modules, disconnected systems) |
| `buildModelGraph()` | Build directed graph for topology analysis |
| `getCalculationOrder()` | Topological sort across all sub-systems |
| `checkMassBalance(String unit)` | Mass balance verification |
| `copy()` | Deep serialization copy |

**Cross-system wiring mechanism:** Systems connect through **shared stream objects**.
When equipment in system A produces an outlet stream, that same Java object is used
as the inlet to equipment in system B. The `ProcessModelGraphBuilder` automatically
detects these cross-system links.

**Recycle convergence:** If recycles span multiple sub-systems, `ProcessModule.run()`
flattens all units and iterates until all recycles converge (max 100 iterations).

### Extraction Workflow Integration

When extracting from a source document, add a **Step 0** before the normal extraction:

**Step 0: Classify Process Complexity**

1. Count the total number of equipment items mentioned
2. Identify distinct plant areas (separation, compression, dehydration, etc.)
3. Check for cross-area recycles (e.g., regenerated solvent returning to absorber)
4. Check if any area matches a pre-built module
5. Choose architecture:

| Finding | Architecture | Output |
|---------|-------------|--------|
| ≤ 15 units, single area or simple branching | A: Single ProcessSystem | JSON |
| Standard subsystem (TEG, CO2 removal, etc.) | B: ProcessModuleBaseClass | Python code using pre-built module |
| Multiple areas, > 15 units, or cross-area recycles | C: ProcessModule | Python code composing ProcessSystems |
| Giant facility (> 50 units) or nested recycles | C: Nested ProcessModules | Python code with nested modules |

**Important:** When Architecture B or C is chosen, the extraction agent MUST:
- Still extract all parameters using the same skill rules (units, component names, etc.)
- Produce Python code instead of (or in addition to) JSON
- Clearly state why JSON alone is insufficient
- Use `ProcessSystem.fromJsonAndRun()` for individual sub-systems where possible,
  then compose them in Python

### Hybrid Approach (JSON + Python Composition)

For large processes, use JSON for each sub-system and compose in Python:

```python
import json
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProcessModule = jneqsim.process.processmodel.ProcessModule

# Each plant area as JSON → ProcessSystem
sep_json = json.dumps({"fluid": {...}, "process": [...]})
comp_json = json.dumps({"fluid": {...}, "process": [...]})

sep_result = ProcessSystem.fromJson(sep_json)   # build but don't run yet
sep_process = sep_result.getProcessSystem()

comp_result = ProcessSystem.fromJson(comp_json)
comp_process = comp_result.getProcessSystem()

# Wire cross-system streams
# Get output from separation and set as input to compression
sep_gas = sep_process.getUnit("HP separator").getGasOutStream()
comp_feed = comp_process.getUnit("comp feed")  # The Stream in comp_json
# Replace comp feed's fluid with sep gas's fluid
comp_feed.setThermoSystem(sep_gas.getThermoSystem().clone())

# Compose and run
plant = ProcessModule("plant")
plant.add(sep_process)
plant.add(comp_process)
plant.run()
```

### When NOT to Use Modules

- **Simple processes** — don't over-architect a 4-unit process with modules
- **Learning / demos** — use single ProcessSystem for clarity
- **JSON-only requirement** — modules require Python/Java code
- **No cross-area interaction** — independent systems can just be separate `fromJsonAndRun()` calls

---

## 17. Word Document (.docx) Input Support

Process descriptions often arrive as Word documents. This section covers extracting
simulation parameters from `.docx` files using `python-docx`.

### Required Library

```python
import docx  # pip install python-docx
```

### Document Loading Pattern

```python
import os, docx

DOC_PATHS = [
    os.path.join(os.path.dirname(globals().get("__vsc_ipynb_file__", "")),
                 "process_description.docx"),
    r"C:\Users\...\process_description.docx",
    "process_description.docx",  # fallback: current directory
]

doc = None
for path in DOC_PATHS:
    if path and os.path.exists(path):
        doc = docx.Document(path)
        break
if doc is None:
    raise FileNotFoundError("Could not find document")
```

### Extracting Paragraphs and Tables

```python
paragraphs = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
tables_data = []
for table in doc.tables:
    rows = [[cell.text.strip() for cell in row.cells] for row in table.rows]
    tables_data.append(rows)
```

### Composition Extraction from Tables

Map document component names to NeqSim names using a lookup table:

```python
NEQSIM_COMPONENT_MAP = {
    "Nitrogen": "nitrogen", "N2": "nitrogen",
    "CO2": "CO2", "Carbon Dioxide": "CO2",
    "Methane": "methane", "C1": "methane",
    "Ethane": "ethane", "C2": "ethane",
    "Propane": "propane", "C3": "propane",
    "i-Butane": "i-butane", "iC4": "i-butane",
    "n-Butane": "n-butane", "nC4": "n-butane",
    "i-Pentane": "i-pentane", "iC5": "i-pentane",
    "n-Pentane": "n-pentane", "nC5": "n-pentane",
    "C6+": "n-hexane",  # Simplified plus-fraction
    "n-Hexane": "n-hexane", "C6": "n-hexane",
    "H2S": "H2S", "Water": "water", "H2O": "water",
}

# Find the composition table (look for "Component" + "Mole fraction" headers)
composition = {}
for table_rows in tables_data:
    header = [h.lower() for h in table_rows[0]]
    if "component" in header and any("mole" in h or "fraction" in h for h in header):
        comp_col = header.index("component")
        frac_col = next(i for i, h in enumerate(header) if "mole" in h or "fraction" in h)
        for row in table_rows[1:]:
            doc_name = row[comp_col].strip()
            neqsim_name = NEQSIM_COMPONENT_MAP.get(doc_name)
            if neqsim_name:
                composition[neqsim_name] = float(row[frac_col])
        break

# Normalize to sum = 1.0
total = sum(composition.values())
composition = {k: v/total for k, v in composition.items()}
```

### Operating Condition Extraction from Narrative

Extract temperatures and pressures from the prose text using pattern matching:

```python
import re

def extract_from_paragraphs(paragraphs, patterns):
    """Search paragraphs for key-value patterns."""
    results = {}
    for p in paragraphs:
        for key, regex in patterns.items():
            m = re.search(regex, p, re.IGNORECASE)
            if m and key not in results:
                results[key] = float(m.group(1))
    return results

# Typical patterns found in process descriptions
patterns = {
    "feed_pressure":  r"(?:inlet|feed)\s+(?:pressure|P)\s*[:\s]+(\d+\.?\d*)\s*bar",
    "feed_temperature": r"(?:inlet|feed)\s+(?:temperature|T)\s*[:\s]+(\d+\.?\d*)\s*°?C",
    "hp_sep_pressure": r"HP\s+(?:separator|sep).*?(\d+\.?\d*)\s*bar",
    "export_pressure": r"export.*?(\d+\.?\d*)\s*bar",
}
```

For complex documents, also extract from tables with "Parameter"/"Value" structure:

```python
for table_rows in tables_data:
    header = [h.lower() for h in table_rows[0]]
    if "parameter" in header and any("value" in h for h in header):
        param_col = header.index("parameter")
        val_col = next(i for i, h in enumerate(header) if "value" in h)
        for row in table_rows[1:]:
            param = row[param_col].lower()
            try:
                val = float(re.sub(r'[^\d.]', '', row[val_col]))
            except ValueError:
                continue
            if "pressure" in param:
                results["feed_pressure"] = val
            elif "temperature" in param:
                results["feed_temperature"] = val
```

### Architecture Decision After Extraction

After extracting all parameters, count equipment and decide architecture:

```python
# Count equipment mentioned in document
equipment_count = sum(1 for p in paragraphs
    if any(kw in p.lower() for kw in
           ["separator", "compressor", "cooler", "heater", "valve",
            "column", "pump", "mixer", "exchanger", "drum"]))

if equipment_count <= 8:
    print("Architecture A: Single ProcessSystem")
elif equipment_count <= 20:
    print("Architecture A or B: Single system or JSON builder")
else:
    print("Architecture C: ProcessModule with multiple sub-systems")
```

### Tested Example

See `examples/notebooks/process_extraction_from_document.ipynb` for a full
end-to-end example that demonstrates the **Free Text → JSON → NeqSim Model** pipeline:

1. Loads a Word document describing a gas+condensate processing facility
2. Extracts composition from a table (10 components)
3. Extracts feed and operating conditions from tables and narrative
4. Generates a NeqSim JSON file (`process_from_document.json`) as portable intermediate
5. Builds process from JSON via `ProcessSystem.fromJson()` (core gas train, 10 units)
6. Adds a scrubber liquid recycle using the hybrid approach (clone + Recycle object)
7. Builds a full plant via ProcessModule with 3 sub-systems (20 units)
8. Validates results (8/8 + 11/11 checks pass) and generates 3 figures

## 18. Recycle Stream Handling

### When Recycles Are Needed

Recycles appear whenever a downstream liquid or gas stream must be returned
to an upstream unit:

- **Scrubber liquid recycle**: Knockout drum condensate → HP separator
- **Anti-surge recycle**: Compressor discharge → compressor suction via cooler
- **Solvent loop**: Regenerated solvent → top of absorber column
- **Reflux**: Condenser liquid → top tray of distillation column

### JSON Builder Limitation

The JSON builder (`ProcessSystem.fromJson()`) does **not** support recycle
wiring natively. The `Recycle` class requires `setOutletStream()` which is
not handled by the reflection-based inlet wiring in `JsonProcessBuilder`.

**Solution: Hybrid approach** — use JSON for the main process, then add
recycles via Python/Java code after building from JSON.

### Recycle Pattern (Clone + Recycle Object)

```python
# 1. Clone a stream to create the "tear" (initial guess) stream
recycle_stream = source_stream.clone("Recycle Stream Name")
recycle_stream.setFlowRate(1e-6, "kg/hr")  # Tiny initial guess
process.add(recycle_stream)

# 2. Add the clone as an extra inlet to the receiving equipment
#    Use addStream() for separators/mixers
hp_separator.addStream(recycle_stream)

# 3. Build the rest of the process normally
# ... (knockout scrubber, pump, etc.)

# 4. Pump the actual recycle liquid back to upstream pressure
pump = ns.Pump("Recycle Pump", scrubber.getLiquidOutStream())
pump.setOutletPressure(upstream_pressure)
process.add(pump)

# 5. Create Recycle object to converge the loop
recycle = ns.Recycle("Recycle Name")
recycle.addStream(pump.getOutletStream())      # Actual downstream output
recycle.setOutletStream(recycle_stream)         # Clone tear stream
recycle.setTolerance(1e-2)                      # Flow/temp/comp tolerance
process.add(recycle)

# ProcessSystem.run() iterates until recycle converges
process.run()
```

### Recycle API Reference

| Method | Description |
|--------|-------------|
| `Recycle(String name)` | Constructor |
| `addStream(StreamInterface)` | Set the input (actual downstream stream) |
| `setOutletStream(StreamInterface)` | Set the output (clone/tear stream) |
| `setTolerance(double)` | Set flow/temp/composition convergence tolerance (default 1e-2) |
| `setAccelerationMethod(AccelerationMethod)` | `DIRECT_SUBSTITUTION`, `WEGSTEIN`, `BROYDEN` |
| `setMaxIterations(int)` | Max recycle iterations (default 10) |

### JSON Schema Extension for Recycles

When the extraction identifies a recycle, include it in the JSON `process`
array as a comment/marker that signals hybrid wiring is needed:

```json
{
  "type": "Recycle",
  "name": "Scrubber Liquid Recycle",
  "inlet": "Scrubber Pump",
  "properties": {
    "outletStream": "Recycle Stream Name",
    "tolerance": 0.01
  },
  "_note": "HYBRID: Requires Python wiring — JSON builder cannot set outletStream"
}
```

The agent should detect this marker and generate additional Python code for
recycle wiring after the `fromJson()` call.

## 19. Three-Step Extraction Pipeline

The recommended workflow for converting free text to a running simulation:

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Free Text   │ ──→ │  NeqSim JSON  │ ──→ │  Running Process  │
│  (.docx/txt) │     │  (.json file) │     │  (ProcessSystem)  │
└─────────────┘     └──────────────┘     └──────────────────┘
   Step 1: Parse       Step 2: Structure     Step 3: Build & Run
```

### Step 1: Parse — Extract Parameters

- Use `python-docx` for `.docx`, regex for plain text
- Extract: composition, feed conditions, equipment list, operating conditions
- Map component names using `NEQSIM_COMPONENT_MAP` (Section 5)
- Normalize mole fractions to sum to 1.0

### Step 2: Structure — Generate JSON

- Build the JSON dict following the schema in Section 1
- Use dot-notation for stream wiring (Section 3)
- Save to a `.json` file for portability and version control
- The JSON file is the **single source of truth** for the process

### Step 3: Build & Run — Load JSON into NeqSim

```python
# Load JSON and build process
import jpype
ProcessSystem = jpype.JClass("neqsim.process.processmodel.ProcessSystem")
result = ProcessSystem.fromJson(json_str)

if result.isError():
    for err in result.getErrors():
        print(f"[{err.getCode()}] {err.getMessage()}")
else:
    process = result.getProcessSystem()
    process.run()

    # Access equipment by name
    separator = process.getUnit("V-101 HP Separator")

    # Access streams via dot-notation
    gas_stream = process.resolveStreamReference("V-101 HP Separator.gasOut")
```

### API Reference for JSON Pipeline

| Method | Returns | Description |
|--------|---------|-------------|
| `ProcessSystem.fromJson(String)` | `SimulationResult` | Build process from JSON (no run) |
| `ProcessSystem.fromJsonAndRun(String)` | `SimulationResult` | Build and run in one call |
| `result.isError()` | `boolean` | Check for build errors |
| `result.isSuccess()` | `boolean` | Check for success |
| `result.getProcessSystem()` | `ProcessSystem` | Get the built process |
| `result.getErrors()` | `List<ErrorDetail>` | Error details with codes and remediation |
| `process.getUnit(String name)` | `ProcessEquipmentInterface` | Get equipment by name |
| `process.resolveStreamReference(String ref)` | `StreamInterface` | Get stream by dot-notation |

### Stream Reference Dot-Notation

| Suffix | Method Called | Example |
|--------|-------------|---------|
| `.gasOut` or `.gas` | `getGasOutStream()` | `"V-101 HP Sep.gasOut"` |
| `.liquidOut` or `.liquid` | `getLiquidOutStream()` | `"V-201 Knockout.liquidOut"` |
| `.oilOut` or `.oil` | `getOilOutStream()` | `"V-101 HP Sep.oilOut"` |
| `.waterOut` or `.water` | `getWaterOutStream()` | `"V-101 HP Sep.waterOut"` |
| `.outlet` (default) | `getOutletStream()` | `"K-401 Compressor"` |

## 20. Compressor Performance Curves & Anti-Surge Handling

### When to Add Compressor Curves

Add performance curves when the document mentions any of:
- Compressor performance maps, characteristic curves, or operating envelopes
- Surge protection, anti-surge control, or recycle valve
- Compressor turndown requirements or minimum flow
- Polytropic or isentropic head values
- Multiple speed operations or variable speed drive (VSD)

### CompressorChartGenerator — Auto-Generate from Design Point

`CompressorChartGenerator` creates a performance map (head, efficiency, surge/choke
curves) scaled from the compressor's computed design point. **The compressor must be
run at least once first** so the generator can read `getPolytropicFluidHead()`,
`getInletStream().getFlowRate("m3/hr")`, and `getSpeed()`.

```python
import jpype
CompressorChartGenerator = jpype.JClass(
    "neqsim.process.equipment.compressor.CompressorChartGenerator"
)

# 1. Configure compressor
comp = ns.Compressor("K-401", inlet_stream)
comp.setUsePolytropicCalc(True)
comp.setOutletPressure(125.0)
comp.setPolytropicEfficiency(0.75)

# 2. Run process once to establish design point
process.add(comp)
process.run()  # Compressor now has head, flow, speed

# 3. Generate chart from design point
chart_gen = CompressorChartGenerator(comp)
comp.setCompressorChart(chart_gen.generateCompressorChart("mid range"))
comp.setCompressorChartType("interpolate and extrapolate")

# 4. Re-run with chart enabled
process.run()
```

**Generation options:**
| Method | Description |
|--------|-------------|
| `generateCompressorChart("normal")` | Single-speed, 5 points per curve |
| `generateCompressorChart("mid range")` | Single-speed, 3 points (surge → stonewall) |
| `generateCompressorChart("normal", 5)` | Multi-speed, 5 speed curves |
| `generateFromTemplate("CENTRIFUGAL_STANDARD", 9)` | Standard centrifugal template |
| `generateFromTemplate("CENTRIFUGAL_HIGH_FLOW", 5)` | High-flow template |
| `generateFromTemplate("CENTRIFUGAL_HIGH_HEAD", 5)` | High-head template |

### Loading Manufacturer Curves from JSON

If the document includes actual performance data (from OEM data sheets):

```python
# From a JSON file
comp.loadCompressorChartFromJson("path/to/chart.json")

# From a JSON string (e.g., extracted from document)
comp.loadCompressorChartFromJsonString(json_string)

# Export chart as JSON
chart_json = comp.getCompressorChartAsJson()
```

**Chart JSON format:**
```json
{
  "compressorName": "K-401 Export Compressor",
  "headUnit": "kJ/kg",
  "maxDesignPower_kW": 8500.0,
  "referenceConditions": {
    "molecularWeight": 18.5,
    "temperature_K": 303.15,
    "pressure_bara": 65.0,
    "compressibilityZ": 0.92
  },
  "speedCurves": [
    {
      "speed_rpm": 3000.0,
      "flow_m3h": [3300, 4700, 6700],
      "head_kJkg": [88.8, 74.0, 37.0],
      "polytropicEfficiency_pct": [67.5, 75.0, 63.75]
    }
  ],
  "surgeFlow": [3300],
  "surgeHead": [88.8],
  "chokeFlow": [6700],
  "chokeHead": [37.0]
}
```

### Anti-Surge Loop Topology

The typical NCS anti-surge pattern (used on offshore platforms):

```
                    ┌──────────────────────────────────────────┐
                    │              Anti-Surge Loop              │
                    ▼                                          │
Gas ──→ [Mixer] ──→ [Cooler] ──→ [Compressor w/Chart] ──→ [Splitter]──→ Main Export
                                                                │
                                                          [Calculator]
                                                                │
                                                     [Anti-Surge Valve] ──→ [Recycle]
                                                                               │
                                                                          back to Mixer
```

**Equipment roles:**
| Equipment | Role |
|-----------|------|
| **Clone stream** | Initial guess / tear stream for recycle (1e-6 kg/hr) |
| **Mixer** | Combines fresh feed + recycle at compressor suction |
| **Cooler** | Removes heat from recycle gas (optional but realistic) |
| **Compressor** | With chart enabled, tracks operating point vs surge line |
| **Splitter** | Splits discharge into main product (stream 0) + recycle (stream 1) |
| **Calculator** | Input=Compressor, Output=Splitter; auto-calculates recycle flow |
| **ThrottlingValve** | Drops recycle pressure back to suction pressure |
| **Recycle** | Converges the loop (addStream → setOutletStream → tolerance) |

### Complete Anti-Surge Code Pattern

```python
import jpype
CompressorChartGenerator = jpype.JClass(
    "neqsim.process.equipment.compressor.CompressorChartGenerator"
)
Calculator = jpype.JClass("neqsim.process.equipment.util.Calculator")

# 1. Clone for anti-surge recycle
as_recycle_stream = gas_stream.clone("AS Recycle Stream")
as_recycle_stream.setFlowRate(1e-6, "kg/hr")
process.add(as_recycle_stream)

# 2. Suction mixer
suction_mixer = ns.Mixer("Suction Mixer")
suction_mixer.addStream(gas_stream)
suction_mixer.addStream(as_recycle_stream)
process.add(suction_mixer)

# 3. Suction cooler (optional, removes recycle heat)
suction_cooler = ns.Cooler("Suction Cooler", suction_mixer.getOutletStream())
suction_cooler.setOutTemperature(273.15 + 30.0)
process.add(suction_cooler)

# 4. Compressor with polytropic calculation
comp = ns.Compressor("Export Compressor", suction_cooler.getOutletStream())
comp.setUsePolytropicCalc(True)
comp.setOutletPressure(125.0)
comp.setPolytropicEfficiency(0.75)
process.add(comp)

# 5. Discharge splitter (stream 0 = product, stream 1 = recycle)
splitter = ns.Splitter("Discharge Splitter", comp.getOutletStream(), 2)
splitter.setFlowRates(jpype.JArray(jpype.JDouble)([-1.0, 1e-6]), "kg/hr")
process.add(splitter)

# 6. Calculator: auto-adjusts splitter based on compressor state
calc = Calculator("Anti-Surge Calculator")
calc.addInputVariable(comp)
calc.setOutputVariable(splitter)
process.add(calc)

# 7. Anti-surge valve
valve = ns.ThrottlingValve("Anti-Surge Valve", splitter.getSplitStream(1))
valve.setOutletPressure(65.0)  # Match suction pressure
process.add(valve)

# 8. Recycle converges the loop
recycle = ns.Recycle("Anti-Surge Recycle")
recycle.addStream(valve.getOutletStream())
recycle.setOutletStream(as_recycle_stream)
recycle.setTolerance(1e-2)
process.add(recycle)

# 9. Export cooler on main product
cooler = ns.Cooler("Export Cooler", splitter.getSplitStream(0))
cooler.setOutTemperature(273.15 + 40.0)
process.add(cooler)

# Phase 1: Run without chart (establish design point)
process.run()

# Phase 2: Generate chart and re-run
chart_gen = CompressorChartGenerator(comp)
comp.setCompressorChart(chart_gen.generateCompressorChart("mid range"))
comp.setCompressorChartType("interpolate and extrapolate")
process.run()

# Check results
recycle_flow = splitter.getSplitStream(1).getFlowRate("kg/hr")
if recycle_flow > 1.0:
    print(f"Anti-surge active: {recycle_flow:.0f} kg/hr recycled")
else:
    print("Operating above surge line — no recycle needed")
```

### Built-in AntiSurge Object (Simpler Alternative)

For simpler checks without the full recycle topology:

```python
# Access the compressor's built-in anti-surge object
anti_surge = comp.getAntiSurge()
anti_surge.setActive(True)
anti_surge.setSurgeControlFactor(1.05)  # 5% margin above surge line

# After running:
is_surge = comp.isSurge()         # True if operating below surge line
is_stonewall = comp.isStoneWall() # True if at choke
head = comp.getPolytropicFluidHead()  # kJ/kg
```

**AntiSurge control strategies:**
| Strategy | Description |
|----------|-------------|
| `ON_OFF` | Binary: valve fully open or closed at surge |
| `PROPORTIONAL` | Valve opening proportional to distance from surge |
| `PID` | Full PID control (Kp=2.0, Ki=0.5, Kd=0.1 defaults) |
| `PREDICTIVE` | Anticipates surge based on rate of change |
| `DUAL_LOOP` | Combines flow controller + backup pressure controller |

### JSON Builder Limitation

Compressor curves and anti-surge loops **cannot** be configured via the JSON
builder alone. Like recycles (Section 18), they require the **hybrid approach**:
JSON for the main process topology, then Python/Java code to add:

1. Performance chart (CompressorChartGenerator or loadFromJson)
2. Anti-surge loop equipment (Mixer, Splitter, Calculator, Valve, Recycle)

### Extraction Agent Detection Rules

When extracting from documents, flag compressors for anti-surge setup when:
- Document mentions "anti-surge", "surge control", "recycle valve", or "minimum flow"
- Compressor has turndown requirements (< 70% of design flow operation expected)
- Multiple compressors in series (cascade surge risk)
- Variable feed conditions (composition or flow rate swings)
- Export/pipeline compressors (typically have anti-surge systems)

---

## 21. Tested Pitfalls & Known Limitations

Systematic testing of the extraction workflow (see `examples/notebooks/test_extraction_workflow.ipynb`)
identified these pitfalls with fixes:

### Component Name Mapping (CRITICAL)

NeqSim uses specific component names in its database. Verbose names from documents
must be mapped to NeqSim names **before** generating JSON. The `fromJson()` builder
does NOT do automatic alias resolution.

| Document Name | NeqSim Name | Common Mistake |
|--------------|-------------|----------------|
| carbon dioxide | `CO2` | `"carbon dioxide"` fails |
| hydrogen sulfide, hydrogen sulphide | `H2S` | `"hydrogen sulfide"` fails |
| water, H2O | `water` | OK |
| iso-butane, isobutane | `i-butane` | `"iso-butane"` may fail |
| iso-pentane, isopentane | `i-pentane` | `"iso-pentane"` may fail |
| monoethylene glycol | `MEG` | `"monoethylene glycol"` fails |
| triethylene glycol | `TEG` | `"triethylene glycol"` fails |
| methyl diethanolamine | `MDEA` | Verbose form fails |

**Rule:** Always map verbose component names to the short NeqSim database names
listed in Section 5 before generating the JSON.

### CPA Mixing Rule Name (CRITICAL)

The mixing rule for CPA **must** be the enum name `"CLASSIC_TX_CPA"`, not the
numeric string `"10"`. The `setMixingRule(String)` method calls
`EosMixingRuleType.byName()` which expects uppercase enum names.

| Correct | Wrong | Error |
|---------|-------|-------|
| `"mixingRule": "CLASSIC_TX_CPA"` | `"mixingRule": "10"` | `EosMixingRuleType:byName - Input name is not valid` |
| `"mixingRule": "classic"` | `"mixingRule": "2"` | Same error |

### Mixer Multi-Inlet Syntax (CRITICAL)

Mixers require `"inlets"` (plural key) with an array value. Using `"inlet"` with
an array causes `"Array must have size 1"` error.

| Correct | Wrong |
|---------|-------|
| `"inlets": ["stream1", "stream2"]` | `"inlet": ["stream1", "stream2"]` |

### Separator Stream Resolution

`resolveStreamReference("HP Sep")` returns `null` for separators — you MUST specify
the port. For non-separator equipment (Cooler, Compressor, etc.), bare names work fine.

| Equipment | Bare Name | With Port |
|-----------|-----------|-----------|
| `Separator` | returns null | `"HP Sep.gasOut"` or `"HP Sep.liquidOut"` |
| `ThreePhaseSeparator` | returns null | `"Sep.gasOut"`, `"Sep.oilOut"`, `"Sep.waterOut"` |
| `Cooler` | `"Cooler"` works | `"Cooler.outlet"` also works |
| `Compressor` | `"Comp"` works | `"Comp.outlet"` also works |
| `Stream` | `"feed"` works | N/A |

### Empty Composition Silently Passes

An empty `"components": {}` does not error at build time but produces a system with
no components. Always validate `len(components) > 0` before generating JSON.

### Unsupported Equipment Types

The following equipment types are NOT supported by `fromJson()`:
- `Absorber` — use `Separator` chain or hybrid approach
- `DistillationColumn` — use hybrid approach (build column in Python/Java)
- `Reactor` — use hybrid approach with `GibbsReactor` in Python/Java
- `Filter` — not in JSON builder

When these are detected in a document, flag them as requiring hybrid approach
and build the surrounding process in JSON, leaving these for manual wiring.

### Error Message Actionability

The `fromJson()` error messages are structured and actionable:
- `[FLUID_ERROR]` → Check component names and mixing rule
- `[UNIT_ERROR]` → Check equipment type name and properties
- `[STREAM_NOT_FOUND]` → Check inlet reference matches a previously defined unit
- `[NO_FLUID]` → The fluid failed to create, cascading to dependent units

### Test Results Summary (14 tests)

| Category | Tests | Passed | Key Findings |
|----------|-------|--------|-------------|
| Main Scenarios | 5 | 5/5 | 3-stage compression, TEG dehydration, subsea tieback, tabular HMB, ambiguous input all pass |
| Edge Cases (original) | 6 | 1/6 | Verbose names, wrong mixer syntax, wrong CPA rule, invalid type, missing ref all caught with clear errors |
| Edge Cases (corrected) | 3 | 3/3 | Fixes for CPA rule, mixer syntax, component names all verified |

See `examples/notebooks/test_extraction_workflow.ipynb` for the full test suite.
