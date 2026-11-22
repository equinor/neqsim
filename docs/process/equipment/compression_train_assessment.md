---
title: Compressor Off-Design Coordinates and Train Candidate Assessment
description: Actual versus mass map flow, EOS acoustic similarity and net-export energy assessment with explicit constraints.
---

# Compressor off-design coordinates and train candidate assessment

NeqSim already provides compressor map backends, MW-map interpolation, capacity constraints,
operating-point snapshots, `CompressorOptimizationHelper`, `ProductionOptimizer`,
`CompressorAntiSurgeApplication` and minimum-speed pressure/recycle coordination.
Use these models to solve the process before comparing candidate operating settings.

## Public map queries

`CompressorChart.polytropicEfficiency(flow,speed)` now delegates to the selected backend's
actual efficiency calculation (percent) instead of returning 100. `checkSurge1(flow,head)`
uses an active explicit head-based surge curve. `checkSurge2(flow,speed)` and
`checkStoneWall(flow,speed)` compare with digitized minimum/maximum flow endpoints at the
nearest map speed. Equality is not classified as outside the boundary; it is not an
independent adequate surge-control margin either.

Missing/nonfinite boundary evidence raises an exception rather than returning an unconditional
false. Invalid coordinates are rejected. The digitized endpoints are screening bounds;
only OEM data identifies a physical surge/choke boundary. Speed-based query methods can
use a nearest curve outside the digitized speed range: inspect speed bounds separately.
The lowercase `getStonewallFlowAtSpeed` dispatches to `getStoneWallFlowAtSpeed`, so the
MW interpolation backend can supply the appropriate maximum-flow bound.

## Acoustic similarity and flow basis

`CompressorMapSimilarity` explicitly transforms coordinates at fixed geometry and matched
inlet Mach number/flow coefficient. Construct it with reference/actual sound speed in m/s
and reference/actual density in kg/m3, or use `fromFluids(reference,actual)`, which flashes
owned clones, verifies single-phase gas and reads EOS isentropic sound speed and density.
The original fluids and compressor chart remain unchanged.

With $r_a=a_{ref}/a_{act}$ and $r_\rho=\rho_{ref}/\rho_{act}$:

$$Q_{ref}=Q_{act}r_a,\qquad N_{ref}=N_{act}r_a$$

$$H_{ref}=H_{act}r_a^2,\qquad \dot m_{ref}=\dot m_{act}r_a r_\rho$$

$Q$ is actual suction volume in m3/hr, $N$ is rpm, $H$ is kJ/kg and $\dot m$ is kg/s.
Normal/standard volume is a different quantity and cannot be passed as actual suction flow.
Inverse volume/mass transforms support a reference stonewall screening estimate.
For the same ideal gas at unchanged temperature, doubling pressure preserves sound speed
and actual-volume coordinates while doubling density and achievable actual mass flow.
Temperature and composition also change sound speed; pressure can change real-gas sound
speed through the EOS. These are similarity coordinates, not proof that efficiency, surge
or choke curves remain invariant under arbitrary composition/real-gas changes.

The existing `CompressorCurveCorrections.calculateSonicVelocity` uses the approximate
$\sqrt{k Z R T/M}$ expression. It is not the exact real-gas isentropic derivative used by
`fromFluids`. The new API does not alter the legacy empirical correction helpers or claim
ASME PTC-10 conformance. Reynolds number, Mach range, polytropic path and OEM applicability
still require qualification.

## Whole-train candidate comparison

`CompressionTrainAssessment` reads already-converged compressor bodies and a caller-identified
net export stream. It sums each body's actual shaft power once, checks the aggregate power
budget, stage operating-point/pressure/capacity evidence, wet suction, required map evidence,
digitized flow/speed range and caller-selected minimum surge/stonewall margins.
It also checks actual net export pressure and positive single-phase gas export.

Specific shaft energy is:

$$e_{shaft}=\frac{\sum_i P_{shaft,i}}{\dot m_{net,export}}$$

Power is kW and net export is kg/s, giving kJ/kg. Serial stage flows are not summed as
production. Gross recycled gas is not substituted for net export. Choose a consistent net
stream after recycle takeoff. The API cannot infer topology, convergence, hidden constraint
providers or stale model state; these are responsibilities of the enclosing process solve.
A chartless thermodynamic case can be assessed only when `requireMaps=false`; that choice
cannot qualify surge/choke protection or a speed limit.

`lowestSpecificEnergy` selects only feasible candidates and returns null if none passes.
A low-power candidate that misses pressure or violates a driver/equipment limit cannot win.
The result is a detached snapshot suitable for bounded cooler-temperature, HP2-speed,
pressure and recycle/load-sharing sweeps. It does not install a continuous load-sharing
controller or run an automatic optimization.

Define separate assessments for separate shaft-power-budget groups. This initial aggregation
does not validate common-shaft speed/torque, motor efficiency, cooler/coolant power, separator
capacity, antiwindup, valve response, train-trip dynamics or controller authority. Evaluate these
with the existing enclosing models and the acceptance cases in
[control/load-sharing #4146](https://github.com/equinor/neqsim/issues/4146),
[train optimization #4147](https://github.com/equinor/neqsim/issues/4147) and
[off-design maps #4148](https://github.com/equinor/neqsim/issues/4148).

## Validation evidence

`CompressorChartQueryTest` exercises two map backends, missing/invalid evidence, equality and
MW-interpolated endpoints. `CompressorMapSimilarityTest` checks analytical identity/round-trip,
ideal temperature/pressure invariants, EOS composition/T/P trends and rejection of liquid states.
`CompressionTrainAssessmentTest` solves a synthetic methane/ethane two-stage train, compares
300 K and 340 K interstage cooling on the same 10 kg/s net export basis, checks summed power
and specific-energy units, and rejects inadequate pressure, power/capacity limits, missing map
evidence, duplicates and missing/zero export. This qualifies software screening and physical
invariants, not off-design OEM maps or closed-loop dynamic performance.

See [compressor controls](compressor_antisurge_control.md),
[compressor curves](compressor_curves.md) and [program roadmap #4149](https://github.com/equinor/neqsim/issues/4149).
Primary sources for the physical/control basis are
[NASA compressible mass-flow equations](https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/mass-flow-rate-equations/)
and [CCC antisurge/control coordination](https://process.honeywell.com/us/en/products/turbomachinery-automation-systems/ccc-control-software/compressor-control/antisurge-control).
