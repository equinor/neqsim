---
title: "MCP Chemistry Tool Reference"
description: "JSON schema reference for the runChemistry MCP tool exposed by the NeqSim MCP server. Covers Pitzer qualification, electrolyte scale prediction, mechanistic CO2 corrosion, Langmuir inhibitor dosing, and packed-bed H2S scavenger breakthrough."
---

# `runChemistry` MCP Tool Reference

The NeqSim MCP server exposes the chemistry stack through a single tool,
`runChemistry`, that accepts a JSON specification with an `analysis` field and
analysis-specific parameters.

All responses follow the standard NeqSim MCP envelope:

```json
{
  "status": "success",
  "analysis": "<name>",
  "data": { ... },
  "provenance": {
    "calculationType": "chemistry: <name>",
    "converged": true,
    "computationTimeMs": 12
  }
}
```

On failure: `{"status":"error","errors":[{"code","message","remediation"}]}`.

## Analyses

### `pitzerQualification`

Fail-closed setup/publication view over the authoritative Java
`SystemPitzer`/`PhasePitzer` coverage and qualification APIs. It performs no
flash and changes no parameter. Amounts are system moles; the aqueous model
derives molality from the water mass.

| Field | Unit | Required / values |
|-------|------|-------------------|
| `temperature_K` | K | required, finite and positive |
| `pressure_bara` | bara | required, finite and positive |
| `components` | mol | required object; positive `water` and non-negative finite amounts |
| `dataset` | – | `auto` (default), `legacy`, `phreeqc-na-k-cl`, `phreeqc-co2-na2so4`, or `phreeqc-catalog` |
| `validationTarget` | – | optional enum: `AQUEOUS_ACTIVITY_COEFFICIENTS`, `WATER_ACTIVITY_AND_OSMOTIC_COEFFICIENT`, `GAS_AQUEOUS_VLE`, `REACTIVE_SPECIATION`, or `MINERAL_SATURATION_AND_PRECIPITATION` |

The response separates interaction coverage, dataset-level qualification,
observable qualification, and the exact state-range helper. `publicationReady`
is true only when those gates and the normalized/non-negative aqueous state
pass. Missing `validationTarget`, incomplete
ionic or neutral topology, an unqualified observable, or an out-of-envelope
state returns `decision: "REJECTED"`; no missing interaction is interpreted as
zero. A non-electroneutral ionic input is rejected before dataset selection.
The current PHREEQC interaction functions have no pressure argument, so
`stateRange.pressureChecked` is explicitly false rather than implying a
pressure qualification.

```json
{
  "analysis": "pitzerQualification",
  "temperature_K": 298.15,
  "pressure_bara": 1.01325,
  "dataset": "phreeqc-na-k-cl",
  "validationTarget": "AQUEOUS_ACTIVITY_COEFFICIENTS",
  "components": {
    "water": 55.508,
    "Na+": 0.5,
    "K+": 0.5,
    "Cl-": 1.0
  }
}
```

Python uses the same Java behavior through JPype; it does not reproduce the
qualification logic:

```python
from neqsim import jneqsim

payload = '{"analysis":"pitzerQualification","temperature_K":298.15,' \
    '"pressure_bara":1.01325,"dataset":"phreeqc-na-k-cl",' \
    '"validationTarget":"AQUEOUS_ACTIVITY_COEFFICIENTS",' \
    '"components":{"water":55.508,"Na+":0.5,"K+":0.5,"Cl-":1.0}}'
result_json = jneqsim.mcp.runners.ChemistryRunner.run(payload)
```

The declared evidence envelopes and source/license matrix are recorded in
[Pitzer parameter provenance](../thermo/pitzer_parameter_provenance.md). In
particular, the PHREEQC `H2Sg`/`(H2Sg)2` source topology is not aliased to
NeqSim `H2S`; it remains visibly incomplete.

### `electrolyteScale`

Davies activity-coefficient saturation indices for CaCO3, BaSO4, CaSO4, SrSO4.
Standards: NACE TM0374, NORSOK M-001.

| Field | Unit | Default |
|-------|------|---------|
| `temperature_C` | °C | 60 |
| `pressure_bara` | bara | 50 |
| `pH` | – | 6.5 |
| `pCO2_bar` | bar | 1.0 |
| `ca_mgL`, `ba_mgL`, `sr_mgL`, `mg_mgL`, `na_mgL`, `k_mgL`, `fe_mgL` | mg/L | 0 |
| `cl_mgL`, `so4_mgL`, `hco3_mgL`, `co3_mgL` | mg/L | 0 |

### `mechanisticCorrosion`

NORSOK M-506 base rate × Nesic mass-transfer (Sherwood correlation) × Langmuir
inhibitor reduction. Standards: NORSOK M-506, NACE SP0775.

| Field | Unit | Default |
|-------|------|---------|
| `temperature_C` | °C | 60 |
| `pressure_bara` | bara | 80 |
| `co2_mol`, `h2s_mol` | mol fraction | 0.05 / 0 |
| `pH` | – | 5.5 |
| `bicarb_mgL` | mg/L | 100 |
| `ionicStrength_molL` | mol/L | 0.5 |
| `velocity_ms` | m/s | 2.0 |
| `diameter_m` | m | 0.15 |
| `density_kgm3` | kg/m³ | 1000 |
| `viscosity_pas` | Pa·s | 1e-3 |
| `dose_mgL` | mg/L | 0 |
| `kAdsRef`, `dHads_kJmol`, `thetaMax`, `molarMass_gmol` | – | optional override of inhibitor isotherm |

### `langmuirInhibitor`

Adsorption isotherm and dose-for-efficiency lookup. Standards: NACE SP0775.

| Field | Unit | Default |
|-------|------|---------|
| `temperature_C` | °C | 60 |
| `dose_mgL` | mg/L | 50 |
| `targetEfficiency` | – | optional, returns `doseForTargetEfficiency_mgL` |
| `kAdsRef`, `dHads_kJmol`, `thetaMax`, `molarMass_gmol` | – | optional |

### `packedBedScavenger`

1D plug-flow H2S scavenger breakthrough simulator. Standards: NACE TM0169.

| Field | Unit | Default |
|-------|------|---------|
| `diameter_m`, `height_m` | m | 0.5 / 2.0 |
| `voidage` | – | 0.4 |
| `loading_mol_kg` | mol H2S / kg media | 5.0 |
| `bulkDensity_kgm3` | kg/m³ | 1100 |
| `stoichiometricRatio` | mol H2S / mol active site | 1.0 |
| `k_per_s` | 1/s | 5.0 |
| `cInlet_molm3` | mol/m³ | 1.0 |
| `flow_m3s` | m³/s | 0.005 |
| `nCells`, `nTimeSteps` | – | 50 / auto |
| `simTime_s` | s | 30 days |
| `breakthroughFraction` | – | 0.05 |

## See also

- [Chemistry overview](index.md)
- [MCP server contract](../../neqsim-mcp-server/MCP_CONTRACT.md)
