---
name: neqsim-vfp-lift-curves
description: "Generate deliverable VFPPROD and VFPINJ lift curve tables (vertical lift performance, tubing performance, THP and BHP tables) for Eclipse and OPM Flow from calibrated well, flowline and topside models with NeqSim VfpTableBuilder, TubingVlpProvider, ForwardVlpProvider and EclipseVFPExporter. USE WHEN: a simulator needs lift curves, a VFP table is built from tubing, flowline or full-system models and validated by IPR x VFP, or an existing deck shows U-shaped BHP, extrapolation or a dry-gas jump."
last_verified: "2026-10-22"
---

# VFP lift curves (VFPPROD / VFPINJ) for Eclipse and OPM Flow

One authoritative route from a calibrated NeqSim model to a deck-ready `VFPPROD` or `VFPINJ` keyword. Do not
hand-build a Python pipeline for this: `VfpTableBuilder` owns axes, evaluation, rate caps, sanity enforcement,
diagnostics and the deck text; you supply the physics as a `VlpProvider`.

## Choose the curve type first

| Question the simulator asks | Curve | Datum / THP axis meaning | Provider |
|---|---|---|---|
| Well delivers what at a wellhead pressure? (`WCONPROD` THP limit) | **Tubing-only VLP** | BHP at well datum; THP = wellhead pressure | `TubingVlpProvider` |
| Template or well delivers what into a flowline/riser to a host node? (`NETWORK`, `BRANPROP`) | **Flowline / inlet curve** | "BHP" = branch inlet pressure; THP = outlet node pressure | `ForwardVlpProvider` on a pipe chain |
| One table from reservoir datum to a fixed host or export pressure (no network option in the deck) | **Full-system curve** = tubing + flowline + topside | THP axis = fixed-node pressure (for example host separator or export); say so in the title | `ForwardVlpProvider` on tubing + flowline + topside |
| Injection well | `VFPINJ` | BHP for a rate and THP; gas or water | `VfpTableBuilder.injector(n, "GAS"/"WAT")` |

Rules: a tubing-only table is the default for wells. Use the full-system curve only when the deck cannot model
the network, and then document that the THP axis is not a wellhead pressure. A flowline table is a network branch,
never a well table.

## API call pattern (one call per table)

```java
// template: all components incl. water, EOS, mixing rule; surfaceGas / condensate: surface compositions
RecombinationFluidFactory wellStream = new RecombinationFluidFactory(template, surfaceGas, condensate)
    .setRateBasis("GAS").setWaterRatioType("WGR").setGasRatioType("OGR");
TubingVlpProvider vlp = new TubingVlpProvider(wellStream, md, tvd, innerDiameter)   // survey, wellhead first
    .setTemperatures(95.0, 40.0).setNumberOfSegments(6)
    .setCorrelation(TubingPerformance.CorrelationType.GRAY);
VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setDatumDepth(2500.0)
    .setRates(VfpTableBuilder.rateAxis(0.2e6, 1.0e6, 5, 1.2))
    .setOutletPressures(VfpTableBuilder.pressureAxis(40.0, 80.0, 4, 0.1, 0.1))
    .setWaterRatios(new double[] {1.0e-6, 1.0e-4}).setGasRatios(new double[] {1.0e-5, 1.0e-4})
    .setRateMonotoneMode(VfpTableBuilder.RateMonotoneMode.FULL).setRateCap(1.1e6, 200.0)
    .addProvenance("Gray tubing survey, calibrated on gauge data").build(vlp);
result.writeDeck(java.nio.file.Paths.get("vfp1.inc"));      // result.getWarnings() lists every repair
```

This example is exercised by `VfpTableBuilderTest.documentedGasWellExampleRuns`. Other builders:
`VfpTableBuilder.oilWell(n)` (OIL, WCT, GOR), `liquidWell(n)` (LIQ, WCT, GLR), `injector(n, phase)`.

