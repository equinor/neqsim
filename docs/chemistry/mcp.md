---
title: "MCP Chemistry Tool Reference"
description: "JSON schema reference for the runChemistry MCP tool exposed by the NeqSim MCP server. Covers Pitzer qualification, activity-consistent electrolyte scale equilibrium, screening-scale prediction, corrosion, inhibitor dosing, and H2S scavenger breakthrough."
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


### `electrolyteScaleEquilibrium`

Thin JSON/MCP adapter over the authoritative Java
`ThermodynamicOperations.precipitateScale(String)` operation. It uses the
selected Pitzer GE or electrolyte-CPA aqueous activity model, retains their
distinct parameter semantics, and returns the pure-solid material ledger rather
than inserting a NeqSim solid phase.

| Field | Unit | Required / values |
|-------|------|-------------------|
| `temperature_K` | K | required, finite and positive |
| `pressure_bara` | bara | required, finite and positive |
| `components` | mol | required electroneutral object; positive water, finite non-negative amounts |
| `model` | – | `pitzer` (default) or `electrolyte-cpa` |
| `dataset` | – | for Pitzer: `phreeqc-ca-mg-cl-so4` (default) or `phreeqc-catalog`; not applicable to electrolyte CPA |
| `mineral` | – | required pure COMPSALT name, for example `CaSO4_A` |

The response reports precipitated mol and g, initial/final saturation ratio,
pure-phase complementarity violation, maximum ion-ledger residual, aqueous
charge/normalization evidence, dataset identity and qualification boundary.
A successful response requires complementarity at most `1e-6`, ion-ledger
residual at most `1e-10 mol`, charge residual at most
`1e-10 mol/kg water`, and a finite, non-negative normalized aqueous phase.
Inputs that mix a Pitzer dataset selector into electrolyte CPA fail closed.

```json
{
  "analysis": "electrolyteScaleEquilibrium",
  "model": "pitzer",
  "dataset": "phreeqc-ca-mg-cl-so4",
  "temperature_K": 298.15,
  "pressure_bara": 1.01325,
  "mineral": "CaSO4_A",
  "components": {
    "water": 55.508,
    "Na+": 1.0,
    "Ca++": 0.2,
    "Mg++": 0.0,
    "Cl-": 1.0,
    "SO4--": 0.2
  }
}
```

The adapter is **design-support**, not a new parameter qualification. It sets
`publicationReady: false` until an exact mixed-brine mineral evidence envelope
is registered for the requested state. Pitzer coefficients are never reused as
reaction constants, mineral log K values, SIT/eNRTL terms, or electrolyte-EOS
parameters. Multi-mineral competition, solid solutions, kinetics, deposition
and inhibitor physics remain outside this operation.

### `electrolyteMultiScaleEquilibrium`

Thin JSON/MCP adapter over the authoritative Java
`ThermodynamicOperations.precipitateScales(String...)` operation. Unlike
`multiMineralScale`, this operation solves shared-ion competition using the
selected Pitzer GE or electrolyte-CPA aqueous activities. Mineral names are
sorted internally, so caller order does not change the coupled result.

| Field | Unit | Required / values |
|-------|------|-------------------|
| `temperature_K` | K | required, finite and positive |
| `pressure_bara` | bara | required, finite and positive |
| `components` | mol | required electroneutral object; positive water, finite non-negative amounts |
| `model` | – | `pitzer` (default) or `electrolyte-cpa` |
| `dataset` | – | for Pitzer: `phreeqc-catalog` (default) or `phreeqc-ca-mg-cl-so4`; not applicable to electrolyte CPA |
| `minerals` | – | required non-empty array of unique pure COMPSALT names |

```json
{
  "analysis": "electrolyteMultiScaleEquilibrium",
  "model": "pitzer",
  "dataset": "phreeqc-catalog",
  "temperature_K": 298.15,
  "pressure_bara": 1.01325,
  "minerals": ["CaSO4_A", "CaSO4_G"],
  "components": {
    "water": 55.508,
    "Na+": 1.0,
    "Ca++": 0.2,
    "Mg++": 0.15,
    "Cl-": 1.3,
    "SO4--": 0.2
  }
}
```

The response contains a deterministic per-mineral solid ledger, active-set
update count, total COMPSALT ion-formula mass, maximum complementarity and
component-ledger residuals, aqueous electroneutrality and phase-state evidence,
and the model/dataset qualification boundary. Passing numerical gates require
maximum complementarity at most `1e-6 log10(SR)`, component-ledger residual at
most `1e-10 mol`, aqueous charge at most `1e-10 mol/kg water`, and a finite,
non-negative normalized aqueous phase.

The result is **design-support** and reports `publicationReady: false` until an
independent competitive mixed-brine/mineral validation envelope is registered.
When both `CaSO4_A` and `CaSO4_G` are requested, the response also contains
`calciumSulfatePhaseBoundaryQualification`. This nested view reuses
`ThermodynamicOperations.qualifyCalciumSulfatePhaseBoundary()` and reports the
CC BY 4.0 Voigt–Freyer pure-water and NaCl/water-activity envelopes, exact
COMPSALT predictions, source and primary-lineage DOIs, pressure scope,
limitations, and a deterministic fail-closed publication decision. It does not
fit or replace any mineral or aqueous-model parameter.

COMPSALT masses represent ion formula units. Encoded crystallization water is
included: gypsum `CaSO4_G` carries two waters per formula unit in saturation,
material balance, dissolution, and hydrated mass. Solid solutions,
precipitation kinetics, deposition and inhibitor physics are not included.

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
