# Frozen independent gas-only laboratory-to-tank benchmark

Base b20e324a60a94778b11425257bb3d8e9636389b7. No production changes.
Classic unshifted SRK methane/ethane, explicitly supplied symmetric kij.
Synthetic laboratory calibration: one 400 K closed-cell point identifies kij=0.02
by algebra from pressure, composition and molar volume. Original kij=0 is retained.
Whole separate 420 K CCE experiment is the laboratory holdout; never use reservoir
volume or pressure to retune the EOS. Both immutable checkpoints are preserved.

Reservoir: 400 K/200 bara, 1017.3 m3, initially all gas; prescribed gas-phase
moles withdrawn, independently predicted composition; named injection added after
withdrawal at each of four equal substeps. Four 86400 s intervals, 100000 mol gas
production each; alternating methane/ethane injections. Fit only volume800..1300 m3
against first three independent pressure observations with sigma0.5bara. Fourth
pressure is held out; a separate +5bara holdout perturbation must not alter fit.
Check held-out produced composition and actual surface gas volume at1.01325bara,
288.15K. There is no oil: GOR must be NaN, never fabricated or called validated.

Freeze bounds before comparing native output: laboratory relative volume1e-8;
pressure1e-6bara; component amounts1e-4mol; named conservation1e-9relative;
surface gas volume1e-7relative; fitted volume0.001m3; holdout shifted chi-square
100within0.001. Repeat/restart and serialized checkpoints must be unchanged.
Reference uses Decimal60-digit arithmetic and independently bisects the SRK
pressure equation; no NeqSim imports, generated native expectations, fitted hidden
parameters, phase interpolation, clipping or extrapolation. Repeat at75digits.

This completes one gas-only synthetic benchmark concept, not measured accuracy,
two-phase GOR/critical acceptance, aquifer/Bayesian/multi-tank or the full issue.
No ordinary-simulation production runtime cost is introduced. Public synthetic
facts and our reference code; no external experimental data or redistribution.
