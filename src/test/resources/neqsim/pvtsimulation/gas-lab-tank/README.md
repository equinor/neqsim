# Independent gas-only laboratory-to-tank benchmark

This is a reproducible **synthetic numerical benchmark**, independent of NeqSim's
flash and history implementations. It is not an experimental fluid calibration,
measured field history, two-phase GOR benchmark or recoverable-reserves estimate.
The source and generated facts are original project work under the repository's
license; no third-party laboratory table is copied or implied licensed.

Run `reference.py` with the selected Python interpreter (standard library only).
The default60digit Decimal calculation and a75digit repeat generate identical
binary64 JSON values. The root solver bisects the declared SRK gas branch and
asserts its pressure equation residual below1e-45bara. Decimal convergence is not
physical input precision. The native test is `IndependentGasLabTankTest`.

## Frozen model and laboratory provenance

Component order is methane/ethane, mole fractions0.9/0.1, sample amount1mol.
Classic unshifted SRK uses R8.3144621J/(mol K), Tc190.56/305.32K,
Pc45.99/48.72bara, acentric factors0.0115/0.0995, and symmetric kij.
The usual SRK alpha is `(1 + m*(1-sqrt(T/Tc)))^2`, with
`m=0.48+1.574*omega-0.176*omega^2`. Pure a and b follow the SRK critical
constraints; mixing is quadratic in a and linear in b. No volume translation.

An original checkpoint has kij0. A synthetic calibration observation supplies
400K/200bara and its independently generated one-mole occupied volume. Solving
the SRK pressure equation algebraically for the sole interaction identifies
kij0.02. This inversion is identifiable for the declared nonzero binary fractions
because its attraction sensitivity is nonzero. It is not a statistical confidence
interval or noisy-data regression. No reservoir observation enters identification.

The **entire separate CCE at420K**, pressures200/150/100/50bara, is withheld from
identification. Mass stays closed at every pressure. Both original and frozen
calibrated checkpoints are replayed, with unchanged sample/whole-experiment and
actual separator provenance. Original EOS mismatch is retained rather than hidden.
No constant-composition table is labeled CVD, and no laboratory row is relabeled
reservoir calibration evidence.

## Reservoir and produced-fluid evidence

The tank is isothermal400K/200bara with initial gas-filled volume1017.3m3 and no
oil/water. Four86400s intervals each prescribe100000mol equilibrium gas withdrawal.
Methane/ethane injection totals are `[0,20000]`, `[10000,0]`, `[0,15000]`,
`[5000,5000]`mol. Every interval has four equal substeps, removing start-state gas
composition before adding injection. The reference independently updates named
inventories and evaluates pressure from the SRK equation at V/N. It does not call
NeqSim or prescribe produced component totals to the native replay.

Only pore volume is fitted within800..1300m3 to the first three independent
pressures with declared synthetic sigma0.5bara. The fourth interval's pressure,
produced composition and actual surface gas volume are withheld. A second history
perturbs only held-out pressure by+5bara: its best volume must be unchanged and
held-out chi-square100. Failed small-volume trials and subsequent replay check
transactional restart; serialized checkpoints must remain byte-identical.

Surface products use1.01325bara/288.15K and the independently solved SRK gas root,
not ideal-gas standardization. No stock-tank oil exists, so GOR is **undefined/NaN**.
The test validates pressure, component amounts and gas product volume without
inventing absent-phase properties. Interval substeps define this finite extraction
protocol; this benchmark does not establish a continuous-flow timestep limit.

All tolerances were frozen in SCOPE.md before native comparison. Full independent
inputs/outputs are in reference.json; complete native results and actually executed
validation commands are retained alongside this receipt when published.

## Continuous well-mixed flow refinement

Run `continuous_reference.py` with the selected Python interpreter, optionally
passing decimal precision75, to regenerate `continuous-reference.json`.
`reference.py` now exposes its unchanged original generator behind a main guard;
the original `reference.json` is byte-identical. Both scripts use only the Python
standard library. See [REFINEMENT_SCOPE.md](REFINEMENT_SCOPE.md) for the freeze,
pre-native failed64-substep1mol target, and finer meshes chosen without native data.

For constant production rate and injection composition over one interval, write
$I_i$ and $P$ for injected component moles and produced total moles over that
interval, $I=\sum_i I_i$, and $N_1=N_0+I-P$. The exact uniformly mixed gas balance
$d n_i/dt=q_i-q_p n_i/N$ gives

$$x_{i,1}=x_{i,inj}+(x_{i,0}-x_{i,inj})(N_1/N_0)^{-I/(I-P)}$$

when $I>0$ and $I\ne P$. When $I=P>0$, the power is replaced by
$\exp(-I/N_0)$; when $I=0$, composition is unchanged. The produced component
amount is $n_{i,0}+I_i-n_{i,1}$. Here $x_{i,inj}=I_i/I$, inventories and interval
amounts are in mol, rates in mol/s, and time in s. Injection-only balances reduce
to exact mixing; no-flow produces no mixture. Pressures use the same independently
implemented SRK equation at fixed400K and1017.3m3, not a pressure-target solve.

The native test compares every finite-substep endpoint, produced components,
pressure and actual surface gas product against the independent finite reference,
then compares the component-inventory errors against the analytical limit. The
ratio must remain0.45..0.55 when halving the timestep. No reservoir-volume fit or
EOS retuning participates in this refinement experiment. The1mol target is a
synthetic numerical bound; it is not measurement uncertainty or a statistical
confidence limit. Raw native outcomes and timing are in `continuous-results.json`;
[REFINEMENT_VALIDATION.md](REFINEMENT_VALIDATION.md) records commands and results.

## Unequal-sigma deterministic inversion

`weighted_reference.py` supplies an independent direct-volume golden-section
minimization for the existing finite4-substep gas protocol. Run it with the
selected Python interpreter, optionally passing precision75; the JSON must match
`weighted-reference.json`. The native fitter retains its existing log-volume
strategy. All inputs, the original incorrectly anticipated1000m3 bound and the
additional980m3 bound control are preserved in [WEIGHTED_SCOPE.md](WEIGHTED_SCOPE.md).
The independent reference established that1000is inactive before native testing;
that control is retained, not silently changed to return a bound.

The same noisy pressures fit different volumes under unequal/equal sigmas.
Scaling all three calibration sigmas by3 must retain volume while dividing the
calibration chi-square by9. The fourth pressure is excluded from matching;
its shifted residual is independently evaluated and need not give chi-square100
when the noisy calibration has already biased volume. Predicted produced
components and actual surface gas volume are compared at the independently
fitted volume; no comparison is mislabeled as measured accuracy. The actual
native fit has finite numerical volume precision, so the frozen product/pressure
bounds remain explicit. No oil exists and GOR remains undefined.

Complete native outputs, commands and validation are recorded in
[WEIGHTED_VALIDATION.md](WEIGHTED_VALIDATION.md) and `weighted-results.json`.
