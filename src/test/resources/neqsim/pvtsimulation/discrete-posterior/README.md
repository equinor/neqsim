# Finite-scenario in-place parameter and forecast quantiles

This optional, public synthetic benchmark qualifies the existing
`ReservoirHistoryEnsemble` object workflow and its marginal parameter quantiles.
Read [SCOPE.md](SCOPE.md) for the frozen inputs, bounds and stop boundary. It reuses
the separately qualified compositional tank and finite segregated-water aquifer;
it introduces no new forward model, EOS fitting or ordinary simulation work.

## Reproduce

Evaluate the exports from the work-with-neqsim `prepare_maven.py` helper before
Maven, then run `run.py` with the selected Python interpreter in a source checkout.
It compiles current repository classes, compiles both drivers for Java 8, runs all
328 hypotheses, checks every retained endpoint/output, and runs the 36 exact
quantile endpoint controls. Outputs are under
`target/discrete-posterior-qualification/`; committed `native.json` is the complete
first qualified output, including its measured timing diagnostics.

`reference.py --precision 60 --output <path>` independently regenerates
`reference.json`. The complete file is byte-identical at 60 and 75 Decimal digits.
The reference imports no NeqSim code. The data and code were created for this
repository from explicit synthetic assumptions; no third-party measurements,
training data, licensed PVT reports or confidential field histories are reproduced.

## Model, quantities and protocol

| Input/output | Definition and units |
| --- | --- |
| Fluid | Pure methane, 400 K, initially 200 bara; classic SRK or explicitly supplied PR alternative, volume correction disabled |
| EOS constants | R=8.3144621 J/(mol K), Tc=190.56 K, Pc=45.99 bar, acentric factor 0.0115; coefficients stated in `reference.py` |
| Original/tuned | Identical immutable checkpoints within each explicitly supplied EOS scenario; no laboratory or reservoir retuning |
| Initial volume | Total initial pore-volume hypothesis, reservoir m3; midpoint grids over 800–1200 m3 |
| Aquifer | 100,000 reservoir m3, capacity C=2.5/5/10 m3/bar, total compressibility C/100000 in 1/bar; initial pressure 200 bara |
| Productivity | Midpoint grids over 0.0001–0.0009 reservoir m3/(s bar) |
| Flow history | Four 2500 s intervals, each withdrawing methane equal to 4% of the independently computed initial inventory in a 1000 m3 SRK tank, times an explicitly supplied factor 0.9/1/1.1; injection is zero |
| Observations | Independent synthetic truth at volume 1000/C 5/J 0.0005, with fixed offsets +0.3/−0.2 bar on two calibration pressures; sigma 1 bar |
| Holdout/forecast | Third pressure is synthetic truth+0.1 bar, excluded from likelihood; fourth pressure is prediction-only |
| Water | Segregated immiscible reservoir-volume support; no water mixing or production through the tracked methane ledger |
| Prior | Complete joint cell probability masses; uniform cell integrals or three-point tensor Gauss quadrature of the explicitly stated conflicting correlated prior density |

There is no standard/separator volume conversion or laboratory CCE/CVD/DLE protocol
in this numerical uncertainty fixture. Methane withdrawals are prescribed inputs,
not independently predicted produced-phase composition/GOR. Original laboratory
calibration/holdout and measured reservoir validation remain separate acceptance.

The reference independently solves the **discrete linear-pressure-path** implicit
Fetkovich/tank equations using Decimal bisection and explicit SRK/PR pressure. It
then computes Gaussian calibration likelihoods and finite probability sums without
calling the native integrator. This establishes the supplied discrete numerical
contract. The separate [continuous coupled ODE benchmark](../coupled-aquifer/README.md)
retains timestep/convergence evidence; this fixture does not assert continuous-time
or radial-transient equivalence.

## Complete numerical evidence

Ten cases contain 328 hypotheses, one explicitly infeasible inventory, and 1,635
retained states including initial states (1,308 interval endpoints). All original
and tuned checkpoint serialization checks pass. The complete reference/native
arrays, all posterior outputs and rejected diagnostics are retained.

| Independent comparison | Maximum discrepancy | Frozen bound |
| --- | ---: | ---: |
| Reservoir pressure | 3.363e-8 bara | 2e-5 bara |
| Segregated water | 1.761e-7 m3 | 2e-4 m3 |
| Methane inventory | 2.794e-9 mol | 1e-6 mol |
| Posterior mass | 4.564e-9 | 2e-6 |
| Volume mean | 4.587e-7 m3 | 1e-3 m3 |
| Parameter quantiles | Exactly equal supplied support values | Per-parameter bounds in SCOPE.md |
| Pressure quantiles | 3.277e-8 bara | 2e-5 bara |
| Correlation | 3.504e-9 | 2e-5 |

