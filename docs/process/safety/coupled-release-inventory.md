---
title: Coupled release and inventory depletion
description: Rigid adiabatic equilibrium inventory coupled to phase-selected release physics, native process dynamics, component and energy balances, and supplier-neutral source frames.
---

# Coupled release and inventory depletion

`ReleaseInventory` is an opt-in process unit for one rigid, adiabatic, well-mixed equilibrium
inventory and one short opening. Unlike a hypothetical source sample, its native
`runTransient` removes mass and energy. The changed temperature, pressure and composition
then determine the next release calculation. It works within both `ProcessSystem` and
`ProcessModel`; neither container nor the existing separator/blowdown APIs are changed.

The original constructor remains a single-gas compatibility API. An overload ending in an
explicit `PhaseType` selects `GAS`, `OIL`, `LIQUID` or `AQUEOUS` withdrawal. The selected
equilibrium phase supplies the upstream state and withdrawn composition; the entire remaining
inventory is then reflashed. This is a boundary condition, not a level/geometry model: no
entrainment, slip, interfacial transfer rate or automatic fallback phase is inferred.

An overload accepting `List<PhaseType>` and `phaseExhaustionMassFraction` adds a caller-declared
ordered transition plan. The first phase must initially be present above the threshold. A transient
step advances only to the next named phase when the active phase inventory mass fraction reaches
the explicit threshold. It never selects a phase from density, position or phase count. If the
immediately next planned phase is not usable, the complete call fails atomically.

The constructor clones the supplied EOS system and scales its amount to the specified
volume at its initial temperature and pressure. The supplied mole count defines composition,
not vessel size or kg/s. Returned fluids and accounting snapshots are independent copies.

The dynamic-capability audit classifies the unit as `DYNAMIC_LUMPED` because it owns
component masses and internal energy. This state-ownership category persists when the
opening is closed or steady-state mode is requested. It does not certify runtime readiness:
activation remains `UNVERIFIED` and source-frame evidence remains `UNQUALIFIED`.

## Balance equations and numerical method

For component inventory $m_i$ in kg, selected-phase component mass fraction $w_{i,s}$,
internal energy $U$ in J, fixed volume $V$ in m3, and release rate $\dot m$ in kg/s:

$$\frac{dm_i}{dt}=-w_{i,s}\dot m,\qquad \frac{dU}{dt}=-h_{0,s}\dot m,\qquad V=\mathrm{constant}$$

$h_{0,s}$ is the selected phase's EOS upstream stagnation enthalpy in J/kg. The discharge removes enthalpy,
including flow work; subtracting only specific internal energy would predict the wrong
cooling. Enthalpy and internal energy can be negative under the selected EOS reference.
Their signed balance is retained. No heat input, boundary work, inflow, kinetic inventory
energy or potential-energy change is included.

Each explicit Euler substep removes selected-phase composition and integrated $h_{0,s}\dot m$,
then calls NeqSim's volume/internal-energy flash with explicit `m3` and `J` units.
It verifies component, volume and energy closure to relative $10^{-7}$, including cumulative
closure against the original inventory. The substep is limited by `maxSubstepS`, remaining
duration, and 1% of current selected-phase mass. This bound prevents overdraw; it is **not** an
accuracy estimate. Refine `maxSubstepS` and the external output timestep for each study.
A call needing more than 10,000 substeps fails without committing this unit's state.

For an ordered plan, the phase-exhaustion threshold is a model input, not a numerical tolerance.
It represents the minimum phase inventory for which the selected homogeneous withdrawal boundary
is considered applicable. The transition preserves all residual material in the reflashed vessel;
it changes only which explicitly named equilibrium phase supplies subsequent outflow composition
and enthalpy. Threshold sensitivity therefore belongs in each study's validation matrix.

The general VU flash's stopping tolerance is looser than the cumulative accounting target.
Each substep therefore permits up to 32 repeated solves of the same VU equations until
volume and energy residuals are at most $10^{-11}$ relative to their respective scales.
This is explicit residual refinement with unchanged physics, not a replacement flash or
property fallback. Failure to meet it produces `INVENTORY_VU_REFINEMENT_FAILED`. The total
number of VU solves is available through `getLastVolumeEnergySolves()` and frame provenance.

The release model is explicitly selected. Its upstream pressure, temperature and enthalpy
must match the selected phase and its upstream/exit composition must match that phase's withdrawal.
Exit static enthalpy plus specific kinetic energy must equal upstream stagnation enthalpy.
Legacy `SCREENING_ONLY` or unresolved-station results are rejected. No alternate release
model, property default or flash recovery is substituted.

