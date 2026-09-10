---
title: VFP export axes, units and pressure contract
description: Supported OPM/Eclipse supplied-BHP export, unit conversion and process-screening boundaries.
---

# VFP export contract

`EclipseVFPExporter` formats **supplied flowing bottomhole pressure at a specified well
datum**. It does not solve a well, infer BHP from a process outlet, convert mass flow to
phase volume, or manufacture pressures for failed operating points.

| Task | API | Meaning |
|---|---|---|
| Format supplied well pressures | `EclipseVFPExporter` | Validated deck structure and unit conversion; physical BHP qualification belongs to the caller |
| Calculate process pressure requirements | `LiftCurveGenerator.generateTable()` and `MultiScenarioVFPGenerator.generateVFPTable()` | Required process inlet pressure for configured outlet conditions; a generic pipeline/facility result is not automatically well BHP |
| Screen fixed-composition capacity | `ProcessOptimizationEngine.generateCapacityScreening()` | Maximum mass throughput at pressure/temperature combinations; no composition recombination or BHP |
| Inspect process capacity/lift results | `FlowRateOptimizer` table `toJson()`, `toCsv()` where available, or `toFormattedString()` | Process inlet/outlet pressures, mass flow, power and feasibility |

A qualified well VFP calculation must separately establish the well geometry and datum,
hydrostatic and frictional pressure losses, thermal boundary conditions, fluid composition
and recombination at each water/gas ratio, lift-gas injection, the standard phase-volume
rate basis, solver convergence, constraints, and an independent hydraulic benchmark.
The exporter cannot certify these conditions. Generic process generators remain screening
tools until the caller establishes that complete well-model contract.

## Supported deck representation

The production format follows the public OPM implementation of the Eclipse `VFPPROD`
keyword. The header has nine positions:

`table number, datum depth, rate type, water ratio type, gas ratio type, THP, ALQ type, units, BHP`.

The five axis records are flow, THP, water ratio, gas ratio and ALQ. Each pressure row
starts with **one-based THP, water-ratio, gas-ratio and ALQ indices**, followed by one BHP
for each flow-axis value. The Java input array has a different order:
`[flow][THP][water ratio][gas ratio][ALQ]`. Every supplied cell is written exactly once.

| Definition | Supported values |
|---|---|
| Production rate | `OIL`, `LIQ` (oil + water), `GAS` |
| Water ratio | `WCT` (water/liquid), `WOR` (water/oil), `WGR` (water/gas) |
| Gas ratio | `GOR` (gas/oil), `GLR` (gas/liquid), `OGR` (oil/gas) |
| Artificial lift | Empty type with a singleton zero axis, or `GRAT` with supplied gas-lift rates |
| Output units | `METRIC`, `FIELD`; must match the surrounding OPM deck |
| Injection | `VFPINJ`, rate type `OIL`, `WAT` or `GAS`, singleton zero water/gas/ALQ axes |

`VFPINJ` writes a six-position header (table, datum, rate, THP, units, BHP), flow and THP
axes, then rows prefixed by the one-based THP index. Nonzero or multiple composition/lift
axes are rejected for injection rather than discarded. No `VFPEXP` export-system dialect
is supported by `EclipseVFPExporter`.

## Input and output units

The default input units are **Sm3/day and bara**, regardless of output unit system.
`setInputUnits(rateUnit, pressureUnit)` accepts `Sm3/day`, `Sm3/hr` or `Sm3/s`, and
`bara`, `Pa` or `psia`. It applies to flow, THP and BHP. The other input conventions
remain metres TVD for datum, Sm3/Sm3 for ratios, and Sm3/day for `GRAT`.

Standard volumes must already use the surface reference conditions of the reservoir
model. A geometric unit conversion does not change the reference temperature or pressure.

| Quantity | METRIC output | FIELD output | FIELD conversion from canonical metric input |
|---|---|---|---|
| Datum | m | ft | m / 0.3048 |
| Absolute THP/BHP | bara | psia | bara × 100000 / 6894.757293168 |
| OIL/LIQ/WAT rate | Sm3/day | STB/day | Sm3/day / 0.158987294928 |
| GAS rate, GRAT | Sm3/day | Mscf/day | Sm3/day / 28.316846592 |
| GOR/GLR | Sm3/Sm3 | Mscf/STB | ratio × 0.158987294928 / 28.316846592 |
| WGR/OGR | Sm3/Sm3 | STB/Mscf | ratio × 28.316846592 / 0.158987294928 |
| WCT/WOR | volume fraction/ratio | volume fraction/ratio | unchanged |

