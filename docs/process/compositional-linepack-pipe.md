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
whether the gas delivered at the receiving end is single phase. Its two-fluid liquid transport predicts how stratified films and pools move. It does not resolve slug
flow or annular films; use [TwoFluidPipe](TWOFLUIDPIPE_MODEL.md) for a flowing steady state in those
regimes and read its [evidence matrix](twofluidpipe-evidence-matrix.md) first.

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

### Liquid transport

`setLiquidTransportModel` selects how the equilibrium liquid moves.

| Model | Meaning | Use |
|---|---|---|
| `BOUNDED_SLIP` (default) with `setLiquidSlipFactor(0)` | only the gas phase is advected; condensate stays where it forms | lower bound on liquid reaching the outlet |
| `BOUNDED_SLIP` with `setLiquidSlipFactor(1)` | mixture advected without slip | upper bound on liquid reaching the outlet |
| `BOUNDED_SLIP` with `setLiquidGravityDriftVelocity(v)` | condensate drifts to the lower neighbour at $v\sqrt{\lvert dz/dx\rvert/10^{-3}}$, capped at 1 m/s | simple accumulation in sags |
| `STRATIFIED_TWO_FLUID` | the liquid film has its own velocity from a momentum balance | predicted liquid movement, pooling and delivery |

```java
pipe.setLiquidTransportModel(CompositionalLinePackPipe.LiquidTransportModel.STRATIFIED_TWO_FLUID);
```

**Two-fluid liquid transport.** `StratifiedLiquidMomentum` solves the combined momentum equation of
stratified flow (Taitel and Dukler, 1976) for the liquid velocity $u_L$ at every face:

$$0 = -\frac{\tau_{wL} S_L}{A_L} + \tau_i S_i \left(\frac{1}{A_L} + \frac{1}{A_G}\right)
+ \frac{\tau_{wG} S_G}{A_G} - (\rho_L - \rho_G)\, g\, \frac{d(z + h_L)}{dx}$$

- $\tau_{wk} = f_k \rho_k u_k \lvert u_k \rvert / 2$ are the wall shears and
  $\tau_i = f_i \rho_G (u_G - u_L)\lvert u_G - u_L\rvert / 2$ the interfacial shear.
- $d(z + h_L)/dx$ is the gradient of the liquid surface elevation, so liquid drains downhill and comes to
  rest with a level surface in a sag.
- Geometry and friction are the `TwoFluidPipe` closures: circular-segment geometry (`GeometryCalculator`),
  Fanning wall friction on the phase hydraulic diameters (`WallFriction`), and the gas friction factor times
  `InterfacialFriction.stratifiedWaveEnhancement` at the interface.
- Where the layer is Kelvin-Helmholtz unstable, a fraction from `EntrainmentDeposition` travels with the gas
  (`setEntrainmentEnabled`, default on).
- The total face mass flow still comes from the mixture momentum equation. Liquid crossing a face replaces
  the same mass of gas-composition flow, so mass, components and energy stay conserved.

The pressure gradient is eliminated on purpose. A film driven by the instantaneous cell pressure gradient
feeds back on the pressure field: in a shut-in test line it grew a cell-to-cell pressure sawtooth of ±0.7 bar
within 20 minutes. With the combined equation the same line stays within ±0.05 bar.

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
| `getLiquidFaceVelocityProfile()`, `getEntrainedFractionProfile()` | film velocity (m/s) and entrained fraction per face (two-fluid transport) |
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

All comparisons below are verification and model-to-model checks. None is a comparison with measured data.

**Unit and pipe tests** (`CompositionalLinePackPipeTest`, `StratifiedLiquidMomentumTest`; 12 tests, 2026-11-27):

- Mass, component and energy balances close to better than $10^{-10}$ relative in a closed inclined line,
  in a 16 km blowdown that crosses into the retrograde region, and with two-fluid liquid transport.
- A stagnant line holds the hydrostatic head $\rho g \Delta z$ within 3 %.
- The dew-point scanner brackets the TP-flash phase change within ±0.2 bar.
- The guard stays shut while any liquid is present and opens with the expected ramp on a dry, packed line.
- The liquid momentum balance returns zero velocity without a driving force, and reproduces the closed-form
  laminar drainage velocity $u = (\rho_L - \rho_G)\, g \sin\theta\, A_L D_{hL} / (8 \mu_L S_L)$ within 2 %.
- In a shut-in V-shaped line the condensate collects at the low point, symmetrically, and none leaves.
- Liquid delivered during a blowdown with two-fluid transport lies between the immobile and no-slip bounds.