## Executable process example

The corresponding construction, registration, stepping, balance access and isolation calls
are exercised by `ReleaseInventoryTest.documentationExampleAndPhysicalIsolationWorkForBothContainers`.
Use imports from `neqsim.process.safety.release`, `neqsim.process.processmodel`,
`neqsim.thermo.system` and `java.util.List`.

```java
SystemInterface gas = new SystemSrkEos(300.0, 2.0); // K and bara
gas.addComponent("nitrogen", 1.0);
gas.setMixingRule("classic");
ReleaseInventory vessel = new ReleaseInventory("inventory", gas,
    1.0, 0.01, 0.7, 10000.0, new IdealGasReleaseModel(), 0.1);
ProcessSystem process = new ProcessSystem();
process.add(vessel);
SourceTermSession session = new SourceTermSession("inventory-study", process);
session.addInventorySource("opening", "inventory");
List<SourceTermFrame> initial = session.runSteadyState(); // no depletion
List<SourceTermFrame> next = session.step(0.5);
ReleaseInventory.Balance accounting = vessel.getBalance();
double emittedMassKg = accounting.getReleasedMassKg();
double outgoingEnthalpyJ = accounting.getReleasedEnergyJ();
vessel.setReleaseEnabled(false); // physically close this opening
List<SourceTermFrame> isolated = session.step(0.5);
```

For an equilibrium gas-over-liquid inventory, select the gas boundary explicitly:

```java
ReleaseInventory phaseSelected = new ReleaseInventory("two-phase", flashedFluid,
    1.0, 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel(), 0.02,
    PhaseType.GAS);
```

Use the actual equilibrated phase type when selecting a hydrocarbon liquid (`OIL` or
`LIQUID`). Construction rejects an absent or unsupported phase. If re-equilibration removes
the selected phase during a transient call, the call fails atomically with
`INVENTORY_SELECTED_PHASE_ABSENT`; callers must split the regime or select another assessed model.

For an assessed gas-to-liquid transition, declare both phases and the inventory mass-fraction
boundary explicitly:

```java
List<PhaseType> withdrawalPlan = Arrays.asList(PhaseType.GAS, liquidType);
ReleaseInventory staged = new ReleaseInventory("staged", flashedFluid,
    1.0, 0.01, 0.62, 101325.0, new HomogeneousEquilibriumReleaseModel(), 0.02,
    withdrawalPlan, 1.0e-4);
```

This does not model a vessel level, interface geometry, entrainment or interfacial transfer rate.
Use a threshold justified for the modeled inventory and verify sensitivity around it.

For an area-based model, add `process` under `"gas-area"`, construct the session from that
`ProcessModel`, and use `session.addInventorySource("opening", "gas-area", "inventory")`.
Keep dynamic mode enabled; this unit is constructed with `setCalculateSteadyState(false)`.
A steady run checks the present state without resetting the inventory or accounting.
Repeated native evaluations with the same transient UUID do not withdraw twice; a different
duration with that same UUID is rejected.

`getBalance()` provides immutable initial, remaining and released component masses in kg,
initial/current internal energy and released enthalpy in J, fixed volume, simulation time
and the last substep count. These integrated quantities are the balance evidence. A frame's
rate is the **instantaneous** rate at its simulation time, not a timestep average. Do not
apply the frame rate as an additional withdrawal from this unit.

## Frame and failure semantics

`addInventorySource` binds the inventory's own geometry and release model. It rejects a
second physical-source registration for the same unit. Existing `addSource` remains a
hypothetical sample and does not enable inventory coupling.

The finite-pipe `ReleaseInventory` constructor adds flow-path length and specified Darcy friction
before the model argument. That geometry is retained as inventory pressure and composition evolve
and is exported by `addInventorySource`. It enables quasi-steady coupling to the ideal-gas and
single-equilibrium-phase real-gas Fanno models; it does not add transient pipe-wave storage to the lumped inventory. Schema v1 accepts the
paired optional `flowPathLength` and `darcyFrictionFactor` source fields.

## Transient single-gas pipe line packing

`IdealGasPipeDecompression` is a separate process unit for finite-speed wave propagation and
line-pack discharge. It solves the one-dimensional conservative mass, momentum and total-energy
equations on a caller-selected finite-volume grid. The upstream end is closed; the downstream end
is connected to a constant absolute receiver pressure. A local Lax--Friedrichs numerical flux and
CFL-limited substeps advance the state, while specified Darcy friction changes momentum without
removing adiabatic total energy.

