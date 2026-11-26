---
title: "Compositional line-pack pipe for retrograde depressurisation and restart"
description: "CompositionalLinePackPipe: conservative compositional transient model of long gas and gas-condensate pipelines with equation-of-state phase split, elevation, wall heat capacity, liquid-mobility bounds, dew-point scanner and a dew-point-guarded delivery controller. Physics, API, evidence and limits."
---

# Compositional line-pack pipe

`CompositionalLinePackPipe` (package `neqsim.process.equipment.pipeline`) is a screening model for long
gas and gas-condensate export lines over hours to days. Typical uses:

- shutdown and settle-out;
- controlled depressurisation;
- holding below the cricondenbar;
- packing and restart.

It answers where and when retrograde condensate forms, how much there is, whether it revaporises, and
whether the gas delivered at the receiving end is single phase. It does not resolve flow regime, slugs or
holdup dynamics; use [TwoFluidPipe](TWOFLUIDPIPE_MODEL.md) for that and read its
[evidence matrix](twofluidpipe-evidence-matrix.md) first.

Two helpers come with it:

- `RetrogradeDewPointScanner`: upper and lower dew-point pressure and the cricondenbar of any composition,
  from direct TP flashes (no envelope tracer).
- `DewPointGuardedOutletController`: a restart delivery policy that keeps liquid in the pipe.

## Model

Each of the $N$ sections of length $\Delta x$ stores its component masses $m_{i,k}$ and its total energy

$$E_i = U_i + C_w\,(T_i - T_\mathrm{ref}) + M_i\,g\,z_i,$$

where $U_i$ is the fluid internal energy, $C_w$ the lumped steel-wall heat capacity (wall at fluid
temperature, `setWallHeatCapacityEnabled`) and $z_i$ the section-centre elevation.

Face mass fluxes $G_j$ follow

$$\frac{\partial G}{\partial t} = -\frac{\partial p}{\partial x} - \rho g \frac{dz}{dx} - \frac{f\,G|G|}{2D\rho},$$

with the Haaland friction factor, treated semi-implicitly. The convective acceleration term is neglected,
which suits long, slow line-pack transients.

The pressure, temperature and phase split of a section come from its volume, internal energy and
composition. A Newton iteration on NeqSim TP flashes solves this (a VU flash). Between full flashes a
linear predictor is used. A full flash is forced when the predicted change exceeds the refresh tolerances
(`setFlashRefreshTolerances`; defaults 0.3 bar and 0.15 K, tightened to 0.15 bar and 0.08 K inside the
two-phase region) or when the composition changes. The time step follows the acoustic CFL limit and is cut
fourfold for 20 steps after any section changes phase count.

### Liquid mobility

The equilibrium liquid is not given its own momentum equation. Two documented limits bound it:

| Setting | Meaning | Use |
|---|---|---|
| `setLiquidSlipFactor(0)` (default) | only the gas phase is advected; condensate stays where it forms | in-place revaporisation, liquid inventory |
| `setLiquidSlipFactor(1)` | mixture advected without slip | worst case for liquid reaching the outlet |
| `setLiquidGravityDriftVelocity(v)` | condensate drifts to the lower neighbour at $v\sqrt{\lvert dz/dx\rvert/10^{-3}}$, capped at 1 m/s | accumulation in sags |

### Boundaries

- **Inlet stream:** supplies composition, mass flow (read at each `runTransient`) and specific enthalpy at
  the stream's own pressure and temperature. Throttling into the pipe is therefore isenthalpic, and
  Joule-Thomson cooling at an inlet choke is captured.
- **Outlet:** the mass flow is prescribed with `setOutletMassFlowRate`.
- **Heat transfer:** `setHeatTransferCoefficient` (W/m²K, on the inner diameter) toward
  `setConstantSurfaceTemperature` (K).

All pressures are absolute (bara) and temperatures in kelvin.

## Example

The snippet runs as `CompositionalLinePackPipeTest.documentationExample`.

```java
SystemInterface gas = new SystemSrkEos(283.15, 130.0);
gas.addComponent("methane", 79.4);
gas.addComponent("CO2", 8.0);
gas.addComponent("ethane", 7.9);
gas.addComponent("propane", 2.8);
gas.addComponent("n-butane", 1.0);
gas.addComponent("n-pentane", 0.5);
gas.addComponent("n-hexane", 0.2);
gas.addComponent("n-heptane", 0.15);
gas.addComponent("n-octane", 0.05);
gas.setMixingRule("classic");
Stream feed = new Stream("feed", gas);
feed.setFlowRate(0.0, "kg/sec");
feed.setTemperature(15.0, "C");
feed.setPressure(200.0, "bara");
feed.run();

CompositionalLinePackPipe pipe = new CompositionalLinePackPipe("export line", feed);
pipe.setLength(20000.0);
pipe.setDiameter(0.5);
pipe.setWallThickness(0.02);
pipe.setPipeWallRoughness(45e-6);
pipe.setNumberOfSections(8);
pipe.setHeatTransferCoefficient(5.0);
pipe.setConstantSurfaceTemperature(278.15);
pipe.initializeUniform(130.0, 283.15);

// depressurise from the outlet
pipe.setOutletMassFlowRate(80.0);
for (int i = 0; i < 20; i++) {
  pipe.runTransient(180.0, UUID.randomUUID());
}
double liquidKg = pipe.getLiquidInventory("kg");

// restart: shut outlet, supply gas, deliver only when the line is dry and above the local dew point
double cricondenbar = RetrogradeDewPointScanner.cricondenbar(gas, 233.15, 303.15, 150.0)[0];
DewPointGuardedOutletController guard = new DewPointGuardedOutletController(cricondenbar);
guard.setDownstreamSections(3);
feed.setFlowRate(60.0, "kg/sec");
feed.run();
for (int i = 0; i < 20; i++) {
  pipe.setOutletMassFlowRate(guard.update(pipe, 60.0, 600.0));
  pipe.runTransient(600.0, UUID.randomUUID());
}
double[] balanceErrors = pipe.getBalanceErrors(); // {mass, worst component, energy}, relative
```

