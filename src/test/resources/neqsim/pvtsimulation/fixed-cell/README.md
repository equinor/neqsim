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
