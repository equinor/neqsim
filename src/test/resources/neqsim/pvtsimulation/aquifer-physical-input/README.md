# Physical radial-aquifer input qualification (#4740, Run71)

Base: `bb5c7d45071d5537adce23e2217da28c49776d85`.
[Frozen scope](https://github.com/equinor/neqsim/issues/4740#issuecomment-6724480933)
and [runtime addendum](https://github.com/equinor/neqsim/issues/4740#issuecomment-6724581060).

This batch qualifies the existing physical conversion APIs, not a new aquifer
flow model. Time uses permeability m2, seconds, porosity fraction, viscosity
Pa s, compressibility **1/Pa** and the finite positive **inner** radius m.
The storage constant uses compressibility **1/bar**, thickness m and angle
degrees, and returns reservoir m3/bar. For consistent pressure bases,
`ctBar = 1e5 * ctPa` and the independent joint identity is
`U*tD = 2*pi*(angle/360)*k*time*height*1e5/viscosity`.

Permeability/time are finite and nonnegative. Time porosity is (0,1], viscosity
and time compressibility are positive. Storage porosity is [0,1]; storage
compressibility and thickness are nonnegative, and angle is [0,360]. All inputs
are validated before physical-zero shortcuts. Positive conversions that round
to zero or overflow fail explicitly; representable subnormal results remain
positive. Ordinary safe intermediates retain the original operator order and
bits. Unsafe intermediate ranges use scaled factors; final rounding near the
representability endpoints uses exact factors and integer half-even rounding
in binary-ULP units. No approximate decimal quotient decides midpoint parity.

## Evidence and acceptance

`PhysicalInputs.java` declares **340 rows**, including repeated physical states
under separate axis/identity controls; these are not 340 unique experiments.
There are 216 positive finite, 6 zero, 73 invalid and 45 unrepresentable outcomes.
The initial 333-row matrix was extended with four explicit IEEE midpoint cases and three finite upper-bound controls
after independent review. Targets and tolerances were not loosened.

`qualify.py` uses Python `Fraction.from_float` to evaluate exact binary inputs
and classify final rounded values. This avoids the rounded-decimal oracle error
at exact half-MIN/overflow ties. Normal finite values use the frozen relative
1e-12 plus one final ULP bound; subnormal/tie results additionally retain exact
binary outcomes. Input identity is independently hash-pinned and every rejected
or nonfinite baseline output is retained. No result is dropped as an outlier.
`AquiferPhysicalInputTest` separately checks ordinary bits, 80-digit arithmetic
away from exact classification ties, explicit boundary parity and the joint
physical-units identity at 54 nearby physical states.

All 340 final native rows pass. Unmodified master fails 130 rows under the same
contract. The first scaled implementation misclassified representability; a
superseded 256-digit decimal variant also rounded an exact 1.5-MIN midpoint to
MIN instead of 2*MIN. Failed sources, logs and native results remain archived.
The final algorithm compares the exact overflow midpoint and rounds the ratio
to an integer number of binary ULPs. Five integrity-test methods reject lost or
changed identities, false exception/output forms, altered boundary outcomes
and damaged runtime samples. These are mathematical consistency and software
qualification, not independent measured PVT/field accuracy.

Four fresh Zulu Java8.0.512-b09 JVMs ran parent/current/current/parent with three
warmups and seven measured samples each, fixed 256MiB heap. Each complete radial
workload converts physical inputs for 20 histories of 1,000 intervals and then
runs the unchanged Carter-Tracy calculation. Complete checksums are identical;
the conservative worst current/best-parent median ratio is 1.073, below the
pre-comparison 1.20 ceiling. The standalone 500,000-pair conversion loop costs
10.75–10.80ms versus 0.849–1.161ms: validation adds about 20ns per pair. This
isolated slowdown is explicit; no speedup is claimed. Cold first conversion
pairs measured 0.665–0.704ms current and 0.664–0.746ms parent, with shared-host
variance. No wall-clock assertion is added to ordinary tests. Ordinary EOS,
flash, PVT, Fetkovich and CT-step paths acquire no conversion calls or arrays.

## Reproduction and retention

The ordinary Maven tests need no downloads or optional native evidence.
Use the current work-with-neqsim Maven bootstrap before Maven/Spotless. Compile
the two affected production classes and this optional driver separately against
the exact parent and current sources using an actual Java8 compiler. For each
fresh JVM run:

```sh
$JDK8/bin/java -Xms256m -Xmx256m -cp /tmp/current-classes PhysicalInputs q71-1-after.csv q71-1-after-runtime.csv
$JDK8/bin/java -Xms256m -Xmx256m -cp /tmp/current-classes PhysicalInputs --cold q71-1-after-cold.csv
$PYTHON /path/to/qualify.py --before q71-0-parent.csv --after q71-1-after.csv --output qualification.json --runtime q71-0-parent-runtime.csv q71-1-after-runtime.csv q71-2-after-runtime.csv q71-3-parent-runtime.csv
NEQSIM_PHYSICAL_EVIDENCE=/path/to/evidence $PYTHON -O /path/to/test_qualification.py
```

Repeat the JVM command with the corresponding parent/current classes in ABBA
order and matching names. Keep all warmups and raw timing samples. The complete
native CSVs, failed variants, commands/exits, runtime metadata and analysis
reports are archived separately with hashes; do not recommit generated output
matrices. The [compact validation receipt](VALIDATION.md) links the durable campaign evidence.

Finite-radius pressure physics, measured laboratory-to-field accuracy, broader
aquifer/Bayesian and multi-tank qualification remain separate open acceptance.
Do not convert invalid or unrepresentable inputs into zero-support prior
scenarios, retune the EOS, or infer recoverable reserves from these conversions.
