---
title: VFPPROD table-driven pipeline
description: Use qualified gas pipeline VFP tables with explicit standard-volume definitions, pressure inversion and outlet temperature.
keywords: "VfpPipeline, VfpTable, VfpTableArguments, StandardConditionsFlash, VFPPROD, OLGA, GOR, OGR"
---

# VFPPROD table-driven pipeline

`VfpPipeline` in `neqsim.process.equipment.pipeline` is a two-port process unit
whose hydraulics come entirely from a supplied VFPPROD table. It can connect an
upstream process to a downstream process, including through ordinary mixers.
No pipe correlation or pressure-drop tuning is added to the approved table.

The accepted representation is **VFPPROD, METRIC, GAS, WGR, GOR or OGR, THP,
BHP**, with a singleton ALQ axis. See the [VFP export contract](../optimization/vfp-export-contract)
for header order, datum meaning and phase-volume units. BHP means the pipeline
inlet pressure at the table provider's datum; THP means arrival pressure. The
caller must establish that this mapping is appropriate for the delivered table.

## Configure and run

1. Load a table using `VfpTable.fromFile(path)` (UTF-8) or `fromString(deckText)`.
2. Connect an inlet with `new VfpPipeline(name, inlet)` or `setInletStream(inlet)`.
3. Supply it with `setVfpTable(table)`.
4. Set the required thermal boundary with `setOutletTemperature(value, unit)`.
5. Set the required surface-volume definition with `setTableArguments(provider)`.
6. Add the inlet and pipeline to the ordinary `ProcessSystem`, then run it.

Executable Java examples, including a pipeline/mixer/export chain and a synthetic
hand-checkable table, are in
[`VfpPipelineTest`](https://github.com/equinor/neqsim/blob/master/src/test/java/neqsim/process/equipment/pipeline/VfpPipelineTest.java)
and
[`VfpTableTest`](https://github.com/equinor/neqsim/blob/master/src/test/java/neqsim/process/equipment/pipeline/VfpTableTest.java).
The same public classes and setters can be called from Python via JPype; a custom
provider implements the `VfpTableArguments` interface.

| Mode | Input | Result |
| --- | --- | --- |
| Forward (default, or `setForwardMode()`) | Inlet stream pressure | Arrival pressure from THP inversion |
| Backward (`setOutletPressure(value, unit)`) | Fixed arrival pressure | `getRequiredInletPressure(unit)` from direct BHP lookup |

Backward mode leaves the connected inlet pressure unchanged. The caller couples the
reported required pressure to field deliverability or a network/adjuster. It is not
a simultaneous network solution. A mixer takes the lowest connected pressure; it
does not balance branch flows to a common tee pressure.

`setNumberOfParallelLines(N)` divides the provider's total gas rate by positive
integer N; all lines have the same WGR/GOR and the outlet retains total flow.
The outlet is a clone of the inlet fluid at the computed arrival pressure and
specified temperature, with a TP flash. Composition and total molar/mass flow
are conserved. VFP tables do not determine heat duty, linepack, transient response,
phase slip, geometry, temperature profile or mechanical design.

## Explicit surface-volume definition

`VfpTableArguments.calculate(stream)` returns exactly three values: total gas
rate in **Sm3/day**, WGR in **Sm3 water/Sm3 gas**, and GOR in **Sm3 gas/Sm3 oil**.
Providers must preserve the inlet and match the table's separation and metering
basis. There is no default provider or default reference temperature/pressure.

`StandardConditionsFlash(T, Tunit, P, Punit)` clones the fluid and performs one
multiphase TP flash at the supplied reference conditions. It sums gas, aqueous
and oil phase volumes there, using phase mass flow divided by phase density. Its rate is the gas-phase volume per day, not
the ideal-gas standard-volume conversion of all inlet moles. At the same T/P this
matches the flashed gas's actual `m3/day` volume. NeqSim's stream `Sm3/day` getter
instead uses ideal molar volume at global standard conditions; these definitions
can differ by gas compressibility and by phase separation. Verify which convention
the table provider used. A staged separator route or ideal-gas metering definition
requires a custom provider.

Dry gas gives WGR zero and GOR positive infinity. `VfpPipeline` converts the
provider's GOR to OGR only for an OGR table, giving OGR zero for oil-free gas.
An infinite GOR cannot be interpolated on a finite GOR axis and is rejected;
choose a matching OGR table or explicitly supply the provider's approved dry-gas
GOR convention. No large GOR, range clamp or ratio-definition correction is invented.
A file whose numerical columns were mislabeled cannot be detected from syntax
alone: verify the provider's definitions and data provenance.

`getTableArguments()` returns a defensive copy of the last successful run's
**per-line** gas rate, WGR and canonical GOR. `getTableGasRatio()` reports the
actual GOR/OGR coordinate used. The ALQ coordinate is `table.getAlq()`.
Diagnostics are last-successful-run results, not live calculations; after zero-flow
bypass the arguments are null and gas ratio is NaN.

## Table interpolation and rejection rules

Direct `table.inletPressure(rate, thp, wgr, gasRatio, alq)` returns bara. Rate
is Sm3/day per line, THP is bara, and gasRatio is in the table's declared GOR or
OGR basis (unlike the provider interface, there is no implicit reciprocal here).
`outletPressure(rate, bhp, wgr, gasRatio, alq)` solves for THP in bara.
Axis getters return copies; `getRateAxis` supports Sm3/day and MSm3/day,
`getThpAxis` supports bara, and ratio axes use Sm3/Sm3.

Interpolation is linear in each axis, including interactions between axes.
Inversion checks the interpolated BHP at every THP knot for strict monotonicity,
then solves the enclosing linear segment directly. Flat or nonmonotonic curves
fail explicitly; no iterative tolerance or initial guess is needed. A singleton
THP axis is usable only when the target equals its stored interpolated BHP.

Nonfinite, negative, unsorted or duplicate axes, nonpositive pressure, missing,
extra or duplicate pressure records, fractional/out-of-range record indices,
unsupported definitions and multiple ALQ values fail with an exception. Explicit
numeric repeats such as `2*100` and Fortran D exponents are accepted; defaulted
numeric cells such as `2*` are rejected. The first keyword in an include/deck is
read; later deck keywords are not interpreted as pressure rows.

Every lookup argument must lie inside its axis, including singleton axes. There
is no extrapolation or clamping. A BHP target outside the inversion range fails.
Zero flow fails by default; `setAllowZeroFlow(true)` explicitly bypasses table
lookup and copies inlet pressure, including in backward mode. The outlet still
uses the specified temperature. Injection tables, FIELD units and other rate or
ratio definitions are outside the current supported scope.

## Validation evidence and limits

The focused tests cover a hand-calculated midpoint to 1e-9 bar, all four
interpolated axes, inversion endpoints/interior points, writer round-trip,
forward/backward consistency, parallel lines, setup failures, malformed records,
wet/dry reference flashes, stream ownership, conservation, Java serialization
and ProcessSystem/mixer integration. These are synthetic software and analytical
interpolation checks. They do not independently qualify an OLGA/Prosper table,
a particular fluid's phase equilibrium, or a field's hydraulic model.
