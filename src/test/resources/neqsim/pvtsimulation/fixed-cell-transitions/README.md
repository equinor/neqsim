# Dry phase-changing explicit-cell reference

Public synthetic numerical benchmark; no measured accuracy or experimental EOS
tuning. Inputs are declared before comparison: classic unshifted SRK,
R=8.3144621 J/(mol K), methane/n-butane Tc=190.55999999999997/425.12 K,
Pc=45.99/37.96 bara, acentric factors 0.0115/0.2002, kij=0.01289789.
These are shared model inputs from the merged dry-tail fixture, not independently
measured observations. No candidate output is used by the predictor.

`independent_reference.py` evaluates the SRK cubic and departure fugacity, solves
binary chemical-potential common tangents with multiple starts, uses the lever
rule, then withdraws the endpoint gas composition to restore the first occupied
volume. The frozen matrix has well-separated coexistence compositions (the
independent reference rejects separation at most 0.01); critical limits are not
qualified. Both admissible cubic roots enter a fixed 257 point tangent-plane grid;
every local minimum is refined independently with bounded scalar minimization.
A minimum below -1e-9 rejects the prediction. This bounded binary audit is not a
general stability theorem. The final endpoint is independently reclassified after
withdrawal, preserving genuinely absent phases.

Equation provenance: the [Penn State cubic fugacity course](https://courses.ems.psu.edu/png520/print/m16_p6.html)
and [IDAES cubic EOS documentation](https://idaes-pse.readthedocs.io/en/2.5.0/explanations/components/property_package/general/eos/cubic.html).
The script is an original implementation of these equations; no paper/PDF or
proprietary field data is redistributed. Exact SRK critical coefficients replace
rounded display coefficients. Public-source access checked 2026-11-04.

27 complete paths / 243 states: 272.5/275/277.5 K; methane mole fractions .94/.95/.96;
100/60/50/30/20/5/2 bara,100/80/65/60/55/50/40/30/25/20/10/5/3/2 bara, and
60/50/30/20/5/2 bara. The first occupied volume is the explicit cell reference,
not an upper-dew estimate. The one-mole prediction scales extensively.

Reproduce with Python3.12.14, NumPy2.3.5 and SciPy1.17.0:

```
python3 independent_reference.py --output reference.json
python3 -O independent_reference.py --output reference-optimized.json
cmp reference.json reference-optimized.json
```

`DryFixedCellPhaseTransitionTest` uses both original/tuned selections and
0.001/1/1000 mol (1458 state executions), comparing phase topology/composition,
phase/retained/withdrawn inventories, occupied volume and every retained/withdrawn
separator product. The separator is 1.5 bara / 303.15 K then 1.01325 bara / 298.15 K;
all surface volumes are actual volumes at that final basis. Checkpoint labels are
separate numerical controls, not independent laboratory calibration.

Frozen bounds: volume 1e-8 relative; phase/component/withdrawn amounts 1e-8 mol
absolute at every scale; composition 1e-8 absolute; cell component closure
1e-10*max(1,initial_i) mol; separator component closure 1e-8 mol. Local production
refinement 1e-12 log-fugacity / relative volume targets are unchanged. Tests also check
serialized protocol identity, defensive outputs, reproducible restart, default
family-change rejection, wet/absent-gas rejection and gas-only tail refinement.
Metrics including elapsed time are written to target/dry-fixed-cell-transition-metrics.json.
Pressure refinement in a two-phase interval changes the laboratory experiment,
so each schedule is predicted separately; gas-only tail refinement is invariant.

Stop boundary: no wet transitions/upper-dew inference, arbitrary critical-state
qualification, modified-black-oil absent-phase properties, measured laboratory or
field accuracy, EOS fitting, reservoir matching, water support or Tran RCVD method.

The generator's optional `--limits-output limit-reference.json` independently
solves the lower dew at 275 K for methane fraction .95: 41.244353348885056 bara.
Six two-row experiments start at 60 bara and approach that noncritical boundary
from both sides with fixed offsets ±0.1/±0.01/±0.001 bara. The positive-side
liquid amounts fall from 5.515288553925579e-5 through 5.5343222306047934e-6 to
5.536228671054744e-7 mol. All negative-side states are gas only. The separate
JUnit limit test uses the same frozen amount/composition/volume bounds; no
absent-phase composition is assigned. Repeating the command with `python3 -O`
and the same two output arguments must reproduce both JSON files byte for byte.

The independent matrix exercises topology changes between pressure endpoints.
Binary tie-line compositions are fixed at each pressure, so nonzero liquid amount
is unchanged during that endpoint's gas withdrawal. This does not independently
qualify topology changes during a multicomponent withdrawal, arbitrary EOS inputs,
or a critical root merger. Those require their own independent qualification.

## Consumer, runtime and publication receipt

[VALIDATION.md](VALIDATION.md) records executed commands, results, unchanged-base
static findings, runtime uncertainty, documentation impact and acceptance limits.
`validation-results.json` is the machine-readable summary. `check_handoff.py`
requires JPype and a resolved candidate classpath; its committed output confirms
separate declared original/tuned volume bases and immutable input ownership.

Runtime reproduction requires compiling the exact base CVD/generator sources
separately against the same resolved dependencies, then compiling
`FixedCellRuntimeProbe.java` against the candidate classes with Java 8 targeting.
Use `compare_runtime.py --classpath <candidate-classes/resources/dependencies>
--baseline-classes <base-output> --probe-classes <probe-output> --base-revision
<base-sha> --output <json>`; optional `--warmup-trials` and `--trials-per-sample`
select the two recorded protocols (10/10 and 100/50). The raw fork results remain
inconclusive. `compare_runtime_in_process.py` takes the same classpath/base/probe
arguments plus `--output`; it checks two fully isolated NeqSim worlds, preserves
paired wall/thread-CPU samples and has a fixed protocol. It is a diagnostic,
not a replacement for end-to-end production performance qualification.
