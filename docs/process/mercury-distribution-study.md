---
title: "Mercury distribution studies in process simulation"
description: "Track elemental mercury through a NeqSim process with UMR-PRU-MC, starting from an E300 fluid, and find where free liquid or solid mercury forms."
---

# Mercury distribution studies in process simulation

Use this guide to find where the mercury of a well stream ends up in a process plant: how much goes with the
gas, the oil and the water, what the mercury content of each product is, and where mercury comes out as a
separate liquid or solid phase.

The study uses three parts:

| Part | Class | What it does |
|------|-------|--------------|
| Fluid | `MercuryFluidConverter` | Converts the study fluid, for example from an E300 file, to UMR-PRU-MC and gives it a mercury content |
| Process | `Separator.setFreeMercuryAccumulation` | Makes the separators hold back mercury that the fluid phases cannot dissolve |
| Report | `MercuryProcessAnalyzer` | Reports mercury in every stream and unit operation, and the mercury balance |

The thermodynamic model, its parameters and its agreement with the mercury calculator are described in
[Mercury thermodynamics](../thermo/mercury_thermodynamics.md).

## The fluid

Mercury is distributed with `SystemUMRPRUMCEos` and the mixing rule `("HV", "UNIFAC_UMRPRU")`. A process
study normally has a characterized fluid in an E300 file, read as a Peng-Robinson or SRK fluid. That model
has no mercury parameters, so the fluid is converted:

```java
SystemInterface e300Fluid = EclipseFluidReadWrite.read("fluid1.e300");
SystemInterface fluid = MercuryFluidConverter.toUmrPruMc(e300Fluid, 100.0, "ug/Sm3");
```

The conversion keeps the components and their amounts, temperature and pressure:

- Components from the NeqSim database are added by name and use the UMR-PRU-MC parameters.
- Pseudo-components keep the critical temperature, critical pressure, acentric factor, molar mass and
  volume correction of the file. They use the Peng-Robinson alpha function, and their UNIFAC groups are CH2
  groups from the molar mass.
- The binary interaction parameters of the file are not used.

The mercury content refers to the whole fluid, in `"mole fraction"`, `"ppb"`, `"ug/Sm3"`, `"ug/Nm3"` or
`"ug/kg"`. Mercury already in the fluid is replaced. `toUmrPruMc(fluid)` without a content keeps the mercury
of the source fluid.

A measured mercury content normally refers to one phase of one stream, for example the gas from the inlet
separator. Run the process once, read the calculated content of that phase from the report, and scale the
content given to the converter by measured over calculated. Below saturation mercury distributes in
proportion to the feed content, so one correction is enough. Check it with a second run.

Add water before the conversion if the E300 fluid has none: `EclipseFluidReadWrite.read(file, true)` adds
the water component, and `fluid.addComponent("water", moles)` sets its amount.

### How close the converted fluid is

The converted fluid is close to the E300 fluid, not identical. Checked on 2026-11-19 for a Peng-Robinson
oil with 12 pseudo-components (`fluid1.e300`) and 5 mol% water, in a process with inlet separation at 70 C
and 60 bara, a second stage at 5 bara, recompression and a cold separator at -40 C:

| Quantity | UMR-PRU-MC relative to the E300 fluid |
|----------|---------------------------------------|
| Gas and oil mass flow from the separators | -1.6 % to +1.4 % |
| Water mass flow from the inlet separator | -6.5 % |
| Gas density | +0.3 % to +0.6 % |
| Oil density | +3 % to +11 % |
| Water density | +7 % |

Use the mercury flows and the contents per mass or per standard volume. Take volume flows and liquid
densities from the E300 model. Compare the main stream flows of the two models in every study and report
the differences.

## Running the process

Build the process with the converted fluid in the same way as with the E300 fluid, switch on free mercury
accumulation and run it:

```java
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(200000.0, "kg/hr");
feed.setTemperature(70.0, "C");
feed.setPressure(60.0, "bara");
process.add(feed);
Separator inletSeparator = new Separator("inlet separator", feed);
process.add(inletSeparator);
Cooler chiller = new Cooler("chiller", inletSeparator.getGasOutStream());
chiller.setOutTemperature(273.15 - 40.0);
process.add(chiller);
Separator coldSeparator = new Separator("cold separator", chiller.getOutletStream());
process.add(coldSeparator);

MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, true);
process.run();

MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);
for (MercuryStreamResult stream : report.getStreams()) {
  String name = stream.getLabel();
  double gramPerDay = stream.getMercuryFlowRate("g/day");
  double saturation = stream.getSaturationRatio(); // above 1: the stream carries free mercury
  String state = stream.getFreeMercuryState(); // "none", "liquid" or "solid"
  double gasContent = stream.getMercuryConcentration("gas", "ug/Sm3"); // NaN without a gas phase
}
double heldBack = coldSeparator.getFreeMercuryAccumulationRate("g/day");
String heldBackState = coldSeparator.getFreeMercuryState();
double notAccountedFor = report.getMercuryBalanceError("g/day");
String json = report.toJson();
```

```python
from neqsim import jneqsim

e300_fluid = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite.read("fluid1.e300")
fluid = jneqsim.thermo.util.mercury.MercuryFluidConverter.toUmrPruMc(e300_fluid, 100.0, "ug/Sm3")

process = build_process(fluid)  # the same function that builds the process for the E300 fluid
analyzer = jneqsim.process.util.mercury.MercuryProcessAnalyzer
analyzer.setFreeMercuryAccumulation(process, True)
process.run()

report = analyzer.analyze(process)
for stream in report.getStreams():
    print(stream.getLabel(), stream.getMercuryFlowRate("g/day"), stream.getSaturationRatio(),
          stream.getFreeMercuryState(), stream.getMercuryConcentration("gas", "ug/Sm3"))
print(report.toJson())
```

