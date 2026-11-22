# Reuse the qualified initial fixed-cell state

Base: d6dd0cd190ac32f04a08627fafdeff7f745566e5. This fixes the remaining registered
zero-component rejection recorded during the preceding Gibbs-reference work.
All controls are project-created synthetic SRK cases, not measured PVT or field data.

The precise restoration caller has already flashed, initialized density, refined
equilibrium and checked the named phase family. Restoring the cell previously
flashed that unchanged state again before its first volume check. In the retained
275K methane/n-butane0.95/0.05mol sample with n-heptane registered at0mol, that
second flash changes total moles to0.999999999998 before any gas withdrawal.
The occupied volume changes from1.6800354597386446e-4 to1.6800354597369639e-4m3:
a relative residual-1.0004457990173167e-12, just beyond the unchanged1e-12 bound.
The two-component control passes. The baseline trace/results retain both outcomes.

The precise route now checks its already qualified state first. It flashes and
refines again after a gas withdrawal, retaining all existing phase, component,
Gibbs, equilibrium and volume checks. The private precise-entry precondition is
documented and has only the existing protocol caller. The legacy scan still
flashes before every restoration check. No tolerance, EOS parameter, public API,
serialized contract, experiment/sample/separator provenance or field model changes.

## Qualification and limits

`FixedCellInitialStateTest` covers54 nearby zero-registration paths (three scales,
six insertion orders and paired nearby T/P), plus81 paths against the existing
independent binary SRK cubic/common-tangent/lever reference. The latter reuses
`../fixed-cell-transitions/reference.json` without changing it or its1e-8mol
comparison bound. The independent Python predictor, constants and provenance are
retained beside that reference; no NeqSim calls or EOS fits generate those values.
There are135 zero-registered paths/999endpoints and9 two-component controls/45endpoints.
This is bounded independent numerical consistency, not laboratory calibration,
raw-measurement accuracy or held-out field validation.

Named zero inventory and its withdrawal must remain exactly0. The first snapshot
volume equals its declared initial reference exactly; all endpoints retain the
1e-12relative-volume target. Caller serialization is unchanged. Named component
closure and zero-registration versus absence use1e-10*max(1,initial)mol bounds.
No absent-phase properties are invented. Water/aqueous, absent gas, critical,
unclosed or other unsupported paths retain their existing rejection semantics.

## Reproduction and runtime

Freeze inputs/bounds in SCOPE.md before comparing. Run the new JUnit on the exact
base: it fails at the overshoot gate. Run it on the candidate together with the
existing dry-transition, fixed-cell-handoff, feasible-Gibbs and legacy CVD tests.
The validation receipt records broader selection, actual exits and limitations.

Runtime uses the existing `../fixed-cell-transitions/FixedCellRuntimeProbe.java`:
350K methane/decane0.6/0.4mol at200/100/50bara. The adjacent driver is copied from
the existing driver with only the predeclared1e-10relative checksum-equivalence
bound; raw checksums are retained. Use separately compiled base CVD classes,
candidate classes/resources/dependencies, heap128/512MB,50warmups,20trials per
sample,8samples per fork and6alternating pairs. `runtime.json` retains every pair.
Observed geometric median ratio0.9528174537, pair range0.6575032920–1.1798595206.
Shared-runner variance remains material: no general speedup/no-regression claim.
The source removes a redundant precise-route flash and adds no thermodynamic
work to ordinary TPflash or the legacy scan.

Existing19SpotBugs findings remain identical on base/candidate (both checks exit1,
zero analysis errors); this is not a clean SpotBugs pass. Checkstyle/Javadoc and
all actual test/format/docs/hook/inventory/coverage evidence are in VALIDATION.md.
