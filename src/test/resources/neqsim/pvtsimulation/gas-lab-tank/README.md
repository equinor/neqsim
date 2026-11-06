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
