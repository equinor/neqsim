# Independent binary two-phase PR benchmark

This validation-only implementation imports no NeqSim code and has no runtime
role in ordinary simulations. Inputs and outputs are original project synthetic
facts under the repository license. See [SCOPE.md](SCOPE.md) for the pre-comparison
bounds, physical protocol, units, base revision and explicit exclusions.

## Independent calculation

The standard-library reference uses the unshifted Peng-Robinson pressure equation
with the explicitly frozen component constants and symmetric interaction. It
isolates real cubic roots between derivative extrema by bisection. A damped
binary Newton solve equates both components' log fugacities in liquid and vapor.
The lever rule determines phase amounts. A separate pressure bisection closes
occupied volume after each withdrawal-before-injection substep. It rejects
unavailable phases, degenerate tie lines, invalid inputs and unbracketed roots.
No pressure, composition or GOR from NeqSim enters reference construction.

At the initial state, gas and oil moles are V*f_g/v_g and V*f_o/v_o, respectively;
component inventories follow from their equilibrium compositions. Initial phase
volume fractions are not molar vapor fraction. Every substep closes both named
component inventories. Surface GOR follows a separate single equilibrium flash
of the commingled interval production at the declared final temperature/pressure.
It is not a reservoir molar gas/oil ratio.

The independent laboratory observation is the one-mole occupied volume at
600 K/200 bara. Substituting that volume into the PR pressure equation gives the
mixture attraction parameter; because its cross term is linear in (1-kij),
solving that scalar equation identifies kij without reservoir data. Keep the
whole 650 K CCE out of identification. The laboratory0.8/0.2 composition and
reservoir phase-volume initialization have different bulk compositions: this
qualifies interaction transfer within a declared binary family, not a
composition-identical recovered laboratory sample-to-field reconstruction.

## Reproduction and evidence

Run `reference.py` with the selected Python interpreter to reproduce
[reference.json](reference.json). Run standard-library unittest discovery in this
directory with pattern `test_*.py`, also under `-O`. `test_reference.py` checks
root algebra, fugacity equality, conservation, phase-volume initialization,
laboratory identification, invalid-input rejection and retained endpoints.
`test_stability.py` independently uses Cardano/trigonometric roots and a separately
summed Gibbs expression. Its composition grid checks all physical roots on the
18 frozen path/surface states. This is bounded sampled stability evidence,
not a universal flash-stability proof or a critical-limit solver.

[stability-results.json](stability-results.json) pins both reference and independent
checker SHA256s. [native-results.json](native-results.json) retains the native
predictions and finite-substep comparisons. [validation.json](validation.json)
records executed checks. Tolerances are not fitted to these results.

The separate workspace JVM command is documented in native-results.json and in
`devtools/test_binary_reservoir_java.py`. It must run in its own process so its
fixture owns JVM initialization/shutdown. Java only receives phase withdrawal
amounts and named injection. Produced component amounts, surface GOR and tank
pressures are predictions. The final interval's pressure, composition and GOR
are validated at the pressure-fitted volume. Perturbing held-out pressure leaves
the fit unchanged; failed trials and repeated replay preserve initial state and
both serialized EOS checkpoints. The 1/2/4/8/16-substep matrix compares the same
finite protocol at each resolution and does not certify a continuous-flow limit.

No oil/water mixing, aqueous phase, well inflow, relative permeability, Bayesian
posterior, multi-tank transport or Tran RCVD is qualified here. Synthetic
independent-code agreement is not measured laboratory or field accuracy. Fitted
pore volume is an in-place quantity, not recoverable reserves.

## Composition-identical sample extension

The separate [same-sample scope](SAME_SAMPLE_SCOPE.md) retains the laboratory
0.8/0.2 bulk mixture at initial reservoir T/P. `same_sample.py` scales its
independent lever-rule molar volume to100m3 and derives the corresponding EOS
phase-volume fractions; the old0.7/0.3 case above remains unchanged.
[same-sample-reference.json](same-sample-reference.json) contains the full
laboratory calibration/holdouts and independent reservoir predictions.
The entire323.15K closed-mass CCE at80/70/60/50bara is withheld from kij fitting,
as is the original650K CCE. The native test executes
`ConstantMassExpansion.runNativeExpansion`, preserving every component and phase.
`test_same_sample.py` adds composition/volume/closed-cell controls and runs the
independent all-root Gibbs checker on22tank, surface and laboratory states.

`CompositionalReservoirHistory.fromSampleComposition` uses these EOS phase-volume
fractions, scaling all phase amounts by the same factor at each trial volume.
The native matrix checks80/100/120m3 initial compositions and inventory scaling,
matched finite extraction protocols1/2/4/8/16, predicted pressure/composition/GOR,
pressure-only volume matching and separately withheld outputs. Receipts
[same-sample-stability.json](same-sample-stability.json),
[same-sample-native.json](same-sample-native.json) and
[same-sample-validation.json](same-sample-validation.json) record executed evidence.
The self-contained [independent review receipt](same-sample-review.json) pins
reviewed sources and embeds numerical results; its raw diagnostic log names
identify local intermediate logs, not additional published fixtures.
The wet/single-phase Java initialization tests qualify composition retention for
their stated sample settings; the independent binary chain has no water and does
not independently qualify wet production or a general near-critical solver.