Python (jpype, task notebooks): load classes through `neqsim_init(...)` and call the same chain; a custom model is a
callable that implements the single-method interface:

```python
from jpype import JImplements, JOverride
VlpProvider = ns.JClass("neqsim.process.fielddevelopment.reservoir.VlpProvider")

@JImplements(VlpProvider)
class FullSystemVlp:
    @JOverride
    def bottomHolePressure(self, rate, thp, wr, gr, alq):   # Sm3/d, bara, ratios, Sm3/d
        return my_chain_inlet_pressure(rate, thp, wr, gr, alq)   # NaN = infeasible
```

For a chain that only knows "outlet pressure from inlet pressure" (pipes, valves, a `ProcessSystem`), wrap it in
`ForwardVlpProvider(model)` and the inverse solve (unit slope, secant, bisection) is done for you.
`ReservoirCouplingExporter.setVlpProvider(...)` plugs the same provider into the legacy exporter; without it that
exporter uses a placeholder correlation and logs a warning. The JSON face is `neqsim.mcp.runners.VfpTableRunner`
(gas or oil well, tubing survey; not yet a registered MCP tool).

## Axis design

- **Rate**: start at 2-5 % of the plateau rate (never 0; the builder floors a zero rate for evaluation), go to at least
  `1.3 x` the highest rate the study can reach (`rateAxis` factor) and use 8-12 points; denser below the loading rate
  of a gas well. OPM Flow and Eclipse extrapolate linearly outside the table without a message.
- **THP / outlet pressure**: cover late life. Extend the lower end 20-30 % below the lowest pressure expected
  (`pressureAxis`), 5-8 points; BHP is almost linear in THP, so this axis needs fewer points than rate.
- **WGR / OGR / GOR / WCT**: the lean gas-condensate response is weak but nonlinear near zero. Put 3-4 points in the
  first decade (base, 5x, 25x the base) instead of one at zero; bracket the field's decline path in OGR as it
  evolves. Linear interpolation across a decade gap gives 1-4 bar error; check at off-grid points.
- **Exact zero ratios**: `Gray` on exactly dry gas (WGR = 0) once gave +15 bar against a trace of water because the
  correlation changed regime. `TubingPerformance` now reads gas and liquid by phase type, and the builder probes
  zero against 1e-6 and warns ("Dry-gas discontinuity"); still start the axis at a small positive value or use
  `setZeroRatioFloor(1e-6)`.
- **ALQ**: singleton `{0}` unless gas lift is modelled (`setAlqType("GRAT")`, axis in Sm3/d). Lift gas enters at
  the bottom of the modelled section in `RecombinationFluidFactory`; use a gas-lift model if injection depth matters.
- Cost: points = rates x THP x WGR x OGR x ALQ; each point is a few tubing solves. Use 5-10 segments per well, check
  a finer direct solve at 3 off-grid points, and parallelise by table (one JVM per table), not inside a provider.

## Correlation selection and calibration

- **Gas and gas-condensate wells: `GRAY`** (`TubingVlpProvider` default). Beggs-Brill over-predicts lean-gas
  tubing pressure drop by about 2x in near-vertical flow (holdup of the order of 2x the no-slip value); NeqSim now
  logs a warning for gas fraction above 0.97 and inclination above 30 degrees. Hagedorn-Brown, Hasan-Kabir and
  Duns-Ros currently address phases by index and can fail on 20+ component fluids; test before use.
- **Oil wells**: `HASAN_KABIR` or `BEGGS_BRILL`, validated against test points; calibrate roughness and a liquid
  holdup/friction multiplier on well tests, not on the VFP.
- **Deviated wells**: give a survey (`md`, `tvd`, interval ID) instead of chained instances. Inclination follows from
  dTVD/dMD per interval; temperature is linear in TVD between bottomhole and wellhead temperature.