Build the process again with the converted fluid. Do not replace the fluid of the feed streams in a process
that is already built: recycle streams and other units keep copies of the first fluid.

For a plant with several process areas, pass the `ProcessModel` to `analyze` and
`setFreeMercuryAccumulation`.

### From a JSON process definition

The JSON process builder converts an E300 fluid when the model is `UMRPRU`, and separators take the option
as a property:

```json
{
  "fluid": {
    "e300FilePath": "fluid1.e300",
    "model": "UMRPRU",
    "mercuryContent": [100.0, "ug/Sm3"],
    "temperature": 333.15,
    "pressure": 60.0
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [200000.0, "kg/hr"]}},
    {"type": "Separator", "name": "inlet separator", "inlet": "feed",
     "properties": {"freeMercuryAccumulation": true}}
  ]
}
```

A fluid given by components uses `"model": "UMRPRU"` with `"mercury"` among the components. The model now
gets the UMR-PRU mixing rule by default; before, the JSON builder and the MCP runners gave it the classic
mixing rule, which does not use the group parameters.

## Free mercury

A stream carries free mercury when it holds more mercury than its gas, oil and aqueous phases dissolve.
This happens when a stream is cooled, and also when gas is compressed and cooled or when oil is flashed to
lower pressure. Free mercury is liquid above 234.321 K (-38.83 C) and solid below.

**In the report.** Every stream has a saturation ratio: the fugacity of mercury with all mercury dissolved
divided by the fugacity of pure liquid or solid mercury. Below one all mercury is dissolved. Above one the
report gives the state and the flow of free mercury, and the content of each phase at saturation. Free
mercury below 0.1 % of the mercury in a stream is not reported, since a stream that leaves a separator
saturated shows such amounts when it is flashed again.

**In the process.** Without free mercury accumulation the process calculation has no separate mercury
phase. A stream above saturation then keeps too much mercury in the gas and oil, or the flash forms a
mercury-rich liquid that leaves with one of the liquid outlets. With
`setFreeMercuryAccumulation(true)` a separator calculates the separate mercury phase with
`MercuryPhaseEquilibrium`, holds that mercury back and lets the gas and liquids leave saturated. The rate is
read with `getFreeMercuryAccumulationRate(unit)` and is included in the mass balance of the separator.

The option works in `Separator`, `GasScrubber`, `TwoPhaseSeparator`, `ThreePhaseSeparator`,
`ThreePhaseGasScrubber` and separators that use their steady-state calculation. Free mercury that forms in
a cooler, a valve or a compressor aftercooler follows the stream to the next separator. Place a separator
where mercury is expected to settle, or read the free mercury of the stream from the report.

The option is off by default, and a separator then does no mercury calculation. Ordinary simulations give
the same results at the same speed. Measured on 2026-11-19 on a separation and recompression process with 13
units and an SRK fluid, as the fastest of 24 batches of 25 runs in three repetitions: 17.1 to 17.7 ms per run
before this functionality and 17.2 to 17.8 ms after it with multiphase check, and 15.5 to 16.2 ms before and
15.1 to 16.6 ms after without. The difference is within the variation between repetitions.
When the option is on and the fluid has no mercury, or dissolves all of it, the separator keeps its
ordinary result.

## Example

A wet gas-condensate with $5 \cdot 10^{-8}$ mole fraction mercury is separated at 60 C and 60 bara. The oil
is flashed to 8 bara and its gas recompressed. The gas is cooled to 25 C, chilled to -45 C in a dew point
control separator and compressed for export. `MercuryProcessAnalyzerTest` holds the case.

| Stream | Mercury, g/day | Saturation ratio | Free mercury |
|--------|----------------|------------------|--------------|
| Feed | 902 | 0.03 | none |
| Gas from the inlet separator | 443 | 0.03 | none |
| Oil from the second stage | 379 | 0.05 | none |
| Gas after the gas cooler, 25 C | 478 | 0.33 | none |
| Gas after the chiller, -45 C | 413 | 72 | solid, 407 g/day |
| Export gas, separators hold back free mercury | 0.96 | 0.001 | none |
| Export gas, without free mercury accumulation | 69.5 | 0.07 | none |

The cold separator holds back 407 g/day of solid mercury, and the export gas holds 0.67 ug/Sm3. Without
free mercury accumulation the same calculation sends 72 times more mercury to export than the gas dissolves
at -45 C.

## Limits

- Only elemental mercury is described. Ionic and organic mercury, mercury sulphide, and mercury taken up by
  steel walls and deposits are not.
- Glycols are outside the range of the mercury parameters. Do not read mercury results through a glycol
  contactor or for a stream with MEG or TEG.
- Free mercury is handled in separators only. Columns, absorbers and membranes distribute mercury with the
  equation of state and have no separate mercury phase.
- The separator option applies to steady-state calculations, not to transient ones.
- NeqSim reports at most three fluid phases.
- A feed or product is a stream that no unit operation produces or uses, found from the inlet and outlet
  streams the units report. `getMercuryBalanceError` is not zero when a stream is used by two units.

## Agent and skill

The `mercury-distribution` agent runs this workflow from an E300 fluid and a process model, and the
`neqsim-mercury-distribution-study` skill holds the steps and checks.
