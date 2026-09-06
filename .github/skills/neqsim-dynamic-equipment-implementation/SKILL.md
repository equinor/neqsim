---
name: neqsim-dynamic-equipment-implementation
description: "Implement and test NeqSim process-equipment runTransient support. USE WHEN: adding dynamic simulation behavior to any ProcessEquipmentInterface class, including inventory volume, pressure/level/temperature state, controller response, and JUnit regression tests for transient timesteps."
last_verified: "2026-06-25"
---

# Dynamic Equipment Implementation

Use this skill when a NeqSim equipment class needs real dynamic simulation support instead of the default steady-state fallback or unsupported transient behavior.

This complements `neqsim-dynamic-simulation`, which explains how to run dynamic studies. This skill explains how to implement and test the Java code that makes equipment participate correctly in `ProcessSystem.runTransient(dt, id)`.

## Local Architecture

- `SimulationInterface.runTransient(double dt, UUID id)` defaults to steady-state `run(id)` when `getCalculateSteadyState()` is true, otherwise throws `UnsupportedOperationException`.
- `ProcessSystem.runTransient(dt, id)` steps each `ProcessEquipmentInterface` through a skip-aware wrapper and calls `unit.runTransient(dt, id)`.
- Equipment that has real dynamic behavior must override `runTransient(double dt, UUID id)`.
- Existing reference implementations include `Separator`, `ThreePhaseSeparator`, `Tank`, `Compressor`, `ThrottlingValve`, `SafetyValve`, `BlowdownValve`, `PipeBeggsAndBrills`, `WaterHammerPipe`, `SimpleReservoir`, and `WellFlow`.
- All dynamic equipment code must remain Java 8 compatible, use Log4j2 for logging, and pass Spotless formatting.

## Implementation Decision Tree

1. **Is the equipment purely algebraic?**
   - Examples: simple pressure drop, splitter ratio, ideal heater with no metal/fluid holdup.
   - Implement only if needed to propagate controller or actuator dynamics; otherwise the steady-state fallback may be sufficient.

2. **Does the equipment hold mass or energy inventory?**
   - Examples: separator, tank, column tray/section, adsorber bed, exchanger volume, pipe segment, reactor, accumulator.
   - Add explicit volume/holdup state and integrate component moles and internal energy over `dt`.

3. **Does the equipment have actuator or mechanical state?**
   - Examples: compressor shaft speed, valve opening, pump ramp, fan speed, bed switching, recycle valve position.
   - Add bounded state variables, ramp-rate limits, and controller hooks.

4. **Does the equipment need thermal inertia?**
   - Examples: heat exchangers, heaters/coolers, fired heaters, reactors, pipes.
   - Add fluid volume and optional wall/metal heat capacity. Preserve energy balance and avoid instant outlet jumps unless the equipment is intentionally algebraic.

## Required Code Pattern

For any new transient implementation:

1. Add serializable state fields with defaults that preserve existing steady-state behavior.
2. Add public setters/getters for dynamic configuration such as volume, residence time, metal mass, ramp rate, or initial inventory.
3. Override `runTransient(double dt, UUID id)`.
4. Keep the steady-state branch first:
   - If `getCalculateSteadyState()` is true, call `run(id)`, `increaseTime(dt)`, set/finish the calculation identifier consistently with the class pattern, and return.
5. Initialize transient state lazily on the first dynamic step from the last steady-state solution.
6. Run controllers or actuator logic before solving the equipment response when the class exposes controller behavior.
7. Integrate component and energy balances over the timestep:
   - Accumulation = inlet rates minus outlet rates plus generation or consumption.
   - Energy accumulation = inlet enthalpy rates minus outlet enthalpy rates plus heat/work terms.
   - Clamp tiny negative mole inventories to zero; do not allow negative total volume, negative pressure, or invalid phase amounts.
8. Re-flash the updated inventory with the appropriate flash operation (`TPflash`, `TVflash`, `PHflash`, or `VUflash`) and call `initProperties()` or the class-equivalent physical-property initialization before reading transport properties.
9. Update every outlet stream from the new state and preserve mass/energy consistency.
10. Call `increaseTime(dt)` and set the calculation identifier before returning.

