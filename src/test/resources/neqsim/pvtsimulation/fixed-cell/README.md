# Explicit occupied-volume cell reference

Synthetic classic unshifted SRK numerical benchmark; no measured calibration or
field-accuracy claim. `independent_reference.py` uses NumPy/SciPy and declared
R/Tc/Pc/omega/kij inputs. No NeqSim calls or fitted parameters. At each pressure
it solves component fugacity equalities and the phase lever rule independently;
initial occupied volume defines Vref, later gas withdrawal is (Vbefore-Vref)/Vgas,molar.
All nongas phases remain. Positive/distinct named phases and reference closure
are required; no clipping or extrapolation is used.

72complete paths /216independent states:340/350/360K,200/100/50bara,
0.001/1/1000mol. Three ternary methane/decane/water feeds, three binary methane/
water feeds and two binary methane/decane feeds are declared in the generator.
Every path retains its initial phase family. Reference pressure/volume is an
explicit initial EOS state, not a dew point or measured saturation normalization.

Run independent_reference.py with Python3.12.14/NumPy2.3.5/SciPy1.17.0 and redirect
stdout to reference.json. Normal and optimized runs must be byte-identical.
`FixedCellDepletionHandoffTest` exercises both original/tuned checkpoint selections
(432state executions), immutable metadata, conservation and all retained/withdrawn
separator products. The separator is10bara303.15K then1.01325bara298.15K; final
product volumes are actual volumes at that last stated basis. No standard basis
is silently substituted. Diagnostics are written to target/fixed-cell-metrics.json.

Frozen bounds: relative cell volume1e-8; absolute phase/component inventory and
withdrawal1e-8mol at every sample scale; composition1e-8; named component closure
1e-10mol; separator component closure1e-8mol. EOS values are shared inputs, not
independent measurements. Named-phase equilibrium agreement is not proof of global
stability. Measured laboratory/field, phase-family transitions, critical states,
wet upper-dew inference and normalized black-oil conversion remain unqualified.
The explicit path requires separate immutable experiment/provenance identity.


## Intermediate T/P precision qualification

Run the same generator with `--off-grid` to reproduce `off-grid-reference.json`.
Use the same pinned Python/NumPy/SciPy versions above. The 96 complete paths
contain 480 states: 342.5/347.5/352.5/357.5 K; 195/160/125/90/55 bara; the same eight
feeds and 0.001/1/1000 mol. Both checkpoint selections give 960 state executions.
The original default generator and reference remain unchanged numerically.

The off-grid test uses all the original frozen bounds, including 1e-8 mol absolute
amount error at every scale. It fails on unmodified master 75a04732 before the
precision repair: the initial 342.5 K/195 bara methane/decane 800/200 mol split differs
by 2.04e-6 mol even before withdrawal. This is not lost total inventory. Reusing
TPmultiflash.solveBeta in this opt-in PVT route refines accepted endpoints; its
internal 1e-12 equilibrium/volume targets are not relaxed benchmark bounds. Phase
family, energy, conservation and transactional failure gates remain required.
Diagnostics: target/fixed-cell-off-grid-metrics.json. This is synthetic numerical
qualification, not measured accuracy, global stability or critical acceptance.
