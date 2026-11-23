---
title: Severe-slug analyser (lumped flowline–riser model)
description: Physics, inputs, units, outputs and limits of SevereSlugAnalyser, the lumped dynamic severe-slugging model for a downward-inclined flowline feeding a vertical riser, and how it relates to the Taitel stability diagnostic.
---

`SevereSlugAnalyser` (package `neqsim.process.measurementdevice.simpleflowregime`) is a fast, lumped
dynamic model of riser-induced (severe) slugging. It is a screening tool: it answers "does this
flowline–riser operating point cycle, and how large is the riser-base pressure swing?" in
milliseconds. It is not a substitute for a distributed transient simulation.

NeqSim has two complementary severe-slugging screens:

| Tool | Type | Answers | Needs |
|------|------|---------|-------|
| `SevereSlugAnalyser` | Lumped dynamic simulation (this page) | Cycles or not, pressure swing, regime label | Geometry, superficial velocities, outlet pressure |
| `SevereSluggingSystemDiagnostic` | Quasi-steady Taitel (1986) criterion | Stable / unstable and pressure margin | A solved `TwoFluidPipe` profile or an explicit descriptor |

See [TwoFluidPipe model](TWOFLUIDPIPE_MODEL) for the Taitel diagnostic.

## Geometry and conventions

| Input | Unit | Meaning |
|-------|------|---------|
| `internalDiameter` | m | Same diameter for flowline and riser |
| `leftLength` | m | Flowline length upstream of the riser base |
| `rightLength` | m | Height of the **vertical** riser |
| `angle` | degrees | **Downward** inclination of the flowline towards the riser base; must be > 0 |
| `usl` | m/s | Superficial liquid velocity at flowing conditions |
| `usg` | m/s | Superficial gas velocity **at the gas reference pressure** |
| `gasReferencePressure` | Pa (absolute) | Pressure at which `usg` is defined. Default 1.0e5 Pa; stream-based constructors use the stream pressure |
| `outletPressure` | Pa (absolute) | Pressure at the riser top |
| `temperature` | °C | Isothermal gas temperature |
| `simulationTime` | s | Simulated time |
| `numberOfTimeSteps` | – | Number of stored output samples |

The gas mass inflow is proportional to `usg × gasReferencePressure`. If you pass a velocity
measured at standard conditions keep the default reference pressure; if you compute it from a
stream, the stream-based constructors set the reference pressure for you.

## Model

Three states are integrated: the mixture velocity of the liquid slug $U_m$, the riser void
fraction $\alpha_r$ and the flowline gas pressure $P$ (plus the liquid-front position $z$ in
the flowline when the bend is blocked).

- **Momentum** of the liquid column in the riser and the blocked part of the flowline:
  pressure difference $P - P_{out}$ against wall friction (Blasius / laminar), riser head
  $\rho_m g H$, and the head recovered from liquid backed up in the downward flowline.
- **Gas mass balance** of the flowline gas volume $A\,\alpha_L\,(L+z)$ as an isothermal ideal gas.
  $\alpha_L$ is the gas fraction of the stratified flowline, from a stratified hold-up balance.
- **Liquid front**: when the bend is blocked, the liquid only has to fill the gas fraction of the
  stratified flowline, so the front moves at $(U_m - U_{sl})/\alpha_L$.
- **Riser void fraction** from gas and liquid balances with a drift-flux slip
  ($C_0 = 1.2$ turbulent, 2.0 laminar; drift $0.35\sqrt{gD}$).

Oil and water are lumped into one liquid by `FluidSevereSlug` using in-situ volume fractions:
density is the volume-weighted average and viscosity a volume-weighted linear average (no
emulsion model). The gas uses the specific gas constant $R/M$ of the gas phase.

The integration is explicit with internal sub-steps of at most 1 ms, so the result does not depend
on `numberOfTimeSteps`; that parameter only sets the resolution of the stored trend.

## Outputs

| Method | Unit | Meaning |
|--------|------|---------|
| `getPredictedFlowRegime()` | text | Regime label, see below |
| `getMeasuredValue()` / `getSlugValue()` | – | Half the peak-to-peak riser-base pressure swing divided by the mean pressure |
| `getPressureAmplitude()` | bar | Peak-to-peak riser-base pressure swing |
| `getMeanPressure()` | bara | Mean riser-base pressure |
| `getPressureTrend()`, `getTimeTrend()` | bara, s | Simulated trend |

All statistics use the **second half** of the simulation, after the start-up transient.