## Equipment Configuration Guidance

Dynamic behavior needs physical capacity. Prefer existing mechanical-design data when it already exists, but expose a simple runtime configuration path for simulations:

- Vessel-like equipment: volume, diameter, length, liquid level, gas headspace, max/min operating pressure.
- Heat-transfer equipment: process-side volume, optional utility-side volume, UA, metal mass, metal heat capacity, ambient heat loss.
- Rotating equipment: inertia, rated speed, ramp rates, driver power limit, recycle or minimum-flow logic.
- Valves and dampers: opening fraction, stroke time, fail action, Cv/characteristic, controller attachment.
- Pipes: length, diameter, roughness, elevation profile, segment inventory, heat-transfer environment.
- Reactors and beds: bed volume, void fraction, catalyst/solid heat capacity, reaction source terms, residence time.

Do not hide required dynamic capacity behind mechanical design only. Task notebooks and MCP workflows need direct, documented setters for dynamic parameters.

## Testing Requirements

Create focused JUnit 5 tests under the matching package in `src/test/java/neqsim/...`.

Minimum test coverage for each dynamic implementation:

1. **Steady-state compatibility:** `runTransient(dt, id)` with `calculateSteadyState=true` matches or preserves the existing `run(id)` behavior.
2. **Dynamic branch executes:** with `calculateSteadyState=false`, `runTransient(dt, id)` does not throw and advances equipment time.
3. **Inventory response:** a flow, heat, pressure, valve-opening, speed, or level disturbance changes a physically relevant state in the expected direction.
4. **Mass balance:** integrated inlet minus outlet accumulation matches inventory change within a reasonable tolerance.
5. **Energy or temperature response:** when relevant, heat/work input changes internal energy, outlet temperature, or pressure consistently.
6. **Controller interaction:** if the equipment has controllers, verify one timestep calls/runs controller logic and applies bounded output.
7. **Bounds and robustness:** zero/low flow, empty inventory, small `dt`, and repeated timesteps remain finite and non-negative.
8. **Serialization/copy safety:** if new fields are non-serializable, mark them `transient`; otherwise verify normal equipment copy behavior still works.

Use physical assertions, not private implementation details. Prefer monotonic or bounded assertions over fragile exact transient values unless a regression baseline is intentional.

## Validation Commands

After editing Java files in the NeqSim repo, run:

```powershell
mvnw.cmd spotless:apply
mvnw.cmd test "-Dtest=YourDynamicTest"
mvnw.cmd spotless:check
```

When public APIs or JavaDoc were added or changed, also run:

```powershell
mvnw.cmd javadoc:javadoc
```

When only agent/skill files were edited, run:

```powershell
python devtools/verify_skills_agents.py
python devtools/generate_agent_skill_map.py
python devtools/verify_skills_agents.py
```

## Common Pitfalls

For `TwoFluidPipe` and related finite-volume code, include all three phase inventories in
rejection/positivity checks, even though the class name says two-fluid. Preserve phase identity
at exact water-cut endpoints and separate phase momenta through primitive/conservative recovery.
Distinguish explicit caller velocities from internal recovery: test legacy bulk updates after
recovery, recovered slip, and zero-to-positive phase appearance without overwriting donor momentum.
Pressure-correction face fluxes must share each donor phase's inventory budget. A positivity repair
that moves mass between phases can hide a phase-balance defect behind an exact total-mass balance.
Source-splitting helpers must not run primitive recovery on an invalid trial before its rejection
check; exercise the same negative-phase test with stiff source terms enabled.
Rollback must restore configured closures and their accepted diagnostics, pressure and phase
densities as well as the state vector; a generic clone may discard transient closure fields.
Pressure and density must use the same applied correction after bounds. Independent cell clipping
can reverse Newton face directions and make an upwind active set cycle; test bounded convergence.
A substep budget
must never enlarge a CFL-limited step or silently truncate a requested interval; compare accepted
time with both equipment and solver clocks. Tracker overlay inventory is separate from Eulerian
inventory, so dissolution must not return mass that initialization never withdrew. Read
`docs/wiki/two_fluid_reporting_and_validation.md` for the distinction between numerical regression
coverage and currently disabled experimental qualification gates.

