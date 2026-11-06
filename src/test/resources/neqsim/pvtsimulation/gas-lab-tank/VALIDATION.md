# Gas-only independent-chain validation

Base: b20e324a60a94778b11425257bb3d8e9636389b7. All10,071base files matched
fresh connector blob hashes before editing. Unmodified-master phase-withdrawal
control:9tests,0failures/errors/skips. No production Java/API/default/dependency
change; only qualification, documentation and capability evidence is added.

## Numerical results under frozen bounds

The independent60/75digit Decimal generator gives byte-identical JSON. A synthetic
400K/200bara closed-cell point identifies kij0.02 algebraically before any tank
matching. Original kij0 remains independently owned. The separate whole420K CCE
has maximum original relative volume discrepancy0.0011326264 and calibrated
discrepancy2.22044605e-16. These are numerical model comparisons, not measurements.

Independent1017.3m3 tank volume is recovered as1017.299992527677m3:
absolute error7.472323e-6m3, below the frozen0.001m3 bound. Maximum direct/fitted
pressure errors5.684342e-13/8.825393e-8bara are below1e-6bara. Maximum fitted
produced-component amount error3.921023e-6mol is below1e-4mol. Maximum fitted
surface-volume relative error1.850742e-13 is below1e-7.

Fourth-interval held-out pressure chi-square3.115502e-14; shifting only that
observation by+5bara gives100.00000353 and **exactly the same fitted volume**.
Held-out produced composition and actual surface gas volume are independently
checked from the fitted replay as well as the prescribed true-volume replay.
No oil exists: stock-tank oil volume0 and GOR undefined are recorded explicitly.
Rejected small-volume trials do not contaminate replay; serialized checkpoints
remain byte-identical. Full original/calibrated CCE and native tank/fit outputs
are retained, with the original EOS discrepancy unmodified.

## Actually executed checks

Final default PVT selection:83source classes,81executed classes/**681tests**,
0failures/errors/skips, exit0,204.167s including explicit JaCoCo prepare/report.
Existing slow-tagged CO2CorrosionAnalyzerTest and
HydrocarbonScrubberSaturationPressureStabilityTest remain excluded by the default
configuration. Standard coverage covers production classes; JUnit records the
two new test methods.41tooling tests pass (pytest10.01s).

Java8 API compilation exits0, generated class major52; actual local runtime is
17.0.20. Private doclint exits0 with no warnings. Scoped changed-test Checkstyle
reports0issues; strict scoped SpotBugs reports0findings/analysis errors/missing
classes and confirms the changed class is an input. These scopes do not claim a
new clean production-wide static pass. No ordinary-simulation timing claim is
made: production source is unchanged. New test timing is test cost only.

Publication requires direct stable Spotless apply/check, documentation search,
generated inventory freshness and both current hook stages. Their executed
commands/results are recorded in commands.json and publication-gates.json.
Hosted exact-head CI/reviews remain separate, pending after draft publication.

## Authoring corrections retained

The initial two-test version passed but review found a mislabeled calibration
experiment identity. A subsequent clean681-test run had one authoring error:
the new single-point calibration call used the native CCE adapter, which correctly
requires a complete pressure path. It was corrected to a TP flash of that isolated
calibration point. The model now records only the400K calibration ID, asserts
that identity and rejects its reuse as a holdout. The whole420K native CCE stays
separate. The corrected four-class suite and final681-test run both pass.
No EOS input, expected numerical value or tolerance was changed for either fix.
The failed command (exit1,227.435s) and corrected executions are retained in
commands.json; the error is not attributed to NeqSim's CCE contract.

## Documentation and scientific boundaries

Updated the existing indexed reservoir-phase guide, PVT-agent handoff, capability
test mapping/limitations and generated inventory. All source/input/output units,
sample, experiment, EOS version and separator bases are declared in README.md.
No notebook, new runtime framework, public API or new MCP operation is added.

This completes the bounded **gas-only synthetic independent-code chain**. It
does not complete measured laboratory/field acceptance, two-phase GOR, critical
paths, aquifer/Bayesian uncertainty, multi-tank or full Tran2025 reproduction.
Pressure sigma0.5bara is a declared synthetic scale, not converted experimental
uncertainty. The precise volume recovery is not a confidence interval, and in-place
volume is not recoverable reserves. #4740 must remain open after this draft.
