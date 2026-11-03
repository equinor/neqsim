# Independent closed-mass CCE reference

`independent_srk_cce.py` computes six nondegenerate binary SRK coexistence states
without calling NeqSim. It reuses the declared cubic-root/fugacity equations of
the earlier native-depletion oracle; component data and gas constant are shared
explicit inputs, not fitted parameters or independent experimental data.

One mole methane/n-decane at 0.70/0.30 and 323.15 K, or 0.95/0.05 and 373.15 K;
pressures 200, 100, 50 bara. Classic SRK, no volume translation, kij=0.041099999,
R=8.3144621 J/(mol K); Tc/Pc/omega are explicit in the script. Solve equality of
both component log fugacities, rejecting equal-composition roots. The lever rule
at the fixed feed composition gives gas fraction; the sum of both phase amounts
times independently calculated molar volumes gives whole-cell volume. No gas or
liquid is withdrawn, unlike CVD and DLE.

From the repository root, with NumPy and SciPy available:

```sh
python3 src/test/resources/neqsim/pvtsimulation/native-cce/independent_srk_cce.py
```

The complete output is retained in `reference.json` and consumed by
`NativeCceHandoffTest`. Frozen before comparison: volume within1e-8 relative,
gas mole fraction and both present-phase compositions within1e-8 absolute;
named component conservation within1e-10 mol for the one-mole fixture. Pure
gas/liquid references independently bisect the SRK pressure equation in Java.
Absent phase compositions are not supplied. Separate phase-limit tests check the
closed-mass gas/oil/gas path; no CVD phase-disappearance support is implied.

This is numerical/model consistency, not a measured-data fit, EOS accuracy claim,
saturation estimate or recoverable-reserves calculation. CCE absolute snapshots
do not depend on the legacy saturation scan or its relative-volume arrays.
Original/tuned sample checkpoints and experiment/separator provenance remain
independent of reservoir-volume matching. The existing CVD reference failure and
the separate shared saturation repair remain unresolved acceptance dependencies.