Explicit EOS pressure and aquifer-storage closure residuals are at most
1.052e-12/1.137e-13 bara. The audit independently recomputes posterior weights from
retained native trajectories; the maximum discrepancy is 8.327e-17. It rejects
malformed, non-finite, incomplete or inconsistent outputs and requires finite
nonnegative evaluation/query timing evidence. Python assertion disabling does not
bypass it. The new audit tests retain 55 distinct corruption controls, plus three direct
reference CDF controls and the complete positive control.

## Sensitivity and interpretation

| Joint scenario set | Count | Mean initial volume, m3 | Volume/J correlation | Effective count |
| --- | ---: | ---: | ---: | ---: |
| Uniform 3x3 | 9 | 1013.8142 | −0.6965 | 5.5078 |
| Uniform 5x5 | 25 | 1013.6330 | −0.6898 | 15.0433 |
| Uniform 9x9 | 81 | 1013.5791 | −0.6893 | 48.3142 |
| Conflicting prior 9x9 | 81 | 1045.3793 | +0.2283 | 17.9659 |
| Capacity 2.5, 5x5 | 25 | 1034.9921 | −0.5795 | 15.1401 |
| Capacity 10, 5x5 | 25 | 1006.7787 | −0.7307 | 14.4545 |
| Joint C2.5/5/10, 3x3 | 27 | 1019.7662 | −0.6546 | 16.2277 |
| SRK/PR and 0.9/1.1 flow, 3x3 | 36 | 1000.6214 | −0.1639 | 17.1500 |

The joint-capacity scenario also retains volume/C correlation −0.1239. Adding a
failed inventory retains rejected prior mass 1/19 and leaves feasible relative
weights unchanged. Adding 100 bar only to the held-out pressure leaves posterior
weights unchanged and changes the independent holdout diagnostic.

The volume mean's small grid change is insufficient to establish continuous
posterior convergence. For example, lower/upper tenth/ninetieth quantiles are
866.67/1133.33 m3 on 3x3 and 9x9, but 920/1160 m3 on 5x5. Prior and support assumptions
have larger effects than that mean refinement. The conflicting prior changes the
volume/J correlation's sign. These diagnostics expose conditional uncertainty;
they do not identify geological parameters uniquely from two pressure observations.

`prior_quadrature_check.py` independently repeats the conflicting prior integration
with five-point tensor Gauss quadrature, preserving all predictions. Its retained
`prior-quadrature.json` shows posterior mass differences at most 1.520e-7 and volume
mean change 1.975e-6 m3; all reported quantiles are unchanged. This is prior
quadrature sensitivity, not continuous posterior convergence.

Parameter and pressure quantiles use the discrete inverse CDF. q=0/1 select the
minimum/maximum **numerically positive-mass support**, including tiny positive
tails. Numerically zero weights, including underflow, supply no support. Explicit
infinite capacity may yield an infinite quantile while its mean is undefined.
Each parameter is marginal: their returned values need not form one joint scenario
or credible region. Probabilities are lower-tail, exclude future measurement noise,
and are not petroleum exceedance percentiles or recoverable reserves.

## Baseline, performance and gates

Unmodified master 863f12f compiles all 3,676 main Java sources. The existing pressure
quantile loop fails 8/36 exact support checks when an upper-tail probability rounds
away during cumulative summation, omitting up to 0.725844 bara. The correction
passes all 36, including positive subnormal and zero-weight controls in both orders.
The new parameter method's five unit tests cover marginal semantics, CDF boundaries,
infinite/zero support, defensive ownership, serialization and invalid probabilities.

The first complete native evaluation takes 92.835 s; its 50 parameter plus 50 pressure
quantile calls across the ten cases take 0.005650 s in total. These are diagnostics,
not CI timing assertions. Five query-only ABBA blocks retain 20 fresh JVM runs and
all 360 timing samples: median interior pressure query 185.3 ns baseline/191.2 ns
candidate. This small diagnostic difference is not a simulation-speed claim.
The forward model and ordinary simulation call paths are unchanged; the new
parameter output is computed only when requested.

Generated JaCoCo evidence covers all 13 lines and all 10 branches of the new
parameter method. The shared inverse-CDF helper covers 11/12 lines; Python audit
line coverage is 98/115 (85.2%). These are measured counters, not a full-coverage
claim. Three optional full PVT-pipeline Python JVM cases remain unselected; the
current-source native posterior/endpoint drivers run separately.

See `validation.json`, `execution.log`, `query-timings.json`, generated coverage and
the #4740 ledger for executed commands, initial snapshot/environment failures and
their resolution. Source/PMD checks report no new findings against the reviewed
baseline. Hosted current-master Code Health static analysis independently fails
with 92 new finding groups; this predates this branch. Local full SpotBugs was not
claimed under OpenJDK17.0.20 because the reviewed baseline pins Temurin17.0.21+9.

The existing discovery/inspectApi registration, PVT/field handoffs and indexed
aquifer guide describe the new output without creating a static MCP operation.
Independent agent source review is retained separately from GitHub approval/CI.
Measured laboratory-to-field accuracy, broader wet/critical paths, full research
and multi-tank acceptance remain unresolved in #4740.
