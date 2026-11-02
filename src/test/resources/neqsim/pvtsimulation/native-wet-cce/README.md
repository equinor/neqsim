# Independent wet closed-mass CCE references

Author-generated synthetic classic SRK references; numerical consistency only,
not measured water/hydrocarbon accuracy. No EOS input is fitted to these outputs.
Whole-cell CCE retains every component, unlike wet CVD/DLE, which remain unsupported.
No absent phase density, composition or modified-black-oil property is invented.

`independent_srk_cce.py` solves nine methane/water0.5/0.5 coexistence states:
340/350/360K and300/150/50bara. `independent_srk_ternary.py` solves three
methane/n-decane/water0.6/0.2/0.2 states at350K and200/100/50bara. Both are
one-mole feeds, unshifted SRK with R=8.3144621J/(molK). Tc, Pc, acentric
factors and actual classic-mixing kij are shared explicit database/model inputs.
The binary methane/water kij is0.45; ternary methane/decane is0.041099999,
methane/water0.45 and decane/water0. These parameters are not a measured calibration.

The cubic-root/departure-function equations reuse the existing native-depletion
SRK reference. Each phase's composition is normalized; log-fugacity equalities
must close within1e-10, and trivial equal-composition roots are rejected. Binary
phase amounts follow the lever rule. For three phases, six fugacity equations
fix the compositions and a three-by-three component balance fixes phase amounts;
all must be positive. Whole-cell volume sums phase amounts times molar volumes.
The ternary case is a bounded, declared separated solution, not a global phase
stability proof. It cannot establish near-critical or general wet-EOS accuracy.

Before comparison, freeze relative volume and absolute phase composition/amount
bounds at1e-8. Binary maxima:1.0681e-13 relative volume,1.0470e-13 gas fraction
and2.0806e-13 composition. Ternary maxima:1.7153e-13 volume,3.4284e-13 fraction
and3.5972e-13 composition. Full native exports and independent reference JSON
are retained. Normal and optimized Python produce identical reference outputs.

Run each independent script from the repository root with Python3/NumPy/SciPy,
redirecting stdout to a temporary JSON file and comparing with the committed
`reference.json` or `ternary-reference.json`. Exact Java exporters are
`neqsim.pvtsimulation.util.WetCceBenchmarkProbe` and
`neqsim.pvtsimulation.util.TernaryWetCceBenchmarkProbe` under test sources; pass
an output JSON filename as their sole argument. Use exact project main/test
classes and dependency classpath. Both compile with Java8 APIs.

`WetCceHandoffTest` exercises the public native handoff against these predictions,
plus a separately bisected pure-water SRK pressure-equation limit and a
single-phase wet-gas limit. Original/tuned checkpoint selection uses declared
synthetic unshifted/volume-corrected revisions, never a measured tuning claim.
Snapshots, serialization and aqueous separator products preserve every named
component and the fixed final surface basis (25C,101.325kPa absolute). Laboratory
calibration/holdout evidence remains separate from reservoir matching/holdouts.
In-place volumes are not recoverable reserves.