- **Calibrate on gauges**: downhole gauge BHP plus wellhead pressure and rate (PDM well tests, historian). Fit one
  parameter per well, typically roughness or a friction multiplier, until BHP at the test rates matches within the
  gauge uncertainty. IPR parameters are not identifiable without BHP gauges; do not tune VLP and IPR on the same
  rate-pressure pair.
- **Temperature**: use measured wellhead flowing temperature; the VLP is weakly sensitive, but gas density and
  condensate dropout follow it.
- Fluids: use the same EOS and mixing rule as the process model. Trace-water TPflash can throw (aqueous repair does
  not converge at 1e-20 mol fraction); `RecombinationFluidFactory` keeps a 1e-12 floor and the providers return NaN
  for a failed point, which the builder reports or extrapolates (`FailurePolicy.EXTRAPOLATE_RATE`).

## Topside and flowline hydraulics for a full-system curve

- Model each element as `dP = K * mdot^2 / rho` (valves, chokes, orifices, fittings, scrubbers: flow and density
  dependent) plus pipe friction and static head from `PipeBeggsAndBrills` with real length, elevation and ID (STID,
  line list). Calibrate K once per element from historian dP, mass rate and density at steady windows, then add one
  smooth residual (linear in rate) for what the model leaves; keep the residual small and report it.
- Wrap the chain as a `ForwardPressureModel` (inlet pressure to outlet pressure) so the same code serves tubing,
  flowline and full-system curves.
- **Routing/compressor configuration**: each train alignment (which wells feed which train, mixed feed, cross-over)
  is its own table set; number tables per configuration and select with the deck's `WVFPPROD`/`NETWORK` input per
  period. For a mixed header use a NeqSim `Mixer` and `ThrottlingValve` so composition and temperature of the mixed
  feed drive the train model.
- **Compressor head limit = rate cap**: a smooth VFP cannot express "this train cannot take more". Compute the
  capacity as a function of suction pressure from the compressor chart (maximum speed, head and power limits, surge),
  pass it as `RateCap` and also give the simulator a group rate limit. The penalty slope (`setRateCap(cap, bar per
  100 % above cap)`) makes the table steep above the cap; the cap range is written into the deck header.
- **Capacity search pitfall**: with a charted compressor at a pressure ratio far from design, feasible net flow is a
  band, not a half-line: low flow can be below the minimum-speed head line. A bisection on net flow reads this as zero
  capacity. Scan a flow grid, keep the feasible set and take its upper end (a throttled-suction envelope is a valid
  conservative alternative); record `[min, max]` per suction pressure.

## Units, datum and deck conventions

- Inputs to `VfpTableBuilder`: Sm3/day of the table rate basis, bara, ratios in Sm3/Sm3, datum in m TVD. Output
  `setUnitSystem("METRIC")` or `"FIELD"`; the exporter converts rates (stb/d, Mscf/d for gas), psia, datum in ft and
  ratios (stb/Mscf for WGR and OGR in FIELD). Standard conditions must equal the deck's surface conditions.
- Keyword layout: `VFPPROD table datum 'FLO' 'WFR' 'GFR' 'THP' 'ALQ' 'UNITS' 'BHP' /`, then rate, THP, WFR, GFR, ALQ
  axes, then one record per THP, WFR, GFR, ALQ index (1-based) holding BHP for every rate.
- `WCONPROD` items: 10 = THP limit, 11 = VFP table number, 12 = ALQ. The tubing table datum must equal the well
  reference depth used for BHP limits.
- BHP above THP, strictly increasing axes and finite values are mandatory; the builder raises BHP to THP and
  enforces slope `>= 0.5` bar/bar in THP (`setMinThpSlope`) and records each repair.
- **OPM Flow**: a table that is U-shaped in rate (high BHP at low rate) makes the well flip between rate and THP
  control ("Oscillating behavior detected", steps shrink to 0.02 day). Use `RateMonotoneMode.FULL`, keep the raw
  curve for the minimum stable rate report. **Eclipse** is more tolerant; `ABOVE_MINIMUM` (default) keeps the
  loading side.