Conservative slug/film coupling uses subcell reconstruction with the Eulerian seven-variable
state as the sole inventory. Assert that every reconstructed variable averages back to its cell
value, including energy after momentum redistribution. Enthalpy reference shifts must not change
the reconstruction. Recover positive trace-phase velocities from their exact momentum/mass
ratios so primitive recovery thresholds cannot alter the numerical flux. Use one shared face
flux per phase, with independent oil/water donors when slip is enabled, and cancel the pressure
part using the same phase face holdups and pressures. Test gas-free and one-cell limits explicitly.
Subcell face holdup can greatly exceed its cell average: include the phase-inventory draining
time in the CFL limit, not only reconstructed velocities. Test a thin body crossing a cell face.
Closed boundaries must constrain the external flux after reconstruction; zeroing cell velocities
alone does not prevent a reconstructed slug from leaking through a valve. Prescribed feeds
belong on external face states, not in the evolving cell density/momentum/holdup inventory.
For total energy, transport phase enthalpy plus kinetic energy, include gravitational work,
and do not add stationary-wall friction dissipation as an external energy source.
Independent experimental comparisons must retain source-cell provenance, source-definition
ambiguities, missing predictions and predeclared tolerances. A prescribed-flow marker experiment
tests a closure; label an actual time-marching pipe comparison separately. Coordinate source
freezes across agents before builds because desktop background compilers can modify target/classes.
Test initially volume-exact alternating pressure and velocity modes before claiming convective-CFL
stability of a collocated pressure correction. Include pressure-correction face transfers in
component/thermal transport after acceptance; global energy conservation alone does not establish
local EOS/pressure-work consistency. Record slug crossings during accepted motion and distinguish
union-occupancy changes from internal endpoints or instantaneous merge/birth geometry.

Reject nonfinite or negative raw phase predictors even when adaptive retry is disabled;
fixed stepping must fail at the last accepted state instead of using a positivity repair
to hide lost phase inventory. Separate clock-resolution/invalid-step failures from actual
attempt-budget exhaustion. A configured minimum timestep must never enlarge a CFL bound.
Pressure-solve convergence must include prescribed boundary pressure as well as volume
closure, and reset/failed steps must clear stale correction results and transfer ledgers.
Exercise five-/six-variable legacy inputs after a populated seven-variable state, including
zero and trace liquid. Do not suppress positive trace holdup in an interior flux while
external faces still transport it. For variable areas, match the momentum flux's face area
in the geometric pressure source. At either boundary, preserve each positive phase holdup
directly; subtracting a nearly unit gas fraction can round a trace liquid to zero. Evaluate
all tracked-interface kinematics against the
same accepted geometry before moving any marker; count reverse inlet exits separately
from downstream outlet arrivals without depositing overlay mass into Eulerian cells.

- Forgetting to set `calculateSteadyState=false` in tests, so the test never exercises the new dynamic branch.
- Adding a dynamic branch that calls `run(id)` internally every timestep and erases the inventory state.
- Reading viscosity, density, or thermal conductivity after a flash without physical-property initialization.
- Updating outlet streams but not the internal inventory, or updating inventory but leaving outlet streams stale.
- Re-flashing every outlet stream after an inventory flash. For phase-separating equipment, rebuild outlets from the
   freshly flashed inventory phases, preserve configured valve-capacity flow rates, and initialize properties without
   another equilibrium flash. This keeps outlets current and avoids multiplying expensive CPA flashes per timestep.
- Introducing Java 9+ syntax such as `var`, `List.of`, `Map.of`, text blocks, or `String.repeat`.
- Adding `System.out.println` in tests or examples; use assertions or Log4j2 logger output.
- Adding non-serializable fields to equipment without `transient`.

## Handoff Checklist

Before considering a dynamic equipment implementation complete, confirm:

- The equipment has a documented physical capacity model or a documented reason why it remains algebraic.
- `runTransient(dt, id)` has both steady-state fallback and real dynamic branch behavior.
- New setters/getters have JavaDoc and Java 8-compatible signatures.
- Tests exercise the real dynamic branch and at least one process-level `ProcessSystem.runTransient(dt, id)` path when practical.
- Spotless and the focused test pass.
