---
title: Coupled gas release and inventory depletion
description: Rigid adiabatic gas inventory coupled to explicit release physics, native process dynamics, component and energy balances, and supplier-neutral source frames.
---

# Coupled gas release and inventory depletion

`ReleaseInventory` is an opt-in process unit for one rigid, adiabatic, well-mixed gas
inventory and one short opening. Unlike a hypothetical source sample, its native
`runTransient` removes mass and energy. The changed temperature, pressure and composition
then determine the next release calculation. It works within both `ProcessSystem` and
`ProcessModel`; neither container nor the existing separator/blowdown APIs are changed.

The constructor clones the supplied EOS system and scales its amount to the specified
volume at its initial temperature and pressure. The supplied mole count defines composition,
not vessel size or kg/s. Returned fluids and accounting snapshots are independent copies.

The dynamic-capability audit classifies the unit as `DYNAMIC_LUMPED` because it owns
component masses and internal energy. This state-ownership category persists when the
opening is closed or steady-state mode is requested. It does not certify runtime readiness:
activation remains `UNVERIFIED` and source-frame evidence remains `UNQUALIFIED`.

## Balance equations and numerical method

For mass $m$ in kg, component mass fraction $w_i$, internal energy $U$ in J, fixed volume
$V$ in m3, and release rate $\dot m$ in kg/s:

$$\frac{dm_i}{dt}=-w_i\dot m,\qquad \frac{dU}{dt}=-h_0\dot m,\qquad V=\mathrm{constant}$$

$h_0$ is the EOS upstream stagnation enthalpy in J/kg. The discharge removes enthalpy,
including flow work; subtracting only specific internal energy would predict the wrong
cooling. Enthalpy and internal energy can be negative under the selected EOS reference.
Their signed balance is retained. No heat input, boundary work, inflow, kinetic inventory
energy or potential-energy change is included.

Each explicit Euler substep removes bulk-composition mass and integrated $h_0\dot m$,
then calls NeqSim's volume/internal-energy flash with explicit `m3` and `J` units.
It verifies component, volume and energy closure to relative $10^{-7}$, including cumulative
closure against the original inventory. The substep is limited by `maxSubstepS`, remaining
duration, and 1% of current inventory mass. This bound prevents overdraw; it is **not** an
accuracy estimate. Refine `maxSubstepS` and the external output timestep for each study.
A call needing more than 10,000 substeps fails without committing this unit's state.

The general VU flash's stopping tolerance is looser than the cumulative accounting target.
Each substep therefore permits up to 32 repeated solves of the same VU equations until
volume and energy residuals are at most $10^{-11}$ relative to their respective scales.
This is explicit residual refinement with unchanged physics, not a replacement flash or
property fallback. Failure to meet it produces `INVENTORY_VU_REFINEMENT_FAILED`. The total
number of VU solves is available through `getLastVolumeEnergySolves()` and frame provenance.

The release model is explicitly selected. Its upstream pressure, temperature and enthalpy
must match the inventory and its upstream/exit composition must match bulk withdrawal.
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
hypothetical sample and does not enable inventory coupling. Schema v1 is unchanged.

Coupled frames carry `releaseBasis=COUPLED_RIGID_ADIABATIC_GAS_INVENTORY`, the integrator
identity, inventory volume/time, cumulative released mass/enthalpy, and
`rateTimeBasis=INSTANTANEOUS_AT_FRAME_TIME` in provenance. Evidence stays `UNQUALIFIED`.
Process, equipment, calculation UUID and session sequence semantics are retained.
The configured maximum substep and last substep count are also recorded in provenance.

`vessel.setReleaseEnabled(false)` stops physical withdrawal and yields an
`INVENTORY_OPENING_CLOSED` disabled frame after the next successful process call.
It invalidates the prior equipment calculation identity. In contrast,
`session.setEnabled(sourceId, false)` disables export only; process physics continue.
Opening/closure changes occur at step boundaries; split steps at known event times.

| Condition | Behavior |
|---|---|
| Receiving pressure at or above initial inventory pressure | Valid zero flow; no reverse flow modeled. |
| Substep would reduce pressure below receiving pressure | `RECEIVING_PRESSURE_CROSSED`; entire unit call rejected; reduce timestep. |
| Condensation or any non-gas phase | `INVENTORY_REGIME_UNSUPPORTED`; no selective-phase or entrainment assumption invented. |
| Invalid model, mismatched upstream state/composition, or screening result | Fail closed without committing inventory. |
| Volume, component or energy closure failure | Fail closed, with the corresponding closure diagnostic. |
| Native process step failure | Session emits `INVALID` without numeric source payload and becomes faulted. |

Updates are transactional **within this unit**: failure preserves its pre-call fluid,
accounting, clock and UUID. Other process units and the process clock may already have
advanced. Follow the [session recovery guidance](live-source-term-sessions#failure-and-freshness-semantics);
a steady rerun is not a rollback of the surrounding process. Caller-owned custom release
models must be deterministic and must not mutate themselves or the process during evaluation.

## Validation and applicability

`ReleaseInventoryTest` checks multicomponent gas depletion using the homogeneous-equilibrium
release model, component/energy/volume closure, cooling and decreasing release rate, input
immutability, cloning, zero flow, isolation/reopening, invalid steps, pressure-boundary
rollback, injected mid-step failure, and both process containers. Actual frames, including
disabled and failed cases, are validated against the bundled JSON Schema.

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

This increment covers a **single gas-phase inventory**. Multiphase storage/phase-selective
withdrawal, pressure-equilibration event location, wall/fire heat transfer, pipe decompression,
non-equilibrium transfer, mixture-specific solid risk, experimental qualification and the
executed Colab demonstration remain separate work in [#3860](https://github.com/equinor/neqsim/issues/3860).
The numerical limit of the selected release model still applies. No facility qualification
or independent safety/domain review is implied.
