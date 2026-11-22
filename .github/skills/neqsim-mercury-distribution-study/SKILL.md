---
name: neqsim-mercury-distribution-study
description: "Run a mercury distribution study on a NeqSim process: convert the E300 study fluid to UMR-PRU-MC with MercuryFluidConverter, rebuild the process, hold back free mercury in separators and report every stream with MercuryProcessAnalyzer. USE WHEN: asked where mercury ends up in gas, oil and water, the mercury content of products, or where free liquid or solid mercury forms in separation, recompression, chilling or dew point control."
last_verified: "2026-11-20"
---

# Mercury distribution study on a process

Use `@mercury-distribution`. Read the
[study guide](../../../docs/process/mercury-distribution-study.md) for the model, the
measured agreement and the limits before running a study. The classes are
`neqsim.thermo.util.mercury.MercuryFluidConverter`,
`neqsim.thermo.util.mercury.MercuryPhaseEquilibrium`,
`neqsim.process.util.mercury.MercuryProcessAnalyzer` and
`Separator.setFreeMercuryAccumulation`.

## When to use

- Mercury split between sales gas, condensate or oil, and produced water.
- Mercury content of a product against a specification.
- Where free mercury forms: after chilling, after compression and cooling, after a
  pressure reduction. Liquid above -38.83 C, solid below.
- How much mercury a separator, cold box feed or mercury removal bed sees.

Hand off to `@thermo-fluid` for a single flash, and to `@production-chemistry` for
ionic or organic mercury, which this model does not describe.

## Inputs

| Input | Note |
|-------|------|
| Study fluid | E300 file or NeqSim fluid; record equation of state and pseudo-components |
| Process | A function that builds the process from a fluid, or a JSON definition |
| Mercury content | Value, unit, and the stream and phase it was measured in |
| Water | Amount in the feed; the mercury content of water needs an aqueous phase |

Ask for the measurement basis when only a number is given. A content in gas from a
separator is not the content of the well stream.

No measurement yet (prospect, or a discovery that was not sampled for mercury): get the
content and its range from `neqsim-reservoir-mercury-estimation`, which gives a best estimate
with P10/P50/P90 and the mineral-equilibrium limits (`ReservoirMercuryEstimator`), and run the
distribution for the low and the high end of the range.

## Steps

1. **Base case.** Read the E300 fluid, build and run the process as the study has it.
   Record the mass flow of the main streams.
2. **Convert the fluid.** `MercuryFluidConverter.toUmrPruMc(fluid, content, unit)`.
   The content is for the whole fluid: `"mole fraction"`, `"ppb"`, `"ug/Sm3"`,
   `"ug/Nm3"` or `"ug/kg"`. Add water before converting
   (`EclipseFluidReadWrite.read(file, True)` then `addComponent("water", moles)`).
3. **Rebuild the process** with the converted fluid. Never replace the fluid of the
   feed streams in a built process; recycles and other units keep copies of the first
   fluid.
4. **Hold back free mercury.** `MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, True)`
   before the run, for a `ProcessSystem` or a `ProcessModel`.
5. **Run and analyze.** `report = MercuryProcessAnalyzer.analyze(process)`.
6. **Match the measurement.** Read the calculated content of the measured phase,
   scale the feed content by measured over calculated, convert, rebuild and run
   again. Confirm the match.
7. **Compare the two models.** Report the difference in the main stream flows
   between the E300 run and the UMR-PRU-MC run.
8. **Report** per stream and per unit, and the mercury balance.