In FIELD VFP tables, **GOR uses Mscf/STB**, not scf/STB. Multiplying by 1000 would
misstate the composition axis. Mass rates (`kg/hr`), actual volume rates, gauge pressures,
`SI` as an output deck keyword and unsupported lift definitions fail explicitly.

## Validation and infeasible points

All axes must be nonempty, finite, nonnegative and strictly increasing; THP must be
positive. WCT is bounded by 0 and 1. Omitted composition and ALQ axes default to singleton
zero, but an explicitly empty array is invalid. Every BHP dimension must match its axis,
and all cells must contain finite positive absolute pressure.

`NaN`, infinity, zero BHP, ragged or transposed arrays, missing cells and unsupported
definitions are rejected before an export opens its destination. Invalid data never become
`1*`, copied neighbors or empirical fallback pressures. Infeasible results can be retained
in diagnostic tables; select a fully feasible grid before requesting a deck.

## Migration from legacy methods

- `EclipseVFPExporter.setLiftCurveData()` and `exportVFPEXP()` now throw
  `UnsupportedOperationException`. Capacity maxima cannot be transformed into well BHP.
- `FlowRateOptimizer.ProcessCapacityTable.toEclipseFormat()` and
  `ProcessLiftCurveTable.toEclipseFormat()` now throw. Use their process diagnostic outputs.
- `ProcessOptimizationEngine.generateLiftCurve(P,T,WC,GOR)` is deprecated. Only the legacy
  singleton zero placeholders are accepted; nonzero or multiple composition entries fail
  before changing the feed. Use `generateCapacityScreening(P,T)` for fixed composition.
- The two-argument screening method uses a minimum outlet pressure of 1 bara and a search
  range of 0–1,000,000 kg/hr. Its five-argument overload accepts explicit outlet pressure
  and mass-flow bounds. Temperature is applied to the feed, composition labels are `NaN`,
  and infeasible samples retain `NaN` flow. Screening inherits the engine's 1% outlet-pressure
  acceptance tolerance and does not establish a global optimum.
- `LiftCurveTable.toEclipseFormat()` remains a deprecated alias for `toDiagnosticTable()`.
  `PressureBoundaryOptimizer.LiftCurveTable.toEclipseFormat()` and
  `MultiScenarioVFPGenerator.toVFPEXPString()` also remain deprecated diagnostic aliases;
  their text contains no reservoir deck keywords or BHP claims. Use `toDiagnosticTable()`
  and `toDiagnosticString()` respectively. The multi-scenario legacy file writer writes
  diagnostic text, so use a `.txt` filename, not a reservoir include file.

The executable [supplied-BHP example](PRACTICAL_EXAMPLES.md#eclipse-vfp-table-generation)
and [exporter API example](OPTIMIZER_PLUGIN_ARCHITECTURE.md#eclipse-vfp-export) illustrate
the supported setup. Their synthetic pressures demonstrate serialization, not well physics.

## Independent validation

`EclipseVFPExporterContractTest` parses slash-delimited records independently of the
exporter and compares every axis/index/value with hand-authored METRIC, FIELD and injection
fixtures. It also checks unit factors separately, input-unit conversion, nonzero composition
and multiple lift values, invalid data, precision, locale and destination-file preservation.
`CapacityScreeningSemanticsTest` checks fixed composition, applied temperatures, pressure
feasibility and rejection of process-to-BHP mappings.

This is deck-contract validation, not execution in Eclipse or OPM Flow. The fixture
[provenance and sources](https://github.com/equinor/neqsim/tree/master/src/test/resources/optimizer/vfp)
record the public OPM keyword schema, parser and unit definitions. Before a reservoir study,
load the generated include in the target simulator and validate the physical well model.

Primary references: [OPM VFPPROD schema](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/share/keywords/000_Eclipse100/V/VFPPROD),
[VFPProdTable parser](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/Schedule/VFPProdTable.cpp),
[OPM unit definitions](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/Units/Units.hpp).
