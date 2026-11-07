# Synthetic two-inventory safety study

`neqsim.process.safety.scenario.AdvancedV101SafetyStudy` is a public executable Java fixture. `AdvancedV101SafetyStudyTest` runs it with the native strict dynamic scenario runner. Its `main` accepts an output directory, optional timestep in seconds, and optional profile names; without profile names it executes all 18 profiles. Each execution exports the actual native result JSON and physical event-window CSV. The command-line entry point is covered by a JUnit export test.

To execute the integration tests and export the exact tested cases, run the repository's Maven wrapper with `-Dtest=AdvancedV101SafetyStudyTest -Dneqsim.advancedSafety.output=target/advanced-safety-study test`. The fixture has no Python or prior-study dependency.

## Physical basis

A 20,000 kg/h feed at 35 °C and 45 bara passes through `20-ESDV-001`, the real `10-V-100` surge inventory, and `20-HIPPS-XV-001` into protected separator `20-V-101`. V100 and V101 start at 44 and 40 bara. Their geometries are diameter × length = 0.6 × 1.5 m and 1 × 3 m, with initial liquid-height fractions 0.3 and 0.5. V101 gas exports through `20-PCV-001` to 35 bara. Each vessel has an actual liquid Cv valve to 20 bara. All valves have calibrated synthetic Cv values; these are not vendor selections.

The model uses SRK EOS with classic mixing. Feed mole fractions are methane 0.780, ethane 0.080, propane 0.050, n-butane 0.025, i-butane 0.015, n-pentane 0.025, n-hexane 0.015, CO₂ 0.008, and nitrogen 0.002.

The physical source retains ordinary material topology for steady initialization and engineering export. Each isolated dynamic case installs two numerical pressure-coupling SetPoints and orders the interstage Cv solve before both inventories. The same current interstage transfer is therefore consumed by both vessels. The numerical helpers are not physical P&ID equipment. Endpoint streams can be staggered after inventories refresh their outlet composition; continuity is assessed immediately after each valve solve, and conservation uses the actual ordered consumed boundaries.

## Native control and protection

| Function | Actual inputs | Native voting | Actual final element |
| --- | --- | --- | --- |
| BPCS pressure | `20-PT-001` | Native PI, 40 bara setpoint, Kp 16 %/bar, Ti 20 s | `20-PCV-001` |
| BPCS levels | `10-LT-001`, `20-LT-001` | Separate native PI loops, Kp 4 %/%, Ti 60 s | `10-LCV-001`, `20-LCV-001` |
| `SIF-ESD-001` | `10-PT-002` at 44.5 bara; `20-PT-002` at 41.8 bara | 1oo2 | `20-ESDV-001` |
| `SIF-HIPPS-001` candidate | `20-PT-101A/B/C`, 41 bara each | 2oo3 | `20-HIPPS-XV-001` |
| `SIF-MANUAL-001` | Actual `20-HS-001` push button | 1oo1 | Both isolation valves via native parallel trip actions |

The three protection functions use actual `ClosedLoopSafetyFunction`, measurement-backed held channels, exact native `ESDLogic`, and actual `TripValveAction` objects. Manual shutdown uses `ParallelActionGroup`. The HIPPS candidate uses an actual finite-stroke `ESDValve`; the legacy HIPPS class's immediate command and stored closure-time metadata are not substituted for measured travel. The nominal 0.5 s actuator stroke describes travel from this model's initial 50% opening to fail position 0%, not a vendor full-stroke specification.

Pressure input qualification is 0.1 s of continuous demand on already held samples; automatic logic delay is 0.1 s. This qualification timer is not a calibrated sensor lag. Qualified channel trips latch until reset. Consequently, nonoverlapping qualified pulses can accumulate a 2oo3 vote; this behavior is explicitly exercised. Bypass retains fixed voting cardinality. Distinct channel identities do not establish installed hardware independence or independent protection layers.

## Profiles and evidence

The 18 profiles are `NORMAL`, `CONTROL_RAMP`, `ONE_FALSE_HIGH`, `TWO_FALSE_HIGH`, `TRUE_DEMAND`, `ONE_STUCK_NORMAL`, `TWO_STUCK_NORMAL`, `ONE_BYPASSED`, `TWO_BYPASSED`, `HIPPS_FAILED`, `HIPPS_SLOW`, `HIPPS_INHIBITED`, `ALL_INHIBITED`, `ESD_ONLY`, `MANUAL`, `PAUSE_RESUME`, `ASYNC_QUALIFIED_PULSES`, and `MANUAL_RESET_RETRIP`.

False-high and stuck-normal profiles inject native transmitter `STUCK_AT_VALUE` faults, rather than fabricating channel evidence after the physical scan. Failure to close sets actual actuator availability false; the slow profile changes actual stroke time to 6 s. Inhibition pauses actual protection. Reset/retrip reads current held input permissives and the current push-button state, preserves first-cycle native evidence, resets bookkeeping, and requires an explicit separate operator reopening. Reset never automatically reopens a valve.

The ordinary scenario lasts 12 s with an event at 1 s; the BPCS opening-ramp scenario lasts 60 s. Actual registered held measurements, their physical-step UUID freshness, each real valve's opening/ports, both vessel inventories and all component inventories are exported. The actual SIF trace may stop when its final element completes; the separate physical trace continues through the full horizon. Paused logic's active elapsed time is distinct from wall time. Command timestamps are distinct from actual closure samples for each valve.

Pressure envelopes use native `MAINTAIN_BOUNDS`, covering every sampled value in the full declared event window. Isolation criteria require actual zero opening within 3 s. Both manual final elements have explicit closure criteria. These tests do not claim continuous behavior between samples.

Negative controls intentionally retain native simulation FAIL results. A successful JUnit test can assert that failed, slow, inhibited or insufficient-vote protection remains a failed functional response. A backup ESD closure does not give a missing HIPPS closure credit.

## Separate numerical and engineering gates

Both separators enable native cumulative energy diagnostics. The frozen relative energy criterion is maximum absolute cumulative debt divided by the absolute initialized vessel internal energy, with a limit of 1e-5. Per-vessel numerical criteria are explicitly optional in the required-response verdict, so a response PASS cannot erase an energy FAIL. Per-vessel coverage and numerical verdicts are exported separately, and engineering qualification remains HOLD.

The independent study audit must integrate raw enthalpy, mass and component boundaries immediately before each vessel consumes them, compare its ordered replay with every strict-run physical sample, and retain each vessel's energy result. A whole-slice residual can cancel local errors. The fixture labels an external independent audit as separately required; it does not present its own native-ledger sum as an independent oracle.

This is an executable synthetic design and fault-study fixture. It supplies traceable candidate inputs to engineering proposals and drawings. It does not approve a safety requirements specification, sensor common-cause assumptions, SIL, ISO/IEC compliance, relief sizing, installed hardware, or construction.