**Liquid momentum balance against `TwoFluidPipe` steady states** (18 gas-condensate cases: 0.3–1.0 m,
40–100 bara, −1° to +2°, 15–300 kg/s; 2026-11-27). The holdup at which the balance carries the liquid rate,
divided by the converged `TwoFluidPipe` holdup:

| `TwoFluidPipe` regime | Cases | Holdup ratio |
|---|---|---|
| Stratified | 7 | 0.89–1.10 |
| Slug (uphill, low gas velocity) | 8 | 0.50–0.92 |
| Annular (film only, no entrainment) | 3 | 1.43–1.81 |

**`TwoFluidPipe` transient on this process** (2026-11-27 probe, rich gas, closed inlet, outlet pressure
ramped from 130 to 30 bara; coupled pressure-momentum, component transport and mass transfer on):

- 5 km, 12 sections, 0.25 s steps, 2 min ramp: stopped at 10 s ("stable time step cannot advance").
- 50 km, 20 sections, 5 s steps, 4 h ramp: stopped at 180 s ("unsupported component inflow through the
  outlet boundary").

That is why this sequence is run with `CompositionalLinePackPipe`, and `TwoFluidPipe` is used for steady
cross-checks.

**Real-route study** (rich gas with CO2, 707 km, 42" line, ID 1.013 m, public Sodir route and EMODnet
bathymetry, SRK with Pedersen C7–C9; shutdown from 180/110 bara, blowdown from the outlet to 80 bara average,
guarded restart from 200 bara; 2026-11-27):

| Quantity | Two-fluid, 100 sections | 50 sections | Without entrainment | No-slip bound | Immobile, 0.1 m/s drift |
|---|---|---|---|---|---|
| Liquid through outlet during blowdown (t) | 1,177 | 1,204 | 1,103 | 2,072 | 0 |
| Peak liquid in line (t) | 2,182 | 2,111 | 2,224 | 1,305 | 3,271 |
| Liquid at line-average 100 bara (t) | 930 | 859 | 1,104 | 536 | 1,586 |
| Outlet opens after restart (h) | 17.5 | 14.9 | 17.8 | 12.4 | 18.7 |
| Rated 46.1 MSm³/d reached (h) | 26.6 | 28.6 | 25.8 | not within 18 h of opening | 27.3 |
| Liquid delivered during restart (t) | 0 | 0 | 0 | 0 | 0 |
| Delivery steps with two-phase outlet | 0 | 0 | 0 | 0 | 0 |

- Balance errors stayed below $2 \times 10^{-14}$ and the thermodynamic closure residual below
  $5 \times 10^{-5}$ in every run.
- At the line-average 100 bara state, `TwoFluidPipe` steady initialisation with
  `setIncludeMassTransfer(true)` holds 1,169 t on the same 456–707 km section (max holdup 2.7 %). The
  two-fluid transient holds 930 t (max holdup 1.7 %), or 1,104 t (2.5 %) without entrainment.
- With the default frozen composition `TwoFluidPipe` finds no liquid there; enable mass transfer for this
  kind of check.
- Mesh (50 / 100 / 200 sections): liquid through the outlet in blowdown 1,204 / 1,177 / 1,178 t; peak liquid
  2,111 / 2,182 / 2,216 t; outlet opens 14.9 / 17.5 / 18.4 h; rated flow at 28.6 / 26.6 / 24.1 h. From 100 to
  200 sections the liquid quantities change by 1.6 % or less. The opening time changes 5 % and the time to
  rated flow 9 %, so treat the restart timings as accurate to about 10 %.
- Pressure drop: `TwoFluidPipe` steady initialisation gives 176.2 bara inlet for the pre-shutdown flow
  against 180.0 bara here (2 %), and 193.5 against 196.3 bara for the restart delivery state (1.4 %).

## Limits

- The two-fluid liquid transport is a stratified-flow model. Where `TwoFluidPipe` classifies slug or annular
  flow, holdup from the liquid momentum balance differs by up to a factor of two (table above). Slug
  initiation, slug length and arrival, and terrain slugging are not modelled.
- Liquid and gas inertia are neglected in the liquid balance. Liquid accumulation shorter than one section
  is averaged out, so a sag shorter than a section cannot fill and seal.
- Phase equilibrium is instantaneous. Real revaporisation of a stratified pool is rate-limited, so the time
  for a line to become liquid-free is optimistic. Add margin or hold time before opening.
- Entrainment switches on at the Kelvin-Helmholtz limit and the correlation then returns up to 0.99. In the
  real-route study it changed the liquid delivered in blowdown by 6 %.
- One temperature per section is shared by gas, liquid and wall. Burial, concrete and depth-dependent sea
  temperature are not modelled.
- No water, MEG or hydrate calculation.
- The steady initialisation is isothermal at the inlet-stream temperature.
- No comparison with measured pipeline data has been made.