```python
E300 = ns.JClass("neqsim.thermo.util.readwrite.EclipseFluidReadWrite")
Converter = ns.JClass("neqsim.thermo.util.mercury.MercuryFluidConverter")
Analyzer = ns.JClass("neqsim.process.util.mercury.MercuryProcessAnalyzer")

base_fluid = E300.read(str(e300_path))
base = build_process(base_fluid)
base.run()

fluid = Converter.toUmrPruMc(base_fluid, 100.0, "ug/Sm3")
plant = build_process(fluid)
Analyzer.setFreeMercuryAccumulation(plant, True)
plant.run()
report = Analyzer.analyze(plant)

for s in report.getStreams():
    print(s.getLabel(), s.getStatus(), s.getMercuryFlowRate("g/day"),
          s.getSaturationRatio(), s.getFreeMercuryState(),
          s.getMercuryConcentration("gas", "ug/Sm3"),
          s.getMercuryConcentration("oil", "ug/kg"))
for u in report.getUnits():
    print(u.getName(), u.getRetainedMercuryFlowRate("g/day"),
          u.getFreeMercuryAccumulationRate("g/day"), u.getFreeMercuryState())
print(report.getFeedMercuryFlowRate("g/day"), report.getProductMercuryFlowRate("g/day"),
      report.getFreeMercuryAccumulationRate("g/day"), report.getMercuryBalanceError("g/day"))
```

A JSON process uses `"model": "UMRPRU"` with `"e300FilePath"` and
`"mercuryContent": [value, "unit"]` in the fluid, and
`"freeMercuryAccumulation": true` in the separator properties.

## Reading the report

- `getSaturationRatio()` below one: all mercury dissolved. Above one: the stream
  carries free mercury; `getFreeMercuryState()` gives `"liquid"` or `"solid"` and
  `getFreeMercuryFlowRate(unit)` the amount.
- `getMercuryConcentration(phase, unit)` is the dissolved content of the gas, oil or
  aqueous phase at the stream temperature and pressure. It is NaN when the stream
  has no such phase.
- Stream labels are the stream name, with the unit name in front when several
  streams share a name. `report.getStream("cold separator.gasOutStream")` finds a
  stream by the unit it leaves and its name in both cases.
- A unit retains mercury when it holds back free mercury (separators), takes it up
  (`MercuryRemovalBed`) or sits in a recycle that is not converged.
- Flow units: `g/day`, `g/hr`, `g/yr`, `kg/hr`, `kg/day`, `kg/yr`, `mole/sec` and
  more. Content units: `ug/Sm3`, `ug/Nm3`, `ug/kg`, `ppb`.

## Checks before reporting

- Every stream has status `ok`, `no flow` or `no mercury`. Explain each `failed`.
- `getMercuryBalanceError` is zero within rounding. Feed mercury equals product
  mercury plus mercury retained in units.
- Main stream mass flows of the UMR-PRU-MC run are within a few percent of the E300
  run. State the differences.
- Streams after a separator with the option on have a saturation ratio at or below
  one.
- The calculated content matches the measurement it was scaled to.

## Common mistakes

- Using `setMixingRule("classic")` with `SystemUMRPRUMCEos`. The mercury parameters
  are UNIFAC group parameters and need `("HV", "UNIFAC_UMRPRU")`.
- Reading the mercury content of the whole fluid as the content of its gas.
- Reading the phases of an ordinary `TPflash` above saturation. It has no separate
  mercury phase. Use the report or `MercuryPhaseEquilibrium`.
- Running without free mercury accumulation and reporting the mercury downstream
  of a cold separator. It then carries more mercury than it dissolves.
- Reporting volume flows or liquid densities from the UMR-PRU-MC run. Its oil
  density was 3 to 11 % above the E300 fluid in the checked case; take them from
  the E300 run.

## Limits

- Elemental mercury only. No ionic or organic mercury, mercury sulphide, or uptake
  on steel walls.
- Not valid with glycols: results through a TEG or MEG contactor are not usable.
- Free mercury is handled in separators that use the `Separator` or
  `ThreePhaseSeparator` steady-state calculation, not in columns, absorbers or
  transient runs.
- At most three fluid phases.
- Agreement with the mercury calculator: about 1 % for fluids to nC20 and within
  5 % for a heavy gas-condensate, measured 2026-11-19; see
  [Mercury thermodynamics](../../../docs/thermo/mercury_thermodynamics.md).