```java
IdealGasPipeDecompression pipe = new IdealGasPipeDecompression(
    "ruptured-line", gas, 100.0, 0.20, 1.0, 101325.0, 0.012, 80, 0.45);
process.add(pipe);

SourceTermSession session = new SourceTermSession("linepack-case", process);
session.addInventorySource("rupture", "ruptured-line");
SourceTermFrame frame = session.step(0.05).get(0);
```

The committed frame rate is the exact downstream mass flux used by the conservative update. Frame
provenance includes pipe length/diameter, cell count, CFL, friction factor, integrator identity,
last substep count, remaining clock, and cumulative discharged mass and total energy. Both
`ProcessSystem` and `ProcessModel` paths use the same coupled-source registration; no second
hypothetical withdrawal is calculated.

The model requires one nonreacting equilibrium gas phase, freezes composition and calorically
perfect properties at the initial state, and remains `UNQUALIFIED`. It excludes real-gas property
evolution, heat transfer, pipe elasticity, an upstream vessel, two-sided rupture, phase change,
slip, entrainment and solid-bearing flow. Refine the grid and compare retained pressure/rate
histories before using a result even inside this applicability boundary.

`RealGasPipeDecompression` uses the same geometry, process-container registration and conservative
accounting, but recovers each cell through a NeqSim volume/internal-energy flash. Pressure,
temperature and acoustic speed therefore evolve with the selected EOS. It fails the whole caller
step atomically if any cell or the receiver boundary ceases to be one equilibrium gas phase. This
adds real-gas transient property evolution, not heat transfer, wall elasticity, finite upstream
vessel or two-sided coupling, non-equilibrium multiphase transport, solids, or qualification.

Compatibility frames carry `releaseBasis=COUPLED_RIGID_ADIABATIC_GAS_INVENTORY`.
Explicit phase-selected frames carry
`releaseBasis=COUPLED_RIGID_ADIABATIC_PHASE_SELECTED_INVENTORY` and
`inventoryWithdrawalPhase`. Both carry the integrator
identity, inventory volume/time, cumulative released mass/enthalpy, and
`rateTimeBasis=INSTANTANEOUS_AT_FRAME_TIME` in provenance. Evidence stays `UNQUALIFIED`.
Process, equipment, calculation UUID and session sequence semantics are retained.
The configured maximum substep and last substep count are also recorded in provenance.
Ordered-plan frames instead carry
`releaseBasis=COUPLED_RIGID_ADIABATIC_PHASE_TRANSITION_INVENTORY`, the complete ordered plan,
the exhaustion threshold, active phase, last-call transition count and cumulative transition
count. All remain string-valued provenance in schema v1; SI source quantities are unchanged.

`vessel.setReleaseEnabled(false)` stops physical withdrawal and yields an
`INVENTORY_OPENING_CLOSED` disabled frame after the next successful process call.
It invalidates the prior equipment calculation identity. In contrast,
`session.setEnabled(sourceId, false)` disables export only; process physics continue.
Opening/closure changes occur at step boundaries; split steps at known event times.

| Condition | Behavior |
|---|---|
| Receiving pressure at or above initial inventory pressure | Valid zero flow; no reverse flow modeled. |
| Substep would reduce pressure below receiving pressure | Bounded event location lands on receiving pressure; provenance records the physical release duration. |
| Selected phase is absent initially | Construction rejects the configuration. |
| Selected phase disappears after re-equilibration | `INVENTORY_SELECTED_PHASE_ABSENT`; the unit call commits no state. |
| Active planned phase reaches its explicit exhaustion threshold | Advances only to the immediately next usable caller-declared phase and records the transition. |
| Immediately next planned phase is absent or exhausted | `INVENTORY_WITHDRAWAL_PHASE_PLAN_EXHAUSTED`; the unit call commits no state. |
| Reactions, forced phases, solids or hydrates | `INVENTORY_REGIME_UNSUPPORTED`; no fallback physics is invented. |
| Invalid model, mismatched upstream state/composition, or screening result | Fail closed without committing inventory. |
| Volume, component or energy closure failure | Fail closed, with the corresponding closure diagnostic. |
| Native process step failure | Session emits `INVALID` without numeric source payload and becomes faulted. |

