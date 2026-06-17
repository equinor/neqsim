---
title: "HAZOP Worksheet (IEC 61882)"
description: "Structured HAZOP study using HAZOPTemplate with IEC 61882 guidewords, process parameters, deviation rows, default grids, and text reports for review records."
---

# HAZOP Worksheet (IEC 61882)

`neqsim.process.safety.hazid.HAZOPTemplate` implements a study worksheet
following IEC 61882 (2016) — the international standard for Hazard and
Operability studies.

## Guidewords And Parameters

The seven IEC 61882 guidewords are exposed as `HAZOPTemplate.GuideWord` values:

| Guideword | Meaning |
|-----------|---------|
| `NO` | Complete absence of intent |
| `MORE` | Quantitative increase |
| `LESS` | Quantitative decrease |
| `AS_WELL_AS` | Qualitative addition |
| `PART_OF` | Qualitative reduction |
| `REVERSE` | Logical opposite |
| `OTHER_THAN` | Complete substitution |

Standard process parameters are exposed as `HAZOPTemplate.Parameter` values:

| Parameter | Typical use |
|-----------|-------------|
| `FLOW` | No, more, less, or reverse flow |
| `PRESSURE` | Overpressure or low-pressure deviations |
| `TEMPERATURE` | High or low temperature deviations |
| `LEVEL` | Separator, vessel, or tank level deviations |
| `COMPOSITION` | Contamination, wrong phase, or wrong mixture |
| `REACTION` | Reaction rate or completion deviations |

## Code pattern

```java
import neqsim.process.safety.hazid.HAZOPTemplate;

HAZOPTemplate hz = new HAZOPTemplate("Node 3 - HP separator inlet",
    "Route HP separator inlet flow within design pressure and liquid handling limits");

hz.addDeviation(HAZOPTemplate.GuideWord.MORE, HAZOPTemplate.Parameter.FLOW,
    "Upstream pump runaway",
    "Liquid carry-over to flare KO drum",
    "FT-101 high-flow alarm; BDV on overpressure",
    "Add HIPPS interlock");

hz.addDeviation(HAZOPTemplate.GuideWord.LESS, HAZOPTemplate.Parameter.LEVEL,
    "LV-201 stuck open",
    "Gas blow-by to LP system, possible overpressure",
    "LSL-201 alarm; PSV-301 on LP separator",
    "Verify PSV sizing for gas blow-by");

String report = hz.report();
```

Use `generateGrid(...)` when a facilitator wants an empty worksheet grid for a
node before filling causes, consequences, safeguards, and recommendations:

```java
HAZOPTemplate grid = new HAZOPTemplate("Node 4 - compressor discharge",
    "Export compressed gas without exceeding downstream design pressure");
grid.generateGrid(HAZOPTemplate.Parameter.FLOW, HAZOPTemplate.Parameter.PRESSURE,
    HAZOPTemplate.Parameter.TEMPERATURE);
```

For automated studies connected to STID extraction and NeqSim process
simulation, use the MCP `runHAZOP` runner documented in
[Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md).

## See also

- [FMEA Worksheet](FMEA.md)
- [Event and Fault Trees](event_fault_trees.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
