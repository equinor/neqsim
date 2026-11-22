# Explicit wet-gas/aqueous fixed-cell reference

Synthetic unshifted classic SRK methane/water. No measured calibration, water-model
accuracy, wet upper-dew inference, critical acceptance or field validation claim.
The full R/Tc/Pc/omega/kij constants and solver settings are in the source; they
are shared model inputs and were not fitted to NeqSim or reservoir volumes.

Run `python3 independent_reference.py > reference.json`. The independent code
uses NumPy/SciPy, evaluates every positive real SRK volume root, locates a binary
lower-convex-hull gap and refines its common tangent using component fugacity
equalities. A finite composition-grid lower-support screen is not a global
stability proof. Phase amounts follow the binary lever rule. Homogeneous gas is
admitted only on the gas side of the binodal, using the lowest homogeneous Gibbs
root. At each pressure gas alone is withdrawn to the original occupied volume;
all remaining component inventories and actual present phase compositions are
reported. No absent aqueous phase values or pseudo black-oil properties appear.

27 paths, 270 states: 440/450/460 K, water 0.05/0.08/0.10 mole fraction,
0.001/1/1000 initial mol and pressures 200/150/100/70/50/30/20/10/5/2 bara.
15 paths include aqueous disappearance; the other paths are homogeneous gas
limits. `WetGasFixedCellHandoffTest` checks both checkpoint choices, 540 endpoint
executions and all retained/withdrawn separator inventories. Those two labeled
checkpoints are a numerical control, not independently measured EOS calibrations.
The separator is 1.01325 bara, 298.15 K, with actual final product volumes.

Frozen comparison bounds: absolute phase/component/withdrawal amounts 1e-8 mol;
composition 1e-8; relative volume 1e-8 against the independent EOS; named balance
1e-10*max(1,initial component mol). Existing internal 1e-12 equilibrium/volume gates
are unchanged. The original fixed-family method still rejects a topology change.
A separate registered-zero-water matrix checks the pure-methane consistency limit;
other tests check serialization, defensive results, translated checkpoint identity,
oil/missing-gas rejection and invalid input. Raw case outcomes are written to
`target/wet-gas-fixed-cell-metrics.json`; failed cases are retained, not omitted.

The pre-comparison SCOPE.md and original-master scalar probe are retained with the
validation receipt. An explicit fixed-cell laboratory convention must not be
silently relabeled as saturation-referenced CVD or a measured production history.


## Off-grid longer-path qualification

`offgrid_reference.py` calls the same independent generator with unchanged declared inputs at
442.5/452.5/457.5 K, water mole fractions 0.06/0.085/0.095 and
0.01/10/100 initial mol. The 20 pressures are
200/180/160/140/120/100/90/80/70/60/50/40/30/20/15/10/7.5/5/3.5/2 bara.
All 27 paths and 540 reference states are retained. Twenty-one paths have aqueous
disappearance and six stay homogeneous gas; none predicts aqueous appearance.
The JUnit fixture checks both checkpoint selections (1080 endpoint comparisons),
with the same amount/composition/volume bounds as the original matrix.

The callable-generator refactor regenerates the original reference.json byte for
byte. `OFFGRID_SCOPE.md` was frozen before new-row generation/comparison;
`OFFGRID_VALIDATION.md` records original-master controls, actual outcomes, commands,
coverage, current-master compatibility and limits. The standalone
`OffGridMasterProbe.java` uses only the old fixed-family API and preserves all
27 baseline outcomes in offgrid-master-results.json. These controls are synthetic
numerical qualification; they do not add measured laboratory/field holdouts or
infer wet upper-dew, critical or oil-containing transition support.

Different pressure sequences remain separate experiment protocols. No equality of
final composition across different withdrawal schedules or continuous-depletion
convergence is asserted by these longer-path comparisons.