Updates are transactional **within this unit**: failure preserves its pre-call fluid,
accounting, clock and UUID. Other process units and the process clock may already have
advanced. Follow the [session recovery guidance](live-source-term-sessions#failure-and-freshness-semantics);
a steady rerun is not a rollback of the surrounding process. Caller-owned custom release
models must be deterministic and must not mutate themselves or the process during evaluation.

## Validation and applicability

`ReleaseInventoryTest` checks multicomponent gas depletion, phase-selected gas-over-liquid and
liquid withdrawal using the homogeneous-equilibrium release model. A controlled deterministic
release model isolates explicit gas-to-liquid exhaustion transition, conservation and timestep/
threshold refinement from release-model solver noise. The suite also checks component/energy/volume closure, cooling and decreasing release rate, input
immutability, cloning, zero flow, isolation/reopening, invalid steps, pressure-boundary
rollback, injected mid-step failure, and both process containers. Actual frames, including
disabled and failed cases, are validated against the bundled JSON Schema.

Phase-selected source frames are schema-validated from both process containers. A three-level
substep refinement checks convergent pressure for gas withdrawal from a methane/n-butane
two-phase inventory, while released component mass verifies selection of the gas composition.
For 0.04, 0.02 and 0.01 s maximum substeps over 0.2 s, the final pressures are
998944.206, 998944.262 and 998944.290 Pa; the fine/coarse pressure-difference ratio is 0.500.
CI retains the component withdrawals and energy residuals in
`target/source-term-benchmarks/inventory-phase-selected-convergence.csv`.
The capability regression checks both process containers and preserves the distinction
between audited state ownership and unverified runtime activation. The focused safety workflow
also runs `DynamicCapabilityBuiltInInventoryTest` to catch missing built-in registrations.

For an independent temporal reference, the constant-gamma ideal-gas choked, rigid adiabatic
solution follows by combining the balance equations with $U=mc_vT$ and the choked discharge
relation. With $a=\dot m_0/m_0$ in 1/s:

$$\frac{m(t)}{m_0}=\left[1+\frac{\gamma-1}{2}at\right]^{-2/(\gamma-1)}$$

$$\frac{T(t)}{T_0}=\left(\frac{m(t)}{m_0}\right)^{\gamma-1},\qquad \frac{p(t)}{p_0}=\left(\frac{m(t)}{m_0}\right)^\gamma$$

The test uses nitrogen at 300 K, initially 2.0 and 2.2 bara, 1 m3, a 0.01 m opening,
$C_d=0.7$, receiving pressure 0.1 bara and 10 s duration. It compares three substep
limits (0.2, 0.1 and 0.05 s) to the closed-form mass, pressure and temperature and checks
first-order refinement. The reference uses the initial NeqSim molar mass/gamma; its temporal
solution is independent, while the implemented inventory retains the real EOS and variable
properties. The test tolerance is 0.3% of initial values and is not an experimental accuracy
claim. CI retains `target/source-term-benchmarks/inventory-convergence.csv`.

The local 0.05 s results at 10 s are:

| Initial pressure (Pa) | Remaining mass (kg) | Pressure (Pa) | Closed-form pressure (Pa) | Absolute energy residual (J) |
|---|---|---|---|---|
| 200000 | 2.00989363 | 171096.452 | 171105.421 | 0.000224 |
| 220000 | 2.21087835 | 188197.626 | 188206.827 | 0.000243 |

The fine/coarse pressure-difference ratio is approximately 0.499 in both cases. These results
separate numerical refinement from the small real-EOS/constant-gamma reference difference.

The transition benchmark spans thresholds 0.026175--0.026185 and maximum substeps 0.15--0.0375 s.
All nine cases conserve 0.0003 kg release, commit exactly one transition and retain component,
energy and volume closure. The finest nearby-threshold pressure spread is below 0.032%; successive
step refinement is non-increasing within the asserted numerical tolerance.

This increment covers equilibrium, phase-selected withdrawal and explicit ordered phase-exhaustion
transitions in a well-mixed rigid inventory. Phase level/geometry, entrainment and slip, finite-rate interfacial transfer, wall/fire heat transfer,
real-gas transient decompression, non-equilibrium transfer, solid-bearing flow physics and experimental qualification
remain separate work in [#3860](https://github.com/equinor/neqsim/issues/3860).
The numerical limit of the selected release model still applies. No facility qualification
or independent safety/domain review is implied.
\n### Receiving-pressure event provenance\n\n`inventoryPressureEquilibrationEvent` records whether the last successful transient call landed on the no-flow boundary. `inventoryReleaseDurationS` is the physical discharge duration within that caller timestep. Event trials use the same EOS, component removal and enthalpy balance as ordinary substeps; failed event location commits no state.\n
