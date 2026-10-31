# Interior hydrostatic pressure reconstruction

## Scope and provenance

This public analytical fixture qualifies the finite-volume interior pressure/body-force balance
implemented by `TwoFluidConservationEquations`. It was derived from the independent
`GravityInteriorEnergyAudit` attached to the durable #3298 campaign record on 2026-10-31. That
audit reproduced the unmodified-master limitation before the production operator was changed.
It is an analytical numerical benchmark, not experimental or field qualification.

The fixture constructs an exact piecewise-constant hydrostatic state. For cell `i`,

`pFace[i+1] = pFace[i] - rho[i] * g * (zFace[i+1] - zFace[i])`

and the stored cell pressure is the average of its two bounding face pressures. The production
operator reconstructs a common internal-face pressure from the adjacent conservative mixture
inertias and signed half-cell gravity heads. It applies the same correction to momentum traction,
phase-pressure diagnostics, and total-energy pressure work.

## Predeclared matrix and gates

`HydrostaticPressureReconstructionTest` executes all 90 combinations below:

- terrain: flat, affine, and the public 5 km rich-gas terrain profile;
- density: constant, smooth sinusoidal variation, and alternating 0.75/1.25 factors;
- mesh: 12, 24, 48, 96, and 192 cells;
- present phase: pure gas at 10 kg/m3 or pure oil at 800 kg/m3 before density factors.

A separately declared 45-case aqueous extension repeats the same terrain, density and mesh matrix
for pure water at 1000 kg/m3. It strengthens three-phase-path coverage without changing the
predeclared 90-case hydrocarbon basis.

Every case must satisfy all of these unchanged gates:

- maximum absolute acceleration below `1e-10 m/s2`;
- exactly zero local phase-mass rate;
- exactly zero local total-energy rate;
- one finite reconstruction diagnostic for every internal face.

The signed-work controls use actual AUSM face fluxes at -2 and +2 m/s and require the added
energy flux to equal `Q * deltaP` with the same donor density as mass transport. The absolute
comparison allowance is `5e-8 W`; this covers subtraction roundoff at an approximately 3.2 MW
total face flux and is not a conservation tolerance. A horizontal control requires an exactly
zero correction, and a transactional trial must restore the accepted diagnostic ledger exactly.

## Result and capability boundary

On the implementation head, all 90 hydrocarbon cases, all 45 aqueous-extension cases and all
signed-work, horizontal-compatibility, and rollback controls pass. The five-method class took about
0.12 seconds in the shared CI runner after compilation; this is a test-runtime observation, not a
production throughput claim.

The production change adds constant work per internal face and reuses its `N-1` diagnostic array
when the mesh size is unchanged. It does not introduce another nonlinear iteration, flash, or
per-phase history structure.

The unchanged opt-in explicit-face Tengesdal five-second gate remains failed. Against exact master
`3c1c5dda4`, the candidate changes the 16-cell 0.1 s failure time from 1.101171875 to 1.1 s, the
16-cell 0.05 s time from 1.15625 to 1.5080078125 s, and the 24-cell 0.05 s time from 0.57265625 to
0.5884765625 s. Every case still fails line search at the unchanged `1e-9` nonlinear tolerance;
the nearby-point progress is not a five-second or physical qualification claim.

The reconstruction balances total mixture momentum for a common mechanical pressure. It does not
claim that multiple stationary phases with different densities share an exact hydrostatic gradient,
does not validate a moving gas/liquid interface or a terrain pool, and does not qualify slugging,
shutdown/restart, phase-transfer rates, pigging, or commercial-simulator parity.