| Label | Condition |
|-------|-----------|
| `Severe Slug` | slug value > 0.1 and slug hold-up > stratified hold-up |
| `Severe Slug 2. Small pressure variations` | slug value > 0.05 and slug hold-up > stratified hold-up |
| `Slug Flow` | slug hold-up ≤ stratified hold-up |
| `Liquid droplets flow` / `Gas droplets flow` / `Stratified Flow` | otherwise, by stratified hold-up < 0.1 / > 0.9 / between |
| `Single Phase` | gas or liquid missing |
| `Regime cannot be detected …` | flowline angle ≤ 0 |

The slug value is normalised by absolute pressure, so the same pressure swing gives a smaller
value at high pressure. Use `getPressureAmplitude()` and compare it with the riser liquid head
$\rho_L g H$ when judging severity for a high-pressure system.

## Usage

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 15.0, 10.0);
fluid.addComponent("methane", 0.00015, "MSm^3/day");
fluid.addComponent("n-heptane", 0.0015, "MSm^3/day");
fluid.addComponent("water", 0.00015, "MSm^3/day");
fluid.setMixingRule(2);
fluid.setMultiPhaseCheck(true);
Stream stream = new Stream("feed", fluid);

// diameter, flowline length, riser height, flowline angle [deg],
// outlet pressure [Pa], temperature [C], simulation time [s], samples
SevereSlugAnalyser analyser =
    new SevereSlugAnalyser("riser", stream, 0.05, 167.0, 7.7, 0.1, 1.0e5, 15.0, 500.0, 50000);

String regime = analyser.getPredictedFlowRegime();
double slugValue = analyser.getMeasuredValue();
double swingBar = analyser.getPressureAmplitude();
```

Explicit superficial velocities (for example a laboratory air–water case):

```java
Pipe pipe = new Pipe("lab", 0.05, 167.0, 7.7, 2.0);
FluidSevereSlug airWater = new FluidSevereSlug(1000.0, 0.001, 0.029); // kg/m3, Pa s, kg/mol
SevereSlugAnalyser analyser = new SevereSlugAnalyser("lab", 1.0e5, 20.0, 500.0, 20000);
analyser.setSuperficialLiquidVelocity(0.1);
analyser.setSuperficialGasVelocity(0.5); // at 1.0e5 Pa reference pressure
String regime = analyser.getPredictedFlowRegime(airWater, pipe, analyser);
```

## Choosing the simulation time

A severe-slugging cycle lasts roughly the time needed to fill the riser and the blocked part of
the flowline with liquid, of order $(H + \alpha_L L)/U_{sl}$. The second half of the simulation
must contain at least one full cycle, otherwise the pressure swing is under-reported. For low
liquid rates use several thousand seconds; the model is cheap enough.

## Limits

- One diameter, a straight downward flowline and a vertical riser. No flexible-riser shapes,
  no topside choke dynamics (`valveConstant` is zero by default).
- Isothermal ideal gas, incompressible liquid, no mass transfer between the phases.
- Oil and water are one lumped liquid; no oil–water slip, emulsion viscosity or water accumulation.
- Regime labels and thresholds are heuristics and have not been tuned against a public data set.
  Treat the result as a screen and confirm borderline cases with a distributed transient model.

## Behaviour changes (2026-11)

Earlier versions contained defects that changed results. If you compare with old numbers:

- Stream-based constructors ignored `outletPressure`, `temperature`, `simulationTime` and
  `numberOfTimeSteps` and always simulated 20 s at 1 bara outlet pressure with a fixed 1 ms step.
- The gas inflow always used 1.0e5 Pa as reference pressure, also when `usg` came from a stream
  at another pressure.
- The lumped liquid density used mass fractions of the total stream (including gas), which
  under-predicted density; the specific gas constant was wrong by many orders of magnitude for
  fluids built from a stream.
- The liquid front in the flowline moved at the superficial velocity instead of
  $(U_m - U_{sl})/\alpha_L$.
- The slug drift velocity used $\sqrt{gD}$ instead of the Bendiksen coefficients.
- The slug value was `max/mean − 1`, which under-reported long build-up / short blow-out cycles.
  It is now half the relative peak-to-peak swing (identical for a symmetric oscillation).
- Phases were addressed by index, which failed when the gas was not phase 0.

## References

- Taitel, Y. (1986). *Stability of Severe Slugging*. Int. J. Multiphase Flow 12(2), 203–217.
- Bendiksen, K.H. (1984). *An experimental investigation of the motion of long bubbles in
  inclined tubes*. Int. J. Multiphase Flow 10(4), 467–483.
- Schmidt, Z., Brill, J.P., Beggs, H.D. (1980). *Experimental Study of Severe Slugging in a
  Two-Phase-Flow Pipeline–Riser Pipe System*. SPE Journal 20(5), 407–414.
