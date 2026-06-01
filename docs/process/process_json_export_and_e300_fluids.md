---
title: Process JSON Export and E300 Fluid Characterization
description: Detailed guide for exporting ProcessSystem and ProcessModel objects to self-contained JSON for MCP and API use, including special handling for E300-characterized base fluids, stream-specific fluids, recycle settings, and verification checks.
---

# Process JSON Export and E300 Fluid Characterization

This guide explains how to export an existing NeqSim `ProcessSystem` or multi-area
`ProcessModel` to JSON, run it through the JSON builder or MCP runner, and keep parity when the
base fluid was characterized from Eclipse E300 data.

Use this page when you already have a working process model in Java or Python and want a durable
JSON artifact for MCP, web APIs, regression tests, or agent workflows. For hand-written builder
input, see [JSON Format for ProcessSystem and ProcessModel](json_process_models_and_systems.md).

## Why E300 Needs Special Treatment

An E300 fluid is not only a component list and a composition. It can carry pseudo-component and
plus-fraction data that affects density, liquid volume, and phase behavior:

| Data item | Why it matters in JSON replay |
|----------|-------------------------------|
| Critical temperature and pressure | Rebuilds the EoS component parameters for TBP and plus fractions |
| Acentric factor and optional OmegaA override | Preserves cubic EoS alpha behavior for characterized components |
| Molecular weight and normal liquid density | Preserves material balance, oil density, and volume rates |
| Critical volume, Rackett Z, parachor | Preserves liquid-density and surface-property behavior where available |
| Volume-correction flag and constants | Preserves Peneloux/E300 volume-shift behavior and oil export volume rates |
| Binary interaction parameters | Preserves tuned phase behavior when non-default BIPs are present |

If JSON only stores component names and mole fractions, mass rates can still match while volume
rates drift. This is most visible on oil export and stable-liquid streams where density depends on
volume correction and characterized pseudo-components.

## Export Modes

NeqSim supports two E300-related JSON modes.

| Mode | JSON field | Best use | Portability |
|------|------------|----------|-------------|
| Path-based import | `fluid.e300FilePath` | Local builder jobs where the E300 file is available on disk | Depends on local file path |
| Self-contained export | `componentProperties`, `useVolumeCorrection`, BIPs | MCP, APIs, task artifacts, and model exchange | Portable JSON, no E300 file needed |

The exporter uses the self-contained mode. It writes an E300-equivalent property snapshot for each
fluid in the process JSON. It does not need the original E300 file path once the fluid has been
loaded into NeqSim.

The current process JSON export includes non-zero scalar EoS binary interaction parameters as
`binaryInteractionParameters` entries with `comp1`, `comp2`, and `kij`. It does not yet serialize
temperature-dependent BIP terms such as `kijT1` or asymmetric `ij`/`ji` mixing-rule matrices.

## Recommended Export Workflow

1. Build and run the original process model until it represents the accepted live case.
2. For multi-area plants, use `ProcessModel` and add process areas in upstream-to-downstream order.
3. Set the intended execution mode on the `ProcessModel` before export:
   - `runStep = false` for MCP run-to-convergence.
   - `runStep = true` only for one-pass diagnostic snapshots.
4. Set outer convergence controls on the `ProcessModel` when the model needs values different from
   the defaults:
   - `maxIterations`
   - `flowTolerance`
   - `temperatureTolerance`
   - `pressureTolerance`
5. Export JSON from the live model.
6. Check that E300-equivalent fluid fields are present.
7. Rebuild or run through MCP.
8. Compare mass rates, standard gas rates, liquid volume rates, and convergence summary against the
   original case.

## What `toJson()` Exports

For a `ProcessSystem`, `toJson()` writes one JSON object with a default `fluid`, optional named
`fluids`, and a `process` array. For a `ProcessModel`, `toJson()` writes a top-level `areas` object
where each area is a `ProcessSystem` JSON object.