## Outputs

| Method | Returns |
|---|---|
| `getPressureProfile()`, `getTemperatureProfile()` | bara, K per section |
| `getLiquidHoldupProfile()`, `getLiquidMassProfile()` | equilibrium liquid volume fraction, kg per section |
| `getLiquidInventory(unit)` | `"kg"`, `"tonnes"` or `"m3"`; other units are rejected |
| `getNumberOfTwoPhaseSections()`, `isOutletSectionTwoPhase()` | phase state |
| `getSectionComposition(i)`, `getOutletC3PlusMolePercent()` | mole fractions; C3+ of the stream leaving the outlet |
| `getSectionUpperDewPointPressure(i, T, pMax)`, `getDownstreamDewPointPressure(k, pMax)` | local dew-point pressures, bara |
| `getBalanceErrors()` | relative mass, worst-component and energy closure |
| `getThermodynamicClosureResidual()` | volume residual of a fresh flash at the reported state |
| `getCumulativeInletMass()`, `getCumulativeOutletMass()`, `getCumulativeOutletLiquidMass()` | kg |

## Dew-point-guarded delivery

The controller keeps the outlet shut until two conditions hold:

1. no section is two-phase; and
2. the outlet pressure exceeds $\max(p_\mathrm{cricondenbar},\,p_\mathrm{dew,local}) + \Delta p$.

Here $p_\mathrm{dew,local}$ is the highest upper dew-point pressure of the last $k$ sections (default 8),
each evaluated at the colder of its own and the outlet temperature, and $\Delta p$ is the margin (default
2 bar). Once open, delivery ramps over `setRampTime` and is throttled proportionally within 10 bar of the
floor, which is re-evaluated every `setEvaluationInterval` (default 900 s).

The local term is essential. Condensate that revaporises during packing leaves C3+-rich gas behind. In the
Åsgard-like study below that gas reached 10–11 mol% C3+ (feed 4.5 mol%) and had a dew point of about
127–137 bara, against a feed cricondenbar of 103 bara. Opening on the feed cricondenbar alone let 170–180 t
of liquid out under the no-slip bound.

## Evidence

**Verification** (`CompositionalLinePackPipeTest`, 2026-11-26):

- Mass, component and energy balances close to better than $10^{-10}$ relative in a closed inclined line
  and in a 16 km blowdown that crosses into the retrograde region.
- A stagnant line holds the hydrostatic head $\rho g \Delta z$ within 3 %.
- The dew-point scanner brackets the TP-flash phase change within ±0.2 bar.
- The guard stays shut while any liquid is present and opens with the expected ramp on a dry, packed line.

**Screening study** (rich gas with CO2, 707 km, 42" line on the public Sodir route with EMODnet
bathymetry, SRK with Pedersen C7–C9; run 2026-11-26):

- Liquid inventory changed 0.9 % from 50 to 100 sections.
- Event times changed by 1 % or less when the time step was halved.
- Balance closure was at round-off.

**Independent cross-check against `TwoFluidPipe` steady initialisation** (same route, fluid and boundaries,
2026-11-26):

- Inlet pressure for the pre-shutdown flow: 176.2 bara with `TwoFluidPipe` against 180.0 bara here (2 %).
- Inlet pressure for the restart delivery state: 193.5 against 196.3 bara (1.4 %).
- `TwoFluidPipe` classified every section single-phase gas in the delivery state, as this model did.

- At the blowdown state with a line-average pressure of 100 bara (outlet section 456–707 km, 721 kg/s, outlet
  34 bara), `TwoFluidPipe` with `setIncludeMassTransfer(true)` held 1,169 t of liquid (max holdup 2.7 %,
  annular and stratified). This model held 1,586 t of accumulated, immobile condensate there. With the
  default frozen composition `TwoFluidPipe` found no liquid; enable mass transfer for this kind of check.

These are model-to-model comparisons, not measurements.

## Limits

- Liquid motion is bounded, not predicted. No flow regimes, slugs, terrain slugging or slug arrival are
  modelled. Liquid accumulation shorter than one section is averaged out.
- Phase equilibrium is instantaneous. Real revaporisation of a stratified pool is rate-limited, so the time
  for a line to become liquid-free is optimistic. Add margin or hold time before opening.
- One temperature per section is shared by gas, liquid and wall. Burial, concrete and depth-dependent sea
  temperature are not modelled.
- No water, MEG or hydrate calculation.
- The steady initialisation is isothermal at the inlet-stream temperature.
- Explicit acoustic time stepping: a 700 km, 100-section line takes minutes per simulated day.