- A rate cap penalty is not a physical limit; also set group or well rate limits in the deck.
- The table has no arrival temperature; keep it in a side file per point for hydrate and cooldown hand-off.

## Validation checklist (do all)

1. `result.getWarnings()` empty or understood; `getRepairCount()` small, largest repair under 0.5 bar.
2. Round trip: `VfpDeckReader.parse(result.toDeckString())` reproduces the table (METRIC) and axes are strictly
   increasing.
3. Off-grid spot points (3 or more, one per axis and one combined): `result.interpolate(...)` against a direct
   provider solve within 0.3 bar; `isInsideAxes` for the study's operating envelope including late life.
4. BHP increases with THP (slope 0.9-1.3 bar/bar for gas wells) and with rate above the loading minimum; no
   negative or sub-THP values.
5. IPR x VFP: solve the intersection the way the simulator does with the field's IPR; compare with the full live
   model at 3-5 operating states (within 1 % of rate).
6. Live bracket for a full-system curve: the topside runs at the curve's inlet pressure and fails or saturates at
   1-2 bar below it.
7. Single-well run in the simulator (Flow or Eclipse) over the full pressure range: no extrapolation messages, no
   control flipping, rates match the IPR x VFP intersection.
8. Provenance in the deck header: model, correlation, calibration data and date, datum, rate-cap decisions.

## Known traps

- `ReservoirCouplingExporter.generateVfpProd` without a provider is a placeholder (hydrostatic plus a guessed
  friction); never deliver it. `MultiScenarioVFPGenerator` and `LiftCurveGenerator` give diagnostic tables in the
  simulation's rate units (kg/hr), which the exporter rejects.
- `EclipseVFPExporter` is a strict formatter: it does not compute BHP, fill gaps or extrapolate.
- `SimpleReservoir` silently stops producing below its `lowPressureLimit` (default 50 bara); NeqSim now warns once
  and `isLowPressureLimitReached()` reports it. Lower the limit for depleted gas tanks.
- After a flash call `initProperties()` before reading densities: a fluid that is only `init(0)` returns zero density
  and the correlation divides by zero.
- Choked or undersized tubing: no BHP below 1500 bara delivers the THP; `TubingPerformance.generateVLPCurve` and the
  provider return NaN. Fix the diameter or trim the rate axis; do not accept a repaired table silently.
- Gas-lift or rate-cap tables are only as good as the group limits in the deck.
- Beggs-Brill in `PipeBeggsAndBrills` for steep lean-gas risers: check against Gray or measured data (warning logged).
- **Python `RecombinationFluidFactory`**: call `init(0)` on the template and on every surface gas, oil and water source system before building the factory. Without it the
  molar compositions read as zero and every point returns NaN (`PhasePrEos` compressibility NaN).
- **Oil wells with a CO2-rich fluid near the bubble point**: `HASAN_KABIR` returned NaN for flowing pressures near Pb (single-phase liquid side) while `BEGGS_BRILL` solved
  everywhere and `GRAY` was inconsistent at low THP; compare the correlations at 3-4 points before building (spread of 30-50 bar seen) and check the first rate point of every
  THP/ratio corner, because `FailurePolicy.EXTRAPOLATE_RATE` needs two valid points per rate curve. A thin wrapper that retries a NaN point with a +/-0.5 bar shift is acceptable if counted and reported.

## Related

- `neqsim-integrated-production-and-lifecycle`, `neqsim-subsea-and-wells` (VLP/IPR chain), `neqsim-production-optimization`
  (network and gas lift), `neqsim-capacity-and-utilization-analysis`, `neqsim-rotating-equipment-design` (compressor
  charts and capacity), `neqsim-near-well-and-injectivity` (community: inflow from rock, deck side, Flow runs),
  `neqsim-model-calibration-and-data-reconciliation`.
- Agent: `vfp-lift-curve-agent` (community) orchestrates the full workflow.