Key top-level fields for `ProcessModel` are:

| Field | Meaning |
|-------|---------|
| `type` | `ProcessModel` |
| `name` | Process model name |
| `areas` | Named `ProcessSystem` JSON definitions |
| `interAreaLinks` | Live stream references crossing process-system boundaries |
| `runStep` | Whether MCP or import should run one step or iterate to convergence |
| `maxIterations` | Outer ProcessModel iteration limit |
| `flowTolerance` | Relative boundary-flow convergence tolerance |
| `temperatureTolerance` | Relative boundary-temperature convergence tolerance |
| `pressureTolerance` | Relative boundary-pressure convergence tolerance |

## E300-Equivalent Fluid Fields

The exporter writes full component properties for all exported fluids, including default fluids,
named stream fluids, and materialized boundary-stream fluids.

Expected fluid fields include:

```json
{
  "model": "PR",
  "temperature": 304.15,
  "pressure": 55.0,
  "mixingRule": "classic",
  "useVolumeCorrection": true,
  "components": {
    "methane": 0.35
  },
  "characterizedComponents": [
    {
      "name": "C7",
      "moleFraction": 0.65,
      "molarMass": 0.2,
      "density": 0.84,
      "Tc": 650.0,
      "Pc": 25.0,
      "acentricFactor": 0.85,
      "volumeCorrection": 0.0456,
      "volumeShift": 0.0456
    }
  ],
  "componentProperties": [
    {
      "name": "methane",
      "moleFraction": 0.35,
      "molarMass": 0.01604,
      "Tc": 190.56,
      "Pc": 45.99,
      "acentricFactor": 0.011,
      "volumeCorrection": 0.0123,
      "omegaA": 0.45724
    }
  ]
}
```

The builder applies `componentProperties` after recreating the base fluid. This is what preserves
E300-like pseudo-component and volume-correction behavior without needing `e300FilePath`.

## Stream-Specific Fluids and Feed Compositions

Large process models often contain multiple feed streams with different compositions or fluids. The
exporter gives explicit stream units their own named fluid reference:

```json
{
  "fluids": {
    "oil_feed_fluid": {
      "model": "PR",
      "useVolumeCorrection": true,
      "componentProperties": []
    }
  },
  "process": [
    {
      "type": "Stream",
      "name": "oil feed",
      "fluidRef": "oil_feed_fluid",
      "properties": {
        "flowRate": [1000.0, "kg/hr"],
        "temperature": [31.0, "C"],
        "pressure": [7.55, "bara"]
      }
    }
  ]
}
```

This avoids the common failure mode where every feed is rebuilt from the same default fluid. For
E300-characterized oils, it also ensures each stream keeps its own composition and component
property snapshot.

## Multi-Area ProcessModel Export

For platform-scale models, split the plant into separate `ProcessSystem` objects and combine them
with `ProcessModel`. JSON export preserves:

| Feature | JSON treatment |
|---------|----------------|
| Process area names | Top-level `areas` keys |
| Cross-area streams | `interAreaLinks` plus materialized boundary streams for area-level rebuilds |
| Execution controls | `runStep`, `maxIterations`, and tolerances |
| Recycle settings | Flow, composition, temperature and pressure tolerances, priority, max iterations, acceleration method, Wegstein settings, downstream properties |
| Compressor behavior | Outlet pressure, speed controls, compressor chart, surge curve, stonewall curve, anti-surge settings |
| Calculator equipment | Calculator input and output equipment references |
| Pipelines | Length, diameter, elevation, wall roughness, and number of increments |
| Heaters/coolers | Outlet temperature, pressure drop, explicit outlet pressure |
| Heat exchangers | Inlet streams, UA value, and outlet-temperature guesses |

The MCP runner now applies top-level `ProcessModel` execution settings before running the model.
Use `runStep: false` when the JSON should converge the same way as the original plant model.

## Verification Checklist

After exporting a model that depends on E300 characterization, inspect the JSON text for these
fields:

| Check | Expected result |
|-------|-----------------|
| `"componentProperties"` | Present in default and named fluids |
| `"useVolumeCorrection"` | Present and true for volume-corrected fluids |
| `"volumeCorrection"` or `"volumeShift"` | Present for E300/TBP/plus components where used |
| `"omegaA"` | Present when an EoS component has an OmegaA override |
| `"binaryInteractionParameters"` | Present when non-zero scalar `kij` values are configured |
| `"fluidRef"` | Present on explicit stream units with their own fluid |
| `"interAreaLinks"` | Present for ProcessModel cross-area stream references |
| `"runStep": false` | Present for run-to-convergence MCP execution |
| Recycle tolerances | Present for recycle units with customized convergence settings |

Then compare the rebuilt or MCP-run report against the original model:

| Quantity | Why it is checked |
|----------|-------------------|
| Gas export standard flow | Confirms gas composition, standard volume basis, and compressor routing |
| Gas export mass flow | Confirms total material balance |
| Oil export mass flow | Confirms liquid routing and pumps |
| Oil export volume flow | Confirms liquid density and volume correction |
| Stable oil volume flow | Confirms E300-like liquid property replay after stabilization |
| `convergenceSummary` | Confirms run mode, iteration count, boundary residuals, and unsolved areas |

Mass rates should normally match first. If mass rates match but oil volume rates differ, inspect
`useVolumeCorrection`, `volumeCorrection`, `density`, and `componentProperties` before changing
process equipment settings.

## MCP Execution Notes

MCP runs process JSON through `ProcessRunner.run(json)`. The response has two separate concepts:

| Response field | Meaning |
|----------------|---------|
| `status: success` | JSON built and the simulation run completed without an exception |
| `provenance.converged` | The `ProcessModel` convergence criteria were met |
| `convergenceSummary` | Detailed iteration count, boundary errors, and unsolved process areas |

For multi-area models, treat `status: success` as a successful execution, not as proof of final
convergence. Check `provenance.converged` and `convergenceSummary` before accepting the run.

## When to Use `e300FilePath`

Use `e300FilePath` only when the JSON consumer can access the same E300 file path:

```json
{
  "fluid": {
    "model": "PR_LK",
    "temperature": 310.15,
    "pressure": 50.0,
    "e300FilePath": "C:/models/basefluid.e300"
  },
  "process": []
}
```

This mode is useful for local jobs and interactive debugging. It is less suitable for MCP or web
APIs because the server may not have that file. For portable artifacts, export the already-loaded
model and rely on `componentProperties` instead.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Oil mass matches but oil volume differs | Missing volume correction or component property snapshot | Re-export with current `toJson()` and verify `useVolumeCorrection` plus `componentProperties` |
| All feeds rebuild with same composition | Missing stream-level `fluidRef` | Export from the live model rather than hand-copying only root `fluid` |
| MCP says success but not converged | Model ran but boundary residuals or area recycle units did not meet tolerances | Check `convergenceSummary`, recycle settings, and `maxIterations` |
| Recycle settings differ after import | Old JSON did not include recycle tolerances or acceleration settings | Re-export with current JSON exporter |
| Compressor train changes behavior | Missing chart, surge, stonewall, anti-surge, or calculator data | Verify `compressorChart`, `antiSurge`, and calculator references in JSON |
| JSON works locally but not on MCP | Path-based `e300FilePath` is not available to MCP | Use self-contained exported JSON with component properties |

## Related Documentation

- [JSON Format for ProcessSystem and ProcessModel](json_process_models_and_systems.md)
- [ProcessModel](processmodel/process_model.md)
- [MCP Server Guide](../integration/mcp_server_guide.md)
- [Process Serialization](../simulation/process_serialization.md)
- [PVT JSON Fluid Format](../pvtsimulation/json_fluid_format.md)
- [UniSim/HYSYS Conversion](unisim-to-neqsim-conversion.md)
