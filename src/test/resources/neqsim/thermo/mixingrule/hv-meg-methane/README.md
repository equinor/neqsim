# Staged SRK-HV MEG/methane declared-input diagnostic

This fixture is not an activated production profile or an original-author reproduction.
It advances the independent calculation retained in campaign receipt
[6713997329](https://github.com/equinor/neqsim/issues/4576#issuecomment-6713997329)
to native NeqSim numerical qualification. No database default changes.

## Inputs and lineage

The six liquid methane measurements are dataset 1 in the public NIST ThermoML
representation of Folas et al. (2007), DOI10.1016/j.fluid.2006.11.001. Compiler
expanded 95% absolute mole-fraction uncertainties are retained separately from
original-author uncertainty, which remains unresolved. This fixture preserves the
campaign web-reader representation; it makes no raw-original-byte identity claim.
Original journal table/header images are not verified. Gas dataset 2 has an
unresolved component label and is excluded. No reverse measurement is manufactured.

The binary SRK tuple comes from Folas thesis Table6.2, with MEG first:
D12=181K, D21=2274K, DT12=DT21=0, alpha=0.07. The model is infinite-pressure,
covolume-weighted modified NRTL with linear covolume and tau=D/T+DT,
D12=(g12-g22)/R and D21=(g21-g11)/R (MEG=1, methane=2). It is not
ordinary NRTL, MHV1/MHV2, CPA or PRSV. No PR transfer is attempted.

MEG uses the already retained Derawi inputs Tc720K, Pc82bara, omega0.5347 and
Mathias-Copeman (1.1121,0.9679,-1.4200). The declared methane inputs are
Tc190.56K, Pc45.99bara, omega0.0115 and ordinary Soave alpha. Exact original-author
methane inputs remain missing. The alpha-only control uses ordinary Soave for MEG
with the same declared critical inputs. Temperatures273.25/283.29K fall below the
MEG alpha fit interval Tr0.4-0.9. No continuous-range or extrapolation qualification.

## Protocol and acceptance

Both models use a synthetic equal-mole MEG/methane feed at each measured T/P.
It is not a measured overall feed or paired experimental tie line. Compare liquid
methane only. Vapor MEG is an unvalidated prediction, retained for independent
numerical parity only. Both independent prediction columns come from the frozen
r48 cubic-HV solver, not a fit to native results. The byte-identical solver is
retained as `binary_solver.py`; `reproduce_reference.py --output /path/report.json`
replays all12 endpoints and two mole-difference steps without NeqSim imports.

Native acceptance asserts all five ThermodynamicModelTest boolean identities
at1e-8 and checkNumerically at1e-6 on a detached, one-mole normalized basis,
with source serialization unchanged. The normalization preserves phase fractions,
compositions, log fugacity coefficients and n_phase*d(log phi)/dn; each invariant
is asserted, as is original-amount dimensionless Hessian symmetry at1e-10.
The raw1e-6mol checker is not reported as passing: at273.25K/99.8bara its final
trace-gas stencils62.89311579/62.89593135 do not resolve the analytic62.89384402
per mol. At323.45K/100.8bara, a raw Hessian pair57814.30331963301 and
57814.30331966281 per mol differs by2.98e-8 (5.2e-13 relative), exceeding its
absolute1e-8 gate. These observations were investigated before choosing a
homogeneous unit basis; no public checker or tolerance was modified. Two-phase
closure requires component balance1e-10, phase/composition normalization1e-12 and
interphase log-fugacity1e-8. Independent prediction parity uses5e-8 relative with
absolute floors1e-11 (liquid methane) and1e-13 (vapor MEG). These are numerical
limits, not experimental accuracy tolerances. Repeat/changed/returned states,
component order and total amounts1e-6/1/1000mol are compared with fresh controls.
Fresh fixed-composition gas/liquid roots independently resolve temperature
stencils0.001/0.0005K at1e-6*max(1,abs(derivative)). Five-component forced-root
controls include water, methanol and ethane, prove dormant-tuple invariance and
CLASSICAL reduction after disabling the sole MEG/methane pair. These are numerical
controls, not multicomponent physical-partition validation.

The assessment CSV under target/hv-meg-methane retains all physical errors,
compiler-band indicators and alpha-range flags. No experimental pass threshold,
parameter fitting or tolerance relaxation is permitted. Both-direction partition,
original source metadata/uncertainty and complete source-paired pure inputs remain
required before activation. No hydrate dissociation, ice/brine or heavy-fraction
qualification follows from this binary diagnostic.

## Reproduction and cost protocol

Run the executable JUnit fixture `HuronVidalMegMethaneDiagnosticTest`. It writes
`target/hv-meg-methane/assessment.csv`; it imposes no empirical error threshold.
The paired pure-MEG vapor-pressure assessment remains in
`HuronVidalLiteratureReproductionTest` and must be read with these binary errors.
The MC liquid-methane AARD is about4.90%; five of six points lie in the compiler
band, while the273.25K point is about12.84% low. This does not establish an
accepted physical profile. Vapor-MEG has no measured comparator here.

The manual `HuronVidalMegMethanePerformanceBenchmark` supports profile0 (MEG MC)
and1 (MEG Soave), rule2/rule4, and init1/init3/flash. The existing serial JVM
runner `devtools/run_huron_vidal_co2_benchmark.py` accepts `--mixture meg-methane`
with explicit `--profiles 0 1`. Use four independent forks and all three scenarios.
Each JVM fixes one alpha/rule/scenario to avoid mixed type profiles; rule order
alternates across forks. The equal-mole feed,298.25K/100.2bara and pure inputs
match. Complete flashes alternate298.25/298.45K. Pure-input fingerprints are
checked after rule switching; each timed round retains topology, phase fraction,
compositions and equilibrium residuals. Warmup is100000 initializations or3000
flashes, followed by ten rounds of10000 initializations or100 flashes.

Rule2/rule4 have different thermodynamics and solver work. Their ratio is model
cost, not implementation overhead or a speedup. Production Java is unchanged in
this diagnostic increment; existing held optimization candidates stay held.
Binary costs do not qualify large-mixture throughput or allocations.

The retained `performance-rounds.csv` has480 rounds from48 successful JVMs.
`performance-execution.json` losslessly factors the common JVM command from each
run and retains source/class hashes and the original raw-manifest hash; the
40-character revision is the verified production/build base, with the new
working-tree diagnostic identified separately by hashes. Maximum balance,
normalization and log-fugacity residuals are2.22e-16/1.11e-16/6.47e-13.
For MC, rule4/rule2 fork-median ratios span2.386-2.561 (init1),3.489-3.599
(init3),1.514-1.753 (full flash); Soave spans1.697-1.902/2.592-2.740/1.076-1.573.
GAS/AQUEOUS topology is shared but predicted compositions differ. AQUEOUS is
NeqSim's label for the MEG-rich liquid here; the binary feed contains no water. All rounds,
including timing outliers, are preserved. This is a cost characterization, not a
performance nonregression claim.

The separate untimed JFR profile has57 execution samples,15 containing
`ComponentGENRTLmodifiedHV.getGamma` and9 containing Mathias-Copeman attraction
work. Flash, molar-volume and stability work also appear. This limited sample
corroborates required model/solver costs; it does not isolate a redundant
calculation or justify changing the physical model. No held optimization is
activated. See `profile-summary.json` for stack-presence counts and recording hash.

The same declared MEG MC/critical configuration also passes the existing native
pure-MEG assessment:15 observations from DOI10.1021/je9004905, AARD5.8616%,
all15 within the retained compiler U95 bands. `pure-MEG-native.csv` preserves the
pointwise native pressures and errors; the source observations/protocol remain
in `../hv-meg-qualification/`. These pure pressures start at307.7K and do not
validate low-temperature binary extrapolation or uniquely diagnose interaction
fit errors. No new pure model or pressure fit is introduced.

## Replay input admission repair

The independent replay now rejects malformed or nonfinite reference numbers before
solving. A mutation of the first MC liquid-reference prediction to `NaN` previously
exited0 and wrote a report: comparisons with NaN could bypass the parity gate.
The repair requires the exact unit/direction/alpha header, six ordered retained
observation coordinates and compiler bands, eight finite decimal fields per row,
and physical mole-fraction bounds. Both alpha controls and the existing numerical
parity/refinement limits remain unchanged. Invalid solver endpoints/residuals also
fail explicitly; JSON output rejects nonfinite numbers. Reports must be written
outside this read-only fixture, and invalid inputs cannot overwrite a prior report.

`devtools/test_huron_vidal_meg_methane_replay.py` covers nonfinite values in every
column, malformed schemas/rows, changed/duplicate/reordered observations, invalid
compiler bands, solver nonfinite output, CLI failure with/without Python assertions,
read-only inputs and valid MC/Soave control reports. Successful normal/-O reports
retain SHA256 `86b3d91ed8874477c60ddbe31c0efd607f841c8a5f874355cf1391d5d96b28a7`.
This is an admission-integrity check on retained campaign data, not fresh primary
source verification or empirical accuracy acceptance. No observation, compiler
uncertainty, pure input, HV tuple, native checker, equation or timing value changes.
